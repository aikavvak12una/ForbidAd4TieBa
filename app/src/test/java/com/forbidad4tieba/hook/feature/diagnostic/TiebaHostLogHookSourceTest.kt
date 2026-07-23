package com.forbidad4tieba.hook.feature.diagnostic

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiebaHostLogHookSourceTest {
    @Test
    fun hostLogCallbackOnlyUsesBoundedInMemoryLogging() {
        val source = readSource(
            "app/src/main/java/com/forbidad4tieba/hook/feature/diagnostic/TiebaHostLogHook.kt",
        )

        assertTrue(source.contains("DetailedLogSession.recordTieba("))
        assertTrue(source.contains("ConfigManager.shouldOutputDetailedLogs()"))
        assertFalse(source.contains("MediaStore"))
        assertFalse(source.contains("FileOutputStream"))
        assertFalse(source.contains("OutputStream"))
        assertFalse(source.contains("contentResolver"))
    }

    private fun readSource(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(5) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Source file not found: $relativePath")
    }
}
