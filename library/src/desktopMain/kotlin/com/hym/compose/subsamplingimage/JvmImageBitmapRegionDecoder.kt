package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.toBitmap
import okio.FileSystem
import okio.Path
import okio.buffer
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

/**
 * @author hehua2008
 * @date 2024/4/26
 */
class JvmImageBitmapRegionDecoder(private val image: Image) :
    ImageBitmapRegionDecoder<JvmImageBitmapRegionDecoder> {
    companion object {
        fun newInstance(path: Path): JvmImageBitmapRegionDecoder {
            FileSystem.SYSTEM.source(path).buffer().use { source ->
                val byteArray = source.readByteArray()
                val image = Image.makeFromEncoded(byteArray)
                return JvmImageBitmapRegionDecoder(image)
            }
        }
    }

    override fun decodeRegion(
        rect: IntRect,
        sampleSize: Int,
        config: ImageBitmapConfig
    ): ImageBitmap? {
        val skiaRect = Rect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat()
        )
        val scale = if (sampleSize <= 1) 1f else 1f / sampleSize
        val bitmap = image.toBitmap(scale, skiaRect)
        return bitmap.asComposeImageBitmap()
    }

    override val width: Int
        get() = image.imageInfo.width

    override val height: Int
        get() = image.imageInfo.height

    override fun close() {
        image.close()
    }
}
