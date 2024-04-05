package com.hym.compose.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.hym.compose.utils.performFling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
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

@Stable
class ZoomState(
    private val scope: CoroutineScope,
    private val flingSpec: DecayAnimationSpec<Velocity>
) {
    companion object {
        internal const val SOURCE_GESTURE = 1
        internal const val SOURCE_DOUBLE_TAP = 2
        internal const val SOURCE_FLING = 3
    }

    var minZoomScale: Float = MIN_ZOOM_SCALE
        private set
    var maxZoomScale: Float = MIN_ZOOM_SCALE // MAX_ZOOM_SCALE
        private set
    var doubleClickZoomScale: Float = DOUBLE_CLICK_ZOOM_SCALE
        private set

    fun setScaleLimits(
        minZoomScale: Float = this.minZoomScale,
        maxZoomScale: Float = this.maxZoomScale,
        doubleClickZoomScale: Float = this.doubleClickZoomScale
    ): ZoomState {
        require(minZoomScale <= this.minZoomScale) {
            "The new minZoomScale($minZoomScale) must <= the old minZoomScale(${this.minZoomScale}"
        }
        require(maxZoomScale >= this.maxZoomScale) {
            "The new maxZoomScale($maxZoomScale) must >= the old maxZoomScale(${this.maxZoomScale}"
        }
        require(doubleClickZoomScale != 1f) {
            "The new doubleClickZoomScale($doubleClickZoomScale) must not be 1f"
        }
        require(0f < minZoomScale && minZoomScale <= doubleClickZoomScale && doubleClickZoomScale <= maxZoomScale) {
            "Invalid arguments! Please check: 0f < minZoomScale=$minZoomScale <= doubleClickZoomScale=$doubleClickZoomScale <= maxZoomScale=$maxZoomScale"
        }

        this.minZoomScale = minZoomScale
        this.maxZoomScale = maxZoomScale
        this.doubleClickZoomScale = doubleClickZoomScale
        return this
    }

    var layoutBounds by mutableStateOf(Rect.Zero)
        internal set

    var contentAspectRatio by mutableFloatStateOf(0f)
        internal set

    val contentBounds by derivedStateOf {
        val curContentAspectRatio = contentAspectRatio
        val curLayoutBounds = layoutBounds
        if (curContentAspectRatio <= 0f || curLayoutBounds.isEmpty) {
            curLayoutBounds
        } else {
            val assumeContentWidth = curLayoutBounds.height * curContentAspectRatio
            if (curLayoutBounds.width < assumeContentWidth) {
                val contentHeight = curLayoutBounds.width / curContentAspectRatio
                val diffHeight = curLayoutBounds.height - contentHeight
                curLayoutBounds.run {
                    copy(top = top + diffHeight / 2, bottom = bottom - diffHeight / 2)
                }
            } else {
                val diffWidth = curLayoutBounds.width - assumeContentWidth
                curLayoutBounds.run {
                    copy(left = left + diffWidth / 2, right = right - diffWidth / 2)
                }
            }
        }
    }

    var panOffset by mutableStateOf(Offset.Zero)
        internal set

    var zoomScale by mutableFloatStateOf(1f)
        internal set

    private val panRestriction by derivedStateOf {
        val curLayoutBounds = layoutBounds
        if (curLayoutBounds.isEmpty) return@derivedStateOf Rect.Zero
        val curScale = zoomScale
        val scaledContentWidth = contentBounds.width * curScale
        val scaledContentHeight = contentBounds.height * curScale
        val isFillWidth = scaledContentWidth >= curLayoutBounds.width
        val isFillHeight = scaledContentHeight >= curLayoutBounds.height
        val diff = if (isFillWidth && isFillHeight) {
            if (curLayoutBounds.width / curLayoutBounds.height < scaledContentWidth / scaledContentHeight) {
                Size(
                    abs(curLayoutBounds.width * (curScale - 1)),
                    abs(curLayoutBounds.height - scaledContentHeight)
                )
            } else {
                Size(
                    abs(curLayoutBounds.width - scaledContentWidth),
                    abs(curLayoutBounds.height * (curScale - 1))
                )
            }
        } else if (isFillWidth) { // isFillHeight.not()
            Size(curLayoutBounds.width * abs(curScale - 1), 0f)
        } else if (isFillHeight) { // isFillWidth.not()
            Size(0f, curLayoutBounds.height * abs(curScale - 1))
        } else { // isFillWidth.not() && isFillHeight.not()
            Size.Zero
        }
        Rect(-diff.width / 2, -diff.height / 2, diff.width / 2, diff.height / 2)
    }

    internal val leftTopRightBottomEdgeReached by derivedStateOf {
        val curPanOffset = panOffset
        val curPanRestriction = panRestriction
        val curLayoutBounds = layoutBounds
        val curScale = zoomScale
        val scaledContentWidth = contentBounds.width * curScale
        val scaledContentHeight = contentBounds.height * curScale
        val isFillWidth = scaledContentWidth >= curLayoutBounds.width
        val isFillHeight = scaledContentHeight >= curLayoutBounds.height
        booleanArrayOf(
            curPanOffset.x <= curPanRestriction.left && isFillWidth,
            curPanOffset.y <= curPanRestriction.top && isFillHeight,
            curPanOffset.x >= curPanRestriction.right && isFillWidth,
            curPanOffset.y >= curPanRestriction.bottom && isFillHeight
        )
    }

    internal fun onGesture(
        centroid: Offset, pan: Offset, zoom: Float, source: Int = SOURCE_GESTURE
    ) {
        val boundsCenter = layoutBounds.center
        val oldZoomScale = zoomScale
        val beforeScaledCentroid = (centroid - boundsCenter) * oldZoomScale

        val newZoomScale = (if (source == SOURCE_DOUBLE_TAP) zoom else (oldZoomScale * zoom))
            .coerceAtLeast(minZoomScale)
            .coerceAtMost(maxZoomScale)
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

    private val zoomAnimation = Animatable(1f)

    /**
     * **NOTE**: There is a bug that detectZoomGestures may swallow double click events initially.
     * I hava no idea how to fix now.
     */
    internal val onDoubleTap: (Offset) -> Unit = { offset ->
        val curScale = zoomScale
        if (abs(curScale - 1f) < 0.05f) {
            scope.launch {
                zoomAnimation.snapTo(curScale)
                zoomAnimation.animateTo(doubleClickZoomScale) {
                    onGesture(offset, Offset.Zero, value, SOURCE_DOUBLE_TAP)
                }
            }
        } else {
            scope.launch {
                animateToDefault()
            }
        }
    }

    private suspend fun animateToDefault() {
        val curScale = zoomScale
        val resetToDefaultCentroid = panOffset / (1f - curScale) + layoutBounds.center
        zoomAnimation.snapTo(curScale)
        zoomAnimation.animateTo(1f) {
            onGesture(resetToDefaultCentroid, Offset.Zero, value, SOURCE_DOUBLE_TAP)
        }
    }

    internal var flingVelocity by mutableStateOf(Velocity.Zero)

    init {
        scope.launch {
            snapshotFlow { flingVelocity }
                .collectLatest { velocity ->
                    if (velocity == Velocity.Zero) return@collectLatest
                    performFling(velocity, flingSpec) { offset ->
                        if (abs(offset.x) > 1f || abs(offset.y) > 1f) {
                            onGesture(Offset.Zero, offset, 1f, SOURCE_FLING)
                            offset
                        } else Offset.Zero
                    }
                    // Reset flingVelocity to zero because snapshotFlow will auto distinctUntilChanged
                    flingVelocity = Velocity.Zero
                }
        }
    }
}

/**
 * Creates a [ZoomState] that is remembered across compositions.
 *
 * Changes to the value of contentAspectRatio, minZoomScale, maxZoomScale and doubleClickZoomScale
 * will take effect and will **NOT** result in the state being recreated.
 */
@Stable
@Composable
fun rememberZoomState(
    contentAspectRatio: Float = 0f,
    minZoomScale: Float = MIN_ZOOM_SCALE,
    maxZoomScale: Float = MAX_ZOOM_SCALE,
    doubleClickZoomScale: Float = DOUBLE_CLICK_ZOOM_SCALE
): ZoomState {
    val scope = rememberCoroutineScope()
    val flingSpec = rememberSplineBasedDecay<Velocity>()
    val zoomState = remember {
        ZoomState(scope, flingSpec)
    }
    LaunchedEffect(contentAspectRatio) {
        zoomState.contentAspectRatio = contentAspectRatio
    }
    LaunchedEffect(minZoomScale, maxZoomScale, doubleClickZoomScale) {
        zoomState.setScaleLimits(minZoomScale, maxZoomScale, doubleClickZoomScale)
    }
    return zoomState
}

@Stable
fun Modifier.zoom(
    zoomState: ZoomState,
    onClick: ((Offset) -> Unit)? = null,
    onLongClick: ((Offset) -> Unit)? = null
): Modifier {
    return this
        .onGloballyPositioned {
            zoomState.layoutBounds = it.size
                .toSize()
                .toRect()
        }
        .clipToBounds() // set clip = true in graphicsLayer block have no effect, I have no idea why
        .graphicsLayer {
            val curScale = zoomState.zoomScale
            scaleX = curScale
            scaleY = curScale
            val curPanOffset = zoomState.panOffset
            translationX = curPanOffset.x
            translationY = curPanOffset.y
        }
        .pointerInput(zoomState) {
            detectZoomGestures(
                leftTopRightBottomEdgeReached = { zoomState.leftTopRightBottomEdgeReached.copyOf() },
                onGestureEnd = { velocity ->
                    zoomState.flingVelocity = velocity
                }
            ) { centroid, pan, zoom ->
                zoomState.onGesture(centroid, pan, zoom)
            }
        }
        .pointerInput(onClick, onLongClick, zoomState) {
            detectTapGestures(
                onTap = onClick,
                onLongPress = onLongClick,
                onDoubleTap = zoomState.onDoubleTap
            )
        }
}

suspend fun PointerInputScope.detectZoomGestures(
    leftTopRightBottomEdgeReached: () -> BooleanArray = DefaultFalseArray,
    onGestureEnd: ((Velocity) -> Unit)? = null,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    val maximumVelocity =
        Velocity(viewConfiguration.maximumFlingVelocity, viewConfiguration.maximumFlingVelocity)
    val velocityTracker = VelocityTracker()

    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        velocityTracker.resetTracking()

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                event.changes.fastForEach {
                    velocityTracker.addPointerInputChange(it)
                }

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

        val velocity = velocityTracker.calculateVelocity(maximumVelocity)
        onGestureEnd?.invoke(velocity)
    }
}
