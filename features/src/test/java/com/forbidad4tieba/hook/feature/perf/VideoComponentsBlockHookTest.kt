package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableResult
import com.forbidad4tieba.hook.symbol.model.VideoDisableSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoComponentsBlockHookTest {
    private val symbols = VideoDisableSymbols(
        VideoDisableTarget.entries.associateWith { point ->
            listOf(method(
                if (point.blockedResult == VideoDisableResult.BOOLEAN_FALSE) "feedGate"
                else "point${point.ordinal}",
            ))
        },
        method("pcdn"),
    )
    private val allMembers get() = (symbols.targets.values.flatten() + symbols.pcdnStart).distinct()
    private val apis = ArrayList<FakeHookApi>()

    @After
    fun release() {
        apis.forEach { it.failUnhook.clear() }
        val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(owned.rollback().isEmpty())
        VideoComponentsBlockHook::class.java.getDeclaredField("installation")
            .apply { isAccessible = true }.set(null, null)
        apis.clear()
    }

    @Test
    fun groupInstallsOnlyWithEveryTargetAndDisablingRestoresOriginals() {
        val api = api()
        var enabled = false
        assertEquals(InstallState.SKIPPED, install(api, enabled = { enabled }).state)
        assertEquals(0, api.created)

        enabled = true
        val missing = symbols.targets.keys.first()
        assertEquals(InstallState.SKIPPED,
            install(api, symbols.copy(targets = symbols.targets - missing)).state)
        assertEquals(0, api.created)

        val installed = install(api, enabled = { enabled })
        assertEquals(InstallState.INSTALLED, installed.state)
        assertEquals(allMembers.size, installed.installedCount)
        for ((point, family) in symbols.targets) {
            for (member in family) {
                var originalCalls = 0
                val result = invoke(api, member) { originalCalls++; originalValue(member) }
                when (point.blockedResult) {
                    VideoDisableResult.BOOLEAN_FALSE -> assertEquals(false, result)
                    VideoDisableResult.VOID -> assertNull(result)
                }
                assertEquals(0, originalCalls)
            }
        }
        assertNull(invoke(api, symbols.pcdnStart) { error("PCDN producer ran") })

        enabled = false
        api.callbacks.values.forEach(::assertPassesThrough)
        enabled = true
        assertEquals(InstallState.ALREADY_INSTALLED, install(api, enabled = { enabled }).state)
        assertEquals(allMembers.size, api.created)
    }

    @Test
    fun featureUsesOnlyTiebaProcessesWithoutFollowingUnrelatedSwitches() {
        val disabled = SettingsSnapshot.bootstrap().copy(
            isVideoPreloadDisabled = false,
            isPostVideoFilterEnabled = true,
            isAdSdkComponentsDisabled = true,
        )
        val enabled = SettingsSnapshot.bootstrap().copy(
            isVideoPreloadDisabled = true,
            isPostVideoFilterEnabled = false,
            isAdSdkComponentsDisabled = false,
        )
        val videoProcesses = listOf(
            "com.baidu.tieba",
            "com.baidu.tieba:remote",
            "com.baidu.tieba:media",
            "com.baidu.tieba:nadmedia",
        )
        val unrelated = listOf(
            "com.android.systemui",
            "com.other.app",
            "com.baidu.tieba.lookalike",
            "com.baidu.tieba:loki",
            "com.baidu.tieba:bdservice_v1",
        )

        for (process in videoProcesses) {
            assertTrue(VideoComponentsFeature.entries(
                HookInstallContext(process, HookSymbols.unsupported()),
                disabled,
            ).isEmpty())
            assertEquals(listOf("VideoComponentsBlockHook"), VideoComponentsFeature.entries(
                HookInstallContext(process, HookSymbols.unsupported()),
                enabled,
            ).map { it.id })
        }
        for (process in unrelated) {
            assertTrue(VideoComponentsFeature.entries(HookInstallContext(process, null), enabled).isEmpty())
        }
    }

    private fun install(
        api: FakeHookApi,
        targets: VideoDisableSymbols? = symbols,
        enabled: () -> Boolean = { true },
    ) = RuntimeHooks.measure("VideoComponentsBlockHook") {
        VideoComponentsBlockHook.install(targets, api.value, enabled = enabled)
    }

    private fun api() = FakeHookApi().also { apis += it }
    private fun method(name: String): Method = Fixture::class.java.getDeclaredMethod(name)

    private fun originalValue(member: Executable): Any? =
        if ((member as Method).returnType == Boolean::class.javaPrimitiveType) true else null

    private fun assertPassesThrough(callback: XposedInterface.Hooker) {
        val expected = Any()
        var calls = 0
        assertEquals(expected, callback.intercept(chain { calls++; expected }))
        assertEquals(1, calls)
    }

    private fun invoke(api: FakeHookApi, member: Executable, original: () -> Any?): Any? {
        val callback = api.callbacks[member] ?: return original()
        return callback.intercept(chain(original))
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
    }

    companion object {
        private fun chain(original: () -> Any?) = Proxy.newProxyInstance(
            VideoComponentsBlockHookTest::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
        ) { _, method, _ ->
            check(method.name == "proceed") { "unexpected callback work: ${method.name}" }
            original()
        } as XposedInterface.Chain
    }
}
