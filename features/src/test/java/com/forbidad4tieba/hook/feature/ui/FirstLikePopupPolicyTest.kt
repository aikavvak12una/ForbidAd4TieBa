package com.forbidad4tieba.hook.feature.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLikePopupPolicyTest {
    // Reduced from the two captured AgreeServerResponseLogHook responses.
    private fun response(): JSONObject = JSONObject(
        """{"error_code":"0","error":{"errno":"0","errmsg":"success"},
            "data":{"agree":{"score":"2","is_first_agree":"1"}},
            "toast":{"type":"1","locate":"1","content":[{"text":"每日首赞"},{"text":" 首赞经验 +2"}],
            "duration":"3000","delay_time":"300"}}""",
    )

    @Test
    fun blocksSuccessfulFirstLikeWithStringOrNumericProtocolValues() {
        val response = response()
        assertTrue(FirstLikePopupPolicy.shouldBlock(response.toString()))
        response.put("error_code", 0).getJSONObject("error").put("errno", 0)
        response.getJSONObject("data").getJSONObject("agree").put("is_first_agree", 1)
        assertTrue(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun preservesLaterLikeToastEvenWhenItsTextMatchesFirstLike() {
        val response = response()
        response.getJSONObject("data").getJSONObject("agree").put("is_first_agree", "0")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun preservesErrorToast() {
        assertFalse(FirstLikePopupPolicy.shouldBlock(response().put("error_code", "160002").toString()))
        val response = response()
        response.getJSONObject("error").put("errno", "160002")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun doesNotInferSuccessFromMissingStatus() {
        val response = response()
        response.remove("error_code")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun requiresTheLikeProtocolFlag() {
        val response = response()
        response.getJSONObject("data").remove("agree")
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun nestedToastAloneDoesNotSuppressTheTopLevelParser() {
        val response = response()
        response.getJSONObject("data").put("toast", response.remove("toast"))
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun preservesEmptyOrUnknownToastShape() {
        val response = response()
        response.put("toast", JSONArray())
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
        response.put("toast", JSONObject().put("content", JSONArray()))
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }

    @Test
    fun malformedAndOversizedResponsesKeepHostBehavior() {
        for (raw in listOf(null, "", "{", "[]", "null", " ".repeat(65_537))) {
            assertFalse(FirstLikePopupPolicy.shouldBlock(raw))
        }
        val response = response().put("padding", "x".repeat(65_537))
        assertFalse(FirstLikePopupPolicy.shouldBlock(response.toString()))
    }
}
