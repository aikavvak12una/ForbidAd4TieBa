package com.forbidad4tieba.hook.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackgroundBoundaryTest {
    private val namespace = "com.forbidad4tieba.hook.feature.ui.nativeglass."
    private val root = File(checkNotNull(System.getProperty("project.root")))

    @Test fun backgroundImplementationHasOnlyExplicitIntegrationConsumers() {
        val sourceRoot = File(root, "features/src/main/java/com/forbidad4tieba/hook")
        val violations = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                dependencies(file.relativeTo(sourceRoot).invariantSeparatorsPath, file.readText()).asSequence()
            }.toList()
        assertEquals(emptyList<String>(), violations)
    }

    @Test fun otherFeaturesCannotImportTheBackgroundCacheEvenThroughAnAlias() {
        val source = "import ${namespace}HomeNativeGlassImageCache as Cache"
        assertEquals(1, dependencies("feature/ui/HistorySearchHook.kt", source).size)
        assertEquals(1, dependencies("ui/UiStyle.kt", source).size)
    }

    @Test fun fullyQualifiedAccessCannotBypassTheBoundary() {
        val source = "fun load() = ${namespace}HomeNativeGlassHook.hook(loader, symbols)"
        assertEquals(1, dependencies("feature/ui/HomeTabHook.kt", source).size)
        assertTrue(dependencies("ui/UiStyle.kt", "import ${namespace}HomeNativeGlassIntegration").isEmpty())
    }

    @Test fun backgroundCannotDependOnAnotherFeatureRenderer() {
        val source = "import com.forbidad4tieba.hook.feature.ui.liquidglass.BottomTabLiquidGlassHook as Bar"
        assertEquals(1, dependencies("feature/ui/nativeglass/HomeNativeGlassHook.kt", source).size)
        assertTrue(dependencies("feature/ui/nativeglass/HomeNativeGlassHook.kt",
            "import com.forbidad4tieba.hook.feature.ui.HomeBottomBarOwnership").isEmpty())
    }

    private fun dependencies(path: String, source: String): List<String> {
        val owner = path.startsWith("feature/ui/nativeglass/")
        val ownedForm = path.startsWith("ui/settings/glass/")
        if (owner) {
            val sharedChrome = setOf(
                "com.forbidad4tieba.hook.feature.ui.HomeBottomBarOwnership",
                "com.forbidad4tieba.hook.feature.ui.SystemBarCompatHook",
            )
            return Regex("(?m)^import (com\\.forbidad4tieba\\.hook\\.feature\\.[\\w.*]+)")
                .findAll(source).map { it.groupValues[1] }
                .filter { !it.startsWith(namespace) && it !in sharedChrome }
                .map { "$path -> $it" }.toList()
        }
        if (ownedForm) return emptyList()
        val allowed = when (path) {
            "FeatureCatalog.kt" -> setOf("HomeNativeGlassFeature", "BottomTabTopLineFeature")
            "ui/SettingsMenuFeature.kt", "ui/SettingsMenuUi.kt", "ui/UiStyle.kt" -> setOf("HomeNativeGlassIntegration")
            else -> emptySet()
        }
        return Regex(Regex.escape(namespace) + "([A-Za-z_][A-Za-z_0-9]*|\\*)")
            .findAll(source).map { it.groupValues[1] }.distinct()
            .filter { it !in allowed }.map { "$path -> $it" }.toList()
    }
}
