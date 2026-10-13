package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BottomTabArtworkContractTest {
    class Tab(context: Context) : FrameLayout(context) {
        fun getContentTv(): View? = null
    }
    class InvalidTab(context: Context) : FrameLayout(context) {
        fun getContentTv(): String = ""
    }

    @Test fun validatesNativeLabelAccessor() {
        assertEquals(Tab::class.java, BottomTabArtworkContract.validate(Tab::class.java).declaringClass)
    }

    @Test fun missingOrInvalidAccessorDisablesOnlyArtworkCapability() {
        assertThrows(NoSuchMethodException::class.java) {
            BottomTabArtworkContract.validate(FrameLayout::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            BottomTabArtworkContract.validate(InvalidTab::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            BottomTabArtworkContract.validate(View::class.java)
        }
    }
}
