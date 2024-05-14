import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenEntries
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * @author hehua2008
 * @date 2024/5/12
 *
 * @see [io.ktor.client.plugins.cache.storage.FileCacheStorage]
 */

private val DEFAULT_LOGGER = KtorSimpleLogger("FileCacheStorage")

/**
 * Creates storage that uses file system to store cache data.
 * @param directory directory to store cache data.
 * @param dispatcher dispatcher to use for file operations.
 */
public fun FileStorage(
    directory: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    logger: Logger = DEFAULT_LOGGER
): CacheStorage = CachingCacheStorage(FileCacheStorage(directory, dispatcher, logger))

internal class CachingCacheStorage(
    private val delegate: CacheStorage
) : CacheStorage {

    private val store = ConcurrentMap<Url, Set<CachedResponseData>>()

    override suspend fun store(url: Url, data: CachedResponseData) {
        delegate.store(url, data)
        store[url] = delegate.findAll(url)
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        val data = store.getValue(url)
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        if (!store.containsKey(url)) {
            store[url] = delegate.findAll(url)
        }
        return store.getValue(url)
    }
}

private class FileCacheStorage(
    private val directory: Path,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: Logger = DEFAULT_LOGGER
) : CacheStorage {

    private val mutexes = ConcurrentMap<String, Mutex>()

    init {
        FileSystem.SYSTEM.createDirectories(directory)
    }

    override suspend fun store(url: Url, data: CachedResponseData): Unit = withContext(dispatcher) {
        val urlHex = key(url)
        val caches = readCache(urlHex).filterNot { it.varyKeys == data.varyKeys } + data
        writeCache(urlHex, caches)
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return readCache(key(url)).toSet()
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val data = readCache(key(url))
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    private fun key(url: Url): String {
        return url.toString().encodeUtf8().md5().hex()
    }

    private suspend fun writeCache(urlHex: String, caches: List<CachedResponseData>) = coroutineScope {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            try {
                FileSystem.SYSTEM.sink(directory / urlHex).buffer().use {
                    it.writeInt(caches.size)
                    for (cache in caches) {
                        writeCache(it, cache)
                    }
                }
            } catch (cause: Exception) {
                logger.trace("Exception during saving a cache to a file: ${cause.stackTraceToString()}")
            }
        }
    }

    private suspend fun readCache(urlHex: String): Set<CachedResponseData> {
        val mutex = mutexes.computeIfAbsent(urlHex) { Mutex() }
        mutex.withLock {
            val file = directory / urlHex
            if (!FileSystem.SYSTEM.exists(file)) return emptySet()

            try {
                FileSystem.SYSTEM.source(file).buffer().use {
                    val requestsCount = it.readInt()
                    val caches = mutableSetOf<CachedResponseData>()
                    for (i in 0 until requestsCount) {
                        caches.add(readCache(it))
                    }
                    return caches
                }
            } catch (cause: Exception) {
                logger.trace("Exception during cache lookup in a file: ${cause.stackTraceToString()}")
                return emptySet()
            }
        }
    }

    private fun writeCache(bufferedSink: BufferedSink, cache: CachedResponseData) {
        bufferedSink.writeUtf8(cache.url.toString() + "\n")
        bufferedSink.writeInt(cache.statusCode.value)
        bufferedSink.writeUtf8(cache.statusCode.description + "\n")
        bufferedSink.writeUtf8(cache.version.toString() + "\n")
        val headers = cache.headers.flattenEntries()
        bufferedSink.writeInt(headers.size)
        for ((key, value) in headers) {
            bufferedSink.writeUtf8(key + "\n")
            bufferedSink.writeUtf8(value + "\n")
        }
        bufferedSink.writeLong(cache.requestTime.timestamp)
        bufferedSink.writeLong(cache.responseTime.timestamp)
        bufferedSink.writeLong(cache.expires.timestamp)
        bufferedSink.writeInt(cache.varyKeys.size)
        for ((key, value) in cache.varyKeys) {
            bufferedSink.writeUtf8(key + "\n")
            bufferedSink.writeUtf8(value + "\n")
        }
        bufferedSink.writeInt(cache.body.size)
        bufferedSink.write(cache.body)
        bufferedSink.flush()
    }

    private fun readCache(bufferedSource: BufferedSource): CachedResponseData {
        val url = bufferedSource.readUtf8Line()!!
        val status = HttpStatusCode(bufferedSource.readInt(), bufferedSource.readUtf8Line()!!)
        val version = HttpProtocolVersion.parse(bufferedSource.readUtf8Line()!!)
        val headersCount = bufferedSource.readInt()
        val headers = HeadersBuilder()
        for (j in 0 until headersCount) {
            val key = bufferedSource.readUtf8Line()!!
            val value = bufferedSource.readUtf8Line()!!
            headers.append(key, value)
        }
        val requestTime = GMTDate(bufferedSource.readLong())
        val responseTime = GMTDate(bufferedSource.readLong())
        val expirationTime = GMTDate(bufferedSource.readLong())
        val varyKeysCount = bufferedSource.readInt()
        val varyKeys = buildMap {
            for (j in 0 until varyKeysCount) {
                val key = bufferedSource.readUtf8Line()!!
                val value = bufferedSource.readUtf8Line()!!
                put(key, value)
            }
        }
        val bodyCount = bufferedSource.readInt()
        val body = ByteArray(bodyCount)
        bufferedSource.readFully(body)
        return CachedResponseData(
            url = Url(url),
            statusCode = status,
            requestTime = requestTime,
            responseTime = responseTime,
            version = version,
            expires = expirationTime,
            headers = headers.build(),
            varyKeys = varyKeys,
            body = body
        )
    }
}
