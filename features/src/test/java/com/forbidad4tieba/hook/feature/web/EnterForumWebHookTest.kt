package com.forbidad4tieba.hook.feature.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnterForumWebHookTest {
    @Test
    fun rewritesOnlyTheKnownForumEntryAndPreservesExistingParameters() {
        for (suffix in listOf("?loadingSignal=1", "/hybrid?loadingSignal=1&customfullscreen=1")) {
            val url = "https://tieba.baidu.com/mo/q/hybrid-main-bawu/forumConcern$suffix"
            assertEquals(url, EnterForumWebHook.targetUrlForLoad(url))
        }
        assertEquals(
            "https://tieba.baidu.com/mo/q/hybrid-main-bawu/forumConcern" +
                "?nonavigationbar=1&customfullscreen=1&loadingSignal=1",
            EnterForumWebHook.targetUrlForLoad(
                "https://tieba.baidu.com/mo/q/hybrid-main-forumtab/mainPage?loadingSignal=1",
            ),
        )
        for (url in listOf(
            null,
            "",
            " ",
            "https://tieba.baidu.com/mo/q/hybrid-main-bawu/report",
            "https://example.com/mo/q/unrelated/mainPage",
        )) {
            assertNull(url, EnterForumWebHook.targetUrlForLoad(url))
        }
    }
}
