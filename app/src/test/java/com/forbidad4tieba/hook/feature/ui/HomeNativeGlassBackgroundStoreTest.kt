package com.forbidad4tieba.hook.feature.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class HomeNativeGlassBackgroundStoreTest {
    private val request = BackgroundRequest("source", "blur", 30, 500, 500)
    private val metadata = BackgroundFileMetadata(1L, 100L)

    private fun entry(request: BackgroundRequest, source: BackgroundFileMetadata, blur: BackgroundFileMetadata,
                      bytes: Long = 10L) = CachedBackgroundBitmap(
        request.cacheKey, request.path, source.lastModified, source.length,
        request.blurCachePath, blur.lastModified, blur.length, request.blurPercent,
        request.targetWidth, request.targetHeight, null, null, bytes, 0L,
    )

    @Test fun cacheReadsNeverReadMetadataEvenAfterTheCheckInterval() {
        var now = 0L
        var reads = 0
        var decodes = 0
        val store = HomeNativeGlassBackgroundStore(100L, 1500L, { now },
            { reads++; metadata }, { r, source, blur -> decodes++; entry(r, source, blur) })
        val loaded = store.refreshOnWorker(request, store.generation()) { true }!!
        val readsAfterLoad = reads
        now = 3000L
        repeat(100) { assertSame(loaded.entry, store.find(request)) }
        assertTrue(store.needsMetadataCheck(loaded.entry))
        assertEquals(readsAfterLoad, reads)
        assertFalse(store.refreshOnWorker(request, store.generation()) { true }!!.changed)
        assertEquals(1, decodes)
        assertFalse(store.needsMetadataCheck(loaded.entry))
    }

    @Test fun aBlockedMetadataReadDoesNotHoldTheMemoryCacheLock() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var block = false
        val store = HomeNativeGlassBackgroundStore(100L, 1500L, { 0L }, {
            if (block) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            metadata
        }, { r, source, blur -> entry(r, source, blur) })
        val loaded = store.refreshOnWorker(request, store.generation()) { true }!!.entry
        block = true
        val workers = Executors.newFixedThreadPool(2)
        try {
            val refresh = workers.submit { store.refreshOnWorker(request, store.generation()) { true } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertSame(loaded, workers.submit<CachedBackgroundBitmap?> { store.find(request) }.get(1, TimeUnit.SECONDS))
            release.countDown()
            refresh.get(2, TimeUnit.SECONDS)
        } finally { release.countDown(); workers.shutdownNow() }
    }

    @Test fun clearingDuringDecodePreventsCacheRepopulation() {
        lateinit var store: HomeNativeGlassBackgroundStore
        store = HomeNativeGlassBackgroundStore(100L, 1500L, { 0L }, { metadata }, { r, source, blur ->
            store.clear()
            entry(r, source, blur)
        })
        assertNull(store.refreshOnWorker(request, store.generation()) { true })
        assertNull(store.find(request))
    }

    @Test fun changedStyleDiscardsItsInFlightResult() {
        var current = true
        val store = HomeNativeGlassBackgroundStore(100L, 1500L, { 0L }, { metadata }, { r, source, blur ->
            current = false
            entry(r, source, blur)
        })
        assertNull(store.refreshOnWorker(request, store.generation()) { current })
        assertNull(store.find(request))
    }

    @Test fun changedFileDuringDecodeIsNotPublished() {
        var version = metadata
        val store = HomeNativeGlassBackgroundStore(100L, 1500L, { 0L }, { version }, { r, source, blur ->
            version = BackgroundFileMetadata(2L, 200L)
            entry(r, source, blur)
        })
        assertNull(store.refreshOnWorker(request, store.generation()) { true })
        assertNull(store.find(request))
    }

    @Test fun blurChangeInvalidatesReusableEntriesWhenTheSourceHasNotChanged() {
        var blurVersion = metadata
        var decodes = 0
        val store = HomeNativeGlassBackgroundStore(100L, 1500L, { 0L },
            { if (it == "blur") blurVersion else metadata },
            { r, source, blur -> decodes++; entry(r, source, blur) })
        val wide = store.refreshOnWorker(request, store.generation()) { true }!!.entry
        val small = request.copy(targetWidth = 200, targetHeight = 200)
        assertSame(wide, store.find(small))
        assertFalse(store.refreshOnWorker(small, store.generation()) { true }!!.changed)
        assertEquals(1, decodes)
        blurVersion = BackgroundFileMetadata(2L, 200L)
        assertTrue(store.refreshOnWorker(small, store.generation()) { true }!!.changed)
        assertEquals(2, decodes)
        assertNull(store.find(request))
    }

    @Test fun strongCacheBudgetIncludesAnOversizedSingleEntry() {
        val store = HomeNativeGlassBackgroundStore(15L, 1500L, { 0L }, { metadata },
            { r, source, blur -> entry(r, source, blur, if (r.path == "huge") 20L else 10L) })
        val first = store.refreshOnWorker(request, store.generation()) { true }!!.entry
        store.refreshOnWorker(request.copy(path = "second"), store.generation()) { true }
        assertEquals(10L, store.retainedBytes())
        // Dropping a cache reference does not destroy a result still owned by its consumer.
        assertSame(first, store.find(request))
        val huge = request.copy(path = "huge")
        val borrowed = store.refreshOnWorker(huge, store.generation()) { true }!!.entry
        assertEquals(0L, store.retainedBytes())
        assertSame(borrowed, store.find(huge))
        store.clear()
        assertNull(store.find(huge))
    }
}
