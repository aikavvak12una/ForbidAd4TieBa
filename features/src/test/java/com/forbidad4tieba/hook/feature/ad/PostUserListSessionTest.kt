package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.BlockedPostUser
import com.forbidad4tieba.hook.config.PostUserFilterSettings
import org.junit.Assert.*
import org.junit.Test

class PostUserListSessionTest {
    private val user = BlockedPostUser("789", "123", "name")

    @Test fun extractsOnlyTheHashDelimitedIdFromTheCopiedShareText() {
        val text = "3.s:/@hhhhh\u2067~喵\u2066@给你分享了贴吧号#5500225939#整段复制后打开贴吧即可找到Ta"
        assertEquals("5500225939", PostUserFilterSettings.filterProfileIdInput(text))
        val session = PostUserListSession()
        val start = session.start(text, emptyList()) as PostUserListSession.Start.Ready
        assertEquals("5500225939", start.request.profileId)
    }

    @Test fun sharedIdsUseTheSameNormalizationAndDuplicateCheckAsTypedIds() {
        val session = PostUserListSession()
        assertSame(PostUserListSession.Start.Duplicate,
            session.start("3.s:/@name@给你分享了贴吧号# 00123 #整段复制", listOf(user)))
        assertNull(session.pending)
        val start = session.start("#00123#", emptyList()) as PostUserListSession.Start.Ready
        assertEquals("123", start.request.profileId)
    }

    @Test fun rejectsMalformedOrAmbiguousShareIdsWithoutStartingARequest() {
        val session = PostUserListSession()
        for (input in listOf("#0#", "#-123#", "#123.5#",
            "#123abc#", "#123# text #456#", "#123#456#", "##")) {
            assertSame(input, PostUserListSession.Start.InvalidId, session.start(input, emptyList()))
            assertNull(session.pending)
        }
    }

    @Test fun unwrappedInputKeepsOnlyDigitsBeforeLookup() {
        assertEquals("5500225939", PostUserFilterSettings.filterProfileIdInput("ID: 55a00225939 "))
        assertEquals("123456", PostUserFilterSettings.filterProfileIdInput("123, 456"))
        assertEquals("00123", PostUserFilterSettings.filterProfileIdInput(" 00123 "))
        assertEquals("", PostUserFilterSettings.filterProfileIdInput("没有数字"))
        val start = PostUserListSession().start("ID: 55a00225939 ", emptyList())
            as PostUserListSession.Start.Ready
        assertEquals("5500225939", start.request.profileId)
    }

    @Test fun validatesOneIdAndSuppressesDuplicateOrOverlappingRequests() {
        val session = PostUserListSession()
        for (input in listOf("", "0", "abc", "没有数字", "##")) {
            assertSame(PostUserListSession.Start.InvalidId, session.start(input, emptyList()))
            assertNull(session.pending)
        }
        assertSame(PostUserListSession.Start.Duplicate, session.start("00123", listOf(user)))
        val request = start(session)
        assertEquals("123", request.profileId)
        assertSame(PostUserListSession.Start.Busy, session.start("456", emptyList()))
        assertSame(request, session.pending)
    }

    @Test fun completesOnlyMatchingProfileWithoutDuplicatingAnExistingUser() {
        val session = PostUserListSession()
        val request = start(session)
        assertEquals(listOf(user), session.finish(request, user, listOf(user)))
        assertNull(session.pending)
        assertNull(session.finish(start(session), user.copy(profileId = "456"), emptyList()))
    }

    @Test fun oldResponsesCannotCompleteANewRequestForTheSameId() {
        val session = PostUserListSession()
        val old = start(session)
        session.cancel()
        val current = start(session)
        assertNull(session.finish(old, user, emptyList()))
        assertSame(current, session.pending)
        assertEquals(listOf(user), session.finish(current, user, emptyList()))
    }

    @Test fun closingTheEditorRejectsLateResultsAndFurtherWork() {
        val session = PostUserListSession()
        val request = start(session)
        session.close()
        assertNull(session.finish(request, user, emptyList()))
        assertSame(PostUserListSession.Start.Closed, session.start("123", emptyList()))
    }

    @Test fun deletedUserIsNotRecreatedByAPendingLookup() {
        val session = PostUserListSession()
        val request = start(session)
        session.removed(user.userId)
        assertNull(session.finish(request, user, emptyList()))
    }

    @Test fun completionMergesTheLatestListAndPreservesUnrelatedDeletions() {
        val session = PostUserListSession()
        val request = start(session)
        val retained = BlockedPostUser("456", "999", "retained")
        session.removed("111")
        assertEquals(listOf(retained, user), session.finish(request, user, listOf(retained)))
    }

    private fun start(session: PostUserListSession) =
        (session.start("00123", emptyList()) as PostUserListSession.Start.Ready).request
}
