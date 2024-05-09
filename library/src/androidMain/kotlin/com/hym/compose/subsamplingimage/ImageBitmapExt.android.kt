package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.hym.compose.utils.Logger
import okio.Path

/**
 * @author hehua2008
 * @date 2024/4/14
 */
private const val TAG = "ImageBitmapExt"

actual fun ImageBitmap.recycle() {
    try {
        asAndroidBitmap().recycle()
    } catch (e: UnsupportedOperationException) {
        Logger.w(TAG, "recycle $this failed", e)
    }
}

actual val DefaultImageBitmapRegionDecoderFactory: (Path) -> ImageBitmapRegionDecoder<*>? =
    { path: Path ->
        AndroidBitmapRegionDecoder.newInstance(path)
    }
