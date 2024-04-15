package com.hym.compose.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastForEach
import com.hym.compose.utils.Comparator
import com.hym.compose.utils.Logger
import com.hym.compose.utils.SourceMarker
import com.hym.compose.utils.calculateScaledRect
import com.hym.compose.utils.closeQuietly
import com.hym.compose.utils.roundToIntSize
import com.hym.compose.zoom.ZoomState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Source
import kotlin.math.roundToInt

/**
 * @author hehua2008
 * @date 2024/4/13
 */
@Stable
@Composable
fun rememberSubsamplingState(
    zoomState: ZoomState,
    sourceProvider: suspend () -> Source,
    previewProvider: suspend () -> ImageBitmap,
    sourceSize: IntSize, // Not as key
    imageBitmapRegionDecoderFactory: (SourceMarker) -> ImageBitmapRegionDecoder<*>?, // Not as key
    onDisposePreview: ((preview: ImageBitmap) -> Unit)? = null // Not as key
): SubsamplingState {
    val scope = rememberCoroutineScope() // Not as key
    val subsamplingState = remember(zoomState, sourceProvider, previewProvider) {
        SubsamplingState(
            sourceSize = sourceSize,
            zoomState = zoomState,
            sourceProvider = sourceProvider,
            previewProvider = previewProvider,
            imageBitmapRegionDecoderFactory = imageBitmapRegionDecoderFactory,
            onDisposePreview = onDisposePreview,
            scope = scope
        )
    }
    return subsamplingState
}

@OptIn(InternalCoroutinesApi::class)
@Stable
class SubsamplingState(
    val sourceSize: IntSize,
    private val zoomState: ZoomState,
    private val sourceProvider: suspend () -> Source,
    private val previewProvider: suspend () -> ImageBitmap,
    private val imageBitmapRegionDecoderFactory: (SourceMarker) -> ImageBitmapRegionDecoder<*>?,
    private val onDisposePreview: ((preview: ImageBitmap) -> Unit)? = null,
    private val scope: CoroutineScope
) : RememberObserver {
    companion object {
        private const val TAG = "SubsamplingState"

        private const val SLICE_THRESHOLD = 640
        private const val DISPLAY_SOURCE_THRESHOLD = 1f
        private const val TILE_OVERLAP_SCALE = 1f
    }

    data class Tile(
        internal val sourceRect: IntRect,
        internal val sampleSize: Int
    ) {
        var imageBitmap: ImageBitmap? = null
            private set

        val srcSize: IntSize
            get() = imageBitmap?.run { IntSize(width, height) } ?: IntSize.Zero

        var dstOffset: IntOffset = IntOffset.Zero
            internal set

        var dstSize: IntSize = IntSize.Zero
            internal set

        constructor(imageBitmap: ImageBitmap, dstSize: IntSize) : this(
            sourceRect = IntRect.Zero,
            sampleSize = 1
        ) {
            this.imageBitmap = imageBitmap
            this.dstSize = dstSize
        }

        private val lock = SynchronizedObject()

        private var isDestroyed = false

        internal fun destroy() {
            synchronized(lock) {
                imageBitmap?.recycle()
                imageBitmap = null
                isDestroyed = true
            }
        }

        internal fun decodeBitmap(
            decoder: ImageBitmapRegionDecoder<*>,
            decoderLock: SynchronizedObject
        ): Boolean {
            synchronized(lock) { // Fast check, with this lock and without decoder lock
                if (imageBitmap != null || isDestroyed) return true
            }
            synchronized(decoderLock) {
                synchronized(lock) { // Check again to avoid repeated decoding
                    if (imageBitmap != null || isDestroyed) return true
                }
                val bitmap = decoder.decodeRegion(rect = sourceRect, sampleSize = sampleSize)
                if (bitmap == null) {
                    Logger.e(TAG, "Failed to decodeRegion($sourceRect) from source")
                    return false
                }
                synchronized(lock) { // Check if destroy is called
                    if (isDestroyed) {
                        bitmap.recycle()
                        return true
                    }
                    imageBitmap = bitmap
                    return true
                }
            }
        }
    }

    private var source by mutableStateOf<SourceMarker?>(null)
    private var preview by mutableStateOf<ImageBitmap?>(null)
    private var sourceDecoder by mutableStateOf<ImageBitmapRegionDecoder<*>?>(null)
    private val sourceDecoderLock = SynchronizedObject()

    internal val previewTile by derivedStateOf {
        val curPreview = preview ?: return@derivedStateOf null
        val contentBounds = zoomState.contentBounds
        if (contentBounds.isEmpty) return@derivedStateOf null
        Tile(
            imageBitmap = curPreview,
            dstSize = contentBounds.size.roundToIntSize()
        )
    }

    internal var displayTiles by mutableStateOf<ImmutableList<Tile>>(persistentListOf())
        private set

    private val reusableTiles = mutableListOf<Tile>()
    private val reusableTilesLock = SynchronizedObject()

    private fun clearReusableTiles() {
        synchronized(reusableTilesLock) {
            reusableTiles.fastForEach { tile ->
                tile.destroy()
            }
            reusableTiles.clear()
        }
    }

    private val pendingTileList by derivedStateOf {
        val transformedContentBounds = zoomState.transformedContentBounds
        val contentSize = zoomState.contentBounds.size
        val contentIntSize = contentSize.roundToIntSize()
        val curPreview = preview
        val curSourceDecoder = sourceDecoder

        if (transformedContentBounds.isEmpty || curSourceDecoder == null ||
            (curPreview != null && transformedContentBounds.run {
                width / curPreview.width < DISPLAY_SOURCE_THRESHOLD &&
                        height / curPreview.height < DISPLAY_SOURCE_THRESHOLD
            })
        ) {
            persistentListOf()
        } else {
            val sampleSize = (sourceSize.width / transformedContentBounds.width)
                .coerceAtMost(sourceSize.height / transformedContentBounds.height)
                .let { sourceScale ->
                    var sampleSize = 1
                    while (sourceScale >= sampleSize * 2) {
                        sampleSize *= 2
                    }
                    sampleSize
                }

            val layoutBounds = zoomState.layoutBounds
            val overlapLayoutBounds = layoutBounds.calculateScaledRect(TILE_OVERLAP_SCALE)

            var horizontalSlice = 1
            while (transformedContentBounds.width / horizontalSlice > SLICE_THRESHOLD) {
                horizontalSlice *= 2
            }
            var verticalSlice = 1
            while (transformedContentBounds.height / verticalSlice > SLICE_THRESHOLD) {
                verticalSlice *= 2
            }

            val tileWidth = (contentSize.width / horizontalSlice).roundToInt()
            val tileLastWidth = contentIntSize.width - (horizontalSlice - 1) * tileWidth
            val tileHeight = (contentSize.height / verticalSlice).roundToInt()
            val tileLastHeight = contentIntSize.height - (verticalSlice - 1) * tileHeight

            val transform = zoomState.transform

            val tileScaledContentWidth = tileWidth * transform.scaleX
            val tileScaledContentLastWidth = tileLastWidth * transform.scaleX
            val tileScaledContentHeight = tileHeight * transform.scaleY
            val tileScaledContentLastHeight = tileLastHeight * transform.scaleY

            val tileSourceHorizontalScale = sourceSize.width / contentSize.width
            val tileSourceVerticalScale = sourceSize.height / contentSize.height

            val pendingTiles = mutableListOf<Tile>()

            for (column in 0 until verticalSlice) {
                val tileScaledY = tileScaledContentHeight * column + transformedContentBounds.top
                val tileOffsetY = tileHeight * column
                val tileSourceY = tileOffsetY * tileSourceVerticalScale

                for (row in 0 until horizontalSlice) {
                    val tileScaledX = tileScaledContentWidth * row + transformedContentBounds.left
                    val tileOffsetX = tileWidth * row
                    val tileSourceX = tileOffsetX * tileSourceHorizontalScale

                    val tileScaledRect = Rect(
                        tileScaledX,
                        tileScaledY,
                        tileScaledX + if (row < horizontalSlice - 1) tileScaledContentWidth else tileScaledContentLastWidth,
                        tileScaledY + if (column < verticalSlice - 1) tileScaledContentHeight else tileScaledContentLastHeight
                    )
                    if (!tileScaledRect.overlaps(overlapLayoutBounds)) continue

                    val tileOffset = IntOffset(tileOffsetX, tileOffsetY)

                    val tileSize = IntSize(
                        if (row < horizontalSlice - 1) tileWidth else tileLastWidth,
                        if (column < verticalSlice - 1) tileHeight else tileLastHeight
                    )

                    val tileSourceRect = Rect(
                        tileSourceX,
                        tileSourceY,
                        tileSourceX + tileSize.width * tileSourceHorizontalScale,
                        tileSourceY + tileSize.height * tileSourceVerticalScale
                    ).roundToIntRect()

                    val tile = synchronized(reusableTilesLock) {
                        val index = reusableTiles.binarySearch {
                            when {
                                sampleSize < it.sampleSize -> -1
                                sampleSize > it.sampleSize -> 1
                                else -> IntRect.Comparator.compare(tileSourceRect, it.sourceRect)
                            }
                        }
                        if (index >= 0) {
                            reusableTiles[index]
                        } else {
                            val insertIndex = -index - 1
                            Tile(
                                sourceRect = tileSourceRect,
                                sampleSize = sampleSize
                            ).also {
                                reusableTiles.add(insertIndex, it)
                            }
                        }
                    }
                    tile.dstOffset = tileOffset
                    tile.dstSize = tileSize

                    pendingTiles.add(tile)
                }
            }

            pendingTiles.toImmutableList()
        }
    }

    private var loadSourceJob: Job? = null
    private var loadPreviewJob: Job? = null
    private var updateTilesJob: Job? = null

    override fun onRemembered() {
        loadSourceJob = scope.launch {
            val sourceMarker = withContext(Dispatchers.IO) {
                SourceMarker(sourceProvider())
            }
            val decoder = withContext(Dispatchers.IO) {
                imageBitmapRegionDecoderFactory(sourceMarker)
            }
            if (decoder == null) {
                sourceMarker.source().closeQuietly()
                Logger.e(TAG, "Failed to create ImageBitmapRegionDecoder")
                return@launch
            }
            if (!isActive) {
                sourceMarker.source().closeQuietly()
                decoder.close()
                return@launch
            }
            source = sourceMarker
            if (decoder.width != 0 && decoder.height != 0) {
                zoomState.contentAspectRatio = decoder.width / decoder.height.toFloat()
            }
            sourceDecoder = decoder
        }

        loadPreviewJob = scope.launch {
            val imageBitmap = withContext(Dispatchers.IO) {
                previewProvider()
            }
            if (!isActive) {
                onDisposePreview?.invoke(imageBitmap)
                return@launch
            }
            preview = imageBitmap
            val decoder = sourceDecoder
            if ((decoder == null || decoder.width == 0 || decoder.height == 0) &&
                (imageBitmap.width != 0 && imageBitmap.height != 0)
            ) {
                zoomState.contentAspectRatio = imageBitmap.width / imageBitmap.height.toFloat()
            }
        }

        updateTilesJob = scope.launch(Dispatchers.IO) {
            snapshotFlow { pendingTileList }
                .collectLatest { pendingTiles ->
                    val curSourceDecoder = sourceDecoder
                    if (curSourceDecoder == null) {
                        // Update to no tile
                    } else {
                        pendingTiles.fastForEach { tile ->
                            if (!isActive) { // updateTilesJob was cancelled
                                clearReusableTiles()
                                return@collectLatest
                            }
                            tile.decodeBitmap(curSourceDecoder, sourceDecoderLock)
                        }
                    }

                    displayTiles = pendingTiles

                    synchronized(reusableTilesLock) {
                        val iterator = reusableTiles.iterator()
                        while (iterator.hasNext()) {
                            val reusableTile = iterator.next()
                            if (pendingTiles.contains(reusableTile)) continue
                            reusableTile.destroy()
                            //TODO: iterator.remove()
                        }
                    }
                }
        }
    }

    override fun onForgotten() {
        loadSourceJob?.cancel()
        source?.source()?.closeQuietly()
        sourceDecoder?.close()

        loadPreviewJob?.cancel()
        preview?.let {
            onDisposePreview?.invoke(it)
        }

        updateTilesJob?.cancel()
        clearReusableTiles()
    }

    override fun onAbandoned() {
        onForgotten()
    }
}
