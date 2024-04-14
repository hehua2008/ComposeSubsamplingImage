package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.hym.compose.utils.Logger
import com.hym.compose.utils.SourceMarker

/**
 * @author hehua2008
 * @date 2024/4/14
 */
private const val TAG = "ImageBitmapExt"

actual fun ImageBitmap.recycle() {
    try {
        this.asSkiaBitmap().run {
            if (isClosed) return
            close()
        }
    } catch (e: Exception) {
        Logger.w(TAG, "recycle $this failed", e)
    }
}

actual val DefaultImageBitmapRegionDecoderFactory: (SourceMarker) -> ImageBitmapRegionDecoder<*>? =
    { sourceMarker: SourceMarker ->
        NativeImageBitmapRegionDecoder.newInstance(sourceMarker)
    }
