package com.hym.compose.subsamplingimage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.IntSize
import com.hym.compose.zoom.ZoomState
import com.hym.compose.zoom.zoom

/**
 * @author hehua2008
 * @date 2024/4/11
 */
@Composable
fun SubsamplingImage(
    zoomState: ZoomState,
    sourceDecoderProvider: suspend () -> ImageBitmapRegionDecoder<*>?,
    previewProvider: (suspend () -> ImageBitmap)? = null,
    sourceIntSize: IntSize = IntSize.Zero,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    onLoadEvent: ((SubsamplingState.LoadEvent) -> Unit)? = null
) {
    val subsamplingState = rememberSubsamplingState(
        zoomState = zoomState,
        sourceDecoderProvider = sourceDecoderProvider,
        previewProvider = previewProvider,
        sourceIntSize = sourceIntSize,
        onLoadEvent = onLoadEvent
    )

    Layout(
        measurePolicy = EmptyMeasurePolicy,
        modifier = modifier
            .clipToBounds()
            .zoom(zoomState)
            .subsampling(
                subsamplingState = subsamplingState,
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter
            )
    )
}

private val EmptyMeasurePolicy = MeasurePolicy { _, constraints ->
    layout(constraints.minWidth, constraints.minHeight) {}
}
