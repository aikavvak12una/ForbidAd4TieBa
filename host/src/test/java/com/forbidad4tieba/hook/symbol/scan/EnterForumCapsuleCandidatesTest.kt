package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodKind
import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnterForumCapsuleCandidatesTest {
    private fun init(name: String, view: String) = DexEnterForumCapsuleMethodMatch(
        name, DexEnterForumCapsuleMethodKind.INIT, 285, "navigation", view,
    )
    private fun refresh(name: String, view: String, title: String) = DexEnterForumCapsuleMethodMatch(
        name, DexEnterForumCapsuleMethodKind.REFRESH, 235, "background", view, title,
    )

    @Test fun linksTheSharedViewInsteadOfChoosingAFieldByName() {
        // 22.12.1.0: x writes both t and Z; w initializes a different bar; J reads t and y.
        val pairs = enterForumCapsulePairs(listOf(
            init("x", "t"), init("x", "Z"), init("w", "a0"), refresh("J", "t", "y"),
        ))
        assertEquals(1, pairs.size)
        assertEquals("x", pairs.single().init.ownerMethodName)
        assertEquals("t", pairs.single().init.viewFieldName)
        assertEquals("y", pairs.single().refresh.titleFieldName)
    }

    @Test fun renamedFieldsAndMethodsRetainTheSameRelationship() {
        val pair = enterForumCapsulePairs(listOf(init("make", "outer"), init("make", "inner"),
            refresh("update", "outer", "title"))).single()
        assertEquals("outer", pair.init.viewFieldName)
    }

    @Test fun unrelatedOrEquallySupportedPairsFailClosed() {
        assertEquals(emptyList<EnterForumCapsuleMethodPair>(),
            enterForumCapsulePairs(listOf(init("make", "first"), refresh("update", "second", "title"))))
        val pairs = enterForumCapsulePairs(listOf(init("first", "view"), init("second", "view"),
            refresh("update", "view", "title")))
        assertNull(uniqueBestCandidate(pairs, 480, 24) { it.score })
    }
}
