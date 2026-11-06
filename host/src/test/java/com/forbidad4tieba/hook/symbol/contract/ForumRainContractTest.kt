package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.scan.ForumRainMapperFixture
import com.forbidad4tieba.hook.symbol.scan.ForumRainModelFixture
import org.junit.Assert.*
import org.junit.Test
import tbclient.FrsPage.FrsRainInfo

class ForumRainContractTest {
    @Test fun validatesAndRestoresRainWithoutAnyOtherForumPath() {
        val loader = javaClass.classLoader!!
        val symbols = rain()
        assertTrue(ForumPageAdContract.isCacheValid(symbols, loader))
        val targets = requireNotNull(ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, symbols))
        assertEquals(ForumRainMapperFixture::class.java.getDeclaredMethod("convert", FrsRainInfo::class.java), targets.rainMapperMethod)
        assertNull(targets.responseParserMethod)
        assertNull(targets.bottomDataMapperMethod)
        assertNull(targets.bottomGameBarMapperMethod)
    }

    @Test fun invalidRainDisablesOnlyItsRestoredTarget() {
        val loader = javaClass.classLoader!!
        for (name in listOf("missing", "primitiveResult", "instanceConverter", "wrongConstructor")) {
            val invalid = rain(name)
            assertFalse(name, ForumPageAdContract.isCacheValid(invalid, loader))
            assertNull(name, ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, invalid))
            val mixed = rain(name, dialog = true)
            val targets = requireNotNull(ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, mixed))
            assertNull(name, targets.rainMapperMethod)
            assertEquals(name, ForumRainDialogFixture::class.java.getDeclaredMethod("show", String::class.java, Any::class.java),
                targets.businessPromotShowMethod)
        }
    }

    @Test fun cachedResultMustMatchTheConverterConstructorContract() {
        val loader = javaClass.classLoader!!
        assertFalse(ForumPageAdContract.isCacheValid(rain(result = Any::class.java.name), loader))
        assertNull(ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, rain(result = Any::class.java.name)))
    }

    private fun rain(name: String = "convert", result: String = ForumRainModelFixture::class.java.name, dialog: Boolean = false): HookSymbols =
        buildHookSymbols {
            this[ForumPageAdContract.forumRainMapperClass] = ForumRainMapperFixture::class.java.name
            this[ForumPageAdContract.forumRainMapperMethod] = name
            this[ForumPageAdContract.forumRainDataClass] = result
            if (dialog) {
                this[ForumPageAdContract.forumDialogControllerClass] = ForumRainDialogFixture::class.java.name
                this[ForumPageAdContract.forumBusinessPromotShowMethod] = "show"
            }
        }
}

class ForumRainDialogFixture {
    fun show(forum: String, promotion: Any): Boolean = true
}
