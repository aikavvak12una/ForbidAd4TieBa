package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.TabPreferences
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HomeTabItemTargets
import com.forbidad4tieba.hook.symbol.model.HomeTabResolvedSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

/** Executes the production callbacks against the host's parse -> rebuild -> consume order. */
class HomeTabFixedHookTest {
    private val previous: SettingsSnapshot = ConfigManager.snapshot()
    private val targets = HomeTabResolvedSymbols(
        hostClass = Host::class.java,
        rebuildMethod = Host::class.java.getDeclaredMethod("rebuild"),
        listField = Host::class.java.getDeclaredField("visible").apply { isAccessible = true },
        parseMethod = Host::class.java.getDeclaredMethod("parse"),
        item = HomeTabItemTargets(
            type = Item::class.java,
            constructor = Item::class.java.getDeclaredConstructor().apply { isAccessible = true },
            typeField = itemField("kind"), codeField = itemField("code"), nameField = itemField("title"), urlField = itemField("url"),
            mainSetter = null, mainIntField = itemField("main"), mainBooleanField = null,
        ),
    )

    private fun itemField(name: String) = Item::class.java.getDeclaredField(name).apply { isAccessible = true }

    @After fun restoreState() {
        releaseHooks()
        ConfigManager.updateRuntimeSettings { previous }
    }

    @Test fun firstColdBindExcludesCachedNewTabsWithoutOpeningSettingsAndKeepsRawCatalog() {
        publish("code:recommend", TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi()
        assertEquals(2, install(api).installedCount)
        val host = Host(listOf(Item(code = "event"), Item(), Item(code = "live", kind = 6)))
        rebuild(api, host)
        assertEquals(listOf("recommend", "my_followed"), codes(host.visible))
        assertEquals(listOf("event", "recommend", "live"), codes(catalog().current()))
        assertEquals(listOf("event", "recommend", "live"), codes(host.raw))
        assertFalse(changed(api, host))
    }

    @Test fun lateUnselectedUpdatesAreFilteredAtParseAndSelectedMetadataChangesStillReachConsumers() {
        publish("code:recommend", TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item()))
        rebuild(api, host)
        host.raw = listOf(Item(code = "late"), Item())
        repeat(4) { assertFalse(changed(api, host)) }
        assertEquals(listOf("late", "recommend"), codes(catalog().current()))
        host.raw = listOf(Item(title = "新标题", url = "https://example.com/new"), Item(code = "late"))
        assertTrue(changed(api, host))
        rebuild(api, host)
        assertEquals("新标题", (host.visible.first() as Item).title)
        assertEquals("https://example.com/new", (host.visible.first() as Item).url)
        assertFalse(changed(api, host))
        repeat(4) { rebuild(api, host) }
        assertEquals(listOf("recommend", "my_followed"), codes(host.visible))
    }

    @Test fun partialDisappearanceKeepsSurvivorsAndOrderAndReturningSelectionsReappear() {
        publish("code:recommend", "code:season")
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item(code = "season"), Item(), Item(code = "extra")))
        rebuild(api, host)
        assertEquals(listOf("season", "recommend"), codes(host.visible))
        host.raw = listOf(Item(), Item(code = "extra"))
        assertTrue(changed(api, host))
        rebuild(api, host)
        assertEquals(listOf("recommend"), codes(host.visible))
        host.raw = listOf(Item(), Item(code = "extra"), Item(code = "season", title = "已恢复"))
        rebuild(api, host)
        assertEquals(listOf("recommend", "season"), codes(host.visible))
    }

    @Test fun catalogKeepsRawUrlIdentityWhenTheAdapterDecoratesTheVisibleItem() {
        val url = "https://example.com/tab?source=host"
        publish("code:recommend")
        ConfigManager.updateRuntimeSettings { it.copy(isHomeTopTabFixedEnabled = false, isHomeTopTabFollowedEnabled = false) }
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item(kind = 202, code = "", title = "页面", url = url), Item()))
        rebuild(api, host)
        (host.visible.first() as Item).url = "$url&bgImg=host-decoration"
        val raw = catalog().current().first()
        assertEquals(url, raw.url)
        val savedKey = checkNotNull(TabPreferences.homeTopTabKeyFor(raw.type, raw.code, raw.url))
        publish(savedKey)
        host.raw = listOf(Item(kind = 202, code = "", title = "页面", url = url), Item())
        rebuild(api, host)
        assertEquals(1, host.visible.size)
        assertEquals(url, (host.visible.single() as Item).url)
    }

    @Test fun unknownItemsAreExcludedAndConflictsOrAllMissingKeepTheOriginalList() {
        publish("code:recommend")
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(null, Item(code = "", kind = -1), Item()))
        assertEquals(listOf("recommend"), codes(parse(api, host)))
        host.raw = listOf(Item(code = "missing"))
        assertSame(host.raw, parse(api, host))
        host.raw = listOf(Item(), Item(title = "conflict"))
        assertSame(host.raw, parse(api, host))
        host.raw = listOf(Item(), Item(code = "", kind = 1))
        assertSame(host.raw, parse(api, host))
    }

    @Test fun onlyFollowedCanBeCreatedAndFailureNeverCommitsAnEmptyHomepage() {
        publish(TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item()))
        rebuild(api, host)
        assertEquals(listOf("my_followed"), codes(host.visible))
        assertFalse(changed(api, host))
        host.raw = listOf(NoDefaultConstructor(1))
        assertSame(host.raw, parse(api, host))
        val logsAfterFailure = android.util.Log.entries.size
        repeat(4) { assertSame(host.raw, parse(api, host)) }
        assertEquals(logsAfterFailure, android.util.Log.entries.size)
        assertEquals(1, host.raw.size)
    }

    @Test fun existingFollowedAliasesDeduplicateThroughTheExistingModulePath() {
        publish(TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item(code = "myfollowed"), Item(code = "followed"), Item()))
        rebuild(api, host)
        assertEquals(listOf("myfollowed"), codes(host.visible))
        assertEquals(3, catalog().current().size)
    }

    @Test fun publishedModeChangesUseTheInstalledParserAndParentOffPassesThrough() {
        publish("code:recommend")
        ConfigManager.updateRuntimeSettings { it.copy(isHomeTopTabFixedEnabled = false, isHomeTopTabFollowedEnabled = false) }
        val api = FakeHookApi()
        install(api)
        val host = Host(listOf(Item(), Item(code = "new")))
        assertSame(host.raw, parse(api, host))
        rebuild(api, host)
        assertEquals(listOf("recommend", "new"), codes(host.visible))
        publish("code:recommend")
        rebuild(api, host)
        assertEquals(listOf("recommend"), codes(host.visible))
        ConfigManager.updateRuntimeSettings { it.copy(isHomeTopTabsCustomEnabled = false, isHomeTopTabFixedEnabled = false) }
        assertSame(host.raw, parse(api, host))
        rebuild(api, host)
        assertEquals(host.raw, host.visible)
    }

    @Test fun independentInstallFailuresRetainTheOtherPathAndRepeatedInstallsCountRealHandles() {
        publish("code:recommend")
        val api = FakeHookApi(setOf(targets.parseMethod!!))
        val outcome = install(api)
        assertEquals(InstallState.PARTIAL, outcome.state)
        assertEquals(1, outcome.installedCount)
        val host = Host(listOf(Item(), Item(code = "new")))
        rebuild(api, host)
        assertTrue("Ordinary mode stays active after parser failure", "new" in codes(host.visible))
        releaseHooks()
        val fixedOnly = FakeHookApi(setOf(targets.rebuildMethod))
        assertEquals(1, install(fixedOnly).installedCount)
        rebuild(fixedOnly, host)
        assertEquals(listOf("recommend"), codes(host.visible))
        releaseHooks()
        val complete = FakeHookApi()
        assertEquals(InstallState.INSTALLED, install(complete).state)
        assertEquals(InstallState.ALREADY_INSTALLED, install(complete).state)
        assertEquals(2, complete.callbacks.size)
        val stale = complete.callbacks.getValue(targets.parseMethod!!)
        releaseHooks()
        assertSame(host.raw, stale.intercept(complete.chain(host) { host.raw }))
    }

    private fun install(api: FakeHookApi) = RuntimeHooks.measure("HomeTabHook") {
        HomeTabHook.hook(targets, api.value)
        null
    }

    private fun publish(vararg keys: String) {
        ConfigManager.updateRuntimeSettings { it.copy(
            isHomeTopTabsCustomEnabled = true, isHomeTopTabFixedEnabled = true,
            homeTopTabFixedKeys = keys.toSet(), homeTopTabDisabledKeys = emptySet(),
            isHomeTopTabMaterialEnabled = "code:material" in keys,
            isHomeTopTabRecommendEnabled = "code:recommend" in keys,
            isHomeTopTabLiveEnabled = "code:live" in keys,
            isHomeTopTabFollowedEnabled = TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED in keys,
        ) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(api: FakeHookApi, host: Host): List<Any?> =
        api.invoke(targets.parseMethod!!, host) { host.raw } as List<Any?>

    private fun rebuild(api: FakeHookApi, host: Host) {
        api.invoke(targets.rebuildMethod, host) {
            host.visible.clear()
            host.visible.addAll(parse(api, host))
            null
        }
    }

    private fun changed(api: FakeHookApi, host: Host): Boolean = parse(api, host) != host.visible
    private fun codes(items: List<Any?>): List<String> = items.map {
        when (it) {
            is HomeTabCatalogSnapshot.Item -> it.code.orEmpty()
            is Item -> it.code
            else -> error("Unexpected tab fixture")
        }
    }
    private fun catalog() = HomeTabHook::class.java.getDeclaredField("catalogSnapshot").apply { isAccessible = true }
        .get(null) as HomeTabCatalogSnapshot
    private fun releaseHooks() {
        val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(owned.rollback().isEmpty())
    }

    private data class Item(
        @JvmField var kind: Int = 1, @JvmField var code: String = "recommend",
        @JvmField var title: String = "推荐", @JvmField var url: String = "", @JvmField var main: Int = 1,
    )
    private class NoDefaultConstructor(@JvmField var kind: Int) {
        @JvmField var code = "recommend"
        @JvmField var title = "推荐"
        @JvmField var url = ""
        @JvmField var main = 1
    }
    private class Host(var raw: List<Any?>) {
        @JvmField val visible = ArrayList<Any?>()
        fun parse(): List<Any?> = raw
        fun rebuild() = Unit
    }

    private fun FakeHookApi.invoke(member: Executable, receiver: Any, proceed: () -> Any?): Any? {
        val callback = callbacks[member]
        return if (callback == null) proceed() else callback.intercept(chain(receiver, proceed))
    }

    private fun FakeHookApi.chain(receiver: Any, proceed: () -> Any?): XposedInterface.Chain =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> proceed()
                "getThisObject" -> receiver
                else -> error("Unexpected chain call: ${method.name}")
            }
        } as XposedInterface.Chain
}
