package com.forbidad4tieba.hook.symbol.validation

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.scan.AiComponentSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.HomeTabItemSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbAdBidSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbEarlyAdInsertSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PlainUrlClickableSpanSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HookSymbolValidator {
    private const val TAG = "[HookSymbolResolver]"
    private const val FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS =
        "com.baidu.tbadk.core.elementsMaven.view.EMTextView"
    private const val NAV_CLASS = StableTiebaHookPoints.NAVIGATION_BAR_CLASS
    private const val PB_COMMON_REQUEST_MODEL_CLASS =
        "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
    private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"
    private const val TB_WEB_VIEW_CLASS = "com.baidu.tieba.browser.TbWebView"
    private const val PERSONALIZE_PAGE_VIEW_CLASS =
        "com.baidu.tieba.homepage.personalize.PersonalizePageView"
    private const val BD_SWITCH_VIEW_CLASS = "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
    private const val PB_FRAGMENT_CLASS = StableTiebaHookPoints.PB_FRAGMENT_CLASS
    private const val AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"
    private const val AI_SPRITE_MEME_PAN_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan"
    private const val AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout"
    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"

    fun isUsable(symbols: HookSymbols, cl: ClassLoader): Boolean {
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
    val hasForumPageAdSymbols =
        symbols.forumResponseDataClass != null ||
            symbols.forumResponseParserMethod != null ||
            !symbols.forumResponseAdFields.isNullOrEmpty() ||
            symbols.forumPageMapperClass != null ||
            symbols.forumBottomDataMapperMethod != null ||
            symbols.forumBottomDataClass != null ||
            symbols.forumBusinessPromotSetterMethod != null ||
            symbols.forumPrivatePopSetterMethod != null ||
            symbols.forumSpriteBubbleSetterMethod != null ||
            symbols.forumMaskPopSetterMethod != null ||
            symbols.forumBottomGameBarMapperMethod != null ||
            symbols.forumHeaderDataMapperMethod != null ||
            symbols.forumHeaderDataClass != null ||
            symbols.forumRainDataClass != null ||
            symbols.forumRainSetterMethod != null ||
            symbols.forumDialogControllerClass != null ||
            symbols.forumBusinessPromotShowMethod != null ||
            symbols.forumAnimationShowMethod != null ||
            symbols.forumGameFloatingBarControllerClass != null ||
            symbols.forumGameFloatingBarShowMethod != null ||
            symbols.forumGameFloatingBarField != null ||
            symbols.forumBusinessPromotBizClass != null ||
            symbols.forumBusinessPromotJumpMethod != null
    if (hasForumPageAdSymbols && !isForumPageAdValid(symbols, cl)) return false
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
    val hasHomeNativeGlassHostDarkModeSwitchSymbols =
        symbols.homeNativeGlassHostDarkModeMoreActivityClass != null ||
            symbols.homeNativeGlassHostDarkModeControllerField != null ||
            symbols.homeNativeGlassHostDarkModeSwitchGetterMethod != null ||
            symbols.homeNativeGlassHostDarkModeSwitchStateField != null ||
            symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod != null ||
            symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod != null ||
            symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod != null
    if (
        hasHomeNativeGlassHostDarkModeSwitchSymbols &&
        !isHomeNativeGlassHostDarkModeSwitchValid(symbols, cl)
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
    val hasPbLikeAutoReplySymbols =
        symbols.pbLikeAutoReplyAgreeViewClass != null ||
            symbols.pbLikeAutoReplyAgreeClickMethod != null ||
            symbols.pbLikeAutoReplyAgreeViewGetDataMethod != null ||
            symbols.pbLikeAutoReplyAgreeDataClass != null ||
            symbols.pbLikeAutoReplyAgreeDataHasAgreeField != null ||
            symbols.pbLikeAutoReplyAgreeDataAgreeTypeField != null ||
            symbols.pbLikeAutoReplyAgreeDataIsInThreadField != null ||
            symbols.pbLikeAutoReplyInputContainerClass != null ||
            symbols.pbLikeAutoReplyInputContainerGetInputViewMethod != null ||
            symbols.pbLikeAutoReplyInputContainerGetSendViewMethod != null
    if (hasPbLikeAutoReplySymbols && !isPbLikeAutoReplyValid(symbols, cl)) return false
    val hasAiComponentSymbols =
        symbols.aiSpriteMemePanControllerClass != null ||
            symbols.aiSpriteMemeEnableMethod != null ||
            symbols.aiPbNewInputContainerClass != null ||
            symbols.aiPbNewInputContainerInitSpriteMemeMethod != null ||
            symbols.aiPbNewInputContainerInitAiWriteMethod != null ||
            symbols.aiPbAiEmojiCreationViewBindMethod != null ||
            symbols.aiPbPageBrowserAiEmojiCreationViewClass != null ||
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

private fun isHomeNativeGlassHostDarkModeSwitchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val moreActivityClassName = symbols.homeNativeGlassHostDarkModeMoreActivityClass ?: return false
    val controllerFieldName = symbols.homeNativeGlassHostDarkModeControllerField ?: return false
    val getterMethodName = symbols.homeNativeGlassHostDarkModeSwitchGetterMethod ?: return false
    val stateFieldName = symbols.homeNativeGlassHostDarkModeSwitchStateField ?: return false
    val setOnMethodName = symbols.homeNativeGlassHostDarkModeSwitchSetOnMethod ?: return false
    val setOffMethodName = symbols.homeNativeGlassHostDarkModeSwitchSetOffMethod ?: return false
    val callbackMethodName = symbols.homeNativeGlassHostDarkModeSwitchCallbackMethod ?: return false
    return try {
        val moreActivityClass = safeFindClass(moreActivityClassName, cl) ?: return false
        val switchClass = safeFindClass(BD_SWITCH_VIEW_CLASS, cl) ?: return false
        val controllerField = collectInstanceFields(moreActivityClass).singleOrNull { field ->
            field.name == controllerFieldName &&
                !Modifier.isStatic(field.modifiers)
        } ?: return false
        val getterOk = collectInstanceMethods(controllerField.type).any { method ->
            method.name == getterMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                switchClass.isAssignableFrom(method.returnType)
        }
        val getterMethod = collectInstanceMethods(controllerField.type).singleOrNull { method ->
            method.name == getterMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                switchClass.isAssignableFrom(method.returnType)
        } ?: return false
        if (!getterOk) return false
        val stateField = collectInstanceFields(switchClass).singleOrNull { field ->
            field.name == stateFieldName &&
                !Modifier.isStatic(field.modifiers) &&
                field.type.isEnum
        } ?: return false
        val switchMethods = switchClass.declaredMethods.toList()
        val setOnOk = switchMethods.any { method ->
            method.name == setOnMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Void.TYPE
        }
        if (!setOnOk) return false
        val setOffOk = switchMethods.any { method ->
            method.name == setOffMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Void.TYPE
        }
        if (!setOffOk) return false
        collectInstanceMethods(moreActivityClass).any { method ->
            val params = method.parameterTypes
            method.name == callbackMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                params.size == 2 &&
                View::class.java.isAssignableFrom(params[0]) &&
                params[0].isAssignableFrom(getterMethod.returnType) &&
                params[1] == stateField.type
        }
    } catch (_: Throwable) {
        false
    }
}

private fun isHomeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val homeTabClass = symbols.homeTabClass ?: return false
    val homeTabRebuildMethod = symbols.homeTabRebuildMethod ?: return false
    val homeTabListField = symbols.homeTabListField ?: return false
    return try {
        val homeClass = safeFindClass(homeTabClass, cl) ?: return false
        val methodOk = homeClass.declaredMethods.any {
            it.name == homeTabRebuildMethod &&
                it.parameterTypes.isEmpty() &&
                it.returnType == Void.TYPE
        }
        if (!methodOk) return false
        val listFieldOk = homeClass.declaredFields.any { it.name == homeTabListField }
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

        val itemClass = resolveHomeTabItemClass(homeClass, homeTabListField) ?: return false
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
    val settingsClassName = symbols.settingsClass ?: return false
    val settingsInitMethod = symbols.settingsInitMethod ?: return false
    val settingsContainerField = symbols.settingsContainerField ?: return false
    return try {
        val settingsClass = safeFindClass(settingsClassName, cl) ?: return false
        val navClass = safeFindClass(NAV_CLASS, cl) ?: return false
        val methodOk = settingsClass.declaredMethods.any { method ->
            if (method.name != settingsInitMethod) return@any false
            if (method.returnType != Void.TYPE) return@any false
            val p = method.parameterTypes
            (p.size == 2 && Context::class.java.isAssignableFrom(p[0]) && navClass.isAssignableFrom(p[1])) ||
                (p.size == 1 && View::class.java.isAssignableFrom(p[0]))
        }
        if (!methodOk) return false
        settingsClass.declaredFields.any { it.name == settingsContainerField }
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

private fun isPbLikeAutoReplyValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val agreeViewClassName = symbols.pbLikeAutoReplyAgreeViewClass ?: return false
    val agreeClickMethodName = symbols.pbLikeAutoReplyAgreeClickMethod ?: return false
    val getDataMethodName = symbols.pbLikeAutoReplyAgreeViewGetDataMethod ?: return false
    val agreeDataClassName = symbols.pbLikeAutoReplyAgreeDataClass ?: return false
    val hasAgreeFieldName = symbols.pbLikeAutoReplyAgreeDataHasAgreeField ?: return false
    val agreeTypeFieldName = symbols.pbLikeAutoReplyAgreeDataAgreeTypeField ?: return false
    val isInThreadFieldName = symbols.pbLikeAutoReplyAgreeDataIsInThreadField ?: return false
    val inputContainerClassName = symbols.pbLikeAutoReplyInputContainerClass ?: return false
    val getInputViewMethodName = symbols.pbLikeAutoReplyInputContainerGetInputViewMethod ?: return false
    val getSendViewMethodName = symbols.pbLikeAutoReplyInputContainerGetSendViewMethod ?: return false
    return try {
        val agreeViewClass = safeFindClass(agreeViewClassName, cl) ?: return false
        val agreeDataClass = safeFindClass(agreeDataClassName, cl) ?: return false
        val inputContainerClass = safeFindClass(inputContainerClassName, cl) ?: return false
        if (!LinearLayout::class.java.isAssignableFrom(agreeViewClass)) return false
        if (!LinearLayout::class.java.isAssignableFrom(inputContainerClass)) return false

        val clickMethodOk = agreeViewClass.declaredMethods.any { method ->
            method.name == agreeClickMethodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == View::class.java
        }
        if (!clickMethodOk) return false

        val getDataOk = collectInstanceMethods(agreeViewClass).any { method ->
            method.name == getDataMethodName &&
                method.parameterTypes.isEmpty() &&
                method.returnType == agreeDataClass
        }
        if (!getDataOk) return false

        val fields = collectInstanceFields(agreeDataClass)
        val hasAgreeOk = fields.any { field ->
            field.name == hasAgreeFieldName && isBooleanType(field.type)
        }
        if (!hasAgreeOk) return false
        val agreeTypeOk = fields.any { field ->
            field.name == agreeTypeFieldName && isIntType(field.type)
        }
        if (!agreeTypeOk) return false
        val isInThreadOk = fields.any { field ->
            field.name == isInThreadFieldName && isBooleanType(field.type)
        }
        if (!isInThreadOk) return false

        val inputMethods = collectInstanceMethods(inputContainerClass)
        val inputOk = inputMethods.any { method ->
            method.name == getInputViewMethodName &&
                method.parameterTypes.isEmpty() &&
                (EditText::class.java.isAssignableFrom(method.returnType) ||
                    View::class.java.isAssignableFrom(method.returnType))
        }
        if (!inputOk) return false
        inputMethods.any { method ->
            method.name == getSendViewMethodName &&
                method.parameterTypes.isEmpty() &&
                View::class.java.isAssignableFrom(method.returnType)
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

    val pageBrowserViewName = symbols.aiPbPageBrowserAiEmojiCreationViewClass
    val pageBrowserBindName = symbols.aiPbPageBrowserAiEmojiCreationBindMethod
    if (!pageBrowserViewName.isNullOrBlank() || !pageBrowserBindName.isNullOrBlank()) {
        val viewClassName = pageBrowserViewName ?: return false
        if (pageBrowserBindName.isNullOrBlank()) return false
        val viewClass = safeFindClass(viewClassName, cl) ?: return false
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

private fun isForumPageAdValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    return try {
        val readiness = ForumPageAdSymbolReadiness.evaluate(symbols)
        if (readiness.response) {
            if (!isForumPageResponsePathValid(symbols, cl)) return false
        }

        if (readiness.bottom) {
            if (!isForumPageBottomPathValid(symbols, cl)) return false
        }

        if (readiness.gameBar) {
            if (!isForumPageStaticMapperValid(symbols.forumPageMapperClass, symbols.forumBottomGameBarMapperMethod, null, cl)) {
                return false
            }
        }

        if (readiness.rain) {
            if (!isForumPageRainPathValid(symbols, cl)) return false
        }

        if (readiness.dialog) {
            if (!isForumPageDialogPathValid(symbols, cl)) return false
        }

        if (readiness.floating) {
            if (!isForumPageFloatingPathValid(symbols, cl)) return false
        }

        if (readiness.biz) {
            if (!isForumPageBusinessBizPathValid(symbols, cl)) return false
        }

        readiness.any
    } catch (t: Throwable) {
        XposedCompat.log("$TAG forumPageAd validation failed: ${sanitizeScanStatusText(formatScanException(t))}")
        false
    }
}

private fun isForumPageResponsePathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val className = symbols.forumResponseDataClass ?: return false
    val methodName = symbols.forumResponseParserMethod ?: return false
    val responseClass = safeFindClass(className, cl) ?: return false
    val hasParser = collectInstanceMethods(responseClass).any { method ->
        method.name == methodName &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1
    }
    if (!hasParser) return false
    val fieldNames = symbols.forumResponseAdFields.orEmpty()
        .filter { it.isNotBlank() }
    if (fieldNames.size < 4) return false
    return fieldNames.all { fieldName -> findForumPageField(responseClass, fieldName) != null }
}

private fun isForumPageBottomPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    if (!isForumPageStaticMapperValid(
            symbols.forumPageMapperClass,
            symbols.forumBottomDataMapperMethod,
            symbols.forumBottomDataClass,
            cl,
        )
    ) {
        return false
    }
    val dataClass = safeFindClass(symbols.forumBottomDataClass ?: return false, cl) ?: return false
    val setterNames = listOf(
        symbols.forumBusinessPromotSetterMethod,
        symbols.forumPrivatePopSetterMethod,
        symbols.forumSpriteBubbleSetterMethod,
        symbols.forumMaskPopSetterMethod,
    ).filterNot { it.isNullOrBlank() }
    return setterNames.size >= 3 && setterNames.all { name ->
        collectInstanceMethods(dataClass).any { method ->
            method.name == name &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive
        }
    }
}

private fun isForumPageRainPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    if (!isForumPageStaticMapperValid(
            symbols.forumPageMapperClass,
            symbols.forumHeaderDataMapperMethod,
            symbols.forumHeaderDataClass,
            cl,
        )
    ) {
        return false
    }
    val headerClass = safeFindClass(symbols.forumHeaderDataClass ?: return false, cl) ?: return false
    val rainClass = safeFindClass(symbols.forumRainDataClass ?: return false, cl) ?: return false
    val setterName = symbols.forumRainSetterMethod ?: return false
    return collectInstanceMethods(headerClass).any { method ->
        method.name == setterName &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(rainClass))
    }
}

private fun isForumPageStaticMapperValid(
    mapperClassName: String?,
    methodName: String?,
    returnClassName: String?,
    cl: ClassLoader,
): Boolean {
    val mapperClass = safeFindClass(mapperClassName ?: return false, cl) ?: return false
    val expectedReturn = returnClassName
        ?.takeIf { it.isNotBlank() }
        ?.let { safeFindClass(it, cl) ?: return false }
    return mapperClass.declaredMethods.any { method ->
        Modifier.isStatic(method.modifiers) &&
            method.name == methodName &&
            method.parameterTypes.size == 1 &&
            method.returnType != Void.TYPE &&
            (expectedReturn == null || method.returnType == expectedReturn)
    }
}

private fun isForumPageDialogPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val controllerClass = safeFindClass(symbols.forumDialogControllerClass ?: return false, cl) ?: return false
    val businessName = symbols.forumBusinessPromotShowMethod
    if (!businessName.isNullOrBlank()) {
        val ok = collectInstanceMethods(controllerClass).any { method ->
            method.name == businessName &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                !method.parameterTypes[1].isPrimitive
        }
        if (!ok) return false
    }
    val animationName = symbols.forumAnimationShowMethod
    if (!animationName.isNullOrBlank()) {
        val ok = collectInstanceMethods(controllerClass).any { method ->
            method.name == animationName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
        if (!ok) return false
    }
    return !businessName.isNullOrBlank() || !animationName.isNullOrBlank()
}

private fun isForumPageFloatingPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val controllerClass = safeFindClass(symbols.forumGameFloatingBarControllerClass ?: return false, cl) ?: return false
    val showName = symbols.forumGameFloatingBarShowMethod
    if (!showName.isNullOrBlank()) {
        val ok = collectInstanceMethods(controllerClass).any { method ->
            method.name == showName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
        if (!ok) return false
    }
    val fieldName = symbols.forumGameFloatingBarField
    if (!fieldName.isNullOrBlank() && findForumPageField(controllerClass, fieldName) == null) {
        return false
    }
    return !showName.isNullOrBlank() || !fieldName.isNullOrBlank()
}

private fun isForumPageBusinessBizPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
    val bizClass = safeFindClass(symbols.forumBusinessPromotBizClass ?: return false, cl) ?: return false
    val methodName = symbols.forumBusinessPromotJumpMethod ?: return false
    return collectInstanceMethods(bizClass).any { method ->
        method.name == methodName &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(String::class.java))
    }
}

private fun findForumPageField(clazz: Class<*>, fieldName: String): Field? {
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        try {
            return current.getDeclaredField(fieldName)
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    return null
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

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<Method> =
        ScanReflection.collectInstanceMethods(clazz)

    private fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    private fun isBooleanType(type: Class<*>): Boolean = ScanReflection.isBooleanType(type)

    private fun isIntType(type: Class<*>): Boolean = ScanReflection.isIntType(type)

    private fun resolveHomeTabItemClass(homeClass: Class<*>, listFieldName: String): Class<*>? =
        HomeTabItemSymbolScanner.resolveHomeTabItemClass(homeClass, listFieldName)

    private fun resolvePbEarlyAdType(typeName: String, cl: ClassLoader): Class<*>? =
        PbEarlyAdInsertSymbolScanner.resolveType(typeName, cl)

    private fun findPbAdBidPageBrowserRequestDataMethodInHierarchy(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Method? = PbAdBidSymbolScanner.findPageBrowserRequestDataMethodInHierarchy(cls, continuationClass)

    private fun isPlainUrlClickableSpanOnClickMethod(method: Method, methodName: String): Boolean =
        PlainUrlClickableSpanSymbolScanner.isOnClickMethod(method, methodName)

    private fun isPlainUrlClickableSpanStructureValid(
        spanClass: Class<*>,
        onClickMethod: Method,
        typeField: Field,
        urlField: Field,
        textField: Field,
    ): Boolean = PlainUrlClickableSpanSymbolScanner.isStructureValid(
        spanClass,
        onClickMethod,
        typeField,
        urlField,
        textField,
    )

    private fun isPlainUrlMessageDispatchMethod(method: Method, responsedMessageClass: Class<*>): Boolean =
        ScanReflection.isPlainUrlMessageDispatchMethod(method, responsedMessageClass)

    private fun isMountCardLinkLayoutOnClickMethod(method: Method, methodName: String): Boolean =
        ScanReflection.isMountCardLinkLayoutOnClickMethod(method, methodName)

    private fun resolveMountCardLinkLayoutDataField(layoutClass: Class<*>, dataClass: Class<*>): java.lang.reflect.Field? =
        ScanReflection.resolveMountCardLinkLayoutDataField(layoutClass, dataClass)

    private fun isMountCardLinkLayoutStructureValid(
        layoutClass: Class<*>,
        onClickMethod: Method,
        dataClass: Class<*>,
        dataField: Field,
        getUrlMethod: Method,
    ): Boolean = ScanReflection.isMountCardLinkLayoutStructureValid(
        layoutClass,
        onClickMethod,
        dataClass,
        dataField,
        getUrlMethod,
    )

    private fun isAiPbNewInputContainerClassValid(
        inputContainerClass: Class<*>,
        spriteMemePanClass: Class<*>?,
    ): Boolean = AiComponentSymbolScanner.isAiPbNewInputContainerClassValid(
        inputContainerClass,
        spriteMemePanClass,
    )

    private fun isAiSpriteMemeEnableMethod(method: Method, inputShowTypeClass: Class<*>?): Boolean =
        AiComponentSymbolScanner.isAiSpriteMemeEnableMethod(method, inputShowTypeClass)

    private fun isPbNewInputContextInitMethod(method: Method): Boolean =
        AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)

    private fun scoreImageViewerJumpButtonOwnerClass(clazz: Class<*>, layoutClass: Class<*>): Int =
        AiComponentSymbolScanner.scoreImageViewerJumpButtonOwnerClass(clazz, layoutClass)

    private fun isImageViewerJumpButtonInitMethod(method: Method): Boolean =
        AiComponentSymbolScanner.isImageViewerJumpButtonInitMethod(method)

    private fun isAiPbEmojiCreationViewBindMethod(method: Method, cl: ClassLoader): Boolean =
        AiComponentSymbolScanner.isAiPbEmojiCreationViewBindMethod(method, cl)

    private fun isPbPageBrowserAiEmojiCreationBindMethod(method: Method): Boolean =
        AiComponentSymbolScanner.isPbPageBrowserAiEmojiCreationBindMethod(method)

    private fun sanitizeScanStatusText(raw: String): String =
        HookSymbolScanDiagnostics.sanitizeScanStatusText(raw)

    private fun formatScanException(t: Throwable): String =
        HookSymbolScanDiagnostics.formatScanException(t)
}
