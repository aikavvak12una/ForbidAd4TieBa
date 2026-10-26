package com.forbidad4tieba.hook.feature.ui

import java.util.Collections

/** Publishes immutable lists; a read never copies every saved collection item. */
internal class CollectionSearchMemoryCache {
    class Snapshot(items: List<Any>, val fullReady: Boolean) {
        val items: List<Any> = Collections.unmodifiableList(ArrayList(items))
    }

    private val entries = LinkedHashMap<String, Snapshot>()

    @Synchronized fun get(account: String): Snapshot? = entries[account]

    @Synchronized fun put(account: String, items: List<Any>, fullReady: Boolean) {
        if (items.isEmpty() && !fullReady) return
        entries[account] = Snapshot(items, fullReady)
        // Keep the existing insertion-order eviction. Reads and replacements do not reorder accounts.
        while (entries.size > 3) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }
}
