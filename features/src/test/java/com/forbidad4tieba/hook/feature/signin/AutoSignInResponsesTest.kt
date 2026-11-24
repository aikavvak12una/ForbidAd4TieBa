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

    @Test fun alreadySignedCodeDoesNotHideAnotherBusinessFailure() {
        for (raw in listOf(
            """{"error_code":160002,"data":{"error":{"errno":"fixture_login_required"}}}""",
            """{"error":{"errno":"160002"},"error_code":"fixture_login_required"}""",
            """{"errno":"fixture_login_required","data":{"error_code":160002}}""",
        )) {
            val result = AutoSignInResponses.single(JSONObject(raw))
            assertFalse(raw, result.success)
            assertEquals("fixture_login_required", result.failure?.code)
        }
    }

    @Test fun alreadySignedCodeDoesNotConfirmAListOrAnEntireBatch() {
        val json = JSONObject("""{"error_code":160002,"forum_info":[]}""")
        assertEquals("160002", AutoSignInResponses.snapshot(json, 0).failure?.code)
        val batch = AutoSignInResponses.batch(json, listOf(one, two))
        assertEquals("160002", batch.failure?.code)
        assertTrue(batch.attempts.values.all { !it.success })
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

    @Test fun errorDetailsAreBoundedAndDoNotKeepCredentialValues() {
        val message = AutoSignInResponses.safeMessage("BDUSS=test-secret; STOKEN=other-secret; tbs=value\n" + "a".repeat(500))
        assertFalse(message.contains("test-secret"))
        assertFalse(message.contains("other-secret"))
        assertFalse(message.contains("tbs=value"))
        assertFalse(message.contains('\n'))
        assertTrue(message.length <= 240)
        assertFalse(AutoSignInResponses.safeMessage("""{"BDUSS":"quoted-secret"}""").contains("quoted-secret"))
    }

}
