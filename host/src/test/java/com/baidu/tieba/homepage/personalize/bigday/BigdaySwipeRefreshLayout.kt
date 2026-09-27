package com.baidu.tieba.homepage.personalize.bigday

/** Type-only fixture for cached member validation; no host or Android behavior is executed. */
@Suppress("UNUSED_PARAMETER")
class BigdaySwipeRefreshLayout {
    fun renamed(refreshing: Boolean, notify: Boolean) {}
    fun wrongParameter(refreshing: Boolean) {}
    fun wrongReturn(refreshing: Boolean, notify: Boolean): Boolean = false
    companion object { @JvmStatic fun staticGesture(refreshing: Boolean, notify: Boolean) {} }
}
