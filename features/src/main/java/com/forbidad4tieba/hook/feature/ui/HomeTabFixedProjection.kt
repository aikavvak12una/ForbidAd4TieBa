package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.TabPreferences

/** Both host consumers receive this projection; the original list remains intact on failure. */
internal object HomeTabFixedProjection {
    enum class Failure { CONFLICTING_IDENTITY, NO_AVAILABLE_SELECTION }
    data class Result<T>(val tabs: List<T>, val failure: Failure? = null)

    fun <T> project(
        hostTabs: List<T>,
        selection: TabPreferences.HomeTopTabSelection,
        keyOf: (T) -> String?,
        syncFollowed: (MutableList<T>) -> Unit,
    ): Result<T> {
        val identities = HashSet<String>(hostTabs.size)
        val selected = ArrayList<T>(hostTabs.size)
        for (item in hostTabs) {
            val key = TabPreferences.canonicalHomeTopTabKey(keyOf(item)) ?: continue
            // The existing module followed path owns its own deduplication.
            if (!identities.add(key) && key != TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED) {
                return Result(hostTabs, Failure.CONFLICTING_IDENTITY)
            }
            if (selection.isTabKeyEnabled(key)) selected.add(item)
        }
        syncFollowed(selected)
        return if (selected.isEmpty()) Result(hostTabs, Failure.NO_AVAILABLE_SELECTION) else Result(selected)
    }
}
