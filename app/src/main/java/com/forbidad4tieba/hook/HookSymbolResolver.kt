package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.symbol.validation.*

import com.forbidad4tieba.hook.symbol.status.*

import com.forbidad4tieba.hook.symbol.scan.*

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.symbol.cache.*

import android.content.Context
import android.os.Bundle
import android.text.style.ClickableSpan
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.AbsListView
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ReflectionUtils
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
    private const val SEARCH_BOX_HEADER_CONTAINER_CLASS = "androidx.coordinatorlayout.widget.CoordinatorLayout"
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
        StableTiebaHookPoints.AGREE_VIEW_CLASS,
        StableTiebaHookPoints.AGREE_DATA_CLASS,
        StableTiebaHookPoints.PB_NEW_INPUT_CONTAINER_CLASS,
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

    private val memoryCache = HookSymbolMemoryCache()

    fun getMemorySymbols(): HookSymbols? = memoryCache.currentSymbols()

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
        if (source.featureStatusMap.isEmpty()) return HookFeatureStatusDeriver.derive(source)
        return LinkedHashMap<String, HookFeatureStatus>(HookFeatureStatusDeriver.derive(source)).apply {
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
        return HookSymbolStatusFormatter.formatFeatureStatusLines(
            statusMap = featureStatusMap(symbols),
            featureKeys = HookFeatureStatusDeriver.featureKeys,
        )
    }

    fun formatHookPointStatusLines(symbols: HookSymbols?): List<String> {
        return HookSymbolStatusFormatter.formatHookPointStatusLines(
            symbols = symbols,
            aiPbAiEmojiCreationViewClass = AI_PB_AI_EMOJI_CREATION_VIEW_CLASS,
            aiPbAiEmojiCreationPageBrowserViewClass = AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS,
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
            val pageClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
                XposedCompat.log("[AutoRefreshHook] skipped: class not found: $PERSONALIZE_PAGE_VIEW_CLASS")
                return null
            }
            val method = collectInstanceMethods(pageClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.isEmpty()
            } ?: run {
                XposedCompat.log("[AutoRefreshHook] skipped: method mismatch: $PERSONALIZE_PAGE_VIEW_CLASS.$methodName()")
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
            val feedCardViewClass = safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl) ?: run {
                XposedCompat.log(
                    "[FeedInfoLogHook] skipped: class not found: " +
                        StableTiebaHookPoints.FEED_CARD_VIEW_CLASS,
                )
                return null
            }
            val bindMethod = ReflectionUtils.findMethodInHierarchy(feedCardViewClass, bindMethodName) { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    !method.parameterTypes[0].isPrimitive
            } ?: run {
                XposedCompat.log(
                    "[FeedInfoLogHook] skipped: method not found: " +
                        "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodName(*)",
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
            val listenerClassName = resolvedSymbols.pbGestureScaleListenerClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleListenerClass")
                return null
            }
            val methodName = resolvedSymbols.pbGestureScaleListenerOnScaleMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleListenerOnScaleMethod")
                return null
            }
            val listenerClass = safeFindClass(listenerClassName, cl) ?: run {
                XposedCompat.log("[PbDisableGestureFontScaleHook] skipped: class not found: $listenerClassName")
                return null
            }
            val method = collectInstanceMethods(listenerClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == ScaleGestureDetector::class.java
            } ?: run {
                XposedCompat.log(
                    "[PbDisableGestureFontScaleHook] skipped: method mismatch: " +
                        "$listenerClassName.$methodName(ScaleGestureDetector)",
                )
                return null
            }
            method.isAccessible = true
            PbGestureScaleSymbols(onScaleMethod = method)
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
            val updateMethod = updateMethodName?.let { methodName ->
                ReflectionUtils.findMethodInHierarchy(activityClass, methodName) { target ->
                    !Modifier.isStatic(target.modifiers) &&
                        target.returnType == Void.TYPE &&
                        target.parameterTypes.size == 1 &&
                        (List::class.java.isAssignableFrom(target.parameterTypes[0]) ||
                            ArrayList::class.java.isAssignableFrom(target.parameterTypes[0]))
                }?.apply { isAccessible = true }
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
                modelField = requireSymbol("collectionModelField", resolvedSymbols.collectionModelField),
                modelListGetterMethod = requireSymbol(
                    "collectionModelListGetterMethod",
                    resolvedSymbols.collectionModelListGetterMethod,
                ),
                modelParseMethod = requireSymbol("collectionModelParseMethod", resolvedSymbols.collectionModelParseMethod),
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

        val memorySymbols = memoryCache.getIfFingerprint(fingerprint)
        if (!forceRescan && memorySymbols != null) {
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
            val unsupported = HookSymbols(
                source = "unsupported",
                createdAt = System.currentTimeMillis(),
                scanErrors = scanErrors,
            )
            unsupported.copy(featureStatusMap = HookFeatureStatusDeriver.derive(unsupported))
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
            symbols.createdAt > 0L
    }

    private inline fun <T> withScanContext(cl: ClassLoader, block: () -> T): T {
        val previous = HookSymbolScanSession.get()
        HookSymbolScanSession.set(HookSymbolScanContext(cl))
        return try {
            block()
        } finally {
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
            return unsupported.copy(featureStatusMap = HookFeatureStatusDeriver.derive(unsupported))
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

        // Dynamic obfuscated symbols used by ad and UI scans.
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
            homeMatch?.let { HomeTabItemSymbolScanner.scan(it, cl, logger) } ?: HomeTabItemScanSymbols()
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
            RecommendCardSymbolScanner.scanNestedData(cl, logger)
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
            PostAdDataFilterSymbolScanner.scan(cl, logger)
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
            EnterForumWebSymbolScanner.scanInitInfoData(cl, logger)
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
            MountCardLinkSymbolScanner.scan(cl, logger)
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
            AutoRefreshSymbolScanner.scan(context, cl, logger)
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
                ImageViewerShareIconSymbolScanner.scanFromDex(
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
            HomeNativeGlassSymbolScanner.scanResourceIds(context, cl, logger)
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
            HistorySearchSymbolScanner.scan(cl, logger)
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
            MsgTabSymbolScanner.scan(cl, logger)
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
            pbLikeAutoReplyAgreeViewClass = pbLikeAutoReplyAgreeViewClass,
            pbLikeAutoReplyAgreeClickMethod = pbLikeAutoReplyAgreeClickMethod,
            pbLikeAutoReplyAgreeViewGetDataMethod = pbLikeAutoReplyAgreeViewGetDataMethod,
            pbLikeAutoReplyAgreeDataClass = pbLikeAutoReplyAgreeDataClass,
            pbLikeAutoReplyAgreeDataHasAgreeField = pbLikeAutoReplyAgreeDataHasAgreeField,
            pbLikeAutoReplyAgreeDataAgreeTypeField = pbLikeAutoReplyAgreeDataAgreeTypeField,
            pbLikeAutoReplyAgreeDataIsInThreadField = pbLikeAutoReplyAgreeDataIsInThreadField,
            pbLikeAutoReplyInputContainerClass = pbLikeAutoReplyInputContainerClass,
            pbLikeAutoReplyInputContainerGetInputViewMethod = pbLikeAutoReplyInputContainerGetInputViewMethod,
            pbLikeAutoReplyInputContainerGetSendViewMethod = pbLikeAutoReplyInputContainerGetSendViewMethod,

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
        return scanned.copy(featureStatusMap = HookFeatureStatusDeriver.derive(scanned))
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

    internal fun scoreImageViewerJumpButtonOwnerClass(clazz: Class<*>, layoutClass: Class<*>): Int {
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

    internal fun isImageViewerJumpButtonInitMethodName(ownerClass: Class<*>, methodName: String): Boolean {
        return ownerClass.declaredMethods.any { method ->
            method.name == methodName && isImageViewerJumpButtonInitMethod(method)
        }
    }

    internal fun isImageViewerJumpButtonInitMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.isEmpty()
    }

    internal fun isAiPbEmojiCreationViewBindMethod(method: Method, cl: ClassLoader): Boolean {
        if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) return false
        val spriteMemeInfoClass = safeFindClass(AI_SPRITE_MEME_INFO_CLASS, cl) ?: return false
        val params = method.parameterTypes
        return params.size == 3 &&
            spriteMemeInfoClass.isAssignableFrom(params[0]) &&
            isIntType(params[1]) &&
            Map::class.java.isAssignableFrom(params[2])
    }

    internal fun isPbPageBrowserAiEmojiCreationBindMethod(method: Method): Boolean {
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

    internal fun isAiSpriteMemeEnableMethod(method: Method, inputShowTypeClass: Class<*>?): Boolean {
        val params = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 2 &&
            Context::class.java.isAssignableFrom(params[0]) &&
            (inputShowTypeClass?.let { params[1] == it } ?: (params[1].name == AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS))
    }

    internal fun isPbNewInputContextInitMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            params.size == 1 &&
            Context::class.java.isAssignableFrom(params[0])
    }

    internal fun isPbNewInputContextInitMethodName(inputContainerClass: Class<*>, methodName: String): Boolean {
        return inputContainerClass.declaredMethods.any { method ->
            method.name == methodName && isPbNewInputContextInitMethod(method)
        }
    }

    internal fun isAiPbNewInputContainerClassValid(
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

    internal fun findPbAdBidPageBrowserRequestDataMethodInHierarchy(
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

    internal fun isPlainUrlMessageDispatchMethod(method: Method, responsedMessageClass: Class<*>): Boolean {
        return method.name == PLAIN_URL_MESSAGE_DISPATCH_METHOD &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            responsedMessageClass.isAssignableFrom(method.parameterTypes[0])
    }

    internal fun isMountCardLinkLayoutOnClickMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == View::class.java
    }

    internal fun resolveMountCardLinkLayoutDataField(layoutClass: Class<*>, dataClass: Class<*>): Field? {
        val candidates = collectInstanceFields(layoutClass).filter { field ->
            !Modifier.isStatic(field.modifiers) && dataClass.isAssignableFrom(field.type)
        }
        return candidates.firstOrNull { it.name == MOUNT_CARD_LINK_LAYOUT_DATA_FIELD }
            ?: candidates.singleOrNull()
    }

    internal fun isMountCardLinkLayoutStructureValid(
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

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    internal fun resolveHistoryDataStringGetterName(methods: List<Method>, vararg candidateNames: String): String? {
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

    internal fun resolveListSetterMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isListType(method.parameterTypes[0])
        }
        return pickMethodName(candidates, preferredName)
    }

    internal fun resolveListGetterMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.parameterTypes.isEmpty() && isListType(method.returnType)
        }
        return pickMethodName(candidates, preferredName)
    }

    internal fun resolveParseMethodName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceMethods(clazz).filter { method ->
            method.returnType != Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java &&
                isListType(method.returnType)
        }
        return pickMethodName(candidates, preferredName)
    }

    internal fun resolveListFieldName(clazz: Class<*>, preferredName: String): String? {
        val candidates = collectInstanceFields(clazz).filter { isListType(it.type) }
        return candidates.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == preferredName) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    internal fun pickMethodName(methods: List<java.lang.reflect.Method>, preferredName: String): String? {
        if (methods.isEmpty()) return null
        methods.firstOrNull { it.name == preferredName }?.let { return it.name }
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    internal fun pickMethod(
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

    internal fun pickFieldName(fields: List<java.lang.reflect.Field>, preferredName: String?): String? {
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

    internal fun isFragmentLikeType(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.name == "androidx.fragment.app.Fragment") return true
            current = current.superclass
        }
        return false
    }

    internal fun collectInstanceFields(clazz: Class<*>): List<Field> {
        HookSymbolScanSession.get()?.let { return it.collectInstanceFields(clazz) }
        return collectInstanceFieldsUncached(clazz)
    }

    internal fun collectInstanceFieldsUncached(clazz: Class<*>): List<Field> {
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

    internal fun collectInstanceMethods(clazz: Class<*>): List<Method> {
        HookSymbolScanSession.get()?.let { return it.collectInstanceMethods(clazz) }
        return collectInstanceMethodsUncached(clazz)
    }

    internal fun collectInstanceMethodsUncached(clazz: Class<*>): List<Method> {
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

    internal fun isListType(type: Class<*>): Boolean {
        return List::class.java.isAssignableFrom(type) || ArrayList::class.java.isAssignableFrom(type)
    }

    internal fun isBooleanType(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType
    }

    internal fun isIntType(type: Class<*>): Boolean {
        return type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
    }

    internal fun isAdapterLike(type: Class<*>): Boolean {
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
        } catch (t: Throwable) {
            XposedCompat.logD("$TAG: pbLikeAutoReply validate failed: ${sanitizeScanStatusText(formatScanException(t))}")
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

    internal fun runRules(
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
                    HookSymbolScanSession.get()?.scanErrors?.let { errors ->
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

    internal fun chooseUniqueScanMatch(
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

    internal fun safeFindClass(name: String, cl: ClassLoader): Class<*>? {
        HookSymbolScanSession.get()?.let { return it.findClass(name, cl) }
        return safeFindClassUncached(name, cl)
    }

    internal fun safeFindClassUncached(name: String, cl: ClassLoader): Class<*>? {
        return try {
            XposedCompat.findClassOrNull(name, cl)
        } catch (_: Throwable) {
            null
        }
    }

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
                "闂?[$name: $value]"
            } else {
                "闂?[$name: NOT FOUND]"
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
            ${fmt("PbLikeAutoReply", "${symbols.pbLikeAutoReplyAgreeViewClass}.${symbols.pbLikeAutoReplyAgreeClickMethod}/${symbols.pbLikeAutoReplyAgreeViewGetDataMethod}[${symbols.pbLikeAutoReplyAgreeDataHasAgreeField},${symbols.pbLikeAutoReplyAgreeDataAgreeTypeField},${symbols.pbLikeAutoReplyAgreeDataIsInThreadField}] -> ${symbols.pbLikeAutoReplyInputContainerClass}.{${symbols.pbLikeAutoReplyInputContainerGetInputViewMethod},${symbols.pbLikeAutoReplyInputContainerGetSendViewMethod}}")}
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
private const val IMAGE_VIEWER_NATIVE_SHARE_FILE_PROVIDER_CLASS = "androidx.core.content.FileProvider"
private const val FORUM_BOTTOM_SHEET_VIEW_CLASS = "com.baidu.tieba.forum.widget.TbBottomSheetView"
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
