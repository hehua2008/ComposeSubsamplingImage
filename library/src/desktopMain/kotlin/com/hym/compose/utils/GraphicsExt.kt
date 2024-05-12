package com.hym.compose.utils

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import kotlin.math.roundToInt

/**
 * @author hehua2008
 * @date 2024/5/12
 */
fun Image.toBitmap(
    scale: Float,
    rect: Rect = Rect(0f, 0f, width.toFloat(), height.toFloat())
): Bitmap {
    val dstRect = Rect(0f, 0f, rect.width, rect.height)
    val bitmap = Bitmap()
    bitmap.allocPixels(
        ImageInfo.makeN32(
            (rect.width * scale).roundToInt(),
            (rect.height * scale).roundToInt(),
            ColorAlphaType.PREMUL
        )
    )
    val canvas = org.jetbrains.skia.Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.drawImageRect(this, rect, dstRect)
    bitmap.setImmutable()
    return bitmap
}
