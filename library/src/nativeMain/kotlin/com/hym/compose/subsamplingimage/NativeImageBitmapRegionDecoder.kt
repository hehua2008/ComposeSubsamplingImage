package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.SourceMarker
import com.hym.compose.utils.reusableRead
import com.hym.compose.utils.toBitmap
import okio.IOException
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.use

/**
 * @author hehua2008
 * @date 2024/4/26
 */
class NativeImageBitmapRegionDecoder(private val pixMap: Pixmap) :
    ImageBitmapRegionDecoder<NativeImageBitmapRegionDecoder> {
    companion object {
        fun newInstance(sourceMarker: SourceMarker): NativeImageBitmapRegionDecoder {
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
                return NativeImageBitmapRegionDecoder(pixMap)
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
        subPixmap.use {
            if (!pixMap.extractSubset(subPixmap, iRect)) {
                return null
            } else {
                Image.makeFromPixmap(it).use { image ->
                    val scale = if (sampleSize <= 1) 1f else 1f / sampleSize
                    val bitmap = image.toBitmap(scale)
                    return bitmap.asComposeImageBitmap()
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
