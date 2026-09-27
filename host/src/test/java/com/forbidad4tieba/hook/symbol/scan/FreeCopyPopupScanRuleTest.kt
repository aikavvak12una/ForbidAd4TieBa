package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import com.baidu.tbadk.core.dialog.RoundLinearLayout
import com.baidu.tbadk.core.elementsMaven.view.EMTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeCopyPopupScanRuleTest {
    private val loader = javaClass.classLoader!!

    @Test fun contentViewUsesBehaviorEvenWhenAnUnrelatedGetterKeepsTheOldName() {
        val target = Popup::class.java.getDeclaredMethod("buildContent")
        val match = FreeCopyPopupRule { method, _ -> method == target }.match(Popup::class.java, loader, null)
        assertEquals("buildContent", match?.methodName)
        assertEquals("text", match?.fieldName)
    }

    @Test fun ambiguousContentBuildersAreRejected() {
        assertNull(FreeCopyPopupRule { _, _ -> true }.match(Popup::class.java, loader, null))
    }

    @Test fun aUniqueGetterWithoutContentEvidenceIsNotEnough() {
        assertNull(FreeCopyPopupRule { _, _ -> false }.match(Popup::class.java, loader, null))
    }

    @Test fun anOldFieldNameCannotResolveAmbiguousLayouts() {
        assertNull(FreeCopyPopupRule { _, _ -> true }.match(AmbiguousLayout::class.java, loader, null))
    }

    private class Popup {
        @JvmField var round: RoundLinearLayout? = null
        @JvmField var text: EMTextView? = null
        @JvmField var context: Context? = null
        @JvmField var items: List<Any> = emptyList()
        fun c(): View? = null
        fun buildContent(): View? = null
    }

    private class AmbiguousLayout {
        @JvmField var b: RoundLinearLayout? = null
        @JvmField var otherRound: RoundLinearLayout? = null
        @JvmField var text: EMTextView? = null
        @JvmField var context: Context? = null
        @JvmField var items: List<Any> = emptyList()
        fun buildContent(): View? = null
    }
}
