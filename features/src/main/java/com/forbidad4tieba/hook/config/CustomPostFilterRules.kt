package com.forbidad4tieba.hook.config

internal data class CustomPostFilterRules(
    val vote: Boolean,
    val video: Boolean,
    val reply: Boolean,
    val hot: Boolean,
    val goods: Boolean,
    val gameBooking: Boolean,
    val help: Boolean,
    val score: Boolean,
    val lottery: Boolean,
    val live: Boolean,
    val recommendForum: Boolean,
    val unfollowedForum: Boolean,
    val forumKeyword: Boolean,
    val forumKeywords: List<String>,
    val modelScore: Boolean,
    val modelScoreThresholds: List<ConfigManager.ModelScoreThreshold>,
) {
    val needsFeedHeadParamsCheck: Boolean =
        reply ||
            gameBooking ||
            help ||
            score ||
            lottery ||
            live ||
            unfollowedForum ||
            forumKeyword ||
            modelScore

    private val hasAnyRule: Boolean = vote ||
            video ||
            reply ||
            hot ||
            goods ||
            gameBooking ||
            help ||
            score ||
            lottery ||
            live ||
            recommendForum ||
            unfollowedForum ||
            forumKeyword ||
            modelScore

    companion object {
        fun from(settings: SettingsSnapshot): CustomPostFilterRules? {
            if (!settings.isCustomPostFilterEnabled) return null
            val forumKeyword =
                settings.isPostForumKeywordFilterEnabled && settings.postForumKeywordList.isNotEmpty()
            val rules = CustomPostFilterRules(
                vote = settings.isPostVoteFilterEnabled,
                video = settings.isPostVideoFilterEnabled,
                reply = settings.isPostReplyFilterEnabled,
                hot = settings.isPostHotFilterEnabled,
                goods = settings.isPostGoodsFilterEnabled,
                gameBooking = settings.isPostGameBookingFilterEnabled,
                help = settings.isPostHelpFilterEnabled,
                score = settings.isPostScoreFilterEnabled,
                lottery = settings.isPostLotteryFilterEnabled,
                live = settings.isPostLiveFilterEnabled,
                recommendForum = settings.isPostRecommendForumFilterEnabled,
                unfollowedForum = settings.isPostUnfollowedForumFilterEnabled,
                forumKeyword = forumKeyword,
                forumKeywords = settings.postForumKeywordList,
                modelScore = settings.isPostModelScoreFilterEnabled,
                modelScoreThresholds = settings.postModelScoreThresholds,
            )
            return if (rules.hasAnyRule) rules else null
        }
    }
}
