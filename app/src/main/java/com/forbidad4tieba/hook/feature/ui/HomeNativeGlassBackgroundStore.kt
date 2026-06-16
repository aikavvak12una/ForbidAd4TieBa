package com.forbidad4tieba.hook.feature.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.LinkedHashMap

internal class HomeNativeGlassBackgroundStore(
    private val maxCachedBytes: Long,
    private val metadataCheckIntervalMs: Long,
) {
    private val cachedBackgroundBitmaps = LinkedHashMap<String, CachedBackgroundBitmap>(4, 0.75f, true)

    fun find(request: BackgroundRequest): CachedBackgroundBitmap? {
        val cached = synchronized(cachedBackgroundBitmaps) {
            cachedBackgroundBitmaps[request.cacheKey]
                ?: cachedBackgroundBitmaps.values.firstOrNull { it.matches(request) }?.also {
                    // Refresh access-order recency for wider reusable entries.
                    cachedBackgroundBitmaps[it.cacheKey]
                }
        } ?: return null
        return cached.takeIf { isMetadataCurrent(it) }
    }

    fun decode(request: BackgroundRequest): CachedBackgroundBitmap {
        val file = File(request.path)
        val lastModified = runCatching { file.lastModified() }.getOrDefault(0L)
        val length = runCatching { file.length() }.getOrDefault(0L)
        val blurCacheFile = File(request.blurCachePath)
        val blurCacheLastModified = runCatching { blurCacheFile.lastModified() }.getOrDefault(0L)
        val blurCacheLength = runCatching { blurCacheFile.length() }.getOrDefault(0L)
        find(request)?.let { cached ->
            if (cached.lastModified == lastModified && cached.length == length) return cached
        }

        val canDecode = runCatching { file.isFile && length > 0L }.getOrDefault(false)
        val bitmap = if (canDecode) {
            runCatching {
                HomeNativeGlassImageCache.decodeSampledBitmap(
                    request.path,
                    request.targetWidth,
                    request.targetHeight,
                )
            }.getOrNull()
        } else {
            null
        }
        val blurredBitmap = if (
            request.blurCachePath.isNotBlank() &&
            runCatching { blurCacheFile.isFile && blurCacheLength > 0L }.getOrDefault(false)
        ) {
            HomeNativeGlassImageCache.decodeBitmap(request.blurCachePath)
        } else {
            null
        }
        val entry = CachedBackgroundBitmap(
            cacheKey = request.cacheKey,
            path = request.path,
            lastModified = lastModified,
            length = length,
            blurCachePath = request.blurCachePath,
            blurCacheLastModified = blurCacheLastModified,
            blurCacheLength = blurCacheLength,
            blurPercent = request.blurPercent,
            targetWidth = request.targetWidth,
            targetHeight = request.targetHeight,
            bitmap = bitmap,
            blurredBitmap = blurredBitmap,
            memoryBytes = entryMemoryBytes(bitmap, blurredBitmap),
            metadataCheckedAt = SystemClock.uptimeMillis(),
        )
        return store(entry)
    }

    private fun isMetadataCurrent(cached: CachedBackgroundBitmap): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - cached.metadataCheckedAt < metadataCheckIntervalMs) {
            return true
        }
        return synchronized(cachedBackgroundBitmaps) {
            if (cachedBackgroundBitmaps[cached.cacheKey] !== cached) return@synchronized false
            val source = readFileMetadata(cached.path)
            val blurCache = readFileMetadata(cached.blurCachePath)
            cached.metadataCheckedAt = now
            val fresh = cached.lastModified == source.lastModified &&
                cached.length == source.length &&
                cached.blurCacheLastModified == blurCache.lastModified &&
                cached.blurCacheLength == blurCache.length
            if (!fresh) {
                cachedBackgroundBitmaps.remove(cached.cacheKey)
            }
            fresh
        }
    }

    private fun readFileMetadata(path: String): BackgroundFileMetadata {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return BackgroundFileMetadata(0L, 0L)
        return runCatching {
            val file = File(trimmed)
            if (file.isFile) {
                BackgroundFileMetadata(file.lastModified(), file.length())
            } else {
                BackgroundFileMetadata(0L, 0L)
            }
        }.getOrDefault(BackgroundFileMetadata(0L, 0L))
    }

    private fun store(entry: CachedBackgroundBitmap): CachedBackgroundBitmap {
        return synchronized(cachedBackgroundBitmaps) {
            val existing = cachedBackgroundBitmaps.values.firstOrNull { cached ->
                cached.path == entry.path &&
                    cached.lastModified == entry.lastModified &&
                    cached.length == entry.length &&
                    cached.blurCachePath == entry.blurCachePath &&
                    cached.blurCacheLastModified == entry.blurCacheLastModified &&
                    cached.blurCacheLength == entry.blurCacheLength &&
                    cached.blurPercent == entry.blurPercent &&
                    cached.targetWidth >= entry.targetWidth &&
                    cached.targetHeight >= entry.targetHeight
            }
            if (existing != null) {
                // Refresh access-order recency before returning the reusable entry.
                cachedBackgroundBitmaps[existing.cacheKey]
                recycleEntryBitmaps(entry)
                return@synchronized existing
            }
            cachedBackgroundBitmaps[entry.cacheKey] = entry
            trimLocked()
            entry
        }
    }

    private fun trimLocked() {
        var totalBytes = 0L
        cachedBackgroundBitmaps.values.forEach { entry ->
            totalBytes += entry.memoryBytes
        }
        val iterator = cachedBackgroundBitmaps.entries.iterator()
        while (
            totalBytes > maxCachedBytes &&
            cachedBackgroundBitmaps.size > 1 &&
            iterator.hasNext()
        ) {
            val entry = iterator.next()
            totalBytes -= entry.value.memoryBytes
            iterator.remove()
        }
    }

    private fun entryMemoryBytes(bitmap: Bitmap?, blurredBitmap: Bitmap?): Long {
        val bitmapBytes = bitmapMemoryBytes(bitmap)
        val blurredBytes = if (blurredBitmap === bitmap) 0L else bitmapMemoryBytes(blurredBitmap)
        return bitmapBytes + blurredBytes
    }

    private fun bitmapMemoryBytes(bitmap: Bitmap?): Long {
        if (bitmap == null) return 0L
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                bitmap.allocationByteCount.toLong()
            } else {
                bitmap.byteCount.toLong()
            }
        }.getOrDefault(0L)
    }

    private fun CachedBackgroundBitmap.matches(request: BackgroundRequest): Boolean {
        return path == request.path &&
            blurCachePath == request.blurCachePath &&
            blurPercent == request.blurPercent &&
            targetWidth >= request.targetWidth &&
            targetHeight >= request.targetHeight
    }

    private fun recycleEntryBitmaps(entry: CachedBackgroundBitmap) {
        runCatching { entry.bitmap?.recycle() }
        if (entry.blurredBitmap !== entry.bitmap) {
            runCatching { entry.blurredBitmap?.recycle() }
        }
    }
}
