package com.hym.compose.subsamplingimage

import android.graphics.Bitmap
import android.os.Build
import com.hym.compose.utils.Logger
import androidx.compose.ui.graphics.ImageBitmapConfig

/**
 * @author hehua2008
 * @date 2024/4/12
 */
private const val TAG = "ImageBitmapConfigExt"

fun ImageBitmapConfig?.toAndroidBitmapConfig(): Bitmap.Config {
    return when (this) {
        ImageBitmapConfig.Alpha8 -> Bitmap.Config.ALPHA_8

        ImageBitmapConfig.Rgb565 -> Bitmap.Config.RGB_565

        ImageBitmapConfig.F16 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap.Config.RGBA_F16
        } else {
            Logger.w(
                TAG,
                "Not support RGBA_F16 on SDK:${Build.VERSION.SDK_INT}, use ARGB_8888 instead",
                Exception()
            )
            Bitmap.Config.ARGB_8888
        }

        ImageBitmapConfig.Gpu -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap.Config.HARDWARE
        } else {
            Logger.w(
                TAG,
                "Not support HARDWARE on SDK:${Build.VERSION.SDK_INT}, use ARGB_8888 instead",
                Exception()
            )
            Bitmap.Config.ARGB_8888
        }

        else -> Bitmap.Config.ARGB_8888
    }
}
