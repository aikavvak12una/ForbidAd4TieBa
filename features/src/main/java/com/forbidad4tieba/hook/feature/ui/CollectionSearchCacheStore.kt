package com.forbidad4tieba.hook.feature.ui

import android.content.Context
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal object CollectionSearchCacheStore {
    private const val CACHE_VERSION = 1
    private const val CACHE_DIR_NAME = "tbhook_cache"
    private const val CACHE_FILE_PREFIX = "collection_search_"
    private const val CACHE_FILE_SUFFIX = ".json"
    private const val MAX_CACHE_FILES = 8
    private const val MAX_DISK_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val FAILURE_BACKOFF_MS = 60_000L

    private const val KEY_VERSION = "version"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_UPDATED_AT = "updated_at_ms"
    private const val KEY_DIRTY = "dirty"
    private const val KEY_FULL_READY = "full_ready"
    private const val KEY_PAGES = "raw_pages"

    @Volatile private var readBackoffUntilMs = 0L
    @Volatile private var writeBackoffUntilMs = 0L

    data class Snapshot(
        val accountKey: String,
        val updatedAtMs: Long,
        val dirty: Boolean,
        val fullReady: Boolean,
        val rawPages: List<String>,
    )

    fun read(context: Context, accountKey: String): Snapshot? {
        if (System.currentTimeMillis() < readBackoffUntilMs) return null
        val file = cacheFile(context, accountKey)
        if (!file.exists()) return null
        val snapshot = readSnapshot(file) ?: return null
        if (snapshot.accountKey != accountKey) return null
        val now = System.currentTimeMillis()
        if (snapshot.rawPages.isEmpty() || now - snapshot.updatedAtMs > MAX_DISK_AGE_MS) {
            runCatching { file.delete() }
            return null
        }
        return snapshot
    }

    fun write(
        context: Context,
        accountKey: String,
        rawPages: List<String>,
        dirty: Boolean,
        fullReady: Boolean,
    ): Boolean {
        if (System.currentTimeMillis() < writeBackoffUntilMs) return false
        if (rawPages.isEmpty()) return false
        val file = cacheFile(context, accountKey)
        val payload = JSONObject().apply {
            put(KEY_VERSION, CACHE_VERSION)
            put(KEY_ACCOUNT, accountKey)
            put(KEY_UPDATED_AT, System.currentTimeMillis())
            put(KEY_DIRTY, dirty)
            put(KEY_FULL_READY, fullReady)
            put(KEY_PAGES, JSONArray().apply { rawPages.forEach(::put) })
        }
        val ok = writeAtomically(file, payload.toString())
        if (ok) prune(context)
        return ok
    }

    fun updateFirstPage(context: Context, accountKey: String, firstPageRaw: String): Boolean {
        if (firstPageRaw.isBlank()) return false
        val snapshot = read(context, accountKey)
        val pages = ArrayList<String>(snapshot?.rawPages ?: emptyList())
        val firstPageSize = extractStoreThreadSize(firstPageRaw)
        if (firstPageSize == 0) {
            pages.clear()
            pages.add(firstPageRaw)
        } else if (pages.isEmpty()) {
            pages.add(firstPageRaw)
        } else {
            pages[0] = firstPageRaw
        }
        return write(
            context = context,
            accountKey = accountKey,
            rawPages = pages,
            dirty = false,
            fullReady = if (firstPageSize == 0) true else snapshot?.fullReady == true,
        )
    }

    fun markDirty(context: Context, accountKey: String, dirty: Boolean): Boolean {
        val file = cacheFile(context, accountKey)
        if (!file.exists()) return false
        val snapshot = readSnapshot(file) ?: return false
        if (snapshot.accountKey != accountKey || snapshot.dirty == dirty || snapshot.rawPages.isEmpty()) return true
        val payload = JSONObject().apply {
            put(KEY_VERSION, CACHE_VERSION)
            put(KEY_ACCOUNT, accountKey)
            put(KEY_UPDATED_AT, if (dirty) snapshot.updatedAtMs else System.currentTimeMillis())
            put(KEY_DIRTY, dirty)
            put(KEY_FULL_READY, snapshot.fullReady)
            put(KEY_PAGES, JSONArray().apply { snapshot.rawPages.forEach(::put) })
        }
        return writeAtomically(file, payload.toString())
    }

    private fun readSnapshot(file: File): Snapshot? {
        return try {
            val text = file.readText()
            if (text.isBlank()) return null
            val obj = JSONObject(text)
            if (obj.optInt(KEY_VERSION, -1) != CACHE_VERSION) return null
            val account = obj.optString(KEY_ACCOUNT).trim()
            if (account.isEmpty()) return null
            val updatedAt = obj.optLong(KEY_UPDATED_AT, 0L)
            if (updatedAt <= 0L) return null
            val pagesArray = obj.optJSONArray(KEY_PAGES) ?: return null
            val pages = ArrayList<String>(pagesArray.length())
            for (i in 0 until pagesArray.length()) {
                val raw = pagesArray.optString(i)
                if (raw.isNotBlank()) pages.add(raw)
            }
            readBackoffUntilMs = 0L
            Snapshot(
                accountKey = account,
                updatedAtMs = updatedAt,
                dirty = obj.optBoolean(KEY_DIRTY, false),
                fullReady = obj.optBoolean(KEY_FULL_READY, false),
                rawPages = pages,
            )
        } catch (t: Throwable) {
            readBackoffUntilMs = System.currentTimeMillis() + FAILURE_BACKOFF_MS
            XposedCompat.logW("[CollectionSearchCacheStore] read failed: ${t.message}")
            null
        }
    }

    private fun extractStoreThreadSize(raw: String): Int {
        return runCatching {
            JSONObject(raw).optJSONArray("store_thread")?.length() ?: -1
        }.getOrDefault(-1)
    }

    private fun writeAtomically(file: File, text: String): Boolean {
        return try {
            val parent = file.parentFile ?: return false
            if (!parent.exists()) parent.mkdirs()
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(text)
            if (file.exists() && !file.delete()) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
            writeBackoffUntilMs = 0L
            true
        } catch (t: Throwable) {
            writeBackoffUntilMs = System.currentTimeMillis() + FAILURE_BACKOFF_MS
            XposedCompat.logW("[CollectionSearchCacheStore] write failed: ${t.message}")
            false
        }
    }

    private fun prune(context: Context) {
        try {
            val dir = cacheDir(context)
            if (!dir.exists()) return
            val files = dir.listFiles { file ->
                file.isFile && file.name.startsWith(CACHE_FILE_PREFIX) && file.name.endsWith(CACHE_FILE_SUFFIX)
            } ?: return
            if (files.size <= MAX_CACHE_FILES) return
            files.sortByDescending { it.lastModified() }
            for (i in MAX_CACHE_FILES until files.size) {
                runCatching { files[i].delete() }
            }
        } catch (_: Throwable) {
        }
    }

    private fun cacheFile(context: Context, accountKey: String): File {
        val safeKey = hashAccount(accountKey)
        return File(cacheDir(context), "$CACHE_FILE_PREFIX$safeKey$CACHE_FILE_SUFFIX")
    }

    private fun cacheDir(context: Context): File {
        return File(context.filesDir, CACHE_DIR_NAME)
    }

    private fun hashAccount(accountKey: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { b -> String.format(Locale.US, "%02x", b) }
        } catch (_: Throwable) {
            accountKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }
    }
}
