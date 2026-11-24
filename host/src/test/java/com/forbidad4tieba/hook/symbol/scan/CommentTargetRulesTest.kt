package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.PbBottomBannerContract
import org.junit.Assert.*
import org.junit.Test
import tbclient.PbPage.DataRes

@Suppress("UNUSED_PARAMETER")
class CommentTargetRulesTest {
    @Test fun sortSetterUsesInitializationAndRequestEvidence() {
        val owner = Sort::class.java
        assertEquals("renamed", LzlSortSymbolScanner.selectSetter(owner, null) { it.name == "renamed" }?.name)
        assertNull(LzlSortSymbolScanner.selectSetter(owner, null) { false })
        assertNull(LzlSortSymbolScanner.selectSetter(owner, null) { true })
    }

    @Test fun parserProtocolRolesRemainDistinctAfterRenaming() {
        val methods = Parser::class.java.declaredMethods.toList()
        val target = methods.single { it.name == "renamed" }
        assertEquals(target, CommentFilterSymbolScanner.select(CommentFilterSymbolScanner.Path.PAGE_NATIVE, listOf(target), null))
        assertNull(CommentFilterSymbolScanner.select(CommentFilterSymbolScanner.Path.PAGE_NATIVE, methods, null))
        assertNull(CommentFilterSymbolScanner.select(CommentFilterSymbolScanner.Path.PAGE_JSON, listOf(target), null))
    }

    @Test fun thresholdRestoresOnlyTheExactIntegerGetter() {
        val owner = Threshold::class.java.name
        val loader = javaClass.classLoader!!
        assertEquals(5, PbCommentPreloadSymbolScanner.restore(loader, "$owner#threshold")?.invoke(Threshold()))
        assertNull(PbCommentPreloadSymbolScanner.restore(loader, "$owner#wrong"))
        assertNull(PbCommentPreloadSymbolScanner.restore(loader, "$owner#missing"))
    }

    @Test fun hotTopicRefreshRequiresDistinctBindSkinAndVisibilityRoles() {
        val expected = setOf("bind", "skin", "visibility")
        fun select(names: Set<String>) = PbBottomEnterBarSymbolScanner.selectHotTopicRefreshMethods(
            HotTopic::class.java.declaredMethods.toList(), null) { it.name in names }
        val selected = select(expected)
        assertEquals(expected, selected.map { it.name }.toSet())
        assertTrue(select(expected - "skin").isEmpty())
        assertTrue(select(expected + "helper").isEmpty())
        assertTrue(PbBottomBannerContract.isHotTopicRefreshGroupValid(selected))
        assertFalse(PbBottomBannerContract.isHotTopicRefreshGroupValid(listOf(selected[0], selected[0], selected[2])))
    }

    class Sort { fun renamed(value: String?) {}; fun a(value: String?) {} }
    class Parser { fun renamed(response: DataRes) {}; fun a(response: DataRes) {} }
    class Threshold { fun threshold() = 5; fun wrong() = true }
    class HotTopic {
        fun bind() = true
        fun skin() {}
        fun visibility(value: Int) {}
        fun helper() {}
    }
}
