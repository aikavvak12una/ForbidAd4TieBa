package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabCatalogSnapshotTest {
    @Test
    fun settingsRetainHiddenHostTabsAndExcludeInjectedTabs() {
        val snapshot = HomeTabCatalogSnapshot()
        val hostTabs = mutableListOf(item("material"), item("recommend"), item("live"))
        snapshot.capture(hostTabs)
        hostTabs.remove(item("live"))
        hostTabs.add(item("followed"))

        assertEquals(listOf("material", "recommend", "live"), snapshot.current().map { it.code })
        assertEquals(listOf("material", "recommend", "followed"), hostTabs.map { it.code })
    }

    @Test
    fun settingsReadLatestHostUpdateAndKeepItsOrder() {
        val snapshot = HomeTabCatalogSnapshot()
        snapshot.capture(listOf(item("material"), item("recommend"), item("live")))
        snapshot.capture(listOf(item("new-tab"), item("recommend")))

        assertEquals(listOf("new-tab", "recommend"), snapshot.current().map { it.code })
    }

    @Test
    fun emptyRebuildPreservesLastCatalogButNewInstallationClearsIt() {
        val snapshot = HomeTabCatalogSnapshot()
        snapshot.capture(listOf(item("recommend")))
        snapshot.capture(emptyList())
        assertEquals(listOf("recommend"), snapshot.current().map { it.code })

        snapshot.clear()
        assertTrue(snapshot.current().isEmpty())
    }

    private fun item(code: String) = HomeTabCatalogSnapshot.Item(null, code, null, null)
}
