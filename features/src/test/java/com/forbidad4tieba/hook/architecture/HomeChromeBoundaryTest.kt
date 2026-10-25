package com.forbidad4tieba.hook.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeChromeBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root = File(checkNotNull(System.getProperty("project.root")))

    @Test fun sharedHomeChromeDoesNotLoadTheFloatingRenderer() {
        val files = Konsist.scopeFromExternalDirectory(File(root, "features/src/main").path).files
        assertEquals(emptyList<String>(), files.flatMap(::rendererDependencies))
    }

    @Test fun boundaryRejectsRendererDependenciesEvenWhenAliased() {
        val directory = temporary.newFolder("bad-home-chrome")
        File(directory, "SystemBarCompatHook.kt").writeText("""
            package com.forbidad4tieba.hook.feature.ui
            import com.forbidad4tieba.hook.feature.ui.liquidglass.BottomTabLiquidGlassHook as Bar
            class SystemBarCompatHook
        """.trimIndent())
        assertEquals(1, Konsist.scopeFromExternalDirectory(directory.path).files
            .flatMap(::rendererDependencies).size)
    }

    private fun rendererDependencies(file: KoFileDeclaration): List<String> {
        if (file.name.removeSuffix(".kt") !in setOf(
                "SystemBarCompatHook", "HomeNativeGlassHook", "HomeBottomBarOwnership",
            )) return emptyList()
        return file.imports.filter {
            it.name.startsWith("com.forbidad4tieba.hook.feature.ui.liquidglass.")
        }.map { "${file.name}: ${it.name}" }
    }
}
