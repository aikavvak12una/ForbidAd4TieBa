package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/** Frozen behavior before the full settings migration, including interacting parent switches. */
class AllSettingsBehaviorTest {
    @Test fun allSettingsPreserveTheAcceptedBehaviorMatrix() {
        val keys = listOf(
            "auto_hide_home_tab",
            "block_ad",
            "block_ad_feed",
            "block_ad_forum_page",
            "block_ad_home_bottom_easter_egg",
            "block_ad_home_side_bar_web",
            "block_ad_home_top_bar",
            "block_ad_mine_tab_web",
            "block_ad_post_page",
            "block_ad_search_box_text",
            "block_ad_strategy",
            "block_titan_patch",
            "bottom_tab_enter_forum",
            "bottom_tab_home",
            "bottom_tab_liquid_glass",
            "bottom_tab_message",
            "bottom_tab_mine",
            "bottom_tab_retail_store",
            "clean_share_tracking_params",
            "default_lzl_earliest",
            "default_notify_tab",
            "disable_ad_sdk_components",
            "disable_ai_components",
            "disable_apsaras_schedule",
            "disable_auto_refresh",
            "disable_flutter_preinit",
            "disable_host_slide_animation",
            "disable_monitor_sync_components",
            "disable_pb_gesture_font_scale",
            "disable_video_components",
            "enable_auto_load_more",
            "enable_auto_sign_in",
            "enable_comment_avatar_direct_profile",
            "enable_custom_post_filter",
            "enable_default_original_image",
            "enable_detailed_logging",
            "enable_free_copy",
            "enable_home_native_glass",
            "enable_pb_like_auto_reply",
            "enable_pb_performance_mode",
            "enable_pb_scroll_coalesce",
            "enable_performance_optimization",
            "enable_tab_customization",
            "filter_enter_forum_web",
            "filter_post_forum_keyword",
            "filter_post_game_booking",
            "filter_post_goods",
            "filter_post_help",
            "filter_post_hot",
            "filter_post_live",
            "filter_post_lottery",
            "filter_post_model_score",
            "filter_post_recommend_forum",
            "filter_post_reply",
            "filter_post_score",
            "filter_post_unfollowed_forum",
            "filter_post_video",
            "filter_post_vote",
            "force_host_feed_cold_opt",
            "force_host_performance_flags",
            "force_low_end_device_config",
            "force_pb_preload",
            "free_copy_comment_dialog",
            "free_copy_comment_injection",
            "free_copy_post_body",
            "free_copy_post_long_press",
            "hide_home_tab_red_dot",
            "hide_input_meme_bar",
            "open_web_link_in_system_browser",
            "private_read_receipt_invisible",
            "restricted_features_unlocked",
            "simplify_bottom_tabs",
            "simplify_home_tabs",
            "verify_reply_after_post",
        )
        val all = keys.associateWith { true }
        val cases = linkedMapOf<String, Map<String, Any?>>()
        cases["empty"] = emptyMap()
        cases["enabled"] = all
        keys.forEach { key ->
            cases["only/$key"] = mapOf(key to true)
            cases["disabled/$key"] = all + (key to false)
            cases["absent/$key"] = all - key
        }
        cases["values"] = all + mapOf(
            "pb_like_auto_reply_text" to "  reply  ",
            "reply_visibility_probe_max_attempts" to 999,
            "reply_visibility_probe_interval_ms" to -3,
            "filter_post_forum_keyword_list" to " A；b, A\n C ",
            "filter_post_model_score_thresholds" to "alpha=0.1234567;beta:2;bad=-1",
            "filter_post_model_score_auto_percentiles" to "alpha=P5;beta=20;bad=7",
            "filter_post_model_score_stats_post_limit" to 1,
            "home_top_tab_disabled_keys" to "[\"code:recommend\",\"module:followed\"]",
            "bottom_tab_liquid_glass_height_dp" to 999,
            "bottom_tab_liquid_glass_width_percent" to 5,
            "bottom_tab_liquid_glass_bottom_gap_dp" to -9,
        )
        val random = Random(101)
        repeat(32) { i -> cases["mixed/$i"] = keys.filter { random.nextBoolean() }.associateWith { random.nextBoolean() } }
        val results = mutableListOf<String>()
        for ((name, values) in cases) for (state in ConfigManager.ScanFeatureAvailabilityState.entries) {
            for (locked in listOf(false, true)) {
                val host = HostCapabilities(HookFeatureKey.orderedKeys.associateWith { state })
                val result = EffectiveSettingsPolicy.derive(UserSettings(values), host, RemoteSettingsPolicy(locked))
                // Keep the frozen matrix over its original fields. The appended feature has its own behavior tests.
                val legacySnapshot = result.snapshot.toString().substringBefore(", commentLevelFilter=") + ")"
                val text = legacySnapshot + "/" + result.normalizedBottomTabs
                val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
                results += "$name/$state/$locked=$hash"
            }
        }
        val actual = results.joinToString("\n", postfix = "\n")
        val file = File(System.getProperty("project.root"), "features/src/test/resources/settings-behavior-baseline.txt")
        assertEquals("Settings behavior changed", file.readText(), actual)
        assertEquals(2056, results.size)
    }
}
