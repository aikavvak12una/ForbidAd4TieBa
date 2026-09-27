package com.forbidad4tieba.hook.feature.signin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoSignInResponsesTest {
    private val one = SignInForum("1", "甲吧")
    private val two = SignInForum("2", "乙吧")

    @Test fun preservesReturnedErrorCodeAndMessage() {
        val result = AutoSignInResponses.single(JSONObject("""{"error_code":123456,"error_msg":"贴吧不存在"}"""))
        assertFalse(result.success)
        assertEquals(SignInFailure(SignInFailureKind.API, "123456", "贴吧不存在"), result.failure)
    }

    @Test fun nestedFailureOverridesSuccessfulEnvelope() {
        val result = AutoSignInResponses.single(JSONObject("""{"errno":0,"data":{"error":{"errno":"fixture_error","usermsg":"失败原因"}}}"""))
        assertFalse(result.success)
        assertEquals("fixture_error", result.failure!!.code)
        assertEquals("失败原因", result.failure.message)
    }

    @Test fun explicitAlreadySignedFlagIsSuccessful() {
        assertTrue(AutoSignInResponses.single(JSONObject("""{"error_code":"already","user_info":{"is_sign_in":1}}""")).success)
        assertTrue(AutoSignInResponses.single(JSONObject("""{"data":{"user_info":{"is_sign_in":"1"}}}""")).success)
    }

    @Test fun missingResultDoesNotBecomeSuccessOrAnInventedErrorCode() {
        val result = AutoSignInResponses.single(JSONObject("""{"data":{}}"""))
        assertFalse(result.success)
        assertEquals(SignInFailureKind.UNCONFIRMED, result.failure!!.kind)
        assertNull(result.failure.code)
    }

    @Test fun batchResultsAreMatchedByForumIdentity() {
        val result = AutoSignInResponses.batch(JSONObject("""{"errno":0,"info":[{"forum_id":"2","signed":0,"error_code":"closed","error_msg":"已关闭"},{"forum_id":"1","signed":1}]}"""), listOf(one, two))
        assertTrue(result.attempts.getValue(one.key).success)
        assertEquals("closed", result.attempts.getValue(two.key).failure!!.code)
        assertEquals("已关闭", result.attempts.getValue(two.key).failure!!.message)
    }

    @Test fun successfulBatchEnvelopeDoesNotMarkMissingForumAsSigned() {
        val result = AutoSignInResponses.batch(JSONObject("""{"errno":0,"info":[{"forum_id":"1","signed":1}]}"""), listOf(one, two))
        assertTrue(result.attempts.getValue(one.key).success)
        assertFalse(result.attempts.getValue(two.key).success)
        assertNull(result.attempts.getValue(two.key).failure!!.code)
    }

    @Test fun duplicateBatchEntriesAreNotArbitrarilySelected() {
        val result = AutoSignInResponses.batch(JSONObject("""{"errno":0,"info":[{"forum_id":"1","signed":1},{"forum_id":"1","signed":0}]}"""), listOf(one))
        assertFalse(result.attempts.getValue(one.key).success)
    }

    @Test fun batchFailureCodeAppliesToUnreturnedForum() {
        val result = AutoSignInResponses.batch(JSONObject("""{"error":{"errno":"batch_denied","errmsg":"暂不可用"}}"""), listOf(one))
        assertEquals("batch_denied", result.attempts.getValue(one.key).failure!!.code)
    }

    @Test fun batchNoticeWithoutApiErrorTriggersFallbackAndKeepsExplicitSuccesses() {
        val result = AutoSignInResponses.batch(JSONObject("""{"errno":0,"data":{"show_dialog":1,
            "sign_notice":"零点到一点为签到高峰期，一键签到失败机率较大，请错开高峰期再来签到！",
            "info":[{"forum_id":"1","signed":1}]}}"""), listOf(one, two))
        assertEquals(SignInFailureKind.SERVER_NOTICE, result.failure?.kind)
        assertTrue(result.attempts.getValue(one.key).success)
        assertEquals(result.failure, result.attempts.getValue(two.key).failure)
    }

    @Test fun batchNoticeDoesNotHideApiFailureOrMakeAnInvalidListUsable() {
        val json = JSONObject("""{"errno":"fixture_login_required","show_dialog":1,"sign_notice":"批签提示","forum_info":[]}""")
        assertEquals("fixture_login_required", AutoSignInResponses.snapshot(json, 0).failure?.code)
        assertEquals("fixture_login_required", AutoSignInResponses.batch(json, listOf(one)).failure?.code)
        json.put("errno", 0).remove("forum_info")
        assertEquals(SignInFailureKind.INVALID_RESPONSE, AutoSignInResponses.snapshot(json, 0).failure?.kind)
    }

    @Test fun snapshotRejectsMalformedListInsteadOfClaimingEverythingIsSigned() {
        assertNull(AutoSignInResponses.snapshot(JSONObject("""{"errno":0}"""), 0).snapshot)
        assertNull(AutoSignInResponses.snapshot(JSONObject("""{"errno":0,"forum_info":[{}]}"""), 0).snapshot)
        assertNull(AutoSignInResponses.snapshot(JSONObject("""{"errno":99,"forum_info":[]}"""), 0).snapshot)
    }

    @Test fun snapshotKeepsOfficialBatchEligibilityAndServerNotice() {
        val result = AutoSignInResponses.snapshot(JSONObject("""{"errno":0,"forum_info":[{"forum_id":"1","forum_name":"甲吧","user_level":6,"is_sign_in":"0"}],"valid":"1","can_use":"0","level":7,"sign_max_num_new":20,"show_dialog":"1","sign_notice":"请完成验证"}"""), 0).snapshot!!
        assertTrue(result.batchAllowed)
        assertFalse(result.allLevels)
        assertEquals(7, result.batchMinLevel)
        assertEquals(20, result.batchSize)
        assertEquals(SignInFailureKind.SERVER_NOTICE, result.notice!!.kind)
        assertEquals("请完成验证", result.notice.message)
    }

    @Test fun errorDetailsAreBoundedAndDoNotKeepCredentialValues() {
        val message = AutoSignInResponses.safeMessage("BDUSS=test-secret; STOKEN=other-secret; tbs=value\n" + "a".repeat(500))
        assertFalse(message.contains("test-secret"))
        assertFalse(message.contains("other-secret"))
        assertFalse(message.contains("tbs=value"))
        assertFalse(message.contains('\n'))
        assertTrue(message.length <= 240)
        assertFalse(AutoSignInResponses.safeMessage("""{"BDUSS":"quoted-secret"}""").contains("quoted-secret"))
    }

    @Test fun conflictingSnapshotEntriesAreRejected() {
        val result = AutoSignInResponses.snapshot(JSONObject("""{"errno":0,"forum_info":[{"forum_id":"1","forum_name":"甲吧","is_sign_in":1},{"forum_id":"1","forum_name":"甲吧","is_sign_in":0}]}"""), 0)
        assertNull(result.snapshot)
        assertEquals(SignInFailureKind.INVALID_RESPONSE, result.failure!!.kind)
    }
}
