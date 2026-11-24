package com.forbidad4tieba.hook.feature.ui
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.symbol.model.HomeRefreshCarrier as C
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField as F
import com.forbidad4tieba.hook.symbol.model.HomeRefreshFlowSymbols
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod as M
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.*
import org.junit.Assert.*
import org.junit.Test
@Suppress("UNUSED_PARAMETER")
class HomeRefreshRuntimeTest {
    @Test fun startupSuppressionKeepsCacheSetupAndOutsidePrefetch() = withRuntime { f ->
        f.host.appInitialize()
        assertEquals(1, f.startupWork)
        assertEquals(0, f.sent)
        f.host.prefetch(false)
        assertEquals(1, f.sent)
        f.host.cacheInitialize()
        f.drain()
        assertEquals("cached", f.host.content)
        assertEquals(1, f.sent)
    }

    @Test fun initialEmptyAndNewCachesRequireARealManualRefresh() = withRuntime { f ->
        f.host.cacheInitialize(); f.drain()
        assertEquals("cached", f.host.content)
        assertEquals(0, f.sent)
        assertEquals(1, f.tasks)
        assertEquals(4, f.host.pageNumber)
        f.host.pageTrigger(); f.drain()
        assertEquals(1, f.sent)
        f.data = null; f.host.content = null; f.host.initialized = false; f.tasks = 0; f.sent = 0
        f.host.cacheInitialize(); f.drain()
        assertNull(f.host.content)
        assertEquals(0, f.sent)
        f.host.pageTrigger(); f.drain()
        assertEquals(1, f.sent)
        f.newPath = true; f.data = null; f.host.content = null; f.host.initialized = false
        f.cacheReads = 0; f.sent = 0
        f.host.pageInitialize(); f.host.cacheInitialize(); f.drain()
        assertEquals(1, f.cacheReads)
        assertEquals(0, f.sent)
        f.host.pageTrigger(); f.drain()
        assertEquals(1, f.sent)
    }

    @Test fun lifecycleFailureRestoresTheManualScope() = withRuntime { f ->
        f.host.pageResume(); f.host.pageForeground()
        assertEquals(2, f.lifecycleWork)
        assertEquals(2, f.loadingCleanup)
        assertEquals(0, f.sent)
        f.host.pageTrigger(); f.drain()
        assertEquals(1, f.sent)
        f.failLifecycle = true
        assertThrows(IllegalStateException::class.java) { f.host.pageResume() }
        f.failLifecycle = false; f.host.dispatch()
        assertEquals(2, f.sent)
    }

    @Test fun disabledAndReleasedCallbacksDoNotReadArguments() = withRuntime { f ->
        f.enabled = false; f.api.snapshots.forEach(f::assertInert)
        f.enabled = true; f.runtime.release(); f.api.snapshots.forEach(f::assertInert)
    }

    private fun withRuntime(test: (Fixture) -> Unit) {
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val previous = moduleField.get(null)
        val api = FakeHookApi(allowLog = true)
        var fixture: Fixture? = null
        try {
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            fixture = Fixture(api).also { ENV = it }
            assertEquals(InstallState.INSTALLED, fixture.runtime.install().state)
            test(fixture)
        } finally {
            try {
                api.failUnhook.clear(); fixture?.runtime?.release()
                val hooks = RuntimeHooks::class.java.getDeclaredField("hooks")
                    .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
                assertTrue(hooks.rollback().isEmpty())
            } finally { ENV = null; moduleField.set(null, previous) }
        }
    }
    internal class Fixture(val api: FakeHookApi) {
        var enabled = true; var newPath = false; var failLifecycle = false
        var data: String? = "cached"; var sent = 0; var cacheReads = 0; var tasks = 0
        var lifecycleWork = 0; var startupWork = 0; var loadingCleanup = 0
        val work = arrayListOf<() -> Unit>()
        val host = Host(this)
        val runtime = HomeRefreshRuntime(
            api.value,
            AutoRefreshSymbols(flow(), method(Host::class.java, "scheduler", String::class.java)),
        ) { enabled }
        fun route(role: M, receiver: Any, args: List<Any?>, body: () -> Any?): Any? =
            intercept(flow().methods.getValue(role).first { it.declaringClass.isInstance(receiver) }, receiver, args, body)
        fun construct(role: C, receiver: Any, args: List<Any?>) =
            intercept(flow().constructors.getValue(role).single(), receiver, args) { null }

        fun intercept(member: Executable, receiver: Any, args: List<Any?>, body: () -> Any?): Any? {
            if (api.callbacks[member] == null) return body()
            val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, call, _ ->
                when (call.name) {
                    "getArgs" -> args; "getThisObject" -> receiver
                    "proceed" -> body()
                    else -> error("Unexpected operation ${call.name}")
                }
            } as XposedInterface.Chain
            return checkNotNull(api.callbacks[member]).intercept(chain)
        }
        fun assertInert(callback: XposedInterface.Hooker) {
            var proceeded = 0
            val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, call, _ ->
                if (call.name == "proceed") { proceeded++; null } else error("Inactive hook read ${call.name}")
            } as XposedInterface.Chain
            callback.intercept(chain); assertEquals(1, proceeded)
        }
        fun drain() { var remaining = 100; while (work.isNotEmpty()) { check(--remaining > 0); work.removeAt(0)() } }
    }
    internal class Host(val fixture: Fixture) {
        @JvmField val model = Model(this)
        var initialized = false; var content: String? = null; var pageNumber = 4
        fun appInitialize() = route(M.APP_INITIALIZE, emptyList()) { fixture.startupWork++; prefetch(false); null }
        fun prefetch(value: Boolean) = route(M.INITIAL_PREFETCH, listOf(value)) { fixture.sent++; null }
        fun pageInitialize() = route(M.PAGE_INITIALIZE, emptyList()) { if (fixture.newPath) newRefresh(false); null }
        fun cacheInitialize() = route(M.CACHE_INITIALIZE, emptyList()) {
            if (!initialized && !fixture.newPath) { initialized = true; refresh(false) }; null
        }
        fun pageTrigger() = route(M.PAGE_TRIGGER, emptyList()) {
            setRefreshing(true); if (fixture.newPath) newRefresh(false) else refresh(false); null
        }
        fun pageResume() = route(M.PAGE_RESUME, emptyList()) { lifecycle(); pageTrigger(); null }
        fun pageForeground() = route(M.PAGE_FOREGROUND, emptyList()) { lifecycle(); pageTrigger(); null }
        fun refresh(value: Boolean) = route(M.REFRESH, listOf(value)) { pageNumber = 1; dispatch(); null }
        fun newRefresh(value: Boolean) = route(M.NEW_REFRESH, listOf(value)) { model.dispatch(); null }
        fun cache(value: Boolean) = Carrier(this, value).also { task ->
            fixture.tasks++; fixture.work += { task.post(task.read()) }
        }
        fun dispatch() = route(M.DISPATCH, emptyList()) { fixture.sent++; null }
        fun setRefreshing(value: Boolean) = route(M.REFRESH_INDICATOR, listOf(value)) {
            if (!value) fixture.loadingCleanup++; null
        }
        fun scheduler(key: String) = true
        private fun lifecycle() {
            fixture.lifecycleWork++; setRefreshing(false)
            if (fixture.failLifecycle) error("host lifecycle failure")
        }
        private fun route(role: M, args: List<Any?>, body: () -> Any?) = fixture.route(role, this, args, body)
    }
    internal class Model(private val host: Host) {
        @JvmField val cache = Cache()
        @JvmField val listener = object : Listener {
            override fun deliver(data: String?) { if (data != null) host.content = data; if (data == null) dispatch() }
        }
        fun dispatch() = host.fixture.route(M.NEW_DISPATCH, this, emptyList()) { host.fixture.sent++; null }
    }
    internal interface Listener { fun deliver(data: String?) }
    internal class Cache {
        fun load(listener: Listener, value: Boolean) = Carrier(listener, value).also { callback ->
            ENV!!.cacheReads++; ENV!!.work += { callback.post(ENV!!.data) }
        }
    }
    internal class Carrier private constructor(val host: Host?, val listener: Listener?) {
        constructor(host: Host, flag: Boolean) : this(host, null) { ENV!!.construct(C.CACHE_TASK, this, listOf(host, flag)) }
        constructor(listener: Listener, flag: Boolean) : this(null, listener) {
            ENV!!.construct(C.CACHE_CALLBACK, this, listOf(listener, flag))
        }
        fun read(): String? = ENV!!.route(M.CACHE_BACKGROUND, this, emptyList()) {
            ENV!!.data.also { if (it == null) host!!.dispatch() else host!!.content = it }
        } as? String
        fun post(data: String?) = ENV!!.route(M.CACHE_POST, this, listOf(data)) {
            if (listener != null) listener.deliver(data) else {
                if (data != null) ENV!!.work += { Continuation(this).run() }
            }; null
        }
    }
    internal class Continuation(@JvmField val task: Carrier) {
        fun run() = ENV!!.route(M.CACHE_CONTINUE, this, emptyList()) { task.host!!.refresh(false); null }
    }
    internal class Idle(@JvmField val task: Carrier) {
        fun run(): Boolean = ENV!!.route(M.IDLE_CONTINUE, this, emptyList()) { task.host!!.pageTrigger(); false } as Boolean
    }
    private companion object {
        var ENV: Fixture? = null
        fun method(type: Class<*>, name: String, vararg params: Class<*>): Method = type.getMethod(name, *params)
        fun flow(): HomeRefreshFlowSymbols {
            val z = Boolean::class.javaPrimitiveType!!
            val m = linkedMapOf<M, List<Method>>()
            fun set(role: M, name: String, vararg params: Class<*>) { m[role] = listOf(method(Host::class.java, name, *params)) }
            set(M.APP_INITIALIZE, "appInitialize"); set(M.INITIAL_PREFETCH, "prefetch", z)
            set(M.PAGE_INITIALIZE, "pageInitialize"); set(M.CACHE_INITIALIZE, "cacheInitialize")
            set(M.PAGE_RESUME, "pageResume"); set(M.PAGE_FOREGROUND, "pageForeground"); set(M.PAGE_TRIGGER, "pageTrigger")
            set(M.REFRESH, "refresh", z); set(M.NEW_REFRESH, "newRefresh", z)
            m[M.CACHE_LOAD] = listOf(method(Host::class.java, "cache", z), method(Cache::class.java, "load", Listener::class.java, z))
            set(M.DISPATCH, "dispatch")
            m[M.NEW_DISPATCH] = listOf(method(Model::class.java, "dispatch"))
            m[M.CACHE_BACKGROUND] = listOf(method(Carrier::class.java, "read"))
            m[M.CACHE_POST] = listOf(method(Carrier::class.java, "post", String::class.java))
            m[M.CACHE_CONTINUE] = listOf(method(Continuation::class.java, "run"))
            m[M.IDLE_CONTINUE] = listOf(method(Idle::class.java, "run"))
            set(M.REFRESH_INDICATOR, "setRefreshing", z)
            val c = mapOf<C, List<Constructor<*>>>(
                C.CACHE_TASK to listOf(Carrier::class.java.getConstructor(Host::class.java, z)),
                C.CACHE_CALLBACK to listOf(Carrier::class.java.getConstructor(Listener::class.java, z)))
            val f = mapOf(F.FEED_OWNER_MODEL to Host::class.java.getField("model"),
                F.NEW_FEED_CACHE to Model::class.java.getField("cache"),
                F.NEW_FEED_CACHE_LISTENER to Model::class.java.getField("listener"),
                F.CACHE_CONTINUATION_TASK to Continuation::class.java.getField("task"),
                F.IDLE_CONTINUATION_TASK to Idle::class.java.getField("task"))
            return HomeRefreshFlowSymbols(m, c, f)
        }
    }
}
