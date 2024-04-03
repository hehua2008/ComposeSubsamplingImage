package com.hym.compose.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * @author hehua2008
 * @date 2024/4/3
 */
fun Size.toOffset(): Offset = Offset(width, height)
