package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.BlockedPostUser
import com.forbidad4tieba.hook.config.PostUserFilterSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PostUserListSessionTest {
    private val user = BlockedPostUser("789", "123", "name")

    @Test
    fun sharedAndTypedIdsUseExactNormalizationBeforeLookup() {
        val copied = "3.s:/@name@给你分享了贴吧号# 00123 #整段复制后打开贴吧即可找到Ta"
        assertEquals("00123", PostUserFilterSettings.filterProfileIdInput(copied))
        assertEquals("5500225939", PostUserFilterSettings.filterProfileIdInput("ID: 55a00225939 "))
        assertEquals("123456", PostUserFilterSettings.filterProfileIdInput("123, 456"))
        assertEquals("", PostUserFilterSettings.filterProfileIdInput("没有数字"))

        val session = PostUserListSession()
        assertSame(PostUserListSession.Start.Duplicate, session.start(copied, listOf(user)))
        for (input in listOf("", "0", "#0#", "#-123#", "#123.5#", "#123abc#",
            "#123# text #456#", "#123#456#", "##")) {
            assertSame(input, PostUserListSession.Start.InvalidId, session.start(input, emptyList()))
            assertNull(session.pending)
        }
        val request = start(session)
        assertEquals("123", request.profileId)
        assertSame(PostUserListSession.Start.Busy, session.start("456", emptyList()))
    }

    @Test
    fun overlappingLateAndClosedResultsCannotCompleteTheSession() {
        val session = PostUserListSession()
        val old = start(session)
        session.cancel()
        val current = start(session)
        assertNull(session.finish(old, user, emptyList()))
        assertSame(current, session.pending)
        assertEquals(listOf(user), session.finish(current, user, emptyList()))

        val closing = start(session)
        session.close()
        assertNull(session.finish(closing, user, emptyList()))
        assertSame(PostUserListSession.Start.Closed, session.start("123", emptyList()))
    }

    @Test
    fun removedUsersStayRemovedAndCompletionMergesTheLatestList() {
        val session = PostUserListSession()
        val removedRequest = start(session)
        session.removed(user.userId)
        assertNull(session.finish(removedRequest, user, emptyList()))

        val request = start(session)
        val retained = BlockedPostUser("456", "999", "retained")
        session.removed("111")
        assertEquals(listOf(retained, user), session.finish(request, user, listOf(retained)))
    }

    @Test
    fun lookupResponseValidatesBothIdsAndPreservesLongUid() {
        assertEquals(
            PostUserLookupResult.Found(BlockedPostUser("9007199254740993", "123", "昵称😀")),
            PostUserLookupResponse.parse("123", response(9007199254740993L)),
        )
        for (raw in listOf(
            response("789", id = "456"),
            response("789", id = ""),
            response("789", id = "0"),
            response("", id = "123"),
        )) {
            assertSame(PostUserLookupResult.InvalidResponse, PostUserLookupResponse.parse("123", raw))
        }
        for (uid in listOf(JSONObject.NULL, "", "0", "-1", "12.3", "1e3", "9223372036854775808")) {
            assertSame("uid=$uid", PostUserLookupResult.InvalidResponse,
                PostUserLookupResponse.parse("123", response(uid)))
        }
        assertSame(PostUserLookupResult.Failed, PostUserLookupResponse.parse("123", null))
        assertSame(PostUserLookupResult.InvalidResponse,
            PostUserLookupResponse.parse("123", "{\"error_code\":0}"))
    }

    private fun start(session: PostUserListSession) =
        (session.start("00123", emptyList()) as PostUserListSession.Start.Ready).request

    private fun response(uid: Any, id: String = "123") = JSONObject()
        .put("error_code", "0")
        .put("user", JSONObject()
            .put("id", uid)
            .put("tieba_uid", id)
            .put("name_show", "昵称😀"))
        .toString()
}
