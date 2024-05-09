package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.toBitmap
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.makeFromFileName

/**
 * @author hehua2008
 * @date 2024/4/26
 */
class JvmImageBitmapRegionDecoder(
    private val imageInfo: ImageInfo,
    private val pixelFile: Path
) : ImageBitmapRegionDecoder<JvmImageBitmapRegionDecoder> {
    companion object {
        fun newInstance(path: Path): JvmImageBitmapRegionDecoder {
            Data.makeFromFileName(path.normalized().toString()).use { data ->
                Codec.makeFromData(data)
            }.use { codec ->
                val imageInfo = codec.imageInfo
                codec.readPixels().use { bitmap ->
                    val pixelFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
                            "pixel_${System.currentTimeMillis()}.data"
                    FileSystem.SYSTEM.write(pixelFile, true) {
                        val oneRowImageInfo = imageInfo.run {
                            ImageInfo(bitmap.width, 1, colorType, colorAlphaType, colorSpace)
                        }
                        for (srcY in 0 until bitmap.height) {
                            val pixelBytes = bitmap.readPixels(
                                oneRowImageInfo,
                                oneRowImageInfo.minRowBytes,
                                0,
                                srcY
                            ) ?: continue
                            write(pixelBytes)
                        }
                        flush()
                    }
                    return JvmImageBitmapRegionDecoder(imageInfo, pixelFile)
                }
            }
        }
    }

    override fun decodeRegion(
        rect: IntRect,
        sampleSize: Int,
        config: ImageBitmapConfig
    ): ImageBitmap? {
        val subImageInfo = imageInfo.run {
            ImageInfo(rect.width, rect.height, colorType, colorAlphaType, colorSpace)
        }
        val subByteArray = ByteArray(subImageInfo.computeMinByteSize())
        FileSystem.SYSTEM.source(pixelFile).buffer().use { bufferedSource ->
            var lastPos = 0L
            for (column in rect.top until rect.bottom) {
                val newPos =
                    imageInfo.computeOffset(rect.left, column, imageInfo.minRowBytes.toLong())
                bufferedSource.skip(newPos - lastPos)
                lastPos = newPos
                var offset = (column - rect.top) * subImageInfo.minRowBytes
                var byteCount = subImageInfo.minRowBytes
                do {
                    val readCount = bufferedSource.read(subByteArray, offset, byteCount)
                    offset += readCount
                    byteCount -= readCount
                    lastPos += readCount
                } while (byteCount > 0)
            }
        }
        Image.makeRaster(subImageInfo, subByteArray, subImageInfo.minRowBytes).use { image ->
            val scale = if (sampleSize <= 1) 1f else 1f / sampleSize
            val bitmap = image.toBitmap(scale)
            return bitmap.asComposeImageBitmap()
        }
    }

    override val width: Int
        get() = imageInfo.width

    override val height: Int
        get() = imageInfo.height

    override fun close() {
        FileSystem.SYSTEM.delete(pixelFile)
    }
}
