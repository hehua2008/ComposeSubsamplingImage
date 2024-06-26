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
import androidx.compose.ui.unit.toIntRect
import androidx.compose.ui.util.fastForEach
import com.hym.compose.utils.Comparator
import com.hym.compose.utils.ImmutableEqualityList
import com.hym.compose.utils.Logger
import com.hym.compose.utils.calculateScaledRect
import com.hym.compose.utils.emptyImmutableEqualityList
import com.hym.compose.utils.mutableEqualityListOf
import com.hym.compose.utils.roundToIntSize
import com.hym.compose.zoom.ZoomState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * @author hehua2008
 * @date 2024/4/13
 */
@Stable
@Composable
fun rememberSubsamplingState(
    zoomState: ZoomState,
    sourceDecoderProvider: suspend () -> ImageBitmapRegionDecoder<*>?,
    previewProvider: (suspend () -> ImageBitmap)? = null,
    sourceIntSize: IntSize = IntSize.Zero, // Not as key
    onLoadEvent: ((SubsamplingState.LoadEvent) -> Unit)? = null // Not as key
): SubsamplingState {
    val scope = rememberCoroutineScope() // Not as key
    val subsamplingState = remember(zoomState, sourceDecoderProvider, previewProvider) {
        SubsamplingState(
            sourceIntSize = sourceIntSize,
            zoomState = zoomState,
            sourceDecoderProvider = sourceDecoderProvider,
            previewProvider = previewProvider,
            onLoadEvent = onLoadEvent,
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
    private val sourceDecoderProvider: suspend () -> ImageBitmapRegionDecoder<*>?,
    private val previewProvider: (suspend () -> ImageBitmap)? = null,
    private val onLoadEvent: ((LoadEvent) -> Unit)? = null,
    private val scope: CoroutineScope
) : RememberObserver {
    companion object {
        private const val TAG = "SubsamplingState"

        private const val SLICE_THRESHOLD = 640
        private const val DISPLAY_SOURCE_THRESHOLD = 1f
        private const val TILE_OVERLAP_SCALE = 1f

        private const val PREVIEW_DEFAULT_SIZE = 1080 * 1920
    }

    sealed interface LoadEvent {
        data object Loading : LoadEvent

        data object PreviewLoaded : LoadEvent

        data object SourceLoaded : LoadEvent

        data class PreviewLoadError(val e: Throwable) : LoadEvent

        data class SourceLoadError(val e: Throwable) : LoadEvent

        data class TileLoadError(val e: Throwable) : LoadEvent

        data class DisposePreview(val preview: ImageBitmap) : LoadEvent

        data object Destroyed : LoadEvent
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

        fun recycle() {
            synchronized(lock) {
                imageBitmap?.recycle()
                imageBitmap = null
            }
        }

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
                val bitmap = try {
                    decoder.decodeRegion(rect = sourceRect, sampleSize = sampleSize)
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to decodeRegion($sourceRect) from source", e)
                    return false
                }
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

    init {
        if (sourceIntSize == IntSize.Zero && previewProvider != null) {
            Logger.w(
                TAG, "If previewProvider is provided, it‘s best to provide sourceIntSize too! " +
                        "Otherwise, the image cannot be displayed until the source image is loaded."
            )
        }
        onLoadEvent?.invoke(LoadEvent.Loading)
    }

    private var sourceDecoder by mutableStateOf<ImageBitmapRegionDecoder<*>?>(null)
    private var preview by mutableStateOf<ImageBitmap?>(null)
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
            val decoder = try {
                withContext(Dispatchers.IO) {
                    sourceDecoderProvider()
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                onLoadEvent?.invoke(LoadEvent.SourceLoadError(e))
                return@launch
            }

            if (decoder == null) {
                val msg = "Failed to create ImageBitmapRegionDecoder"
                Logger.e(TAG, msg)
                onLoadEvent?.invoke(LoadEvent.SourceLoadError(Exception(msg)))
                return@launch
            }
            if (!isActive) {
                decoder.close()
                return@launch
            }
            onLoadEvent?.invoke(LoadEvent.SourceLoaded)
            if (decoder.width != 0 && decoder.height != 0) {
                sourceSize = IntSize(decoder.width, decoder.height)
                zoomState.contentAspectRatio = decoder.width / decoder.height.toFloat()
            }
            sourceDecoder = decoder

            if (previewProvider != null) return@launch
            loadPreviewJob = scope.launch decodePreview@{
                val imageBitmap = withContext(Dispatchers.IO) {
                    val sourceRect = if (decoder.width != 0 && decoder.height != 0) {
                        IntRect(0, 0, decoder.width, decoder.height)
                    } else sourceSize.toIntRect()
                    val sampleSize =
                        sqrt(sourceRect.width * sourceRect.height / PREVIEW_DEFAULT_SIZE.toDouble())
                            .let { times ->
                                var sampleSize = 1
                                while (times >= sampleSize) {
                                    sampleSize *= 2
                                }
                                sampleSize
                            }
                    synchronized(sourceDecoderLock) {
                        try {
                            decoder.decodeRegion(rect = sourceRect, sampleSize = sampleSize)
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to decodeRegion from source to create preview", e)
                            return@withContext null
                        }
                    }.also {
                        it ?: Logger.e(TAG, "Failed to decodeRegion from source to create preview")
                    }
                } ?: return@decodePreview
                if (!isActive) {
                    imageBitmap.recycle()
                    return@decodePreview
                }
                preview = imageBitmap
            }
        }

        previewProvider?.let {
            loadPreviewJob = scope.launch {
                val imageBitmap = try {
                    withContext(Dispatchers.IO) {
                        it()
                    }
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Throwable) {
                    onLoadEvent?.invoke(LoadEvent.PreviewLoadError(e))
                    return@launch
                }

                if (!isActive) {
                    onLoadEvent?.invoke(LoadEvent.DisposePreview(imageBitmap))
                    return@launch
                }
                preview = imageBitmap
                onLoadEvent?.invoke(LoadEvent.PreviewLoaded)
                val decoder = sourceDecoder
                if ((decoder == null || decoder.width == 0 || decoder.height == 0) &&
                    (imageBitmap.width != 0 && imageBitmap.height != 0)
                ) {
                    zoomState.contentAspectRatio = imageBitmap.width / imageBitmap.height.toFloat()
                }
            }
        }

        updateTilesJob = scope.launch {
            var decodeTilesJob: Job? = null

            snapshotFlow { pendingTileList }
                .collectLatest { pendingTiles ->
                    decodeTilesJob?.cancelAndJoin()

                    decodeTilesJob = launch(Dispatchers.IO) decode@{
                        val curSourceDecoder = sourceDecoder
                        if (curSourceDecoder == null) {
                            // Update to no tile
                        } else {
                            val displayPendingReusableTiles = mutableListOf<ReusableTile>()
                            displayTiles.fastForEach {
                                if (it is SnapshotTile) {
                                    displayPendingReusableTiles.add(it.reusableTile)
                                }
                            }
                            pendingTiles.fastForEach {
                                displayPendingReusableTiles.add(it.reusableTile)
                            }
                            synchronized(reusableTilesLock) {
                                val iterator = reusableTiles.iterator()
                                while (iterator.hasNext()) {
                                    val reusableTile = iterator.next()
                                    if (displayPendingReusableTiles.contains(reusableTile)) continue
                                    reusableTile.recycle()
                                    //TODO: iterator.remove()
                                }
                            }

                            pendingTiles.fastForEach { tile ->
                                if (!isActive) { // decodeTilesJob or updateTilesJob was cancelled
                                    //clearReusableTiles()
                                    return@decode
                                }
                                try {
                                    if (!tile.reusableTile.decodeBitmap(
                                            curSourceDecoder,
                                            sourceDecoderLock
                                        )
                                    ) {
                                        onLoadEvent?.invoke(LoadEvent.TileLoadError(Exception("Get null")))
                                    }
                                } catch (e: Throwable) {
                                    onLoadEvent?.invoke(LoadEvent.TileLoadError(e))
                                }
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
                                reusableTile.recycle()
                                //TODO: iterator.remove()
                            }
                        }
                    }
                }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onForgotten() {
        loadSourceJob?.cancel()
        sourceDecoder?.let {
            GlobalScope.launch(Dispatchers.IO) {
                synchronized(sourceDecoderLock) {
                    try {
                        it.close()
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to close sourceDecoder", e)
                    }
                }
            }
        }

        loadPreviewJob?.cancel()
        preview?.let {
            // Try to fix Error, cannot access an invalid/free'd bitmap here!
            preview = null
            if (previewProvider != null) {
                onLoadEvent?.invoke(LoadEvent.DisposePreview(it))
            } else {
                it.recycle()
            }
        }

        updateTilesJob?.cancel()
        // Try to fix Error, cannot access an invalid/free'd bitmap here!
        displayTiles = emptyImmutableEqualityList()
        clearReusableTiles()

        onLoadEvent?.invoke(LoadEvent.Destroyed)
    }

    override fun onAbandoned() {
        onForgotten()
    }
}
