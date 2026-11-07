package com.forbidad4tieba.hook.feature.ui

import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.ViewOffsetBehavior
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeTopTabAutoHideMetadataTest {
    private val owner = HomeTopTabAutoHideHook
    private val loader = checkNotNull(javaClass.classLoader)
    private val cachePairs = listOf(
        "expandedMethodCache" to "expandedMethodMissCache",
        "totalScrollRangeMethodCache" to "totalScrollRangeMethodMissCache",
        "offsetChangedMethodCache" to "offsetChangedMethodMissCache",
        "offsetMethodCache" to "offsetMethodMissCache",
        "dependentDispatchMethodCache" to "dependentDispatchMethodMissCache",
        "behaviorMethodCache" to "behaviorMethodMissCache",
        "methodCache" to "methodMissCache",
    )

    @Before fun clearMetadataCaches() {
        cachePairs.forEach { (cache, misses) ->
            cache(cache).clear()
            misses(misses).clear()
        }
    }

    @Test fun installationPreparesAllSevenGroupsAndTheirRuntimeReadsReuseTheSameMetadata() {
        owner.prepareFrameworkMethods(loader)
        val expected = listOf(
            Triple("expandedMethod", "expandedMethodCache", AppBarLayout::class.java),
            Triple("totalScrollRangeMethod", "totalScrollRangeMethodCache", AppBarLayout::class.java),
            Triple("offsetChangedMethod", "offsetChangedMethodCache", AppBarLayout::class.java),
            Triple("offsetMethods", "offsetMethodCache", AppBarLayout.Behavior::class.java),
            Triple("dependentDispatchMethod", "dependentDispatchMethodCache", CoordinatorLayout::class.java),
            Triple("behaviorMethods", "behaviorMethodCache", CoordinatorLayout.LayoutParams::class.java),
            Triple("scrollFlagMethods", "methodCache", AppBarLayout.LayoutParams::class.java),
        )
        expected.forEach { (accessor, cacheName, type) ->
            val prepared = cache(cacheName)[type]
            assertNotNull("$accessor must be ready before its first runtime use", prepared)
            assertSame(prepared, lookup(accessor, type))
        }
        assertNotNull(cache("offsetMethodCache")[AppBarLayout.BaseBehavior::class.java])
        assertNotNull(cache("offsetMethodCache")[ViewOffsetBehavior::class.java])
    }

    @Test fun preparedMetadataStillReadsCurrentRangeOffsetFlagsAndBehaviorAndDeliversNotifications() {
        owner.prepareFrameworkMethods(loader)
        val appBar = AppBarLayout()
        val range = lookup("totalScrollRangeMethod", appBar.javaClass) as Method
        assertEquals(80, range.invoke(appBar))
        appBar.range = 135
        assertEquals(135, range.invoke(appBar))

        val behavior = AppBarLayout.Behavior()
        val offsets = checkNotNull(lookup("offsetMethods", behavior.javaClass))
        val getOffset = member(offsets, "getTopAndBottomOffset")
        val setOffset = member(offsets, "setTopAndBottomOffset")
        assertEquals(0, getOffset.invoke(behavior))
        assertEquals(true, setOffset.invoke(behavior, -43))
        assertEquals(-43, getOffset.invoke(behavior))
        assertEquals(false, setOffset.invoke(behavior, -43))
        behavior.setTopAndBottomOffset(-77)
        assertEquals(-77, getOffset.invoke(behavior))

        val notifyOffset = lookup("offsetChangedMethod", appBar.javaClass) as Method
        notifyOffset.invoke(appBar, -43)
        notifyOffset.invoke(appBar, -77)
        assertEquals(2, appBar.offsetNotifications)
        assertEquals(-77, appBar.lastOffset)
        val coordinator = CoordinatorLayout()
        val notifyDependency = lookup("dependentDispatchMethod", coordinator.javaClass) as Method
        notifyDependency.invoke(coordinator, *arrayOfNulls<Any>(1))
        notifyDependency.invoke(coordinator, *arrayOfNulls<Any>(1))
        assertEquals(2, coordinator.dependencyNotifications)

        val expanded = lookup("expandedMethod", appBar.javaClass) as Method
        expanded.invoke(appBar, false, false)
        assertFalse(appBar.expanded)
        assertFalse(appBar.animated)
        expanded.invoke(appBar, true, true)
        assertTrue(appBar.expanded)
        assertTrue(appBar.animated)

        val params = AppBarLayout.LayoutParams()
        val flags = checkNotNull(lookup("scrollFlagMethods", params.javaClass))
        member(flags, "setScrollFlags").invoke(params, 5)
        assertEquals(5, member(flags, "getScrollFlags").invoke(params))
        params.setScrollFlags(1)
        assertEquals(1, member(flags, "getScrollFlags").invoke(params))

        val layout = CoordinatorLayout.LayoutParams()
        val methods = checkNotNull(lookup("behaviorMethods", layout.javaClass))
        val first = CoordinatorLayout.Behavior()
        val replacement = CoordinatorLayout.Behavior()
        member(methods, "setBehavior").invoke(layout, first)
        assertSame(first, member(methods, "getBehavior").invoke(layout))
        layout.setBehavior(replacement)
        assertSame(replacement, member(methods, "getBehavior").invoke(layout))
    }

    @Test fun unfamiliarConcreteClassesRetainPositiveAndNegativeCaching() {
        owner.prepareFrameworkMethods(loader)
        val unknown = UnknownBehavior()
        assertNull(cache("offsetMethodCache")[unknown.javaClass])
        val resolved = checkNotNull(lookup("offsetMethods", unknown.javaClass))
        assertSame(resolved, lookup("offsetMethods", unknown.javaClass))
        unknown.setTopAndBottomOffset(-21)
        assertEquals(-21, member(resolved, "getTopAndBottomOffset").invoke(unknown))

        val accessors = listOf(
            "expandedMethod", "totalScrollRangeMethod", "offsetChangedMethod", "offsetMethods",
            "dependentDispatchMethod", "behaviorMethods", "scrollFlagMethods",
        )
        accessors.zip(cachePairs).forEach { (accessor, pair) ->
            assertNull(lookup(accessor, MissingApis::class.java))
            assertTrue(misses(pair.second).contains(MissingApis::class.java))
            assertNull(lookup(accessor, MissingApis::class.java))
            assertFalse(cache(pair.first).containsKey(MissingApis::class.java))
        }
    }

    @Test fun oneUnavailableFrameworkClassDoesNotPreventPreparingOtherCapabilities() {
        val partialLoader = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == AppBarLayout::class.java.name) throw NoClassDefFoundError("optional fixture")
                return super.loadClass(name, resolve)
            }
        }
        owner.prepareFrameworkMethods(partialLoader)
        assertTrue(cache("expandedMethodCache").isEmpty())
        assertNotNull(cache("methodCache")[AppBarLayout.LayoutParams::class.java])
        assertNotNull(cache("behaviorMethodCache")[CoordinatorLayout.LayoutParams::class.java])
        assertNotNull(cache("offsetMethodCache")[AppBarLayout.Behavior::class.java])
    }

    private fun lookup(name: String, type: Class<*>): Any? = owner.javaClass
        .getDeclaredMethod(name, Class::class.java).apply { isAccessible = true }.invoke(owner, type)

    private fun member(group: Any, name: String): Method = group.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(group) as Method

    @Suppress("UNCHECKED_CAST")
    private fun cache(name: String): MutableMap<Class<*>, Any> = owner.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableMap<Class<*>, Any>

    @Suppress("UNCHECKED_CAST")
    private fun misses(name: String): MutableSet<Class<*>> = owner.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableSet<Class<*>>

    private class UnknownBehavior : ViewOffsetBehavior()
    private class MissingApis
}
