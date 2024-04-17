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
import com.hym.compose.utils.ImmutableEqualityList
import com.hym.compose.utils.Logger
import com.hym.compose.utils.SourceMarker
import com.hym.compose.utils.calculateScaledRect
import com.hym.compose.utils.closeQuietly
import com.hym.compose.utils.emptyImmutableEqualityList
import com.hym.compose.utils.mutableEqualityListOf
import com.hym.compose.utils.roundToIntSize
import com.hym.compose.zoom.ZoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    sourceIntSize: IntSize = IntSize.Zero, // Not as key
    imageBitmapRegionDecoderFactory: (SourceMarker) -> ImageBitmapRegionDecoder<*>?, // Not as key
    onDisposePreview: ((preview: ImageBitmap) -> Unit)? = null // Not as key
): SubsamplingState {
    val scope = rememberCoroutineScope() // Not as key
    val subsamplingState = remember(zoomState, sourceProvider, previewProvider) {
        SubsamplingState(
            sourceIntSize = sourceIntSize,
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
    sourceIntSize: IntSize = IntSize.Zero,
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

    sealed interface Tile {
        val imageBitmap: ImageBitmap?

        val srcSize: IntSize

        val dstOffset: IntOffset

        val dstSize: IntSize
    }

    internal data class ReusableTile(
        val sourceRect: IntRect,
        val sampleSize: Int
    ) : Tile {
        override var imageBitmap: ImageBitmap? = null
            private set

        override val srcSize: IntSize
            get() = imageBitmap?.run { IntSize(width, height) } ?: IntSize.Zero

        override val dstOffset: IntOffset = IntOffset.Zero

        override val dstSize: IntSize = IntSize.Zero

        private val lock = SynchronizedObject()

        private var isDestroyed = false

        fun destroy() {
            synchronized(lock) {
                imageBitmap?.recycle()
                imageBitmap = null
                isDestroyed = true
            }
        }

        fun decodeBitmap(
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

    internal data class SnapshotTile(
        val reusableTile: ReusableTile,
        override val dstOffset: IntOffset,
        override val dstSize: IntSize
    ) : Tile by reusableTile

    internal data class PreviewTile(
        override val imageBitmap: ImageBitmap,
        override val dstSize: IntSize
    ) : Tile {
        override val srcSize: IntSize = IntSize(imageBitmap.width, imageBitmap.height)
        override val dstOffset: IntOffset = IntOffset.Zero
    }

    private var source by mutableStateOf<SourceMarker?>(null)
    private var preview by mutableStateOf<ImageBitmap?>(null)
    private var sourceDecoder by mutableStateOf<ImageBitmapRegionDecoder<*>?>(null)
    private val sourceDecoderLock = SynchronizedObject()
    internal var sourceSize by mutableStateOf(sourceIntSize)
        private set

    internal val previewTile by derivedStateOf<Tile?> {
        val curPreview = preview ?: return@derivedStateOf null
        val contentBounds = zoomState.contentBounds
        if (contentBounds.isEmpty) return@derivedStateOf null
        PreviewTile(
            imageBitmap = curPreview,
            dstSize = contentBounds.size.roundToIntSize()
        )
    }

    internal var displayTiles: ImmutableEqualityList<out Tile>
            by mutableStateOf(emptyImmutableEqualityList())
        private set

    private val reusableTiles = mutableListOf<ReusableTile>()
    private val reusableTilesLock = SynchronizedObject()

    private fun clearReusableTiles() {
        synchronized(reusableTilesLock) {
            reusableTiles.fastForEach { tile ->
                tile.destroy()
            }
            reusableTiles.clear()
        }
    }

    private val pendingTileList: ImmutableEqualityList<SnapshotTile> by derivedStateOf {
        val transformedContentBounds = zoomState.transformedContentBounds
        val contentSize = zoomState.contentBounds.size
        val contentIntSize = contentSize.roundToIntSize()
        val curPreview = preview
        val curSourceDecoder = sourceDecoder
        val curSourceSize = sourceSize

        if (transformedContentBounds.isEmpty || curSourceDecoder == null || curSourceSize == IntSize.Zero ||
            (curPreview != null && transformedContentBounds.run {
                width / curPreview.width < DISPLAY_SOURCE_THRESHOLD &&
                        height / curPreview.height < DISPLAY_SOURCE_THRESHOLD
            })
        ) {
            emptyImmutableEqualityList()
        } else {
            val sampleSize = (curSourceSize.width / transformedContentBounds.width)
                .coerceAtMost(curSourceSize.height / transformedContentBounds.height)
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

            val tileSourceHorizontalScale = curSourceSize.width / contentSize.width
            val tileSourceVerticalScale = curSourceSize.height / contentSize.height

            val pendingTiles = mutableEqualityListOf<SnapshotTile>()

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

                    val reusableTile = synchronized(reusableTilesLock) {
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
                            ReusableTile(
                                sourceRect = tileSourceRect,
                                sampleSize = sampleSize
                            ).also {
                                reusableTiles.add(insertIndex, it)
                            }
                        }
                    }

                    val tile = SnapshotTile(
                        reusableTile = reusableTile,
                        dstOffset = tileOffset,
                        dstSize = tileSize
                    )

                    pendingTiles.add(tile)
                }
            }

            pendingTiles.toImmutableEqualityList()
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
                sourceSize = IntSize(decoder.width, decoder.height)
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
            var decodeTilesJob: Job? = null

            snapshotFlow { pendingTileList }
                .collectLatest { pendingTiles ->
                    decodeTilesJob?.cancelAndJoin()

                    decodeTilesJob = launch(Dispatchers.IO) decode@{
                        val curSourceDecoder = sourceDecoder
                        if (curSourceDecoder == null) {
                            // Update to no tile
                        } else {
                            pendingTiles.fastForEach { tile ->
                                if (!isActive) { // decodeTilesJob or updateTilesJob was cancelled
                                    //clearReusableTiles()
                                    return@decode
                                }
                                tile.reusableTile.decodeBitmap(curSourceDecoder, sourceDecoderLock)
                            }
                        }

                        if (!isActive) return@decode
                        displayTiles = pendingTiles

                        val pendingReusableTiles = pendingTiles.map { it.reusableTile }
                        synchronized(reusableTilesLock) {
                            val iterator = reusableTiles.iterator()
                            while (iterator.hasNext()) {
                                val reusableTile = iterator.next()
                                if (pendingReusableTiles.contains(reusableTile)) continue
                                reusableTile.destroy()
                                //TODO: iterator.remove()
                            }
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
