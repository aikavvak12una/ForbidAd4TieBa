package com.forbidad4tieba.hook.symbol.model

data class PbSymbols(
    val ad: PbAdSymbolsGroup = PbAdSymbolsGroup(),
    val falling: PbFallingSymbolsGroup = PbFallingSymbolsGroup(),
    val comment: PbCommentSymbolsGroup = PbCommentSymbolsGroup(),
    val gestureScale: PbGestureScaleSymbolsGroup = PbGestureScaleSymbolsGroup(),
    val likeAutoReply: PbLikeAutoReplySymbolsGroup = PbLikeAutoReplySymbolsGroup(),
    val misc: PbMiscSymbolsGroup = PbMiscSymbolsGroup(),
)

data class PbAdSymbolsGroup(
    val earlyInsert: PbEarlyAdInsertSymbolsGroup = PbEarlyAdInsertSymbolsGroup(),
    val bid: PbAdBidSymbolsGroup = PbAdBidSymbolsGroup(),
)

data class PbEarlyAdInsertSymbolsGroup(
    val pbEarlyAdInsertClass: String? = null,
    val pbEarlyAdInsertMethodSpecs: List<String>? = null,
)

data class PbAdBidSymbolsGroup(
    val pbAdBidCommonRequestModelClass: String? = null,
    val pbAdBidCommonRequestStartMethods: List<String>? = null,
    val pbAdBidCommonRequestNotifyMethod: String? = null,
    val pbAdBidPageBrowserRequestModelClass: String? = null,
    val pbAdBidPageBrowserRequestDataMethod: String? = null,
)

data class PbFallingSymbolsGroup(
    val pbFallingViewClass: String? = null,
    val pbFallingInitMethod: String? = null,
    val pbFallingShowMethod: String? = null,
    val pbFallingClearMethod: String? = null,
)

data class PbCommentSymbolsGroup(
    val bottomSheet: ForumBottomSheetSymbolsGroup = ForumBottomSheetSymbolsGroup(),
    val autoLoad: AutoLoadSymbolsGroup = AutoLoadSymbolsGroup(),
    val scroll: PbCommentScrollSymbolsGroup = PbCommentScrollSymbolsGroup(),
    val bottomList: PbCommentBottomListSymbolsGroup = PbCommentBottomListSymbolsGroup(),
    val bottomRecycler: PbCommentBottomRecyclerSymbolsGroup = PbCommentBottomRecyclerSymbolsGroup(),
)

data class ForumBottomSheetSymbolsGroup(
    val forumBottomSheetViewClass: String? = null,
    val forumBottomSheetInitScrollMethod: String? = null,
)

data class AutoLoadSymbolsGroup(
    val autoRefreshTriggerMethod: String? = null,
    val autoLoadMoreConfigClass: String? = null,
    val autoLoadMoreConfigMethod: String? = null,
)

data class PbCommentScrollSymbolsGroup(
    val pbCommentScrollListenerClass: String? = null,
    val pbCommentScrollMethod: String? = null,
    val pbCommentScrollFragmentField: String? = null,
    val pbCommentScrollBottomListenerField: String? = null,
    val pbCommentScrollBottomMethod: String? = null,
)

data class PbCommentBottomListSymbolsGroup(
    val pbCommentBottomListScrollClass: String? = null,
    val pbCommentBottomListScrollMethod: String? = null,
    val pbCommentBottomListOwnerField: String? = null,
)

data class PbCommentBottomRecyclerSymbolsGroup(
    val pbCommentBottomRecyclerScrollClass: String? = null,
    val pbCommentBottomRecyclerScrollMethod: String? = null,
    val pbCommentBottomRecyclerOwnerField: String? = null,
)

data class PbGestureScaleSymbolsGroup(
    val pbGestureScaleManagerClass: String? = null,
    val pbGestureScaleDispatchMethod: String? = null,
    val pbGestureScaleListenerSetterMethod: String? = null,
    val pbGestureScaleListenerClass: String? = null,
    val pbGestureScaleListenerOnScaleMethod: String? = null,
)

data class PbLikeAutoReplySymbolsGroup(
    val agreeView: PbLikeAutoReplyAgreeViewSymbolsGroup = PbLikeAutoReplyAgreeViewSymbolsGroup(),
    val agreeData: PbLikeAutoReplyAgreeDataSymbolsGroup = PbLikeAutoReplyAgreeDataSymbolsGroup(),
    val inputContainer: PbLikeAutoReplyInputContainerSymbolsGroup = PbLikeAutoReplyInputContainerSymbolsGroup(),
)

data class PbLikeAutoReplyAgreeViewSymbolsGroup(
    val pbLikeAutoReplyAgreeViewClass: String? = null,
    val pbLikeAutoReplyAgreeClickMethod: String? = null,
    val pbLikeAutoReplyAgreeViewGetDataMethod: String? = null,
)

data class PbLikeAutoReplyAgreeDataSymbolsGroup(
    val pbLikeAutoReplyAgreeDataClass: String? = null,
    val pbLikeAutoReplyAgreeDataHasAgreeField: String? = null,
    val pbLikeAutoReplyAgreeDataAgreeTypeField: String? = null,
    val pbLikeAutoReplyAgreeDataIsInThreadField: String? = null,
)

data class PbLikeAutoReplyInputContainerSymbolsGroup(
    val pbLikeAutoReplyInputContainerClass: String? = null,
    val pbLikeAutoReplyInputContainerGetInputViewMethod: String? = null,
    val pbLikeAutoReplyInputContainerGetSendViewMethod: String? = null,
)

data class PbMiscSymbolsGroup(
    val commonLayoutPreloader: PbCommonLayoutPreloaderSymbolsGroup = PbCommonLayoutPreloaderSymbolsGroup(),
    val replyServerResponse: ReplyServerResponseSymbolsGroup = ReplyServerResponseSymbolsGroup(),
)

data class PbCommonLayoutPreloaderSymbolsGroup(
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null,
)

data class ReplyServerResponseSymbolsGroup(
    val replyServerResponseClass: String? = null,
    val replyServerResponseDecodeMethod: String? = null,
    val replyServerResponseResultJsonField: String? = null,
)
