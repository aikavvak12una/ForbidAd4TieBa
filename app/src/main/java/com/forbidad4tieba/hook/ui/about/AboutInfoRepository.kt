package com.forbidad4tieba.hook.ui.about

import android.content.Context
import com.forbidad4tieba.hook.BuildConfig
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class AboutDocument(
    val payload: AboutPayload,
    val source: String,
    val url: String,
    val bytes: Int,
    val elapsedMs: Long,
    val cacheAgeMs: Long,
)

/** Includes a cached miss: early policy application and the fetch worker share one disk read. */
internal class AboutStartupCache {
    private class Loaded(val document: AboutDocument?)
    @Volatile private var loaded: Loaded? = null

    fun getOrRead(read: () -> AboutDocument?): AboutDocument? {
        loaded?.let { return it.document }
        return synchronized(this) {
            (loaded ?: Loaded(read()).also { loaded = it }).document
        }
    }

    fun publish(document: AboutDocument) = synchronized(this) { loaded = Loaded(document) }
}

internal object AboutPayloadReader {
    const val MAX_BYTES = 1024 * 1024
    data class Text(val value: String, val bytes: Int)

    /** Enforces the byte limit even for chunked responses or a misleading Content-Length. */
    fun read(input: InputStream, maxBytes: Int = MAX_BYTES): Text {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(minOf(maxBytes, 8192))
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) throw IOException("about payload exceeds $maxBytes bytes")
            output.write(buffer, 0, count)
        }
        return Text(output.toString(Charsets.UTF_8.name()).trim(), total)
    }
}

internal object AboutInfoRepository {
    private const val CACHE_DIR_NAME = "tbhook"
    private const val CACHE_FILE_NAME = "about_info_cache.json"
    private val startupCache = AboutStartupCache()
    private val sourceUrls = listOf(
        "https://raw.giteeusercontent.com/ratsoluos/detectupdate/raw/master/about.json",
        "https://raw.githubusercontent.com/aikavvak12una/ForbidAd4TieBa/refs/heads/main/about.json",
        "https://github.com/aikavvak12una/ForbidAd4TieBa/raw/refs/heads/main/about.json",
    )

    fun cached(context: Context): AboutDocument? = startupCache.getOrRead { readDisk(context) }

    fun fetchAndCache(context: Context): AboutDocument? {
        for (url in sourceUrls) {
            val start = System.currentTimeMillis()
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "TBHook/${BuildConfig.VERSION_NAME}")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    XposedCompat.logW("[AboutInfo] fetch failed: url=$url code=$code")
                    continue
                }
                if (connection.contentLengthLong > AboutPayloadReader.MAX_BYTES) {
                    throw IOException("about Content-Length exceeds ${AboutPayloadReader.MAX_BYTES} bytes")
                }
                val text = connection.inputStream.use { AboutPayloadReader.read(it) }
                val payload = AboutPayloadParser.parse(text.value) ?: continue
                val document = AboutDocument(payload, "remote", url, text.bytes,
                    System.currentTimeMillis() - start, -1L)
                writeDisk(context, text.value, url)
                startupCache.publish(document)
                return document
            } catch (failure: Throwable) {
                XposedCompat.logW("[AboutInfo] fetch exception: url=$url elapsedMs=${System.currentTimeMillis() - start} msg=${failure.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    private fun readDisk(context: Context): AboutDocument? {
        val start = System.currentTimeMillis()
        return try {
            val file = cacheFile(context)
            if (!file.isFile) return null
            if (file.length() > AboutPayloadReader.MAX_BYTES) throw IOException("about cache exceeds byte limit")
            val text = file.inputStream().use { AboutPayloadReader.read(it) }
            val payload = AboutPayloadParser.parse(text.value) ?: return null
            AboutDocument(payload, "cache", "cache", text.bytes, System.currentTimeMillis() - start,
                (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0L))
        } catch (failure: Throwable) {
            XposedCompat.logW("[AboutInfo] cache read exception: ${failure.message}")
            null
        }
    }

    private fun writeDisk(context: Context, text: String, url: String) {
        var temporary: File? = null
        try {
            val file = cacheFile(context)
            val parent = file.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("mkdirs")
            }
            val output = File(parent, "$CACHE_FILE_NAME.tmp")
            temporary = output
            output.writeText(text, Charsets.UTF_8)
            if (!output.renameTo(file)) {
                throw IOException("rename")
            }
            XposedCompat.logD { "[AboutInfo] cache updated for next startup: url=$url" }
        } catch (failure: Throwable) {
            runCatching { temporary?.delete() }.onFailure { cleanup ->
                XposedCompat.logD { "[AboutInfo] temporary cache cleanup failed: ${cleanup.message}" }
            }
            XposedCompat.logW("[AboutInfo] cache update failed: url=$url msg=${failure.message}")
        }
    }

    private fun cacheFile(context: Context) = File(File(context.filesDir, CACHE_DIR_NAME), CACHE_FILE_NAME)
}
