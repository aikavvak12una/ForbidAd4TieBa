package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.VideoDisableContract
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.model.*
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

class BackgroundProducerRulesTest {
    private val loader = javaClass.classLoader!!
    private val names = listOf("AD_CSJ", "AD_GDT", "AD_BAIDU")
    private val video = VideoDisableCache(VideoDisableTarget.entries.associate {
        it.name to VideoDisableFamily(listOf(it.className), listOf("${it.className}#${it.methodName}"))
    })
    private fun videoSymbols(cache: VideoDisableCache = video) = buildHookSymbols {
        this[VideoDisableContract.methods] = cache
        this[VideoDisableContract.pcdnStartMethod] = "com.baidu.tieba.video.fixture.PcdnConfig#begin"
    }

    @Test fun adTargetsRequireOneLiveConsumerAndTheirOwnChannelAnchor() {
        for (target in PerformanceRuntimeTarget.entries) {
            val strings = setOfNotNull(target.semanticKey)
            val consumer = setOf("host.InitializationConsumer")
            assertTrue(PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, consumer, strings))
            assertFalse(PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 0, consumer, strings))
            assertFalse(PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 2, consumer, strings))
            assertFalse(PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, setOf(target.className), strings))
            assertFalse(PerformanceRuntimeSymbolScanner.hasRequiredEvidence(target, 1, consumer, emptySet()))
        }
    }

    @Test fun oneAdOwnerFailureRetainsTheOtherSdkTargets() {
        val broken = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == PerformanceRuntimeTarget.AD_GDT.className) throw LinkageError("fixture")
                return super.loadClass(name, resolve)
            }
        }
        assertEquals(setOf(PerformanceRuntimeTarget.AD_CSJ, PerformanceRuntimeTarget.AD_BAIDU),
            PerformanceRuntimeSymbolScanner.restore(broken, names).keys)
        assertFalse(PerformanceRuntimeSymbolScanner.isCacheValid(broken, names))
        assertFalse(PerformanceRuntimeSymbolScanner.isCacheValid(loader, names + "AD_CSJ"))
        assertFalse(PerformanceRuntimeSymbolScanner.isCacheValid(loader, names + "unknown"))
    }

    @Test fun abCacheRejectsUnknownAndDuplicateMembers() {
        val members = listOf("cookieRepeatedOpt", "isPbCommentFunAdABTest", "isPbPageBannerFunAdSdkTest")
        assertEquals(members.toSet(), PerformanceAbSymbolScanner.restore(loader, members).keys)
        assertFalse(PerformanceAbSymbolScanner.isCacheValid(loader, members + "unknown"))
        assertFalse(PerformanceAbSymbolScanner.isCacheValid(loader, members + "cookieRepeatedOpt"))
    }

    @Test fun videoNeedsEveryProducerAndKeepsPartialDiscoveryNegativeCached() {
        val complete = videoSymbols()
        assertEquals(setOf(VideoDisableTarget.FEED_PRELOAD_OPEN, VideoDisableTarget.PREBOOT_DELIVER, VideoDisableTarget.PREBOOT_WORK),
            requireNotNull(VideoDisableContract.resolve(loader, complete)).targets.keys)
        for (missing in listOf("FEED_PRELOAD_OPEN", "PREBOOT_DELIVER", "PREBOOT_WORK")) {
            val partial = videoSymbols(video.copy(families = video.families - missing))
            assertNull(VideoDisableContract.resolve(loader, partial))
            assertTrue(VideoDisableContract.isCacheValid(partial, loader))
            assertEquals(listOf("VideoDisable.$missing"), VideoDisableContract.capabilities(partial)
                .getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).missingCritical)
            assertEquals(PerformanceContract.capabilities(complete), PerformanceContract.capabilities(partial))
        }
        val family = video.families.getValue("FEED_PRELOAD_OPEN")
        val duplicate = videoSymbols(video.copy(families = video.families +
            ("FEED_PRELOAD_OPEN" to family.copy(methods = family.methods + family.methods))))
        assertFalse(VideoDisableContract.isCacheValid(duplicate, loader))
        assertNull(VideoDisableContract.resolve(loader, duplicate))
    }

    @Test fun pcdnUniquenessAndVirtualWorkDispatchHaveDifferentEvidenceRules() {
        assertNull(VideoDisableSymbolScanner.uniquePcdnOwner(emptyList()))
        assertEquals("owner", VideoDisableSymbolScanner.uniquePcdnOwner(listOf("owner", "owner")))
        assertNull(VideoDisableSymbolScanner.uniquePcdnOwner(listOf("owner", "other")))
        assertEquals("owner#start", VideoDisableSymbolScanner.uniquePcdnStart(listOf("owner#start")))
        assertNull(VideoDisableSymbolScanner.uniquePcdnStart(listOf("owner#start", "owner#start")))
        assertNull(VideoDisableSymbolScanner.uniquePcdnStart(listOf("owner#start", "owner#other")))
        val work = VideoDisableTarget.PREBOOT_WORK
        assertTrue(work.hasConsumer(1, 0))
        assertFalse(work.hasConsumer(0, 0))
        assertTrue(VideoDisableSymbolScanner.isVirtualDispatchSignature(work, Modifier.PUBLIC or Modifier.ABSTRACT, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(work, Modifier.PUBLIC or Modifier.STATIC, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(work, Modifier.PRIVATE, "void", emptyList()))
        assertFalse(VideoDisableSymbolScanner.isVirtualDispatchSignature(VideoDisableTarget.PREBOOT_DELIVER,
            Modifier.PUBLIC, "void", VideoDisableTarget.PREBOOT_DELIVER.parameterTypeNames))
    }
}
