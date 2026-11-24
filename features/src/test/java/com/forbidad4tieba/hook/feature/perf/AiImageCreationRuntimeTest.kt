package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.AiImageCreationSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageField
import com.forbidad4tieba.hook.symbol.model.AiImageMethod as M
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Tiny carriers exercise callback boundaries; no copy of the obfuscated host. */
class AiImageCreationRuntimeTest {
    private val ai = "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-create-pic"
    private val ordinary = "https://tieba.baidu.com/"
    private val targets = AiImageCreationSymbols(
        M.entries.associateWith { role -> Carrier::class.java.declaredMethods.single { it.name == when (role) {
            M.IMAGE_JUMP_DATA -> "jump"; M.IMAGE_JUMP_SCHEME -> "schema"; M.ROUTE_DISPATCH -> "route"
            M.WEB_CONFIG_BUILD -> "web"; M.INPUT_AI_VISIBLE -> "visible"; M.INPUT_AI_SCHEMA -> "schema"
            M.PB_GUIDE_BIND -> "bind"; M.PB_GUIDE_CAPSULE -> "capsule"; M.IMAGE_BOTTOM_TIP_BIND -> "tip"
        } }.apply { isAccessible = true } },
        mapOf(AiImageField.WEB_URL to Carrier::class.java.getDeclaredField("destination")),
    )

    @Test fun filtersOnlyTheSelectedDestinationAndRetainsOrdinaryData() = withRuntime { api, runtime ->
        assertEquals(InstallState.INSTALLED, runtime.install().state)
        val carrier = Carrier(ai)
        fun invoke(role: M, args: List<Any?> = emptyList(), original: Any? = "host"): Any? =
            call(api.callbacks.getValue(targets[role]), carrier, args) { original }
        assertEquals(false, invoke(M.ROUTE_DISPATCH, listOf(null, null, arrayOf(ai, ordinary))))
        assertEquals("host", invoke(M.ROUTE_DISPATCH, listOf(ai, ai, arrayOf(ordinary, ai))))
        assertNull(invoke(M.WEB_CONFIG_BUILD))
        assertEquals(0, invoke(M.INPUT_AI_VISIBLE))
        assertNull(invoke(M.IMAGE_JUMP_DATA, original = carrier))
        carrier.destination = ordinary
        assertEquals("host", invoke(M.WEB_CONFIG_BUILD))
        assertEquals(1, invoke(M.INPUT_AI_VISIBLE, original = 1))
        assertSame(carrier, invoke(M.IMAGE_JUMP_DATA, original = carrier))
        assertEquals("host", invoke(M.IMAGE_BOTTOM_TIP_BIND, listOf(ai, ordinary)))
    }

    @Test fun disabledPartialAndReleasedGroupsNeverReadCallbackInputs() {
        var enabled = false
        withRuntime({ enabled }) { api, runtime ->
            assertEquals(InstallState.SKIPPED, runtime.install().state)
            assertTrue(api.callbacks.isEmpty())
            enabled = true
            api.failInstall += targets[M.IMAGE_BOTTOM_TIP_BIND]
            assertEquals(InstallState.ROLLED_BACK, runtime.install().state)
            assertTrue(api.callbacks.isEmpty())
            assertFalse(runtime.active)
            api.snapshots.forEach(::assertInert)
            api.failUnhook += targets[M.IMAGE_JUMP_DATA]
            assertEquals(InstallState.ROLLBACK_FAILED, runtime.install().state)
            assertEquals(1, runtime.handleCount)
            api.snapshots.forEach(::assertInert)
            api.failUnhook.clear()
            assertTrue(runtime.release().isEmpty())
            api.failInstall.clear()
            assertEquals(InstallState.INSTALLED, runtime.install().state)
            enabled = false
            api.snapshots.forEach(::assertInert)
            enabled = true
            assertTrue(runtime.release().isEmpty())
            api.snapshots.forEach(::assertInert)
        }
    }

    private fun withRuntime(enabled: () -> Boolean = { true }, test: (FakeHookApi, AiImageCreationRuntime) -> Unit) {
        val api = FakeHookApi()
        val runtime = AiImageCreationRuntime(api.value, targets, enabled)
        try { test(api, runtime) }
        finally {
            api.failUnhook.clear(); runtime.release()
            val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
            assertTrue(owned.rollback().isEmpty())
        }
    }

    private fun assertInert(callback: XposedInterface.Hooker) {
        var proceeded = 0
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, op, _ ->
            if (op.name == "proceed") { proceeded++; null } else error("Inactive callback read ${op.name}")
        } as XposedInterface.Chain
        callback.intercept(chain)
        assertEquals(1, proceeded)
    }

    private fun call(callback: XposedInterface.Hooker, receiver: Any, args: List<Any?>, original: () -> Any?): Any? =
        callback.intercept(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, op, _ ->
            when (op.name) { "proceed" -> original(); "getThisObject" -> receiver; "getArgs" -> args
                else -> error("Unexpected callback operation ${op.name}") }
        } as XposedInterface.Chain)

    class Carrier(@JvmField var destination: String) {
        fun jump(): Any? = null
        fun schema(): String = destination
        fun route() = true
        fun web(): Any? = null
        fun visible() = 1
        fun bind() {}
        fun capsule(): Any? = null
        fun tip() {}
    }
}
