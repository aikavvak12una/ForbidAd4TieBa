package com.forbidad4tieba.hook.symbol.dexkit

import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanSession
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.UsingFieldData
import java.io.File

internal object DexKitSemanticScanner {
    private const val TAG = "DexKitSemantic"
    private const val PB_AD_BID_ENDPOINT = "c/b/ad/adBid?cmd=309757&format=protobuf"
    private const val PB_COMMON_REQUEST_MODEL_CLASS =
        "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
    private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS =
        "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
    private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
    private const val OBJECT_CLASS = "java.lang.Object"
    private const val AGREE_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.AgreeData"
    private const val AGREE_DATA_HAS_AGREE_FIELD = "hasAgree"
    private const val AGREE_DATA_AGREE_TYPE_FIELD = "agreeType"
    private const val TB_FLOATING_BAR_CLASS = "com.baidu.tieba.feed.component.view.TbFloatingBar"
    private const val PAGE_BROWSER_AI_EMOJI_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val HOST_FOLLOW_SYSTEM_PREF_KEY = "key_is_follow_system_mode"
    private const val SHARE_DIALOG_CONFIG_CLASS =
        "com.baidu.tbadk.core.atomData.ShareDialogConfig"
    private const val SHARE_DIALOG_ADD_OUTSIDE_METHOD = "addOutsideTextView"
    private const val TIEBA_DRAWABLE_CLASS = "com.baidu.tieba.R\$drawable"

    fun scanShareIcon(
        sourcePaths: List<String>,
        ownerClassNames: List<String>,
        cl: ClassLoader,
        resolveDrawableResource: (String) -> Int?,
        logger: ScanLogger? = null,
    ): DexShareIconMatch? = withBridge(sourcePaths, logger, "ImageViewerNativeShareHook.IconDex") { bridge ->
        val ownerMatch = ownerClassNames.asSequence()
            .flatMap { className -> exactMethods(bridge, className, logger).asSequence() }
            .mapNotNull { method ->
                val drawable = method.usingFields
                    .asSequence()
                    .map { it.field }
                    .filter { field -> field.declaredClassName == TIEBA_DRAWABLE_CLASS }
                    .mapNotNull { field -> resolveStaticIntField(cl, field.declaredClassName, field.fieldName) }
                    .firstOrNull { isDrawableResourceId(it) }
                    ?: return@mapNotNull null
                val score = 120 +
                    scoreInvokes(method, "setPureDrawable", 35) +
                    scoreInvokes(method, "setImageResource", 20) +
                    scoreClassName(method.declaredClassName, "Image", 10)
                DexShareIconMatch(
                    ownerClassName = method.declaredClassName,
                    ownerMethodName = method.methodName,
                    resId = drawable,
                    score = score,
                )
            }
            .maxWithOrNull(compareBy<DexShareIconMatch> { it.score }.thenBy { -it.ownerMethodName.length })
        ownerMatch ?: scanShareIconFromAddOutsideCallers(bridge, cl, resolveDrawableResource, logger)
    }

    fun scanAutoRefresh(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAutoRefreshMatch> = withBridge(sourcePaths, logger, "AutoRefreshHook.Dex", emptyList()) { bridge ->
        exactMethods(bridge, ownerClassName, logger).mapNotNull { method ->
            if (method.returnTypeName != "void" || method.paramCount != 0) return@mapNotNull null
            val invokes = method.invokes.toList()
            val hasSelection = invokes.any { it.methodName == "setSelection" }
            val hasSetRefreshing = invokes.any {
                it.methodName == "setRefreshing" || it.declaredClassName.contains("SwipeRefreshLayout")
            }
            val hasScrollTabNotify = invokes.any {
                it.methodName == "b" &&
                    it.declaredClassName == "com.baidu.tieba.homepage.framework.indicator.ScrollFragmentTabHost\$z"
            }
            if (!hasSelection && !hasSetRefreshing && !hasScrollTabNotify) return@mapNotNull null
            var score = 0
            val evidence = ArrayList<String>(4)
            if (hasSelection) {
                score += 95
                evidence += "selection"
            }
            if (hasSetRefreshing) {
                score += 125
                evidence += "setRefreshing"
            }
            if (hasScrollTabNotify) {
                score += 50
                evidence += "scrollTabNotify"
            }
            if (method.methodName == "w1") score += 30
            if (method.methodName.length <= 3) score += 8
            if (score < 120) return@mapNotNull null
            DexAutoRefreshMatch(method.methodName, score, evidence.joinToString(","))
        }
    }

    fun scanPbLikeAgreeClick(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexPbLikeAgreeClickMatch> =
        withBridge(sourcePaths, logger, "PbLikeAutoReplyHook.AgreeClickDex", emptyList()) { bridge ->
            exactMethods(bridge, ownerClassName, logger).mapNotNull { method ->
                if (method.returnTypeName != "void" || method.paramTypeNames != listOf("android.view.View")) {
                    return@mapNotNull null
                }
                var readsHasAgree = 0
                var readsAgreeType = 0
                var writesHasAgree = 0
                var writesAgreeType = 0
                var hasAgreeDataField = false
                method.usingFields.forEach { using ->
                    val field = using.field
                    if (field.typeName == AGREE_DATA_CLASS) hasAgreeDataField = true
                    if (field.declaredClassName != AGREE_DATA_CLASS) return@forEach
                    when (field.fieldName) {
                        AGREE_DATA_HAS_AGREE_FIELD ->
                            if (using.usingType.isWrite()) writesHasAgree++ else readsHasAgree++
                        AGREE_DATA_AGREE_TYPE_FIELD ->
                            if (using.usingType.isWrite()) writesAgreeType++ else readsAgreeType++
                    }
                }
                val hasViewGetId = method.invokes.any {
                    it.methodName == "getId" && it.declaredClassName == "android.view.View"
                }
                val hasStateWrite = writesHasAgree > 0 && writesAgreeType > 0
                val hasStateRead = readsHasAgree > 0 || readsAgreeType > 0
                if (!hasStateWrite || !hasStateRead || !hasViewGetId) return@mapNotNull null
                var score = writesHasAgree.coerceAtMost(3) * 80 +
                    writesAgreeType.coerceAtMost(3) * 70 + 70
                val evidence = ArrayList<String>(7)
                evidence += "writeHasAgree=$writesHasAgree"
                evidence += "writeAgreeType=$writesAgreeType"
                if (readsHasAgree > 0) {
                    score += 45
                    evidence += "readHasAgree"
                }
                if (readsAgreeType > 0) {
                    score += 40
                    evidence += "readAgreeType"
                }
                if (hasAgreeDataField) {
                    score += 25
                    evidence += "agreeDataField"
                }
                if (method.methodName.length <= 3) score += 8
                if (score < 300) return@mapNotNull null
                DexPbLikeAgreeClickMatch(method.methodName, score, evidence.joinToString(","))
            }
        }

    fun scanAiWriteInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> =
        scanPbInputInit(sourcePaths, ownerClassName, logger, "AiComponentDisableHook.AiWriteDex") { method ->
            var score = 0
            val evidence = ArrayList<String>(5)
            if (method.usingStrings.any { it.contains("AI", ignoreCase = true) || it.contains("ai_write") }) {
                score += 90
                evidence += "aiString"
            }
            if (method.invokes.any { it.declaredClassName == "com.baidu.tieba.hs6" }) {
                score += 120
                evidence += "helper"
            }
            if (method.invokes.any { it.methodName == "setOnClickListener" }) {
                score += 45
                evidence += "click"
            }
            if (method.usingFields.any { it.field.typeName == "android.widget.FrameLayout" }) {
                score += 35
                evidence += "frame"
            }
            score.takeIf { it >= 80 }?.let {
                val hasFrameClick = "frame" in evidence && "click" in evidence
                DexAiComponentInitMatch(
                    method.methodName,
                    it,
                    evidence.joinToString(","),
                    strong = it >= 120 || hasFrameClick,
                )
            }
        }

    fun scanSpriteMemeInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> =
        scanPbInputInit(sourcePaths, ownerClassName, logger, "AiComponentDisableHook.SpriteMemeDex") { method ->
            var score = 0
            val evidence = ArrayList<String>(5)
            if (method.invokes.any { it.declaredClassName == "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan" }) {
                score += 150
                evidence += "spriteMemePan"
            }
            if (method.usingStrings.any { it.contains("SpriteMeme", ignoreCase = true) || it.contains("meme") }) {
                score += 50
                evidence += "memeString"
            }
            if (method.usingFields.any { it.field.typeName == "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan" }) {
                score += 60
                evidence += "panField"
            }
            score.takeIf { it >= 110 }?.let {
                DexAiComponentInitMatch(method.methodName, it, evidence.joinToString(","))
            }
        }

    fun scanImageViewerJumpButtonInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> =
        withBridge(sourcePaths, logger, "AiComponentDisableHook.ImageViewerJumpButtonDex", emptyList()) { bridge ->
            exactMethods(bridge, ownerClassName, logger).mapNotNull { method ->
                if (method.returnTypeName != "void" || method.paramCount != 0) return@mapNotNull null
                var score = 0
                val evidence = ArrayList<String>(5)
                if (method.usingFields.any { it.field.typeName == "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout" }) {
                    score += 120
                    evidence += "layoutField"
                }
                if (method.invokes.any { it.declaredClassName == "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout" }) {
                    score += 90
                    evidence += "layoutInvoke"
                }
                if (method.invokes.any { it.methodName == "setVisibility" }) {
                    score += 45
                    evidence += "visibility"
                }
                if (method.invokes.any { it.methodName == "setOnClickListener" }) {
                    score += 35
                    evidence += "click"
                }
                if (score < 90) return@mapNotNull null
                DexAiComponentInitMatch(method.methodName, score, evidence.joinToString(","))
            }
        }

    fun scanHostDarkModeSwitch(
        sourcePaths: List<String>,
        controllerFields: Map<String, String>,
        logger: ScanLogger? = null,
    ): List<DexHostDarkModeSwitchMatch> =
        withBridge(sourcePaths, logger, "HomeNativeGlassHook.HostDarkModeSwitchDex", emptyList()) { bridge ->
            val prefKeyMethods = findMethodsByString(
                bridge = bridge,
                value = HOST_FOLLOW_SYSTEM_PREF_KEY,
                logger = logger,
                tag = "$TAG.HostDarkModePrefKey",
            )
            val callbackMatches = scanHostDarkModeSwitchFromCallback(
                bridge,
                controllerFields,
                prefKeyMethods,
                logger,
            )
            val controllerPrefMatches = scanHostDarkModeSwitchFromControllerPreference(
                bridge,
                controllerFields,
                prefKeyMethods,
                logger,
            )
            val getterMatches = controllerFields.flatMap { (fieldName, controllerClassName) ->
                exactMethods(bridge, controllerClassName, logger).mapNotNull { method ->
                    if (method.paramCount != 0 ||
                        method.returnTypeName != "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
                    ) {
                        return@mapNotNull null
                    }
                    var score = 0
                    val evidence = ArrayList<String>(4)
                    if (method.hasString(HOST_FOLLOW_SYSTEM_PREF_KEY)) {
                        score += 160
                        evidence += "prefKey"
                    }
                    if (method.invokes.any { it.returnTypeName == "com.baidu.adp.widget.BdSwitchView.BdSwitchView" }) {
                        score += 50
                        evidence += "switchInvoke"
                    }
                    if (method.methodName.length <= 3) score += 8
                    if (score < 40) return@mapNotNull null
                    DexHostDarkModeSwitchMatch(
                        controllerFieldName = fieldName,
                        getterMethodName = method.methodName,
                        score = score,
                        evidence = evidence.joinToString(",").ifBlank { "switchGetter" },
                    )
                }
            }
            (controllerPrefMatches + callbackMatches + getterMatches)
                .groupBy { "${it.controllerFieldName}.${it.getterMethodName}" }
                .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
                .sortedByDescending { it.score }
        }

    fun scanPbAdBid(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexPbAdBidRawScan = withBridge(sourcePaths, logger, "PbAdRequestBlockHook.AdBid.Dex", DexPbAdBidRawScan()) { bridge ->
        val endpointMethods = bridge.findMethod(
            FindMethod.create()
                .searchPackages("com.baidu.tieba")
                .matcher(MethodMatcher.create().addEqString(PB_AD_BID_ENDPOINT)),
        ).toList()

        val modelMatches = endpointMethods.mapNotNull { method ->
            val kind = when {
                extendsClass(bridge, method.declaredClassName, PB_COMMON_REQUEST_MODEL_CLASS, logger) -> "common"
                extendsClass(bridge, method.declaredClassName, PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, logger) -> "pageBrowser"
                else -> null
            } ?: return@mapNotNull null
            DexPbAdBidModelMatch(
                className = method.declaredClassName,
                requestImplMethodName = method.methodName,
                kind = kind,
                score = 260 + if (method.methodName.length <= 3) 8 else 0,
                evidence = "endpoint,$kind",
            )
        }

        val pageBrowserRequestData = exactMethods(bridge, PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, logger)
            .singleOrNull { method ->
                method.returnTypeName == OBJECT_CLASS &&
                    method.paramTypeNames == listOf(KOTLIN_CONTINUATION_CLASS)
            }
            ?.methodName

        DexPbAdBidRawScan(
            modelMatches = modelMatches,
            pageBrowserRequestDataMethodName = pageBrowserRequestData,
        )
    }

    fun scanGameFloatingBar(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexGameFloatingBarMatch? =
        withBridge(sourcePaths, logger, "ForumPageAdBlockHook.GameFloatingBarDex") { bridge ->
            val classes = findClassesByName(bridge, "GameFloatingBarController", logger) +
                exactClassOrNull(bridge, "com.baidu.tieba.forum.controller.GameFloatingBarController", logger)
            classes.filterNotNull().distinctBy { it.name }.flatMap { cls ->
                cls.methods.orEmpty().mapNotNull { method ->
                    if (method.returnTypeName != "void" || method.paramCount != 0) return@mapNotNull null
                    val hasShowSignal = method.methodName == "showFloatingBar" ||
                        method.methodName == "k2" ||
                        method.usingStrings.any { it.contains("showFloatingBar", ignoreCase = true) } ||
                        method.invokes.any { it.declaredClassName == TB_FLOATING_BAR_CLASS }
                    if (!hasShowSignal) return@mapNotNull null
                    val fieldName = cls.fields.orEmpty()
                        .firstOrNull { it.typeName == TB_FLOATING_BAR_CLASS }
                        ?.fieldName
                    var score = 150
                    if (cls.name == "com.baidu.tieba.forum.controller.GameFloatingBarController") score += 90
                    if (method.methodName == "showFloatingBar") score += 60
                    if (fieldName != null) score += 42
                    DexGameFloatingBarMatch(cls.name, method.methodName, fieldName, score, "dexkitShow")
                }
            }.maxWithOrNull(compareBy<DexGameFloatingBarMatch> { it.score }.thenBy { it.controllerClassName })
        }

    fun scanPbPageBrowserAiEmojiCreation(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexPbPageBrowserAiEmojiCreationMatch? =
        withBridge(sourcePaths, logger, "AiComponentDisableHook.PbPageBrowserAiEmojiCreationDex") { bridge ->
            val classes = findClassesByName(bridge, "CommentFloorAiEmojiCreationView", logger) +
                exactClassOrNull(bridge, PAGE_BROWSER_AI_EMOJI_VIEW_CLASS, logger)
            classes.filterNotNull().distinctBy { it.name }.flatMap { cls ->
                cls.methods.orEmpty().mapNotNull { method ->
                    if (method.returnTypeName != "void" || method.paramCount != 1) return@mapNotNull null
                    var score = 130
                    val evidence = ArrayList<String>(4)
                    if (method.methodName == "bindData") {
                        score += 90
                        evidence += "bindData"
                    }
                    if (method.methodName.length <= 2) {
                        score += 16
                        evidence += "obfuscatedBind"
                    }
                    if (method.paramTypeNames.firstOrNull()?.contains("AiEmojiCreation") == true) {
                        score += 70
                        evidence += "state"
                    }
                    if (cls.name == PAGE_BROWSER_AI_EMOJI_VIEW_CLASS) {
                        score += 90
                        evidence += "stableClass"
                    }
                    DexPbPageBrowserAiEmojiCreationMatch(
                        viewClassName = cls.name,
                        bindMethodName = method.methodName,
                        score = score,
                        evidence = evidence.joinToString(",").ifBlank { "shape" },
                    )
                }
            }.maxWithOrNull(compareBy<DexPbPageBrowserAiEmojiCreationMatch> { it.score }.thenBy { it.viewClassName })
        }

    fun scanEnterForumCapsules(
        sourcePaths: List<String>,
        ownerClassNames: List<String>,
        logger: ScanLogger? = null,
    ): Map<String, List<DexEnterForumCapsuleMethodMatch>> =
        withBridge(sourcePaths, logger, "HomeNativeGlassHook.EnterForumCapsuleDex", emptyMap()) { bridge ->
            ownerClassNames.associateWith { owner ->
                exactMethods(bridge, owner, logger).flatMap { method ->
                    if (method.returnTypeName != "void" || method.paramCount != 0) return@flatMap emptyList()
                    scoreEnterForumCapsuleMethod(method)
                }
            }.filterValues { it.isNotEmpty() }
        }

    fun scanEnterForumCapsule(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexEnterForumCapsuleMethodMatch> =
        scanEnterForumCapsules(sourcePaths, listOf(ownerClassName), logger)[ownerClassName].orEmpty()

    private fun scanShareIconFromAddOutsideCallers(
        bridge: DexKitBridge,
        cl: ClassLoader,
        resolveDrawableResource: (String) -> Int?,
        logger: ScanLogger?,
    ): DexShareIconMatch? {
        return try {
            val exactAddOutside = exactMethods(bridge, SHARE_DIALOG_CONFIG_CLASS, logger)
                .singleOrNull { method ->
                    method.methodName == SHARE_DIALOG_ADD_OUTSIDE_METHOD &&
                        method.returnTypeName == "void" &&
                        method.paramTypeNames == listOf(
                            "int",
                            "int",
                            "android.view.View\$OnClickListener",
                        )
                }
            val callers = exactAddOutside?.callers.orEmpty().toList()
            val exactQuery = bridge.findMethod(
                FindMethod.create()
                    .searchPackages("com.baidu.tieba")
                    .matcher(
                        MethodMatcher.create()
                            .addInvoke(
                                MethodMatcher.create()
                                    .declaredClass(SHARE_DIALOG_CONFIG_CLASS)
                                    .name(SHARE_DIALOG_ADD_OUTSIDE_METHOD)
                                    .returnType("void")
                                    .paramTypes(
                                        "int",
                                        "int",
                                        "android.view.View\$OnClickListener",
                                    ),
                            ),
                    ),
            ).toList()
            val broadQuery = bridge.findMethod(
                FindMethod.create()
                    .searchPackages("com.baidu.tieba")
                    .matcher(
                        MethodMatcher.create()
                            .addInvoke(
                                MethodMatcher.create()
                                    .declaredClass(SHARE_DIALOG_CONFIG_CLASS)
                                    .name(SHARE_DIALOG_ADD_OUTSIDE_METHOD),
                            ),
                    ),
            ).toList()
            (callers + exactQuery + broadQuery)
                .distinctBy { it.methodSign }
                .mapNotNull { method ->
                    val drawableField = method.usingFields
                        .asSequence()
                        .map { it.field }
                        .filter { field -> field.declaredClassName == TIEBA_DRAWABLE_CLASS }
                        .maxWithOrNull(
                            compareBy<FieldData> { scoreShareDrawableField(it.fieldName) }
                                .thenBy { -it.fieldName.length }
                                .thenBy { it.fieldName },
                        )
                        ?.takeIf { scoreShareDrawableField(it.fieldName) > 0 }
                    val drawable = drawableField?.let { field ->
                        resolveStaticIntField(
                            cl,
                            field.declaredClassName,
                            field.fieldName,
                        )?.takeIf { isDrawableResourceId(it) }
                    } ?: resolveDrawableResource("icon_unite_share_baf")?.takeIf { isDrawableResourceId(it) }
                        ?: return@mapNotNull null
                    val score = 180 +
                        (drawableField?.let { scoreShareDrawableField(it.fieldName) } ?: 110) +
                        scoreInvokes(method, SHARE_DIALOG_ADD_OUTSIDE_METHOD, 70)
                    DexShareIconMatch(
                        ownerClassName = method.declaredClassName,
                        ownerMethodName = method.methodName,
                        resId = drawable,
                        score = score,
                    )
                }
                .maxWithOrNull(compareBy<DexShareIconMatch> { it.score }.thenBy { it.ownerClassName })
        } catch (t: Throwable) {
            recordIssue(logger, "$TAG.ShareIconAddOutside", HookSymbolScanDiagnostics.formatScanException(t))
            null
        }
    }

    private fun scanHostDarkModeSwitchFromCallback(
        bridge: DexKitBridge,
        controllerFields: Map<String, String>,
        prefKeyMethods: List<MethodData>,
        logger: ScanLogger?,
    ): List<DexHostDarkModeSwitchMatch> {
        return try {
            val controllerByClass = controllerFields.entries.groupBy({ it.value }, { it.key })
            (
                exactMethods(bridge, "com.baidu.tieba.setting.more.MoreActivity", logger) +
                    prefKeyMethods.filter { it.declaredClassName == "com.baidu.tieba.setting.more.MoreActivity" }
                )
                .distinctBy { it.methodSign }
                .filter { method ->
                    method.hasString(HOST_FOLLOW_SYSTEM_PREF_KEY) &&
                        method.returnTypeName == "void" &&
                        method.paramCount == 2 &&
                        method.paramTypeNames.firstOrNull()?.endsWith("View") == true &&
                        method.paramTypeNames.getOrNull(1) ==
                        "com.baidu.adp.widget.BdSwitchView.BdSwitchView\$SwitchState"
                }
                .flatMap { callback ->
                    callback.invokes.withIndex().mapNotNull { (invokeIndex, invoke) ->
                        if (invoke.paramCount != 0 ||
                            invoke.returnTypeName != "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
                        ) {
                            return@mapNotNull null
                        }
                        val fieldNames = controllerByClass[invoke.declaredClassName].orEmpty()
                        fieldNames.map { fieldName ->
                            var score = 360
                            val evidence = ArrayList<String>(4)
                            evidence += "callback=${callback.methodName}"
                            evidence += "prefKey"
                            evidence += "getterInvoke"
                            score += invokeIndex.coerceAtMost(4) * 40
                            evidence += "invokeIndex=$invokeIndex"
                            if (invoke.methodName.length <= 3) score += 8
                            DexHostDarkModeSwitchMatch(
                                controllerFieldName = fieldName,
                                getterMethodName = invoke.methodName,
                                score = score,
                                evidence = evidence.joinToString(","),
                                callbackMethodName = callback.methodName,
                            )
                        }
                    }.flatten()
                }
        } catch (t: Throwable) {
            recordIssue(logger, "$TAG.HostDarkModeCallback", HookSymbolScanDiagnostics.formatScanException(t))
            emptyList()
        }
    }

    private fun scanHostDarkModeSwitchFromControllerPreference(
        bridge: DexKitBridge,
        controllerFields: Map<String, String>,
        prefKeyMethods: List<MethodData>,
        logger: ScanLogger?,
    ): List<DexHostDarkModeSwitchMatch> {
        return try {
            controllerFields.flatMap { (fieldName, controllerClassName) ->
                val methods = (
                    exactMethods(bridge, controllerClassName, logger) +
                        prefKeyMethods.filter { it.declaredClassName == controllerClassName }
                    ).distinctBy { it.methodSign }
                val preferredSwitchFieldScores = methods
                    .filter { method -> method.hasString(HOST_FOLLOW_SYSTEM_PREF_KEY) }
                    .flatMap { method -> method.usingFields.map { it.field } }
                    .filter { field ->
                        field.declaredClassName == controllerClassName &&
                            field.typeName.contains("Switch", ignoreCase = true)
                    }
                    .groupingBy { it.fieldName }
                    .eachCount()
                if (preferredSwitchFieldScores.isEmpty()) {
                    return@flatMap emptyList()
                }
                methods.mapNotNull { method ->
                    if (method.paramCount != 0 ||
                        method.returnTypeName != "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
                    ) {
                        return@mapNotNull null
                    }
                    val getterField = method.usingFields
                        .asSequence()
                        .map { it.field }
                        .firstOrNull { field ->
                            field.declaredClassName == controllerClassName &&
                                field.fieldName in preferredSwitchFieldScores.keys
                        } ?: return@mapNotNull null
                    val fieldScore = preferredSwitchFieldScores[getterField.fieldName] ?: 0
                    var score = 520 + fieldScore.coerceAtMost(4) * 45
                    val evidence = ArrayList<String>(4)
                    evidence += "controllerPrefKey"
                    evidence += "switchField=${getterField.fieldName}x$fieldScore"
                    if (method.methodName.length <= 3) score += 8
                    DexHostDarkModeSwitchMatch(
                        controllerFieldName = fieldName,
                        getterMethodName = method.methodName,
                        score = score,
                        evidence = evidence.joinToString(","),
                    )
                }
            }
        } catch (t: Throwable) {
            recordIssue(logger, "$TAG.HostDarkModeControllerPref", HookSymbolScanDiagnostics.formatScanException(t))
            emptyList()
        }
    }

    private fun scanPbInputInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger?,
        tag: String,
        scorer: (MethodData) -> DexAiComponentInitMatch?,
    ): List<DexAiComponentInitMatch> = withBridge(sourcePaths, logger, tag, emptyList()) { bridge ->
        exactMethods(bridge, ownerClassName, logger)
            .filter { it.returnTypeName == "void" && it.paramTypeNames == listOf("android.content.Context") }
            .mapNotNull(scorer)
    }

    private fun scoreEnterForumCapsuleMethod(method: MethodData): List<DexEnterForumCapsuleMethodMatch> {
        val fields = method.usingFields
        val invokes = method.invokes.toList()
        val out = ArrayList<DexEnterForumCapsuleMethodMatch>(2)

        val putView = bestFieldName(fields, write = true) {
            it.typeName == "android.view.View" || it.typeName == "android.view.ViewGroup" || it.typeName.endsWith("Layout")
        }
        val getView = bestFieldName(fields, write = false) {
            it.typeName == "android.view.View" || it.typeName == "android.view.ViewGroup" || it.typeName.endsWith("Layout")
        }
        val getString = bestFieldName(fields, write = false) { it.typeName == "java.lang.String" }
        val hasAddCustomView = invokes.any { it.methodName == "addCustomView" }
        val hasFindViewById = invokes.any { it.methodName == "findViewById" }
        val hasClick = invokes.any { it.methodName == "setOnClickListener" }
        val hasTextEmpty = invokes.any {
            it.methodName == "isEmpty" && it.declaredClassName == "android.text.TextUtils"
        }
        val hasBackground = invokes.any {
            it.methodName == "setBackgroundResource" ||
                it.declaredClassName == "com.baidu.tbadk.core.elementsMaven.EMManager"
        }
        val hasVisibility = invokes.any { it.methodName == "setVisibility" }

        if (putView != null && hasAddCustomView && hasFindViewById) {
            var score = 245
            val evidence = ArrayList<String>(4)
            evidence += "addCustomView"
            evidence += "findViewById"
            evidence += "viewField=$putView"
            if (hasClick) {
                score += 40
                evidence += "click"
            }
            if (method.methodName == "r") score += 16
            out += DexEnterForumCapsuleMethodMatch(
                ownerMethodName = method.methodName,
                kind = DexEnterForumCapsuleMethodKind.INIT,
                score = score,
                evidence = evidence.joinToString(","),
                viewFieldName = putView,
            )
        }
        if (getView != null && getString != null && (hasTextEmpty || hasVisibility) && hasBackground) {
            var score = 235
            val evidence = ArrayList<String>(5)
            evidence += "viewField=$getView"
            evidence += "titleField=$getString"
            if (hasTextEmpty) evidence += "isEmpty"
            if (hasBackground) evidence += "background"
            if (hasVisibility) evidence += "visibility"
            if (method.methodName == "D") score += 16
            out += DexEnterForumCapsuleMethodMatch(
                ownerMethodName = method.methodName,
                kind = DexEnterForumCapsuleMethodKind.REFRESH,
                score = score,
                evidence = evidence.joinToString(","),
                viewFieldName = getView,
                titleFieldName = getString,
            )
        }
        return out
    }

    private inline fun <T> withBridge(
        sourcePaths: List<String>,
        logger: ScanLogger?,
        tag: String,
        fallback: T,
        block: (DexKitBridge) -> T,
    ): T {
        val cachedBridge = HookSymbolScanSession.get()?.dexKitBridge(sourcePaths, logger)
        val bridge = cachedBridge ?: DexKitBridgeProvider.openFirstAvailable(sourcePaths, logger) ?: return fallback
        return try {
            if (cachedBridge != null) {
                block(bridge.bridge)
            } else {
                bridge.use { block(it.bridge) }
            }
        } catch (t: Throwable) {
            recordIssue(logger, tag, HookSymbolScanDiagnostics.formatScanException(t))
            fallback
        }
    }

    private fun <T> withBridge(
        sourcePaths: List<String>,
        logger: ScanLogger?,
        tag: String,
        block: (DexKitBridge) -> T?,
    ): T? = withBridge(sourcePaths, logger, tag, null, block)

    private fun exactMethods(bridge: DexKitBridge, className: String, logger: ScanLogger?): List<MethodData> {
        return try {
            bridge.getClassData(className)?.methods.orEmpty().toList()
        } catch (t: Throwable) {
            recordIssue(logger, "$TAG.ExactMethods.$className", HookSymbolScanDiagnostics.formatScanException(t))
            emptyList()
        }
    }

    private fun findMethodsByString(
        bridge: DexKitBridge,
        value: String,
        logger: ScanLogger?,
        tag: String,
    ): List<MethodData> {
        return try {
            bridge.findMethod(
                FindMethod.create()
                    .searchPackages("com.baidu.tieba")
                    .matcher(MethodMatcher.create().addEqString(value)),
            ).toList()
        } catch (t: Throwable) {
            recordIssue(logger, tag, HookSymbolScanDiagnostics.formatScanException(t))
            emptyList()
        }
    }

    private fun exactClassOrNull(
        bridge: DexKitBridge,
        className: String,
        logger: ScanLogger?,
    ): org.luckypray.dexkit.result.ClassData? {
        return try {
            bridge.getClassData(className)
        } catch (t: Throwable) {
            HookSymbolScanDiagnostics.log(logger, "$TAG: exact class unavailable $className")
            null
        }
    }

    private fun findClassesByName(
        bridge: DexKitBridge,
        classNamePart: String,
        logger: ScanLogger?,
    ): List<org.luckypray.dexkit.result.ClassData> {
        return try {
            bridge.findClass(
                FindClass.create()
                    .searchPackages("com.baidu.tieba")
                    .matcher(
                        ClassMatcher.create().className(classNamePart, StringMatchType.Contains),
                    ),
            ).toList()
        } catch (t: Throwable) {
            recordIssue(logger, "$TAG.FindClass.$classNamePart", HookSymbolScanDiagnostics.formatScanException(t))
            emptyList()
        }
    }

    private fun extendsClass(
        bridge: DexKitBridge,
        className: String,
        expectedSuperClass: String,
        logger: ScanLogger?,
    ): Boolean {
        var current = exactClassOrNull(bridge, className, logger)
        repeat(12) {
            if (current == null) return false
            if (current?.name == expectedSuperClass) return true
            current = try {
                current?.superClass
            } catch (t: Throwable) {
                return false
            }
        }
        return false
    }

    private fun bestFieldName(
        fields: List<UsingFieldData>,
        write: Boolean,
        predicate: (FieldData) -> Boolean,
    ): String? {
        return fields.asSequence()
            .filter { if (write) it.usingType.isWrite() else it.usingType.isRead() }
            .map { it.field }
            .filter(predicate)
            .groupingBy { it.fieldName }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            ?.key
    }

    private fun scoreInvokes(method: MethodData, name: String, score: Int): Int =
        if (method.invokes.any { it.methodName == name }) score else 0

    private fun scoreClassName(value: String, signal: String, score: Int): Int =
        if (value.contains(signal, ignoreCase = true)) score else 0

    private fun MethodData.hasString(value: String): Boolean =
        usingStrings.any { it == value || it.contains(value) }

    private fun scoreShareDrawableField(name: String): Int {
        val lower = name.lowercase()
        if (!lower.contains("share")) return 0
        var score = 60
        if (lower.startsWith("icon_")) score += 20
        if (lower.contains("unite")) score += 40
        if (lower.contains("baf")) score += 30
        if (lower.contains("pb")) score += 30
        if (lower.contains("bottom")) score += 20
        if (lower.contains("pure")) score += 16
        if (lower.contains("wechat")) score -= 100
        if (lower.contains("weibo")) score -= 100
        if (lower.contains("qzone")) score -= 100
        if (lower.contains("qq")) score -= 100
        return score.coerceAtLeast(0)
    }

    private fun resolveStaticIntField(cl: ClassLoader, className: String, fieldName: String): Int? {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return null
        return runCatching {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.getInt(null)
        }.getOrNull()
    }

    private fun isDrawableResourceId(value: Int): Boolean {
        if ((value ushr 24) != 0x7F) return false
        return value != 0
    }

    private fun recordIssue(logger: ScanLogger?, tag: String, raw: String) {
        val detail = HookSymbolScanDiagnostics.sanitizeScanStatusText(raw)
        val errors = HookSymbolScanSession.get()?.scanErrors
        if (errors != null) {
            HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
        } else {
            HookSymbolScanDiagnostics.log(logger, "$tag: $detail")
        }
    }
}
