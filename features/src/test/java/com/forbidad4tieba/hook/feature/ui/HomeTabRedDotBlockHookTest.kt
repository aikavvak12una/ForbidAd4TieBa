package com.forbidad4tieba.hook.feature.ui

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.contract.*
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

class HomeTabRedDotBlockHookTest {
    private val draw = Badge::class.java.getDeclaredMethod("draw", Canvas::class.java)
    private val text = Badge::class.java.getDeclaredMethod("getRedNum")
    private val visible = Badge::class.java.getDeclaredMethod("setRedDotVisible", Boolean::class.javaPrimitiveType)
    private fun targets(
        visibility: List<Method> = emptyList(),
        message: RedDotMessageTargets? = RedDotMessageTargets(draw, text),
        resources: List<RedDotResourceSetter> = emptyList(),
        ids: Set<Int> = emptySet(),
        navigation: RedDotNavigationTargets? = null,
    ) = RedDotTargets(visibility, message, resources, ids, navigation)

    @After fun release() {
        val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(owned.rollback().isEmpty())
    }

    @Test fun hostReshowCannotPaintPlainDotsAndContentSurvivesRapidUpdates() {
        val api = FakeApi()
        val badge = Badge()
        install(api, targets())
        for (label in listOf(null, "", " ", "7", "...", "99+", "活动", "新", null, "0")) {
            badge.label = label
            badge.shown = true // Host caller shows the badge after refresh has returned.
            var paints = 0
            api.invoke(draw, badge, arrayOf(null)) { paints++; null }
            assertEquals(label, if (label.isNullOrBlank()) 0 else 1, paints)
            assertTrue(badge.shown) // Host visibility/model is untouched; suppression is at paint.
        }
    }

    @Test fun featureOffBypassesTheReaderAndReenablingUsesTheLatestContent() {
        val api = FakeApi()
        val badge = Badge()
        var enabled = true
        install(api, targets(), { enabled })
        api.invoke(draw, badge, arrayOf(null)) { fail("Plain dot painted"); null }
        enabled = false
        badge.label = "99+"
        val before = badge.reads
        var paints = 0
        api.invoke(draw, badge, arrayOf(null)) { paints++; null }
        assertEquals(before, badge.reads)
        enabled = true
        api.invoke(draw, badge, arrayOf(null)) { paints++; null }
        assertEquals(2, paints)
    }

    @Test fun failedReaderPreservesHostDrawing() {
        val api = FakeApi()
        val badge = Badge().apply { brokenReader = true }
        install(api, targets())
        var paints = 0
        api.invoke(draw, badge, arrayOf(null)) { paints++; null }
        assertEquals(1, paints)
    }

    @Test fun failedMessageHookDoesNotPreventIndependentVisibilityHook() {
        val api = FakeApi(setOf(draw))
        val outcome = install(api, targets(listOf(visible)))
        assertEquals(InstallState.PARTIAL, outcome.state)
        assertEquals(1, outcome.installedCount)
        var received: Any? = null
        api.invoke(visible, Badge(), arrayOf(true)) { received = it[0]; null }
        assertEquals(false, received)
        api.invoke(visible, Badge(), arrayOf(false)) { received = it[0]; null }
        assertEquals(false, received)
    }

    @Test fun repeatedInstallCountsHandlesAndReleasedCallbacksAreInert() {
        val api = FakeApi()
        assertEquals(1, install(api, targets()).installedCount)
        assertEquals(InstallState.ALREADY_INSTALLED, install(api, targets()).state)
        assertEquals(1, api.created)
        val stale = api.callbacks.getValue(draw)
        release()
        var paints = 0
        stale.intercept(api.chain(Badge(), arrayOf(null)) { paints++; null })
        assertEquals(1, paints)
    }

    @Test fun resourceScopeOnlyMasksNamedArtworkAfterTheHostSetter() {
        val api = FakeApi()
        val member = Skin::class.java.getDeclaredMethod("background", View::class.java, Int::class.javaPrimitiveType)
        val view = allocate(View::class.java)
        val events = mutableListOf<String>()
        var enabled = true
        var maskEnabled: (() -> Boolean)? = null
        RuntimeHooks.measure("HomeTabRedDotBlockHook") {
            HomeTabRedDotBlockHook.install(
                targets(message = null, resources = listOf(RedDotResourceSetter(member, RedDotLayer.BACKGROUND)),
                    ids = setOf(7)), api.value, { enabled },
            ) { target, layer, active ->
                assertSame(view, target)
                assertEquals(RedDotLayer.BACKGROUND, layer)
                maskEnabled = active
                events.add("mask")
            }
        }
        api.invoke(member, Skin(), arrayOf(view, 7)) { events.add("host"); null }
        assertEquals(listOf("host", "mask"), events)
        events.clear()
        api.invoke(member, Skin(), arrayOf(view, 8)) { events.add("host"); null }
        assertEquals(listOf("host"), events)
        enabled = false
        assertFalse(maskEnabled!!.invoke())
        api.invoke(member, Skin(), arrayOf(view, 7)) { events.add("host"); null }
        assertEquals(listOf("host", "host"), events)
    }

    @Test fun navigationReshowKeepsTheSeparateDotTransparentWithoutRescanningOrTouchingText() {
        val api = FakeApi()
        val dot = allocate(AlphaView::class.java).apply { value = 0.8f }
        val holder = Holder(dot)
        val adapter = Adapter()
        val create = Adapter::class.java.getDeclaredMethod("create", ViewGroup::class.java, Int::class.javaPrimitiveType)
        val bind = Adapter::class.java.getDeclaredMethod("bind", Holder::class.java, Int::class.javaPrimitiveType)
        var scans = 0
        var enabled = true
        val nav = RedDotNavigationTargets(create, bind) { scans++; (it as Holder).dot }
        install(api, targets(message = null, navigation = nav), { enabled })
        api.invoke(create, adapter, arrayOf(null, 1)) { holder }
        assertEquals(0f, dot.value)
        repeat(3) {
            holder.shown = true
            api.invoke(bind, adapter, arrayOf(holder, 0)) { null }
            assertEquals(0f, dot.value)
        }
        assertEquals(1, scans)
        assertEquals("活动 / 7", holder.otherText)
        enabled = false
        api.invoke(bind, adapter, arrayOf(holder, 0)) { null }
        assertEquals(0.8f, dot.value)
    }

    @Test fun failedNavigationBindingRollsBackItsCreateHookAndLeavesMessageDrawing() {
        val create = Adapter::class.java.getDeclaredMethod("create", ViewGroup::class.java, Int::class.javaPrimitiveType)
        val bind = Adapter::class.java.getDeclaredMethod("bind", Holder::class.java, Int::class.javaPrimitiveType)
        val api = FakeApi(setOf(bind))
        val nav = RedDotNavigationTargets(create, bind) { error("No callback should remain") }
        val outcome = install(api, targets(navigation = nav))
        assertEquals(InstallState.PARTIAL, outcome.state)
        assertEquals(1, outcome.installedCount)
        assertFalse(create in api.callbacks)
        assertTrue(draw in api.callbacks)
    }

    private fun install(api: FakeApi, targets: RedDotTargets, enabled: () -> Boolean = { true }) =
        RuntimeHooks.measure("HomeTabRedDotBlockHook") { HomeTabRedDotBlockHook.install(targets, api.value, enabled) }

    class Badge {
        var label: String? = null
        var shown = false
        var reads = 0
        var brokenReader = false
        fun getRedNum(): String? { reads++; check(!brokenReader); return label }
        fun draw(canvas: Canvas?) {}
        fun setRedDotVisible(value: Boolean) {}
    }
    class Skin { fun background(view: View, id: Int) {} }
    class AlphaView : View(null) {
        var value = 1f
        override fun getAlpha() = value
        override fun setAlpha(alpha: Float) { value = alpha }
    }
    class Holder(val dot: AlphaView) {
        var shown = false
        val otherText = "活动 / 7"
    }
    class Adapter {
        fun create(parent: ViewGroup?, type: Int): Holder? = null
        fun bind(holder: Holder, position: Int) {}
    }

    private fun <T> allocate(type: Class<T>): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return checkNotNull(type.cast((field.get(null) as Unsafe).allocateInstance(type)))
    }

    private class FakeApi(private val failing: Set<Executable> = emptySet()) {
        val callbacks = LinkedHashMap<Executable, XposedInterface.Hooker>()
        var created = 0
        val value = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, method, args ->
            check(method.name == "hook")
            val member = args!![0] as Executable
            object : XposedInterface.HookBuilder {
                override fun setId(id: String?) = this
                override fun setPriority(priority: Int) = this
                override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = this
                override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                    check(member !in failing) { "fixture installation failure" }
                    callbacks[member] = hooker
                    created++
                    return object : XposedInterface.HookHandle {
                        override fun getExecutable() = member
                        override fun getId(): String? = null
                        override fun unhook() { callbacks.remove(member) }
                        override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = error("unused")
                    }
                }
            }
        } as XposedInterface

        fun invoke(member: Executable, receiver: Any, args: Array<Any?>,
                   proceed: (Array<Any?>) -> Any?): Any? {
            val callback = callbacks[member]
            return if (callback == null) proceed(args) else callback.intercept(chain(receiver, args, proceed))
        }

        fun chain(receiver: Any, args: Array<Any?>, proceed: (Array<Any?>) -> Any?): XposedInterface.Chain =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, call ->
                when (method.name) {
                    "proceed" -> {
                        @Suppress("UNCHECKED_CAST")
                        proceed(if (call.isNullOrEmpty()) args else call[0] as Array<Any?>)
                    }
                    "getThisObject" -> receiver
                    "getArgs" -> args.asList()
                    else -> error("Unexpected chain call: " + method.name)
                }
            } as XposedInterface.Chain
    }
}

