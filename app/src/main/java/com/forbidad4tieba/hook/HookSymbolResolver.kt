package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.symbol.validation.*

import com.forbidad4tieba.hook.symbol.status.*

import com.forbidad4tieba.hook.symbol.scan.*

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.symbol.cache.*

import android.content.Context
import android.os.Bundle
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.widget.AbsListView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.concurrent.thread

internal object HookSymbolResolver {
    private const val TAG = "[HookSymbolResolver]"
    private const val KEY_SYMBOL_FP = HookSymbolCacheKeys.SYMBOL_FP
    private const val KEY_SYMBOL_JSON = HookSymbolCacheKeys.SYMBOL_JSON
    private const val KEY_CACHE_MODULE_VERSION = HookSymbolCacheKeys.MODULE_VERSION
    private const val KEY_SYMBOL_VERIFIED_FP = HookSymbolCacheKeys.VERIFIED_FP
    // Update these target version bounds when adapting a new Tieba release.
    private const val TARGET_TIEBA_VERSION_NAME = "22.8.1.1"
    private const val MIN_TIEBA_VERSION_CODE = 369098752L
    private const val MAX_TIEBA_VERSION_CODE = 369623301L
    private const val VERSION_TYPE_META_NAME = "versionType"
    private const val OFFICIAL_VERSION_TYPE = "3"

    private data class CachedMethodSpec(
        val name: String,
        val returnTypeName: String,
        val parameterTypeNames: List<String>,
    )

    private const val PLAIN_URL_CLICKABLE_SPAN_CLASS = "com.baidu.tieba.ui7"
    private const val PLAIN_URL_CLICKABLE_SPAN_TYPE_FIELD = "d"
    private const val PLAIN_URL_CLICKABLE_SPAN_URL_FIELD = "e"
    private const val PLAIN_URL_CLICKABLE_SPAN_TEXT_FIELD = "i"
    private const val PLAIN_URL_CLICK_MESSAGE_CMD = 2001332
    private const val PLAIN_URL_MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val PLAIN_URL_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.ResponsedMessage"
    private const val PLAIN_URL_CUSTOM_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.CustomResponsedMessage"
    private const val PLAIN_URL_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val PLAIN_URL_GET_CMD_METHOD = "getCmd"
    private const val PLAIN_URL_GET_DATA_METHOD = "getData"
    private const val PLAIN_URL_GET_INST_METHOD = "getInst"
    private const val SEARCH_BOX_HEADER_CONTAINER_CLASS = "androidx.coordinatorlayout.widget.CoordinatorLayout"
    private const val AI_SPRITE_MEME_PAN_CONTROLLER_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePanController"
    private const val AI_SPRITE_MEME_PAN_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan"
    private const val AI_PB_NEW_INPUT_CONTAINER_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewInputContainer"
    private const val AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"
    private const val AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout"
    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val REPLY_SERVER_RESPONSE_CLASS = "com.baidu.tieba.write.message.AddPostHttpResponse"
    private const val REPLY_SERVER_RESPONSE_DECODE_METHOD = "decodeInBackGround"
    private const val REPLY_SERVER_RESPONSE_RESULT_JSON_FIELD = "resultJSON"
    private const val JSON_HTTP_RESPONSED_MESSAGE_CLASS = "com.baidu.tbadk.message.http.JsonHttpResponsedMessage"
    private const val AGREE_SERVER_RESPONSE_CLASS = "com.baidu.tieba.pb.data.PbFloorAgreeResponseMessage"
    private const val AGREE_SERVER_RESPONSE_DECODE_LOGIC_METHOD = "decodeLogicInBackGround"
    private const val ADD_POST_REQUEST_CLASS = "com.baidu.tieba.write.message.AddPostRequest"
    private const val ADD_POST_REQUEST_DATA_FIELD = "requestData"
    private const val RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.ResponsedMessage"
    private const val RESPONSED_MESSAGE_GET_ORIGINAL_METHOD = "getOrginalMessage"
    private const val MESSAGE_CLASS = "com.baidu.adp.framework.message.Message"
    private const val MESSAGE_GET_EXTRA_METHOD = "getExtra"
    private const val MESSAGE_GET_TAG_METHOD = "getTag"
    private const val MESSAGE_SET_TAG_METHOD = "setTag"
    private const val HTTP_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpMessage"
    private const val HTTP_MESSAGE_ADD_PARAM_METHOD = "addParam"
    private const val HTTP_MESSAGE_ADD_HEADER_METHOD = "addHeader"
    private const val MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val MESSAGE_MANAGER_GET_INSTANCE_METHOD = "getInstance"
    private const val MESSAGE_MANAGER_FIND_TASK_METHOD = "findTask"
    private const val MESSAGE_MANAGER_REGISTER_TASK_METHOD = "registerTask"
    private const val MESSAGE_MANAGER_SEND_MESSAGE_METHOD = "sendMessage"
    private const val MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.MessageTask"
    private const val HTTP_MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.HttpMessageTask"
    private const val HTTP_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpResponsedMessage"
    private const val HTTP_MESSAGE_TASK_SET_RESPONSE_CLASS_METHOD = "setResponsedClass"
    private const val TB_HTTP_MESSAGE_TASK_CLASS = "com.baidu.tbadk.task.TbHttpMessageTask"
    private const val TB_HTTP_MESSAGE_TASK_SET_NEED_TBS_METHOD = "setIsNeedTbs"
    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"
    private const val BD_UNIQUE_ID_GEN_METHOD = "gen"
    private const val TBADK_CORE_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val TBADK_CORE_APPLICATION_GET_INST_METHOD = "getInst"
    private const val TBADK_CORE_APPLICATION_GET_ZID_METHOD = "getZid"
    private const val TB_CONFIG_CLASS = "com.baidu.tbadk.TbConfig"
    private const val TB_CONFIG_SERVER_ADDRESS_FIELD = "SERVER_ADDRESS"
    private const val TB_CONFIG_PB_FLOOR_AGREE_URL_FIELD = "PB_FLOOR_AGREE_URL"
    private const val CMD_CONFIG_HTTP_CLASS = "com.baidu.tbadk.core.frameworkData.CmdConfigHttp"
    private const val CMD_CONFIG_HTTP_PB_FLOOR_AGREE_FIELD = "CMD_PB_FLOOR_AGREE"
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
    private const val HOME_NATIVE_GLASS_MORE_ACTIVITY_CLASS = "com.baidu.tieba.setting.more.MoreActivity"
    private const val HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS =
        "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
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
    private const val MOUNT_CARD_LINK_LAYOUT_CLASS =
        "com.baidu.tbadk.core.view.commonMountCard.TbMountCardLinkLayout"
    private const val MOUNT_CARD_LINK_INFO_DATA_CLASS = "com.baidu.tbadk.data.CardLinkInfoData"
    private const val MOUNT_CARD_LINK_LAYOUT_ON_CLICK_METHOD = "onClick"
    private const val MOUNT_CARD_LINK_INFO_GET_URL_METHOD = "getUrl"
    private val PLAIN_URL_CLICKABLE_SPAN_CONTAINER_CLASSES = listOf(
        "com.baidu.tieba.si7",
        "com.baidu.tieba.qi7",
        "com.baidu.tbadk.widget.richText.TbRichTextItem",
    )
    private val plainUrlMessageDataSymbolsCache = ConcurrentHashMap<Class<*>, PlainUrlMessageDataSymbols>()

    private val SCAN_WHITELIST_CLASSES = listOf(
        "com.baidu.tieba.zj9",
        "com.baidu.tieba.cke",
        "com.baidu.tieba.k1a",
        "com.baidu.tieba.feed.list.FeedTemplateAdapter",
        "com.baidu.tieba.forum.controller.ForumDialogController",
        "com.baidu.tieba.forum.controller.GameFloatingBarController",
        "com.baidu.tieba.forum.hybrid.biz.BusinessPromotBiz",
        "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt",
        "com.baidu.tbadk.data.CloseAdData",
        FREE_COPY_POPUP_MENU_CLASS,

        StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS,
        StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS,
        StableTiebaHookPoints.PB_FALLING_VIEW_CLASS,
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
        StableTiebaHookPoints.AGREE_VIEW_CLASS,
        StableTiebaHookPoints.AGREE_DATA_CLASS,
        StableTiebaHookPoints.PB_NEW_INPUT_CONTAINER_CLASS,
        StableTiebaHookPoints.FORUM_BOTTOM_SHEET_VIEW_CLASS,
        AI_SPRITE_MEME_PAN_CONTROLLER_CLASS,
        AI_PB_NEW_INPUT_CONTAINER_CLASS,
    )

    private val memoryCache = HookSymbolMemoryCache()

    fun getMemorySymbols(): HookSymbols? = memoryCache.currentSymbols()

    fun resolveAutoSignInHybridNativeProxySymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AutoSignInHybridNativeProxySymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: scan symbols unavailable")
                return null
            }
            fun required(name: String, value: String?): String? {
                return value?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[AutoSignIn] hybrid native proxy skipped: missing $name")
                    null
                }
            }

            val proxyClassName = required(
                "autoSignInHybridProxyClass",
                resolvedSymbols.autoSignInHybridProxyClass,
            ) ?: return null
            val proxyConstructorSpec = required(
                "autoSignInHybridProxyConstructorSpec",
                resolvedSymbols.autoSignInHybridProxyConstructorSpec,
            ) ?: return null
            val jsBridgeClassName = required(
                "autoSignInHybridJsBridgeClass",
                resolvedSymbols.autoSignInHybridJsBridgeClass,
            ) ?: return null
            val nativeNetworkProxyMethodName = required(
                "autoSignInHybridNativeNetworkProxyMethod",
                resolvedSymbols.autoSignInHybridNativeNetworkProxyMethod,
            ) ?: return null
            val taskClassName = required(
                "autoSignInHybridTaskClass",
                resolvedSymbols.autoSignInHybridTaskClass,
            ) ?: return null
            val taskConstructorSpec = required(
                "autoSignInHybridTaskConstructorSpec",
                resolvedSymbols.autoSignInHybridTaskConstructorSpec,
            ) ?: return null
            val doInBackgroundMethodName = required(
                "autoSignInHybridTaskDoInBackgroundMethod",
                resolvedSymbols.autoSignInHybridTaskDoInBackgroundMethod,
            ) ?: return null
            val proxyClass = safeFindClass(proxyClassName, cl) ?: error("proxy class not found: $proxyClassName")
            val jsBridgeClass =
                safeFindClass(jsBridgeClassName, cl) ?: error("js bridge class not found: $jsBridgeClassName")
            val proxyConstructor = proxyClass.declaredConstructors.singleOrNull { constructor ->
                proxyConstructorSpec == "jsBridge" &&
                constructor.parameterTypes.size == 1 &&
                    constructor.parameterTypes[0] == jsBridgeClass
            } ?: error("proxy constructor mismatch: $proxyClassName($jsBridgeClassName)")
            val nativeNetworkProxyMethod = jsBridgeClass.declaredMethods.singleOrNull { method ->
                method.name == nativeNetworkProxyMethodName &&
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
            } ?: error("bridge method mismatch: $jsBridgeClassName.$nativeNetworkProxyMethodName")
            val taskClass = safeFindClass(taskClassName, cl) ?: error("task class not found: $taskClassName")
            val taskConstructor = taskClass.declaredConstructors.singleOrNull { constructor ->
                taskConstructorSpec ==
                    "java.lang.String,java.lang.String,int,int,long,java.util.HashMap,android.webkit.WebView" &&
                    isHybridNativeProxyTaskConstructor(constructor)
            } ?: error("task constructor mismatch: $taskClassName")
            val doInBackgroundMethod = taskClass.declaredMethods.singleOrNull { method ->
                method.name == doInBackgroundMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isArray &&
                    method.parameterTypes[0].componentType == Any::class.java &&
                    (java.util.Map::class.java.isAssignableFrom(method.returnType) ||
                        method.returnType == Any::class.java)
            } ?: error("task doInBackground mismatch: $taskClassName.$doInBackgroundMethodName")

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

    private fun isHybridNativeProxyTaskConstructor(constructor: Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 7 &&
            types[0] == String::class.java &&
            types[1] == String::class.java &&
            isIntType(types[2]) &&
            isIntType(types[3]) &&
            types[4] == Long::class.javaPrimitiveType &&
            java.util.HashMap::class.java.isAssignableFrom(types[5]) &&
            types[6] == android.webkit.WebView::class.java
    }

    fun featureStatusMap(symbols: HookSymbols?): Map<String, HookFeatureStatus> {
        return HookFeatureStatusDeriver.deriveWithOverrides(symbols)
    }

    fun isScanVersionCheckFailed(symbols: HookSymbols?): Boolean {
        return symbols != null && symbols.scanSupportState != ScanSupportState.SUPPORTED
    }

    fun hasScanErrors(symbols: HookSymbols?, failedLines: List<String>? = null): Boolean {
        if (symbols == null) return true
        if (symbols.source != "scan") return true
        if (symbols.scanErrors.isNotEmpty()) return true
        val missingLines = failedLines ?: formatUnavailableHookPointStatusLines(symbols)
        return missingLines.isNotEmpty()
    }

    fun formatUnavailableHookPointStatusLines(symbols: HookSymbols?): List<String> {
        return formatHookPointStatusLines(symbols).filter(::isUnavailableHookPointStatusLine)
    }

    fun formatScanIssueLines(symbols: HookSymbols?): List<String> {
        if (symbols == null) return listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-")
        return formatUnavailableHookPointStatusLines(symbols).ifEmpty { symbols.scanErrors }
    }

    private fun isUnavailableHookPointStatusLine(line: String): Boolean {
        return line.contains(" state=MISSING ") ||
            line.contains(" state=PARTIAL ") ||
            line.contains(" state=ERROR ")
    }

    fun formatScanVersionWarning(symbols: HookSymbols?): String? {
        if (!isScanVersionCheckFailed(symbols)) return null
        return UiText.Settings.scanTiebaVersionNotAdapted(TARGET_TIEBA_VERSION_NAME)
    }

    fun formatScanFeatureWarning(): String {
        return UiText.Settings.scanFeatureAbnormal(TARGET_TIEBA_VERSION_NAME)
    }

    fun formatFeatureStatusLines(symbols: HookSymbols?): List<String> {
        return HookSymbolStatusFormatter.formatFeatureStatusLines(
            statusMap = featureStatusMap(symbols),
            featureKeys = HookFeatureStatusDeriver.featureKeys,
        )
    }

    fun formatHookPointStatusLines(symbols: HookSymbols?): List<String> {
        return HookSymbolStatusFormatter.formatHookPointStatusLines(
            symbols = symbols,
            aiPbAiEmojiCreationViewClass = AI_PB_AI_EMOJI_CREATION_VIEW_CLASS,
            aiPbAiEmojiCreationPageBrowserViewClass =
                symbols?.aiPbPageBrowserAiEmojiCreationViewClass
                    ?: AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS,
            msgTabViewModelClass = MSG_TAB_VIEW_MODEL_CLASS,
            msgTabContainerViewClass = MSG_TAB_CONTAINER_VIEW_CLASS,
        )
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
                    PlainUrlClickableSpanSymbolScanner.isOnClickMethod(method, onClickMethodName)
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

            if (!PlainUrlClickableSpanSymbolScanner.isStructureValid(spanClass, onClickMethods.first(), typeField, urlField, textField)) {
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
                    ScanReflection.isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
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

    fun resolvePlainUrlBrowserHelperSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PlainUrlBrowserHelperSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] browser helper skipped: scan symbols unavailable")
                return null
            }
            val browserHelperClassName =
                resolvedSymbols.plainUrlBrowserHelperClass?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] browser helper skipped: missing class")
                    return null
                }
            val startWebActivityMethodName =
                resolvedSymbols.plainUrlBrowserHelperStartWebActivityMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] browser helper skipped: missing startWebActivity")
                    return null
                }
            val browserHelperClass = safeFindClass(browserHelperClassName, cl) ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] browser helper skipped: class not found: $browserHelperClassName")
                return null
            }
            val startWebActivityMethods = browserHelperClass.declaredMethods
                .filter { method ->
                    PlainUrlBrowserHelperSymbolScanner.isStartWebActivityMethod(
                        method,
                        startWebActivityMethodName,
                    )
                }
                .distinctBy { method ->
                    method.name + "#" + method.parameterTypes.joinToString(",") { it.name }
                }
            if (startWebActivityMethods.isEmpty()) {
                XposedCompat.log(
                    "[PlainUrlDirectBrowserHook] browser helper skipped: startWebActivity overloads mismatch",
                )
                return null
            }
            startWebActivityMethods.forEach { it.isAccessible = true }
            PlainUrlBrowserHelperSymbols(
                browserHelperClass = browserHelperClass,
                startWebActivityMethods = startWebActivityMethods,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] browser helper symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePlainUrlWebContainerSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PlainUrlWebContainerSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] web container skipped: scan symbols unavailable")
                return null
            }
            val activityClassName =
                resolvedSymbols.plainUrlWebContainerActivityClass?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[PlainUrlDirectBrowserHook] web container skipped: missing activity class")
                    return null
                }
            val activityClass = safeFindClass(activityClassName, cl) ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] web container skipped: class not found: $activityClassName")
                return null
            }
            val initDataMethod = resolvedSymbols.plainUrlWebContainerInitDataMethod
                ?.takeIf { it.isNotBlank() }
                ?.let { methodName ->
                    activityClass.declaredMethods.firstOrNull { method ->
                        PlainUrlWebContainerSymbolScanner.isInitDataMethod(method, methodName)
                    } ?: run {
                        XposedCompat.log("[PlainUrlDirectBrowserHook] web container initData skipped: method mismatch")
                        null
                    }
                }
            val shouldOverrideUrlLoadingMethod = resolvePlainUrlWebContainerNavigationMethod(resolvedSymbols, cl)
            if (initDataMethod == null && shouldOverrideUrlLoadingMethod == null) {
                XposedCompat.log("[PlainUrlDirectBrowserHook] web container skipped: no valid methods")
                return null
            }
            initDataMethod?.isAccessible = true
            shouldOverrideUrlLoadingMethod?.isAccessible = true
            PlainUrlWebContainerSymbols(
                webContainerActivityClass = activityClass,
                initDataMethod = initDataMethod,
                shouldOverrideUrlLoadingMethod = shouldOverrideUrlLoadingMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] web container symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePlainUrlWebContainerNavigationMethod(
        symbols: HookSymbols,
        cl: ClassLoader,
    ): Method? {
        val webViewClientClassName = symbols.plainUrlWebContainerWebViewClientClass?.takeIf { it.isNotBlank() }
            ?: return null
        val methodName = symbols.plainUrlWebContainerShouldOverrideUrlLoadingMethod?.takeIf { it.isNotBlank() }
            ?: return null
        val webViewClientClass = safeFindClass(webViewClientClassName, cl) ?: run {
            XposedCompat.log(
                "[PlainUrlDirectBrowserHook] web container navigation skipped: class not found: $webViewClientClassName",
            )
            return null
        }
        return webViewClientClass.declaredMethods.firstOrNull { method ->
            PlainUrlWebContainerSymbolScanner.isShouldOverrideUrlLoadingMethod(method, methodName)
        } ?: run {
            XposedCompat.log("[PlainUrlDirectBrowserHook] web container navigation skipped: method mismatch")
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
            val messageManagerGetSocketClientMethodName = required(
                "privateReadReceiptMessageManagerGetSocketClientMethod",
                resolvedSymbols.privateReadReceiptMessageManagerGetSocketClientMethod,
            ) ?: return null
            val messageSendMethodName = required(
                "privateReadReceiptMessageSendMethod",
                resolvedSymbols.privateReadReceiptMessageSendMethod,
            ) ?: return null
            val messageBaseClassName = required(
                "privateReadReceiptMessageBaseClass",
                resolvedSymbols.privateReadReceiptMessageBaseClass,
            ) ?: return null
            val socketClientClassName = required(
                "privateReadReceiptSocketClientClass",
                resolvedSymbols.privateReadReceiptSocketClientClass,
            ) ?: return null
            val socketDuplicateCheckMethodName = required(
                "privateReadReceiptSocketDuplicateCheckMethod",
                resolvedSymbols.privateReadReceiptSocketDuplicateCheckMethod,
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
            val socketClientClass = safeFindClass(socketClientClassName, cl) ?: return null
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
            val messageManagerGetSocketClientMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetSocketClientMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == socketClientClass
            } ?: return null
            val socketDuplicateCheckMethod = socketClientClass.declaredMethods.singleOrNull { method ->
                method.name == socketDuplicateCheckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(requestClass)
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
                messageManagerGetSocketClientMethod,
                messageManagerSendMethod,
                socketDuplicateCheckMethod,
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
                messageManagerGetSocketClientMethod = messageManagerGetSocketClientMethod,
                messageManagerSendMethod = messageManagerSendMethod,
                socketDuplicateCheckMethod = socketDuplicateCheckMethod,
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

    fun resolvePlainUrlClickSpanMarkerField(cl: ClassLoader): Field? {
        return try {
            val tbSingletonClass = safeFindClass(StableTiebaHookPoints.TB_SINGLETON_CLASS, cl) ?: return null
            tbSingletonClass.declaredFields.singleOrNull { field ->
                field.name == "isClickSpan" &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
        } catch (t: Throwable) {
            XposedCompat.log("[PlainUrlDirectBrowserHook] click span marker resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
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
                ScanReflection.isMountCardLinkLayoutOnClickMethod(method, onClickMethodName)
            } ?: run {
                XposedCompat.log("[PlainUrlDirectBrowserHook] mount card skipped: onClick mismatch: $layoutClassName.$onClickMethodName")
                return null
            }
            val dataField = ScanReflection.resolveMountCardLinkLayoutDataField(layoutClass, dataClass)
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
            if (!ScanReflection.isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)) {
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

    fun resolveShareTrackingParamCleanerSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ShareTrackingParamCleanerSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ShareTrackingParamCleanerHook] skipped: scan symbols unavailable")
                return null
            }
            val builderClassName = resolvedSymbols.shareTrackBuilderClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[ShareTrackingParamCleanerHook] skipped: missing shareTrackBuilderClass")
                return null
            }
            val buildUrlMethodName = resolvedSymbols.shareTrackBuildUrlMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[ShareTrackingParamCleanerHook] skipped: missing shareTrackBuildUrlMethod")
                return null
            }
            val builderClass = safeFindClass(builderClassName, cl) ?: run {
                XposedCompat.log("[ShareTrackingParamCleanerHook] skipped: class not found: $builderClassName")
                return null
            }
            val buildUrlMethod = builderClass.declaredMethods.singleOrNull { method ->
                method.name == buildUrlMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            } ?: run {
                XposedCompat.log(
                    "[ShareTrackingParamCleanerHook] skipped: method mismatch: " +
                        "$builderClassName.$buildUrlMethodName(String,String,String,boolean)",
                )
                return null
            }
            buildUrlMethod.isAccessible = true
            ShareTrackingParamCleanerSymbols(buildUrlMethod = buildUrlMethod)
        } catch (t: Throwable) {
            XposedCompat.log("[ShareTrackingParamCleanerHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveImageViewerNativeShareSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ImageViewerNativeShareSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ImageViewerNativeShareHook] skipped: scan symbols unavailable")
                return null
            }
            val configClassName = requiredImageViewerShareSymbol(
                "imageViewerShareConfigClass",
                resolvedSymbols.imageViewerShareConfigClass,
            ) ?: return null
            val isDialogFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareIsDialogField",
                resolvedSymbols.imageViewerShareIsDialogField,
            ) ?: return null
            val shareItemFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareItemField",
                resolvedSymbols.imageViewerShareItemField,
            ) ?: return null
            val addOutsideMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareAddOutsideMethod",
                resolvedSymbols.imageViewerShareAddOutsideMethod,
            ) ?: return null
            val getRequestDataMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareGetRequestDataMethod",
                resolvedSymbols.imageViewerShareGetRequestDataMethod,
            ) ?: return null
            val setRequestDataMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareSetRequestDataMethod",
                resolvedSymbols.imageViewerShareSetRequestDataMethod,
            ) ?: return null
            val getContextMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareGetContextMethod",
                resolvedSymbols.imageViewerShareGetContextMethod,
            ) ?: return null
            val shareItemClassName = requiredImageViewerShareSymbol(
                "imageViewerShareItemClass",
                resolvedSymbols.imageViewerShareItemClass,
            ) ?: return null
            val imageUriFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareItemImageUriField",
                resolvedSymbols.imageViewerShareItemImageUriField,
            ) ?: return null
            val itemViewClassName = requiredImageViewerShareSymbol(
                "imageViewerShareItemViewClass",
                resolvedSymbols.imageViewerShareItemViewClass,
            ) ?: return null
            val itemNameByResMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareItemNameByResMethod",
                resolvedSymbols.imageViewerShareItemNameByResMethod,
            ) ?: return null
            val itemNameByTextMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareItemNameByTextMethod",
                resolvedSymbols.imageViewerShareItemNameByTextMethod,
            ) ?: return null
            val shareIconResId = resolvedSymbols.imageViewerShareIconResId?.takeIf { it != 0 } ?: run {
                XposedCompat.log("[ImageViewerNativeShareHook] skipped: missing imageViewerShareIconResId")
                return null
            }

            val messageManagerClass = safeFindClass(StableTiebaHookPoints.MESSAGE_MANAGER_CLASS, cl) ?: return null
            val messageClass = safeFindClass(StableTiebaHookPoints.MESSAGE_CLASS, cl) ?: return null
            val customMessageClass = safeFindClass(StableTiebaHookPoints.CUSTOM_MESSAGE_CLASS, cl) ?: return null
            val shareDialogConfigClass = safeFindClass(configClassName, cl) ?: return null
            val shareItemClass = safeFindClass(shareItemClassName, cl) ?: return null
            val shareDialogItemViewClass = safeFindClass(itemViewClassName, cl) ?: return null
            val fileProviderClass = safeFindClass(IMAGE_VIEWER_NATIVE_SHARE_FILE_PROVIDER_CLASS, cl) ?: return null

            val sendMessageMethod = XposedCompat.findMethodOrNull(
                messageManagerClass,
                StableTiebaHookPoints.METHOD_SEND_MESSAGE,
                messageClass,
            ) ?: return null
            val customMessageGetDataMethod =
                ReflectionUtils.findMethodInHierarchy(customMessageClass, StableTiebaHookPoints.METHOD_GET_DATA)
                    ?: return null
            val isImageViewerDialogField =
                findImageViewerShareField(shareDialogConfigClass, isDialogFieldName) ?: return null
            val shareItemField = findImageViewerShareField(shareDialogConfigClass, shareItemFieldName) ?: return null
            val addOutsideTextViewMethod = XposedCompat.findMethodOrNull(
                shareDialogConfigClass,
                addOutsideMethodName,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                View.OnClickListener::class.java,
            ) ?: return null
            val getRequestDataMethod = ReflectionUtils.findMethodInHierarchy(
                shareDialogConfigClass,
                getRequestDataMethodName,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && Map::class.java.isAssignableFrom(method.returnType)
            } ?: return null
            val setRequestDataMethod = XposedCompat.findMethodOrNull(
                shareDialogConfigClass,
                setRequestDataMethodName,
                Map::class.java,
            ) ?: return null
            val getContextMethod = ReflectionUtils.findMethodInHierarchy(
                shareDialogConfigClass,
                getContextMethodName,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && Context::class.java.isAssignableFrom(method.returnType)
            } ?: return null
            val itemNameByResMethod = XposedCompat.findMethodOrNull(
                shareDialogItemViewClass,
                itemNameByResMethodName,
                Int::class.javaPrimitiveType!!,
            ) ?: return null
            val itemNameByTextMethod = XposedCompat.findMethodOrNull(
                shareDialogItemViewClass,
                itemNameByTextMethodName,
                String::class.java,
            ) ?: return null
            val fileProviderGetUriMethod = fileProviderClass.getDeclaredMethod(
                "getUriForFile",
                Context::class.java,
                String::class.java,
                File::class.java,
            )

            listOf(
                sendMessageMethod,
                customMessageGetDataMethod,
                addOutsideTextViewMethod,
                getRequestDataMethod,
                setRequestDataMethod,
                getContextMethod,
                itemNameByResMethod,
                itemNameByTextMethod,
                fileProviderGetUriMethod,
            ).forEach { it.isAccessible = true }

            ImageViewerNativeShareSymbols(
                customMessageClass = customMessageClass,
                shareDialogConfigClass = shareDialogConfigClass,
                sendMessageMethod = sendMessageMethod,
                customMessageGetDataMethod = customMessageGetDataMethod,
                isImageViewerDialogField = isImageViewerDialogField,
                shareItemField = shareItemField,
                addOutsideTextViewMethod = addOutsideTextViewMethod,
                getRequestDataMethod = getRequestDataMethod,
                setRequestDataMethod = setRequestDataMethod,
                getContextMethod = getContextMethod,
                shareItemTitleField = resolvedSymbols.imageViewerShareItemTitleField
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemContentField = resolvedSymbols.imageViewerShareItemContentField
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemLinkUrlField = resolvedSymbols.imageViewerShareItemLinkUrlField
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemImageUriField = findImageViewerShareField(shareItemClass, imageUriFieldName),
                shareItemImageUrlField = resolvedSymbols.imageViewerShareItemImageUrlField
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemLocalFileField = resolvedSymbols.imageViewerShareItemLocalFileField
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                itemNameByResMethod = itemNameByResMethod,
                itemNameByTextMethod = itemNameByTextMethod,
                fileProviderGetUriMethod = fileProviderGetUriMethod,
                shareIconResId = shareIconResId,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ImageViewerNativeShareHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun requiredImageViewerShareSymbol(name: String, value: String?): String? {
        return value?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[ImageViewerNativeShareHook] skipped: missing $name")
            null
        }
    }

    private fun findImageViewerShareField(clazz: Class<*>, fieldName: String): Field? {
        return runCatching {
            clazz.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull()
    }

    fun resolveEnterForumWebSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): EnterForumWebSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[EnterForumWebHook] skipped: scan symbols unavailable")
                return null
            }
            val sourceMethod = resolveEnterForumInitInfoUrlMethod(cl, resolvedSymbols)
            val webLoadMethod = resolveEnterForumWebLoadMethod(cl, resolvedSymbols)
            if (sourceMethod == null && webLoadMethod == null) {
                XposedCompat.log("[EnterForumWebHook] skipped: no resolved install targets")
                null
            } else {
                EnterForumWebSymbols(
                    sourceGetUrlMethod = sourceMethod,
                    webLoadMethod = webLoadMethod,
                )
            }
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolveEnterForumInitInfoUrlMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val dataClassName = symbols.enterForumInitInfoDataClass?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols.enterForumInitInfoGetUrlMethod?.takeIf { it.isNotBlank() } ?: return null
        val dataClass = safeFindClass(dataClassName, cl) ?: run {
            XposedCompat.log("[EnterForumWebHook] source skipped: class not found: $dataClassName")
            return null
        }
        val method = collectInstanceMethods(dataClass).singleOrNull { candidate ->
            candidate.name == methodName &&
                candidate.returnType == String::class.java &&
                candidate.parameterTypes.isEmpty()
        } ?: run {
            XposedCompat.log("[EnterForumWebHook] source skipped: method mismatch: $dataClassName.$methodName()")
            return null
        }
        method.isAccessible = true
        return method
    }

    private fun resolveEnterForumWebLoadMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val controllerClassName = symbols.enterForumWebControllerClass?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols.enterForumWebLoadMethod?.takeIf { it.isNotBlank() } ?: return null
        val controllerClass = safeFindClass(controllerClassName, cl) ?: run {
            XposedCompat.log("[EnterForumWebHook] skipped: class not found: $controllerClassName")
            return null
        }
        val method = collectInstanceMethods(controllerClass).singleOrNull { candidate ->
            candidate.name == methodName &&
                candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 1 &&
                candidate.parameterTypes[0] == String::class.java
        } ?: run {
            XposedCompat.log("[EnterForumWebHook] skipped: method mismatch: $controllerClassName.$methodName(String)")
            return null
        }
        method.isAccessible = true
        return method
    }

    fun resolveForumNativeTopShiftSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ForumNativeTopShiftSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.forumBottomSheetViewClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: missing forumBottomSheetViewClass")
                return null
            }
            val methodName = resolvedSymbols.forumBottomSheetInitScrollMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: missing forumBottomSheetInitScrollMethod")
                return null
            }
            val targetClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: class not found: $className")
                return null
            }
            val method = collectInstanceMethods(targetClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 3 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
            } ?: run {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: method mismatch: $className.$methodName")
                return null
            }
            method.isAccessible = true
            ForumNativeTopShiftSymbols(initScrollMethod = method)
        } catch (t: Throwable) {
            XposedCompat.log("[ForumNativeTopShiftBlockHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveHomeTopBarRightSlotSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): HomeTopBarRightSlotSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[HomeTopBarRightSlotHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.homeRightSlotClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[HomeTopBarRightSlotHook] skipped: missing homeRightSlotClass")
                return null
            }
            val methodNames = resolvedSymbols.homeRightSlotStateMethods.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (methodNames.isEmpty()) {
                XposedCompat.log("[HomeTopBarRightSlotHook] skipped: missing homeRightSlotStateMethods")
                return null
            }

            val slotClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[HomeTopBarRightSlotHook] skipped: class not found: $className")
                return null
            }
            if (!ViewGroup::class.java.isAssignableFrom(slotClass)) {
                XposedCompat.log("[HomeTopBarRightSlotHook] skipped: class is not a ViewGroup: $className")
                return null
            }
            val methods = collectInstanceMethods(slotClass)
            val stateMethods = ArrayList<Method>(methodNames.size)
            for (methodName in methodNames) {
                val method = methods.singleOrNull { candidate ->
                    candidate.name == methodName &&
                        candidate.returnType == Void.TYPE &&
                        candidate.parameterTypes.isEmpty()
                } ?: run {
                    XposedCompat.log("[HomeTopBarRightSlotHook] skipped: method mismatch: $className.$methodName()")
                    return null
                }
                stateMethods += method
            }

            val searchIconGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getSearchIconView",
                returnTypeMatch = { ImageView::class.java.isAssignableFrom(it) },
            ) ?: return null
            val gameIconGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getGameIconView",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null
            val topBarTipGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getTopBarTip",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null
            val redDotGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getRedDotView",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null

            listOf(
                searchIconGetter,
                gameIconGetter,
                topBarTipGetter,
                redDotGetter,
            ).plus(stateMethods).forEach { it.isAccessible = true }

            HomeTopBarRightSlotSymbols(
                slotClass = slotClass,
                stateMethods = stateMethods,
                searchIconViewMethod = searchIconGetter,
                gameIconViewMethod = gameIconGetter,
                topBarTipMethod = topBarTipGetter,
                redDotViewMethod = redDotGetter,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTopBarRightSlotHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveHomeNativeGlassHostDarkModeSwitchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): HomeNativeGlassHostDarkModeSwitchTargets? {
        class OptionalSymbolMissing(message: String) : IllegalStateException(message)

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[HomeNativeGlassHook] host dark mode switch skipped: scan symbols unavailable")
                return null
            }
            fun required(name: String, value: String?): String {
                return value?.takeIf { it.isNotBlank() } ?: throw OptionalSymbolMissing("missing $name")
            }

            val moreActivityClassName = required(
                "homeNativeGlassHostDarkModeMoreActivityClass",
                resolvedSymbols.homeNativeGlassHostDarkModeMoreActivityClass,
            )
            val controllerFieldName = required(
                "homeNativeGlassHostDarkModeControllerField",
                resolvedSymbols.homeNativeGlassHostDarkModeControllerField,
            )
            val switchGetterMethodName = required(
                "homeNativeGlassHostDarkModeSwitchGetterMethod",
                resolvedSymbols.homeNativeGlassHostDarkModeSwitchGetterMethod,
            )
            val switchStateFieldName = required(
                "homeNativeGlassHostDarkModeSwitchStateField",
                resolvedSymbols.homeNativeGlassHostDarkModeSwitchStateField,
            )
            val switchSetOnMethodName = required(
                "homeNativeGlassHostDarkModeSwitchSetOnMethod",
                resolvedSymbols.homeNativeGlassHostDarkModeSwitchSetOnMethod,
            )
            val switchSetOffMethodName = required(
                "homeNativeGlassHostDarkModeSwitchSetOffMethod",
                resolvedSymbols.homeNativeGlassHostDarkModeSwitchSetOffMethod,
            )
            val switchCallbackMethodName = required(
                "homeNativeGlassHostDarkModeSwitchCallbackMethod",
                resolvedSymbols.homeNativeGlassHostDarkModeSwitchCallbackMethod,
            )
            val moreActivityClass = safeFindClass(moreActivityClassName, cl)
                ?: error("class not found: $moreActivityClassName")
            val switchClass = safeFindClass(HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS, cl)
                ?: error("class not found: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS")
            val controllerField = collectInstanceFields(moreActivityClass).singleOrNull { field ->
                field.name == controllerFieldName &&
                    !Modifier.isStatic(field.modifiers)
            } ?: error("controller field mismatch: $moreActivityClassName.$controllerFieldName")
            val switchGetterMethod = collectInstanceMethods(controllerField.type).singleOrNull { method ->
                method.name == switchGetterMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    switchClass.isAssignableFrom(method.returnType)
            } ?: error("switch getter mismatch: ${controllerField.type.name}.$switchGetterMethodName()")
            val switchStateField = collectInstanceFields(switchClass).singleOrNull { field ->
                field.name == switchStateFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type.isEnum
            } ?: error("switch state field mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchStateFieldName")
            val switchSetOnMethod = switchClass.declaredMethods.singleOrNull { method ->
                method.name == switchSetOnMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: error("switch set on mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchSetOnMethodName()")
            val switchSetOffMethod = switchClass.declaredMethods.singleOrNull { method ->
                method.name == switchSetOffMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: error("switch set off mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchSetOffMethodName()")
            val switchCallbackMethod = collectInstanceMethods(moreActivityClass).singleOrNull { method ->
                val params = method.parameterTypes
                method.name == switchCallbackMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    params.size == 2 &&
                    params[0].isAssignableFrom(switchGetterMethod.returnType) &&
                    params[1] == switchStateField.type
            } ?: error("switch callback mismatch: $moreActivityClassName.$switchCallbackMethodName(View, SwitchState)")

            controllerField.isAccessible = true
            switchGetterMethod.isAccessible = true
            switchStateField.isAccessible = true
            switchSetOnMethod.isAccessible = true
            switchSetOffMethod.isAccessible = true
            switchCallbackMethod.isAccessible = true
            HomeNativeGlassHostDarkModeSwitchTargets(
                moreActivityClass = moreActivityClass,
                controllerField = controllerField,
                switchGetterMethod = switchGetterMethod,
                switchStateField = switchStateField,
                switchSetOnMethod = switchSetOnMethod,
                switchSetOffMethod = switchSetOffMethod,
                switchCallbackMethod = switchCallbackMethod,
            )
        } catch (t: Throwable) {
            if (t is OptionalSymbolMissing) {
                XposedCompat.log("[HomeNativeGlassHook] host dark mode switch skipped: ${t.message}")
            } else {
                XposedCompat.log("[HomeNativeGlassHook] host dark mode switch symbol resolve FAILED: ${t.message}")
            }
            null
        }
    }

    private fun resolveHomeRightSlotGetter(
        methods: List<Method>,
        name: String,
        returnTypeMatch: (Class<*>) -> Boolean,
    ): Method? {
        return methods.singleOrNull { method ->
            method.name == name &&
                method.parameterTypes.isEmpty() &&
                returnTypeMatch(method.returnType)
        } ?: run {
            XposedCompat.log("[HomeTopBarRightSlotHook] skipped: getter mismatch: $name()")
            null
        }
    }

    fun resolveSearchBoxTextAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): SearchBoxTextAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: scan symbols unavailable")
                return null
            }
            val searchBoxClassName = resolvedSymbols.searchBoxViewClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: missing searchBoxViewClass")
                return null
            }
            val setHintMethodName = resolvedSymbols.searchBoxSetHintMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: missing searchBoxSetHintMethod")
                return null
            }
            val ownerClassName = resolvedSymbols.homeSearchBoxOwnerClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxOwnerClass")
                return null
            }
            val ownerInitMethodName = resolvedSymbols.homeSearchBoxInitMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxInitMethod")
                return null
            }
            val ownerGetterMethodName = resolvedSymbols.homeSearchBoxGetterMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxGetterMethod")
                return null
            }

            val searchBoxClass = safeFindClass(searchBoxClassName, cl) ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: class not found: $searchBoxClassName")
                return null
            }
            val ownerClass = safeFindClass(ownerClassName, cl) ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: class not found: $ownerClassName")
                return null
            }
            val recyclerClass = safeFindClass(StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] skipped: class not found: " +
                        StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS,
                )
                return null
            }
            val containerClass = safeFindClass(SEARCH_BOX_HEADER_CONTAINER_CLASS, cl) ?: run {
                XposedCompat.log("[SearchBoxTextAdHook] skipped: class not found: $SEARCH_BOX_HEADER_CONTAINER_CLASS")
                return null
            }

            val setHintMethod = searchBoxClass.declaredMethods.singleOrNull { method ->
                method.name == setHintMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType
            } ?: run {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$searchBoxClassName.$setHintMethodName(List,boolean)",
                )
                return null
            }

            val ownerMethods = collectInstanceMethods(ownerClass)
            val ownerInitMethod = ownerMethods.singleOrNull { method ->
                method.name == ownerInitMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    recyclerClass.isAssignableFrom(method.parameterTypes[0]) &&
                    containerClass.isAssignableFrom(method.parameterTypes[1])
            } ?: run {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$ownerClassName.$ownerInitMethodName(${recyclerClass.name},${containerClass.name})",
                )
                return null
            }
            val ownerGetterMethod = ownerMethods.singleOrNull { method ->
                method.name == ownerGetterMethodName &&
                    method.parameterTypes.isEmpty() &&
                    searchBoxClass.isAssignableFrom(method.returnType)
            } ?: run {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$ownerClassName.$ownerGetterMethodName()",
                )
                return null
            }

            setHintMethod.isAccessible = true
            ownerInitMethod.isAccessible = true
            ownerGetterMethod.isAccessible = true
            SearchBoxTextAdSymbols(
                setHintMethod = setHintMethod,
                ownerInitMethod = ownerInitMethod,
                ownerGetterMethod = ownerGetterMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[SearchBoxTextAdHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveAutoRefreshSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AutoRefreshSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[AutoRefreshHook] skipped: scan symbols unavailable")
                return null
            }
            val methodName = resolvedSymbols.autoRefreshTriggerMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[AutoRefreshHook] skipped: missing autoRefreshTriggerMethod")
                return null
            }
            val pageClass = safeFindClass(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[AutoRefreshHook] skipped: class not found: " +
                        StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS,
                )
                return null
            }
            val method = collectInstanceMethods(pageClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.isEmpty()
            } ?: run {
                XposedCompat.log(
                    "[AutoRefreshHook] skipped: method mismatch: " +
                        "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.$methodName()",
                )
                return null
            }
            method.isAccessible = true
            AutoRefreshSymbols(triggerMethod = method)
        } catch (t: Throwable) {
            XposedCompat.log("[AutoRefreshHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveAutoLoadMoreSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AutoLoadMoreSymbols? {
        return try {
            val ubsMethod = XposedCompat.findMethodOrNull(
                StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS,
                cl,
                StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && isBooleanType(method.returnType)
            }?.apply { isAccessible = true }

            val configClassName = symbols
                ?.autoLoadMoreConfigClass
                ?.takeIf { it.isNotBlank() }
            val configMethodName = symbols
                ?.autoLoadMoreConfigMethod
                ?.takeIf { it.isNotBlank() }
            val configMethod = if (configClassName == null || configMethodName == null) {
                null
            } else {
                XposedCompat.findMethodOrNull(configClassName, cl, configMethodName)
                    ?.takeIf { method ->
                        method.parameterTypes.isEmpty() && isIntType(method.returnType)
                    }
                    ?.apply { isAccessible = true }
            }

            if (ubsMethod == null && configMethod == null) {
                XposedCompat.log(
                    "[AutoLoadMoreHook] skipped: missing install targets " +
                        "ubs=${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}." +
                        "${StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT}, " +
                        "config=$configClassName.$configMethodName",
                )
                return null
            }
            AutoLoadMoreSymbols(
                ubsMethod = ubsMethod,
                configMethod = configMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[AutoLoadMoreHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveHomeTabSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): HomeTabResolvedSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        fun namedFieldInHierarchy(clazz: Class<*>, fieldName: String): Field? {
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

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[HomeTabHook] skipped: scan symbols unavailable")
                return null
            }
            val hostClassName = requireSymbol("homeTabClass", resolvedSymbols.homeTabClass)
            val rebuildMethodName = requireSymbol("homeTabRebuildMethod", resolvedSymbols.homeTabRebuildMethod)
            val listFieldName = requireSymbol("homeTabListField", resolvedSymbols.homeTabListField)
            val hostClass = safeFindClass(hostClassName, cl) ?: run {
                XposedCompat.log("[HomeTabHook] skipped: class not found: $hostClassName")
                return null
            }
            val rebuildMethod = XposedCompat.findMethodOrNull(hostClass, rebuildMethodName) ?: run {
                XposedCompat.log("[HomeTabHook] skipped: method not found: $hostClassName.$rebuildMethodName()")
                return null
            }
            val listField = namedFieldInHierarchy(hostClass, listFieldName)
                ?.takeIf { List::class.java.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }
                ?: run {
                    XposedCompat.log("[HomeTabHook] skipped: field mismatch: $hostClassName.$listFieldName")
                    return null
                }
            rebuildMethod.isAccessible = true
            HomeTabResolvedSymbols(
                hostClass = hostClass,
                rebuildMethod = rebuildMethod,
                listField = listField,
                itemTypeField = requireSymbol("homeTabItemTypeField", resolvedSymbols.homeTabItemTypeField),
                itemCodeField = requireSymbol("homeTabItemCodeField", resolvedSymbols.homeTabItemCodeField),
                itemNameField = requireSymbol("homeTabItemNameField", resolvedSymbols.homeTabItemNameField),
                itemUrlField = requireSymbol("homeTabItemUrlField", resolvedSymbols.homeTabItemUrlField),
                itemMainSetterMethod = resolvedSymbols.homeTabItemMainSetterMethod?.takeIf { it.isNotBlank() },
                itemMainIntField = resolvedSymbols.homeTabItemMainIntField?.takeIf { it.isNotBlank() },
                itemMainBooleanField = resolvedSymbols.homeTabItemMainBooleanField?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTabHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePbFallingAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbFallingAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbFallingAdHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.pbFallingViewClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbFallingAdHook] skipped: missing pbFallingViewClass")
                return null
            }
            val targetClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[PbFallingAdHook] skipped: class not found: $className")
                return null
            }
            if (!ViewGroup::class.java.isAssignableFrom(targetClass)) {
                XposedCompat.log("[PbFallingAdHook] skipped: class is not a ViewGroup: $className")
                return null
            }

            val initMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols.pbFallingInitMethod,
                label = "init",
                signature = ::isPbFallingInitSignature,
            )
            val showMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols.pbFallingShowMethod,
                label = "show",
                signature = ::isPbFallingShowSignature,
            )
            val clearMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols.pbFallingClearMethod,
                label = "clear",
                signature = ::isPbFallingClearSignature,
            )

            if (listOfNotNull(initMethod, showMethod, clearMethod).isEmpty()) {
                XposedCompat.log("[PbFallingAdHook] skipped: no usable methods resolved on $className")
                return null
            }

            PbFallingAdSymbols(
                targetClass = targetClass,
                initMethod = initMethod,
                showMethod = showMethod,
                clearMethod = clearMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbFallingAdHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbFallingMethod(
        targetClass: Class<*>,
        methodName: String?,
        label: String,
        signature: (Method) -> Boolean,
    ): Method? {
        val normalizedName = methodName?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[PbFallingAdHook] $label skipped: missing method name")
            return null
        }
        val method = collectInstanceMethods(targetClass).singleOrNull { candidate ->
            candidate.name == normalizedName && signature(candidate)
        } ?: run {
            XposedCompat.log("[PbFallingAdHook] $label skipped: method mismatch: ${targetClass.name}.$normalizedName")
            return null
        }
        method.isAccessible = true
        return method
    }

    private fun isPbFallingInitSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            Context::class.java.isAssignableFrom(method.parameterTypes[0])
    }

    private fun isPbFallingShowSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[2] == Int::class.javaPrimitiveType &&
            method.parameterTypes[3] == Boolean::class.javaPrimitiveType
    }

    private fun isPbFallingClearSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE && method.parameterTypes.isEmpty()
    }

    fun resolvePbBottomEnterBarStableSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbBottomEnterBarStableSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbBottomEnterBarHook] stable skipped: scan symbols unavailable")
                return null
            }
            val bottomClass = resolvedSymbols.pbBottomEnterBarViewClass
                ?.takeIf { it.isNotBlank() }
                ?.let { className ->
                    safeFindClass(className, cl) ?: run {
                        XposedCompat.log("[PbBottomEnterBarHook] stable skipped: bottom class not found: $className")
                        null
                    }
                }
            val bottomRefreshMethods = bottomClass?.let { clazz ->
                resolvedSymbols.pbBottomEnterBarRefreshMethodSpecs.orEmpty()
                    .mapNotNull { spec -> resolvePbBottomEnterBarRefreshMethod(clazz, spec, cl) }
                    .distinct()
            }.orEmpty()
            val animationTipClass = resolvedSymbols.pbEnterFrsAnimationTipViewClass
                ?.takeIf { it.isNotBlank() }
                ?.let { className ->
                    safeFindClass(className, cl) ?: run {
                        XposedCompat.log("[PbBottomEnterBarHook] stable skipped: animation tip class not found: $className")
                        null
                    }
                }
            if (bottomClass == null && animationTipClass == null) {
                XposedCompat.log("[PbBottomEnterBarHook] stable skipped: no cached stable targets resolved")
                return null
            }
            bottomRefreshMethods.forEach { it.isAccessible = true }
            PbBottomEnterBarStableSymbols(
                bottomEnterBarViewClass = bottomClass,
                bottomEnterBarRefreshMethods = bottomRefreshMethods,
                enterFrsAnimationTipViewClass = animationTipClass,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] stable symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbBottomEnterBarRefreshMethod(
        targetClass: Class<*>,
        spec: String,
        cl: ClassLoader,
    ): Method? {
        val parts = spec.split('|', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) {
            XposedCompat.logD("[PbBottomEnterBarHook] invalid bottom refresh method spec: $spec")
            return null
        }
        val paramTypes = if (parts[1].isBlank()) {
            emptyArray<Class<*>>()
        } else {
            parts[1].split(',').map { typeName ->
                PbEarlyAdInsertSymbolScanner.resolveType(typeName, cl) ?: run {
                    XposedCompat.logD("[PbBottomEnterBarHook] bottom refresh param type not found: $typeName")
                    return null
                }
            }.toTypedArray()
        }
        val method = try {
            targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        } catch (_: NoSuchMethodException) {
            XposedCompat.logD("[PbBottomEnterBarHook] bottom refresh method not resolved: $spec")
            return null
        }
        return method.takeIf(::isPbBottomEnterBarRefreshSignature) ?: run {
            XposedCompat.logD("[PbBottomEnterBarHook] bottom refresh method mismatch: $spec")
            null
        }
    }

    private fun isPbBottomEnterBarRefreshSignature(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return (method.name == "setData" && params.size == 1) ||
            (method.name == "onChangeSkinType" && params.isEmpty())
    }

    fun resolvePbBottomEnterBarHotTopicGuideSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbBottomEnterBarHotTopicGuideSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbBottomEnterBarHook] hot topic guide skipped: scan symbols unavailable")
                return null
            }
            val totalViewMethodName = resolvedSymbols.pbHotTopicGuideTotalViewMethod
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    XposedCompat.log("[PbBottomEnterBarHook] hot topic guide skipped: missing total view method")
                    return null
                }
            val guideClass = safeFindClass(StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[PbBottomEnterBarHook] hot topic guide skipped: class not found: " +
                        StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS,
                )
                return null
            }
            val totalViewMethod = guideClass.getDeclaredMethod(totalViewMethodName).takeIf { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == View::class.java
            } ?: run {
                XposedCompat.log(
                    "[PbBottomEnterBarHook] hot topic guide skipped: total view method mismatch: " +
                        "${guideClass.name}.$totalViewMethodName",
                )
                return null
            }
            val refreshMethods = resolvedSymbols.pbHotTopicGuideRefreshMethodSpecs.orEmpty()
                .mapNotNull { spec -> resolvePbHotTopicGuideRefreshMethod(guideClass, spec) }
                .distinct()
            totalViewMethod.isAccessible = true
            refreshMethods.forEach { it.isAccessible = true }
            PbBottomEnterBarHotTopicGuideSymbols(
                guideClass = guideClass,
                totalViewMethod = totalViewMethod,
                refreshMethods = refreshMethods,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] hot topic guide symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbHotTopicGuideRefreshMethod(targetClass: Class<*>, spec: String): Method? {
        val parts = spec.split('|', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) {
            XposedCompat.logD("[PbBottomEnterBarHook] invalid hot topic guide method spec: $spec")
            return null
        }
        val paramTypes: Array<Class<*>> = when (parts[1].trim()) {
            "" -> emptyArray()
            "int" -> arrayOf(Integer.TYPE)
            else -> {
                XposedCompat.logD("[PbBottomEnterBarHook] unsupported hot topic guide method spec: $spec")
                return null
            }
        }
        val method = try {
            targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        } catch (_: NoSuchMethodException) {
            XposedCompat.logD("[PbBottomEnterBarHook] hot topic guide method not resolved: $spec")
            return null
        }
        return method.takeIf(::isPbHotTopicGuideRefreshSignature) ?: run {
            XposedCompat.logD("[PbBottomEnterBarHook] hot topic guide method mismatch: $spec")
            null
        }
    }

    private fun isPbHotTopicGuideRefreshSignature(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.isEmpty() ||
            (params.size == 1 && params[0] == Int::class.javaPrimitiveType)
    }

    fun resolvePbEarlyAdBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbEarlyAdBlockSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.pbEarlyAdInsertClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: missing pbEarlyAdInsertClass")
                return null
            }
            val specs = resolvedSymbols.pbEarlyAdInsertMethodSpecs.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (specs.isEmpty()) {
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: missing pbEarlyAdInsertMethodSpecs")
                return null
            }

            val targetClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: class not found: $className")
                return null
            }

            val methods = specs.mapNotNull { spec ->
                resolvePbEarlyAdBlockMethod(targetClass, spec, cl)
            }
            if (methods.isEmpty()) {
                XposedCompat.log("[PbEarlyAdBlockHook] skipped: no methods resolved on $className")
                return null
            }

            PbEarlyAdBlockSymbols(
                targetClass = targetClass,
                methods = methods,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbEarlyAdBlockHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbEarlyAdBlockMethod(
        targetClass: Class<*>,
        spec: String,
        cl: ClassLoader,
    ): PbEarlyAdBlockMethodSymbols? {
        val parts = spec.split('|', limit = 3)
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) {
            XposedCompat.logD("[PbEarlyAdBlockHook] invalid method spec: $spec")
            return null
        }
        val returnType = PbEarlyAdInsertSymbolScanner.resolveType(parts[1], cl) ?: run {
            XposedCompat.logD("[PbEarlyAdBlockHook] return type not found: ${parts[1]}")
            return null
        }
        val paramTypes = if (parts[2].isBlank()) {
            emptyArray<Class<*>>()
        } else {
            parts[2].split(',').map { typeName ->
                PbEarlyAdInsertSymbolScanner.resolveType(typeName, cl) ?: run {
                    XposedCompat.logD("[PbEarlyAdBlockHook] param type not found: $typeName")
                    return null
                }
            }.toTypedArray()
        }

        val method = try {
            targetClass.getDeclaredMethod(parts[0], *paramTypes).takeIf { candidate ->
                Modifier.isStatic(candidate.modifiers) &&
                    (candidate.returnType == returnType || returnType.isAssignableFrom(candidate.returnType))
            }
        } catch (_: NoSuchMethodException) {
            null
        } ?: run {
            XposedCompat.logD("[PbEarlyAdBlockHook] method not resolved: $spec")
            return null
        }
        method.isAccessible = true
        return PbEarlyAdBlockMethodSymbols(
            method = method,
            returnsSparseArray = android.util.SparseArray::class.java.isAssignableFrom(method.returnType),
        )
    }

    fun resolvePbAdRequestBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbAdRequestBlockSymbols? {
        return try {
            val pbPage = resolvePbPageRequestMessageTargets(cl)
            val pageBrowser = resolvePageBrowserRequestMessageTarget(cl)
            val commonAdBid = resolveCommonAdBidTargets(cl, symbols)
            val pageBrowserAdBid = resolvePageBrowserAdBidTarget(cl, symbols)
            if (
                pbPage == null &&
                pageBrowser == null &&
                commonAdBid == null &&
                pageBrowserAdBid == null
            ) {
                XposedCompat.log("[PbAdRequestBlockHook] skipped: no resolved install targets")
                return null
            }
            PbAdRequestBlockSymbols(
                pbPageEncodeMethod = pbPage?.first,
                pbPageFieldPatches = pbPage?.second.orEmpty(),
                pageBrowserAddAdMethod = pageBrowser,
                commonAdBidTargetClass = commonAdBid?.targetClass,
                commonAdBidStartMethods = commonAdBid?.startMethods.orEmpty(),
                commonAdBidNotifyMethod = commonAdBid?.notifyMethod,
                pageBrowserAdBidTargetClass = pageBrowserAdBid?.first,
                pageBrowserAdBidRequestDataMethod = pageBrowserAdBid?.second,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbAdRequestBlockHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private data class CommonAdBidTargets(
        val targetClass: Class<*>,
        val startMethods: List<Method>,
        val notifyMethod: Method,
    )

    private fun resolvePbPageRequestMessageTargets(cl: ClassLoader): Pair<Method, List<PbAdRequestFieldPatchSymbols>>? {
        val requestClass = safeFindClass(PB_PAGE_REQUEST_MESSAGE_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS")
            return null
        }
        val encodeMethod = XposedCompat.findMethodOrNull(
            requestClass,
            "encode",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] method NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS.encode(boolean)")
            return null
        }
        val patches = resolvePbPageRequestFieldPatches(requestClass)
        if (patches.isEmpty()) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped PbPageRequestMessage: ad fields not resolved")
            return null
        }
        encodeMethod.isAccessible = true
        return encodeMethod to patches
    }

    private fun resolvePageBrowserRequestMessageTarget(cl: ClassLoader): Method? {
        val requestClass = safeFindClass(PAGE_BROWSER_REQUEST_MESSAGE_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PAGE_BROWSER_REQUEST_MESSAGE_CLASS")
            return null
        }
        val builderClass = safeFindClass(PB_LIST_DATA_REQ_BUILDER_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_LIST_DATA_REQ_BUILDER_CLASS")
            return null
        }
        return XposedCompat.findMethodOrNull(
            requestClass,
            "addAdRequestMessage",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            builderClass,
        )?.apply { isAccessible = true } ?: run {
            XposedCompat.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$PAGE_BROWSER_REQUEST_MESSAGE_CLASS.addAdRequestMessage(int,int,DataReq.Builder)",
            )
            null
        }
    }

    private fun resolveCommonAdBidTargets(cl: ClassLoader, symbols: HookSymbols?): CommonAdBidTargets? {
        val resolvedSymbols = symbols ?: return null
        val modelClassName = resolvedSymbols.pbAdBidCommonRequestModelClass?.takeIf { it.isNotBlank() } ?: return null
        val startMethodNames = resolvedSymbols.pbAdBidCommonRequestStartMethods.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val notifyMethodName = resolvedSymbols.pbAdBidCommonRequestNotifyMethod?.takeIf { it.isNotBlank() }
        if (startMethodNames.isEmpty() || notifyMethodName == null) return null

        val commonBaseClass = safeFindClass(PB_COMMON_REQUEST_MODEL_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_COMMON_REQUEST_MODEL_CLASS")
            return null
        }
        val targetModelClass = safeFindClass(modelClassName, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return null
        }
        if (!commonBaseClass.isAssignableFrom(targetModelClass)) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped common AdBid: target is not CommonRequestModel")
            return null
        }

        val notifyMethod = findPbAdRequestInstanceMethod(
            commonBaseClass,
            notifyMethodName,
            Void.TYPE,
            Int::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] method NOT FOUND: $PB_COMMON_REQUEST_MODEL_CLASS.$notifyMethodName(int)")
            return null
        }

        val startMethods = startMethodNames.mapNotNull { methodName ->
            findPbAdRequestInstanceMethod(commonBaseClass, methodName, Void.TYPE) ?: run {
                XposedCompat.logD("[PbAdRequestBlockHook] common AdBid start method not resolved: $methodName")
                null
            }
        }
        if (startMethods.isEmpty()) return null
        return CommonAdBidTargets(
            targetClass = targetModelClass,
            startMethods = startMethods,
            notifyMethod = notifyMethod,
        )
    }

    private fun resolvePageBrowserAdBidTarget(cl: ClassLoader, symbols: HookSymbols?): Pair<Class<*>, Method>? {
        val resolvedSymbols = symbols ?: return null
        val modelClassName = resolvedSymbols.pbAdBidPageBrowserRequestModelClass?.takeIf { it.isNotBlank() }
            ?: return null
        val requestDataMethodName = resolvedSymbols.pbAdBidPageBrowserRequestDataMethod?.takeIf { it.isNotBlank() }
            ?: return null
        val targetModelClass = safeFindClass(modelClassName, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return null
        }
        val continuationClass = safeFindClass(KOTLIN_CONTINUATION_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $KOTLIN_CONTINUATION_CLASS")
            return null
        }
        safeFindClass(PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, cl)
            ?.takeIf { !it.isAssignableFrom(targetModelClass) }
            ?.let { XposedCompat.logD("[PbAdRequestBlockHook] pagebrowser AdBid target is outside legacy BaseRequestModel") }

        val requestDataMethod = findPbAdRequestInstanceMethodInHierarchy(
            targetModelClass,
            requestDataMethodName,
            Any::class.java,
            continuationClass,
        ) ?: run {
            XposedCompat.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$modelClassName.$requestDataMethodName(Continuation)",
            )
            return null
        }
        return targetModelClass to requestDataMethod
    }

    private fun resolvePbPageRequestFieldPatches(requestClass: Class<*>): List<PbAdRequestFieldPatchSymbols> {
        val patches = ArrayList<PbAdRequestFieldPatchSymbols>(5)
        fun add(name: String, value: Any?) {
            val field = try {
                XposedCompat.findField(requestClass, name)
            } catch (_: Throwable) {
                null
            } ?: return
            field.isAccessible = true
            patches.add(PbAdRequestFieldPatchSymbols(field, value))
        }
        add("adxBearBannerStr", "")
        add("adxBearCommentStr", "")
        add("adExternalBannerStr", "")
        add("adExternalCommentStr", "")
        add("isReqAd", 0)
        return patches
    }

    private fun findPbAdRequestInstanceMethod(
        clazz: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg paramTypes: Class<*>,
    ): Method? {
        return try {
            clazz.getDeclaredMethod(name, *paramTypes).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == returnType
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun findPbAdRequestInstanceMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            findPbAdRequestInstanceMethod(current, name, returnType, *paramTypes)?.let { return it }
            current = current.superclass
        }
        return null
    }

    fun resolvePostAdDataFilterSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PostAdDataFilterSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PostAdHook] skipped: scan symbols unavailable")
                return null
            }
            val setDataMethodName = resolvedSymbols.typeAdapterSetDataMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PostAdHook] skipped: missing typeAdapterSetDataMethod")
                return null
            }
            val itemClassName = resolvedSymbols.typeAdapterDataItemClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PostAdHook] skipped: missing typeAdapterDataItemClass")
                return null
            }
            val getTypeMethodName = resolvedSymbols.typeAdapterDataGetTypeMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PostAdHook] skipped: missing typeAdapterDataGetTypeMethod")
                return null
            }

            val adapterClass = safeFindClass(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl) ?: run {
                XposedCompat.log("[PostAdHook] class NOT FOUND: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}")
                return null
            }
            val setDataMethod = try {
                adapterClass.getDeclaredMethod(setDataMethodName, List::class.java).takeIf { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
                }
            } catch (_: NoSuchMethodException) {
                null
            } ?: run {
                XposedCompat.log("[PostAdHook] method NOT FOUND: TypeAdapter.$setDataMethodName(List)")
                return null
            }

            val itemClass = safeFindClass(itemClassName, cl) ?: run {
                XposedCompat.log("[PostAdHook] class NOT FOUND: $itemClassName")
                return null
            }
            val getTypeMethod = try {
                itemClass.getMethod(getTypeMethodName).takeIf { method ->
                    method.parameterTypes.isEmpty()
                }
            } catch (_: NoSuchMethodException) {
                null
            } ?: run {
                XposedCompat.log("[PostAdHook] method NOT FOUND: $itemClassName.$getTypeMethodName()")
                return null
            }

            val blockedTypes = resolvePostAdBlockedTypes(cl)
            val blockedItemClasses = resolvePostAdBlockedItemClasses(cl)
            if (blockedTypes.isEmpty() && blockedItemClasses.isEmpty()) {
                XposedCompat.log("[PostAdHook] skipped: no blocked post-ad types resolved")
                return null
            }

            setDataMethod.isAccessible = true
            getTypeMethod.isAccessible = true
            PostAdDataFilterSymbols(
                setDataMethod = setDataMethod,
                itemClass = itemClass,
                getTypeMethod = getTypeMethod,
                blockedTypes = blockedTypes,
                blockedItemClasses = blockedItemClasses,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PostAdHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveForumPageAdBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ForumPageAdBlockSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ForumPageAdBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val responseTargets = resolveForumPageResponseTargets(cl, resolvedSymbols)
            val bottomTargets = resolveForumPageBottomTargets(cl, resolvedSymbols)
            val bottomGameBarMapper = resolveForumPageMapperMethod(
                cl = cl,
                mapperClassName = resolvedSymbols.forumPageMapperClass,
                methodName = resolvedSymbols.forumBottomGameBarMapperMethod,
                returnClassName = null,
                label = "bottom game bar mapper",
            )
            val headerTargets = resolveForumPageHeaderTargets(cl, resolvedSymbols)
            val dialogTargets = resolveForumPageDialogTargets(cl, resolvedSymbols)
            val floatingTargets = resolveForumPageFloatingTargets(cl, resolvedSymbols)
            val businessPromotJumpMethod = resolveForumPageBusinessPromotJumpMethod(cl, resolvedSymbols)

            if (
                responseTargets == null &&
                bottomTargets == null &&
                bottomGameBarMapper == null &&
                headerTargets == null &&
                dialogTargets.first == null &&
                dialogTargets.second == null &&
                floatingTargets.first == null &&
                businessPromotJumpMethod == null
            ) {
                XposedCompat.log("[ForumPageAdBlockHook] skipped: no resolved install targets")
                return null
            }

            ForumPageAdBlockSymbols(
                responseParserMethod = responseTargets?.first,
                responseAdFields = responseTargets?.second.orEmpty(),
                bottomDataMapperMethod = bottomTargets?.first,
                bottomDataSetterMethods = bottomTargets?.second.orEmpty(),
                bottomGameBarMapperMethod = bottomGameBarMapper,
                headerDataMapperMethod = headerTargets?.first,
                rainSetterMethod = headerTargets?.second,
                businessPromotShowMethod = dialogTargets.first,
                animationShowMethod = dialogTargets.second,
                gameFloatingBarShowMethod = floatingTargets.first,
                gameFloatingBarField = floatingTargets.second,
                businessPromotJumpMethod = businessPromotJumpMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ForumPageAdBlockHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolveForumPageResponseTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method, List<Field>>? {
        val className = symbols.forumResponseDataClass?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols.forumResponseParserMethod?.takeIf { it.isNotBlank() } ?: return null
        val fieldNames = symbols.forumResponseAdFields.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (fieldNames.size < 4) return null

        val responseClass = safeFindClass(className, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] response class NOT FOUND: $className")
            return null
        }
        val parser = collectInstanceMethods(responseClass).singleOrNull { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        } ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] parser method NOT FOUND: $className.$methodName")
            return null
        }
        val fields = fieldNames.mapNotNull { fieldName ->
            findForumPageField(responseClass, fieldName)
        }
        if (fields.size < 4) {
            XposedCompat.log("[ForumPageAdBlockHook] response ad fields not resolved on $className")
            return null
        }
        parser.isAccessible = true
        fields.forEach { it.isAccessible = true }
        return parser to fields
    }

    private fun resolveForumPageBottomTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method, List<Method>>? {
        val mapperMethod = resolveForumPageMapperMethod(
            cl = cl,
            mapperClassName = symbols.forumPageMapperClass,
            methodName = symbols.forumBottomDataMapperMethod,
            returnClassName = symbols.forumBottomDataClass,
            label = "bottom data mapper",
        ) ?: return null
        val dataClassName = symbols.forumBottomDataClass?.takeIf { it.isNotBlank() } ?: return null
        val dataClass = safeFindClass(dataClassName, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] bottom data class NOT FOUND: $dataClassName")
            return null
        }
        val setters = listOf(
            symbols.forumBusinessPromotSetterMethod,
            symbols.forumPrivatePopSetterMethod,
            symbols.forumSpriteBubbleSetterMethod,
            symbols.forumMaskPopSetterMethod,
        ).mapNotNull { methodName ->
            resolveForumPageNullableSetter(dataClass, methodName)
        }.distinct()
        if (setters.size < 3) {
            XposedCompat.log("[ForumPageAdBlockHook] bottom data setters not resolved on $dataClassName")
            return null
        }
        return mapperMethod to setters
    }

    private fun resolveForumPageHeaderTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method, Method>? {
        val mapperMethod = resolveForumPageMapperMethod(
            cl = cl,
            mapperClassName = symbols.forumPageMapperClass,
            methodName = symbols.forumHeaderDataMapperMethod,
            returnClassName = symbols.forumHeaderDataClass,
            label = "header data mapper",
        ) ?: return null
        val headerClassName = symbols.forumHeaderDataClass?.takeIf { it.isNotBlank() } ?: return null
        val rainClassName = symbols.forumRainDataClass?.takeIf { it.isNotBlank() } ?: return null
        val setterName = symbols.forumRainSetterMethod?.takeIf { it.isNotBlank() } ?: return null
        val headerClass = safeFindClass(headerClassName, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] header data class NOT FOUND: $headerClassName")
            return null
        }
        val rainClass = safeFindClass(rainClassName, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] rain data class NOT FOUND: $rainClassName")
            return null
        }
        val setter = collectInstanceMethods(headerClass).singleOrNull { method ->
            method.name == setterName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(rainClass))
        } ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] rain setter NOT FOUND: $headerClassName.$setterName")
            return null
        }
        setter.isAccessible = true
        return mapperMethod to setter
    }

    private fun resolveForumPageMapperMethod(
        cl: ClassLoader,
        mapperClassName: String?,
        methodName: String?,
        returnClassName: String?,
        label: String,
    ): Method? {
        val className = mapperClassName?.takeIf { it.isNotBlank() } ?: return null
        val normalizedMethodName = methodName?.takeIf { it.isNotBlank() } ?: return null
        val mapperClass = safeFindClass(className, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] mapper class NOT FOUND: $className")
            return null
        }
        val expectedReturn = returnClassName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                safeFindClass(it, cl) ?: run {
                    XposedCompat.log("[ForumPageAdBlockHook] $label return class NOT FOUND: $it")
                    return null
                }
            }
        val method = mapperClass.declaredMethods.singleOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.name == normalizedMethodName &&
                candidate.parameterTypes.size == 1 &&
                candidate.returnType != Void.TYPE &&
                (expectedReturn == null || candidate.returnType == expectedReturn)
        } ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] $label NOT FOUND: $className.$normalizedMethodName")
            return null
        }
        method.isAccessible = true
        return method
    }

    private fun resolveForumPageNullableSetter(
        dataClass: Class<*>,
        methodName: String?,
    ): Method? {
        val normalizedName = methodName?.takeIf { it.isNotBlank() } ?: return null
        return collectInstanceMethods(dataClass).singleOrNull { method ->
            method.name == normalizedName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive
        }?.apply { isAccessible = true }
    }

    private fun resolveForumPageDialogTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method?, Method?> {
        val className = symbols.forumDialogControllerClass?.takeIf { it.isNotBlank() } ?: return null to null
        val controllerClass = safeFindClass(className, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] dialog controller class NOT FOUND: $className")
            return null to null
        }
        val businessShowName = symbols.forumBusinessPromotShowMethod?.takeIf { it.isNotBlank() }
        val businessShow = businessShowName?.let { methodName ->
            collectInstanceMethods(controllerClass).singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    !method.parameterTypes[1].isPrimitive
            }?.apply { isAccessible = true }
        }
        val animationName = symbols.forumAnimationShowMethod?.takeIf { it.isNotBlank() }
        val animationShow = animationName?.let { methodName ->
            collectInstanceMethods(controllerClass).singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
        }
        return businessShow to animationShow
    }

    private fun resolveForumPageFloatingTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method?, Field?> {
        val className = symbols.forumGameFloatingBarControllerClass?.takeIf { it.isNotBlank() } ?: return null to null
        val controllerClass = safeFindClass(className, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] floating bar controller class NOT FOUND: $className")
            return null to null
        }
        val showName = symbols.forumGameFloatingBarShowMethod?.takeIf { it.isNotBlank() }
        val showMethod = showName?.let { methodName ->
            collectInstanceMethods(controllerClass).singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
        }
        val fieldName = symbols.forumGameFloatingBarField?.takeIf { it.isNotBlank() }
        val floatingBarField = fieldName?.let { findForumPageField(controllerClass, it) }
            ?.apply { isAccessible = true }
        return showMethod to floatingBarField
    }

    private fun resolveForumPageBusinessPromotJumpMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        val className = symbols.forumBusinessPromotBizClass?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols.forumBusinessPromotJumpMethod?.takeIf { it.isNotBlank() } ?: return null
        val bizClass = safeFindClass(className, cl) ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] business promot biz class NOT FOUND: $className")
            return null
        }
        return collectInstanceMethods(bizClass).singleOrNull { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.apply { isAccessible = true } ?: run {
            XposedCompat.log("[ForumPageAdBlockHook] business promot jump method NOT FOUND: $className.$methodName")
            null
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
        XposedCompat.logD("[ForumPageAdBlockHook] field not resolved: ${clazz.name}.$fieldName")
        return null
    }

    private fun resolvePostAdBlockedItemClasses(cl: ClassLoader): Array<Class<*>> {
        return arrayOf(
            safeFindClass(StableTiebaHookPoints.PB_FIRST_FLOOR_RECOMMEND_DATA_CLASS, cl),
        ).filterNotNull().toTypedArray()
    }

    private fun resolvePostAdBlockedTypes(cl: ClassLoader): Set<Any> {
        val out = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val advertAppInfoClass = safeFindClass(POST_AD_ADVERT_APP_INFO_CLASS, cl) ?: return out
        for (fieldName in POST_AD_ADVERT_APP_TYPE_FIELDS) {
            val value = try {
                XposedCompat.findField(advertAppInfoClass, fieldName).get(null)
            } catch (_: Throwable) {
                null
            } ?: continue
            out.add(value)
        }
        return out
    }

    fun resolveFeedAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
        includeCustomPostFilter: Boolean = false,
    ): FeedAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[FeedAdHook] skipped: scan symbols unavailable")
                return null
            }
            val templateKeyMethodName = resolvedSymbols.feedTemplateKeyMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FeedAdHook] skipped: feedTemplateKeyMethod missing")
                return null
            }
            val templateAdapterClass = safeFindClass(StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS, cl) ?: run {
                XposedCompat.log("[FeedAdHook] class NOT FOUND: ${StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS}")
                return null
            }
            val setListMethod = findFeedListMethod(templateAdapterClass, StableTiebaHookPoints.METHOD_SET_LIST) ?: run {
                XposedCompat.log(
                    "[FeedAdHook] method NOT FOUND: " +
                        "${StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS}.${StableTiebaHookPoints.METHOD_SET_LIST}(List)",
                )
                return null
            }

            val loadMoreMethod = resolveFeedLoadMoreMethod(cl, resolvedSymbols)
            val customPostFilter = if (includeCustomPostFilter) {
                resolveCustomPostCardFilterSymbols(resolvedSymbols) ?: return null
            } else {
                null
            }
            FeedAdSymbols(
                setListMethod = setListMethod,
                loadMoreMethod = loadMoreMethod,
                templateKeyMethodName = templateKeyMethodName,
                customPostFilter = customPostFilter,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[FeedAdHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveFeedInfoLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): FeedInfoLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[FeedInfoLogHook] skipped: scan symbols unavailable")
                return null
            }
            val bindMethodName = resolvedSymbols.feedCardBindMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FeedInfoLogHook] skipped: feedCardBindMethod missing")
                return null
            }
            val bindMethodSpec = resolvedSymbols.feedCardBindMethodSpec?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[FeedInfoLogHook] skipped: feedCardBindMethodSpec missing")
                return null
            }
            val feedCardViewClass = safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[FeedInfoLogHook] skipped: class not found: " +
                        StableTiebaHookPoints.FEED_CARD_VIEW_CLASS,
                )
                return null
            }
            val bindMethod = resolveMethodByCachedSpec(feedCardViewClass, bindMethodSpec, bindMethodName) ?: run {
                XposedCompat.log(
                    "[FeedInfoLogHook] skipped: method not found: " +
                        "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodSpec",
                )
                return null
            }
            bindMethod.isAccessible = true
            FeedInfoLogSymbols(
                bindMethod = bindMethod,
                templateKeyMethodName = resolvedSymbols.feedTemplateKeyMethod?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            XposedCompat.log("[FeedInfoLogHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveReplyServerResponseLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ReplyServerResponseLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ReplyServerResponseLogHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.replyServerResponseClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[ReplyServerResponseLogHook] skipped: response class missing")
                return null
            }
            val decodeMethodName = resolvedSymbols.replyServerResponseDecodeMethod?.takeIf { it.isNotBlank() }
                ?: run {
                    XposedCompat.log("[ReplyServerResponseLogHook] skipped: decode method missing")
                    return null
                }
            val resultJsonFieldName =
                resolvedSymbols.replyServerResponseResultJsonField?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[ReplyServerResponseLogHook] skipped: result JSON field missing")
                    return null
                }
            val responseClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[ReplyServerResponseLogHook] skipped: class not found: $className")
                return null
            }
            val decodeMethod = responseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isReplyServerResponseDecodeMethod(method, decodeMethodName)
            } ?: run {
                XposedCompat.log("[ReplyServerResponseLogHook] skipped: method not found: $className.$decodeMethodName")
                return null
            }
            val resultJsonField = responseClass.declaredFields.singleOrNull { field ->
                field.name == resultJsonFieldName && JSONObject::class.java.isAssignableFrom(field.type)
            } ?: run {
                XposedCompat.log(
                    "[ReplyServerResponseLogHook] skipped: field not found: $className.$resultJsonFieldName",
                )
                return null
            }
            decodeMethod.isAccessible = true
            resultJsonField.isAccessible = true
            ReplyServerResponseLogSymbols(
                decodeMethod = decodeMethod,
                resultJsonField = resultJsonField,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ReplyServerResponseLogHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveAgreeServerResponseLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): AgreeServerResponseLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[AgreeServerResponseLogHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols.agreeServerResponseClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[AgreeServerResponseLogHook] skipped: response class missing")
                return null
            }
            val decodeLogicMethodName =
                resolvedSymbols.agreeServerResponseDecodeLogicMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[AgreeServerResponseLogHook] skipped: decode logic method missing")
                    return null
                }
            val responseClass = safeFindClass(className, cl) ?: run {
                XposedCompat.log("[AgreeServerResponseLogHook] skipped: class not found: $className")
                return null
            }
            val decodeLogicMethod = responseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isAgreeServerResponseDecodeLogicMethod(method, decodeLogicMethodName)
            } ?: run {
                XposedCompat.log(
                    "[AgreeServerResponseLogHook] skipped: method not found: " +
                        "$className.$decodeLogicMethodName(int,JSONObject)",
                )
                return null
            }
            decodeLogicMethod.isAccessible = true
            AgreeServerResponseLogSymbols(decodeLogicMethod = decodeLogicMethod)
        } catch (t: Throwable) {
            XposedCompat.log("[AgreeServerResponseLogHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveReplyVisibilityProbeSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): ReplyVisibilityProbeSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[ReplyVisibilityProbeHook] skipped: scan symbols unavailable")
                return null
            }

            fun requireSymbol(name: String, value: String?): String {
                return value?.takeIf { it.isNotBlank() } ?: error("$name missing")
            }

            val replyResponseClassName = requireSymbol(
                "replyVisibilityProbeReplyResponseClass",
                resolvedSymbols.replyVisibilityProbeReplyResponseClass,
            )
            val replyDecodeMethodName = requireSymbol(
                "replyVisibilityProbeReplyDecodeMethod",
                resolvedSymbols.replyVisibilityProbeReplyDecodeMethod,
            )
            val replyResultJsonFieldName = requireSymbol(
                "replyVisibilityProbeReplyResultJsonField",
                resolvedSymbols.replyVisibilityProbeReplyResultJsonField,
            )
            val addPostRequestClassName = requireSymbol(
                "replyVisibilityProbeAddPostRequestClass",
                resolvedSymbols.replyVisibilityProbeAddPostRequestClass,
            )
            val addPostRequestDataFieldName = requireSymbol(
                "replyVisibilityProbeAddPostRequestDataField",
                resolvedSymbols.replyVisibilityProbeAddPostRequestDataField,
            )
            val responsedMessageClassName = requireSymbol(
                "replyVisibilityProbeResponsedMessageClass",
                resolvedSymbols.replyVisibilityProbeResponsedMessageClass,
            )
            val getOriginalMessageMethodName = requireSymbol(
                "replyVisibilityProbeGetOriginalMessageMethod",
                resolvedSymbols.replyVisibilityProbeGetOriginalMessageMethod,
            )
            val messageClassName = requireSymbol(
                "replyVisibilityProbeMessageClass",
                resolvedSymbols.replyVisibilityProbeMessageClass,
            )
            val messageGetExtraMethodName = requireSymbol(
                "replyVisibilityProbeMessageGetExtraMethod",
                resolvedSymbols.replyVisibilityProbeMessageGetExtraMethod,
            )
            val messageGetTagMethodName = requireSymbol(
                "replyVisibilityProbeMessageGetTagMethod",
                resolvedSymbols.replyVisibilityProbeMessageGetTagMethod,
            )
            val messageSetTagMethodName = requireSymbol(
                "replyVisibilityProbeMessageSetTagMethod",
                resolvedSymbols.replyVisibilityProbeMessageSetTagMethod,
            )
            val httpMessageClassName = requireSymbol(
                "replyVisibilityProbeHttpMessageClass",
                resolvedSymbols.replyVisibilityProbeHttpMessageClass,
            )
            requireSymbol(
                "replyVisibilityProbeHttpMessageConstructor",
                resolvedSymbols.replyVisibilityProbeHttpMessageConstructor,
            )
            val httpMessageAddParamMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageAddParamMethod",
                resolvedSymbols.replyVisibilityProbeHttpMessageAddParamMethod,
            )
            val httpMessageAddHeaderMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageAddHeaderMethod",
                resolvedSymbols.replyVisibilityProbeHttpMessageAddHeaderMethod,
            )
            val messageManagerClassName = requireSymbol(
                "replyVisibilityProbeMessageManagerClass",
                resolvedSymbols.replyVisibilityProbeMessageManagerClass,
            )
            val messageManagerGetInstanceMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerGetInstanceMethod",
                resolvedSymbols.replyVisibilityProbeMessageManagerGetInstanceMethod,
            )
            val messageManagerFindTaskMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerFindTaskMethod",
                resolvedSymbols.replyVisibilityProbeMessageManagerFindTaskMethod,
            )
            val messageManagerRegisterTaskMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerRegisterTaskMethod",
                resolvedSymbols.replyVisibilityProbeMessageManagerRegisterTaskMethod,
            )
            val messageManagerSendMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerSendMethod",
                resolvedSymbols.replyVisibilityProbeMessageManagerSendMethod,
            )
            val tbHttpMessageTaskClassName = requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskClass",
                resolvedSymbols.replyVisibilityProbeTbHttpMessageTaskClass,
            )
            requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskConstructor",
                resolvedSymbols.replyVisibilityProbeTbHttpMessageTaskConstructor,
            )
            val httpMessageTaskSetResponsedClassMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod",
                resolvedSymbols.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod,
            )
            val tbHttpMessageTaskSetIsNeedTbsMethodName = requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod",
                resolvedSymbols.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod,
            )
            val bdUniqueIdClassName = requireSymbol(
                "replyVisibilityProbeBdUniqueIdClass",
                resolvedSymbols.replyVisibilityProbeBdUniqueIdClass,
            )
            val bdUniqueIdGenMethodName = requireSymbol(
                "replyVisibilityProbeBdUniqueIdGenMethod",
                resolvedSymbols.replyVisibilityProbeBdUniqueIdGenMethod,
            )
            val tbadkCoreApplicationClassName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationClass",
                resolvedSymbols.replyVisibilityProbeTbadkCoreApplicationClass,
            )
            val tbadkCoreApplicationGetInstMethodName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationGetInstMethod",
                resolvedSymbols.replyVisibilityProbeTbadkCoreApplicationGetInstMethod,
            )
            val tbadkCoreApplicationGetZidMethodName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationGetZidMethod",
                resolvedSymbols.replyVisibilityProbeTbadkCoreApplicationGetZidMethod,
            )
            val tbConfigClassName = requireSymbol(
                "replyVisibilityProbeTbConfigClass",
                resolvedSymbols.replyVisibilityProbeTbConfigClass,
            )
            val tbConfigServerAddressFieldName = requireSymbol(
                "replyVisibilityProbeTbConfigServerAddressField",
                resolvedSymbols.replyVisibilityProbeTbConfigServerAddressField,
            )
            val tbConfigPbFloorAgreeUrlFieldName = requireSymbol(
                "replyVisibilityProbeTbConfigPbFloorAgreeUrlField",
                resolvedSymbols.replyVisibilityProbeTbConfigPbFloorAgreeUrlField,
            )
            val cmdConfigHttpClassName = requireSymbol(
                "replyVisibilityProbeCmdConfigHttpClass",
                resolvedSymbols.replyVisibilityProbeCmdConfigHttpClass,
            )
            val cmdPbFloorAgreeFieldName = requireSymbol(
                "replyVisibilityProbeCmdPbFloorAgreeField",
                resolvedSymbols.replyVisibilityProbeCmdPbFloorAgreeField,
            )
            val agreeResponseClassName = requireSymbol(
                "replyVisibilityProbeAgreeResponseClass",
                resolvedSymbols.replyVisibilityProbeAgreeResponseClass,
            )
            val agreeDecodeLogicMethodName = requireSymbol(
                "replyVisibilityProbeAgreeDecodeLogicMethod",
                resolvedSymbols.replyVisibilityProbeAgreeDecodeLogicMethod,
            )

            val replyResponseClass = safeFindClass(replyResponseClassName, cl) ?: error("class not found: $replyResponseClassName")
            val addPostRequestClass =
                safeFindClass(addPostRequestClassName, cl) ?: error("class not found: $addPostRequestClassName")
            val responsedMessageClass =
                safeFindClass(responsedMessageClassName, cl) ?: error("class not found: $responsedMessageClassName")
            val messageClass = safeFindClass(messageClassName, cl) ?: error("class not found: $messageClassName")
            val httpMessageClass =
                safeFindClass(httpMessageClassName, cl) ?: error("class not found: $httpMessageClassName")
            val messageManagerClass =
                safeFindClass(messageManagerClassName, cl) ?: error("class not found: $messageManagerClassName")
            val messageTaskClass = safeFindClass(MESSAGE_TASK_CLASS, cl) ?: error("class not found: $MESSAGE_TASK_CLASS")
            val httpMessageTaskClass =
                safeFindClass(HTTP_MESSAGE_TASK_CLASS, cl) ?: error("class not found: $HTTP_MESSAGE_TASK_CLASS")
            val httpResponsedMessageClass =
                safeFindClass(HTTP_RESPONSED_MESSAGE_CLASS, cl) ?: error("class not found: $HTTP_RESPONSED_MESSAGE_CLASS")
            val tbHttpMessageTaskClass =
                safeFindClass(tbHttpMessageTaskClassName, cl) ?: error("class not found: $tbHttpMessageTaskClassName")
            val bdUniqueIdClass =
                safeFindClass(bdUniqueIdClassName, cl) ?: error("class not found: $bdUniqueIdClassName")
            val tbadkCoreApplicationClass = safeFindClass(tbadkCoreApplicationClassName, cl)
                ?: error("class not found: $tbadkCoreApplicationClassName")
            val tbConfigClass = safeFindClass(tbConfigClassName, cl) ?: error("class not found: $tbConfigClassName")
            val cmdConfigHttpClass =
                safeFindClass(cmdConfigHttpClassName, cl) ?: error("class not found: $cmdConfigHttpClassName")
            val agreeResponseClass =
                safeFindClass(agreeResponseClassName, cl) ?: error("class not found: $agreeResponseClassName")
            if (!httpResponsedMessageClass.isAssignableFrom(agreeResponseClass)) {
                error("$agreeResponseClassName is not a HttpResponsedMessage")
            }

            val replyDecodeMethod = replyResponseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isReplyServerResponseDecodeMethod(method, replyDecodeMethodName)
            } ?: error("method not found: $replyResponseClassName.$replyDecodeMethodName(int,byte[])")
            val replyResultJsonField = replyResponseClass.declaredFields.singleOrNull { field ->
                field.name == replyResultJsonFieldName && JSONObject::class.java.isAssignableFrom(field.type)
            } ?: error("field not found: $replyResponseClassName.$replyResultJsonFieldName")
            val addPostRequestDataField = addPostRequestClass.declaredFields.singleOrNull { field ->
                field.name == addPostRequestDataFieldName &&
                    ReplyVisibilityProbeSymbolScanner.isStringMapField(field)
            } ?: error("field not found: $addPostRequestClassName.$addPostRequestDataFieldName")
            val getOriginalMessageMethod = responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getOriginalMessageMethodName &&
                    method.parameterTypes.isEmpty() &&
                    messageClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $responsedMessageClassName.$getOriginalMessageMethodName()")
            val messageGetExtraMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageGetExtraMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: error("method not found: $messageClassName.$messageGetExtraMethodName()")
            val messageGetTagMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageGetTagMethodName &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageClassName.$messageGetTagMethodName()")
            val messageSetTagMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageSetTagMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == bdUniqueIdClass
            } ?: error("method not found: $messageClassName.$messageSetTagMethodName(BdUniqueId)")
            val httpMessageConstructor = httpMessageClass.declaredConstructors.singleOrNull { constructor ->
                constructor.parameterTypes.size == 1 && isIntType(constructor.parameterTypes[0])
            } ?: error("constructor not found: $httpMessageClassName(int)")
            val httpMessageAddParamMethod = httpMessageClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageAddParamMethodName &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == Any::class.java
            } ?: error("method not found: $httpMessageClassName.$httpMessageAddParamMethodName(String,Object)")
            val httpMessageAddHeaderMethod = httpMessageClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageAddHeaderMethodName &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
            } ?: error("method not found: $httpMessageClassName.$httpMessageAddHeaderMethodName(String,String)")
            val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    messageManagerClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageManagerClassName.$messageManagerGetInstanceMethodName()")
            val messageManagerFindTaskMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerFindTaskMethodName &&
                    method.parameterTypes.size == 1 &&
                    isIntType(method.parameterTypes[0]) &&
                    messageTaskClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageManagerClassName.$messageManagerFindTaskMethodName(int)")
            val messageManagerRegisterTaskMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerRegisterTaskMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageTaskClass
            } ?: error("method not found: $messageManagerClassName.$messageManagerRegisterTaskMethodName(MessageTask)")
            val messageManagerSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerSendMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageClass
            } ?: error("method not found: $messageManagerClassName.$messageManagerSendMethodName(Message)")
            val tbHttpMessageTaskConstructor = tbHttpMessageTaskClass.declaredConstructors.singleOrNull { constructor ->
                constructor.parameterTypes.size == 2 &&
                    isIntType(constructor.parameterTypes[0]) &&
                    constructor.parameterTypes[1] == String::class.java
            } ?: error("constructor not found: $tbHttpMessageTaskClassName(int,String)")
            val httpMessageTaskSetResponsedClassMethod = httpMessageTaskClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageTaskSetResponsedClassMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Class::class.java
            } ?: error("method not found: $HTTP_MESSAGE_TASK_CLASS.$httpMessageTaskSetResponsedClassMethodName(Class)")
            val tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskClass.declaredMethods.singleOrNull { method ->
                method.name == tbHttpMessageTaskSetIsNeedTbsMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            } ?: error("method not found: $tbHttpMessageTaskClassName.$tbHttpMessageTaskSetIsNeedTbsMethodName(boolean)")
            val bdUniqueIdGenMethod = bdUniqueIdClass.declaredMethods.singleOrNull { method ->
                method.name == bdUniqueIdGenMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $bdUniqueIdClassName.$bdUniqueIdGenMethodName()")
            val tbadkCoreApplicationGetInstMethod =
                tbadkCoreApplicationClass.declaredMethods.singleOrNull { method ->
                    method.name == tbadkCoreApplicationGetInstMethodName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        tbadkCoreApplicationClass.isAssignableFrom(method.returnType)
                } ?: error("method not found: $tbadkCoreApplicationClassName.$tbadkCoreApplicationGetInstMethodName()")
            val tbadkCoreApplicationGetZidMethod =
                tbadkCoreApplicationClass.declaredMethods.singleOrNull { method ->
                    method.name == tbadkCoreApplicationGetZidMethodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == String::class.java
                } ?: error("method not found: $tbadkCoreApplicationClassName.$tbadkCoreApplicationGetZidMethodName()")
            val tbConfigServerAddressField = tbConfigClass.declaredFields.singleOrNull { field ->
                field.name == tbConfigServerAddressFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: error("field not found: $tbConfigClassName.$tbConfigServerAddressFieldName")
            val tbConfigPbFloorAgreeUrlField = tbConfigClass.declaredFields.singleOrNull { field ->
                field.name == tbConfigPbFloorAgreeUrlFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: error("field not found: $tbConfigClassName.$tbConfigPbFloorAgreeUrlFieldName")
            val cmdPbFloorAgreeField = cmdConfigHttpClass.declaredFields.singleOrNull { field ->
                field.name == cmdPbFloorAgreeFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    isIntType(field.type)
            } ?: error("field not found: $cmdConfigHttpClassName.$cmdPbFloorAgreeFieldName")
            val agreeDecodeLogicMethod = agreeResponseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isAgreeServerResponseDecodeLogicMethod(
                    method,
                    agreeDecodeLogicMethodName,
                )
            } ?: error("method not found: $agreeResponseClassName.$agreeDecodeLogicMethodName(int,JSONObject)")

            listOf(
                replyDecodeMethod,
                getOriginalMessageMethod,
                messageGetExtraMethod,
                messageGetTagMethod,
                messageSetTagMethod,
                httpMessageAddParamMethod,
                httpMessageAddHeaderMethod,
                messageManagerGetInstanceMethod,
                messageManagerFindTaskMethod,
                messageManagerRegisterTaskMethod,
                messageManagerSendMethod,
                httpMessageTaskSetResponsedClassMethod,
                tbHttpMessageTaskSetIsNeedTbsMethod,
                bdUniqueIdGenMethod,
                tbadkCoreApplicationGetInstMethod,
                tbadkCoreApplicationGetZidMethod,
                agreeDecodeLogicMethod,
            ).forEach { it.isAccessible = true }
            listOf(
                replyResultJsonField,
                addPostRequestDataField,
                tbConfigServerAddressField,
                tbConfigPbFloorAgreeUrlField,
                cmdPbFloorAgreeField,
            ).forEach { it.isAccessible = true }
            httpMessageConstructor.isAccessible = true
            tbHttpMessageTaskConstructor.isAccessible = true

            ReplyVisibilityProbeSymbols(
                replyDecodeMethod = replyDecodeMethod,
                replyResultJsonField = replyResultJsonField,
                addPostRequestClass = addPostRequestClass,
                addPostRequestDataField = addPostRequestDataField,
                getOriginalMessageMethod = getOriginalMessageMethod,
                messageGetExtraMethod = messageGetExtraMethod,
                messageGetTagMethod = messageGetTagMethod,
                messageSetTagMethod = messageSetTagMethod,
                httpMessageConstructor = httpMessageConstructor,
                httpMessageAddParamMethod = httpMessageAddParamMethod,
                httpMessageAddHeaderMethod = httpMessageAddHeaderMethod,
                messageManagerGetInstanceMethod = messageManagerGetInstanceMethod,
                messageManagerFindTaskMethod = messageManagerFindTaskMethod,
                messageManagerRegisterTaskMethod = messageManagerRegisterTaskMethod,
                messageManagerSendMethod = messageManagerSendMethod,
                tbHttpMessageTaskConstructor = tbHttpMessageTaskConstructor,
                httpMessageTaskSetResponsedClassMethod = httpMessageTaskSetResponsedClassMethod,
                tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskSetIsNeedTbsMethod,
                bdUniqueIdGenMethod = bdUniqueIdGenMethod,
                tbadkCoreApplicationGetInstMethod = tbadkCoreApplicationGetInstMethod,
                tbadkCoreApplicationGetZidMethod = tbadkCoreApplicationGetZidMethod,
                tbConfigServerAddressField = tbConfigServerAddressField,
                tbConfigPbFloorAgreeUrlField = tbConfigPbFloorAgreeUrlField,
                cmdPbFloorAgreeField = cmdPbFloorAgreeField,
                agreeResponseClass = agreeResponseClass,
                agreeDecodeLogicMethod = agreeDecodeLogicMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ReplyVisibilityProbeHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolveFeedLoadMoreMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val methodName = symbols.feedTemplateLoadMoreMethod?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[FeedAdHook] FeedTemplateAdapter loadMore skipped: scan symbol missing")
            return null
        }
        val adapterClass = safeFindClass(StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS, cl) ?: run {
            XposedCompat.log("[FeedAdHook] class NOT FOUND: ${StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS}")
            return null
        }
        return findFeedListMethod(adapterClass, methodName) ?: run {
            XposedCompat.log(
                "[FeedAdHook] method NOT FOUND: " +
                    "${StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS}.$methodName(List)",
            )
            null
        }
    }

    private fun findFeedListMethod(clazz: Class<*>, methodName: String): Method? {
        return try {
            clazz.getDeclaredMethod(methodName, List::class.java).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun resolveCustomPostCardFilterSymbols(symbols: HookSymbols): CustomPostCardFilterSymbols? {
        val templateKeyMethodName = symbols.feedTemplateKeyMethod?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[CustomPostCardBlockHook] SKIP: feedTemplateKeyMethod missing")
            return null
        }
        val templatePayloadMethodName = symbols.feedTemplatePayloadMethod?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[CustomPostCardBlockHook] SKIP: feedTemplatePayloadMethod missing")
            return null
        }
        val dataListFieldName = symbols.feedCardDataListField?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[CustomPostCardBlockHook] SKIP: feedCardDataListField missing")
            return null
        }
        return CustomPostCardFilterSymbols(
            dataListFieldName = dataListFieldName,
            templateKeyMethodName = templateKeyMethodName,
            templatePayloadMethodName = templatePayloadMethodName,
            headParamsFieldName = symbols.feedHeadParamsField?.takeIf { it.isNotBlank() },
            recommendNestedDataMethodName = symbols.feedRecommendCardNestedDataMethod?.takeIf { it.isNotBlank() },
            recommendNestedDataListFieldName = symbols.feedRecommendCardNestedDataListField?.takeIf { it.isNotBlank() },
        )
    }

    fun resolveStrategyAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): StrategyAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[StrategyAdHook] skipped: scan symbols unavailable")
                return null
            }
            val constants = ArrayList<ConstantReturnMethodSymbols>(4)
            resolveConstantReturnMethod(
                cl = cl,
                className = resolvedSymbols.splashAdHelperClass,
                methodName = resolvedSymbols.splashAdHelperMethod,
                value = true,
                label = "splash helper",
            )?.let(constants::add)

            val closeAdMethods = listOfNotNull(
                resolvedSymbols.closeAdDataMethodG1,
                resolvedSymbols.closeAdDataMethodJ1,
            ).flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (!resolvedSymbols.closeAdDataClass.isNullOrBlank() && closeAdMethods.isNotEmpty()) {
                for (methodName in closeAdMethods) {
                    resolveConstantReturnMethod(
                        cl = cl,
                        className = resolvedSymbols.closeAdDataClass,
                        methodName = methodName,
                        value = 1,
                        label = "CloseAdData",
                    )?.let(constants::add)
                }
            } else {
                XposedCompat.logD("[StrategyAdHook] CloseAdData skipped: methods not resolved by scan")
            }

            val zgaMethods = resolveZgaStringMethods(cl, resolvedSymbols)
            if (constants.isEmpty() && zgaMethods.isEmpty()) {
                XposedCompat.log("[StrategyAdHook] skipped: no resolved symbol targets")
                return null
            }
            StrategyAdSymbols(
                constantReturnMethods = constants,
                zgaMethods = zgaMethods,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[StrategyAdHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolveConstantReturnMethod(
        cl: ClassLoader,
        className: String?,
        methodName: String?,
        value: Any,
        label: String,
    ): ConstantReturnMethodSymbols? {
        if (className.isNullOrBlank() || methodName.isNullOrBlank()) {
            XposedCompat.logD("[StrategyAdHook] $label skipped: scan symbols missing")
            return null
        }
        val clazz = safeFindClass(className, cl) ?: run {
            XposedCompat.logD { "[StrategyAdHook] class NOT FOUND: $className (for $methodName)" }
            return null
        }
        val method = XposedCompat.findMethodOrNull(clazz, methodName) ?: run {
            XposedCompat.logD { "[StrategyAdHook] method NOT FOUND: ${clazz.simpleName}.$methodName" }
            return null
        }
        method.isAccessible = true
        return ConstantReturnMethodSymbols(method = method, value = value)
    }

    private fun resolveZgaStringMethods(cl: ClassLoader, symbols: HookSymbols): List<Method> {
        val zgaClassName = symbols.zgaClass?.takeIf { it.isNotBlank() } ?: return emptyList()
        val zgaMethods = symbols.zgaMethods.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (zgaMethods.isEmpty()) return emptyList()
        val zgaClass = safeFindClass(zgaClassName, cl) ?: return emptyList()
        if (zgaClass.isInterface || Modifier.isAbstract(zgaClass.modifiers)) {
            XposedCompat.logD { "[StrategyAdHook] zga skipped: target class is abstract/interface: $zgaClassName" }
            return emptyList()
        }
        return zgaMethods.flatMap { methodName ->
            zgaClass.declaredMethods.filter { method ->
                method.name == methodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java &&
                    !Modifier.isAbstract(method.modifiers)
            }
        }.onEach { it.isAccessible = true }
    }

    fun resolvePbGestureScaleSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbGestureScaleSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: scan symbols unavailable")
                return null
            }
            val managerClassName = resolvedSymbols.pbGestureScaleManagerClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleManagerClass")
                return null
            }
            val dispatchMethodName = resolvedSymbols.pbGestureScaleDispatchMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleDispatchMethod")
                return null
            }
            val managerClass = safeFindClass(managerClassName, cl) ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: class not found: $managerClassName")
                return null
            }
            val dispatchMethod = managerClass.declaredMethods.singleOrNull { candidate ->
                !Modifier.isStatic(candidate.modifiers) &&
                    candidate.name == dispatchMethodName &&
                    candidate.returnType == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == MotionEvent::class.java
            } ?: run {
                XposedCompat.log(
                    "[PbDisableGestureFontScaleHook] skipped: method mismatch: " +
                        "$managerClassName.$dispatchMethodName(MotionEvent)",
                )
                return null
            }
            dispatchMethod.isAccessible = true
            PbGestureScaleSymbols(dispatchMethod = dispatchMethod)
        } catch (t: Throwable) {
            XposedCompat.log("[PbDisableGestureFontScaleHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePbScrollCoalesceSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbScrollCoalesceSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbScrollCoalesceHook] skipped: scan symbols unavailable")
                return null
            }
            val listenerClassName = resolvedSymbols.pbCommentScrollListenerClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbScrollCoalesceHook] skipped: missing pbCommentScrollListenerClass")
                return null
            }
            val methodName = resolvedSymbols.pbCommentScrollMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbScrollCoalesceHook] skipped: missing pbCommentScrollMethod")
                return null
            }
            val listenerClass = safeFindClass(listenerClassName, cl) ?: run {
                XposedCompat.log("[PbScrollCoalesceHook] skipped: class not found: $listenerClassName")
                return null
            }
            val method = collectInstanceMethods(listenerClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 4 &&
                    candidate.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: run {
                XposedCompat.log("[PbScrollCoalesceHook] skipped: method mismatch: $listenerClassName.$methodName")
                return null
            }
            method.isAccessible = true
            PbScrollCoalesceSymbols(scrollMethod = method)
        } catch (t: Throwable) {
            XposedCompat.log("[PbScrollCoalesceHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolvePbCommentAutoLoadSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbCommentAutoLoadSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbCommentAutoLoadHook] skipped: scan symbols unavailable")
                return null
            }
            val listTargets = resolvePbCommentBottomListTargets(cl, resolvedSymbols)
            val recyclerTargets = resolvePbCommentBottomRecyclerTargets(cl, resolvedSymbols)
            if (listTargets == null && recyclerTargets == null) {
                XposedCompat.log("[PbCommentAutoLoadHook] skipped: bottom mechanism symbol missing")
                return null
            }
            PbCommentAutoLoadSymbols(listTargets = listTargets, recyclerTargets = recyclerTargets)
        } catch (t: Throwable) {
            XposedCompat.log("[PbCommentAutoLoadHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbCommentBottomListTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): PbCommentBottomListSymbols? {
        val scrollClassName = symbols.pbCommentBottomListScrollClass?.takeIf { it.isNotBlank() } ?: return null
        val scrollMethodName = symbols.pbCommentBottomListScrollMethod?.takeIf { it.isNotBlank() } ?: return null
        val ownerFieldName = symbols.pbCommentBottomListOwnerField?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val scrollClass = safeFindClass(scrollClassName, cl) ?: return null
            val listClass = safeFindClass(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl) ?: return null
            val ownerField = XposedCompat.findField(scrollClass, ownerFieldName)
            if (ownerField.type != listClass) return null
            val bottomListenerField = XposedCompat.findField(listClass, PB_COMMENT_BOTTOM_LISTENER_FIELD)
            val bottomMethod = resolvePbCommentBottomMethod(bottomListenerField.type) ?: return null
            val scrollMethod = resolvePbCommentListScrollMethod(scrollClass, scrollMethodName) ?: return null
            ownerField.isAccessible = true
            bottomListenerField.isAccessible = true
            PbCommentBottomListSymbols(
                listClass = listClass,
                scrollMethod = scrollMethod,
                ownerField = ownerField,
                bottomListenerField = bottomListenerField,
                bottomMethod = bottomMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbCommentAutoLoadHook] BdListView target resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbCommentBottomRecyclerTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): PbCommentBottomRecyclerSymbols? {
        val scrollClassName = symbols.pbCommentBottomRecyclerScrollClass?.takeIf { it.isNotBlank() } ?: return null
        val scrollMethodName = symbols.pbCommentBottomRecyclerScrollMethod?.takeIf { it.isNotBlank() } ?: return null
        val ownerFieldName = symbols.pbCommentBottomRecyclerOwnerField?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val scrollClass = safeFindClass(scrollClassName, cl) ?: return null
            val recyclerClass = safeFindClass(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS, cl) ?: return null
            val recyclerViewClass = safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl) ?: return null
            val ownerField = XposedCompat.findField(scrollClass, ownerFieldName)
            if (ownerField.type != recyclerClass) return null
            val bottomListenerField = XposedCompat.findField(recyclerClass, PB_COMMENT_BOTTOM_LISTENER_FIELD)
            val bottomMethod = resolvePbCommentBottomMethod(bottomListenerField.type) ?: return null
            val firstVisibleMethod = resolvePbCommentNoArgIntMethod(recyclerClass, "getFirstVisiblePosition") ?: return null
            val lastVisibleMethod = resolvePbCommentNoArgIntMethod(recyclerClass, "getLastVisiblePosition") ?: return null
            val getAdapterMethod = resolvePbCommentNoArgMethod(recyclerViewClass, "getAdapter") ?: return null
            val scrollMethod = resolvePbCommentRecyclerScrolledMethod(
                scrollClass = scrollClass,
                methodName = scrollMethodName,
                recyclerViewClass = recyclerViewClass,
            ) ?: return null
            ownerField.isAccessible = true
            bottomListenerField.isAccessible = true
            PbCommentBottomRecyclerSymbols(
                recyclerClass = recyclerClass,
                scrollMethod = scrollMethod,
                ownerField = ownerField,
                bottomListenerField = bottomListenerField,
                bottomMethod = bottomMethod,
                firstVisibleMethod = firstVisibleMethod,
                lastVisibleMethod = lastVisibleMethod,
                getAdapterMethod = getAdapterMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbCommentAutoLoadHook] BdRecyclerView target resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    private fun resolvePbCommentListScrollMethod(clazz: Class<*>, methodName: String): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == AbsListView::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun resolvePbCommentRecyclerScrolledMethod(
        scrollClass: Class<*>,
        methodName: String,
        recyclerViewClass: Class<*>,
    ): Method? {
        return scrollClass.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == recyclerViewClass &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun resolvePbCommentNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == methodName &&
                    it.parameterTypes.isEmpty()
            }
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }

    private fun resolvePbCommentNoArgIntMethod(clazz: Class<*>, methodName: String): Method? {
        return resolvePbCommentNoArgMethod(clazz, methodName)
            ?.takeIf { it.returnType == Int::class.javaPrimitiveType }
    }

    private fun resolvePbCommentBottomMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == PB_COMMENT_BOTTOM_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true }
    }

    fun resolvePbLikeAutoReplySymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): PbLikeAutoReplySymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        fun resolveClass(name: String): Class<*> {
            return safeFindClass(name, cl) ?: error("class not found: $name")
        }

        fun resolveAgreeClickMethod(clazz: Class<*>, methodName: String): Method {
            return clazz.declaredMethods.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == View::class.java
            }?.apply { isAccessible = true }
                ?: error("method not found: ${clazz.name}.$methodName(View)")
        }

        fun resolveNoArgMethod(clazz: Class<*>, methodName: String): Method {
            return clazz.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
                ?: error("method not found: ${clazz.name}.$methodName()")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[PbLikeAutoReplyHook] skipped: scan symbols unavailable")
                return null
            }
            val agreeViewClass = resolveClass(
                requireSymbol("pbLikeAutoReplyAgreeViewClass", resolvedSymbols.pbLikeAutoReplyAgreeViewClass),
            )
            val inputContainerClass = resolveClass(
                requireSymbol("pbLikeAutoReplyInputContainerClass", resolvedSymbols.pbLikeAutoReplyInputContainerClass),
            )
            val agreeClickMethod = resolveAgreeClickMethod(
                agreeViewClass,
                requireSymbol("pbLikeAutoReplyAgreeClickMethod", resolvedSymbols.pbLikeAutoReplyAgreeClickMethod),
            )
            val getDataMethod = resolveNoArgMethod(
                agreeViewClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeViewGetDataMethod",
                    resolvedSymbols.pbLikeAutoReplyAgreeViewGetDataMethod,
                ),
            )
            val agreeDataClass = resolveClass(
                requireSymbol("pbLikeAutoReplyAgreeDataClass", resolvedSymbols.pbLikeAutoReplyAgreeDataClass),
            )
            if (!agreeDataClass.isAssignableFrom(getDataMethod.returnType)) {
                error("agreeViewGetDataMethod return mismatch: ${getDataMethod.returnType.name}")
            }
            val hasAgreeField = XposedCompat.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataHasAgreeField",
                    resolvedSymbols.pbLikeAutoReplyAgreeDataHasAgreeField,
                ),
            )
            val agreeTypeField = XposedCompat.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataAgreeTypeField",
                    resolvedSymbols.pbLikeAutoReplyAgreeDataAgreeTypeField,
                ),
            )
            val isInThreadField = XposedCompat.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataIsInThreadField",
                    resolvedSymbols.pbLikeAutoReplyAgreeDataIsInThreadField,
                ),
            )
            val getInputViewMethod = resolveNoArgMethod(
                inputContainerClass,
                requireSymbol(
                    "pbLikeAutoReplyInputContainerGetInputViewMethod",
                    resolvedSymbols.pbLikeAutoReplyInputContainerGetInputViewMethod,
                ),
            )
            if (
                !EditText::class.java.isAssignableFrom(getInputViewMethod.returnType) &&
                !View::class.java.isAssignableFrom(getInputViewMethod.returnType)
            ) {
                error("inputContainerGetInputViewMethod return mismatch: ${getInputViewMethod.returnType.name}")
            }
            val getSendViewMethod = resolveNoArgMethod(
                inputContainerClass,
                requireSymbol(
                    "pbLikeAutoReplyInputContainerGetSendViewMethod",
                    resolvedSymbols.pbLikeAutoReplyInputContainerGetSendViewMethod,
                ),
            )
            if (!View::class.java.isAssignableFrom(getSendViewMethod.returnType)) {
                error("inputContainerGetSendViewMethod return mismatch: ${getSendViewMethod.returnType.name}")
            }

            PbLikeAutoReplySymbols(
                agreeViewClass = agreeViewClass,
                inputContainerClass = inputContainerClass,
                agreeClickMethod = agreeClickMethod,
                getDataMethod = getDataMethod,
                hasAgreeField = hasAgreeField,
                agreeTypeField = agreeTypeField,
                isInThreadField = isInThreadField,
                getInputViewMethod = getInputViewMethod,
                getSendViewMethod = getSendViewMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[PbLikeAutoReplyHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveMsgTabDefaultNotifySymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): MsgTabDefaultNotifySymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[MsgTabDefaultNotifyHook] skipped: scan symbols unavailable")
                return null
            }
            val methodName = resolvedSymbols.msgTabLocateToTabMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[MsgTabDefaultNotifyHook] skipped: missing msgTabLocateToTabMethod")
                return null
            }
            val viewModelClass = safeFindClass(StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[MsgTabDefaultNotifyHook] skipped: class not found: " +
                        StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS,
                )
                return null
            }
            val method = viewModelClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Long::class.javaPrimitiveType &&
                    candidate.parameterTypes.size == 2 &&
                    candidate.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    candidate.parameterTypes[1] == String::class.java
            } ?: run {
                XposedCompat.log(
                    "[MsgTabDefaultNotifyHook] skipped: method mismatch: " +
                        "${StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS}.$methodName(long,String)",
                )
                return null
            }
            method.isAccessible = true
            MsgTabDefaultNotifySymbols(locateToTabMethod = method)
        } catch (t: Throwable) {
            XposedCompat.log("[MsgTabDefaultNotifyHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveMainTabBottomSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): MainTabBottomSymbols? {
        fun namedFieldInHierarchy(clazz: Class<*>, fieldName: String): Field? {
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

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: scan symbols unavailable")
                return null
            }
            val dataClassName = resolvedSymbols.mainTabDataClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: missing mainTabDataClass")
                return null
            }
            val addMethodName = resolvedSymbols.mainTabAddMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: missing mainTabAddMethod")
                return null
            }
            val getListMethodName = resolvedSymbols.mainTabGetListMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: missing mainTabGetListMethod")
                return null
            }
            val structureMethodName =
                resolvedSymbols.mainTabDelegateGetStructureMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.log("[MainTabBottomHook] skipped: missing mainTabDelegateGetStructureMethod")
                    return null
                }
            val typeFieldName = resolvedSymbols.mainTabStructureTypeField?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: missing mainTabStructureTypeField")
                return null
            }

            val dataClass = safeFindClass(dataClassName, cl) ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: class not found: $dataClassName")
                return null
            }
            val addMethod = collectInstanceMethods(dataClass).singleOrNull { candidate ->
                candidate.name == addMethodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 1
            } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: method mismatch: $dataClassName.$addMethodName(*)")
                return null
            }
            val getListMethod = collectInstanceMethods(dataClass).singleOrNull { candidate ->
                candidate.name == getListMethodName &&
                    candidate.parameterTypes.isEmpty() &&
                    List::class.java.isAssignableFrom(candidate.returnType)
            } ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: method mismatch: $dataClassName.$getListMethodName()")
                return null
            }
            val delegateClass = addMethod.parameterTypes.firstOrNull() ?: run {
                XposedCompat.log("[MainTabBottomHook] skipped: add method delegate parameter missing")
                return null
            }
            val structureMethod = collectInstanceMethods(delegateClass).singleOrNull { candidate ->
                candidate.name == structureMethodName &&
                    candidate.parameterTypes.isEmpty() &&
                    !candidate.returnType.isPrimitive
            } ?: run {
                XposedCompat.log(
                    "[MainTabBottomHook] skipped: method mismatch: " +
                        "${delegateClass.name}.$structureMethodName()",
                )
                return null
            }
            val structureClass = structureMethod.returnType
            val typeField = namedFieldInHierarchy(structureClass, typeFieldName)
                ?.takeIf { it.type == Int::class.javaPrimitiveType }
                ?.apply { isAccessible = true }
                ?: run {
                    XposedCompat.log(
                        "[MainTabBottomHook] skipped: field mismatch: " +
                            "${structureClass.name}.$typeFieldName",
                    )
                    return null
                }
            val dynamicIconField = resolvedSymbols.mainTabStructureDynamicIconField
                ?.takeIf { it.isNotBlank() }
                ?.let { fieldName ->
                    namedFieldInHierarchy(structureClass, fieldName)
                        ?.apply { isAccessible = true }
                        ?: run {
                            XposedCompat.logD {
                                "[MainTabBottomHook] optional field missing: ${structureClass.name}.$fieldName"
                            }
                            null
                        }
                }
            val fragmentField = resolvedSymbols.mainTabStructureFragmentField
                ?.takeIf { it.isNotBlank() }
                ?.let { fieldName ->
                    namedFieldInHierarchy(structureClass, fieldName)
                        ?.apply { isAccessible = true }
                        ?: run {
                            XposedCompat.logD {
                                "[MainTabBottomHook] optional field missing: ${structureClass.name}.$fieldName"
                            }
                            null
                        }
                }

            addMethod.isAccessible = true
            getListMethod.isAccessible = true
            structureMethod.isAccessible = true
            MainTabBottomSymbols(
                dataClass = dataClass,
                addMethod = addMethod,
                getListMethod = getListMethod,
                structureMethod = structureMethod,
                structureTypeField = typeField,
                structureDynamicIconField = dynamicIconField,
                structureFragmentField = fragmentField,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[MainTabBottomHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveMethodByCachedSpec(clazz: Class<*>, spec: String, expectedName: String): Method? {
        val parsed = parseCachedMethodSpec(spec) ?: return null
        if (parsed.name != expectedName) return null
        val paramTypes = parsed.parameterTypeNames.map { typeName ->
            resolveCachedClassName(typeName, clazz.classLoader) ?: return null
        }.toTypedArray()
        val method = ReflectionUtils.findMethodInHierarchy(clazz, parsed.name, *paramTypes) ?: return null
        return method.takeIf { it.returnType.name == parsed.returnTypeName }
    }

    private fun parseCachedMethodSpec(raw: String): CachedMethodSpec? {
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3) return null
        val name = parts[0].takeIf { it.isNotBlank() } ?: return null
        val returnType = parts[1].takeIf { it.isNotBlank() } ?: return null
        val params = parts[2].split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return CachedMethodSpec(name, returnType, params)
    }

    private fun resolveCachedClassName(typeName: String, cl: ClassLoader?): Class<*>? {
        return when (typeName) {
            Void.TYPE.name -> Void.TYPE
            Boolean::class.javaPrimitiveType!!.name -> Boolean::class.javaPrimitiveType
            Byte::class.javaPrimitiveType!!.name -> Byte::class.javaPrimitiveType
            Char::class.javaPrimitiveType!!.name -> Char::class.javaPrimitiveType
            Short::class.javaPrimitiveType!!.name -> Short::class.javaPrimitiveType
            Int::class.javaPrimitiveType!!.name -> Int::class.javaPrimitiveType
            Long::class.javaPrimitiveType!!.name -> Long::class.javaPrimitiveType
            Float::class.javaPrimitiveType!!.name -> Float::class.javaPrimitiveType
            Double::class.javaPrimitiveType!!.name -> Double::class.javaPrimitiveType
            else -> runCatching { Class.forName(typeName, false, cl) }.getOrNull()
        }
    }

    fun resolveHistorySearchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): HistorySearchSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[HistorySearchHook] skipped: scan symbols unavailable")
                return null
            }
            val activityClass = safeFindClass(StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[HistorySearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS,
                )
                return null
            }
            val updateMethodName = resolvedSymbols.historyActivityListUpdateMethod?.takeIf { it.isNotBlank() }
            val updateMethodSpec = resolvedSymbols.historyActivityListUpdateMethodSpec?.takeIf { it.isNotBlank() }
            val updateMethod = if (updateMethodName != null && updateMethodSpec != null) {
                resolveMethodByCachedSpec(activityClass, updateMethodSpec, updateMethodName)?.apply {
                    isAccessible = true
                }
            } else {
                null
            }
            if (updateMethodName != null && updateMethod == null) {
                XposedCompat.logD {
                    "[HistorySearchHook] optional list update method missing: " +
                        "${activityClass.name}.$updateMethodName"
                }
            }

            HistorySearchSymbols(
                activityClass = activityClass,
                onCreateMethod = XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java),
                onResumeMethod = XposedCompat.findMethodOrNull(activityClass, "onResume"),
                onDestroyMethod = XposedCompat.findMethodOrNull(activityClass, "onDestroy"),
                activityListUpdateMethod = updateMethod,
                adapterField = requireSymbol("historyAdapterField", resolvedSymbols.historyAdapterField),
                adapterSetListMethod = requireSymbol(
                    "historyAdapterSetListMethod",
                    resolvedSymbols.historyAdapterSetListMethod,
                ),
                adapterSetListMethodSpec = requireSymbol(
                    "historyAdapterSetListMethodSpec",
                    resolvedSymbols.historyAdapterSetListMethodSpec,
                ),
                listField = requireSymbol("historyListField", resolvedSymbols.historyListField),
                activityNavBarField = requireSymbol(
                    "historyActivityNavBarField",
                    resolvedSymbols.historyActivityNavBarField,
                ),
                threadNameMethod = requireSymbol("historyThreadNameMethod", resolvedSymbols.historyThreadNameMethod),
                forumNameMethod = requireSymbol("historyForumNameMethod", resolvedSymbols.historyForumNameMethod),
                userNameMethod = requireSymbol("historyUserNameMethod", resolvedSymbols.historyUserNameMethod),
                descriptionMethod = requireSymbol(
                    "historyDescriptionMethod",
                    resolvedSymbols.historyDescriptionMethod,
                ),
                threadIdMethod = requireSymbol("historyThreadIdMethod", resolvedSymbols.historyThreadIdMethod),
                postIdMethod = requireSymbol("historyPostIdMethod", resolvedSymbols.historyPostIdMethod),
                liveIdMethod = requireSymbol("historyLiveIdMethod", resolvedSymbols.historyLiveIdMethod),
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HistorySearchHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveCollectionSearchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): CollectionSearchSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[CollectionSearchHook] skipped: scan symbols unavailable")
                return null
            }
            val activityClass = safeFindClass(StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[CollectionSearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS,
                )
                return null
            }
            val fragmentClass = safeFindClass(StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[CollectionSearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS,
                )
                return null
            }
            CollectionSearchSymbols(
                activityClass = activityClass,
                fragmentClass = fragmentClass,
                presenterField = requireSymbol("collectionPresenterField", resolvedSymbols.collectionPresenterField),
                presenterListSetterMethod = requireSymbol(
                    "collectionPresenterListSetterMethod",
                    resolvedSymbols.collectionPresenterListSetterMethod,
                ),
                presenterListSetterMethodSpec = requireSymbol(
                    "collectionPresenterListSetterMethodSpec",
                    resolvedSymbols.collectionPresenterListSetterMethodSpec,
                ),
                modelField = requireSymbol("collectionModelField", resolvedSymbols.collectionModelField),
                modelListGetterMethod = requireSymbol(
                    "collectionModelListGetterMethod",
                    resolvedSymbols.collectionModelListGetterMethod,
                ),
                modelListGetterMethodSpec = requireSymbol(
                    "collectionModelListGetterMethodSpec",
                    resolvedSymbols.collectionModelListGetterMethodSpec,
                ),
                modelParseMethod = requireSymbol("collectionModelParseMethod", resolvedSymbols.collectionModelParseMethod),
                modelParseMethodSpec = requireSymbol(
                    "collectionModelParseMethodSpec",
                    resolvedSymbols.collectionModelParseMethodSpec,
                ),
                modelListField = requireSymbol("collectionModelListField", resolvedSymbols.collectionModelListField),
                fragmentDisplayListField = requireSymbol(
                    "collectionFragmentDisplayListField",
                    resolvedSymbols.collectionFragmentDisplayListField,
                ),
                activityNavControllerField = resolvedSymbols.collectionActivityNavControllerField
                    ?.takeIf { it.isNotBlank() },
                navBarField = requireSymbol("collectionNavBarField", resolvedSymbols.collectionNavBarField),
                presenterAdapterField = resolvedSymbols.collectionPresenterAdapterField?.takeIf { it.isNotBlank() },
                adapterShowFooterMethod = resolvedSymbols.collectionAdapterShowFooterMethod?.takeIf { it.isNotBlank() },
                adapterLoadingMethod = resolvedSymbols.collectionAdapterLoadingMethod?.takeIf { it.isNotBlank() },
                adapterHasMoreMethod = resolvedSymbols.collectionAdapterHasMoreMethod?.takeIf { it.isNotBlank() },
                editModeMethod = resolvedSymbols.collectionEditModeMethod?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            XposedCompat.log("[CollectionSearchHook] symbol resolve FAILED: ${t.message}")
            XposedCompat.log(t)
            null
        }
    }

    fun resolveDefaultOriginalImageSymbols(
        cl: ClassLoader,
        symbols: HookSymbols? = getMemorySymbols(),
    ): DefaultOriginalImageSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                XposedCompat.log("[DefaultOriginalImageHook] disabled: scan symbols unavailable")
                return null
            }
            val missing = ArrayList<String>(8)
            fun requireSymbol(name: String, value: String?): String? {
                if (value.isNullOrBlank()) {
                    missing.add(name)
                    return null
                }
                return value
            }

            val urlDragClassName = requireSymbol(
                "origImageUrlDragImageViewClass",
                resolvedSymbols.origImageUrlDragImageViewClass,
            )
            val dataClassName = requireSymbol("origImageDataClass", resolvedSymbols.origImageDataClass)
            val assistDataMethod = requireSymbol("origImageAssistDataMethod", resolvedSymbols.origImageAssistDataMethod)
            val showButtonField = requireSymbol("origImageShowButtonField", resolvedSymbols.origImageShowButtonField)
            val blockedField = requireSymbol("origImageBlockedField", resolvedSymbols.origImageBlockedField)
            val originalProcessField = requireSymbol(
                "origImageOriginalProcessField",
                resolvedSymbols.origImageOriginalProcessField,
            )
            val originalUrlField = requireSymbol("origImageOriginalUrlField", resolvedSymbols.origImageOriginalUrlField)
            val triggerMethod = requireSymbol("origImageTriggerMethod", resolvedSymbols.origImageTriggerMethod)
            if (missing.isNotEmpty()) {
                XposedCompat.log("[DefaultOriginalImageHook] disabled: missing scan symbols ${missing.joinToString(",")}")
                return null
            }

            val urlDragClass = safeFindClass(urlDragClassName!!, cl) ?: run {
                XposedCompat.log("[DefaultOriginalImageHook] class NOT FOUND: $urlDragClassName")
                return null
            }
            val dataClass = safeFindClass(dataClassName!!, cl) ?: run {
                XposedCompat.log("[DefaultOriginalImageHook] class NOT FOUND: $dataClassName")
                return null
            }

            DefaultOriginalImageSymbols(
                pagerAdapterClass = resolvedSymbols.origImagePagerAdapterClass?.takeIf { it.isNotBlank() }
                    ?.let { safeFindClass(it, cl) },
                urlDragImageViewClass = urlDragClass,
                dataClass = dataClass,
                setPrimaryItemMethod = resolvedSymbols.origImageSetPrimaryItemMethod,
                setAssistUrlMethod = resolvedSymbols.origImageSetAssistUrlMethod,
                assistDataMethod = assistDataMethod!!,
                originTextMethod = resolvedSymbols.origImageOriginTextMethod,
                showButtonField = showButtonField!!,
                blockedField = blockedField!!,
                originalProcessField = originalProcessField!!,
                originalUrlField = originalUrlField!!,
                sharedPrefHelperClass = resolvedSymbols.origImageSharedPrefHelperClass?.takeIf { it.isNotBlank() }
                    ?.let { safeFindClass(it, cl) },
                sharedPrefGetInstanceMethod = resolvedSymbols.origImageSharedPrefGetInstanceMethod,
                sharedPrefPutBooleanMethod = resolvedSymbols.origImageSharedPrefPutBooleanMethod,
                md5Class = resolvedSymbols.origImageMd5Class?.takeIf { it.isNotBlank() }?.let { safeFindClass(it, cl) },
                md5Method = resolvedSymbols.origImageMd5Method,
                triggerMethod = triggerMethod!!,
                directStartMethod = resolvedSymbols.origImageDirectStartMethod,
            )
        } catch (t: Throwable) {
            XposedCompat.log("[DefaultOriginalImageHook] symbol resolve FAILED: ${t.message}")
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
                method.name == enableMethodName &&
                    AiComponentSymbolScanner.isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$controllerClassName.$enableMethodName",
                )
                return null
            }
            val initSpriteMemeMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initSpriteMemeMethodName &&
                    AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initSpriteMemeMethodName",
                )
                return null
            }
            val initAiWriteMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initAiWriteMethodName &&
                    AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            } ?: run {
                XposedCompat.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initAiWriteMethodName",
                )
                return null
            }
            if (!AiComponentSymbolScanner.isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) {
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
                method.name == methodName && AiComponentSymbolScanner.isAiPbEmojiCreationViewBindMethod(method, cl)
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
            val viewClassName = symbols.aiPbPageBrowserAiEmojiCreationViewClass
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val viewClass = safeFindClass(viewClassName, cl) ?: return null
            viewClass.declaredMethods.singleOrNull { method ->
                method.name == methodName && AiComponentSymbolScanner.isPbPageBrowserAiEmojiCreationBindMethod(method)
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
            if (AiComponentSymbolScanner.scoreImageViewerJumpButtonOwnerClass(ownerClass, layoutClass) <= 0) {
                XposedCompat.log("[AiComponentDisableHook] image viewer skipped: owner structure mismatch")
                return null
            }
            val initMethod = ownerClass.declaredMethods.singleOrNull { method ->
                method.name == initMethodName && AiComponentSymbolScanner.isImageViewerJumpButtonInitMethod(method)
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

        val memorySymbols = memoryCache.getIfFingerprint(fingerprint)
        if (!forceRescan && memorySymbols != null) {
            log(logger, "memory cache hit: source=${memorySymbols.source}")
            if (isLightweightUsable(memorySymbols)) {
                return memorySymbols
            }
            log(logger, "memory cache unusable, rescan required")
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
                    memoryCache.put(fingerprint, accepted)
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
            val unsupported = HookSymbols.unsupported(
                createdAt = System.currentTimeMillis(),
                scanErrors = scanErrors,
            )
            unsupported.withFeatureStatusMap(HookFeatureStatusDeriver.derive(unsupported))
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

        memoryCache.put(fingerprint, scanned)

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

        val memorySymbols = memoryCache.getIfFingerprint(fingerprint)
        if (memorySymbols != null) {
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
                memoryCache.put(fingerprint, accepted)
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
            if (symbols.source != "unsupported") {
                ConfigManager.markPostScanEnvironmentWarningPending(appCtx)
            }
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
        memoryCache.clear()
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
        if (!HookSymbolValidator.isUsable(symbols, cl)) {
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
            symbols.createdAt > 0L &&
            symbols.cacheSchemaVersion == HookSymbols.CACHE_SCHEMA_VERSION &&
            symbols.dexKitRuleVersion == HookSymbols.DEXKIT_RULE_VERSION
    }

    private inline fun <T> withScanContext(cl: ClassLoader, block: () -> T): T {
        val previous = HookSymbolScanSession.get()
        val context = HookSymbolScanContext(cl)
        HookSymbolScanSession.set(context)
        return try {
            block()
        } finally {
            context.close()
            HookSymbolScanSession.set(previous)
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

    private fun scanInternal(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols {
        val scanErrors = ArrayList<String>()
        HookSymbolScanSession.get()?.scanErrors = scanErrors
        val scanCandidates = ScanCandidateCollector.collect(context, cl, logger)
        val lifeLineCandidates = scanCandidates.obfuscated.ifEmpty { scanCandidates.all }
        val obfuscatedWithWhitelist = (lifeLineCandidates + SCAN_WHITELIST_CLASSES).distinct()
        val candidatesWithWhitelist = (scanCandidates.all + SCAN_WHITELIST_CLASSES).distinct()
        log(
            logger,
            "candidates=${candidatesWithWhitelist.size}, lifeLine=${lifeLineCandidates.size}, " +
                "obfuscated=${scanCandidates.obfuscated.size}, expanded=${scanCandidates.expanded.size}",
        )
        if (candidatesWithWhitelist.isEmpty()) {
            val unsupported = HookSymbols.unsupported(createdAt = System.currentTimeMillis())
            return unsupported.withFeatureStatusMap(HookFeatureStatusDeriver.derive(unsupported))
        }

        val settingsScan = runScanStep(
            "SettingsMenuHook",
            logger,
            scanErrors,
            SettingsScanSymbols(),
        ) {
            SettingsSymbolScanner.scan(lifeLineCandidates, cl, logger)
        }
        if (settingsScan.settingsClass == null) {
            log(logger, "settings match not found, SettingsMenuHook disabled, continuing remaining scan")
        }

        val homeTabScan = runScanStep(
            "HomeTabHook",
            logger,
            scanErrors,
            HomeTabScanSymbols(),
        ) {
            HomeTabSymbolScanner.scan(lifeLineCandidates, cl, logger)
        }

        // Dynamic obfuscated symbols used by ad and UI scans.
        var feedKeyMethod: String? = null
        var feedPayloadMethod: String? = null
        var feedLoadMoreMethod: String? = null
        var feedCardBindMethod: String? = null
        var feedCardBindMethodSpec: String? = null
        var feedCardDataListField: String? = null
        var feedHeadParamsField: String? = null
        var feedRecommendCardNestedDataMethod: String? = null
        var feedRecommendCardNestedDataListField: String? = null
        var replyServerResponseClass: String? = null
        var replyServerResponseDecodeMethod: String? = null
        var replyServerResponseResultJsonField: String? = null
        var agreeServerResponseClass: String? = null
        var agreeServerResponseDecodeLogicMethod: String? = null
        var replyVisibilityProbeScan = ReplyVisibilityProbeScanSymbols()
        var splashAdHelperClass: String? = null
        var splashAdHelperMethod: String? = null
        var closeAdDataClass: String? = null
        var closeAdDataMethodG1: String? = null
        var closeAdDataMethodJ1: String? = null
        var zgaClass: String? = null
        var zgaMethodsList: List<String>? = null
        var homePersonalizeAnchorClasses: List<String>? = null
        var pbFallingViewClass: String? = null
        var pbFallingInitMethod: String? = null
        var pbFallingShowMethod: String? = null
        var pbFallingClearMethod: String? = null
        var pbBottomEnterBarViewClass: String? = null
        var pbBottomEnterBarConstructorCount: Int? = null
        var pbBottomEnterBarRefreshMethodSpecs: List<String>? = null
        var pbEnterFrsAnimationTipViewClass: String? = null
        var pbEnterFrsAnimationTipConstructorCount: Int? = null
        var pbEnterFrsAnimationTipCallerClasses: List<String>? = null
        var pbHotTopicGuideTotalViewMethod: String? = null
        var pbHotTopicGuideRefreshMethodSpecs: List<String>? = null
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
        var forumPageAdScan = ForumPageAdScanSymbols()
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
        var plainUrlBrowserHelperClass: String? = null
        var plainUrlBrowserHelperStartWebActivityMethod: String? = null
        var plainUrlWebContainerActivityClass: String? = null
        var plainUrlWebContainerInitDataMethod: String? = null
        var plainUrlWebContainerWebViewClientClass: String? = null
        var plainUrlWebContainerShouldOverrideUrlLoadingMethod: String? = null
        var privateReadReceiptModelClass: String? = null
        var privateReadReceiptModelReadDispatchMethod: String? = null
        var privateReadReceiptMessageManagerClass: String? = null
        var privateReadReceiptMessageManagerGetInstanceMethod: String? = null
        var privateReadReceiptMessageManagerGetSocketClientMethod: String? = null
        var privateReadReceiptMessageSendMethod: String? = null
        var privateReadReceiptMessageBaseClass: String? = null
        var privateReadReceiptSocketClientClass: String? = null
        var privateReadReceiptSocketDuplicateCheckMethod: String? = null
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
        var mineTabWebViewClass: String? = null
        var mineTabWebLoadUrlMethod: String? = null
        var mineTabWebGetUrlMethod: String? = null
        var mineTabWebGetInnerWebViewMethod: String? = null
        var homeSideBarWebViewClass: String? = null
        var homeSideBarTbWebViewClass: String? = null
        var homeSideBarWebGetWebViewMethod: String? = null
        var homeSideBarWebGetUrlMethod: String? = null
        var homeSideBarWebGetInnerWebViewMethod: String? = null
        var homeSideBarWebLoadUrlMethods: List<String>? = null
        var autoSignInNetworkClass: String? = null
        var autoSignInNetworkConstructorSpec: String? = null
        var autoSignInNetworkAddPostDataMethod: String? = null
        var autoSignInNetworkPostNetDataMethod: String? = null
        var autoSignInNetworkSetNeedTbsMethod: String? = null
        var autoSignInNetworkSetNeedSigMethod: String? = null
        var autoSignInTbConfigClass: String? = null
        var autoSignInServerAddressField: String? = null
        var autoSignInCoreApplicationClass: String? = null
        var autoSignInCurrentAccountMethod: String? = null
        var autoSignInHybridProxyClass: String? = null
        var autoSignInHybridProxyConstructorSpec: String? = null
        var autoSignInHybridJsBridgeClass: String? = null
        var autoSignInHybridNativeNetworkProxyMethod: String? = null
        var autoSignInHybridTaskClass: String? = null
        var autoSignInHybridTaskConstructorSpec: String? = null
        var autoSignInHybridTaskDoInBackgroundMethod: String? = null
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
        var pbLikeAutoReplyAgreeViewClass: String? = null
        var pbLikeAutoReplyAgreeClickMethod: String? = null
        var pbLikeAutoReplyAgreeViewGetDataMethod: String? = null
        var pbLikeAutoReplyAgreeDataClass: String? = null
        var pbLikeAutoReplyAgreeDataHasAgreeField: String? = null
        var pbLikeAutoReplyAgreeDataAgreeTypeField: String? = null
        var pbLikeAutoReplyAgreeDataIsInThreadField: String? = null
        var pbLikeAutoReplyInputContainerClass: String? = null
        var pbLikeAutoReplyInputContainerGetInputViewMethod: String? = null
        var pbLikeAutoReplyInputContainerGetSendViewMethod: String? = null
        var collectionPresenterField: String? = null
        var collectionPresenterListSetterMethod: String? = null
        var collectionPresenterListSetterMethodSpec: String? = null
        var collectionPresenterAdapterField: String? = null
        var collectionModelField: String? = null
        var collectionModelListGetterMethod: String? = null
        var collectionModelListGetterMethodSpec: String? = null
        var collectionModelParseMethod: String? = null
        var collectionModelParseMethodSpec: String? = null
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
        var historyAdapterSetListMethodSpec: String? = null
        var historyListField: String? = null
        var historyActivityListUpdateMethod: String? = null
        var historyActivityListUpdateMethodSpec: String? = null
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
        var homeNativeGlassTopChromeTabSelectedMethodSpecs: List<String>? = null
        var homeNativeGlassSubPbSetNextPageMethod: String? = null
        var homeNativeGlassSubPbSetNextPageParamType: String? = null
        var homeNativeGlassSortSwitchBackgroundPaintField: String? = null
        var homeNativeGlassSortSwitchSlideDrawMethod: String? = null
        var homeNativeGlassSortSwitchSlidePathField: String? = null
        var homeNativeGlassEnterForumCapsuleControllerClass: String? = null
        var homeNativeGlassEnterForumCapsuleInitMethod: String? = null
        var homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null
        var homeNativeGlassEnterForumCapsuleViewField: String? = null
        var homeNativeGlassEnterForumCapsuleTitleField: String? = null
        var homeNativeGlassHostDarkModeMoreActivityClass: String? = null
        var homeNativeGlassHostDarkModeControllerField: String? = null
        var homeNativeGlassHostDarkModeSwitchGetterMethod: String? = null
        var homeNativeGlassHostDarkModeSwitchStateField: String? = null
        var homeNativeGlassHostDarkModeSwitchSetOnMethod: String? = null
        var homeNativeGlassHostDarkModeSwitchSetOffMethod: String? = null
        var homeNativeGlassHostDarkModeSwitchCallbackMethod: String? = null
        var pbCommonLayoutPreloaderGetOrDefaultMethod: String? = null
        var aiSpriteMemePanControllerClass: String? = null
        var aiSpriteMemeEnableMethod: String? = null
        var aiPbNewInputContainerClass: String? = null
        var aiPbNewInputContainerInitSpriteMemeMethod: String? = null
        var aiPbNewInputContainerInitAiWriteMethod: String? = null
        var aiPbAiEmojiCreationViewBindMethod: String? = null
        var aiPbPageBrowserAiEmojiCreationViewClass: String? = null
        var aiPbPageBrowserAiEmojiCreationBindMethod: String? = null
        var aiImageViewerJumpButtonOwnerClass: String? = null
        var aiImageViewerJumpButtonInitMethod: String? = null

        val homeItemScan = runScanStep(
            "HomeTabHook.Item",
            logger,
            scanErrors,
            HomeTabItemScanSymbols(),
        ) {
            if (homeTabScan.tabClass != null) {
                HomeTabItemSymbolScanner.scan(homeTabScan, cl, logger)
            } else {
                HomeTabItemScanSymbols()
            }
        }
        homeTabItemTypeField = homeItemScan.typeField
        homeTabItemCodeField = homeItemScan.codeField
        homeTabItemNameField = homeItemScan.nameField
        homeTabItemUrlField = homeItemScan.urlField
        homeTabItemMainSetterMethod = homeItemScan.mainSetterMethod
        homeTabItemMainIntField = homeItemScan.mainIntField
        homeTabItemMainBooleanField = homeItemScan.mainBooleanField

        val feedTemplateScan = runScanStep(
            "FeedTemplateHook",
            logger,
            scanErrors,
            FeedTemplateScanSymbols(),
        ) {
            FeedTemplateSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        feedKeyMethod = feedTemplateScan.keyMethod
        feedPayloadMethod = feedTemplateScan.payloadMethod
        feedLoadMoreMethod = feedTemplateScan.loadMoreMethod

        val feedCardScan = runScanStep(
            "FeedInfoLogHook.Bind",
            logger,
            scanErrors,
            FeedCardScanSymbols(),
        ) {
            FeedCardSymbolScanner.scanBind(candidatesWithWhitelist, cl, logger)
        }
        feedCardBindMethod = feedCardScan.bindMethod
        feedCardBindMethodSpec = feedCardScan.bindMethodSpec
        feedCardDataListField = feedCardScan.dataListField
        runScanStep("ReplyServerResponseLogHook", logger, scanErrors, Unit) {
            ReplyVisibilityProbeSymbolScanner.scanReplyServerResponseLog(cl, logger)?.let { scan ->
                replyServerResponseClass = scan.responseClass
                replyServerResponseDecodeMethod = scan.decodeMethod
                replyServerResponseResultJsonField = scan.resultJsonField
            }
        }
        runScanStep("AgreeServerResponseLogHook", logger, scanErrors, Unit) {
            ReplyVisibilityProbeSymbolScanner.scanAgreeServerResponseLog(cl, logger)?.let { scan ->
                agreeServerResponseClass = scan.responseClass
                agreeServerResponseDecodeLogicMethod = scan.decodeLogicMethod
            }
        }
        replyVisibilityProbeScan = runScanStep(
            "ReplyVisibilityProbeHook",
            logger,
            scanErrors,
            ReplyVisibilityProbeScanSymbols(),
        ) {
            ReplyVisibilityProbeSymbolScanner.scan(cl, logger)
        }

        val feedHeadParamsScan = runScanStep(
            "CustomPostCardBlockHook.FeedHeadParams",
            logger,
            scanErrors,
            FeedCardScanSymbols(),
        ) {
            FeedCardSymbolScanner.scanHeadParams(candidatesWithWhitelist, cl, logger)
        }
        feedHeadParamsField = feedHeadParamsScan.feedHeadParamsField

        val recommendCardNestedDataScan = runScanStep(
            "CustomPostCardBlockHook.RecommendCard",
            logger,
            scanErrors,
            RecommendCardNestedDataScanSymbols(),
        ) {
            RecommendCardSymbolScanner.scanNestedData(cl, logger)
        }
        feedRecommendCardNestedDataMethod = recommendCardNestedDataScan.nestedDataMethod
        feedRecommendCardNestedDataListField = recommendCardNestedDataScan.nestedDataListField

        val strategyAdScan = runScanStep(
            "StrategyAdHook",
            logger,
            scanErrors,
            StrategyAdScanSymbols(),
        ) {
            StrategyAdSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        splashAdHelperClass = strategyAdScan.splashAdHelperClass
        splashAdHelperMethod = strategyAdScan.splashAdHelperMethod
        closeAdDataClass = strategyAdScan.closeAdDataClass
        closeAdDataMethodG1 = strategyAdScan.closeAdDataMethodG1
        closeAdDataMethodJ1 = strategyAdScan.closeAdDataMethodJ1
        zgaClass = strategyAdScan.zgaClass
        zgaMethodsList = strategyAdScan.zgaMethods

        val homeHeaderScan = runScanStep(
            "HomeHeaderHooks",
            logger,
            scanErrors,
            HomeHeaderScanSymbols(),
        ) {
            HomeHeaderSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        val pbFallingScan = runScanStep(
            "PbFallingAdHook",
            logger,
            scanErrors,
            PbFallingScanSymbols(),
        ) {
            PbFallingSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        pbFallingViewClass = pbFallingScan.viewClass
        pbFallingInitMethod = pbFallingScan.initMethod
        pbFallingShowMethod = pbFallingScan.showMethod
        pbFallingClearMethod = pbFallingScan.clearMethod

        val pbBottomEnterBarScan = runScanStep(
            "PbBottomEnterBarHook",
            logger,
            scanErrors,
            PbBottomEnterBarScanSymbols(),
        ) {
            PbBottomEnterBarSymbolScanner.scan(cl, logger)
        }
        pbBottomEnterBarViewClass = pbBottomEnterBarScan.bottomEnterBarViewClass
        pbBottomEnterBarConstructorCount = pbBottomEnterBarScan.bottomEnterBarConstructorCount
        pbBottomEnterBarRefreshMethodSpecs =
            pbBottomEnterBarScan.bottomEnterBarRefreshMethodSpecs.takeIf { it.isNotEmpty() }
        pbEnterFrsAnimationTipViewClass = pbBottomEnterBarScan.enterFrsAnimationTipViewClass
        pbEnterFrsAnimationTipConstructorCount = pbBottomEnterBarScan.enterFrsAnimationTipConstructorCount
        pbEnterFrsAnimationTipCallerClasses =
            pbBottomEnterBarScan.enterFrsAnimationTipCallerClasses.takeIf { it.isNotEmpty() }
        pbHotTopicGuideTotalViewMethod = pbBottomEnterBarScan.hotTopicGuideTotalViewMethod
        pbHotTopicGuideRefreshMethodSpecs =
            pbBottomEnterBarScan.hotTopicGuideRefreshMethodSpecs.takeIf { it.isNotEmpty() }

        val pbEarlyAdInsertScan = runScanStep(
            "PbEarlyAdBlockHook",
            logger,
            scanErrors,
            PbEarlyAdInsertScanSymbols(null, emptyList()),
        ) {
            PbEarlyAdInsertSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
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
            PbAdBidSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
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
            PostAdDataFilterSymbolScanner.scan(cl, logger)
        }
        typeAdapterSetDataMethod = typeAdapterDataFilterScan.setDataMethod
        typeAdapterDataItemClass = typeAdapterDataFilterScan.dataItemClass
        typeAdapterDataGetTypeMethod = typeAdapterDataFilterScan.dataGetTypeMethod

        forumPageAdScan = runScanStep(
            "ForumPageAdBlockHook",
            logger,
            scanErrors,
            ForumPageAdScanSymbols(),
        ) {
            ForumPageAdSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
        }

        val enterForumWebScan = runScanStep(
            "EnterForumWebHook",
            logger,
            scanErrors,
            EnterForumWebScanSymbols(),
        ) {
            EnterForumWebSymbolScanner.scan(obfuscatedWithWhitelist, cl, logger)
        }
        enterForumWebControllerClass = enterForumWebScan.controllerClass
        enterForumWebLoadMethod = enterForumWebScan.webLoadMethod
        enterForumInitInfoDataClass = enterForumWebScan.initInfoDataClass
        enterForumInitInfoGetUrlMethod = enterForumWebScan.initInfoGetUrlMethod

        val mineTabWebBlockScan = runScanStep(
            "MineTabWebBlockHook",
            logger,
            scanErrors,
            MineTabWebBlockScanSymbols(),
        ) {
            WebAdBlockSymbolScanner.scanMineTab(cl, logger)
        }
        mineTabWebViewClass = mineTabWebBlockScan.webViewClass
        mineTabWebLoadUrlMethod = mineTabWebBlockScan.loadUrlMethod
        mineTabWebGetUrlMethod = mineTabWebBlockScan.getUrlMethod
        mineTabWebGetInnerWebViewMethod = mineTabWebBlockScan.getInnerWebViewMethod

        val homeSideBarWebBlockScan = runScanStep(
            "HomeSideBarWebBlockHook",
            logger,
            scanErrors,
            HomeSideBarWebBlockScanSymbols(),
        ) {
            WebAdBlockSymbolScanner.scanHomeSideBar(cl, logger)
        }
        homeSideBarWebViewClass = homeSideBarWebBlockScan.sideBarWebViewClass
        homeSideBarTbWebViewClass = homeSideBarWebBlockScan.tbWebViewClass
        homeSideBarWebGetWebViewMethod = homeSideBarWebBlockScan.getWebViewMethod
        homeSideBarWebGetUrlMethod = homeSideBarWebBlockScan.getUrlMethod
        homeSideBarWebGetInnerWebViewMethod = homeSideBarWebBlockScan.getInnerWebViewMethod
        homeSideBarWebLoadUrlMethods = homeSideBarWebBlockScan.loadUrlMethods.takeIf { it.isNotEmpty() }

        val autoSignInScan = runScanStep(
            "AutoSignInManager",
            logger,
            scanErrors,
            AutoSignInScanSymbols(),
        ) {
            AutoSignInSymbolScanner.scan(cl, logger)
        }
        autoSignInNetworkClass = autoSignInScan.networkClass
        autoSignInNetworkConstructorSpec = autoSignInScan.networkConstructorSpec
        autoSignInNetworkAddPostDataMethod = autoSignInScan.addPostDataMethod
        autoSignInNetworkPostNetDataMethod = autoSignInScan.postNetDataMethod
        autoSignInNetworkSetNeedTbsMethod = autoSignInScan.setNeedTbsMethod
        autoSignInNetworkSetNeedSigMethod = autoSignInScan.setNeedSigMethod
        autoSignInTbConfigClass = autoSignInScan.tbConfigClass
        autoSignInServerAddressField = autoSignInScan.serverAddressField
        autoSignInCoreApplicationClass = autoSignInScan.coreApplicationClass
        autoSignInCurrentAccountMethod = autoSignInScan.currentAccountMethod
        autoSignInHybridProxyClass = autoSignInScan.hybridProxyClass
        autoSignInHybridProxyConstructorSpec = autoSignInScan.hybridProxyConstructorSpec
        autoSignInHybridJsBridgeClass = autoSignInScan.hybridJsBridgeClass
        autoSignInHybridNativeNetworkProxyMethod = autoSignInScan.hybridNativeNetworkProxyMethod
        autoSignInHybridTaskClass = autoSignInScan.hybridTaskClass
        autoSignInHybridTaskConstructorSpec = autoSignInScan.hybridTaskConstructorSpec
        autoSignInHybridTaskDoInBackgroundMethod = autoSignInScan.hybridTaskDoInBackgroundMethod

        val plainUrlSpanScan = runScanStep(
            "PlainUrlDirectBrowserHook.Direct",
            logger,
            scanErrors,
            PlainUrlClickableSpanScanSymbols(),
        ) {
            PlainUrlClickableSpanSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
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
            PlainUrlMessageSymbolScanner.scan(cl, logger)
        }
        plainUrlMessageManagerClass = plainUrlMessageScan.messageManagerClass
        plainUrlMessageDispatchMethod = plainUrlMessageScan.dispatchMethod
        plainUrlResponsedMessageClass = plainUrlMessageScan.responsedMessageClass
        plainUrlResponsedMessageGetCmdMethod = plainUrlMessageScan.getCmdMethod
        plainUrlCustomResponsedMessageClass = plainUrlMessageScan.customResponsedMessageClass
        plainUrlCustomResponsedMessageGetDataMethod = plainUrlMessageScan.getDataMethod
        plainUrlApplicationClass = plainUrlMessageScan.applicationClass
        plainUrlApplicationGetInstMethod = plainUrlMessageScan.getInstMethod

        val plainUrlBrowserHelperScan = runScanStep(
            "PlainUrlDirectBrowserHook.BrowserHelper",
            logger,
            scanErrors,
            PlainUrlBrowserHelperScanSymbols(),
        ) {
            PlainUrlBrowserHelperSymbolScanner.scan(cl, logger)
        }
        plainUrlBrowserHelperClass = plainUrlBrowserHelperScan.browserHelperClass
        plainUrlBrowserHelperStartWebActivityMethod = plainUrlBrowserHelperScan.startWebActivityMethod

        val plainUrlWebContainerScan = runScanStep(
            "PlainUrlDirectBrowserHook.WebContainer",
            logger,
            scanErrors,
            PlainUrlWebContainerScanSymbols(),
        ) {
            PlainUrlWebContainerSymbolScanner.scan(cl, logger)
        }
        plainUrlWebContainerActivityClass = plainUrlWebContainerScan.webContainerActivityClass
        plainUrlWebContainerInitDataMethod = plainUrlWebContainerScan.initDataMethod
        plainUrlWebContainerWebViewClientClass = plainUrlWebContainerScan.webViewClientClass
        plainUrlWebContainerShouldOverrideUrlLoadingMethod =
            plainUrlWebContainerScan.shouldOverrideUrlLoadingMethod

        val privateReadReceiptScan = runScanStep(
            "PrivateReadReceiptBlockHook",
            logger,
            scanErrors,
            PrivateReadReceiptScanSymbols(),
        ) {
            PrivateReadReceiptSymbolScanner.scan(cl, logger)
        }
        privateReadReceiptModelClass = privateReadReceiptScan.modelClass
        privateReadReceiptModelReadDispatchMethod = privateReadReceiptScan.modelReadDispatchMethod
        privateReadReceiptMessageManagerClass = privateReadReceiptScan.messageManagerClass
        privateReadReceiptMessageManagerGetInstanceMethod = privateReadReceiptScan.messageManagerGetInstanceMethod
        privateReadReceiptMessageManagerGetSocketClientMethod =
            privateReadReceiptScan.messageManagerGetSocketClientMethod
        privateReadReceiptMessageSendMethod = privateReadReceiptScan.messageSendMethod
        privateReadReceiptMessageBaseClass = privateReadReceiptScan.messageBaseClass
        privateReadReceiptSocketClientClass = privateReadReceiptScan.socketClientClass
        privateReadReceiptSocketDuplicateCheckMethod = privateReadReceiptScan.socketDuplicateCheckMethod
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
            MountCardLinkSymbolScanner.scan(cl, logger)
        }
        mountCardLinkLayoutClass = mountCardLinkScan.layoutClass
        mountCardLinkLayoutOnClickMethod = mountCardLinkScan.onClickMethod
        mountCardLinkLayoutDataField = mountCardLinkScan.dataField
        mountCardLinkInfoDataClass = mountCardLinkScan.dataClass
        mountCardLinkInfoGetUrlMethod = mountCardLinkScan.getUrlMethod

        val forumBottomSheetScan = runScanStep(
            "ForumNativeTopShiftBlockHook",
            logger,
            scanErrors,
            ForumBottomSheetScanSymbols(),
        ) {
            ForumBottomSheetSymbolScanner.scan(cl, logger)
        }
        forumBottomSheetViewClass = forumBottomSheetScan.viewClass
        forumBottomSheetInitScrollMethod = forumBottomSheetScan.initScrollMethod

        autoRefreshTriggerMethod = runScanStep(
            "AutoRefreshHook",
            logger,
            scanErrors,
            null as String?,
        ) {
            AutoRefreshSymbolScanner.scan(context, cl, logger)
        }

        val autoLoadMoreScan = runScanStep(
            "AutoLoadMoreHook.Config",
            logger,
            scanErrors,
            AutoLoadMoreConfigScanSymbols(),
        ) {
            AutoRefreshSymbolScanner.scanLoadMoreConfig(cl, logger)
        }
        autoLoadMoreConfigClass = autoLoadMoreScan.configClass
        autoLoadMoreConfigMethod = autoLoadMoreScan.configMethod

        val pbCommentInteractionScan = runScanStep(
            "PbCommentInteractionHooks",
            logger,
            scanErrors,
            PbCommentInteractionScanSymbols(),
        ) {
            PbCommentInteractionSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        pbCommentScrollListenerClass = pbCommentInteractionScan.scrollListenerClass
        pbCommentScrollMethod = pbCommentInteractionScan.scrollMethod
        pbCommentScrollFragmentField = pbCommentInteractionScan.scrollFragmentField
        pbCommentScrollBottomListenerField = pbCommentInteractionScan.scrollBottomListenerField
        pbCommentScrollBottomMethod = pbCommentInteractionScan.scrollBottomMethod
        pbCommentBottomListScrollClass = pbCommentInteractionScan.bottomListScrollClass
        pbCommentBottomListScrollMethod = pbCommentInteractionScan.bottomListScrollMethod
        pbCommentBottomListOwnerField = pbCommentInteractionScan.bottomListOwnerField
        pbCommentBottomRecyclerScrollClass = pbCommentInteractionScan.bottomRecyclerScrollClass
        pbCommentBottomRecyclerScrollMethod = pbCommentInteractionScan.bottomRecyclerScrollMethod
        pbCommentBottomRecyclerOwnerField = pbCommentInteractionScan.bottomRecyclerOwnerField
        pbGestureScaleManagerClass = pbCommentInteractionScan.gestureScaleManagerClass
        pbGestureScaleDispatchMethod = pbCommentInteractionScan.gestureScaleDispatchMethod
        pbGestureScaleListenerSetterMethod = pbCommentInteractionScan.gestureScaleListenerSetterMethod
        pbGestureScaleListenerClass = pbCommentInteractionScan.gestureScaleListenerClass
        pbGestureScaleListenerOnScaleMethod = pbCommentInteractionScan.gestureScaleListenerOnScaleMethod

        val pbLikeAutoReplyScan = runScanStep(
            "PbLikeAutoReplyHook",
            logger,
            scanErrors,
            PbLikeAutoReplyScanSymbols(),
        ) {
            PbLikeAutoReplySymbolScanner.scan(context, cl, logger)
        }
        pbLikeAutoReplyAgreeViewClass = pbLikeAutoReplyScan.agreeViewClass
        pbLikeAutoReplyAgreeClickMethod = pbLikeAutoReplyScan.agreeClickMethod
        pbLikeAutoReplyAgreeViewGetDataMethod = pbLikeAutoReplyScan.agreeViewGetDataMethod
        pbLikeAutoReplyAgreeDataClass = pbLikeAutoReplyScan.agreeDataClass
        pbLikeAutoReplyAgreeDataHasAgreeField = pbLikeAutoReplyScan.agreeDataHasAgreeField
        pbLikeAutoReplyAgreeDataAgreeTypeField = pbLikeAutoReplyScan.agreeDataAgreeTypeField
        pbLikeAutoReplyAgreeDataIsInThreadField = pbLikeAutoReplyScan.agreeDataIsInThreadField
        pbLikeAutoReplyInputContainerClass = pbLikeAutoReplyScan.inputContainerClass
        pbLikeAutoReplyInputContainerGetInputViewMethod = pbLikeAutoReplyScan.inputContainerGetInputViewMethod
        pbLikeAutoReplyInputContainerGetSendViewMethod = pbLikeAutoReplyScan.inputContainerGetSendViewMethod

        val imageViewerShareScan = runScanStep(
            "ImageViewerShareHooks",
            logger,
            scanErrors,
            ImageViewerShareScanSymbols(),
        ) {
            ImageViewerShareSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
        }
        shareTrackBuilderClass = imageViewerShareScan.shareTrackBuilderClass
        shareTrackBuildUrlMethod = imageViewerShareScan.shareTrackBuildUrlMethod
        shareTrackAppendQueryMethod = imageViewerShareScan.shareTrackAppendQueryMethod
        imageViewerShareItemClass = imageViewerShareScan.itemClass
        imageViewerShareItemTitleField = imageViewerShareScan.itemTitleField
        imageViewerShareItemContentField = imageViewerShareScan.itemContentField
        imageViewerShareItemLinkUrlField = imageViewerShareScan.itemLinkUrlField
        imageViewerShareItemImageUriField = imageViewerShareScan.itemImageUriField
        imageViewerShareItemImageUrlField = imageViewerShareScan.itemImageUrlField
        imageViewerShareItemLocalFileField = imageViewerShareScan.itemLocalFileField
        imageViewerShareConfigClass = imageViewerShareScan.configClass
        imageViewerShareAddOutsideMethod = imageViewerShareScan.addOutsideMethod
        imageViewerShareGetRequestDataMethod = imageViewerShareScan.getRequestDataMethod
        imageViewerShareSetRequestDataMethod = imageViewerShareScan.setRequestDataMethod
        imageViewerShareGetContextMethod = imageViewerShareScan.getContextMethod
        imageViewerShareIsDialogField = imageViewerShareScan.isDialogField
        imageViewerShareItemField = imageViewerShareScan.itemField
        imageViewerShareItemViewClass = imageViewerShareScan.itemViewClass
        imageViewerShareItemNameByResMethod = imageViewerShareScan.itemNameByResMethod
        imageViewerShareItemNameByTextMethod = imageViewerShareScan.itemNameByTextMethod
        imageViewerShareIconResId = imageViewerShareScan.iconResId

        val anchorCandidates = listOf(
            StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS,
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
            HomeNativeGlassSymbolScanner.scanResourceIds(context, cl, logger)
        }
        homeNativeGlassSubPbNextPageMoreViewId = homeNativeGlassResourceIds.subPbNextPageMoreViewId
        homeNativeGlassPbReplyTitleDividerViewId = homeNativeGlassResourceIds.pbReplyTitleDividerViewId
        val homeNativeGlassDynamicBackgroundColorIds = homeNativeGlassResourceIds.dynamicBackgroundColorIds

        val homeNativeGlassTopChromeSymbols = runScanStep(
            "HomeNativeGlassHook.TopChrome",
            logger,
            scanErrors,
            HomeNativeGlassTopChromeSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanTopChrome(cl, logger)
        }
        homeNativeGlassTopChromeTabSelectedMethodSpecs =
            homeNativeGlassTopChromeSymbols.tabSelectedMethodSpecs.takeIf { it.isNotEmpty() }

        val homeNativeGlassSubPbNextPageSymbols = runScanStep(
            "HomeNativeGlassHook.SubPbNextPage",
            logger,
            scanErrors,
            HomeNativeGlassSubPbNextPageSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanSubPbNextPage(cl, logger)
        }
        homeNativeGlassSubPbSetNextPageMethod =
            homeNativeGlassSubPbNextPageSymbols.methodName
        homeNativeGlassSubPbSetNextPageParamType =
            homeNativeGlassSubPbNextPageSymbols.parameterTypeName

        val homeNativeGlassSortSwitchSymbols = runScanStep(
            "HomeNativeGlassHook.SortSwitch",
            logger,
            scanErrors,
            HomeNativeGlassSortSwitchSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanSortSwitch(cl, logger)
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
            HomeNativeGlassSymbolScanner.scanEnterForumCapsule(context, candidatesWithWhitelist, cl, logger)
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

        val homeNativeGlassHostDarkModeSwitchSymbols = runScanStep(
            "HomeNativeGlassHook.HostDarkModeSwitch",
            logger,
            scanErrors,
            HomeNativeGlassHostDarkModeSwitchSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanHostDarkModeSwitch(context, cl, logger)
        }
        homeNativeGlassHostDarkModeMoreActivityClass =
            homeNativeGlassHostDarkModeSwitchSymbols.moreActivityClass
        homeNativeGlassHostDarkModeControllerField =
            homeNativeGlassHostDarkModeSwitchSymbols.controllerField
        homeNativeGlassHostDarkModeSwitchGetterMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchGetterMethod
        homeNativeGlassHostDarkModeSwitchStateField =
            homeNativeGlassHostDarkModeSwitchSymbols.switchStateField
        homeNativeGlassHostDarkModeSwitchSetOnMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchSetOnMethod
        homeNativeGlassHostDarkModeSwitchSetOffMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchSetOffMethod
        homeNativeGlassHostDarkModeSwitchCallbackMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchCallbackMethod

        pbCommonLayoutPreloaderGetOrDefaultMethod = runScanStep(
            "HomeNativeGlassHook.CommonLayoutPreloader",
            logger,
            scanErrors,
            null as String?,
        ) {
            HomeNativeGlassSymbolScanner.scanPbCommonLayoutPreloaderGetOrDefaultMethod(cl, logger)
        }

        val collectionScan = runScanStep(
            "CollectionSearchHook",
            logger,
            scanErrors,
            CollectionSearchScanSymbols(),
        ) {
            CollectionSearchSymbolScanner.scan(cl, logger)
        }
        collectionPresenterField = collectionScan.presenterField
        collectionPresenterListSetterMethod = collectionScan.presenterListSetterMethod
        collectionPresenterListSetterMethodSpec = collectionScan.presenterListSetterMethodSpec
        collectionPresenterAdapterField = collectionScan.presenterAdapterField
        collectionModelField = collectionScan.modelField
        collectionModelListGetterMethod = collectionScan.modelListGetterMethod
        collectionModelListGetterMethodSpec = collectionScan.modelListGetterMethodSpec
        collectionModelParseMethod = collectionScan.modelParseMethod
        collectionModelParseMethodSpec = collectionScan.modelParseMethodSpec
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
            HistorySearchSymbolScanner.scan(cl, logger)
        }
        historyAdapterField = historyScan.adapterField
        historyAdapterSetListMethod = historyScan.adapterSetListMethod
        historyAdapterSetListMethodSpec = historyScan.adapterSetListMethodSpec
        historyListField = historyScan.listField
        historyActivityListUpdateMethod = historyScan.activityListUpdateMethod
        historyActivityListUpdateMethodSpec = historyScan.activityListUpdateMethodSpec
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
            MsgTabSymbolScanner.scan(cl, logger)
        }
        msgTabLocateToTabMethod = msgTabScan.locateToTabMethod
        msgTabContainerSelectMethod = msgTabScan.containerSelectMethod
        msgTabContainerExtDataField = msgTabScan.containerExtDataField

        val freeCopyPopupScan = runScanStep(
            "FreeCopyHook.Popup",
            logger,
            scanErrors,
            FreeCopyPopupScanSymbols(),
        ) {
            FreeCopyPopupSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        freeCopyPopupMenuClass = freeCopyPopupScan.menuClass
        freeCopyPopupContentViewMethod = freeCopyPopupScan.contentViewMethod
        freeCopyPopupTextField = freeCopyPopupScan.textField

        val mainTabBottomScan = runScanStep(
            "MainTabBottomHook",
            logger,
            scanErrors,
            MainTabBottomScanSymbols(),
        ) {
            MainTabBottomSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
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
            OriginalImageSymbolScanner.scan(cl, logger)
        }
        val aiComponentScan = runScanStep(
            "AiComponentDisableHook",
            logger,
            scanErrors,
            AiComponentScanSymbols(),
        ) {
            AiComponentSymbolScanner.scan(context, cl, candidatesWithWhitelist, logger)
        }
        aiSpriteMemePanControllerClass = aiComponentScan.spriteMemePanControllerClass
        aiSpriteMemeEnableMethod = aiComponentScan.spriteMemeEnableMethod
        aiPbNewInputContainerClass = aiComponentScan.pbNewInputContainerClass
        aiPbNewInputContainerInitSpriteMemeMethod = aiComponentScan.pbInitSpriteMemeMethod
        aiPbNewInputContainerInitAiWriteMethod = aiComponentScan.pbInitAiWriteMethod
        aiPbAiEmojiCreationViewBindMethod = aiComponentScan.pbAiEmojiCreationViewBindMethod
        aiPbPageBrowserAiEmojiCreationViewClass = aiComponentScan.pbPageBrowserAiEmojiCreationViewClass
        aiPbPageBrowserAiEmojiCreationBindMethod = aiComponentScan.pbPageBrowserAiEmojiCreationBindMethod
        aiImageViewerJumpButtonOwnerClass = aiComponentScan.imageViewerJumpButtonOwnerClass
        aiImageViewerJumpButtonInitMethod = aiComponentScan.imageViewerJumpButtonInitMethod
        val scanned = buildHookSymbols {
            settingsClass = settingsScan.settingsClass
            settingsInitMethod = settingsScan.initMethod
            settingsContainerField = settingsScan.containerField
            homeTabClass = homeTabScan.tabClass
            homeTabRebuildMethod = homeTabScan.rebuildMethod
            homeTabListField = homeTabScan.listField
            this.homeTabItemTypeField = homeTabItemTypeField
            this.homeTabItemCodeField = homeTabItemCodeField
            this.homeTabItemNameField = homeTabItemNameField
            this.homeTabItemUrlField = homeTabItemUrlField
            this.homeTabItemMainSetterMethod = homeTabItemMainSetterMethod
            this.homeTabItemMainIntField = homeTabItemMainIntField
            this.homeTabItemMainBooleanField = homeTabItemMainBooleanField
            feedTemplateKeyMethod = feedKeyMethod
            feedTemplatePayloadMethod = feedPayloadMethod
            feedTemplateLoadMoreMethod = feedLoadMoreMethod
            this.feedCardBindMethod = feedCardBindMethod
            this.feedCardBindMethodSpec = feedCardBindMethodSpec
            this.feedCardDataListField = feedCardDataListField
            this.feedHeadParamsField = feedHeadParamsField
            this.feedRecommendCardNestedDataMethod = feedRecommendCardNestedDataMethod
            this.feedRecommendCardNestedDataListField = feedRecommendCardNestedDataListField
            this.replyServerResponseClass = replyServerResponseClass
            this.replyServerResponseDecodeMethod = replyServerResponseDecodeMethod
            this.replyServerResponseResultJsonField = replyServerResponseResultJsonField
            this.agreeServerResponseClass = agreeServerResponseClass
            this.agreeServerResponseDecodeLogicMethod = agreeServerResponseDecodeLogicMethod
            this.replyVisibilityProbeReplyResponseClass = replyVisibilityProbeScan.replyResponseClass
            this.replyVisibilityProbeReplyDecodeMethod = replyVisibilityProbeScan.replyDecodeMethod
            this.replyVisibilityProbeReplyResultJsonField = replyVisibilityProbeScan.replyResultJsonField
            this.replyVisibilityProbeAddPostRequestClass = replyVisibilityProbeScan.addPostRequestClass
            this.replyVisibilityProbeAddPostRequestDataField = replyVisibilityProbeScan.addPostRequestDataField
            this.replyVisibilityProbeResponsedMessageClass = replyVisibilityProbeScan.responsedMessageClass
            this.replyVisibilityProbeGetOriginalMessageMethod = replyVisibilityProbeScan.getOriginalMessageMethod
            this.replyVisibilityProbeMessageClass = replyVisibilityProbeScan.messageClass
            this.replyVisibilityProbeMessageGetExtraMethod = replyVisibilityProbeScan.messageGetExtraMethod
            this.replyVisibilityProbeMessageGetTagMethod = replyVisibilityProbeScan.messageGetTagMethod
            this.replyVisibilityProbeMessageSetTagMethod = replyVisibilityProbeScan.messageSetTagMethod
            this.replyVisibilityProbeHttpMessageClass = replyVisibilityProbeScan.httpMessageClass
            this.replyVisibilityProbeHttpMessageConstructor = replyVisibilityProbeScan.httpMessageConstructor
            this.replyVisibilityProbeHttpMessageAddParamMethod = replyVisibilityProbeScan.httpMessageAddParamMethod
            this.replyVisibilityProbeHttpMessageAddHeaderMethod = replyVisibilityProbeScan.httpMessageAddHeaderMethod
            this.replyVisibilityProbeMessageManagerClass = replyVisibilityProbeScan.messageManagerClass
            this.replyVisibilityProbeMessageManagerGetInstanceMethod =
                replyVisibilityProbeScan.messageManagerGetInstanceMethod
            this.replyVisibilityProbeMessageManagerFindTaskMethod =
                replyVisibilityProbeScan.messageManagerFindTaskMethod
            this.replyVisibilityProbeMessageManagerRegisterTaskMethod =
                replyVisibilityProbeScan.messageManagerRegisterTaskMethod
            this.replyVisibilityProbeMessageManagerSendMethod = replyVisibilityProbeScan.messageManagerSendMethod
            this.replyVisibilityProbeTbHttpMessageTaskClass = replyVisibilityProbeScan.tbHttpMessageTaskClass
            this.replyVisibilityProbeTbHttpMessageTaskConstructor =
                replyVisibilityProbeScan.tbHttpMessageTaskConstructor
            this.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod =
                replyVisibilityProbeScan.httpMessageTaskSetResponsedClassMethod
            this.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod =
                replyVisibilityProbeScan.tbHttpMessageTaskSetIsNeedTbsMethod
            this.replyVisibilityProbeBdUniqueIdClass = replyVisibilityProbeScan.bdUniqueIdClass
            this.replyVisibilityProbeBdUniqueIdGenMethod = replyVisibilityProbeScan.bdUniqueIdGenMethod
            this.replyVisibilityProbeTbadkCoreApplicationClass =
                replyVisibilityProbeScan.tbadkCoreApplicationClass
            this.replyVisibilityProbeTbadkCoreApplicationGetInstMethod =
                replyVisibilityProbeScan.tbadkCoreApplicationGetInstMethod
            this.replyVisibilityProbeTbadkCoreApplicationGetZidMethod =
                replyVisibilityProbeScan.tbadkCoreApplicationGetZidMethod
            this.replyVisibilityProbeTbConfigClass = replyVisibilityProbeScan.tbConfigClass
            this.replyVisibilityProbeTbConfigServerAddressField =
                replyVisibilityProbeScan.tbConfigServerAddressField
            this.replyVisibilityProbeTbConfigPbFloorAgreeUrlField =
                replyVisibilityProbeScan.tbConfigPbFloorAgreeUrlField
            this.replyVisibilityProbeCmdConfigHttpClass = replyVisibilityProbeScan.cmdConfigHttpClass
            this.replyVisibilityProbeCmdPbFloorAgreeField = replyVisibilityProbeScan.cmdPbFloorAgreeField
            this.replyVisibilityProbeAgreeResponseClass = replyVisibilityProbeScan.agreeResponseClass
            this.replyVisibilityProbeAgreeDecodeLogicMethod = replyVisibilityProbeScan.agreeDecodeLogicMethod
            this.splashAdHelperClass = splashAdHelperClass
            this.splashAdHelperMethod = splashAdHelperMethod
            this.closeAdDataClass = closeAdDataClass
            this.closeAdDataMethodG1 = closeAdDataMethodG1
            this.closeAdDataMethodJ1 = closeAdDataMethodJ1
            this.zgaClass = zgaClass
            zgaMethods = zgaMethodsList
            applyHomeHeaderScan(homeHeaderScan)
            this.homePersonalizeAnchorClasses = homePersonalizeAnchorClasses
            this.pbFallingViewClass = pbFallingViewClass
            this.pbFallingInitMethod = pbFallingInitMethod
            this.pbFallingShowMethod = pbFallingShowMethod
            this.pbFallingClearMethod = pbFallingClearMethod
            this.pbBottomEnterBarViewClass = pbBottomEnterBarViewClass
            this.pbBottomEnterBarConstructorCount = pbBottomEnterBarConstructorCount
            this.pbBottomEnterBarRefreshMethodSpecs = pbBottomEnterBarRefreshMethodSpecs
            this.pbEnterFrsAnimationTipViewClass = pbEnterFrsAnimationTipViewClass
            this.pbEnterFrsAnimationTipConstructorCount = pbEnterFrsAnimationTipConstructorCount
            this.pbEnterFrsAnimationTipCallerClasses = pbEnterFrsAnimationTipCallerClasses
            this.pbHotTopicGuideTotalViewMethod = pbHotTopicGuideTotalViewMethod
            this.pbHotTopicGuideRefreshMethodSpecs = pbHotTopicGuideRefreshMethodSpecs
            this.pbEarlyAdInsertClass = pbEarlyAdInsertClass
            this.pbEarlyAdInsertMethodSpecs = pbEarlyAdInsertMethodSpecs
            this.pbAdBidCommonRequestModelClass = pbAdBidCommonRequestModelClass
            this.pbAdBidCommonRequestStartMethods = pbAdBidCommonRequestStartMethods
            this.pbAdBidCommonRequestNotifyMethod = pbAdBidCommonRequestNotifyMethod
            this.pbAdBidPageBrowserRequestModelClass = pbAdBidPageBrowserRequestModelClass
            this.pbAdBidPageBrowserRequestDataMethod = pbAdBidPageBrowserRequestDataMethod
            this.typeAdapterSetDataMethod = typeAdapterSetDataMethod
            this.typeAdapterDataItemClass = typeAdapterDataItemClass
            this.typeAdapterDataGetTypeMethod = typeAdapterDataGetTypeMethod
            this.forumResponseDataClass = forumPageAdScan.responseDataClass
            this.forumResponseParserMethod = forumPageAdScan.responseParserMethod
            this.forumResponseAdFields = forumPageAdScan.responseAdFields.takeIf { it.isNotEmpty() }
            this.forumPageMapperClass = forumPageAdScan.mapperClass
            this.forumBottomDataMapperMethod = forumPageAdScan.bottomDataMapperMethod
            this.forumBottomDataClass = forumPageAdScan.bottomDataClass
            this.forumBusinessPromotSetterMethod = forumPageAdScan.businessPromotSetterMethod
            this.forumPrivatePopSetterMethod = forumPageAdScan.privatePopSetterMethod
            this.forumSpriteBubbleSetterMethod = forumPageAdScan.spriteBubbleSetterMethod
            this.forumMaskPopSetterMethod = forumPageAdScan.maskPopSetterMethod
            this.forumBottomGameBarMapperMethod = forumPageAdScan.bottomGameBarMapperMethod
            this.forumHeaderDataMapperMethod = forumPageAdScan.headerDataMapperMethod
            this.forumHeaderDataClass = forumPageAdScan.headerDataClass
            this.forumRainDataClass = forumPageAdScan.rainDataClass
            this.forumRainSetterMethod = forumPageAdScan.rainSetterMethod
            this.forumDialogControllerClass = forumPageAdScan.dialogControllerClass
            this.forumBusinessPromotShowMethod = forumPageAdScan.businessPromotShowMethod
            this.forumAnimationShowMethod = forumPageAdScan.animationShowMethod
            this.forumGameFloatingBarControllerClass = forumPageAdScan.gameFloatingBarControllerClass
            this.forumGameFloatingBarShowMethod = forumPageAdScan.gameFloatingBarShowMethod
            this.forumGameFloatingBarField = forumPageAdScan.gameFloatingBarField
            this.forumBusinessPromotBizClass = forumPageAdScan.businessPromotBizClass
            this.forumBusinessPromotJumpMethod = forumPageAdScan.businessPromotJumpMethod
            this.enterForumWebControllerClass = enterForumWebControllerClass
            this.enterForumWebLoadMethod = enterForumWebLoadMethod
            this.enterForumInitInfoDataClass = enterForumInitInfoDataClass
            this.enterForumInitInfoGetUrlMethod = enterForumInitInfoGetUrlMethod
            this.plainUrlClickableSpanClass = plainUrlClickableSpanClass
            this.plainUrlClickableSpanOnClickMethod = plainUrlClickableSpanOnClickMethod
            this.plainUrlClickableSpanOnClickOwnerClasses = plainUrlClickableSpanOnClickOwnerClasses
            this.plainUrlClickableSpanTypeField = plainUrlClickableSpanTypeField
            this.plainUrlClickableSpanUrlField = plainUrlClickableSpanUrlField
            this.plainUrlClickableSpanTextField = plainUrlClickableSpanTextField
            this.plainUrlMessageManagerClass = plainUrlMessageManagerClass
            this.plainUrlMessageDispatchMethod = plainUrlMessageDispatchMethod
            this.plainUrlResponsedMessageClass = plainUrlResponsedMessageClass
            this.plainUrlResponsedMessageGetCmdMethod = plainUrlResponsedMessageGetCmdMethod
            this.plainUrlCustomResponsedMessageClass = plainUrlCustomResponsedMessageClass
            this.plainUrlCustomResponsedMessageGetDataMethod = plainUrlCustomResponsedMessageGetDataMethod
            this.plainUrlApplicationClass = plainUrlApplicationClass
            this.plainUrlApplicationGetInstMethod = plainUrlApplicationGetInstMethod
            this.plainUrlBrowserHelperClass = plainUrlBrowserHelperClass
            this.plainUrlBrowserHelperStartWebActivityMethod = plainUrlBrowserHelperStartWebActivityMethod
            this.plainUrlWebContainerActivityClass = plainUrlWebContainerActivityClass
            this.plainUrlWebContainerInitDataMethod = plainUrlWebContainerInitDataMethod
            this.plainUrlWebContainerWebViewClientClass = plainUrlWebContainerWebViewClientClass
            this.plainUrlWebContainerShouldOverrideUrlLoadingMethod =
                plainUrlWebContainerShouldOverrideUrlLoadingMethod
            this.privateReadReceiptModelClass = privateReadReceiptModelClass
            this.privateReadReceiptModelReadDispatchMethod = privateReadReceiptModelReadDispatchMethod
            this.privateReadReceiptMessageManagerClass = privateReadReceiptMessageManagerClass
            this.privateReadReceiptMessageManagerGetInstanceMethod = privateReadReceiptMessageManagerGetInstanceMethod
            this.privateReadReceiptMessageManagerGetSocketClientMethod =
                privateReadReceiptMessageManagerGetSocketClientMethod
            this.privateReadReceiptMessageSendMethod = privateReadReceiptMessageSendMethod
            this.privateReadReceiptMessageBaseClass = privateReadReceiptMessageBaseClass
            this.privateReadReceiptSocketClientClass = privateReadReceiptSocketClientClass
            this.privateReadReceiptSocketDuplicateCheckMethod = privateReadReceiptSocketDuplicateCheckMethod
            this.privateReadReceiptRequestClass = privateReadReceiptRequestClass
            this.privateReadReceiptModelBaseClass = privateReadReceiptModelBaseClass
            this.privateReadReceiptCommitResponseClass = privateReadReceiptCommitResponseClass
            this.privateReadReceiptProcessAckMethod = privateReadReceiptProcessAckMethod
            this.privateReadReceiptResponseErrorMethod = privateReadReceiptResponseErrorMethod
            this.privateReadReceiptRequestMsgIdField = privateReadReceiptRequestMsgIdField
            this.privateReadReceiptRequestToUidField = privateReadReceiptRequestToUidField
            this.privateReadReceiptModelDataField = privateReadReceiptModelDataField
            this.privateReadReceiptPageDataClass = privateReadReceiptPageDataClass
            this.privateReadReceiptPageDataChatListMethod = privateReadReceiptPageDataChatListMethod
            this.privateReadReceiptChatMessageClass = privateReadReceiptChatMessageClass
            this.privateReadReceiptChatMessageMsgIdMethod = privateReadReceiptChatMessageMsgIdMethod
            this.privateReadReceiptChatMessageUserIdMethod = privateReadReceiptChatMessageUserIdMethod
            this.privateReadReceiptChatMessageLocalDataMethod = privateReadReceiptChatMessageLocalDataMethod
            this.privateReadReceiptLocalDataClass = privateReadReceiptLocalDataClass
            this.privateReadReceiptLocalDataStatusMethod = privateReadReceiptLocalDataStatusMethod
            this.privateReadReceiptAccountClass = privateReadReceiptAccountClass
            this.privateReadReceiptCurrentAccountMethod = privateReadReceiptCurrentAccountMethod
            this.mountCardLinkLayoutClass = mountCardLinkLayoutClass
            this.mountCardLinkLayoutOnClickMethod = mountCardLinkLayoutOnClickMethod
            this.mountCardLinkLayoutDataField = mountCardLinkLayoutDataField
            this.mountCardLinkInfoDataClass = mountCardLinkInfoDataClass
            this.mountCardLinkInfoGetUrlMethod = mountCardLinkInfoGetUrlMethod
            this.mineTabWebViewClass = mineTabWebViewClass
            this.mineTabWebLoadUrlMethod = mineTabWebLoadUrlMethod
            this.mineTabWebGetUrlMethod = mineTabWebGetUrlMethod
            this.mineTabWebGetInnerWebViewMethod = mineTabWebGetInnerWebViewMethod
            this.homeSideBarWebViewClass = homeSideBarWebViewClass
            this.homeSideBarTbWebViewClass = homeSideBarTbWebViewClass
            this.homeSideBarWebGetWebViewMethod = homeSideBarWebGetWebViewMethod
            this.homeSideBarWebGetUrlMethod = homeSideBarWebGetUrlMethod
            this.homeSideBarWebGetInnerWebViewMethod = homeSideBarWebGetInnerWebViewMethod
            this.homeSideBarWebLoadUrlMethods = homeSideBarWebLoadUrlMethods
            this.autoSignInNetworkClass = autoSignInNetworkClass
            this.autoSignInNetworkConstructorSpec = autoSignInNetworkConstructorSpec
            this.autoSignInNetworkAddPostDataMethod = autoSignInNetworkAddPostDataMethod
            this.autoSignInNetworkPostNetDataMethod = autoSignInNetworkPostNetDataMethod
            this.autoSignInNetworkSetNeedTbsMethod = autoSignInNetworkSetNeedTbsMethod
            this.autoSignInNetworkSetNeedSigMethod = autoSignInNetworkSetNeedSigMethod
            this.autoSignInTbConfigClass = autoSignInTbConfigClass
            this.autoSignInServerAddressField = autoSignInServerAddressField
            this.autoSignInCoreApplicationClass = autoSignInCoreApplicationClass
            this.autoSignInCurrentAccountMethod = autoSignInCurrentAccountMethod
            this.autoSignInHybridProxyClass = autoSignInHybridProxyClass
            this.autoSignInHybridProxyConstructorSpec = autoSignInHybridProxyConstructorSpec
            this.autoSignInHybridJsBridgeClass = autoSignInHybridJsBridgeClass
            this.autoSignInHybridNativeNetworkProxyMethod = autoSignInHybridNativeNetworkProxyMethod
            this.autoSignInHybridTaskClass = autoSignInHybridTaskClass
            this.autoSignInHybridTaskConstructorSpec = autoSignInHybridTaskConstructorSpec
            this.autoSignInHybridTaskDoInBackgroundMethod = autoSignInHybridTaskDoInBackgroundMethod
            this.forumBottomSheetViewClass = forumBottomSheetViewClass
            this.forumBottomSheetInitScrollMethod = forumBottomSheetInitScrollMethod
            this.autoRefreshTriggerMethod = autoRefreshTriggerMethod
            this.autoLoadMoreConfigClass = autoLoadMoreConfigClass
            this.autoLoadMoreConfigMethod = autoLoadMoreConfigMethod
            this.pbCommentScrollListenerClass = pbCommentScrollListenerClass
            this.pbCommentScrollMethod = pbCommentScrollMethod
            this.pbCommentScrollFragmentField = pbCommentScrollFragmentField
            this.pbCommentScrollBottomListenerField = pbCommentScrollBottomListenerField
            this.pbCommentScrollBottomMethod = pbCommentScrollBottomMethod
            this.pbCommentBottomListScrollClass = pbCommentBottomListScrollClass
            this.pbCommentBottomListScrollMethod = pbCommentBottomListScrollMethod
            this.pbCommentBottomListOwnerField = pbCommentBottomListOwnerField
            this.pbCommentBottomRecyclerScrollClass = pbCommentBottomRecyclerScrollClass
            this.pbCommentBottomRecyclerScrollMethod = pbCommentBottomRecyclerScrollMethod
            this.pbCommentBottomRecyclerOwnerField = pbCommentBottomRecyclerOwnerField
            this.pbGestureScaleManagerClass = pbGestureScaleManagerClass
            this.pbGestureScaleDispatchMethod = pbGestureScaleDispatchMethod
            this.pbGestureScaleListenerSetterMethod = pbGestureScaleListenerSetterMethod
            this.pbGestureScaleListenerClass = pbGestureScaleListenerClass
            this.pbGestureScaleListenerOnScaleMethod = pbGestureScaleListenerOnScaleMethod
            this.pbLikeAutoReplyAgreeViewClass = pbLikeAutoReplyAgreeViewClass
            this.pbLikeAutoReplyAgreeClickMethod = pbLikeAutoReplyAgreeClickMethod
            this.pbLikeAutoReplyAgreeViewGetDataMethod = pbLikeAutoReplyAgreeViewGetDataMethod
            this.pbLikeAutoReplyAgreeDataClass = pbLikeAutoReplyAgreeDataClass
            this.pbLikeAutoReplyAgreeDataHasAgreeField = pbLikeAutoReplyAgreeDataHasAgreeField
            this.pbLikeAutoReplyAgreeDataAgreeTypeField = pbLikeAutoReplyAgreeDataAgreeTypeField
            this.pbLikeAutoReplyAgreeDataIsInThreadField = pbLikeAutoReplyAgreeDataIsInThreadField
            this.pbLikeAutoReplyInputContainerClass = pbLikeAutoReplyInputContainerClass
            this.pbLikeAutoReplyInputContainerGetInputViewMethod = pbLikeAutoReplyInputContainerGetInputViewMethod
            this.pbLikeAutoReplyInputContainerGetSendViewMethod = pbLikeAutoReplyInputContainerGetSendViewMethod

            this.collectionPresenterField = collectionPresenterField
            this.collectionPresenterListSetterMethod = collectionPresenterListSetterMethod
            this.collectionPresenterListSetterMethodSpec = collectionPresenterListSetterMethodSpec
            this.collectionPresenterAdapterField = collectionPresenterAdapterField
            this.collectionModelField = collectionModelField
            this.collectionModelListGetterMethod = collectionModelListGetterMethod
            this.collectionModelListGetterMethodSpec = collectionModelListGetterMethodSpec
            this.collectionModelParseMethod = collectionModelParseMethod
            this.collectionModelParseMethodSpec = collectionModelParseMethodSpec
            this.collectionModelListField = collectionModelListField
            this.collectionFragmentDisplayListField = collectionFragmentDisplayListField
            this.collectionActivityNavControllerField = collectionActivityNavControllerField
            this.collectionNavBarField = collectionNavBarField
            this.collectionAdapterShowFooterMethod = collectionAdapterShowFooterMethod
            this.collectionAdapterLoadingMethod = collectionAdapterLoadingMethod
            this.collectionAdapterHasMoreMethod = collectionAdapterHasMoreMethod
            this.collectionEditModeMethod = collectionEditModeMethod

            this.historyAdapterField = historyAdapterField
            this.historyAdapterSetListMethod = historyAdapterSetListMethod
            this.historyAdapterSetListMethodSpec = historyAdapterSetListMethodSpec
            this.historyListField = historyListField
            this.historyActivityListUpdateMethod = historyActivityListUpdateMethod
            this.historyActivityListUpdateMethodSpec = historyActivityListUpdateMethodSpec
            this.historyActivityNavBarField = historyActivityNavBarField
            this.historyThreadNameMethod = historyThreadNameMethod
            this.historyForumNameMethod = historyForumNameMethod
            this.historyUserNameMethod = historyUserNameMethod
            this.historyDescriptionMethod = historyDescriptionMethod
            this.historyThreadIdMethod = historyThreadIdMethod
            this.historyPostIdMethod = historyPostIdMethod
            this.historyLiveIdMethod = historyLiveIdMethod

            this.msgTabLocateToTabMethod = msgTabLocateToTabMethod
            this.msgTabContainerSelectMethod = msgTabContainerSelectMethod
            this.msgTabContainerExtDataField = msgTabContainerExtDataField
            this.freeCopyPopupMenuClass = freeCopyPopupMenuClass
            this.freeCopyPopupContentViewMethod = freeCopyPopupContentViewMethod
            this.freeCopyPopupTextField = freeCopyPopupTextField

            this.mainTabDataClass = mainTabDataClass
            this.mainTabAddMethod = mainTabAddMethod
            this.mainTabGetListMethod = mainTabGetListMethod
            this.mainTabDelegateGetStructureMethod = mainTabDelegateGetStructureMethod
            this.mainTabStructureTypeField = mainTabStructureTypeField
            this.mainTabStructureDynamicIconField = mainTabStructureDynamicIconField
            this.mainTabStructureFragmentField = mainTabStructureFragmentField

            origImagePagerAdapterClass = originalImageScan.pagerAdapterClass
            origImageUrlDragImageViewClass = originalImageScan.urlDragImageViewClass
            origImageDataClass = originalImageScan.dataClass
            origImageSetPrimaryItemMethod = originalImageScan.setPrimaryItemMethod
            origImageSetAssistUrlMethod = originalImageScan.setAssistUrlMethod
            origImageAssistDataMethod = originalImageScan.assistDataMethod
            origImageOriginTextMethod = originalImageScan.originTextMethod
            origImageShowButtonField = originalImageScan.showButtonField
            origImageBlockedField = originalImageScan.blockedField
            origImageOriginalProcessField = originalImageScan.originalProcessField
            origImageOriginalUrlField = originalImageScan.originalUrlField
            origImageSharedPrefHelperClass = originalImageScan.sharedPrefHelperClass
            origImageSharedPrefGetInstanceMethod = originalImageScan.sharedPrefGetInstanceMethod
            origImageSharedPrefPutBooleanMethod = originalImageScan.sharedPrefPutBooleanMethod
            origImageMd5Class = originalImageScan.md5Class
            origImageMd5Method = originalImageScan.md5Method
            origImagePrimaryReadyMethod = originalImageScan.primaryReadyMethod
            origImageTriggerMethod = originalImageScan.triggerMethod
            origImageDirectStartMethod = originalImageScan.directStartMethod
            this.shareTrackBuilderClass = shareTrackBuilderClass
            this.shareTrackBuildUrlMethod = shareTrackBuildUrlMethod
            this.shareTrackAppendQueryMethod = shareTrackAppendQueryMethod
            this.imageViewerShareConfigClass = imageViewerShareConfigClass
            this.imageViewerShareIsDialogField = imageViewerShareIsDialogField
            this.imageViewerShareItemField = imageViewerShareItemField
            this.imageViewerShareAddOutsideMethod = imageViewerShareAddOutsideMethod
            this.imageViewerShareGetRequestDataMethod = imageViewerShareGetRequestDataMethod
            this.imageViewerShareSetRequestDataMethod = imageViewerShareSetRequestDataMethod
            this.imageViewerShareGetContextMethod = imageViewerShareGetContextMethod
            this.imageViewerShareItemClass = imageViewerShareItemClass
            this.imageViewerShareItemTitleField = imageViewerShareItemTitleField
            this.imageViewerShareItemContentField = imageViewerShareItemContentField
            this.imageViewerShareItemLinkUrlField = imageViewerShareItemLinkUrlField
            this.imageViewerShareItemImageUriField = imageViewerShareItemImageUriField
            this.imageViewerShareItemImageUrlField = imageViewerShareItemImageUrlField
            this.imageViewerShareItemLocalFileField = imageViewerShareItemLocalFileField
            this.imageViewerShareItemViewClass = imageViewerShareItemViewClass
            this.imageViewerShareItemNameByResMethod = imageViewerShareItemNameByResMethod
            this.imageViewerShareItemNameByTextMethod = imageViewerShareItemNameByTextMethod
            this.imageViewerShareIconResId = imageViewerShareIconResId
            applyHomeNativeGlassScan(
                HomeNativeGlassScanSymbols(
                    subPbNextPageMoreViewId = homeNativeGlassSubPbNextPageMoreViewId,
                    pbReplyTitleDividerViewId = homeNativeGlassPbReplyTitleDividerViewId,
                    dynamicBackgroundColorIds = homeNativeGlassDynamicBackgroundColorIds,
                    topChromeTabSelectedMethodSpecs = homeNativeGlassTopChromeTabSelectedMethodSpecs,
                    subPbSetNextPageMethod = homeNativeGlassSubPbSetNextPageMethod,
                    subPbSetNextPageParamType = homeNativeGlassSubPbSetNextPageParamType,
                    sortSwitchBackgroundPaintField = homeNativeGlassSortSwitchBackgroundPaintField,
                    sortSwitchSlideDrawMethod = homeNativeGlassSortSwitchSlideDrawMethod,
                    sortSwitchSlidePathField = homeNativeGlassSortSwitchSlidePathField,
                    enterForumCapsuleControllerClass = homeNativeGlassEnterForumCapsuleControllerClass,
                    enterForumCapsuleInitMethod = homeNativeGlassEnterForumCapsuleInitMethod,
                    enterForumCapsuleRefreshMethod = homeNativeGlassEnterForumCapsuleRefreshMethod,
                    enterForumCapsuleViewField = homeNativeGlassEnterForumCapsuleViewField,
                    enterForumCapsuleTitleField = homeNativeGlassEnterForumCapsuleTitleField,
                    hostDarkModeMoreActivityClass = homeNativeGlassHostDarkModeMoreActivityClass,
                    hostDarkModeControllerField = homeNativeGlassHostDarkModeControllerField,
                    hostDarkModeSwitchGetterMethod = homeNativeGlassHostDarkModeSwitchGetterMethod,
                    hostDarkModeSwitchStateField = homeNativeGlassHostDarkModeSwitchStateField,
                    hostDarkModeSwitchSetOnMethod = homeNativeGlassHostDarkModeSwitchSetOnMethod,
                    hostDarkModeSwitchSetOffMethod = homeNativeGlassHostDarkModeSwitchSetOffMethod,
                    hostDarkModeSwitchCallbackMethod = homeNativeGlassHostDarkModeSwitchCallbackMethod,
                    pbCommonLayoutPreloaderGetOrDefaultMethod = pbCommonLayoutPreloaderGetOrDefaultMethod,
                ),
            )
            this.aiSpriteMemePanControllerClass = aiSpriteMemePanControllerClass
            this.aiSpriteMemeEnableMethod = aiSpriteMemeEnableMethod
            this.aiPbNewInputContainerClass = aiPbNewInputContainerClass
            this.aiPbNewInputContainerInitSpriteMemeMethod = aiPbNewInputContainerInitSpriteMemeMethod
            this.aiPbNewInputContainerInitAiWriteMethod = aiPbNewInputContainerInitAiWriteMethod
            this.aiPbAiEmojiCreationViewBindMethod = aiPbAiEmojiCreationViewBindMethod
            this.aiPbPageBrowserAiEmojiCreationViewClass = aiPbPageBrowserAiEmojiCreationViewClass
            this.aiPbPageBrowserAiEmojiCreationBindMethod = aiPbPageBrowserAiEmojiCreationBindMethod
            this.aiImageViewerJumpButtonOwnerClass = aiImageViewerJumpButtonOwnerClass
            this.aiImageViewerJumpButtonInitMethod = aiImageViewerJumpButtonInitMethod

            this.scanErrors = scanErrors
            source = if (homeTabScan.tabClass != null) "scan" else "partial"
            createdAt = System.currentTimeMillis()
        }
        return scanned.withFeatureStatusMap(HookFeatureStatusDeriver.derive(scanned))
    }

    internal fun collectInstanceFields(clazz: Class<*>): List<Field> =
        ScanReflection.collectInstanceFields(clazz)

    internal fun collectInstanceFieldsUncached(clazz: Class<*>): List<Field> =
        ScanReflection.collectInstanceFieldsUncached(clazz)

    internal fun collectInstanceMethods(clazz: Class<*>): List<Method> =
        ScanReflection.collectInstanceMethods(clazz)

    internal fun collectInstanceMethodsUncached(clazz: Class<*>): List<Method> =
        ScanReflection.collectInstanceMethodsUncached(clazz)

    internal fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    internal fun isBooleanType(type: Class<*>): Boolean = ScanReflection.isBooleanType(type)

    internal fun isIntType(type: Class<*>): Boolean = ScanReflection.isIntType(type)

    internal fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    internal fun safeFindClassUncached(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClassUncached(name, cl)

    private fun applyScanSupportCheck(
        context: Context,
        symbols: HookSymbols,
        logger: ScanLogger?,
    ): HookSymbols {
        val versionInfo = readTargetAppVersionInfo(context, logger)
            ?: return withDerivedFeatureStatus(
                symbols.withScanSupport(ScanSupportState.UNKNOWN),
            )

        if (
            versionInfo.versionCode > MAX_TIEBA_VERSION_CODE ||
            versionInfo.versionCode < MIN_TIEBA_VERSION_CODE
        ) {
            return withDerivedFeatureStatus(
                symbols.withScanSupport(
                    state = ScanSupportState.UNSUPPORTED_VERSION,
                    targetVersionCode = versionInfo.versionCode,
                    targetVersionName = versionInfo.versionName,
                    targetVersionType = null,
                ),
            )
        }

        val versionType = readTargetVersionType(context, logger)
        return withDerivedFeatureStatus(
            symbols.withScanSupport(
                state = if (isOfficialTiebaVersionType(versionType)) {
                    ScanSupportState.SUPPORTED
                } else {
                    ScanSupportState.NON_OFFICIAL
                },
                targetVersionCode = versionInfo.versionCode,
                targetVersionName = versionInfo.versionName,
                targetVersionType = versionType,
            ),
        )
    }

    private fun withDerivedFeatureStatus(symbols: HookSymbols): HookSymbols {
        return symbols.withFeatureStatusMap(HookFeatureStatusDeriver.derive(symbols))
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
        } catch (t: Throwable) {
            XposedCompat.logD("$TAG fingerprint build failed: ${t.javaClass.simpleName}: ${t.message}")
            "unknown:${runtimeModuleVersionCodeLabel()}:${System.currentTimeMillis()}"
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
        HookSymbolScanDiagnostics.log(logger, line)
    }

    fun formatScanException(t: Throwable): String {
        return HookSymbolScanDiagnostics.formatScanException(t)
    }

    private fun buildScanError(tag: String, t: Throwable): String {
        return "${tag.trim()} :: ${sanitizeScanStatusText(formatScanException(t))}"
    }

    private fun splitScanError(error: String): Pair<String, String> {
        return HookSymbolScanDiagnostics.splitScanError(error)
    }

    private fun sanitizeScanStatusText(raw: String): String {
        return HookSymbolScanDiagnostics.sanitizeScanStatusText(raw)
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
        HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
    }

    private fun describeSymbols(context: Context, symbols: HookSymbols): String =
        HookSymbolDiagnostics.describeSymbols(context, symbols)

    private fun describeAppMeta(context: Context): String =
        HookSymbolDiagnostics.describeAppMeta(context)

    fun runtimeModuleVersionName(): String =
        HookSymbolDiagnostics.runtimeModuleVersionName()

    private fun runtimeModuleVersionCodeLabel(): String =
        HookSymbolDiagnostics.runtimeModuleVersionCodeLabel()
}

private const val IMAGE_VIEWER_NATIVE_SHARE_FILE_PROVIDER_CLASS = "androidx.core.content.FileProvider"
private const val PB_COMMENT_BOTTOM_LISTENER_FIELD = "mOnScrollToBottomListener"
private const val PB_COMMENT_BOTTOM_METHOD = "onScrollToBottom"
private const val PB_AD_INSERT_CLASS = "com.baidu.tieba.pb.pb.main.underlayer.PbAdapterManagerInsertUtilKt"
private const val PB_EARLY_AD_INSERT_MIN_METHOD_COUNT = 2
private const val PB_PAGE_REQUEST_MESSAGE_CLASS = "com.baidu.tieba.pb.PbPageRequestMessage"
private const val PAGE_BROWSER_REQUEST_MESSAGE_CLASS =
    "com.baidu.tieba.pb.pagebrowser.net.PageBrowserRequestMessage"
private const val PB_LIST_DATA_REQ_BUILDER_CLASS = "tbclient.PbList.DataReq\$Builder"
private const val POST_AD_ADVERT_APP_INFO_CLASS = "com.baidu.tbadk.core.data.AdvertAppInfo"
private const val PB_COMMON_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
private const val RECOMMEND_CARD_VIEW_CLASS = "com.baidu.tieba.feed.component.RecommendCardView"
private val POST_AD_ADVERT_APP_TYPE_FIELDS = arrayOf(
    "TYPE_FRS_ADVERT_APP_EMPTY",
    "TYPE_FRS_ADVERT_APP_SINGLE_PIC",
    "TYPE_FRS_ADVERT_APP_MULTI_PIC",
    "TYPE_FRS_ADVERT_APP_VIDEO",
    "TYPE_FRS_ADVERT_APP_VR_VIDEO",
    "TYPE_PB_ADVERT_APP_EMPTY",
    "TYPE_RECOMMEND_ADVERT_APP_EMPTY",
    "TYPE_RECOMMEND_ADVERT_APP_SINGLE_PIC",
    "TYPE_RECOMMEND_ADVERT_APP_VIDEO",
    "TYPE_ADVERT_LEGO_APP",
    "TYPE_ADVERT_LEGO_APP_SINGLE",
    "TYPE_ADVERT_LEGO_APP_MULTI",
    "TYPE_ADVERT_LEGO_APP_VIDEO",
    "TYPE_ADVERT_LEGO_APP_SMALL_PIC",
    "TYPE_ADVERT_LEGO_APP_SMALL_VIDEO_PIC",
    "TYPE_ADVERT_FUN_AD_TEMPLETE",
    "TYPE_ADVERT_FUN_AD_EMPTY",
    "TYPE_ADVERT_FUN_AD_PLACEHOLDER",
    "TYPE_ADVERT_FUN_AD_COMMENT_PLACEHOLDER",
)
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
