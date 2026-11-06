package com.forbidad4tieba.hook.symbol.scan

import java.lang.reflect.Method
import com.forbidad4tieba.hook.symbol.contract.PbBottomBannerContract
import org.junit.Assert.*
import org.junit.Test

class PbHotTopicRefreshTest {
    private val methods = Fixture::class.java.declaredMethods.toList()
    private val expected = setOf("bind", "skin", "visibility")

    @Test fun selectsBindingSkinAndVisibilityButNotShapeOnlyHelpers() {
        assertEquals(expected, select { it.name in expected }.map { it.name }.toSet())
    }

    @Test fun missingOrAmbiguousSemanticRolesDisableOnlyThisGroup() {
        assertTrue(select { it.name in expected && it.name != "skin" }.isEmpty())
        assertTrue(select { it.name in expected || it.name == "helper" }.isEmpty())
        assertTrue(select { false }.isEmpty())
    }

    @Test fun invalidSignaturesCannotSupplyASemanticRole() {
        assertTrue(select { it.name in setOf("bind", "visibility", "wrong") }.isEmpty())
    }

    @Test fun restorationRequiresTheCompleteDistinctRefreshGroup() {
        val selected = select { it.name in expected }
        assertTrue(PbBottomBannerContract.isHotTopicRefreshGroupValid(selected))
        assertFalse(PbBottomBannerContract.isHotTopicRefreshGroupValid(selected.drop(1)))
        assertFalse(PbBottomBannerContract.isHotTopicRefreshGroupValid(listOf(selected[0], selected[0], selected[2])))
        assertFalse(PbBottomBannerContract.isHotTopicRefreshGroupValid(methods))
    }

    private fun select(evidence: (Method) -> Boolean) =
        PbBottomEnterBarSymbolScanner.selectHotTopicRefreshMethods(methods, null, evidence)

    @Suppress("UNUSED_PARAMETER")
    class Fixture {
        fun bind(): Boolean = true
        fun skin() {}
        fun visibility(value: Int) {}
        fun helper() {}
        fun wrong(value: String) {}
    }
}
