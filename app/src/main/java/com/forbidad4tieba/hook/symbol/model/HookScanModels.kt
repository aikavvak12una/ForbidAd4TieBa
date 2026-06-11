package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class ScanCandidateSet(
    val obfuscated: List<String>,
    val expanded: List<String>,
) {
    val all: List<String>
        get() = (obfuscated + expanded).distinct()
}

internal data class PbEarlyAdInsertScanSymbols(
    val className: String?,
    val methodSpecs: List<String>,
)

internal data class PbAdBidScanSymbols(
    val commonRequestModelClass: String? = null,
    val commonRequestStartMethods: List<String> = emptyList(),
    val commonRequestNotifyMethod: String? = null,
    val pageBrowserRequestModelClass: String? = null,
    val pageBrowserRequestDataMethod: String? = null,
)

internal data class TypeAdapterDataFilterScanSymbols(
    val setDataMethod: String? = null,
    val dataItemClass: String? = null,
    val dataGetTypeMethod: String? = null,
)

internal data class RecommendCardNestedDataScanSymbols(
    val nestedDataMethod: String? = null,
    val nestedDataListField: String? = null,
)

internal data class ReplyServerResponseLogScanSymbols(
    val responseClass: String? = null,
    val decodeMethod: String? = null,
    val resultJsonField: String? = null,
)

internal data class RecommendCardNestedDataCandidate(
    val bindMethodName: String,
    val stateClassName: String,
    val nestedDataMethodName: String,
    val nestedDataClassName: String,
    val listFieldName: String,
)

internal data class PlainUrlClickableSpanScanSymbols(
    val className: String? = null,
    val onClickMethod: String? = null,
    val onClickOwnerClasses: List<String> = emptyList(),
    val typeField: String? = null,
    val urlField: String? = null,
    val textField: String? = null,
)

internal data class PlainUrlClickableSpanFieldSymbols(
    val typeField: Field,
    val urlField: Field,
    val textField: Field,
    val evidence: String,
)

internal data class PlainUrlMessageDispatchScanSymbols(
    val messageManagerClass: String? = null,
    val dispatchMethod: String? = null,
    val responsedMessageClass: String? = null,
    val getCmdMethod: String? = null,
    val customResponsedMessageClass: String? = null,
    val getDataMethod: String? = null,
    val applicationClass: String? = null,
    val getInstMethod: String? = null,
)

internal data class PrivateReadReceiptScanSymbols(
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

internal data class PlainUrlClickableSpanClassMatch(
    val clazz: Class<*>,
    val score: Int,
)

internal data class MountCardLinkLayoutScanSymbols(
    val layoutClass: String? = null,
    val onClickMethod: String? = null,
    val dataField: String? = null,
    val dataClass: String? = null,
    val getUrlMethod: String? = null,
)

internal data class AiComponentScanSymbols(
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

internal data class HomeNativeGlassResourceIds(
    val subPbNextPageMoreViewId: Int? = null,
    val pbReplyTitleDividerViewId: Int? = null,
    val dynamicBackgroundColorIds: List<Int> = emptyList(),
)

internal data class HomeNativeGlassSortSwitchSymbols(
    val backgroundPaintField: String? = null,
    val slideDrawMethod: String? = null,
    val slidePathField: String? = null,
)

internal data class HomeNativeGlassEnterForumCapsuleSymbols(
    val controllerClass: String? = null,
    val initMethod: String? = null,
    val refreshMethod: String? = null,
    val viewField: String? = null,
    val titleField: String? = null,
)

internal data class HomeNativeGlassHostDarkModeSwitchSymbols(
    val moreActivityClass: String? = null,
    val controllerField: String? = null,
    val switchGetterMethod: String? = null,
    val switchStateField: String? = null,
    val switchSetOnMethod: String? = null,
    val switchSetOffMethod: String? = null,
    val switchCallbackMethod: String? = null,
)

internal data class DexHostDarkModeSwitchMatch(
    val controllerFieldName: String,
    val getterMethodName: String,
    val score: Int,
    val evidence: String,
)

internal data class HomeNativeGlassEnterForumCapsuleClassCandidate(
    val clazz: Class<*>,
    val fields: List<Field>,
    val methods: List<Method>,
    val score: Int,
    val evidence: String,
)

internal data class HomeNativeGlassEnterForumCapsuleResolvedCandidate(
    val symbols: HomeNativeGlassEnterForumCapsuleSymbols,
    val score: Int,
    val evidence: String,
)

internal data class PbLikeAutoReplyScanSymbols(
    val agreeViewClass: String? = null,
    val agreeClickMethod: String? = null,
    val agreeViewGetDataMethod: String? = null,
    val agreeDataClass: String? = null,
    val agreeDataHasAgreeField: String? = null,
    val agreeDataAgreeTypeField: String? = null,
    val agreeDataIsInThreadField: String? = null,
    val inputContainerClass: String? = null,
    val inputContainerGetInputViewMethod: String? = null,
    val inputContainerGetSendViewMethod: String? = null,
)

internal data class ShareIconOwnerCandidate(
    val className: String,
    val score: Int,
)

internal data class DexShareIconMatch(
    val ownerClassName: String,
    val ownerMethodName: String,
    val resId: Int,
    val score: Int,
)

internal data class DexAutoRefreshMatch(
    val ownerMethodName: String,
    val score: Int,
    val evidence: String,
)

internal data class DexAiComponentInitMatch(
    val ownerMethodName: String,
    val score: Int,
    val evidence: String,
    val strong: Boolean = true,
)

internal data class DexPbLikeAgreeClickMatch(
    val ownerMethodName: String,
    val score: Int,
    val evidence: String,
)

internal data class DexPbAdBidModelMatch(
    val className: String,
    val requestImplMethodName: String,
    val kind: String,
    val score: Int,
    val evidence: String,
)

internal data class DexPbAdBidScanSymbols(
    val commonModelClassName: String? = null,
    val pageBrowserModelClassName: String? = null,
    val pageBrowserRequestDataMethodName: String? = null,
)

internal data class DexPbAdBidRawScan(
    val modelMatches: List<DexPbAdBidModelMatch> = emptyList(),
    val pageBrowserRequestDataMethodName: String? = null,
)

internal object DexEnterForumCapsuleMethodKind {
    const val INIT = "init"
    const val REFRESH = "refresh"
}

internal data class DexEnterForumCapsuleMethodMatch(
    val ownerMethodName: String,
    val kind: String,
    val score: Int,
    val evidence: String,
    val viewFieldName: String? = null,
    val titleFieldName: String? = null,
)

internal data class CollectionSearchScanSymbols(
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

internal data class HistorySearchScanSymbols(
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

internal data class MsgTabScanSymbols(
    val locateToTabMethod: String? = null,
    val containerSelectMethod: String? = null,
    val containerExtDataField: String? = null,
)

internal data class MainTabBottomScanSymbols(
    val dataClass: String? = null,
    val addMethod: String? = null,
    val getListMethod: String? = null,
    val delegateGetStructureMethod: String? = null,
    val structureTypeField: String? = null,
    val structureDynamicIconField: String? = null,
    val structureFragmentField: String? = null,
)

internal data class OriginalImageScanSymbols(
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

internal data class CollectionPresenterCandidate(
    val fieldName: String,
    val setterMethod: String,
    val presenterClass: Class<*>,
)

internal data class CollectionModelCandidate(
    val fieldName: String,
    val getterMethod: String,
    val parseMethod: String,
    val listFieldName: String?,
)

internal data class CollectionAdapterSymbols(
    val presenterAdapterField: String? = null,
    val showFooterMethod: String? = null,
    val loadingMethod: String? = null,
    val hasMoreMethod: String? = null,
)

internal data class CollectionNavigationSymbols(
    val activityNavControllerField: String? = null,
    val navBarField: String? = null,
)

internal data class HistoryAdapterCandidate(
    val fieldName: String,
    val setterMethod: String,
)

internal data class HomeTabItemScanSymbols(
    val typeField: String? = null,
    val codeField: String? = null,
    val nameField: String? = null,
    val urlField: String? = null,
    val mainSetterMethod: String? = null,
    val mainIntField: String? = null,
    val mainBooleanField: String? = null,
)

internal data class TargetAppVersionInfo(
    val versionCode: Long,
    val versionName: String?,
)
