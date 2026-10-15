package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.BlockedPostUser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostUserLookupResponseTest {
    @Test fun confirmsBothIdentifiersAndPreservesLongUidAndNickname() {
        assertEquals(PostUserLookupResult.Found(BlockedPostUser("9007199254740993", "123", "昵称😀")),
            PostUserLookupResponse.parse("123", response(9007199254740993L)))
    }

    @Test fun mismatchedOrMissingPublicIdCannotBlockAnUnrelatedUser() {
        for (id in listOf("456", "", "0", "123.0")) {
            assertSame(PostUserLookupResult.InvalidResponse, PostUserLookupResponse.parse("123", response("789", id)))
        }
        assertSame(PostUserLookupResult.InvalidResponse,
            PostUserLookupResponse.parse("123", "{\"error_code\":0,\"user\":{\"id\":789,\"name_show\":\"name\"}}"))
    }

    @Test fun missingMalformedAndNonIntegerUidsNeverProduceASavedUser() {
        for (uid in listOf(JSONObject.NULL, "", "0", "-1", "12.3", "1e3", "9223372036854775808")) {
            assertSame("uid=$uid", PostUserLookupResult.InvalidResponse,
                PostUserLookupResponse.parse("123", response(uid)))
        }
        assertSame(PostUserLookupResult.InvalidResponse, PostUserLookupResponse.parse("123",
            """{"error_code":0,"user":{"id":123.0,"tieba_uid":"123","name_show":"name"}}"""))
    }

    @Test fun transportApiAndInvalidResponsesAreExplicitFailures() {
        assertSame(PostUserLookupResult.Failed, PostUserLookupResponse.parse("123", null))
        assertSame(PostUserLookupResult.Failed, PostUserLookupResponse.parse("123", "{\"error_code\":110001}"))
        for (raw in listOf("bad json", "{}", "{\"error_code\":0}", "{\"error_code\":0,\"user\":[]}")) {
            assertSame(raw, PostUserLookupResult.InvalidResponse, PostUserLookupResponse.parse("123", raw))
        }
    }

    @Test fun nameIsRequiredAndUserNameCanSupplyAMissingDisplayName() {
        val root = JSONObject(response("789"))
        root.getJSONObject("user").put("name_show", "").put("name", "用户名")
        assertEquals("用户名", (PostUserLookupResponse.parse("123", root.toString()) as PostUserLookupResult.Found).user.nickname)
        root.getJSONObject("user").put("name", "")
        assertSame(PostUserLookupResult.InvalidResponse, PostUserLookupResponse.parse("123", root.toString()))
    }

    private fun response(uid: Any, profileId: String = "123") = JSONObject().put("error_code", "0")
        .put("user", JSONObject().put("id", uid).put("tieba_uid", profileId).put("name_show", "昵称😀")).toString()
}
