package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Method

class FeedScanRulesTest {
    private val loader = javaClass.classLoader!!

    @Test fun bindFollowsEvidenceWhenTheOldNameBelongsToADistractor() {
        val target = BindFixture::class.java.getDeclaredMethod("renamed", CardData::class.java)
        assertEquals(target.name, bindRule(target).match(BindFixture::class.java, loader, null)?.methodName)
    }

    @Test fun bindDoesNotBreakSemanticTiesByName() {
        assertNull(FeedCardBindRule { _, _ -> true }.match(BindFixture::class.java, loader, null))
    }

    @Test fun bindRejectsMissingEvidenceAndInvalidSignatures() {
        val owner = BindFixture::class.java
        assertNull(FeedCardBindRule { _, _ -> false }.match(owner, loader, null))
        listOf("wrongData", "wrongReturn", "staticBind").forEach { name ->
            val method = owner.declaredMethods.single { it.name == name }
            assertNull(name, bindRule(method).match(owner, loader, null))
        }
    }

    @Test fun loadMoreFollowsEvidenceAfterRenaming() {
        val target = LoadFixture::class.java.getDeclaredMethod("renamed", ArrayList::class.java)
        assertEquals(target.name, loadRule(target).match(LoadFixture::class.java, loader, null)?.methodName)
    }

    @Test fun loadMoreRejectsAmbiguousOrUnanchoredMethods() {
        assertNull(FeedTemplateLoadMoreRule { _, _ -> true }.match(LoadFixture::class.java, loader, null))
        assertNull(FeedTemplateLoadMoreRule { _, _ -> false }.match(LoadFixture::class.java, loader, null))
    }

    @Test fun loadMoreEvidenceCannotOverrideSignatureValidation() {
        val owner = LoadFixture::class.java
        listOf("wrongParameter", "wrongReturn", "staticLoad").forEach { name ->
            assertNull(name, loadRule(owner.declaredMethods.single { it.name == name }).match(owner, loader, null))
        }
    }

    private fun bindRule(target: Method) = FeedCardBindRule { method, _ -> method == target }
    private fun loadRule(target: Method) = FeedTemplateLoadMoreRule { method, _ -> method == target }

    private class CardData {
        @JvmField val items: List<Any> = emptyList()
        @JvmField val first: String = ""
        @JvmField val second: String = ""
        @JvmField val enabled: Boolean = false
        @JvmField val selected: Boolean = false
        @JvmField val attributes: Map<String, Any> = emptyMap()
    }

    @Suppress("UNUSED_PARAMETER")
    private class BindFixture {
        fun w(data: CardData) {}
        fun renamed(data: CardData) {}
        fun wrongData(data: String) {}
        fun wrongReturn(data: CardData): Boolean = false
        companion object { @JvmStatic fun staticBind(data: CardData) {} }
    }

    @Suppress("UNUSED_PARAMETER")
    private class LoadFixture {
        fun L(items: List<Any>) {}
        fun renamed(items: ArrayList<Any>) {}
        fun wrongParameter(count: Int) {}
        fun wrongReturn(items: List<Any>): Boolean = false
        companion object { @JvmStatic fun staticLoad(items: List<Any>) {} }
    }
}
