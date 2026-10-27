package com.forbidad4tieba.hook.symbol.contract

import com.baidu.searchbox.player.BDVideoPlayer
import com.baidu.searchbox.player.kernel.AbsVideoKernel
import com.baidu.searchbox.player.kernel.EmptyKernel
import com.forbidad4tieba.hook.symbol.cache.HookSymbolCachePolicy
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.VideoDisableSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.function.Supplier

class VideoDisableContractTest {
    private val loader = javaClass.classLoader!!
    private val pcdn = "com.baidu.tieba.video.fixture.PcdnConfig#begin"
    private val dispatch = "com.baidu.tieba.play.TbVideoView#dispatchPlayback"
    private val full = VideoDisableCache(VideoDisableTarget.entries.associate { target ->
        val root = Class.forName(target.className, false, loader)
        val hierarchy = if (target.isStatic || target.isPrivate) listOf(root) else when (root) {
            BDVideoPlayer::class.java -> listOf(root, BeforeSuperPlayer::class.java, LeafPlayer::class.java)
            com.baidu.cyberplayer.sdk.DuMediaVideoView::class.java -> listOf(root,
                com.baidu.tieba.play.cyberPlayer.TbCyberVideoView::class.java, com.baidu.tieba.ala.TbLiveCyberView::class.java)
            else -> listOf(root)
        }
        val methods = hierarchy.flatMap { owner -> owner.declaredMethods.filter {
            it.name == target.methodName && target.matchesSignature(it.modifiers, it.returnType.name, it.parameterTypes.map { type -> type.name })
        } }.map { "${it.declaringClass.name}#${it.name}" }.sorted()
        target.name to VideoDisableFamily(hierarchy.map { it.name }.sorted(), methods)
    })

    private fun fixture(
        cache: VideoDisableCache = full,
        start: String? = pcdn,
        playback: String? = dispatch,
        ads: List<String> = PerformanceRuntimeTarget.entries.map { it.name },
        emptyKernel: String? = VideoDisableSymbolScanner.EMPTY_KERNEL_CONSTRUCTOR,
    ): HookSymbols = buildHookSymbols {
        source = "scan"
        createdAt = 1L
        this[VideoDisableContract.methods] = cache
        this[VideoDisableContract.pcdnStartMethod] = start
        this[VideoDisableContract.playDispatchMethod] = playback
        this[PerformanceContract.runtimeMethods] = ads
        this[VideoDisableContract.emptyKernelConstructor] = emptyKernel
    }

    @Test fun completeGroupRestoresRootsOverridesAndBothSemanticMembersWithoutInvocation() {
        val symbols = fixture()
        val restored = requireNotNull(VideoDisableContract.resolve(loader, symbols))
        assertEquals(21, restored.targets.size)
        assertEquals(VideoDisableTarget.entries.toSet(), restored.targets.keys)
        restored.targets.forEach { (target, methods) ->
            assertTrue(methods.isNotEmpty())
            methods.forEach { method ->
                assertTrue(Class.forName(target.className, false, loader).isAssignableFrom(method.declaringClass))
                assertEquals(target.methodName, method.name)
                assertTrue(target.matchesSignature(method.modifiers, method.returnType.name, method.parameterTypes.map { it.name }))
            }
        }
        assertEquals(3, restored.targets.getValue(VideoDisableTarget.BD_RESUME).size)
        assertEquals(3, restored.targets.getValue(VideoDisableTarget.CYBER_START).size)
        assertTrue(restored.targets.getValue(VideoDisableTarget.CYBER_SET_PATH).any {
            it.declaringClass == com.baidu.tieba.ala.TbLiveCyberView::class.java
        })
        assertEquals("begin", restored.pcdnStart.name)
        assertEquals("dispatchPlayback", restored.playDispatch.name)
        assertEquals(PerformanceRuntimeTarget.entries.toSet(), restored.adTargets.keys)
        assertEquals(EmptyKernel::class.java, restored.emptyKernelConstructor.declaringClass)
        assertEquals(listOf(VideoDisableTarget.FEED_PRELOAD_OPEN), restored.targets.keys.filter { it.blockedResult == VideoDisableResult.BOOLEAN_FALSE })
        assertEquals(HookFeatureState.FULL, VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
        assertTrue(VideoDisableContract.points(symbols).all { it.state == HookPointState.FOUND })
        assertEquals(24, VideoDisableContract.points(symbols).size)
        VideoDisableContract.points(symbols).forEach {
            assertEquals(listOf(HookFeatureKey.DISABLE_VIDEO_COMPONENTS), SymbolContracts.featuresForPoint(it.name))
        }
    }

    @Test fun videoRequiresAllSharedAdChannelsWhileTheAdCapabilityRemainsPartial() {
        for (missing in PerformanceRuntimeTarget.entries) {
            val symbols = fixture(ads = PerformanceRuntimeTarget.entries.filter { it != missing }.map { it.name })
            val video = VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS)
            assertEquals(HookFeatureState.DISABLED, video.state)
            assertEquals(listOf("PerformanceRuntime.${missing.name}"), video.missingCritical)
            assertTrue(video.missingOptional.isEmpty())
            assertNull(VideoDisableContract.resolve(loader, symbols))
            assertEquals(HookFeatureState.PARTIAL,
                PerformanceContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_AD_SDK_COMPONENTS).state)
            assertTrue(VideoDisableContract.points(symbols).all { it.state == HookPointState.FOUND })
            assertEquals(3, PerformanceContract.points(symbols).count { it.name.startsWith("PerformanceRuntime.AD_") })
        }
    }

    @Test fun everyMissingFamilyOrSemanticMemberDisablesTheWholeGroup() {
        val otherStates = PerformanceContract.capabilities(fixture())
        for (missing in full.families.keys) {
            val symbols = fixture(full.copy(families = full.families - missing))
            val status = VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS)
            assertEquals(missing, HookFeatureState.DISABLED, status.state)
            assertEquals(listOf("VideoDisable.$missing"), status.missingCritical)
            assertTrue(status.missingOptional.isEmpty())
            assertNull(missing, VideoDisableContract.resolve(loader, symbols))
            assertTrue("Partial scan remains diagnostic evidence: $missing", VideoDisableContract.isCacheValid(symbols, loader))
            assertEquals(otherStates, PerformanceContract.capabilities(symbols))
            assertEquals(23, VideoDisableContract.points(symbols).count { it.state == HookPointState.FOUND })
        }
        for ((symbols, missing) in listOf(fixture(start = null) to "PCDN_START", fixture(playback = null) to "PLAY_DISPATCH",
            fixture(emptyKernel = null) to "EMPTY_KERNEL_CONSTRUCTOR")) {
            val status = VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS)
            assertEquals(HookFeatureState.DISABLED, status.state)
            assertEquals(listOf("VideoDisable.$missing"), status.missingCritical)
            assertNull(VideoDisableContract.resolve(loader, symbols))
            assertTrue(VideoDisableContract.isCacheValid(symbols, loader))
        }
    }

    @Test fun aMissingOverrideCannotPassUsingTheBaseMethodAlone() {
        val target = VideoDisableTarget.BD_RESUME
        val family = full.families.getValue(target.name)
        assertEquals(3, family.methods.size)
        for (removed in family.methods) {
            val changed = family.copy(methods = family.methods - removed)
            val symbols = fixture(full.copy(families = full.families + (target.name to changed)))
            assertNull(removed, VideoDisableContract.resolve(loader, symbols))
            assertFalse(removed, VideoDisableContract.isCacheValid(symbols, loader))
        }
        val brokenHierarchy = family.copy(hierarchy = family.hierarchy - BeforeSuperPlayer::class.java.name)
        assertNull(VideoDisableContract.resolve(loader, fixture(full.copy(families = full.families + (target.name to brokenHierarchy)))))
        val reordered = family.copy(hierarchy = family.hierarchy.reversed(), methods = family.methods.reversed())
        assertEquals(VideoDisableSymbolScanner.restoreFamily(loader, target, family), VideoDisableSymbolScanner.restoreFamily(loader, target, reordered))
    }

    @Test fun anEmptyHostKernelPreservesConstructorInvariantsThatSkippingSetupBreaks() {
        val resolved = requireNotNull(VideoDisableContract.resolve(loader, fixture()))
        val factory = Supplier { resolved.emptyKernelConstructor.newInstance() as AbsVideoKernel }
        assertThrows(NullPointerException::class.java) { BDVideoPlayer.SkippedSetup(factory) }
        val first = BDVideoPlayer(factory)
        val second = BDVideoPlayer(factory)
        assertEquals(EmptyKernel::class.java, first.kernelLayer.kernel.javaClass)
        assertFalse(first.kernelLayer.isAcceptVolumeChange)
        assertNotSame(first.kernelLayer.kernel, second.kernelLayer.kernel)
        assertFalse(VideoDisableTarget.entries.any { it.methodName == "setupKernelLayer" || it.methodName == "attachKernelLayer" })
        assertEquals(listOf(VideoDisableTarget.DUMEDIA_CREATE_KERNEL),
            VideoDisableTarget.entries.filter { it.blockedResult == VideoDisableResult.EMPTY_KERNEL })
    }

    @Test fun corruptFamiliesMissingOwnersAndInvalidSemanticSignaturesNeverExposeAPartialInstallSet() {
        val first = VideoDisableTarget.FEED_PRELOAD_OPEN
        val family = full.families.getValue(first.name)
        for (cache in listOf(
            full.copy(families = full.families + ("UNKNOWN" to family)),
            full.copy(families = full.families + (first.name to family.copy(methods = family.methods + family.methods.first()))),
            full.copy(families = full.families + (first.name to family.copy(hierarchy = family.hierarchy + family.hierarchy.first()))),
        )) {
            val symbols = fixture(cache)
            assertEquals(HookFeatureState.DISABLED, VideoDisableContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
            assertNull(VideoDisableContract.resolve(loader, symbols))
            assertFalse(VideoDisableContract.isCacheValid(symbols, loader))
        }
        for (owner in full.families.values.flatMap { it.hierarchy }.distinct()) {
            val missing = object : ClassLoader(loader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name == owner) throw ClassNotFoundException(name)
                    return super.loadClass(name, resolve)
                }
            }
            assertNull(owner, VideoDisableContract.resolve(missing, fixture()))
            assertFalse(owner, VideoDisableContract.isCacheValid(fixture(), missing))
        }
        for (start in listOf("bad", "com.baidu.tieba.video.fixture.PcdnConfig#instance", "com.baidu.tieba.video.fixture.PcdnConfig#wrongReturn")) {
            assertNull(start, VideoDisableContract.resolve(loader, fixture(start = start)))
            assertFalse(start, VideoDisableContract.isCacheValid(fixture(start = start), loader))
        }
        for (playback in listOf("bad", "com.baidu.tieba.video.fixture.PcdnConfig#begin", "com.baidu.tieba.play.TbVideoView#start")) {
            assertNull(playback, VideoDisableContract.resolve(loader, fixture(playback = playback)))
            assertFalse(playback, VideoDisableContract.isCacheValid(fixture(playback = playback), loader))
        }
        assertNull(VideoDisableContract.resolve(loader, fixture(emptyKernel = "invalid")))
        assertFalse(VideoDisableContract.isCacheValid(fixture(emptyKernel = "invalid"), loader))
        val missingEmptyKernel = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == EmptyKernel::class.java.name) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertNull(VideoDisableContract.resolve(missingEmptyKernel, fixture()))
        assertFalse(VideoDisableContract.isCacheValid(fixture(), missingEmptyKernel))
    }

    @Test fun cacheRoundTripPreservesFamiliesAndRejectsReleasedPriorRulesAndOldRootOnlyFormat() {
        val expected = fixture()
        val restored = requireNotNull(HookSymbols.fromJson(expected.toJson()))
        assertEquals(expected, restored)
        assertEquals(VideoDisableContract.points(expected), VideoDisableContract.points(restored))
        assertNotNull(VideoDisableContract.resolve(loader, restored))
        assertTrue(HookSymbolCachePolicy.isUsable(restored))
        for (rule in listOf(81, 82)) {
            val old = requireNotNull(HookSymbols.fromJson(JSONObject(expected.toJson()).put("dexKitRuleVersion", rule).toString()))
            assertFalse("old rule=$rule", HookSymbolCachePolicy.isUsable(old))
        }
        val rootOnly = requireNotNull(HookSymbols.fromJson(JSONObject(expected.toJson())
            .put("videoDisableMethods", JSONArray(VideoDisableTarget.entries.map { it.name })).toString()))
        assertNull(VideoDisableContract.resolve(loader, rootOnly))
        val empty = fixture(VideoDisableCache(), null, null)
        assertNull(VideoDisableContract.resolve(loader, empty))
        assertTrue(VideoDisableContract.isCacheValid(empty, loader))
        assertEquals(HookFeatureState.DISABLED, VideoDisableContract.capabilities(empty).getValue(HookFeatureKey.DISABLE_VIDEO_COMPONENTS).state)
    }

    open class BeforeSuperPlayer : BDVideoPlayer() {
        override fun doPlay() { throw AssertionError("subclass progress work after super") }
        override fun start() { throw AssertionError("subclass dispatch to resume") }
        override fun resume() { throw AssertionError("subclass progress work") }
    }
    class LeafPlayer : BeforeSuperPlayer() {
        override fun resume() { throw AssertionError("second-level override") }
    }
}
