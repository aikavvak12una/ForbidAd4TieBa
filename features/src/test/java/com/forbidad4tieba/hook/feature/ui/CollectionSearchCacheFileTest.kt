package com.forbidad4tieba.hook.feature.ui

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CollectionSearchCacheFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun replacementFailurePreservesOldCompleteCacheAndAllowsRetry() {
        val file = temporary.newFile("account.json").apply { writeText("old complete") }
        assertThrows(IOException::class.java) {
            CollectionSearchCacheFile.write(file, { it.write("new complete".toByteArray()) }) { _, destination ->
                assertEquals("old complete", destination.readText())
                throw IOException("rename failure")
            }
        }
        assertEquals("old complete", file.readText())
        CollectionSearchCacheFile.write(file, "retry complete")
        assertEquals("retry complete", file.readText())
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test fun partialWriteFailureNeverTruncatesThePublishedFile() {
        val file = temporary.newFile("account.json").apply { writeText("old complete") }
        assertThrows(IOException::class.java) {
            CollectionSearchCacheFile.write(file, {
                it.write("partial".toByteArray())
                throw IOException("disk full")
            }) { _, _ -> fail("Failed writes must not be published") }
        }
        assertEquals("old complete", file.readText())
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
    }

    @Test fun interruptedTemporaryFileIsReplacedWithoutMixingAccounts() {
        val first = temporary.newFile("first.json").apply { writeText("first old") }
        val second = temporary.newFile("second.json").apply { writeText("second old") }
        File(first.parentFile, first.name + ".tmp").writeText("partial interrupted data")
        CollectionSearchCacheFile.write(first, "first new")
        assertEquals("first new", first.readText())
        assertEquals("second old", second.readText())
        val fresh = File(temporary.root, "nested/new.json")
        CollectionSearchCacheFile.write(fresh, "new account")
        assertEquals("new account", fresh.readText())
    }
}
