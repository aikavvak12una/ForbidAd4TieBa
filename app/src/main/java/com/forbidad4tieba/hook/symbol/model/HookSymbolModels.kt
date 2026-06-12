package com.forbidad4tieba.hook.symbol.model

internal fun interface ScanLogger {
    fun log(line: String)
}

object HookFeatureState {
    const val FULL = "full"
    const val PARTIAL = "partial"
    const val DISABLED = "disabled"
    const val HARD_CODED = "hardcoded"
}

object ScanSupportState {
    const val SUPPORTED = "supported"
    const val UNSUPPORTED_VERSION = "unsupported_version"
    const val NON_OFFICIAL = "non_official"
    const val UNKNOWN = "unknown"
}

object HookFeatureKey {
    const val BLOCK_AD = "block_ad"
    const val ENABLE_CUSTOM_POST_FILTER = "enable_custom_post_filter"

    const val SIMPLIFY_HOME_TOP_TABS = "simplify_home_tabs"
    const val SIMPLIFY_BOTTOM_TABS = "simplify_bottom_tabs"
    const val HIDE_PB_BOTTOM_BANNER = "hide_pb_bottom_enter_bar"
    const val FILTER_ENTER_FORUM_WEB = "filter_enter_forum_web"
    const val OPEN_WEB_LINK_IN_SYSTEM_BROWSER = "open_web_link_in_system_browser"
    const val HOME_NATIVE_GLASS = "enable_home_native_glass"
    const val AUTO_LOAD_MORE = "enable_auto_load_more"
    const val ENABLE_PB_LIKE_AUTO_REPLY = "enable_pb_like_auto_reply"
    const val DISABLE_AUTO_REFRESH = "disable_auto_refresh"
    const val ENABLE_PB_SCROLL_COALESCE = "enable_pb_scroll_coalesce"
    const val DISABLE_PB_GESTURE_FONT_SCALE = "disable_pb_gesture_font_scale"
    const val DISABLE_FORUM_NATIVE_TOP_SHIFT = "disable_forum_native_top_shift"
    const val FREE_COPY = "enable_free_copy"
    const val DEFAULT_NOTIFY_TAB = "default_notify_tab"
    const val DEFAULT_ORIGINAL_IMAGE = "enable_default_original_image"
    const val AUTO_SIGN_IN = "enable_auto_sign_in"
    const val PRIVATE_READ_RECEIPT_INVISIBLE = "private_read_receipt_invisible"
    const val CLEAN_SHARE_TRACKING_PARAMS = "clean_share_tracking_params"
    const val DISABLE_AI_COMPONENTS = "disable_ai_components"
    const val VERIFY_REPLY_AFTER_POST = "verify_reply_after_post"
}

data class HookFeatureStatus(
    val state: String = HookFeatureState.DISABLED,
    val missingCritical: List<String> = emptyList(),
    val missingOptional: List<String> = emptyList(),
) {
    fun isSupported(): Boolean = state != HookFeatureState.DISABLED
    fun isPartial(): Boolean = state == HookFeatureState.PARTIAL
}
