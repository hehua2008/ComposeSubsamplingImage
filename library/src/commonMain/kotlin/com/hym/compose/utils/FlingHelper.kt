package com.hym.compose.utils

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * @author hehua2008
 * @date 2024/4/4
 */
private val FlingHelperMotionDurationScale = object : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/**
 * A type converter that converts a [Velocity] to a [AnimationVector2D], and vice versa.
 */
val VelocityToVector: TwoWayConverter<Velocity, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.x, it.y) },
        convertFromVector = { Velocity(it.v1, it.v2) }
    )

suspend fun performFling(
    initialVelocity: Float,
    flingDecay: DecayAnimationSpec<Float>,
    motionDurationScale: MotionDurationScale = FlingHelperMotionDurationScale,
    scrollBy: (Float) -> Float
): Float {
    // come up with the better threshold, but we need it since spline curve gives us NaNs
    return withContext(motionDurationScale) {
        if (abs(initialVelocity) > 1f) {
            var velocityLeft = initialVelocity
            var lastValue = 0f
            val animationState = AnimationState(
                initialValue = 0f,
                initialVelocity = initialVelocity,
            )
            try {
                animationState.animateDecay(flingDecay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    // avoid rounding errors and stop if anything is unconsumed
                    if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
                }
            } catch (exception: CancellationException) {
                velocityLeft = animationState.velocity
            }
            velocityLeft
        } else {
            initialVelocity
        }
    }
}

suspend fun performFling(
    initialVelocity: Velocity,
    flingDecay: DecayAnimationSpec<Velocity>,
    motionDurationScale: MotionDurationScale = FlingHelperMotionDurationScale,
    scrollBy: (Offset) -> Offset
): Velocity {
    // come up with the better threshold, but we need it since spline curve gives us NaNs
    return withContext(motionDurationScale) {
        if (abs(initialVelocity.x) > 1f || abs(initialVelocity.y) > 1f) {
            var velocityLeft = initialVelocity
            var lastValue = Velocity.Zero
            val animationState = AnimationState(
                typeConverter = VelocityToVector,
                initialValue = Velocity.Zero,
                initialVelocity = initialVelocity,
            )
            try {
                animationState.animateDecay(flingDecay) {
                    val delta = (value - lastValue).run { Offset(x, y) }
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    // avoid rounding errors and stop if anything is unconsumed
                    if (abs(delta.x - consumed.x) > 0.5f && abs(delta.y - consumed.y) > 0.5f) this.cancelAnimation()
                }
            } catch (exception: CancellationException) {
                velocityLeft = animationState.velocity
            }
            velocityLeft
        } else {
            initialVelocity
        }
    }
}
