package com.forbidad4tieba.hook.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchitectureBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root = File(checkNotNull(System.getProperty("project.root")))

    @Test fun lowerModulesDoNotImportApplicationOrFeatureImplementation() {
        for (module in listOf("contracts", "host", "runtime")) {
            val files = Konsist.scopeFromExternalDirectory(File(root, "$module/src/main").path).files
            assertTrue("$module must contain production code", files.isNotEmpty())
            val violations = files.flatMap { forbiddenImports(it, module) }
            assertEquals("$module imports cross its build boundary", emptyList<String>(), violations)
        }
    }

    @Test fun featureDomainsUseSharedContractsInsteadOfOtherInstallers() {
        val files = Konsist.scopeFromExternalDirectory(File(root, "features/src/main").path).files
        val violations = files.flatMap { file ->
            val owner = featureDomain(file.packagee?.name.orEmpty())
            file.imports.mapNotNull { dependency ->
                val target = featureDomain(dependency.name)
                if (owner != null && target != null && owner != target && target != "shared") {
                    "${file.name}: $owner -> ${dependency.name}"
                } else null
            }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test fun hooksCannotImportDiscoveryOrCacheImplementations() {
        val files = Konsist.scopeFromExternalDirectory(File(root, "features/src/main").path).files
        val forbidden = listOf("symbol.scan.", "symbol.dexkit.", "symbol.validation.", "symbol.cache.")
        val violations = files.flatMap { file ->
            file.imports.filter { dependency -> forbidden.any { dependency.name.contains(it) } }
                .map { "${file.name}: ${it.name}" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test fun settingsFormsDoNotInstallHooksOrDriveScanAndMenuControllers() {
        val files = Konsist.scopeFromExternalDirectory(File(root, "features/src/main").path).files
        assertEquals(emptyList<String>(), files.flatMap(::settingsFormViolations))
    }

    @Test fun settingsFormBoundaryRejectsAliasedInstallerAndControllerImports() {
        val folder = temporary.newFolder("negative-settings-form")
        File(folder, "BadForm.kt").writeText("""
            package com.forbidad4tieba.hook.ui.settings.forms
            import com.forbidad4tieba.hook.core.RuntimeHooks as Hooks
            import com.forbidad4tieba.hook.ui.settings.SettingsMenuController as Menu
            import com.forbidad4tieba.hook.HookSymbolResolver as Symbols
            class BadForm
        """.trimIndent())
        val violations = Konsist.scopeFromExternalDirectory(folder.path).files
            .flatMap(::settingsFormViolations)
        assertEquals(3, violations.size)
    }

    @Test fun forbiddenImportsAreRejectedEvenWhenAliased() {
        val folder = temporary.newFolder("negative-host")
        File(folder, "BadHost.kt").writeText("""
            package com.forbidad4tieba.hook.symbol.example
            import com.forbidad4tieba.hook.config.ConfigManager as Preferences
            import com.forbidad4tieba.hook.core.XposedCompat
            class BadHost
        """.trimIndent())
        val violations = Konsist.scopeFromExternalDirectory(folder.path).files
            .flatMap { forbiddenImports(it, "host") }
        assertEquals(2, violations.size)
        assertTrue(violations.any { it.contains("ConfigManager") })
        assertTrue(violations.any { it.contains("XposedCompat") })
    }

    @Test fun contractsRejectAndroidAndHostTypes() {
        val folder = temporary.newFolder("negative-contracts")
        File(folder, "BadContract.kt").writeText("""
            package com.forbidad4tieba.hook.contracts
            import android.content.Context
            import com.forbidad4tieba.hook.symbol.model.HookSymbols
            class BadContract
        """.trimIndent())
        val violations = Konsist.scopeFromExternalDirectory(folder.path).files
            .flatMap { forbiddenImports(it, "contracts") }
        assertEquals(2, violations.size)
    }

    private fun forbiddenImports(file: KoFileDeclaration, module: String): List<String> {
        val rootPackage = "com.forbidad4tieba.hook."
        val sharedForbidden = listOf("feature.", "ui.", "config.", "MainHook", "BuildConfig", "FeatureLifecycle")
        val moduleForbidden = when (module) {
            "host" -> sharedForbidden + listOf("core.XposedCompat", "core.OwnedHookSet", "HookInstaller")
            "runtime" -> sharedForbidden + listOf("symbol.", "HookSymbolResolver", "utils.ReflectionUtils")
            else -> sharedForbidden + listOf("symbol.model.HookSymbols", "HookSymbolResolver", "core.XposedCompat")
        }
        return file.imports.mapNotNull { dependency ->
            val name = dependency.name
            val forbidden = moduleForbidden.any { name.startsWith(rootPackage + it) } ||
                (module == "contracts" && (name.startsWith("android.") || name.startsWith("org.json.") || name.startsWith("io.github.libxposed."))) ||
                (module == "host" && name.startsWith("io.github.libxposed.")) ||
                (module == "runtime" && name.startsWith("org.luckypray.dexkit."))
            if (forbidden) "${file.name}: $name" else null
        }
    }

    private fun featureDomain(name: String): String? = name
        .takeIf { it.startsWith("com.forbidad4tieba.hook.feature.") }
        ?.removePrefix("com.forbidad4tieba.hook.feature.")?.substringBefore('.')

    private fun settingsFormViolations(file: KoFileDeclaration): List<String> {
        val prefix = "com.forbidad4tieba.hook."
        val packageName = file.packagee?.name.orEmpty()
        if (listOf("forms", "glass", "modelscore").none {
            packageName.startsWith(prefix + "ui.settings." + it)
        }) return emptyList()
        val forbidden = listOf(
            "core.RuntimeHooks", "HookInstaller", "HookSymbolResolver", "contracts.MemberAccess",
            "ui.SettingsMenuHook", "ui.settings.SettingsMenuController", "ui.settings.SettingsScanController",
        )
        return file.imports.filter { dependency ->
            forbidden.any { dependency.name.startsWith(prefix + it) } ||
                dependency.name.startsWith("java.lang.reflect.") ||
                dependency.name.startsWith("io.github.libxposed.")
        }.map { "${file.name}: ${it.name}" }
    }
}
