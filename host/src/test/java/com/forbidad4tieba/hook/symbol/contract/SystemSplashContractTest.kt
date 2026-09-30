package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemSplashContractTest {
    class View
    class ValidBuilder {
        @JvmField val mContext: Context? = null
        @JvmField var mOverlayDrawable: Drawable? = null
        var color: Int = 0
        fun build() = View()
        fun setBackgroundColor(color: Int): ValidBuilder { this.color = color; return this }
    }
    class WrongContextBuilder {
        @JvmField val mContext: String? = null
        @JvmField val mOverlayDrawable: Drawable? = null
        fun build() = View()
        fun setBackgroundColor(color: Int): WrongContextBuilder = this
    }
    class MissingOverlayBuilder {
        @JvmField val mContext: Context? = null
        fun build() = View()
        fun setBackgroundColor(color: Int): MissingOverlayBuilder = this
    }
    class FinalOverlayBuilder {
        @JvmField val mContext: Context? = null
        @JvmField val mOverlayDrawable: Drawable? = null
        fun build() = View()
        fun setBackgroundColor(color: Int): FinalOverlayBuilder = this
    }
    class WrongBuildResult {
        fun build() = Any()
    }
    class StaticBuild {
        companion object { @JvmStatic fun build() = View() }
    }

    @Test fun restoredMembersOperateOnTheSameBuilder() {
        val targets = SystemSplashContract.validate(ValidBuilder::class.java, View::class.java)
        val builder = ValidBuilder()
        assertFalse(targets.hasOverlay(builder))
        targets.setBackground(builder, 0xff121212.toInt(), null)
        assertEquals(0xff121212.toInt(), builder.color)
        assertEquals(View::class.java, targets.build.invoke(builder).javaClass)
    }

    @Test fun missingAndStructurallyChangedFrameworkTargetsFailClosed() {
        for (builder in listOf(WrongContextBuilder::class.java, WrongBuildResult::class.java,
            StaticBuild::class.java, FinalOverlayBuilder::class.java)) {
            assertThrows(IllegalStateException::class.java) {
                SystemSplashContract.validate(builder, View::class.java)
            }
        }
        assertThrows(NoSuchFieldException::class.java) {
            SystemSplashContract.validate(MissingOverlayBuilder::class.java, View::class.java)
        }
        assertThrows(NoSuchMethodException::class.java) {
            SystemSplashContract.validate(Any::class.java, View::class.java)
        }
    }
}
