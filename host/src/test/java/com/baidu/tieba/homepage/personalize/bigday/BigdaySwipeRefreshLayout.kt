package com.baidu.tieba.homepage.personalize.bigday

import android.view.ViewGroup

/** Type-only fixture; no Android behavior is executed. */
@Suppress("UNUSED_PARAMETER")
class BigdaySwipeRefreshLayout : ViewGroup(null) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    fun setRefreshing(refreshing: Boolean) {}
}
