package com.forbidad4tieba.hook

import android.content.Context
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.ui.UiText
import dalvik.system.DexFile
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.concurrent.thread

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
}

data class HookFeatureStatus(
    val state: String = HookFeatureState.DISABLED,
    val missingCritical: List<String> = emptyList(),
    val missingOptional: List<String> = emptyList(),
) {
    fun isSupported(): Boolean = state != HookFeatureState.DISABLED
    fun isPartial(): Boolean = state == HookFeatureState.PARTIAL
}

data class HookSymbols(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val homeTabItemTypeField: String? = null,
    val homeTabItemCodeField: String? = null,
    val homeTabItemNameField: String? = null,
    val homeTabItemUrlField: String? = null,
    val homeTabItemMainSetterMethod: String? = null,
    val homeTabItemMainIntField: String? = null,
    val homeTabItemMainBooleanField: String? = null,
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
    val feedTemplateKeyMethod: String? = null,
    val feedTemplatePayloadMethod: String? = null,
    val feedTemplateLoadMoreMethod: String? = null,
    val splashAdHelperClass: String? = null,
    val splashAdHelperMethod: String? = null,
    val closeAdDataClass: String? = null,
    val closeAdDataMethodG1: String? = null,
    val closeAdDataMethodJ1: String? = null,

    val zgaClass: String? = null,
    val zgaMethods: List<String>? = null,
    val searchBoxViewClass: String? = null,
    val searchBoxSetHintMethod: String? = null,
    val homeSearchBoxOwnerClass: String? = null,
    val homeSearchBoxInitMethod: String? = null,
    val homeSearchBoxGetterMethod: String? = null,
    val homePersonalizeAnchorClasses: List<String>? = null,
    val homeRightSlotClass: String? = null,
    val homeRightSlotStateMethods: List<String>? = null,
    val pbFallingViewClass: String? = null,
    val pbFallingInitMethod: String? = null,
    val pbFallingShowMethod: String? = null,
    val pbFallingClearMethod: String? = null,
    val pbEarlyAdInsertClass: String? = null,
    val pbEarlyAdInsertMethodSpecs: List<String>? = null,
    val pbAdBidCommonRequestModelClass: String? = null,
    val pbAdBidCommonRequestStartMethods: List<String>? = null,
    val pbAdBidCommonRequestNotifyMethod: String? = null,
    val pbAdBidPageBrowserRequestModelClass: String? = null,
    val pbAdBidPageBrowserRequestDataMethod: String? = null,
    val typeAdapterSetDataMethod: String? = null,
    val typeAdapterDataItemClass: String? = null,
    val typeAdapterDataGetTypeMethod: String? = null,
    val enterForumWebControllerClass: String? = null,
    val enterForumWebLoadMethod: String? = null,
    val enterForumInitInfoDataClass: String? = null,
    val enterForumInitInfoGetUrlMethod: String? = null,
    val plainUrlClickableSpanClass: String? = null,
    val plainUrlClickableSpanOnClickMethod: String? = null,
    val plainUrlClickableSpanOnClickOwnerClasses: List<String>? = null,
    val plainUrlClickableSpanTypeField: String? = null,
    val plainUrlClickableSpanUrlField: String? = null,
    val plainUrlClickableSpanTextField: String? = null,
    val plainUrlMessageManagerClass: String? = null,
    val plainUrlMessageDispatchMethod: String? = null,
    val plainUrlResponsedMessageClass: String? = null,
    val plainUrlResponsedMessageGetCmdMethod: String? = null,
    val plainUrlCustomResponsedMessageClass: String? = null,
    val plainUrlCustomResponsedMessageGetDataMethod: String? = null,
    val plainUrlApplicationClass: String? = null,
    val plainUrlApplicationGetInstMethod: String? = null,
    val privateReadReceiptModelClass: String? = null,
    val privateReadReceiptModelReadDispatchMethod: String? = null,
    val privateReadReceiptMessageManagerClass: String? = null,
    val privateReadReceiptMessageManagerGetInstanceMethod: String? = null,
    val privateReadReceiptMessageSendMethod: String? = null,
    val privateReadReceiptMessageBaseClass: String? = null,
    val privateReadReceiptRequestClass: String? = null,
    val privateReadReceiptModelBaseClass: String? = null,
    val privateReadReceiptCommitResponseClass: String? = null,
    val privateReadReceiptProcessAckMethod: String? = null,
    val privateReadReceiptResponseErrorMethod: String? = null,
    val privateReadReceiptRequestMsgIdField: String? = null,
    val privateReadReceiptRequestToUidField: String? = null,
    val privateReadReceiptModelDataField: String? = null,
    val privateReadReceiptPageDataClass: String? = null,
    val privateReadReceiptPageDataChatListMethod: String? = null,
    val privateReadReceiptChatMessageClass: String? = null,
    val privateReadReceiptChatMessageMsgIdMethod: String? = null,
    val privateReadReceiptChatMessageUserIdMethod: String? = null,
    val privateReadReceiptChatMessageLocalDataMethod: String? = null,
    val privateReadReceiptLocalDataClass: String? = null,
    val privateReadReceiptLocalDataStatusMethod: String? = null,
    val privateReadReceiptAccountClass: String? = null,
    val privateReadReceiptCurrentAccountMethod: String? = null,
    val mountCardLinkLayoutClass: String? = null,
    val mountCardLinkLayoutOnClickMethod: String? = null,
    val mountCardLinkLayoutDataField: String? = null,
    val mountCardLinkInfoDataClass: String? = null,
    val mountCardLinkInfoGetUrlMethod: String? = null,
    val forumBottomSheetViewClass: String? = null,
    val forumBottomSheetInitScrollMethod: String? = null,
    val autoRefreshTriggerMethod: String? = null,
    val autoLoadMoreConfigClass: String? = null,
    val autoLoadMoreConfigMethod: String? = null,
    val pbCommentScrollListenerClass: String? = null,
    val pbCommentScrollMethod: String? = null,
    val pbCommentScrollFragmentField: String? = null,
    val pbCommentScrollBottomListenerField: String? = null,
    val pbCommentScrollBottomMethod: String? = null,
    val pbCommentBottomListScrollClass: String? = null,
    val pbCommentBottomListScrollMethod: String? = null,
    val pbCommentBottomListOwnerField: String? = null,
    val pbCommentBottomRecyclerScrollClass: String? = null,
    val pbCommentBottomRecyclerScrollMethod: String? = null,
    val pbCommentBottomRecyclerOwnerField: String? = null,
    val pbGestureScaleManagerClass: String? = null,
    val pbGestureScaleDispatchMethod: String? = null,
    val pbGestureScaleListenerSetterMethod: String? = null,
    val pbGestureScaleListenerClass: String? = null,
    val pbGestureScaleListenerOnScaleMethod: String? = null,

    val collectionPresenterField: String? = null,
    val collectionPresenterListSetterMethod: String? = null,
    val collectionPresenterAdapterField: String? = null,
    val collectionModelField: String? = null,
    val collectionModelListGetterMethod: String? = null,
    val collectionModelParseMethod: String? = null,
    val collectionModelListField: String? = null,
    val collectionFragmentDisplayListField: String? = null,
    val collectionActivityNavControllerField: String? = null,
    val collectionNavBarField: String? = null,
    val collectionAdapterShowFooterMethod: String? = null,
    val collectionAdapterLoadingMethod: String? = null,
    val collectionAdapterHasMoreMethod: String? = null,
    val collectionEditModeMethod: String? = null,

    val historyAdapterField: String? = null,
    val historyAdapterSetListMethod: String? = null,
    val historyListField: String? = null,
    val historyActivityListUpdateMethod: String? = null,
    val historyActivityNavBarField: String? = null,
    val historyThreadNameMethod: String? = null,
    val historyForumNameMethod: String? = null,
    val historyUserNameMethod: String? = null,
    val historyDescriptionMethod: String? = null,
    val historyThreadIdMethod: String? = null,
    val historyPostIdMethod: String? = null,
    val historyLiveIdMethod: String? = null,

    val msgTabLocateToTabMethod: String? = null,
    val msgTabContainerSelectMethod: String? = null,
    val msgTabContainerExtDataField: String? = null,
    val freeCopyPopupMenuClass: String? = null,
    val freeCopyPopupContentViewMethod: String? = null,
    val freeCopyPopupTextField: String? = null,

    val mainTabDataClass: String? = null,
    val mainTabAddMethod: String? = null,
    val mainTabGetListMethod: String? = null,
    val mainTabDelegateGetStructureMethod: String? = null,
    val mainTabStructureTypeField: String? = null,
    val mainTabStructureDynamicIconField: String? = null,
    val mainTabStructureFragmentField: String? = null,

    val origImagePagerAdapterClass: String? = null,
    val origImageUrlDragImageViewClass: String? = null,
    val origImageDataClass: String? = null,
    val origImageSetPrimaryItemMethod: String? = null,
    val origImageSetAssistUrlMethod: String? = null,
    val origImageAssistDataMethod: String? = null,
    val origImageOriginTextMethod: String? = null,
    val origImageShowButtonField: String? = null,
    val origImageBlockedField: String? = null,
    val origImageOriginalProcessField: String? = null,
    val origImageOriginalUrlField: String? = null,
    val origImageSharedPrefHelperClass: String? = null,
    val origImageSharedPrefGetInstanceMethod: String? = null,
    val origImageSharedPrefPutBooleanMethod: String? = null,
    val origImageMd5Class: String? = null,
    val origImageMd5Method: String? = null,
    val origImagePrimaryReadyMethod: String? = null,
    val origImageTriggerMethod: String? = null,
    val origImageDirectStartMethod: String? = null,
    val shareTrackBuilderClass: String? = null,
    val shareTrackBuildUrlMethod: String? = null,
    val shareTrackAppendQueryMethod: String? = null,
    val imageViewerShareConfigClass: String? = null,
    val imageViewerShareIsDialogField: String? = null,
    val imageViewerShareItemField: String? = null,
    val imageViewerShareAddOutsideMethod: String? = null,
    val imageViewerShareGetRequestDataMethod: String? = null,
    val imageViewerShareSetRequestDataMethod: String? = null,
    val imageViewerShareGetContextMethod: String? = null,
    val imageViewerShareItemClass: String? = null,
    val imageViewerShareItemTitleField: String? = null,
    val imageViewerShareItemContentField: String? = null,
    val imageViewerShareItemLinkUrlField: String? = null,
    val imageViewerShareItemImageUriField: String? = null,
    val imageViewerShareItemImageUrlField: String? = null,
    val imageViewerShareItemLocalFileField: String? = null,
    val imageViewerShareItemViewClass: String? = null,
    val imageViewerShareItemNameByResMethod: String? = null,
    val imageViewerShareItemNameByTextMethod: String? = null,
    val imageViewerShareIconResId: Int? = null,
    val homeNativeGlassSubPbNextPageMoreViewId: Int? = null,
    val homeNativeGlassPbReplyTitleDividerViewId: Int? = null,
    val homeNativeGlassDynamicBackgroundColorIds: List<Int> = emptyList(),
    val homeNativeGlassSortSwitchBackgroundPaintField: String? = null,
    val homeNativeGlassSortSwitchSlideDrawMethod: String? = null,
    val homeNativeGlassSortSwitchSlidePathField: String? = null,
    val homeNativeGlassEnterForumCapsuleControllerClass: String? = null,
    val homeNativeGlassEnterForumCapsuleInitMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null,
    val homeNativeGlassEnterForumCapsuleViewField: String? = null,
    val homeNativeGlassEnterForumCapsuleTitleField: String? = null,
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null,

    val feedCardBindMethod: String? = null,
    val feedCardDataListField: String? = null,
    val feedHeadParamsField: String? = null,
    val feedRecommendCardNestedDataMethod: String? = null,
    val feedRecommendCardNestedDataListField: String? = null,
    val aiSpriteMemePanControllerClass: String? = null,
    val aiSpriteMemeEnableMethod: String? = null,
    val aiPbNewInputContainerClass: String? = null,
    val aiPbNewInputContainerInitSpriteMemeMethod: String? = null,
    val aiPbNewInputContainerInitAiWriteMethod: String? = null,
    val aiPbAiEmojiCreationViewBindMethod: String? = null,
    val aiPbPageBrowserAiEmojiCreationBindMethod: String? = null,
    val aiImageViewerJumpButtonOwnerClass: String? = null,
    val aiImageViewerJumpButtonInitMethod: String? = null,

    val featureStatusMap: Map<String, HookFeatureStatus> = emptyMap(),
    val scanSupportState: String = ScanSupportState.UNKNOWN,
    val scanTargetVersionCode: Long? = null,
    val scanTargetVersionName: String? = null,
    val scanTargetVersionType: String? = null,
    val scanErrors: List<String> = emptyList(),

    val source: String = "unsupported",
    val createdAt: Long = 0L,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("homeTabClass", homeTabClass)
            put("homeTabRebuildMethod", homeTabRebuildMethod)
            put("homeTabListField", homeTabListField)
            put("homeTabItemTypeField", homeTabItemTypeField)
            put("homeTabItemCodeField", homeTabItemCodeField)
            put("homeTabItemNameField", homeTabItemNameField)
            put("homeTabItemUrlField", homeTabItemUrlField)
            put("homeTabItemMainSetterMethod", homeTabItemMainSetterMethod)
            put("homeTabItemMainIntField", homeTabItemMainIntField)
            put("homeTabItemMainBooleanField", homeTabItemMainBooleanField)
            put("settingsClass", settingsClass)
            put("settingsInitMethod", settingsInitMethod)
            put("settingsContainerField", settingsContainerField)
            put("feedTemplateKeyMethod", feedTemplateKeyMethod)
            put("feedTemplatePayloadMethod", feedTemplatePayloadMethod)
            put("feedTemplateLoadMoreMethod", feedTemplateLoadMoreMethod)
            put("splashAdHelperClass", splashAdHelperClass)
            put("splashAdHelperMethod", splashAdHelperMethod)
            put("closeAdDataClass", closeAdDataClass)
            put("closeAdDataMethodG1", closeAdDataMethodG1)
            put("closeAdDataMethodJ1", closeAdDataMethodJ1)

            put("zgaClass", zgaClass)
            if (zgaMethods != null) {
                val array = org.json.JSONArray()
                zgaMethods.forEach { array.put(it) }
                put("zgaMethods", array)
            }
            put("searchBoxViewClass", searchBoxViewClass)
            put("searchBoxSetHintMethod", searchBoxSetHintMethod)
            put("homeSearchBoxOwnerClass", homeSearchBoxOwnerClass)
            put("homeSearchBoxInitMethod", homeSearchBoxInitMethod)
            put("homeSearchBoxGetterMethod", homeSearchBoxGetterMethod)
            if (homePersonalizeAnchorClasses != null) {
                val array = org.json.JSONArray()
                homePersonalizeAnchorClasses.forEach { array.put(it) }
                put("homePersonalizeAnchorClasses", array)
            }
            put("homeRightSlotClass", homeRightSlotClass)
            if (homeRightSlotStateMethods != null) {
                val array = org.json.JSONArray()
                homeRightSlotStateMethods.forEach { array.put(it) }
                put("homeRightSlotStateMethods", array)
            }
            put("pbFallingViewClass", pbFallingViewClass)
            put("pbFallingInitMethod", pbFallingInitMethod)
            put("pbFallingShowMethod", pbFallingShowMethod)
            put("pbFallingClearMethod", pbFallingClearMethod)
            put("pbEarlyAdInsertClass", pbEarlyAdInsertClass)
            if (pbEarlyAdInsertMethodSpecs != null) {
                val array = org.json.JSONArray()
                pbEarlyAdInsertMethodSpecs.forEach { array.put(it) }
                put("pbEarlyAdInsertMethodSpecs", array)
            }
            put("pbAdBidCommonRequestModelClass", pbAdBidCommonRequestModelClass)
            if (pbAdBidCommonRequestStartMethods != null) {
                val array = org.json.JSONArray()
                pbAdBidCommonRequestStartMethods.forEach { array.put(it) }
                put("pbAdBidCommonRequestStartMethods", array)
            }
            put("pbAdBidCommonRequestNotifyMethod", pbAdBidCommonRequestNotifyMethod)
            put("pbAdBidPageBrowserRequestModelClass", pbAdBidPageBrowserRequestModelClass)
            put("pbAdBidPageBrowserRequestDataMethod", pbAdBidPageBrowserRequestDataMethod)
            put("typeAdapterSetDataMethod", typeAdapterSetDataMethod)
            put("typeAdapterDataItemClass", typeAdapterDataItemClass)
            put("typeAdapterDataGetTypeMethod", typeAdapterDataGetTypeMethod)
            put("enterForumWebControllerClass", enterForumWebControllerClass)
            put("enterForumWebLoadMethod", enterForumWebLoadMethod)
            put("enterForumInitInfoDataClass", enterForumInitInfoDataClass)
            put("enterForumInitInfoGetUrlMethod", enterForumInitInfoGetUrlMethod)
            put("plainUrlClickableSpanClass", plainUrlClickableSpanClass)
            put("plainUrlClickableSpanOnClickMethod", plainUrlClickableSpanOnClickMethod)
            if (plainUrlClickableSpanOnClickOwnerClasses != null) {
                val array = org.json.JSONArray()
                plainUrlClickableSpanOnClickOwnerClasses.forEach { array.put(it) }
                put("plainUrlClickableSpanOnClickOwnerClasses", array)
            }
            put("plainUrlClickableSpanTypeField", plainUrlClickableSpanTypeField)
            put("plainUrlClickableSpanUrlField", plainUrlClickableSpanUrlField)
            put("plainUrlClickableSpanTextField", plainUrlClickableSpanTextField)
            put("plainUrlMessageManagerClass", plainUrlMessageManagerClass)
            put("plainUrlMessageDispatchMethod", plainUrlMessageDispatchMethod)
            put("plainUrlResponsedMessageClass", plainUrlResponsedMessageClass)
            put("plainUrlResponsedMessageGetCmdMethod", plainUrlResponsedMessageGetCmdMethod)
            put("plainUrlCustomResponsedMessageClass", plainUrlCustomResponsedMessageClass)
            put("plainUrlCustomResponsedMessageGetDataMethod", plainUrlCustomResponsedMessageGetDataMethod)
            put("plainUrlApplicationClass", plainUrlApplicationClass)
            put("plainUrlApplicationGetInstMethod", plainUrlApplicationGetInstMethod)
            put("privateReadReceiptModelClass", privateReadReceiptModelClass)
            put("privateReadReceiptModelReadDispatchMethod", privateReadReceiptModelReadDispatchMethod)
            put("privateReadReceiptMessageManagerClass", privateReadReceiptMessageManagerClass)
            put("privateReadReceiptMessageManagerGetInstanceMethod", privateReadReceiptMessageManagerGetInstanceMethod)
            put("privateReadReceiptMessageSendMethod", privateReadReceiptMessageSendMethod)
            put("privateReadReceiptMessageBaseClass", privateReadReceiptMessageBaseClass)
            put("privateReadReceiptRequestClass", privateReadReceiptRequestClass)
            put("privateReadReceiptModelBaseClass", privateReadReceiptModelBaseClass)
            put("privateReadReceiptCommitResponseClass", privateReadReceiptCommitResponseClass)
            put("privateReadReceiptProcessAckMethod", privateReadReceiptProcessAckMethod)
            put("privateReadReceiptResponseErrorMethod", privateReadReceiptResponseErrorMethod)
            put("privateReadReceiptRequestMsgIdField", privateReadReceiptRequestMsgIdField)
            put("privateReadReceiptRequestToUidField", privateReadReceiptRequestToUidField)
            put("privateReadReceiptModelDataField", privateReadReceiptModelDataField)
            put("privateReadReceiptPageDataClass", privateReadReceiptPageDataClass)
            put("privateReadReceiptPageDataChatListMethod", privateReadReceiptPageDataChatListMethod)
            put("privateReadReceiptChatMessageClass", privateReadReceiptChatMessageClass)
            put("privateReadReceiptChatMessageMsgIdMethod", privateReadReceiptChatMessageMsgIdMethod)
            put("privateReadReceiptChatMessageUserIdMethod", privateReadReceiptChatMessageUserIdMethod)
            put("privateReadReceiptChatMessageLocalDataMethod", privateReadReceiptChatMessageLocalDataMethod)
            put("privateReadReceiptLocalDataClass", privateReadReceiptLocalDataClass)
            put("privateReadReceiptLocalDataStatusMethod", privateReadReceiptLocalDataStatusMethod)
            put("privateReadReceiptAccountClass", privateReadReceiptAccountClass)
            put("privateReadReceiptCurrentAccountMethod", privateReadReceiptCurrentAccountMethod)
            put("mountCardLinkLayoutClass", mountCardLinkLayoutClass)
            put("mountCardLinkLayoutOnClickMethod", mountCardLinkLayoutOnClickMethod)
            put("mountCardLinkLayoutDataField", mountCardLinkLayoutDataField)
            put("mountCardLinkInfoDataClass", mountCardLinkInfoDataClass)
            put("mountCardLinkInfoGetUrlMethod", mountCardLinkInfoGetUrlMethod)
            put("forumBottomSheetViewClass", forumBottomSheetViewClass)
            put("forumBottomSheetInitScrollMethod", forumBottomSheetInitScrollMethod)
            put("autoRefreshTriggerMethod", autoRefreshTriggerMethod)
            put("autoLoadMoreConfigClass", autoLoadMoreConfigClass)
            put("autoLoadMoreConfigMethod", autoLoadMoreConfigMethod)
            put("pbCommentScrollListenerClass", pbCommentScrollListenerClass)
            put("pbCommentScrollMethod", pbCommentScrollMethod)
            put("pbCommentScrollFragmentField", pbCommentScrollFragmentField)
            put("pbCommentScrollBottomListenerField", pbCommentScrollBottomListenerField)
            put("pbCommentScrollBottomMethod", pbCommentScrollBottomMethod)
            put("pbCommentBottomListScrollClass", pbCommentBottomListScrollClass)
            put("pbCommentBottomListScrollMethod", pbCommentBottomListScrollMethod)
            put("pbCommentBottomListOwnerField", pbCommentBottomListOwnerField)
            put("pbCommentBottomRecyclerScrollClass", pbCommentBottomRecyclerScrollClass)
            put("pbCommentBottomRecyclerScrollMethod", pbCommentBottomRecyclerScrollMethod)
            put("pbCommentBottomRecyclerOwnerField", pbCommentBottomRecyclerOwnerField)
            put("pbGestureScaleManagerClass", pbGestureScaleManagerClass)
            put("pbGestureScaleDispatchMethod", pbGestureScaleDispatchMethod)
            put("pbGestureScaleListenerSetterMethod", pbGestureScaleListenerSetterMethod)
            put("pbGestureScaleListenerClass", pbGestureScaleListenerClass)
            put("pbGestureScaleListenerOnScaleMethod", pbGestureScaleListenerOnScaleMethod)

            put("collectionPresenterField", collectionPresenterField)
            put("collectionPresenterListSetterMethod", collectionPresenterListSetterMethod)
            put("collectionPresenterAdapterField", collectionPresenterAdapterField)
            put("collectionModelField", collectionModelField)
            put("collectionModelListGetterMethod", collectionModelListGetterMethod)
            put("collectionModelParseMethod", collectionModelParseMethod)
            put("collectionModelListField", collectionModelListField)
            put("collectionFragmentDisplayListField", collectionFragmentDisplayListField)
            put("collectionActivityNavControllerField", collectionActivityNavControllerField)
            put("collectionNavBarField", collectionNavBarField)
            put("collectionAdapterShowFooterMethod", collectionAdapterShowFooterMethod)
            put("collectionAdapterLoadingMethod", collectionAdapterLoadingMethod)
            put("collectionAdapterHasMoreMethod", collectionAdapterHasMoreMethod)
            put("collectionEditModeMethod", collectionEditModeMethod)

            put("historyAdapterField", historyAdapterField)
            put("historyAdapterSetListMethod", historyAdapterSetListMethod)
            put("historyListField", historyListField)
            put("historyActivityListUpdateMethod", historyActivityListUpdateMethod)
            put("historyActivityNavBarField", historyActivityNavBarField)
            put("historyThreadNameMethod", historyThreadNameMethod)
            put("historyForumNameMethod", historyForumNameMethod)
            put("historyUserNameMethod", historyUserNameMethod)
            put("historyDescriptionMethod", historyDescriptionMethod)
            put("historyThreadIdMethod", historyThreadIdMethod)
            put("historyPostIdMethod", historyPostIdMethod)
            put("historyLiveIdMethod", historyLiveIdMethod)

            put("msgTabLocateToTabMethod", msgTabLocateToTabMethod)
            put("msgTabContainerSelectMethod", msgTabContainerSelectMethod)
            put("msgTabContainerExtDataField", msgTabContainerExtDataField)
            put("freeCopyPopupMenuClass", freeCopyPopupMenuClass)
            put("freeCopyPopupContentViewMethod", freeCopyPopupContentViewMethod)
            put("freeCopyPopupTextField", freeCopyPopupTextField)

            put("mainTabDataClass", mainTabDataClass)
            put("mainTabAddMethod", mainTabAddMethod)
            put("mainTabGetListMethod", mainTabGetListMethod)
            put("mainTabDelegateGetStructureMethod", mainTabDelegateGetStructureMethod)
            put("mainTabStructureTypeField", mainTabStructureTypeField)
            put("mainTabStructureDynamicIconField", mainTabStructureDynamicIconField)
            put("mainTabStructureFragmentField", mainTabStructureFragmentField)

            put("origImagePagerAdapterClass", origImagePagerAdapterClass)
            put("origImageUrlDragImageViewClass", origImageUrlDragImageViewClass)
            put("origImageDataClass", origImageDataClass)
            put("origImageSetPrimaryItemMethod", origImageSetPrimaryItemMethod)
            put("origImageSetAssistUrlMethod", origImageSetAssistUrlMethod)
            put("origImageAssistDataMethod", origImageAssistDataMethod)
            put("origImageOriginTextMethod", origImageOriginTextMethod)
            put("origImageShowButtonField", origImageShowButtonField)
            put("origImageBlockedField", origImageBlockedField)
            put("origImageOriginalProcessField", origImageOriginalProcessField)
            put("origImageOriginalUrlField", origImageOriginalUrlField)
            put("origImageSharedPrefHelperClass", origImageSharedPrefHelperClass)
            put("origImageSharedPrefGetInstanceMethod", origImageSharedPrefGetInstanceMethod)
            put("origImageSharedPrefPutBooleanMethod", origImageSharedPrefPutBooleanMethod)
            put("origImageMd5Class", origImageMd5Class)
            put("origImageMd5Method", origImageMd5Method)
            put("origImagePrimaryReadyMethod", origImagePrimaryReadyMethod)
            put("origImageTriggerMethod", origImageTriggerMethod)
            put("origImageDirectStartMethod", origImageDirectStartMethod)
            put("shareTrackBuilderClass", shareTrackBuilderClass)
            put("shareTrackBuildUrlMethod", shareTrackBuildUrlMethod)
            put("shareTrackAppendQueryMethod", shareTrackAppendQueryMethod)
            put("imageViewerShareConfigClass", imageViewerShareConfigClass)
            put("imageViewerShareIsDialogField", imageViewerShareIsDialogField)
            put("imageViewerShareItemField", imageViewerShareItemField)
            put("imageViewerShareAddOutsideMethod", imageViewerShareAddOutsideMethod)
            put("imageViewerShareGetRequestDataMethod", imageViewerShareGetRequestDataMethod)
            put("imageViewerShareSetRequestDataMethod", imageViewerShareSetRequestDataMethod)
            put("imageViewerShareGetContextMethod", imageViewerShareGetContextMethod)
            put("imageViewerShareItemClass", imageViewerShareItemClass)
            put("imageViewerShareItemTitleField", imageViewerShareItemTitleField)
            put("imageViewerShareItemContentField", imageViewerShareItemContentField)
            put("imageViewerShareItemLinkUrlField", imageViewerShareItemLinkUrlField)
            put("imageViewerShareItemImageUriField", imageViewerShareItemImageUriField)
            put("imageViewerShareItemImageUrlField", imageViewerShareItemImageUrlField)
            put("imageViewerShareItemLocalFileField", imageViewerShareItemLocalFileField)
            put("imageViewerShareItemViewClass", imageViewerShareItemViewClass)
            put("imageViewerShareItemNameByResMethod", imageViewerShareItemNameByResMethod)
            put("imageViewerShareItemNameByTextMethod", imageViewerShareItemNameByTextMethod)
            put("imageViewerShareIconResId", imageViewerShareIconResId)
            put("homeNativeGlassSubPbNextPageMoreViewId", homeNativeGlassSubPbNextPageMoreViewId)
            put("homeNativeGlassPbReplyTitleDividerViewId", homeNativeGlassPbReplyTitleDividerViewId)
            if (homeNativeGlassDynamicBackgroundColorIds.isNotEmpty()) {
                val array = org.json.JSONArray()
                homeNativeGlassDynamicBackgroundColorIds.forEach { array.put(it) }
                put("homeNativeGlassDynamicBackgroundColorIds", array)
            }
            put(
                "homeNativeGlassSortSwitchBackgroundPaintField",
                homeNativeGlassSortSwitchBackgroundPaintField,
            )
            put(
                "homeNativeGlassSortSwitchSlideDrawMethod",
                homeNativeGlassSortSwitchSlideDrawMethod,
            )
            put(
                "homeNativeGlassSortSwitchSlidePathField",
                homeNativeGlassSortSwitchSlidePathField,
            )
            put(
                "homeNativeGlassEnterForumCapsuleControllerClass",
                homeNativeGlassEnterForumCapsuleControllerClass,
            )
            put(
                "homeNativeGlassEnterForumCapsuleInitMethod",
                homeNativeGlassEnterForumCapsuleInitMethod,
            )
            put(
                "homeNativeGlassEnterForumCapsuleRefreshMethod",
                homeNativeGlassEnterForumCapsuleRefreshMethod,
            )
            put(
                "homeNativeGlassEnterForumCapsuleViewField",
                homeNativeGlassEnterForumCapsuleViewField,
            )
            put(
                "homeNativeGlassEnterForumCapsuleTitleField",
                homeNativeGlassEnterForumCapsuleTitleField,
            )
            put("pbCommonLayoutPreloaderGetOrDefaultMethod", pbCommonLayoutPreloaderGetOrDefaultMethod)

            put("feedCardBindMethod", feedCardBindMethod)
            put("feedCardDataListField", feedCardDataListField)
            put("feedHeadParamsField", feedHeadParamsField)
            put("feedRecommendCardNestedDataMethod", feedRecommendCardNestedDataMethod)
            put("feedRecommendCardNestedDataListField", feedRecommendCardNestedDataListField)
            put("aiSpriteMemePanControllerClass", aiSpriteMemePanControllerClass)
            put("aiSpriteMemeEnableMethod", aiSpriteMemeEnableMethod)
            put("aiPbNewInputContainerClass", aiPbNewInputContainerClass)
            put("aiPbNewInputContainerInitSpriteMemeMethod", aiPbNewInputContainerInitSpriteMemeMethod)
            put("aiPbNewInputContainerInitAiWriteMethod", aiPbNewInputContainerInitAiWriteMethod)
            put("aiPbAiEmojiCreationViewBindMethod", aiPbAiEmojiCreationViewBindMethod)
            put("aiPbPageBrowserAiEmojiCreationBindMethod", aiPbPageBrowserAiEmojiCreationBindMethod)
            put("aiImageViewerJumpButtonOwnerClass", aiImageViewerJumpButtonOwnerClass)
            put("aiImageViewerJumpButtonInitMethod", aiImageViewerJumpButtonInitMethod)

            val statusObj = JSONObject()
            for ((featureKey, featureStatus) in featureStatusMap) {
                statusObj.put(
                    featureKey,
                    JSONObject().apply {
                        put("state", featureStatus.state)
                        val critical = org.json.JSONArray()
                        featureStatus.missingCritical.forEach { critical.put(it) }
                        put("missingCritical", critical)
                        val optional = org.json.JSONArray()
                        featureStatus.missingOptional.forEach { optional.put(it) }
                        put("missingOptional", optional)
                    },
                )
            }
            put("featureStatusMap", statusObj)

            put("scanSupportState", scanSupportState)
            put("scanTargetVersionCode", scanTargetVersionCode)
            put("scanTargetVersionName", scanTargetVersionName)
            put("scanTargetVersionType", scanTargetVersionType)
            if (scanErrors.isNotEmpty()) {
                val array = org.json.JSONArray()
                scanErrors.forEach { array.put(it) }
                put("scanErrors", array)
            }

            put("source", source)
            put("createdAt", createdAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val zgaMethodsArray = obj.optJSONArray("zgaMethods")
                val zgaMethodsList = if (zgaMethodsArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until zgaMethodsArray.length()) {
                        list.add(zgaMethodsArray.getString(i))
                    }
                    list
                } else null

                val anchorClassesArray = obj.optJSONArray("homePersonalizeAnchorClasses")
                val anchorClassesList = if (anchorClassesArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until anchorClassesArray.length()) {
                        list.add(anchorClassesArray.getString(i))
                    }
                    list
                } else null

                val featureStatusMap = mutableMapOf<String, HookFeatureStatus>()
                val statusObj = obj.optJSONObject("featureStatusMap")
                if (statusObj != null) {
                    val names = statusObj.keys()
                    while (names.hasNext()) {
                        val featureKey = names.next()
                        val statusValue = statusObj.optJSONObject(featureKey) ?: continue
                        val state = statusValue.optString("state", HookFeatureState.DISABLED)
                        val missingCritical = statusValue.optStringArray("missingCritical")
                        val missingOptional = statusValue.optStringArray("missingOptional")
                        featureStatusMap[featureKey] = HookFeatureStatus(
                            state = state,
                            missingCritical = missingCritical,
                            missingOptional = missingOptional,
                        )
                    }
                }

                HookSymbols(
                    homeTabClass = obj.optStringOrNull("homeTabClass"),
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod"),
                    homeTabListField = obj.optStringOrNull("homeTabListField"),
                    homeTabItemTypeField = obj.optStringOrNull("homeTabItemTypeField"),
                    homeTabItemCodeField = obj.optStringOrNull("homeTabItemCodeField"),
                    homeTabItemNameField = obj.optStringOrNull("homeTabItemNameField"),
                    homeTabItemUrlField = obj.optStringOrNull("homeTabItemUrlField"),
                    homeTabItemMainSetterMethod = obj.optStringOrNull("homeTabItemMainSetterMethod"),
                    homeTabItemMainIntField = obj.optStringOrNull("homeTabItemMainIntField"),
                    homeTabItemMainBooleanField = obj.optStringOrNull("homeTabItemMainBooleanField"),
                    settingsClass = obj.optStringOrNull("settingsClass"),
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod"),
                    settingsContainerField = obj.optStringOrNull("settingsContainerField"),
                    feedTemplateKeyMethod = obj.optStringOrNull("feedTemplateKeyMethod"),
                    feedTemplatePayloadMethod = obj.optStringOrNull("feedTemplatePayloadMethod"),
                    feedTemplateLoadMoreMethod = obj.optStringOrNull("feedTemplateLoadMoreMethod"),
                    splashAdHelperClass = obj.optStringOrNull("splashAdHelperClass"),
                    splashAdHelperMethod = obj.optStringOrNull("splashAdHelperMethod"),
                    closeAdDataClass = obj.optStringOrNull("closeAdDataClass"),
                    closeAdDataMethodG1 = obj.optStringOrNull("closeAdDataMethodG1"),
                    closeAdDataMethodJ1 = obj.optStringOrNull("closeAdDataMethodJ1"),

                    zgaClass = obj.optStringOrNull("zgaClass"),
                    zgaMethods = zgaMethodsList,
                    searchBoxViewClass = obj.optStringOrNull("searchBoxViewClass"),
                    searchBoxSetHintMethod = obj.optStringOrNull("searchBoxSetHintMethod"),
                    homeSearchBoxOwnerClass = obj.optStringOrNull("homeSearchBoxOwnerClass"),
                    homeSearchBoxInitMethod = obj.optStringOrNull("homeSearchBoxInitMethod"),
                    homeSearchBoxGetterMethod = obj.optStringOrNull("homeSearchBoxGetterMethod"),
                    homePersonalizeAnchorClasses = anchorClassesList,
                    homeRightSlotClass = obj.optStringOrNull("homeRightSlotClass"),
                    homeRightSlotStateMethods = obj.optStringArray("homeRightSlotStateMethods")
                        .takeIf { it.isNotEmpty() },
                    pbFallingViewClass = obj.optStringOrNull("pbFallingViewClass"),
                    pbFallingInitMethod = obj.optStringOrNull("pbFallingInitMethod"),
                    pbFallingShowMethod = obj.optStringOrNull("pbFallingShowMethod"),
                    pbFallingClearMethod = obj.optStringOrNull("pbFallingClearMethod"),
                    pbEarlyAdInsertClass = obj.optStringOrNull("pbEarlyAdInsertClass"),
                    pbEarlyAdInsertMethodSpecs = obj.optStringArray("pbEarlyAdInsertMethodSpecs")
                        .takeIf { it.isNotEmpty() },
                    pbAdBidCommonRequestModelClass = obj.optStringOrNull("pbAdBidCommonRequestModelClass"),
                    pbAdBidCommonRequestStartMethods = obj.optStringArray("pbAdBidCommonRequestStartMethods")
                        .takeIf { it.isNotEmpty() },
                    pbAdBidCommonRequestNotifyMethod = obj.optStringOrNull("pbAdBidCommonRequestNotifyMethod"),
                    pbAdBidPageBrowserRequestModelClass = obj.optStringOrNull("pbAdBidPageBrowserRequestModelClass"),
                    pbAdBidPageBrowserRequestDataMethod = obj.optStringOrNull("pbAdBidPageBrowserRequestDataMethod"),
                    typeAdapterSetDataMethod = obj.optStringOrNull("typeAdapterSetDataMethod"),
                    typeAdapterDataItemClass = obj.optStringOrNull("typeAdapterDataItemClass"),
                    typeAdapterDataGetTypeMethod = obj.optStringOrNull("typeAdapterDataGetTypeMethod"),
                    enterForumWebControllerClass = obj.optStringOrNull("enterForumWebControllerClass"),
                    enterForumWebLoadMethod = obj.optStringOrNull("enterForumWebLoadMethod"),
                    enterForumInitInfoDataClass = obj.optStringOrNull("enterForumInitInfoDataClass"),
                    enterForumInitInfoGetUrlMethod = obj.optStringOrNull("enterForumInitInfoGetUrlMethod"),
                    plainUrlClickableSpanClass = obj.optStringOrNull("plainUrlClickableSpanClass"),
                    plainUrlClickableSpanOnClickMethod = obj.optStringOrNull("plainUrlClickableSpanOnClickMethod"),
                    plainUrlClickableSpanOnClickOwnerClasses = obj.optStringArray("plainUrlClickableSpanOnClickOwnerClasses")
                        .takeIf { it.isNotEmpty() },
                    plainUrlClickableSpanTypeField = obj.optStringOrNull("plainUrlClickableSpanTypeField"),
                    plainUrlClickableSpanUrlField = obj.optStringOrNull("plainUrlClickableSpanUrlField"),
                    plainUrlClickableSpanTextField = obj.optStringOrNull("plainUrlClickableSpanTextField"),
                    plainUrlMessageManagerClass = obj.optStringOrNull("plainUrlMessageManagerClass"),
                    plainUrlMessageDispatchMethod = obj.optStringOrNull("plainUrlMessageDispatchMethod"),
                    plainUrlResponsedMessageClass = obj.optStringOrNull("plainUrlResponsedMessageClass"),
                    plainUrlResponsedMessageGetCmdMethod = obj.optStringOrNull("plainUrlResponsedMessageGetCmdMethod"),
                    plainUrlCustomResponsedMessageClass = obj.optStringOrNull("plainUrlCustomResponsedMessageClass"),
                    plainUrlCustomResponsedMessageGetDataMethod = obj.optStringOrNull("plainUrlCustomResponsedMessageGetDataMethod"),
                    plainUrlApplicationClass = obj.optStringOrNull("plainUrlApplicationClass"),
                    plainUrlApplicationGetInstMethod = obj.optStringOrNull("plainUrlApplicationGetInstMethod"),
                    privateReadReceiptModelClass = obj.optStringOrNull("privateReadReceiptModelClass"),
                    privateReadReceiptModelReadDispatchMethod = obj.optStringOrNull("privateReadReceiptModelReadDispatchMethod"),
                    privateReadReceiptMessageManagerClass = obj.optStringOrNull("privateReadReceiptMessageManagerClass"),
                    privateReadReceiptMessageManagerGetInstanceMethod = obj.optStringOrNull("privateReadReceiptMessageManagerGetInstanceMethod"),
                    privateReadReceiptMessageSendMethod = obj.optStringOrNull("privateReadReceiptMessageSendMethod"),
                    privateReadReceiptMessageBaseClass = obj.optStringOrNull("privateReadReceiptMessageBaseClass"),
                    privateReadReceiptRequestClass = obj.optStringOrNull("privateReadReceiptRequestClass"),
                    privateReadReceiptModelBaseClass = obj.optStringOrNull("privateReadReceiptModelBaseClass"),
                    privateReadReceiptCommitResponseClass = obj.optStringOrNull("privateReadReceiptCommitResponseClass"),
                    privateReadReceiptProcessAckMethod = obj.optStringOrNull("privateReadReceiptProcessAckMethod"),
                    privateReadReceiptResponseErrorMethod = obj.optStringOrNull("privateReadReceiptResponseErrorMethod"),
                    privateReadReceiptRequestMsgIdField = obj.optStringOrNull("privateReadReceiptRequestMsgIdField"),
                    privateReadReceiptRequestToUidField = obj.optStringOrNull("privateReadReceiptRequestToUidField"),
                    privateReadReceiptModelDataField = obj.optStringOrNull("privateReadReceiptModelDataField"),
                    privateReadReceiptPageDataClass = obj.optStringOrNull("privateReadReceiptPageDataClass"),
                    privateReadReceiptPageDataChatListMethod = obj.optStringOrNull("privateReadReceiptPageDataChatListMethod"),
                    privateReadReceiptChatMessageClass = obj.optStringOrNull("privateReadReceiptChatMessageClass"),
                    privateReadReceiptChatMessageMsgIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageMsgIdMethod"),
                    privateReadReceiptChatMessageUserIdMethod = obj.optStringOrNull("privateReadReceiptChatMessageUserIdMethod"),
                    privateReadReceiptChatMessageLocalDataMethod = obj.optStringOrNull("privateReadReceiptChatMessageLocalDataMethod"),
                    privateReadReceiptLocalDataClass = obj.optStringOrNull("privateReadReceiptLocalDataClass"),
                    privateReadReceiptLocalDataStatusMethod = obj.optStringOrNull("privateReadReceiptLocalDataStatusMethod"),
                    privateReadReceiptAccountClass = obj.optStringOrNull("privateReadReceiptAccountClass"),
                    privateReadReceiptCurrentAccountMethod = obj.optStringOrNull("privateReadReceiptCurrentAccountMethod"),
                    mountCardLinkLayoutClass = obj.optStringOrNull("mountCardLinkLayoutClass"),
                    mountCardLinkLayoutOnClickMethod = obj.optStringOrNull("mountCardLinkLayoutOnClickMethod"),
                    mountCardLinkLayoutDataField = obj.optStringOrNull("mountCardLinkLayoutDataField"),
                    mountCardLinkInfoDataClass = obj.optStringOrNull("mountCardLinkInfoDataClass"),
                    mountCardLinkInfoGetUrlMethod = obj.optStringOrNull("mountCardLinkInfoGetUrlMethod"),
                    forumBottomSheetViewClass = obj.optStringOrNull("forumBottomSheetViewClass"),
                    forumBottomSheetInitScrollMethod = obj.optStringOrNull("forumBottomSheetInitScrollMethod"),
                    autoRefreshTriggerMethod = obj.optStringOrNull("autoRefreshTriggerMethod"),
                    autoLoadMoreConfigClass = obj.optStringOrNull("autoLoadMoreConfigClass"),
                    autoLoadMoreConfigMethod = obj.optStringOrNull("autoLoadMoreConfigMethod"),
                    pbCommentScrollListenerClass = obj.optStringOrNull("pbCommentScrollListenerClass"),
                    pbCommentScrollMethod = obj.optStringOrNull("pbCommentScrollMethod"),
                    pbCommentScrollFragmentField = obj.optStringOrNull("pbCommentScrollFragmentField"),
                    pbCommentScrollBottomListenerField = obj.optStringOrNull("pbCommentScrollBottomListenerField"),
                    pbCommentScrollBottomMethod = obj.optStringOrNull("pbCommentScrollBottomMethod"),
                    pbCommentBottomListScrollClass = obj.optStringOrNull("pbCommentBottomListScrollClass"),
                    pbCommentBottomListScrollMethod = obj.optStringOrNull("pbCommentBottomListScrollMethod"),
                    pbCommentBottomListOwnerField = obj.optStringOrNull("pbCommentBottomListOwnerField"),
                    pbCommentBottomRecyclerScrollClass = obj.optStringOrNull("pbCommentBottomRecyclerScrollClass"),
                    pbCommentBottomRecyclerScrollMethod = obj.optStringOrNull("pbCommentBottomRecyclerScrollMethod"),
                    pbCommentBottomRecyclerOwnerField = obj.optStringOrNull("pbCommentBottomRecyclerOwnerField"),
                    pbGestureScaleManagerClass = obj.optStringOrNull("pbGestureScaleManagerClass"),
                    pbGestureScaleDispatchMethod = obj.optStringOrNull("pbGestureScaleDispatchMethod"),
                    pbGestureScaleListenerSetterMethod = obj.optStringOrNull("pbGestureScaleListenerSetterMethod"),
                    pbGestureScaleListenerClass = obj.optStringOrNull("pbGestureScaleListenerClass"),
                    pbGestureScaleListenerOnScaleMethod = obj.optStringOrNull("pbGestureScaleListenerOnScaleMethod"),

                    collectionPresenterField = obj.optStringOrNull("collectionPresenterField"),
                    collectionPresenterListSetterMethod = obj.optStringOrNull("collectionPresenterListSetterMethod"),
                    collectionPresenterAdapterField = obj.optStringOrNull("collectionPresenterAdapterField"),
                    collectionModelField = obj.optStringOrNull("collectionModelField"),
                    collectionModelListGetterMethod = obj.optStringOrNull("collectionModelListGetterMethod"),
                    collectionModelParseMethod = obj.optStringOrNull("collectionModelParseMethod"),
                    collectionModelListField = obj.optStringOrNull("collectionModelListField"),
                    collectionFragmentDisplayListField = obj.optStringOrNull("collectionFragmentDisplayListField"),
                    collectionActivityNavControllerField = obj.optStringOrNull("collectionActivityNavControllerField"),
                    collectionNavBarField = obj.optStringOrNull("collectionNavBarField"),
                    collectionAdapterShowFooterMethod = obj.optStringOrNull("collectionAdapterShowFooterMethod"),
                    collectionAdapterLoadingMethod = obj.optStringOrNull("collectionAdapterLoadingMethod"),
                    collectionAdapterHasMoreMethod = obj.optStringOrNull("collectionAdapterHasMoreMethod"),
                    collectionEditModeMethod = obj.optStringOrNull("collectionEditModeMethod"),

                    historyAdapterField = obj.optStringOrNull("historyAdapterField"),
                    historyAdapterSetListMethod = obj.optStringOrNull("historyAdapterSetListMethod"),
                    historyListField = obj.optStringOrNull("historyListField"),
                    historyActivityListUpdateMethod = obj.optStringOrNull("historyActivityListUpdateMethod"),
                    historyActivityNavBarField = obj.optStringOrNull("historyActivityNavBarField"),
                    historyThreadNameMethod = obj.optStringOrNull("historyThreadNameMethod"),
                    historyForumNameMethod = obj.optStringOrNull("historyForumNameMethod"),
                    historyUserNameMethod = obj.optStringOrNull("historyUserNameMethod"),
                    historyDescriptionMethod = obj.optStringOrNull("historyDescriptionMethod"),
                    historyThreadIdMethod = obj.optStringOrNull("historyThreadIdMethod"),
                    historyPostIdMethod = obj.optStringOrNull("historyPostIdMethod"),
                    historyLiveIdMethod = obj.optStringOrNull("historyLiveIdMethod"),

                    msgTabLocateToTabMethod = obj.optStringOrNull("msgTabLocateToTabMethod"),
                    msgTabContainerSelectMethod = obj.optStringOrNull("msgTabContainerSelectMethod"),
                    msgTabContainerExtDataField = obj.optStringOrNull("msgTabContainerExtDataField"),
                    freeCopyPopupMenuClass = obj.optStringOrNull("freeCopyPopupMenuClass"),
                    freeCopyPopupContentViewMethod = obj.optStringOrNull("freeCopyPopupContentViewMethod"),
                    freeCopyPopupTextField = obj.optStringOrNull("freeCopyPopupTextField"),

                    mainTabDataClass = obj.optStringOrNull("mainTabDataClass"),
                    mainTabAddMethod = obj.optStringOrNull("mainTabAddMethod"),
                    mainTabGetListMethod = obj.optStringOrNull("mainTabGetListMethod"),
                    mainTabDelegateGetStructureMethod = obj.optStringOrNull("mainTabDelegateGetStructureMethod"),
                    mainTabStructureTypeField = obj.optStringOrNull("mainTabStructureTypeField"),
                    mainTabStructureDynamicIconField = obj.optStringOrNull("mainTabStructureDynamicIconField"),
                    mainTabStructureFragmentField = obj.optStringOrNull("mainTabStructureFragmentField"),

                    origImagePagerAdapterClass = obj.optStringOrNull("origImagePagerAdapterClass"),
                    origImageUrlDragImageViewClass = obj.optStringOrNull("origImageUrlDragImageViewClass"),
                    origImageDataClass = obj.optStringOrNull("origImageDataClass"),
                    origImageSetPrimaryItemMethod = obj.optStringOrNull("origImageSetPrimaryItemMethod"),
                    origImageSetAssistUrlMethod = obj.optStringOrNull("origImageSetAssistUrlMethod"),
                    origImageAssistDataMethod = obj.optStringOrNull("origImageAssistDataMethod"),
                    origImageOriginTextMethod = obj.optStringOrNull("origImageOriginTextMethod"),
                    origImageShowButtonField = obj.optStringOrNull("origImageShowButtonField"),
                    origImageBlockedField = obj.optStringOrNull("origImageBlockedField"),
                    origImageOriginalProcessField = obj.optStringOrNull("origImageOriginalProcessField"),
                    origImageOriginalUrlField = obj.optStringOrNull("origImageOriginalUrlField"),
                    origImageSharedPrefHelperClass = obj.optStringOrNull("origImageSharedPrefHelperClass"),
                    origImageSharedPrefGetInstanceMethod = obj.optStringOrNull("origImageSharedPrefGetInstanceMethod"),
                    origImageSharedPrefPutBooleanMethod = obj.optStringOrNull("origImageSharedPrefPutBooleanMethod"),
                    origImageMd5Class = obj.optStringOrNull("origImageMd5Class"),
                    origImageMd5Method = obj.optStringOrNull("origImageMd5Method"),
                    origImagePrimaryReadyMethod = obj.optStringOrNull("origImagePrimaryReadyMethod"),
                    origImageTriggerMethod = obj.optStringOrNull("origImageTriggerMethod"),
                    origImageDirectStartMethod = obj.optStringOrNull("origImageDirectStartMethod"),
                    shareTrackBuilderClass = obj.optStringOrNull("shareTrackBuilderClass"),
                    shareTrackBuildUrlMethod = obj.optStringOrNull("shareTrackBuildUrlMethod"),
                    shareTrackAppendQueryMethod = obj.optStringOrNull("shareTrackAppendQueryMethod"),
                    imageViewerShareConfigClass = obj.optStringOrNull("imageViewerShareConfigClass"),
                    imageViewerShareIsDialogField = obj.optStringOrNull("imageViewerShareIsDialogField"),
                    imageViewerShareItemField = obj.optStringOrNull("imageViewerShareItemField"),
                    imageViewerShareAddOutsideMethod = obj.optStringOrNull("imageViewerShareAddOutsideMethod"),
                    imageViewerShareGetRequestDataMethod = obj.optStringOrNull("imageViewerShareGetRequestDataMethod"),
                    imageViewerShareSetRequestDataMethod = obj.optStringOrNull("imageViewerShareSetRequestDataMethod"),
                    imageViewerShareGetContextMethod = obj.optStringOrNull("imageViewerShareGetContextMethod"),
                    imageViewerShareItemClass = obj.optStringOrNull("imageViewerShareItemClass"),
                    imageViewerShareItemTitleField = obj.optStringOrNull("imageViewerShareItemTitleField"),
                    imageViewerShareItemContentField = obj.optStringOrNull("imageViewerShareItemContentField"),
                    imageViewerShareItemLinkUrlField = obj.optStringOrNull("imageViewerShareItemLinkUrlField"),
                    imageViewerShareItemImageUriField = obj.optStringOrNull("imageViewerShareItemImageUriField"),
                    imageViewerShareItemImageUrlField = obj.optStringOrNull("imageViewerShareItemImageUrlField"),
                    imageViewerShareItemLocalFileField = obj.optStringOrNull("imageViewerShareItemLocalFileField"),
                    imageViewerShareItemViewClass = obj.optStringOrNull("imageViewerShareItemViewClass"),
                    imageViewerShareItemNameByResMethod = obj.optStringOrNull("imageViewerShareItemNameByResMethod"),
                    imageViewerShareItemNameByTextMethod = obj.optStringOrNull("imageViewerShareItemNameByTextMethod"),
                    imageViewerShareIconResId = obj.optIntOrNull("imageViewerShareIconResId"),
                    homeNativeGlassSubPbNextPageMoreViewId = obj.optIntOrNull("homeNativeGlassSubPbNextPageMoreViewId"),
                    homeNativeGlassPbReplyTitleDividerViewId = obj.optIntOrNull("homeNativeGlassPbReplyTitleDividerViewId"),
                    homeNativeGlassDynamicBackgroundColorIds =
                        obj.optIntArray("homeNativeGlassDynamicBackgroundColorIds"),
                    homeNativeGlassSortSwitchBackgroundPaintField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchBackgroundPaintField"),
                    homeNativeGlassSortSwitchSlideDrawMethod =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlideDrawMethod"),
                    homeNativeGlassSortSwitchSlidePathField =
                        obj.optStringOrNull("homeNativeGlassSortSwitchSlidePathField"),
                    homeNativeGlassEnterForumCapsuleControllerClass =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleControllerClass"),
                    homeNativeGlassEnterForumCapsuleInitMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleInitMethod"),
                    homeNativeGlassEnterForumCapsuleRefreshMethod =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleRefreshMethod"),
                    homeNativeGlassEnterForumCapsuleViewField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleViewField"),
                    homeNativeGlassEnterForumCapsuleTitleField =
                        obj.optStringOrNull("homeNativeGlassEnterForumCapsuleTitleField"),
                    pbCommonLayoutPreloaderGetOrDefaultMethod =
                        obj.optStringOrNull("pbCommonLayoutPreloaderGetOrDefaultMethod"),

                    feedCardBindMethod = obj.optStringOrNull("feedCardBindMethod"),
                    feedCardDataListField = obj.optStringOrNull("feedCardDataListField"),
                    feedHeadParamsField = obj.optStringOrNull("feedHeadParamsField"),
                    feedRecommendCardNestedDataMethod = obj.optStringOrNull("feedRecommendCardNestedDataMethod"),
                    feedRecommendCardNestedDataListField = obj.optStringOrNull("feedRecommendCardNestedDataListField"),
                    aiSpriteMemePanControllerClass = obj.optStringOrNull("aiSpriteMemePanControllerClass"),
                    aiSpriteMemeEnableMethod = obj.optStringOrNull("aiSpriteMemeEnableMethod"),
                    aiPbNewInputContainerClass = obj.optStringOrNull("aiPbNewInputContainerClass"),
                    aiPbNewInputContainerInitSpriteMemeMethod = obj.optStringOrNull("aiPbNewInputContainerInitSpriteMemeMethod"),
                    aiPbNewInputContainerInitAiWriteMethod = obj.optStringOrNull("aiPbNewInputContainerInitAiWriteMethod"),
                    aiPbAiEmojiCreationViewBindMethod = obj.optStringOrNull("aiPbAiEmojiCreationViewBindMethod"),
                    aiPbPageBrowserAiEmojiCreationBindMethod = obj.optStringOrNull("aiPbPageBrowserAiEmojiCreationBindMethod"),
                    aiImageViewerJumpButtonOwnerClass = obj.optStringOrNull("aiImageViewerJumpButtonOwnerClass"),
                    aiImageViewerJumpButtonInitMethod = obj.optStringOrNull("aiImageViewerJumpButtonInitMethod"),

                    featureStatusMap = featureStatusMap,
                    scanSupportState = obj.optString("scanSupportState", ScanSupportState.UNKNOWN),
                    scanTargetVersionCode = obj.optLongOrNull("scanTargetVersionCode"),
                    scanTargetVersionName = obj.optStringOrNull("scanTargetVersionName"),
                    scanTargetVersionType = obj.optStringOrNull("scanTargetVersionType"),
                    scanErrors = obj.optStringArray("scanErrors"),

                    source = obj.optString("source", "unsupported"),
                    createdAt = obj.optLong("createdAt", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (isNull(name)) return null
            val s = optString(name)
            return s.ifEmpty { null }
        }

        private fun JSONObject.optIntOrNull(name: String): Int? {
            if (!has(name) || isNull(name)) return null
            return optInt(name).takeIf { it != 0 }
        }

        private fun JSONObject.optLongOrNull(name: String): Long? {
            if (!has(name) || isNull(name)) return null
            return try {
                getLong(name)
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringArray(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) out.add(value)
            }
            return out
        }

        private fun JSONObject.optIntArray(name: String): List<Int> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<Int>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optInt(i, 0)
                if (value != 0) out.add(value)
            }
            return out
        }
    }
}

internal data class FreeCopyPopupSymbols(
    val menuClass: Class<*>,
    val contentViewMethod: Method,
    val textField: Field,
)

internal data class PlainUrlClickableSpanSymbols(
    val spanClass: Class<*>,
    val onClickMethods: List<Method>,
    val typeField: Field,
    val urlField: Field,
    val textField: Field,
)

internal data class PlainUrlMessageDispatchSymbols(
    val messageManagerClass: Class<*>,
    val dispatchMethod: Method,
    val responsedMessageClass: Class<*>,
    val getCmdMethod: Method,
    val customResponsedMessageClass: Class<*>,
    val getDataMethod: Method,
    val applicationClass: Class<*>,
    val getInstMethod: Method,
)

internal data class PlainUrlMessageDataSymbols(
    val typeField: Field,
    val urlField: Field,
    val textField: Field,
)

internal data class PrivateReadReceiptSymbols(
    val modelClass: Class<*>,
    val modelReadDispatchMethod: Method,
    val processAckMethod: Method,
    val responseErrorMethod: Method,
    val messageManagerGetInstanceMethod: Method,
    val messageManagerSendMethod: Method,
    val requestConstructor: Constructor<*>,
    val requestMessageClass: Class<*>,
    val requestMsgIdField: Field,
    val requestToUidField: Field,
    val modelDataField: Field,
    val pageDataChatListMethod: Method,
    val chatMessageMsgIdMethod: Method,
    val chatMessageUserIdMethod: Method,
    val chatMessageLocalDataMethod: Method,
    val localDataStatusMethod: Method,
    val currentAccountMethod: Method,
)

internal data class MountCardLinkLayoutSymbols(
    val layoutClass: Class<*>,
    val onClickMethod: Method,
    val dataField: Field,
    val getUrlMethod: Method,
)

internal data class AiComponentSymbols(
    val spriteMemeEnableMethod: Method,
    val pbInitSpriteMemeMethod: Method,
    val pbInitAiWriteMethod: Method,
    val pbAiEmojiCreationViewBindMethod: Method? = null,
    val pbPageBrowserAiEmojiCreationBindMethod: Method? = null,
)

internal data class AiImageViewerJumpButtonSymbols(
    val initMethod: Method,
)

internal data class AutoSignInHybridNativeProxySymbols(
    val jsBridgeClass: Class<*>,
    val nativeNetworkProxyMethod: Method,
    val taskConstructor: Constructor<*>,
    val doInBackgroundMethod: Method,
)



internal object HookSymbolResolver {
    private const val TAG = "[HookSymbolResolver]"
    private const val KEY_SYMBOL_FP = "hook_symbol_fp_v23"
    private const val KEY_SYMBOL_JSON = "hook_symbol_json_v64"
    private const val KEY_CACHE_MODULE_VERSION = "hook_symbol_cache_module_version"
    private const val KEY_SYMBOL_VERIFIED_FP = "hook_symbol_verified_fp_v1"
    // 模块适配新的贴吧目标版本时，同步更新这三个值。
    private const val TARGET_TIEBA_VERSION_NAME = "22.6.5.1"
    private const val MIN_TIEBA_VERSION_CODE = 369098752L
    private const val MAX_TIEBA_VERSION_CODE = 369493253L
    private const val VERSION_TYPE_META_NAME = "versionType"
    private const val OFFICIAL_VERSION_TYPE = "3"
    private const val PLAIN_URL_CLICKABLE_SPAN_CLASS = "com.baidu.tieba.ui7"
    private const val PLAIN_URL_CLICKABLE_SPAN_TYPE_FIELD = "d"
    private const val PLAIN_URL_CLICKABLE_SPAN_URL_FIELD = "e"
    private const val PLAIN_URL_CLICKABLE_SPAN_TEXT_FIELD = "i"
    private const val PLAIN_URL_CLICK_MESSAGE_CMD = 2001332
    private const val PLAIN_URL_MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val PLAIN_URL_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.ResponsedMessage"
    private const val PLAIN_URL_CUSTOM_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.CustomResponsedMessage"
    private const val PLAIN_URL_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val PLAIN_URL_MESSAGE_DISPATCH_METHOD = "dispatchResponsedMessage"
    private const val PLAIN_URL_GET_CMD_METHOD = "getCmd"
    private const val PLAIN_URL_GET_DATA_METHOD = "getData"
    private const val PLAIN_URL_GET_INST_METHOD = "getInst"
    private const val ENTER_FORUM_INIT_INFO_DATA_CLASS = "com.baidu.tbadk.coreExtra.data.InitInfoData"
    private const val ENTER_FORUM_INIT_INFO_GET_URL_METHOD = "getForumEnterUrl"
    private const val AI_SPRITE_MEME_PAN_CONTROLLER_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePanController"
    private const val AI_SPRITE_MEME_ENABLE_METHOD = "s"
    private const val AI_SPRITE_MEME_PAN_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan"
    private const val AI_PB_NEW_INPUT_CONTAINER_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewInputContainer"
    private const val AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"
    private const val AI_PB_NEW_INPUT_INIT_SPRITE_MEME_METHOD = "e0"
    private const val AI_PB_NEW_INPUT_INIT_AI_WRITE_METHOD = "X"
    private const val AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout"
    private const val AI_IMAGE_VIEWER_BOTTOM_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageViewerBottomLayout"
    private const val AI_IMAGE_VIEWER_ABS_FLOOR_IMAGE_TEXT_VIEW_CLASS =
        "com.baidu.tbadk.coreExtra.view.AbsFloorImageTextView"
    private const val AI_IMAGE_VIEWER_FACE_GROUP_DOWNLOAD_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.FaceGroupDownloadLayout"
    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val AI_SPRITE_MEME_INFO_CLASS = "tbclient.SpriteMemeInfo"
    private const val HOME_NATIVE_GLASS_SUB_PB_NEXT_PAGE_MORE_VIEW_RES_NAME = "pb_more_view"
    private const val HOME_NATIVE_GLASS_PB_REPLY_TITLE_DIVIDER_VIEW_RES_NAME = "divider_bottom"
    private const val HOME_NATIVE_GLASS_SORT_SWITCH_BACKGROUND_PAINT_FIELD = "o"
    private const val HOME_NATIVE_GLASS_SORT_SWITCH_SLIDE_DRAW_METHOD = "y"
    private const val HOME_NATIVE_GLASS_SORT_SWITCH_SLIDE_PATH_FIELD = "z"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_CONTROLLER_CLASS = "com.baidu.tieba.gcd"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_INIT_METHOD = "r"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_REFRESH_METHOD = "D"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_VIEW_FIELD = "l"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_TITLE_FIELD = "q"
    private const val HOME_NATIVE_GLASS_BASE_FRAGMENT_CLASS = "com.baidu.tbadk.core.BaseFragment"
    private const val HOME_NATIVE_GLASS_PB_BAR_IMAGE_VIEW_CLASS = "com.baidu.tbadk.core.view.PbBarImageView"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT = 24
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_MIN_CLASS_SCORE = 170
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_MIN_SCORE_GAP = 24
    private val HOME_NATIVE_GLASS_DYNAMIC_BACKGROUND_COLOR_RES_NAMES = arrayOf(
        "CAM_X0110",
        "CAM_X0112",
        "CAM_X0201",
        "CAM_X0202",
        "CAM_X0203",
        "CAM_X0204",
        "CAM_X0205",
        "CAM_X0206",
        "CAM_X0207",
        "CAM_X0208",
        "CAM_X0209",
        "CAM_X0210",
        "CAM_X0211",
        "CAM_X0212",
        "color_bg_page",
        "color_bg_primary_tiny",
    )
    private val TARGET_APP_R_ID_CLASSES = listOf(
        "com.baidu.tieba.R\$id",
        "com.baidu.searchbox.livenps.R\$id",
    )
    private val TARGET_APP_R_COLOR_CLASSES = listOf(
        "com.baidu.tieba.R\$color",
        "com.baidu.searchbox.livenps.R\$color",
    )
    private const val PRIVATE_READ_RECEIPT_MODEL_CLASS =
        "com.baidu.tieba.immessagecenter.im.model.PersonalMsglistModel"
    private val PRIVATE_READ_RECEIPT_MODEL_READ_DISPATCH_CANDIDATES = arrayOf("h1", "K2")
    private const val PRIVATE_READ_RECEIPT_MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val PRIVATE_READ_RECEIPT_MESSAGE_MANAGER_GET_INSTANCE_METHOD = "getInstance"
    private const val PRIVATE_READ_RECEIPT_MESSAGE_BASE_CLASS = "com.baidu.adp.framework.message.Message"
    private const val PRIVATE_READ_RECEIPT_MESSAGE_SEND_METHOD = "sendMessage"
    private const val PRIVATE_READ_RECEIPT_REQUEST_CLASS =
        "com.baidu.tieba.im.message.RequestPersonalMsgReadMessage"
    private const val PRIVATE_READ_RECEIPT_MODEL_BASE_CLASS = "com.baidu.tieba.im.model.MsglistModel"
    private const val PRIVATE_READ_RECEIPT_COMMIT_RESPONSE_CLASS = "com.baidu.tieba.im.message.ResponseCommitMessage"
    private const val PRIVATE_READ_RECEIPT_PROCESS_ACK_METHOD = "processMsgACK"
    private const val PRIVATE_READ_RECEIPT_RESPONSE_ERROR_METHOD = "getError"
    private const val PRIVATE_READ_RECEIPT_REQUEST_MSG_ID_FIELD = "hasSentMsgId"
    private const val PRIVATE_READ_RECEIPT_REQUEST_TO_UID_FIELD = "toUid"
    private const val PRIVATE_READ_RECEIPT_MODEL_DATA_FIELD = "mDatas"
    private const val PRIVATE_READ_RECEIPT_PAGE_DATA_CLASS = "com.baidu.tieba.im.data.MsgPageData"
    private const val PRIVATE_READ_RECEIPT_PAGE_DATA_CHAT_LIST_METHOD = "getChatMessages"
    private const val PRIVATE_READ_RECEIPT_CHAT_MESSAGE_CLASS = "com.baidu.tieba.im.message.chat.ChatMessage"
    private const val PRIVATE_READ_RECEIPT_CHAT_MESSAGE_MSG_ID_METHOD = "getMsgId"
    private const val PRIVATE_READ_RECEIPT_CHAT_MESSAGE_USER_ID_METHOD = "getUserId"
    private const val PRIVATE_READ_RECEIPT_CHAT_MESSAGE_LOCAL_DATA_METHOD = "getLocalData"
    private const val PRIVATE_READ_RECEIPT_LOCAL_DATA_CLASS = "com.baidu.tieba.im.data.MsgLocalData"
    private const val PRIVATE_READ_RECEIPT_LOCAL_DATA_STATUS_METHOD = "getStatus"
    private const val PRIVATE_READ_RECEIPT_ACCOUNT_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val PRIVATE_READ_RECEIPT_CURRENT_ACCOUNT_METHOD = "getCurrentAccount"
    private const val HYBRID_JS_BRIDGE_PROXY_CLASS = "com.baidu.tieba.h5power.HybridJsBridgePlugin_Proxy"
    private const val MOUNT_CARD_LINK_LAYOUT_CLASS =
        "com.baidu.tbadk.core.view.commonMountCard.TbMountCardLinkLayout"
    private const val MOUNT_CARD_LINK_INFO_DATA_CLASS = "com.baidu.tbadk.data.CardLinkInfoData"
    private const val MOUNT_CARD_LINK_LAYOUT_ON_CLICK_METHOD = "onClick"
    private const val MOUNT_CARD_LINK_LAYOUT_DATA_FIELD = "b"
    private const val MOUNT_CARD_LINK_INFO_GET_URL_METHOD = "getUrl"
    private val PLAIN_URL_CLICKABLE_SPAN_CONTAINER_CLASSES = listOf(
        "com.baidu.tieba.si7",
        "com.baidu.tieba.qi7",
        "com.baidu.tbadk.widget.richText.TbRichTextItem",
    )
    private val plainUrlMessageDataSymbolsCache = ConcurrentHashMap<Class<*>, PlainUrlMessageDataSymbols>()
    private val scanContext = ThreadLocal<ScanContext?>()

    private class ScanContext(private val classLoader: ClassLoader) {
        private val classCache = HashMap<String, Class<*>?>()
        private val instanceFieldsCache = IdentityHashMap<Class<*>, List<Field>>()
        private val instanceMethodsCache = IdentityHashMap<Class<*>, List<Method>>()
        var scanErrors: MutableList<String>? = null

        fun findClass(name: String, cl: ClassLoader): Class<*>? {
            if (cl !== classLoader) return HookSymbolResolver.safeFindClassUncached(name, cl)
            if (classCache.containsKey(name)) return classCache[name]
            val resolved = HookSymbolResolver.safeFindClassUncached(name, cl)
            classCache[name] = resolved
            return resolved
        }

        fun collectInstanceFields(clazz: Class<*>): List<Field> {
            instanceFieldsCache[clazz]?.let { return it }
            val resolved = HookSymbolResolver.collectInstanceFieldsUncached(clazz)
            instanceFieldsCache[clazz] = resolved
            return resolved
        }

        fun collectInstanceMethods(clazz: Class<*>): List<Method> {
            instanceMethodsCache[clazz]?.let { return it }
            val resolved = HookSymbolResolver.collectInstanceMethodsUncached(clazz)
            instanceMethodsCache[clazz] = resolved
            return resolved
        }
    }

    private data class ScanCandidateSet(
        val obfuscated: List<String>,
        val expanded: List<String>,
    ) {
        val all: List<String>
            get() = (obfuscated + expanded).distinct()
    }

    private data class PbEarlyAdInsertScanSymbols(
        val className: String?,
        val methodSpecs: List<String>,
    )

    private data class PbAdBidScanSymbols(
        val commonRequestModelClass: String? = null,
        val commonRequestStartMethods: List<String> = emptyList(),
        val commonRequestNotifyMethod: String? = null,
        val pageBrowserRequestModelClass: String? = null,
        val pageBrowserRequestDataMethod: String? = null,
    )

    private data class TypeAdapterDataFilterScanSymbols(
        val setDataMethod: String? = null,
        val dataItemClass: String? = null,
        val dataGetTypeMethod: String? = null,
    )

    private data class RecommendCardNestedDataScanSymbols(
        val nestedDataMethod: String? = null,
        val nestedDataListField: String? = null,
    )

    private data class RecommendCardNestedDataCandidate(
        val bindMethodName: String,
        val stateClassName: String,
        val nestedDataMethodName: String,
        val nestedDataClassName: String,
        val listFieldName: String,
    )

    private data class PlainUrlClickableSpanScanSymbols(
        val className: String? = null,
        val onClickMethod: String? = null,
        val onClickOwnerClasses: List<String> = emptyList(),
        val typeField: String? = null,
        val urlField: String? = null,
        val textField: String? = null,
    )

    private data class PlainUrlClickableSpanFieldSymbols(
        val typeField: Field,
        val urlField: Field,
        val textField: Field,
        val evidence: String,
    )

    private data class PlainUrlMessageDispatchScanSymbols(
        val messageManagerClass: String? = null,
        val dispatchMethod: String? = null,
        val responsedMessageClass: String? = null,
        val getCmdMethod: String? = null,
        val customResponsedMessageClass: String? = null,
        val getDataMethod: String? = null,
        val applicationClass: String? = null,
        val getInstMethod: String? = null,
    )

    private data class PrivateReadReceiptScanSymbols(
        val modelClass: String? = null,
        val modelReadDispatchMethod: String? = null,
        val messageManagerClass: String? = null,
        val messageManagerGetInstanceMethod: String? = null,
        val messageSendMethod: String? = null,
        val messageBaseClass: String? = null,
        val requestClass: String? = null,
        val modelBaseClass: String? = null,
        val commitResponseClass: String? = null,
        val processAckMethod: String? = null,
        val responseErrorMethod: String? = null,
        val requestMsgIdField: String? = null,
        val requestToUidField: String? = null,
        val modelDataField: String? = null,
        val pageDataClass: String? = null,
        val pageDataChatListMethod: String? = null,
        val chatMessageClass: String? = null,
        val chatMessageMsgIdMethod: String? = null,
        val chatMessageUserIdMethod: String? = null,
        val chatMessageLocalDataMethod: String? = null,
        val localDataClass: String? = null,
        val localDataStatusMethod: String? = null,
        val accountClass: String? = null,
        val currentAccountMethod: String? = null,
    )

    private data class PlainUrlClickableSpanClassMatch(
        val clazz: Class<*>,
        val score: Int,
    )

    private data class MountCardLinkLayoutScanSymbols(
        val layoutClass: String? = null,
        val onClickMethod: String? = null,
        val dataField: String? = null,
        val dataClass: String? = null,
        val getUrlMethod: String? = null,
    )

    private data class AiComponentScanSymbols(
        val spriteMemePanControllerClass: String? = null,
        val spriteMemeEnableMethod: String? = null,
        val pbNewInputContainerClass: String? = null,
        val pbInitSpriteMemeMethod: String? = null,
        val pbInitAiWriteMethod: String? = null,
        val pbAiEmojiCreationViewBindMethod: String? = null,
        val pbPageBrowserAiEmojiCreationBindMethod: String? = null,
        val imageViewerJumpButtonOwnerClass: String? = null,
        val imageViewerJumpButtonInitMethod: String? = null,
    )

    private data class HomeNativeGlassResourceIds(
        val subPbNextPageMoreViewId: Int? = null,
        val pbReplyTitleDividerViewId: Int? = null,
        val dynamicBackgroundColorIds: List<Int> = emptyList(),
    )

    private data class HomeNativeGlassSortSwitchSymbols(
        val backgroundPaintField: String? = null,
        val slideDrawMethod: String? = null,
        val slidePathField: String? = null,
    )

    private data class HomeNativeGlassEnterForumCapsuleSymbols(
        val controllerClass: String? = null,
        val initMethod: String? = null,
        val refreshMethod: String? = null,
        val viewField: String? = null,
        val titleField: String? = null,
    )

    private data class HomeNativeGlassEnterForumCapsuleClassCandidate(
        val clazz: Class<*>,
        val fields: List<Field>,
        val methods: List<Method>,
        val score: Int,
        val evidence: String,
    )

    private data class HomeNativeGlassEnterForumCapsuleResolvedCandidate(
        val symbols: HomeNativeGlassEnterForumCapsuleSymbols,
        val score: Int,
        val evidence: String,
    )

    private val ALL_FEATURE_KEYS = listOf(
        HookFeatureKey.BLOCK_AD,
        HookFeatureKey.ENABLE_CUSTOM_POST_FILTER,

        HookFeatureKey.SIMPLIFY_HOME_TOP_TABS,
        HookFeatureKey.SIMPLIFY_BOTTOM_TABS,
        HookFeatureKey.HIDE_PB_BOTTOM_BANNER,
        HookFeatureKey.FILTER_ENTER_FORUM_WEB,
        HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER,
        HookFeatureKey.HOME_NATIVE_GLASS,
        HookFeatureKey.AUTO_LOAD_MORE,
        HookFeatureKey.DISABLE_AUTO_REFRESH,
        HookFeatureKey.ENABLE_PB_SCROLL_COALESCE,
        HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE,
        HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT,
        HookFeatureKey.FREE_COPY,
        HookFeatureKey.DEFAULT_NOTIFY_TAB,
        HookFeatureKey.DEFAULT_ORIGINAL_IMAGE,
        HookFeatureKey.AUTO_SIGN_IN,
        HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE,
        HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS,
        HookFeatureKey.DISABLE_AI_COMPONENTS,
    )
    private val SCAN_WHITELIST_CLASSES = listOf(
        "com.baidu.tieba.zj9",
        "com.baidu.tieba.feed.list.FeedTemplateAdapter",
        "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt",
        "com.baidu.tbadk.data.CloseAdData",
        FREE_COPY_POPUP_MENU_CLASS,

        "com.baidu.tbadk.widget.TbSearchBoxView",
        HOME_RIGHT_SLOT_CLASS,
        "com.baidu.tieba.pb.view.PbFallingView",
        PB_AD_INSERT_CLASS,
        PLAIN_URL_CLICKABLE_SPAN_CLASS,
        "com.baidu.tbadk.mainTab.MaintabAddResponedData",
        "com.baidu.tieba.no6",
        "com.baidu.tbadk.core.atomData.ShareDialogConfig",
        "com.baidu.tbadk.coreExtra.share.ShareItem",
        "com.baidu.tieba.sharesdk.view.ShareDialogItemView",
        "com.baidu.tieba.feed.card.FeedCardView",
        "com.baidu.tieba.feed.component.uistate.CardHeadUiState",
        "com.baidu.tbadk.coreExtra.view.UrlDragImageView",
        StableTiebaHookPoints.BD_LIST_VIEW_CLASS,
        StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS,
        FORUM_BOTTOM_SHEET_VIEW_CLASS,
        AI_SPRITE_MEME_PAN_CONTROLLER_CLASS,
        AI_PB_NEW_INPUT_CONTAINER_CLASS,
    )

    private val EXPANDED_SCAN_PACKAGE_PREFIXES = listOf(
        "com.baidu.tieba.ad.",
        "com.baidu.tieba.browser.",
        "com.baidu.tieba.card.",
        "com.baidu.tieba.feed.",
        "com.baidu.tieba.feedlog.",
        "com.baidu.tieba.forum.",
        "com.baidu.tieba.funad.",
        "com.baidu.tieba.home.",
        "com.baidu.tieba.homepage.",
        "com.baidu.tieba.immessagecenter.",
        "com.baidu.tieba.myCollection.",
        "com.baidu.tieba.pb.",
        "com.baidu.tieba.sharesdk.",
        "com.baidu.tbadk.core.atomData.",
        "com.baidu.tbadk.core.sharedPref.",
        "com.baidu.tbadk.core.util.",
        "com.baidu.tbadk.core.view.",
        "com.baidu.tbadk.coreExtra.",
        "com.baidu.tbadk.data.",
        "com.baidu.tbadk.editortools.",
        "com.baidu.tbadk.mainTab.",
        "com.baidu.tbadk.widget.",
        "com.baidu.adp.widget.ListView.",
        "com.baidu.searchbox.task.view.mainactivity.",
    )

    private val EXPANDED_SCAN_CLASS_MARKERS = listOf(
        "Adapter",
        "Config",
        "Controller",
        "Data",
        "Delegate",
        "Helper",
        "Kt",
        "Manager",
        "Parser",
        "Presenter",
        "Share",
        "State",
        "Util",
        "View",
        "ViewModel",
    )

    @Volatile
    private var sMemoryFingerprint: String? = null

    @Volatile
    private var sMemorySymbols: HookSymbols? = null

    fun getMemorySymbols(): HookSymbols? = sMemorySymbols

    fun resolveAutoSignInHybridNativeProxySymbols(
        cl: ClassLoader,
    ): AutoSignInHybridNativeProxySymbols? {
        return try {
            val proxyClass = safeFindClass(HYBRID_JS_BRIDGE_PROXY_CLASS, cl) ?: run {
                XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: proxy class not found")
                return null
            }
            val proxyConstructor = proxyClass.declaredConstructors.singleOrNull { constructor ->
                constructor.parameterTypes.size == 1 &&
                    !constructor.parameterTypes[0].isPrimitive
            } ?: run {
                XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: proxy constructor mismatch")
                return null
            }
            val jsBridgeClass = proxyConstructor.parameterTypes[0]
            val nativeNetworkProxyMethod = jsBridgeClass.declaredMethods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 7 &&
                    method.parameterTypes[0] == android.webkit.WebView::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == String::class.java &&
                    method.parameterTypes[4] == JSONObject::class.java &&
                    isIntType(method.parameterTypes[5]) &&
                    isIntType(method.parameterTypes[6]) &&
                    method.returnType != Void.TYPE
            } ?: run {
                XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: bridge method mismatch")
                return null
            }
            val taskConstructor = findHybridNativeProxyTaskConstructor(jsBridgeClass, cl) ?: run {
                XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: task constructor mismatch")
                return null
            }
            val taskClass = taskConstructor.declaringClass
            val doInBackgroundMethod = taskClass.declaredMethods
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].isArray &&
                        method.parameterTypes[0].componentType == Any::class.java &&
                        (java.util.Map::class.java.isAssignableFrom(method.returnType) ||
                            method.returnType == Any::class.java)
                }
                .sortedBy { method ->
                    if (java.util.Map::class.java.isAssignableFrom(method.returnType)) 0 else 1
                }
                .firstOrNull()
                ?: run {
                    XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: task doInBackground mismatch")
                    return null
                }

            nativeNetworkProxyMethod.isAccessible = true
            taskConstructor.isAccessible = true
            doInBackgroundMethod.isAccessible = true
            XposedCompat.log(
                "[AutoSignIn] hybrid native proxy resolved: " +
                    "${jsBridgeClass.name}.${nativeNetworkProxyMethod.name} task=${taskClass.name}",
            )
            AutoSignInHybridNativeProxySymbols(
                jsBridgeClass = jsBridgeClass,
                nativeNetworkProxyMethod = nativeNetworkProxyMethod,
                taskConstructor = taskConstructor,
                doInBackgroundMethod = doInBackgroundMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[AutoSignIn] hybrid native proxy resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun findHybridNativeProxyTaskConstructor(
        jsBridgeClass: Class<*>,
        cl: ClassLoader,
    ): Constructor<*>? {
        val candidates = LinkedHashSet<Class<*>>()
        try {
            jsBridgeClass.declaredClasses.forEach { candidates.add(it) }
        } catch (_: Throwable) {
        }
        for (suffix in 'a'..'z') {
            safeFindClass("${jsBridgeClass.name}\$$suffix", cl)?.let { candidates.add(it) }
        }
        return candidates
            .asSequence()
            .flatMap { clazz -> clazz.declaredConstructors.asSequence() }
            .singleOrNull { constructor ->
                val types = constructor.parameterTypes
                types.size == 7 &&
                    types[0] == String::class.java &&
                    types[1] == String::class.java &&
                    isIntType(types[2]) &&
                    isIntType(types[3]) &&
                    types[4] == Long::class.javaPrimitiveType &&
                    java.util.HashMap::class.java.isAssignableFrom(types[5]) &&
                    types[6] == android.webkit.WebView::class.java
            }
    }

    fun featureStatusMap(symbols: HookSymbols?): Map<String, HookFeatureStatus> {
        val source = symbols ?: HookSymbols(source = "unsupported")
        if (source.featureStatusMap.isEmpty()) return deriveFeatureStatusMap(source)
        return LinkedHashMap<String, HookFeatureStatus>(deriveFeatureStatusMap(source)).apply {
            putAll(source.featureStatusMap)
        }
    }

    fun isScanVersionCheckFailed(symbols: HookSymbols?): Boolean {
        return symbols != null && symbols.scanSupportState != ScanSupportState.SUPPORTED
    }

    fun hasScanErrors(symbols: HookSymbols?, failedLines: List<String>? = null): Boolean {
        if (symbols == null) return true
        if (symbols.source != "scan") return true
        if (symbols.scanErrors.isNotEmpty()) return true
        val missingLines = failedLines ?: formatHookPointStatusLines(symbols).filter { it.contains("MISSING") }
        return missingLines.isNotEmpty()
    }

    fun formatScanVersionWarning(symbols: HookSymbols?): String? {
        if (!isScanVersionCheckFailed(symbols)) return null
        return UiText.Settings.scanTiebaVersionNotAdapted(TARGET_TIEBA_VERSION_NAME)
    }

    fun formatScanFeatureWarning(): String {
        return UiText.Settings.scanFeatureAbnormal(TARGET_TIEBA_VERSION_NAME)
    }

    fun formatFeatureStatusLines(symbols: HookSymbols?): List<String> {
        val statusMap = featureStatusMap(symbols)
        return ALL_FEATURE_KEYS.map { key ->
            val status = statusMap[key] ?: HookFeatureStatus()
            val critical = if (status.missingCritical.isEmpty()) "-" else status.missingCritical.joinToString(",")
            val optional = if (status.missingOptional.isEmpty()) "-" else status.missingOptional.joinToString(",")
            "Feature[$key] state=${status.state} critical=$critical optional=$optional"
        }
    }

    fun formatHookPointStatusLines(symbols: HookSymbols?): List<String> {
        if (symbols == null) {
            return listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-")
        }

        val out = ArrayList<String>(48)

        fun has(value: String?): Boolean = !value.isNullOrBlank()
        fun has(value: Int?): Boolean = value != null && value != 0
        fun hasList(values: List<String>?): Boolean = values.orEmpty().any { it.isNotBlank() }
        fun hasIntList(values: List<Int>?): Boolean = values.orEmpty().any { it != 0 }
        fun listTarget(values: List<String>?): String {
            return values.orEmpty().filter { it.isNotBlank() }.joinToString(",").ifBlank { "-" }
        }
        fun resourceListTarget(values: List<Int>?): String {
            val ids = values.orEmpty().filter { it != 0 }
            return if (ids.isEmpty()) "-" else "count=${ids.size}"
        }
        fun add(name: String, target: String, checks: List<Pair<String, Boolean>>) {
            val missing = checks.asSequence()
                .filter { !it.second }
                .map { it.first }
                .toList()
            val state = if (missing.isEmpty()) "FOUND" else "MISSING"
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add("HookPoint[$name] state=$state missing=$missingText target=$target")
        }
        fun addOptional(name: String, target: String, checks: List<Pair<String, Boolean>>) {
            val missing = checks.asSequence()
                .filter { !it.second }
                .map { it.first }
                .toList()
            val state = if (missing.isEmpty()) "FOUND" else "PARTIAL"
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add("HookPoint[$name] state=$state missing=$missingText target=$target")
        }

        add(
            "SettingsMenuHook",
            "${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}]",
            listOf(
                "settingsClass" to has(symbols.settingsClass),
                "settingsInitMethod" to has(symbols.settingsInitMethod),
                "settingsContainerField" to has(symbols.settingsContainerField),
            ),
        )
        add(
            "HomeTabHook",
            "${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}]",
            listOf(
                "homeTabClass" to has(symbols.homeTabClass),
                "homeTabRebuildMethod" to has(symbols.homeTabRebuildMethod),
                "homeTabListField" to has(symbols.homeTabListField),
                "homeTabItemTypeField" to has(symbols.homeTabItemTypeField),
                "homeTabItemCodeField" to has(symbols.homeTabItemCodeField),
                "homeTabItemNameField" to has(symbols.homeTabItemNameField),
                "homeTabItemUrlField" to has(symbols.homeTabItemUrlField),
                "homeTabItemMainSetterMethod" to has(symbols.homeTabItemMainSetterMethod),
                "homeTabItemMainIntField" to has(symbols.homeTabItemMainIntField),
                "homeTabItemMainBooleanField" to has(symbols.homeTabItemMainBooleanField),
            ),
        )
        add(
            "StrategyAdHook.HomePersonalizeAnchors",
            "anchors={${listTarget(symbols.homePersonalizeAnchorClasses)}}",
            listOf("homePersonalizeAnchorClasses" to hasList(symbols.homePersonalizeAnchorClasses)),
        )
        add(
            "FeedAdHook.TemplateKey",
            "Feed item.${symbols.feedTemplateKeyMethod}()",
            listOf("feedTemplateKeyMethod" to has(symbols.feedTemplateKeyMethod)),
        )
        add(
            "CustomPostCardBlockHook.Payload",
            "Feed item.${symbols.feedTemplatePayloadMethod}()",
            listOf("feedTemplatePayloadMethod" to has(symbols.feedTemplatePayloadMethod)),
        )
        add(
            "FeedAdHook.LoadMore",
            "com.baidu.tieba.feed.list.FeedTemplateAdapter.${symbols.feedTemplateLoadMoreMethod}(List)",
            listOf("feedTemplateLoadMoreMethod" to has(symbols.feedTemplateLoadMoreMethod)),
        )
        add(
            "CustomPostCardBlockHook",
            "com.baidu.tieba.feed.list.TemplateAdapter.setList / FeedTemplateAdapter.${symbols.feedTemplateLoadMoreMethod}",
            listOf(
                "feedTemplateKeyMethod" to has(symbols.feedTemplateKeyMethod),
                "feedTemplatePayloadMethod" to has(symbols.feedTemplatePayloadMethod),
                "feedTemplateLoadMoreMethod" to has(symbols.feedTemplateLoadMoreMethod),
                "feedCardDataListField" to has(symbols.feedCardDataListField),
            ),
        )
        add(
            "CustomPostCardBlockHook.HeadParams",
            "Feed head params.${symbols.feedHeadParamsField}",
            listOf("feedHeadParamsField" to has(symbols.feedHeadParamsField)),
        )
        add(
            "CustomPostCardBlockHook.RecommendCard",
            "RecommendCardUiState.${symbols.feedRecommendCardNestedDataMethod}()[NestedData.${symbols.feedRecommendCardNestedDataListField}]",
            listOf(
                "feedRecommendCardNestedDataMethod" to has(symbols.feedRecommendCardNestedDataMethod),
                "feedRecommendCardNestedDataListField" to has(symbols.feedRecommendCardNestedDataListField),
            ),
        )
        add(
            "FeedInfoLogHook.Bind",
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols.feedCardBindMethod}",
            listOf("feedCardBindMethod" to has(symbols.feedCardBindMethod)),
        )
        add(
            "HomeNativeGlassHook",
            "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.<init> / " +
                "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols.feedCardBindMethod}",
            listOf("feedCardBindMethod" to has(symbols.feedCardBindMethod)),
        )
        add(
            "HomeNativeGlassHook.Resources",
            "subPbNext=${formatResourceId(symbols.homeNativeGlassSubPbNextPageMoreViewId)}, " +
                "titleDivider=${formatResourceId(symbols.homeNativeGlassPbReplyTitleDividerViewId)}",
            listOf(
                "homeNativeGlassSubPbNextPageMoreViewId" to has(symbols.homeNativeGlassSubPbNextPageMoreViewId),
                "homeNativeGlassPbReplyTitleDividerViewId" to has(symbols.homeNativeGlassPbReplyTitleDividerViewId),
            ),
        )
        add(
            "HomeNativeGlassHook.DynamicBackgroundColors",
            resourceListTarget(symbols.homeNativeGlassDynamicBackgroundColorIds),
            listOf(
                "homeNativeGlassDynamicBackgroundColorIds" to
                    hasIntList(symbols.homeNativeGlassDynamicBackgroundColorIds),
            ),
        )
        add(
            "HomeNativeGlassHook.SortSwitchBackground",
            "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}.${symbols.homeNativeGlassSortSwitchBackgroundPaintField}",
            listOf(
                "homeNativeGlassSortSwitchBackgroundPaintField" to
                    has(symbols.homeNativeGlassSortSwitchBackgroundPaintField),
            ),
        )
        add(
            "HomeNativeGlassHook.SortSwitchSelectedSlide",
            "${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}." +
                "${symbols.homeNativeGlassSortSwitchSlideDrawMethod}(Canvas)" +
                "[${symbols.homeNativeGlassSortSwitchSlidePathField}]",
            listOf(
                "homeNativeGlassSortSwitchSlideDrawMethod" to
                    has(symbols.homeNativeGlassSortSwitchSlideDrawMethod),
                "homeNativeGlassSortSwitchSlidePathField" to
                    has(symbols.homeNativeGlassSortSwitchSlidePathField),
            ),
        )
        add(
            "HomeNativeGlassHook.EnterForumCapsule",
            "${symbols.homeNativeGlassEnterForumCapsuleControllerClass}." +
                "${symbols.homeNativeGlassEnterForumCapsuleInitMethod}/" +
                "${symbols.homeNativeGlassEnterForumCapsuleRefreshMethod}" +
                "[${symbols.homeNativeGlassEnterForumCapsuleViewField}]",
            listOf(
                "homeNativeGlassEnterForumCapsuleControllerClass" to
                    has(symbols.homeNativeGlassEnterForumCapsuleControllerClass),
                "homeNativeGlassEnterForumCapsuleInitMethod" to
                    has(symbols.homeNativeGlassEnterForumCapsuleInitMethod),
                "homeNativeGlassEnterForumCapsuleRefreshMethod" to
                    has(symbols.homeNativeGlassEnterForumCapsuleRefreshMethod),
                "homeNativeGlassEnterForumCapsuleViewField" to
                    has(symbols.homeNativeGlassEnterForumCapsuleViewField),
                "homeNativeGlassEnterForumCapsuleTitleField" to
                    has(symbols.homeNativeGlassEnterForumCapsuleTitleField),
            ),
        )
        add(
            "HomeNativeGlassHook.CommonLayoutPreloader",
            "${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}.${symbols.pbCommonLayoutPreloaderGetOrDefaultMethod}",
            listOf(
                "pbCommonLayoutPreloaderGetOrDefaultMethod" to
                    has(symbols.pbCommonLayoutPreloaderGetOrDefaultMethod),
            ),
        )
        add(
            "StrategyAdHook.Splash",
            "${symbols.splashAdHelperClass}.${symbols.splashAdHelperMethod}",
            listOf(
                "splashAdHelperClass" to has(symbols.splashAdHelperClass),
                "splashAdHelperMethod" to has(symbols.splashAdHelperMethod),
            ),
        )
        add(
            "StrategyAdHook.CloseAd",
            "${symbols.closeAdDataClass}.{${symbols.closeAdDataMethodG1},${symbols.closeAdDataMethodJ1}}",
            listOf(
                "closeAdDataClass" to has(symbols.closeAdDataClass),
                "closeAdDataMethodG1" to has(symbols.closeAdDataMethodG1),
                "closeAdDataMethodJ1" to has(symbols.closeAdDataMethodJ1),
            ),
        )
        add(
            "StrategyAdHook.Zga",
            "${symbols.zgaClass}.{${listTarget(symbols.zgaMethods)}}",
            listOf(
                "zgaClass" to has(symbols.zgaClass),
                "zgaMethods" to hasList(symbols.zgaMethods),
            ),
        )
        add(
            "SearchBoxTextAdHook.Hint",
            "${symbols.searchBoxViewClass}.${symbols.searchBoxSetHintMethod}",
            listOf(
                "searchBoxViewClass" to has(symbols.searchBoxViewClass),
                "searchBoxSetHintMethod" to has(symbols.searchBoxSetHintMethod),
            ),
        )
        add(
            "SearchBoxTextAdHook.Owner",
            "${symbols.homeSearchBoxOwnerClass}.{${symbols.homeSearchBoxInitMethod},${symbols.homeSearchBoxGetterMethod}}",
            listOf(
                "homeSearchBoxOwnerClass" to has(symbols.homeSearchBoxOwnerClass),
                "homeSearchBoxInitMethod" to has(symbols.homeSearchBoxInitMethod),
                "homeSearchBoxGetterMethod" to has(symbols.homeSearchBoxGetterMethod),
            ),
        )
        add(
            "HomeTopBarRightSlotHook",
            "${symbols.homeRightSlotClass}.{${listTarget(symbols.homeRightSlotStateMethods)}}",
            listOf(
                "homeRightSlotClass" to has(symbols.homeRightSlotClass),
                "homeRightSlotStateMethods" to hasList(symbols.homeRightSlotStateMethods),
            ),
        )
        add(
            "PbFallingAdHook",
            "${symbols.pbFallingViewClass}.{${symbols.pbFallingInitMethod},${symbols.pbFallingShowMethod},${symbols.pbFallingClearMethod}}",
            listOf(
                "pbFallingViewClass" to has(symbols.pbFallingViewClass),
                "pbFallingInitMethod" to has(symbols.pbFallingInitMethod),
                "pbFallingShowMethod" to has(symbols.pbFallingShowMethod),
                "pbFallingClearMethod" to has(symbols.pbFallingClearMethod),
            ),
        )
        add(
            "PbEarlyAdBlockHook",
            "${symbols.pbEarlyAdInsertClass}.{${listTarget(symbols.pbEarlyAdInsertMethodSpecs)}}",
            listOf(
                "pbEarlyAdInsertClass" to has(symbols.pbEarlyAdInsertClass),
                "pbEarlyAdInsertMethodSpecs" to hasList(symbols.pbEarlyAdInsertMethodSpecs),
            ),
        )
        add(
            "PbAdRequestBlockHook.AdBid.Common",
            "common=${symbols.pbAdBidCommonRequestModelClass}.{${listTarget(symbols.pbAdBidCommonRequestStartMethods)}}#${symbols.pbAdBidCommonRequestNotifyMethod}",
            listOf(
                "pbAdBidCommonRequestModelClass" to has(symbols.pbAdBidCommonRequestModelClass),
                "pbAdBidCommonRequestStartMethods" to hasList(symbols.pbAdBidCommonRequestStartMethods),
                "pbAdBidCommonRequestNotifyMethod" to has(symbols.pbAdBidCommonRequestNotifyMethod),
            ),
        )
        addOptional(
            "PbAdRequestBlockHook.AdBid.PageBrowser",
            "pageBrowser=" + if (
                has(symbols.pbAdBidPageBrowserRequestModelClass) &&
                has(symbols.pbAdBidPageBrowserRequestDataMethod)
            ) {
                "${symbols.pbAdBidPageBrowserRequestModelClass}.${symbols.pbAdBidPageBrowserRequestDataMethod}"
            } else {
                "optional-absent"
            },
            listOf(
                "pbAdBidPageBrowserRequestModelClass" to has(symbols.pbAdBidPageBrowserRequestModelClass),
                "pbAdBidPageBrowserRequestDataMethod" to has(symbols.pbAdBidPageBrowserRequestDataMethod),
            ),
        )
        add(
            "PostAdHook.DataFilter",
            "${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols.typeAdapterSetDataMethod}(List) " +
                "item=${symbols.typeAdapterDataItemClass}.${symbols.typeAdapterDataGetTypeMethod}()",
            listOf(
                "typeAdapterSetDataMethod" to has(symbols.typeAdapterSetDataMethod),
                "typeAdapterDataItemClass" to has(symbols.typeAdapterDataItemClass),
                "typeAdapterDataGetTypeMethod" to has(symbols.typeAdapterDataGetTypeMethod),
            ),
        )
        val enterForumInitInfoReady =
            has(symbols.enterForumInitInfoDataClass) &&
                has(symbols.enterForumInitInfoGetUrlMethod)
        val enterForumWebLoadReady =
            has(symbols.enterForumWebControllerClass) &&
                has(symbols.enterForumWebLoadMethod)
        add(
            "EnterForumWebHook",
            "source=${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod} " +
                "webLoad=${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}(String)",
            listOf(
                "enterForumUrlSourceOrWebLoadTarget" to (enterForumInitInfoReady || enterForumWebLoadReady),
            ),
        )
        addOptional(
            "EnterForumWebHook.InitInfoData",
            "${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod}()",
            listOf(
                "enterForumInitInfoDataClass" to has(symbols.enterForumInitInfoDataClass),
                "enterForumInitInfoGetUrlMethod" to has(symbols.enterForumInitInfoGetUrlMethod),
            ),
        )
        addOptional(
            "EnterForumWebHook.WebLoad",
            "${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}(String)",
            listOf(
                "enterForumWebControllerClass" to has(symbols.enterForumWebControllerClass),
                "enterForumWebLoadMethod" to has(symbols.enterForumWebLoadMethod),
            ),
        )
        val plainUrlDirectHookReady =
            has(symbols.plainUrlClickableSpanClass) &&
                has(symbols.plainUrlClickableSpanOnClickMethod) &&
                hasList(symbols.plainUrlClickableSpanOnClickOwnerClasses) &&
                has(symbols.plainUrlClickableSpanTypeField) &&
                has(symbols.plainUrlClickableSpanUrlField) &&
                has(symbols.plainUrlClickableSpanTextField)
        val plainUrlMessageHookReady =
            has(symbols.plainUrlMessageManagerClass) &&
                has(symbols.plainUrlMessageDispatchMethod) &&
                has(symbols.plainUrlResponsedMessageClass) &&
                has(symbols.plainUrlResponsedMessageGetCmdMethod) &&
                has(symbols.plainUrlCustomResponsedMessageClass) &&
                has(symbols.plainUrlCustomResponsedMessageGetDataMethod) &&
                has(symbols.plainUrlApplicationClass) &&
                has(symbols.plainUrlApplicationGetInstMethod)
        add(
            "PlainUrlDirectBrowserHook",
            "direct=${listTarget(symbols.plainUrlClickableSpanOnClickOwnerClasses)}.${symbols.plainUrlClickableSpanOnClickMethod}(View)[${symbols.plainUrlClickableSpanTypeField},${symbols.plainUrlClickableSpanUrlField},${symbols.plainUrlClickableSpanTextField}] message=${symbols.plainUrlMessageManagerClass}.${symbols.plainUrlMessageDispatchMethod}(${symbols.plainUrlResponsedMessageClass})",
            listOf(
                "plainUrlDirectHookOrMessage" to (plainUrlDirectHookReady || plainUrlMessageHookReady),
            ),
        )
        add(
            "PlainUrlDirectBrowserHook.MountCard",
            "${symbols.mountCardLinkLayoutClass}.${symbols.mountCardLinkLayoutOnClickMethod}(View)[${symbols.mountCardLinkLayoutDataField}->${symbols.mountCardLinkInfoDataClass}.${symbols.mountCardLinkInfoGetUrlMethod}]",
            listOf(
                "mountCardLinkLayoutClass" to has(symbols.mountCardLinkLayoutClass),
                "mountCardLinkLayoutOnClickMethod" to has(symbols.mountCardLinkLayoutOnClickMethod),
                "mountCardLinkLayoutDataField" to has(symbols.mountCardLinkLayoutDataField),
                "mountCardLinkInfoDataClass" to has(symbols.mountCardLinkInfoDataClass),
                "mountCardLinkInfoGetUrlMethod" to has(symbols.mountCardLinkInfoGetUrlMethod),
            ),
        )
        add(
            "ForumNativeTopShiftBlockHook",
            "${symbols.forumBottomSheetViewClass}.${symbols.forumBottomSheetInitScrollMethod}",
            listOf(
                "forumBottomSheetViewClass" to has(symbols.forumBottomSheetViewClass),
                "forumBottomSheetInitScrollMethod" to has(symbols.forumBottomSheetInitScrollMethod),
            ),
        )
        add(
            "AutoRefreshHook",
            "$PERSONALIZE_PAGE_VIEW_CLASS.${symbols.autoRefreshTriggerMethod}",
            listOf("autoRefreshTriggerMethod" to has(symbols.autoRefreshTriggerMethod)),
        )
        add(
            "AutoLoadMoreHook.Config",
            "${symbols.autoLoadMoreConfigClass}.${symbols.autoLoadMoreConfigMethod}",
            listOf(
                "autoLoadMoreConfigClass" to has(symbols.autoLoadMoreConfigClass),
                "autoLoadMoreConfigMethod" to has(symbols.autoLoadMoreConfigMethod),
            ),
        )
        add(
            "PbScrollCoalesceHook",
            "${symbols.pbCommentScrollListenerClass}.${symbols.pbCommentScrollMethod}",
            listOf(
                "pbCommentScrollListenerClass" to has(symbols.pbCommentScrollListenerClass),
                "pbCommentScrollMethod" to has(symbols.pbCommentScrollMethod),
            ),
        )
        run {
            val listChecks = listOf(
                "pbCommentBottomListScrollClass" to has(symbols.pbCommentBottomListScrollClass),
                "pbCommentBottomListScrollMethod" to has(symbols.pbCommentBottomListScrollMethod),
                "pbCommentBottomListOwnerField" to has(symbols.pbCommentBottomListOwnerField),
            )
            val recyclerChecks = listOf(
                "pbCommentBottomRecyclerScrollClass" to has(symbols.pbCommentBottomRecyclerScrollClass),
                "pbCommentBottomRecyclerScrollMethod" to has(symbols.pbCommentBottomRecyclerScrollMethod),
                "pbCommentBottomRecyclerOwnerField" to has(symbols.pbCommentBottomRecyclerOwnerField),
            )
            val listFound = listChecks.all { it.second }
            val recyclerFound = recyclerChecks.all { it.second }
            val anyBottomSymbol = (listChecks + recyclerChecks).any { it.second }
            val missing = when {
                listFound || recyclerFound -> emptyList()
                anyBottomSymbol -> (listChecks + recyclerChecks).filter { !it.second }.map { it.first }
                else -> listOf("pbCommentBottomMechanism")
            }
            val state = when {
                listFound || recyclerFound -> "FOUND"
                anyBottomSymbol -> "PARTIAL"
                else -> "MISSING"
            }
            val missingText = if (missing.isEmpty()) "-" else missing.joinToString(",")
            out.add(
                "HookPoint[PbCommentAutoLoadHook] state=$state missing=$missingText target=" +
                    "${symbols.pbCommentBottomListScrollClass}.${symbols.pbCommentBottomListScrollMethod} / " +
                    "${symbols.pbCommentBottomRecyclerScrollClass}.${symbols.pbCommentBottomRecyclerScrollMethod}",
            )
        }
        add(
            "PbDisableGestureFontScaleHook.Manager",
            "${symbols.pbGestureScaleManagerClass}.${symbols.pbGestureScaleDispatchMethod}/${symbols.pbGestureScaleListenerSetterMethod}",
            listOf(
                "pbGestureScaleManagerClass" to has(symbols.pbGestureScaleManagerClass),
                "pbGestureScaleDispatchMethod" to has(symbols.pbGestureScaleDispatchMethod),
                "pbGestureScaleListenerSetterMethod" to has(symbols.pbGestureScaleListenerSetterMethod),
            ),
        )
        add(
            "PbDisableGestureFontScaleHook.Listener",
            "${symbols.pbGestureScaleListenerClass}.${symbols.pbGestureScaleListenerOnScaleMethod}",
            listOf(
                "pbGestureScaleListenerClass" to has(symbols.pbGestureScaleListenerClass),
                "pbGestureScaleListenerOnScaleMethod" to has(symbols.pbGestureScaleListenerOnScaleMethod),
            ),
        )
        add(
            "AiComponentDisableHook.SpriteMeme",
            "${symbols.aiSpriteMemePanControllerClass}.${symbols.aiSpriteMemeEnableMethod}",
            listOf(
                "aiSpriteMemePanControllerClass" to has(symbols.aiSpriteMemePanControllerClass),
                "aiSpriteMemeEnableMethod" to has(symbols.aiSpriteMemeEnableMethod),
            ),
        )
        add(
            "AiComponentDisableHook.PbNewInputContainer",
            "${symbols.aiPbNewInputContainerClass}.{${symbols.aiPbNewInputContainerInitSpriteMemeMethod},${symbols.aiPbNewInputContainerInitAiWriteMethod}}",
            listOf(
                "aiPbNewInputContainerClass" to has(symbols.aiPbNewInputContainerClass),
                "aiPbNewInputContainerInitSpriteMemeMethod" to has(symbols.aiPbNewInputContainerInitSpriteMemeMethod),
                "aiPbNewInputContainerInitAiWriteMethod" to has(symbols.aiPbNewInputContainerInitAiWriteMethod),
            ),
        )
        add(
            "AiComponentDisableHook.PbAiEmojiCreation",
            "$AI_PB_AI_EMOJI_CREATION_VIEW_CLASS.${symbols.aiPbAiEmojiCreationViewBindMethod}",
            listOf(
                "aiPbAiEmojiCreationViewBindMethod" to has(symbols.aiPbAiEmojiCreationViewBindMethod),
            ),
        )
        if (has(symbols.aiPbPageBrowserAiEmojiCreationBindMethod)) {
            add(
                "AiComponentDisableHook.PbPageBrowserAiEmojiCreation",
                "$AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS.${symbols.aiPbPageBrowserAiEmojiCreationBindMethod}",
                listOf(
                    "aiPbPageBrowserAiEmojiCreationBindMethod" to true,
                ),
            )
        }
        add(
            "AiComponentDisableHook.ImageViewerJumpButton",
            "${symbols.aiImageViewerJumpButtonOwnerClass}.${symbols.aiImageViewerJumpButtonInitMethod}",
            listOf(
                "aiImageViewerJumpButtonOwnerClass" to has(symbols.aiImageViewerJumpButtonOwnerClass),
                "aiImageViewerJumpButtonInitMethod" to has(symbols.aiImageViewerJumpButtonInitMethod),
            ),
        )
        add(
            "MsgTabDefaultNotifyHook",
            "$MSG_TAB_VIEW_MODEL_CLASS.${symbols.msgTabLocateToTabMethod}(long,String)",
            listOf("msgTabLocateToTabMethod" to has(symbols.msgTabLocateToTabMethod)),
        )
        add(
            "MsgTabDefaultNotifyHook.Container",
            "${MSG_TAB_CONTAINER_VIEW_CLASS}.${symbols.msgTabContainerSelectMethod}[${symbols.msgTabContainerExtDataField}]",
            listOf(
                "msgTabContainerSelectMethod" to has(symbols.msgTabContainerSelectMethod),
                "msgTabContainerExtDataField" to has(symbols.msgTabContainerExtDataField),
            ),
        )
        add(
            "PrivateReadReceiptBlockHook",
            "${symbols.privateReadReceiptModelClass}.${symbols.privateReadReceiptModelReadDispatchMethod} / " +
                "${symbols.privateReadReceiptMessageManagerClass}.${symbols.privateReadReceiptMessageSendMethod}" +
                "(${symbols.privateReadReceiptMessageBaseClass}) -> " +
                "${symbols.privateReadReceiptRequestClass}[${symbols.privateReadReceiptRequestMsgIdField},${symbols.privateReadReceiptRequestToUidField}]",
            listOf(
                "privateReadReceiptModelClass" to has(symbols.privateReadReceiptModelClass),
                "privateReadReceiptModelReadDispatchMethod" to has(symbols.privateReadReceiptModelReadDispatchMethod),
                "privateReadReceiptMessageManagerClass" to has(symbols.privateReadReceiptMessageManagerClass),
                "privateReadReceiptMessageManagerGetInstanceMethod" to has(symbols.privateReadReceiptMessageManagerGetInstanceMethod),
                "privateReadReceiptMessageSendMethod" to has(symbols.privateReadReceiptMessageSendMethod),
                "privateReadReceiptMessageBaseClass" to has(symbols.privateReadReceiptMessageBaseClass),
                "privateReadReceiptRequestClass" to has(symbols.privateReadReceiptRequestClass),
                "privateReadReceiptModelBaseClass" to has(symbols.privateReadReceiptModelBaseClass),
                "privateReadReceiptCommitResponseClass" to has(symbols.privateReadReceiptCommitResponseClass),
                "privateReadReceiptProcessAckMethod" to has(symbols.privateReadReceiptProcessAckMethod),
                "privateReadReceiptResponseErrorMethod" to has(symbols.privateReadReceiptResponseErrorMethod),
                "privateReadReceiptRequestMsgIdField" to has(symbols.privateReadReceiptRequestMsgIdField),
                "privateReadReceiptRequestToUidField" to has(symbols.privateReadReceiptRequestToUidField),
                "privateReadReceiptModelDataField" to has(symbols.privateReadReceiptModelDataField),
                "privateReadReceiptPageDataClass" to has(symbols.privateReadReceiptPageDataClass),
                "privateReadReceiptPageDataChatListMethod" to has(symbols.privateReadReceiptPageDataChatListMethod),
                "privateReadReceiptChatMessageClass" to has(symbols.privateReadReceiptChatMessageClass),
                "privateReadReceiptChatMessageMsgIdMethod" to has(symbols.privateReadReceiptChatMessageMsgIdMethod),
                "privateReadReceiptChatMessageUserIdMethod" to has(symbols.privateReadReceiptChatMessageUserIdMethod),
                "privateReadReceiptChatMessageLocalDataMethod" to has(symbols.privateReadReceiptChatMessageLocalDataMethod),
                "privateReadReceiptLocalDataClass" to has(symbols.privateReadReceiptLocalDataClass),
                "privateReadReceiptLocalDataStatusMethod" to has(symbols.privateReadReceiptLocalDataStatusMethod),
                "privateReadReceiptAccountClass" to has(symbols.privateReadReceiptAccountClass),
                "privateReadReceiptCurrentAccountMethod" to has(symbols.privateReadReceiptCurrentAccountMethod),
            ),
        )
        add(
            "FreeCopyHook.Popup",
            "${symbols.freeCopyPopupMenuClass}.${symbols.freeCopyPopupContentViewMethod}[${symbols.freeCopyPopupTextField}]",
            listOf(
                "freeCopyPopupMenuClass" to has(symbols.freeCopyPopupMenuClass),
                "freeCopyPopupContentViewMethod" to has(symbols.freeCopyPopupContentViewMethod),
                "freeCopyPopupTextField" to has(symbols.freeCopyPopupTextField),
            ),
        )
        add(
            "CollectionSearchHook.Core",
            "${symbols.collectionPresenterField}.${symbols.collectionPresenterListSetterMethod}",
            listOf(
                "collectionPresenterField" to has(symbols.collectionPresenterField),
                "collectionPresenterListSetterMethod" to has(symbols.collectionPresenterListSetterMethod),
                "collectionPresenterAdapterField" to has(symbols.collectionPresenterAdapterField),
                "collectionModelField" to has(symbols.collectionModelField),
                "collectionModelListGetterMethod" to has(symbols.collectionModelListGetterMethod),
                "collectionModelParseMethod" to has(symbols.collectionModelParseMethod),
            ),
        )
        add(
            "CollectionSearchHook.List",
            "${symbols.collectionModelListField}/${symbols.collectionFragmentDisplayListField}",
            listOf(
                "collectionModelListField" to has(symbols.collectionModelListField),
                "collectionFragmentDisplayListField" to has(symbols.collectionFragmentDisplayListField),
                "collectionActivityNavControllerField" to has(symbols.collectionActivityNavControllerField),
                "collectionNavBarField" to has(symbols.collectionNavBarField),
            ),
        )
        add(
            "CollectionSearchHook.Adapter",
            "${symbols.collectionPresenterAdapterField}.{${symbols.collectionAdapterShowFooterMethod},${symbols.collectionAdapterLoadingMethod},${symbols.collectionAdapterHasMoreMethod}}",
            listOf(
                "collectionAdapterShowFooterMethod" to has(symbols.collectionAdapterShowFooterMethod),
                "collectionAdapterLoadingMethod" to has(symbols.collectionAdapterLoadingMethod),
                "collectionAdapterHasMoreMethod" to has(symbols.collectionAdapterHasMoreMethod),
                "collectionEditModeMethod" to has(symbols.collectionEditModeMethod),
            ),
        )
        add(
            "HistorySearchHook",
            "${symbols.historyAdapterField}.${symbols.historyAdapterSetListMethod}[${symbols.historyListField}]",
            listOf(
                "historyAdapterField" to has(symbols.historyAdapterField),
                "historyAdapterSetListMethod" to has(symbols.historyAdapterSetListMethod),
                "historyListField" to has(symbols.historyListField),
                "historyActivityListUpdateMethod" to has(symbols.historyActivityListUpdateMethod),
                "historyActivityNavBarField" to has(symbols.historyActivityNavBarField),
                "historyThreadNameMethod" to has(symbols.historyThreadNameMethod),
                "historyForumNameMethod" to has(symbols.historyForumNameMethod),
                "historyUserNameMethod" to has(symbols.historyUserNameMethod),
                "historyDescriptionMethod" to has(symbols.historyDescriptionMethod),
                "historyThreadIdMethod" to has(symbols.historyThreadIdMethod),
                "historyPostIdMethod" to has(symbols.historyPostIdMethod),
                "historyLiveIdMethod" to has(symbols.historyLiveIdMethod),
            ),
        )
        add(
            "MainTabBottomHook",
            "${symbols.mainTabDataClass}.${symbols.mainTabAddMethod}/${symbols.mainTabGetListMethod}",
            listOf(
                "mainTabDataClass" to has(symbols.mainTabDataClass),
                "mainTabAddMethod" to has(symbols.mainTabAddMethod),
                "mainTabGetListMethod" to has(symbols.mainTabGetListMethod),
                "mainTabDelegateGetStructureMethod" to has(symbols.mainTabDelegateGetStructureMethod),
                "mainTabStructureTypeField" to has(symbols.mainTabStructureTypeField),
            ),
        )
        add(
            "MainTabBottomHook.StructureFields",
            "${symbols.mainTabDelegateGetStructureMethod}[${symbols.mainTabStructureDynamicIconField},${symbols.mainTabStructureFragmentField}]",
            listOf(
                "mainTabStructureDynamicIconField" to has(symbols.mainTabStructureDynamicIconField),
                "mainTabStructureFragmentField" to has(symbols.mainTabStructureFragmentField),
            ),
        )
        add(
            "DefaultOriginalImageHook",
            "${symbols.origImageUrlDragImageViewClass}.${symbols.origImageTriggerMethod}",
            listOf(
                "origImageUrlDragImageViewClass" to has(symbols.origImageUrlDragImageViewClass),
                "origImageDataClass" to has(symbols.origImageDataClass),
                "origImageTriggerMethod" to has(symbols.origImageTriggerMethod),
                "origImageAssistDataMethod" to has(symbols.origImageAssistDataMethod),
                "origImageShowButtonField" to has(symbols.origImageShowButtonField),
                "origImageBlockedField" to has(symbols.origImageBlockedField),
                "origImageOriginalProcessField" to has(symbols.origImageOriginalProcessField),
                "origImageOriginalUrlField" to has(symbols.origImageOriginalUrlField),
            ),
        )
        add(
            "DefaultOriginalImageHook.PrimaryItem",
            "${symbols.origImagePagerAdapterClass}.${symbols.origImageSetPrimaryItemMethod}",
            listOf(
                "origImagePagerAdapterClass" to has(symbols.origImagePagerAdapterClass),
                "origImageSetPrimaryItemMethod" to has(symbols.origImageSetPrimaryItemMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.UrlDragExtra",
            "${symbols.origImageUrlDragImageViewClass}.{${symbols.origImageSetAssistUrlMethod},${symbols.origImagePrimaryReadyMethod},${symbols.origImageDirectStartMethod},${symbols.origImageOriginTextMethod}}",
            listOf(
                "origImageSetAssistUrlMethod" to has(symbols.origImageSetAssistUrlMethod),
                "origImagePrimaryReadyMethod" to has(symbols.origImagePrimaryReadyMethod),
                "origImageDirectStartMethod" to has(symbols.origImageDirectStartMethod),
                "origImageOriginTextMethod" to has(symbols.origImageOriginTextMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.SharedPrefs",
            "${symbols.origImageSharedPrefHelperClass}.{${symbols.origImageSharedPrefGetInstanceMethod},${symbols.origImageSharedPrefPutBooleanMethod}}",
            listOf(
                "origImageSharedPrefHelperClass" to has(symbols.origImageSharedPrefHelperClass),
                "origImageSharedPrefGetInstanceMethod" to has(symbols.origImageSharedPrefGetInstanceMethod),
                "origImageSharedPrefPutBooleanMethod" to has(symbols.origImageSharedPrefPutBooleanMethod),
            ),
        )
        add(
            "DefaultOriginalImageHook.Md5",
            "${symbols.origImageMd5Class}.${symbols.origImageMd5Method}",
            listOf(
                "origImageMd5Class" to has(symbols.origImageMd5Class),
                "origImageMd5Method" to has(symbols.origImageMd5Method),
            ),
        )
        add(
            "ShareTrackingParamCleanerHook",
            "${symbols.shareTrackBuilderClass}.${symbols.shareTrackBuildUrlMethod}",
            listOf(
                "shareTrackBuilderClass" to has(symbols.shareTrackBuilderClass),
                "shareTrackBuildUrlMethod" to has(symbols.shareTrackBuildUrlMethod),
            ),
        )
        add(
            "ShareTrackingParamCleanerHook.AppendQuery",
            "${symbols.shareTrackBuilderClass}.${symbols.shareTrackAppendQueryMethod}",
            listOf("shareTrackAppendQueryMethod" to has(symbols.shareTrackAppendQueryMethod)),
        )
        add(
            "ImageViewerNativeShareHook",
            "${symbols.imageViewerShareConfigClass}.${symbols.imageViewerShareAddOutsideMethod}",
            listOf(
                "imageViewerShareConfigClass" to has(symbols.imageViewerShareConfigClass),
                "imageViewerShareIsDialogField" to has(symbols.imageViewerShareIsDialogField),
                "imageViewerShareItemField" to has(symbols.imageViewerShareItemField),
                "imageViewerShareAddOutsideMethod" to has(symbols.imageViewerShareAddOutsideMethod),
                "imageViewerShareGetRequestDataMethod" to has(symbols.imageViewerShareGetRequestDataMethod),
                "imageViewerShareSetRequestDataMethod" to has(symbols.imageViewerShareSetRequestDataMethod),
                "imageViewerShareGetContextMethod" to has(symbols.imageViewerShareGetContextMethod),
                "imageViewerShareItemClass" to has(symbols.imageViewerShareItemClass),
                "imageViewerShareItemImageUriField" to has(symbols.imageViewerShareItemImageUriField),
                "imageViewerShareItemViewClass" to has(symbols.imageViewerShareItemViewClass),
                "imageViewerShareItemNameByResMethod" to has(symbols.imageViewerShareItemNameByResMethod),
                "imageViewerShareItemNameByTextMethod" to has(symbols.imageViewerShareItemNameByTextMethod),
                "imageViewerShareIconResId" to has(symbols.imageViewerShareIconResId),
            ),
        )
        add(
            "ImageViewerNativeShareHook.ItemTextFields",
            "${symbols.imageViewerShareItemClass}[${symbols.imageViewerShareItemTitleField},${symbols.imageViewerShareItemContentField},${symbols.imageViewerShareItemLinkUrlField},${symbols.imageViewerShareItemImageUrlField},${symbols.imageViewerShareItemLocalFileField}]",
            listOf(
                "imageViewerShareItemTitleField" to has(symbols.imageViewerShareItemTitleField),
                "imageViewerShareItemContentField" to has(symbols.imageViewerShareItemContentField),
                "imageViewerShareItemLinkUrlField" to has(symbols.imageViewerShareItemLinkUrlField),
                "imageViewerShareItemImageUrlField" to has(symbols.imageViewerShareItemImageUrlField),
                "imageViewerShareItemLocalFileField" to has(symbols.imageViewerShareItemLocalFileField),
            ),
        )
        symbols.scanErrors.forEach { error ->
            val (name, detail) = splitScanError(error)
            out.add("HookPoint[$name] state=ERROR missing=exception target=$detail")
        }

        return out
    }

    fun resolvePlainUrlClickableSpanSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PlainUrlClickableSpanSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.plainUrlClickableSpanClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanClass")
                return null
            }
            val onClickMethodName =
                resolvedSymbols.plainUrlClickableSpanOnClickMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanOnClickMethod")
                    return null
                }
            val ownerClassNames = resolvedSymbols.plainUrlClickableSpanOnClickOwnerClasses
                .orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf(className) }
            val typeFieldName = resolvedSymbols.plainUrlClickableSpanTypeField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanTypeField")
                return null
            }
            val urlFieldName = resolvedSymbols.plainUrlClickableSpanUrlField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanUrlField")
                return null
            }
            val textFieldName = resolvedSymbols.plainUrlClickableSpanTextField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanTextField")
                return null
            }

            val spanClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: class not found: $className")
                return null
            }
            val onClickMethods = ownerClassNames.mapNotNull { ownerClassName ->
                val ownerClass = safeFindClass(ownerClassName, cl) ?: return@mapNotNull null
                if (!spanClass.isAssignableFrom(ownerClass)) return@mapNotNull null
                ownerClass.declaredMethods.singleOrNull { method ->
                    isPlainUrlClickableSpanOnClickMethod(method, onClickMethodName)
                }
            }.distinctBy { "${it.declaringClass.name}#${it.name}" }
            if (onClickMethods.isEmpty()) {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: onClick methods mismatch: $onClickMethodName owners=$ownerClassNames")
                return null
            }
            val fields = collectInstanceFields(spanClass)
            val typeField = fields.singleOrNull { field ->
                field.name == typeFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    isIntType(field.type)
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: type field mismatch: $className.$typeFieldName")
                return null
            }
            val urlField = fields.singleOrNull { field ->
                field.name == urlFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: url field mismatch: $className.$urlFieldName")
                return null
            }
            val textField = fields.singleOrNull { field ->
                field.name == textFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: text field mismatch: $className.$textFieldName")
                return null
            }

            if (!isPlainUrlClickableSpanStructureValid(spanClass, onClickMethods.first(), typeField, urlField, textField)) {
                XposedCompat.log("[PlainUrlDirectBrowserHook] skipped: span structure mismatch: $className")
                return null
            }

            onClickMethods.forEach { it.isAccessible = true }
            typeField.isAccessible = true
            urlField.isAccessible = true
            textField.isAccessible = true
            PlainUrlClickableSpanSymbols(
                spanClass = spanClass,
                onClickMethods = onClickMethods,
                typeField = typeField,
                urlField = urlField,
                textField = textField,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePlainUrlMessageDispatchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PlainUrlMessageDispatchSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: scan symbols unavailable")
                return null
            }
            val messageManagerClassName = resolvedSymbols.plainUrlMessageManagerClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlMessageManagerClass")
                return null
            }
            val dispatchMethodName = resolvedSymbols.plainUrlMessageDispatchMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlMessageDispatchMethod")
                return null
            }
            val responsedMessageClassName =
                resolvedSymbols.plainUrlResponsedMessageClass?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlResponsedMessageClass")
                    return null
                }
            val getCmdMethodName =
                resolvedSymbols.plainUrlResponsedMessageGetCmdMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlResponsedMessageGetCmdMethod")
                    return null
                }
            val customResponsedMessageClassName =
                resolvedSymbols.plainUrlCustomResponsedMessageClass?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlCustomResponsedMessageClass")
                    return null
                }
            val getDataMethodName =
                resolvedSymbols.plainUrlCustomResponsedMessageGetDataMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlCustomResponsedMessageGetDataMethod")
                    return null
                }
            val applicationClassName = resolvedSymbols.plainUrlApplicationClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlApplicationClass")
                return null
            }
            val getInstMethodName = resolvedSymbols.plainUrlApplicationGetInstMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlApplicationGetInstMethod")
                return null
            }

            val messageManagerClass = safeFindClass(messageManagerClassName, cl) ?: return null
            val responsedMessageClass = safeFindClass(responsedMessageClassName, cl) ?: return null
            val customResponsedMessageClass = safeFindClass(customResponsedMessageClassName, cl) ?: return null
            val applicationClass = safeFindClass(applicationClassName, cl) ?: return null
            val dispatchMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == dispatchMethodName &&
                    isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
            } ?: return null
            val getCmdMethod = responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getCmdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isIntType(method.returnType)
            } ?: return null
            val getDataMethod = customResponsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: return null
            val getInstMethod = applicationClass.declaredMethods.singleOrNull { method ->
                method.name == getInstMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    applicationClass.isAssignableFrom(method.returnType)
            } ?: return null

            listOf(dispatchMethod, getCmdMethod, getDataMethod, getInstMethod).forEach { it.isAccessible = true }
            PlainUrlMessageDispatchSymbols(
                messageManagerClass = messageManagerClass,
                dispatchMethod = dispatchMethod,
                responsedMessageClass = responsedMessageClass,
                getCmdMethod = getCmdMethod,
                customResponsedMessageClass = customResponsedMessageClass,
                getDataMethod = getDataMethod,
                applicationClass = applicationClass,
                getInstMethod = getInstMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] message symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePrivateReadReceiptSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PrivateReadReceiptSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PrivateReadReceiptBlockHook] skipped: scan symbols unavailable")
                return null
            }

            fun required(name: String, value: String?): String? {
                val normalized = value?.takeIf { it.isNotBlank() }
                if (normalized == null) {
                    XposedCompat.log("[PrivateReadReceiptBlockHook] skipped: missing $name")
                }
                return normalized
            }

            val modelClassName = required("privateReadReceiptModelClass", resolvedSymbols.privateReadReceiptModelClass)
                ?: return null
            val modelReadMethodName = required(
                "privateReadReceiptModelReadDispatchMethod",
                resolvedSymbols.privateReadReceiptModelReadDispatchMethod,
            ) ?: return null
            val messageManagerClassName = required(
                "privateReadReceiptMessageManagerClass",
                resolvedSymbols.privateReadReceiptMessageManagerClass,
            ) ?: return null
            val messageManagerGetInstanceMethodName = required(
                "privateReadReceiptMessageManagerGetInstanceMethod",
                resolvedSymbols.privateReadReceiptMessageManagerGetInstanceMethod,
            ) ?: return null
            val messageSendMethodName = required(
                "privateReadReceiptMessageSendMethod",
                resolvedSymbols.privateReadReceiptMessageSendMethod,
            ) ?: return null
            val messageBaseClassName = required(
                "privateReadReceiptMessageBaseClass",
                resolvedSymbols.privateReadReceiptMessageBaseClass,
            ) ?: return null
            val requestClassName = required("privateReadReceiptRequestClass", resolvedSymbols.privateReadReceiptRequestClass)
                ?: return null
            val modelBaseClassName = required(
                "privateReadReceiptModelBaseClass",
                resolvedSymbols.privateReadReceiptModelBaseClass,
            ) ?: return null
            val commitResponseClassName = required(
                "privateReadReceiptCommitResponseClass",
                resolvedSymbols.privateReadReceiptCommitResponseClass,
            ) ?: return null
            val processAckMethodName = required(
                "privateReadReceiptProcessAckMethod",
                resolvedSymbols.privateReadReceiptProcessAckMethod,
            ) ?: return null
            val responseErrorMethodName = required(
                "privateReadReceiptResponseErrorMethod",
                resolvedSymbols.privateReadReceiptResponseErrorMethod,
            ) ?: return null
            val requestMsgIdFieldName = required(
                "privateReadReceiptRequestMsgIdField",
                resolvedSymbols.privateReadReceiptRequestMsgIdField,
            ) ?: return null
            val requestToUidFieldName = required(
                "privateReadReceiptRequestToUidField",
                resolvedSymbols.privateReadReceiptRequestToUidField,
            ) ?: return null
            val modelDataFieldName = required(
                "privateReadReceiptModelDataField",
                resolvedSymbols.privateReadReceiptModelDataField,
            ) ?: return null
            val pageDataClassName = required(
                "privateReadReceiptPageDataClass",
                resolvedSymbols.privateReadReceiptPageDataClass,
            ) ?: return null
            val pageDataChatListMethodName = required(
                "privateReadReceiptPageDataChatListMethod",
                resolvedSymbols.privateReadReceiptPageDataChatListMethod,
            ) ?: return null
            val chatMessageClassName = required(
                "privateReadReceiptChatMessageClass",
                resolvedSymbols.privateReadReceiptChatMessageClass,
            ) ?: return null
            val chatMsgIdMethodName = required(
                "privateReadReceiptChatMessageMsgIdMethod",
                resolvedSymbols.privateReadReceiptChatMessageMsgIdMethod,
            ) ?: return null
            val chatUserIdMethodName = required(
                "privateReadReceiptChatMessageUserIdMethod",
                resolvedSymbols.privateReadReceiptChatMessageUserIdMethod,
            ) ?: return null
            val chatLocalDataMethodName = required(
                "privateReadReceiptChatMessageLocalDataMethod",
                resolvedSymbols.privateReadReceiptChatMessageLocalDataMethod,
            ) ?: return null
            val localDataClassName = required(
                "privateReadReceiptLocalDataClass",
                resolvedSymbols.privateReadReceiptLocalDataClass,
            ) ?: return null
            val localDataStatusMethodName = required(
                "privateReadReceiptLocalDataStatusMethod",
                resolvedSymbols.privateReadReceiptLocalDataStatusMethod,
            ) ?: return null
            val accountClassName = required(
                "privateReadReceiptAccountClass",
                resolvedSymbols.privateReadReceiptAccountClass,
            ) ?: return null
            val currentAccountMethodName = required(
                "privateReadReceiptCurrentAccountMethod",
                resolvedSymbols.privateReadReceiptCurrentAccountMethod,
            ) ?: return null

            val modelClass = safeFindClass(modelClassName, cl) ?: return null
            val messageManagerClass = safeFindClass(messageManagerClassName, cl) ?: return null
            val messageBaseClass = safeFindClass(messageBaseClassName, cl) ?: return null
            val requestClass = safeFindClass(requestClassName, cl) ?: return null
            val modelBaseClass = safeFindClass(modelBaseClassName, cl) ?: return null
            val commitResponseClass = safeFindClass(commitResponseClassName, cl) ?: return null
            val pageDataClass = safeFindClass(pageDataClassName, cl) ?: return null
            val chatMessageClass = safeFindClass(chatMessageClassName, cl) ?: return null
            val localDataClass = safeFindClass(localDataClassName, cl) ?: return null
            val accountClass = safeFindClass(accountClassName, cl) ?: return null
            if (!messageBaseClass.isAssignableFrom(requestClass)) return null

            val modelReadDispatchMethod = modelClass.declaredMethods.singleOrNull { method ->
                method.name == modelReadMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: return null
            val messageManagerSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageSendMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageBaseClass
            } ?: return null
            val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == messageManagerClass
            } ?: return null
            val requestConstructor = requestClass.declaredConstructors.singleOrNull { ctor ->
                ctor.parameterTypes.size == 2 &&
                    ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    ctor.parameterTypes[1] == Long::class.javaPrimitiveType
            } ?: return null
            val processAckMethod = modelBaseClass.declaredMethods.singleOrNull { method ->
                method.name == processAckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == commitResponseClass
            } ?: return null
            val responseErrorMethod = collectInstanceMethods(commitResponseClass).singleOrNull { method ->
                method.name == responseErrorMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isIntType(method.returnType)
            } ?: return null
            val requestMsgIdField = collectInstanceFields(requestClass).singleOrNull { field ->
                field.name == requestMsgIdFieldName && field.type == Long::class.javaPrimitiveType
            } ?: return null
            val requestToUidField = collectInstanceFields(requestClass).singleOrNull { field ->
                field.name == requestToUidFieldName && field.type == Long::class.javaPrimitiveType
            } ?: return null
            val modelDataField = collectInstanceFields(modelClass).singleOrNull { field ->
                field.name == modelDataFieldName && field.type == pageDataClass
            } ?: return null
            val pageDataChatListMethod = pageDataClass.declaredMethods.singleOrNull { method ->
                method.name == pageDataChatListMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isListType(method.returnType)
            } ?: return null
            val chatMessageMsgIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatMsgIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return null
            val chatMessageUserIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatUserIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return null
            val chatMessageLocalDataMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatLocalDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == localDataClass
            } ?: return null
            val localDataStatusMethod = localDataClass.declaredMethods.singleOrNull { method ->
                method.name == localDataStatusMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Short::class.javaObjectType
            } ?: return null
            val currentAccountMethod = accountClass.declaredMethods.singleOrNull { method ->
                method.name == currentAccountMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == String::class.java
            } ?: return null

            listOf(
                modelReadDispatchMethod,
                processAckMethod,
                responseErrorMethod,
                messageManagerGetInstanceMethod,
                messageManagerSendMethod,
                pageDataChatListMethod,
                chatMessageMsgIdMethod,
                chatMessageUserIdMethod,
                chatMessageLocalDataMethod,
                localDataStatusMethod,
                currentAccountMethod,
            ).forEach { it.isAccessible = true }
            listOf(requestMsgIdField, requestToUidField, modelDataField).forEach { it.isAccessible = true }
            requestConstructor.isAccessible = true

            PrivateReadReceiptSymbols(
                modelClass = modelClass,
                modelReadDispatchMethod = modelReadDispatchMethod,
                processAckMethod = processAckMethod,
                responseErrorMethod = responseErrorMethod,
                messageManagerGetInstanceMethod = messageManagerGetInstanceMethod,
                messageManagerSendMethod = messageManagerSendMethod,
                requestConstructor = requestConstructor,
                requestMessageClass = requestClass,
                requestMsgIdField = requestMsgIdField,
                requestToUidField = requestToUidField,
                modelDataField = modelDataField,
                pageDataChatListMethod = pageDataChatListMethod,
                chatMessageMsgIdMethod = chatMessageMsgIdMethod,
                chatMessageUserIdMethod = chatMessageUserIdMethod,
                chatMessageLocalDataMethod = chatMessageLocalDataMethod,
                localDataStatusMethod = localDataStatusMethod,
                currentAccountMethod = currentAccountMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PrivateReadReceiptBlockHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePlainUrlMessageDataSymbols(data: Any): PlainUrlMessageDataSymbols? {
        val dataClass = data.javaClass
        plainUrlMessageDataSymbolsCache[dataClass]?.let { return it }
        val fields = collectInstanceFields(dataClass)
        val intFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && isIntType(field.type)
        }
        val stringFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == String::class.java
        }
        val typeField = intFields.firstOrNull() ?: return null
        val urlField = stringFields.firstOrNull() ?: return null
        val textField = stringFields.getOrNull(1) ?: urlField
        listOf(typeField, urlField, textField).forEach { it.isAccessible = true }
        val resolved = PlainUrlMessageDataSymbols(typeField, urlField, textField)
        plainUrlMessageDataSymbolsCache[dataClass] = resolved
        return resolved
    }

    fun isPlainUrlClickMessageCmd(cmd: Int): Boolean {
        return cmd == PLAIN_URL_CLICK_MESSAGE_CMD
    }

    fun resolveMountCardLinkLayoutSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): MountCardLinkLayoutSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: scan symbols unavailable")
                return null
            }
            val layoutClassName = resolvedSymbols.mountCardLinkLayoutClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: missing layout class")
                return null
            }
            val onClickMethodName = resolvedSymbols.mountCardLinkLayoutOnClickMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: missing onClick method")
                return null
            }
            val dataFieldName = resolvedSymbols.mountCardLinkLayoutDataField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: missing data field")
                return null
            }
            val dataClassName = resolvedSymbols.mountCardLinkInfoDataClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: missing data class")
                return null
            }
            val getUrlMethodName = resolvedSymbols.mountCardLinkInfoGetUrlMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: missing getUrl method")
                return null
            }

            val layoutClass = safeFindClass(layoutClassName, cl) ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: layout class not found: $layoutClassName")
                return null
            }
            val dataClass = safeFindClass(dataClassName, cl) ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: data class not found: $dataClassName")
                return null
            }
            val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
                isMountCardLinkLayoutOnClickMethod(method, onClickMethodName)
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: onClick mismatch: $layoutClassName.$onClickMethodName")
                return null
            }
            val dataField = resolveMountCardLinkLayoutDataField(layoutClass, dataClass)
                ?.takeIf { it.name == dataFieldName } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: data field mismatch: $layoutClassName.$dataFieldName")
                return null
            }
            val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
                method.name == getUrlMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: getUrl mismatch: $dataClassName.$getUrlMethodName")
                return null
            }
            if (!isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)) {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: structure mismatch: $layoutClassName")
                return null
            }

            onClickMethod.isAccessible = true
            dataField.isAccessible = true
            getUrlMethod.isAccessible = true
            MountCardLinkLayoutSymbols(
                layoutClass = layoutClass,
                onClickMethod = onClickMethod,
                dataField = dataField,
                getUrlMethod = getUrlMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] mount card symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveAiComponentSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AiComponentSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[AiComponentDisableHook] skipped: scan symbols unavailable")
                return null
            }

            fun required(name: String, value: String?): String? {
                val normalized = value?.takeIf { it.isNotBlank() }
                if (normalized == null) {
                    XposedCompat.log("[AiComponentDisableHook] skipped: missing $name")
                }
                return normalized
            }

            val controllerClassName = required(
                "aiSpriteMemePanControllerClass",
                resolvedSymbols.aiSpriteMemePanControllerClass,
            ) ?: return null
            val enableMethodName = required(
                "aiSpriteMemeEnableMethod",
                resolvedSymbols.aiSpriteMemeEnableMethod,
            ) ?: return null
            val inputContainerClassName = required(
                "aiPbNewInputContainerClass",
                resolvedSymbols.aiPbNewInputContainerClass,
            ) ?: return null
            val initSpriteMemeMethodName = required(
                "aiPbNewInputContainerInitSpriteMemeMethod",
                resolvedSymbols.aiPbNewInputContainerInitSpriteMemeMethod,
            ) ?: return null
            val initAiWriteMethodName = required(
                "aiPbNewInputContainerInitAiWriteMethod",
                resolvedSymbols.aiPbNewInputContainerInitAiWriteMethod,
            ) ?: return null
            val controllerClass = safeFindClass(controllerClassName, cl) ?: run {
                XposedCompat.log("[AiComponentDisableHook] skipped: class not found: $controllerClassName")
                return null
            }
            val inputContainerClass = safeFindClass(inputContainerClassName, cl) ?: run {
                XposedCompat.log("[AiComponentDisableHook] skipped: class not found: $inputContainerClassName")
                return null
            }
            val inputShowTypeClass = safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
            val spriteMemePanClass = safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)

            val enableMethod = controllerClass.declaredMethods.singleOrNull { method ->
                method.name == enableMethodName && isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$controllerClassName.$enableMethodName",
                )
                return null
            }
            val initSpriteMemeMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initSpriteMemeMethodName && isPbNewInputContextInitMethod(method)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initSpriteMemeMethodName",
                )
                return null
            }
            val initAiWriteMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initAiWriteMethodName && isPbNewInputContextInitMethod(method)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initAiWriteMethodName",
                )
                return null
            }
            if (!isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) {
                XposedCompat.log("[AiComponentDisableHook] skipped: input container structure mismatch")
                return null
            }

            val pbAiEmojiCreationViewBindMethod = resolveAiPbEmojiCreationViewBindMethod(cl, resolvedSymbols)
            val pbPageBrowserAiEmojiCreationBindMethod =
                resolvePbPageBrowserAiEmojiCreationBindMethod(cl, resolvedSymbols)

            listOfNotNull(
                enableMethod,
                initSpriteMemeMethod,
                initAiWriteMethod,
                pbAiEmojiCreationViewBindMethod,
                pbPageBrowserAiEmojiCreationBindMethod,
            ).forEach { it.isAccessible = true }
            AiComponentSymbols(
                spriteMemeEnableMethod = enableMethod,
                pbInitSpriteMemeMethod = initSpriteMemeMethod,
                pbInitAiWriteMethod = initAiWriteMethod,
                pbAiEmojiCreationViewBindMethod = pbAiEmojiCreationViewBindMethod,
                pbPageBrowserAiEmojiCreationBindMethod = pbPageBrowserAiEmojiCreationBindMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[AiComponentDisableHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveAiImageViewerJumpButtonSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AiImageViewerJumpButtonSymbols? {
        val initMethod = resolveAiImageViewerJumpButtonInitMethod(cl, symbols) ?: return null
        return AiImageViewerJumpButtonSymbols(initMethod = initMethod)
    }

    private fun resolveAiPbEmojiCreationViewBindMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        return try {
            val methodName = symbols.aiPbAiEmojiCreationViewBindMethod?.takeIf { it.isNotBlank() } ?: return null
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: return null
            viewClass.declaredMethods.singleOrNull { method ->
                method.name == methodName && isAiPbEmojiCreationViewBindMethod(method, cl)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[AiComponentDisableHook] AI emoji creation view bind resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbPageBrowserAiEmojiCreationBindMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        return try {
            val methodName = symbols.aiPbPageBrowserAiEmojiCreationBindMethod?.takeIf { it.isNotBlank() } ?: return null
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS, cl) ?: return null
            viewClass.declaredMethods.singleOrNull { method ->
                method.name == methodName && isPbPageBrowserAiEmojiCreationBindMethod(method)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[AiComponentDisableHook] page browser AI emoji creation bind resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolveAiImageViewerJumpButtonInitMethod(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): Method? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[AiComponentDisableHook] image viewer skipped: scan symbols unavailable")
                return null
            }
            val ownerClassName = resolvedSymbols.aiImageViewerJumpButtonOwnerClass?.takeIf { it.isNotBlank() }
            val initMethodName = resolvedSymbols.aiImageViewerJumpButtonInitMethod?.takeIf { it.isNotBlank() }
            if (ownerClassName == null || initMethodName == null) {
                XposedCompat.log("[AiComponentDisableHook] image viewer skipped: scan symbols missing")
                return null
            }

            val ownerClass = safeFindClass(ownerClassName, cl)
            val layoutClass = safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl)
            if (ownerClass == null || layoutClass == null) {
                XposedCompat.log(
                    "[AiComponentDisableHook] image viewer skipped: class not found: " +
                        "$ownerClassName / $AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS",
                )
                return null
            }
            if (scoreImageViewerJumpButtonOwnerClass(ownerClass, layoutClass) <= 0) {
                XposedCompat.log("[AiComponentDisableHook] image viewer skipped: owner structure mismatch")
                return null
            }
            val initMethod = ownerClass.declaredMethods.singleOrNull { method ->
                method.name == initMethodName && isImageViewerJumpButtonInitMethod(method)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] image viewer skipped: method mismatch: " +
                        "$ownerClassName.$initMethodName",
                )
                return null
            }
            initMethod.isAccessible = true
            initMethod
        } catch (t: Throwable) {
            XposedCompat.log("[AiComponentDisableHook] image viewer symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveFreeCopyPopupSymbols(cl: ClassLoader, symbols: HookSymbols? = getMemorySymbols()): FreeCopyPopupSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: scan symbols unavailable")
                return null
            }
            val menuClassName = resolvedSymbols.freeCopyPopupMenuClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: missing freeCopyPopupMenuClass")
                return null
            }
            val contentViewMethodName = resolvedSymbols.freeCopyPopupContentViewMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: missing freeCopyPopupContentViewMethod")
                return null
            }
            val textFieldName = resolvedSymbols.freeCopyPopupTextField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: missing freeCopyPopupTextField")
                return null
            }

            val menuClass = safeFindClass(menuClassName, cl) ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: class not found: $menuClassName")
                return null
            }
            val roundLayoutClass = safeFindClass(FREE_COPY_POPUP_ROUND_LAYOUT_CLASS, cl) ?: return null
            val emTextViewClass = safeFindClass(FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS, cl) ?: return null

            val hasRoundLayoutField = menuClass.declaredFields.any {
                roundLayoutClass.isAssignableFrom(it.type)
            }
            if (!hasRoundLayoutField) {
                XposedCompat.log("[FreeCopyHook] popup skipped: $menuClassName structure mismatch")
                return null
            }

            val textField = menuClass.declaredFields.singleOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == textFieldName &&
                    (emTextViewClass.isAssignableFrom(it.type) || TextView::class.java.isAssignableFrom(it.type))
            } ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: text field mismatch: $textFieldName")
                return null
            }

            val contentViewMethod = menuClass.declaredMethods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == contentViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    View::class.java.isAssignableFrom(method.returnType)
            } ?: run {
                XposedCompat.log("[FreeCopyHook] popup skipped: content view method mismatch: $contentViewMethodName")
                return null
            }

            textField.isAccessible = true
            contentViewMethod.isAccessible = true
            FreeCopyPopupSymbols(
                menuClass = menuClass,
                contentViewMethod = contentViewMethod,
                textField = textField,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[FreeCopyHook] popup symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolve(
        context: Context,
        cl: ClassLoader,
        forceRescan: Boolean,
        showToast: Boolean,
        logger: ScanLogger? = null,
    ): HookSymbols {
        val startedAt = System.currentTimeMillis()
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        val prefs = appCtx.getSharedPreferences(ConfigManager.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        log(logger, "resolve start, forceRescan=$forceRescan")
        log(logger, "fingerprint=$fingerprint")
        log(logger, "thread=${Thread.currentThread().name}")
        log(logger, "classLoader=${cl.javaClass.name}@${System.identityHashCode(cl)}")
        log(logger, "app=${describeAppMeta(appCtx)}")
        ensureCacheForModuleVersion(appCtx, prefs, logger)

        val memorySymbols = sMemorySymbols
        if (!forceRescan && memorySymbols != null && fingerprint == sMemoryFingerprint) {
            log(logger, "memory cache hit: source=${memorySymbols.source}")
            return memorySymbols
        }

        if (!forceRescan) {
            val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
            val cached = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
            log(logger, "disk cache fp match=${cacheFp == fingerprint}, cached=${cached != null}")
            if (cacheFp == fingerprint && cached != null) {
                val accepted = acceptCachedSymbolsIfUsable(cached, cl, fingerprint, prefs, logger, "disk cache")
                if (accepted != null) {
                    log(logger, "disk cache usable: source=${accepted.source}")
                    log(logger, "cache symbols: \n${describeSymbols(appCtx, accepted)}")
                    sMemoryFingerprint = fingerprint
                    sMemorySymbols = accepted
                    return accepted
                }
                log(logger, "disk cache unusable, rescan required")
            }
        }

        if (showToast) {
            toastOnMain(appCtx, UiText.SymbolResolverToast.SCANNING)
        }
        log(logger, "scan begin")

        val scanned = try {
            applyScanSupportCheck(appCtx, scan(appCtx, cl, logger), logger)
        } catch (t: Throwable) {
            val scanErrors = ArrayList<String>(1)
            logScanException(logger, "Scan", scanErrors, t)
            val unsupported = HookSymbols(
                source = "unsupported",
                createdAt = System.currentTimeMillis(),
                scanErrors = scanErrors,
            )
            unsupported.copy(featureStatusMap = deriveFeatureStatusMap(unsupported))
        }
        log(logger, "scan done: source=${scanned.source}")
        log(
            logger,
            "scan support: state=${scanned.scanSupportState}, " +
                "version=${scanned.scanTargetVersionName}(${scanned.scanTargetVersionCode}), " +
                "versionType=${scanned.scanTargetVersionType ?: "-"}",
        )
        formatFeatureStatusLines(scanned).forEach { line -> log(logger, line) }
        formatHookPointStatusLines(scanned).forEach { line -> log(logger, line) }
        log(logger, "final symbols: \n${describeSymbols(appCtx, scanned)}")

        val cacheEditor = prefs.edit()
            .putString(KEY_SYMBOL_FP, fingerprint)
            .putString(KEY_SYMBOL_JSON, scanned.toJson())
        if (scanned.source == "unsupported") {
            cacheEditor.remove(KEY_SYMBOL_VERIFIED_FP)
        } else {
            cacheEditor.putString(KEY_SYMBOL_VERIFIED_FP, fingerprint)
        }
        cacheEditor.apply()
        log(logger, "cache updated")

        sMemoryFingerprint = fingerprint
        sMemorySymbols = scanned

        if (showToast) {
            val versionWarning = formatScanVersionWarning(scanned)
            when {
                versionWarning != null -> toastOnMain(appCtx, versionWarning)
                hasScanErrors(scanned) -> toastOnMain(appCtx, formatScanFeatureWarning())
                else -> toastOnMain(appCtx, UiText.SymbolResolverToast.SCAN_DONE)
            }
        }
        log(logger, "durationMs=${System.currentTimeMillis() - startedAt}")
        return scanned
    }

    fun loadCachedIfUsable(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger? = null,
        verifyFull: Boolean = true,
    ): HookSymbols? {
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        val prefs = appCtx.getSharedPreferences(ConfigManager.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        log(logger, "loadCachedIfUsable fingerprint=$fingerprint")
        ensureCacheForModuleVersion(appCtx, prefs, logger)

        val memorySymbols = sMemorySymbols
        if (memorySymbols != null && fingerprint == sMemoryFingerprint) {
            log(logger, "memory cache candidate: source=${memorySymbols.source}")
            if (isLightweightUsable(memorySymbols)) return memorySymbols
        }

        val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
        val cached = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
        log(logger, "disk cache candidate: fpMatch=${cacheFp == fingerprint}, exists=${cached != null}")
        if (cacheFp == fingerprint && cached != null) {
            val accepted = acceptCachedSymbolsIfUsable(
                symbols = cached,
                cl = cl,
                fingerprint = fingerprint,
                prefs = prefs,
                logger = logger,
                source = "disk cache",
                verifyFull = verifyFull,
            )
            if (accepted != null) {
                sMemoryFingerprint = fingerprint
                sMemorySymbols = accepted
                log(logger, "disk cache usable")
                return accepted
            }
        }
        log(logger, "no usable cache")
        return null
    }

    fun manualRescanAsync(context: Context, classLoader: ClassLoader?, clearUserData: Boolean) {
        val appCtx = context.applicationContext ?: context
        val cl = classLoader ?: appCtx.classLoader
        if (cl == null) {
            toastOnMain(appCtx, UiText.SymbolResolverToast.CLASSLOADER_UNAVAILABLE)
            return
        }
        toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_START)

        thread(name = "tbhook-manual-rescan", isDaemon = true) {
            if (clearUserData) {
                val clearResult = ModuleUserDataCleaner.clearBeforeManualScan(appCtx)
                if (!clearResult.success) {
                    toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_CLEAR_FAILED)
                    return@thread
                }
            }
            val symbols = resolve(
                context = appCtx,
                cl = cl,
                forceRescan = true,
                showToast = false,
            )
            val versionWarning = formatScanVersionWarning(symbols)
            when {
                versionWarning != null -> toastOnMain(appCtx, versionWarning)
                hasScanErrors(symbols) -> toastOnMain(appCtx, formatScanFeatureWarning())
                else -> toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_DONE)
            }
        }
    }

    private fun ensureCacheForModuleVersion(
        context: Context,
        prefs: android.content.SharedPreferences,
        logger: ScanLogger?,
    ) {
        val moduleVersion = runtimeModuleVersionCodeLabel()
        val cachedVersion = prefs.getString(KEY_CACHE_MODULE_VERSION, null)
        if (cachedVersion == moduleVersion) return
        prefs.edit()
            .remove(KEY_SYMBOL_FP)
            .remove(KEY_SYMBOL_JSON)
            .remove(KEY_SYMBOL_VERIFIED_FP)
            .putString(KEY_CACHE_MODULE_VERSION, moduleVersion)
            .apply()
        sMemoryFingerprint = null
        sMemorySymbols = null
        log(logger, "module version changed ($cachedVersion -> $moduleVersion), symbol cache cleared")
        log(logger, "module cache owner=${context.packageName}")
    }

    private fun acceptCachedSymbolsIfUsable(
        symbols: HookSymbols,
        cl: ClassLoader,
        fingerprint: String,
        prefs: android.content.SharedPreferences,
        logger: ScanLogger?,
        source: String,
        verifyFull: Boolean = true,
    ): HookSymbols? {
        if (!isLightweightUsable(symbols)) {
            log(logger, "$source rejected: lightweight cache check failed")
            return null
        }
        if (symbols.source == "unsupported") {
            log(logger, "$source unsupported, skipping full verification")
            return symbols
        }
        if (!verifyFull) {
            log(logger, "$source lightweight usable")
            return symbols
        }
        if (prefs.getString(KEY_SYMBOL_VERIFIED_FP, null) == fingerprint) {
            log(logger, "$source fast path: full verification already completed for fingerprint")
            return symbols
        }
        if (!isUsable(symbols, cl)) {
            log(logger, "$source rejected: full verification failed")
            return null
        }
        prefs.edit()
            .putString(KEY_SYMBOL_VERIFIED_FP, fingerprint)
            .apply()
        log(logger, "$source full verification completed")
        return symbols
    }

    private fun isLightweightUsable(symbols: HookSymbols): Boolean {
        if (symbols.source == "unsupported") return true
        return (symbols.source == "scan" || symbols.source == "partial") &&
            symbols.createdAt > 0L
    }

    private inline fun <T> withScanContext(cl: ClassLoader, block: () -> T): T {
        val previous = scanContext.get()
        scanContext.set(ScanContext(cl))
        return try {
            block()
        } finally {
            scanContext.set(previous)
        }
    }

    private fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols =
        withScanContext(cl) {
            scanInternal(context, cl, logger)
        }

    private inline fun <T> runScanStep(
        tag: String,
        logger: ScanLogger?,
        errors: MutableList<String>,
        fallback: T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            logScanException(logger, tag, errors, t)
            fallback
        }
    }

    private fun scanEnterForumInitInfoDataSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Pair<String, String>? {
        val cls = safeFindClass(ENTER_FORUM_INIT_INFO_DATA_CLASS, cl)
        if (cls == null) {
            log(logger, "enterForumWeb.initInfoData: class not found: $ENTER_FORUM_INIT_INFO_DATA_CLASS")
            return null
        }
        val getter = cls.methods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == ENTER_FORUM_INIT_INFO_GET_URL_METHOD &&
                method.returnType == String::class.java &&
                method.parameterTypes.isEmpty()
        }
        if (getter == null) {
            log(
                logger,
                "enterForumWeb.initInfoData: method not found: " +
                    "$ENTER_FORUM_INIT_INFO_DATA_CLASS.$ENTER_FORUM_INIT_INFO_GET_URL_METHOD()",
            )
            return null
        }
        log(logger, "enterForumWeb.initInfoData matched: ${cls.name}.${getter.name}")
        return cls.name to getter.name
    }

    private fun scanInternal(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols {
        val scanErrors = ArrayList<String>()
        scanContext.get()?.scanErrors = scanErrors
        val scanCandidates = listScanCandidateClasses(context, cl, logger)
        val lifeLineCandidates = scanCandidates.obfuscated.ifEmpty { scanCandidates.all }
        val obfuscatedWithWhitelist = (lifeLineCandidates + SCAN_WHITELIST_CLASSES).distinct()
        val candidatesWithWhitelist = (scanCandidates.all + SCAN_WHITELIST_CLASSES).distinct()
        log(
            logger,
            "candidates=${candidatesWithWhitelist.size}, lifeLine=${lifeLineCandidates.size}, " +
                "obfuscated=${scanCandidates.obfuscated.size}, expanded=${scanCandidates.expanded.size}",
        )
        if (candidatesWithWhitelist.isEmpty()) {
            val unsupported = HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())
            return unsupported.copy(featureStatusMap = deriveFeatureStatusMap(unsupported))
        }

        val navClass = safeFindClass(NAV_CLASS, cl)
        if (navClass == null) {
            recordScanIssue(logger, "SettingsMenuHook", scanErrors, "nav class not found: $NAV_CLASS")
        }

        val settingsMatch = navClass?.let {
            val settingsRules = listOf(SettingsLevel1Rule(it))
            runRules(lifeLineCandidates, cl, settingsRules, logger, "settings")
        }
        if (settingsMatch == null) {
            log(logger, "settings match not found, SettingsMenuHook disabled, continuing remaining scan")
        }

        val homeRules = listOf(HomeTabsLevel1Rule())
        val homeMatch = runRules(lifeLineCandidates, cl, homeRules, logger, "home")

        // 动态提取广告和界面相关的混淆名。
        var feedKeyMethod: String? = null
        var feedPayloadMethod: String? = null
        var feedLoadMoreMethod: String? = null
        var feedCardBindMethod: String? = null
        var feedCardDataListField: String? = null
        var feedHeadParamsField: String? = null
        var feedRecommendCardNestedDataMethod: String? = null
        var feedRecommendCardNestedDataListField: String? = null
        var splashAdHelperClass: String? = null
        var splashAdHelperMethod: String? = null
        var closeAdDataClass: String? = null
        var closeAdDataMethodG1: String? = null
        var closeAdDataMethodJ1: String? = null
        var zgaClass: String? = null
        var zgaMethodsList: List<String>? = null
        var searchBoxViewClass: String? = null
        var searchBoxSetHintMethod: String? = null
        var homeSearchBoxOwnerClass: String? = null
        var homeSearchBoxInitMethod: String? = null
        var homeSearchBoxGetterMethod: String? = null
        var homePersonalizeAnchorClasses: List<String>? = null
        var homeRightSlotClass: String? = null
        var homeRightSlotStateMethods: List<String>? = null
        var pbFallingViewClass: String? = null
        var pbFallingInitMethod: String? = null
        var pbFallingShowMethod: String? = null
        var pbFallingClearMethod: String? = null
        var pbEarlyAdInsertClass: String? = null
        var pbEarlyAdInsertMethodSpecs: List<String>? = null
        var pbAdBidCommonRequestModelClass: String? = null
        var pbAdBidCommonRequestStartMethods: List<String>? = null
        var pbAdBidCommonRequestNotifyMethod: String? = null
        var pbAdBidPageBrowserRequestModelClass: String? = null
        var pbAdBidPageBrowserRequestDataMethod: String? = null
        var typeAdapterSetDataMethod: String? = null
        var typeAdapterDataItemClass: String? = null
        var typeAdapterDataGetTypeMethod: String? = null
        var enterForumWebControllerClass: String? = null
        var enterForumWebLoadMethod: String? = null
        var enterForumInitInfoDataClass: String? = null
        var enterForumInitInfoGetUrlMethod: String? = null
        var plainUrlClickableSpanClass: String? = null
        var plainUrlClickableSpanOnClickMethod: String? = null
        var plainUrlClickableSpanOnClickOwnerClasses: List<String>? = null
        var plainUrlClickableSpanTypeField: String? = null
        var plainUrlClickableSpanUrlField: String? = null
        var plainUrlClickableSpanTextField: String? = null
        var plainUrlMessageManagerClass: String? = null
        var plainUrlMessageDispatchMethod: String? = null
        var plainUrlResponsedMessageClass: String? = null
        var plainUrlResponsedMessageGetCmdMethod: String? = null
        var plainUrlCustomResponsedMessageClass: String? = null
        var plainUrlCustomResponsedMessageGetDataMethod: String? = null
        var plainUrlApplicationClass: String? = null
        var plainUrlApplicationGetInstMethod: String? = null
        var privateReadReceiptModelClass: String? = null
        var privateReadReceiptModelReadDispatchMethod: String? = null
        var privateReadReceiptMessageManagerClass: String? = null
        var privateReadReceiptMessageManagerGetInstanceMethod: String? = null
        var privateReadReceiptMessageSendMethod: String? = null
        var privateReadReceiptMessageBaseClass: String? = null
        var privateReadReceiptRequestClass: String? = null
        var privateReadReceiptModelBaseClass: String? = null
        var privateReadReceiptCommitResponseClass: String? = null
        var privateReadReceiptProcessAckMethod: String? = null
        var privateReadReceiptResponseErrorMethod: String? = null
        var privateReadReceiptRequestMsgIdField: String? = null
        var privateReadReceiptRequestToUidField: String? = null
        var privateReadReceiptModelDataField: String? = null
        var privateReadReceiptPageDataClass: String? = null
        var privateReadReceiptPageDataChatListMethod: String? = null
        var privateReadReceiptChatMessageClass: String? = null
        var privateReadReceiptChatMessageMsgIdMethod: String? = null
        var privateReadReceiptChatMessageUserIdMethod: String? = null
        var privateReadReceiptChatMessageLocalDataMethod: String? = null
        var privateReadReceiptLocalDataClass: String? = null
        var privateReadReceiptLocalDataStatusMethod: String? = null
        var privateReadReceiptAccountClass: String? = null
        var privateReadReceiptCurrentAccountMethod: String? = null
        var mountCardLinkLayoutClass: String? = null
        var mountCardLinkLayoutOnClickMethod: String? = null
        var mountCardLinkLayoutDataField: String? = null
        var mountCardLinkInfoDataClass: String? = null
        var mountCardLinkInfoGetUrlMethod: String? = null
        var forumBottomSheetViewClass: String? = null
        var forumBottomSheetInitScrollMethod: String? = null
        var autoRefreshTriggerMethod: String? = null
        var autoLoadMoreConfigClass: String? = null
        var autoLoadMoreConfigMethod: String? = null
        var pbCommentScrollListenerClass: String? = null
        var pbCommentScrollMethod: String? = null
        var pbCommentScrollFragmentField: String? = null
        var pbCommentScrollBottomListenerField: String? = null
        var pbCommentScrollBottomMethod: String? = null
        var pbCommentBottomListScrollClass: String? = null
        var pbCommentBottomListScrollMethod: String? = null
        var pbCommentBottomListOwnerField: String? = null
        var pbCommentBottomRecyclerScrollClass: String? = null
        var pbCommentBottomRecyclerScrollMethod: String? = null
        var pbCommentBottomRecyclerOwnerField: String? = null
        var pbGestureScaleManagerClass: String? = null
        var pbGestureScaleDispatchMethod: String? = null
        var pbGestureScaleListenerSetterMethod: String? = null
        var pbGestureScaleListenerClass: String? = null
        var pbGestureScaleListenerOnScaleMethod: String? = null
        var collectionPresenterField: String? = null
        var collectionPresenterListSetterMethod: String? = null
        var collectionPresenterAdapterField: String? = null
        var collectionModelField: String? = null
        var collectionModelListGetterMethod: String? = null
        var collectionModelParseMethod: String? = null
        var collectionModelListField: String? = null
        var collectionFragmentDisplayListField: String? = null
        var collectionActivityNavControllerField: String? = null
        var collectionNavBarField: String? = null
        var collectionAdapterShowFooterMethod: String? = null
        var collectionAdapterLoadingMethod: String? = null
        var collectionAdapterHasMoreMethod: String? = null
        var collectionEditModeMethod: String? = null
        var historyAdapterField: String? = null
        var historyAdapterSetListMethod: String? = null
        var historyListField: String? = null
        var historyActivityListUpdateMethod: String? = null
        var historyActivityNavBarField: String? = null
        var historyThreadNameMethod: String? = null
        var historyForumNameMethod: String? = null
        var historyUserNameMethod: String? = null
        var historyDescriptionMethod: String? = null
        var historyThreadIdMethod: String? = null
        var historyPostIdMethod: String? = null
        var historyLiveIdMethod: String? = null
        var msgTabLocateToTabMethod: String? = null
        var msgTabContainerSelectMethod: String? = null
        var msgTabContainerExtDataField: String? = null
        var freeCopyPopupMenuClass: String? = null
        var freeCopyPopupContentViewMethod: String? = null
        var freeCopyPopupTextField: String? = null
        var homeTabItemTypeField: String? = null
        var homeTabItemCodeField: String? = null
        var homeTabItemNameField: String? = null
        var homeTabItemUrlField: String? = null
        var homeTabItemMainSetterMethod: String? = null
        var homeTabItemMainIntField: String? = null
        var homeTabItemMainBooleanField: String? = null
        var mainTabDataClass: String? = null
        var mainTabAddMethod: String? = null
        var mainTabGetListMethod: String? = null
        var mainTabDelegateGetStructureMethod: String? = null
        var mainTabStructureTypeField: String? = null
        var mainTabStructureDynamicIconField: String? = null
        var mainTabStructureFragmentField: String? = null
        var shareTrackBuilderClass: String? = null
        var shareTrackBuildUrlMethod: String? = null
        var shareTrackAppendQueryMethod: String? = null
        var imageViewerShareConfigClass: String? = null
        var imageViewerShareIsDialogField: String? = null
        var imageViewerShareItemField: String? = null
        var imageViewerShareAddOutsideMethod: String? = null
        var imageViewerShareGetRequestDataMethod: String? = null
        var imageViewerShareSetRequestDataMethod: String? = null
        var imageViewerShareGetContextMethod: String? = null
        var imageViewerShareItemClass: String? = null
        var imageViewerShareItemTitleField: String? = null
        var imageViewerShareItemContentField: String? = null
        var imageViewerShareItemLinkUrlField: String? = null
        var imageViewerShareItemImageUriField: String? = null
        var imageViewerShareItemImageUrlField: String? = null
        var imageViewerShareItemLocalFileField: String? = null
        var imageViewerShareItemViewClass: String? = null
        var imageViewerShareItemNameByResMethod: String? = null
        var imageViewerShareItemNameByTextMethod: String? = null
        var imageViewerShareIconResId: Int? = null
        var homeNativeGlassSubPbNextPageMoreViewId: Int? = null
        var homeNativeGlassPbReplyTitleDividerViewId: Int? = null
        var homeNativeGlassSortSwitchBackgroundPaintField: String? = null
        var homeNativeGlassSortSwitchSlideDrawMethod: String? = null
        var homeNativeGlassSortSwitchSlidePathField: String? = null
        var homeNativeGlassEnterForumCapsuleControllerClass: String? = null
        var homeNativeGlassEnterForumCapsuleInitMethod: String? = null
        var homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null
        var homeNativeGlassEnterForumCapsuleViewField: String? = null
        var homeNativeGlassEnterForumCapsuleTitleField: String? = null
        var pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null
        var aiSpriteMemePanControllerClass: String? = null
        var aiSpriteMemeEnableMethod: String? = null
        var aiPbNewInputContainerClass: String? = null
        var aiPbNewInputContainerInitSpriteMemeMethod: String? = null
        var aiPbNewInputContainerInitAiWriteMethod: String? = null
        var aiPbAiEmojiCreationViewBindMethod: String? = null
        var aiPbPageBrowserAiEmojiCreationBindMethod: String? = null
        var aiImageViewerJumpButtonOwnerClass: String? = null
        var aiImageViewerJumpButtonInitMethod: String? = null

        fun unpack(raw: String, expectedParts: Int): List<String?> {
            val parts = ArrayList<String?>(expectedParts)
            var start = 0
            while (parts.size < expectedParts - 1) {
                val idx = raw.indexOf(',', start)
                if (idx < 0) break
                val token = raw.substring(start, idx).trim()
                parts.add(token.ifEmpty { null })
                start = idx + 1
            }
            val tail = raw.substring(start).trim()
            parts.add(tail.ifEmpty { null })
            while (parts.size < expectedParts) {
                parts.add(null)
            }
            return parts
        }

        val homeItemScan = runScanStep(
            "HomeTabHook.Item",
            logger,
            scanErrors,
            HomeTabItemScanSymbols(),
        ) {
            homeMatch?.let { scanHomeTabItemSymbols(it, cl, logger) } ?: HomeTabItemScanSymbols()
        }
        homeTabItemTypeField = homeItemScan.typeField
        homeTabItemCodeField = homeItemScan.codeField
        homeTabItemNameField = homeItemScan.nameField
        homeTabItemUrlField = homeItemScan.urlField
        homeTabItemMainSetterMethod = homeItemScan.mainSetterMethod
        homeTabItemMainIntField = homeItemScan.mainIntField
        homeTabItemMainBooleanField = homeItemScan.mainBooleanField

        val feedKeyMatch = runRules(candidatesWithWhitelist, cl, listOf(FeedTemplateKeyRule()), logger, "feedKey")
        if (feedKeyMatch != null) {
            feedKeyMethod = feedKeyMatch.methodName
            feedPayloadMethod = feedKeyMatch.fieldName.takeIf { it.isNotBlank() }
        }

        val feedLoadMoreMatch = runRules(candidatesWithWhitelist, cl, listOf(FeedTemplateLoadMoreRule()), logger, "feedLoadMore")
        if (feedLoadMoreMatch != null) {
            feedLoadMoreMethod = feedLoadMoreMatch.methodName
        } else {
            logFeedLoadMoreDiagnostics(cl, logger)
        }

        runScanStep("FeedInfoLogHook.Bind", logger, scanErrors, Unit) {
            val feedCardBindMatch = runRules(candidatesWithWhitelist, cl, listOf(FeedCardBindRule()), logger, "feedCardBind")
            if (feedCardBindMatch != null) {
                feedCardBindMethod = feedCardBindMatch.methodName
                val feedCardClass = safeFindClass("com.baidu.tieba.feed.card.FeedCardView", cl)
                val bindMethod = feedCardClass?.declaredMethods?.firstOrNull { method ->
                    method.name == feedCardBindMethod &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        !method.parameterTypes[0].isPrimitive
                }
                val cardDataClass = bindMethod?.parameterTypes?.firstOrNull()
                feedCardDataListField = cardDataClass
                    ?.let(::collectInstanceFields)
                    ?.firstOrNull { isListType(it.type) }
                    ?.name
            }
        }

        val feedHeadParamsMatch = runRules(
            candidatesWithWhitelist,
            cl,
            listOf(FeedHeadParamsFieldRule()),
            logger,
            "feedHeadParams",
        )
        if (feedHeadParamsMatch != null) {
            feedHeadParamsField = feedHeadParamsMatch.fieldName
        }

        val recommendCardNestedDataScan = runScanStep(
            "CustomPostCardBlockHook.RecommendCard",
            logger,
            scanErrors,
            RecommendCardNestedDataScanSymbols(),
        ) {
            scanRecommendCardNestedDataSymbols(cl, logger)
        }
        feedRecommendCardNestedDataMethod = recommendCardNestedDataScan.nestedDataMethod
        feedRecommendCardNestedDataListField = recommendCardNestedDataScan.nestedDataListField
        
        val splashAdMatch = runRules(candidatesWithWhitelist, cl, listOf(SplashAdHelperRule()), logger, "splashAdHelper")
        if (splashAdMatch != null) {
            splashAdHelperClass = splashAdMatch.className
            splashAdHelperMethod = splashAdMatch.methodName
        }
        
        val closeAdMatch = runRules(
            listOf("com.baidu.tbadk.data.CloseAdData"),
            cl,
            listOf(CloseAdDataRule()),
            logger,
            "closeAdData",
        )
        if (closeAdMatch != null) {
            closeAdDataClass = closeAdMatch.className
            val parts = closeAdMatch.methodName.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                closeAdDataMethodG1 = parts[0]
                closeAdDataMethodJ1 = parts.drop(1).joinToString(",")
            }
        }
        
        
        val zgaMatch = runRules(candidatesWithWhitelist, cl, listOf(ZgaRule()), logger, "zga")
        if (zgaMatch != null) {
            val candidateMethods = zgaMatch.methodName.split(",").filter { it.isNotBlank() }.distinct()
            if (isZgaScanResultValid(cl, zgaMatch.className, candidateMethods)) {
                zgaClass = zgaMatch.className
                zgaMethodsList = candidateMethods
            } else {
                log(
                    logger,
                    "zga scan rejected: class=${zgaMatch.className}, methods=${candidateMethods.joinToString(",")}",
                )
            }
        }

        val searchBoxMatch = runRules(candidatesWithWhitelist, cl, listOf(SearchBoxSetHintRule()), logger, "searchBoxHint")
        if (searchBoxMatch != null) {
            searchBoxViewClass = searchBoxMatch.className
            searchBoxSetHintMethod = searchBoxMatch.methodName
        }
        val resolvedSearchBoxViewClass = searchBoxViewClass
        if (resolvedSearchBoxViewClass != null) {
            val homeSearchBoxOwnerMatch = runRules(
                listOf(HOME_SEARCH_BOX_OWNER_CLASS),
                cl,
                listOf(HomeSearchBoxOwnerRule(HOME_SEARCH_BOX_OWNER_CLASS, resolvedSearchBoxViewClass)),
                logger,
                "homeSearchBoxOwner",
            )
            if (homeSearchBoxOwnerMatch != null) {
                homeSearchBoxOwnerClass = homeSearchBoxOwnerMatch.className
                homeSearchBoxInitMethod = homeSearchBoxOwnerMatch.methodName
                homeSearchBoxGetterMethod = homeSearchBoxOwnerMatch.fieldName
            }
        }

        val homeRightSlotMatch = runRules(
            listOf(HOME_RIGHT_SLOT_CLASS),
            cl,
            listOf(HomeTopBarRightSlotRule(HOME_RIGHT_SLOT_CLASS)),
            logger,
            "homeRightSlot",
        )
        if (homeRightSlotMatch != null) {
            homeRightSlotClass = homeRightSlotMatch.className
            homeRightSlotStateMethods = homeRightSlotMatch.methodName
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        val pbFallingMatch = runRules(candidatesWithWhitelist, cl, listOf(PbFallingViewRule()), logger, "pbFalling")
        if (pbFallingMatch != null) {
            pbFallingViewClass = pbFallingMatch.className
            val parts = pbFallingMatch.methodName.split(",")
            if (parts.size >= 3) {
                pbFallingInitMethod = parts[0]
                pbFallingShowMethod = parts[1]
                pbFallingClearMethod = parts[2]
            }
        }

        val pbEarlyAdInsertScan = runScanStep(
            "PbEarlyAdBlockHook",
            logger,
            scanErrors,
            PbEarlyAdInsertScanSymbols(null, emptyList()),
        ) {
            scanPbEarlyAdInsertSymbols(candidatesWithWhitelist, cl, logger)
        }
        if (pbEarlyAdInsertScan.className != null && pbEarlyAdInsertScan.methodSpecs.isNotEmpty()) {
            pbEarlyAdInsertClass = pbEarlyAdInsertScan.className
            pbEarlyAdInsertMethodSpecs = pbEarlyAdInsertScan.methodSpecs
        }

        val pbAdBidScan = runScanStep(
            "PbAdRequestBlockHook.AdBid",
            logger,
            scanErrors,
            PbAdBidScanSymbols(),
        ) {
            scanPbAdBidSymbols(context, candidatesWithWhitelist, cl, logger)
        }
        pbAdBidCommonRequestModelClass = pbAdBidScan.commonRequestModelClass
        pbAdBidCommonRequestStartMethods = pbAdBidScan.commonRequestStartMethods.takeIf { it.isNotEmpty() }
        pbAdBidCommonRequestNotifyMethod = pbAdBidScan.commonRequestNotifyMethod
        pbAdBidPageBrowserRequestModelClass = pbAdBidScan.pageBrowserRequestModelClass
        pbAdBidPageBrowserRequestDataMethod = pbAdBidScan.pageBrowserRequestDataMethod

        val typeAdapterDataFilterScan = runScanStep(
            "PostAdHook.DataFilter",
            logger,
            scanErrors,
            TypeAdapterDataFilterScanSymbols(),
        ) {
            scanTypeAdapterDataFilterSymbols(cl, logger)
        }
        typeAdapterSetDataMethod = typeAdapterDataFilterScan.setDataMethod
        typeAdapterDataItemClass = typeAdapterDataFilterScan.dataItemClass
        typeAdapterDataGetTypeMethod = typeAdapterDataFilterScan.dataGetTypeMethod

        val tbWebViewClass = safeFindClass(TB_WEB_VIEW_CLASS, cl)
        if (tbWebViewClass == null) {
            log(logger, "enterForumWeb: TbWebView class not found")
        } else {
            val enterForumWebMatch = runRules(
                obfuscatedWithWhitelist,
                cl,
                listOf(EnterForumWebControllerRule(tbWebViewClass)),
                logger,
                "enterForumWeb",
            )
            if (enterForumWebMatch != null) {
                enterForumWebControllerClass = enterForumWebMatch.className
                enterForumWebLoadMethod = enterForumWebMatch.methodName
            }
        }

        val enterForumInitInfoDataMatch = runScanStep(
            "EnterForumWebHook.InitInfoData",
            logger,
            scanErrors,
            null as Pair<String, String>?,
        ) {
            scanEnterForumInitInfoDataSymbols(cl, logger)
        }
        if (enterForumInitInfoDataMatch != null) {
            enterForumInitInfoDataClass = enterForumInitInfoDataMatch.first
            enterForumInitInfoGetUrlMethod = enterForumInitInfoDataMatch.second
        }

        val plainUrlSpanScan = runScanStep(
            "PlainUrlDirectBrowserHook.Direct",
            logger,
            scanErrors,
            PlainUrlClickableSpanScanSymbols(),
        ) {
            scanPlainUrlClickableSpanSymbols(candidatesWithWhitelist, cl, logger)
        }
        plainUrlClickableSpanClass = plainUrlSpanScan.className
        plainUrlClickableSpanOnClickMethod = plainUrlSpanScan.onClickMethod
        plainUrlClickableSpanOnClickOwnerClasses = plainUrlSpanScan.onClickOwnerClasses
        plainUrlClickableSpanTypeField = plainUrlSpanScan.typeField
        plainUrlClickableSpanUrlField = plainUrlSpanScan.urlField
        plainUrlClickableSpanTextField = plainUrlSpanScan.textField
        val plainUrlMessageScan = runScanStep(
            "PlainUrlDirectBrowserHook.Message",
            logger,
            scanErrors,
            PlainUrlMessageDispatchScanSymbols(),
        ) {
            scanPlainUrlMessageDispatchSymbols(cl, logger)
        }
        plainUrlMessageManagerClass = plainUrlMessageScan.messageManagerClass
        plainUrlMessageDispatchMethod = plainUrlMessageScan.dispatchMethod
        plainUrlResponsedMessageClass = plainUrlMessageScan.responsedMessageClass
        plainUrlResponsedMessageGetCmdMethod = plainUrlMessageScan.getCmdMethod
        plainUrlCustomResponsedMessageClass = plainUrlMessageScan.customResponsedMessageClass
        plainUrlCustomResponsedMessageGetDataMethod = plainUrlMessageScan.getDataMethod
        plainUrlApplicationClass = plainUrlMessageScan.applicationClass
        plainUrlApplicationGetInstMethod = plainUrlMessageScan.getInstMethod

        val privateReadReceiptScan = runScanStep(
            "PrivateReadReceiptBlockHook",
            logger,
            scanErrors,
            PrivateReadReceiptScanSymbols(),
        ) {
            scanPrivateReadReceiptSymbols(cl, logger)
        }
        privateReadReceiptModelClass = privateReadReceiptScan.modelClass
        privateReadReceiptModelReadDispatchMethod = privateReadReceiptScan.modelReadDispatchMethod
        privateReadReceiptMessageManagerClass = privateReadReceiptScan.messageManagerClass
        privateReadReceiptMessageManagerGetInstanceMethod = privateReadReceiptScan.messageManagerGetInstanceMethod
        privateReadReceiptMessageSendMethod = privateReadReceiptScan.messageSendMethod
        privateReadReceiptMessageBaseClass = privateReadReceiptScan.messageBaseClass
        privateReadReceiptRequestClass = privateReadReceiptScan.requestClass
        privateReadReceiptModelBaseClass = privateReadReceiptScan.modelBaseClass
        privateReadReceiptCommitResponseClass = privateReadReceiptScan.commitResponseClass
        privateReadReceiptProcessAckMethod = privateReadReceiptScan.processAckMethod
        privateReadReceiptResponseErrorMethod = privateReadReceiptScan.responseErrorMethod
        privateReadReceiptRequestMsgIdField = privateReadReceiptScan.requestMsgIdField
        privateReadReceiptRequestToUidField = privateReadReceiptScan.requestToUidField
        privateReadReceiptModelDataField = privateReadReceiptScan.modelDataField
        privateReadReceiptPageDataClass = privateReadReceiptScan.pageDataClass
        privateReadReceiptPageDataChatListMethod = privateReadReceiptScan.pageDataChatListMethod
        privateReadReceiptChatMessageClass = privateReadReceiptScan.chatMessageClass
        privateReadReceiptChatMessageMsgIdMethod = privateReadReceiptScan.chatMessageMsgIdMethod
        privateReadReceiptChatMessageUserIdMethod = privateReadReceiptScan.chatMessageUserIdMethod
        privateReadReceiptChatMessageLocalDataMethod = privateReadReceiptScan.chatMessageLocalDataMethod
        privateReadReceiptLocalDataClass = privateReadReceiptScan.localDataClass
        privateReadReceiptLocalDataStatusMethod = privateReadReceiptScan.localDataStatusMethod
        privateReadReceiptAccountClass = privateReadReceiptScan.accountClass
        privateReadReceiptCurrentAccountMethod = privateReadReceiptScan.currentAccountMethod

        val mountCardLinkScan = runScanStep(
            "PlainUrlDirectBrowserHook.MountCard",
            logger,
            scanErrors,
            MountCardLinkLayoutScanSymbols(),
        ) {
            scanMountCardLinkLayoutSymbols(cl, logger)
        }
        mountCardLinkLayoutClass = mountCardLinkScan.layoutClass
        mountCardLinkLayoutOnClickMethod = mountCardLinkScan.onClickMethod
        mountCardLinkLayoutDataField = mountCardLinkScan.dataField
        mountCardLinkInfoDataClass = mountCardLinkScan.dataClass
        mountCardLinkInfoGetUrlMethod = mountCardLinkScan.getUrlMethod

        val forumBottomSheetMatch = runRules(
            listOf(FORUM_BOTTOM_SHEET_VIEW_CLASS),
            cl,
            listOf(ForumBottomSheetInitScrollRule(FORUM_BOTTOM_SHEET_VIEW_CLASS)),
            logger,
            "forumBottomSheet",
        )
        if (forumBottomSheetMatch != null) {
            forumBottomSheetViewClass = forumBottomSheetMatch.className
            forumBottomSheetInitScrollMethod = forumBottomSheetMatch.methodName
        }

        autoRefreshTriggerMethod = runScanStep(
            "AutoRefreshHook",
            logger,
            scanErrors,
            null as String?,
        ) {
            scanAutoRefreshTriggerSymbol(context, cl, logger)
        }

        val autoLoadMoreMatch = runRules(
            listOf(HOME_PRELOAD_CONFIG_COMPANION_CLASS),
            cl,
            listOf(AutoLoadMoreConfigRule(HOME_PRELOAD_CONFIG_PARSER_CLASS)),
            logger,
            "autoLoadMore",
        )
        if (autoLoadMoreMatch != null) {
            autoLoadMoreConfigClass = autoLoadMoreMatch.className
            autoLoadMoreConfigMethod = autoLoadMoreMatch.methodName
        }

        val pbCommentScrollMatch = runRules(
            candidatesWithWhitelist,
            cl,
            listOf(PbCommentScrollRule(PB_FRAGMENT_CLASS)),
            logger,
            "pbCommentScroll",
        )
        if (pbCommentScrollMatch != null) {
            pbCommentScrollListenerClass = pbCommentScrollMatch.className
            pbCommentScrollMethod = pbCommentScrollMatch.methodName
            val fields = unpack(pbCommentScrollMatch.fieldName, 3)
            pbCommentScrollFragmentField = fields[0]
            pbCommentScrollBottomListenerField = fields[1]
            pbCommentScrollBottomMethod = fields[2]
        }

        fun bottomMechanismCandidates(ownerClassName: String): List<String> {
            val nestedPrefix = "$ownerClassName\$"
            val nested = candidatesWithWhitelist
                .filter { it.startsWith(nestedPrefix) }
                .distinct()
            return if (nested.isNotEmpty()) nested else listOf(ownerClassName)
        }

        val pbCommentBottomListMatch = runRules(
            bottomMechanismCandidates(StableTiebaHookPoints.BD_LIST_VIEW_CLASS),
            cl,
            listOf(BdListViewBottomScrollRule(StableTiebaHookPoints.BD_LIST_VIEW_CLASS)),
            logger,
            "pbCommentBottomList",
        )
        if (pbCommentBottomListMatch != null) {
            pbCommentBottomListScrollClass = pbCommentBottomListMatch.className
            pbCommentBottomListScrollMethod = pbCommentBottomListMatch.methodName
            pbCommentBottomListOwnerField = pbCommentBottomListMatch.fieldName
        }

        val pbCommentBottomRecyclerMatch = runRules(
            bottomMechanismCandidates(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS),
            cl,
            listOf(BdRecyclerViewBottomScrollRule(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS)),
            logger,
            "pbCommentBottomRecycler",
        )
        if (pbCommentBottomRecyclerMatch != null) {
            pbCommentBottomRecyclerScrollClass = pbCommentBottomRecyclerMatch.className
            pbCommentBottomRecyclerScrollMethod = pbCommentBottomRecyclerMatch.methodName
            pbCommentBottomRecyclerOwnerField = pbCommentBottomRecyclerMatch.fieldName
        }

        val pbGestureScaleMatch = runRules(
            candidatesWithWhitelist,
            cl,
            listOf(PbGestureScaleRule()),
            logger,
            "pbGestureScale",
        )
        if (pbGestureScaleMatch != null) {
            pbGestureScaleManagerClass = pbGestureScaleMatch.className
            pbGestureScaleListenerClass = pbGestureScaleMatch.fieldName.ifBlank { null }
            val methods = unpack(pbGestureScaleMatch.methodName, 3)
            pbGestureScaleDispatchMethod = methods[0]
            pbGestureScaleListenerSetterMethod = methods[1]
            pbGestureScaleListenerOnScaleMethod = methods[2]
        }

        val shareTrackMatch = runRules(candidatesWithWhitelist, cl, listOf(ShareTrackUrlBuilderRule()), logger, "shareTrack")
        if (shareTrackMatch != null) {
            shareTrackBuilderClass = shareTrackMatch.className
            val parts = shareTrackMatch.methodName.split(",")
            if (parts.isNotEmpty()) {
                shareTrackBuildUrlMethod = parts[0]
            }
            if (parts.size >= 2) {
                shareTrackAppendQueryMethod = parts[1]
            }
        }

        val imageViewerShareItemMatch = runRules(
            listOf("com.baidu.tbadk.coreExtra.share.ShareItem"),
            cl,
            listOf(ImageViewerShareItemRule()),
            logger,
            "imageViewerShareItem",
        )
        if (imageViewerShareItemMatch != null) {
            imageViewerShareItemClass = imageViewerShareItemMatch.className
            val itemFields = unpack(imageViewerShareItemMatch.fieldName, 6)
            imageViewerShareItemTitleField = itemFields[0]
            imageViewerShareItemContentField = itemFields[1]
            imageViewerShareItemLinkUrlField = itemFields[2]
            imageViewerShareItemImageUriField = itemFields[3]
            imageViewerShareItemImageUrlField = itemFields[4]
            imageViewerShareItemLocalFileField = itemFields[5]
        }

        val shareItemClassName = imageViewerShareItemClass
        if (!shareItemClassName.isNullOrBlank()) {
            val imageViewerShareConfigMatch = runRules(
                candidatesWithWhitelist,
                cl,
                listOf(ImageViewerShareConfigRule(shareItemClassName)),
                logger,
                "imageViewerShareConfig",
            )
            if (imageViewerShareConfigMatch != null) {
                imageViewerShareConfigClass = imageViewerShareConfigMatch.className
                val configMethods = unpack(imageViewerShareConfigMatch.methodName, 4)
                imageViewerShareAddOutsideMethod = configMethods[0]
                imageViewerShareGetRequestDataMethod = configMethods[1]
                imageViewerShareSetRequestDataMethod = configMethods[2]
                imageViewerShareGetContextMethod = configMethods[3]

                val configFields = unpack(imageViewerShareConfigMatch.fieldName, 2)
                imageViewerShareIsDialogField = configFields[0]
                imageViewerShareItemField = configFields[1]
            }
        }

        val imageViewerShareItemViewMatch = runRules(
            candidatesWithWhitelist,
            cl,
            listOf(ImageViewerShareItemViewRule()),
            logger,
            "imageViewerShareItemView",
        )
        if (imageViewerShareItemViewMatch != null) {
            imageViewerShareItemViewClass = imageViewerShareItemViewMatch.className
            val itemViewMethods = unpack(imageViewerShareItemViewMatch.methodName, 2)
            imageViewerShareItemNameByResMethod = itemViewMethods[0]
            imageViewerShareItemNameByTextMethod = itemViewMethods[1]
        }

        val imageViewerShareIconMatch = runRules(
            listOf("com.baidu.tieba.R\$drawable"),
            cl,
            listOf(ImageViewerShareIconResourceRule()),
            logger,
            "imageViewerShareIcon",
        )
        if (imageViewerShareIconMatch != null) {
            imageViewerShareIconResId = imageViewerShareIconMatch.fieldName.toIntOrNull()
        } else {
            imageViewerShareIconResId = runScanStep(
                "ImageViewerNativeShareHook.Icon",
                logger,
                scanErrors,
                null as Int?,
            ) {
                scanImageViewerShareIconFromDex(
                    context = context,
                    candidates = candidatesWithWhitelist,
                    cl = cl,
                    logger = logger,
                )
            }
        }

        val anchorCandidates = listOf(
            HOME_SEARCH_BOX_OWNER_CLASS,
            "com.baidu.tieba.homepage.personalize.PersonalizePageView",
            "com.baidu.searchbox.task.view.mainactivity.InitPersonalizeViewTask",
        )
        val resolvedAnchors = anchorCandidates.filter { safeFindClass(it, cl) != null }
        homePersonalizeAnchorClasses = if (resolvedAnchors.isNotEmpty()) resolvedAnchors else null

        val homeNativeGlassResourceIds = runScanStep(
            "HomeNativeGlassHook.Resources",
            logger,
            scanErrors,
            HomeNativeGlassResourceIds(),
        ) {
            scanHomeNativeGlassResourceIds(context, cl, logger)
        }
        homeNativeGlassSubPbNextPageMoreViewId = homeNativeGlassResourceIds.subPbNextPageMoreViewId
        homeNativeGlassPbReplyTitleDividerViewId = homeNativeGlassResourceIds.pbReplyTitleDividerViewId
        val homeNativeGlassDynamicBackgroundColorIds = homeNativeGlassResourceIds.dynamicBackgroundColorIds

        val homeNativeGlassSortSwitchSymbols = runScanStep(
            "HomeNativeGlassHook.SortSwitch",
            logger,
            scanErrors,
            HomeNativeGlassSortSwitchSymbols(),
        ) {
            scanHomeNativeGlassSortSwitchSymbols(cl, logger)
        }
        homeNativeGlassSortSwitchBackgroundPaintField =
            homeNativeGlassSortSwitchSymbols.backgroundPaintField
        homeNativeGlassSortSwitchSlideDrawMethod =
            homeNativeGlassSortSwitchSymbols.slideDrawMethod
        homeNativeGlassSortSwitchSlidePathField =
            homeNativeGlassSortSwitchSymbols.slidePathField

        val homeNativeGlassEnterForumCapsuleSymbols = runScanStep(
            "HomeNativeGlassHook.EnterForumCapsule",
            logger,
            scanErrors,
            HomeNativeGlassEnterForumCapsuleSymbols(),
        ) {
            scanHomeNativeGlassEnterForumCapsuleSymbols(context, candidatesWithWhitelist, cl, logger)
        }
        homeNativeGlassEnterForumCapsuleControllerClass =
            homeNativeGlassEnterForumCapsuleSymbols.controllerClass
        homeNativeGlassEnterForumCapsuleInitMethod =
            homeNativeGlassEnterForumCapsuleSymbols.initMethod
        homeNativeGlassEnterForumCapsuleRefreshMethod =
            homeNativeGlassEnterForumCapsuleSymbols.refreshMethod
        homeNativeGlassEnterForumCapsuleViewField =
            homeNativeGlassEnterForumCapsuleSymbols.viewField
        homeNativeGlassEnterForumCapsuleTitleField =
            homeNativeGlassEnterForumCapsuleSymbols.titleField

        pbCommonLayoutPreloaderGetOrDefaultMethod = runScanStep(
            "HomeNativeGlassHook.CommonLayoutPreloader",
            logger,
            scanErrors,
            null as String?,
        ) {
            scanPbCommonLayoutPreloaderGetOrDefaultMethod(cl, logger)
        }

        val collectionScan = runScanStep(
            "CollectionSearchHook",
            logger,
            scanErrors,
            CollectionSearchScanSymbols(),
        ) {
            scanCollectionSearchSymbols(cl, logger)
        }
        collectionPresenterField = collectionScan.presenterField
        collectionPresenterListSetterMethod = collectionScan.presenterListSetterMethod
        collectionPresenterAdapterField = collectionScan.presenterAdapterField
        collectionModelField = collectionScan.modelField
        collectionModelListGetterMethod = collectionScan.modelListGetterMethod
        collectionModelParseMethod = collectionScan.modelParseMethod
        collectionModelListField = collectionScan.modelListField
        collectionFragmentDisplayListField = collectionScan.fragmentDisplayListField
        collectionActivityNavControllerField = collectionScan.activityNavControllerField
        collectionNavBarField = collectionScan.navBarField
        collectionAdapterShowFooterMethod = collectionScan.adapterShowFooterMethod
        collectionAdapterLoadingMethod = collectionScan.adapterLoadingMethod
        collectionAdapterHasMoreMethod = collectionScan.adapterHasMoreMethod
        collectionEditModeMethod = collectionScan.editModeMethod

        val historyScan = runScanStep(
            "HistorySearchHook",
            logger,
            scanErrors,
            HistorySearchScanSymbols(),
        ) {
            scanHistorySearchSymbols(cl, logger)
        }
        historyAdapterField = historyScan.adapterField
        historyAdapterSetListMethod = historyScan.adapterSetListMethod
        historyListField = historyScan.listField
        historyActivityListUpdateMethod = historyScan.activityListUpdateMethod
        historyActivityNavBarField = historyScan.activityNavBarField
        historyThreadNameMethod = historyScan.threadNameMethod
        historyForumNameMethod = historyScan.forumNameMethod
        historyUserNameMethod = historyScan.userNameMethod
        historyDescriptionMethod = historyScan.descriptionMethod
        historyThreadIdMethod = historyScan.threadIdMethod
        historyPostIdMethod = historyScan.postIdMethod
        historyLiveIdMethod = historyScan.liveIdMethod

        val msgTabScan = runScanStep(
            "MsgTabDefaultNotifyHook",
            logger,
            scanErrors,
            MsgTabScanSymbols(),
        ) {
            scanMsgTabSymbols(cl, logger)
        }
        msgTabLocateToTabMethod = msgTabScan.locateToTabMethod
        msgTabContainerSelectMethod = msgTabScan.containerSelectMethod
        msgTabContainerExtDataField = msgTabScan.containerExtDataField

        val freeCopyPopupMatch = runRules(
            candidatesWithWhitelist,
            cl,
            listOf(FreeCopyPopupRule()),
            logger,
            "freeCopyPopup",
        )
        if (freeCopyPopupMatch != null) {
            freeCopyPopupMenuClass = freeCopyPopupMatch.className
            freeCopyPopupContentViewMethod = freeCopyPopupMatch.methodName
            freeCopyPopupTextField = freeCopyPopupMatch.fieldName
        }

        val mainTabBottomScan = runScanStep(
            "MainTabBottomHook",
            logger,
            scanErrors,
            MainTabBottomScanSymbols(),
        ) {
            scanMainTabBottomSymbols(candidatesWithWhitelist, cl, logger)
        }
        mainTabDataClass = mainTabBottomScan.dataClass
        mainTabAddMethod = mainTabBottomScan.addMethod
        mainTabGetListMethod = mainTabBottomScan.getListMethod
        mainTabDelegateGetStructureMethod = mainTabBottomScan.delegateGetStructureMethod
        mainTabStructureTypeField = mainTabBottomScan.structureTypeField
        mainTabStructureDynamicIconField = mainTabBottomScan.structureDynamicIconField
        mainTabStructureFragmentField = mainTabBottomScan.structureFragmentField

        val originalImageScan = runScanStep(
            "DefaultOriginalImageHook",
            logger,
            scanErrors,
            OriginalImageScanSymbols(),
        ) {
            scanOriginalImageSymbols(cl, logger)
        }
        val aiComponentScan = runScanStep(
            "AiComponentDisableHook",
            logger,
            scanErrors,
            AiComponentScanSymbols(),
        ) {
            scanAiComponentSymbols(context, cl, candidatesWithWhitelist, logger)
        }
        aiSpriteMemePanControllerClass = aiComponentScan.spriteMemePanControllerClass
        aiSpriteMemeEnableMethod = aiComponentScan.spriteMemeEnableMethod
        aiPbNewInputContainerClass = aiComponentScan.pbNewInputContainerClass
        aiPbNewInputContainerInitSpriteMemeMethod = aiComponentScan.pbInitSpriteMemeMethod
        aiPbNewInputContainerInitAiWriteMethod = aiComponentScan.pbInitAiWriteMethod
        aiPbAiEmojiCreationViewBindMethod = aiComponentScan.pbAiEmojiCreationViewBindMethod
        aiPbPageBrowserAiEmojiCreationBindMethod = aiComponentScan.pbPageBrowserAiEmojiCreationBindMethod
        aiImageViewerJumpButtonOwnerClass = aiComponentScan.imageViewerJumpButtonOwnerClass
        aiImageViewerJumpButtonInitMethod = aiComponentScan.imageViewerJumpButtonInitMethod
        val scanned = HookSymbols(
            settingsClass = settingsMatch?.className,
            settingsInitMethod = settingsMatch?.methodName,
            settingsContainerField = settingsMatch?.fieldName,
            homeTabClass = homeMatch?.className,
            homeTabRebuildMethod = homeMatch?.methodName,
            homeTabListField = homeMatch?.fieldName,
            homeTabItemTypeField = homeTabItemTypeField,
            homeTabItemCodeField = homeTabItemCodeField,
            homeTabItemNameField = homeTabItemNameField,
            homeTabItemUrlField = homeTabItemUrlField,
            homeTabItemMainSetterMethod = homeTabItemMainSetterMethod,
            homeTabItemMainIntField = homeTabItemMainIntField,
            homeTabItemMainBooleanField = homeTabItemMainBooleanField,
            feedTemplateKeyMethod = feedKeyMethod,
            feedTemplatePayloadMethod = feedPayloadMethod,
            feedTemplateLoadMoreMethod = feedLoadMoreMethod,
            feedCardBindMethod = feedCardBindMethod,
            feedCardDataListField = feedCardDataListField,
            feedHeadParamsField = feedHeadParamsField,
            feedRecommendCardNestedDataMethod = feedRecommendCardNestedDataMethod,
            feedRecommendCardNestedDataListField = feedRecommendCardNestedDataListField,
            splashAdHelperClass = splashAdHelperClass,
            splashAdHelperMethod = splashAdHelperMethod,
            closeAdDataClass = closeAdDataClass,
            closeAdDataMethodG1 = closeAdDataMethodG1,
            closeAdDataMethodJ1 = closeAdDataMethodJ1,
            zgaClass = zgaClass,
            zgaMethods = zgaMethodsList,
            searchBoxViewClass = searchBoxViewClass,
            searchBoxSetHintMethod = searchBoxSetHintMethod,
            homeSearchBoxOwnerClass = homeSearchBoxOwnerClass,
            homeSearchBoxInitMethod = homeSearchBoxInitMethod,
            homeSearchBoxGetterMethod = homeSearchBoxGetterMethod,
            homePersonalizeAnchorClasses = homePersonalizeAnchorClasses,
            homeRightSlotClass = homeRightSlotClass,
            homeRightSlotStateMethods = homeRightSlotStateMethods,
            pbFallingViewClass = pbFallingViewClass,
            pbFallingInitMethod = pbFallingInitMethod,
            pbFallingShowMethod = pbFallingShowMethod,
            pbFallingClearMethod = pbFallingClearMethod,
            pbEarlyAdInsertClass = pbEarlyAdInsertClass,
            pbEarlyAdInsertMethodSpecs = pbEarlyAdInsertMethodSpecs,
            pbAdBidCommonRequestModelClass = pbAdBidCommonRequestModelClass,
            pbAdBidCommonRequestStartMethods = pbAdBidCommonRequestStartMethods,
            pbAdBidCommonRequestNotifyMethod = pbAdBidCommonRequestNotifyMethod,
            pbAdBidPageBrowserRequestModelClass = pbAdBidPageBrowserRequestModelClass,
            pbAdBidPageBrowserRequestDataMethod = pbAdBidPageBrowserRequestDataMethod,
            typeAdapterSetDataMethod = typeAdapterSetDataMethod,
            typeAdapterDataItemClass = typeAdapterDataItemClass,
            typeAdapterDataGetTypeMethod = typeAdapterDataGetTypeMethod,
            enterForumWebControllerClass = enterForumWebControllerClass,
            enterForumWebLoadMethod = enterForumWebLoadMethod,
            enterForumInitInfoDataClass = enterForumInitInfoDataClass,
            enterForumInitInfoGetUrlMethod = enterForumInitInfoGetUrlMethod,
            plainUrlClickableSpanClass = plainUrlClickableSpanClass,
            plainUrlClickableSpanOnClickMethod = plainUrlClickableSpanOnClickMethod,
            plainUrlClickableSpanOnClickOwnerClasses = plainUrlClickableSpanOnClickOwnerClasses,
            plainUrlClickableSpanTypeField = plainUrlClickableSpanTypeField,
            plainUrlClickableSpanUrlField = plainUrlClickableSpanUrlField,
            plainUrlClickableSpanTextField = plainUrlClickableSpanTextField,
            plainUrlMessageManagerClass = plainUrlMessageManagerClass,
            plainUrlMessageDispatchMethod = plainUrlMessageDispatchMethod,
            plainUrlResponsedMessageClass = plainUrlResponsedMessageClass,
            plainUrlResponsedMessageGetCmdMethod = plainUrlResponsedMessageGetCmdMethod,
            plainUrlCustomResponsedMessageClass = plainUrlCustomResponsedMessageClass,
            plainUrlCustomResponsedMessageGetDataMethod = plainUrlCustomResponsedMessageGetDataMethod,
            plainUrlApplicationClass = plainUrlApplicationClass,
            plainUrlApplicationGetInstMethod = plainUrlApplicationGetInstMethod,
            privateReadReceiptModelClass = privateReadReceiptModelClass,
            privateReadReceiptModelReadDispatchMethod = privateReadReceiptModelReadDispatchMethod,
            privateReadReceiptMessageManagerClass = privateReadReceiptMessageManagerClass,
            privateReadReceiptMessageManagerGetInstanceMethod = privateReadReceiptMessageManagerGetInstanceMethod,
            privateReadReceiptMessageSendMethod = privateReadReceiptMessageSendMethod,
            privateReadReceiptMessageBaseClass = privateReadReceiptMessageBaseClass,
            privateReadReceiptRequestClass = privateReadReceiptRequestClass,
            privateReadReceiptModelBaseClass = privateReadReceiptModelBaseClass,
            privateReadReceiptCommitResponseClass = privateReadReceiptCommitResponseClass,
            privateReadReceiptProcessAckMethod = privateReadReceiptProcessAckMethod,
            privateReadReceiptResponseErrorMethod = privateReadReceiptResponseErrorMethod,
            privateReadReceiptRequestMsgIdField = privateReadReceiptRequestMsgIdField,
            privateReadReceiptRequestToUidField = privateReadReceiptRequestToUidField,
            privateReadReceiptModelDataField = privateReadReceiptModelDataField,
            privateReadReceiptPageDataClass = privateReadReceiptPageDataClass,
            privateReadReceiptPageDataChatListMethod = privateReadReceiptPageDataChatListMethod,
            privateReadReceiptChatMessageClass = privateReadReceiptChatMessageClass,
            privateReadReceiptChatMessageMsgIdMethod = privateReadReceiptChatMessageMsgIdMethod,
            privateReadReceiptChatMessageUserIdMethod = privateReadReceiptChatMessageUserIdMethod,
            privateReadReceiptChatMessageLocalDataMethod = privateReadReceiptChatMessageLocalDataMethod,
            privateReadReceiptLocalDataClass = privateReadReceiptLocalDataClass,
            privateReadReceiptLocalDataStatusMethod = privateReadReceiptLocalDataStatusMethod,
            privateReadReceiptAccountClass = privateReadReceiptAccountClass,
            privateReadReceiptCurrentAccountMethod = privateReadReceiptCurrentAccountMethod,
            mountCardLinkLayoutClass = mountCardLinkLayoutClass,
            mountCardLinkLayoutOnClickMethod = mountCardLinkLayoutOnClickMethod,
            mountCardLinkLayoutDataField = mountCardLinkLayoutDataField,
            mountCardLinkInfoDataClass = mountCardLinkInfoDataClass,
            mountCardLinkInfoGetUrlMethod = mountCardLinkInfoGetUrlMethod,
            forumBottomSheetViewClass = forumBottomSheetViewClass,
            forumBottomSheetInitScrollMethod = forumBottomSheetInitScrollMethod,
            autoRefreshTriggerMethod = autoRefreshTriggerMethod,
            autoLoadMoreConfigClass = autoLoadMoreConfigClass,
            autoLoadMoreConfigMethod = autoLoadMoreConfigMethod,
            pbCommentScrollListenerClass = pbCommentScrollListenerClass,
            pbCommentScrollMethod = pbCommentScrollMethod,
            pbCommentScrollFragmentField = pbCommentScrollFragmentField,
            pbCommentScrollBottomListenerField = pbCommentScrollBottomListenerField,
            pbCommentScrollBottomMethod = pbCommentScrollBottomMethod,
            pbCommentBottomListScrollClass = pbCommentBottomListScrollClass,
            pbCommentBottomListScrollMethod = pbCommentBottomListScrollMethod,
            pbCommentBottomListOwnerField = pbCommentBottomListOwnerField,
            pbCommentBottomRecyclerScrollClass = pbCommentBottomRecyclerScrollClass,
            pbCommentBottomRecyclerScrollMethod = pbCommentBottomRecyclerScrollMethod,
            pbCommentBottomRecyclerOwnerField = pbCommentBottomRecyclerOwnerField,
            pbGestureScaleManagerClass = pbGestureScaleManagerClass,
            pbGestureScaleDispatchMethod = pbGestureScaleDispatchMethod,
            pbGestureScaleListenerSetterMethod = pbGestureScaleListenerSetterMethod,
            pbGestureScaleListenerClass = pbGestureScaleListenerClass,
            pbGestureScaleListenerOnScaleMethod = pbGestureScaleListenerOnScaleMethod,

            collectionPresenterField = collectionPresenterField,
            collectionPresenterListSetterMethod = collectionPresenterListSetterMethod,
            collectionPresenterAdapterField = collectionPresenterAdapterField,
            collectionModelField = collectionModelField,
            collectionModelListGetterMethod = collectionModelListGetterMethod,
            collectionModelParseMethod = collectionModelParseMethod,
            collectionModelListField = collectionModelListField,
            collectionFragmentDisplayListField = collectionFragmentDisplayListField,
            collectionActivityNavControllerField = collectionActivityNavControllerField,
            collectionNavBarField = collectionNavBarField,
            collectionAdapterShowFooterMethod = collectionAdapterShowFooterMethod,
            collectionAdapterLoadingMethod = collectionAdapterLoadingMethod,
            collectionAdapterHasMoreMethod = collectionAdapterHasMoreMethod,
            collectionEditModeMethod = collectionEditModeMethod,

            historyAdapterField = historyAdapterField,
            historyAdapterSetListMethod = historyAdapterSetListMethod,
            historyListField = historyListField,
            historyActivityListUpdateMethod = historyActivityListUpdateMethod,
            historyActivityNavBarField = historyActivityNavBarField,
            historyThreadNameMethod = historyThreadNameMethod,
            historyForumNameMethod = historyForumNameMethod,
            historyUserNameMethod = historyUserNameMethod,
            historyDescriptionMethod = historyDescriptionMethod,
            historyThreadIdMethod = historyThreadIdMethod,
            historyPostIdMethod = historyPostIdMethod,
            historyLiveIdMethod = historyLiveIdMethod,

            msgTabLocateToTabMethod = msgTabLocateToTabMethod,
            msgTabContainerSelectMethod = msgTabContainerSelectMethod,
            msgTabContainerExtDataField = msgTabContainerExtDataField,
            freeCopyPopupMenuClass = freeCopyPopupMenuClass,
            freeCopyPopupContentViewMethod = freeCopyPopupContentViewMethod,
            freeCopyPopupTextField = freeCopyPopupTextField,

            mainTabDataClass = mainTabDataClass,
            mainTabAddMethod = mainTabAddMethod,
            mainTabGetListMethod = mainTabGetListMethod,
            mainTabDelegateGetStructureMethod = mainTabDelegateGetStructureMethod,
            mainTabStructureTypeField = mainTabStructureTypeField,
            mainTabStructureDynamicIconField = mainTabStructureDynamicIconField,
            mainTabStructureFragmentField = mainTabStructureFragmentField,

            origImagePagerAdapterClass = originalImageScan.pagerAdapterClass,
            origImageUrlDragImageViewClass = originalImageScan.urlDragImageViewClass,
            origImageDataClass = originalImageScan.dataClass,
            origImageSetPrimaryItemMethod = originalImageScan.setPrimaryItemMethod,
            origImageSetAssistUrlMethod = originalImageScan.setAssistUrlMethod,
            origImageAssistDataMethod = originalImageScan.assistDataMethod,
            origImageOriginTextMethod = originalImageScan.originTextMethod,
            origImageShowButtonField = originalImageScan.showButtonField,
            origImageBlockedField = originalImageScan.blockedField,
            origImageOriginalProcessField = originalImageScan.originalProcessField,
            origImageOriginalUrlField = originalImageScan.originalUrlField,
            origImageSharedPrefHelperClass = originalImageScan.sharedPrefHelperClass,
            origImageSharedPrefGetInstanceMethod = originalImageScan.sharedPrefGetInstanceMethod,
            origImageSharedPrefPutBooleanMethod = originalImageScan.sharedPrefPutBooleanMethod,
            origImageMd5Class = originalImageScan.md5Class,
            origImageMd5Method = originalImageScan.md5Method,
            origImagePrimaryReadyMethod = originalImageScan.primaryReadyMethod,
            origImageTriggerMethod = originalImageScan.triggerMethod,
            origImageDirectStartMethod = originalImageScan.directStartMethod,
            shareTrackBuilderClass = shareTrackBuilderClass,
            shareTrackBuildUrlMethod = shareTrackBuildUrlMethod,
            shareTrackAppendQueryMethod = shareTrackAppendQueryMethod,
            imageViewerShareConfigClass = imageViewerShareConfigClass,
            imageViewerShareIsDialogField = imageViewerShareIsDialogField,
            imageViewerShareItemField = imageViewerShareItemField,
            imageViewerShareAddOutsideMethod = imageViewerShareAddOutsideMethod,
            imageViewerShareGetRequestDataMethod = imageViewerShareGetRequestDataMethod,
            imageViewerShareSetRequestDataMethod = imageViewerShareSetRequestDataMethod,
            imageViewerShareGetContextMethod = imageViewerShareGetContextMethod,
            imageViewerShareItemClass = imageViewerShareItemClass,
            imageViewerShareItemTitleField = imageViewerShareItemTitleField,
            imageViewerShareItemContentField = imageViewerShareItemContentField,
            imageViewerShareItemLinkUrlField = imageViewerShareItemLinkUrlField,
            imageViewerShareItemImageUriField = imageViewerShareItemImageUriField,
            imageViewerShareItemImageUrlField = imageViewerShareItemImageUrlField,
            imageViewerShareItemLocalFileField = imageViewerShareItemLocalFileField,
            imageViewerShareItemViewClass = imageViewerShareItemViewClass,
            imageViewerShareItemNameByResMethod = imageViewerShareItemNameByResMethod,
            imageViewerShareItemNameByTextMethod = imageViewerShareItemNameByTextMethod,
            imageViewerShareIconResId = imageViewerShareIconResId,
            homeNativeGlassSubPbNextPageMoreViewId = homeNativeGlassSubPbNextPageMoreViewId,
            homeNativeGlassPbReplyTitleDividerViewId = homeNativeGlassPbReplyTitleDividerViewId,
            homeNativeGlassDynamicBackgroundColorIds = homeNativeGlassDynamicBackgroundColorIds,
            homeNativeGlassSortSwitchBackgroundPaintField = homeNativeGlassSortSwitchBackgroundPaintField,
            homeNativeGlassSortSwitchSlideDrawMethod = homeNativeGlassSortSwitchSlideDrawMethod,
            homeNativeGlassSortSwitchSlidePathField = homeNativeGlassSortSwitchSlidePathField,
            homeNativeGlassEnterForumCapsuleControllerClass =
                homeNativeGlassEnterForumCapsuleControllerClass,
            homeNativeGlassEnterForumCapsuleInitMethod =
                homeNativeGlassEnterForumCapsuleInitMethod,
            homeNativeGlassEnterForumCapsuleRefreshMethod =
                homeNativeGlassEnterForumCapsuleRefreshMethod,
            homeNativeGlassEnterForumCapsuleViewField =
                homeNativeGlassEnterForumCapsuleViewField,
            homeNativeGlassEnterForumCapsuleTitleField =
                homeNativeGlassEnterForumCapsuleTitleField,
            pbCommonLayoutPreloaderGetOrDefaultMethod = pbCommonLayoutPreloaderGetOrDefaultMethod,
            aiSpriteMemePanControllerClass = aiSpriteMemePanControllerClass,
            aiSpriteMemeEnableMethod = aiSpriteMemeEnableMethod,
            aiPbNewInputContainerClass = aiPbNewInputContainerClass,
            aiPbNewInputContainerInitSpriteMemeMethod = aiPbNewInputContainerInitSpriteMemeMethod,
            aiPbNewInputContainerInitAiWriteMethod = aiPbNewInputContainerInitAiWriteMethod,
            aiPbAiEmojiCreationViewBindMethod = aiPbAiEmojiCreationViewBindMethod,
            aiPbPageBrowserAiEmojiCreationBindMethod = aiPbPageBrowserAiEmojiCreationBindMethod,
            aiImageViewerJumpButtonOwnerClass = aiImageViewerJumpButtonOwnerClass,
            aiImageViewerJumpButtonInitMethod = aiImageViewerJumpButtonInitMethod,

            scanErrors = scanErrors,
            source = if (homeMatch != null) "scan" else "partial",
            createdAt = System.currentTimeMillis(),
        )
        return scanned.copy(featureStatusMap = deriveFeatureStatusMap(scanned))
    }

    private fun scanHomeNativeGlassResourceIds(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassResourceIds {
        fun resolveId(resourceName: String): Int? {
            val name = resourceName.trim()
            if (name.isEmpty()) return null

            val resourceId = context.resources.getIdentifier(name, "id", context.packageName)
            if (resourceId > 0 && isResolvedIdResource(context, resourceId, logger, "resources.getIdentifier($name)")) {
                log(logger, "homeNativeGlassResources: id/$resourceName resolved by resources name $name")
                return resourceId
            }

            for (className in TARGET_APP_R_ID_CLASSES) {
                val id = resolveRIdField(cl, className, name, logger) ?: continue
                if (isResolvedIdResource(context, id, logger, "$className.$name")) {
                    log(logger, "homeNativeGlassResources: id/$resourceName resolved by $className.$name")
                    return id
                }
            }

            log(logger, "homeNativeGlassResources: missing id/$resourceName")
            return null
        }
        fun resolveColor(resourceName: String): Int? {
            val name = resourceName.trim()
            if (name.isEmpty()) return null

            val resourceId = context.resources.getIdentifier(name, "color", context.packageName)
            if (resourceId > 0 && isResolvedColorResource(context, resourceId, logger, "resources.getIdentifier($name)")) {
                log(logger, "homeNativeGlassResources: color/$resourceName resolved by resources name $name")
                return resourceId
            }

            for (className in TARGET_APP_R_COLOR_CLASSES) {
                val id = resolveRIdField(cl, className, name, logger) ?: continue
                if (isResolvedColorResource(context, id, logger, "$className.$name")) {
                    log(logger, "homeNativeGlassResources: color/$resourceName resolved by $className.$name")
                    return id
                }
            }

            log(logger, "homeNativeGlassResources: missing color/$resourceName")
            return null
        }

        val out = HomeNativeGlassResourceIds(
            subPbNextPageMoreViewId = resolveId(HOME_NATIVE_GLASS_SUB_PB_NEXT_PAGE_MORE_VIEW_RES_NAME),
            pbReplyTitleDividerViewId = resolveId(HOME_NATIVE_GLASS_PB_REPLY_TITLE_DIVIDER_VIEW_RES_NAME),
            dynamicBackgroundColorIds = HOME_NATIVE_GLASS_DYNAMIC_BACKGROUND_COLOR_RES_NAMES
                .mapNotNull(::resolveColor)
                .distinct(),
        )
        log(
            logger,
            "homeNativeGlassResources: " +
                "subPbNext=${formatResourceId(out.subPbNextPageMoreViewId)}, " +
                "titleDivider=${formatResourceId(out.pbReplyTitleDividerViewId)}, " +
                "dynamicBackgroundColors=${out.dynamicBackgroundColorIds.size}",
        )
        return out
    }

    private fun scanHomeNativeGlassSortSwitchSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassSortSwitchSymbols {
        val sortSwitchClass = safeFindClass(StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS, cl)
            ?: run {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: class missing ${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}",
                )
                return HomeNativeGlassSortSwitchSymbols()
            }
        val fields = try {
            sortSwitchClass.declaredFields.toList()
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassSortSwitch: declaredFields failed: ${t.message}")
            return HomeNativeGlassSortSwitchSymbols()
        }
        val paintFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name == "android.graphics.Paint"
        }
        val backgroundPaint = if (paintFields.size < 3) {
            log(
                logger,
                "homeNativeGlassSortSwitch: insufficient Paint fields count=${paintFields.size}",
            )
            null
        } else {
            paintFields.singleOrNull {
                it.name == HOME_NATIVE_GLASS_SORT_SWITCH_BACKGROUND_PAINT_FIELD
            }?.takeIf { field ->
                val valid = paintFields.first() == field
                if (!valid) {
                    log(
                        logger,
                        "homeNativeGlassSortSwitch: background Paint field order mismatch, " +
                            "first=${paintFields.first().name}, selected=${field.name}",
                    )
                }
                valid
            } ?: run {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: background Paint field missing, " +
                        "paintFields=${paintFields.joinToString(",") { it.name }}",
                )
                null
            }
        }
        val pathFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name == "android.graphics.Path"
        }
        val slidePath = if (pathFields.size < 2) {
            log(
                logger,
                "homeNativeGlassSortSwitch: insufficient Path fields count=${pathFields.size}",
            )
            null
        } else {
            pathFields.singleOrNull {
                it.name == HOME_NATIVE_GLASS_SORT_SWITCH_SLIDE_PATH_FIELD
            }?.takeIf { field ->
                val valid = pathFields.last() == field
                if (!valid) {
                    log(
                        logger,
                        "homeNativeGlassSortSwitch: slide Path field order mismatch, " +
                            "last=${pathFields.last().name}, selected=${field.name}",
                    )
                }
                valid
            } ?: run {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: slide Path field missing, " +
                        "pathFields=${pathFields.joinToString(",") { it.name }}",
                )
                null
            }
        }
        val slideDrawMethod = try {
            sortSwitchClass.getDeclaredMethod(
                HOME_NATIVE_GLASS_SORT_SWITCH_SLIDE_DRAW_METHOD,
                android.graphics.Canvas::class.java,
            )
        } catch (t: Throwable) {
            log(
                logger,
                "homeNativeGlassSortSwitch: slide draw method missing " +
                    "${HOME_NATIVE_GLASS_SORT_SWITCH_SLIDE_DRAW_METHOD}(Canvas): ${t.message}",
            )
            null
        }?.takeIf { method ->
            val valid = !Modifier.isStatic(method.modifiers) && method.returnType == java.lang.Void.TYPE
            if (!valid) {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: slide draw method rejected " +
                        "${method.name} return=${method.returnType.name} static=${Modifier.isStatic(method.modifiers)}",
                )
            }
            valid
        }
        log(
            logger,
            "homeNativeGlassSortSwitch: backgroundPaint=${backgroundPaint?.name}, " +
                "slideDraw=${slideDrawMethod?.name}, slidePath=${slidePath?.name}, " +
                "paintFields=${paintFields.joinToString(",") { it.name }}, " +
                "pathFields=${pathFields.joinToString(",") { it.name }}",
        )
        return HomeNativeGlassSortSwitchSymbols(
            backgroundPaintField = backgroundPaint?.name,
            slideDrawMethod = slideDrawMethod?.name,
            slidePathField = slidePath?.name,
        )
    }

    private fun scanHomeNativeGlassEnterForumCapsuleSymbols(
        context: Context,
        candidateClassNames: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassEnterForumCapsuleSymbols {
        val baseFragmentClass = safeFindClass(HOME_NATIVE_GLASS_BASE_FRAGMENT_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: BaseFragment class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val navigationBarClass = safeFindClass(StableTiebaHookPoints.NAVIGATION_BAR_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: NavigationBar class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val pbBarImageViewClass = safeFindClass(HOME_NATIVE_GLASS_PB_BAR_IMAGE_VIEW_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: PbBarImageView class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val emTextViewClass = safeFindClass(StableTiebaHookPoints.EM_TEXT_VIEW_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: EMTextView class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }

        val classCandidates = findHomeNativeGlassEnterForumCapsuleClassCandidates(
            candidateClassNames = candidateClassNames,
            cl = cl,
            baseFragmentClass = baseFragmentClass,
            navigationBarClass = navigationBarClass,
            pbBarImageViewClass = pbBarImageViewClass,
            emTextViewClass = emTextViewClass,
            logger = logger,
        )
        if (classCandidates.isEmpty()) {
            log(logger, "homeNativeGlassEnterForumCapsule: no semantic class candidates")
            return HomeNativeGlassEnterForumCapsuleSymbols()
        }

        val sourcePaths = appSourcePaths(context)
        val scannedCandidates = classCandidates
            .take(HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT)
        val dexMatchesByClass = if (sourcePaths.isNotEmpty()) {
            DexShareIconScanner.scanEnterForumCapsules(
                sourcePaths = sourcePaths,
                ownerClassNames = scannedCandidates.map { it.clazz.name },
                logger = logger,
            )
        } else {
            emptyMap()
        }
        val resolvedCandidates = scannedCandidates
            .mapNotNull { candidate ->
                resolveHomeNativeGlassEnterForumCapsuleCandidate(
                    candidate,
                    dexMatchesByClass[candidate.clazz.name].orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<HomeNativeGlassEnterForumCapsuleResolvedCandidate> { it.score }
                    .thenBy { it.symbols.controllerClass.orEmpty().length }
                    .thenBy { it.symbols.controllerClass.orEmpty() },
            )

        val best = resolvedCandidates.firstOrNull() ?: run {
            val preview = classCandidates
                .take(6)
                .joinToString(" || ") { "${it.clazz.name}:${it.score}[${it.evidence}]" }
            log(
                logger,
                "homeNativeGlassEnterForumCapsule: no complete semantic match, " +
                    "sourcePaths=${sourcePaths.size}, top=$preview",
            )
            return HomeNativeGlassEnterForumCapsuleSymbols()
        }
        val second = resolvedCandidates.getOrNull(1)
        if (second != null && best.score - second.score < HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_MIN_SCORE_GAP) {
            log(
                logger,
                "homeNativeGlassEnterForumCapsule ambiguous: " +
                    "best=${best.symbols.controllerClass}:${best.score}[${best.evidence}], " +
                    "second=${second.symbols.controllerClass}:${second.score}[${second.evidence}]",
            )
            return HomeNativeGlassEnterForumCapsuleSymbols()
        }

        val symbols = best.symbols
        log(
            logger,
            "homeNativeGlassEnterForumCapsule matched: " +
                "${symbols.controllerClass}.${symbols.initMethod}/${symbols.refreshMethod} " +
                "view=${symbols.viewField} title=${symbols.titleField} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return symbols
    }

    private fun findHomeNativeGlassEnterForumCapsuleClassCandidates(
        candidateClassNames: List<String>,
        cl: ClassLoader,
        baseFragmentClass: Class<*>,
        navigationBarClass: Class<*>,
        pbBarImageViewClass: Class<*>,
        emTextViewClass: Class<*>,
        logger: ScanLogger?,
    ): List<HomeNativeGlassEnterForumCapsuleClassCandidate> {
        val names = (listOf(HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_CONTROLLER_CLASS) + candidateClassNames)
            .distinct()
        val out = ArrayList<HomeNativeGlassEnterForumCapsuleClassCandidate>(8)
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (className in names) {
            if (!className.startsWith("com.baidu.")) continue
            try {
                val clazz = safeFindClass(className, cl) ?: continue
                if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) continue
                val fields = collectInstanceFields(clazz)
                val methods = collectInstanceMethods(clazz)
                val scored = scoreHomeNativeGlassEnterForumCapsuleClass(
                    clazz = clazz,
                    fields = fields,
                    methods = methods,
                    baseFragmentClass = baseFragmentClass,
                    navigationBarClass = navigationBarClass,
                    pbBarImageViewClass = pbBarImageViewClass,
                    emTextViewClass = emTextViewClass,
                ) ?: continue
                out.add(
                    HomeNativeGlassEnterForumCapsuleClassCandidate(
                        clazz = clazz,
                        fields = fields,
                        methods = methods,
                        score = scored.first,
                        evidence = scored.second,
                    ),
                )
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = sanitizeScanStatusText(formatScanException(t))
                }
            }
        }
        if (skippedByReflection > 0) {
            log(
                logger,
                "homeNativeGlassEnterForumCapsule: skipped classes by reflection=$skippedByReflection" +
                    (firstReflectionError?.let { ", firstException=$it" } ?: ""),
            )
        }
        return out.sortedWith(
            compareByDescending<HomeNativeGlassEnterForumCapsuleClassCandidate> { it.score }
                .thenBy { it.clazz.name.length }
                .thenBy { it.clazz.name },
        )
    }

    private fun scoreHomeNativeGlassEnterForumCapsuleClass(
        clazz: Class<*>,
        fields: List<Field>,
        methods: List<Method>,
        baseFragmentClass: Class<*>,
        navigationBarClass: Class<*>,
        pbBarImageViewClass: Class<*>,
        emTextViewClass: Class<*>,
    ): Pair<Int, String>? {
        val constructors = try {
            clazz.declaredConstructors.toList()
        } catch (_: Throwable) {
            return null
        }
        val compatibleCtor = constructors.firstOrNull { ctor ->
            isHomeNativeGlassEnterForumCapsuleConstructor(ctor, baseFragmentClass)
        } ?: return null
        val exactCtor = compatibleCtor.parameterTypes.size == 4
        val hasNavigationBarField = fields.any { navigationBarClass.isAssignableFrom(it.type) }
        val hasBaseFragmentField = fields.any { baseFragmentClass.isAssignableFrom(it.type) }
        val hasPbBarImageField = fields.any { pbBarImageViewClass.isAssignableFrom(it.type) }
        val hasEmTextField = fields.any { emTextViewClass.isAssignableFrom(it.type) }
        val viewFields = fields.filter { View::class.java.isAssignableFrom(it.type) }
        val primaryViewFields = viewFields.filter { ViewGroup::class.java.isAssignableFrom(it.type) }
        val stringFields = fields.filter { it.type == String::class.java }
        val noArgVoidCount = methods.count(::isHomeNativeGlassNoArgVoidMethod)

        if (!hasNavigationBarField || primaryViewFields.isEmpty() || stringFields.isEmpty()) {
            return null
        }

        var score = if (exactCtor) 80 else 62
        val evidence = ArrayList<String>(10)
        evidence.add(if (exactCtor) "ctorExact" else "ctorCompatible")
        if (clazz.name == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_CONTROLLER_CLASS) {
            score += 35
            evidence.add("legacyClass")
        }
        if (hasNavigationBarField) {
            score += 62
            evidence.add("navigationBar")
        }
        if (hasBaseFragmentField) {
            score += 28
            evidence.add("baseFragment")
        }
        if (hasPbBarImageField) {
            score += 35
            evidence.add("pbBarImage")
        }
        if (hasEmTextField) {
            score += 35
            evidence.add("emText")
        }
        score += primaryViewFields.size.coerceAtMost(3) * 12
        score += stringFields.size.coerceAtMost(2) * 8
        if (noArgVoidCount >= 2) {
            score += 18
            evidence.add("voidNoArg=$noArgVoidCount")
        }
        score -= fields.size / 12
        score -= methods.size / 18
        return if (score >= HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_MIN_CLASS_SCORE) {
            score to evidence.joinToString(",")
        } else {
            null
        }
    }

    private fun isHomeNativeGlassEnterForumCapsuleConstructor(
        ctor: Constructor<*>,
        baseFragmentClass: Class<*>,
    ): Boolean {
        val params = ctor.parameterTypes
        if (params.size < 3 || params.size > 6) return false
        val hasBaseFragment = params.any { baseFragmentClass.isAssignableFrom(it) }
        val hasRootView = params.any { View::class.java.isAssignableFrom(it) }
        val hasBoolean = params.any { isBooleanType(it) }
        return hasBaseFragment && hasRootView && hasBoolean
    }

    private fun resolveHomeNativeGlassEnterForumCapsuleCandidate(
        candidate: HomeNativeGlassEnterForumCapsuleClassCandidate,
        dexMatches: List<DexEnterForumCapsuleMethodMatch>,
    ): HomeNativeGlassEnterForumCapsuleResolvedCandidate? {
        val validDexMatches = dexMatches.filter { match ->
            candidate.methods.any { method ->
                method.name == match.ownerMethodName && isHomeNativeGlassNoArgVoidMethod(method)
            }
        }
        val initDex = validDexMatches
            .filter { it.kind == DexEnterForumCapsuleMethodKind.INIT }
            .maxWithOrNull(compareBy<DexEnterForumCapsuleMethodMatch> { it.score }.thenByDescending { -it.ownerMethodName.length })
        val refreshDex = validDexMatches
            .filter { it.kind == DexEnterForumCapsuleMethodKind.REFRESH }
            .maxWithOrNull(compareBy<DexEnterForumCapsuleMethodMatch> { it.score }.thenByDescending { -it.ownerMethodName.length })

        val legacyInit = candidate.methods.singleOrNull { method ->
            method.name == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_INIT_METHOD &&
                isHomeNativeGlassNoArgVoidMethod(method)
        }?.name
        val legacyRefresh = candidate.methods.singleOrNull { method ->
            method.name == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_REFRESH_METHOD &&
                isHomeNativeGlassNoArgVoidMethod(method)
        }?.name

        val initMethodName = initDex?.ownerMethodName ?: legacyInit ?: return null
        val refreshMethodName = refreshDex?.ownerMethodName ?: legacyRefresh ?: return null
        val viewField = selectHomeNativeGlassEnterForumCapsuleViewField(
            fields = candidate.fields,
            preferredNames = listOfNotNull(
                initDex?.viewFieldName,
                refreshDex?.viewFieldName,
                HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_VIEW_FIELD,
            ),
        ) ?: return null
        val titleField = selectHomeNativeGlassEnterForumCapsuleTitleField(
            fields = candidate.fields,
            preferredNames = listOfNotNull(
                refreshDex?.titleFieldName,
                HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_TITLE_FIELD,
            ),
        ) ?: return null

        var score = candidate.score
        val evidence = ArrayList<String>(6)
        evidence.add("class=${candidate.evidence}")
        if (initDex != null) {
            score += initDex.score
            evidence.add("initDex=${initDex.ownerMethodName}[${initDex.evidence}]")
        } else if (legacyInit != null) {
            score += 45
            evidence.add("initLegacy=$legacyInit")
        }
        if (refreshDex != null) {
            score += refreshDex.score
            evidence.add("refreshDex=${refreshDex.ownerMethodName}[${refreshDex.evidence}]")
        } else if (legacyRefresh != null) {
            score += 45
            evidence.add("refreshLegacy=$legacyRefresh")
        }
        if (initDex?.viewFieldName == viewField.name) score += 24
        if (refreshDex?.viewFieldName == viewField.name) score += 18
        if (refreshDex?.titleFieldName == titleField.name) score += 24
        if (viewField.name == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_VIEW_FIELD) score += 8
        if (titleField.name == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_TITLE_FIELD) score += 8

        return HomeNativeGlassEnterForumCapsuleResolvedCandidate(
            symbols = HomeNativeGlassEnterForumCapsuleSymbols(
                controllerClass = candidate.clazz.name,
                initMethod = initMethodName,
                refreshMethod = refreshMethodName,
                viewField = viewField.name,
                titleField = titleField.name,
            ),
            score = score,
            evidence = evidence.joinToString(";"),
        )
    }

    private fun selectHomeNativeGlassEnterForumCapsuleViewField(
        fields: List<Field>,
        preferredNames: List<String>,
    ): Field? {
        val viewFields = fields.filter { View::class.java.isAssignableFrom(it.type) }
        for (name in preferredNames.distinct()) {
            viewFields.singleOrNull { it.name == name }?.let { return it }
        }
        val primary = viewFields.filter { ViewGroup::class.java.isAssignableFrom(it.type) }
        return primary.singleOrNull()
    }

    private fun selectHomeNativeGlassEnterForumCapsuleTitleField(
        fields: List<Field>,
        preferredNames: List<String>,
    ): Field? {
        val stringFields = fields.filter { it.type == String::class.java }
        for (name in preferredNames.distinct()) {
            stringFields.singleOrNull { it.name == name }?.let { return it }
        }
        return stringFields.singleOrNull()
    }

    private fun isHomeNativeGlassNoArgVoidMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType == Void.TYPE
    }

    private fun resolveRIdField(
        cl: ClassLoader,
        className: String,
        fieldName: String,
        logger: ScanLogger?,
    ): Int? {
        val clazz = safeFindClass(className, cl) ?: return null
        val field = try {
            clazz.getDeclaredField(fieldName)
        } catch (_: NoSuchFieldException) {
            null
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: read field failed $className.$fieldName: ${t.message}")
            null
        } ?: return null

        if (field.type != Int::class.javaPrimitiveType && field.type != Integer.TYPE) {
            log(logger, "homeNativeGlassResources: field is not int $className.$fieldName")
            return null
        }
        return try {
            field.isAccessible = true
            field.getInt(null).takeIf { it != 0 }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: get field failed $className.$fieldName: ${t.message}")
            null
        }
    }

    private fun isResolvedIdResource(
        context: Context,
        id: Int,
        logger: ScanLogger?,
        source: String,
    ): Boolean {
        if (id <= 0) return false
        return try {
            val typeName = context.resources.getResourceTypeName(id)
            if (typeName == "id") {
                true
            } else {
                log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)} type=$typeName")
                false
            }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)}: ${t.message}")
            false
        }
    }

    private fun isResolvedColorResource(
        context: Context,
        id: Int,
        logger: ScanLogger?,
        source: String,
    ): Boolean {
        if (id <= 0) return false
        return try {
            val typeName = context.resources.getResourceTypeName(id)
            if (typeName == "color") {
                true
            } else {
                log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)} type=$typeName")
                false
            }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)}: ${t.message}")
            false
        }
    }

    private fun formatResourceId(value: Int?): String {
        return if (value != null && value != 0) {
            "0x${Integer.toHexString(value)}"
        } else {
            "-"
        }
    }

    private fun scanPbCommonLayoutPreloaderGetOrDefaultMethod(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): String? {
        val clazz = safeFindClass(StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS, cl) ?: run {
            log(logger, "pbCommonLayoutPreloader: class not found: ${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}")
            return null
        }
        val candidates = clazz.declaredMethods.filter(::isPbCommonLayoutPreloaderGetOrDefaultMethod)
        val method = candidates.singleOrNull() ?: run {
            log(
                logger,
                "pbCommonLayoutPreloader: method candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return null
        }
        log(logger, "pbCommonLayoutPreloader matched: ${clazz.name}.${method.name}")
        return method.name
    }

    private fun isPbCommonLayoutPreloaderGetOrDefaultMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (!View::class.java.isAssignableFrom(method.returnType)) return false
        val params = method.parameterTypes
        return params.size == 4 &&
            Context::class.java.isAssignableFrom(params[0]) &&
            params[1] == Boolean::class.javaPrimitiveType &&
            isIntType(params[2]) &&
            Class::class.java.isAssignableFrom(params[3])
    }

    private fun scanRecommendCardNestedDataSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): RecommendCardNestedDataScanSymbols {
        val viewClass = safeFindClass(RECOMMEND_CARD_VIEW_CLASS, cl) ?: run {
            log(logger, "recommendCard nestedData: class not found: $RECOMMEND_CARD_VIEW_CLASS")
            return RecommendCardNestedDataScanSymbols()
        }
        val candidates = ArrayList<RecommendCardNestedDataCandidate>(4)
        val bindMethods = viewClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !method.isSynthetic &&
                !method.isBridge &&
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive &&
                method.parameterTypes[0].name.startsWith("com.baidu.tieba.")
        }
        for (bindMethod in bindMethods) {
            val stateClass = bindMethod.parameterTypes[0]
            val stateFields = collectInstanceFields(stateClass)
            val stateMethods = collectInstanceMethods(stateClass)
            for (method in stateMethods) {
                if (method.isSynthetic || method.isBridge) continue
                if (method.parameterTypes.isNotEmpty()) continue
                if (method.returnType.isPrimitive || method.returnType == Void.TYPE || method.returnType == String::class.java) {
                    continue
                }
                val nestedDataClass = method.returnType
                if (stateFields.none { it.type == nestedDataClass }) continue
                val listFields = collectInstanceFields(nestedDataClass)
                    .filter { isListType(it.type) }
                if (listFields.size != 1) continue
                candidates.add(
                    RecommendCardNestedDataCandidate(
                        bindMethodName = bindMethod.name,
                        stateClassName = stateClass.name,
                        nestedDataMethodName = method.name,
                        nestedDataClassName = nestedDataClass.name,
                        listFieldName = listFields.single().name,
                    ),
                )
            }
        }

        val distinct = candidates.distinctBy { "${it.stateClassName}.${it.nestedDataMethodName}:${it.listFieldName}" }
        return when (distinct.size) {
            1 -> {
                val match = distinct.single()
                log(
                    logger,
                    "recommendCard nestedData matched: " +
                        "$RECOMMEND_CARD_VIEW_CLASS.${match.bindMethodName} -> " +
                        "${match.stateClassName}.${match.nestedDataMethodName}()[${match.nestedDataClassName}.${match.listFieldName}]",
                )
                RecommendCardNestedDataScanSymbols(
                    nestedDataMethod = match.nestedDataMethodName,
                    nestedDataListField = match.listFieldName,
                )
            }
            0 -> {
                log(logger, "recommendCard nestedData not found")
                RecommendCardNestedDataScanSymbols()
            }
            else -> {
                log(
                    logger,
                    "recommendCard nestedData not unique: " +
                        distinct.joinToString(";") {
                            "${it.stateClassName}.${it.nestedDataMethodName}[${it.nestedDataClassName}.${it.listFieldName}]"
                        },
                )
                RecommendCardNestedDataScanSymbols()
            }
        }
    }

    private fun scanAiComponentSymbols(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val imageViewerJumpButtonScan = scanImageViewerJumpButtonSymbols(context, cl, candidateClassNames, logger)
        val emojiCreationScan = scanPbAiEmojiCreationSymbols(cl, logger)
        val optionalScan = imageViewerJumpButtonScan.copy(
            pbAiEmojiCreationViewBindMethod = emojiCreationScan.pbAiEmojiCreationViewBindMethod,
            pbPageBrowserAiEmojiCreationBindMethod = emojiCreationScan.pbPageBrowserAiEmojiCreationBindMethod,
        )
        val controllerClass = safeFindClass(AI_SPRITE_MEME_PAN_CONTROLLER_CLASS, cl) ?: run {
            log(logger, "aiComponent: class not found: $AI_SPRITE_MEME_PAN_CONTROLLER_CLASS")
            return optionalScan
        }
        val inputContainerClass = safeFindClass(AI_PB_NEW_INPUT_CONTAINER_CLASS, cl) ?: run {
            log(logger, "aiComponent: class not found: $AI_PB_NEW_INPUT_CONTAINER_CLASS")
            return optionalScan.copy(
                spriteMemePanControllerClass = controllerClass.name,
                spriteMemeEnableMethod = resolveAiSpriteMemeEnableMethod(controllerClass, cl, logger)?.name,
            )
        }
        val spriteMemePanClass = safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)
        val enableMethod = resolveAiSpriteMemeEnableMethod(controllerClass, cl, logger)
        val initSpriteMemeMethod = scanSpriteMemeInitMethodFromDex(context, inputContainerClass, logger)
            ?: resolveNamedPbNewInputContextInitMethod(
                inputContainerClass,
                AI_PB_NEW_INPUT_INIT_SPRITE_MEME_METHOD,
                logger,
            )
        val initAiWriteMethod = scanAiWriteInitMethodFromDex(context, inputContainerClass, logger)
            ?: resolveNamedPbNewInputContextInitMethod(
                inputContainerClass,
                AI_PB_NEW_INPUT_INIT_AI_WRITE_METHOD,
                logger,
            )

        if (!isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) {
            log(logger, "aiComponent: PbNewInputContainer structure mismatch")
            return optionalScan.copy(
                spriteMemePanControllerClass = controllerClass.name,
                spriteMemeEnableMethod = enableMethod?.name,
                pbNewInputContainerClass = inputContainerClass.name,
                pbInitSpriteMemeMethod = initSpriteMemeMethod?.name,
                pbInitAiWriteMethod = initAiWriteMethod?.name,
            )
        }

        log(
            logger,
            "aiComponent matched: " +
                "${controllerClass.name}.${enableMethod?.name} / " +
                "${inputContainerClass.name}.{${initSpriteMemeMethod?.name},${initAiWriteMethod?.name}}",
        )
        return optionalScan.copy(
            spriteMemePanControllerClass = controllerClass.name,
            spriteMemeEnableMethod = enableMethod?.name,
            pbNewInputContainerClass = inputContainerClass.name,
            pbInitSpriteMemeMethod = initSpriteMemeMethod?.name,
            pbInitAiWriteMethod = initAiWriteMethod?.name,
        )
    }

    private fun scanPbAiEmojiCreationSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val viewBindMethod = scanPbAiEmojiCreationViewBindMethod(cl, logger)
        val pageBrowserBindMethod = scanPbPageBrowserAiEmojiCreationBindMethod(cl, logger)
        log(
            logger,
            "aiComponent.pbAiEmojiCreation matched: " +
                "bind=${viewBindMethod?.name ?: "-"}, pageBrowser=${pageBrowserBindMethod?.name ?: "-"}",
        )
        return AiComponentScanSymbols(
            pbAiEmojiCreationViewBindMethod = viewBindMethod?.name,
            pbPageBrowserAiEmojiCreationBindMethod = pageBrowserBindMethod?.name,
        )
    }

    private fun scanPbAiEmojiCreationViewBindMethod(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Method? {
        return try {
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: view class not found")
                return null
            }
            viewClass.declaredMethods.singleOrNull { method ->
                isAiPbEmojiCreationViewBindMethod(method, cl)
            } ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: view bind method not found")
                null
            }
        } catch (t: Throwable) {
            log(logger, "aiComponent.pbAiEmojiCreation: view bind error: ${formatScanException(t)}")
            null
        }
    }

    private fun scanPbPageBrowserAiEmojiCreationBindMethod(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Method? {
        return try {
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS, cl) ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: page browser view class not found")
                return null
            }
            viewClass.declaredMethods.singleOrNull { method ->
                isPbPageBrowserAiEmojiCreationBindMethod(method)
            } ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: page browser bind method not found")
                null
            }
        } catch (t: Throwable) {
            log(logger, "aiComponent.pbAiEmojiCreation: page browser bind error: ${formatScanException(t)}")
            null
        }
    }

    private fun scanImageViewerJumpButtonSymbols(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val layoutClass = safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl) ?: run {
            log(logger, "aiComponent.imageViewerJumpButton: class not found: $AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS")
            return AiComponentScanSymbols()
        }
        val ownerClass = resolveImageViewerJumpButtonOwnerClass(candidateClassNames, cl, layoutClass, logger)
            ?: return AiComponentScanSymbols()
        val initMethod = scanImageViewerJumpButtonInitMethodFromDex(context, ownerClass, logger)
        if (initMethod == null) {
            log(logger, "aiComponent.imageViewerJumpButton: init method missing: ${ownerClass.name}")
            return AiComponentScanSymbols(imageViewerJumpButtonOwnerClass = ownerClass.name)
        }
        log(logger, "aiComponent.imageViewerJumpButton matched: ${ownerClass.name}.${initMethod.name}")
        return AiComponentScanSymbols(
            imageViewerJumpButtonOwnerClass = ownerClass.name,
            imageViewerJumpButtonInitMethod = initMethod.name,
        )
    }

    private fun resolveImageViewerJumpButtonOwnerClass(
        candidateClassNames: List<String>,
        cl: ClassLoader,
        layoutClass: Class<*>,
        logger: ScanLogger?,
    ): Class<*>? {
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        val matches = candidateClassNames
            .distinct()
            .mapNotNull { className ->
                try {
                    val clazz = safeFindClass(className, cl) ?: return@mapNotNull null
                    val score = scoreImageViewerJumpButtonOwnerClass(clazz, layoutClass)
                    if (score > 0) clazz to score else null
                } catch (t: Throwable) {
                    skippedByReflection++
                    if (firstReflectionError == null) {
                        firstReflectionError = sanitizeScanStatusText(formatScanException(t))
                    }
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<Class<*>, Int>> { it.second }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.name },
            )

        if (skippedByReflection > 0) {
            val line = "aiComponent.imageViewerJumpButton: skipped classes by reflection=$skippedByReflection" +
                (firstReflectionError?.let { ", firstException=$it" } ?: "")
            try {
                XposedCompat.logD("$TAG: $line")
            } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
            try {
                logger?.log(line)
            } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
        }
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.imageViewerJumpButton: owner class not found")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.second - second.second < 20) {
            log(
                logger,
                "aiComponent.imageViewerJumpButton: owner ambiguous: " +
                    "best=${best.first.name}:${best.second}, second=${second.first.name}:${second.second}",
            )
            return null
        }
        return best.first
    }

    private fun scoreImageViewerJumpButtonOwnerClass(clazz: Class<*>, layoutClass: Class<*>): Int {
        if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) return 0
        if (!clazz.name.startsWith("com.baidu.tieba.")) return 0
        val fields = collectInstanceFields(clazz)
        if (fields.none { layoutClass.isAssignableFrom(it.type) }) return 0

        val constructors = runCatching { clazz.declaredConstructors }.getOrNull() ?: return 0
        val methods = runCatching { clazz.declaredMethods }.getOrNull() ?: return 0
        val hasContextRelativeCtor = constructors.any { ctor ->
            val params = ctor.parameterTypes
            params.size == 2 &&
                Context::class.java.isAssignableFrom(params[0]) &&
                RelativeLayout::class.java.isAssignableFrom(params[1])
        }
        if (!hasContextRelativeCtor) return 0

        var score = 150
        if (clazz.interfaces.any { it.name == "com.baidu.tieba.np6" }) score += 18
        if (fields.any { it.type == Context::class.java }) score += 10
        if (fields.any { it.type == LinearLayout::class.java }) score += 10
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_BOTTOM_LAYOUT_CLASS }) score += 14
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_ABS_FLOOR_IMAGE_TEXT_VIEW_CLASS }) score += 10
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_FACE_GROUP_DOWNLOAD_LAYOUT_CLASS }) score += 10
        if (methods.any { method -> method.parameterTypes.isEmpty() && layoutClass.isAssignableFrom(method.returnType) }) {
            score += 12
        }
        score -= fields.size / 3
        score -= methods.size / 6
        score -= clazz.simpleName.length
        return if (score >= 145) score else 0
    }

    private fun scanImageViewerJumpButtonInitMethodFromDex(
        context: Context,
        ownerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.imageViewerJumpButtonDex: apk source path unavailable")
            return null
        }
        val matches = DexShareIconScanner.scanImageViewerJumpButtonInit(
            sourcePaths = sourcePaths,
            ownerClassName = ownerClass.name,
            logger = logger,
        )
            .filter { isImageViewerJumpButtonInitMethodName(ownerClass, it.ownerMethodName) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        return pickImageViewerJumpButtonInitDexMatch(ownerClass, matches, logger)
    }

    private fun pickImageViewerJumpButtonInitDexMatch(
        ownerClass: Class<*>,
        matches: List<DexAiComponentInitMatch>,
        logger: ScanLogger?,
    ): Method? {
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.imageViewerJumpButtonDex: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 20) {
            log(
                logger,
                "aiComponent.imageViewerJumpButtonDex ambiguous: " +
                    "best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }
        val method = ownerClass.declaredMethods.singleOrNull { method ->
            method.name == best.ownerMethodName && isImageViewerJumpButtonInitMethod(method)
        } ?: run {
            log(logger, "aiComponent.imageViewerJumpButtonDex: reflection mismatch: ${ownerClass.name}.${best.ownerMethodName}")
            return null
        }
        log(
            logger,
            "aiComponent.imageViewerJumpButtonDex matched: ${ownerClass.name}.${method.name} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return method
    }

    private fun isImageViewerJumpButtonInitMethodName(ownerClass: Class<*>, methodName: String): Boolean {
        return ownerClass.declaredMethods.any { method ->
            method.name == methodName && isImageViewerJumpButtonInitMethod(method)
        }
    }

    private fun isImageViewerJumpButtonInitMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.isEmpty()
    }

    private fun isAiPbEmojiCreationViewBindMethod(method: Method, cl: ClassLoader): Boolean {
        if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) return false
        val spriteMemeInfoClass = safeFindClass(AI_SPRITE_MEME_INFO_CLASS, cl) ?: return false
        val params = method.parameterTypes
        return params.size == 3 &&
            spriteMemeInfoClass.isAssignableFrom(params[0]) &&
            isIntType(params[1]) &&
            Map::class.java.isAssignableFrom(params[2])
    }

    private fun isPbPageBrowserAiEmojiCreationBindMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1
    }

    private fun scanAiWriteInitMethodFromDex(
        context: Context,
        inputContainerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.aiWriteDex: apk source path unavailable")
            return null
        }
        val dexMatches = DexShareIconScanner.scanAiWriteInit(
            sourcePaths = sourcePaths,
            ownerClassName = inputContainerClass.name,
            logger = logger,
        )
            .filter { isPbNewInputContextInitMethodName(inputContainerClass, it.ownerMethodName) }
        val matches = dexMatches
            .filter { it.strong }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        if (matches.isEmpty()) {
            logAiComponentInitDexDiagnostics(logger, "aiWriteDex", dexMatches)
        }
        return pickAiComponentInitDexMatch(
            ownerClass = inputContainerClass,
            matches = matches,
            logger = logger,
            tag = "aiWriteDex",
        )
    }

    private fun scanSpriteMemeInitMethodFromDex(
        context: Context,
        inputContainerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.spriteInitDex: apk source path unavailable")
            return null
        }
        val matches = DexShareIconScanner.scanSpriteMemeInit(
            sourcePaths = sourcePaths,
            ownerClassName = inputContainerClass.name,
            logger = logger,
        )
            .filter { isPbNewInputContextInitMethodName(inputContainerClass, it.ownerMethodName) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        return pickAiComponentInitDexMatch(
            ownerClass = inputContainerClass,
            matches = matches,
            logger = logger,
            tag = "spriteInitDex",
        )
    }

    private fun logAiComponentInitDexDiagnostics(
        logger: ScanLogger?,
        tag: String,
        matches: List<DexAiComponentInitMatch>,
    ) {
        if (matches.isEmpty()) return
        val summary = matches
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
            .take(3)
            .joinToString(";") { match ->
                "${match.ownerMethodName}:${match.score}[${match.evidence}]"
            }
        log(logger, "aiComponent.$tag partial candidates: $summary")
    }

    private fun pickAiComponentInitDexMatch(
        ownerClass: Class<*>,
        matches: List<DexAiComponentInitMatch>,
        logger: ScanLogger?,
        tag: String,
    ): Method? {
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.$tag: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 20) {
            log(
                logger,
                "aiComponent.$tag ambiguous: best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }
        val method = ownerClass.declaredMethods.singleOrNull { method ->
            method.name == best.ownerMethodName && isPbNewInputContextInitMethod(method)
        } ?: run {
            log(logger, "aiComponent.$tag: reflection mismatch: ${ownerClass.name}.${best.ownerMethodName}")
            return null
        }
        log(
            logger,
            "aiComponent.$tag matched: ${ownerClass.name}.${method.name} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return method
    }

    private fun resolveAiSpriteMemeEnableMethod(
        controllerClass: Class<*>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Method? {
        val inputShowTypeClass = safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
        val candidates = controllerClass.declaredMethods.filter { method ->
            isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
        }
        if (candidates.size == 1) return candidates.first()
        val preferred = candidates.singleOrNull { it.name == AI_SPRITE_MEME_ENABLE_METHOD }
        if (preferred != null) return preferred
        log(
            logger,
            "aiComponent: SpriteMeme enable method " +
                "candidates=${candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" }}",
        )
        return null
    }

    private fun resolveNamedPbNewInputContextInitMethod(
        inputContainerClass: Class<*>,
        methodName: String,
        logger: ScanLogger?,
    ): Method? {
        val candidates = inputContainerClass.declaredMethods.filter { method ->
            method.name == methodName && isPbNewInputContextInitMethod(method)
        }
        val method = candidates.singleOrNull()
        if (method != null) return method
        log(
            logger,
            "aiComponent: PbNewInputContainer.$methodName candidates=" +
                candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
        )
        return null
    }

    private fun isAiSpriteMemeEnableMethod(method: Method, inputShowTypeClass: Class<*>?): Boolean {
        val params = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 2 &&
            Context::class.java.isAssignableFrom(params[0]) &&
            (inputShowTypeClass?.let { params[1] == it } ?: (params[1].name == AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS))
    }

    private fun isPbNewInputContextInitMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            params.size == 1 &&
            Context::class.java.isAssignableFrom(params[0])
    }

    private fun isPbNewInputContextInitMethodName(inputContainerClass: Class<*>, methodName: String): Boolean {
        return inputContainerClass.declaredMethods.any { method ->
            method.name == methodName && isPbNewInputContextInitMethod(method)
        }
    }

    private fun isAiPbNewInputContainerClassValid(
        inputContainerClass: Class<*>,
        spriteMemePanClass: Class<*>?,
    ): Boolean {
        if (!LinearLayout::class.java.isAssignableFrom(inputContainerClass)) return false
        val fields = collectInstanceFields(inputContainerClass)
        val hasAiWriteFrame = fields.any { field -> FrameLayout::class.java.isAssignableFrom(field.type) }
        if (!hasAiWriteFrame) return false
        if (spriteMemePanClass == null) return true
        return fields.any { field -> spriteMemePanClass.isAssignableFrom(field.type) }
    }

    private fun scanTypeAdapterDataFilterSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): TypeAdapterDataFilterScanSymbols {
        val typeAdapterClass = safeFindClass(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl) ?: run {
            log(logger, "typeAdapterDataFilter: class not found: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}")
            return TypeAdapterDataFilterScanSymbols()
        }
        val bdUniqueIdClass = safeFindClass(BD_UNIQUE_ID_CLASS, cl) ?: run {
            log(logger, "typeAdapterDataFilter: class not found: $BD_UNIQUE_ID_CLASS")
            return TypeAdapterDataFilterScanSymbols()
        }

        val setDataMethod = resolveTypeAdapterSetDataMethod(typeAdapterClass, logger)
        val dataItemClass = resolveTypeAdapterDataItemClass(typeAdapterClass, setDataMethod, logger)
        val getTypeMethod = dataItemClass?.let {
            resolveTypeAdapterDataGetTypeMethod(it, bdUniqueIdClass, logger)
        }

        return TypeAdapterDataFilterScanSymbols(
            setDataMethod = setDataMethod?.name,
            dataItemClass = dataItemClass?.name,
            dataGetTypeMethod = getTypeMethod?.name,
        )
    }

    private fun resolveTypeAdapterSetDataMethod(
        typeAdapterClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = typeAdapterClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isListType(method.parameterTypes[0])
        }
        val genericCandidates = candidates.filter { method ->
            extractListGenericClass(method.genericParameterTypes.firstOrNull()) != null
        }
        val resolved = genericCandidates.singleOrNull() ?: candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "typeAdapterDataFilter: setData method mismatch candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun resolveTypeAdapterDataItemClass(
        typeAdapterClass: Class<*>,
        setDataMethod: Method?,
        logger: ScanLogger?,
    ): Class<*>? {
        setDataMethod
            ?.genericParameterTypes
            ?.firstOrNull()
            ?.let(::extractListGenericClass)
            ?.let { return it }

        val listFieldItemClasses = collectInstanceFields(typeAdapterClass)
            .asSequence()
            .filter { field -> isListType(field.type) }
            .mapNotNull { field -> extractListGenericClass(field.genericType) }
            .distinctBy { it.name }
            .toList()
        if (listFieldItemClasses.size == 1) return listFieldItemClasses.first()

        val interfaceItemClasses = typeAdapterClass.genericInterfaces
            .asSequence()
            .mapNotNull(::extractSingleGenericClass)
            .distinctBy { it.name }
            .toList()
        if (interfaceItemClasses.size == 1) return interfaceItemClasses.first()

        log(
            logger,
            "typeAdapterDataFilter: data item class mismatch " +
                "fields=${listFieldItemClasses.joinToString(",") { it.name }.ifBlank { "-" }} " +
                "interfaces=${interfaceItemClasses.joinToString(",") { it.name }.ifBlank { "-" }}",
        )
        return null
    }

    private fun resolveTypeAdapterDataGetTypeMethod(
        dataItemClass: Class<*>,
        bdUniqueIdClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = dataItemClass.methods.filter { method ->
            method.parameterTypes.isEmpty() &&
                bdUniqueIdClass.isAssignableFrom(method.returnType)
        }
        val resolved = candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "typeAdapterDataFilter: getType method mismatch class=${dataItemClass.name} candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun extractListGenericClass(type: Type?): Class<*>? {
        val parameterized = type as? ParameterizedType ?: return null
        val rawClass = parameterized.rawType as? Class<*> ?: return null
        if (!isListType(rawClass)) return null
        val arg = parameterized.actualTypeArguments.singleOrNull() ?: return null
        return extractGenericClass(arg)
    }

    private fun extractSingleGenericClass(type: Type?): Class<*>? {
        val parameterized = type as? ParameterizedType ?: return null
        val args = parameterized.actualTypeArguments
        if (args.size != 1) return null
        return extractGenericClass(args[0])
    }

    private fun extractGenericClass(type: Type?): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            is WildcardType -> type.upperBounds.firstOrNull()?.let(::extractGenericClass)
            else -> null
        }
    }

    private fun scanPbEarlyAdInsertSymbols(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbEarlyAdInsertScanSymbols {
        val targetClassNames = (listOf(PB_AD_INSERT_CLASS) +
            candidates.filter(::isPbEarlyAdInsertCandidateClassName)).distinct()
        val matches = ArrayList<ScanMatch>()
        val diagnostics = ArrayList<ScanMatch>()
        var fixedClassFound = false
        var skippedByReflection = 0

        for (className in targetClassNames) {
            try {
                val targetClass = safeFindClass(className, cl) ?: continue
                fixedClassFound = fixedClassFound || className == PB_AD_INSERT_CLASS
                if (targetClass.isInterface || Modifier.isAbstract(targetClass.modifiers)) continue
                buildPbEarlyAdDiagnosticMatch(targetClass)?.let(diagnostics::add)

                val specs = scanPbEarlyAdInsertMethodSpecs(
                    targetClass,
                    cl,
                    logger = null,
                    logExactMismatch = false,
                )
                if (specs.size < PB_EARLY_AD_INSERT_MIN_METHOD_COUNT) {
                    if (className == PB_AD_INSERT_CLASS && specs.isNotEmpty()) {
                        log(
                            logger,
                            "pbEarlyAdInsert: fixed class incomplete specs=${specs.size}, class=$className",
                        )
                    }
                    continue
                }

                val score = scorePbEarlyAdInsertCandidate(targetClass, specs)
                matches.add(ScanMatch(className, specs.joinToString(";"), "", score))
            } catch (t: Throwable) {
                skippedByReflection++
                log(
                    logger,
                    "pbEarlyAdInsert: skip class=$className reflection failed: " +
                        "${t.javaClass.simpleName}:${t.message}",
                )
            }
        }

        if (skippedByReflection > 0) {
            log(logger, "pbEarlyAdInsert scan skipped classes by reflection=$skippedByReflection")
        }

        if (!fixedClassFound) {
            log(logger, "pbEarlyAdInsert: fixed class not found: $PB_AD_INSERT_CLASS")
        }

        val match = chooseUniqueScanMatch(
            tag = "pbEarlyAdInsert",
            ruleName = "PbEarlyAdInsertSemanticRule",
            matches = matches,
            logger = logger,
            minScore = 140,
            minScoreGap = 12,
        )
        if (match == null) {
            logPbEarlyAdInsertDiagnostics(diagnostics, logger)
            log(
                logger,
                "pbEarlyAdInsert: no complete unique semantic match, scanned=${targetClassNames.size}",
            )
            return PbEarlyAdInsertScanSymbols(null, emptyList())
        }

        val specs = match.methodName
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return PbEarlyAdInsertScanSymbols(match.className, specs)
    }

    private fun isPbEarlyAdInsertCandidateClassName(className: String): Boolean {
        val shortName = className.substringAfterLast('.')
        if (isLikelyObfuscatedShortName(shortName) &&
            (className.startsWith("com.baidu.tieba.") || className.startsWith("com.baidu.tbadk."))) {
            return true
        }
        if (!className.startsWith("com.baidu.tieba.pb.")) return false
        return shortName.endsWith("Kt") ||
            shortName.contains("Insert", ignoreCase = true) ||
            shortName.contains("Ad", ignoreCase = true) ||
            shortName.contains("Adapter", ignoreCase = true) ||
            isLikelyObfuscatedShortName(shortName)
    }

    private fun scanPbAdBidSymbols(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbAdBidScanSymbols {
        val commonBaseClass = safeFindClass(PB_COMMON_REQUEST_MODEL_CLASS, cl)
        val pageBrowserBaseClass = safeFindClass(PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, cl)
        val adBidResClass = safeFindClass(PB_AD_BID_RES_IDL_CLASS, cl)
        val httpMessageClass = safeFindClass(HTTP_MESSAGE_CLASS, cl)
        val requestClass = safeFindClass(TIEBA_REQUEST_INTERFACE_CLASS, cl)
        val continuationClass = safeFindClass(KOTLIN_CONTINUATION_CLASS, cl)

        if (commonBaseClass == null) {
            log(logger, "pbAdBid: common base class not found: $PB_COMMON_REQUEST_MODEL_CLASS")
        }
        if (pageBrowserBaseClass == null) {
            log(logger, "pbAdBid: pagebrowser base class not found: $PB_PAGE_BROWSER_REQUEST_MODEL_CLASS")
        }
        if (adBidResClass == null) {
            log(logger, "pbAdBid: AdBidResIdl class not found: $PB_AD_BID_RES_IDL_CLASS")
        }
        if (requestClass == null) {
            log(logger, "pbAdBid: request interface not found: $TIEBA_REQUEST_INTERFACE_CLASS")
        }
        if (httpMessageClass == null) {
            log(logger, "pbAdBid: HttpMessage class not found: $HTTP_MESSAGE_CLASS")
        }

        val commonStartMethods = commonBaseClass
            ?.let { resolvePbAdBidCommonStartMethods(it, logger) }
            .orEmpty()
        val commonNotifyMethod = commonBaseClass
            ?.let { resolvePbAdBidCommonNotifyMethod(it, logger) }
        val pageBrowserBaseRequestDataMethod = if (continuationClass != null) {
            pageBrowserBaseClass?.let { resolvePbAdBidPageBrowserRequestDataMethod(it, continuationClass, logger) }
        } else {
            log(logger, "pbAdBid: continuation class not found: $KOTLIN_CONTINUATION_CLASS")
            null
        }

        val targetClassNames = candidates
            .filter(::isPbAdBidCandidateClassName)
            .distinct()
        var skippedByReflection = 0

        val commonMatches = ArrayList<ScanMatch>()
        val pageBrowserMatches = ArrayList<ScanMatch>()
        if (adBidResClass != null && requestClass != null) {
            for (className in targetClassNames) {
                val cls = try {
                    safeFindClass(className, cl)
                } catch (t: Throwable) {
                    skippedByReflection++
                    log(
                        logger,
                        "pbAdBid: skip class=$className reflection failed: " +
                            "${t.javaClass.simpleName}:${t.message}",
                    )
                    null
                } ?: continue

                if (commonBaseClass != null) {
                    try {
                        scorePbAdBidCommonModelClass(
                            cls,
                            commonBaseClass,
                            adBidResClass,
                            httpMessageClass,
                            requestClass,
                        )?.let(commonMatches::add)
                    } catch (t: Throwable) {
                        skippedByReflection++
                        log(
                            logger,
                            "pbAdBid.common: skip class=$className scoring failed: " +
                                "${t.javaClass.simpleName}:${t.message}",
                        )
                    }
                }
                if (continuationClass != null) {
                    try {
                        scorePbAdBidPageBrowserModelClass(
                            cls,
                            pageBrowserBaseClass,
                            adBidResClass,
                            requestClass,
                            continuationClass,
                            pageBrowserBaseRequestDataMethod,
                        )?.let(pageBrowserMatches::add)
                    } catch (t: Throwable) {
                        skippedByReflection++
                        log(
                            logger,
                            "pbAdBid.pageBrowser: skip class=$className scoring failed: " +
                                "${t.javaClass.simpleName}:${t.message}",
                        )
                    }
                }
            }
        }
        if (skippedByReflection > 0) {
            log(logger, "pbAdBid scan skipped classes by reflection=$skippedByReflection")
        }

        val commonMatch = chooseUniqueScanMatch(
            tag = "pbAdBid.common",
            ruleName = "PbAdBidCommonModelRule",
            matches = commonMatches,
            logger = logger,
            minScore = 150,
            minScoreGap = 20,
        )
        val pageBrowserMatch = chooseUniqueScanMatch(
            tag = "pbAdBid.pageBrowser",
            ruleName = "PbAdBidPageBrowserModelRule",
            matches = pageBrowserMatches,
            logger = logger,
            minScore = 140,
            minScoreGap = 20,
        )

        if (commonMatch == null) {
            log(logger, "pbAdBid.common: no complete unique match, scanned=${targetClassNames.size}")
        }
        if (pageBrowserMatch == null) {
            log(logger, "pbAdBid.pageBrowser: no complete unique match, scanned=${targetClassNames.size}")
        }

        val dexScan = if (
            commonMatch == null ||
            pageBrowserMatch == null ||
            pageBrowserBaseRequestDataMethod == null
        ) {
            scanPbAdBidSymbolsFromDex(context, logger)
        } else {
            DexPbAdBidScanSymbols()
        }

        return PbAdBidScanSymbols(
            commonRequestModelClass = commonMatch?.className ?: dexScan.commonModelClassName,
            commonRequestStartMethods = commonStartMethods,
            commonRequestNotifyMethod = commonNotifyMethod?.name,
            pageBrowserRequestModelClass = pageBrowserMatch?.className ?: dexScan.pageBrowserModelClassName,
            pageBrowserRequestDataMethod = pageBrowserBaseRequestDataMethod?.name
                ?: pageBrowserMatch?.fieldName?.takeIf { it.isNotBlank() }
                ?: dexScan.pageBrowserRequestDataMethodName,
        )
    }

    private fun scanPbAdBidSymbolsFromDex(
        context: Context,
        logger: ScanLogger?,
    ): DexPbAdBidScanSymbols {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "pbAdBidDex: apk source path unavailable")
            return DexPbAdBidScanSymbols()
        }

        val rawDexScan = DexShareIconScanner.scanPbAdBid(sourcePaths, logger)
        val matches = rawDexScan.modelMatches
        val commonMatch = chooseUniquePbAdBidDexMatch(
            tag = "pbAdBid.commonDex",
            matches = matches.filter { it.kind == PB_AD_BID_DEX_KIND_COMMON },
            logger = logger,
        )
        val pageBrowserMatch = chooseUniquePbAdBidDexMatch(
            tag = "pbAdBid.pageBrowserDex",
            matches = matches.filter { it.kind == PB_AD_BID_DEX_KIND_PAGE_BROWSER },
            logger = logger,
        )
        val requestDataMethod = rawDexScan.pageBrowserRequestDataMethodName
        if (requestDataMethod == null && pageBrowserMatch != null) {
            log(logger, "pbAdBid.pageBrowserDex: requestData method not found in dex base model")
        }
        return DexPbAdBidScanSymbols(
            commonModelClassName = commonMatch?.className,
            pageBrowserModelClassName = pageBrowserMatch?.className,
            pageBrowserRequestDataMethodName = requestDataMethod,
        )
    }

    private fun chooseUniquePbAdBidDexMatch(
        tag: String,
        matches: List<DexPbAdBidModelMatch>,
        logger: ScanLogger?,
    ): DexPbAdBidModelMatch? {
        if (matches.isEmpty()) {
            log(logger, "$tag: no semantic match")
            return null
        }
        val sorted = matches.sortedWith(
            compareByDescending<DexPbAdBidModelMatch> { it.score }
                .thenBy { it.className }
                .thenBy { it.requestImplMethodName },
        )
        val best = sorted.first()
        val sameScore = sorted.filter { it.score == best.score }
        if (sameScore.size > 1) {
            log(
                logger,
                "$tag ambiguous top score=${best.score}: " +
                    sameScore.take(5).joinToString("; ") {
                        "${it.className}.${it.requestImplMethodName}[${it.evidence}]"
                    },
            )
            return null
        }
        val second = sorted.getOrNull(1)
        if (second != null && best.score - second.score < 16) {
            log(
                logger,
                "$tag ambiguous close score: best=${best.className}.${best.requestImplMethodName}:${best.score}, " +
                    "second=${second.className}.${second.requestImplMethodName}:${second.score}",
            )
            return null
        }
        log(
            logger,
            "$tag matched: ${best.className}.${best.requestImplMethodName} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return best
    }

    private fun isPbAdBidCandidateClassName(className: String): Boolean {
        if (!className.startsWith("com.baidu.tieba.")) return false
        val shortName = className.substringAfterLast('.')
        return isLikelyObfuscatedShortName(shortName) ||
            className.startsWith("com.baidu.tieba.pb.")
    }

    private fun resolvePbAdBidCommonStartMethods(
        commonBaseClass: Class<*>,
        logger: ScanLogger?,
    ): List<String> {
        val methods = commonBaseClass.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    !Modifier.isAbstract(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
            .map { it.name }
            .distinct()
            .sorted()
        if (methods.isEmpty()) {
            log(logger, "pbAdBid.common: no no-arg request start methods in ${commonBaseClass.name}")
        }
        return methods
    }

    private fun resolvePbAdBidCommonNotifyMethod(
        commonBaseClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val methods = commonBaseClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isIntType(method.parameterTypes[0])
        }
        val resolved = methods.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "pbAdBid.common: notify method mismatch candidates=" +
                    methods.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun resolvePbAdBidPageBrowserRequestDataMethod(
        pageBrowserBaseClass: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val methods = pageBrowserBaseClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                method.returnType == Any::class.java &&
                method.parameterTypes.size == 1 &&
                continuationClass.isAssignableFrom(method.parameterTypes[0])
        }
        val resolved = methods.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "pbAdBid.pageBrowser: requestData method mismatch candidates=" +
                    methods.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun scorePbAdBidCommonModelClass(
        cls: Class<*>,
        commonBaseClass: Class<*>,
        adBidResClass: Class<*>,
        httpMessageClass: Class<*>?,
        requestClass: Class<*>,
    ): ScanMatch? {
        if (cls == commonBaseClass || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!commonBaseClass.isAssignableFrom(cls)) return null
        val hasGenericAdBidType = hasGenericSuperclassArgument(cls, commonBaseClass, adBidResClass)
        val hasStructuralAdBidType = !hasGenericAdBidType &&
            referencesClassInMembersOrNestedClasses(cls, adBidResClass)
        if (!hasGenericAdBidType && !hasStructuralAdBidType) return null

        val requestMethods = cls.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                requestClass.isAssignableFrom(method.returnType)
        }
        val requestMethod = requestMethods.singleOrNull() ?: return null

        val setterMethods = if (httpMessageClass != null) {
            cls.declaredMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    httpMessageClass.isAssignableFrom(method.parameterTypes[0])
            }
        } else {
            emptyList()
        }
        val setterMethod = setterMethods.singleOrNull()

        var score = 150
        if (hasGenericAdBidType) score += 34
        if (hasStructuralAdBidType) score += 24
        if (cls.superclass == commonBaseClass) score += 18
        if (cls.declaredConstructors.any { it.parameterTypes.isEmpty() }) score += 12
        if (httpMessageClass != null && cls.declaredFields.any { field ->
                !Modifier.isStatic(field.modifiers) && httpMessageClass.isAssignableFrom(field.type)
            }
        ) {
            score += 20
        }
        if (setterMethod != null) score += 16
        if (cls.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].name == TIEBA_REQUEST_CALLBACK_CLASS
            }
        ) {
            score += 12
        }
        score -= cls.declaredMethods.size / 8
        score -= cls.declaredFields.size / 6
        return ScanMatch(cls.name, requestMethod.name, setterMethod?.name.orEmpty(), score)
    }

    private fun scorePbAdBidPageBrowserModelClass(
        cls: Class<*>,
        pageBrowserBaseClass: Class<*>?,
        adBidResClass: Class<*>,
        requestClass: Class<*>,
        continuationClass: Class<*>,
        pageBrowserBaseRequestDataMethod: Method?,
    ): ScanMatch? {
        if (cls == pageBrowserBaseClass || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val resolvedBaseClass = pageBrowserBaseClass?.takeIf { it.isAssignableFrom(cls) }
            ?: resolvePbAdBidPageBrowserBaseFromCandidate(cls, continuationClass)
            ?: return null
        val hasGenericAdBidType = hasGenericSuperclassArgument(cls, resolvedBaseClass, adBidResClass)
        val hasStructuralAdBidType = !hasGenericAdBidType &&
            referencesClassInMembersOrNestedClasses(cls, adBidResClass)
        if (!hasGenericAdBidType && !hasStructuralAdBidType) return null

        val requestMethods = cls.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                requestClass.isAssignableFrom(method.returnType)
        }
        val requestMethod = requestMethods.singleOrNull() ?: return null
        val requestDataMethod = pageBrowserBaseRequestDataMethod
            ?.takeIf { it.declaringClass.isAssignableFrom(cls) }
            ?: findPbAdBidPageBrowserRequestDataMethodInHierarchy(cls, continuationClass)
            ?: return null

        var score = 140
        if (hasGenericAdBidType) score += 34
        if (hasStructuralAdBidType) score += 24
        if (cls.superclass == resolvedBaseClass) score += 18
        if (cls.declaredConstructors.any { ctor ->
                ctor.parameterTypes.size == 1 && isIntType(ctor.parameterTypes[0])
            }
        ) {
            score += 24
        }
        if (cls.declaredFields.any { field -> !Modifier.isStatic(field.modifiers) && isIntType(field.type) }) {
            score += 12
        }
        score -= cls.declaredMethods.size / 8
        score -= cls.declaredFields.size / 6
        return ScanMatch(cls.name, requestMethod.name, requestDataMethod.name, score)
    }

    private fun resolvePbAdBidPageBrowserBaseFromCandidate(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Class<*>? {
        var current: Class<*>? = cls.superclass
        while (current != null && current != Any::class.java) {
            if (findPbAdBidPageBrowserRequestDataMethod(current, continuationClass) != null) {
                return current
            }
            current = current.superclass
        }
        return null
    }

    private fun findPbAdBidPageBrowserRequestDataMethodInHierarchy(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Method? {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            findPbAdBidPageBrowserRequestDataMethod(current, continuationClass)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun findPbAdBidPageBrowserRequestDataMethod(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Method? {
        val methods = cls.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                method.returnType == Any::class.java &&
                method.parameterTypes.size == 1 &&
                continuationClass.isAssignableFrom(method.parameterTypes[0])
        }
        return methods.singleOrNull()
    }

    private fun referencesClassInMembersOrNestedClasses(
        cls: Class<*>,
        targetClass: Class<*>,
        maxDepth: Int = 1,
    ): Boolean {
        for (method in cls.declaredMethods) {
            if (typeReferencesClass(method.genericReturnType, targetClass.name)) return true
            if (method.genericParameterTypes.any { typeReferencesClass(it, targetClass.name) }) return true
            if (method.returnType == targetClass || method.parameterTypes.any { it == targetClass }) return true
        }
        for (field in cls.declaredFields) {
            if (typeReferencesClass(field.genericType, targetClass.name) || field.type == targetClass) return true
        }
        if (maxDepth <= 0) return false
        for (nested in cls.declaredClasses) {
            if (referencesClassInMembersOrNestedClasses(nested, targetClass, maxDepth - 1)) return true
        }
        return false
    }

    private fun hasGenericSuperclassArgument(
        cls: Class<*>,
        expectedRawType: Class<*>,
        expectedArgument: Class<*>,
    ): Boolean {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            val generic = current.genericSuperclass
            if (generic is ParameterizedType) {
                val rawClass = generic.rawType as? Class<*>
                if (rawClass != null && expectedRawType.isAssignableFrom(rawClass)) {
                    return generic.actualTypeArguments.any { type ->
                        typeReferencesClass(type, expectedArgument.name)
                    }
                }
            }
            current = current.superclass
        }
        return false
    }

    private fun typeReferencesClass(type: Type, className: String): Boolean {
        return when (type) {
            is Class<*> -> type.name == className
            is ParameterizedType -> {
                typeReferencesClass(type.rawType, className) ||
                    type.actualTypeArguments.any { typeReferencesClass(it, className) }
            }
            else -> type.typeName == className
        }
    }

    private fun scanPlainUrlClickableSpanSymbols(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PlainUrlClickableSpanScanSymbols {
        val spanClass = resolvePlainUrlClickableSpanClass(candidates, cl, logger) ?: return PlainUrlClickableSpanScanSymbols()

        val onClickMethod = spanClass.declaredMethods.singleOrNull { method ->
            isPlainUrlClickableSpanOnClickMethod(method, "onClick")
        } ?: run {
            log(logger, "plainUrlClickableSpan: onClick(View) not found in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }
        val resolvedFields = resolvePlainUrlClickableSpanFields(spanClass, logger) ?: run {
            log(logger, "plainUrlClickableSpan: fields mismatch in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }
        val typeField = resolvedFields.typeField
        val urlField = resolvedFields.urlField
        val textField = resolvedFields.textField

        if (!isPlainUrlClickableSpanStructureValid(spanClass, onClickMethod, typeField, urlField, textField)) {
            log(logger, "plainUrlClickableSpan: structure mismatch in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }

        val ownerClasses = collectPlainUrlClickableSpanOnClickOwners(candidates, cl, spanClass, onClickMethod.name)
        if (ownerClasses.isEmpty()) {
            log(logger, "plainUrlClickableSpan: no onClick owners found")
            return PlainUrlClickableSpanScanSymbols()
        }

        log(
            logger,
            "plainUrlClickableSpan matched: ${spanClass.name}.${onClickMethod.name} " +
                "owners=${ownerClasses.size} fields=${typeField.name}/${urlField.name}/${textField.name} " +
                "evidence=${resolvedFields.evidence}",
        )
        return PlainUrlClickableSpanScanSymbols(
            className = spanClass.name,
            onClickMethod = onClickMethod.name,
            onClickOwnerClasses = ownerClasses,
            typeField = typeField.name,
            urlField = urlField.name,
            textField = textField.name,
        )
    }

    private fun resolvePlainUrlClickableSpanClass(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Class<*>? {
        safeFindClass(PLAIN_URL_CLICKABLE_SPAN_CLASS, cl)
            ?.takeIf(::isPlainUrlClickableSpanFixedClassCandidate)
            ?.let { return it }

        val matches = ArrayList<PlainUrlClickableSpanClassMatch>()
        var skippedByReflection = 0
        for (className in candidates) {
            if (!isPlainUrlClickableSpanCandidateClassName(className)) continue
            val cls = try {
                safeFindClass(className, cl)
            } catch (_: Throwable) {
                skippedByReflection++
                null
            } ?: continue
            val score = scorePlainUrlClickableSpanClass(cls)
            if (score >= 120) {
                matches.add(PlainUrlClickableSpanClassMatch(cls, score))
            }
        }
        if (skippedByReflection > 0) {
            log(logger, "plainUrlClickableSpan scan skipped classes by reflection=$skippedByReflection")
        }
        if (matches.isEmpty()) {
            log(logger, "plainUrlClickableSpan: no semantic class match among ${candidates.size} candidates")
            return null
        }

        val sorted = matches.sortedWith(
            compareByDescending<PlainUrlClickableSpanClassMatch> { it.score }
                .thenBy { it.clazz.name.length }
                .thenBy { it.clazz.name },
        )
        val best = sorted.first()
        val topScoreMatches = sorted.filter { it.score == best.score }
        if (topScoreMatches.size > 1) {
            val sample = topScoreMatches.take(5).joinToString("; ") { "${it.clazz.name}:${it.score}" }
            log(logger, "plainUrlClickableSpan ambiguous class score=${best.score}: $sample")
            return null
        }
        val second = sorted.getOrNull(1)
        if (second != null && best.score - second.score < 12) {
            log(
                logger,
                "plainUrlClickableSpan ambiguous class close score: " +
                    "best=${best.clazz.name}:${best.score}, second=${second.clazz.name}:${second.score}",
            )
            return null
        }

        log(logger, "plainUrlClickableSpan class matched by semantic scan: ${best.clazz.name} score=${best.score}")
        return best.clazz
    }

    private fun isPlainUrlClickableSpanFixedClassCandidate(cls: Class<*>): Boolean {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return false
        if (!ClickableSpan::class.java.isAssignableFrom(cls)) return false
        val hasOnClick = cls.declaredMethods.any { method ->
            isPlainUrlClickableSpanOnClickMethod(method, "onClick")
        }
        return hasOnClick && hasPlainUrlClickableSpanConstructor(cls)
    }

    private fun isPlainUrlClickableSpanCandidateClassName(className: String): Boolean {
        if (!className.startsWith("com.baidu.tieba.") && !className.startsWith("com.baidu.tbadk.")) return false
        val shortName = className.substringAfterLast('.')
        return isLikelyObfuscatedShortName(shortName) ||
            className.startsWith("com.baidu.tbadk.widget.richText.") ||
            shortName.contains("RichText", ignoreCase = true) ||
            shortName.contains("Clickable", ignoreCase = true) ||
            shortName.contains("Span", ignoreCase = true)
    }

    private fun scorePlainUrlClickableSpanClass(cls: Class<*>): Int {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return 0
        if (!ClickableSpan::class.java.isAssignableFrom(cls)) return 0
        if (!cls.declaredMethods.any { isPlainUrlClickableSpanOnClickMethod(it, "onClick") }) return 0
        if (!hasPlainUrlClickableSpanConstructor(cls)) return 0
        val fields = collectInstanceFields(cls)
        val intFieldCount = fields.count { !Modifier.isStatic(it.modifiers) && isIntType(it.type) }
        val stringFieldCount = fields.count { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        if (intFieldCount < 2 || stringFieldCount < 1) return 0

        var score = 110
        if (cls.name == PLAIN_URL_CLICKABLE_SPAN_CLASS) score += 45
        if (cls.name.startsWith("com.baidu.tieba.")) score += 12
        if (cls.simpleName.length <= 4) score += 8
        if (intFieldCount >= 4) score += 16 else score += intFieldCount * 3
        if (stringFieldCount >= 3) score += 16 else score += stringFieldCount * 4
        if (fields.any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type.isInterface &&
                    field.type.declaredMethods.any { method ->
                        method.returnType == Void.TYPE &&
                            method.parameterTypes.size == 1 &&
                            method.parameterTypes[0].isAssignableFrom(cls)
                    }
            }
        ) {
            score += 18
        }
        if (fields.any { field -> !Modifier.isStatic(field.modifiers) && Map::class.java.isAssignableFrom(field.type) }) {
            score += 8
        }
        if (fields.any { field -> !Modifier.isStatic(field.modifiers) && field.type.name == "tbclient.ThemeColorInfo" }) {
            score += 8
        }
        score -= cls.declaredMethods.size / 10
        score -= fields.size / 8
        return score
    }

    private fun collectPlainUrlClickableSpanOnClickOwners(
        candidates: List<String>,
        cl: ClassLoader,
        spanClass: Class<*>,
        onClickMethodName: String,
    ): List<String> {
        val owners = LinkedHashSet<String>()

        fun collect(clazz: Class<*>?) {
            if (clazz == null) return
            if (!spanClass.isAssignableFrom(clazz)) return
            val hasOnClick = clazz.declaredMethods.any { method ->
                isPlainUrlClickableSpanOnClickMethod(method, onClickMethodName)
            }
            if (hasOnClick) owners.add(clazz.name)
        }

        collect(spanClass)
        for (containerName in PLAIN_URL_CLICKABLE_SPAN_CONTAINER_CLASSES) {
            val containerClass = safeFindClass(containerName, cl) ?: continue
            for (nested in containerClass.declaredClasses) {
                collect(nested)
            }
        }
        for (className in candidates) {
            val shortName = className.substringAfterLast('.')
            if (!isLikelyObfuscatedShortName(shortName) && '$' !in className) continue
            val clazz = safeFindClass(className, cl) ?: continue
            collect(clazz)
        }
        return owners.toList()
    }

    private fun resolvePlainUrlClickableSpanFields(
        spanClass: Class<*>,
        logger: ScanLogger?,
    ): PlainUrlClickableSpanFieldSymbols? {
        probePlainUrlClickableSpanConstructorFields(spanClass, logger)?.let { return it }
        resolvePlainUrlClickableSpanNamedFields(spanClass)?.let { return it }
        return resolvePlainUrlClickableSpanOrderedFields(spanClass)
    }

    private fun probePlainUrlClickableSpanConstructorFields(
        spanClass: Class<*>,
        logger: ScanLogger?,
    ): PlainUrlClickableSpanFieldSymbols? {
        val constructors = spanClass.declaredConstructors
            .filter { ctor ->
                ctor.parameterTypes.size <= 5 &&
                    ctor.parameterTypes.any(::isIntType) &&
                    ctor.parameterTypes.any { it == String::class.java }
            }
            .sortedWith(compareBy({ it.parameterTypes.size }, { it.toGenericString() }))
        val fields = collectInstanceFields(spanClass)

        for (ctor in constructors) {
            val paramTypes = ctor.parameterTypes
            val firstIntIndex = paramTypes.indexOfFirst(::isIntType)
            val firstStringIndex = paramTypes.indexOfFirst { it == String::class.java }
            if (firstIntIndex < 0 || firstStringIndex < 0) continue

            val intMarkers = mutableMapOf<Int, Int>()
            val stringMarkers = mutableMapOf<Int, String>()
            val args = arrayOfNulls<Any>(paramTypes.size)
            var unsupportedPrimitive = false
            for (index in paramTypes.indices) {
                val type = paramTypes[index]
                args[index] = when {
                    isIntType(type) -> (775300 + index).also { intMarkers[index] = it }
                    type == String::class.java -> "https://tbhook.example/link-$index".also {
                        stringMarkers[index] = it
                    }
                    isBooleanType(type) -> false
                    type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> 0L
                    type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> 0f
                    type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> 0.0
                    type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType -> 0.toShort()
                    type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType -> 0.toByte()
                    type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType -> 0.toChar()
                    type.isPrimitive -> {
                        unsupportedPrimitive = true
                        null
                    }
                    else -> null
                }
            }
            if (unsupportedPrimitive) continue

            val instance = runCatching {
                ctor.isAccessible = true
                ctor.newInstance(*args)
            }.getOrNull() ?: continue
            val typeMarker = intMarkers[firstIntIndex] ?: continue
            val urlMarker = stringMarkers[firstStringIndex] ?: continue

            val typeField = fields.singleOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    isIntType(field.type) &&
                    runCatching {
                        field.isAccessible = true
                        field.get(instance) == typeMarker
                    }.getOrDefault(false)
            } ?: continue
            val urlField = fields.singleOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java &&
                    runCatching {
                        field.isAccessible = true
                        field.get(instance) == urlMarker
                    }.getOrDefault(false)
            } ?: continue
            val textField = pickPlainUrlClickableSpanTextField(fields, urlField)
            return PlainUrlClickableSpanFieldSymbols(
                typeField = typeField,
                urlField = urlField,
                textField = textField,
                evidence = "constructor",
            )
        }

        log(logger, "plainUrlClickableSpan: constructor probe unavailable in ${spanClass.name}")
        return null
    }

    private fun resolvePlainUrlClickableSpanNamedFields(spanClass: Class<*>): PlainUrlClickableSpanFieldSymbols? {
        val fields = collectInstanceFields(spanClass)
        val typeField = fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_TYPE_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                isIntType(field.type)
        } ?: return null
        val urlField = fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_URL_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: return null
        return PlainUrlClickableSpanFieldSymbols(
            typeField = typeField,
            urlField = urlField,
            textField = pickPlainUrlClickableSpanTextField(fields, urlField),
            evidence = "named",
        )
    }

    private fun resolvePlainUrlClickableSpanOrderedFields(spanClass: Class<*>): PlainUrlClickableSpanFieldSymbols? {
        val fields = collectInstanceFields(spanClass)
        val intFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && isIntType(field.type)
        }
        val stringFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == String::class.java
        }
        val typeField = intFields.getOrNull(3) ?: return null
        val urlField = stringFields.firstOrNull() ?: return null
        val textField = stringFields.getOrNull(2) ?: urlField
        return PlainUrlClickableSpanFieldSymbols(
            typeField = typeField,
            urlField = urlField,
            textField = textField,
            evidence = "fieldOrder",
        )
    }

    private fun pickPlainUrlClickableSpanTextField(fields: List<Field>, urlField: Field): Field {
        return fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_TEXT_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: urlField
    }

    private fun isPlainUrlClickableSpanOnClickMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == View::class.java
    }

    private fun isPlainUrlMessageDispatchMethod(method: Method, responsedMessageClass: Class<*>): Boolean {
        return method.name == PLAIN_URL_MESSAGE_DISPATCH_METHOD &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            responsedMessageClass.isAssignableFrom(method.parameterTypes[0])
    }

    private fun isPlainUrlClickableSpanStructureValid(
        spanClass: Class<*>,
        onClickMethod: Method,
        typeField: Field,
        urlField: Field,
        textField: Field,
    ): Boolean {
        if (spanClass.isInterface || Modifier.isAbstract(spanClass.modifiers)) return false
        if (!ClickableSpan::class.java.isAssignableFrom(spanClass)) return false
        if (!isPlainUrlClickableSpanOnClickMethod(onClickMethod, onClickMethod.name)) return false
        if (Modifier.isStatic(typeField.modifiers) || !isIntType(typeField.type)) return false
        if (Modifier.isStatic(urlField.modifiers) || urlField.type != String::class.java) return false
        if (Modifier.isStatic(textField.modifiers) || textField.type != String::class.java) return false
        if (!hasPlainUrlClickableSpanConstructor(spanClass)) return false
        val fields = collectInstanceFields(spanClass)
        return fields.count { isIntType(it.type) } >= 4 &&
            fields.count { it.type == String::class.java } >= 3
    }

    private fun hasPlainUrlClickableSpanConstructor(spanClass: Class<*>): Boolean {
        return spanClass.declaredConstructors.any { ctor ->
            ctor.parameterTypes.any(::isIntType) &&
                ctor.parameterTypes.any { it == String::class.java }
        }
    }

    private fun scanPlainUrlMessageDispatchSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PlainUrlMessageDispatchScanSymbols {
        val messageManagerClass = safeFindClass(PLAIN_URL_MESSAGE_MANAGER_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $PLAIN_URL_MESSAGE_MANAGER_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val responsedMessageClass = safeFindClass(PLAIN_URL_RESPONSED_MESSAGE_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $PLAIN_URL_RESPONSED_MESSAGE_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val customResponsedMessageClass = safeFindClass(PLAIN_URL_CUSTOM_RESPONSED_MESSAGE_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $PLAIN_URL_CUSTOM_RESPONSED_MESSAGE_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val applicationClass = safeFindClass(PLAIN_URL_APPLICATION_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $PLAIN_URL_APPLICATION_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        if (!responsedMessageClass.isAssignableFrom(customResponsedMessageClass)) {
            log(logger, "plainUrlMessage: CustomResponsedMessage hierarchy mismatch")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val dispatchMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
            isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
        } ?: run {
            log(logger, "plainUrlMessage: dispatchResponsedMessage(ResponsedMessage) not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getCmdMethod = responsedMessageClass.declaredMethods.singleOrNull { method ->
            method.name == PLAIN_URL_GET_CMD_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isIntType(method.returnType)
        } ?: run {
            log(logger, "plainUrlMessage: ResponsedMessage.getCmd() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getDataMethod = customResponsedMessageClass.declaredMethods.singleOrNull { method ->
            method.name == PLAIN_URL_GET_DATA_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Any::class.java
        } ?: run {
            log(logger, "plainUrlMessage: CustomResponsedMessage.getData() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getInstMethod = applicationClass.declaredMethods.singleOrNull { method ->
            method.name == PLAIN_URL_GET_INST_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                applicationClass.isAssignableFrom(method.returnType)
        } ?: run {
            log(logger, "plainUrlMessage: TbadkCoreApplication.getInst() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }

        log(
            logger,
            "plainUrlMessage matched: ${messageManagerClass.name}.${dispatchMethod.name} cmd=$PLAIN_URL_CLICK_MESSAGE_CMD",
        )
        return PlainUrlMessageDispatchScanSymbols(
            messageManagerClass = messageManagerClass.name,
            dispatchMethod = dispatchMethod.name,
            responsedMessageClass = responsedMessageClass.name,
            getCmdMethod = getCmdMethod.name,
            customResponsedMessageClass = customResponsedMessageClass.name,
            getDataMethod = getDataMethod.name,
            applicationClass = applicationClass.name,
            getInstMethod = getInstMethod.name,
        )
    }

    private fun scanPrivateReadReceiptSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PrivateReadReceiptScanSymbols {
        val modelClass = safeFindClass(PRIVATE_READ_RECEIPT_MODEL_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_MODEL_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val messageManagerClass = safeFindClass(PRIVATE_READ_RECEIPT_MESSAGE_MANAGER_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_MESSAGE_MANAGER_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val messageBaseClass = safeFindClass(PRIVATE_READ_RECEIPT_MESSAGE_BASE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_MESSAGE_BASE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val requestClass = safeFindClass(PRIVATE_READ_RECEIPT_REQUEST_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_REQUEST_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val requestConstructor = requestClass.declaredConstructors.singleOrNull { ctor ->
            ctor.parameterTypes.size == 2 &&
                ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                ctor.parameterTypes[1] == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage(long,long) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val modelBaseClass = safeFindClass(PRIVATE_READ_RECEIPT_MODEL_BASE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_MODEL_BASE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val commitResponseClass = safeFindClass(PRIVATE_READ_RECEIPT_COMMIT_RESPONSE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_COMMIT_RESPONSE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val pageDataClass = safeFindClass(PRIVATE_READ_RECEIPT_PAGE_DATA_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_PAGE_DATA_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val chatMessageClass = safeFindClass(PRIVATE_READ_RECEIPT_CHAT_MESSAGE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_CHAT_MESSAGE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val localDataClass = safeFindClass(PRIVATE_READ_RECEIPT_LOCAL_DATA_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_LOCAL_DATA_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val accountClass = safeFindClass(PRIVATE_READ_RECEIPT_ACCOUNT_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PRIVATE_READ_RECEIPT_ACCOUNT_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        if (!messageBaseClass.isAssignableFrom(requestClass)) {
            log(logger, "privateReadReceipt: request class hierarchy mismatch")
            return PrivateReadReceiptScanSymbols()
        }

        val modelReadDispatchMethod = run {
            // 快速路径先试已知混淆名。
            val byName = PRIVATE_READ_RECEIPT_MODEL_READ_DISPATCH_CANDIDATES.firstNotNullOfOrNull { name ->
                modelClass.declaredMethods.singleOrNull { method ->
                    method.name == name &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty()
                }
            }
            if (byName != null) {
                byName
            } else {
                // 结构兜底：找只在 PersonalMsglistModel 声明的唯一 final 无参 void 方法。
                // 父类里声明的方法不算。
                val ancestorMethodNames = generateSequence(modelClass.superclass) { it.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .filter { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterTypes.isEmpty() }
                    .map { it.name }
                    .toSet()
                modelClass.declaredMethods.singleOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isFinal(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty() &&
                        method.name !in ancestorMethodNames
                }
            }
        } ?: run {
            log(logger, "privateReadReceipt: PersonalMsglistModel read dispatch method not found")
            return PrivateReadReceiptScanSymbols()
        }
        val messageSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_MESSAGE_SEND_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == messageBaseClass
        } ?: run {
            log(logger, "privateReadReceipt: MessageManager.sendMessage(Message) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_MESSAGE_MANAGER_GET_INSTANCE_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == messageManagerClass
        } ?: run {
            log(logger, "privateReadReceipt: MessageManager.getInstance() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val processAckMethod = modelBaseClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_PROCESS_ACK_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == commitResponseClass
        } ?: run {
            log(logger, "privateReadReceipt: MsglistModel.processMsgACK(ResponseCommitMessage) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val responseErrorMethod = collectInstanceMethods(commitResponseClass).singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_RESPONSE_ERROR_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isIntType(method.returnType)
        } ?: run {
            log(logger, "privateReadReceipt: ResponseCommitMessage.getError() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val requestFields = collectInstanceFields(requestClass)
        val requestMsgIdField = requestFields.singleOrNull { field ->
            field.name == PRIVATE_READ_RECEIPT_REQUEST_MSG_ID_FIELD &&
                field.type == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage.hasSentMsgId not found")
            return PrivateReadReceiptScanSymbols()
        }
        val requestToUidField = requestFields.singleOrNull { field ->
            field.name == PRIVATE_READ_RECEIPT_REQUEST_TO_UID_FIELD &&
                field.type == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage.toUid not found")
            return PrivateReadReceiptScanSymbols()
        }
        val modelDataField = collectInstanceFields(modelClass).singleOrNull { field ->
            field.name == PRIVATE_READ_RECEIPT_MODEL_DATA_FIELD &&
                field.type == pageDataClass
        } ?: run {
            log(logger, "privateReadReceipt: PersonalMsglistModel.mDatas not found")
            return PrivateReadReceiptScanSymbols()
        }
        val pageDataChatListMethod = pageDataClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_PAGE_DATA_CHAT_LIST_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isListType(method.returnType)
        } ?: run {
            log(logger, "privateReadReceipt: MsgPageData.getChatMessages() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatMsgIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_CHAT_MESSAGE_MSG_ID_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getMsgId() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatUserIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_CHAT_MESSAGE_USER_ID_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getUserId() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatLocalDataMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_CHAT_MESSAGE_LOCAL_DATA_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == localDataClass
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getLocalData() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val localStatusMethod = localDataClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_LOCAL_DATA_STATUS_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Short::class.javaObjectType
        } ?: run {
            log(logger, "privateReadReceipt: MsgLocalData.getStatus() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val currentAccountMethod = accountClass.declaredMethods.singleOrNull { method ->
            method.name == PRIVATE_READ_RECEIPT_CURRENT_ACCOUNT_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == String::class.java
        } ?: run {
            log(logger, "privateReadReceipt: TbadkCoreApplication.getCurrentAccount() not found")
            return PrivateReadReceiptScanSymbols()
        }

        log(
            logger,
            "privateReadReceipt matched: ${modelClass.name}.${modelReadDispatchMethod.name} " +
                "${messageManagerClass.name}.${messageSendMethod.name}(${messageBaseClass.name})",
        )
        return PrivateReadReceiptScanSymbols(
            modelClass = modelClass.name,
            modelReadDispatchMethod = modelReadDispatchMethod.name,
            messageManagerClass = messageManagerClass.name,
            messageManagerGetInstanceMethod = messageManagerGetInstanceMethod.name,
            messageSendMethod = messageSendMethod.name,
            messageBaseClass = messageBaseClass.name,
            requestClass = requestClass.name,
            modelBaseClass = modelBaseClass.name,
            commitResponseClass = commitResponseClass.name,
            processAckMethod = processAckMethod.name,
            responseErrorMethod = responseErrorMethod.name,
            requestMsgIdField = requestMsgIdField.name,
            requestToUidField = requestToUidField.name,
            modelDataField = modelDataField.name,
            pageDataClass = pageDataClass.name,
            pageDataChatListMethod = pageDataChatListMethod.name,
            chatMessageClass = chatMessageClass.name,
            chatMessageMsgIdMethod = chatMsgIdMethod.name,
            chatMessageUserIdMethod = chatUserIdMethod.name,
            chatMessageLocalDataMethod = chatLocalDataMethod.name,
            localDataClass = localDataClass.name,
            localDataStatusMethod = localStatusMethod.name,
            accountClass = accountClass.name,
            currentAccountMethod = currentAccountMethod.name,
        )
    }

    private fun scanMountCardLinkLayoutSymbols(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): MountCardLinkLayoutScanSymbols {
        val layoutClass = safeFindClass(MOUNT_CARD_LINK_LAYOUT_CLASS, cl) ?: run {
            log(logger, "mountCardLinkLayout: class not found: $MOUNT_CARD_LINK_LAYOUT_CLASS")
            return MountCardLinkLayoutScanSymbols()
        }
        val dataClass = safeFindClass(MOUNT_CARD_LINK_INFO_DATA_CLASS, cl) ?: run {
            log(logger, "mountCardLinkLayout: data class not found: $MOUNT_CARD_LINK_INFO_DATA_CLASS")
            return MountCardLinkLayoutScanSymbols()
        }
        val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
            isMountCardLinkLayoutOnClickMethod(method, MOUNT_CARD_LINK_LAYOUT_ON_CLICK_METHOD)
        } ?: run {
            log(logger, "mountCardLinkLayout: onClick(View) not found in ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        val dataField = resolveMountCardLinkLayoutDataField(layoutClass, dataClass) ?: run {
            log(logger, "mountCardLinkLayout: data field mismatch in ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
            method.name == MOUNT_CARD_LINK_INFO_GET_URL_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterTypes.isEmpty()
        } ?: run {
            log(logger, "mountCardLinkLayout: getUrl() not found in ${dataClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        if (!isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)) {
            log(logger, "mountCardLinkLayout: structure mismatch in ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }

        log(
            logger,
            "mountCardLinkLayout matched: ${layoutClass.name}.${onClickMethod.name} " +
                "data=${dataField.name} url=${dataClass.name}.${getUrlMethod.name}",
        )
        return MountCardLinkLayoutScanSymbols(
            layoutClass = layoutClass.name,
            onClickMethod = onClickMethod.name,
            dataField = dataField.name,
            dataClass = dataClass.name,
            getUrlMethod = getUrlMethod.name,
        )
    }

    private fun isMountCardLinkLayoutOnClickMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == View::class.java
    }

    private fun resolveMountCardLinkLayoutDataField(layoutClass: Class<*>, dataClass: Class<*>): Field? {
        val candidates = collectInstanceFields(layoutClass).filter { field ->
            !Modifier.isStatic(field.modifiers) && dataClass.isAssignableFrom(field.type)
        }
        return candidates.firstOrNull { it.name == MOUNT_CARD_LINK_LAYOUT_DATA_FIELD }
            ?: candidates.singleOrNull()
    }

    private fun isMountCardLinkLayoutStructureValid(
        layoutClass: Class<*>,
        onClickMethod: Method,
        dataClass: Class<*>,
        dataField: Field,
        getUrlMethod: Method,
    ): Boolean {
        if (!View.OnClickListener::class.java.isAssignableFrom(layoutClass)) return false
        if (!View::class.java.isAssignableFrom(layoutClass)) return false
        if (!isMountCardLinkLayoutOnClickMethod(onClickMethod, onClickMethod.name)) return false
        if (Modifier.isStatic(dataField.modifiers) || !dataClass.isAssignableFrom(dataField.type)) return false
        if (Modifier.isStatic(getUrlMethod.modifiers)) return false
        return getUrlMethod.returnType == String::class.java && getUrlMethod.parameterTypes.isEmpty()
    }

    private fun kotlinMetadataStrings(targetClass: Class<*>): List<String> {
        val metadata = targetClass.getAnnotation(kotlin.Metadata::class.java) ?: return emptyList()
        return (
            readMetadataStringArray(metadata, "d1") +
                readMetadataStringArray(metadata, "d2") +
                readMetadataStringArray(metadata, "data1") +
                readMetadataStringArray(metadata, "data2")
            )
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun readMetadataStringArray(metadata: Annotation, methodName: String): List<String> {
        val value = runCatching {
            metadata.javaClass.getMethod(methodName).invoke(metadata)
        }.getOrNull() as? Array<*> ?: return emptyList()
        return value.mapNotNull { it as? String }
    }

    private fun logFeedLoadMoreDiagnostics(cl: ClassLoader, logger: ScanLogger?) {
        val adapterClassName = "com.baidu.tieba.feed.list.FeedTemplateAdapter"
        val adapterClass = safeFindClass(adapterClassName, cl)
        if (adapterClass == null) {
            log(logger, "feedLoadMore diag: class not found: $adapterClassName")
            logTemplateAdapterDiagnostics(cl, logger)
            return
        }

        val metadata = kotlinMetadataStrings(adapterClass)
        val metadataHints = metadata
            .flatMap { text ->
                listOf("loadMore", "refreshList", "setList", "List").filter { text.contains(it) }
            }
            .distinct()
            .joinToString(",")
            .ifBlank { "-" }
        val listMethods = adapterClass.declaredMethods
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.any { type ->
                        List::class.java.isAssignableFrom(type) ||
                            java.util.Collection::class.java.isAssignableFrom(type)
                    }
            }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(12)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }
        val voidMethods = adapterClass.declaredMethods
            .filter { it.returnType == Void.TYPE && it.parameterTypes.size <= 2 }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(16)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }

        log(
            logger,
            "feedLoadMore diag: class=$adapterClassName, super=${adapterClass.superclass?.name}, " +
                "metadataHints=$metadataHints, listMethods=$listMethods, voidMethods=$voidMethods",
        )
        logTemplateAdapterDiagnostics(cl, logger)
    }

    private fun logTemplateAdapterDiagnostics(cl: ClassLoader, logger: ScanLogger?) {
        val templateClassName = "com.baidu.tieba.feed.list.TemplateAdapter"
        val templateClass = safeFindClass(templateClassName, cl) ?: run {
            log(logger, "feedLoadMore diag: class not found: $templateClassName")
            return
        }
        val listMethods = templateClass.declaredMethods
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.any { type ->
                        List::class.java.isAssignableFrom(type) ||
                            java.util.Collection::class.java.isAssignableFrom(type)
                    }
            }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(12)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }
        log(logger, "feedLoadMore diag: class=$templateClassName, listMethods=$listMethods")
    }

    private fun buildPbEarlyAdDiagnosticMatch(targetClass: Class<*>): ScanMatch? {
        val metadata = kotlinMetadataStrings(targetClass)
        val metadataScore = metadata.count { text ->
            text.contains("Ad") ||
                text.contains("Advert") ||
                text.contains("Adx") ||
                text.contains("FunAd") ||
                text.contains("insert", ignoreCase = true)
        }
        val interestingMethods = targetClass.declaredMethods.mapNotNull { method ->
            if (!Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers)) return@mapNotNull null
            val typeNames = method.parameterTypes.map { it.name } + method.returnType.name
            var score = 0
            if (typeNames.any { it == PB_DATA_CLASS }) score += 5
            if (typeNames.any { it == PB_FRAGMENT_CLASS }) score += 4
            if (typeNames.any { it == PB_ADAPTER_DATA_CLASS }) score += 4
            if (typeNames.any { it == THREAD_DATA_CLASS }) score += 3
            if (typeNames.any { it == android.util.SparseArray::class.java.name }) score += 4
            if (typeNames.any { it == ArrayList::class.java.name }) score += 2
            if (method.returnType == Void.TYPE) score += 1
            if (score >= 6) method to score else null
        }
        if (interestingMethods.isEmpty() && metadataScore == 0) return null

        var score = 60 + metadataScore * 5
        if (targetClass.name.contains(".pb.", ignoreCase = true)) score += 10
        if (targetClass.name.contains("underlayer", ignoreCase = true)) score += 10
        if (targetClass.simpleName.endsWith("Kt")) score += 8
        score += interestingMethods.sumOf { it.second }.coerceAtMost(80)
        score -= targetClass.declaredMethods.size / 12
        val methodText = interestingMethods
            .sortedWith(compareByDescending<Pair<Method, Int>> { it.second }.thenBy { it.first.name })
            .take(6)
            .joinToString("; ") { describeMethodShape(it.first) }
        return ScanMatch(targetClass.name, methodText, "", score)
    }

    private fun logPbEarlyAdInsertDiagnostics(diagnostics: List<ScanMatch>, logger: ScanLogger?) {
        if (diagnostics.isEmpty()) {
            log(logger, "pbEarlyAdInsert diag: no partial candidates with PbData/PbFragment/list signatures")
            return
        }
        val sample = diagnostics
            .sortedWith(compareByDescending<ScanMatch> { it.score }.thenBy { it.className })
            .take(6)
            .joinToString(" || ") { "${it.className}:${it.score}[${it.methodName.ifBlank { "-" }}]" }
        log(logger, "pbEarlyAdInsert diag top partial candidates: $sample")
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun scorePbEarlyAdInsertCandidate(targetClass: Class<*>, specs: List<String>): Int {
        val metadata = kotlinMetadataStrings(targetClass)
        val specText = specs.joinToString(";")
        var score = 120
        if (targetClass.name == PB_AD_INSERT_CLASS) score += 80
        if (targetClass.name.startsWith("com.baidu.tieba.pb.")) score += 30
        if (targetClass.name.contains(".underlayer.")) score += 20
        if (targetClass.simpleName.endsWith("Kt")) score += 20
        if (metadata.any { it.contains("insertFunAd") || it.contains("handlePbCommentFunAd") }) score += 40
        if (metadata.any { it.contains("Advert") || it.contains("Adx") || it.contains("FunAd") }) score += 20
        if (specText.contains(android.util.SparseArray::class.java.name)) score += 35
        if (specText.contains(PB_FRAGMENT_CLASS) && specText.contains(ArrayList::class.java.name)) score += 10
        score += specs.size * 8
        score -= targetClass.declaredMethods.size / 10
        score -= targetClass.simpleName.length / 4
        return score
    }

    private fun scanPbEarlyAdInsertMethodSpecs(
        targetClass: Class<*>,
        cl: ClassLoader,
        logger: ScanLogger?,
        logExactMismatch: Boolean = true,
    ): List<String> {
        if (targetClass.name == PB_AD_INSERT_CLASS) {
            log(logger, "pbEarlyAdInsert: fixed class found: $PB_AD_INSERT_CLASS")
        }

        val specs = ArrayList<String>(4)
        specs.addAll(
            findPbEarlyAdMethodSpecs(
                targetClass = targetClass,
                cl = cl,
                logger = logger,
                label = "pbCommentAd",
                returnTypeName = "void",
                paramTypeNames = listOf(PB_DATA_CLASS, PB_FRAGMENT_CLASS, "boolean"),
                expectedCount = 2,
                logMismatch = logExactMismatch,
            ),
        )
        specs.addAll(
            findPbEarlyAdMethodSpecs(
                targetClass = targetClass,
                cl = cl,
                logger = logger,
                label = "pbCommentFunAd",
                returnTypeName = "void",
                paramTypeNames = listOf(
                    ArrayList::class.java.name,
                    "boolean",
                    ArrayList::class.java.name,
                    PB_FRAGMENT_CLASS,
                    PB_DATA_CLASS,
                ),
                expectedCount = 1,
                logMismatch = logExactMismatch,
            ),
        )
        specs.addAll(
            findPbEarlyAdMethodSpecs(
                targetClass = targetClass,
                cl = cl,
                logger = logger,
                label = "pbFunBannerAd",
                returnTypeName = PB_ADAPTER_DATA_CLASS,
                paramTypeNames = listOf(
                    ArrayList::class.java.name,
                    "int",
                    PB_DATA_CLASS,
                    THREAD_DATA_CLASS,
                    PB_FRAGMENT_CLASS,
                ),
                expectedCount = 1,
                logMismatch = logExactMismatch,
            ),
        )

        val exactSpecs = specs.distinct()
        if (exactSpecs.size >= 4) return exactSpecs
        return scanPbEarlyAdInsertFlexibleMethodSpecs(targetClass, cl)
    }

    private fun scanPbEarlyAdInsertFlexibleMethodSpecs(targetClass: Class<*>, cl: ClassLoader): List<String> {
        val pbFragmentClass = resolvePbEarlyAdType(PB_FRAGMENT_CLASS, cl) ?: return emptyList()
        val threadDataClass = resolvePbEarlyAdType(THREAD_DATA_CLASS, cl)
        val adapterDataClass = resolvePbEarlyAdType(PB_ADAPTER_DATA_CLASS, cl)
        val methods = targetClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)
        }
        if (methods.size < PB_EARLY_AD_INSERT_MIN_METHOD_COUNT) return emptyList()
        fun isListLike(type: Class<*>): Boolean =
            List::class.java.isAssignableFrom(type) ||
                java.util.Collection::class.java.isAssignableFrom(type)

        val dataTypes = LinkedHashSet<Class<*>>()
        for (method in methods) {
            for (type in method.parameterTypes) {
                if (type.isPrimitive ||
                    type == String::class.java ||
                    type == pbFragmentClass ||
                    type == threadDataClass ||
                    isListLike(type)) {
                    continue
                }
                dataTypes.add(type)
            }
        }

        var bestSpecs: List<String> = emptyList()
        var bestScore = 0
        var bestScoreCount = 0
        for (dataType in dataTypes) {
            val commentAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == dataType &&
                    method.parameterTypes[1] == pbFragmentClass &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
            val commentFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == pbFragmentClass &&
                    method.parameterTypes[4] == dataType
            }
            val bannerAd = methods.filter { method ->
                method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == dataType &&
                    (threadDataClass == null || threadDataClass.isAssignableFrom(method.parameterTypes[3])) &&
                    method.parameterTypes[4] == pbFragmentClass &&
                    !method.returnType.isPrimitive &&
                    method.returnType != Void.TYPE &&
                    (adapterDataClass == null || adapterDataClass.isAssignableFrom(method.returnType))
            }
            if (commentAd.size != 2 || commentFunAd.size != 1 || bannerAd.size != 1) continue

            val placeholderFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 6 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[3]) &&
                    method.parameterTypes[4] == dataType &&
                    isListLike(method.parameterTypes[5])
            }

            val candidateSpecs = (
                commentAd + commentFunAd + bannerAd +
                    placeholderFunAd.takeIf { it.size == 1 }.orEmpty()
                )
                .sortedBy { it.name }
                .map(::encodePbEarlyAdMethodSpec)
                .distinct()
            val score = candidateSpecs.size * 10 +
                if (dataType.name == PB_DATA_CLASS) 20 else 0
            if (candidateSpecs.size >= 4 && score > bestScore) {
                bestSpecs = candidateSpecs
                bestScore = score
                bestScoreCount = 1
            } else if (candidateSpecs.size >= 4 && score == bestScore) {
                bestScoreCount++
            }
        }
        if (bestSpecs.size >= 4) {
            return if (bestScoreCount == 1) bestSpecs else emptyList()
        }

        var newBestSpecs: List<String> = emptyList()
        var newBestScore = 0
        var newBestScoreCount = 0
        for (dataType in dataTypes) {
            val commentFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == pbFragmentClass &&
                    method.parameterTypes[4] == dataType
            }
            val sparseAdData = methods.filter { method ->
                android.util.SparseArray::class.java.isAssignableFrom(method.returnType) &&
                    method.parameterTypes.size == 3 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == dataType &&
                    method.parameterTypes[2] == pbFragmentClass
            }
            val commentAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == dataType &&
                    method.parameterTypes[1] == pbFragmentClass &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
            val placeholderFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 6 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[3]) &&
                    method.parameterTypes[4] == dataType &&
                    isListLike(method.parameterTypes[5])
            }
            if (commentFunAd.size != 1 || sparseAdData.size != 1) continue

            val directCommentAd = commentAd.takeIf { it.size in 1..2 }.orEmpty()
            val directPlaceholderFunAd = placeholderFunAd.takeIf { it.size == 1 }.orEmpty()
            val candidateSpecs = (
                directCommentAd + commentFunAd + sparseAdData + directPlaceholderFunAd
                )
                .sortedBy { it.name }
                .map(::encodePbEarlyAdMethodSpec)
                .distinct()
            val score = candidateSpecs.size * 10 + 50 +
                (if (directCommentAd.isNotEmpty()) 20 else 0) +
                (if (directPlaceholderFunAd.isNotEmpty()) 15 else 0)
            if (candidateSpecs.size >= PB_EARLY_AD_INSERT_MIN_METHOD_COUNT && score > newBestScore) {
                newBestSpecs = candidateSpecs
                newBestScore = score
                newBestScoreCount = 1
            } else if (candidateSpecs.size >= PB_EARLY_AD_INSERT_MIN_METHOD_COUNT && score == newBestScore) {
                newBestScoreCount++
            }
        }
        return if (
            newBestSpecs.size >= PB_EARLY_AD_INSERT_MIN_METHOD_COUNT &&
            newBestScoreCount == 1
        ) {
            newBestSpecs
        } else {
            emptyList()
        }
    }

    private fun findPbEarlyAdMethodSpecs(
        targetClass: Class<*>,
        cl: ClassLoader,
        logger: ScanLogger?,
        label: String,
        returnTypeName: String,
        paramTypeNames: List<String>,
        expectedCount: Int,
        logMismatch: Boolean = true,
    ): List<String> {
        val returnType = resolvePbEarlyAdType(returnTypeName, cl) ?: run {
            if (logMismatch) {
                log(logger, "pbEarlyAdInsert[$label]: return type missing: $returnTypeName")
            }
            return emptyList()
        }
        val paramTypes = paramTypeNames.map { typeName ->
            resolvePbEarlyAdType(typeName, cl) ?: run {
                if (logMismatch) {
                    log(logger, "pbEarlyAdInsert[$label]: param type missing: $typeName")
                }
                return emptyList()
            }
        }
        val matches = targetClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                (method.returnType == returnType || returnType.isAssignableFrom(method.returnType)) &&
                method.parameterTypes.contentEquals(paramTypes.toTypedArray())
        }
        if (matches.size != expectedCount) {
            if (logMismatch) {
                val names = matches.joinToString(",") { it.name }
                log(
                    logger,
                    "pbEarlyAdInsert[$label]: expected=$expectedCount actual=${matches.size} methods=$names",
                )
            }
            return emptyList()
        }
        return matches
            .sortedBy { it.name }
            .map(::encodePbEarlyAdMethodSpec)
    }

    private fun resolvePbEarlyAdType(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            else -> safeFindClass(typeName, cl)
        }
    }

    private fun encodePbEarlyAdMethodSpec(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.name}|${method.returnType.name}|$params"
    }

    private data class ShareIconOwnerCandidate(
        val className: String,
        val score: Int,
    )

    private data class DexShareIconMatch(
        val ownerClassName: String,
        val ownerMethodName: String,
        val resId: Int,
        val score: Int,
    )

    private data class DexAutoRefreshMatch(
        val ownerMethodName: String,
        val score: Int,
        val evidence: String,
    )

    private data class DexAiComponentInitMatch(
        val ownerMethodName: String,
        val score: Int,
        val evidence: String,
        val strong: Boolean = true,
    )

    private data class DexPbAdBidModelMatch(
        val className: String,
        val requestImplMethodName: String,
        val kind: String,
        val score: Int,
        val evidence: String,
    )

    private data class DexPbAdBidScanSymbols(
        val commonModelClassName: String? = null,
        val pageBrowserModelClassName: String? = null,
        val pageBrowserRequestDataMethodName: String? = null,
    )

    private data class DexPbAdBidRawScan(
        val modelMatches: List<DexPbAdBidModelMatch> = emptyList(),
        val pageBrowserRequestDataMethodName: String? = null,
    )

    private object DexEnterForumCapsuleMethodKind {
        const val INIT = "init"
        const val REFRESH = "refresh"
    }

    private data class DexEnterForumCapsuleMethodMatch(
        val ownerMethodName: String,
        val kind: String,
        val score: Int,
        val evidence: String,
        val viewFieldName: String? = null,
        val titleFieldName: String? = null,
    )

    private fun scanAutoRefreshTriggerSymbol(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): String? {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
            log(logger, "autoRefresh: class not found: $PERSONALIZE_PAGE_VIEW_CLASS")
            return null
        }
        val dexMatch = scanAutoRefreshTriggerFromDex(context, cl, logger)
        if (dexMatch != null) return dexMatch

        if (appSourcePaths(context).isNotEmpty()) {
            logAutoRefreshDiagnostics(context, cl, logger)
            return null
        }

        val ruleMatch = runCatching { AutoRefreshTriggerRule().match(targetClass, cl) }.getOrNull()
        if (ruleMatch != null) {
            log(
                logger,
                "autoRefresh matched by AutoRefreshTriggerRule fallback: " +
                    "${ruleMatch.className}.${ruleMatch.methodName} score=${ruleMatch.score}",
            )
            return ruleMatch.methodName
        }
        logAutoRefreshDiagnostics(context, cl, logger)
        return null
    }

    private fun scanAutoRefreshTriggerFromDex(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): String? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "autoRefreshDex: apk source path unavailable")
            return null
        }

        val matches = DexShareIconScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isAutoRefreshMethodNameValid(it.ownerMethodName, cl) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAutoRefreshMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )

        val best = matches.firstOrNull() ?: run {
            log(logger, "autoRefreshDex: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 8) {
            log(
                logger,
                "autoRefreshDex ambiguous: best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }

        log(
            logger,
            "autoRefreshDex matched: $PERSONALIZE_PAGE_VIEW_CLASS.${best.ownerMethodName} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return best.ownerMethodName
    }

    private fun isAutoRefreshMethodNameValid(methodName: String, cl: ClassLoader): Boolean {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: return false
        return targetClass.declaredMethods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
    }

    private fun logAutoRefreshDiagnostics(context: Context, cl: ClassLoader, logger: ScanLogger?) {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
            log(logger, "autoRefresh diag: class not found: $PERSONALIZE_PAGE_VIEW_CLASS")
            return
        }
        val methods = targetClass.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
            .sortedWith(compareBy<Method>({ it.name.length }, { it.name }))
            .take(40)
            .joinToString("; ") { describeMethodShape(it) }
        log(logger, "autoRefresh diag: voidNoArgMethods=$methods")

        val sourcePaths = appSourcePaths(context)
        val candidates = DexShareIconScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isAutoRefreshMethodNameValid(it.ownerMethodName, cl) }
            .sortedWith(compareByDescending<DexAutoRefreshMatch> { it.score }.thenBy { it.ownerMethodName })
            .take(8)
            .joinToString(" || ") { "${it.ownerMethodName}:${it.score}[${it.evidence}]" }
        if (candidates.isNotBlank()) {
            log(logger, "autoRefresh diag candidates: $candidates")
        }
    }

    private fun scanImageViewerShareIconFromDex(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Int? {
        val ownerCandidates = findShareIconOwnerCandidates(candidates, cl)
        if (ownerCandidates.isEmpty()) {
            log(logger, "imageViewerShareIconDex: owner candidate not found")
            return null
        }

        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "imageViewerShareIconDex: apk source path unavailable")
            return null
        }

        val ownerClassNames = ownerCandidates
            .flatMap { owner ->
                val nested = safeFindClass(owner.className, cl)
                    ?.declaredClasses
                    ?.map { it.name }
                    .orEmpty()
                listOf(owner.className) + nested
            }
            .distinct()
        val match = DexShareIconScanner.scan(sourcePaths, ownerClassNames, cl, logger)
        if (match != null) {
            log(
                logger,
                "imageViewerShareIconDex matched: " +
                    "${match.ownerClassName}.${match.ownerMethodName} icon=${match.resId} score=${match.score}",
            )
            return match.resId
        }

        val ownerPreview = ownerCandidates
            .take(5)
            .joinToString(",") { "${it.className}:${it.score}" }
        log(logger, "imageViewerShareIconDex: no drawable constant matched, owners=$ownerPreview")
        return null
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun findShareIconOwnerCandidates(
        candidates: List<String>,
        cl: ClassLoader,
    ): List<ShareIconOwnerCandidate> {
        return candidates.asSequence()
            .mapNotNull { className ->
                val cls = safeFindClass(className, cl) ?: return@mapNotNull null
                val score = scoreShareIconOwnerClass(cls)
                if (score > 0) ShareIconOwnerCandidate(className, score) else null
            }
            .sortedWith(
                compareByDescending<ShareIconOwnerCandidate> { it.score }
                    .thenBy { it.className.length }
                    .thenBy { it.className },
            )
            .take(16)
            .toList()
    }

    private fun scoreShareIconOwnerClass(cls: Class<*>): Int {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return 0
        if (!cls.name.startsWith("com.baidu.tieba.")) return 0

        val fields = runCatching { cls.declaredFields }.getOrNull() ?: return 0
        val constructors = runCatching { cls.declaredConstructors }.getOrNull() ?: return 0
        val methods = runCatching { cls.declaredMethods }.getOrNull() ?: return 0
        val nestedClasses = runCatching { cls.declaredClasses }.getOrNull().orEmpty()

        val hasImageViewField = fields.any { field ->
            ImageView::class.java.isAssignableFrom(field.type)
        }
        val hasHeadImageField = fields.any { field ->
            field.type.name.endsWith(".HeadImageView") ||
                field.type.name == "com.baidu.tbadk.core.view.HeadImageView"
        }
        val hasAnimatorField = fields.any { field ->
            field.type.name == "android.animation.ValueAnimator"
        }
        val hasImageViewCtor = constructors.any { ctor ->
            ctor.parameterTypes.size == 1 &&
                ImageView::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        val hasRunnableNested = nestedClasses.any { nested ->
            Runnable::class.java.isAssignableFrom(nested)
        }
        val boolFieldCount = fields.count { field ->
            field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java
        }
        val intFieldCount = fields.count { field ->
            field.type == Int::class.javaPrimitiveType || field.type == Int::class.java
        }
        val noArgBooleanMethods = methods.count { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
        }
        val noArgIntMethods = methods.count { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Int::class.javaPrimitiveType || method.returnType == Int::class.java)
        }

        if (!hasImageViewField || !hasAnimatorField || !hasImageViewCtor) return 0

        var score = 120
        if (hasHeadImageField) score += 24
        if (hasRunnableNested) score += 20
        score += boolFieldCount.coerceAtMost(3) * 6
        score += intFieldCount.coerceAtMost(2) * 5
        score += noArgBooleanMethods.coerceAtMost(2) * 4
        score += noArgIntMethods.coerceAtMost(1) * 4
        score -= methods.size / 6
        score -= fields.size / 4
        score -= cls.simpleName.length
        return if (score >= 120) score else 0
    }

    private object DexShareIconScanner {
        private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
        private const val TEXT_UTILS_DESCRIPTOR = "Landroid/text/TextUtils;"
        private const val NAVIGATION_BAR_DESCRIPTOR = "Lcom/baidu/tbadk/core/view/NavigationBar;"
        private const val PB_BAR_IMAGE_VIEW_DESCRIPTOR = "Lcom/baidu/tbadk/core/view/PbBarImageView;"
        private const val EM_TEXT_VIEW_DESCRIPTOR =
            "Lcom/baidu/tbadk/core/elementsMaven/view/EMTextView;"
        private const val EM_MANAGER_DESCRIPTOR = "Lcom/baidu/tbadk/core/elementsMaven/EMManager;"
        private const val WEBP_MANAGER_DESCRIPTOR = "Lcom/baidu/tbadk/core/util/WebPManager;"
        private const val WEBP_SET_PURE_DRAWABLE = "setPureDrawable"
        private const val R_DRAWABLE_DESCRIPTOR = "Lcom/baidu/tieba/R\$drawable;"
        private const val BIGDAY_SWIPE_REFRESH_DESCRIPTOR =
            "Lcom/baidu/tieba/homepage/personalize/bigday/BigdaySwipeRefreshLayout;"
        private const val SCROLL_FRAGMENT_TAB_CALLBACK_DESCRIPTOR =
            "Lcom/baidu/tieba/homepage/framework/indicator/ScrollFragmentTabHost\$z;"
        private const val AI_WRITE_HELPER_DESCRIPTOR = "Lcom/baidu/tieba/hs6;"
        private const val AI_SPRITE_MEME_PAN_DESCRIPTOR =
            "Lcom/baidu/tbadk/editortools/meme/pan/SpriteMemePan;"
        private const val IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR =
            "Lcom/baidu/tbadk/coreExtra/view/ImageJumpButtonLayout;"
        private const val ANDROID_VIEW_DESCRIPTOR = "Landroid/view/View;"
        private const val FRAME_LAYOUT_DESCRIPTOR = "Landroid/widget/FrameLayout;"
        private const val LINEAR_LAYOUT_DESCRIPTOR = "Landroid/widget/LinearLayout;"
        private const val VIEW_GROUP_DESCRIPTOR = "Landroid/view/ViewGroup;"
        private const val LINEAR_LAYOUT_LAYOUT_PARAMS_DESCRIPTOR =
            "Landroid/widget/LinearLayout\$LayoutParams;"
        private const val VIEW_MARGIN_LAYOUT_PARAMS_DESCRIPTOR =
            "Landroid/view/ViewGroup\$MarginLayoutParams;"
        private const val PB_AD_BID_ENDPOINT = "c/b/ad/adBid?cmd=309757&format=protobuf"
        private const val PB_COMMON_REQUEST_MODEL_DESCRIPTOR =
            "Lcom/baidu/tieba/pb/pb/main/newmodel/CommonRequestModel;"
        private const val PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR =
            "Lcom/baidu/tieba/pb/pagebrowser/model/BaseRequestModel;"
        private const val KOTLIN_CONTINUATION_DESCRIPTOR = "Lkotlin/coroutines/Continuation;"
        private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

        fun scan(
            sourcePaths: List<String>,
            ownerClassNames: List<String>,
            cl: ClassLoader,
            logger: ScanLogger? = null,
        ): DexShareIconMatch? {
            val ownerDescriptors = ownerClassNames.associateBy { "L${it.replace('.', '/')};" }
            var best: DexShareIconMatch? = null
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    for ((descriptor, className) in ownerDescriptors) {
                        try {
                            val match = reader.findShareIconMatch(descriptor, className, cl)
                            if (match != null && (best == null || match.score > best!!.score)) best = match
                        } catch (t: Throwable) {
                            recordError(t)
                        }
                    }
                }
            }
            recordDexScanIssue(logger, "ImageViewerNativeShareHook.IconDex", errorCount, firstError)
            return best
        }

        fun scanAutoRefresh(
            sourcePaths: List<String>,
            ownerClassName: String,
            logger: ScanLogger? = null,
        ): List<DexAutoRefreshMatch> {
            val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
            val out = ArrayList<DexAutoRefreshMatch>(4)
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    try {
                        out.addAll(reader.findAutoRefreshMatches(ownerDescriptor))
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
            recordDexScanIssue(logger, "AutoRefreshHook.Dex", errorCount, firstError)
            return out
        }

        fun scanAiWriteInit(
            sourcePaths: List<String>,
            ownerClassName: String,
            logger: ScanLogger? = null,
        ): List<DexAiComponentInitMatch> {
            val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
            val out = ArrayList<DexAiComponentInitMatch>(4)
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    try {
                        out.addAll(reader.findAiWriteInitMatches(ownerDescriptor))
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
            recordDexScanIssue(logger, "AiComponentDisableHook.AiWriteDex", errorCount, firstError)
            return out
        }

        fun scanSpriteMemeInit(
            sourcePaths: List<String>,
            ownerClassName: String,
            logger: ScanLogger? = null,
        ): List<DexAiComponentInitMatch> {
            val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
            val out = ArrayList<DexAiComponentInitMatch>(4)
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    try {
                        out.addAll(reader.findSpriteMemeInitMatches(ownerDescriptor))
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
            recordDexScanIssue(logger, "AiComponentDisableHook.SpriteMemeDex", errorCount, firstError)
            return out
        }

        fun scanImageViewerJumpButtonInit(
            sourcePaths: List<String>,
            ownerClassName: String,
            logger: ScanLogger? = null,
        ): List<DexAiComponentInitMatch> {
            val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
            val out = ArrayList<DexAiComponentInitMatch>(4)
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    try {
                        out.addAll(reader.findImageViewerJumpButtonInitMatches(ownerDescriptor))
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
            recordDexScanIssue(logger, "AiComponentDisableHook.ImageViewerJumpButtonDex", errorCount, firstError)
            return out
        }

        private fun recordDexScanIssue(
            logger: ScanLogger?,
            tag: String,
            errorCount: Int,
            firstError: String?,
        ) {
            if (errorCount <= 0) return
            val detail = "source errors=$errorCount" +
                (firstError?.let { ", firstException=$it" } ?: "")
            val errors = scanContext.get()?.scanErrors
            if (errors != null) {
                recordScanIssue(logger, tag, errors, detail)
            } else {
                log(logger, "$tag: $detail")
            }
        }

        fun scanEnterForumCapsule(
            sourcePaths: List<String>,
            ownerClassName: String,
            logger: ScanLogger? = null,
        ): List<DexEnterForumCapsuleMethodMatch> {
            return scanEnterForumCapsules(sourcePaths, listOf(ownerClassName), logger)[ownerClassName].orEmpty()
        }

        fun scanPbAdBid(
            sourcePaths: List<String>,
            logger: ScanLogger? = null,
        ): DexPbAdBidRawScan {
            val modelMatches = ArrayList<DexPbAdBidModelMatch>(4)
            val requestDataMethodNames = LinkedHashSet<String>()
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    try {
                        modelMatches.addAll(reader.findPbAdBidModelMatches())
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                    try {
                        reader.findPbAdBidPageBrowserRequestDataMethod()?.let(requestDataMethodNames::add)
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
            if (errorCount > 0) {
                log(
                    logger,
                    "pbAdBidDex: source errors=$errorCount" +
                        (firstError?.let { ", firstException=$it" } ?: ""),
                )
            }
            val requestDataMethodName = when (requestDataMethodNames.size) {
                0 -> null
                1 -> requestDataMethodNames.first()
                else -> {
                    log(
                        logger,
                        "pbAdBid.pageBrowserDex: requestData method ambiguous: " +
                            requestDataMethodNames.take(5).joinToString(","),
                    )
                    null
                }
            }
            return DexPbAdBidRawScan(
                modelMatches = modelMatches,
                pageBrowserRequestDataMethodName = requestDataMethodName,
            )
        }

        fun scanEnterForumCapsules(
            sourcePaths: List<String>,
            ownerClassNames: List<String>,
            logger: ScanLogger? = null,
        ): Map<String, List<DexEnterForumCapsuleMethodMatch>> {
            val owners = ownerClassNames
                .distinct()
                .associateBy { "L${it.replace('.', '/')};" }
            val out = LinkedHashMap<String, MutableList<DexEnterForumCapsuleMethodMatch>>()
            var errorCount = 0
            var firstError: String? = null
            fun recordError(t: Throwable) {
                errorCount++
                if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
            }
            for (sourcePath in sourcePaths) {
                val file = File(sourcePath)
                if (!file.isFile) continue
                scanDexFiles(file, ::recordError) { dexBytes ->
                    val reader = try {
                        DexImage(dexBytes)
                    } catch (t: Throwable) {
                        recordError(t)
                        return@scanDexFiles
                    }
                    for ((descriptor, className) in owners) {
                        val matches = reader.findEnterForumCapsuleMatches(descriptor)
                        if (matches.isNotEmpty()) {
                            out.getOrPut(className) { ArrayList(4) }.addAll(matches)
                        }
                    }
                }
            }
            if (errorCount > 0) {
                log(
                    logger,
                    "homeNativeGlassEnterForumCapsuleDex: source errors=$errorCount" +
                        (firstError?.let { ", firstException=$it" } ?: ""),
                )
            }
            return out
        }

        private fun scanDexFiles(
            file: File,
            onError: (Throwable) -> Unit = {},
            block: (ByteArray) -> Unit,
        ) {
            if (file.extension.equals("dex", ignoreCase = true)) {
                runCatching { block(file.readBytes()) }.onFailure(onError)
                return
            }
            runCatching {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!name.startsWith("classes") || !name.endsWith(".dex")) continue
                        zip.getInputStream(entry).use { input ->
                            block(input.readBytes())
                        }
                    }
                }
            }.onFailure(onError)
        }

        private class DexImage(private val bytes: ByteArray) {
            private val stringIdsSize = intAt(56)
            private val stringIdsOff = intAt(60)
            private val typeIdsSize = intAt(64)
            private val typeIdsOff = intAt(68)
            private val protoIdsSize = intAt(72)
            private val protoIdsOff = intAt(76)
            private val fieldIdsSize = intAt(80)
            private val fieldIdsOff = intAt(84)
            private val methodIdsSize = intAt(88)
            private val methodIdsOff = intAt(92)
            private val classDefsSize = intAt(96)
            private val classDefsOff = intAt(100)
            private val stringCache = HashMap<Int, String?>()
            private val typeCache = HashMap<Int, String?>()
            private val classSuperCache = HashMap<String, String?>()
            private val aiWriteListenerEvidenceCache = HashMap<String, Boolean>()

            fun findAutoRefreshMatches(ownerDescriptor: String): List<DexAutoRefreshMatch> {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return emptyList()
                    return scanAutoRefreshClassData(classDataOff)
                }
                return emptyList()
            }

            fun findAiWriteInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return emptyList()
                    return scanAiWriteInitClassData(classDataOff)
                }
                return emptyList()
            }

            fun findSpriteMemeInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return emptyList()
                    return scanSpriteMemeInitClassData(classDataOff)
                }
                return emptyList()
            }

            fun findImageViewerJumpButtonInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return emptyList()
                    return scanImageViewerJumpButtonInitClassData(classDataOff)
                }
                return emptyList()
            }

            fun findEnterForumCapsuleMatches(ownerDescriptor: String): List<DexEnterForumCapsuleMethodMatch> {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return emptyList()
                    return scanEnterForumCapsuleClassData(classDataOff, ownerDescriptor)
                }
                return emptyList()
            }

            fun findPbAdBidModelMatches(): List<DexPbAdBidModelMatch> {
                val out = ArrayList<DexPbAdBidModelMatch>(4)
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val descriptor = typeDescriptor(intAt(classDefOff)) ?: continue
                    if (!descriptor.startsWith("Lcom/baidu/tieba/")) continue
                    val kind = when {
                        extendsDescriptor(descriptor, PB_COMMON_REQUEST_MODEL_DESCRIPTOR) ->
                            PB_AD_BID_DEX_KIND_COMMON
                        extendsDescriptor(descriptor, PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR) ->
                            PB_AD_BID_DEX_KIND_PAGE_BROWSER
                        else -> null
                    } ?: continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) continue
                    out.addAll(scanPbAdBidModelClassData(classDataOff, descriptor, kind))
                }
                return out
            }

            fun findPbAdBidPageBrowserRequestDataMethod(): String? {
                val classDataOff = classDataOffset(PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR) ?: return null
                val names = scanMethodNamesByProto(
                    classDataOff = classDataOff,
                    returnDescriptor = OBJECT_DESCRIPTOR,
                    parameterDescriptors = listOf(KOTLIN_CONTINUATION_DESCRIPTOR),
                )
                return names.singleOrNull()
            }

            private fun classDataOffset(descriptor: String): Int? {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != descriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    return classDataOff.takeIf { it != 0 }
                }
                return null
            }

            private fun scanPbAdBidModelClassData(
                classDataOff: Int,
                ownerDescriptor: String,
                kind: String,
            ): List<DexPbAdBidModelMatch> {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                val out = ArrayList<DexPbAdBidModelMatch>(2)
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanPbAdBidModelCodeItem(method, ownerDescriptor, kind)?.let(out::add)
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanPbAdBidModelCodeItem(method, ownerDescriptor, kind)?.let(out::add)
                }
                return out
            }

            private fun scanMethodNamesByProto(
                classDataOff: Int,
                returnDescriptor: String,
                parameterDescriptors: List<String>,
            ): List<String> {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                val out = ArrayList<String>(2)
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    if (!isStaticAccess(method.accessFlags) &&
                        methodReturnDescriptor(methodIdx) == returnDescriptor &&
                        methodParameterDescriptors(methodIdx) == parameterDescriptors
                    ) {
                        methodName(methodIdx)?.takeIf { it.isNotBlank() }?.let(out::add)
                    }
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    if (!isStaticAccess(method.accessFlags) &&
                        methodReturnDescriptor(methodIdx) == returnDescriptor &&
                        methodParameterDescriptors(methodIdx) == parameterDescriptors
                    ) {
                        methodName(methodIdx)?.takeIf { it.isNotBlank() }?.let(out::add)
                    }
                }
                return out.distinct()
            }

            private fun hasAiWriteClickListenerEvidence(listenerDescriptor: String): Boolean {
                aiWriteListenerEvidenceCache[listenerDescriptor]?.let { return it }
                val classDataOff = classDataOffset(listenerDescriptor) ?: run {
                    aiWriteListenerEvidenceCache[listenerDescriptor] = false
                    return false
                }
                val matched = scanAiWriteListenerClassData(classDataOff)
                aiWriteListenerEvidenceCache[listenerDescriptor] = matched
                return matched
            }

            fun findShareIconMatch(
                ownerDescriptor: String,
                ownerClassName: String,
                cl: ClassLoader,
            ): DexShareIconMatch? {
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    val classIdx = intAt(classDefOff)
                    if (typeDescriptor(classIdx) != ownerDescriptor) continue
                    val classDataOff = intAt(classDefOff + 24)
                    if (classDataOff == 0) return null
                    return scanClassData(classDataOff, ownerClassName, cl)
                }
                return null
            }

            private fun scanAutoRefreshClassData(classDataOff: Int): List<DexAutoRefreshMatch> {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                val out = ArrayList<DexAutoRefreshMatch>(4)
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanAutoRefreshCodeItem(method.codeOff, methodName(methodIdx))?.let(out::add)
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanAutoRefreshCodeItem(method.codeOff, methodName(methodIdx))?.let(out::add)
                }
                return out
            }

            private fun scanAiWriteInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
                return scanAiComponentInitClassData(classDataOff, ::scanAiWriteInitCodeItem)
            }

            private fun scanSpriteMemeInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
                return scanAiComponentInitClassData(classDataOff, ::scanSpriteMemeInitCodeItem)
            }

            private fun scanImageViewerJumpButtonInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
                return scanAiComponentInitClassData(classDataOff, ::scanImageViewerJumpButtonInitCodeItem)
            }

            private fun scanEnterForumCapsuleClassData(
                classDataOff: Int,
                ownerDescriptor: String,
            ): List<DexEnterForumCapsuleMethodMatch> {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                val out = ArrayList<DexEnterForumCapsuleMethodMatch>(4)
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    out.addAll(
                        scanEnterForumCapsuleCodeItem(
                            method.codeOff,
                            methodName(methodIdx),
                            ownerDescriptor,
                        ),
                    )
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    out.addAll(
                        scanEnterForumCapsuleCodeItem(
                            method.codeOff,
                            methodName(methodIdx),
                            ownerDescriptor,
                        ),
                    )
                }
                return out
            }

            private fun scanAiWriteListenerClassData(classDataOff: Int): Boolean {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    if (scanAiWriteListenerCodeItem(method.codeOff, methodName(methodIdx))) return true
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    if (scanAiWriteListenerCodeItem(method.codeOff, methodName(methodIdx))) return true
                }
                return false
            }

            private fun scanAiComponentInitClassData(
                classDataOff: Int,
                scanner: (Int, String?) -> DexAiComponentInitMatch?,
            ): List<DexAiComponentInitMatch> {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                val out = ArrayList<DexAiComponentInitMatch>(4)
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanner(method.codeOff, methodName(methodIdx))?.let(out::add)
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    scanner(method.codeOff, methodName(methodIdx))?.let(out::add)
                }
                return out
            }

            private fun scanClassData(
                classDataOff: Int,
                ownerClassName: String,
                cl: ClassLoader,
            ): DexShareIconMatch? {
                var cursor = classDataOff
                val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
                val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
                val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

                repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
                repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

                var best: DexShareIconMatch? = null
                var methodIdx = 0
                repeat(directMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    val match = scanCodeItem(method.codeOff, ownerClassName, methodName(methodIdx), cl)
                    if (match != null && (best == null || match.score > best.score)) best = match
                }

                methodIdx = 0
                repeat(virtualMethodsSize) {
                    val method = readEncodedMethod(cursor, methodIdx)
                    cursor = method.next
                    methodIdx = method.methodIdx
                    val match = scanCodeItem(method.codeOff, ownerClassName, methodName(methodIdx), cl)
                    if (match != null && (best == null || match.score > best.score)) best = match
                }
                return best
            }

            private fun scanPbAdBidModelCodeItem(
                method: EncodedMethod,
                ownerDescriptor: String,
                kind: String,
            ): DexPbAdBidModelMatch? {
                if (isStaticAccess(method.accessFlags)) return null
                if (!codeContainsString(method.codeOff, PB_AD_BID_ENDPOINT)) return null
                val ownerClassName = descriptorToClassName(ownerDescriptor) ?: return null
                val methodName = methodName(method.methodIdx)?.takeIf { it.isNotBlank() } ?: return null
                var score = 260
                val evidence = ArrayList<String>(4)
                evidence.add("endpoint")
                when (kind) {
                    PB_AD_BID_DEX_KIND_COMMON -> {
                        score += 40
                        evidence.add("commonBase")
                    }
                    PB_AD_BID_DEX_KIND_PAGE_BROWSER -> {
                        score += 40
                        evidence.add("pageBrowserBase")
                    }
                }
                if (methodReturnDescriptor(method.methodIdx)?.endsWith("/u3b;") == true) {
                    score += 25
                    evidence.add("requestReturn")
                }
                if (methodParameterDescriptors(method.methodIdx).isEmpty()) {
                    score += 10
                    evidence.add("noArgs")
                }
                return DexPbAdBidModelMatch(
                    className = ownerClassName,
                    requestImplMethodName = methodName,
                    kind = kind,
                    score = score,
                    evidence = evidence.joinToString(","),
                )
            }

            private fun codeContainsString(codeOff: Int, expected: String): Boolean {
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return false
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return false
                for (i in 0 until insnsSize) {
                    val offset = insnsOff + i * 2
                    val unit = ushortAt(offset)
                    when (unit and 0xFF) {
                        0x1A -> {
                            val value = stringAt(ushortAt(offset + 2)).orEmpty()
                            if (value == expected) return true
                        }
                        0x1B -> {
                            val value = stringAt(intAt(offset + 2)).orEmpty()
                            if (value == expected) return true
                        }
                    }
                }
                return false
            }

            private fun skipEncodedField(offset: Int): Int {
                var cursor = offset
                cursor = readUleb128(cursor).next
                cursor = readUleb128(cursor).next
                return cursor
            }

            private fun readEncodedMethod(offset: Int, previousMethodIdx: Int): EncodedMethod {
                var cursor = offset
                val methodIdxDiff = readUleb128(cursor).also { cursor = it.next }.value
                val accessFlags = readUleb128(cursor).also { cursor = it.next }.value
                val codeOff = readUleb128(cursor).also { cursor = it.next }.value
                return EncodedMethod(previousMethodIdx + methodIdxDiff, accessFlags, codeOff, cursor)
            }

            private fun scanAutoRefreshCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
            ): DexAutoRefreshMatch? {
                val methodName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

                var hasSelectionTop = false
                var hasSelectionCall = false
                var hasSetRefreshing = false
                var hasSetRefreshingTrue = false
                var hasSwipeRefreshInvoke = false
                var hasScrollTabNotify = false
                for (i in 0 until insnsSize) {
                    val unit = ushortAt(insnsOff + i * 2)
                    val op = unit and 0xFF
                    val is35cInvoke = op in 0x6E..0x72
                    val isRangeInvoke = op in 0x74..0x78
                    if (!is35cInvoke && !isRangeInvoke) continue

                    val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                    val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)

                    if (isListSetSelection(methodIdx)) {
                        hasSelectionCall = true
                        val selectionArgReg = invokeRegs.getOrNull(1)
                        if (selectionArgReg != null && findIntConstBefore(insnsOff, i, selectionArgReg) == 0) {
                            hasSelectionTop = true
                        }
                    }

                    if (isSwipeRefreshInvoke(methodIdx)) {
                        hasSwipeRefreshInvoke = true
                    }

                    if (isSwipeRefreshSetRefreshing(methodIdx)) {
                        hasSetRefreshing = true
                        val refreshingArgReg = invokeRegs.getOrNull(1)
                        if (refreshingArgReg != null && findBooleanConstBefore(insnsOff, i, refreshingArgReg) == true) {
                            hasSetRefreshingTrue = true
                        }
                    }

                    if (isScrollTabNotify(methodIdx)) {
                        hasScrollTabNotify = true
                    }
                }

                val hasStrongRefreshEvidence = hasSetRefreshingTrue ||
                    (hasSelectionTop && (hasSetRefreshing || hasSwipeRefreshInvoke || hasScrollTabNotify))
                if (!hasStrongRefreshEvidence) return null

                var score = 0
                val evidence = ArrayList<String>(6)
                if (hasSelectionTop) {
                    score += 130
                    evidence.add("selection0")
                } else if (hasSelectionCall) {
                    score += 35
                    evidence.add("selection")
                }
                if (hasSetRefreshingTrue) {
                    score += 170
                    evidence.add("setRefreshingTrue")
                } else if (hasSetRefreshing) {
                    score += 70
                    evidence.add("setRefreshing")
                }
                if (hasSwipeRefreshInvoke) {
                    score += 35
                    evidence.add("swipeRefresh")
                }
                if (hasScrollTabNotify) {
                    score += 50
                    evidence.add("scrollTabNotify")
                }
                if (methodName == "w1") score += 30
                if (methodName.length <= 3) score += 8
                if (methodName.endsWith("1")) score += 6
                if (score < 150) return null
                return DexAutoRefreshMatch(methodName, score, evidence.joinToString(","))
            }

            private fun scanEnterForumCapsuleCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
                ownerDescriptor: String,
            ): List<DexEnterForumCapsuleMethodMatch> {
                val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return emptyList()
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return emptyList()
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return emptyList()

                var hasAddCustomView = false
                var hasFindViewById = false
                var hasSetOnClickListener = false
                var hasTextUtilsIsEmpty = false
                var hasEmManagerFrom = false
                var hasEmBackground = false
                var hasSetBackgroundResource = false
                var hasSetVisibility = false
                var hasSetVisibilityVisible = false
                val putViewFields = LinkedHashMap<String, Int>()
                val getViewFields = LinkedHashMap<String, Int>()
                val putPbBarFields = LinkedHashMap<String, Int>()
                val putEmTextFields = LinkedHashMap<String, Int>()
                val getStringFields = LinkedHashMap<String, Int>()

                fun count(map: MutableMap<String, Int>, key: String) {
                    map[key] = (map[key] ?: 0) + 1
                }

                for (i in 0 until insnsSize) {
                    val offset = insnsOff + i * 2
                    val unit = ushortAt(offset)
                    val op = unit and 0xFF
                    if (op in 0x52..0x5F) {
                        val fieldIdx = ushortAt(offset + 2)
                        if (fieldClassDescriptor(fieldIdx) == ownerDescriptor) {
                            val fieldName = fieldName(fieldIdx).orEmpty()
                            val type = fieldTypeDescriptor(fieldIdx).orEmpty()
                            if (fieldName.isNotBlank()) {
                                if (op == 0x54) {
                                    when {
                                        isViewLikeDescriptor(type) -> count(getViewFields, fieldName)
                                        type == STRING_DESCRIPTOR -> count(getStringFields, fieldName)
                                    }
                                } else if (op == 0x5B) {
                                    when {
                                        type == PB_BAR_IMAGE_VIEW_DESCRIPTOR -> count(putPbBarFields, fieldName)
                                        type == EM_TEXT_VIEW_DESCRIPTOR -> count(putEmTextFields, fieldName)
                                        isViewLikeDescriptor(type) -> count(putViewFields, fieldName)
                                    }
                                }
                            }
                        }
                        continue
                    }

                    val is35cInvoke = op in 0x6E..0x72
                    val isRangeInvoke = op in 0x74..0x78
                    if (!is35cInvoke && !isRangeInvoke) continue
                    val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                    when {
                        isNavigationBarAddCustomView(methodIdx) -> hasAddCustomView = true
                        isViewFindViewById(methodIdx) -> hasFindViewById = true
                        isViewSetOnClickListener(methodIdx) -> hasSetOnClickListener = true
                        isTextUtilsIsEmpty(methodIdx) -> hasTextUtilsIsEmpty = true
                        isEmManagerFrom(methodIdx) -> hasEmManagerFrom = true
                        isEmManagerBackgroundMethod(methodIdx) -> hasEmBackground = true
                        isViewSetBackgroundResource(methodIdx) -> hasSetBackgroundResource = true
                        isViewSetVisibility(methodIdx) -> {
                            hasSetVisibility = true
                            val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                            val visibilityReg = invokeRegs.getOrNull(1)
                            if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.VISIBLE) {
                                hasSetVisibilityVisible = true
                            }
                        }
                    }
                }

                val out = ArrayList<DexEnterForumCapsuleMethodMatch>(2)
                val initViewField = bestDexFieldName(putViewFields)
                val hasInitEvidence = initViewField != null &&
                    hasAddCustomView &&
                    hasFindViewById &&
                    (hasSetOnClickListener || putPbBarFields.isNotEmpty() || putEmTextFields.isNotEmpty())
                if (hasInitEvidence) {
                    var score = 0
                    val evidence = ArrayList<String>(8)
                    score += 120
                    evidence.add("addCustomView")
                    score += 55
                    evidence.add("findViewById")
                    score += 70
                    evidence.add("viewField=$initViewField")
                    if (hasSetOnClickListener) {
                        score += 40
                        evidence.add("click")
                    }
                    if (putPbBarFields.isNotEmpty()) {
                        score += 35
                        evidence.add("pbBar")
                    }
                    if (putEmTextFields.isNotEmpty()) {
                        score += 35
                        evidence.add("emText")
                    }
                    if (ownerName == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_INIT_METHOD) score += 16
                    if (ownerName.length <= 2) score += 8
                    if (score >= 260) {
                        out.add(
                            DexEnterForumCapsuleMethodMatch(
                                ownerMethodName = ownerName,
                                kind = DexEnterForumCapsuleMethodKind.INIT,
                                score = score,
                                evidence = evidence.joinToString(","),
                                viewFieldName = initViewField,
                            ),
                        )
                    }
                }

                val refreshViewField = bestDexFieldName(getViewFields)
                val refreshTitleField = bestDexFieldName(getStringFields)
                val hasRefreshBackground = hasEmBackground || hasSetBackgroundResource
                val hasRefreshEvidence = refreshViewField != null &&
                    refreshTitleField != null &&
                    (hasTextUtilsIsEmpty || hasSetVisibility) &&
                    (hasRefreshBackground || hasEmManagerFrom)
                if (hasRefreshEvidence) {
                    var score = 0
                    val evidence = ArrayList<String>(8)
                    score += 70
                    evidence.add("viewField=$refreshViewField")
                    score += 65
                    evidence.add("titleField=$refreshTitleField")
                    if (hasTextUtilsIsEmpty) {
                        score += 70
                        evidence.add("isEmpty")
                    }
                    if (hasEmBackground) {
                        score += 85
                        evidence.add("emBackground")
                    } else if (hasEmManagerFrom) {
                        score += 35
                        evidence.add("emManager")
                    }
                    if (hasSetBackgroundResource) {
                        score += 45
                        evidence.add("clearBackground")
                    }
                    if (hasSetVisibilityVisible) {
                        score += 20
                        evidence.add("visible")
                    } else if (hasSetVisibility) {
                        score += 10
                        evidence.add("visibility")
                    }
                    if (ownerName == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_REFRESH_METHOD) score += 16
                    if (ownerName.length <= 2) score += 8
                    if (score >= 235) {
                        out.add(
                            DexEnterForumCapsuleMethodMatch(
                                ownerMethodName = ownerName,
                                kind = DexEnterForumCapsuleMethodKind.REFRESH,
                                score = score,
                                evidence = evidence.joinToString(","),
                                viewFieldName = refreshViewField,
                                titleFieldName = refreshTitleField,
                            ),
                        )
                    }
                }
                return out
            }

            private fun scanAiWriteListenerCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
            ): Boolean {
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return false
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return false

                var hasAiWriteReply = false
                var hasAiCapacityHelper = false
                for (i in 0 until insnsSize) {
                    val offset = insnsOff + i * 2
                    val unit = ushortAt(offset)
                    val op = unit and 0xFF
                    when {
                        op == 0x1A -> {
                            val value = stringAt(ushortAt(offset + 2)).orEmpty()
                            if (isAiWriteReplyString(value)) hasAiWriteReply = true
                        }
                        op == 0x1B -> {
                            val value = stringAt(intAt(offset + 2)).orEmpty()
                            if (isAiWriteReplyString(value)) hasAiWriteReply = true
                        }
                        op in 0x52..0x6D -> {
                            val fieldIdx = ushortAt(offset + 2)
                            val name = fieldName(fieldIdx).orEmpty()
                            if (isAiWriteReplyString(name)) hasAiWriteReply = true
                            if (
                                isAiCapacityDescriptor(fieldClassDescriptor(fieldIdx).orEmpty()) ||
                                isAiCapacityDescriptor(fieldTypeDescriptor(fieldIdx).orEmpty())
                            ) {
                                hasAiCapacityHelper = true
                            }
                        }
                        op in 0x6E..0x72 || op in 0x74..0x78 -> {
                            val methodIdx = ushortAt(offset + 2)
                            if (isAiCapacityDescriptor(methodClassDescriptor(methodIdx).orEmpty())) {
                                hasAiCapacityHelper = true
                            }
                        }
                    }
                    if (hasAiWriteReply || (hasAiCapacityHelper && ownerMethodName == "onClick")) return true
                }
                return false
            }

            private fun scanAiWriteInitCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
            ): DexAiComponentInitMatch? {
                val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

                var hasHelperFactory = false
                var hasFrameLayoutCtor = false
                var hasFrameLayoutNewInstance = false
                var hasFrameLayoutFieldPut = false
                var hasViewLikeNewInstance = false
                var hasViewLikeFieldPut = false
                var hasSetOnClickListener = false
                var hasSetVisibility = false
                var hasSetVisibilityGone = false
                var hasLayoutParams = false
                var hasMargins = false
                val listenerDescriptors = linkedSetOf<String>()

                for (i in 0 until insnsSize) {
                    val unit = ushortAt(insnsOff + i * 2)
                    val op = unit and 0xFF
                    if (op == 0x22) {
                        val descriptor = typeDescriptor(ushortAt(insnsOff + (i + 1) * 2)).orEmpty()
                        if (descriptor == FRAME_LAYOUT_DESCRIPTOR) {
                            hasFrameLayoutNewInstance = true
                        }
                        if (isViewLikeDescriptor(descriptor)) {
                            hasViewLikeNewInstance = true
                        } else if (isAppClassDescriptor(descriptor)) {
                            listenerDescriptors.add(descriptor)
                        }
                        continue
                    }
                    if (op == 0x5B) {
                        val fieldIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (isFrameLayoutField(fieldIdx)) {
                            hasFrameLayoutFieldPut = true
                        }
                        if (isViewLikeField(fieldIdx)) {
                            hasViewLikeFieldPut = true
                        }
                        continue
                    }

                    val is35cInvoke = op in 0x6E..0x72
                    val isRangeInvoke = op in 0x74..0x78
                    if (!is35cInvoke && !isRangeInvoke) continue

                    val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                    if (isAiWriteHelperFactory(methodIdx)) {
                        hasHelperFactory = true
                    }
                    if (isFrameLayoutConstructor(methodIdx)) {
                        hasFrameLayoutCtor = true
                    }
                    if (isViewSetOnClickListener(methodIdx)) {
                        hasSetOnClickListener = true
                    }
                    if (isViewGetLayoutParams(methodIdx)) {
                        hasLayoutParams = true
                    }
                    if (isViewMarginSetMargins(methodIdx)) {
                        hasMargins = true
                    }
                    if (isViewSetVisibility(methodIdx)) {
                        hasSetVisibility = true
                        val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                        val visibilityReg = invokeRegs.getOrNull(1)
                        if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.GONE) {
                            hasSetVisibilityGone = true
                        }
                    }
                }

                var score = 0
                val evidence = ArrayList<String>(12)
                val hasAiWriteClickListener = listenerDescriptors.any(::hasAiWriteClickListenerEvidence)
                if (hasAiWriteClickListener) {
                    score += 170
                    evidence.add("aiClickListener")
                }
                if (hasHelperFactory) {
                    score += 150
                    evidence.add("hs6Factory")
                }
                if (hasFrameLayoutFieldPut) {
                    score += 85
                    evidence.add("frameField")
                }
                if (hasFrameLayoutCtor) {
                    score += 45
                    evidence.add("frameCtor")
                }
                if (hasFrameLayoutNewInstance) {
                    score += 45
                    evidence.add("frameNew")
                }
                if (!hasFrameLayoutFieldPut && hasViewLikeFieldPut) {
                    score += 35
                    evidence.add("viewField")
                }
                if (!hasFrameLayoutCtor && !hasFrameLayoutNewInstance && hasViewLikeNewInstance) {
                    score += 25
                    evidence.add("viewNew")
                }
                if (hasSetOnClickListener) {
                    score += 80
                    evidence.add("click")
                }
                if (hasSetVisibilityGone) {
                    score += 90
                    evidence.add("visibilityGone")
                } else if (hasSetVisibility) {
                    score += 40
                    evidence.add("visibility")
                }
                if (hasLayoutParams) {
                    score += 20
                    evidence.add("layoutParams")
                }
                if (hasMargins) {
                    score += 25
                    evidence.add("margins")
                }
                if (ownerName == AI_PB_NEW_INPUT_INIT_AI_WRITE_METHOD) score += 20
                if (ownerName.length <= 3) score += 8
                val hasViewSlotEvidence = hasHelperFactory ||
                    hasFrameLayoutFieldPut ||
                    hasFrameLayoutCtor ||
                    hasFrameLayoutNewInstance ||
                    (hasViewLikeFieldPut && (hasLayoutParams || hasMargins || hasAiWriteClickListener)) ||
                    (hasLayoutParams && hasMargins)
                val hasUiPlacementEvidence = hasSetVisibilityGone ||
                    hasSetVisibility ||
                    hasLayoutParams ||
                    hasMargins ||
                    hasAiWriteClickListener
                val hasStrongEvidence = hasSetOnClickListener &&
                    hasUiPlacementEvidence &&
                    hasViewSlotEvidence &&
                    (hasAiWriteClickListener || hasHelperFactory || hasFrameLayoutFieldPut || hasFrameLayoutCtor || hasFrameLayoutNewInstance)
                if (!hasStrongEvidence && score < 90) return null
                if (hasStrongEvidence && score < 170) return null
                return DexAiComponentInitMatch(
                    ownerMethodName = ownerName,
                    score = score,
                    evidence = evidence.joinToString(","),
                    strong = hasStrongEvidence,
                )
            }

            private fun scanSpriteMemeInitCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
            ): DexAiComponentInitMatch? {
                val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

                var hasSpriteMemeCtor = false
                var hasListPanGetter = false
                var hasHintPanGetter = false
                var hasSetLayoutParams = false
                var hasSetPbEventCallBack = false

                for (i in 0 until insnsSize) {
                    val unit = ushortAt(insnsOff + i * 2)
                    val op = unit and 0xFF
                    if (op !in 0x6E..0x72 && op !in 0x74..0x78) continue
                    val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                    if (isSpriteMemePanConstructor(methodIdx)) {
                        hasSpriteMemeCtor = true
                    }
                    if (isSpriteMemePanMethod(methodIdx, "getSpriteMemeListPan")) {
                        hasListPanGetter = true
                    }
                    if (isSpriteMemePanMethod(methodIdx, "getSpriteMemeHintPan")) {
                        hasHintPanGetter = true
                    }
                    if (isViewSetLayoutParams(methodIdx)) {
                        hasSetLayoutParams = true
                    }
                    if (methodName(methodIdx) == "setPbEventCallBack") {
                        hasSetPbEventCallBack = true
                    }
                }

                val hasStrongEvidence = hasSpriteMemeCtor && (hasListPanGetter || hasHintPanGetter)
                if (!hasStrongEvidence) return null

                var score = 0
                val evidence = ArrayList<String>(5)
                if (hasSpriteMemeCtor) {
                    score += 150
                    evidence.add("spriteCtor")
                }
                if (hasListPanGetter) {
                    score += 60
                    evidence.add("listPan")
                }
                if (hasHintPanGetter) {
                    score += 60
                    evidence.add("hintPan")
                }
                if (hasSetLayoutParams) {
                    score += 25
                    evidence.add("layoutParams")
                }
                if (hasSetPbEventCallBack) {
                    score += 35
                    evidence.add("callback")
                }
                if (ownerName == AI_PB_NEW_INPUT_INIT_SPRITE_MEME_METHOD) score += 20
                if (ownerName.length <= 3) score += 8
                if (score < 190) return null
                return DexAiComponentInitMatch(ownerName, score, evidence.joinToString(","))
            }

            private fun scanImageViewerJumpButtonInitCodeItem(
                codeOff: Int,
                ownerMethodName: String?,
            ): DexAiComponentInitMatch? {
                val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

                var hasLayoutNewInstance = false
                var hasLayoutCtor = false
                var hasLayoutFieldPut = false
                var hasVisibilityGone = false
                var hasVisibilityCall = false
                var hasLinearLayoutParams = false
                var hasAddView = false

                for (i in 0 until insnsSize) {
                    val unit = ushortAt(insnsOff + i * 2)
                    val op = unit and 0xFF
                    if (op == 0x22) {
                        val descriptor = typeDescriptor(ushortAt(insnsOff + (i + 1) * 2)).orEmpty()
                        if (descriptor == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR) {
                            hasLayoutNewInstance = true
                        }
                        continue
                    }
                    if (op == 0x5B) {
                        val fieldIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (fieldTypeDescriptor(fieldIdx) == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR) {
                            hasLayoutFieldPut = true
                        }
                        continue
                    }

                    val is35cInvoke = op in 0x6E..0x72
                    val isRangeInvoke = op in 0x74..0x78
                    if (!is35cInvoke && !isRangeInvoke) continue

                    val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                    if (isImageJumpButtonLayoutConstructor(methodIdx)) {
                        hasLayoutCtor = true
                    }
                    if (isLinearLayoutLayoutParamsConstructor(methodIdx)) {
                        hasLinearLayoutParams = true
                    }
                    if (isViewGroupAddView(methodIdx)) {
                        hasAddView = true
                    }
                    if (isViewSetVisibility(methodIdx)) {
                        hasVisibilityCall = true
                        val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                        val visibilityReg = invokeRegs.getOrNull(1)
                        if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.GONE) {
                            hasVisibilityGone = true
                        }
                    }
                }

                val hasStrongEvidence = (hasLayoutCtor || hasLayoutNewInstance) &&
                    hasLayoutFieldPut &&
                    (hasAddView || hasVisibilityGone)
                if (!hasStrongEvidence) return null

                var score = 0
                val evidence = ArrayList<String>(7)
                if (hasLayoutCtor) {
                    score += 145
                    evidence.add("jumpCtor")
                }
                if (hasLayoutNewInstance) {
                    score += 90
                    evidence.add("jumpNew")
                }
                if (hasLayoutFieldPut) {
                    score += 95
                    evidence.add("jumpField")
                }
                if (hasAddView) {
                    score += 65
                    evidence.add("addView")
                }
                if (hasVisibilityGone) {
                    score += 45
                    evidence.add("visibilityGone")
                } else if (hasVisibilityCall) {
                    score += 20
                    evidence.add("visibility")
                }
                if (hasLinearLayoutParams) {
                    score += 25
                    evidence.add("linearParams")
                }
                if (ownerName == "r") score += 18
                if (ownerName.length <= 2) score += 8
                if (score < 280) return null
                return DexAiComponentInitMatch(ownerName, score, evidence.joinToString(","))
            }

            private fun scanCodeItem(
                codeOff: Int,
                ownerClassName: String,
                ownerMethodName: String?,
                cl: ClassLoader,
            ): DexShareIconMatch? {
                if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
                val insnsSize = intAt(codeOff + 12)
                val insnsOff = codeOff + 16
                if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

                var best: DexShareIconMatch? = null
                for (i in 0 until insnsSize) {
                    val unit = ushortAt(insnsOff + i * 2)
                    when (unit and 0xFF) {
                        0x71 -> {
                            val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                            if (!isWebPSetPureDrawable(methodIdx)) continue
                            val regs = invoke35cRegisters(insnsOff, i)
                            if (regs.size < 2) continue
                            val resId = findDrawableValueBefore(insnsOff, i, regs[1], cl) ?: continue
                            val match = DexShareIconMatch(
                                ownerClassName = ownerClassName,
                                ownerMethodName = ownerMethodName.orEmpty(),
                                resId = resId,
                                score = 240 + i.coerceAtMost(40),
                            )
                            if (best == null || match.score > best.score) best = match
                        }
                        0x77 -> {
                            val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                            if (!isWebPSetPureDrawable(methodIdx)) continue
                            val argCount = (unit ushr 8) and 0xFF
                            if (argCount < 2) continue
                            val firstReg = ushortAt(insnsOff + (i + 2) * 2)
                            val resId = findDrawableValueBefore(insnsOff, i, firstReg + 1, cl) ?: continue
                            val match = DexShareIconMatch(
                                ownerClassName = ownerClassName,
                                ownerMethodName = ownerMethodName.orEmpty(),
                                resId = resId,
                                score = 230 + i.coerceAtMost(40),
                            )
                            if (best == null || match.score > best.score) best = match
                        }
                    }
                }
                return best
            }

            private fun findBooleanConstBefore(
                insnsOff: Int,
                invokeIndex: Int,
                targetReg: Int,
            ): Boolean? {
                return findIntConstBefore(insnsOff, invokeIndex, targetReg)?.let { it != 0 }
            }

            private fun findIntConstBefore(
                insnsOff: Int,
                invokeIndex: Int,
                targetReg: Int,
            ): Int? {
                val start = (invokeIndex - 32).coerceAtLeast(0)
                for (i in invokeIndex - 1 downTo start) {
                    val offset = insnsOff + i * 2
                    val unit = ushortAt(offset)
                    val op = unit and 0xFF
                    when (op) {
                        0x12 -> {
                            val dest = (unit ushr 8) and 0x0F
                            if (dest != targetReg) continue
                            val literal = signExtend4((unit ushr 12) and 0x0F)
                            return literal
                        }
                        0x13 -> {
                            val dest = (unit ushr 8) and 0xFF
                            if (dest != targetReg) continue
                            return signedShortAt(offset + 2)
                        }
                        0x14 -> {
                            val dest = (unit ushr 8) and 0xFF
                            if (dest != targetReg) continue
                            return intAt(offset + 2)
                        }
                        0x15 -> {
                            val dest = (unit ushr 8) and 0xFF
                            if (dest != targetReg) continue
                            return signedShortAt(offset + 2) shl 16
                        }
                    }
                }
                return null
            }

            private fun signExtend4(value: Int): Int {
                return if ((value and 0x8) != 0) value or -0x10 else value
            }

            private fun invokeRegisters(
                insnsOff: Int,
                index: Int,
                firstUnit: Int,
                is35cInvoke: Boolean,
            ): List<Int> {
                if (is35cInvoke) return invoke35cRegisters(insnsOff, index)
                val argCount = (firstUnit ushr 8) and 0xFF
                if (argCount <= 0) return emptyList()
                val firstReg = ushortAt(insnsOff + (index + 2) * 2)
                return (0 until argCount.coerceAtMost(16)).map { firstReg + it }
            }

            private fun invoke35cRegisters(insnsOff: Int, index: Int): List<Int> {
                val first = ushortAt(insnsOff + index * 2)
                val argCount = (first ushr 12) and 0x0F
                val fifth = (first ushr 8) and 0x0F
                val third = ushortAt(insnsOff + (index + 2) * 2)
                val regs = intArrayOf(
                    third and 0x0F,
                    (third ushr 4) and 0x0F,
                    (third ushr 8) and 0x0F,
                    (third ushr 12) and 0x0F,
                    fifth,
                )
                return regs.take(argCount.coerceIn(0, regs.size))
            }

            private fun findDrawableValueBefore(
                insnsOff: Int,
                invokeIndex: Int,
                targetReg: Int,
                cl: ClassLoader,
            ): Int? {
                val start = (invokeIndex - 96).coerceAtLeast(0)
                for (i in invokeIndex - 1 downTo start) {
                    val offset = insnsOff + i * 2
                    val unit = ushortAt(offset)
                    val op = unit and 0xFF
                    val dest = (unit ushr 8) and 0xFF
                    if (dest != targetReg) continue
                    when (op) {
                        0x14 -> {
                            val value = intAt(offset + 2)
                            if (isDrawableResourceId(value)) return value
                        }
                        0x15 -> {
                            val value = signedShortAt(offset + 2) shl 16
                            if (isDrawableResourceId(value)) return value
                        }
                        0x60 -> {
                            val fieldIdx = ushortAt(offset + 2)
                            val value = resolveDrawableFieldValue(fieldIdx, cl)
                            if (value != null && isDrawableResourceId(value)) return value
                        }
                    }
                }
                return null
            }

            private fun isWebPSetPureDrawable(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == WEBP_SET_PURE_DRAWABLE &&
                    methodClassDescriptor(methodIdx) == WEBP_MANAGER_DESCRIPTOR
            }

            private fun isAiWriteHelperFactory(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                val descriptor = methodClassDescriptor(methodIdx) ?: return false
                val name = methodName(methodIdx).orEmpty()
                return descriptor == AI_WRITE_HELPER_DESCRIPTOR &&
                    (name == "a" || name.length <= 2)
            }

            private fun isFrameLayoutConstructor(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "<init>" &&
                    methodClassDescriptor(methodIdx) == FRAME_LAYOUT_DESCRIPTOR
            }

            private fun isFrameLayoutField(fieldIdx: Int): Boolean {
                if (fieldIdx !in 0 until fieldIdsSize) return false
                return fieldTypeDescriptor(fieldIdx) == FRAME_LAYOUT_DESCRIPTOR
            }

            private fun isViewLikeField(fieldIdx: Int): Boolean {
                if (fieldIdx !in 0 until fieldIdsSize) return false
                return isViewLikeDescriptor(fieldTypeDescriptor(fieldIdx).orEmpty())
            }

            private fun isViewSetOnClickListener(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setOnClickListener") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isViewSetVisibility(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setVisibility") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isViewGetLayoutParams(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "getLayoutParams") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isViewSetLayoutParams(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setLayoutParams") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isViewFindViewById(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "findViewById") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isViewSetBackgroundResource(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setBackgroundResource") return false
                return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isNavigationBarAddCustomView(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "addCustomView" &&
                    methodClassDescriptor(methodIdx) == NAVIGATION_BAR_DESCRIPTOR
            }

            private fun isTextUtilsIsEmpty(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "isEmpty" &&
                    methodClassDescriptor(methodIdx) == TEXT_UTILS_DESCRIPTOR
            }

            private fun isEmManagerFrom(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "from" &&
                    methodClassDescriptor(methodIdx) == EM_MANAGER_DESCRIPTOR
            }

            private fun isEmManagerBackgroundMethod(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodClassDescriptor(methodIdx) != EM_MANAGER_DESCRIPTOR) return false
                val name = methodName(methodIdx).orEmpty()
                return name == "setBackGroundRealColor" ||
                    name == "setBackGroundColor" ||
                    name == "setCorner"
            }

            private fun bestDexFieldName(counts: LinkedHashMap<String, Int>): String? {
                var bestName: String? = null
                var bestCount = 0
                for ((name, count) in counts) {
                    if (count > bestCount) {
                        bestName = name
                        bestCount = count
                    }
                }
                return bestName
            }

            private fun isViewMarginSetMargins(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setMargins") return false
                val descriptor = methodClassDescriptor(methodIdx).orEmpty()
                return descriptor == VIEW_MARGIN_LAYOUT_PARAMS_DESCRIPTOR ||
                    descriptor.endsWith("/FrameLayout\$LayoutParams;") ||
                    descriptor.endsWith("/ViewGroup\$MarginLayoutParams;")
            }

            private fun isSpriteMemePanConstructor(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "<init>" &&
                    methodClassDescriptor(methodIdx) == AI_SPRITE_MEME_PAN_DESCRIPTOR
            }

            private fun isSpriteMemePanMethod(methodIdx: Int, name: String): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == name &&
                    methodClassDescriptor(methodIdx) == AI_SPRITE_MEME_PAN_DESCRIPTOR
            }

            private fun isImageJumpButtonLayoutConstructor(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "<init>" &&
                    methodClassDescriptor(methodIdx) == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR
            }

            private fun isLinearLayoutLayoutParamsConstructor(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "<init>" &&
                    methodClassDescriptor(methodIdx) == LINEAR_LAYOUT_LAYOUT_PARAMS_DESCRIPTOR
            }

            private fun isViewGroupAddView(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "addView") return false
                val descriptor = methodClassDescriptor(methodIdx).orEmpty()
                return descriptor == VIEW_GROUP_DESCRIPTOR ||
                    descriptor == LINEAR_LAYOUT_DESCRIPTOR ||
                    descriptor.endsWith("ViewGroup;") ||
                    descriptor.endsWith("Layout;")
            }

            private fun isViewLikeDescriptor(descriptor: String): Boolean {
                if (descriptor.isBlank()) return false
                return descriptor == ANDROID_VIEW_DESCRIPTOR ||
                    descriptor == FRAME_LAYOUT_DESCRIPTOR ||
                    descriptor.endsWith("View;") ||
                    descriptor.endsWith("Layout;") ||
                    descriptor.endsWith("Button;") ||
                    descriptor.endsWith("ProgressBar;") ||
                    descriptor.endsWith("Progressbar;")
            }

            private fun isAppClassDescriptor(descriptor: String): Boolean {
                return descriptor.startsWith("Lcom/baidu/") ||
                    descriptor.startsWith("Landroidx/") ||
                    descriptor.startsWith("Lkotlin/")
            }

            private fun isAiWriteReplyString(value: String): Boolean {
                return value == "AI_WRITE_REPLY" ||
                    value.equals("ai_write_reply", ignoreCase = true) ||
                    value.equals("aiWriteReply", ignoreCase = true)
            }

            private fun isAiCapacityDescriptor(descriptor: String): Boolean {
                return descriptor.contains("AICapacityApplyHelper") ||
                    descriptor.contains("capacityApplyType") ||
                    descriptor.contains("AIWrite", ignoreCase = true)
            }

            private fun isListSetSelection(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setSelection") return false
                val descriptor = methodClassDescriptor(methodIdx).orEmpty()
                return descriptor.contains("List") ||
                    descriptor.contains("RecyclerView") ||
                    descriptor.contains("AdapterView") ||
                    descriptor.contains("TabHost") ||
                    descriptor.contains("Gallery")
            }

            private fun isSwipeRefreshInvoke(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return isSwipeRefreshDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isSwipeRefreshSetRefreshing(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                if (methodName(methodIdx) != "setRefreshing") return false
                return isSwipeRefreshDescriptor(methodClassDescriptor(methodIdx).orEmpty())
            }

            private fun isSwipeRefreshDescriptor(descriptor: String): Boolean {
                return descriptor == BIGDAY_SWIPE_REFRESH_DESCRIPTOR ||
                    descriptor.endsWith("/BigdaySwipeRefreshLayout;") ||
                    descriptor.contains("SwipeRefreshLayout")
            }

            private fun isScrollTabNotify(methodIdx: Int): Boolean {
                if (methodIdx !in 0 until methodIdsSize) return false
                return methodName(methodIdx) == "b" &&
                    methodClassDescriptor(methodIdx) == SCROLL_FRAGMENT_TAB_CALLBACK_DESCRIPTOR
            }

            private fun resolveDrawableFieldValue(fieldIdx: Int, cl: ClassLoader): Int? {
                if (fieldIdx !in 0 until fieldIdsSize) return null
                if (fieldClassDescriptor(fieldIdx) != R_DRAWABLE_DESCRIPTOR) return null
                val name = fieldName(fieldIdx) ?: return null
                val drawableClass = XposedCompat.findClassOrNull("com.baidu.tieba.R\$drawable", cl) ?: return null
                return runCatching {
                    val field = drawableClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.getInt(null)
                }.getOrNull()
            }

            private fun isDrawableResourceId(value: Int): Boolean {
                if ((value ushr 24) != 0x7F) return false
                return ((value ushr 16) and 0xFF) == 0x08
            }

            private fun isStaticAccess(accessFlags: Int): Boolean {
                return (accessFlags and 0x0008) != 0
            }

            private fun methodClassDescriptor(methodIdx: Int): String? {
                val off = methodIdsOff + methodIdx * 8
                return typeDescriptor(ushortAt(off))
            }

            private fun methodReturnDescriptor(methodIdx: Int): String? {
                if (methodIdx !in 0 until methodIdsSize) return null
                val methodOff = methodIdsOff + methodIdx * 8
                val protoIdx = ushortAt(methodOff + 2)
                if (protoIdx !in 0 until protoIdsSize) return null
                val protoOff = protoIdsOff + protoIdx * 12
                return typeDescriptor(intAt(protoOff + 4))
            }

            private fun methodParameterDescriptors(methodIdx: Int): List<String> {
                if (methodIdx !in 0 until methodIdsSize) return emptyList()
                val methodOff = methodIdsOff + methodIdx * 8
                val protoIdx = ushortAt(methodOff + 2)
                if (protoIdx !in 0 until protoIdsSize) return emptyList()
                val protoOff = protoIdsOff + protoIdx * 12
                val parametersOff = intAt(protoOff + 8)
                if (parametersOff == 0) return emptyList()
                val size = intAt(parametersOff)
                if (size < 0 || parametersOff + 4 + size * 2 > bytes.size) return emptyList()
                val out = ArrayList<String>(size)
                for (i in 0 until size) {
                    typeDescriptor(ushortAt(parametersOff + 4 + i * 2))?.let(out::add)
                }
                return out
            }

            private fun methodName(methodIdx: Int): String? {
                val off = methodIdsOff + methodIdx * 8
                return stringAt(intAt(off + 4))
            }

            private fun fieldClassDescriptor(fieldIdx: Int): String? {
                val off = fieldIdsOff + fieldIdx * 8
                return typeDescriptor(ushortAt(off))
            }

            private fun fieldTypeDescriptor(fieldIdx: Int): String? {
                val off = fieldIdsOff + fieldIdx * 8
                return typeDescriptor(ushortAt(off + 2))
            }

            private fun fieldName(fieldIdx: Int): String? {
                val off = fieldIdsOff + fieldIdx * 8
                return stringAt(intAt(off + 4))
            }

            private fun typeDescriptor(typeIdx: Int): String? {
                if (typeIdx !in 0 until typeIdsSize) return null
                typeCache[typeIdx]?.let { return it }
                val stringIdx = intAt(typeIdsOff + typeIdx * 4)
                val value = stringAt(stringIdx)
                typeCache[typeIdx] = value
                return value
            }

            private fun extendsDescriptor(
                descriptor: String,
                expectedSuperDescriptor: String,
            ): Boolean {
                var current: String? = descriptor
                repeat(12) {
                    if (current == null) return false
                    if (current == expectedSuperDescriptor) return true
                    current = classSuperDescriptor(current!!)
                }
                return false
            }

            private fun classSuperDescriptor(descriptor: String): String? {
                if (classSuperCache.containsKey(descriptor)) return classSuperCache[descriptor]
                for (i in 0 until classDefsSize) {
                    val classDefOff = classDefsOff + i * 32
                    if (typeDescriptor(intAt(classDefOff)) != descriptor) continue
                    val superDescriptor = typeDescriptor(intAt(classDefOff + 8))
                    classSuperCache[descriptor] = superDescriptor
                    return superDescriptor
                }
                classSuperCache[descriptor] = null
                return null
            }

            private fun descriptorToClassName(descriptor: String): String? {
                if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return null
                return descriptor.substring(1, descriptor.length - 1).replace('/', '.')
            }

            private fun stringAt(stringIdx: Int): String? {
                if (stringIdx !in 0 until stringIdsSize) return null
                if (stringCache.containsKey(stringIdx)) return stringCache[stringIdx]
                val dataOff = intAt(stringIdsOff + stringIdx * 4)
                var cursor = readUleb128(dataOff).next
                val chars = ArrayList<Byte>()
                while (cursor < bytes.size && bytes[cursor].toInt() != 0) {
                    chars.add(bytes[cursor])
                    cursor++
                }
                val value = runCatching {
                    chars.toByteArray().toString(Charsets.UTF_8)
                }.getOrNull()
                stringCache[stringIdx] = value
                return value
            }

            private fun readUleb128(offset: Int): Uleb {
                var cursor = offset
                var result = 0
                var shift = 0
                while (cursor < bytes.size) {
                    val b = bytes[cursor].toInt() and 0xFF
                    cursor++
                    result = result or ((b and 0x7F) shl shift)
                    if ((b and 0x80) == 0) break
                    shift += 7
                }
                return Uleb(result, cursor)
            }

            private fun ushortAt(offset: Int): Int {
                if (offset < 0 || offset + 1 >= bytes.size) return 0
                return (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            }

            private fun signedShortAt(offset: Int): Int {
                val value = ushortAt(offset)
                return if ((value and 0x8000) != 0) value or -0x10000 else value
            }

            private fun intAt(offset: Int): Int {
                if (offset < 0 || offset + 3 >= bytes.size) return 0
                return (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            }

            private data class Uleb(
                val value: Int,
                val next: Int,
            )

            private data class EncodedMethod(
                val methodIdx: Int,
                val accessFlags: Int,
                val codeOff: Int,
                val next: Int,
            )
        }
    }

    private data class CollectionSearchScanSymbols(
        val presenterField: String? = null,
        val presenterListSetterMethod: String? = null,
        val presenterAdapterField: String? = null,
        val modelField: String? = null,
        val modelListGetterMethod: String? = null,
        val modelParseMethod: String? = null,
        val modelListField: String? = null,
        val fragmentDisplayListField: String? = null,
        val activityNavControllerField: String? = null,
        val navBarField: String? = null,
        val adapterShowFooterMethod: String? = null,
        val adapterLoadingMethod: String? = null,
        val adapterHasMoreMethod: String? = null,
        val editModeMethod: String? = null,
    )

    private data class HistorySearchScanSymbols(
        val adapterField: String? = null,
        val adapterSetListMethod: String? = null,
        val listField: String? = null,
        val activityListUpdateMethod: String? = null,
        val activityNavBarField: String? = null,
        val threadNameMethod: String? = null,
        val forumNameMethod: String? = null,
        val userNameMethod: String? = null,
        val descriptionMethod: String? = null,
        val threadIdMethod: String? = null,
        val postIdMethod: String? = null,
        val liveIdMethod: String? = null,
    )

    private data class MsgTabScanSymbols(
        val locateToTabMethod: String? = null,
        val containerSelectMethod: String? = null,
        val containerExtDataField: String? = null,
    )

    private data class MainTabBottomScanSymbols(
        val dataClass: String? = null,
        val addMethod: String? = null,
        val getListMethod: String? = null,
        val delegateGetStructureMethod: String? = null,
        val structureTypeField: String? = null,
        val structureDynamicIconField: String? = null,
        val structureFragmentField: String? = null,
    )

    private data class OriginalImageScanSymbols(
        val pagerAdapterClass: String? = null,
        val urlDragImageViewClass: String? = null,
        val dataClass: String? = null,
        val setPrimaryItemMethod: String? = null,
        val setAssistUrlMethod: String? = null,
        val assistDataMethod: String? = null,
        val originTextMethod: String? = null,
        val showButtonField: String? = null,
        val blockedField: String? = null,
        val originalProcessField: String? = null,
        val originalUrlField: String? = null,
        val sharedPrefHelperClass: String? = null,
        val sharedPrefGetInstanceMethod: String? = null,
        val sharedPrefPutBooleanMethod: String? = null,
        val md5Class: String? = null,
        val md5Method: String? = null,
        val primaryReadyMethod: String? = null,
        val triggerMethod: String? = null,
        val directStartMethod: String? = null,
    )

    private data class CollectionPresenterCandidate(
        val fieldName: String,
        val setterMethod: String,
        val presenterClass: Class<*>,
    )

    private data class CollectionModelCandidate(
        val fieldName: String,
        val getterMethod: String,
        val parseMethod: String,
        val listFieldName: String?,
    )

    private data class CollectionAdapterSymbols(
        val presenterAdapterField: String? = null,
        val showFooterMethod: String? = null,
        val loadingMethod: String? = null,
        val hasMoreMethod: String? = null,
    )

    private data class CollectionNavigationSymbols(
        val activityNavControllerField: String? = null,
        val navBarField: String? = null,
    )

    private data class HistoryAdapterCandidate(
        val fieldName: String,
        val setterMethod: String,
    )

    private data class HomeTabItemScanSymbols(
        val typeField: String? = null,
        val codeField: String? = null,
        val nameField: String? = null,
        val urlField: String? = null,
        val mainSetterMethod: String? = null,
        val mainIntField: String? = null,
        val mainBooleanField: String? = null,
    )

    private fun scanOriginalImageSymbols(cl: ClassLoader, logger: ScanLogger?): OriginalImageScanSymbols {
        val adapterClass = safeFindClass(IMAGE_PAGER_ADAPTER_CLASS, cl)
        val urlDragClass = safeFindClass(URL_DRAG_IMAGE_VIEW_CLASS, cl)
        val dataClass = safeFindClass(IMAGE_URL_DATA_CLASS, cl)
        val sharedPrefClass = safeFindClass(SHARED_PREF_HELPER_CLASS, cl)
        val md5Class = safeFindClass(TB_MD5_CLASS, cl)

        if (adapterClass == null) log(logger, "origImage: class not found: $IMAGE_PAGER_ADAPTER_CLASS")
        if (urlDragClass == null) log(logger, "origImage: class not found: $URL_DRAG_IMAGE_VIEW_CLASS")
        if (dataClass == null) log(logger, "origImage: class not found: $IMAGE_URL_DATA_CLASS")

        val setPrimaryItemMethod = adapterClass?.declaredMethods?.firstOrNull { method ->
            method.name == "setPrimaryItem" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 3 &&
                ViewGroup::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Any::class.java
        }?.name

        val originalMethods = urlDragClass
            ?.let { runCatching { OriginalImageMethodsRule().match(it, cl) }.getOrNull() }
            ?.methodName
            ?.split(",")
            ?.map { it.trim().ifEmpty { null } }
            .orEmpty()
        fun originalMethodPart(index: Int): String? = originalMethods.getOrNull(index)

        val setAssistUrlMethod = if (urlDragClass != null && dataClass != null) {
            val candidates = urlDragClass.declaredMethods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == dataClass &&
                    !Modifier.isStatic(method.modifiers)
            }
            candidates.singleOrNull { it.name == "setAssistUrl" }?.name
                ?: candidates.singleOrNull()?.name
        } else {
            null
        }
        val assistDataCandidates = urlDragClass?.declaredMethods?.filter { method ->
            method.parameterTypes.isEmpty() &&
                method.returnType == dataClass &&
                !Modifier.isStatic(method.modifiers)
        }.orEmpty()
        val assistDataMethod = assistDataCandidates.firstOrNull { it.name == "getmAssistUrlData" }?.name
            ?: assistDataCandidates.singleOrNull()?.name
        val originTextMethod = urlDragClass?.declaredMethods?.firstOrNull { method ->
            method.parameterTypes.isEmpty() &&
                method.returnType == String::class.java &&
                !Modifier.isStatic(method.modifiers) &&
                method.name == "getmCheckOriginPicText"
        }?.name

        val dataFields = dataClass?.let(::collectInstanceFields).orEmpty()
        val showButtonField = dataFields.firstOrNull {
            it.name == "mIsShowOrigonButton" && isBooleanType(it.type)
        }?.name
        val blockedField = dataFields.firstOrNull {
            it.name == "isBlockedPic" && isBooleanType(it.type)
        }?.name
        val originalProcessField = dataFields.firstOrNull {
            it.name == "originalProcess" && isIntType(it.type)
        }?.name
        val originalUrlField = dataFields.firstOrNull {
            it.name == "originalUrl" && it.type == String::class.java
        }?.name

        val sharedPrefGetInstanceMethod = sharedPrefClass?.declaredMethods?.firstOrNull { method ->
            method.name == "getInstance" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType.name == SHARED_PREF_HELPER_CLASS
        }?.name
        val sharedPrefPutBooleanMethod = sharedPrefClass?.declaredMethods?.firstOrNull { method ->
            method.name == "putBoolean" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.name
        val md5Method = md5Class?.declaredMethods?.firstOrNull { method ->
            method.name == "getNameMd5FromUrl" &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        }?.name

        val result = OriginalImageScanSymbols(
            pagerAdapterClass = adapterClass?.name,
            urlDragImageViewClass = urlDragClass?.name,
            dataClass = dataClass?.name,
            setPrimaryItemMethod = setPrimaryItemMethod,
            setAssistUrlMethod = setAssistUrlMethod,
            assistDataMethod = assistDataMethod,
            originTextMethod = originTextMethod,
            showButtonField = showButtonField,
            blockedField = blockedField,
            originalProcessField = originalProcessField,
            originalUrlField = originalUrlField,
            sharedPrefHelperClass = sharedPrefClass?.name,
            sharedPrefGetInstanceMethod = sharedPrefGetInstanceMethod,
            sharedPrefPutBooleanMethod = sharedPrefPutBooleanMethod,
            md5Class = md5Class?.name,
            md5Method = md5Method,
            primaryReadyMethod = originalMethodPart(0),
            triggerMethod = originalMethodPart(1),
            directStartMethod = originalMethodPart(2),
        )
        log(
            logger,
            "origImage symbols: view=${result.urlDragImageViewClass}.${result.triggerMethod}, " +
                "setAssist=${result.setAssistUrlMethod}, " +
                "data={${result.showButtonField},${result.blockedField},${result.originalProcessField},${result.originalUrlField}}",
        )
        return result
    }

    private fun scanCollectionSearchSymbols(cl: ClassLoader, logger: ScanLogger?): CollectionSearchScanSymbols {
        val fragmentClass = safeFindClass(COLLECTION_THREAD_FRAGMENT_CLASS, cl)
        if (fragmentClass == null) {
            log(logger, "collectionScan: thread fragment class not found")
            return CollectionSearchScanSymbols()
        }

        val presenterCandidate = resolveCollectionPresenterCandidate(fragmentClass)
        val modelCandidate = resolveCollectionModelCandidate(fragmentClass)
        val adapterSymbols = presenterCandidate?.let { resolveCollectionAdapterSymbols(it.presenterClass) }
            ?: CollectionAdapterSymbols()
        val fragmentDisplayListField = resolveCollectionFragmentDisplayListField(
            fragmentClass = fragmentClass,
            modelFieldName = modelCandidate?.fieldName,
        )
        val collectionActivityClass = safeFindClass(COLLECTION_ACTIVITY_CLASS, cl)
        val navigationSymbols = collectionActivityClass?.let(::resolveCollectionNavigationSymbols) ?: CollectionNavigationSymbols()
        val editModeMethod = collectionActivityClass?.let(::resolveCollectionEditModeMethod)

        val out = CollectionSearchScanSymbols(
            presenterField = presenterCandidate?.fieldName,
            presenterListSetterMethod = presenterCandidate?.setterMethod,
            presenterAdapterField = adapterSymbols.presenterAdapterField,
            modelField = modelCandidate?.fieldName,
            modelListGetterMethod = modelCandidate?.getterMethod,
            modelParseMethod = modelCandidate?.parseMethod,
            modelListField = modelCandidate?.listFieldName,
            fragmentDisplayListField = fragmentDisplayListField,
            activityNavControllerField = navigationSymbols.activityNavControllerField,
            navBarField = navigationSymbols.navBarField,
            adapterShowFooterMethod = adapterSymbols.showFooterMethod,
            adapterLoadingMethod = adapterSymbols.loadingMethod,
            adapterHasMoreMethod = adapterSymbols.hasMoreMethod,
            editModeMethod = editModeMethod,
        )

        log(
            logger,
            "collectionScan: presenter=${out.presenterField}.${out.presenterListSetterMethod}, " +
                "model=${out.modelField}.${out.modelListGetterMethod}/${out.modelParseMethod}[${out.modelListField}], " +
                "nav={${out.activityNavControllerField},${out.navBarField}}, " +
                "adapter=${out.presenterAdapterField}.{${out.adapterShowFooterMethod},${out.adapterLoadingMethod},${out.adapterHasMoreMethod}}, " +
                "fragmentList=${out.fragmentDisplayListField}, editMode=${out.editModeMethod}",
        )
        return out
    }

    private fun resolveCollectionPresenterCandidate(fragmentClass: Class<*>): CollectionPresenterCandidate? {
        val candidates = collectInstanceFields(fragmentClass).mapNotNull { field ->
            val setter = resolveListSetterMethodName(field.type, preferredName = "w") ?: return@mapNotNull null
            CollectionPresenterCandidate(fieldName = field.name, setterMethod = setter, presenterClass = field.type)
        }
        return candidates.minWithOrNull(
            compareBy<CollectionPresenterCandidate>(
                { if (it.fieldName == "d") 0 else 1 },
                { if (it.setterMethod == "w") 0 else 1 },
                { it.setterMethod.length },
                { it.fieldName.length },
                { it.fieldName },
            ),
        )
    }

    private fun resolveCollectionModelCandidate(fragmentClass: Class<*>): CollectionModelCandidate? {
        val candidates = collectInstanceFields(fragmentClass).mapNotNull { field ->
            val getter = resolveListGetterMethodName(field.type, preferredName = "n") ?: return@mapNotNull null
            val parse = resolveParseMethodName(field.type, preferredName = "t") ?: return@mapNotNull null
            val listField = resolveListFieldName(field.type, preferredName = "d")
            CollectionModelCandidate(
                fieldName = field.name,
                getterMethod = getter,
                parseMethod = parse,
                listFieldName = listField,
            )
        }
        return candidates.minWithOrNull(
            compareBy<CollectionModelCandidate>(
                { if (it.fieldName == "c") 0 else 1 },
                { if (it.getterMethod == "n") 0 else 1 },
                { if (it.parseMethod == "t") 0 else 1 },
                { if (it.listFieldName == "d") 0 else 1 },
                { if (it.listFieldName.isNullOrBlank()) 1 else 0 },
                { it.fieldName.length },
                { it.fieldName },
            ),
        )
    }

    private fun resolveCollectionAdapterSymbols(presenterClass: Class<*>): CollectionAdapterSymbols {
        val adapterField = collectInstanceFields(presenterClass)
            .filter { field -> isAdapterLike(field.type) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "g") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?: return CollectionAdapterSymbols()

        val footerCandidates = collectInstanceMethods(adapterField.type).filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                (method.parameterTypes[0] == Boolean::class.javaPrimitiveType || method.parameterTypes[0] == Boolean::class.java)
        }

        val showFooterMethod = pickMethodName(footerCandidates, preferredName = "m")
        val loadingMethod = pickMethodName(
            footerCandidates.filter { it.name != showFooterMethod },
            preferredName = "p",
        )
        val hasMoreMethod = pickMethodName(
            footerCandidates.filter { it.name != showFooterMethod && it.name != loadingMethod },
            preferredName = "n",
        )

        return CollectionAdapterSymbols(
            presenterAdapterField = adapterField.name,
            showFooterMethod = showFooterMethod,
            loadingMethod = loadingMethod,
            hasMoreMethod = hasMoreMethod,
        )
    }

    private fun resolveCollectionFragmentDisplayListField(fragmentClass: Class<*>, modelFieldName: String?): String? {
        val listFields = collectInstanceFields(fragmentClass).filter { isListType(it.type) }
        if (listFields.isEmpty()) return null
        listFields.firstOrNull { it.name == "f" }?.let { return it.name }
        listFields.firstOrNull { it.name != modelFieldName }?.let { return it.name }
        return listFields.minByOrNull { it.name.length }?.name
    }

    private fun resolveCollectionEditModeMethod(activityClass: Class<*>): String? {
        val methods = collectInstanceMethods(activityClass).filter { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
        }
        if (methods.isEmpty()) return null
        methods.firstOrNull { it.name == "g1" }?.let { return it.name }
        methods.firstOrNull { it.name == "isEditMode" }?.let { return it.name }
        methods.firstOrNull { it.name.contains("edit", ignoreCase = true) }?.let { return it.name }
        return methods.minByOrNull { it.name.length }?.name
    }

    private fun resolveCollectionNavigationSymbols(activityClass: Class<*>): CollectionNavigationSymbols {
        val activityFields = collectInstanceFields(activityClass)
        val directNavField = activityFields
            .filter { field -> hasNavigationAddCustomViewMethod(field.type) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "b") 0 else 1 },
                    { if (it.name == "d") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
        if (directNavField != null) {
            return CollectionNavigationSymbols(
                activityNavControllerField = null,
                navBarField = directNavField.name,
            )
        }

        val nestedCandidates = ArrayList<Triple<String, String, Class<*>>>()
        for (controllerField in activityFields) {
            val navField = collectInstanceFields(controllerField.type)
                .firstOrNull { field -> hasNavigationAddCustomViewMethod(field.type) }
                ?: continue
            nestedCandidates.add(Triple(controllerField.name, navField.name, navField.type))
        }
        if (nestedCandidates.isEmpty()) return CollectionNavigationSymbols()

        val best = nestedCandidates.minWithOrNull(
            compareBy<Triple<String, String, Class<*>>>(
                { if (it.first == "a") 0 else 1 },
                { if (it.second == "d") 0 else 1 },
                { it.first.length },
                { it.second.length },
                { it.first },
                { it.second },
            ),
        ) ?: return CollectionNavigationSymbols()
        return CollectionNavigationSymbols(
            activityNavControllerField = best.first,
            navBarField = best.second,
        )
    }

    private fun hasNavigationAddCustomViewMethod(clazz: Class<*>): Boolean {
        return collectInstanceMethods(clazz).any { method ->
            method.name == "addCustomView" &&
                method.parameterTypes.size == 3 &&
                View::class.java.isAssignableFrom(method.parameterTypes[1])
        }
    }

    private fun scanHistorySearchSymbols(cl: ClassLoader, logger: ScanLogger?): HistorySearchScanSymbols {
        val activityClass = safeFindClass(HISTORY_ACTIVITY_CLASS, cl)
        if (activityClass == null) {
            log(logger, "historyScan: history activity class not found")
            return HistorySearchScanSymbols()
        }
        val historyDataMethods = safeFindClass(HISTORY_DATA_CLASS, cl)
            ?.let(::collectInstanceMethods)
            .orEmpty()

        val adapterCandidate = resolveHistoryAdapterCandidate(activityClass)
        val listField = collectInstanceFields(activityClass)
            .filter { isListType(it.type) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "h") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val out = HistorySearchScanSymbols(
            adapterField = adapterCandidate?.fieldName,
            adapterSetListMethod = adapterCandidate?.setterMethod,
            listField = listField,
            activityListUpdateMethod = pickMethodName(
                methods = collectInstanceMethods(activityClass).filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        isListType(method.parameterTypes[0])
                },
                preferredName = "g",
            ),
            activityNavBarField = resolveHistoryNavigationFieldName(activityClass),
            threadNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getThreadName"),
            forumNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getForumName"),
            userNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getUserName"),
            descriptionMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getDescription"),
            threadIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getThreadId"),
            postIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getPostID", "getPostId"),
            liveIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getLiveId"),
        )
        log(
            logger,
            "historyScan: adapter=${out.adapterField}.${out.adapterSetListMethod}, " +
                "listField=${out.listField}, update=${out.activityListUpdateMethod}, nav=${out.activityNavBarField}, " +
                "getters={${out.threadNameMethod},${out.forumNameMethod},${out.userNameMethod}," +
                "${out.descriptionMethod},${out.threadIdMethod},${out.postIdMethod},${out.liveIdMethod}}",
        )
        return out
    }

    private fun resolveHistoryNavigationFieldName(activityClass: Class<*>): String? {
        return collectInstanceFields(activityClass)
            .filter { field -> hasNavigationAddCustomViewMethod(field.type) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "b") 0 else 1 },
                    { if (it.name == "d") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.name
    }

    private fun resolveHistoryAdapterCandidate(activityClass: Class<*>): HistoryAdapterCandidate? {
        val candidates = collectInstanceFields(activityClass).mapNotNull { field ->
            val setter = resolveListSetterMethodName(field.type, preferredName = "g") ?: return@mapNotNull null
            HistoryAdapterCandidate(fieldName = field.name, setterMethod = setter)
        }
        return candidates.minWithOrNull(
            compareBy<HistoryAdapterCandidate>(
                { if (it.fieldName == "f") 0 else 1 },
                { if (it.setterMethod == "g") 0 else 1 },
                { it.setterMethod.length },
                { it.fieldName.length },
                { it.fieldName },
            ),
        )
    }

    private fun scanHomeTabItemSymbols(homeMatch: ScanMatch, cl: ClassLoader, logger: ScanLogger?): HomeTabItemScanSymbols {
        val homeClass = safeFindClass(homeMatch.className, cl)
        if (homeClass == null) {
            log(logger, "home item scan: home class not found ${homeMatch.className}")
            return HomeTabItemScanSymbols()
        }
        val itemClass = resolveHomeTabItemClass(homeClass, homeMatch.fieldName)
        if (itemClass == null) {
            log(logger, "home item scan: item class unresolved")
            return HomeTabItemScanSymbols()
        }
        val fields = collectInstanceFields(itemClass)
        val typeField = pickHomeTabIntFieldName(fields, preferredName = "a")

        val stringFields = fields.filter { it.type == String::class.java }
        val codeField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("c"),
            containsKeyword = "code",
        )
        val nameField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("b"),
            containsKeyword = "name",
            excludes = setOfNotNull(codeField),
        )
        val urlField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("d"),
            containsKeyword = "url",
            excludes = setOfNotNull(codeField, nameField),
        )

        val mainSetterMethod = collectInstanceMethods(itemClass)
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    (method.parameterTypes[0] == Boolean::class.javaPrimitiveType ||
                        method.parameterTypes[0] == Boolean::class.java)
            }
            .minWithOrNull(
                compareBy<Method>(
                    { if (it.name == "k") 0 else 1 },
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val mainIntField = fields
            .filter { field ->
                (field.type == Int::class.javaPrimitiveType || field.type == Int::class.javaObjectType)
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "i") 0 else 1 },
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val mainBooleanField = fields
            .filter { field ->
                (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java)
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val out = HomeTabItemScanSymbols(
            typeField = typeField,
            codeField = codeField,
            nameField = nameField,
            urlField = urlField,
            mainSetterMethod = mainSetterMethod,
            mainIntField = mainIntField,
            mainBooleanField = mainBooleanField,
        )
        log(
            logger,
            "home item scan: cls=${itemClass.name}, " +
                "fields={${out.typeField},${out.codeField},${out.nameField},${out.urlField}}, " +
                "main={${out.mainSetterMethod},${out.mainIntField},${out.mainBooleanField}}",
        )
        return out
    }

    private fun resolveHomeTabItemClass(homeClass: Class<*>, listFieldName: String): Class<*>? {
        val factoryReturn = collectInstanceMethods(homeClass)
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType != Void.TYPE &&
                    !method.returnType.isPrimitive &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            .minWithOrNull(
                compareBy<Method>(
                    { if (it.name.length <= 2) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.returnType
        if (factoryReturn != null) return factoryReturn

        val listField = collectInstanceFields(homeClass).firstOrNull { it.name == listFieldName } ?: return null
        val generic = listField.genericType as? java.lang.reflect.ParameterizedType ?: return null
        val actual = generic.actualTypeArguments.firstOrNull() as? Class<*>
        return actual
    }

    private fun pickHomeTabIntFieldName(fields: List<java.lang.reflect.Field>, preferredName: String): String? {
        return fields
            .filter { it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == preferredName) 0 else 1 },
                    { if (it.name.contains("type", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.name
    }

    private fun pickHomeTabStringFieldName(
        fields: List<java.lang.reflect.Field>,
        preferredNames: List<String>,
        containsKeyword: String? = null,
        excludes: Set<String> = emptySet(),
    ): String? {
        return fields
            .filter { !excludes.contains(it.name) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { preferredNames.indexOf(it.name).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } },
                    { if (!containsKeyword.isNullOrBlank() && it.name.contains(containsKeyword, ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.name
    }

    private fun resolveHistoryDataStringGetterName(methods: List<Method>, vararg candidateNames: String): String? {
        for (name in candidateNames) {
            val method = methods.firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.isEmpty() &&
                    (method.returnType == String::class.java || CharSequence::class.java.isAssignableFrom(method.returnType))
            }
            if (method != null) return method.name
        }
        return null
    }

    private fun scanMsgTabSymbols(cl: ClassLoader, logger: ScanLogger?): MsgTabScanSymbols {
        val vmClass = safeFindClass(MSG_TAB_VIEW_MODEL_CLASS, cl)
        val containerClass = safeFindClass(MSG_TAB_CONTAINER_VIEW_CLASS, cl)

        val locateMethod = vmClass?.let { cls ->
            val candidates = collectInstanceMethods(cls).filter { method ->
                method.returnType == Long::class.javaPrimitiveType &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    method.parameterTypes[1] == String::class.java
            }
            pickMethodName(candidates, preferredName = "m")
        }

        val containerSelectMethod = containerClass?.let { cls ->
            val candidates = collectInstanceMethods(cls).filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == Long::class.javaPrimitiveType
            }
            pickMethodName(candidates, preferredName = "L")
        }

        val containerExtDataField = containerClass?.let { cls ->
            val candidates = collectInstanceFields(cls).filter { it.type == String::class.java }
            candidates.minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "C") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name
        }

        val out = MsgTabScanSymbols(
            locateToTabMethod = locateMethod,
            containerSelectMethod = containerSelectMethod,
            containerExtDataField = containerExtDataField,
        )
        log(
            logger,
            "msgTabScan: locate=${out.locateToTabMethod}, container=${out.containerSelectMethod}, ext=${out.containerExtDataField}",
        )
        return out
    }

    private fun scanMainTabBottomSymbols(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): MainTabBottomScanSymbols {
        val match = runRules(candidates, cl, listOf(MainTabBottomLevel1Rule()), logger, "mainTabBottom")
        if (match == null) {
            log(logger, "mainTabBottom: no scan match")
            return MainTabBottomScanSymbols()
        }

        val targetClass = safeFindClass(match.className, cl)
        if (targetClass == null) {
            log(logger, "mainTabBottom: target class not found ${match.className}")
            return MainTabBottomScanSymbols()
        }

        val methodParts = match.methodName.split(",")
        val addMethod = resolveMainTabBottomAddMethod(targetClass, methodParts.getOrNull(0))
        val getListMethod = resolveMainTabBottomGetListMethod(targetClass, methodParts.getOrNull(1))
        if (addMethod == null || getListMethod == null) {
            log(
                logger,
                "mainTabBottom: unresolved methods add=${addMethod?.name}, getList=${getListMethod?.name}",
            )
            return MainTabBottomScanSymbols()
        }

        val delegateClass = addMethod.parameterTypes.firstOrNull()
        if (delegateClass == null) {
            log(logger, "mainTabBottom: add method has no delegate parameter")
            return MainTabBottomScanSymbols()
        }
        val structureGetter = resolveMainTabBottomStructureGetter(delegateClass, methodParts.getOrNull(2))
        val structureClass = structureGetter?.returnType
        if (structureGetter == null || structureClass == null) {
            log(logger, "mainTabBottom: delegate structure getter unresolved")
            return MainTabBottomScanSymbols()
        }

        val fieldParts = match.fieldName.split(",")
        val typeFieldName = resolveMainTabBottomIntFieldName(structureClass, fieldParts.getOrNull(0))
        if (typeFieldName == null) {
            log(logger, "mainTabBottom: structure type field unresolved")
            return MainTabBottomScanSymbols()
        }
        val dynamicFieldName = resolveMainTabBottomOptionalFieldName(
            clazz = structureClass,
            preferredName = fieldParts.getOrNull(1),
        ) { field ->
            field.type.name.contains("DynamicIconData")
        }
        val fragmentFieldName = resolveMainTabBottomOptionalFieldName(
            clazz = structureClass,
            preferredName = fieldParts.getOrNull(2),
        ) { field ->
            isFragmentLikeType(field.type)
        }

        val out = MainTabBottomScanSymbols(
            dataClass = targetClass.name,
            addMethod = addMethod.name,
            getListMethod = getListMethod.name,
            delegateGetStructureMethod = structureGetter.name,
            structureTypeField = typeFieldName,
            structureDynamicIconField = dynamicFieldName,
            structureFragmentField = fragmentFieldName,
        )
        log(
            logger,
            "mainTabBottom: ${out.dataClass}.${out.addMethod}/${out.getListMethod}, " +
                "delegate=${delegateClass.name}.${out.delegateGetStructureMethod}, " +
                "structure=${structureClass.name}[type=${out.structureTypeField}, " +
                "dynamic=${out.structureDynamicIconField}, fragment=${out.structureFragmentField}]",
        )
        return out
    }

    private fun resolveMainTabBottomAddMethod(clazz: Class<*>, preferredName: String?): java.lang.reflect.Method? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.returnType == Void.TYPE && method.parameterTypes.size == 1
        }
        return pickMethod(candidates, preferredName)
    }

    private fun resolveMainTabBottomGetListMethod(clazz: Class<*>, preferredName: String?): java.lang.reflect.Method? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.parameterTypes.isEmpty() && isListType(method.returnType)
        }
        return pickMethod(candidates, preferredName)
    }

    private fun resolveMainTabBottomStructureGetter(
        delegateClass: Class<*>,
        preferredName: String?,
    ): java.lang.reflect.Method? {
        val candidates = collectInstanceMethods(delegateClass).filter { method ->
            method.parameterTypes.isEmpty() &&
                !method.returnType.isPrimitive &&
                resolveMainTabBottomIntFieldName(method.returnType, preferredName = "type") != null
        }
        if (candidates.isEmpty()) return null

        if (!preferredName.isNullOrBlank()) {
            val preferred = candidates.firstOrNull { it.name == preferredName }
            if (preferred != null && !java.lang.reflect.Modifier.isAbstract(preferred.modifiers)) {
                return preferred
            }
        }

        candidates.firstOrNull {
            !java.lang.reflect.Modifier.isAbstract(it.modifiers) && it.name.startsWith("get")
        }?.let { return it }

        candidates.firstOrNull { !java.lang.reflect.Modifier.isAbstract(it.modifiers) }?.let { return it }

        return pickMethod(candidates, preferredName)
    }

    private fun resolveMainTabBottomIntFieldName(clazz: Class<*>, preferredName: String?): String? {
        val fields = collectInstanceFields(clazz).filter { field ->
            field.type == Int::class.javaPrimitiveType
        }
        if (fields.isEmpty()) return null

        fields.firstOrNull { it.name == "type" }?.let { return it.name }
        fields.firstOrNull { it.name.contains("type", ignoreCase = true) }?.let { return it.name }

        if (!preferredName.isNullOrBlank()) {
            fields.firstOrNull { it.name == preferredName }?.let { field ->
                val suspicious = field.name.contains("Res", ignoreCase = true) ||
                    field.name.contains("Drawable", ignoreCase = true) ||
                    field.name.contains("Icon", ignoreCase = true)
                if (!suspicious) return field.name
            }
        }

        return fields.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { field ->
                    when {
                        field.name.length <= 2 -> 0
                        else -> 1
                    }
                },
                { field -> if (field.name.contains("Res", ignoreCase = true)) 1 else 0 },
                { field -> if (field.name.contains("Drawable", ignoreCase = true)) 1 else 0 },
                { field -> if (field.name.contains("Icon", ignoreCase = true)) 1 else 0 },
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    private fun resolveMainTabBottomOptionalFieldName(
        clazz: Class<*>,
        preferredName: String?,
        predicate: (java.lang.reflect.Field) -> Boolean,
    ): String? {
        val fields = collectInstanceFields(clazz).filter(predicate)
        if (fields.isEmpty()) return null
        return pickFieldName(fields, preferredName)
    }

    private fun resolveListSetterMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isListType(method.parameterTypes[0])
        }
        return pickMethodName(candidates, preferredName)
    }

    private fun resolveListGetterMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.parameterTypes.isEmpty() && isListType(method.returnType)
        }
        return pickMethodName(candidates, preferredName)
    }

    private fun resolveParseMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.returnType != Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java &&
                isListType(method.returnType)
        }
        return pickMethodName(candidates, preferredName)
    }

    private fun resolveListFieldName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceFields(clazz).filter { isListType(it.type) }
        return candidates.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == preferredName) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    private fun pickMethodName(methods: List<java.lang.reflect.Method>, preferredName: String): String? {
        if (methods.isEmpty()) return null
        methods.firstOrNull { it.name == preferredName }?.let { return it.name }
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    private fun pickMethod(
        methods: List<java.lang.reflect.Method>,
        preferredName: String?,
    ): java.lang.reflect.Method? {
        if (methods.isEmpty()) return null
        if (!preferredName.isNullOrBlank()) {
            methods.firstOrNull { it.name == preferredName }?.let { return it }
        }
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { it.name.length },
                { it.name },
            ),
        )
    }

    private fun pickFieldName(fields: List<java.lang.reflect.Field>, preferredName: String?): String? {
        if (fields.isEmpty()) return null
        if (!preferredName.isNullOrBlank()) {
            fields.firstOrNull { it.name == preferredName }?.let { return it.name }
        }
        return fields.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    private fun isFragmentLikeType(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.name == "androidx.fragment.app.Fragment") return true
            current = current.superclass
        }
        return false
    }

    private fun collectInstanceFields(clazz: Class<*>): List<Field> {
        scanContext.get()?.let { return it.collectInstanceFields(clazz) }
        return collectInstanceFieldsUncached(clazz)
    }

    private fun collectInstanceFieldsUncached(clazz: Class<*>): List<Field> {
        val out = ArrayList<Field>(16)
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                out.add(field)
            }
            current = current.superclass
        }
        return out
    }

    private fun collectInstanceMethods(clazz: Class<*>): List<Method> {
        scanContext.get()?.let { return it.collectInstanceMethods(clazz) }
        return collectInstanceMethodsUncached(clazz)
    }

    private fun collectInstanceMethodsUncached(clazz: Class<*>): List<Method> {
        val out = ArrayList<Method>(24)
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                out.add(method)
            }
            current = current.superclass
        }
        return out
    }

    private fun isListType(type: Class<*>): Boolean {
        return List::class.java.isAssignableFrom(type) || ArrayList::class.java.isAssignableFrom(type)
    }

    private fun isBooleanType(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType
    }

    private fun isIntType(type: Class<*>): Boolean {
        return type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
    }

    private fun isAdapterLike(type: Class<*>): Boolean {
        val methods = collectInstanceMethods(type)
        val hasGetCount = methods.any {
            it.name == "getCount" &&
                it.parameterTypes.isEmpty() &&
                (it.returnType == Int::class.javaPrimitiveType || it.returnType == Int::class.java)
        }
        if (!hasGetCount) return false
        return methods.any {
            it.name == "notifyDataSetChanged" && it.parameterTypes.isEmpty() && it.returnType == Void.TYPE
        }
    }

    private fun isZgaScanResultValid(
        cl: ClassLoader,
        className: String,
        methodNames: List<String>,
    ): Boolean {
        if (methodNames.isEmpty()) return false
        return try {
            val cls = safeFindClass(className, cl) ?: return false
            if (cls.isInterface || java.lang.reflect.Modifier.isAbstract(cls.modifiers)) return false
            val hasIoException = methodNames.any { methodName ->
                cls.declaredMethods.any { method ->
                    method.name == methodName &&
                        java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                        !java.lang.reflect.Modifier.isAbstract(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.exceptionTypes.any { ex -> ex == java.io.IOException::class.java }
                }
            }
            if (!hasIoException) return false
            methodNames.all { methodName ->
                cls.declaredMethods.any { method ->
                    method.name == methodName &&
                        java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                        !java.lang.reflect.Modifier.isAbstract(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun listScanCandidateClasses(context: Context, cl: ClassLoader, logger: ScanLogger?): ScanCandidateSet {
        val obfuscated = ArrayList<String>(512)
        val expanded = ArrayList<String>(1024)

        fun collect(name: String) {
            if (!isTargetAppClassName(name)) return
            val shortName = name.substringAfterLast('.')
            if (isLikelyObfuscatedShortName(shortName)) {
                obfuscated.add(name)
            }
            if (isExpandedScanClassName(name)) {
                expanded.add(name)
            }
        }

        try {
            var currentCl: ClassLoader? = cl
            while (currentCl != null) {
                if (currentCl is dalvik.system.BaseDexClassLoader) {
                    val pathListField = XposedCompat.findField(currentCl::class.java, "pathList")
                    val pathList = pathListField.get(currentCl)!!
                    val dexElementsField = XposedCompat.findField(pathList::class.java, "dexElements")
                    val dexElements = dexElementsField.get(pathList) as Array<*>
                    
                    for (element in dexElements) {
                        if (element == null) continue
                        val dexFileField = try { XposedCompat.findField(element::class.java, "dexFile") } catch (_: Throwable) { null } ?: continue
                        val dexFile = dexFileField.get(element) as? DexFile ?: continue
                        val entries = dexFile.entries()
                        while (entries.hasMoreElements()) {
                            collect(entries.nextElement())
                        }
                    }
                }
                currentCl = currentCl.parent
            }
        } catch (t: Throwable) {
            log(logger, "list classes from dex path list failed: ${t.message}")
            XposedCompat.log(t)
        }
        
        if (obfuscated.isNotEmpty() || expanded.isNotEmpty()) {
            val result = ScanCandidateSet(
                obfuscated = obfuscated.distinct(),
                expanded = expanded.distinct(),
            )
            log(logger, "obfuscated root classes listed: ${result.obfuscated.size}")
            log(logger, "expanded scan classes listed: ${result.expanded.size}")
            return result
        }
        
        val sourceDir = context.applicationInfo?.sourceDir ?: return ScanCandidateSet(emptyList(), emptyList())
        var dexFile: DexFile? = null
        try {
            dexFile = DexFile(sourceDir)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                collect(entries.nextElement())
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG: list classes fallback failed: ${t.message}")
            log(logger, "list classes fallback failed: ${t.message}")
            XposedCompat.log(t)
        } finally {
            try {
                dexFile?.close()
            } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
        }
        val result = ScanCandidateSet(
            obfuscated = obfuscated.distinct(),
            expanded = expanded.distinct(),
        )
        log(logger, "obfuscated root classes listed: ${result.obfuscated.size}")
        log(logger, "expanded scan classes listed: ${result.expanded.size}")
        return result
    }

    private fun isTargetAppClassName(name: String): Boolean {
        return name.startsWith("com.baidu.tieba.") ||
            name.startsWith("com.baidu.tbadk.") ||
            name.startsWith("com.baidu.adp.widget.ListView.") ||
            name.startsWith("com.baidu.searchbox.task.view.mainactivity.")
    }

    private fun isExpandedScanClassName(name: String): Boolean {
        val shortName = name.substringAfterLast('.')
        if (shortName == "BuildConfig" || shortName == "R" || shortName.startsWith("R\$")) return false
        if (EXPANDED_SCAN_PACKAGE_PREFIXES.any { name.startsWith(it) }) return true
        return EXPANDED_SCAN_CLASS_MARKERS.any { shortName.contains(it) }
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun runRules(
        candidates: List<String>,
        cl: ClassLoader,
        rules: List<ScanRule>,
        logger: ScanLogger?,
        tag: String
    ): ScanMatch? {
        for (rule in rules) {
            val matches = ArrayList<ScanMatch>()
            var skippedByReflection = 0
            var firstReflectionError: String? = null
            for (className in candidates) {
                try {
                    val cls = safeFindClass(className, cl) ?: continue
                    val match = rule.match(cls, cl)
                    if (match != null) {
                        matches.add(match)
                    }
                } catch (t: Throwable) {
                    skippedByReflection++
                    if (firstReflectionError == null) {
                        firstReflectionError = sanitizeScanStatusText(formatScanException(t))
                    }
                }
            }
            if (skippedByReflection > 0) {
                val line = "$tag scan rule ${rule.javaClass.simpleName} skipped classes by reflection=$skippedByReflection" +
                    (firstReflectionError?.let { ", firstException=$it" } ?: "")
                try {
                    XposedCompat.logD("$TAG: $line")
                } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
                try {
                    logger?.log(line)
                } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
            }
            if (matches.isEmpty()) {
                if (skippedByReflection > 0) {
                    scanContext.get()?.scanErrors?.let { errors ->
                        recordScanIssue(
                            logger = logger,
                            tag = tag,
                            errors = errors,
                            detail = "rule ${rule.javaClass.simpleName} no semantic match; " +
                                "reflectionFailures=$skippedByReflection" +
                                (firstReflectionError?.let { ", firstException=$it" } ?: ""),
                        )
                    }
                }
                log(
                    logger,
                    "$tag scan rule ${rule.javaClass.simpleName} no semantic match among ${candidates.size} candidates",
                )
                continue
            }
            val match = chooseUniqueScanMatch(
                tag = tag,
                ruleName = rule.javaClass.simpleName,
                matches = matches,
                logger = logger,
                minScore = rule.minScore,
                minScoreGap = rule.minScoreGap,
            )
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun chooseUniqueScanMatch(
        tag: String,
        ruleName: String,
        matches: List<ScanMatch>,
        logger: ScanLogger?,
        minScore: Int,
        minScoreGap: Int,
    ): ScanMatch? {
        if (matches.isEmpty()) return null
        val sorted = matches.sortedWith(
            compareByDescending<ScanMatch> { it.score }
                .thenBy { it.className }
                .thenBy { it.methodName }
                .thenBy { it.fieldName },
        )
        val best = sorted.first()
        if (best.score < minScore) {
            log(
                logger,
                "$tag scan rule $ruleName rejected: best score=${best.score} < minScore=$minScore, " +
                    "best=${describeScanMatch(best)}",
            )
            return null
        }

        val topScoreMatches = sorted.filter { it.score == best.score }
        if (topScoreMatches.size > 1) {
            val sample = describeScanMatches(topScoreMatches)
            log(
                logger,
                "$tag scan rule $ruleName ambiguous top score=${best.score}, " +
                    "candidates=${topScoreMatches.size}: $sample",
            )
            return null
        }

        val second = sorted.getOrNull(1)
        if (second != null && minScoreGap > 0) {
            val gap = best.score - second.score
            if (gap < minScoreGap) {
                log(
                    logger,
                    "$tag scan rule $ruleName ambiguous close score: best=${describeScanMatch(best)}, " +
                        "second=${describeScanMatch(second)}, gap=$gap < minGap=$minScoreGap",
                )
                return null
            }
        }

        log(logger, "$tag matched by $ruleName: ${best.className}.${best.methodName} score=${best.score}")
        return best
    }

    private fun describeScanMatches(matches: List<ScanMatch>): String {
        return matches.take(5).joinToString("; ") { describeScanMatch(it) }
    }

    private fun describeScanMatch(match: ScanMatch): String {
        return "${match.className}.${match.methodName}/${match.fieldName}:${match.score}"
    }

    private fun isUsable(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!isSettingsValid(symbols, cl)) return false
        val hasHomeSymbols =
            symbols.homeTabClass != null ||
                symbols.homeTabRebuildMethod != null ||
                symbols.homeTabListField != null
        if (hasHomeSymbols && !isHomeValid(symbols, cl)) return false
        val hasHomeRightSlotSymbols =
            symbols.homeRightSlotClass != null ||
                symbols.homeRightSlotStateMethods != null
        if (hasHomeRightSlotSymbols && !isHomeRightSlotValid(symbols, cl)) return false
        val hasPbFallingSymbols =
            symbols.pbFallingViewClass != null ||
                symbols.pbFallingInitMethod != null ||
                symbols.pbFallingShowMethod != null ||
                symbols.pbFallingClearMethod != null
        if (hasPbFallingSymbols && !isPbFallingValid(symbols, cl)) return false
        val hasPbEarlyAdInsertSymbols =
            symbols.pbEarlyAdInsertClass != null ||
                symbols.pbEarlyAdInsertMethodSpecs != null
        if (hasPbEarlyAdInsertSymbols && !isPbEarlyAdInsertValid(symbols, cl)) return false
        val hasPbAdBidSymbols =
            symbols.pbAdBidCommonRequestModelClass != null ||
                symbols.pbAdBidCommonRequestStartMethods != null ||
                symbols.pbAdBidCommonRequestNotifyMethod != null ||
                symbols.pbAdBidPageBrowserRequestModelClass != null ||
                symbols.pbAdBidPageBrowserRequestDataMethod != null
        if (hasPbAdBidSymbols && !isPbAdBidValid(symbols, cl)) return false
        val hasTypeAdapterDataFilterSymbols =
            symbols.typeAdapterSetDataMethod != null ||
                symbols.typeAdapterDataItemClass != null ||
                symbols.typeAdapterDataGetTypeMethod != null
        if (hasTypeAdapterDataFilterSymbols && !isTypeAdapterDataFilterValid(symbols, cl)) return false
        val hasEnterForumWebSymbols =
            symbols.enterForumWebControllerClass != null ||
                symbols.enterForumWebLoadMethod != null
        if (hasEnterForumWebSymbols && !isEnterForumWebValid(symbols, cl)) return false
        val hasEnterForumInitInfoSymbols =
            symbols.enterForumInitInfoDataClass != null ||
                symbols.enterForumInitInfoGetUrlMethod != null
        if (hasEnterForumInitInfoSymbols && !isEnterForumInitInfoValid(symbols, cl)) return false
        val hasPlainUrlClickableSpanSymbols =
                symbols.plainUrlClickableSpanClass != null ||
                symbols.plainUrlClickableSpanOnClickMethod != null ||
                !symbols.plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty() ||
                symbols.plainUrlClickableSpanTypeField != null ||
                symbols.plainUrlClickableSpanUrlField != null ||
                symbols.plainUrlClickableSpanTextField != null ||
                symbols.plainUrlMessageManagerClass != null ||
                symbols.plainUrlMessageDispatchMethod != null ||
                symbols.plainUrlResponsedMessageClass != null ||
                symbols.plainUrlResponsedMessageGetCmdMethod != null ||
                symbols.plainUrlCustomResponsedMessageClass != null ||
                symbols.plainUrlCustomResponsedMessageGetDataMethod != null ||
                symbols.plainUrlApplicationClass != null ||
                symbols.plainUrlApplicationGetInstMethod != null
        if (hasPlainUrlClickableSpanSymbols && !isPlainUrlClickableSpanValid(symbols, cl)) return false
        val hasPrivateReadReceiptSymbols =
                symbols.privateReadReceiptModelClass != null ||
                symbols.privateReadReceiptModelReadDispatchMethod != null ||
                symbols.privateReadReceiptMessageManagerClass != null ||
                symbols.privateReadReceiptMessageManagerGetInstanceMethod != null ||
                symbols.privateReadReceiptMessageSendMethod != null ||
                symbols.privateReadReceiptRequestClass != null ||
                symbols.privateReadReceiptModelBaseClass != null ||
                symbols.privateReadReceiptCommitResponseClass != null ||
                symbols.privateReadReceiptProcessAckMethod != null ||
                symbols.privateReadReceiptResponseErrorMethod != null ||
                symbols.privateReadReceiptRequestMsgIdField != null ||
                symbols.privateReadReceiptRequestToUidField != null ||
                symbols.privateReadReceiptModelDataField != null ||
                symbols.privateReadReceiptPageDataClass != null ||
                symbols.privateReadReceiptPageDataChatListMethod != null ||
                symbols.privateReadReceiptChatMessageClass != null ||
                symbols.privateReadReceiptChatMessageMsgIdMethod != null ||
                symbols.privateReadReceiptChatMessageUserIdMethod != null ||
                symbols.privateReadReceiptChatMessageLocalDataMethod != null ||
                symbols.privateReadReceiptLocalDataClass != null ||
                symbols.privateReadReceiptLocalDataStatusMethod != null ||
                symbols.privateReadReceiptAccountClass != null ||
                symbols.privateReadReceiptCurrentAccountMethod != null
        if (hasPrivateReadReceiptSymbols && !isPrivateReadReceiptValid(symbols, cl)) return false
        val hasMountCardLinkSymbols =
            symbols.mountCardLinkLayoutClass != null ||
                symbols.mountCardLinkLayoutOnClickMethod != null ||
                symbols.mountCardLinkLayoutDataField != null ||
                symbols.mountCardLinkInfoDataClass != null ||
                symbols.mountCardLinkInfoGetUrlMethod != null
        if (hasMountCardLinkSymbols && !isMountCardLinkLayoutValid(symbols, cl)) return false
        val hasForumBottomSheetSymbols =
            symbols.forumBottomSheetViewClass != null ||
                symbols.forumBottomSheetInitScrollMethod != null
        if (hasForumBottomSheetSymbols && !isForumBottomSheetValid(symbols, cl)) return false
        val hasAutoRefreshSymbols = symbols.autoRefreshTriggerMethod != null
        if (hasAutoRefreshSymbols && !isAutoRefreshValid(symbols, cl)) return false
        val hasAutoLoadMoreSymbols =
            symbols.autoLoadMoreConfigClass != null ||
                symbols.autoLoadMoreConfigMethod != null
        if (hasAutoLoadMoreSymbols && !isAutoLoadMoreConfigValid(symbols, cl)) return false
        val hasPbCommentScrollSymbols =
            symbols.pbCommentScrollListenerClass != null ||
                symbols.pbCommentScrollMethod != null ||
                symbols.pbCommentScrollFragmentField != null ||
                symbols.pbCommentScrollBottomListenerField != null ||
                symbols.pbCommentScrollBottomMethod != null
        if (hasPbCommentScrollSymbols && !isPbCommentScrollValid(symbols, cl)) return false
        val hasPbCommentBottomSymbols =
            symbols.pbCommentBottomListScrollClass != null ||
                symbols.pbCommentBottomListScrollMethod != null ||
                symbols.pbCommentBottomListOwnerField != null ||
                symbols.pbCommentBottomRecyclerScrollClass != null ||
                symbols.pbCommentBottomRecyclerScrollMethod != null ||
                symbols.pbCommentBottomRecyclerOwnerField != null
        if (hasPbCommentBottomSymbols && !isPbCommentBottomMechanismValid(symbols, cl)) return false
        val hasHomeNativeGlassSortSwitchSymbols =
            symbols.homeNativeGlassSortSwitchBackgroundPaintField != null ||
                symbols.homeNativeGlassSortSwitchSlideDrawMethod != null ||
                symbols.homeNativeGlassSortSwitchSlidePathField != null
        if (hasHomeNativeGlassSortSwitchSymbols && !isHomeNativeGlassSortSwitchValid(symbols, cl)) {
            return false
        }
        val hasHomeNativeGlassEnterForumCapsuleSymbols =
            symbols.homeNativeGlassEnterForumCapsuleControllerClass != null ||
                symbols.homeNativeGlassEnterForumCapsuleInitMethod != null ||
                symbols.homeNativeGlassEnterForumCapsuleRefreshMethod != null ||
                symbols.homeNativeGlassEnterForumCapsuleViewField != null ||
                symbols.homeNativeGlassEnterForumCapsuleTitleField != null
        if (
            hasHomeNativeGlassEnterForumCapsuleSymbols &&
            !isHomeNativeGlassEnterForumCapsuleValid(symbols, cl)
        ) {
            return false
        }
        val hasPbGestureScaleSymbols =
            symbols.pbGestureScaleManagerClass != null ||
                symbols.pbGestureScaleDispatchMethod != null ||
                symbols.pbGestureScaleListenerSetterMethod != null ||
                symbols.pbGestureScaleListenerClass != null ||
                symbols.pbGestureScaleListenerOnScaleMethod != null
        if (hasPbGestureScaleSymbols && !isPbGestureScaleValid(symbols, cl)) return false
        val hasAiComponentSymbols =
            symbols.aiSpriteMemePanControllerClass != null ||
                symbols.aiSpriteMemeEnableMethod != null ||
                symbols.aiPbNewInputContainerClass != null ||
                symbols.aiPbNewInputContainerInitSpriteMemeMethod != null ||
                symbols.aiPbNewInputContainerInitAiWriteMethod != null ||
                symbols.aiPbAiEmojiCreationViewBindMethod != null ||
                symbols.aiPbPageBrowserAiEmojiCreationBindMethod != null ||
                symbols.aiImageViewerJumpButtonOwnerClass != null ||
                symbols.aiImageViewerJumpButtonInitMethod != null
        if (hasAiComponentSymbols && !isAiComponentValid(symbols, cl)) return false
        val hasCollectionSearchSymbols =
            symbols.collectionPresenterField != null ||
                symbols.collectionPresenterListSetterMethod != null ||
                symbols.collectionModelField != null ||
                symbols.collectionModelListGetterMethod != null ||
                symbols.collectionModelParseMethod != null ||
                symbols.collectionModelListField != null ||
                symbols.collectionFragmentDisplayListField != null
        if (hasCollectionSearchSymbols && symbols.collectionNavBarField.isNullOrBlank()) return false
        val hasHistorySearchSymbols =
            symbols.historyAdapterField != null ||
                symbols.historyAdapterSetListMethod != null ||
                symbols.historyListField != null
        if (hasHistorySearchSymbols && symbols.historyActivityNavBarField.isNullOrBlank()) return false
        val hasMainTabBottomSymbols =
            symbols.mainTabDataClass != null ||
                symbols.mainTabAddMethod != null ||
                symbols.mainTabGetListMethod != null ||
                symbols.mainTabDelegateGetStructureMethod != null ||
                symbols.mainTabStructureTypeField != null ||
                symbols.mainTabStructureDynamicIconField != null ||
                symbols.mainTabStructureFragmentField != null
        if (hasMainTabBottomSymbols && !isMainTabBottomValid(symbols, cl)) return false
        val hasShareTrackSymbols =
            symbols.shareTrackBuilderClass != null ||
                symbols.shareTrackBuildUrlMethod != null ||
                symbols.shareTrackAppendQueryMethod != null
        if (hasShareTrackSymbols && !isShareTrackValid(symbols, cl)) return false
        val hasFreeCopyPopupSymbols =
            symbols.freeCopyPopupMenuClass != null ||
                symbols.freeCopyPopupContentViewMethod != null ||
                symbols.freeCopyPopupTextField != null
        if (hasFreeCopyPopupSymbols && !isFreeCopyPopupValid(symbols, cl)) return false
        val hasCompleteImageViewerShareSymbols =
            symbols.imageViewerShareConfigClass != null &&
                symbols.imageViewerShareIsDialogField != null &&
                symbols.imageViewerShareItemField != null &&
                symbols.imageViewerShareAddOutsideMethod != null &&
                symbols.imageViewerShareGetRequestDataMethod != null &&
                symbols.imageViewerShareSetRequestDataMethod != null &&
                symbols.imageViewerShareGetContextMethod != null &&
                symbols.imageViewerShareItemClass != null &&
                symbols.imageViewerShareItemImageUriField != null &&
                symbols.imageViewerShareItemViewClass != null &&
                symbols.imageViewerShareItemNameByResMethod != null &&
                symbols.imageViewerShareItemNameByTextMethod != null
        if (hasCompleteImageViewerShareSymbols && !isImageViewerShareValid(symbols, cl)) return false
        val hasOriginalImageSymbols =
            symbols.origImagePagerAdapterClass != null ||
                symbols.origImageUrlDragImageViewClass != null ||
                symbols.origImageDataClass != null ||
                symbols.origImageTriggerMethod != null ||
                symbols.origImageAssistDataMethod != null
        if (hasOriginalImageSymbols && !isOriginalImageValid(symbols, cl)) return false
        return true
    }

    private fun isFreeCopyPopupValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val menuClassName = symbols.freeCopyPopupMenuClass ?: return false
        val contentViewMethodName = symbols.freeCopyPopupContentViewMethod ?: return false
        val textFieldName = symbols.freeCopyPopupTextField ?: return false
        return try {
            val menuClass = safeFindClass(menuClassName, cl) ?: return false
            val hasContentViewMethod = menuClass.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == contentViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    View::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasContentViewMethod) return false
            val emTextViewClass = safeFindClass(FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS, cl)
            menuClass.declaredFields.any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.name == textFieldName &&
                    (
                        TextView::class.java.isAssignableFrom(field.type) ||
                            emTextViewClass?.isAssignableFrom(field.type) == true
                    )
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeNativeGlassEnterForumCapsuleValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClassName = symbols.homeNativeGlassEnterForumCapsuleControllerClass ?: return false
        val initMethodName = symbols.homeNativeGlassEnterForumCapsuleInitMethod ?: return false
        val refreshMethodName = symbols.homeNativeGlassEnterForumCapsuleRefreshMethod ?: return false
        val viewFieldName = symbols.homeNativeGlassEnterForumCapsuleViewField ?: return false
        val titleFieldName = symbols.homeNativeGlassEnterForumCapsuleTitleField ?: return false
        return try {
            val controllerClass = safeFindClass(controllerClassName, cl) ?: return false
            val fields = collectInstanceFields(controllerClass)
            val viewFieldOk = fields.any { field ->
                field.name == viewFieldName &&
                    View::class.java.isAssignableFrom(field.type)
            }
            if (!viewFieldOk) return false
            val titleFieldOk = fields.any { field ->
                field.name == titleFieldName &&
                    field.type == String::class.java
            }
            if (!titleFieldOk) return false
            val methods = collectInstanceMethods(controllerClass)
            val initMethodOk = methods.any { method ->
                method.name == initMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
            if (!initMethodOk) return false
            methods.any { method ->
                method.name == refreshMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeNativeGlassSortSwitchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val sortSwitchClass = safeFindClass(StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS, cl) ?: return false
        return try {
            val backgroundPaintFieldName = symbols.homeNativeGlassSortSwitchBackgroundPaintField
            if (!backgroundPaintFieldName.isNullOrBlank()) {
                val field = sortSwitchClass.getDeclaredField(backgroundPaintFieldName)
                if (field.type.name != "android.graphics.Paint" || Modifier.isStatic(field.modifiers)) {
                    return false
                }
            }
            val slidePathFieldName = symbols.homeNativeGlassSortSwitchSlidePathField
            if (!slidePathFieldName.isNullOrBlank()) {
                val field = sortSwitchClass.getDeclaredField(slidePathFieldName)
                if (field.type.name != "android.graphics.Path" || Modifier.isStatic(field.modifiers)) {
                    return false
                }
            }
            val slideDrawMethodName = symbols.homeNativeGlassSortSwitchSlideDrawMethod
            if (!slideDrawMethodName.isNullOrBlank()) {
                val method = sortSwitchClass.getDeclaredMethod(
                    slideDrawMethodName,
                    android.graphics.Canvas::class.java,
                )
                if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                    return false
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (symbols.homeTabClass == null || symbols.homeTabRebuildMethod == null || symbols.homeTabListField == null) return false
        return try {
            val homeClass = safeFindClass(symbols.homeTabClass, cl) ?: return false
            val methodOk = homeClass.declaredMethods.any {
                it.name == symbols.homeTabRebuildMethod &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Void.TYPE
            }
            if (!methodOk) return false
            val listFieldOk = homeClass.declaredFields.any { it.name == symbols.homeTabListField }
            if (!listFieldOk) return false

            val hasHomeItemSymbols =
                !symbols.homeTabItemTypeField.isNullOrBlank() ||
                    !symbols.homeTabItemCodeField.isNullOrBlank() ||
                    !symbols.homeTabItemNameField.isNullOrBlank() ||
                    !symbols.homeTabItemUrlField.isNullOrBlank() ||
                    !symbols.homeTabItemMainSetterMethod.isNullOrBlank() ||
                    !symbols.homeTabItemMainIntField.isNullOrBlank() ||
                    !symbols.homeTabItemMainBooleanField.isNullOrBlank()
            if (!hasHomeItemSymbols) return true

            val itemClass = resolveHomeTabItemClass(homeClass, symbols.homeTabListField) ?: return false
            val fields = collectInstanceFields(itemClass)
            val methods = collectInstanceMethods(itemClass)

            if (!symbols.homeTabItemTypeField.isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols.homeTabItemTypeField &&
                        (it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType)
                }
                if (!ok) return false
            }
            if (!symbols.homeTabItemCodeField.isNullOrBlank()) {
                val ok = fields.any { it.name == symbols.homeTabItemCodeField && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols.homeTabItemNameField.isNullOrBlank()) {
                val ok = fields.any { it.name == symbols.homeTabItemNameField && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols.homeTabItemUrlField.isNullOrBlank()) {
                val ok = fields.any { it.name == symbols.homeTabItemUrlField && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols.homeTabItemMainSetterMethod.isNullOrBlank()) {
                val ok = methods.any { method ->
                    method.name == symbols.homeTabItemMainSetterMethod &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        (method.parameterTypes[0] == Boolean::class.javaPrimitiveType || method.parameterTypes[0] == Boolean::class.java)
                }
                if (!ok) return false
            }
            if (!symbols.homeTabItemMainIntField.isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols.homeTabItemMainIntField &&
                        (it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType)
                }
                if (!ok) return false
            }
            if (!symbols.homeTabItemMainBooleanField.isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols.homeTabItemMainBooleanField &&
                        (it.type == Boolean::class.javaPrimitiveType || it.type == Boolean::class.java)
                }
                if (!ok) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isSettingsValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (symbols.settingsClass == null || symbols.settingsInitMethod == null || symbols.settingsContainerField == null) return false
        return try {
            val settingsClass = safeFindClass(symbols.settingsClass, cl) ?: return false
            val navClass = safeFindClass(NAV_CLASS, cl) ?: return false
            val methodOk = settingsClass.declaredMethods.any { method ->
                if (method.name != symbols.settingsInitMethod) return@any false
                if (method.returnType != Void.TYPE) return@any false
                val p = method.parameterTypes
                (p.size == 2 && Context::class.java.isAssignableFrom(p[0]) && navClass.isAssignableFrom(p[1])) ||
                    (p.size == 1 && View::class.java.isAssignableFrom(p[0]))
            }
            if (!methodOk) return false
            settingsClass.declaredFields.any { it.name == symbols.settingsContainerField }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeRightSlotValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.homeRightSlotClass ?: return false
        val stateMethods = symbols.homeRightSlotStateMethods.orEmpty()
        if (stateMethods.isEmpty()) return false
        return try {
            val slotClass = safeFindClass(className, cl) ?: return false
            stateMethods.all { methodName ->
                slotClass.declaredMethods.any { method ->
                    method.name == methodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty()
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbFallingValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.pbFallingViewClass ?: return false
        val initMethod = symbols.pbFallingInitMethod ?: return false
        val showMethod = symbols.pbFallingShowMethod ?: return false
        val clearMethod = symbols.pbFallingClearMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            val hasInit = targetClass.declaredMethods.any { method ->
                method.name == initMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            if (!hasInit) return false
            val hasShow = targetClass.declaredMethods.any { method ->
                method.name == showMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            if (!hasShow) return false
            targetClass.declaredMethods.any { method ->
                method.name == clearMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbEarlyAdInsertValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.pbEarlyAdInsertClass ?: return false
        val specs = symbols.pbEarlyAdInsertMethodSpecs.orEmpty()
        if (specs.isEmpty()) return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            specs.all { spec ->
                val parts = spec.split('|', limit = 3)
                if (parts.size != 3) return@all false
                val methodName = parts[0]
                val returnType = resolvePbEarlyAdType(parts[1], cl) ?: return@all false
                val paramTypes = if (parts[2].isBlank()) {
                    emptyArray<Class<*>>()
                } else {
                    parts[2].split(',').map { typeName ->
                        resolvePbEarlyAdType(typeName, cl) ?: return@all false
                    }.toTypedArray()
                }
                val method = targetClass.getDeclaredMethod(methodName, *paramTypes)
                Modifier.isStatic(method.modifiers) &&
                    (method.returnType == returnType || returnType.isAssignableFrom(method.returnType))
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbAdBidValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val commonStartMethods = symbols.pbAdBidCommonRequestStartMethods.orEmpty()
        val hasCompleteCommonSymbols =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                commonStartMethods.isNotEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val hasCompletePageBrowserSymbols =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        return try {
            if (hasCompleteCommonSymbols) {
                val commonBaseClass = safeFindClass(PB_COMMON_REQUEST_MODEL_CLASS, cl) ?: return false
                val commonModelClass = safeFindClass(symbols.pbAdBidCommonRequestModelClass!!, cl) ?: return false
                if (!commonBaseClass.isAssignableFrom(commonModelClass)) return false

                val hasAllStartMethods = commonStartMethods.all { methodName ->
                    commonBaseClass.declaredMethods.any { method ->
                        method.name == methodName &&
                            !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Void.TYPE &&
                            method.parameterTypes.isEmpty()
                    }
                }
                if (!hasAllStartMethods) return false

                val hasNotifyMethod = commonBaseClass.declaredMethods.any { method ->
                    method.name == symbols.pbAdBidCommonRequestNotifyMethod &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        isIntType(method.parameterTypes[0])
                }
                if (!hasNotifyMethod) return false
            }

            if (hasCompletePageBrowserSymbols) {
                val pageBrowserModelClass = safeFindClass(symbols.pbAdBidPageBrowserRequestModelClass!!, cl)
                    ?: return false
                val continuationClass = safeFindClass(KOTLIN_CONTINUATION_CLASS, cl) ?: return false
                val method = findPbAdBidPageBrowserRequestDataMethodInHierarchy(
                    pageBrowserModelClass,
                    continuationClass,
                ) ?: return false
                if (method.name != symbols.pbAdBidPageBrowserRequestDataMethod) return false
            }

            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isTypeAdapterDataFilterValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val setDataMethodName = symbols.typeAdapterSetDataMethod ?: return false
        val dataItemClassName = symbols.typeAdapterDataItemClass ?: return false
        val dataGetTypeMethodName = symbols.typeAdapterDataGetTypeMethod ?: return false
        return try {
            val typeAdapterClass = safeFindClass(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl) ?: return false
            val dataItemClass = safeFindClass(dataItemClassName, cl) ?: return false
            val bdUniqueIdClass = safeFindClass(BD_UNIQUE_ID_CLASS, cl) ?: return false

            val hasSetDataMethod = typeAdapterClass.declaredMethods.any { method ->
                method.name == setDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    isListType(method.parameterTypes[0])
            }
            if (!hasSetDataMethod) return false

            dataItemClass.methods.any { method ->
                method.name == dataGetTypeMethodName &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isEnterForumWebValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.enterForumWebControllerClass ?: return false
        val methodName = symbols.enterForumWebLoadMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            if (!hasEnterForumMainWebControllerSignal(targetClass)) return false
            val tbWebViewClass = safeFindClass(TB_WEB_VIEW_CLASS, cl) ?: return false
            val hasWebViewGetter = targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    tbWebViewClass.isAssignableFrom(method.returnType)
            }
            if (!hasWebViewGetter) return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasEnterForumMainWebControllerSignal(targetClass: Class<*>): Boolean {
        val hasUniqueIdField = targetClass.declaredFields.any { field ->
            field.type.name == BD_UNIQUE_ID_CLASS
        }
        val hasUniqueIdConstructor = targetClass.declaredConstructors.any { constructor ->
            constructor.parameterTypes.any { it.name == BD_UNIQUE_ID_CLASS }
        }
        return hasUniqueIdField || hasUniqueIdConstructor
    }

    private fun isEnterForumInitInfoValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.enterForumInitInfoDataClass ?: return false
        val methodName = symbols.enterForumInitInfoGetUrlMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            targetClass.methods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlainUrlClickableSpanValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return isPlainUrlClickableSpanDirectValid(symbols, cl) ||
            isPlainUrlMessageDispatchValid(symbols, cl)
    }

    private fun isPlainUrlClickableSpanDirectValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.plainUrlClickableSpanClass ?: return false
        val methodName = symbols.plainUrlClickableSpanOnClickMethod ?: return false
        val ownerClassNames = symbols.plainUrlClickableSpanOnClickOwnerClasses.orEmpty()
        if (ownerClassNames.isEmpty()) return false
        val typeFieldName = symbols.plainUrlClickableSpanTypeField ?: return false
        val urlFieldName = symbols.plainUrlClickableSpanUrlField ?: return false
        val textFieldName = symbols.plainUrlClickableSpanTextField ?: return false
        return try {
            val spanClass = safeFindClass(className, cl) ?: return false
            val onClickMethods = ownerClassNames.mapNotNull { ownerClassName ->
                val ownerClass = safeFindClass(ownerClassName, cl) ?: return@mapNotNull null
                if (!spanClass.isAssignableFrom(ownerClass)) return@mapNotNull null
                ownerClass.declaredMethods.singleOrNull { method ->
                    isPlainUrlClickableSpanOnClickMethod(method, methodName)
                }
            }
            if (onClickMethods.size != ownerClassNames.size || onClickMethods.isEmpty()) return false
            val fields = collectInstanceFields(spanClass)
            val typeField = fields.singleOrNull { field ->
                field.name == typeFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    isIntType(field.type)
            } ?: return false
            val urlField = fields.singleOrNull { field ->
                field.name == urlFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: return false
            val textField = fields.singleOrNull { field ->
                field.name == textFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: return false
            isPlainUrlClickableSpanStructureValid(spanClass, onClickMethods.first(), typeField, urlField, textField)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlainUrlMessageDispatchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val messageManagerClassName = symbols.plainUrlMessageManagerClass ?: return false
        val dispatchMethodName = symbols.plainUrlMessageDispatchMethod ?: return false
        val responsedMessageClassName = symbols.plainUrlResponsedMessageClass ?: return false
        val getCmdMethodName = symbols.plainUrlResponsedMessageGetCmdMethod ?: return false
        val customResponsedMessageClassName = symbols.plainUrlCustomResponsedMessageClass ?: return false
        val getDataMethodName = symbols.plainUrlCustomResponsedMessageGetDataMethod ?: return false
        val applicationClassName = symbols.plainUrlApplicationClass ?: return false
        val getInstMethodName = symbols.plainUrlApplicationGetInstMethod ?: return false
        return try {
            val messageManagerClass = safeFindClass(messageManagerClassName, cl) ?: return false
            val responsedMessageClass = safeFindClass(responsedMessageClassName, cl) ?: return false
            val customResponsedMessageClass = safeFindClass(customResponsedMessageClassName, cl) ?: return false
            val applicationClass = safeFindClass(applicationClassName, cl) ?: return false
            if (!responsedMessageClass.isAssignableFrom(customResponsedMessageClass)) return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == dispatchMethodName &&
                    isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
            } ?: return false
            responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getCmdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isIntType(method.returnType)
            } ?: return false
            customResponsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: return false
            applicationClass.declaredMethods.singleOrNull { method ->
                method.name == getInstMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    applicationClass.isAssignableFrom(method.returnType)
            } != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPrivateReadReceiptValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val modelClassName = symbols.privateReadReceiptModelClass ?: return false
        val modelReadMethodName = symbols.privateReadReceiptModelReadDispatchMethod ?: return false
        val messageManagerClassName = symbols.privateReadReceiptMessageManagerClass ?: return false
        val messageManagerGetInstanceMethodName = symbols.privateReadReceiptMessageManagerGetInstanceMethod ?: return false
        val messageSendMethodName = symbols.privateReadReceiptMessageSendMethod ?: return false
        val messageBaseClassName = symbols.privateReadReceiptMessageBaseClass ?: return false
        val requestClassName = symbols.privateReadReceiptRequestClass ?: return false
        val modelBaseClassName = symbols.privateReadReceiptModelBaseClass ?: return false
        val commitResponseClassName = symbols.privateReadReceiptCommitResponseClass ?: return false
        val processAckMethodName = symbols.privateReadReceiptProcessAckMethod ?: return false
        val responseErrorMethodName = symbols.privateReadReceiptResponseErrorMethod ?: return false
        val requestMsgIdFieldName = symbols.privateReadReceiptRequestMsgIdField ?: return false
        val requestToUidFieldName = symbols.privateReadReceiptRequestToUidField ?: return false
        val modelDataFieldName = symbols.privateReadReceiptModelDataField ?: return false
        val pageDataClassName = symbols.privateReadReceiptPageDataClass ?: return false
        val pageDataChatListMethodName = symbols.privateReadReceiptPageDataChatListMethod ?: return false
        val chatMessageClassName = symbols.privateReadReceiptChatMessageClass ?: return false
        val chatMsgIdMethodName = symbols.privateReadReceiptChatMessageMsgIdMethod ?: return false
        val chatUserIdMethodName = symbols.privateReadReceiptChatMessageUserIdMethod ?: return false
        val chatLocalDataMethodName = symbols.privateReadReceiptChatMessageLocalDataMethod ?: return false
        val localDataClassName = symbols.privateReadReceiptLocalDataClass ?: return false
        val localDataStatusMethodName = symbols.privateReadReceiptLocalDataStatusMethod ?: return false
        val accountClassName = symbols.privateReadReceiptAccountClass ?: return false
        val currentAccountMethodName = symbols.privateReadReceiptCurrentAccountMethod ?: return false
        return try {
            val modelClass = safeFindClass(modelClassName, cl) ?: return false
            val messageManagerClass = safeFindClass(messageManagerClassName, cl) ?: return false
            val messageBaseClass = safeFindClass(messageBaseClassName, cl) ?: return false
            val requestClass = safeFindClass(requestClassName, cl) ?: return false
            requestClass.declaredConstructors.singleOrNull { ctor ->
                ctor.parameterTypes.size == 2 &&
                    ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    ctor.parameterTypes[1] == Long::class.javaPrimitiveType
            } ?: return false
            val modelBaseClass = safeFindClass(modelBaseClassName, cl) ?: return false
            val commitResponseClass = safeFindClass(commitResponseClassName, cl) ?: return false
            val pageDataClass = safeFindClass(pageDataClassName, cl) ?: return false
            val chatMessageClass = safeFindClass(chatMessageClassName, cl) ?: return false
            val localDataClass = safeFindClass(localDataClassName, cl) ?: return false
            val accountClass = safeFindClass(accountClassName, cl) ?: return false
            if (!messageBaseClass.isAssignableFrom(requestClass)) return false
            modelClass.declaredMethods.singleOrNull { method ->
                method.name == modelReadMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            } ?: return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageSendMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageBaseClass
            } ?: return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == messageManagerClass
            } ?: return false
            modelBaseClass.declaredMethods.singleOrNull { method ->
                method.name == processAckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == commitResponseClass
            } ?: return false
            collectInstanceMethods(commitResponseClass).singleOrNull { method ->
                method.name == responseErrorMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isIntType(method.returnType)
            } ?: return false
            val requestFields = collectInstanceFields(requestClass)
            requestFields.singleOrNull {
                it.name == requestMsgIdFieldName && it.type == Long::class.javaPrimitiveType
            } ?: return false
            requestFields.singleOrNull {
                it.name == requestToUidFieldName && it.type == Long::class.javaPrimitiveType
            } ?: return false
            collectInstanceFields(modelClass).singleOrNull {
                it.name == modelDataFieldName && it.type == pageDataClass
            } ?: return false
            pageDataClass.declaredMethods.singleOrNull { method ->
                method.name == pageDataChatListMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isListType(method.returnType)
            } ?: return false
            chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatMsgIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return false
            chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatUserIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return false
            chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatLocalDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == localDataClass
            } ?: return false
            localDataClass.declaredMethods.singleOrNull { method ->
                method.name == localDataStatusMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Short::class.javaObjectType
            } ?: return false
            accountClass.declaredMethods.singleOrNull { method ->
                method.name == currentAccountMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == String::class.java
            } != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun isMountCardLinkLayoutValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val layoutClassName = symbols.mountCardLinkLayoutClass ?: return false
        val onClickMethodName = symbols.mountCardLinkLayoutOnClickMethod ?: return false
        val dataFieldName = symbols.mountCardLinkLayoutDataField ?: return false
        val dataClassName = symbols.mountCardLinkInfoDataClass ?: return false
        val getUrlMethodName = symbols.mountCardLinkInfoGetUrlMethod ?: return false
        return try {
            val layoutClass = safeFindClass(layoutClassName, cl) ?: return false
            val dataClass = safeFindClass(dataClassName, cl) ?: return false
            val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
                isMountCardLinkLayoutOnClickMethod(method, onClickMethodName)
            } ?: return false
            val dataField = resolveMountCardLinkLayoutDataField(layoutClass, dataClass)
                ?.takeIf { it.name == dataFieldName } ?: return false
            val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
                method.name == getUrlMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            } ?: return false
            isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isForumBottomSheetValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.forumBottomSheetViewClass ?: return false
        val methodName = symbols.forumBottomSheetInitScrollMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAutoRefreshValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val methodName = symbols.autoRefreshTriggerMethod ?: return false
        return try {
            val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAutoLoadMoreConfigValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.autoLoadMoreConfigClass ?: return false
        val methodName = symbols.autoLoadMoreConfigMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Int::class.javaPrimitiveType
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbCommentScrollValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val listenerClassName = symbols.pbCommentScrollListenerClass ?: return false
        val scrollMethodName = symbols.pbCommentScrollMethod ?: return false
        return try {
            val listenerClass = safeFindClass(listenerClassName, cl) ?: return false
            val hasScrollMethod = listenerClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == scrollMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Int::class.javaPrimitiveType
            }
            if (!hasScrollMethod) return false

            val fragmentFieldName = symbols.pbCommentScrollFragmentField
            val bottomListenerFieldName = symbols.pbCommentScrollBottomListenerField
            val bottomMethodName = symbols.pbCommentScrollBottomMethod
            if (
                fragmentFieldName.isNullOrBlank() ||
                bottomListenerFieldName.isNullOrBlank() ||
                bottomMethodName.isNullOrBlank()
            ) {
                return true
            }

            val fragmentField = listenerClass.declaredFields.firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.name == fragmentFieldName &&
                    field.type.name == PB_FRAGMENT_CLASS
            } ?: return false
            val bottomListenerField = fragmentField.type.declaredFields.firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.name == bottomListenerFieldName
            } ?: return false
            bottomListenerField.type.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == bottomMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbCommentBottomMechanismValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val listOk = isListBottomScrollValid(
            symbols.pbCommentBottomListScrollClass,
            symbols.pbCommentBottomListScrollMethod,
            symbols.pbCommentBottomListOwnerField,
            StableTiebaHookPoints.BD_LIST_VIEW_CLASS,
            cl,
        ) { method ->
            method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == android.widget.AbsListView::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }
        val recyclerOk = isListBottomScrollValid(
            symbols.pbCommentBottomRecyclerScrollClass,
            symbols.pbCommentBottomRecyclerScrollMethod,
            symbols.pbCommentBottomRecyclerOwnerField,
            StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS,
            cl,
        ) { method ->
            val recyclerViewClass = safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
            method.parameterTypes.size == 3 &&
                recyclerViewClass != null &&
                method.parameterTypes[0] == recyclerViewClass &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        return listOk || recyclerOk
    }

    private fun isListBottomScrollValid(
        className: String?,
        methodName: String?,
        ownerFieldName: String?,
        ownerClassName: String,
        cl: ClassLoader,
        parameterCheck: (Method) -> Boolean,
    ): Boolean {
        if (className.isNullOrBlank() || methodName.isNullOrBlank() || ownerFieldName.isNullOrBlank()) {
            return false
        }
        return try {
            val targetClass = safeFindClass(className, cl)
            val ownerClass = safeFindClass(ownerClassName, cl)
            if (targetClass == null || ownerClass == null) return false
            val ownerField = targetClass.declaredFields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.name == ownerFieldName &&
                    field.type == ownerClass
            } ?: return false
            ownerField.isAccessible = true
            targetClass.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    parameterCheck(method)
            }
        } catch (t: Throwable) {
            XposedCompat.logD("$TAG: pbCommentBottom validate failed: ${sanitizeScanStatusText(formatScanException(t))}")
            false
        }
    }

    private fun isPbGestureScaleValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val managerClassName = symbols.pbGestureScaleManagerClass ?: return false
        val dispatchMethodName = symbols.pbGestureScaleDispatchMethod ?: return false
        val listenerSetterMethodName = symbols.pbGestureScaleListenerSetterMethod ?: return false
        val listenerClassName = symbols.pbGestureScaleListenerClass ?: return false
        val onScaleMethodName = symbols.pbGestureScaleListenerOnScaleMethod ?: return false
        return try {
            val managerClass = safeFindClass(managerClassName, cl) ?: return false
            val listenerClass = safeFindClass(listenerClassName, cl) ?: return false

            val hasScaleDetectorField = managerClass.declaredFields.any {
                it.type == android.view.ScaleGestureDetector::class.java
            }
            if (!hasScaleDetectorField) return false

            val dispatchMethod = managerClass.declaredMethods.any { method ->
                method.name == dispatchMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == android.view.MotionEvent::class.java
            }
            if (!dispatchMethod) return false

            val setterMethod = managerClass.declaredMethods.any { method ->
                method.name == listenerSetterMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isInterface
            }
            if (!setterMethod) return false

            val parentClass = listenerClass.superclass ?: return false
            if (!android.view.ScaleGestureDetector.SimpleOnScaleGestureListener::class.java.isAssignableFrom(parentClass)) {
                return false
            }
            listenerClass.declaredMethods.any { method ->
                method.name == onScaleMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == android.view.ScaleGestureDetector::class.java
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAiComponentValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClassName = symbols.aiSpriteMemePanControllerClass ?: return false
        val enableMethodName = symbols.aiSpriteMemeEnableMethod ?: return false
        val inputContainerClassName = symbols.aiPbNewInputContainerClass ?: return false
        val initSpriteMemeMethodName = symbols.aiPbNewInputContainerInitSpriteMemeMethod ?: return false
        val initAiWriteMethodName = symbols.aiPbNewInputContainerInitAiWriteMethod ?: return false
        return try {
            val controllerClass = safeFindClass(controllerClassName, cl) ?: return false
            val inputContainerClass = safeFindClass(inputContainerClassName, cl) ?: return false
            val inputShowTypeClass = safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
            val spriteMemePanClass = safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)
            if (!isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) return false
            val enableOk = controllerClass.declaredMethods.any { method ->
                method.name == enableMethodName && isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
            }
            if (!enableOk) return false
            val initSpriteOk = inputContainerClass.declaredMethods.any { method ->
                method.name == initSpriteMemeMethodName && isPbNewInputContextInitMethod(method)
            }
            if (!initSpriteOk) return false
            val initAiWriteOk = inputContainerClass.declaredMethods.any { method ->
                method.name == initAiWriteMethodName && isPbNewInputContextInitMethod(method)
            }
            if (!initAiWriteOk) return false

            if (!isAiPbEmojiCreationValid(symbols, cl)) return false

            val imageViewerOwnerName = symbols.aiImageViewerJumpButtonOwnerClass
            val imageViewerInitName = symbols.aiImageViewerJumpButtonInitMethod
            if (!imageViewerOwnerName.isNullOrBlank() || !imageViewerInitName.isNullOrBlank()) {
                if (imageViewerOwnerName.isNullOrBlank() || imageViewerInitName.isNullOrBlank()) return false
                val layoutClass = safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl) ?: return false
                val ownerClass = safeFindClass(imageViewerOwnerName, cl) ?: return false
                if (scoreImageViewerJumpButtonOwnerClass(ownerClass, layoutClass) <= 0) return false
                ownerClass.declaredMethods.any { method ->
                    method.name == imageViewerInitName && isImageViewerJumpButtonInitMethod(method)
                }
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAiPbEmojiCreationValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val viewBindName = symbols.aiPbAiEmojiCreationViewBindMethod
        if (!viewBindName.isNullOrBlank()) {
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: return false
            val bindOk = viewClass.declaredMethods.any { method ->
                method.name == viewBindName && isAiPbEmojiCreationViewBindMethod(method, cl)
            }
            if (!bindOk) return false
        }

        val pageBrowserBindName = symbols.aiPbPageBrowserAiEmojiCreationBindMethod
        if (!pageBrowserBindName.isNullOrBlank()) {
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS, cl) ?: return false
            val bindOk = viewClass.declaredMethods.any { method ->
                method.name == pageBrowserBindName && isPbPageBrowserAiEmojiCreationBindMethod(method)
            }
            if (!bindOk) return false
        }
        return true
    }

    private fun isMainTabBottomValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val dataClassName = symbols.mainTabDataClass ?: return false
        val addMethodName = symbols.mainTabAddMethod ?: return false
        val getListMethodName = symbols.mainTabGetListMethod ?: return false
        val structureMethodName = symbols.mainTabDelegateGetStructureMethod ?: return false
        val typeFieldName = symbols.mainTabStructureTypeField ?: return false
        return try {
            val dataClass = safeFindClass(dataClassName, cl) ?: return false
            val addMethod = dataClass.declaredMethods.firstOrNull { method ->
                method.name == addMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1
            } ?: return false
            dataClass.declaredMethods.firstOrNull { method ->
                method.name == getListMethodName &&
                    method.parameterTypes.isEmpty() &&
                    isListType(method.returnType)
            } ?: return false
            val delegateClass = addMethod.parameterTypes.firstOrNull() ?: return false
            val structureMethod = delegateClass.declaredMethods.firstOrNull { method ->
                method.name == structureMethodName &&
                    method.parameterTypes.isEmpty() &&
                    !method.returnType.isPrimitive
            } ?: return false
            val structureClass = structureMethod.returnType
            val hasTypeField = collectInstanceFields(structureClass).any { field ->
                field.name == typeFieldName && field.type == Int::class.javaPrimitiveType
            }
            if (!hasTypeField) return false
            val dynamicName = symbols.mainTabStructureDynamicIconField
            if (!dynamicName.isNullOrBlank()) {
                val hasDynamic = collectInstanceFields(structureClass).any { it.name == dynamicName }
                if (!hasDynamic) return false
            }
            val fragmentName = symbols.mainTabStructureFragmentField
            if (!fragmentName.isNullOrBlank()) {
                val hasFragment = collectInstanceFields(structureClass).any { it.name == fragmentName }
                if (!hasFragment) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isShareTrackValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols.shareTrackBuilderClass ?: return false
        val buildUrlMethodName = symbols.shareTrackBuildUrlMethod ?: return false
        return try {
            val targetClass = safeFindClass(className, cl) ?: return false
            val hasBuildUrl = targetClass.declaredMethods.any { method ->
                method.name == buildUrlMethodName &&
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            if (!hasBuildUrl) return false
            val appendMethodName = symbols.shareTrackAppendQueryMethod
            if (!appendMethodName.isNullOrBlank()) {
                val hasAppend = targetClass.declaredMethods.any { method ->
                    method.name == appendMethodName &&
                        java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == String::class.java
                }
                if (!hasAppend) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isImageViewerShareValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val configClassName = symbols.imageViewerShareConfigClass ?: return false
        val isDialogFieldName = symbols.imageViewerShareIsDialogField ?: return false
        val shareItemFieldName = symbols.imageViewerShareItemField ?: return false
        val addOutsideMethodName = symbols.imageViewerShareAddOutsideMethod ?: return false
        val getRequestDataMethodName = symbols.imageViewerShareGetRequestDataMethod ?: return false
        val setRequestDataMethodName = symbols.imageViewerShareSetRequestDataMethod ?: return false
        val getContextMethodName = symbols.imageViewerShareGetContextMethod ?: return false
        val shareItemClassName = symbols.imageViewerShareItemClass ?: return false
        val shareItemImageUriFieldName = symbols.imageViewerShareItemImageUriField ?: return false
        val itemViewClassName = symbols.imageViewerShareItemViewClass ?: return false
        val itemNameByResMethodName = symbols.imageViewerShareItemNameByResMethod ?: return false
        val itemNameByTextMethodName = symbols.imageViewerShareItemNameByTextMethod ?: return false
        return try {
            val configClass = safeFindClass(configClassName, cl) ?: return false
            val shareItemClass = safeFindClass(shareItemClassName, cl) ?: return false
            val itemViewClass = safeFindClass(itemViewClassName, cl) ?: return false
            val iconResId = symbols.imageViewerShareIconResId
            if (iconResId != null) {
                if (!isDrawableResId(iconResId)) return false
            }

            val configFields = collectInstanceFields(configClass)
            val hasDialogField = configFields.any { field ->
                field.name == isDialogFieldName && field.type == Boolean::class.javaPrimitiveType
            }
            if (!hasDialogField) return false
            val hasShareItemField = configFields.any { field ->
                field.name == shareItemFieldName && field.type.name == shareItemClassName
            }
            if (!hasShareItemField) return false
            val hasAddOutside = configClass.declaredMethods.any { method ->
                method.name == addOutsideMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == View.OnClickListener::class.java
            }
            if (!hasAddOutside) return false
            val hasGetRequestData = configClass.declaredMethods.any { method ->
                method.name == getRequestDataMethodName &&
                    method.parameterTypes.isEmpty() &&
                    Map::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasGetRequestData) return false
            val hasSetRequestData = configClass.declaredMethods.any { method ->
                method.name == setRequestDataMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    Map::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            if (!hasSetRequestData) return false
            val hasGetContext = (configClass.declaredMethods + configClass.methods).distinctBy { method ->
                "${method.name}#${method.parameterTypes.joinToString(",") { it.name }}#${method.returnType.name}"
            }.any { method ->
                method.name == getContextMethodName &&
                    method.parameterTypes.isEmpty() &&
                    Context::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasGetContext) return false

            val shareItemFields = collectInstanceFields(shareItemClass)
            val hasImageUriField = shareItemFields.any { field ->
                field.name == shareItemImageUriFieldName && field.type == android.net.Uri::class.java
            }
            if (!hasImageUriField) return false

            val localFileFieldName = symbols.imageViewerShareItemLocalFileField
            if (!localFileFieldName.isNullOrBlank()) {
                val hasLocalFileField = shareItemFields.any { field ->
                    field.name == localFileFieldName && field.type == String::class.java
                }
                if (!hasLocalFileField) return false
            }
            val imageUrlFieldName = symbols.imageViewerShareItemImageUrlField
            if (!imageUrlFieldName.isNullOrBlank()) {
                val hasImageUrlField = shareItemFields.any { field ->
                    field.name == imageUrlFieldName && field.type == String::class.java
                }
                if (!hasImageUrlField) return false
            }
            val titleFieldName = symbols.imageViewerShareItemTitleField
            if (!titleFieldName.isNullOrBlank()) {
                val hasTitleField = shareItemFields.any { field ->
                    field.name == titleFieldName && field.type == String::class.java
                }
                if (!hasTitleField) return false
            }
            val contentFieldName = symbols.imageViewerShareItemContentField
            if (!contentFieldName.isNullOrBlank()) {
                val hasContentField = shareItemFields.any { field ->
                    field.name == contentFieldName && field.type == String::class.java
                }
                if (!hasContentField) return false
            }
            val linkFieldName = symbols.imageViewerShareItemLinkUrlField
            if (!linkFieldName.isNullOrBlank()) {
                val hasLinkField = shareItemFields.any { field ->
                    field.name == linkFieldName && field.type == String::class.java
                }
                if (!hasLinkField) return false
            }

            val hasItemNameByRes = itemViewClass.declaredMethods.any { method ->
                method.name == itemNameByResMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            if (!hasItemNameByRes) return false
            itemViewClass.declaredMethods.any { method ->
                method.name == itemNameByTextMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isOriginalImageValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return try {
            val adapterClassName = symbols.origImagePagerAdapterClass
            val setPrimaryMethodName = symbols.origImageSetPrimaryItemMethod
            if (!adapterClassName.isNullOrBlank() || !setPrimaryMethodName.isNullOrBlank()) {
                if (adapterClassName.isNullOrBlank() || setPrimaryMethodName.isNullOrBlank()) return false
                val adapterClass = safeFindClass(adapterClassName, cl) ?: return false
                val hasSetPrimary = adapterClass.declaredMethods.any { method ->
                    method.name == setPrimaryMethodName &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 3 &&
                        ViewGroup::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                        method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[2] == Any::class.java
                }
                if (!hasSetPrimary) return false
            }

            val urlDragClassName = symbols.origImageUrlDragImageViewClass
            val urlDragClass = if (!urlDragClassName.isNullOrBlank()) {
                safeFindClass(urlDragClassName, cl) ?: return false
            } else {
                null
            }
            fun hasUrlDragMethod(methodName: String?, check: (Method) -> Boolean): Boolean {
                if (methodName.isNullOrBlank()) return true
                val cls = urlDragClass ?: return false
                return cls.declaredMethods.any { it.name == methodName && check(it) }
            }

            if (!hasUrlDragMethod(symbols.origImagePrimaryReadyMethod) {
                    it.returnType == Void.TYPE && it.parameterTypes.isEmpty()
                }) return false
            if (!hasUrlDragMethod(symbols.origImageTriggerMethod) {
                    it.returnType == Void.TYPE && it.parameterTypes.isEmpty()
                }) return false
            if (!hasUrlDragMethod(symbols.origImageDirectStartMethod) {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
                }) return false
            if (!hasUrlDragMethod(symbols.origImageSetAssistUrlMethod) {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == symbols.origImageDataClass
                }) return false

            val dataClassName = symbols.origImageDataClass
            val dataClass = if (!dataClassName.isNullOrBlank()) {
                safeFindClass(dataClassName, cl) ?: return false
            } else {
                null
            }
            if (!hasUrlDragMethod(symbols.origImageAssistDataMethod) {
                    it.parameterTypes.isEmpty() && it.returnType.name == dataClassName
                }) return false
            if (!hasUrlDragMethod(symbols.origImageOriginTextMethod) {
                    it.parameterTypes.isEmpty() && it.returnType == String::class.java
                }) return false

            val dataFields = dataClass?.let(::collectInstanceFields).orEmpty()
            fun hasDataField(fieldName: String?, check: (Class<*>) -> Boolean): Boolean {
                if (fieldName.isNullOrBlank()) return true
                if (dataClass == null) return false
                return dataFields.any { it.name == fieldName && check(it.type) }
            }
            if (!hasDataField(symbols.origImageShowButtonField, ::isBooleanType)) return false
            if (!hasDataField(symbols.origImageBlockedField, ::isBooleanType)) return false
            if (!hasDataField(symbols.origImageOriginalProcessField, ::isIntType)) return false
            if (!hasDataField(symbols.origImageOriginalUrlField) { it == String::class.java }) return false

            val sharedPrefClassName = symbols.origImageSharedPrefHelperClass
            val sharedPrefClass = if (!sharedPrefClassName.isNullOrBlank()) {
                safeFindClass(sharedPrefClassName, cl) ?: return false
            } else {
                null
            }
            val getInstanceName = symbols.origImageSharedPrefGetInstanceMethod
            if (!getInstanceName.isNullOrBlank()) {
                val cls = sharedPrefClass ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == getInstanceName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType.name == sharedPrefClassName
                }
                if (!ok) return false
            }
            val putBooleanName = symbols.origImageSharedPrefPutBooleanMethod
            if (!putBooleanName.isNullOrBlank()) {
                val cls = sharedPrefClass ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == putBooleanName &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                if (!ok) return false
            }

            val md5ClassName = symbols.origImageMd5Class
            val md5Class = if (!md5ClassName.isNullOrBlank()) {
                safeFindClass(md5ClassName, cl) ?: return false
            } else {
                null
            }
            val md5MethodName = symbols.origImageMd5Method
            if (!md5MethodName.isNullOrBlank()) {
                val cls = md5Class ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == md5MethodName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                if (!ok) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isDrawableResId(value: Int): Boolean {
        if ((value ushr 24) != 0x7F) return false
        return ((value ushr 16) and 0xFF) == 0x08
    }

    private fun deriveFeatureStatusMap(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = LinkedHashMap<String, HookFeatureStatus>(ALL_FEATURE_KEYS.size)
        val feedTemplateKeyMissing = symbols.feedTemplateKeyMethod.isNullOrBlank()
        val customPostCritical = ArrayList<String>(4)
        val customPostOptional = ArrayList<String>(1)
        if (feedTemplateKeyMissing) customPostCritical.add("feedTemplateKeyMethod")
        if (symbols.feedTemplatePayloadMethod.isNullOrBlank()) customPostCritical.add("feedTemplatePayloadMethod")
        if (symbols.feedTemplateLoadMoreMethod.isNullOrBlank()) customPostCritical.add("feedTemplateLoadMoreMethod")
        if (symbols.feedCardDataListField.isNullOrBlank()) customPostCritical.add("feedCardDataListField")
        if (symbols.feedHeadParamsField.isNullOrBlank()) customPostOptional.add("feedHeadParamsField")
        if (symbols.feedRecommendCardNestedDataMethod.isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataMethod")
        }
        if (symbols.feedRecommendCardNestedDataListField.isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataListField")
        }
        out[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER] = if (customPostCritical.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = customPostCritical,
                missingOptional = customPostOptional,
            )
        } else if (customPostOptional.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = customPostOptional,
            )
        } else {
            HookFeatureStatus(state = HookFeatureState.FULL)
        }

        out[HookFeatureKey.HIDE_PB_BOTTOM_BANNER] = HookFeatureStatus(state = HookFeatureState.HARD_CODED)
        val freeCopyOptional = ArrayList<String>(3)
        if (symbols.freeCopyPopupMenuClass.isNullOrBlank()) freeCopyOptional.add("freeCopyPopupMenuClass")
        if (symbols.freeCopyPopupContentViewMethod.isNullOrBlank()) {
            freeCopyOptional.add("freeCopyPopupContentViewMethod")
        }
        if (symbols.freeCopyPopupTextField.isNullOrBlank()) freeCopyOptional.add("freeCopyPopupTextField")
        out[HookFeatureKey.FREE_COPY] = if (freeCopyOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = freeCopyOptional)
        }
        out[HookFeatureKey.AUTO_SIGN_IN] = HookFeatureStatus(state = HookFeatureState.HARD_CODED)

        val privateReadReceiptCritical = ArrayList<String>(19)
        if (symbols.privateReadReceiptModelClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelClass")
        }
        if (symbols.privateReadReceiptModelReadDispatchMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelReadDispatchMethod")
        }
        if (symbols.privateReadReceiptMessageManagerClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerClass")
        }
        if (symbols.privateReadReceiptMessageManagerGetInstanceMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerGetInstanceMethod")
        }
        if (symbols.privateReadReceiptMessageSendMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageSendMethod")
        }
        if (symbols.privateReadReceiptMessageBaseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageBaseClass")
        }
        if (symbols.privateReadReceiptRequestClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestClass")
        }
        if (symbols.privateReadReceiptModelBaseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelBaseClass")
        }
        if (symbols.privateReadReceiptCommitResponseClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCommitResponseClass")
        }
        if (symbols.privateReadReceiptProcessAckMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptProcessAckMethod")
        }
        if (symbols.privateReadReceiptResponseErrorMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptResponseErrorMethod")
        }
        if (symbols.privateReadReceiptRequestMsgIdField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestMsgIdField")
        }
        if (symbols.privateReadReceiptRequestToUidField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestToUidField")
        }
        if (symbols.privateReadReceiptModelDataField.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelDataField")
        }
        if (symbols.privateReadReceiptPageDataClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataClass")
        }
        if (symbols.privateReadReceiptPageDataChatListMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataChatListMethod")
        }
        if (symbols.privateReadReceiptChatMessageClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageClass")
        }
        if (symbols.privateReadReceiptChatMessageMsgIdMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageMsgIdMethod")
        }
        if (symbols.privateReadReceiptChatMessageUserIdMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageUserIdMethod")
        }
        if (symbols.privateReadReceiptChatMessageLocalDataMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageLocalDataMethod")
        }
        if (symbols.privateReadReceiptLocalDataClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptLocalDataClass")
        }
        if (symbols.privateReadReceiptLocalDataStatusMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptLocalDataStatusMethod")
        }
        if (symbols.privateReadReceiptAccountClass.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptAccountClass")
        }
        if (symbols.privateReadReceiptCurrentAccountMethod.isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCurrentAccountMethod")
        }
        out[HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE] = if (privateReadReceiptCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = privateReadReceiptCritical,
            )
        }

        val enterForumInitInfoReady =
            !symbols.enterForumInitInfoDataClass.isNullOrBlank() &&
                !symbols.enterForumInitInfoGetUrlMethod.isNullOrBlank()
        val enterForumWebLoadReady =
            !symbols.enterForumWebControllerClass.isNullOrBlank() &&
                !symbols.enterForumWebLoadMethod.isNullOrBlank()
        out[HookFeatureKey.FILTER_ENTER_FORUM_WEB] = if (enterForumInitInfoReady || enterForumWebLoadReady) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = listOf("enterForumInitInfoData", "enterForumWebLoadTarget"),
            )
        }

        val homeNativeGlassCritical = ArrayList<String>(1)
        val homeNativeGlassOptional = ArrayList<String>(12)
        if (symbols.feedCardBindMethod.isNullOrBlank()) homeNativeGlassCritical.add("feedCardBindMethod")
        if (symbols.homeNativeGlassSubPbNextPageMoreViewId == null || symbols.homeNativeGlassSubPbNextPageMoreViewId == 0) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbNextPageMoreViewId")
        }
        if (symbols.homeNativeGlassPbReplyTitleDividerViewId == null || symbols.homeNativeGlassPbReplyTitleDividerViewId == 0) {
            homeNativeGlassOptional.add("homeNativeGlassPbReplyTitleDividerViewId")
        }
        if (symbols.pbCommonLayoutPreloaderGetOrDefaultMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("pbCommonLayoutPreloaderGetOrDefaultMethod")
        }
        if (symbols.homeNativeGlassDynamicBackgroundColorIds.isEmpty()) {
            homeNativeGlassOptional.add("homeNativeGlassDynamicBackgroundColorIds")
        }
        if (symbols.homeNativeGlassSortSwitchBackgroundPaintField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchBackgroundPaintField")
        }
        if (symbols.homeNativeGlassSortSwitchSlideDrawMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlideDrawMethod")
        }
        if (symbols.homeNativeGlassSortSwitchSlidePathField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlidePathField")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleControllerClass.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleControllerClass")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleInitMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleInitMethod")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleRefreshMethod.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleRefreshMethod")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleViewField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleViewField")
        }
        if (symbols.homeNativeGlassEnterForumCapsuleTitleField.isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleTitleField")
        }
        out[HookFeatureKey.HOME_NATIVE_GLASS] = if (homeNativeGlassCritical.isEmpty()) {
            if (homeNativeGlassOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = homeNativeGlassOptional,
                )
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeNativeGlassCritical,
                missingOptional = homeNativeGlassOptional,
            )
        }

        val plainUrlDirectMissing = ArrayList<String>(6)
        if (symbols.plainUrlClickableSpanClass.isNullOrBlank()) plainUrlDirectMissing.add("plainUrlClickableSpanClass")
        if (symbols.plainUrlClickableSpanOnClickMethod.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickMethod")
        }
        if (symbols.plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickOwnerClasses")
        }
        if (symbols.plainUrlClickableSpanTypeField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTypeField")
        }
        if (symbols.plainUrlClickableSpanUrlField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanUrlField")
        }
        if (symbols.plainUrlClickableSpanTextField.isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTextField")
        }
        val plainUrlMessageMissing = ArrayList<String>(8)
        if (symbols.plainUrlMessageManagerClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageManagerClass")
        }
        if (symbols.plainUrlMessageDispatchMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageDispatchMethod")
        }
        if (symbols.plainUrlResponsedMessageClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageClass")
        }
        if (symbols.plainUrlResponsedMessageGetCmdMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageGetCmdMethod")
        }
        if (symbols.plainUrlCustomResponsedMessageClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageClass")
        }
        if (symbols.plainUrlCustomResponsedMessageGetDataMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageGetDataMethod")
        }
        if (symbols.plainUrlApplicationClass.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationClass")
        }
        if (symbols.plainUrlApplicationGetInstMethod.isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationGetInstMethod")
        }
        val mountCardMissing = ArrayList<String>(5)
        if (symbols.mountCardLinkLayoutClass.isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutClass")
        if (symbols.mountCardLinkLayoutOnClickMethod.isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkLayoutOnClickMethod")
        }
        if (symbols.mountCardLinkLayoutDataField.isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutDataField")
        if (symbols.mountCardLinkInfoDataClass.isNullOrBlank()) mountCardMissing.add("mountCardLinkInfoDataClass")
        if (symbols.mountCardLinkInfoGetUrlMethod.isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkInfoGetUrlMethod")
        }
        val plainUrlDirectReady = plainUrlDirectMissing.isEmpty()
        val plainUrlMessageReady = plainUrlMessageMissing.isEmpty()
        val plainUrlReady = plainUrlDirectReady || plainUrlMessageReady
        val plainUrlMissing = if (plainUrlReady) {
            emptyList()
        } else {
            (plainUrlDirectMissing + plainUrlMessageMissing).distinct()
        }
        val mountCardReady = mountCardMissing.isEmpty()
        out[HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER] = when {
            plainUrlReady && mountCardReady -> HookFeatureStatus(state = HookFeatureState.FULL)
            plainUrlReady || mountCardReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = plainUrlMissing + mountCardMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = plainUrlMissing + mountCardMissing,
            )
        }

        val autoRefreshCritical = ArrayList<String>(1)
        if (symbols.autoRefreshTriggerMethod.isNullOrBlank()) {
            autoRefreshCritical.add("autoRefreshTriggerMethod")
        }
        out[HookFeatureKey.DISABLE_AUTO_REFRESH] = if (autoRefreshCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = autoRefreshCritical)
        }

        val pbGestureScaleCritical = ArrayList<String>(5)
        if (symbols.pbGestureScaleManagerClass.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleManagerClass")
        }
        if (symbols.pbGestureScaleDispatchMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleDispatchMethod")
        }
        if (symbols.pbGestureScaleListenerSetterMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerSetterMethod")
        }
        if (symbols.pbGestureScaleListenerClass.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerClass")
        }
        if (symbols.pbGestureScaleListenerOnScaleMethod.isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerOnScaleMethod")
        }
        out[HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE] = if (pbGestureScaleCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = pbGestureScaleCritical)
        }

        val forumTopShiftCritical = ArrayList<String>(2)
        if (symbols.forumBottomSheetViewClass.isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetViewClass")
        }
        if (symbols.forumBottomSheetInitScrollMethod.isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetInitScrollMethod")
        }
        out[HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT] = if (forumTopShiftCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = forumTopShiftCritical)
        }

        val autoLoadMoreOptional = ArrayList<String>(8)
        if (symbols.autoLoadMoreConfigClass.isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigClass")
        }
        if (symbols.autoLoadMoreConfigMethod.isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigMethod")
        }
        val hasListBottomMechanism =
            !symbols.pbCommentBottomListScrollClass.isNullOrBlank() &&
                !symbols.pbCommentBottomListScrollMethod.isNullOrBlank() &&
                !symbols.pbCommentBottomListOwnerField.isNullOrBlank()
        val hasRecyclerBottomMechanism =
            !symbols.pbCommentBottomRecyclerScrollClass.isNullOrBlank() &&
                !symbols.pbCommentBottomRecyclerScrollMethod.isNullOrBlank() &&
                !symbols.pbCommentBottomRecyclerOwnerField.isNullOrBlank()
        if (!hasListBottomMechanism && !hasRecyclerBottomMechanism) {
            autoLoadMoreOptional.add("pbCommentBottomMechanism")
        }
        out[HookFeatureKey.AUTO_LOAD_MORE] = if (autoLoadMoreOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = autoLoadMoreOptional,
            )
        }

        val pbScrollCoalesceCritical = ArrayList<String>(2)
        if (symbols.pbCommentScrollListenerClass.isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollListenerClass")
        }
        if (symbols.pbCommentScrollMethod.isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollMethod")
        }
        out[HookFeatureKey.ENABLE_PB_SCROLL_COALESCE] = if (pbScrollCoalesceCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = pbScrollCoalesceCritical)
        }

        val notifyMissingCritical = ArrayList<String>(1)
        if (symbols.msgTabLocateToTabMethod.isNullOrBlank()) {
            notifyMissingCritical.add("msgTabLocateToTabMethod")
        }
        out[HookFeatureKey.DEFAULT_NOTIFY_TAB] = if (notifyMissingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = notifyMissingCritical,
            )
        }

        val homeCritical = ArrayList<String>(7)
        val homeOptional = ArrayList<String>(3)
        if (symbols.homeTabClass.isNullOrBlank()) homeCritical.add("homeTabClass")
        if (symbols.homeTabRebuildMethod.isNullOrBlank()) homeCritical.add("homeTabRebuildMethod")
        if (symbols.homeTabListField.isNullOrBlank()) homeCritical.add("homeTabListField")
        if (symbols.homeTabItemTypeField.isNullOrBlank()) homeCritical.add("homeTabItemTypeField")
        if (symbols.homeTabItemCodeField.isNullOrBlank()) homeCritical.add("homeTabItemCodeField")
        if (symbols.homeTabItemNameField.isNullOrBlank()) homeCritical.add("homeTabItemNameField")
        if (symbols.homeTabItemUrlField.isNullOrBlank()) homeCritical.add("homeTabItemUrlField")
        if (symbols.homeTabItemMainSetterMethod.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainSetterMethod")
        }
        if (symbols.homeTabItemMainIntField.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainIntField")
        }
        if (symbols.homeTabItemMainBooleanField.isNullOrBlank()) {
            homeOptional.add("homeTabItemMainBooleanField")
        }
        out[HookFeatureKey.SIMPLIFY_HOME_TOP_TABS] = if (homeCritical.isEmpty() && homeOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else if (homeCritical.isEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = homeOptional,
            )
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeCritical,
                missingOptional = homeOptional,
            )
        }

        val bottomCritical = ArrayList<String>(5)
        val bottomOptional = ArrayList<String>(2)
        if (symbols.mainTabDataClass.isNullOrBlank()) bottomCritical.add("mainTabDataClass")
        if (symbols.mainTabAddMethod.isNullOrBlank()) bottomCritical.add("mainTabAddMethod")
        if (symbols.mainTabGetListMethod.isNullOrBlank()) bottomCritical.add("mainTabGetListMethod")
        if (symbols.mainTabDelegateGetStructureMethod.isNullOrBlank()) {
            bottomCritical.add("mainTabDelegateGetStructureMethod")
        }
        if (symbols.mainTabStructureTypeField.isNullOrBlank()) bottomCritical.add("mainTabStructureTypeField")
        if (symbols.mainTabStructureDynamicIconField.isNullOrBlank()) {
            bottomOptional.add("mainTabStructureDynamicIconField")
        }
        if (symbols.mainTabStructureFragmentField.isNullOrBlank()) {
            bottomOptional.add("mainTabStructureFragmentField")
        }
        out[HookFeatureKey.SIMPLIFY_BOTTOM_TABS] = when {
            bottomCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = bottomCritical,
                missingOptional = bottomOptional,
            )
            bottomOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = bottomOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }

        val imageCritical = ArrayList<String>(8)
        val imageOptional = ArrayList<String>(10)
        if (symbols.origImageUrlDragImageViewClass.isNullOrBlank()) imageCritical.add("origImageUrlDragImageViewClass")
        if (symbols.origImageDataClass.isNullOrBlank()) imageCritical.add("origImageDataClass")
        if (symbols.origImageTriggerMethod.isNullOrBlank()) imageCritical.add("origImageTriggerMethod")
        if (symbols.origImageAssistDataMethod.isNullOrBlank()) imageCritical.add("origImageAssistDataMethod")
        if (symbols.origImageShowButtonField.isNullOrBlank()) imageCritical.add("origImageShowButtonField")
        if (symbols.origImageBlockedField.isNullOrBlank()) imageCritical.add("origImageBlockedField")
        if (symbols.origImageOriginalProcessField.isNullOrBlank()) imageCritical.add("origImageOriginalProcessField")
        if (symbols.origImageOriginalUrlField.isNullOrBlank()) imageCritical.add("origImageOriginalUrlField")
        if (symbols.origImagePagerAdapterClass.isNullOrBlank()) imageOptional.add("origImagePagerAdapterClass")
        if (symbols.origImageSetPrimaryItemMethod.isNullOrBlank()) imageOptional.add("origImageSetPrimaryItemMethod")
        if (symbols.origImageSetAssistUrlMethod.isNullOrBlank()) imageOptional.add("origImageSetAssistUrlMethod")
        if (symbols.origImagePrimaryReadyMethod.isNullOrBlank()) imageOptional.add("origImagePrimaryReadyMethod")
        if (symbols.origImageDirectStartMethod.isNullOrBlank()) imageOptional.add("origImageDirectStartMethod")
        if (symbols.origImageOriginTextMethod.isNullOrBlank()) imageOptional.add("origImageOriginTextMethod")
        if (symbols.origImageSharedPrefHelperClass.isNullOrBlank()) imageOptional.add("origImageSharedPrefHelperClass")
        if (symbols.origImageSharedPrefGetInstanceMethod.isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefGetInstanceMethod")
        }
        if (symbols.origImageSharedPrefPutBooleanMethod.isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefPutBooleanMethod")
        }
        if (symbols.origImageMd5Class.isNullOrBlank()) imageOptional.add("origImageMd5Class")
        if (symbols.origImageMd5Method.isNullOrBlank()) imageOptional.add("origImageMd5Method")
        out[HookFeatureKey.DEFAULT_ORIGINAL_IMAGE] = when {
            imageCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = imageCritical,
                missingOptional = imageOptional,
            )
            imageOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = imageOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }

        val shareTrackingCritical = ArrayList<String>(2)
        if (symbols.shareTrackBuilderClass.isNullOrBlank()) shareTrackingCritical.add("shareTrackBuilderClass")
        if (symbols.shareTrackBuildUrlMethod.isNullOrBlank()) shareTrackingCritical.add("shareTrackBuildUrlMethod")
        out[HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS] = if (shareTrackingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = shareTrackingCritical)
        }

        val aiComponentCritical = ArrayList<String>(5)
        if (symbols.aiSpriteMemePanControllerClass.isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemePanControllerClass")
        }
        if (symbols.aiSpriteMemeEnableMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemeEnableMethod")
        }
        if (symbols.aiPbNewInputContainerClass.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerClass")
        }
        if (symbols.aiPbNewInputContainerInitSpriteMemeMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitSpriteMemeMethod")
        }
        if (symbols.aiPbNewInputContainerInitAiWriteMethod.isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitAiWriteMethod")
        }
        val aiComponentOptional = ArrayList<String>(3)
        if (symbols.aiPbAiEmojiCreationViewBindMethod.isNullOrBlank()) {
            aiComponentOptional.add("aiPbAiEmojiCreationViewBindMethod")
        }
        if (symbols.aiImageViewerJumpButtonOwnerClass.isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonOwnerClass")
        }
        if (symbols.aiImageViewerJumpButtonInitMethod.isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonInitMethod")
        }
        out[HookFeatureKey.DISABLE_AI_COMPONENTS] = if (aiComponentCritical.isEmpty()) {
            if (aiComponentOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = aiComponentOptional)
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = aiComponentCritical,
                missingOptional = aiComponentOptional,
            )
        }

        val adOptional = ArrayList<String>(18)
        if (symbols.feedTemplateKeyMethod.isNullOrBlank()) adOptional.add("feedTemplateKeyMethod")
        if (symbols.feedTemplateLoadMoreMethod.isNullOrBlank()) adOptional.add("feedTemplateLoadMoreMethod")
        if (symbols.splashAdHelperClass.isNullOrBlank()) adOptional.add("splashAdHelperClass")
        if (symbols.splashAdHelperMethod.isNullOrBlank()) adOptional.add("splashAdHelperMethod")
        if (symbols.closeAdDataClass.isNullOrBlank()) adOptional.add("closeAdDataClass")
        if (symbols.closeAdDataMethodG1.isNullOrBlank()) adOptional.add("closeAdDataMethodG1")
        if (symbols.closeAdDataMethodJ1.isNullOrBlank()) adOptional.add("closeAdDataMethodJ1")
        if (symbols.zgaClass.isNullOrBlank()) adOptional.add("zgaClass")
        if (symbols.zgaMethods.isNullOrEmpty()) adOptional.add("zgaMethods")
        if (symbols.searchBoxViewClass.isNullOrBlank()) adOptional.add("searchBoxViewClass")
        if (symbols.searchBoxSetHintMethod.isNullOrBlank()) adOptional.add("searchBoxSetHintMethod")
        if (symbols.homeSearchBoxOwnerClass.isNullOrBlank()) adOptional.add("homeSearchBoxOwnerClass")
        if (symbols.homeSearchBoxInitMethod.isNullOrBlank()) adOptional.add("homeSearchBoxInitMethod")
        if (symbols.homeSearchBoxGetterMethod.isNullOrBlank()) adOptional.add("homeSearchBoxGetterMethod")
        if (symbols.homePersonalizeAnchorClasses.isNullOrEmpty()) {
            adOptional.add("homePersonalizeAnchorClasses")
        }
        if (symbols.homeRightSlotClass.isNullOrBlank()) adOptional.add("homeRightSlotClass")
        if (symbols.homeRightSlotStateMethods.isNullOrEmpty()) adOptional.add("homeRightSlotStateMethods")
        if (symbols.pbFallingViewClass.isNullOrBlank()) adOptional.add("pbFallingViewClass")
        if (symbols.pbFallingInitMethod.isNullOrBlank()) adOptional.add("pbFallingInitMethod")
        if (symbols.pbFallingShowMethod.isNullOrBlank()) adOptional.add("pbFallingShowMethod")
        if (symbols.pbFallingClearMethod.isNullOrBlank()) adOptional.add("pbFallingClearMethod")
        if (symbols.pbEarlyAdInsertClass.isNullOrBlank()) adOptional.add("pbEarlyAdInsertClass")
        if (symbols.pbEarlyAdInsertMethodSpecs.orEmpty().size < PB_EARLY_AD_INSERT_MIN_METHOD_COUNT) {
            adOptional.add("pbEarlyAdInsertMethodSpecs")
        }
        val hasPbAdBidCommonPath =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val hasPbAdBidPageBrowserPath =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        if (!hasPbAdBidCommonPath && !hasPbAdBidPageBrowserPath) {
            if (symbols.pbAdBidCommonRequestModelClass.isNullOrBlank()) {
                adOptional.add("pbAdBidCommonRequestModelClass")
            }
            if (symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty()) {
                adOptional.add("pbAdBidCommonRequestStartMethods")
            }
            if (symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()) {
                adOptional.add("pbAdBidCommonRequestNotifyMethod")
            }
            if (symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank()) {
                adOptional.add("pbAdBidPageBrowserRequestModelClass")
            }
            if (symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()) {
                adOptional.add("pbAdBidPageBrowserRequestDataMethod")
            }
        }
        if (symbols.typeAdapterSetDataMethod.isNullOrBlank()) adOptional.add("typeAdapterSetDataMethod")
        if (symbols.typeAdapterDataItemClass.isNullOrBlank()) adOptional.add("typeAdapterDataItemClass")
        if (symbols.typeAdapterDataGetTypeMethod.isNullOrBlank()) {
            adOptional.add("typeAdapterDataGetTypeMethod")
        }
        out[HookFeatureKey.BLOCK_AD] = if (adOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = adOptional)
        }

        for (key in ALL_FEATURE_KEYS) {
            if (!out.containsKey(key)) out[key] = HookFeatureStatus()
        }
        return out
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? {
        scanContext.get()?.let { return it.findClass(name, cl) }
        return safeFindClassUncached(name, cl)
    }

    private fun safeFindClassUncached(name: String, cl: ClassLoader): Class<*>? {
        return try {
            XposedCompat.findClassOrNull(name, cl)
        } catch (_: Throwable) {
            null
        }
    }

    private data class TargetAppVersionInfo(
        val versionCode: Long,
        val versionName: String?,
    )

    private fun applyScanSupportCheck(
        context: Context,
        symbols: HookSymbols,
        logger: ScanLogger?,
    ): HookSymbols {
        val versionInfo = readTargetAppVersionInfo(context, logger)
            ?: return symbols.copy(scanSupportState = ScanSupportState.UNKNOWN)

        if (
            versionInfo.versionCode > MAX_TIEBA_VERSION_CODE ||
            versionInfo.versionCode < MIN_TIEBA_VERSION_CODE
        ) {
            return symbols.copy(
                scanSupportState = ScanSupportState.UNSUPPORTED_VERSION,
                scanTargetVersionCode = versionInfo.versionCode,
                scanTargetVersionName = versionInfo.versionName,
                scanTargetVersionType = null,
            )
        }

        val versionType = readTargetVersionType(context, logger)
        return symbols.copy(
            scanSupportState = if (isOfficialTiebaVersionType(versionType)) {
                ScanSupportState.SUPPORTED
            } else {
                ScanSupportState.NON_OFFICIAL
            },
            scanTargetVersionCode = versionInfo.versionCode,
            scanTargetVersionName = versionInfo.versionName,
            scanTargetVersionType = versionType,
        )
    }

    private fun readTargetAppVersionInfo(context: Context, logger: ScanLogger?): TargetAppVersionInfo? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            TargetAppVersionInfo(versionCode = versionCode, versionName = info.versionName)
        } catch (t: Throwable) {
            log(logger, "scan support version read failed: ${t.message}")
            null
        }
    }

    fun isOfficialTiebaVersionType(versionType: String?): Boolean {
        return versionType == OFFICIAL_VERSION_TYPE
    }

    @Suppress("DEPRECATION")
    fun readTargetVersionType(context: Context, logger: ScanLogger? = null): String? {
        return try {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA,
            )
            val value = info.metaData?.get(VERSION_TYPE_META_NAME)
            when (value) {
                is Number -> value.toLong().toString()
                is String -> value.trim().ifEmpty { null }
                null -> null
                else -> value.toString().trim().ifEmpty { null }
            }
        } catch (t: Throwable) {
            log(logger, "scan support versionType read failed: ${t.message}")
            null
        }
    }

    private fun buildFingerprint(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val sourceDir = pkgInfo.applicationInfo?.sourceDir
            val file = if (sourceDir.isNullOrBlank()) null else File(sourceDir)
            val size = file?.length() ?: -1L
            val modified = file?.lastModified() ?: -1L
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
            val titanFingerprint = TitanRuntimeState.buildFingerprint(context)
            "$vCode:${pkgInfo.lastUpdateTime}:$size:$modified:${runtimeModuleVersionCodeLabel()}:$titanFingerprint"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun toastOnMain(context: Context, text: String) {
        try {
            val appCtx = context.applicationContext ?: context
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                try {
                    Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
            }
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
    }

    private fun log(logger: ScanLogger?, line: String) {
        try {
            XposedCompat.log("$TAG: $line")
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
        try {
            logger?.log(line)
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
    }

    fun formatScanException(t: Throwable): String {
        val type = t::class.java.name
        val message = t.message?.trim().orEmpty()
        return if (message.isNotEmpty()) "$type: $message" else type
    }

    private fun buildScanError(tag: String, t: Throwable): String {
        return "${tag.trim()} :: ${sanitizeScanStatusText(formatScanException(t))}"
    }

    private fun splitScanError(error: String): Pair<String, String> {
        val separator = " :: "
        val idx = error.indexOf(separator)
        if (idx <= 0) return "ScanException" to sanitizeScanStatusText(error)
        val tag = error.substring(0, idx).trim().ifEmpty { "ScanException" }
        val detail = error.substring(idx + separator.length).trim().ifEmpty { "unknown" }
        return tag to sanitizeScanStatusText(detail)
    }

    private fun sanitizeScanStatusText(raw: String): String {
        return raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(240)
            .ifBlank { "unknown" }
    }

    private fun logScanException(
        logger: ScanLogger?,
        tag: String,
        errors: MutableList<String>,
        throwable: Throwable,
    ) {
        val error = buildScanError(tag, throwable)
        errors.add(error)
        log(logger, "$tag scan exception: ${splitScanError(error).second}")
        try {
            XposedCompat.log(throwable)
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
    }

    private fun recordScanIssue(
        logger: ScanLogger?,
        tag: String,
        errors: MutableList<String>,
        detail: String,
    ) {
        val error = "${tag.trim()} :: ${sanitizeScanStatusText(detail)}"
        if (!errors.contains(error)) {
            errors.add(error)
        }
        log(logger, "$tag scan exception: ${splitScanError(error).second}")
    }

    private fun describeSymbols(context: Context, symbols: HookSymbols): String {
        val appMeta = describeAppMetaFull(context)
        val modVersion = runtimeModuleVersionName()

        fun fmt(name: String, value: String?): String {
            val isNull = value == null || 
                         value == "null" || 
                         value.contains("null.") || 
                         value.contains(".null") || 
                         value.contains("[null]")
                         
            return if (!isNull) {
                "✅ [$name: $value]"
            } else {
                "❌ [$name: NOT FOUND]"
            }
        }

        val imageViewerShareValue = if (
            !symbols.imageViewerShareConfigClass.isNullOrBlank() &&
            !symbols.imageViewerShareIsDialogField.isNullOrBlank() &&
            !symbols.imageViewerShareItemField.isNullOrBlank() &&
            !symbols.imageViewerShareAddOutsideMethod.isNullOrBlank() &&
            !symbols.imageViewerShareGetRequestDataMethod.isNullOrBlank() &&
            !symbols.imageViewerShareSetRequestDataMethod.isNullOrBlank() &&
            !symbols.imageViewerShareGetContextMethod.isNullOrBlank() &&
            !symbols.imageViewerShareItemClass.isNullOrBlank() &&
            !symbols.imageViewerShareItemImageUriField.isNullOrBlank() &&
            !symbols.imageViewerShareItemViewClass.isNullOrBlank() &&
            !symbols.imageViewerShareItemNameByResMethod.isNullOrBlank() &&
            !symbols.imageViewerShareItemNameByTextMethod.isNullOrBlank() &&
            symbols.imageViewerShareIconResId != null
        ) {
            "${symbols.imageViewerShareConfigClass}[${symbols.imageViewerShareIsDialogField},${symbols.imageViewerShareItemField}].{${symbols.imageViewerShareAddOutsideMethod},${symbols.imageViewerShareGetRequestDataMethod},${symbols.imageViewerShareSetRequestDataMethod},${symbols.imageViewerShareGetContextMethod}} / ${symbols.imageViewerShareItemClass}[${symbols.imageViewerShareItemTitleField},${symbols.imageViewerShareItemContentField},${symbols.imageViewerShareItemLinkUrlField},${symbols.imageViewerShareItemImageUriField},${symbols.imageViewerShareItemImageUrlField},${symbols.imageViewerShareItemLocalFileField}] / ${symbols.imageViewerShareItemViewClass}.{${symbols.imageViewerShareItemNameByResMethod},${symbols.imageViewerShareItemNameByTextMethod}} icon=${symbols.imageViewerShareIconResId}"
        } else {
            null
        }

        val originalImageValue = if (
            !symbols.origImageUrlDragImageViewClass.isNullOrBlank() &&
            !symbols.origImageDataClass.isNullOrBlank() &&
            !symbols.origImageTriggerMethod.isNullOrBlank() &&
            !symbols.origImageAssistDataMethod.isNullOrBlank() &&
            !symbols.origImageShowButtonField.isNullOrBlank() &&
            !symbols.origImageBlockedField.isNullOrBlank() &&
            !symbols.origImageOriginalProcessField.isNullOrBlank() &&
            !symbols.origImageOriginalUrlField.isNullOrBlank()
        ) {
            "${symbols.origImageUrlDragImageViewClass}.{${symbols.origImagePrimaryReadyMethod},${symbols.origImageTriggerMethod},${symbols.origImageDirectStartMethod}} assist=${symbols.origImageSetAssistUrlMethod}/${symbols.origImageAssistDataMethod}/${symbols.origImageOriginTextMethod} data=${symbols.origImageDataClass}[${symbols.origImageShowButtonField},${symbols.origImageBlockedField},${symbols.origImageOriginalProcessField},${symbols.origImageOriginalUrlField}] primary=${symbols.origImagePagerAdapterClass}.${symbols.origImageSetPrimaryItemMethod}"
        } else {
            null
        }

        val plainUrlValue = when {
            !symbols.plainUrlClickableSpanOnClickOwnerClasses.isNullOrEmpty() &&
                !symbols.plainUrlClickableSpanOnClickMethod.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanTypeField.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanUrlField.isNullOrBlank() &&
                !symbols.plainUrlClickableSpanTextField.isNullOrBlank() -> {
                "${symbols.plainUrlClickableSpanOnClickOwnerClasses.joinToString(",")}.${symbols.plainUrlClickableSpanOnClickMethod}[${symbols.plainUrlClickableSpanTypeField},${symbols.plainUrlClickableSpanUrlField},${symbols.plainUrlClickableSpanTextField}]"
            }
            !symbols.plainUrlMessageManagerClass.isNullOrBlank() &&
                !symbols.plainUrlMessageDispatchMethod.isNullOrBlank() &&
                !symbols.plainUrlResponsedMessageClass.isNullOrBlank() &&
                !symbols.plainUrlResponsedMessageGetCmdMethod.isNullOrBlank() &&
                !symbols.plainUrlCustomResponsedMessageClass.isNullOrBlank() &&
                !symbols.plainUrlCustomResponsedMessageGetDataMethod.isNullOrBlank() &&
                !symbols.plainUrlApplicationClass.isNullOrBlank() &&
                !symbols.plainUrlApplicationGetInstMethod.isNullOrBlank() -> {
                "${symbols.plainUrlMessageManagerClass}.${symbols.plainUrlMessageDispatchMethod}(${symbols.plainUrlResponsedMessageClass}) cmd=${symbols.plainUrlResponsedMessageGetCmdMethod} data=${symbols.plainUrlCustomResponsedMessageClass}.${symbols.plainUrlCustomResponsedMessageGetDataMethod} app=${symbols.plainUrlApplicationClass}.${symbols.plainUrlApplicationGetInstMethod}"
            }
            else -> null
        }

        val aiComponentValue = if (
            !symbols.aiSpriteMemePanControllerClass.isNullOrBlank() &&
            !symbols.aiSpriteMemeEnableMethod.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerClass.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerInitSpriteMemeMethod.isNullOrBlank() &&
            !symbols.aiPbNewInputContainerInitAiWriteMethod.isNullOrBlank()
        ) {
            val imageViewerJumpButton = if (
                !symbols.aiImageViewerJumpButtonOwnerClass.isNullOrBlank() &&
                !symbols.aiImageViewerJumpButtonInitMethod.isNullOrBlank()
            ) {
                " / ${symbols.aiImageViewerJumpButtonOwnerClass}.${symbols.aiImageViewerJumpButtonInitMethod}"
            } else {
                ""
            }
            "${symbols.aiSpriteMemePanControllerClass}.${symbols.aiSpriteMemeEnableMethod} / " +
                "${symbols.aiPbNewInputContainerClass}.{${symbols.aiPbNewInputContainerInitSpriteMemeMethod},${symbols.aiPbNewInputContainerInitAiWriteMethod}}" +
                imageViewerJumpButton
        } else {
            null
        }

        val pbAdBidCommonReady =
            !symbols.pbAdBidCommonRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidCommonRequestStartMethods.isNullOrEmpty() &&
                !symbols.pbAdBidCommonRequestNotifyMethod.isNullOrBlank()
        val pbAdBidPageBrowserReady =
            !symbols.pbAdBidPageBrowserRequestModelClass.isNullOrBlank() &&
                !symbols.pbAdBidPageBrowserRequestDataMethod.isNullOrBlank()
        val pbAdBidValue = when {
            pbAdBidCommonReady || pbAdBidPageBrowserReady -> buildString {
                if (pbAdBidCommonReady) {
                    append("common=")
                    append(symbols.pbAdBidCommonRequestModelClass)
                    append(".{")
                    append(symbols.pbAdBidCommonRequestStartMethods?.joinToString(";"))
                    append("}#")
                    append(symbols.pbAdBidCommonRequestNotifyMethod)
                } else {
                    append("common=optional-absent")
                }
                append(" pageBrowser=")
                if (pbAdBidPageBrowserReady) {
                    append(symbols.pbAdBidPageBrowserRequestModelClass)
                    append(".")
                    append(symbols.pbAdBidPageBrowserRequestDataMethod)
                } else {
                    append("optional-absent")
                }
            }
            else -> null
        }

        return """
            ========== TBHook Match Status ==========
            App Version   : $appMeta
            Module Version: $modVersion
            Match Result  :
            ${fmt("Home", "${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}] item={${symbols.homeTabItemTypeField},${symbols.homeTabItemCodeField},${symbols.homeTabItemNameField},${symbols.homeTabItemUrlField}} main={${symbols.homeTabItemMainSetterMethod},${symbols.homeTabItemMainIntField},${symbols.homeTabItemMainBooleanField}}")}
            ${fmt("Settings", "${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}]")}
            ${fmt("Zga", symbols.zgaClass)}
            ${fmt("Splash", "${symbols.splashAdHelperClass}.${symbols.splashAdHelperMethod}")}
            ${fmt("FeedKey", symbols.feedTemplateKeyMethod)}
            ${fmt("FeedPayload", symbols.feedTemplatePayloadMethod)}
            ${fmt("FeedLoadMore", symbols.feedTemplateLoadMoreMethod)}
            ${fmt("SearchBoxHint", "${symbols.searchBoxViewClass}.${symbols.searchBoxSetHintMethod} owner=${symbols.homeSearchBoxOwnerClass}.{${symbols.homeSearchBoxInitMethod},${symbols.homeSearchBoxGetterMethod}}")}
            ${fmt("HomeRightSlot", "${symbols.homeRightSlotClass}.{${symbols.homeRightSlotStateMethods?.joinToString(",")}}")}
            ${fmt("PbFalling", "${symbols.pbFallingViewClass}.{${symbols.pbFallingInitMethod},${symbols.pbFallingShowMethod},${symbols.pbFallingClearMethod}}")}
            ${fmt("PbEarlyAdInsert", "${symbols.pbEarlyAdInsertClass}.{${symbols.pbEarlyAdInsertMethodSpecs?.joinToString(";")}}")}
            ${fmt("EnterForumWeb", "source=${symbols.enterForumInitInfoDataClass}.${symbols.enterForumInitInfoGetUrlMethod} webLoad=${symbols.enterForumWebControllerClass}.${symbols.enterForumWebLoadMethod}")}
            ${fmt("PlainUrlClickableSpan", plainUrlValue)}
            ${fmt("MountCardLink", "${symbols.mountCardLinkLayoutClass}.${symbols.mountCardLinkLayoutOnClickMethod}[${symbols.mountCardLinkLayoutDataField}->${symbols.mountCardLinkInfoDataClass}.${symbols.mountCardLinkInfoGetUrlMethod}]")}
            ${fmt("ForumTopShift", "${symbols.forumBottomSheetViewClass}.${symbols.forumBottomSheetInitScrollMethod}")}
            ${fmt("AutoRefresh", "${PERSONALIZE_PAGE_VIEW_CLASS}.${symbols.autoRefreshTriggerMethod}")}
            ${fmt("AutoLoadMore", "${symbols.autoLoadMoreConfigClass}.${symbols.autoLoadMoreConfigMethod}")}
            ${fmt("PbCommentBottom", "${symbols.pbCommentBottomListScrollClass}.${symbols.pbCommentBottomListScrollMethod}[${symbols.pbCommentBottomListOwnerField}] / ${symbols.pbCommentBottomRecyclerScrollClass}.${symbols.pbCommentBottomRecyclerScrollMethod}[${symbols.pbCommentBottomRecyclerOwnerField}]")}
            ${fmt("PbGestureScale", "${symbols.pbGestureScaleManagerClass}.${symbols.pbGestureScaleDispatchMethod}/${symbols.pbGestureScaleListenerSetterMethod} -> ${symbols.pbGestureScaleListenerClass}.${symbols.pbGestureScaleListenerOnScaleMethod}")}
            ${fmt("CollectionCore", "${symbols.collectionPresenterField}.${symbols.collectionPresenterListSetterMethod} / ${symbols.collectionModelField}.${symbols.collectionModelListGetterMethod}/${symbols.collectionModelParseMethod}")}
            ${fmt("CollectionAdapter", "${symbols.collectionPresenterAdapterField}.{${symbols.collectionAdapterShowFooterMethod},${symbols.collectionAdapterLoadingMethod},${symbols.collectionAdapterHasMoreMethod}}")}
            ${fmt("CollectionListField", "${symbols.collectionModelListField}/${symbols.collectionFragmentDisplayListField} nav={${symbols.collectionActivityNavControllerField},${symbols.collectionNavBarField}}")}
            ${fmt("CollectionEditMode", symbols.collectionEditModeMethod)}
            ${fmt("HistorySearch", "${symbols.historyAdapterField}.${symbols.historyAdapterSetListMethod}[${symbols.historyListField}]#${symbols.historyActivityListUpdateMethod} nav=${symbols.historyActivityNavBarField} getters={${symbols.historyThreadNameMethod},${symbols.historyForumNameMethod},${symbols.historyUserNameMethod},${symbols.historyDescriptionMethod},${symbols.historyThreadIdMethod},${symbols.historyPostIdMethod},${symbols.historyLiveIdMethod}}")}
            ${fmt("MsgTabNotify", "${symbols.msgTabLocateToTabMethod}/${symbols.msgTabContainerSelectMethod}[${symbols.msgTabContainerExtDataField}]")}
            ${fmt("PrivateReadReceipt", "${symbols.privateReadReceiptModelClass}.${symbols.privateReadReceiptModelReadDispatchMethod}[${symbols.privateReadReceiptModelDataField}->${symbols.privateReadReceiptPageDataClass}.${symbols.privateReadReceiptPageDataChatListMethod}] ack=${symbols.privateReadReceiptModelBaseClass}.${symbols.privateReadReceiptProcessAckMethod}(${symbols.privateReadReceiptCommitResponseClass}).${symbols.privateReadReceiptResponseErrorMethod} / ${symbols.privateReadReceiptMessageManagerClass}.${symbols.privateReadReceiptMessageManagerGetInstanceMethod}.${symbols.privateReadReceiptMessageSendMethod}(${symbols.privateReadReceiptMessageBaseClass}) request=${symbols.privateReadReceiptRequestClass}[${symbols.privateReadReceiptRequestMsgIdField},${symbols.privateReadReceiptRequestToUidField}]")}
            ${fmt("FreeCopyPopup", "${symbols.freeCopyPopupMenuClass}.${symbols.freeCopyPopupContentViewMethod}[${symbols.freeCopyPopupTextField}]")}
            ${fmt("BottomTab", "${symbols.mainTabDataClass}.${symbols.mainTabAddMethod}/${symbols.mainTabGetListMethod}->${symbols.mainTabDelegateGetStructureMethod}[${symbols.mainTabStructureTypeField},${symbols.mainTabStructureDynamicIconField},${symbols.mainTabStructureFragmentField}]")}
            ${fmt("DefaultOriginalImage", originalImageValue)}
            ${fmt("ImageViewerShare", imageViewerShareValue)}
            ${fmt("FeedCardBind", "FeedCardView.${symbols.feedCardBindMethod}")}
            ${fmt("FeedCardDataList", symbols.feedCardDataListField)}
            ${fmt("FeedHeadParams", symbols.feedHeadParamsField)}
            ${fmt("RecommendCardNestedData", "${symbols.feedRecommendCardNestedDataMethod}[${symbols.feedRecommendCardNestedDataListField}]")}
            ${fmt("PbAdBid", pbAdBidValue)}
            ${fmt("PostAdDataFilter", "${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols.typeAdapterSetDataMethod} item=${symbols.typeAdapterDataItemClass}.${symbols.typeAdapterDataGetTypeMethod}")}
            ${fmt("AiComponents", aiComponentValue)}

            Source        : ${symbols.source}
            Scan Errors   : ${symbols.scanErrors.joinToString(" | ").ifEmpty { "-" }}
            =========================================
        """.trimIndent()
    }

    private fun describeAppMetaFull(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "${info.versionName} ($vCode)"
        } catch (_: Throwable) {
            "Unknown"
        }
    }

    private fun describeAppMeta(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "pkg=${context.packageName}, verCode=$vCode, update=${info.lastUpdateTime}"
        } catch (_: Throwable) {
            "pkg=${context.packageName}"
        }
    }

    fun runtimeModuleVersionName(): String {
        return readRuntimeBuildConfigField("VERSION_NAME") as? String ?: BuildConfig.VERSION_NAME
    }

    private fun runtimeModuleVersionCodeLabel(): String {
        return "${runtimeModuleVersionName()}:${runtimeModuleVersionCode()}"
    }

    private fun runtimeModuleVersionCode(): Long {
        return when (val value = readRuntimeBuildConfigField("VERSION_CODE")) {
            is Int -> value.toLong()
            is Long -> value
            is Number -> value.toLong()
            else -> BuildConfig.VERSION_CODE.toLong()
        }
    }

    private fun readRuntimeBuildConfigField(fieldName: String): Any? {
        return try {
            val buildConfigClass = Class.forName(
                "$MODULE_PACKAGE_NAME.BuildConfig",
                false,
                HookSymbolResolver::class.java.classLoader,
            )
            buildConfigClass.getField(fieldName).get(null)
        } catch (_: Throwable) {
            null
        }
    }
}

private const val MODULE_PACKAGE_NAME = "com.forbidad4tieba.hook"
private const val NAV_CLASS = StableTiebaHookPoints.NAVIGATION_BAR_CLASS
private const val TB_WEB_VIEW_CLASS = "com.baidu.tieba.browser.TbWebView"
private const val FORUM_BOTTOM_SHEET_VIEW_CLASS = "com.baidu.tieba.forum.widget.TbBottomSheetView"
private const val PB_AD_INSERT_CLASS = "com.baidu.tieba.pb.pb.main.underlayer.PbAdapterManagerInsertUtilKt"
private const val PB_EARLY_AD_INSERT_MIN_METHOD_COUNT = 2
private const val PB_COMMON_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
private const val PB_AD_BID_RES_IDL_CLASS = "tbclient.AdBid.AdBidResIdl"
private const val PB_AD_BID_DEX_KIND_COMMON = "common"
private const val PB_AD_BID_DEX_KIND_PAGE_BROWSER = "pageBrowser"
private const val HTTP_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpMessage"
private const val TIEBA_REQUEST_INTERFACE_CLASS = "com.baidu.tieba.u3b"
private const val TIEBA_REQUEST_CALLBACK_CLASS = "com.baidu.tieba.d8d"
private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"
private const val PB_DATA_CLASS = "com.baidu.tieba.iic"
private const val PB_FRAGMENT_CLASS = StableTiebaHookPoints.PB_FRAGMENT_CLASS
private const val PB_ADAPTER_DATA_CLASS = "com.baidu.tieba.yf"
private const val THREAD_DATA_CLASS = "com.baidu.tbadk.core.data.ThreadData"
private const val RECOMMEND_CARD_VIEW_CLASS = "com.baidu.tieba.feed.component.RecommendCardView"
private const val PERSONALIZE_PAGE_VIEW_CLASS =
    "com.baidu.tieba.homepage.personalize.PersonalizePageView"
private const val HOME_SEARCH_BOX_OWNER_CLASS =
    "com.baidu.tieba.homepage.personalize.PersonalizeHeaderViewController"
private const val HOME_RIGHT_SLOT_CLASS =
    "com.baidu.tieba.homepage.personalize.view.HomeTabBarRightSlot"
private const val HOME_PRELOAD_CONFIG_PARSER_CLASS =
    "com.baidu.tieba.homepage.switchs.HomePreloadMoreConfigParser"
private const val HOME_PRELOAD_CONFIG_COMPANION_CLASS =
    "com.baidu.tieba.homepage.switchs.HomePreloadMoreConfigParser\$a"
private const val COLLECTION_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.CollectTabActivity"
private const val COLLECTION_THREAD_FRAGMENT_CLASS = "com.baidu.tieba.myCollection.ThreadFragment"
private const val HISTORY_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.history.PbHistoryActivity"
private const val HISTORY_DATA_CLASS = "com.baidu.tieba.myCollection.baseHistory.PbHistoryData"
private const val FREE_COPY_POPUP_MENU_CLASS = "com.baidu.tieba.ba6"
private const val FREE_COPY_POPUP_ROUND_LAYOUT_CLASS = "com.baidu.tbadk.core.dialog.RoundLinearLayout"
private const val FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS =
    "com.baidu.tbadk.core.elementsMaven.view.EMTextView"
private const val MSG_TAB_VIEW_MODEL_CLASS = StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS
private const val MSG_TAB_CONTAINER_VIEW_CLASS =
    "com.baidu.tieba.immessagecenter.msgtab.ui.view.MsgCenterContainerView"
private const val IMAGE_PAGER_ADAPTER_CLASS = "com.baidu.tbadk.coreExtra.view.ImagePagerAdapter"
private const val URL_DRAG_IMAGE_VIEW_CLASS = "com.baidu.tbadk.coreExtra.view.UrlDragImageView"
private const val IMAGE_URL_DATA_CLASS = "com.baidu.tbadk.coreExtra.view.ImageUrlData"
private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
private const val TB_MD5_CLASS = "com.baidu.tbadk.core.util.TbMd5"
