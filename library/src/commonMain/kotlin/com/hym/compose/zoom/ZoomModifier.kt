package com.hym.compose.zoom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.hym.compose.utils.Logger
import com.hym.compose.utils.calculateScaledRect
import com.hym.compose.utils.performFling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
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

@Stable
class ZoomState(
    private val scope: CoroutineScope,
    private val flingSpec: DecayAnimationSpec<Velocity>
) {
    companion object {
        // Priority: Gesture(Zoom/Pan) > DoubleTap > AnimateCenter > Fling
        internal const val SOURCE_GESTURE = 1
        internal const val SOURCE_DOUBLE_TAP = 2
        internal const val SOURCE_ANIMATE_CENTER = 3
        internal const val SOURCE_FLING = 4
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
        val diff = if (isGestureZooming || centerAnimationJob != null || zoomAnimationJob != null) {
            curLayoutBounds.size * abs(2 * curScale)
        } else if (isFillWidth && isFillHeight) {
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

    internal fun onPreZoom(availableZoomChange: Float): Float {
        if (availableZoomChange == 1f) return 1f // Consider if will accept pan change
        val curScale = zoomScale
        return if (curScale > minZoomScale && availableZoomChange < 1f) {
            availableZoomChange.coerceAtLeast(minZoomScale / curScale)
        } else if (curScale < maxZoomScale && availableZoomChange > 1f) {
            availableZoomChange.coerceAtMost(maxZoomScale / curScale)
        } else {
            1f // Will not accept zoom change
        }
    }

    internal fun onPrePan(availablePanChange: Offset): Offset {
        if (availablePanChange == Offset.Zero) return Offset.Zero
        val curScale = zoomScale
        if (curScale <= 1f) return Offset.Zero // Will not accept pan when zoomScale <= 1f
        val curPanOffset = panOffset
        val curPanRestriction = panRestriction
        val curLayoutBounds = layoutBounds
        val scaledContentWidth = contentBounds.width * curScale
        val scaledContentHeight = contentBounds.height * curScale
        return Offset(
            if (scaledContentWidth < curLayoutBounds.width) {
                0f // Will not accept horizontal pan
            } else {
                availablePanChange.x
                    .coerceAtLeast(curPanRestriction.left - curPanOffset.x)
                    .coerceAtMost(curPanRestriction.right - curPanOffset.x)
            },
            if (scaledContentHeight < curLayoutBounds.height) {
                0f // Will not accept vertical pan
            } else {
                availablePanChange.y
                    .coerceAtLeast(curPanRestriction.top - curPanOffset.y)
                    .coerceAtMost(curPanRestriction.bottom - curPanOffset.y)
            }
        )
    }

    internal fun onGesture(
        centroid: Offset, pan: Offset, zoom: Float, source: Int = SOURCE_GESTURE
    ) {
        when (source) {
            SOURCE_GESTURE -> {
                stopZoomAnimation()
            }

            SOURCE_ANIMATE_CENTER -> {
                if (zoomAnimationJob != null) return
            }

            SOURCE_FLING -> {
                if (zoomAnimationJob != null || centerAnimationJob != null) return
            }
        }

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
        if (oldZoomScale != newZoomScale && source == SOURCE_GESTURE) {
            isGestureZooming = true // Set this before reading panRestriction
        }
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

    private var zoomAnimationJob by mutableStateOf<Job?>(null)

    private val zoomAnimation = Animatable(1f)

    private val scaleSpringSpec =
        SpringSpec(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f)

    private fun stopZoomAnimation() {
        zoomAnimationJob?.let {
            it.cancel()
            zoomAnimationJob = null
        }
    }

    /**
     * **NOTE**: There is a bug that detectZoomGestures may swallow double click events initially.
     * I hava no idea how to fix now.
     */
    internal val onDoubleTap: (Offset) -> Unit = { offset ->
        zoomAnimationJob?.cancel()
        val curScale = zoomScale
        zoomAnimationJob = if (abs(curScale - 1f) < 0.05f) {
            val curLayoutBounds = layoutBounds
            val curContentBounds = contentBounds
            val targetScale = doubleClickZoomScale
            val scaledContentBounds = curContentBounds.calculateScaledRect(targetScale, offset)
            val adjustedCentroid = scaledContentBounds.run {
                val boundsCenter = curLayoutBounds.center
                val leftDiff = left - curLayoutBounds.left
                val rightDiff = right - curLayoutBounds.right
                val topDiff = top - curLayoutBounds.top
                val bottomDiff = bottom - curLayoutBounds.bottom
                Offset(
                    if (width <= curLayoutBounds.width) {
                        boundsCenter.x
                    } else if (leftDiff < 0 && rightDiff < 0 || leftDiff > 0 && rightDiff > 0) {
                        if (offset.x < boundsCenter.x) {
                            (curLayoutBounds.left - curContentBounds.left * targetScale) / (1 - targetScale)
                        } else {
                            (curLayoutBounds.right - curContentBounds.right * targetScale) / (1 - targetScale)
                        }
                    } else offset.x,
                    if (height <= curLayoutBounds.height) {
                        boundsCenter.y
                    } else if (topDiff < 0 && bottomDiff < 0 || topDiff > 0 && bottomDiff > 0) {
                        if (offset.y < boundsCenter.y) {
                            (curLayoutBounds.top - curContentBounds.top * targetScale) / (1 - targetScale)
                        } else {
                            (curLayoutBounds.bottom - curContentBounds.bottom * targetScale) / (1 - targetScale)
                        }
                    } else offset.y
                )
            }
            scope.launch {
                val currentJob = currentCoroutineContext().job
                try {
                    stopAnimateToCenter()
                    zoomAnimation.snapTo(curScale)
                    zoomAnimation.animateTo(
                        targetValue = targetScale, animationSpec = scaleSpringSpec
                    ) {
                        if (!currentJob.isActive) return@animateTo
                        onGesture(adjustedCentroid, Offset.Zero, value, SOURCE_DOUBLE_TAP)
                    }
                    if (zoomAnimationJob === currentJob) zoomAnimationJob = null
                } catch (e: CancellationException) {
                    Logger.d(TAG, "doubleClickZoomScale was cancelled")
                } catch (e: Exception) {
                    if (zoomAnimationJob === currentJob) zoomAnimationJob = null
                }
            }
        } else {
            scope.launch {
                animateToDefault()
            }
        }
    }

    private suspend fun animateToDefault() {
        val currentJob = currentCoroutineContext().job
        try {
            stopAnimateToCenter()
            val curScale = zoomScale
            val resetToDefaultCentroid = panOffset / (1f - curScale) + layoutBounds.center
            zoomAnimation.snapTo(curScale)
            zoomAnimation.animateTo(targetValue = 1f, animationSpec = scaleSpringSpec) {
                if (!currentJob.isActive) return@animateTo
                onGesture(resetToDefaultCentroid, Offset.Zero, value, SOURCE_DOUBLE_TAP)
            }
            if (zoomAnimationJob === currentJob) zoomAnimationJob = null
        } catch (e: CancellationException) {
            Logger.d(TAG, "animateToDefault was cancelled")
        } catch (e: Exception) {
            if (zoomAnimationJob === currentJob) zoomAnimationJob = null
        }
    }

    internal var isGestureZooming by mutableStateOf(false)

    private var centerAnimationJob by mutableStateOf<Job?>(null)

    private val centerAnimation = Animatable(Offset.Zero, Offset.Companion.VectorConverter)

    private val motionSpringSpec = SpringSpec<Offset>(
        dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow
    )

    private suspend fun stopAnimateToCenter() {
        centerAnimation.stop()
        centerAnimationJob?.let {
            if (!it.isCancelled && !it.isCompleted) {
                try {
                    it.cancelAndJoin()
                } catch (e: CancellationException) {
                    // ignore
                }
            }
            centerAnimationJob = null
        }
    }

    private suspend fun startAnimateToCenter() {
        centerAnimation.stop()
        centerAnimationJob?.cancel()

        centerAnimationJob = scope.launch {
            delay(300) // start centerAnimation 300ms after ending gesture zoom

            val curLayoutBounds = layoutBounds
            val curPanOffset = panOffset
            val scaledContentBounds = contentBounds.calculateScaledRect(zoomScale)
                .translate(curPanOffset)
            val targetPanOffset = scaledContentBounds.run {
                val leftDiff = left - curLayoutBounds.left
                val rightDiff = right - curLayoutBounds.right
                val topDiff = top - curLayoutBounds.top
                val bottomDiff = bottom - curLayoutBounds.bottom
                Offset(
                    if ((leftDiff < 0 && rightDiff < 0) || (leftDiff > 0 && rightDiff > 0)) {
                        if (width >= curLayoutBounds.width) {
                            // align right
                            if (abs(rightDiff) < abs(leftDiff)) (curLayoutBounds.width - width) / 2
                            // align left
                            else -(curLayoutBounds.width - width) / 2
                        } else 0f // align horizontal center
                    } else if (leftDiff > 0 && rightDiff < 0) {
                        0f // align horizontal center
                    } else { // leftDiff < 0 && rightDiff > 0
                        curPanOffset.x
                    },
                    if ((topDiff < 0 && bottomDiff < 0) || (topDiff > 0 && bottomDiff > 0)) {
                        if (height >= curLayoutBounds.height) {
                            // align bottom
                            if (abs(bottomDiff) < abs(topDiff)) (curLayoutBounds.height - height) / 2
                            // align top
                            else -(curLayoutBounds.height - height) / 2
                        } else 0f // align vertical center
                    } else if (topDiff > 0 && bottomDiff < 0) {
                        0f // align vertical center
                    } else { // topDiff < 0 && bottomDiff > 0
                        curPanOffset.y
                    }
                )
            }
            val currentJob = currentCoroutineContext().job
            if (curPanOffset == targetPanOffset) {
                if (centerAnimationJob === currentJob) centerAnimationJob = null
                return@launch
            }
            try {
                centerAnimation.snapTo(curPanOffset)
                centerAnimation.animateTo(
                    targetValue = targetPanOffset, animationSpec = motionSpringSpec
                ) {
                    if (!currentJob.isActive) return@animateTo
                    panOffset = value
                }
                if (centerAnimationJob === currentJob) centerAnimationJob = null
            } catch (e: CancellationException) {
                Logger.d(TAG, "animateToCenter was cancelled")
            } catch (e: Exception) {
                if (centerAnimationJob === currentJob) centerAnimationJob = null
            }
        }
    }

    internal var flingVelocity by mutableStateOf(Velocity.Zero)

    init {
        scope.launch {
            snapshotFlow { isGestureZooming }
                .collectLatest {
                    if (it) {
                        stopAnimateToCenter()
                    } else if (zoomAnimationJob != null) {
                        Logger.d(TAG, "Don't animateToCenter when zoom animating")
                    } else {
                        startAnimateToCenter()
                    }
                }
        }

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
                onPreZoom = { zoomChange -> zoomState.onPreZoom(zoomChange) },
                onPrePan = { panChange -> zoomState.onPrePan(panChange) },
                onGestureEnd = { velocity ->
                    zoomState.isGestureZooming = false
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
    onPreZoom: (Float) -> Float = { zoomChange -> zoomChange },
    onPrePan: (Offset) -> Offset = { panChange -> panChange },
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
            val pointerUp = event.changes.fastAll { it.changedToUpIgnoreConsumed() }
            if (pointerUp) break
            val canceled = event.changes.fastAny { it.isConsumed }
            if (canceled) break

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

            val preZoomConsume = onPreZoom(zoomChange)
            val prePanConsume = onPrePan(panChange)
            val absX = abs(panChange.x)
            val absY = abs(panChange.y)

            if (preZoomConsume == 1f &&
                ((abs(prePanConsume.x) < 0.5f && absX > absY) ||
                        (abs(prePanConsume.y) < 0.5f && absY > absX))
            ) {
                // Not consume
            } else {
                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (preZoomConsume != 1f ||
                        prePanConsume != Offset.Zero
                    ) {
                        onGesture(centroid, prePanConsume, preZoomConsume)
                    }
                }
                event.changes.fastForEach {
                    if (it.positionChanged()) {
                        it.consume()
                    }
                }
            }
        } while (event.changes.fastAny { it.pressed })

        val velocity = velocityTracker.calculateVelocity(maximumVelocity)
        onGestureEnd?.invoke(velocity)
    }
}
