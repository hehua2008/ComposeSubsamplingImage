import androidx.compose.ui.graphics.ImageBitmap
import coil3.annotation.ExperimentalCoilApi

/**
 * @author hehua2008
 * @date 2024/4/26
 */
@OptIn(ExperimentalCoilApi::class)
expect object EmptyCoilImage : coil3.Image

expect fun ByteArray.decodeToImageBitmap(): ImageBitmap

@OptIn(ExperimentalCoilApi::class)
expect fun coil3.Image.toImageBitmap(): ImageBitmap
