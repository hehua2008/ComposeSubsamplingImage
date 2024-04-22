package com.hym.compose.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * @author hehua2008
 * @date 2024/4/3
 */
fun Size.toOffset(): Offset = Offset(width, height)

fun Size.roundToIntSize(): IntSize {
    return IntSize(width.roundToInt(), height.roundToInt())
}
