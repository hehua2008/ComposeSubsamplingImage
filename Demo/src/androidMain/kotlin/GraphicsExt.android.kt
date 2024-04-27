import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.annotation.ExperimentalCoilApi

/**
 * @author hehua2008
 * @date 2024/4/26
 */
actual fun ByteArray.decodeToImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
    return bitmap.asImageBitmap()
}

@OptIn(ExperimentalCoilApi::class)
actual fun coil3.Image.toImageBitmap(): ImageBitmap {
    if (this is BitmapImage) {
        return this.bitmap.asImageBitmap()
    } else {
        throw UnsupportedOperationException("$this")
    }
}
