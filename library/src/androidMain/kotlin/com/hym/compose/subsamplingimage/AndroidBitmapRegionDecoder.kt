package com.hym.compose.subsamplingimage

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntRect
import com.hym.compose.utils.SourceMarker
import com.hym.compose.utils.reusableRead

/**
 * @author hehua2008
 * @date 2024/4/12
 */
class AndroidBitmapRegionDecoder(private val decoder: BitmapRegionDecoder) :
    ImageBitmapRegionDecoder<AndroidBitmapRegionDecoder> {
    companion object {
        fun newInstance(sourceMarker: SourceMarker): AndroidBitmapRegionDecoder? {
            return sourceMarker.reusableRead { source ->
                val inputStream = source.inputStream()
                val decoder = BitmapRegionDecoder.newInstance(inputStream, false)
                if (decoder == null) null else AndroidBitmapRegionDecoder(decoder)
            }
        }
    }

    override fun decodeRegion(
        rect: IntRect,
        sampleSize: Int,
        config: ImageBitmapConfig
    ): ImageBitmap? {
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize
        options.inPreferredConfig = config.toAndroidBitmapConfig()
        return decoder.decodeRegion(rect.toAndroidRect(), options)?.asImageBitmap()
    }

    override val width: Int
        get() = decoder.width

    override val height: Int
        get() = decoder.height

    override fun close() {
        decoder.recycle()
    }
}
