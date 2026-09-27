package com.forbidad4tieba.hook.feature.ui

import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

internal data class BackgroundLoadResult(val entry: CachedBackgroundBitmap, val changed: Boolean)

/** Memory queries never read files. Only the existing decode worker calls [refreshOnWorker]. */
internal class HomeNativeGlassBackgroundStore(
    private val maxCachedBytes: Long,
    private val metadataCheckIntervalMs: Long,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val readMetadata: (String) -> BackgroundFileMetadata = ::readBackgroundMetadata,
    private val decode: (BackgroundRequest, BackgroundFileMetadata, BackgroundFileMetadata) -> CachedBackgroundBitmap =
        ::decodeBackgroundBitmap,
) {
    private val lock = Any()
    private val entries = LinkedHashMap<String, CachedBackgroundBitmap>(4, 0.75f, true)
    private val releasedEntries = LinkedHashMap<String, WeakReference<CachedBackgroundBitmap>>()
    private var epoch = 0L

    fun generation(): Long = synchronized(lock) { epoch }

    fun find(request: BackgroundRequest): CachedBackgroundBitmap? = synchronized(lock) {
        entries[request.cacheKey]?.let { return@synchronized it }
        entries.values.firstOrNull { it.matches(request) }?.let {
            entries[it.cacheKey]
            return@synchronized it
        }
        // A just-published oversized entry can still be delivered without exceeding the
        // strong cache budget. Page drawables own their pixels; eviction never recycles them.
        val iterator = releasedEntries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().get()
            if (entry == null) iterator.remove()
            else if (entry.matches(request)) return@synchronized entry
        }
        null
    }

    fun needsMetadataCheck(entry: CachedBackgroundBitmap): Boolean =
        clock() - entry.metadataCheckedAt >= metadataCheckIntervalMs

    fun clear() = synchronized(lock) {
        epoch++
        entries.clear()
        releasedEntries.clear()
    }

    internal fun retainedBytes(): Long = synchronized(lock) { entries.values.sumOf { it.memoryBytes } }

    fun refreshOnWorker(
        request: BackgroundRequest,
        generation: Long,
        isCurrent: () -> Boolean,
    ): BackgroundLoadResult? {
        if (generation != this.generation() || !isCurrent()) return null
        val source = readMetadata(request.path)
        val blur = readMetadata(request.blurCachePath)
        val cached = find(request)
        if (cached != null && cached.matchesMetadata(source, blur)) {
            return synchronized(lock) {
                if (generation != epoch || !isCurrent()) return@synchronized null
                cached.metadataCheckedAt = clock()
                BackgroundLoadResult(cached, changed = false)
            }
        }

        val decoded = decode(request, source, blur)
        // The file or active style may change while BitmapFactory is working. An old
        // result must neither repopulate a cleared cache nor be delivered to the page.
        val stable = source == readMetadata(request.path) && blur == readMetadata(request.blurCachePath)
        val result = synchronized(lock) {
            if (!stable || generation != epoch || !isCurrent()) return@synchronized null
            decoded.metadataCheckedAt = clock()
            val obsolete = entries.values.iterator()
            while (obsolete.hasNext()) {
                val entry = obsolete.next()
                if (entry.path == request.path && entry.blurCachePath == request.blurCachePath &&
                    !entry.matchesMetadata(source, blur)
                ) obsolete.remove()
            }
            releasedEntries.entries.removeAll { (_, ref) ->
                val entry = ref.get()
                entry == null || (entry.path == request.path && entry.blurCachePath == request.blurCachePath &&
                    !entry.matchesMetadata(source, blur))
            }
            entries[decoded.cacheKey] = decoded
            trimLocked()
            BackgroundLoadResult(decoded, changed = true)
        }
        if (result == null) recycleUnpublishedBackground(decoded)
        return result
    }

    private fun trimLocked() {
        var total = entries.values.sumOf { it.memoryBytes }
        val iterator = entries.entries.iterator()
        // Bound negative entries too, while allowing common day/night and size variants.
        while ((total > maxCachedBytes || entries.size > 16) && iterator.hasNext()) {
            val entry = iterator.next().value
            total -= entry.memoryBytes
            iterator.remove()
            releasedEntries[entry.cacheKey] = WeakReference(entry)
        }
    }
}

private fun CachedBackgroundBitmap.matches(request: BackgroundRequest): Boolean =
    path == request.path && blurCachePath == request.blurCachePath && blurPercent == request.blurPercent &&
        targetWidth >= request.targetWidth && targetHeight >= request.targetHeight

private fun CachedBackgroundBitmap.matchesMetadata(
    source: BackgroundFileMetadata,
    blur: BackgroundFileMetadata,
): Boolean = lastModified == source.lastModified && length == source.length &&
    blurCacheLastModified == blur.lastModified && blurCacheLength == blur.length

private fun readBackgroundMetadata(path: String): BackgroundFileMetadata {
    if (path.isBlank()) return BackgroundFileMetadata(0L, 0L)
    return runCatching {
        val file = File(path)
        if (file.isFile) BackgroundFileMetadata(file.lastModified(), file.length())
        else BackgroundFileMetadata(0L, 0L)
    }.getOrDefault(BackgroundFileMetadata(0L, 0L))
}

private fun decodeBackgroundBitmap(
    request: BackgroundRequest,
    source: BackgroundFileMetadata,
    blur: BackgroundFileMetadata,
): CachedBackgroundBitmap {
    val bitmap = if (source.length > 0L) runCatching {
        HomeNativeGlassImageCache.decodeSampledBitmap(request.path, request.targetWidth, request.targetHeight)
    }.getOrNull() else null
    val blurredBitmap = if (blur.length > 0L) runCatching {
        HomeNativeGlassImageCache.decodeBitmap(request.blurCachePath)
    }.getOrNull() else null
    return CachedBackgroundBitmap(
        cacheKey = request.cacheKey,
        path = request.path,
        lastModified = source.lastModified,
        length = source.length,
        blurCachePath = request.blurCachePath,
        blurCacheLastModified = blur.lastModified,
        blurCacheLength = blur.length,
        blurPercent = request.blurPercent,
        targetWidth = request.targetWidth,
        targetHeight = request.targetHeight,
        bitmap = bitmap,
        blurredBitmap = blurredBitmap,
        memoryBytes = bitmapMemoryBytes(bitmap) + if (blurredBitmap === bitmap) 0L else bitmapMemoryBytes(blurredBitmap),
        metadataCheckedAt = 0L,
    )
}

private fun bitmapMemoryBytes(bitmap: Bitmap?): Long =
    if (bitmap == null) 0L else runCatching { bitmap.allocationByteCount.toLong() }.getOrDefault(0L)

private fun recycleUnpublishedBackground(entry: CachedBackgroundBitmap) {
    runCatching { entry.bitmap?.recycle() }
    if (entry.blurredBitmap !== entry.bitmap) runCatching { entry.blurredBitmap?.recycle() }
}
