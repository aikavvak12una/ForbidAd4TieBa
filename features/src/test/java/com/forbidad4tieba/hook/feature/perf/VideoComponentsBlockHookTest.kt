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
import com.forbidad4tieba.hook.testing.FakeHookApi
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
        VideoDisableTarget.entries.associateWith { point -> listOf(method(if (point.blockedResult == VideoDisableResult.BOOLEAN_FALSE) "feedGate" else "point${point.ordinal}")) },
        method("pcdn"),
    )
    private val allMembers get() = (symbols.targets.values.flatten() + symbols.pcdnStart).distinct()
    private val apis = ArrayList<FakeHookApi>()

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
                }
                assertEquals(0, calls)
            }
        }
        assertNull(api.invoke(symbols.pcdnStart) { error("PCDN producer ran") })
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

    @Test fun foregroundPlaybackAndSharedInitializationAreNeverIntercepted() {
        val api = api()
        install(api)
        var originalCalls = 0
        for (member in ForegroundFixture::class.java.declaredMethods) {
            assertFalse(api.callbacks.containsKey(member))
            api.invoke(member) { originalCalls++; null }
        }
        assertEquals(5, originalCalls)
    }

    class ForegroundFixture {
        fun initCyberPlayer() = Unit
        fun create() = Unit
        fun setVideoPath() = Unit
        fun start() = Unit
        fun prefetch() = Unit
    }

    @Test fun duplicateMethodsAcrossFamiliesAndSemanticPointsHaveOnlyOneHandle() {
        val api = api()
        val duplicatedFamilies = symbols.targets.mapValues { (_, family) -> family + family }.toMutableMap()
        duplicatedFamilies[VideoDisableTarget.PREBOOT_WORK] = duplicatedFamilies.getValue(VideoDisableTarget.PREBOOT_WORK) + symbols.pcdnStart
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

    @Test fun featureUsesOnlyVideoProcessesAndDoesNotFollowVideoFilteringOrAdSettings() {
        assertEquals(FeaturePhase.POST_ATTACH, VideoComponentsFeature.phase)
        assertEquals(FeatureProcess.ANY, VideoComponentsFeature.process)
        for (video in listOf(false, true)) for (filter in listOf(false, true)) for (ads in listOf(false, true)) {
            val settings = SettingsSnapshot.bootstrap().copy(
                isVideoPreloadDisabled = video,
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

    private fun install(api: FakeHookApi, targets: VideoDisableSymbols? = symbols, enabled: () -> Boolean = { true }) =
        RuntimeHooks.measure("VideoComponentsBlockHook") {
            VideoComponentsBlockHook.install(targets, api.value, enabled = enabled)
        }

    private fun api() = FakeHookApi().also { apis += it }
    private fun method(name: String): Method = Fixture::class.java.getDeclaredMethod(name)
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

    private fun FakeHookApi.invoke(member: Executable, original: () -> Any?): Any? {
        val callback = callbacks[member] ?: return original()
        return callback.intercept(chain(original))
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
