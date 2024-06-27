import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.discardRemaining
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readAvailable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * @author hehua2008
 * @date 2024/5/12
 */
suspend inline fun <T> HttpResponse.use(block: (HttpResponse) -> T): T {
    try {
        return block(this)
    } finally {
        withContext(NonCancellable) {
            discardRemaining()
        }
    }
}

suspend fun HttpClient.prepareGetBytes(
    url: String,
    bufferSize: Int = 8192,
    onProgress: ((bytesSentTotal: Long, contentLength: Long?) -> Unit)? = null,
    onGetBytes: (bytes: ByteArray, count: Int) -> Unit
) {
    prepareGet(url).execute {
        it.use { httpResponse ->
            val contentLength = httpResponse.contentLength()
            val bodyChannel: ByteReadChannel = httpResponse.body()
            val packetLimit = bufferSize.toLong()
            val bytes = ByteArray(bufferSize)
            var bytesSentTotal: Long = 0
            while (!bodyChannel.isClosedForRead) {
                val packet = bodyChannel.readRemaining(packetLimit)
                while (!packet.isEmpty) {
                    val count = packet.readAvailable(bytes)
                    bytesSentTotal += count
                    onGetBytes(bytes, count)
                    onProgress?.invoke(bytesSentTotal, contentLength)
                }
            }
        }
    }
}
