package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import org.json.JSONObject

internal object CustomPostFilterMatcher {
    private const val BUTTON_NAME_KEY = "button_name"
    private const val GAME_EXT_KEY = "game_ext"
    private const val THREAD_TYPE_NORMAL = "0"
    private const val THREAD_TYPE_SCORE = "75"
    private const val CARD_TYPE_NORMAL = "normal"
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

    fun decideByTemplateKey(templateKey: String?): Decision {
        if (!isEnabled() || templateKey.isNullOrBlank()) {
            return Decision(blocked = false)
        }
        val type = classifyByTemplateKey(templateKey) ?: return Decision(blocked = false)
        val enabled = when (type) {
            PostType.VOTE -> ConfigManager.isPostVoteFilterEnabled
            PostType.VIDEO -> ConfigManager.isPostVideoFilterEnabled
            PostType.REPLY -> ConfigManager.isPostReplyFilterEnabled
            PostType.HOT -> ConfigManager.isPostHotFilterEnabled
            PostType.GOODS -> ConfigManager.isPostGoodsFilterEnabled
            PostType.GAME_BOOKING -> ConfigManager.isPostGameBookingFilterEnabled
            PostType.HELP -> ConfigManager.isPostHelpFilterEnabled
            PostType.SCORE -> ConfigManager.isPostScoreFilterEnabled
            PostType.RECOMMEND_FORUM -> ConfigManager.isPostRecommendForumFilterEnabled
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
        if (!ConfigManager.isCustomPostFilterEnabled) return false
        return ConfigManager.isPostVoteFilterEnabled ||
            ConfigManager.isPostVideoFilterEnabled ||
            ConfigManager.isPostReplyFilterEnabled ||
            ConfigManager.isPostHotFilterEnabled ||
            ConfigManager.isPostGoodsFilterEnabled ||
            ConfigManager.isPostGameBookingFilterEnabled ||
            ConfigManager.isPostHelpFilterEnabled ||
            ConfigManager.isPostScoreFilterEnabled ||
            ConfigManager.isPostRecommendForumFilterEnabled ||
            ConfigManager.isPostLiveFilterEnabled ||
            ConfigManager.isPostUnfollowedForumFilterEnabled ||
            (ConfigManager.isPostForumKeywordFilterEnabled && ConfigManager.postForumKeywordList.isNotEmpty()) ||
            ConfigManager.isPostModelScoreFilterEnabled
    }

    fun needsRecommendCardNestedDataCheck(): Boolean {
        return ConfigManager.isCustomPostFilterEnabled &&
            ConfigManager.isPostRecommendForumFilterEnabled
    }

    fun decideByRecommendCardTemplateKey(templateKey: String?): Decision {
        if (!needsRecommendCardNestedDataCheck() || templateKey.isNullOrBlank()) {
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

    fun needsFeedHeadParamsCheck(): Boolean {
        if (!ConfigManager.isCustomPostFilterEnabled) return false
        return ConfigManager.isPostGameBookingFilterEnabled ||
            ConfigManager.isPostHelpFilterEnabled ||
            ConfigManager.isPostScoreFilterEnabled ||
            ConfigManager.isPostLiveFilterEnabled ||
            ConfigManager.isPostUnfollowedForumFilterEnabled ||
            (ConfigManager.isPostForumKeywordFilterEnabled && ConfigManager.postForumKeywordList.isNotEmpty()) ||
            ConfigManager.isPostModelScoreFilterEnabled
    }

    fun decideByFeedHeadParams(params: Map<*, *>?): Decision {
        if (!needsFeedHeadParamsCheck() || params == null) {
            return Decision(blocked = false)
        }
        if (ConfigManager.isPostGameBookingFilterEnabled) {
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
        if (ConfigManager.isPostLiveFilterEnabled &&
            recomType == RECOM_TYPE_LIVE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:live:recom_type=$recomType",
            )
        }
        if (ConfigManager.isPostHelpFilterEnabled &&
            threadType == THREAD_TYPE_NORMAL &&
            cardType == CARD_TYPE_NORMAL &&
            specialThread == SPECIAL_THREAD_TRUE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:help:thread_type=$threadType,card_type=$cardType,is_special_thread=$specialThread",
            )
        }
        if (ConfigManager.isPostScoreFilterEnabled &&
            threadType == THREAD_TYPE_SCORE &&
            cardType == CARD_TYPE_NORMAL_SCORE
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:score:thread_type=$threadType,card_type=$cardType",
            )
        }
        if (ConfigManager.isPostUnfollowedForumFilterEnabled &&
            forumLiked == FORUM_LIKED_FALSE &&
            pageFrom == PAGE_FROM_RECOMMEND
        ) {
            return Decision(
                blocked = true,
                reason = "custom_post_type:unfollowed_forum:forum_is_liked=$forumLiked,page_from=$pageFrom",
            )
        }
        if (ConfigManager.isPostForumKeywordFilterEnabled &&
            ConfigManager.postForumKeywordList.isNotEmpty() &&
            !forumName.isNullOrBlank()
        ) {
            val forumLower = forumName.lowercase()
            val hit = ConfigManager.postForumKeywordList.firstOrNull { keyword ->
                forumLower.contains(keyword)
            }
            if (hit != null) {
                return Decision(
                    blocked = true,
                    reason = "custom_post_type:forum_keyword:forum_name=$forumName,hit=$hit",
                )
            }
        }
        val modelScoreDecision = decideByModelScoreThreshold(params)
        if (modelScoreDecision.blocked) return modelScoreDecision
        return Decision(blocked = false)
    }

    private fun findPromotionButtonName(params: Map<*, *>): String? {
        params.stringValue(BUTTON_NAME_KEY)?.let { return it }
        val gameExt = params.stringValue(GAME_EXT_KEY) ?: return null
        return runCatching {
            JSONObject(gameExt).optString(BUTTON_NAME_KEY).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun decideByModelScoreThreshold(params: Map<*, *>): Decision {
        if (!ConfigManager.isPostModelScoreFilterEnabled) return Decision(blocked = false)
        val rawExtra = params.stringValue("extra") ?: return Decision(blocked = false)
        val scores = CustomPostModelScoreCatalog.extractScores(rawExtra)
        if (scores.isEmpty()) return Decision(blocked = false)
        CustomPostModelScoreStats.record(scores)
        val thresholds = ConfigManager.postModelScoreThresholds
        if (thresholds.isEmpty()) return Decision(blocked = false)
        val autoPercentiles = ConfigManager.postModelScoreAutoPercentiles
        for (threshold in thresholds) {
            if (
                autoPercentiles.containsKey(threshold.key) &&
                !CustomPostModelScoreStats.isAutoPercentileThresholdReady(threshold.key)
            ) {
                continue
            }
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
