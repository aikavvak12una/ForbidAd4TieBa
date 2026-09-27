package com.forbidad4tieba.hook.feature.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeHotPathSourceTest {
    @Test
    fun collectionDiskRestoreStaysOffTheCallingThreadAndRejectsStaleResults() {
        val source = readSource(
            "features/src/main/java/com/forbidad4tieba/hook/feature/ui/CollectionSearchHook.kt",
        )
        val asyncRestore = section(
            source,
            "    private fun restoreFullDataFromDiskAsync(",
            "    private fun readFullDataFromDisk(",
        )
        val diskRead = section(
            source,
            "    private fun readFullDataFromDisk(",
            "    private fun completeDiskRestore(",
        )
        val memoryRestore = section(
            source,
            "    private fun restoreFullDataFromCache(",
            "    private fun restoreFullDataFromDiskAsync(",
        )
        val completion = section(
            source,
            "    private fun completeDiskRestore(",
            "    private fun startFetchAfterDiskRestoreMiss(",
        )

        assertEquals(1, occurrences(source, "CollectionSearchCacheStore.read("))
        assertTrue(asyncRestore.indexOf("sDiskIoExecutor.execute {") >= 0)
        assertTrue(
            asyncRestore.indexOf("readFullDataFromDisk(") >
                asyncRestore.indexOf("sDiskIoExecutor.execute {"),
        )
        assertTrue(
            asyncRestore.indexOf("state.beginDiskRestore(fetchOnMiss, userVisible) ?: return false") >
                asyncRestore.indexOf("resolveModelParseMethod(model.javaClass) ?: return false"),
        )
        assertTrue(diskRead.contains("CollectionSearchCacheStore.read("))
        assertTrue(memoryRestore.contains("return trustedFull"))
        assertFalse(source.contains("private fun hasFullCache("))
        assertTrue(completion.contains("sSessions.current(fragment, request) ?: return"))
        assertTrue(
            completion.contains("current.finishDiskRestore(request, resolveCurrentAccount(fragment.javaClass.classLoader)) ?: return"),
        )
        assertTrue(completion.indexOf("current.finishDiskRestore(") < completion.indexOf("replaceModelDataset("))
        assertTrue(memoryRestore.contains("if (!isSessionCurrent(fragment, state)) return false"))
        assertTrue(
            memoryRestore.indexOf("isSessionCurrent(fragment, state)") < memoryRestore.indexOf("getFullDataCache("),
        )
    }

    @Test
    fun systemBarCallbacksUseCachedReflectionAndFrameworkResourceIds() {
        val source = readSource(
            "features/src/main/java/com/forbidad4tieba/hook/feature/ui/SystemBarCompatHook.kt",
        )
        val navigationHookInstall = section(
            source,
            "    private fun installNavigationBarColorHook(",
            "    private fun rememberRequestedNavigationBarColor(",
        )
        val invokeNoArg = section(
            source,
            "    private fun invokeNoArgView(",
            "    private fun resolveNoArgViewMethod(",
        )
        val resolveNoArg = section(
            source,
            "    private fun resolveNoArgViewMethod(",
            "    private fun isGestureNavigation(",
        )

        assertTrue(
            navigationHookInstall.indexOf("navigationBarColorHookAttemptedClasses.add") <
                navigationHookInstall.indexOf("findMethodInHierarchy("),
        )
        assertFalse(navigationHookInstall.contains("navigationBarColorHookAttemptedClasses.remove"))
        assertEquals(2, occurrences(source, ".getIdentifier("))
        assertTrue(source.contains("resolveFrameworkResourceIds(app)"))
        assertFalse(invokeNoArg.contains("getDeclaredMethod("))
        assertTrue(resolveNoArg.contains("noArgViewMethodCache.computeIfAbsent"))
        assertTrue(source.contains("supportedActivityClassCache.computeIfAbsent"))
        assertTrue(source.contains("mainTabActivityClassCache.computeIfAbsent"))
    }

    private fun section(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        val endIndex = source.indexOf(end, startIndex + start.length)
        assertTrue("Missing section start: $start", startIndex >= 0)
        assertTrue("Missing section end: $end", endIndex > startIndex)
        return source.substring(startIndex, endIndex)
    }

    private fun occurrences(source: String, needle: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = source.indexOf(needle, index)
            if (index < 0) return count
            count += 1
            index += needle.length
        }
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
