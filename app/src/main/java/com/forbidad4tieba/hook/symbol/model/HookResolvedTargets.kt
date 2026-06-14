package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

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

internal data class ShareTrackingParamCleanerSymbols(
    val buildUrlMethod: Method,
)

internal data class ImageViewerNativeShareSymbols(
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

internal data class EnterForumWebSymbols(
    val sourceGetUrlMethod: Method?,
    val webLoadMethod: Method?,
)

internal data class ForumNativeTopShiftSymbols(
    val initScrollMethod: Method,
)

internal data class HomeTopBarRightSlotSymbols(
    val slotClass: Class<*>,
    val stateMethods: List<Method>,
    val searchIconViewMethod: Method,
    val gameIconViewMethod: Method,
    val topBarTipMethod: Method,
    val redDotViewMethod: Method,
)

internal data class HomeNativeGlassHostDarkModeSwitchTargets(
    val moreActivityClass: Class<*>,
    val controllerField: Field,
    val switchGetterMethod: Method,
    val switchStateField: Field,
    val switchSetOnMethod: Method,
    val switchSetOffMethod: Method,
    val switchCallbackMethod: Method,
)

internal data class SearchBoxTextAdSymbols(
    val setHintMethod: Method,
    val ownerInitMethod: Method,
    val ownerGetterMethod: Method,
)

internal data class AutoRefreshSymbols(
    val triggerMethod: Method,
)

internal data class AutoLoadMoreSymbols(
    val ubsMethod: Method?,
    val configMethod: Method?,
)

internal data class HomeTabResolvedSymbols(
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

internal data class PbFallingAdSymbols(
    val targetClass: Class<*>,
    val initMethod: Method?,
    val showMethod: Method?,
    val clearMethod: Method?,
)

internal data class PbEarlyAdBlockMethodSymbols(
    val method: Method,
    val returnsSparseArray: Boolean,
)

internal data class PbEarlyAdBlockSymbols(
    val targetClass: Class<*>,
    val methods: List<PbEarlyAdBlockMethodSymbols>,
)

internal data class PbAdRequestFieldPatchSymbols(
    val field: Field,
    val value: Any?,
)

internal data class PbAdRequestBlockSymbols(
    val pbPageEncodeMethod: Method?,
    val pbPageFieldPatches: List<PbAdRequestFieldPatchSymbols>,
    val pageBrowserAddAdMethod: Method?,
    val commonAdBidTargetClass: Class<*>?,
    val commonAdBidStartMethods: List<Method>,
    val commonAdBidNotifyMethod: Method?,
    val pageBrowserAdBidTargetClass: Class<*>?,
    val pageBrowserAdBidRequestDataMethod: Method?,
)

internal data class PostAdDataFilterSymbols(
    val setDataMethod: Method,
    val itemClass: Class<*>,
    val getTypeMethod: Method,
    val blockedTypes: Set<Any>,
    val blockedItemClasses: Array<Class<*>>,
)

internal data class CustomPostCardFilterSymbols(
    val dataListFieldName: String,
    val templateKeyMethodName: String,
    val templatePayloadMethodName: String,
    val headParamsFieldName: String?,
    val recommendNestedDataMethodName: String?,
    val recommendNestedDataListFieldName: String?,
)

internal data class FeedAdSymbols(
    val setListMethod: Method,
    val loadMoreMethod: Method?,
    val templateKeyMethodName: String,
    val customPostFilter: CustomPostCardFilterSymbols?,
)

internal data class FeedInfoLogSymbols(
    val bindMethod: Method,
    val templateKeyMethodName: String?,
)

internal data class ForumPageAdBlockSymbols(
    val responseParserMethod: Method?,
    val responseAdFields: List<Field>,
    val bottomDataMapperMethod: Method?,
    val bottomDataSetterMethods: List<Method>,
    val bottomGameBarMapperMethod: Method?,
    val headerDataMapperMethod: Method?,
    val rainSetterMethod: Method?,
    val businessPromotShowMethod: Method?,
    val animationShowMethod: Method?,
    val gameFloatingBarShowMethod: Method?,
    val gameFloatingBarField: Field?,
    val businessPromotJumpMethod: Method?,
)

internal data class ReplyServerResponseLogSymbols(
    val decodeMethod: Method,
    val resultJsonField: Field,
)

internal data class AgreeServerResponseLogSymbols(
    val decodeLogicMethod: Method,
)

internal data class ReplyVisibilityProbeSymbols(
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

internal data class ConstantReturnMethodSymbols(
    val method: Method,
    val value: Any,
)

internal data class StrategyAdSymbols(
    val constantReturnMethods: List<ConstantReturnMethodSymbols>,
    val zgaMethods: List<Method>,
)

internal data class PbGestureScaleSymbols(
    val dispatchMethod: Method,
)

internal data class PbScrollCoalesceSymbols(
    val scrollMethod: Method,
)

internal data class PbCommentBottomListSymbols(
    val listClass: Class<*>,
    val scrollMethod: Method,
    val ownerField: Field,
    val bottomListenerField: Field,
    val bottomMethod: Method,
)

internal data class PbCommentBottomRecyclerSymbols(
    val recyclerClass: Class<*>,
    val scrollMethod: Method,
    val ownerField: Field,
    val bottomListenerField: Field,
    val bottomMethod: Method,
    val firstVisibleMethod: Method,
    val lastVisibleMethod: Method,
    val getAdapterMethod: Method,
)

internal data class PbCommentAutoLoadSymbols(
    val listTargets: PbCommentBottomListSymbols?,
    val recyclerTargets: PbCommentBottomRecyclerSymbols?,
)

internal data class PbLikeAutoReplySymbols(
    val agreeViewClass: Class<*>,
    val inputContainerClass: Class<*>,
    val agreeClickMethod: Method,
    val getDataMethod: Method,
    val hasAgreeField: Field,
    val agreeTypeField: Field,
    val isInThreadField: Field,
    val getInputViewMethod: Method,
    val getSendViewMethod: Method,
)

internal data class MsgTabDefaultNotifySymbols(
    val locateToTabMethod: Method,
)

internal data class MainTabBottomSymbols(
    val dataClass: Class<*>,
    val addMethod: Method,
    val getListMethod: Method,
    val structureMethod: Method,
    val structureTypeField: Field,
    val structureDynamicIconField: Field?,
    val structureFragmentField: Field?,
)

internal data class HistorySearchSymbols(
    val activityClass: Class<*>,
    val onCreateMethod: Method?,
    val onResumeMethod: Method?,
    val onDestroyMethod: Method?,
    val activityListUpdateMethod: Method?,
    val adapterField: String,
    val adapterSetListMethod: String,
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

internal data class CollectionSearchSymbols(
    val activityClass: Class<*>,
    val fragmentClass: Class<*>,
    val presenterField: String,
    val presenterListSetterMethod: String,
    val modelField: String,
    val modelListGetterMethod: String,
    val modelParseMethod: String,
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

internal data class DefaultOriginalImageSymbols(
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
