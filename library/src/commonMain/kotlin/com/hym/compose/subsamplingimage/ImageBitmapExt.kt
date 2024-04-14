package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import com.hym.compose.utils.SourceMarker

/**
 * @author hehua2008
 * @date 2024/4/14
 */
expect fun ImageBitmap.recycle()

expect val DefaultImageBitmapRegionDecoderFactory: (SourceMarker) -> ImageBitmapRegionDecoder<*>?
