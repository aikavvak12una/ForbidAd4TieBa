package com.forbidad4tieba.hook.feature.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimpleUiPolicyTest {
    @Test fun defaultSortChangesOnlyTheHotDefaultAndDisabledInputIsUntouched() {
        for (value in listOf(null, "2", "1", "unknown")) {
            assertEquals(value, DefaultLzlEarliestHook.defaultSort(value, false))
            assertEquals(if (value == null || value == "2") "0" else value, DefaultLzlEarliestHook.defaultSort(value, true))
        }
    }

    @Test fun gestureSuppressionDoesNotCallHostAndNormalModePreservesFailure() {
        assertEquals(false, PbDisableGestureFontScaleHook.dispatchResult(true) { error("Should be blocked") })
        assertEquals(true, PbDisableGestureFontScaleHook.dispatchResult(false) { true })
        assertThrows(IllegalStateException::class.java) { PbDisableGestureFontScaleHook.dispatchResult(false) { error("Host") } }
    }

    @Test fun firstLikeSuppressionRequiresSuccessAndItsProtocolFlag() {
        val response = JSONObject("""{"error_code":"0","error":{"errno":"0"},"data":{"agree":{"is_first_agree":"1"}},"toast":{"type":"1","content":[{"text":"first"}]}}""")
        assertTrue(FirstLikePopupPolicy.shouldBlock(response.toString()))
        response.getJSONObject("data").getJSONObject("agree").put("is_first_agree", "0")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
        response.getJSONObject("data").getJSONObject("agree").put("is_first_agree", "1")
        response.put("error_code", "160002")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
        response.remove("error_code")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
        assertFalse(FirstLikePopupPolicy.shouldBlock("{"))
    }

    @Test fun systemBarUsesValidatedActivityAndTheCurrentSecureNavigationSetting() {
        class Post
        class Other
        assertTrue(SystemBarCompatHook.isFloatingPostReplyActivity(Post::class.java, Post::class.java, true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(Other::class.java, Post::class.java, true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(Post::class.java, null, true))
        assertFalse(SystemBarCompatHook.isFloatingPostReplyActivity(Post::class.java, Post::class.java, false))
        assertFalse(SystemBarCompatHook.isGestureNavigationMode(secureMode = 0, frameworkMode = 2))
        assertTrue(SystemBarCompatHook.isGestureNavigationMode(secureMode = null, frameworkMode = 2))
    }

    @Test fun splashReadsSystemModeOnlyForTiebaAndDoesNotCacheAnOldMode() {
        assertNull(SystemSplashPolicy.background("other.app") { error("Unrelated lookup") })
        assertEquals(0xff121212.toInt(), SystemSplashPolicy.background("com.baidu.tieba") { 0x21 })
        assertNull(SystemSplashPolicy.background("com.baidu.tieba") { 0x11 })
    }
}
