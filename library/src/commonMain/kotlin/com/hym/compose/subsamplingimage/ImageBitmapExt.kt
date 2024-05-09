package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import okio.Path

/**
 * @author hehua2008
 * @date 2024/4/14
 */
expect fun ImageBitmap.recycle()

expect val DefaultImageBitmapRegionDecoderFactory: (Path) -> ImageBitmapRegionDecoder<*>?
