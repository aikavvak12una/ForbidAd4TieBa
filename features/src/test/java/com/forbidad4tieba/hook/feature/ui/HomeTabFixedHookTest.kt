package com.forbidad4tieba.hook.feature.ui
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.TabPreferences
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HomeTabItemTargets
import com.forbidad4tieba.hook.symbol.model.HomeTabResolvedSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
internal fun XposedInterface.Hooker.call(
    args: List<Any?> = emptyList(),
    receiver: Any? = null,
    proceed: (Array<Any?>) -> Any? = { args.toTypedArray() },
): Any? = intercept(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, call ->
        when (method.name) {
            "getArgs" -> args
            "getThisObject" -> receiver ?: error("missing callback receiver")
            "proceed" -> {
                @Suppress("UNCHECKED_CAST")
                proceed(if (call.isNullOrEmpty()) args.toTypedArray() else call[0] as Array<Any?>)
            }
            else -> error("chain ${method.name}")
        }
    } as XposedInterface.Chain)
internal fun FakeHookApi.call(
    member: Executable,
    args: List<Any?> = emptyList(),
    receiver: Any? = null,
    proceed: (Array<Any?>) -> Any? = { it },
): Any? {
    val callback = callbacks[member] ?: return proceed(args.toTypedArray())
    return callback.call(args, receiver, proceed)
}
class HomeTabFixedHookTest {
    private val previous = ConfigManager.snapshot()
    private val targets = HomeTabResolvedSymbols(
        Host::class.java, Host::class.java.getDeclaredMethod("rebuild"),
        Host::class.java.getDeclaredField("visible").apply { isAccessible = true },
        Host::class.java.getDeclaredMethod("parse"),
        HomeTabItemTargets(
            Item::class.java, Item::class.java.getDeclaredConstructor().apply { isAccessible = true },
            field("kind"), field("code"), field("title"), field("url"), null, field("main"), null,
        ),
    )
    @After fun release() {
        val hooks = RuntimeHooks::class.java.getDeclaredField("hooks")
            .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(hooks.rollback().isEmpty())
        ConfigManager.updateRuntimeSettings { previous }
    }
    @Test fun fixedModeKeepsRawCatalogAndFallsBackForEmptyOrDisabledInput() {
        publish("code:recommend", TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi(); val host = Host(listOf(Item(code = "event"), Item(), Item(code = "live", kind = 6)))
        assertEquals(2, install(api).installedCount)
        rebuild(api, host)
        assertEquals(listOf("recommend", "my_followed"), codes(host.visible))
        assertEquals(listOf("event", "recommend", "live"), codes(host.raw))
        assertEquals(listOf("event", "recommend", "live"), codes(catalog().current()))

        publish("code:recommend")
        host.raw = emptyList()
        assertSame(host.raw, parse(api, host))
        ConfigManager.updateRuntimeSettings { it.copy(isHomeTopTabsCustomEnabled = false, isHomeTopTabFixedEnabled = false) }
        val hostItems = listOf(Item(), Item(code = "new"))
        host.raw = hostItems
        assertSame(hostItems, parse(api, host))
        rebuild(api, host)
        assertEquals(hostItems, host.visible)
    }
    @Test fun followedInjectionRequiresAUsableTemplateAndNeverCommitsFailure() {
        publish(TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED)
        val api = FakeHookApi(); install(api); val host = Host(listOf(Item()))
        rebuild(api, host)
        assertEquals(listOf("my_followed"), codes(host.visible))
        val unwritable = listOf(NoDefaultConstructor(1))
        host.raw = unwritable
        assertSame(unwritable, parse(api, host))
    }
    private fun field(name: String) = Item::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun install(api: FakeHookApi) = RuntimeHooks.measure("HomeTabHook") { HomeTabHook.hook(targets, api.value); null }
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
        api.call(targets.parseMethod!!, receiver = host) { host.raw } as List<Any?>
    private fun rebuild(api: FakeHookApi, host: Host) = api.call(targets.rebuildMethod, receiver = host) {
        host.visible.clear(); host.visible.addAll(parse(api, host)); null
    }
    private fun codes(items: List<Any?>): List<String> = items.map {
        when (it) { is HomeTabCatalogSnapshot.Item -> it.code.orEmpty(); is Item -> it.code; else -> error("tab") }
    }
    private fun catalog(): HomeTabCatalogSnapshot = HomeTabHook::class.java
        .getDeclaredField("catalogSnapshot").apply { isAccessible = true }.get(null) as HomeTabCatalogSnapshot
    private data class Item(
        @JvmField var kind: Int = 1, @JvmField var code: String = "recommend",
        @JvmField var title: String = "推荐", @JvmField var url: String = "", @JvmField var main: Int = 1,
    )
    private class NoDefaultConstructor(@JvmField var kind: Int) {
        @JvmField var code = "recommend"; @JvmField var title = "推荐"; @JvmField var url = ""; @JvmField var main = 1
    }
    private class Host(var raw: List<Any?>) {
        @JvmField val visible = ArrayList<Any?>()
        fun parse(): List<Any?> = raw
        fun rebuild() = Unit
    }
}
