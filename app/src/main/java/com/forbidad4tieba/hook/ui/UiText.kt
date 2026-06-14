package com.forbidad4tieba.hook.ui

object UiText {
    object Settings {
        const val GROUP_CONTENT_BLOCK = "内容屏蔽"
        const val GROUP_UI_OPTIMIZE = "UI 净化"
        const val GROUP_PERFORMANCE = "性能优化"
        const val GROUP_EXTENSION = "扩展"
        const val GROUP_DEFAULT_ENABLED = "默认生效"
        const val DEFAULT_ENABLED_DESC = "默认启用的功能"

        const val BLOCK_AD_LABEL = "屏蔽广告"
        const val BLOCK_AD_DESC = "屏蔽开屏、信息流、帖子详情页等推广广告"
        const val BLOCK_AD_DIALOG_TITLE = BLOCK_AD_LABEL
        const val BLOCK_AD_SAVED = "屏蔽广告子功能配置已保存"
        const val BLOCK_AD_FEED_LABEL = "信息流广告"
        const val BLOCK_AD_FEED_DESC = "屏蔽首页信息流广告卡片和广告容器"
        const val BLOCK_AD_POST_PAGE_LABEL = "帖子页广告"
        const val BLOCK_AD_POST_PAGE_DESC = "屏蔽帖子详情页列表广告、广告请求、掉落广告和广告实验"
        const val BLOCK_AD_FORUM_PAGE_LABEL = "吧页面广告"
        const val BLOCK_AD_FORUM_PAGE_DESC = "屏蔽吧页面广告弹窗、动画浮层和底部游戏推广"
        const val BLOCK_AD_STRATEGY_LABEL = "开屏与广告策略"
        const val BLOCK_AD_STRATEGY_DESC = "关闭开屏广告、免广告状态和广告策略开关"
        const val BLOCK_AD_SEARCH_BOX_TEXT_LABEL = "搜索框文字广告"
        const val BLOCK_AD_SEARCH_BOX_TEXT_DESC = "清空首页搜索框轮播推广文字"
        const val BLOCK_AD_HOME_TOP_BAR_LABEL = "首页顶部游戏推广"
        const val BLOCK_AD_HOME_TOP_BAR_DESC = "隐藏首页右上游戏、提示和红点槽位"
        const val BLOCK_AD_MINE_TAB_WEB_LABEL = "净化我的页面"
        const val BLOCK_AD_MINE_TAB_WEB_DESC = "隐藏我的页 Web 推广区块"
        const val BLOCK_AD_HOME_SIDE_BAR_WEB_LABEL = "净化首页侧边栏"
        const val BLOCK_AD_HOME_SIDE_BAR_WEB_DESC = "隐藏首页侧边栏 Web 推广区块"
        const val CUSTOM_POST_FILTER_LABEL = "首页推荐自定义屏蔽"
        const val CUSTOM_POST_FILTER_DESC = "按帖子类型或特征屏蔽信息流卡片"
        const val CUSTOM_POST_FILTER_VOTE_LABEL = "屏蔽投票帖"
        const val CUSTOM_POST_FILTER_VOTE_DESC = "屏蔽包含投票组件的帖子"
        const val CUSTOM_POST_FILTER_VIDEO_LABEL = "屏蔽视频帖"
        const val CUSTOM_POST_FILTER_VIDEO_DESC = "屏蔽视频类型帖子"
        const val CUSTOM_POST_FILTER_LIVE_LABEL = "屏蔽直播帖"
        const val CUSTOM_POST_FILTER_LIVE_DESC = "屏蔽信息流直播帖子"
        const val CUSTOM_POST_FILTER_REPLY_LABEL = "屏蔽回复帖"
        const val CUSTOM_POST_FILTER_REPLY_DESC = "屏蔽回复转发类帖子"
        const val CUSTOM_POST_FILTER_HOT_LABEL = "屏蔽热点"
        const val CUSTOM_POST_FILTER_HOT_DESC = "屏蔽推荐信息组件、实时热点和热门话题卡片"
        const val CUSTOM_POST_FILTER_GOODS_LABEL = "屏蔽带货帖"
        const val CUSTOM_POST_FILTER_GOODS_DESC = "屏蔽带商品链接组件的帖子"
        const val CUSTOM_POST_FILTER_GAME_BOOKING_LABEL = "屏蔽游戏/购买推广贴"
        const val CUSTOM_POST_FILTER_GAME_BOOKING_DESC = "屏蔽带游戏预约组件的帖子"
        const val CUSTOM_POST_FILTER_HELP_LABEL = "屏蔽求助/悬赏帖"
        const val CUSTOM_POST_FILTER_HELP_DESC = "屏蔽求助帖和悬赏互动帖子"
        const val CUSTOM_POST_FILTER_SCORE_LABEL = "屏蔽打分帖"
        const val CUSTOM_POST_FILTER_SCORE_DESC = "按打分帖特征屏蔽整张帖子卡片"
        const val CUSTOM_POST_FILTER_RECOMMEND_FORUM_LABEL = "屏蔽可能感兴趣的吧"
        const val CUSTOM_POST_FILTER_RECOMMEND_FORUM_DESC = "整块屏蔽首页推荐里的推荐吧卡片"
        const val CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_LABEL = "屏蔽未关注的吧"
        const val CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_DESC = "屏蔽推荐流中 forum_is_liked=0 的帖子"
        const val CUSTOM_POST_FILTER_FORUM_KEYWORD_LABEL = "按关键词屏蔽吧"
        const val CUSTOM_POST_FILTER_FORUM_KEYWORD_DESC = "按吧名关键词屏蔽帖子"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_LABEL = "按帖子模型分数屏蔽"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_DESC = "低于单项模型阈值时屏蔽帖子"
        const val CUSTOM_POST_FILTER_DIALOG_TITLE = "自定义帖子屏蔽"
        const val CUSTOM_POST_FILTER_SAVED = "自定义帖子屏蔽配置已保存"
        const val CUSTOM_POST_FILTER_KEYWORD_DIALOG_TITLE = "吧名关键词"
        const val CUSTOM_POST_FILTER_KEYWORD_HINT = "输入关键词，逗号或换行分隔"
        const val CUSTOM_POST_FILTER_KEYWORD_GUIDE = "支持逗号、分号或换行分隔，按吧名包含匹配"
        const val CUSTOM_POST_FILTER_KEYWORD_COUNT_PREFIX = "已识别关键词："
        const val CUSTOM_POST_FILTER_KEYWORD_EMPTY = "关键词已清空"
        const val CUSTOM_POST_FILTER_KEYWORD_SAVED = "吧名关键词已保存"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_DIALOG_TITLE = "模型分数阈值筛选"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_GUIDE = "留空表示不启用该模型过滤，填写最低允许数值\n*过高的值可能导致信息流频繁加载\n*对求助类型帖子无效且不统计\n*启用指定百分位为最低值时，将在应用启动时自动更新"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_HINT = "最低值"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_LABEL = "帖子统计数量"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_DESC = "滚动统计指定数量的帖子样本数据"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_HINT = "5000"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_ICON = "\u2716"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_DESC = "清除统计数据"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEARED = "统计数据已清除"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_STARTED = "开启统计数据"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_INVALID = "数量不能低于1000"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_ICON = "\u2335"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_COLLAPSE_ICON = "\u2335"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_DESC = "展开统计"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_COLLAPSE_DESC = "收起统计"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_EMPTY = "模型分数阈值已清空"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_SAVED = "模型分数阈值已保存"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_EMPTY = "暂无统计样本"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_SAMPLE_COUNT_LABEL = "样本数量"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_AVERAGE_LABEL = "平均数"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_STATS_DISTRIBUTION_LABEL = "分布信息"
        const val CUSTOM_POST_FILTER_MODEL_SCORE_AUTO_PERCENTILE_DIALOG_TITLE = "自动应用百分位"
        const val CUSTOM_POST_FILTER_MODEL_MSD_SCORE_LABEL = "msd_score"
        const val CUSTOM_POST_FILTER_MODEL_MSD_SCORE_DESC = "MSD 综合兴趣分"
        const val CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_LABEL = "msd_duration_score"
        const val CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_DESC = "MSD 时长兴趣分"
        const val CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_LABEL = "dnn_pb_dur_ctr[0]"
        const val CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_DESC = "DNN 帖页停留/点击预估"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_LABEL = "cupai_all_scores[1]"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_DESC = "粗排多目标兴趣分"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_LABEL = "cupai_all_scores[2]"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_DESC = "粗排多目标互动/停留分"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_LABEL = "cupai_all_scores[3]"
        const val CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_DESC = "粗排多目标细分目标分"
        const val CUSTOM_POST_FILTER_MODEL_CDNN_LTR_LABEL = "cdnn_ltr"
        const val CUSTOM_POST_FILTER_MODEL_CDNN_LTR_DESC = "排序融合分"

        const val SIMPLIFY_HOME_TAB_LABEL = "自定义首页顶部 Tab"
        const val SIMPLIFY_HOME_TAB_DESC = "配置有料、推荐、直播、关注 Tab 显示"
        const val AUTO_HIDE_HOME_TAB_LABEL = "浏览时隐藏 Tab "
        const val AUTO_HIDE_HOME_TAB_DESC = "浏览首页信息流时随滚动收起 Tab 栏"
        const val SIMPLIFY_BOTTOM_TAB_LABEL = "自定义底部 Tab"
        const val SIMPLIFY_BOTTOM_TAB_DESC = "配置首页、进吧、小卖部、消息、我的 Tab 显示"
        const val HIDE_PB_BOTTOM_BANNER_LABEL = "屏蔽帖子底部热点/进吧组件"
        const val FILTER_ENTER_FORUM_WEB_LABEL = "净化进吧页面"
        const val FILTER_ENTER_FORUM_WEB_DESC = "替换进吧页面为关注的吧"
        const val OPEN_WEB_LINK_IN_SYSTEM_BROWSER_LABEL = "使用外置浏览器打开链接"
        const val OPEN_WEB_LINK_IN_SYSTEM_BROWSER_DESC = "调用系统浏览器，禁用跳转内置浏览器和小程序"
        const val HOME_NATIVE_GLASS_LABEL = "推荐页/评论页自定义背景"
        const val HOME_NATIVE_GLASS_DESC = "为首页推荐信息流和帖子评论页设置自定义背景和卡片毛玻璃效果，会造成额外性能消耗"
        const val HOME_NATIVE_GLASS_DIALOG_TITLE = HOME_NATIVE_GLASS_LABEL
        const val HOME_NATIVE_GLASS_MODE_LIGHT_LABEL = "浅色模式"
        const val HOME_NATIVE_GLASS_MODE_DARK_LABEL = "深色模式"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_LABEL = "背景图片"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_DESC = "*过高分辨率的图片会造成额外性能消耗\n*选择图片后自动计算合适的叠色强度以保证文本可读性"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_NONE = "未选择"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CHOOSE = "选择"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CLEAR = "清除"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PICKER_UNAVAILABLE = "无法打开系统媒体图片选择器"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_IMPORT_FAILED = "背景图片导入失败"
        const val HOME_NATIVE_GLASS_BACKGROUND_IMAGE_IMPORTED = "背景图片已导入"
        const val HOME_NATIVE_GLASS_TINT_COLOR_LABEL = "动态取色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_DESC = "选择主题颜色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_DEFAULT = "默认"
        const val HOME_NATIVE_GLASS_TINT_COLOR_EMPTY = "选择背景图片后可提取颜色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_UNAVAILABLE = "未提取到可选颜色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADD = "+"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADD_DESC = "添加主题颜色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADD_DIALOG_TITLE = "添加主题颜色"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADD_HINT = "#RRGGBB"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADD_INVALID = "色值格式无效"
        const val HOME_NATIVE_GLASS_TINT_COLOR_ADDED = "颜色已添加"
        const val HOME_NATIVE_GLASS_TINT_COLOR_DUPLICATED = "颜色已存在"
        const val HOME_NATIVE_GLASS_TINT_ALPHA_LABEL = "叠色强度"
        const val HOME_NATIVE_GLASS_TINT_ALPHA_DESC = "作用于模糊处理效果上层，用于提高文本可读性，黑↔白"
        const val HOME_NATIVE_GLASS_TAB_DYNAMIC_TINT_LABEL = "Tab 动态底色"
        const val HOME_NATIVE_GLASS_TAB_DYNAMIC_TINT_DESC = "使用动态取色作为顶部和底部 Tab 背景底色"
        const val HOME_NATIVE_GLASS_CARD_BLUR_LABEL = "模糊强度"
        const val HOME_NATIVE_GLASS_CARD_BLUR_DESC = "用于提高复杂背景下的文本可读性"
        const val HOME_NATIVE_GLASS_CARD_RADIUS_LABEL = "卡片圆角"
        const val HOME_NATIVE_GLASS_CARD_RADIUS_DESC = "帖子卡片以及卡片内组件背景圆角半径"
        const val HOME_NATIVE_GLASS_STROKE_LABEL = "描边效果"
        const val HOME_NATIVE_GLASS_STROKE_DESC = "为自定义背景卡片显示高光描边"
        const val HOME_NATIVE_GLASS_SHADOW_LABEL = "阴影强度"
        const val HOME_NATIVE_GLASS_SHADOW_DESC = "控制卡片柔和阴影透明度"
        const val HOME_NATIVE_GLASS_RESTORE_DEFAULTS = "恢复默认"
        const val HOME_NATIVE_GLASS_SAVED = "已保存，重启生效"
        fun homeNativeGlassBackgroundImageSelected(name: String): String = "已选择：$name"
        fun homeNativeGlassTintColorSwatch(index: Int): String = "动态取色色块 $index"

        const val AUTO_LOAD_MORE_LABEL = "预加载"
        const val AUTO_LOAD_MORE_DESC = "浏览信息流和帖子评论时自动静默加载下一页\n*滑动卡顿尝试关闭此功能"
        const val PB_LIKE_AUTO_REPLY_LABEL = "快捷回复"
        const val PB_LIKE_AUTO_REPLY_DESC = "点赞帖子后，自动发送预设回复内容"
        const val PB_LIKE_AUTO_REPLY_DIALOG_TITLE = "点赞后自动回复"
        const val PB_LIKE_AUTO_REPLY_CONTENT_LABEL = "回复内容"
        const val PB_LIKE_AUTO_REPLY_CONTENT_HINT = "输入点赞帖子后自动发送的回复"
        const val PB_LIKE_AUTO_REPLY_CONTENT_EMPTY = "回复内容不能为空"
        const val PB_LIKE_AUTO_REPLY_CONTENT_SAVED = "自动回复内容已保存"
        const val REPLY_VISIBILITY_PROBE_LABEL = "评论检测"
        const val REPLY_VISIBILITY_PROBE_DESC = "发送评论后 Toast 输出评论状态"
        const val REPLY_VISIBILITY_PROBE_DIALOG_TITLE = REPLY_VISIBILITY_PROBE_LABEL
        const val REPLY_VISIBILITY_PROBE_GUIDE =
            "通过点赞获取评论发布后检测时间内的评论状态"
        const val REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS_LABEL = "最大检测次数"
        const val REPLY_VISIBILITY_PROBE_INTERVAL_LABEL = "检测间隔"
        const val REPLY_VISIBILITY_PROBE_INTERVAL_UNIT = "ms"
        const val REPLY_VISIBILITY_PROBE_CONFIG_SAVED = "评论检测配置已保存"
        const val REPLY_VISIBILITY_PROBE_CONFIG_INVALID = "评论检测配置无效"
        const val REPLY_VISIBILITY_PROBE_CODE_SEND_FAILED = "send_failed"
        const val REPLY_VISIBILITY_PROBE_CODE_EMPTY_RESPONSE = "empty_response"
        const val REPLY_VISIBILITY_PROBE_CODE_MISSING_RESULT = "missing_result"
        const val REPLY_VISIBILITY_PROBE_CODE_TIMEOUT = "timeout"
        const val REPLY_VISIBILITY_PROBE_SEND_FAILED = "评论发送验证请求发送失败。"
        const val REPLY_VISIBILITY_PROBE_EMPTY_RESPONSE = "评论发送验证响应为空。"
        const val REPLY_VISIBILITY_PROBE_TIMEOUT = "评论发送验证响应超时。"
        const val REPLY_VISIBILITY_PROBE_MISSING_RESULT =
            "无法获取点赞响应 error_code/error_msg。"
        fun replyVisibilityProbeMaxAttemptsDesc(min: Int, max: Int, defaultValue: Int): String =
            "返回成功前最大检测的次数，范围 $min-$max"
        fun replyVisibilityProbeIntervalDesc(min: Int, max: Int, defaultValue: Int): String =
            "每次检测间隔，范围 $min-$max ms"
        const val DISABLE_AUTO_REFRESH_LABEL = "禁止自动刷新"
        const val DISABLE_AUTO_REFRESH_DESC = "避免首页被强制重置刷新列表"
        const val DISABLE_PB_GESTURE_FONT_SCALE_LABEL = "禁用调整字号手势"
        const val FREE_COPY_LABEL = "评论自由复制"
        const val DISABLE_PB_AUTO_EXPAND_LABEL = "吧页面禁止自动展开"
        const val IMAGE_NATIVE_SHARE_LABEL = "图片添加原生分享功能"
        const val BLOCK_UPDATE_DIALOG_LABEL = "屏蔽更新升级弹窗"
        const val COLLECTION_SEARCH_LABEL = "收藏搜索"
        const val HISTORY_SEARCH_LABEL = "浏览历史搜索"
        const val DISABLE_IMAGE_LEFT_SWIPE_FORUM_ENTRY_LABEL = "禁用图片左滑进吧入口"
        const val DEFAULT_NOTIFY_TAB_LABEL = "消息默认通知页"
        const val DEFAULT_NOTIFY_TAB_DESC = "进入消息页时默认显示通知"
        const val CLEAN_SHARE_TRACKING_LABEL = "安全分享"
        const val CLEAN_SHARE_TRACKING_DESC = "移除分享链接中的追踪参数"
        val DEFAULT_ENABLED_FEATURES = listOf(
            HIDE_PB_BOTTOM_BANNER_LABEL,
            DISABLE_PB_GESTURE_FONT_SCALE_LABEL,
            FREE_COPY_LABEL,
            DISABLE_PB_AUTO_EXPAND_LABEL,
            IMAGE_NATIVE_SHARE_LABEL,
            BLOCK_UPDATE_DIALOG_LABEL,
            COLLECTION_SEARCH_LABEL,
            HISTORY_SEARCH_LABEL,
            DISABLE_IMAGE_LEFT_SWIPE_FORUM_ENTRY_LABEL,
            DEFAULT_NOTIFY_TAB_LABEL,
            CLEAN_SHARE_TRACKING_LABEL,
        ).joinToString("\n")
        const val AUTO_SIGN_IN_LABEL = "自动签到"
        const val AUTO_SIGN_IN_DESC = "启动时按官方批量规则自动签到"
        const val PERFORMANCE_OPTIMIZATION_DESC = "优化运行时性能消耗，可能造成功能异常"
        const val PERFORMANCE_OPTIMIZATION_DIALOG_TITLE = "性能优化"
        const val PERFORMANCE_OPTIMIZATION_SAVED = "性能优化配置已保存"
        const val PERFORMANCE_GROUP_HOST_RUNTIME = "运行时"
        const val PERFORMANCE_GROUP_STARTUP = "启动与框架"
        const val PERFORMANCE_GROUP_COMPONENT = "运行期阻断"
        const val FORCE_HOST_PERFORMANCE_FLAGS_LABEL = "启用宿主轻量配置"
        const val FORCE_HOST_PERFORMANCE_FLAGS_DESC = "强制冷启动、Idle、Cookie、头像和吧页聊天等官方性能 AB 走轻量路径"
        const val DISABLE_APSARAS_SCHEDULE_LABEL = "禁用飞天调度"
        const val DISABLE_APSARAS_SCHEDULE_DESC = "关闭 Apsaras 调度 AB，避免 native 调度运行时和任务重排成本"
        const val DISABLE_FLUTTER_PREINIT_LABEL = "禁用 Flutter 预热"
        const val DISABLE_FLUTTER_PREINIT_DESC = "阻止 Flutter NPS 插件在启动或 idle 阶段预初始化，首次进入 Flutter 页面会变慢"
        const val FORCE_LOW_END_DEVICE_CONFIG_LABEL = "强制低端机精简"
        const val FORCE_LOW_END_DEVICE_CONFIG_DESC = "强制低端机优化开关和禁用列表生效，关闭动画、图片预取、Home 数据预取、WebView proxy 等"
        const val BLOCK_TITAN_PATCH_LABEL = "禁用热修复"
        const val BLOCK_TITAN_PATCH_DESC = "阻止热修复框架加载补丁并清除已下载的补丁文件\n*如无异常，保持为关闭状态"
        const val PRIVATE_READ_RECEIPT_INVISIBLE_LABEL = "拦截已读检测"
        const val PRIVATE_READ_RECEIPT_INVISIBLE_DESC = "仅在回复后标记对面信息为已读状态"
        const val DEFAULT_ORIGINAL_IMAGE_LABEL = "默认查看原图"
        const val DEFAULT_ORIGINAL_IMAGE_DESC = "进入图片预览后自动查看原图"
        const val DISABLE_AI_COMPONENTS_LABEL = "禁用 AI 组件"
        const val DISABLE_AI_COMPONENTS_DESC = "屏蔽回复页 AI 写回复、AI 萌图面板和图片查看器 AI 跳转按钮"
        const val DISABLE_AD_SDK_COMPONENTS_LABEL = "阻断广告 SDK 初始化"
        const val DISABLE_AD_SDK_COMPONENTS_DESC = "运行期跳过广告 SDK 初始化入口，不修改宿主组件启用状态"
        const val DISABLE_VIDEO_COMPONENTS_LABEL = "关闭视频预加载"
        const val DISABLE_VIDEO_COMPONENTS_DESC = "运行期关闭信息流视频预加载和预连接，不禁用视频播放组件"
        const val PB_PERFORMANCE_MODE_LABEL = "帖子页性能优化"
        const val PB_PERFORMANCE_MODE_DESC = "启用帖子页轻量分支，关闭滚动日志、图片性能日志和 PB 广告实验"
        const val PB_SCROLL_COALESCE_LABEL = "合并帖子页滚动回调"
        const val PB_SCROLL_COALESCE_DESC = "在评论列表滚动回调中合并过密的刷新请求，降低帖子页滑动时的主线程压力"
        const val DETAILED_LOGGING_LABEL = "输出详细日志"
        const val DETAILED_LOGGING_DESC = "输出详细日志，需重启生效"
        const val FORCE_FEED_UI_OPT_LABEL = "稳定首页新版布局"
        const val FORCE_FEED_UI_OPT_DESC = "强制启用首页新版布局，并运行期跳过部分统计上报入口\n*关闭可能导致其他UI净化功能布局异常"

        const val ACTION_ICON_SETTINGS = "\u2630"
        const val ACTION_ICON_PLAY = "\u25b6"

        const val UNSUPPORTED_SUFFIX = " (当前版本不支持)"
        const val PARTIAL_SUFFIX = "\u26a0"
        const val SCAN_DISABLED_PREFIX = "缺失关键方法："
        const val SCAN_PARTIAL_PREFIX = "缺失："

        const val ABOUT = "关于"
        const val UNKNOWN = "未知"
        const val VERSION = "版本"
        const val TIEBA_OFFICIAL_VERSION = "正式版"
        const val TIEBA_TEST_VERSION = "测试版"
        const val MODULE_RELEASE_VERSION = "release"
        const val MODULE_DEBUG_VERSION = "debug"
        const val AUTHOR = "作者"
        const val AUTHOR_NAME = "aikavvak12una"
        const val RUNTIME_ENVIRONMENT = "runtimeEnvironment"
        const val MODULE_SETTINGS = "设置"
        const val BRAND_TAG = "ForbidAd4TieBa"
        const val SAVE = "保存"
        const val SAVE_AND_RESTART = "保存并重启"

        const val SETTINGS_SAVED = "设置已保存，重启贴吧生效。"
        const val SETTINGS_SAVED_RESTARTING = "设置已保存，正在重启贴吧"
        const val CONTEXT_UNAVAILABLE = "无法获取界面上下文，改为后台扫描"
        const val CLASSLOADER_UNAVAILABLE = "无法获取类加载器"

        const val SCAN_PREPARING = "准备扫描"
        const val SCAN_RUNNING = "正在解析符号"
        const val SCAN_CACHE_VERIFY = "校验缓存"
        const val SCAN_WRITING_CACHE = "写入缓存"
        const val SCAN_CLEARING = "清理模块数据"
        const val BUTTON_OK = "确认"
        const val BUTTON_CANCEL = "取消"
        const val BUTTON_RESTART = "重启"
        const val BUTTON_COPY_SCAN_LOG = "复制日志"
        const val DIALOG_SCAN_TITLE = "反混淆扫描"
        const val DIALOG_SCAN_ACTION_TITLE = "重置"
        const val SCAN_ACTION_RESCAN_ONLY = "重新反混淆扫描"
        const val SCAN_ACTION_CLEAR_DATA_RESTART = "清除模块数据并重启"
        const val CLEAR_MODULE_DATA_STARTED = "正在清除模块所有数据..."
        const val CLEAR_MODULE_DATA_RESTARTING = "已清除"
        const val SCAN_CLEARING_USER_DATA = "正在清理模块用户数据..."
        const val SCAN_RESTART_REQUESTED = "请求重启"
        const val SCAN_COMPLETED = "扫描完成"
        const val SCAN_PARTIALLY_COMPLETED = "部分功能不可用"
        const val SCAN_FAILED = "扫描失败"
        const val SCAN_RESULT_LABEL = "完成"
        const val SCAN_VERSION_LABEL = "贴吧"
        const val SCAN_MODULE_LABEL = "模块"
        const val SCAN_LOG_COPIED = "扫描日志已复制"
        const val INITIAL_SCAN_ENVIRONMENT_WARNING_TITLE = "警告"
        const val INITIAL_SCAN_ENVIRONMENT_WARNING_MESSAGE = "本模块仅供个人学习与技术研究使用，请勿在任何平台二次分发、售卖。\n\n在安装使用本模块前应仔细审查源代码，确保已知模块功能、行为并符合预期，开发者不对使用本模块造成的任何后果承担责任。\n\n点击确认代表您已知悉并承担可能带来的后果。"
        const val RESTRICTED_FEATURE_WARNING_TITLE = "注意"
        const val RESTRICTED_FEATURE_WARNING_MESSAGE = "使用隐藏功能可能导致应用出现卡顿、崩溃、账号被封禁等异常，点击确认代表您已知悉并承担可能带来的后果"
        const val RESTRICTED_FEATURE_CONFIRM = "确认"
        const val RESTRICTED_FEATURE_UNLOCKED = "已显示被隐藏的功能"
        const val RESTRICTED_FEATURE_UNSUPPORTED_ENVIRONMENT = "当前环境不支持"


        const val HOME_TOP_TAB_DIALOG_TITLE = SIMPLIFY_HOME_TAB_LABEL
        const val HOME_TOP_TAB_MATERIAL_LABEL = "有料"
        const val HOME_TOP_TAB_RECOMMEND_LABEL = "推荐"
        const val HOME_TOP_TAB_LIVE_LABEL = "直播"
        const val HOME_TOP_TAB_FOLLOWED_LABEL = "关注"
        const val HOME_TOP_TAB_AT_LEAST_ONE = "至少保留一个首页顶部 Tab"
        const val HOME_TOP_TAB_SAVED = "首页顶部 Tab 配置已保存"
        const val BOTTOM_TAB_DIALOG_TITLE = SIMPLIFY_BOTTOM_TAB_LABEL
        const val BOTTOM_TAB_HOME_LABEL = "首页"
        const val BOTTOM_TAB_ENTER_FORUM_LABEL = "进吧"
        const val BOTTOM_TAB_RETAIL_STORE_LABEL = "小卖部"
        const val BOTTOM_TAB_MESSAGE_LABEL = "消息"
        const val BOTTOM_TAB_MINE_LABEL = "我的"
        const val BOTTOM_TAB_AT_LEAST_ONE = "至少保留一个底部 Tab"
        const val BOTTOM_TAB_SAVED = "底部 Tab 配置已保存"


        const val RESTART_HINT = "（重启生效）"
        const val RESTART_TIEBA_HINT = "（重启贴吧生效）"

        fun withRestartHint(message: String): String = "$message$RESTART_HINT"
        fun withRestartTiebaHint(message: String): String = "$message$RESTART_TIEBA_HINT"
        fun withUnsupportedSuffix(label: String): String = "$label$UNSUPPORTED_SUFFIX"
        fun withPartialSuffix(label: String): String = "$label$PARTIAL_SUFFIX"
        fun restrictedFeatureConfirmWaiting(seconds: Int): String = "$RESTRICTED_FEATURE_CONFIRM (${seconds}s)"
        fun initialScanEnvironmentWarningConfirmWaiting(seconds: Int): String = "$BUTTON_OK (${seconds}s)"
        fun scanUserDataCleared(count: Int): String = "模块用户数据清理完成：$count 项"
        fun scanUserDataClearFailed(count: Int): String = "模块用户数据清理失败：$count 项"
        fun clearModuleDataPartialFailed(count: Int): String = "模块数据清理存在失败项：$count 项，正在重启贴吧"
        fun scanActivity(name: String): String = "界面=$name"
        fun scanProcess(packageName: String): String = "进程包名=$packageName"
        fun scanClassLoader(classLoader: String): String = "类加载器=$classLoader"
        fun scanResultSource(source: String): String = "结果来源=$source"
        fun scanException(message: String): String = "异常：$message"
        fun aboutVersionSummary(
            tiebaBuildType: String,
            tiebaVersion: String,
            moduleBuildType: String,
            moduleVersion: String,
        ): String = "$tiebaVersion$tiebaBuildType   $moduleVersion$moduleBuildType"
        fun scanVersionSummary(
            tiebaBuildType: String,
            tiebaVersion: String,
            moduleBuildType: String,
            moduleVersion: String,
        ): String = "$SCAN_VERSION_LABEL $tiebaVersion$tiebaBuildType\n$SCAN_MODULE_LABEL $moduleVersion$moduleBuildType"
        fun scanTiebaVersionNotAdapted(targetVersionName: String): String =
            "当前贴吧版本未适配，部分功能可能异常，建议切换到${targetVersionName}"
        fun scanFeatureAbnormal(targetVersionName: String): String =
            "部分功能异常，建议切换到贴吧${targetVersionName}"
        fun modelScoreThresholdInvalid(model: String): String = "$model 阈值无效"
        fun modelScoreAutoPercentileLabel(percentile: Int): String = "P$percentile"
        fun modelScoreAutoPercentileEnabled(percentile: Int, value: String): String = "已启用 P$percentile 自动应用：$value"
        fun modelScoreAutoPercentilePending(percentile: Int, sampleCount: Int, minSampleCount: Int): String =
            "已启用 P$percentile 自动应用，样本数量 $sampleCount，大于 $minSampleCount 后生效"
        fun modelScoreAutoPercentileDisabled(percentile: Int): String = "已关闭 P$percentile 自动应用"
        fun modelScoreBucketInfo(
            start: String,
            end: String,
            count: Int,
            cumulativeCount: Int,
            cumulativePercent: String,
        ): String = "区间 $start-$end，数量 $count，低于该值 $cumulativeCount 条/$cumulativePercent"
    }

    object SymbolResolverToast {
        const val SCANNING = "正在扫描符号..."
        const val SCAN_DONE = "符号扫描完成"
        const val CLASSLOADER_UNAVAILABLE = "类加载器不可用"
        const val MANUAL_SCAN_START = "手动扫描开始"
        const val MANUAL_SCAN_CLEAR_FAILED = "清理模块数据失败，已取消扫描"
        const val MANUAL_SCAN_DONE = "手动扫描完成，重启贴吧生效"
    }

    object PrivateReadReceipt {
        const val TOAST_REPORT_INTERCEPTED = "已拦截"
        const val TOAST_STATE_SYNCED = "已同步状态"
    }

    object HomeTabAutoHide {
        const val TOAST_LOCKED_HIDDEN = "Tab 已锁定隐藏，返回键取消"
    }

    object ImageViewerNativeShare {
        const val BUTTON_LABEL = "分享"
        const val CHOOSER_TITLE = "分享"
        const val START_FAILED = "系统分享拉起失败"
    }


    object CollectionSearch {
        const val SYNC_ACTION_FOOTER = "重新同步收藏缓存"
        const val DIALOG_TITLE = "收藏搜索"
        const val DIALOG_HINT = "输入关键词（标题/作者/吧名）"
        const val DIALOG_ACTION_SEARCH = "搜索"
        const val DIALOG_ACTION_SYNC = "同步缓存"
        const val INPUT_CLEAR_SYMBOL = "\u2715"
        const val BUTTON_CONTENT_DESC = "tb_collection_search"

        const val TOAST_SYNC_IN_PROGRESS = "正在同步缓存，请稍后"
        const val TOAST_SYNC_PARTIAL = "全量同步未完成，已保留部分数据"
        const val TOAST_SYNC_FAILED = "全量同步失败，将使用已有缓存"
        const val TOAST_EXIT_EDIT_FIRST = "请先退出编辑模式"
        const val TOAST_PAGE_NOT_READY = "收藏页尚未就绪"
        const val TOAST_NO_CACHE = "未找到全量缓存，正在构建"
        const val TOAST_CACHE_SYNCING = "正在同步全量缓存同步"
        const val TOAST_NO_ITEMS = "暂无可搜索内容"
        const val TOAST_FILTER_CLEARED = "已清除搜索条件"

        fun toastLoadedAllFavorites(size: Int): String = "已同步全部收藏：$size 条"
        fun toastMatched(matched: Int, total: Int): String = "匹配 $matched/$total"
    }

    object HistorySearch {
        const val DIALOG_TITLE = "浏览历史搜索"
        const val DIALOG_HINT = "输入关键词（标题/作者/吧名）"
        const val DIALOG_ACTION_SEARCH = "搜索"
        const val INPUT_CLEAR_SYMBOL = "\u2715"
        const val BUTTON_CONTENT_DESC = "tb_history_search"
        const val TOAST_FILTER_CLEARED = "已清除搜索条件"

        fun toastMatched(matched: Int, total: Int): String = "匹配 $matched/$total"
    }

    object AutoSignIn {
        const val TOAST_TASK_RUNNING = "自动签到任务正在进行中，请稍后再试"
        const val TOAST_BDUSS_MISSING = "自动签到失败：未找到账户凭证(BDUSS)，请先登录贴吧"
        const val TOAST_TBS_MISSING = "自动签到失败：无法获取 tbs 参数"
        const val TOAST_SIGN_PEAK_PAUSED = "签到高峰期，已暂停"

        fun toastAlreadyAllSigned(totalLiked: Int): String =
            "当前 $totalLiked 个贴吧已全部签到"

        fun toastStart(total: Int): String =
            "自动签到开始：共有 $total 个吧待签到"

        fun toastBatchDone(signedCount: Int, leftCount: Int): String =
            "批量签到${signedCount}，剩余${leftCount}"

        fun toastAllSigned(total: Int): String = "自动签到成功：已签到 $total 个吧"
        fun toastPartialDone(signedCount: Int, leftCount: Int): String =
            "已签到 $signedCount 个吧，剩余 $leftCount 个吧未签到"

        fun toastError(message: String?): String = "自动签到异常：${message.orEmpty()}"
    }
}
