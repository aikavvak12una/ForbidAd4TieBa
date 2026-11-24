package com.forbidad4tieba.hook.feature.ui
import android.graphics.Canvas
import android.view.View
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.contract.RedDotLayer
import com.forbidad4tieba.hook.symbol.contract.RedDotMessageTargets
import com.forbidad4tieba.hook.symbol.contract.RedDotResourceSetter
import com.forbidad4tieba.hook.symbol.contract.RedDotTargets
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe
class HomeTabRedDotBlockHookTest {
    private val draw = Badge::class.java.getDeclaredMethod("draw", Canvas::class.java)
    private val text = Badge::class.java.getDeclaredMethod("getRedNum")

    @After fun release() {
        val hooks = RuntimeHooks::class.java.getDeclaredField("hooks")
            .apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(hooks.rollback().isEmpty())
    }
    @Test fun contentLabelsAndReaderFailuresPreserveTheHostModel() {
        val api = FakeHookApi(); val badge = Badge()
        install(api, targets())
        for (label in listOf(null, "", "7", "99+", "活动")) {
            badge.label = label; badge.shown = true; var paints = 0
            api.call(draw, listOf(null), badge) { paints++; null }
            assertEquals(label, if (label.isNullOrBlank()) 0 else 1, paints)
            assertTrue(badge.shown); assertEquals(label, badge.label)
        }
        badge.brokenReader = true; var paints = 0
        api.call(draw, listOf(null), badge) { paints++; null }
        assertEquals(1, paints)
    }
    @Test fun disabledPaintPathReadsNeitherReceiverNorLabel() {
        val api = FakeHookApi(); var enabled = false
        install(api, targets(), { enabled })
        var proceeded = 0
        val chain = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java),
        ) { _, method, _ ->
            if (method.name == "proceed") { proceeded++; null } else error("disabled read ${method.name}")
        } as XposedInterface.Chain
        api.callbacks.getValue(draw).intercept(chain)
        assertEquals(1, proceeded)
        enabled = true; var paints = 0
        api.call(draw, listOf(null), Badge().apply { label = "7" }) { paints++; null }
        assertEquals(1, paints)
    }
    @Test fun resourceMaskRunsAfterHostOnlyForNamedArtworkScope() {
        val api = FakeHookApi()
        val member = Skin::class.java.getDeclaredMethod("background", View::class.java, Int::class.javaPrimitiveType)
        val view = allocate(View::class.java); val events = arrayListOf<String>()
        var enabled = true; var maskEnabled: (() -> Boolean)? = null
        install(
            api, targets(listOf(RedDotResourceSetter(member, RedDotLayer.BACKGROUND)), setOf(7)), { enabled },
        ) { target, layer, active ->
            assertSame(view, target); assertEquals(RedDotLayer.BACKGROUND, layer)
            maskEnabled = active; events += "mask"
        }
        api.call(member, arrayOf<Any?>(view, 7).toList(), Skin()) { events += "host"; null }
        assertEquals(listOf("host", "mask"), events)
        events.clear(); api.call(member, arrayOf<Any?>(view, 8).toList(), Skin()) { events += "host"; null }
        assertEquals(listOf("host"), events)
        enabled = false; assertFalse(maskEnabled!!.invoke())
        api.call(member, arrayOf<Any?>(view, 7).toList(), Skin()) { events += "host"; null }
        assertEquals(listOf("host", "host"), events)
    }
    private fun targets(
        resources: List<RedDotResourceSetter> = emptyList(),
        ids: Set<Int> = emptySet(),
    ) = RedDotTargets(emptyList(), RedDotMessageTargets(draw, text), resources, ids, null)
    private fun install(
        api: FakeHookApi,
        targets: RedDotTargets,
        enabled: () -> Boolean = { true },
        mask: (View, RedDotLayer, () -> Boolean) -> Unit = { _, _, _ -> },
    ) = RuntimeHooks.measure("HomeTabRedDotBlockHook") {
        HomeTabRedDotBlockHook.install(targets, api.value, enabled, mask)
    }
    class Badge {
        var label: String? = null; var shown = false; var brokenReader = false
        fun getRedNum(): String? { check(!brokenReader); return label }
        fun draw(canvas: Canvas?) = Unit
    }
    class Skin { fun background(view: View, id: Int) = Unit }
    private fun <T> allocate(type: Class<T>): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return checkNotNull(type.cast((field.get(null) as Unsafe).allocateInstance(type)))
    }
}
