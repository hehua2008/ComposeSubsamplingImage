package com.hym.compose.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect

/**
 * @author hehua2008
 * @date 2024/4/3
 */
/**
 * **NOTE**: The rect and the scaleCenter must be in the same coordinate system.
 */
fun Rect.calculateScaledRect(scale: Float, scaleCenter: Offset = this.center): Rect {
    return scaleCenter.run {
        Rect(
            x - scale * (x - left),
            y - scale * (y - top),
            x + scale * (right - x),
            y + scale * (bottom - y)
        )
    }
}

val IntRect.Companion.Comparator: Comparator<IntRect>
    get() = object : Comparator<IntRect> {
        override fun compare(a: IntRect, b: IntRect): Int {
            val leftDiff = a.left - b.left
            if (leftDiff != 0) return leftDiff

            val topDiff = a.top - b.top
            if (topDiff != 0) return topDiff

            val rightDiff = a.right - b.right
            if (rightDiff != 0) return rightDiff

            val bottomDiff = a.bottom - b.bottom
            if (bottomDiff != 0) return bottomDiff

            return 0
        }
    }
