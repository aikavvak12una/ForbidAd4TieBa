package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PbTopEnterBarBlockHookTest {
    @Test fun preservesInitializationAndRechecksSettingsBeforeTouchingTheController() {
        val events = mutableListOf<String>()
        var enabled = true
        var installs = 0
        lateinit var callback: XposedInterface.Hooker
        val target = Fixture::class.java.getDeclaredMethod("initialize")
        val targets = PbTopEnterBarSymbols(target, Fixture::class.java.getDeclaredField("container"),
            Fixture::class.java.getDeclaredField("navigation"))
        val api = proxy<XposedInterface> { method, _ ->
            check(method.name == "hook")
            installs++
            proxy<XposedInterface.HookBuilder> { builderMethod, args ->
                check(builderMethod.name == "intercept")
                callback = args!![0] as XposedInterface.Hooker
                proxy<XposedInterface.HookHandle> { handleMethod, _ ->
                    when (handleMethod.name) {
                        "getExecutable" -> target
                        "getId", "unhook" -> null
                        else -> error("Unexpected handle call: $handleMethod")
                    }
                }
            }
        }
        val isEnabled = { events += "settings"; enabled }
        val handle = PbTopEnterBarBlockHook.install(api, targets, isEnabled)
        try {
            assertSame(handle, PbTopEnterBarBlockHook.install(api, targets, isEnabled))
            assertEquals(1, installs)
            val result = Any()
            val chain = proxy<XposedInterface.Chain> { method, _ ->
                check(method.name == "proceed") { "Disabled callback touched the controller: $method" }
                events += "proceed"
                enabled = false // A settings publication during host initialization must be observed.
                result
            }
            assertSame(result, callback.intercept(chain))
            assertEquals(listOf("proceed", "settings"), events)

            events.clear()
            enabled = true
            val failure = IllegalStateException("host initializer failed")
            val failingChain = proxy<XposedInterface.Chain> { method, _ ->
                check(method.name == "proceed")
                throw failure
            }
            assertSame(failure, assertThrows(IllegalStateException::class.java) { callback.intercept(failingChain) })
            assertTrue(events.isEmpty())

            handle.unhook()
            assertSame(result, callback.intercept(chain))
            assertEquals(listOf("proceed"), events)
        } finally {
            handle.unhook()
        }
    }

    class Fixture {
        @JvmField var container: Any? = null
        @JvmField var navigation: Any? = null
        fun initialize() = Unit
    }

    private inline fun <reified T> proxy(noinline call: (Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method, args) } as T
}
