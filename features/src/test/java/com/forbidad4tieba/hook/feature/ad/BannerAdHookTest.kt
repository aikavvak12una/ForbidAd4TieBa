package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.InstallState
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
import org.junit.Assert.*
import org.junit.Test

class BannerAdHookTest {
    private val conversion = Converter::class.java.getDeclaredMethod("convert", Layout::class.java)

    @Test fun exactHomeBannerMarkerRequiresOnlyPopulatedFeedBannerComponents() {
        for (count in 1..3) {
            val components = MutableList<Any?>(count) { Component("feed_banner", Any()) }
            val parameters = mutableListOf<Any?>(
                LogParam("source", null),
                LogParam("card_type", "homeBanner"),
                LogParam("card_type", "homeBanner"),
            )
            val banner = BannerCard(components, parameters)
            val layout = Layout("banner_card", banner)
            val original = snapshot(layout)

            assertTrue(BannerAdHook.shouldBlock(layout, targets()))
            assertEquals(original, snapshot(layout))
            assertSame(banner, layout.banner_card)
            assertSame(components, banner.components)
            assertSame(parameters, banner.log_param)
        }
    }

    @Test fun ordinaryUnknownNullAndIncompleteSourcesArePreserved() {
        val populated = Component("feed_banner", Any())
        val marker = listOf<Any?>(LogParam("card_type", "homeBanner"))
        val validBanner = BannerCard(listOf(populated), marker)
        val cases = linkedMapOf<String, Any?>(
            "null" to null,
            "unknown object" to Any(),
            "ordinary feed" to Layout("feed_card", validBanner),
            "strategy banner" to Layout("recommend_banner", validBanner),
            "unknown layout" to Layout("banner_card_extra", validBanner),
            "missing layout" to Layout(null, validBanner),
            "missing banner" to Layout("banner_card", null),
            "missing components" to Layout("banner_card", BannerCard(null, marker)),
            "empty components" to Layout("banner_card", BannerCard(emptyList(), marker)),
            "null component" to Layout("banner_card", BannerCard(listOf(null), marker)),
            "unknown component object" to Layout("banner_card", BannerCard(listOf(Any()), marker)),
            "missing component kind" to Layout("banner_card", BannerCard(listOf(Component(null, Any())), marker)),
            "unknown component kind" to Layout("banner_card", BannerCard(listOf(Component("feed_banner_extra", Any())), marker)),
            "mixed components" to Layout("banner_card", BannerCard(listOf(populated, Component("feed_head", Any())), marker)),
            "missing payload" to Layout("banner_card", BannerCard(listOf(Component("feed_banner", null)), marker)),
            "one missing payload" to Layout("banner_card", BannerCard(listOf(populated, Component("feed_banner", null)), marker)),
            "missing parameters" to Layout("banner_card", BannerCard(listOf(populated), null)),
            "empty parameters" to Layout("banner_card", BannerCard(listOf(populated), emptyList())),
            "unknown parameter" to Layout("banner_card", BannerCard(listOf(populated), listOf(Any()))),
            "null parameter" to Layout("banner_card", BannerCard(listOf(populated), listOf(null))),
            "marker absent" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("source", "homeBanner")))),
            "marker key missing" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam(null, "homeBanner")))),
            "marker key wrong case" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("CARD_TYPE", "homeBanner")))),
            "marker value missing" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("card_type", null)))),
            "marker value empty" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("card_type", "")))),
            "marker value wrong case" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("card_type", "homebanner")))),
            "marker value whitespace" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("card_type", "homeBanner ")))),
            "other banner marker" to Layout("banner_card", BannerCard(listOf(populated), listOf(LogParam("card_type", "ordinaryBanner")))),
        )
        for ((name, layout) in cases) {
            val original = snapshot(layout)
            assertFalse(name, BannerAdHook.shouldBlock(layout, targets()))
            assertEquals("source changed: $name", original, snapshot(layout))
        }
    }

    @Test fun conflictingMarkersArePreservedInEitherOrder() {
        val accepted = LogParam("card_type", "homeBanner")
        for (value in listOf(null, "", "ordinaryBanner")) {
            val conflicting = LogParam("card_type", value)
            for (parameters in listOf(listOf(accepted, conflicting), listOf(conflicting, accepted))) {
                val layout = Layout("banner_card", BannerCard(listOf(Component("feed_banner", Any())), parameters))
                val original = snapshot(layout)
                assertFalse(BannerAdHook.shouldBlock(layout, targets()))
                assertEquals(original, snapshot(layout))
            }
        }
    }

    @Test fun enabledBannerSkipsConversionButRetainedSourcesProceedUnchanged() = withRuntime { api ->
        setEnabled(true)
        val outcome = install()
        assertEquals(InstallState.INSTALLED, outcome.state)
        assertEquals(1, outcome.installedCount)
        assertEquals(setOf(conversion), api.callbacks.keys)
        val callback = api.callbacks.getValue(conversion)
        var conversions = 0
        val hostResult = Any()
        val target = homeBanner()
        val originalTarget = snapshot(target)

        assertNull(callback.invoke(target) { conversions++; hostResult })
        assertEquals(0, conversions)
        assertEquals(originalTarget, snapshot(target))

        val retained = listOf(null, Any(), Layout("feed_card", target.banner_card),
            Layout("banner_card", BannerCard(listOf(Component("feed_banner", null)),
                listOf(LogParam("card_type", "homeBanner")))))
        for (layout in retained) {
            val original = snapshot(layout)
            assertSame(hostResult, callback.invoke(layout) { conversions++; hostResult })
            assertEquals(original, snapshot(layout))
        }
        assertEquals(retained.size, conversions)
    }

    @Test fun disabledBannerNeverReadsArgumentsAndOtherFiltersDoNotEnableIt() = withRuntime { api ->
        setEnabled(false)
        assertEquals(InstallState.SKIPPED, install().state)
        assertEquals(0, api.created)
        assertTrue(api.callbacks.isEmpty())
        setSettings(SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true, isCustomPostFilterEnabled = true))
        assertEquals(InstallState.SKIPPED, install().state)
        assertEquals(0, api.created)
        assertTrue(api.callbacks.isEmpty())

        setEnabled(true)
        assertEquals(InstallState.INSTALLED, install().state)
        val callback = api.callbacks.getValue(conversion)
        setEnabled(false)
        var conversions = 0
        val hostResult = Any()
        assertSame(hostResult, callback.invoke(allowArgs = false) { conversions++; hostResult })
        assertEquals(1, conversions)
        setEnabled(true)
        assertNull(callback.invoke(homeBanner()) { conversions++; hostResult })
        assertEquals(1, conversions)
    }

    @Test fun installationFailuresAreRetryableAndSuccessDoesNotInstallDuplicates() = withRuntime { api ->
        setEnabled(true)
        api.failInstall += conversion
        val failed = install()
        assertEquals(InstallState.FAILED, failed.state)
        assertEquals(0, failed.installedCount)
        assertEquals(0, api.created)
        assertTrue(api.callbacks.isEmpty())

        api.failInstall.clear()
        val recovered = install()
        assertEquals(InstallState.INSTALLED, recovered.state)
        assertEquals(1, recovered.installedCount)
        assertEquals(1, api.created)
        val callback = api.callbacks.getValue(conversion)
        val repeated = install()
        assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
        assertEquals(1, repeated.installedCount)
        assertEquals(1, api.created)
        assertSame(callback, api.callbacks.getValue(conversion))
    }

    @Test fun releasedCallbacksStayInertAfterReinstallation() = withRuntime { api ->
        setEnabled(true)
        install()
        val stale = api.callbacks.getValue(conversion)
        release()
        assertTrue(api.callbacks.isEmpty())
        var conversions = 0
        val hostResult = Any()
        assertSame(hostResult, stale.invoke(allowArgs = false) { conversions++; hostResult })
        assertEquals(1, conversions)

        assertEquals(InstallState.INSTALLED, install().state)
        assertEquals(2, api.created)
        val current = api.callbacks.getValue(conversion)
        assertNotSame(stale, current)
        assertSame(hostResult, stale.invoke(allowArgs = false) { conversions++; hostResult })
        assertNull(current.invoke(homeBanner()) { conversions++; hostResult })
        assertEquals(2, conversions)
    }

    private fun homeBanner() = Layout("banner_card", BannerCard(
        mutableListOf(Component("feed_banner", Any())),
        mutableListOf(LogParam("card_type", "homeBanner")),
    ))

    private fun snapshot(value: Any?): Any? = when (value) {
        is Layout -> listOf(value, value.layout, snapshot(value.banner_card))
        is BannerCard -> listOf(value, snapshot(value.components), snapshot(value.log_param))
        is Component -> listOf(value, value.component, value.feed_banner)
        is LogParam -> listOf(value, value.key, value.value)
        is List<*> -> value.map(::snapshot)
        else -> value
    }

    private fun targets() = BannerAdSymbols(
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

    private fun install() = RuntimeHooks.measure("BannerAdHook") { BannerAdHook.hook(targets()) }

    private fun setEnabled(value: Boolean) = setSettings(SettingsSnapshot.bootstrap().copy(isFeedAdBlockEnabled = value))

    private fun setSettings(settings: SettingsSnapshot) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
            .set(ConfigManager, settings)
    }

    private fun release() {
        val installed = BannerAdHook::class.java.getDeclaredField("installedMethods\$delegate").apply { isAccessible = true }
            .get(null) as Lazy<*>
        assertTrue((installed.value as OwnedHookSet<*, *>).rollback().isEmpty())
        val runtime = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(runtime.rollback().isEmpty())
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshotField.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        val module = object : XposedModule() {}.apply { attachFramework(api.value) {} }
        try {
            XposedCompat.attachModule(module)
            test(api)
        } finally {
            try {
                release()
            } finally {
                snapshotField.set(ConfigManager, originalSettings)
                moduleField.set(XposedCompat, originalModule)
            }
        }
    }

    private fun XposedInterface.Hooker.invoke(
        layout: Any? = null,
        allowArgs: Boolean = true,
        original: () -> Any?,
    ): Any? {
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, arguments ->
            when (method.name) {
                "getArgs" -> {
                    check(allowArgs) { "Arguments must not be read while disabled or released" }
                    listOf(layout)
                }
                "proceed" -> {
                    check(arguments.isNullOrEmpty()) { "Original conversion arguments must not be replaced" }
                    original()
                }
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain
        return intercept(chain)
    }

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
