package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import org.junit.Assert.*
import org.junit.Test

class PbTopEnterBarBlockHookTest {
    @Test fun initializationRechecksThePublishedSettingAndPreservesHostFailure() {
        val api = FakeHookApi()
        val target = Fixture::class.java.getDeclaredMethod("initialize")
        val targets = PbTopEnterBarSymbols(target, Fixture::class.java.getDeclaredField("container"),
            Fixture::class.java.getDeclaredField("navigation"))
        var enabled = true
        val handle = PbTopEnterBarBlockHook.install(api.value, targets) { enabled }
        val callback = api.callbacks.getValue(target)
        try {
            val result = Any()
            assertSame(result, callback.call(proceed = { enabled = false; result }))
            val failure = IllegalStateException("Host initializer")
            assertSame(failure, assertThrows(IllegalStateException::class.java) {
                callback.call(proceed = { throw failure })
            })
            handle.unhook()
            assertSame(result, callback.call(proceed = { result }))
        } finally { handle.unhook() }
    }

    class Fixture {
        @JvmField var container: Any? = null
        @JvmField var navigation: Any? = null
        fun initialize() = Unit
    }
}
