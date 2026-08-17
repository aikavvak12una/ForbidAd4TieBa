package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class CommentAvatarDirectProfileHookTest {
    @Test
    fun extractsFrsBusinessCardPortraitForInternalNavigation() {
        val portrait = "tb.1.de370a04.noEmXhaCv4nr9aN61VwANw?t=1420173709"
        val params = """{"page":"h5/openWebView","pageParams":{"openType":1,"url":"https://tieba.baidu.com/mo/q/hybrid-main-frs/customBusinessCard","initData":{"element_from":"feed_head","friendPortrait":"$portrait","forumId":15301,"page_from":"frs_default"}}}"""
        val route = "tiebaapp://router/portal?params=" +
            URLEncoder.encode(params, Charsets.UTF_8.name()).replace("+", "%20")

        assertEquals(
            portrait,
            CommentAvatarDirectProfileHook.extractBusinessCardPortraitForTest(route),
        )
    }

    @Test
    fun leavesNonBusinessCardRouteUnchanged() {
        val route = "tiebaapp://router/portal?params=%7B%22page%22%3A%22h5%2FopenWebView%22%7D"
        assertNull(CommentAvatarDirectProfileHook.extractBusinessCardPortraitForTest(route))
    }

    @Test
    fun acceptsOnlyPositiveNumericUserIds() {
        assertEquals(6510551020L, CommentAvatarDirectProfileHook.parsePositiveUserIdForTest(" 6510551020 "))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest("0"))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest("12x"))
        assertNull(CommentAvatarDirectProfileHook.parsePositiveUserIdForTest(null))
    }
}
