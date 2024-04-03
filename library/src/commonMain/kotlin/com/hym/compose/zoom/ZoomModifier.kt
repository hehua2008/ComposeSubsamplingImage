package com.hym.compose.zoom

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import kotlin.math.abs

/**
 * @author hehua2008
 * @date 2024/4/3
 */
private const val TAG = "ZoomModifier"

private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 4f

/**
 * **NOTE**: Composable function modifiers are never skipped
 *
 * Composable factory modifiers are never skipped because composable functions that have return
 * values cannot be skipped. This means your modifier function will be called on every
 * recomposition, which may be expensive if it recomposes frequently.
 */
@Composable
fun Modifier.zoom(
    minZoomScale: Float = MIN_ZOOM_SCALE,
    maxZoomScale: Float = MAX_ZOOM_SCALE
): Modifier {
    require(0f < minZoomScale && minZoomScale <= maxZoomScale) {
        "Invalid arguments! Please check: 0f < minZoomScale=$minZoomScale <= maxZoomScale=$maxZoomScale"
    }
    val updatedMinZoomScale by rememberUpdatedState(minZoomScale)
    val updatedMaxZoomScale by rememberUpdatedState(maxZoomScale)
    var layoutBounds by remember { mutableStateOf(Rect.Zero) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    return this
        .onGloballyPositioned {
            layoutBounds = it.size
                .toSize()
                .toRect()
        }
        .clipToBounds() // set clip = true in graphicsLayer block have no effect, I have no idea why
        .graphicsLayer {
            val curScale = zoomScale
            scaleX = curScale
            scaleY = curScale
            val curPanOffset = panOffset
            translationX = curPanOffset.x
            translationY = curPanOffset.y
        }
        .pointerInput(null) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                val boundsCenter = layoutBounds.center
                val oldZoomScale = zoomScale
                val beforeScaledCentroid = (centroid - boundsCenter) * oldZoomScale

                val newZoomScale = (oldZoomScale * zoom)
                    .coerceAtLeast(updatedMinZoomScale)
                    .coerceAtMost(updatedMaxZoomScale)
                if (oldZoomScale != newZoomScale) {
                    zoomScale = newZoomScale
                }

                val afterScaledCentroid = (centroid - boundsCenter) * newZoomScale
                val adjustOffset = afterScaledCentroid - beforeScaledCentroid

                val curLayoutBounds = layoutBounds
                val maxOffsetX = abs(curLayoutBounds.width * (newZoomScale - 1)) / 2
                val maxOffsetY = abs(curLayoutBounds.height * (newZoomScale - 1)) / 2
                val oldPanOffset = panOffset
                val scaledPanOffset = pan * newZoomScale
                val newPanOffset = Offset(
                    (oldPanOffset.x + scaledPanOffset.x - adjustOffset.x)
                        .coerceAtLeast(-maxOffsetX)
                        .coerceAtMost(maxOffsetX),
                    (oldPanOffset.y + scaledPanOffset.y - adjustOffset.y)
                        .coerceAtLeast(-maxOffsetY)
                        .coerceAtMost(maxOffsetY)
                )

                if (oldPanOffset != newPanOffset) {
                    panOffset = newPanOffset
                }
            }
        }
}
