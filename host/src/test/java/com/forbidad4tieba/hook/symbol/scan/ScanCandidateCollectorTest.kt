package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCandidateCollectorTest {
    @Test fun classSpellingAndNestingDoNotAffectEligibility() {
        listOf(
            "com.baidu.tieba.a", "com.baidu.tieba.a12345", "com.baidu.tieba.RenamedController",
            "com.baidu.tieba.pb.SomePresenter", "com.baidu.tieba.pb.SomePresenter\$a",
            "com.baidu.tbadk.editortools.pb.PbNewInputContainer",
            "com.baidu.adp.widget.ListView.AnyListener",
            "com.baidu.searchbox.task.view.mainactivity.AnyController",
        ).forEach { assertTrue(it, ScanCandidateCollector.isCandidateClassName(it)) }
    }

    @Test fun limitsInventoryToOwnedPackagesAndExcludesGeneratedResources() {
        listOf(
            "com.example.tieba.FakePresenter", "com.baidu.tiebaextra.FakePresenter", "com.baidu.tieba",
            "com.baidu.tieba.R", "com.baidu.tieba.R\$id", "com.baidu.tieba.nested.R\$layout", "com.baidu.tbadk.BuildConfig",
        ).forEach { assertFalse(it, ScanCandidateCollector.isCandidateClassName(it)) }
    }
}
