package com.forbidad4tieba.hook.feature.diagnostic

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TiebaHostLogHookSourceTest {
    @Test fun hostLogCallbacksKeepDiskAndDiscoveryOffTheHotPath() {
        val root = File(checkNotNull(System.getProperty("project.root")))
        val directory = File(root, "features/src/main/java/com/forbidad4tieba/hook/feature/diagnostic")
        val callback = File(directory, "TiebaHostLogHook.kt").readText()
        assertTrue(callback.contains("ConfigManager.snapshot().isDetailedLoggingEnabled"))
        assertTrue(callback.contains("DetailedLogSession.recordTieba("))
        for (name in listOf("TiebaHostLogHook.kt", "CloudDialogLog.kt")) {
            val source = File(directory, name).readText()
            for (forbidden in listOf("FileOutputStream", "contentResolver", "Class.forName", "getDeclared", "openConnection"))
                assertFalse("$name: $forbidden", source.contains(forbidden))
        }
    }
}
