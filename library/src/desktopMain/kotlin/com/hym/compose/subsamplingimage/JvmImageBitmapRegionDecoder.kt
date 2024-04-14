package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.SourceMarker
import com.hym.compose.utils.reusableRead
import okio.IOException
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.SamplingMode
import kotlin.math.roundToInt

/**
 * @author hehua2008
 * @date 2024/4/26
 */
class JvmImageBitmapRegionDecoder(private val pixMap: Pixmap) :
    ImageBitmapRegionDecoder<JvmImageBitmapRegionDecoder> {
    companion object {
        fun newInstance(sourceMarker: SourceMarker): JvmImageBitmapRegionDecoder {
            sourceMarker.reusableRead { source ->
                val byteArray = source.readByteArray()
                Image.makeFromEncoded(byteArray)
            }.use { image ->
                val imageInfo = image.imageInfo
                val data = Data.makeUninitialized(imageInfo.computeMinByteSize())
                val pixMap = Pixmap.make(imageInfo, data, imageInfo.minRowBytes)
                if (!image.readPixels(pixMap, 0, 0, false)) {
                    throw IOException("Image.readPixels() failed")
                }
                return JvmImageBitmapRegionDecoder(pixMap)
            }
        }
    }

    override fun decodeRegion(
        rect: IntRect,
        sampleSize: Int,
        config: ImageBitmapConfig
    ): ImageBitmap? {
        if (sampleSize <= 1 && rect.left == 0 && rect.top == 0 && rect.right == width && rect.bottom == height) {
            Image.makeFromPixmap(pixMap).use { image ->
                return image.toComposeImageBitmap()
            }
        }
        val subPixmap = Pixmap()
        val iRect = IRect.makeLTRB(rect.left, rect.top, rect.right, rect.bottom)
        if (!pixMap.extractSubset(subPixmap, iRect)) {
            subPixmap.close()
            return null
        }
        if (sampleSize <= 1) {
            subPixmap.use {
                Image.makeFromPixmap(it).use { image ->
                    return image.toComposeImageBitmap()
                }
            }
        }
        val subSampleImageInfo = pixMap.info.run {
            ImageInfo(
                (rect.width / sampleSize.toFloat()).roundToInt(),
                (rect.height / sampleSize.toFloat()).roundToInt(),
                colorType,
                colorAlphaType,
                colorSpace
            )
        }
        Data.makeUninitialized(subSampleImageInfo.computeMinByteSize()).use { subSampleData ->
            Pixmap.make(subSampleImageInfo, subSampleData, subSampleImageInfo.minRowBytes)
                .use { subSamplePixmap ->
                    subPixmap.use {
                        it.scalePixels(subSamplePixmap, SamplingMode.LINEAR)
                    }
                    Image.makeFromPixmap(subSamplePixmap).use { image ->
                        return image.toComposeImageBitmap()
                    }
                }
        }
    }

    override val width: Int
        get() = pixMap.info.width

    override val height: Int
        get() = pixMap.info.height

    override fun close() {
        pixMap.close()
    }
}
