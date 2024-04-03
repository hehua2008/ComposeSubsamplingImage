package com.hym.compose.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

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
