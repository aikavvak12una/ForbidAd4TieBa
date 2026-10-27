package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.cache.HookSymbolCachePolicy
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VideoDisableContractTest {
    private val loader = javaClass.classLoader!!
    private val pcdn = "com.baidu.tieba.video.fixture.PcdnConfig#begin"
    private val full = VideoDisableCache(VideoDisableTarget.entries.associate {
        it.name to VideoDisableFamily(listOf(it.className), listOf("${it.className}#${it.methodName}"))
    })
    private fun fixture(cache: VideoDisableCache = full, start: String? = pcdn) = buildHookSymbols {
        source = "scan"
        createdAt = 1L
        this[VideoDisableContract.methods] = cache
        this[VideoDisableContract.pcdnStartMethod] = start
    }

    @Test fun onlyBackgroundProducersAreRestoredWithoutPlaybackOrAdDependencies() {
        val symbols = fixture()
        val restored = requireNotNull(VideoDisableContract.resolve(loader, symbols))
        assertEquals(setOf(VideoDisableTarget.FEED_PRELOAD_OPEN, VideoDisableTarget.PREBOOT_DELIVER, VideoDisableTarget.PREBOOT_WORK), restored.targets.keys)
        assertEquals("begin", restored.pcdnStart.name)
        assertTrue(VideoDisableContract.points(symbols).all { it.state == HookPointState.FOUND })
        assertEquals(HookFeatureState.FULL, VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
        assertTrue(VideoDisableContract.isCacheValid(symbols, loader))
    }

    @Test fun eachMissingProducerDisablesOnlyVideoAndKeepsNegativeCacheValid() {
        val other = PerformanceContract.capabilities(fixture())
        for (missing in full.families.keys) {
            val symbols = fixture(full.copy(families = full.families - missing))
            assertNull(VideoDisableContract.resolve(loader, symbols))
            assertTrue(VideoDisableContract.isCacheValid(symbols, loader))
            assertEquals(listOf("VideoDisable.$missing"), VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).missingCritical)
            assertEquals(other, PerformanceContract.capabilities(symbols))
        }
        assertNull(VideoDisableContract.resolve(loader, fixture(start = null)))
        assertTrue(VideoDisableContract.isCacheValid(fixture(VideoDisableCache(), null), loader))
    }

    @Test fun staleUnknownDuplicateAndStructurallyInvalidCacheCannotInstall() {
        val target = VideoDisableTarget.FEED_PRELOAD_OPEN
        val family = full.families.getValue(target.name)
        for (changed in listOf(
            full.copy(families = full.families + ("CYBER_INIT" to family)),
            full.copy(families = full.families + (target.name to family.copy(methods = family.methods + family.methods))),
            full.copy(families = full.families + (target.name to family.copy(hierarchy = listOf("missing.Owner")))),
            full.copy(families = full.families + (target.name to family.copy(methods = listOf("${target.className}#wrong")))),
        )) {
            assertNull(VideoDisableContract.resolve(loader, fixture(changed)))
            assertFalse(VideoDisableContract.isCacheValid(fixture(changed), loader))
        }
        for (descriptor in listOf("bad", "com.baidu.tieba.video.fixture.PcdnConfig#instance", "com.baidu.tieba.video.fixture.PcdnConfig#wrongReturn")) {
            assertNull(VideoDisableContract.resolve(loader, fixture(start = descriptor)))
            assertFalse(VideoDisableContract.isCacheValid(fixture(start = descriptor), loader))
        }
    }

    @Test fun cacheRoundTripPreservesTargetsAndRejectsThePreviousFullDisableRule() {
        val original = fixture()
        val restored = requireNotNull(HookSymbols.fromJson(original.toJson()))
        assertEquals(VideoDisableContract.points(original), VideoDisableContract.points(restored))
        assertNotNull(VideoDisableContract.resolve(loader, restored))
        val old = requireNotNull(HookSymbols.fromJson(JSONObject(original.toJson()).put("dexKitRuleVersion", 84).toString()))
        assertFalse(HookSymbolCachePolicy.isUsable(old))
        assertFalse(original.toJson().contains("videoDisableEmptyKernelConstructor"))
        assertFalse(original.toJson().contains("videoDisablePlayDispatch"))
    }
}
