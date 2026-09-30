package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.scan.CommentFilterSymbolScanner.Path
import org.junit.Assert.*
import org.junit.Test
import tbclient.PbPage.DataRes

class CommentFilterSymbolScannerTest {
    @Test fun renamedEvidenceSelectsExactlyOneAndMissingOrTiedEvidenceDisablesPath() {
        val methods = Parser::class.java.declaredMethods.toList()
        val target = methods.single { it.name == "renamed" }
        assertEquals(target, CommentFilterSymbolScanner.select(Path.PAGE_NATIVE, listOf(target), null))
        assertNull(CommentFilterSymbolScanner.select(Path.PAGE_NATIVE, emptyList(), null))
        assertNull(CommentFilterSymbolScanner.select(Path.PAGE_NATIVE, methods, null))
        assertNull(CommentFilterSymbolScanner.select(Path.PAGE_JSON, listOf(target), null))
        assertEquals(target, CommentFilterSymbolScanner.restore("${Parser::class.java.name}#renamed", Path.PAGE_NATIVE, javaClass.classLoader!!))
    }
    @Suppress("UNUSED_PARAMETER")
    class Parser {
        fun renamed(response: DataRes) {}
        fun distractor(response: DataRes) {}
    }
}
