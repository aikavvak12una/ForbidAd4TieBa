package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.BannerAdSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAdHookTest {
    private val conversion = Converter::class.java.getDeclaredMethod("convert", Layout::class.java)
    private val targets = BannerAdSymbols(
        conversionMethod = conversion,
        layoutField = Layout::class.java.getField("layout"),
        bannerCardField = Layout::class.java.getField("banner_card"),
        componentsField = BannerCard::class.java.getField("components"),
        componentField = Component::class.java.getField("component"),
        feedBannerField = Component::class.java.getField("feed_banner"),
        logParamField = BannerCard::class.java.getField("log_param"),
        logKeyField = LogParam::class.java.getField("key"),
        logValueField = LogParam::class.java.getField("value"),
    )

    @Test
    fun exactPopulatedMarkerIsRequiredAndNullPayloadsFailOpen() {
        val marker = LogParam("card_type", "homeBanner")
        val valid = homeBanner()
        assertTrue(BannerAdHook.shouldBlock(valid, targets))
        listOf(
            Layout("feed_card", valid.banner_card),
            Layout("banner_card", BannerCard(listOf(Component("feed_banner", null)), listOf(marker))),
            Layout("banner_card", BannerCard(listOf(Component("feed_banner", Any())), listOf(marker, LogParam("card_type", "ordinaryBanner")))),
            Layout("banner_card", BannerCard(listOf(Component("feed_banner", Any())), listOf(LogParam("card_type", "homebanner")))),
        ).forEach { assertFalse(BannerAdHook.shouldBlock(it, targets)) }
    }

    @Test
    fun closedCallbackDoesNotReadArgumentsAndReleasedCallbackFailsOpen() = withRuntime { api ->
        setEnabled(true)
        BannerAdHook.hook(targets)
        val callback = api.callbacks.getValue(conversion)
        assertNull(callback.call(listOf(homeBanner())) { "host" })

        setEnabled(false)
        assertSame("host", callback.call(null) { "host" })
        release()
        assertSame("host", callback.call(null) { "host" })
    }

    private fun homeBanner() = Layout(
        "banner_card",
        BannerCard(
            listOf(Component("feed_banner", Any())),
            listOf(LogParam("card_type", "homeBanner")),
        ),
    )

    private fun setEnabled(value: Boolean) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot")
            .apply { isAccessible = true }
            .set(ConfigManager, SettingsSnapshot.bootstrap().copy(isFeedAdBlockEnabled = value))
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val settingsField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val oldSettings = settingsField.get(ConfigManager)
        val oldModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        try {
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            test(api)
        } finally {
            settingsField.set(ConfigManager, oldSettings)
            moduleField.set(XposedCompat, oldModule)
            release()
        }
    }

    private fun release() {
        val installed = BannerAdHook::class.java.getDeclaredField("installedMethods\$delegate")
            .apply { isAccessible = true }.get(null) as Lazy<*>
        assertTrue((installed.value as OwnedHookSet<*, *>).rollback().isEmpty())
        val runtime = RuntimeHooks::class.java.getDeclaredField("hooks")
            .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(runtime.rollback().isEmpty())
    }

    private fun XposedInterface.Hooker.call(args: List<Any?>?, original: () -> Any?): Any? =
        intercept(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getArgs" -> checkNotNull(args) { "Closed callback read arguments" }
                "proceed" -> original()
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain)

    class Layout(@JvmField val layout: String?, @JvmField val banner_card: BannerCard?)
    class BannerCard(@JvmField val components: List<Any?>?, @JvmField val log_param: List<Any?>?)
    class Component(@JvmField val component: String?, @JvmField val feed_banner: Any?)
    class LogParam(@JvmField val key: String?, @JvmField val value: String?)
    class Converter {
        companion object {
            @JvmStatic fun convert(layout: Layout): Any? = null
        }
    }
}
