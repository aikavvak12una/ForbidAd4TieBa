package com.forbidad4tieba.hook.feature.ui

/** Keeps host list membership before filtering; decoding and persistence happen in settings. */
internal class HomeTabCatalogSnapshot {
    @Volatile private var tabs: List<Any?> = emptyList()

    fun capture(hostTabs: List<Any?>) {
        if (hostTabs.isNotEmpty()) tabs = hostTabs.toList()
    }

    fun current(): List<Any?> = tabs

    fun clear() {
        tabs = emptyList()
    }
}
