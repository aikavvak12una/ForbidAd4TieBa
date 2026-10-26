package com.forbidad4tieba.hook.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A weak key is ineffective when its cached value points back into the same view tree. */
class HomeChromeCacheLifetimeTest {
    private val root = File(checkNotNull(System.getProperty("project.root")))
    private val cacheModels = listOf("ResolvedBottomTab", "BottomTabState", "ContentInsetAdjustment", "AppBarOffsetController")

    @Test fun homeChromeCachesDoNotOwnActivityViewOrBehaviorInstances() {
        val paths = listOf("HomeTopTabAutoHideHook.kt", "HomeBottomTabAutoHideHook.kt", "SystemBarCompatHook.kt")
        val violations = paths.flatMap { name ->
            val source = File(root, "features/src/main/java/com/forbidad4tieba/hook/feature/ui/$name").readText()
            violations(source).map { "$name: $it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test fun directAndAliasedStrongBackReferencesAreRejected() {
        assertEquals(1, violations("val cache = WeakHashMap<Window, Activity>()").size)
        assertEquals(1, violations("data class BottomTabState(val wrapper: View)").size)
        assertEquals(1, violations("import android.view.View as Target\ndata class ResolvedBottomTab(val root: Target)").size)
        assertEquals(1, violations("data class AppBarOffsetController(val behavior: Any)").size)
        assertTrue(violations("data class BottomTabState(val wrapper: WeakReference<View>)").isEmpty())
    }

    private fun violations(source: String): List<String> {
        val aliases = Regex("(?m)^import android\\.(?:view|app)\\.(?:View|ViewGroup|Window|Activity) as (\\w+)")
            .findAll(source).map { it.groupValues[1] }.toList()
        val types = (listOf("View", "ViewGroup", "Window", "Activity", "Any") + aliases).joinToString("|")
        val strongType = "(?:android\\.(?:view|app)\\.)?(?:$types)"
        val failures = Regex("WeakHashMap<\\s*$strongType\\s*,\\s*$strongType\\s*>")
            .findAll(source).map { it.value }.toMutableList()
        for (name in cacheModels) {
            val marker = "class $name("
            val start = source.indexOf(marker)
            if (start < 0) continue
            var depth = 1
            var end = start + marker.length
            while (end < source.length && depth > 0) {
                when (source[end++]) { '(' -> depth++; ')' -> depth-- }
            }
            val header = source.substring(start + marker.length, end)
            Regex("(?:val|var)\\s+\\w+\\s*:\\s*$strongType\\b(?![\\w<])")
                .findAll(header).forEach { failures.add("$name: ${it.value}") }
        }
        return failures
    }
}
