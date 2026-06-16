package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import org.json.JSONObject

internal object CustomPostFilterMatcher {
    private const val BUTTON_NAME_KEY = "button_name"
    private const val GAME_EXT_KEY = "game_ext"
    private const val THREAD_TYPE_NORMAL = "0"
    private const val THREAD_TYPE_SCORE = "75"
    private const val CARD_TYPE_NORMAL = "normal"
    private const val CARD_TYPE_QUESTION = "question"
    private const val CARD_TYPE_NORMAL_SCORE = "normalScore"
    private const val SPECIAL_THREAD_TRUE = "1"
    private const val FORUM_LIKED_FALSE = "0"
    private const val PAGE_FROM_RECOMMEND = "recommend"
    private const val RECOM_TYPE_LIVE = "3"
    private val RECOMMEND_FORUM_TEMPLATE_KEYS = setOf(
        "recommend_card_forum_attention",
        "recommend_card_person_attention",
        "recommend_card_list",
    )

    internal data class Decision(
        val blocked: Boolean,
        val reason: String? = null,
    )

    internal data class RuntimeRules(
        val vote: Boolean,
        val video: Boolean,
        val reply: Boolean,
        val hot: Boolean,
        val goods: Boolean,
        val gameBooking: Boolean,
        val help: Boolean,
        val score: Boolean,
        val live: Boolean,
        val recommendForum: Boolean,
        val unfollowedForum: Boolean,
        val forumKeyword: Boolean,
        val forumKeywords: List<String>,
        val modelScore: Boolean,
        val modelScoreThresholds: List<ConfigManager.ModelScoreThreshold>,
    ) {
        val needsFeedHeadParamsCheck: Boolean =
            gameBooking ||
                help ||
                score ||
                live ||
                unfollowedForum ||
                forumKeyword ||
                modelScore
    }

    fun runtimeRules(): RuntimeRules? {
        val settings = ConfigManager.snapshot()
        if (!settings.isCustomPostFilterEnabled) return null
        val forumKeyword =
            settings.isPostForumKeywordFilterEnabled && settings.postForumKeywordList.isNotEmpty()
        val rules = RuntimeRules(
            vote = settings.isPostVoteFilterEnabled,
            video = settings.isPostVideoFilterEnabled,
            reply = settings.isPostReplyFilterEnabled,
            hot = settings.isPostHotFilterEnabled,
            goods = settings.isPostGoodsFilterEnabled,
            gameBooking = settings.isPostGameBookingFilterEnabled,
            help = settings.isPostHelpFilterEnabled,
            score = settings.isPostScoreFilterEnabled,
            live = settings.isPostLiveFilterEnabled,
            recommendForum = settings.isPostRecommendForumFilterEnabled,
            unfollowedForum = settings.isPostUnfollowedForumFilterEnabled,
            forumKeyword = forumKeyword,
            forumKeywords = settings.postForumKeywordList,
            modelScore = settings.isPostModelScoreFilterEnabled,
            modelScoreThresholds = settings.postModelScoreThresholds,
        )
        return if (rules.hasAnyRule()) rules else null
    }

    fun decideByTemplateKey(templateKey: String?, rules: RuntimeRules): Decision {
        if (templateKey.isNullOrBlank()) {
            return Decision(blocked = false)
        }
        val type = classifyByTemplateKey(templateKey) ?: return Decision(blocked = false)
        val enabled = when (type) {
            PostType.VOTE -> rules.vote
            PostType.VIDEO -> rules.video
            PostType.REPLY -> rules.reply
            PostType.HOT -> rules.hot
            PostType.GOODS -> rules.goods
            PostType.GAME_BOOKING -> rules.gameBooking
            PostType.HELP -> rules.help
            PostType.SCORE -> rules.score
            PostType.RECOMMEND_FORUM -> rules.recommendForum
        }
        return if (enabled) {
            Decision(
                blocked = true,
                reason = "custom_post_type:${type.key}:template_key=$templateKey",
            )
        } else {
            Decision(blocked = false)
        }
    }

    fun isEnabled(): Boolean {
        return runtimeRules() != null
    }

    fun decideByRecommendCardTemplateKey(templateKey: String?, rules: RuntimeRules): Decision {
        if (!rules.recommendForum || templateKey.isNullOrBlank()) {
            return Decision(blocked = false)
        }
        if (templateKey !in RECOMMEND_FORUM_TEMPLATE_KEYS) {
            return Decision(blocked = false)
        }
        return Decision(
            blocked = true,
            reason = "custom_post_type:${PostType.RECOMMEND_FORUM.key}:template_key=$templateKey",
        )
    }

    fun decideByFeedHeadParams(params: Map<*, *>?, rules: RuntimeRules): Decision {
        if (!rules.needsFeedHeadParamsCheck || params == null) {
            return Decision(blocked = false)
        }
        if (rules.gameBooking) {
            val buttonName = findPromotionButtonName(params)
            if (buttonName != null) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_type:game_booking:button_name=$buttonName",
                )
            }
        }
        val recomType = params.stringValue("recom_type")
        val threadType = params.stringValue("thread_type")
        val cardType = params.stringValue("card_type")
        val specialThread = params.stringValue("is_special_thread")
        val forumLiked = params.stringValue("forum_is_liked")
        val forumName = params.stringValue("forum_name")
        val pageFrom = params.stringValue("page_from")
        if (rules.live &&
            recomType == RECOM_TYPE_LIVE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:live:recom_type=$recomType",
            )
        }
        if (rules.help &&
            threadType == THREAD_TYPE_NORMAL &&
            isHelpCardType(cardType) &&
            specialThread == SPECIAL_THREAD_TRUE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:help:thread_type=$threadType,card_type=$cardType,is_special_thread=$specialThread",
            )
        }
        if (rules.score &&
            threadType == THREAD_TYPE_SCORE &&
            cardType == CARD_TYPE_NORMAL_SCORE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:score:thread_type=$threadType,card_type=$cardType",
            )
        }
        if (rules.unfollowedForum &&
            forumLiked == FORUM_LIKED_FALSE &&
            pageFrom == PAGE_FROM_RECOMMEND
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:unfollowed_forum:forum_is_liked=$forumLiked,page_from=$pageFrom",
            )
        }
        if (rules.forumKeyword &&
            !forumName.isNullOrBlank()
        ) {
            val forumLower = forumName.lowercase()
            val hit = rules.forumKeywords.firstOrNull { keyword ->
                forumLower.contains(keyword)
            }
            if (hit != null) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_type:forum_keyword:forum_name=$forumName,hit=$hit",
                )
            }
        }
        val modelScoreDecision = decideByModelScoreThreshold(params, rules)
        if (modelScoreDecision.blocked) return modelScoreDecision
        return Decision(blocked = false)
    }

    private fun isHelpCardType(cardType: String?): Boolean {
        return cardType == CARD_TYPE_NORMAL || cardType == CARD_TYPE_QUESTION
    }

    private fun findPromotionButtonName(params: Map<*, *>): String? {
        params.stringValue(BUTTON_NAME_KEY)?.let { return it }
        val gameExt = params.stringValue(GAME_EXT_KEY) ?: return null
        return runCatching {
            JSONObject(gameExt).optString(BUTTON_NAME_KEY).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun decideByModelScoreThreshold(params: Map<*, *>, rules: RuntimeRules): Decision {
        if (!rules.modelScore) return Decision(blocked = false)
        val rawExtra = params.stringValue("extra") ?: return Decision(blocked = false)
        val scores = CustomPostModelScoreCatalog.extractScores(rawExtra)
        if (scores.isEmpty()) return Decision(blocked = false)
        CustomPostModelScoreStats.record(scores)
        val thresholds = rules.modelScoreThresholds
        if (thresholds.isEmpty()) return Decision(blocked = false)
        for (threshold in thresholds) {
            val score = scores[threshold.key] ?: continue
            if (score < threshold.threshold) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_model_score:${threshold.key}=$score<threshold=${threshold.threshold}",
                )
            }
        }
        return Decision(blocked = false)
    }

    private fun RuntimeRules.hasAnyRule(): Boolean {
        return vote ||
            video ||
            reply ||
            hot ||
            goods ||
            gameBooking ||
            help ||
            score ||
            live ||
            recommendForum ||
            unfollowedForum ||
            forumKeyword ||
            modelScore
    }

    private fun classifyByTemplateKey(templateKey: String): PostType? {
        return when (templateKey) {
            "feed_input_guide" -> PostType.HELP
            "card_vote" -> PostType.VOTE
            "video", "video_card", "staggered_video" -> PostType.VIDEO
            "feed_origin_mount" -> PostType.REPLY
            "recommend_info", "hot_card", "hot_topic_card", "multi_thread_card" -> PostType.HOT
            "feed_link_store" -> PostType.GOODS
            "feed_mount_book" -> PostType.GAME_BOOKING
            "feed_score" -> PostType.SCORE
            in RECOMMEND_FORUM_TEMPLATE_KEYS -> PostType.RECOMMEND_FORUM
            else -> null
        }
    }

    private enum class PostType(val key: String) {
        VOTE("vote"),
        VIDEO("video"),
        REPLY("reply"),
        HOT("hot"),
        GOODS("goods"),
        GAME_BOOKING("game_booking"),
        HELP("help"),
        SCORE("score"),
        RECOMMEND_FORUM("recommend_forum"),
    }

    private fun Map<*, *>.stringValue(key: String): String? {
        val value = this[key] ?: return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }
}
