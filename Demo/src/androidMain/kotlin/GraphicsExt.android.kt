import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.annotation.ExperimentalCoilApi

/**
 * @author hehua2008
 * @date 2024/4/26
 */
@OptIn(ExperimentalCoilApi::class)
actual object EmptyCoilImage : coil3.Image {
    override val size: Long = 0
    override val width: Int = 0
    override val height: Int = 0
    override val shareable: Boolean = true

    private val emptyDrawable = ColorDrawable()

    override fun asDrawable(resources: Resources): Drawable = emptyDrawable
}

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
