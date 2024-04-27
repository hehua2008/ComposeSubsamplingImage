import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.annotation.ExperimentalCoilApi
import org.jetbrains.skia.impl.use

/**
 * @author hehua2008
 * @date 2024/4/26
 */
actual fun ByteArray.decodeToImageBitmap(): ImageBitmap {
    org.jetbrains.skia.Image.makeFromEncoded(this).use { image ->
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        return bitmap.asComposeImageBitmap()
    }
}

@OptIn(ExperimentalCoilApi::class)
actual fun coil3.Image.toImageBitmap(): ImageBitmap {
    return this.toBitmap().asComposeImageBitmap()
}
