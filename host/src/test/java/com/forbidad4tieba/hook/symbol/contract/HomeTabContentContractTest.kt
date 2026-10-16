package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import org.junit.Assert.*
import org.junit.Test

class HomeTabContentContractTest {
    @Test fun webAndLiveCapabilitiesRemainIndependent() {
        for (web in listOf(null, FrameLayout::class.java, RelativeLayout::class.java)) {
            for (live in listOf(null, FrameLayout::class.java, View::class.java)) {
                val targets = HomeTabContentContract.validate(ViewGroup::class.java, ViewGroup::class.java, web, live)
                assertEquals(web == FrameLayout::class.java, targets.hasWebContent)
                assertEquals(live == FrameLayout::class.java, targets.hasLiveContent)
            }
        }
    }

    @Test fun invalidHomeOrPagerCannotExposeContentTargets() {
        assertThrows(IllegalStateException::class.java) {
            HomeTabContentContract.validate(View::class.java, ViewGroup::class.java, FrameLayout::class.java, FrameLayout::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            HomeTabContentContract.validate(ViewGroup::class.java, String::class.java, FrameLayout::class.java, FrameLayout::class.java)
        }
    }
}
