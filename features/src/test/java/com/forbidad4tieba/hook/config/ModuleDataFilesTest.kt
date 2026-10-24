package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.contracts.ModuleDataPaths
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModuleDataFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun completeInventoryIncludesPreferencesBackgroundsCachesAndNativeExtraction() {
        assertEquals(setOf("tbhook_user_settings", "tbhook_module_state", "tbhook_symbol_cache", "tiebahook_settings"),
            ModuleDataPaths.preferenceNames(true).toSet())
        assertFalse(ModuleDataPaths.preferenceNames(false).contains(ModuleDataPaths.USER_SETTINGS))
        assertEquals(ModuleDataPaths.files.size, ModuleDataPaths.files.distinctBy { it.area to it.relativePath }.size)
        assertTrue(ModuleDataPaths.files.any { it.relativePath == "home_native_glass" && it.fullResetOnly })
        assertTrue(ModuleDataPaths.files.any { it.relativePath == "tbhook_native_libs" && it.area == ModuleDataPaths.Area.CODE_CACHE })
    }

    @Test fun scanKeepsUserChoicesAndFullResetDeletesEveryOwnedFixtureButNoHostFile() {
        val roots = ModuleDataPaths.Area.entries.associateWith { temporary.newFolder(it.name) }
        val prefs = temporary.newFolder("shared_prefs")
        for (name in ModuleDataPaths.preferenceNames(true)) {
            File(prefs, "$name.xml").writeText("settings, including unknown and retired keys")
            File(prefs, "$name.xml.bak").writeText("backup")
        }
        for (entry in ModuleDataPaths.files) {
            val path = ModuleDataFiles.resolve(roots.getValue(entry.area), entry.relativePath)
            if (path.name.contains('.')) {
                checkNotNull(path.parentFile).mkdirs()
                path.writeText("module data")
            } else {
                File(path, "nested").mkdirs()
                File(path, "nested/owned.bin").writeText("module data")
            }
        }
        val hostFile = File(roots.getValue(ModuleDataPaths.Area.FILES), "host_account.json").apply { writeText("keep") }
        val hostPrefs = File(prefs, "tieba_settings.xml").apply { writeText("keep") }
        for (name in ModuleDataPaths.preferenceNames(false)) {
            ModuleDataFiles.delete(prefs, "$name.xml")
            ModuleDataFiles.delete(prefs, "$name.xml.bak")
        }
        for (entry in ModuleDataPaths.files.filterNot { it.fullResetOnly })
            ModuleDataFiles.delete(roots.getValue(entry.area), entry.relativePath)
        assertTrue(File(prefs, "${ModuleDataPaths.USER_SETTINGS}.xml").isFile)
        assertTrue(File(roots.getValue(ModuleDataPaths.Area.FILES), "home_native_glass/nested/owned.bin").isFile)
        for (name in ModuleDataPaths.preferenceNames(true)) {
            ModuleDataFiles.delete(prefs, "$name.xml")
            ModuleDataFiles.delete(prefs, "$name.xml.bak")
        }
        for (entry in ModuleDataPaths.files) {
            ModuleDataFiles.delete(roots.getValue(entry.area), entry.relativePath)
            assertFalse(ModuleDataFiles.resolve(roots.getValue(entry.area), entry.relativePath).exists())
        }
        assertFalse(File(prefs, "${ModuleDataPaths.USER_SETTINGS}.xml").exists())
        assertFalse(File(prefs, "${ModuleDataPaths.USER_SETTINGS}.xml.bak").exists())
        assertEquals("keep", hostFile.readText())
        assertEquals("keep", hostPrefs.readText())
    }

    @Test fun traversalAndInvalidRootsFailInsteadOfBecomingSuccessfulNoOps() {
        val root = temporary.newFolder("root")
        for (name in listOf("", ".", "..", "../host", "owned/../../host", "C:/host", "owned\\host")) {
            assertThrows(IllegalArgumentException::class.java) { ModuleDataFiles.delete(root, name) }
        }
        val file = File(root, "not-a-directory").apply { writeText("keep") }
        assertThrows(IllegalStateException::class.java) { ModuleDataFiles.delete(file, "child") }
        assertFalse(ModuleDataFiles.delete(root, "absent"))
        assertEquals("keep", file.readText())
    }

    @Test fun symlinkCannotRedirectAnOwnedRootAndNestedLinksDoNotDeleteTheirTargets() {
        val root = temporary.newFolder("root")
        val outside = temporary.newFolder("outside")
        val host = File(outside, "host.txt").apply { writeText("keep") }
        try { Files.createSymbolicLink(File(root, "owned").toPath(), outside.toPath()) }
        catch (unavailable: Exception) { assumeNoException(unavailable); return }
        assertThrows(IllegalStateException::class.java) { ModuleDataFiles.delete(root, "owned") }
        Files.delete(File(root, "owned").toPath())
        val owned = File(root, "owned").apply { mkdirs() }
        Files.createSymbolicLink(File(owned, "nested-link").toPath(), outside.toPath())
        assertTrue(ModuleDataFiles.delete(root, "owned"))
        assertEquals("keep", host.readText())
    }
}
