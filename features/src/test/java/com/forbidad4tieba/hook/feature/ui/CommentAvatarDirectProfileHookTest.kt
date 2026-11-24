package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class CommentAvatarDirectProfileHookTest {
    @Test
    fun extractsPortraitFromSubReplyWebContainerDataWithoutNumericUserId() {
        val portrait = "tb.1.example.portrait?t=123"
        val pageData = hashMapOf(
            "page_from" to "pb", "element_from" to "pb_lzl",
            "friendPortrait" to portrait, "forumId" to 42,
        )
        assertEquals(portrait, CommentAvatarDirectProfileHook.extractWebBusinessCardPortrait(
            "https://tieba.baidu.com/mo/q/hybrid-main-frs/customBusinessCard/hybrid?nonavigationbar=1&cuid=test|suffix",
            pageData,
        ))
    }

    @Test
    fun leavesUnrelatedWebPagesAndInvalidPortraitDataUnchanged() {
        val url = "https://tieba.baidu.com/mo/q/hybrid-main-frs/customBusinessCard/hybrid"
        for (data in listOf(null, "{\"friendPortrait\":\"portrait\"}", emptyMap<String, Any>(),
            mapOf("friendPortrait" to "  "), mapOf("friendPortrait" to 123))) {
            assertNull(CommentAvatarDirectProfileHook.extractWebBusinessCardPortrait(url, data))
        }
        for (otherUrl in listOf(null, "not a URL", "$url/other", "$url.evil",
            url.replace("tieba.baidu.com", "example.com"),
            "https://example.com/?next=$url", "https://tieba.baidu.com/other")) {
            assertNull(CommentAvatarDirectProfileHook.extractWebBusinessCardPortrait(
                otherUrl, mapOf("friendPortrait" to "portrait"),
            ))
        }
    }

    @Test
    fun acceptsOnlyPositiveNumericUserIds() {
        assertEquals(6510551020L, CommentAvatarDirectProfileHook.parsePositiveUserIdForTest(" 6510551020 "))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest("0"))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest("12x"))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest(null))
    }
}
