package com.forbidad4tieba.hook.feature.ui
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
class PublishEntryHookTest {
    private val key = "22.12_publish_button_right_top"
    private val readers = listOf("isHit", "isHitOnce").map {
        Readers::class.java.getDeclaredMethod(it, String::class.java)
    }
    @Test fun exactExperimentKeyUsesEitherSwitchAndPreservesHostFailure() {
        val api = FakeHookApi()
        val defaults = SettingsSnapshot.bootstrap()
        var settings = defaults
        var settingsReads = 0
        val handles = PublishEntryHook.install(api.value, readers) { settingsReads++; settings }
        try {
            assertEquals(readers.toSet(), api.callbacks.keys)
            for (callback in api.callbacks.values) {
                for ((customTabs, floatingBar) in listOf(true to false, false to true)) {
                    settings = defaults.copy(
                        isBottomTabsCustomEnabled = customTabs,
                        isFloatingBottomBarEnabled = floatingBar,
                    )
                    assertEquals(false, callback.intercept(chain(key) { error("host reached") }))
                }
                settingsReads = 0
                for (otherKey in listOf(null, "", "other_experiment", key + "_extra")) {
                    for (original in listOf(false, true)) {
                        var calls = 0
                        assertEquals(original, callback.intercept(chain(otherKey) { calls++; original }))
                        assertEquals(1, calls)
                    }
                }
                assertEquals(0, settingsReads)
                settings = defaults
                var calls = 0
                assertEquals(true, callback.intercept(chain(key) { calls++; true }))
                assertEquals(1, calls)
                val failure = IllegalStateException("host failure")
                assertSame(failure, assertThrows(IllegalStateException::class.java) {
                    callback.intercept(chain(key) { throw failure })
                })
            }
        } finally {
            handles.asReversed().forEach { it.unhook() }
        }
    }
    private fun chain(key: String?, original: () -> Any?): XposedInterface.Chain =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getArgs" -> listOf(key); "proceed" -> original()
                else -> error("chain ${method.name}")
            }
        } as XposedInterface.Chain
    class Readers {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: String?) = true
        }
    }
}
