package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabCatalogSnapshotTest {
    @Test
    fun settingsRetainHiddenHostTabsAndExcludeInjectedTabs() {
        val snapshot = HomeTabCatalogSnapshot()
        val hostTabs = mutableListOf<Any?>("material", "recommend", "live")
        snapshot.capture(hostTabs)
        hostTabs.remove("live")
        hostTabs.add("followed")

        assertEquals(listOf("material", "recommend", "live"), snapshot.current())
        assertEquals(listOf("material", "recommend", "followed"), hostTabs)
    }

    @Test
    fun settingsReadLatestHostUpdateAndKeepItsOrder() {
        val snapshot = HomeTabCatalogSnapshot()
        snapshot.capture(listOf("material", "recommend", "live"))
        snapshot.capture(listOf("new-tab", "recommend"))

        assertEquals(listOf("new-tab", "recommend"), snapshot.current())
    }

    @Test
    fun emptyRebuildPreservesLastCatalogButNewInstallationClearsIt() {
        val snapshot = HomeTabCatalogSnapshot()
        snapshot.capture(listOf("recommend"))
        snapshot.capture(emptyList())
        assertEquals(listOf("recommend"), snapshot.current())

        snapshot.clear()
        assertTrue(snapshot.current().isEmpty())
    }
}
