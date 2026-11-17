package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSchema
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class FeedInfoLogRuntimeTest {
    private val bind = Binder::class.java.getMethod("bind", Card::class.java)

    @Test fun initiallyDisabledCallbackDoesNotReadArgumentsOrDiagnosticGetters() = withRuntime { api ->
        setEnabled(false)
        install()
        val card = Card()
        invoke(api, card, allowArgs = false)
        assertEquals(0, card.component.reads)
        assertEquals(0, card.author.reads)
        assertEquals("fixture-author", card.author.nickname)
    }

    @Test fun installedCallbackStopsDiagnosticReadsAfterDisabling() = withRuntime { api ->
        setEnabled(true)
        install()
        val enabledCard = Card()
        invoke(api, enabledCard)
        assertEquals(1, enabledCard.component.reads)
        assertEquals(0, enabledCard.author.reads)

        setEnabled(false)
        val disabledCard = Card()
        invoke(api, disabledCard, allowArgs = false)
        assertEquals(0, disabledCard.component.reads)
        assertEquals(0, disabledCard.author.reads)
        assertEquals("fixture-author", disabledCard.author.nickname)
    }

    @Test fun releasedCallbackDoesNotReadArgumentsEvenWhenLoggingIsEnabled() = withRuntime { api ->
        setEnabled(true)
        install()
        val callback = api.snapshots.single()
        release()
        val card = Card()
        invoke(callback, card, allowArgs = false)
        assertEquals(0, card.component.reads)
        assertEquals(0, card.author.reads)
    }

    private fun install() = FeedInfoLogHook.hook(
        FeedInfoLogSymbols(bind, FeedInfoLogSchema.from(bind, Template::class.java.getMethod("key"))),
    )

    private fun setEnabled(enabled: Boolean) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
            .set(null, SettingsSnapshot.bootstrap().copy(isDetailedLoggingEnabled = enabled))
    }

    private fun invoke(api: FakeHookApi, card: Card, allowArgs: Boolean = true) =
        invoke(checkNotNull(api.callbacks[bind]), card, allowArgs)

    private fun invoke(callback: XposedInterface.Hooker, card: Card, allowArgs: Boolean) {
        var proceeded = 0
        var argumentReads = 0
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "proceed" -> { check(args.isNullOrEmpty()); proceeded++; "host-result" }
                "getArgs" -> { argumentReads++; check(allowArgs) { "Disabled callback read card arguments" }; listOf(card) }
                else -> error("Unexpected callback access: ${method.name}")
            }
        } as XposedInterface.Chain
        assertEquals("host-result", callback.intercept(chain))
        assertEquals(1, proceeded)
        if (!allowArgs) assertEquals("Caught failures must not hide argument reads", 0, argumentReads)
    }

    private fun release() {
        val hooks = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(hooks.rollback().isEmpty())
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val snapshot = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val module = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val previousSettings = snapshot.get(null)
        val previousModule = module.get(null)
        val api = FakeHookApi(allowLog = true)
        try {
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            test(api)
        } finally {
            try { release() } finally {
                snapshot.set(null, previousSettings)
                module.set(null, previousModule)
            }
        }
    }

    interface Template { fun key(): String }
    class Component : Template {
        @JvmField var reads = 0
        override fun key(): String { reads++; return "fixture-template" }
    }
    class Author {
        @JvmField var nickname = "fixture-author"
        @JvmField var reads = 0
        fun key(): String { reads++; nickname = ""; return "unrelated" }
    }
    class Card {
        @JvmField val component = Component()
        @JvmField val author = Author()
    }
    class Binder { fun bind(card: Card) = Unit }
}
