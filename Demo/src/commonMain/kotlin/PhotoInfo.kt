import androidx.compose.ui.graphics.ImageBitmap
import coil3.Uri
import okio.Path

/**
 * @author hehua2008
 * @date 2022/3/8
 */
sealed interface PhotoInfo<T : Any> {
    val original: T

    val thumb: T get() = original

    val description: String get() = original.toString()

    val width: Int get() = -1

    val height: Int get() = -1

    fun hasThumb() = original != thumb
}

data class UrlPhotoInfo(
    override val original: String,
    override val thumb: String = original,
    override val description: String = original,
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<String>

data class UriPhotoInfo(
    override val original: Uri,
    override val thumb: Uri = original,
    override val description: String = original.toString(),
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<Uri>

data class FilePhotoInfo(
    override val original: Path,
    override val thumb: Path = original,
    override val description: String = original.toString(),
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<Path>

data class ResPhotoInfo(
    /*@RawRes @DrawableRes*/ override val original: Int,
    override val thumb: Int = original,
    override val description: String = original.toString(),
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<Int>

data class BitmapPhotoInfo(
    override val original: ImageBitmap,
    override val thumb: ImageBitmap = original,
    override val description: String = "Bitmap(${original.width}x${original.height})",
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<ImageBitmap>

data class ByteArrayPhotoInfo(
    override val original: ByteArray,
    override val thumb: ByteArray = original,
    override val description: String = "ByteArray(${original.size})",
    override val width: Int = -1,
    override val height: Int = -1
) : PhotoInfo<ByteArray> {
    override fun equals(other: Any?): Boolean {
        other ?: return false
        if (this === other) return true
        if (this::class != other::class) return false

        other as ByteArrayPhotoInfo

        if (original !== other.original) return false
        if (thumb !== other.thumb) return false
        if (description != other.description) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = original.hashCode()
        result = 31 * result + thumb.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}
