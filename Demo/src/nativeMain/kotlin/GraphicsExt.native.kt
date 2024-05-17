import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.annotation.ExperimentalCoilApi
import org.jetbrains.skia.impl.use

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

    private val emptyBitmap = org.jetbrains.skia.Bitmap().apply { setImmutable() }

    override fun toBitmap(): org.jetbrains.skia.Bitmap = emptyBitmap
}

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
