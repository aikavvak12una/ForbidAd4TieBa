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

internal data class PbFallingScanSymbols(
    val viewClass: String? = null,
    val initMethod: String? = null,
    val showMethod: String? = null,
    val clearMethod: String? = null,
)

internal data class TypeAdapterDataFilterScanSymbols(
    val setDataMethod: String? = null,
    val dataItemClass: String? = null,
    val dataGetTypeMethod: String? = null,
)

internal data class StrategyAdScanSymbols(
    val splashAdHelperClass: String? = null,
    val splashAdHelperMethod: String? = null,
    val closeAdDataClass: String? = null,
    val closeAdDataMethodG1: String? = null,
    val closeAdDataMethodJ1: String? = null,
    val zgaClass: String? = null,
    val zgaMethods: List<String>? = null,
)

internal data class SettingsScanSymbols(
    val settingsClass: String? = null,
    val initMethod: String? = null,
    val containerField: String? = null,
)

internal data class HomeHeaderScanSymbols(
    val searchBoxViewClass: String? = null,
    val searchBoxSetHintMethod: String? = null,
    val homeSearchBoxOwnerClass: String? = null,
    val homeSearchBoxInitMethod: String? = null,
    val homeSearchBoxGetterMethod: String? = null,
    val homeRightSlotClass: String? = null,
    val homeRightSlotStateMethods: List<String>? = null,
)

internal data class HomeTabScanSymbols(
    val tabClass: String? = null,
    val rebuildMethod: String? = null,
    val listField: String? = null,
)

internal data class ForumPageAdScanSymbols(
    val responseDataClass: String? = null,
    val responseParserMethod: String? = null,
    val responseAdFields: List<String> = emptyList(),
    val mapperClass: String? = null,
    val bottomDataMapperMethod: String? = null,
    val bottomDataClass: String? = null,
    val businessPromotSetterMethod: String? = null,
    val privatePopSetterMethod: String? = null,
    val spriteBubbleSetterMethod: String? = null,
    val maskPopSetterMethod: String? = null,
    val bottomGameBarMapperMethod: String? = null,
    val headerDataMapperMethod: String? = null,
    val headerDataClass: String? = null,
    val rainDataClass: String? = null,
    val rainSetterMethod: String? = null,
    val dialogControllerClass: String? = null,
    val businessPromotShowMethod: String? = null,
    val animationShowMethod: String? = null,
    val gameFloatingBarControllerClass: String? = null,
    val gameFloatingBarShowMethod: String? = null,
    val gameFloatingBarField: String? = null,
    val businessPromotBizClass: String? = null,
    val businessPromotJumpMethod: String? = null,
)

internal data class EnterForumWebScanSymbols(
    val controllerClass: String? = null,
    val webLoadMethod: String? = null,
    val initInfoDataClass: String? = null,
    val initInfoGetUrlMethod: String? = null,
)

internal data class MineTabWebBlockScanSymbols(
    val webViewClass: String? = null,
    val loadUrlMethod: String? = null,
    val getUrlMethod: String? = null,
    val getInnerWebViewMethod: String? = null,
)

internal data class HomeSideBarWebBlockScanSymbols(
    val sideBarWebViewClass: String? = null,
    val tbWebViewClass: String? = null,
    val getWebViewMethod: String? = null,
    val getUrlMethod: String? = null,
    val getInnerWebViewMethod: String? = null,
    val loadUrlMethods: List<String> = emptyList(),
)

internal data class RecommendCardNestedDataScanSymbols(
    val nestedDataMethod: String? = null,
    val nestedDataListField: String? = null,
)

internal data class FeedTemplateScanSymbols(
    val keyMethod: String? = null,
    val payloadMethod: String? = null,
    val loadMoreMethod: String? = null,
)

internal data class FeedCardScanSymbols(
    val bindMethod: String? = null,
    val bindMethodSpec: String? = null,
    val dataListField: String? = null,
    val feedHeadParamsField: String? = null,
)

internal data class ReplyServerResponseLogScanSymbols(
    val responseClass: String? = null,
    val decodeMethod: String? = null,
    val resultJsonField: String? = null,
)

internal data class AgreeServerResponseLogScanSymbols(
    val responseClass: String? = null,
    val decodeLogicMethod: String? = null,
)

internal data class ReplyVisibilityProbeScanSymbols(
    val replyResponseClass: String? = null,
    val replyDecodeMethod: String? = null,
    val replyResultJsonField: String? = null,
    val addPostRequestClass: String? = null,
    val addPostRequestDataField: String? = null,
    val responsedMessageClass: String? = null,
    val getOriginalMessageMethod: String? = null,
    val messageClass: String? = null,
    val messageGetExtraMethod: String? = null,
    val messageGetTagMethod: String? = null,
    val messageSetTagMethod: String? = null,
    val httpMessageClass: String? = null,
    val httpMessageConstructor: String? = null,
    val httpMessageAddParamMethod: String? = null,
    val httpMessageAddHeaderMethod: String? = null,
    val messageManagerClass: String? = null,
    val messageManagerGetInstanceMethod: String? = null,
    val messageManagerFindTaskMethod: String? = null,
    val messageManagerRegisterTaskMethod: String? = null,
    val messageManagerSendMethod: String? = null,
    val tbHttpMessageTaskClass: String? = null,
    val tbHttpMessageTaskConstructor: String? = null,
    val httpMessageTaskSetResponsedClassMethod: String? = null,
    val tbHttpMessageTaskSetIsNeedTbsMethod: String? = null,
    val bdUniqueIdClass: String? = null,
    val bdUniqueIdGenMethod: String? = null,
    val tbadkCoreApplicationClass: String? = null,
    val tbadkCoreApplicationGetInstMethod: String? = null,
    val tbadkCoreApplicationGetZidMethod: String? = null,
    val tbConfigClass: String? = null,
    val tbConfigServerAddressField: String? = null,
    val tbConfigPbFloorAgreeUrlField: String? = null,
    val cmdConfigHttpClass: String? = null,
    val cmdPbFloorAgreeField: String? = null,
    val agreeResponseClass: String? = null,
    val agreeDecodeLogicMethod: String? = null,
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

internal data class FreeCopyPopupScanSymbols(
    val menuClass: String? = null,
    val contentViewMethod: String? = null,
    val textField: String? = null,
)

internal data class ForumBottomSheetScanSymbols(
    val viewClass: String? = null,
    val initScrollMethod: String? = null,
)

internal data class AutoLoadMoreConfigScanSymbols(
    val configClass: String? = null,
    val configMethod: String? = null,
)

internal data class PbCommentInteractionScanSymbols(
    val scrollListenerClass: String? = null,
    val scrollMethod: String? = null,
    val scrollFragmentField: String? = null,
    val scrollBottomListenerField: String? = null,
    val scrollBottomMethod: String? = null,
    val bottomListScrollClass: String? = null,
    val bottomListScrollMethod: String? = null,
    val bottomListOwnerField: String? = null,
    val bottomRecyclerScrollClass: String? = null,
    val bottomRecyclerScrollMethod: String? = null,
    val bottomRecyclerOwnerField: String? = null,
    val gestureScaleManagerClass: String? = null,
    val gestureScaleDispatchMethod: String? = null,
    val gestureScaleListenerSetterMethod: String? = null,
    val gestureScaleListenerClass: String? = null,
    val gestureScaleListenerOnScaleMethod: String? = null,
)

internal data class PbBottomEnterBarScanSymbols(
    val bottomEnterBarViewClass: String? = null,
    val bottomEnterBarConstructorCount: Int? = null,
    val bottomEnterBarRefreshMethodSpecs: List<String> = emptyList(),
    val enterFrsAnimationTipViewClass: String? = null,
    val enterFrsAnimationTipConstructorCount: Int? = null,
    val enterFrsAnimationTipCallerClasses: List<String> = emptyList(),
    val hotTopicGuideTotalViewMethod: String? = null,
    val hotTopicGuideRefreshMethodSpecs: List<String> = emptyList(),
)

internal data class ImageViewerShareScanSymbols(
    val shareTrackBuilderClass: String? = null,
    val shareTrackBuildUrlMethod: String? = null,
    val shareTrackAppendQueryMethod: String? = null,
    val itemClass: String? = null,
    val itemTitleField: String? = null,
    val itemContentField: String? = null,
    val itemLinkUrlField: String? = null,
    val itemImageUriField: String? = null,
    val itemImageUrlField: String? = null,
    val itemLocalFileField: String? = null,
    val configClass: String? = null,
    val addOutsideMethod: String? = null,
    val getRequestDataMethod: String? = null,
    val setRequestDataMethod: String? = null,
    val getContextMethod: String? = null,
    val isDialogField: String? = null,
    val itemField: String? = null,
    val itemViewClass: String? = null,
    val itemNameByResMethod: String? = null,
    val itemNameByTextMethod: String? = null,
    val iconResId: Int? = null,
)

internal data class AiComponentScanSymbols(
    val spriteMemePanControllerClass: String? = null,
    val spriteMemeEnableMethod: String? = null,
    val pbNewInputContainerClass: String? = null,
    val pbInitSpriteMemeMethod: String? = null,
    val pbInitAiWriteMethod: String? = null,
    val pbAiEmojiCreationViewBindMethod: String? = null,
    val pbPageBrowserAiEmojiCreationViewClass: String? = null,
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

internal data class HomeNativeGlassTopChromeSymbols(
    val tabSelectedMethodSpecs: List<String> = emptyList(),
)

internal data class HomeNativeGlassSubPbNextPageSymbols(
    val methodName: String? = null,
    val parameterTypeName: String? = null,
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
    val callbackMethodName: String? = null,
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

internal data class DexGameFloatingBarMatch(
    val controllerClassName: String,
    val showMethodName: String,
    val floatingBarFieldName: String?,
    val score: Int,
    val evidence: String,
)

internal data class DexPbPageBrowserAiEmojiCreationMatch(
    val viewClassName: String,
    val bindMethodName: String,
    val score: Int,
    val evidence: String,
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
    val presenterListSetterMethodSpec: String? = null,
    val presenterAdapterField: String? = null,
    val modelField: String? = null,
    val modelListGetterMethod: String? = null,
    val modelListGetterMethodSpec: String? = null,
    val modelParseMethod: String? = null,
    val modelParseMethodSpec: String? = null,
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
    val adapterSetListMethodSpec: String? = null,
    val listField: String? = null,
    val activityListUpdateMethod: String? = null,
    val activityListUpdateMethodSpec: String? = null,
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

internal data class AutoSignInScanSymbols(
    val networkClass: String? = null,
    val networkConstructorSpec: String? = null,
    val addPostDataMethod: String? = null,
    val postNetDataMethod: String? = null,
    val setNeedTbsMethod: String? = null,
    val setNeedSigMethod: String? = null,
    val tbConfigClass: String? = null,
    val serverAddressField: String? = null,
    val coreApplicationClass: String? = null,
    val currentAccountMethod: String? = null,
    val hybridProxyClass: String? = null,
    val hybridProxyConstructorSpec: String? = null,
    val hybridJsBridgeClass: String? = null,
    val hybridNativeNetworkProxyMethod: String? = null,
    val hybridTaskClass: String? = null,
    val hybridTaskConstructorSpec: String? = null,
    val hybridTaskDoInBackgroundMethod: String? = null,
)

internal data class CollectionPresenterCandidate(
    val fieldName: String,
    val setterMethod: String,
    val setterMethodSpec: String,
    val presenterClass: Class<*>,
)

internal data class CollectionModelCandidate(
    val fieldName: String,
    val getterMethod: String,
    val getterMethodSpec: String,
    val parseMethod: String,
    val parseMethodSpec: String,
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
    val setterMethodSpec: String,
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
