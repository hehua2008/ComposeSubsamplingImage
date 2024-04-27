import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cookies.ConstantCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.util.date.getTimeMillis
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * @author hehua2008
 * @date 2024/5/12
 */
class MainViewModel : ViewModel() {
    private val client = HttpClient {
        //expectSuccess = true
        followRedirects = true

        install(DefaultRequest) {
            url {
                protocol = URLProtocol.HTTPS
            }
        }

        install(UserAgent) {
            agent = "KtorClient"
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        install(HttpCookies) {
            storage = ConstantCookiesStorage()
        }

        /*
        install(ContentEncoding) {
            deflate(1.0f)
            gzip(0.9f)
        }
        */

        install(HttpCache) {
            val cacheFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "KtorHttpCache"
            publicStorage(FileStorage(cacheFile))
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    com.hym.compose.utils.Logger.d("HttpClient", message)
                }
            }
            level = LogLevel.HEADERS
            //sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }

    suspend fun getText(url: String): String {
        val response = client.get(url)
        return response.bodyAsText()
    }

    suspend fun getChannel(url: String): ByteReadChannel {
        val response = client.get(url)
        return response.bodyAsChannel()
    }

    suspend fun getImageBitmap(url: String): ImageBitmap {
        val response = client.get(url)
        val byteArray: ByteArray = response.body()
        return byteArray.decodeToImageBitmap()
    }

    suspend fun getFile(
        url: String,
        onProgress: ((bytesSentTotal: Long, contentLength: Long?) -> Unit)? = null
    ): Path {
        val md5 = url.encodeUtf8().md5().hex().uppercase()
        val dataFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor_$md5.data"
        val metadata = FileSystem.SYSTEM.metadataOrNull(dataFile)
        val size = metadata?.size
        if (size != null && size > 0 && metadata.isRegularFile) {
            onProgress?.invoke(size, size)
            return dataFile
        }
        //FileSystem.SYSTEM.delete(dataFile)

        val response = client.get(url) {
            onDownload { bytesSentTotal, contentLength ->
                onProgress?.invoke(bytesSentTotal, contentLength)
            }
        }
        val bodyChannel = response.bodyAsChannel()

        val now = getTimeMillis()
        val tmpFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor_$md5-$now.tmp"
        //FileSystem.SYSTEM.delete(tmpFile)

        FileSystem.SYSTEM.sink(tmpFile, true).buffer().use { bufferedSink ->
            val bytes = ByteArray(8192)
            while (true) {
                val count = bodyChannel.readAvailable(bytes)
                if (count <= 0) break
                bufferedSink.write(bytes, 0, count)
            }
            bufferedSink.flush()
        }

        // "ktor_$md5-$now.tmp"" -> "ktor_$md5.data"
        FileSystem.SYSTEM.atomicMove(tmpFile, dataFile)
        return dataFile
    }

    suspend fun prepareGetFile(
        url: String,
        onProgress: ((bytesSentTotal: Long, contentLength: Long?) -> Unit)? = null
    ): Path {
        val md5 = url.encodeUtf8().md5().hex().uppercase()
        val dataFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor_$md5.data"
        val metadata = FileSystem.SYSTEM.metadataOrNull(dataFile)
        val size = metadata?.size
        if (size != null && size > 0 && metadata.isRegularFile) {
            onProgress?.invoke(size, size)
            return dataFile
        }
        //FileSystem.SYSTEM.delete(dataFile)

        val now = getTimeMillis()
        val tmpFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor_$md5-$now.tmp"
        //FileSystem.SYSTEM.delete(tmpFile)

        FileSystem.SYSTEM.sink(tmpFile, true).buffer().use { bufferedSink ->
            client.prepareGetBytes(url = url, onProgress = onProgress) { bytes, count ->
                bufferedSink.write(bytes, 0, count)
            }
            bufferedSink.flush()
        }

        // "ktor_$md5-$now.tmp"" -> "ktor_$md5.data"
        FileSystem.SYSTEM.atomicMove(tmpFile, dataFile)
        return dataFile
    }
}
