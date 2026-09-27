package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

data class FreeCopyPopupSymbols(
    val menuClass: Class<*>,
    val contentViewMethod: Method,
    val textField: Field,
)

data class FreeCopyNativeSymbols(
    val postDataClass: Class<*>,
    val copyMethod: Method,
    val postParseMethod: Method?,
    val subPostParseMethod: Method?,
    val postTitleField: Field?,
    val postFloorField: Field?,
    val subPostTitleField: Field?,
    val subPostFloorField: Field?,
    val postFloorMethod: Method?,
    val richTextViewClass: Class<*>?,
    val longPressMethods: List<Method>,
    val titleBindMethods: List<Method>,
    val titleContainerField: Field?,
    val titleTextField: Field?,
    val titlePostDataMethod: Method?,
    val webViewBindMethod: Method?,
    val webViewGetterMethod: Method?,
    val innerWebViewGetterMethod: Method?,
    val webViewPageDataGetterMethod: Method?,
    val webViewFirstFloorPostGetterMethod: Method?,
    val clipboardWriteMethods: List<Method>,
)

data class PlainUrlClickableSpanSymbols(
    val spanClass: Class<*>,
    val onClickMethods: List<Method>,
    val typeField: Field,
    val urlField: Field,
    val textField: Field,
)

data class PlainUrlMessageDispatchSymbols(
    val messageManagerClass: Class<*>,
    val dispatchMethod: Method,
    val responsedMessageClass: Class<*>,
    val getCmdMethod: Method,
    val customResponsedMessageClass: Class<*>,
    val getDataMethod: Method,
    val applicationClass: Class<*>,
    val getInstMethod: Method,
)

data class PlainUrlMessageDataSymbols(
    val typeField: Field,
    val urlField: Field,
    val textField: Field,
)

data class PlainUrlBrowserHelperSymbols(
    val browserHelperClass: Class<*>,
    val startWebActivityMethods: List<Method>,
)

data class PrivateReadReceiptSymbols(
    val modelClass: Class<*>,
    val modelReadDispatchMethod: Method,
    val processAckMethod: Method,
    val responseErrorMethod: Method,
    val messageManagerGetInstanceMethod: Method,
    val messageManagerGetSocketClientMethod: Method,
    val messageManagerSendMethod: Method,
    val socketDuplicateCheckMethod: Method,
    val requestConstructor: Constructor<*>,
    val requestMessageClass: Class<*>,
    val requestMsgIdField: Field,
    val requestToUidField: Field,
    val modelDataField: Field,
    val pageDataChatListMethod: Method,
    val chatMessageMsgIdMethod: Method,
    val chatMessageUserIdMethod: Method,
    val currentAccountMethod: Method,
)

data class MountCardLinkLayoutSymbols(
    val layoutClass: Class<*>,
    val onClickMethod: Method,
    val dataField: Field,
    val getUrlMethod: Method,
)

data class ShareTrackingParamCleanerSymbols(
    val buildUrlMethod: Method,
)

data class ImageViewerNativeShareSymbols(
    val customMessageClass: Class<*>,
    val shareDialogConfigClass: Class<*>,
    val sendMessageMethod: Method,
    val customMessageGetDataMethod: Method,
    val isImageViewerDialogField: Field,
    val shareItemField: Field,
    val addOutsideTextViewMethod: Method,
    val getRequestDataMethod: Method,
    val setRequestDataMethod: Method,
    val getContextMethod: Method,
    val shareItemTitleField: Field?,
    val shareItemContentField: Field?,
    val shareItemLinkUrlField: Field?,
    val shareItemImageUriField: Field?,
    val shareItemImageUrlField: Field?,
    val shareItemLocalFileField: Field?,
    val itemNameByResMethod: Method,
    val itemNameByTextMethod: Method,
    val fileProviderGetUriMethod: Method,
    val shareIconResId: Int,
)

data class EnterForumWebSymbols(
    val sourceGetUrlMethod: Method?,
    val webLoadMethod: Method,
    val webViewField: Field,
    val setForceCommonMethod: Method,
)

data class ForumNativeTopShiftSymbols(
    val initScrollMethod: Method,
    val smoothInitGetterMethod: Method,
    val setupMethod: Method,
    val maxScrollGetterMethod: Method,
)

data class HomeTopBarRightSlotSymbols(
    val slotClass: Class<*>,
    val stateMethods: List<Method>,
    val searchIconViewMethod: Method,
    val gameIconViewMethod: Method,
    val topBarTipMethod: Method,
    val redDotViewMethod: Method,
)

data class HomeNativeGlassHostDarkModeSwitchTargets(
    val moreActivityClass: Class<*>,
    val controllerField: Field,
    val switchGetterMethod: Method,
    val switchStateField: Field,
    val switchSetOnMethod: Method,
    val switchSetOffMethod: Method,
    val switchCallbackMethod: Method,
)

data class SearchBoxTextAdSymbols(
    val setHintMethod: Method,
    val ownerInitMethod: Method,
    val ownerGetterMethod: Method,
)

data class AutoRefreshSymbols(
    val triggerMethod: Method,
    val netRequestMethods: List<Method>,
    val cacheRestoreMethod: Method?,
    val pullGestureMethod: Method?,
)

data class AutoLoadMoreSymbols(
    val ubsMethod: Method?,
    val configMethod: Method?,
)

data class HomeTabResolvedSymbols(
    val hostClass: Class<*>,
    val rebuildMethod: Method,
    val listField: Field,
    val itemTypeField: String,
    val itemCodeField: String,
    val itemNameField: String,
    val itemUrlField: String,
    val itemMainSetterMethod: String?,
    val itemMainIntField: String?,
    val itemMainBooleanField: String?,
)

data class PbFallingAdSymbols(
    val targetClass: Class<*>,
    val initMethod: Method?,
    val showMethod: Method?,
    val clearMethod: Method?,
)

data class PbEarlyAdBlockMethodSymbols(
    val method: Method,
    val returnsSparseArray: Boolean,
)

data class PbEarlyAdBlockSymbols(
    val targetClass: Class<*>,
    val methods: List<PbEarlyAdBlockMethodSymbols>,
)

data class PbFirstFloorRecommendInsertSymbols(
    val method: Method,
)

data class PbAdRequestFieldPatchSymbols(
    val field: Field,
    val value: Any?,
)

data class PbAdRequestBlockSymbols(
    val pbPageEncodeMethod: Method?,
    val pbPageFieldPatches: List<PbAdRequestFieldPatchSymbols>,
    val pageBrowserAddAdMethod: Method?,
    val commonAdBidTargetClass: Class<*>?,
    val commonAdBidStartMethods: List<Method>,
    val commonAdBidNotifyMethod: Method?,
    val pageBrowserAdBidTargetClass: Class<*>?,
    val pageBrowserAdBidRequestDataMethod: Method?,
)

data class PostAdDataFilterSymbols(
    val setDataMethods: List<Method>,
    val itemClass: Class<*>,
    val getTypeMethod: Method,
    val blockedTypes: Set<Any>,
    val blockedItemClasses: Array<Class<*>>,
)

data class CustomPostCardFilterSymbols(
    val dataListFieldName: String,
    val templateKeyMethodName: String,
    val templatePayloadMethodName: String,
    val headParamsFieldName: String?,
    val recommendNestedDataMethodName: String?,
    val recommendNestedDataListFieldName: String?,
    val schemaGetter: Method? = null,
)

data class FeedAdSymbols(
    val setListMethod: Method,
    val loadMoreMethod: Method?,
    val templateKeyMethodName: String,
    val customPostFilter: CustomPostCardFilterSymbols?,
)

data class FeedInfoLogSymbols(
    val bindMethod: Method,
    val templateKeyMethodName: String?,
)

data class ForumPageAdBlockSymbols(
    val responseParserMethod: Method?,
    val responseAdFields: List<Field>,
    val bottomDataMapperMethod: Method?,
    val bottomDataSetterMethods: List<Method>,
    val bottomGameBarMapperMethod: Method?,
    val headerDataMapperMethod: Method?,
    val rainSetterMethod: Method?,
    val businessPromotShowMethod: Method?,
    val gameFloatingBarShowMethod: Method?,
    val gameFloatingBarField: Field?,
    val businessPromotJumpMethod: Method?,
)

data class ReplyServerResponseLogSymbols(
    val decodeMethod: Method,
    val resultJsonField: Field,
)

data class AgreeServerResponseLogSymbols(
    val decodeLogicMethod: Method,
)

data class ReplyVisibilityProbeSymbols(
    val replyDecodeMethod: Method,
    val replyResultJsonField: Field,
    val addPostRequestClass: Class<*>,
    val addPostRequestDataField: Field,
    val getOriginalMessageMethod: Method,
    val messageGetExtraMethod: Method,
    val messageGetTagMethod: Method,
    val messageSetTagMethod: Method,
    val httpMessageConstructor: Constructor<*>,
    val httpMessageAddParamMethod: Method,
    val httpMessageAddHeaderMethod: Method,
    val messageManagerGetInstanceMethod: Method,
    val messageManagerFindTaskMethod: Method,
    val messageManagerRegisterTaskMethod: Method,
    val messageManagerSendMethod: Method,
    val tbHttpMessageTaskConstructor: Constructor<*>,
    val httpMessageTaskSetResponsedClassMethod: Method,
    val tbHttpMessageTaskSetIsNeedTbsMethod: Method,
    val bdUniqueIdGenMethod: Method,
    val tbadkCoreApplicationGetInstMethod: Method,
    val tbadkCoreApplicationGetZidMethod: Method,
    val tbConfigServerAddressField: Field,
    val tbConfigPbFloorAgreeUrlField: Field,
    val cmdPbFloorAgreeField: Field,
    val agreeResponseClass: Class<*>,
    val agreeDecodeLogicMethod: Method,
)

data class ConstantReturnMethodSymbols(
    val method: Method,
    val value: Any,
)

data class StrategyAdSymbols(
    val constantReturnMethods: List<ConstantReturnMethodSymbols>,
    val zgaMethods: List<Method>,
)

data class HomeBottomEasterEggAdSymbols(
    val parserMethod: Method,
)

data class PbGestureScaleSymbols(
    val dispatchMethod: Method,
)

data class PbBottomEnterBarStableSymbols(
    val bottomEnterBarViewClass: Class<*>?,
    val bottomEnterBarRefreshMethods: List<Method>,
    val enterFrsAnimationTipViewClass: Class<*>?,
)

data class PbBottomEnterBarHotTopicGuideSymbols(
    val guideClass: Class<*>,
    val totalViewMethod: Method,
    val refreshMethods: List<Method>,
)

data class PbScrollCoalesceSymbols(
    val scrollMethod: Method,
)

data class PbCommentBottomListSymbols(
    val listClass: Class<*>,
    val scrollMethod: Method,
    val ownerField: Field,
    val bottomListenerField: Field,
    val bottomMethod: Method,
)

data class PbCommentBottomRecyclerSymbols(
    val recyclerClass: Class<*>,
    val scrollMethod: Method,
    val ownerField: Field,
    val bottomListenerField: Field,
    val bottomMethod: Method,
    val firstVisibleMethod: Method,
    val lastVisibleMethod: Method,
    val getAdapterMethod: Method,
)

data class PbCommentAutoLoadSymbols(
    val listTargets: PbCommentBottomListSymbols?,
    val recyclerTargets: PbCommentBottomRecyclerSymbols?,
)

data class PbLikeAutoReplySymbols(
    val agreeViewClass: Class<*>,
    val inputContainerClass: Class<*>,
    val agreeClickMethod: Method,
    val getDataMethod: Method,
    val hasAgreeField: Field,
    val agreeTypeField: Field,
    val isInThreadField: Field,
    val getInputViewMethod: Method,
    val getSendViewMethod: Method,
    val flow: PbAutoReplyFlowTargets,
)

data class GlobalDirectProfileSymbols(
    val clickableHeaderSetDataMethod: Method,
    val clickableHeaderGetUserIdMethod: Method,
    val customMessageConstructor: java.lang.reflect.Constructor<*>,
    val personInfoConfigClass: Class<*>,
    val personInfoGetContextMethod: Method,
    val personInfoGetIntentMethod: Method,
    val personPolymericConfigConstructor: java.lang.reflect.Constructor<*>,
    val personPolymericCreateNormalConfigMethod: Method,
    val personPolymericGetIntentMethod: Method,
    val personPolymericSetUriMethod: Method,
    val currentAccountMethod: Method?,
    val applicationGetInstMethod: Method,
    val messageManagerGetInstanceMethod: Method,
    val messageManagerSendMethod: Method,
    val urlManagerDealOneLinkMethods: List<Method>,
)
data class MsgTabDefaultNotifySymbols(
    val locateToTabMethod: Method,
)

data class MainTabBottomSymbols(
    val dataClass: Class<*>,
    val addMethod: Method,
    val getListMethod: Method,
    val structureMethod: Method,
    val structureTypeField: Field,
    val structureDynamicIconField: Field?,
    val structureFragmentField: Field?,
)

data class HistorySearchSymbols(
    val activityClass: Class<*>,
    val onCreateMethod: Method?,
    val onResumeMethod: Method?,
    val onDestroyMethod: Method?,
    val activityListUpdateMethod: Method?,
    val adapterField: String,
    val adapterSetListMethod: String,
    val adapterSetListMethodSpec: String,
    val listField: String,
    val activityNavBarField: String,
    val threadNameMethod: String,
    val forumNameMethod: String,
    val userNameMethod: String,
    val descriptionMethod: String,
    val threadIdMethod: String,
    val postIdMethod: String,
    val liveIdMethod: String,
)

data class CollectionSearchSymbols(
    val activityClass: Class<*>,
    val fragmentClass: Class<*>,
    val presenterField: String,
    val presenterListSetterMethod: String,
    val presenterListSetterMethodSpec: String,
    val modelField: String,
    val modelListGetterMethod: String,
    val modelListGetterMethodSpec: String,
    val modelParseMethod: String,
    val modelParseMethodSpec: String,
    val modelListField: String,
    val fragmentDisplayListField: String,
    val activityNavControllerField: String?,
    val navBarField: String,
    val presenterAdapterField: String?,
    val adapterShowFooterMethod: String?,
    val adapterLoadingMethod: String?,
    val adapterHasMoreMethod: String?,
    val editModeMethod: String?,
)

data class DefaultOriginalImageSymbols(
    val pagerAdapterClass: Class<*>?,
    val urlDragImageViewClass: Class<*>,
    val dataClass: Class<*>,
    val setPrimaryItemMethod: String?,
    val setAssistUrlMethod: String?,
    val assistDataMethod: String,
    val originTextMethod: String?,
    val showButtonField: String,
    val blockedField: String,
    val originalProcessField: String,
    val originalUrlField: String,
    val sharedPrefHelperClass: Class<*>?,
    val sharedPrefGetInstanceMethod: String?,
    val sharedPrefPutBooleanMethod: String?,
    val md5Class: Class<*>?,
    val md5Method: String?,
    val triggerMethod: String,
    val directStartMethod: String?,
)

data class AiComponentSymbols(
    val spriteMemeEnableMethod: Method,
    val pbInitSpriteMemeMethod: Method,
    val pbInitAiWriteMethod: Method,
    val pbAiEmojiCreationViewBindMethod: Method? = null,
    val pbPageBrowserAiEmojiCreationBindMethod: Method? = null,
)

data class InputMemeBarSymbols(
    val enableMethod: Method,
)

data class AiImageViewerJumpButtonSymbols(
    val initMethod: Method,
)

data class AutoSignInHybridNativeProxySymbols(
    val jsBridgeClass: Class<*>,
    val nativeNetworkProxyMethod: Method,
    val taskConstructor: Constructor<*>,
    val doInBackgroundMethod: Method,
)
