package com.hym.compose.subsamplingimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.cfDictionaryOf
import com.hym.compose.utils.cfRetain
import com.hym.compose.utils.toBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.makeFromFileName
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceShouldCache

/**
 * @author hehua2008
 * @date 2024/4/26
 */
class NativeImageBitmapRegionDecoder(
    private val imageInfo: ImageInfo,
    @OptIn(ExperimentalForeignApi::class) private val imageSource: CGImageSourceRef
) : ImageBitmapRegionDecoder<NativeImageBitmapRegionDecoder> {
    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun newInstance(path: Path): NativeImageBitmapRegionDecoder {
            val file = path.normalized().toString()
            Data.makeFromFileName(file).use { data ->
                Codec.makeFromData(data)
            }.use { codec ->
                val filePath =
                    CFStringCreateWithCString(kCFAllocatorDefault, file, kCFStringEncodingUTF8)!!
                val url = CFURLCreateWithFileSystemPath(
                    kCFAllocatorDefault, filePath, kCFURLPOSIXPathStyle, false
                )!!
                CFRelease(filePath)
                val imageSource = CGImageSourceCreateWithURL(url, null)!!
                CFRelease(url)
                return NativeImageBitmapRegionDecoder(codec.imageInfo, imageSource)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun decodeRegion(
        rect: IntRect,
        sampleSize: Int,
        config: ImageBitmapConfig
    ): ImageBitmap? {
        // Refer: https://developer.apple.com/documentation/imageio/cgimagesource#3702930
        val optionMap = mapOf(kCGImageSourceShouldCache to kCFBooleanFalse)
        val options = cfRetain(optionMap) {
            cfDictionaryOf(optionMap)
        }
        val cgImage = CGImageSourceCreateImageAtIndex(imageSource, 0u, options)!!
        CFRelease(options)
        val regionRect = CGRectMake(
            rect.left.toDouble(),
            rect.top.toDouble(),
            rect.width.toDouble(),
            rect.height.toDouble()
        )
        val regionImage = CGImageCreateWithImageInRect(cgImage, regionRect)!!
        val regionDataProvider = CGImageGetDataProvider(regionImage)!!
        val regionData = CGDataProviderCopyData(regionDataProvider)!!

        CGDataProviderRelease(regionDataProvider)
        //CGImageRelease(regionImage)
        CGImageRelease(cgImage)

        val subData = ManagedCFBytes(
            CFDataGetBytePtr(regionData)!!,
            CFDataGetLength(regionData).toInt()
        ).toData()
        val subImageInfo = imageInfo.run {
            ImageInfo(rect.width, rect.height, colorType, colorAlphaType, colorSpace)
        }
        val subPixmap = Pixmap.make(subImageInfo, subData, subImageInfo.minRowBytes)

        try {
            Image.makeFromPixmap(subPixmap).use { image ->
                val scale = if (sampleSize <= 1) 1f else 1f / sampleSize
                val bitmap = image.toBitmap(scale)
                return bitmap.asComposeImageBitmap()
            }
        } finally {
            CFRelease(regionData)
            subData.close()
            subPixmap.close()
        }
    }

    override val width: Int
        get() = imageInfo.width

    override val height: Int
        get() = imageInfo.height

    @OptIn(ExperimentalForeignApi::class)
    override fun close() {
        CFRelease(imageSource)
    }
}
