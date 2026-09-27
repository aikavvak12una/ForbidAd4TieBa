package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeatureCatalogTest {
    @Test fun everyDeclaredFeatureHasExactlyOneRegistration() {
        val root = File(checkNotNull(System.getProperty("project.root")), "features/src/main")
        val declaration = Regex("""FeatureDefinition(?:\.(?:single|observed))?\(\s*id\s*=\s*"([^"]+)"""")
        val declared = root.walkTopDown().filter { it.extension == "kt" }
            .flatMap { declaration.findAll(it.readText()).map { match -> match.groupValues[1] } }.toList()
        val registered = FeatureCatalog.definitions.map { it.id }
        assertTrue("The declaration check must find production definitions", declared.isNotEmpty())
        assertEquals("Duplicate declaration", declared.size, declared.toSet().size)
        assertEquals("Duplicate registration", registered.size, registered.toSet().size)
        assertEquals("Missing or obsolete registration", declared.toSet(), registered.toSet())
    }

    @Test fun catalogCannotRedefineFeatureRules() {
        val catalog = File(checkNotNull(System.getProperty("project.root")),
            "features/src/main/java/com/forbidad4tieba/hook/FeatureCatalog.kt").readText()
        listOf("symbol.contract.", "settings.is", "symbols[", "canInstall").forEach { forbidden ->
            assertTrue("Feature rules belong to their owner: $forbidden", forbidden !in catalog)
        }
    }

    @Test fun disabledAndWrongProcessFeaturesDoNotCreateInstallers() {
        var factories = 0
        var installations = 0
        val context = HookInstallContext("com.baidu.tieba", null)
        val definition = FeatureDefinition.observed("example", FeaturePhase.STATIC, FeatureProcess.MAIN,
            enabled = { it.isAutoLoadMoreEnabled }) { _, _ -> installations++ }
        assertTrue(definition.entries(context, SettingsSnapshot()).isEmpty())
        val entries = definition.entries(context, SettingsSnapshot(isAutoLoadMoreEnabled = true))
        assertEquals(0, installations)
        entries.single().install(javaClass.classLoader!!)
        assertEquals(1, installations)

        val guardedFactory = FeatureDefinition("factory", FeaturePhase.STATIC, FeatureProcess.MAIN) { _, _ ->
            factories++
            emptyList()
        }
        guardedFactory.entries(HookInstallContext("com.baidu.tieba:remote", null), SettingsSnapshot())
        assertEquals(0, factories)
    }
}
