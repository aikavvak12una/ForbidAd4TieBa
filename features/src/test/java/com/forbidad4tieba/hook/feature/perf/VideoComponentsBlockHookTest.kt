package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.model.VideoDisableResult
import com.forbidad4tieba.hook.symbol.model.VideoDisableSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class VideoComponentsBlockHookTest {
    private val symbols = VideoDisableSymbols(
        VideoDisableTarget.entries.associateWith { point ->
            when (point) {
                VideoDisableTarget.DUMEDIA_CREATE_KERNEL -> listOf(KernelFactory::class.java.getDeclaredMethod("create", String::class.java))
                VideoDisableTarget.BD_DO_PLAY -> playerMethods("doPlay")
                VideoDisableTarget.BD_START -> playerMethods("start")
                VideoDisableTarget.BD_RESUME -> playerMethods("resume")
                VideoDisableTarget.TB_START -> listOf(VideoViewFixture::class.java.getDeclaredMethod("start"))
                else -> listOf(method(if (point.blockedResult == VideoDisableResult.BOOLEAN_FALSE) "feedGate" else "point${point.ordinal}"))
            }
        },
        method("pcdn"),
        VideoViewFixture::class.java.getDeclaredMethod("dispatch", Int::class.javaPrimitiveType),
        mapOf(
            PerformanceRuntimeTarget.AD_CSJ to method("adCsj"),
            PerformanceRuntimeTarget.AD_GDT to method("adGdt"),
            PerformanceRuntimeTarget.AD_BAIDU to method("adBaidu"),
        ),
        EmptyKernel::class.java.getDeclaredConstructor(),
    )
    private val allMembers get() = (symbols.targets.values.flatten() + symbols.pcdnStart + symbols.playDispatch).distinct()
    private val apis = ArrayList<FakeApi>()

    @After fun release() {
        apis.forEach { it.failUnhook.clear() }
        val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(owned.rollback().isEmpty())
        VideoComponentsBlockHook::class.java.getDeclaredField("installation").apply { isAccessible = true }
            .set(null, null)
        (AdSdkInitBlockHook::class.java.getDeclaredField("installed").apply { isAccessible = true }
            .get(null) as MutableMap<*, *>).clear()
        AdSdkInitBlockHook::class.java.getDeclaredField("prepared").apply { isAccessible = true }.set(null, null)
        apis.clear()
    }

    @Test fun noPointInstallsWhenAnyRequiredTargetIsUnavailable() {
        val api = api()
        for (missing in VideoDisableTarget.entries) {
            val outcome = install(api, symbols.copy(targets = symbols.targets - missing))
            assertEquals(missing.name, InstallState.SKIPPED, outcome.state)
            assertEquals(0, outcome.installedCount)
            assertEquals(0, api.created)
            val emptyFamily = install(api, symbols.copy(targets = symbols.targets + (missing to emptyList())))
            assertEquals(missing.name, InstallState.SKIPPED, emptyFamily.state)
            assertEquals(0, api.created)
        }
        assertEquals(InstallState.SKIPPED, install(api, null).state)
        assertEquals(0, api.created)
    }

    @Test fun groupActivatesOnlyAfterEveryHandleExistsAndDisablingRestoresEveryOriginal() {
        val api = api()
        var enabled = false
        assertEquals(InstallState.SKIPPED, install(api, enabled = { enabled }).state)
        assertEquals(0, api.created)
        enabled = true
        api.afterInstall = { api.callbacks.values.forEach(::assertPassesThrough) }
        val outcome = install(api, enabled = { enabled })
        assertEquals(InstallState.INSTALLED, outcome.state)
        assertEquals(allMembers.size, outcome.installedCount)
        for ((point, family) in symbols.targets) {
            for (member in family) {
                var calls = 0
                val result = api.invoke(member) { calls++; originalValue(member) }
                when (point.blockedResult) {
                    VideoDisableResult.BOOLEAN_FALSE -> assertEquals(point.name, false, result)
                    VideoDisableResult.VOID -> assertNull(point.name, result)
                    VideoDisableResult.EMPTY_KERNEL -> assertTrue(point.name, result is EmptyKernel)
                }
                assertEquals(0, calls)
            }
        }
        assertNull(api.invoke(symbols.pcdnStart) { error("PCDN producer ran") })
        assertNull(api.invoke(symbols.playDispatch) { error("play dispatch ran") })
        enabled = false
        api.callbacks.values.forEach(::assertPassesThrough)
        enabled = true
        val repeated = install(api, enabled = { enabled })
        assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
        assertEquals(allMembers.size, repeated.installedCount)
        assertEquals(allMembers.size, api.created)
    }

    @Test fun failureAtEachPointRollsBackTheWholeInactiveGroupAndCanRetry() {
        for (failed in allMembers) {
            val api = api().apply {
                failInstall += failed
                afterInstall = { callbacks.values.forEach(::assertPassesThrough) }
            }
            try {
                val failure = install(api)
                assertEquals(failed.name, InstallState.ROLLED_BACK, failure.state)
                assertEquals(0, failure.installedCount)
                assertTrue(api.callbacks.isEmpty())
                api.snapshots.forEach(::assertPassesThrough)
                val oldCount = api.created
                api.failInstall.clear()
                val recovered = install(api)
                assertEquals(InstallState.INSTALLED, recovered.state)
                assertEquals(allMembers.size, recovered.installedCount)
                assertEquals(oldCount + allMembers.size, api.created)
                val repeated = install(api)
                assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
                assertEquals(allMembers.size, repeated.installedCount)
                assertEquals(oldCount + allMembers.size, api.created)
            } finally {
                release()
            }
        }
    }

    @Test fun aFailedRollbackLeavesInertHandlesAndMustFinishBeforeRetryInstallation() {
        val api = api()
        val retained = symbols.targets.values.first().first()
        api.failInstall += symbols.pcdnStart
        api.failUnhook += retained
        val failure = install(api)
        assertEquals(InstallState.ROLLBACK_FAILED, failure.state)
        assertEquals(1, failure.installedCount)
        assertEquals(setOf(retained), api.callbacks.keys)
        api.snapshots.forEach(::assertPassesThrough)
        val oldCallbacks = api.snapshots.toList()
        val oldCount = api.created
        api.failInstall.clear()
        val stillBlocked = install(api)
        assertEquals(InstallState.ROLLBACK_FAILED, stillBlocked.state)
        assertEquals(1, stillBlocked.installedCount)
        assertEquals(oldCount, api.created)
        api.failUnhook.clear()
        val recovered = install(api)
        assertEquals(InstallState.INSTALLED, recovered.state)
        assertEquals(allMembers.size, recovered.installedCount)
        assertEquals(oldCount + allMembers.size, api.created)
        oldCallbacks.forEach(::assertPassesThrough)
        assertEquals(false, api.invoke(retained) { error("recovered gate was not active") })
    }

    @Test fun losingCompleteTargetsInvalidatesAnExistingGroup() {
        val api = api()
        assertEquals(InstallState.INSTALLED, install(api).state)
        val staleCallbacks = api.snapshots.toList()
        val unavailable = install(api, null)
        assertEquals(InstallState.SKIPPED, unavailable.state)
        assertEquals(0, unavailable.installedCount)
        assertTrue(api.callbacks.isEmpty())
        staleCallbacks.forEach(::assertPassesThrough)
    }

    @Test fun overrideBodiesAndDirectDispatchAreStoppedBeforeTheirOuterSideEffects() {
        val api = api()
        var enabled = true
        assertEquals(InstallState.INSTALLED, install(api, enabled = { enabled }).state)
        val player = DerivedPlayer()
        val view = VideoViewFixture()
        val overrideMethods = listOf("doPlay", "start", "resume").map {
            DerivedPlayer::class.java.getDeclaredMethod(it)
        }
        for (member in overrideMethods) {
            assertTrue(api.callbacks.containsKey(member))
            api.invoke(member) { member.invoke(player); null }
        }
        val start = VideoViewFixture::class.java.getDeclaredMethod("start")
        api.invoke(start) { view.start(); null }
        api.invoke(symbols.playDispatch) { view.dispatch(1); null }
        assertEquals(0, player.progressStarts)
        assertEquals(0, player.plays)
        assertEquals(0, view.audioFocusRequests)
        assertEquals(0, view.wakeLocks)

        enabled = false
        for (member in overrideMethods) api.invoke(member) { member.invoke(player); null }
        api.invoke(start) { view.start(); null }
        api.invoke(symbols.playDispatch) { view.dispatch(1); null }
        assertEquals(3, player.progressStarts)
        assertEquals(3, player.plays)
        assertEquals(2, view.audioFocusRequests)
        assertEquals(2, view.wakeLocks)
    }

    @Test fun constructionGetsANonNullIndependentLightweightKernelAndOffRestoresTheFactory() {
        val api = api()
        var enabled = true
        val beforeInstall = EmptyKernel.created
        assertEquals(InstallState.INSTALLED, install(api, enabled = { enabled }).state)
        assertEquals("installation must not create an early video session", beforeInstall, EmptyKernel.created)
        val create = KernelFactory::class.java.getDeclaredMethod("create", String::class.java)
        assertEquals(XposedInterface.ExceptionMode.PASSTHROUGH, api.exceptionModes[create])
        val factory = KernelFactory()
        fun construct() = RequiresNonNullLayer {
            api.invoke(create) { factory.create("fixture") } as FixtureKernel?
        }
        val first = construct()
        val second = construct()
        assertNotNull(first.layer)
        assertTrue(first.layer.kernel is EmptyKernel)
        assertTrue(second.layer.kernel is EmptyKernel)
        assertNotSame(first.layer.kernel, second.layer.kernel)
        assertFalse(first.layer.acceptVolumeChange)
        assertEquals(0, factory.realKernelCreates)
        assertEquals(beforeInstall + 2, EmptyKernel.created)
        enabled = false
        val restored = construct()
        assertTrue(restored.layer.kernel is RealKernel)
        assertFalse(restored.layer.acceptVolumeChange)
        assertEquals(1, factory.realKernelCreates)
        assertEquals(beforeInstall + 2, EmptyKernel.created)
    }

    @Test fun duplicateMethodsAcrossFamiliesAndSemanticPointsHaveOnlyOneHandle() {
        val api = api()
        val duplicatedFamilies = symbols.targets.mapValues { (_, family) -> family + family }.toMutableMap()
        duplicatedFamilies[VideoDisableTarget.CYBER_INIT] = duplicatedFamilies.getValue(VideoDisableTarget.CYBER_INIT) + symbols.pcdnStart
        duplicatedFamilies[VideoDisableTarget.TB_START] = duplicatedFamilies.getValue(VideoDisableTarget.TB_START) + symbols.playDispatch
        val duplicated = symbols.copy(targets = duplicatedFamilies)
        val outcome = install(api, duplicated)
        assertEquals(InstallState.INSTALLED, outcome.state)
        assertEquals(allMembers.size, outcome.installedCount)
        assertEquals(allMembers.toSet(), api.callbacks.keys)
        assertEquals(allMembers.size, api.created)
        // The logical grouping/order differs, but the actual method set is unchanged.
        val repeated = install(api, symbols)
        assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
        assertEquals(allMembers.size, repeated.installedCount)
        assertEquals(allMembers.size, api.created)
    }

    @Test fun videoAdSuppressionWaitsForSuccessfulGroupAndKeepsTheOriginalAdChoice() {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshotField.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        try {
            for (adChoice in listOf(false, true)) {
                val api = api()
                XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
                val selected = SettingsSnapshot.bootstrap().copy(
                    isVideoComponentsDisabled = true, isAdSdkComponentsDisabled = adChoice,
                )
                snapshotField.set(ConfigManager, selected)
                fun prepareAds() = RuntimeHooks.measure("AdSdkInitBlockHook") { AdSdkInitBlockHook.hook(symbols.adTargets) }
                fun installVideo() = RuntimeHooks.measure("VideoComponentsBlockHook") { VideoComponentsBlockHook.hook(symbols) }
                fun assertAdResult(expected: Boolean) {
                    symbols.adTargets.values.forEach { member -> assertEquals(expected, api.invoke(member) { true }) }
                }
                try {
                    val beforeAds = installVideo()
                    assertEquals(InstallState.SKIPPED, beforeAds.state)
                    assertFalse(VideoComponentsBlockHook.isInstallationActive())
                    assertEquals(0, api.created)
                    if (!adChoice) {
                        api.failInstall += symbols.adTargets.getValue(PerformanceRuntimeTarget.AD_CSJ)
                        assertEquals(InstallState.PARTIAL, prepareAds().state)
                        assertFalse(AdSdkInitBlockHook.isPreparedFor(symbols.adTargets))
                        assertEquals(InstallState.SKIPPED, installVideo().state)
                        assertFalse(VideoComponentsBlockHook.isInstallationActive())
                        assertAdResult(true)
                        api.failInstall.clear()
                    }
                    val prepared = prepareAds()
                    assertEquals(InstallState.INSTALLED, prepared.state)
                    assertEquals(3, prepared.installedCount)
                    assertTrue(AdSdkInitBlockHook.isPreparedFor(symbols.adTargets))
                    assertFalse(VideoComponentsBlockHook.isInstallationActive())
                    assertAdResult(!adChoice)

                    api.failInstall += symbols.playDispatch
                    val failedVideo = installVideo()
                    assertEquals(InstallState.ROLLED_BACK, failedVideo.state)
                    assertFalse(VideoComponentsBlockHook.isInstallationActive())
                    assertAdResult(!adChoice)
                    assertEquals(symbols.adTargets.values.toSet(), api.callbacks.keys)

                    api.failInstall.clear()
                    val active = installVideo()
                    assertEquals(InstallState.INSTALLED, active.state)
                    assertEquals(allMembers.size, active.installedCount)
                    assertTrue(VideoComponentsBlockHook.isInstallationActive())
                    assertAdResult(false)
                    assertEquals(adChoice, ConfigManager.snapshot().isAdSdkComponentsDisabled)
                    val beforeRepeat = api.created
                    assertEquals(InstallState.ALREADY_INSTALLED, prepareAds().state)
                    assertEquals(InstallState.ALREADY_INSTALLED, installVideo().state)
                    assertEquals(beforeRepeat, api.created)

                    snapshotField.set(ConfigManager, selected.copy(isVideoComponentsDisabled = false))
                    assertAdResult(!adChoice)
                    assertEquals(adChoice, ConfigManager.snapshot().isAdSdkComponentsDisabled)
                    if (!adChoice) {
                        snapshotField.set(ConfigManager, selected)
                        val channel = PerformanceRuntimeTarget.AD_CSJ
                        val released = RuntimeHooks.builder(api.value, symbols.adTargets.getValue(channel), "AdSdkInitBlockHook", channel.name)
                            .intercept { error("existing ad registration should be reused") }
                        released.unhook()
                        assertFalse(AdSdkInitBlockHook.isPreparedFor(symbols.adTargets))
                        assertEquals(InstallState.SKIPPED, installVideo().state)
                        assertFalse(VideoComponentsBlockHook.isInstallationActive())
                        assertAdResult(true)
                    }
                } finally {
                    release()
                }
            }
        } finally {
            snapshotField.set(ConfigManager, originalSettings)
            moduleField.set(XposedCompat, originalModule)
        }
    }

    @Test fun featureUsesOnlyVideoProcessesAndDoesNotFollowVideoFilteringOrAdSettings() {
        assertEquals(FeaturePhase.POST_ATTACH, VideoComponentsFeature.phase)
        assertEquals(FeatureProcess.ANY, VideoComponentsFeature.process)
        for (video in listOf(false, true)) for (filter in listOf(false, true)) for (ads in listOf(false, true)) {
            val settings = SettingsSnapshot.bootstrap().copy(
                isVideoComponentsDisabled = video,
                isPostVideoFilterEnabled = filter,
                isAdSdkComponentsDisabled = ads,
            )
            for (process in listOf("com.baidu.tieba", "com.baidu.tieba:remote", "com.baidu.tieba:media", "com.baidu.tieba:nadmedia")) {
                val entries = VideoComponentsFeature.entries(HookInstallContext(process, HookSymbols.unsupported()), settings)
                assertEquals("$process video=$video filter=$filter ads=$ads",
                    if (video) listOf("VideoComponentsBlockHook") else emptyList<String>(), entries.map { it.id })
            }
            for (process in listOf("com.android.systemui", "com.other.app", "com.baidu.tieba.lookalike", "com.baidu.tieba:loki", "com.baidu.tieba:bdservice_v1")) {
                assertTrue(VideoComponentsFeature.entries(HookInstallContext(process, null), settings).isEmpty())
            }
        }
    }

    private fun install(api: FakeApi, targets: VideoDisableSymbols? = symbols, enabled: () -> Boolean = { true }) =
        RuntimeHooks.measure("VideoComponentsBlockHook") {
            VideoComponentsBlockHook.install(targets, api.value, dependenciesReady = true, enabled = enabled)
        }

    private fun api() = FakeApi().also { apis += it }
    private fun method(name: String): Method = Fixture::class.java.getDeclaredMethod(name)
    private fun playerMethods(name: String) = listOf(
        BasePlayer::class.java.getDeclaredMethod(name), DerivedPlayer::class.java.getDeclaredMethod(name),
    )
    private fun originalValue(member: Executable): Any? = if ((member as Method).returnType == Boolean::class.javaPrimitiveType) true else null
    private fun assertPassesThrough(callback: XposedInterface.Hooker) {
        val expected = Any()
        var calls = 0
        assertSame(expected, callback.intercept(chain { calls++; expected }))
        assertEquals(1, calls)
    }

    class Fixture {
        fun feedGate() = true
        fun point1() = Unit
        fun point2() = Unit
        fun point3() = Unit
        fun point4() = Unit
        fun point5() = Unit
        fun point6() = Unit
        fun point7() = Unit
        fun point8() = Unit
        fun point9() = Unit
        fun point10() = Unit
        fun point11() = Unit
        fun point12() = Unit
        fun point13() = Unit
        fun point14() = Unit
        fun point15() = Unit
        fun point16() = Unit
        fun point17() = Unit
        fun point18() = Unit
        fun point19() = Unit
        fun point20() = Unit
        fun point21() = Unit
        fun point22() = Unit
        fun pcdn() = Unit
        fun adCsj() = true
        fun adGdt() = true
        fun adBaidu() = true
    }

    open class BasePlayer {
        var plays = 0
        open fun doPlay() { plays++ }
        open fun start() { plays++ }
        open fun resume() { plays++ }
    }

    class DerivedPlayer : BasePlayer() {
        var progressStarts = 0
        override fun doPlay() { super.doPlay(); progressStarts++ }
        override fun start() { super.start(); progressStarts++ }
        override fun resume() { super.resume(); progressStarts++ }
    }

    open class FixtureKernel
    class EmptyKernel : FixtureKernel() {
        init { created++ }
        companion object { var created = 0 }
    }
    class RealKernel : FixtureKernel()
    class KernelFactory {
        var realKernelCreates = 0
        fun create(url: String): FixtureKernel { realKernelCreates++; return RealKernel() }
    }
    class KernelLayer(val kernel: FixtureKernel) {
        var acceptVolumeChange = true
    }
    class RequiresNonNullLayer(createKernel: () -> FixtureKernel?) {
        val layer = KernelLayer(requireNotNull(createKernel()))
        init { layer.acceptVolumeChange = false }
    }

    class VideoViewFixture {
        var audioFocusRequests = 0
        var wakeLocks = 0
        fun start() { dispatch(0) }
        fun dispatch(mode: Int) { audioFocusRequests++; wakeLocks++ }
    }

    private class FakeApi {
        val callbacks = LinkedHashMap<Executable, XposedInterface.Hooker>()
        val snapshots = ArrayList<XposedInterface.Hooker>()
        val failInstall = HashSet<Executable>()
        val failUnhook = HashSet<Executable>()
        var afterInstall: () -> Unit = {}
        var created = 0
        val exceptionModes = LinkedHashMap<Executable, XposedInterface.ExceptionMode>()
        val value = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, method, args ->
            check(method.name == "hook")
            val member = args!![0] as Executable
            object : XposedInterface.HookBuilder {
                override fun setId(id: String?) = this
                override fun setPriority(priority: Int) = this
                override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { exceptionModes[member] = mode }
                override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                    check(member !in failInstall) { "fixture install failure" }
                    callbacks[member] = hooker
                    snapshots += hooker
                    created++
                    afterInstall()
                    return object : XposedInterface.HookHandle {
                        override fun getExecutable() = member
                        override fun getId(): String? = null
                        override fun unhook() {
                            check(member !in failUnhook) { "fixture unhook failure" }
                            callbacks.remove(member)
                        }
                        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = error("unused")
                    }
                }
            }
        } as XposedInterface

        fun invoke(member: Executable, original: () -> Any?): Any? {
            val callback = callbacks[member] ?: return original()
            return callback.intercept(chain(original))
        }
    }

    companion object {
        private fun chain(original: () -> Any?) = Proxy.newProxyInstance(
            VideoComponentsBlockHookTest::class.java.classLoader, arrayOf(XposedInterface.Chain::class.java),
        ) { _, method, _ ->
            check(method.name == "proceed") { "unexpected callback work: ${method.name}" }
            original()
        } as XposedInterface.Chain
    }
}
