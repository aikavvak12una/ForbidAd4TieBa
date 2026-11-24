package com.forbidad4tieba.hook.feature.ui
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
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
    private val groups = listOf(
        Triple("expandedMethod", "expandedMethodCache", AppBarLayout::class.java),
        Triple("totalScrollRangeMethod", "totalScrollRangeMethodCache", AppBarLayout::class.java),
        Triple("offsetChangedMethod", "offsetChangedMethodCache", AppBarLayout::class.java),
        Triple("offsetMethods", "offsetMethodCache", AppBarLayout.Behavior::class.java),
        Triple("dependentDispatchMethod", "dependentDispatchMethodCache", CoordinatorLayout::class.java),
        Triple("behaviorMethods", "behaviorMethodCache", CoordinatorLayout.LayoutParams::class.java),
        Triple("scrollFlagMethods", "methodCache", AppBarLayout.LayoutParams::class.java),
    )
    private val missCaches = groups.map { (_, cache) -> cache to cache.replace("Cache", "MissCache") }
    @Before fun clear() {
        missCaches.forEach { (cache, misses) -> this.cache(cache).clear(); this.misses(misses).clear() }
    }
    @Test fun installationPreparesEveryLookupBeforeRuntimeUse() {
        owner.prepareFrameworkMethods(loader)
        groups.forEach { (accessor, cacheName, type) ->
            val prepared = cache(cacheName)[type]
            assertNotNull(accessor, prepared)
            assertSame(accessor, prepared, lookup(accessor, type))
        }
    }
    @Test fun missingFrameworkApisUseTheDedicatedNegativeCache() {
        owner.prepareFrameworkMethods(loader)
        groups.zip(missCaches).forEach { (group, pair) ->
            assertNull(lookup(group.first, MissingApis::class.java))
            assertTrue(misses(pair.second).contains(MissingApis::class.java))
            assertNull(lookup(group.first, MissingApis::class.java))
            assertFalse(cache(pair.first).containsKey(MissingApis::class.java))
        }
    }
    private fun lookup(name: String, type: Class<*>): Any? =
        owner.javaClass.getDeclaredMethod(name, Class::class.java).apply { isAccessible = true }.invoke(owner, type)
    @Suppress("UNCHECKED_CAST")
    private fun cache(name: String): MutableMap<Class<*>, Any> =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableMap<Class<*>, Any>
    @Suppress("UNCHECKED_CAST")
    private fun misses(name: String): MutableSet<Class<*>> =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableSet<Class<*>>
    private class MissingApis
}
