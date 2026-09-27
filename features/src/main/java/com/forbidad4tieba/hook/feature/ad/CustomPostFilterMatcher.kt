package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.CustomPostFilterRules
import org.json.JSONObject

internal object CustomPostFilterMatcher {
    private const val BUTTON_NAME_KEY = "button_name"
    private const val GAME_EXT_KEY = "game_ext"
    private const val THREAD_TYPE_NORMAL = "0"
    private const val THREAD_TYPE_SCORE = "75"
    private const val THREAD_TYPE_LOTTERY = "76"
    private const val CARD_TYPE_NORMAL = "normal"
    private const val CARD_TYPE_QUESTION = "question"
    private const val CARD_TYPE_QUESTION_GOOD = "question_good"
    private const val CARD_TYPE_QUESTION_FEELYOU = "question_feelyou"
    private const val CARD_TYPE_COMMENT_FORWARD = "commentForwardCard"
    private const val CARD_TYPE_NORMAL_SCORE = "normalScore"
    private const val CARD_TYPE_BRAND_LOTTERY_AD = "brandLotteryAd"
    private const val SPECIAL_THREAD_TRUE = "1"
    private const val FORUM_LIKED_FALSE = "0"
    private const val PAGE_FROM_RECOMMEND = "recommend"
    private const val RECOM_TYPE_LIVE = "3"
    private val LOTTERY_PRIMARY_TEXT_MARKERS = arrayOf("抽奖", "大奖", "中奖")
    private val LOTTERY_CONTEXT_TEXT_MARKERS = arrayOf("活动时间", "开奖时间", "活动奖品", "盖楼")
    private val RECOMMEND_FORUM_TEMPLATE_KEYS = setOf(
        "recommend_card_forum_attention",
        "recommend_card_person_attention",
        "recommend_card_list",
        "sideway_list",
    )

    internal data class Decision(
        val blocked: Boolean,
        val reason: String? = null,
        val modelScores: Map<String, Double>? = null,
    )

    val KEEP = Decision(blocked = false)

    fun runtimeRules(): CustomPostFilterRules? = ConfigManager.snapshot().customPostRules

    fun decideByTemplateKey(templateKey: String?, rules: CustomPostFilterRules): Decision {
        if (templateKey.isNullOrBlank()) {
            return KEEP
        }
        val type = classifyByTemplateKey(templateKey) ?: return KEEP
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
            KEEP
        }
    }

    fun isEnabled(): Boolean {
        return runtimeRules() != null
    }

    fun decideByCardSchema(schema: String?, rules: CustomPostFilterRules): Decision =
        if (rules.hot && HotTopicRoute.matches(schema)) {
            Decision(blocked = true, reason = "custom_post_type:hot:card_schema=topic_detail")
        } else {
            KEEP
        }

    fun decideByRecommendCardTemplateKey(templateKey: String?, rules: CustomPostFilterRules): Decision {
        if (!rules.recommendForum || templateKey.isNullOrBlank()) {
            return KEEP
        }
        if (templateKey !in RECOMMEND_FORUM_TEMPLATE_KEYS) {
            return KEEP
        }
        return Decision(
            blocked = true,
            reason = "custom_post_type:${PostType.RECOMMEND_FORUM.key}:template_key=$templateKey",
        )
    }

    fun decideByFeedHeadParams(params: Map<*, *>?, rules: CustomPostFilterRules): Decision {
        if (!rules.needsFeedHeadParamsCheck || params == null) {
            return KEEP
        }
        val cardType = params.stringValue("card_type")
        if (rules.reply && cardType == CARD_TYPE_COMMENT_FORWARD) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:reply:card_type=$cardType",
            )
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
        val specialThread = params.stringValue("is_special_thread")
        val forumLiked = params.stringValue("forum_is_liked")
        val forumName = params.stringValue("forum_name")
        val pageFrom = params.stringValue("page_from")
        val title = params.stringValue("title")
        val abstractText = params.stringValue("abstract")
        if (rules.live &&
            recomType == RECOM_TYPE_LIVE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:live:recom_type=$recomType",
            )
        }
        if (rules.lottery) {
            val lotterySignal = findLotterySignal(threadType, cardType, title, abstractText)
            if (lotterySignal != null) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_type:lottery:$lotterySignal",
                )
            }
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
        return decideByModelScoreThreshold(params, rules)
    }

    private fun isHelpCardType(cardType: String?): Boolean {
        return cardType == CARD_TYPE_NORMAL ||
            cardType == CARD_TYPE_QUESTION ||
            cardType == CARD_TYPE_QUESTION_GOOD ||
            cardType == CARD_TYPE_QUESTION_FEELYOU
    }

    private fun findPromotionButtonName(params: Map<*, *>): String? {
        params.stringValue(BUTTON_NAME_KEY)?.let { return it }
        val gameExt = params.stringValue(GAME_EXT_KEY) ?: return null
        return runCatching {
            JSONObject(gameExt).optString(BUTTON_NAME_KEY).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun decideByModelScoreThreshold(params: Map<*, *>, rules: CustomPostFilterRules): Decision {
        if (!rules.modelScore) return KEEP
        val rawExtra = params.stringValue("extra") ?: return KEEP
        val scores = CustomPostModelScoreCatalog.extractScores(rawExtra)
        if (scores.isEmpty()) return KEEP
        val thresholds = rules.modelScoreThresholds
        if (thresholds.isEmpty()) return Decision(false, modelScores = scores)
        for (threshold in thresholds) {
            val score = scores[threshold.key] ?: continue
            if (score < threshold.threshold) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_model_score:${threshold.key}=$score<threshold=${threshold.threshold}",
                    modelScores = scores,
                )
            }
        }
        return Decision(false, modelScores = scores)
    }

    private fun classifyByTemplateKey(templateKey: String): PostType? {
        return when (templateKey) {
            "feed_input_guide" -> PostType.HELP
            "card_vote", "card_multi_vote", "feed_discuss", "feed_pk" -> PostType.VOTE
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

    private fun findLotterySignal(
        threadType: String?,
        cardType: String?,
        title: String?,
        abstractText: String?,
    ): String? {
        if (cardType == CARD_TYPE_BRAND_LOTTERY_AD) {
            return "card_type=$CARD_TYPE_BRAND_LOTTERY_AD"
        }
        if (threadType != THREAD_TYPE_LOTTERY) return null
        val text = buildString {
            if (!title.isNullOrBlank()) append(title)
            if (!abstractText.isNullOrBlank()) {
                if (isNotEmpty()) append(' ')
                append(abstractText)
            }
        }
        if (text.isBlank()) return null
        val hasPrimary = LOTTERY_PRIMARY_TEXT_MARKERS.any { text.contains(it) }
        val hasContext = LOTTERY_CONTEXT_TEXT_MARKERS.any { text.contains(it) }
        return if (hasPrimary && hasContext) {
            "thread_type=$THREAD_TYPE_LOTTERY,text=lottery_markers"
        } else {
            null
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
