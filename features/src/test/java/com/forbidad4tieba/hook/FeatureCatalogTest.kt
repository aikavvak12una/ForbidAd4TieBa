package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeatureCatalogTest {
    @Test
    fun everyDeclaredFeatureHasOneRegistrationAndTheCatalogOwnsNoRules() {
        val root = File(checkNotNull(System.getProperty("project.root")), "features/src/main")
        val declaration = Regex("""FeatureDefinition(?:\.(?:single|observed))?\(\s*id\s*=\s*"([^"]+)"""")
        val declared = root.walkTopDown().filter { it.extension == "kt" }
            .flatMap { declaration.findAll(it.readText()).map { match -> match.groupValues[1] } }
            .toList()
        val registered = FeatureCatalog.definitions.map { it.id }
        assertTrue("The declaration check must find production definitions", declared.isNotEmpty())
        assertEquals("Duplicate declaration", declared.size, declared.toSet().size)
        assertEquals("Duplicate registration", registered.size, registered.toSet().size)
        assertEquals("Missing or obsolete registration", declared.toSet(), registered.toSet())

        val catalog = File(
            checkNotNull(System.getProperty("project.root")),
            "features/src/main/java/com/forbidad4tieba/hook/FeatureCatalog.kt",
        ).readText()
        listOf("symbol.contract.", "settings.is", "symbols[", "canInstall").forEach { forbidden ->
            assertFalse("Feature rules belong to their owner: $forbidden", forbidden in catalog)
        }
    }

    @Test
    fun mainProcessSelectorsAndStaticPlansStayNarrow() {
        val settings = SettingsSnapshot.bootstrap()
        val aiImage = FeatureCatalog.definitions.single { it.id == "AiImageCreationHook" }
        for (process in listOf("com.baidu.tieba", "com.baidu.tieba:remote",
            "com.baidu.tieba:media", "com.android.systemui")) {
            val context = HookInstallContext(process, null)
            assertEquals(process == "com.baidu.tieba" || process == "com.baidu.tieba:remote", aiImage.process.accepts(context))
            assertTrue(aiImage.entries(context, settings).isEmpty())
        }
        val publish = FeatureCatalog.definitions.single { it.id == "PublishEntryHook" }
        assertEquals(FeaturePhase.POST_ATTACH, publish.phase)
        assertEquals(null, publish.toggle)
        for ((customTabs, floatingBar, expected) in listOf(
            Triple(false, false, 0),
            Triple(true, false, 1),
            Triple(false, true, 1),
            Triple(true, true, 1),
        )) {
            assertEquals(
                expected,
                publish.entries(
                    HookInstallContext("com.baidu.tieba", null),
                    settings.copy(
                        isBottomTabsCustomEnabled = customTabs,
                        isFloatingBottomBarEnabled = floatingBar,
                    ),
                ).size,
            )
        }
        assertTrue(
            publish.entries(
                HookInstallContext("com.baidu.tieba:remote", null),
                settings.copy(isFloatingBottomBarEnabled = true),
            ).isEmpty(),
        )

        for (id in listOf("BottomTabLiquidGlassHook", "PostReplyLiquidGlassHook")) {
            val definition = FeatureCatalog.definitions.single { it.id == id }
            assertTrue(definition.entries(HookInstallContext("com.baidu.tieba", null), settings).isEmpty())
            assertEquals(
                1,
                definition.entries(
                    HookInstallContext("com.baidu.tieba", null),
                    settings.copy(isFloatingBottomBarEnabled = true),
                ).size,
            )
            assertTrue(
                definition.entries(
                    HookInstallContext("com.baidu.tieba:remote", null),
                    settings.copy(isFloatingBottomBarEnabled = true),
                ).isEmpty(),
            )
        }

        assertEquals(
            1,
            FeatureCatalog.staticPlan("com.baidu.tieba").entries.count { it.id == "LaunchSplashHook" },
        )
        assertEquals(
            listOf("SystemSplashHook"),
            FeatureCatalog.staticPlan("com.android.systemui").entries.map { it.id },
        )
        assertFalse(FeatureCatalog.shouldInstallAttachHook("com.android.systemui"))
        assertTrue(
            FeatureCatalog.staticPlan("com.baidu.tieba:remote")
                .entries.none { it.id == "LaunchSplashHook" || it.id == "SystemSplashHook" },
        )
    }
}
