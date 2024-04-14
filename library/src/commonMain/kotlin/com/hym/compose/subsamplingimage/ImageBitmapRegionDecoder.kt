package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.unit.IntRect

/**
 * @author hehua2008
 * @date 2024/4/12
 */
@OptIn(ExperimentalStdlibApi::class)
interface ImageBitmapRegionDecoder<T : ImageBitmapRegionDecoder<T>> : AutoCloseable {
    fun decodeRegion(
        rect: IntRect,
        sampleSize: Int = 1,
        config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    ): ImageBitmap?

    /** Returns the original image's width */
    val width: Int

    /** Returns the original image's height */
    val height: Int
}
