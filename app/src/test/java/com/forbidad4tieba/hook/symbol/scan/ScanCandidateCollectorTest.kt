package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.scan.ScanCandidateCollector.ScanCandidateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanCandidateCollectorTest {
    @Test
    fun classifyTargetObfuscatedClassNames() {
        assertEquals(
            ScanCandidateKind.OBFUSCATED,
            ScanCandidateCollector.classifyClassName("com.baidu.tieba.a1"),
        )
    }

    @Test
    fun classifyExpandedTargetClassNames() {
        assertEquals(
            ScanCandidateKind.EXPANDED,
            ScanCandidateCollector.classifyClassName("com.baidu.tieba.pb.SomePresenter"),
        )
        assertEquals(
            ScanCandidateKind.EXPANDED,
            ScanCandidateCollector.classifyClassName("com.baidu.tbadk.editortools.pb.PbNewInputContainer"),
        )
    }

    @Test
    fun ignoreNonTargetAndResourceClassNames() {
        assertNull(ScanCandidateCollector.classifyClassName("com.example.tieba.FakePresenter"))
        assertNull(ScanCandidateCollector.classifyClassName("com.baidu.tieba.R"))
        assertNull(ScanCandidateCollector.classifyClassName("com.baidu.tieba.R\$id"))
    }
}
