package com.hym.compose.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
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
private val DefaultFalseArray = { BooleanArray(4) { false } }

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
    val panRestriction by remember {
        derivedStateOf {
            val diff = layoutBounds.size * abs(zoomScale - 1)
            Rect(-diff.width / 2, -diff.height / 2, diff.width / 2, diff.height / 2)
        }
    }
    val leftTopRightBottomEdgeReached by remember {
        derivedStateOf {
            val curPanOffset = panOffset
            val curPanRestriction = panRestriction
            booleanArrayOf(
                curPanOffset.x <= curPanRestriction.left,
                curPanOffset.y <= curPanRestriction.top,
                curPanOffset.x >= curPanRestriction.right,
                curPanOffset.y >= curPanRestriction.bottom
            )
        }
    }
    val scope = rememberCoroutineScope()

    val onGesture: (centroid: Offset, pan: Offset, zoom: Float, isGesture: Boolean) -> Unit =
        remember {
            { centroid, pan, zoom, isGesture ->
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

                val oldPanOffset = panOffset
                val scaledPanOffset = pan * newZoomScale
                val curPanRestriction = panRestriction
                val newPanOffset = Offset(
                    (oldPanOffset.x + scaledPanOffset.x - adjustOffset.x)
                        .coerceAtLeast(curPanRestriction.left)
                        .coerceAtMost(curPanRestriction.right),
                    (oldPanOffset.y + scaledPanOffset.y - adjustOffset.y)
                        .coerceAtLeast(curPanRestriction.top)
                        .coerceAtMost(curPanRestriction.bottom)
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
                    onGesture(offset, Offset.Zero, value, false)
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
            detectZoomGestures(
                leftTopRightBottomEdgeReached = { leftTopRightBottomEdgeReached.copyOf() },
            ) { centroid, pan, zoom ->
                onGesture(centroid, pan, zoom, true)
            }
        }
        .pointerInput(onClick, onLongClick, onDoubleTap) {
            detectTapGestures(onTap = onClick, onLongPress = onLongClick, onDoubleTap = onDoubleTap)
        }
}

suspend fun PointerInputScope.detectZoomGestures(
    leftTopRightBottomEdgeReached: () -> BooleanArray = DefaultFalseArray,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                val edgeReached = leftTopRightBottomEdgeReached()
                val cantPanX =
                    (edgeReached[0] && panChange.x < 0) || (edgeReached[2] && panChange.x > 0)
                val cantPanY =
                    (edgeReached[1] && panChange.y < 0) || (edgeReached[3] && panChange.y > 0)
                val absX = abs(panChange.x)
                val absY = abs(panChange.y)

                if (zoomChange <= 1f && ((cantPanX && absX > absY) || (cantPanY && absY > absX))) {
                    // Not consume
                } else {
                    if (pastTouchSlop) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val adjustedPanChange = Offset(
                            if (cantPanX) 0f else panChange.x,
                            if (cantPanY) 0f else panChange.y
                        )
                        if (zoomChange != 1f ||
                            adjustedPanChange != Offset.Zero
                        ) {
                            onGesture(centroid, adjustedPanChange, zoomChange)
                        }
                    }
                    event.changes.fastForEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}
