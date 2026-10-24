package com.forbidad4tieba.hook.core

import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class DetailedLogExportNameTest {
    @Test fun cleanupRecognizesGeneratedExportsAndSystemCollisionSuffixesOnly() {
        val name = DetailedLogFileFormatter.fileName(0L, ZoneOffset.UTC)
        assertTrue(DetailedLogFileFormatter.isExportFileName(name))
        assertTrue(DetailedLogFileFormatter.isExportFileName(name.replace(".log", " (2).log")))
        for (other in listOf("notes.log", "FA4TB_notes.log", "../$name", "$name.bak", name.replace(".log", ".png")))
            assertFalse(DetailedLogFileFormatter.isExportFileName(other))
    }
}
