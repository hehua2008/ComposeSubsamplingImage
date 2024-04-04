package com.hym.compose.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * @author hehua2008
 * @date 2024/4/3
 */
private const val TAG = "ZoomModifier"

private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 4f
private const val DOUBLE_CLICK_ZOOM_SCALE = 2f

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
    maxZoomScale: Float = MAX_ZOOM_SCALE,
    doubleClickZoomScale: Float = DOUBLE_CLICK_ZOOM_SCALE,
    onClick: ((Offset) -> Unit)? = null,
    onLongClick: ((Offset) -> Unit)? = null
): Modifier {
    require(0f < minZoomScale && minZoomScale <= doubleClickZoomScale && doubleClickZoomScale <= maxZoomScale) {
        "Invalid arguments! Please check: 0f < minZoomScale=$minZoomScale <= doubleClickZoomScale=$doubleClickZoomScale <= maxZoomScale=$maxZoomScale"
    }
    val updatedMinZoomScale by rememberUpdatedState(minZoomScale)
    val updatedMaxZoomScale by rememberUpdatedState(maxZoomScale)
    val updatedDoubleClickZoomScale by rememberUpdatedState(doubleClickZoomScale)
    var layoutBounds by remember { mutableStateOf(Rect.Zero) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val zoomAnimation = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, isGesture: Boolean) -> Unit =
        remember {
            { centroid, pan, zoom, rotation, isGesture ->
                val boundsCenter = layoutBounds.center
                val oldZoomScale = zoomScale
                val beforeScaledCentroid = (centroid - boundsCenter) * oldZoomScale

                val newZoomScale = (if (isGesture) (oldZoomScale * zoom) else zoom)
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

    val onDoubleTap: (Offset) -> Unit = remember {
        { offset ->
            val curScale = zoomScale
            val targetZoomScale = if (curScale == 1f) updatedDoubleClickZoomScale else 1f
            scope.launch {
                zoomAnimation.snapTo(curScale)
                zoomAnimation.animateTo(targetZoomScale) {
                    onGesture(offset, Offset.Zero, value, 0f, false)
                }
            }
        }
    }

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
        .pointerInput(onGesture) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                onGesture(centroid, pan, zoom, rotation, true)
            }
        }
        .pointerInput(onClick, onLongClick, onDoubleTap) {
            detectTapGestures(onTap = onClick, onLongPress = onLongClick, onDoubleTap = onDoubleTap)
        }
}
