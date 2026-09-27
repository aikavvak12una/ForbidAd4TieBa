package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeaderScanRulesTest {
    @Test fun businessMethodsWithOnPrefixAreRetainedAndActualSdkOverridesAreExcluded() {
        val match = HomeTopBarRightSlotRule(Slot::class.java.name).match(Slot::class.java, javaClass.classLoader!!, null)
        assertEquals(setOf("onBusinessStateChanged", "renamedRefresh"), match?.methodName?.split(",")?.toSet())
    }

    private class Slot(context: Context) : ViewGroup(context) {
        fun getSearchIconView(): ImageView? = null
        fun getGameIconView(): View? = null
        fun getRedDotView(): View? = null
        fun getTopBarTip(): View? = null
        fun onBusinessStateChanged() = Unit
        fun renamedRefresh() = Unit
        override fun onDetachedFromWindow() = Unit
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
    }
}
