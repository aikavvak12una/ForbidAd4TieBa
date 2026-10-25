package com.forbidad4tieba.hook.feature.ui

/** Freeze only catalog fields before the adapter decorates URLs; encoding and IO stay in settings. */
internal class HomeTabCatalogSnapshot {
    data class Item(val type: Int?, val code: String?, val label: String?, val url: String?)

    @Volatile private var tabs: List<Item> = emptyList()

    fun capture(hostTabs: List<Item>) {
        if (hostTabs.isNotEmpty()) tabs = hostTabs.toList()
    }

    fun current(): List<Item> = tabs

    fun clear() {
        tabs = emptyList()
    }
}
