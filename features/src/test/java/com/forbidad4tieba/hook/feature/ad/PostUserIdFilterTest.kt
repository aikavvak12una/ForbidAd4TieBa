package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.*
import org.junit.Test

class PostUserIdFilterTest {
    private val settings = SettingsSnapshot.bootstrap().copy(
        isCustomPostFilterEnabled = true,
        isPostUserIdFilterEnabled = true,
        postUserIdList = setOf("123", "9007199254740993"),
    )
    private val filter = feedFilterFixture(Data::class.java, Component::class.java)

    @Test fun matchesWholeAuthorUidIncludingLongValuesAndNeverSubstrings() {
        for (uid in listOf("123", " 00123 ", 123, 123L, "9007199254740993", 9007199254740993L)) {
            val decision = CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("user_id" to uid), settings.customPostRules!!)
            assertTrue("uid=$uid", decision.blocked)
            assertTrue(decision.reason!!.startsWith("custom_post_type:user_id:"))
        }
        for (uid in listOf(null, "", "0", "12", "1234", "9123", "123.0", 123.0, "-123", "123 456", "9007199254740992")) {
            assertSame("uid=$uid", CustomPostFilterMatcher.KEEP,
                CustomPostFilterMatcher.decideByFeedHeadParams(mapOf("user_id" to uid), settings.customPostRules!!))
        }
    }

    @Test fun unrelatedIdsAndMissingParamsDoNotBlockTheCard() {
        assertSame(CustomPostFilterMatcher.KEEP,
            CustomPostFilterMatcher.decideByFeedHeadParams(null, settings.customPostRules!!))
        assertSame(CustomPostFilterMatcher.KEEP, CustomPostFilterMatcher.decideByFeedHeadParams(
            mapOf("uid" to "123", "author_id" to "123", "thread_id" to "123", "forum_id" to "123"),
            settings.customPostRules!!,
        ))
    }

    @Test fun removesEntireCardsAndPreservesOrderIdentityAndOriginalInput() {
        val blocked = card("user_id" to "123")
        val kept = card("user_id" to "1234")
        val missing = card("thread_id" to "123")
        val input = listOf(kept, blocked, null, missing, kept, blocked)
        for (method in listOf("setList", "loadMore")) {
            val output = CustomPostCardBlockHook.filterList(input, filter, method, rules = settings.customPostRules)
            assertEquals(listOf(kept, null, missing, kept), output)
            assertSame(kept, output[0])
            assertNotSame(input, output)
            assertEquals(listOf(kept, blocked, null, missing, kept, blocked), input)
        }
    }

    @Test fun disablingEitherSwitchOrClearingUsersKeepsTheOriginalList() {
        val input = listOf(card("user_id" to "123"))
        for (disabled in listOf(
            settings.copy(isCustomPostFilterEnabled = false),
            settings.copy(isPostUserIdFilterEnabled = false),
            settings.copy(postUserIdList = emptySet()),
        )) {
            assertNull(disabled.customPostRules)
            assertSame(input, CustomPostCardBlockHook.filterList(input, filter, "setList", rules = disabled.customPostRules))
        }
        val unmatched = listOf(card("user_id" to "456"))
        assertSame(unmatched, CustomPostCardBlockHook.filterList(unmatched, filter, "setList", rules = settings.customPostRules))
    }

    @Test fun missingHeadTargetDisablesOnlyHeadRulesAndOtherFiltersStillWork() {
        val uidCard = card("user_id" to "123")
        val voteCard = Card(Data(listOf(Component("card_vote", emptyMap()))))
        val input = listOf(uidCard, voteCard)
        val runtime = filter.copy(headParamsField = null)
        assertSame(input, CustomPostCardBlockHook.filterList(input, runtime, "setList", rules = settings.customPostRules))
        assertEquals(listOf(uidCard), CustomPostCardBlockHook.filterList(input, runtime, "setList",
            rules = settings.copy(isPostVoteFilterEnabled = true).customPostRules))
    }

    @Test fun forumAndUidRulesCooperateAndSnapshotChangesDoNotAffectPreviousRules() {
        val both = settings.copy(isPostForumKeywordFilterEnabled = true, postForumKeywordList = listOf("blocked"))
        val byUid = card("user_id" to "123", "forum_name" to "allowed")
        val byForum = card("user_id" to "456", "forum_name" to "Blocked Forum")
        val kept = card("user_id" to "789", "forum_name" to "allowed")
        val input = listOf(byUid, byForum, kept)
        assertEquals(listOf(kept), CustomPostCardBlockHook.filterList(input, filter, "setList", rules = both.customPostRules))
        val changed = both.copy(postUserIdList = setOf("789"))
        assertEquals(listOf(byUid), CustomPostCardBlockHook.filterList(input, filter, "setList", rules = changed.customPostRules))
        assertEquals(listOf(kept), CustomPostCardBlockHook.filterList(input, filter, "setList", rules = both.customPostRules))
    }

    private fun card(vararg params: Pair<String, String>) =
        Card(Data(listOf(Component("feed_head", mapOf(*params)), Component("feed_content", emptyMap()))))

    class Card(private val data: Data) : FeedTemplateFixture {
        override fun templateKey() = "feed_card"
        override fun payload() = data
    }
    class Data(@JvmField val dataList: List<Component>)
    class Component(private val key: String, @JvmField val params: Map<String, String>) : FeedTemplateFixture {
        override fun templateKey() = key
    }
}
