package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints

internal val FEED_CARD_TOUCH_METHODS = arrayOf(
    "dispatchTouchEvent",
    "onInterceptTouchEvent",
    "onTouchEvent",
)

internal val HOME_TOP_CHROME_CLASSES = arrayOf(
    StableTiebaHookPoints.HOME_SCROLL_TAB_BAR_LAYOUT_CLASS,
    StableTiebaHookPoints.HOME_FIXED_APP_BAR_LAYOUT_CLASS,
)

internal val HOME_BOTTOM_TAB_HOST_CLASSES = arrayOf(
    StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS,
    StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS,
)

internal val HOST_SKIN_TYPE_CHANGE_METHODS = arrayOf(
    "setSkinType",
    "setSkinTypeValue",
)

internal const val HOME_RECOMMEND_CONTROL_FRAGMENT_CLASS =
    "com.baidu.tieba.homepage.framework.RecommendFrsControlFragment"
internal const val BD_PAGE_CONTEXT_CLASS = "com.baidu.adp.base.BdPageContext"
internal const val PB_POST_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.PostData"

internal val SKIN_MANAGER_SHAPE_BACKGROUND_SIGNATURES = arrayOf(
    arrayOf(View::class.java, Integer.TYPE, Integer.TYPE, Integer.TYPE),
    arrayOf(View::class.java, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE),
    arrayOf(
        View::class.java,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
    ),
    arrayOf(
        View::class.java,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
        Integer.TYPE,
    ),
)

internal val CARD_COMPONENT_GLASS_CLASSES = arrayOf(
    StableTiebaHookPoints.FEED_CARD_REPLY_VIEW_CLASS,
    StableTiebaHookPoints.FEED_CARD_VOTE_VIEW_CLASS,
    StableTiebaHookPoints.FEED_CARD_ORIGIN_MOUNT_VIEW_CLASS,
)

internal val HOME_BLOCKED_CARD_COMPONENT_CLASSES = arrayOf(
    StableTiebaHookPoints.FEED_CARD_INPUT_GUIDE_VIEW_CLASS,
)

internal val PB_COMMENT_SURFACE_CLASSES = arrayOf(
    StableTiebaHookPoints.PB_EXTENSION_PB_VIEW_CLASS,
    StableTiebaHookPoints.PB_ITEM_RELATIVE_VIEW_CLASS,
    StableTiebaHookPoints.PB_PAGE_BROWSER_RECYCLER_VIEW_CLASS,
    StableTiebaHookPoints.PB_COMMENT_RECYCLER_VIEW_CLASS,
    StableTiebaHookPoints.PB_COMMENT_FLOOR_VIEW_CLASS,
    StableTiebaHookPoints.PB_COMMENT_FLOOR_SUB_VIEW_CLASS,
)

internal val PB_COMMENT_ROOT_SURFACE_CLASSES = arrayOf(
    StableTiebaHookPoints.PB_EXTENSION_PB_VIEW_CLASS,
    StableTiebaHookPoints.PB_ITEM_RELATIVE_VIEW_CLASS,
    StableTiebaHookPoints.PB_PAGE_BROWSER_RECYCLER_VIEW_CLASS,
    StableTiebaHookPoints.PB_COMMENT_RECYCLER_VIEW_CLASS,
)

internal val PB_COMMENT_ITEM_SURFACE_CLASSES = arrayOf(
    StableTiebaHookPoints.PB_COMMENT_FLOOR_VIEW_CLASS,
    StableTiebaHookPoints.PB_COMMENT_FLOOR_SUB_VIEW_CLASS,
)

internal val SHARE_DIALOG_MARKER_CLASSES = arrayOf(
    "com.baidu.tieba.transmitShare.ShareScrollableLayout",
    "com.baidu.tieba.transmitShare.ShareGridLayout",
    "com.baidu.tieba.sharesdk.view.ShareDialogItemView",
)

internal const val HOME_SEARCH_BOX_HEADER_CONTAINER_CLASS =
    "androidx.coordinatorlayout.widget.CoordinatorLayout"
internal const val TB_RICH_TEXT_MODEL_CLASS = "com.baidu.tbadk.widget.richText.TbRichText"
internal val HOME_SEARCH_BOX_BOOTSTRAP_REFRESH_DELAYS_MS = longArrayOf(0L, 160L)
internal val CARD_COMPONENT_BOOTSTRAP_REFRESH_DELAYS_MS = longArrayOf(0L, 160L)
internal const val HOME_BOTTOM_TAB_BOUNDARY_PARENT_DEPTH = 4
internal const val HOME_TOP_TAB_RECOMMEND_TYPE = 1
internal const val HOME_TOP_TAB_RECOMMEND_CODE = "recommend"
internal const val PB_SUB_PB_SHADOW_ELEVATION_DP = 4.5f
internal const val PB_SUB_PB_SHADOW_TRANSLATION_Z_DP = 1.5f
internal const val PB_SUB_PB_SHADOW_PARENT_DEPTH = 3
internal const val PB_SUB_PB_SCROLL_CACHE_PARENT_DEPTH = 6
internal const val PB_SUB_PB_CONTENT_CLEAR_DEPTH = 3
internal const val PB_SUB_PB_EXTRA_VERTICAL_PADDING_DP = 4f
internal const val SUB_PB_REPLY_ITEM_CONTENT_CLEAR_DEPTH = 2
internal const val SUB_PB_NAVIGATION_BAR_BACKGROUND_CLEAR_DEPTH = 3
internal const val SUB_PB_NAVIGATION_BAR_REAPPLY_DELAY_MS = 160L
internal const val HOME_TOP_BACKGROUND_CLEAR_DEPTH = 2
internal const val HOME_TOP_RECYCLER_CHILD_CLEAR_DEPTH = 1
internal const val HOME_FEED_CARD_BACKGROUND_PARENT_SCAN_DEPTH = 4
internal const val HOME_IMAGE_CONTAINER_RADIUS_SCAN_DEPTH = 8
internal const val PB_COMMENT_TOP_CONTAINER_CLEAR_DEPTH = 2
internal const val PB_COMMENT_HOST_CLEAR_DEPTH = 5
internal const val PB_COMMENT_BACKGROUND_PATH_CLEAR_DEPTH = 18
internal const val PB_COMMENT_BACKGROUND_WRITE_PARENT_SCAN_DEPTH = 8
internal const val PB_COMMENT_LIST_ITEM_CLEAR_DEPTH = 4
internal const val SHARE_DIALOG_MARKER_SCAN_DEPTH = 8
internal const val BACKGROUND_CACHE_SAMPLE_EDGE = 48
internal const val PAGE_BACKGROUND_MAX_DECODE_EDGE = 1440
internal const val MAX_CACHED_BACKGROUND_BITMAP_BYTES = 18L * 1024L * 1024L
internal const val BACKGROUND_CACHE_METADATA_CHECK_INTERVAL_MS = 1500L
internal const val IMAGE_CONTAINER_RADIUS_CHILD_DEPTH = 3
internal const val IMAGE_CONTAINER_RADIUS_SCALE = 0.75f
internal const val PB_SORT_SWITCH_COMPONENT_CHECK_DEPTH = 4
internal const val PB_SORT_SWITCH_PARENT_SCAN_DEPTH = 16
internal const val PB_REPLY_TITLE_SORT_SEARCH_DEPTH = 8
internal const val PB_REPLY_TITLE_DYNAMIC_TINT_DEPTH = 8
internal const val PB_REPLY_TITLE_DECORATIVE_CLEAR_DEPTH = 6
internal const val PB_REPLY_TITLE_DECORATIVE_MAX_HEIGHT_DP = 2
internal const val PB_REPLY_TITLE_DECORATIVE_MIN_HEIGHT_PX = 2
internal const val PB_REPLY_BAR_INPUT_PARENT_SCAN_DEPTH = 4
internal const val PB_REPLY_BAR_INPUT_SEARCH_DEPTH = 8
internal const val PB_REPLY_BAR_INPUT_CAPSULE_MIN_SCORE = 5
internal const val PB_REPLY_BAR_INPUT_CAPSULE_MAX_HEIGHT_DP = 72f
internal const val PB_REPLY_BAR_INPUT_BOTTOM_TOLERANCE_DP = 180f
internal const val PB_REPLY_BAR_INPUT_CAPSULE_FALLBACK_RADIUS_DP = 16f
internal const val PB_REPLY_BAR_INPUT_FRAME_PARENT_SCAN_DEPTH = 6
internal const val PB_REPLY_BAR_INPUT_FRAME_MAX_HEIGHT_DP = 112f
internal const val PB_ENTER_FORUM_CAPSULE_FALLBACK_RADIUS_DP = 14f
internal const val SUB_PB_INPUT_ROOT_PARENT_SCAN_DEPTH = 12
internal const val SUB_PB_INPUT_CAPSULE_SCAN_DEPTH = 8
internal const val SUB_PB_INPUT_CAPSULE_MIN_SCORE = 8
internal const val SUB_PB_INPUT_FRAME_PARENT_CLEAR_DEPTH = 5
internal const val SUB_PB_INPUT_FRAME_MAX_HEIGHT_DP = 96f
internal const val SUB_PB_INPUT_FRAME_BOTTOM_TOLERANCE_DP = 112f
internal const val SUB_PB_INPUT_CAPSULE_FALLBACK_RADIUS_DP = 16f
internal const val SUB_PB_INPUT_BAR_REAPPLY_DELAY_MS = 160L
