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
import java.lang.reflect.Modifier

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
    private const val HEAD_PENDANT_VIEW_CLASS = "com.baidu.tbadk.core.view.HeadPendantView"
    private const val TB_FLOATING_BAR_CLASS = "com.baidu.tieba.feed.component.view.TbFloatingBar"
    private const val PAGE_BROWSER_AI_EMOJI_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val HOST_FOLLOW_SYSTEM_PREF_KEY = "key_is_follow_system_mode"
    private const val SHARE_DIALOG_CONFIG_CLASS =
        "com.baidu.tbadk.core.atomData.ShareDialogConfig"
    private const val SHARE_DIALOG_ADD_OUTSIDE_METHOD = "addOutsideTextView"
    private const val TIEBA_DRAWABLE_CLASS = "com.baidu.tieba.R\$drawable"
    private const val JAVA_LIST_CLASS = "java.util.List"
    private const val JSON_OBJECT_CLASS = "org.json.JSONObject"
    private const val EASTER_EGG_DATA_CLASS = "com.baidu.tieba.easteregg.data.EasterEggAdData"
    private const val EASTER_EGG_DATA_HOLDER_CLASS =
        "com.baidu.tieba.easteregg.data.EasterEggAdDataHolder"

    private const val LIST_UTILS_CLASS = "com.baidu.tbadk.core.util.ListUtils"
    private const val ORIGINAL_IMAGE_DOWNLOAD_TIP_PREF_KEY = "original_img_down_tip"
    private const val FREE_COPY_LONG_PRESS_STAT_KEY = "card_long_click"
    private const val FREE_COPY_POST_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.PostData"
    private const val FREE_COPY_THREAD_DATA_CLASS = "com.baidu.tbadk.core.data.ThreadData"
    private const val TEXT_VIEW_CLASS = "android.widget.TextView"
    private const val REC_PERSONALIZE_MODEL_CLASS =
        "com.baidu.tieba.homepage.personalize.model.RecPersonalizePageModel"
    private const val REC_PERSONALIZE_REQUEST_CLASS =
        "com.baidu.tieba.homepage.personalize.data.RecPersonalizeRequest"
    private const val NET_MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val REC_HTTP_SENDER_CLASS = "com.baidu.tieba.p50"
    private const val LOW_SCORE_SCHEDULER_CLASS = "com.baidu.tieba.parser.LowScoreScheduler"
    private const val COLD_START_DELAY_SCHEDULE_CLASS = "com.baidu.searchbox.launch.ColdStartDelaySchedule"

    fun scanHomeBottomEasterEggParser(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): HomeBottomEasterEggAdScanSymbols =
        withBridge(
            sourcePaths,
            logger,
            "HomeBottomEasterEggAdHook.ParserDex",
            HomeBottomEasterEggAdScanSymbols(),
        ) { bridge ->
            val matches = findMethodsByString(
                bridge,
                "floating_icon",
                logger,
                "HomeBottomEasterEggAdHook.FindFloatingIcon",
            ).asSequence()
                .filter { method -> method.hasString("easter_egg") }
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnTypeName == "void" &&
                        method.paramTypeNames == listOf(JSON_OBJECT_CLASS)
                }
                .filter { method ->
                    method.invokes.any { it.declaredClassName.startsWith(EASTER_EGG_DATA_CLASS) } &&
                        method.invokes.any { it.declaredClassName.startsWith(EASTER_EGG_DATA_HOLDER_CLASS) }
                }
                .distinctBy { method ->
                    method.declaredClassName + "#" + method.methodName + "(" +
                        method.paramTypeNames.joinToString(",") + ")"
                }
                .toList()

            when (matches.size) {
                1 -> HomeBottomEasterEggAdScanSymbols(
                    parserClass = matches.single().declaredClassName,
                    parserMethod = matches.single().methodName,
                )
                0 -> {
                    HookSymbolScanDiagnostics.log(
                        logger,
                        "HomeBottomEasterEggAdHook.ParserDex: no unique structural candidate",
                    )
                    HomeBottomEasterEggAdScanSymbols()
                }
                else -> {
                    recordIssue(
                        logger,
                        "HomeBottomEasterEggAdHook.ParserDex",
                        "ambiguous candidates=${matches.joinToString { it.declaredClassName + "#" + it.methodName }}",
                    )
                    HomeBottomEasterEggAdScanSymbols()
                }
            }
        }

    fun scanFreeCopyPostDataCopy(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexFreeCopyMethodMatch> =
        withBridge(sourcePaths, logger, "FreeCopyHook.NativeCopyDex", emptyList()) { bridge ->
            exactMethods(bridge, ownerClassName, logger).mapNotNull { method ->
                if (
                    Modifier.isStatic(method.modifiers) ||
                    method.returnTypeName != "void" ||
                    method.paramCount != 0
                ) {
                    return@mapNotNull null
                }
                val clipboardCalls = method.invokes.filter { invoked ->
                    invoked.declaredClassName in setOf(
                        "android.text.ClipboardManager",
                        "android.content.ClipboardManager",
                    ) && invoked.methodName in setOf("setText", "setPrimaryClip")
                }
                if (clipboardCalls.isEmpty()) return@mapNotNull null
                DexFreeCopyMethodMatch(
                    ownerClassName = method.declaredClassName,
                    methodName = method.methodName,
                    returnTypeName = method.returnTypeName,
                    parameterTypeNames = method.paramTypeNames,
                )
            }
        }

    fun scanFreeCopyPostLongPress(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): List<DexFreeCopyMethodMatch> =
        withBridge(sourcePaths, logger, "FreeCopyHook.LongPressDex", emptyList()) { bridge ->
            findMethodsByString(
                bridge = bridge,
                value = FREE_COPY_LONG_PRESS_STAT_KEY,
                logger = logger,
                tag = "$TAG.FreeCopyLongPress",
            ).mapNotNull { method ->
                val params = method.paramTypeNames
                if (
                    Modifier.isStatic(method.modifiers) ||
                    method.returnTypeName != "boolean" ||
                    params.size !in 2..4 ||
                    params.firstOrNull() != "android.view.View"
                ) {
                    return@mapNotNull null
                }
                val invokes = method.invokes.toList()
                val readsViewTag = invokes.any {
                    it.declaredClassName == "android.view.View" && it.methodName == "getTag"
                }
                val readsSparseArray = invokes.any {
                    it.declaredClassName == "android.util.SparseArray" && it.methodName == "get"
                }
                val usesPostData = invokes.any { it.declaredClassName == FREE_COPY_POST_DATA_CLASS } ||
                    method.usingFields.any { it.field.typeName == FREE_COPY_POST_DATA_CLASS }
                if (!readsViewTag || !readsSparseArray || !usesPostData) return@mapNotNull null
                val postDataIntNoArgMethodSpecs = invokes.asSequence()
                    .filter { invoked ->
                        invoked.declaredClassName == FREE_COPY_POST_DATA_CLASS &&
                            !Modifier.isStatic(invoked.modifiers) &&
                            invoked.returnTypeName == "int" &&
                            invoked.paramCount == 0
                    }
                    .map { invoked ->
                        invoked.methodName + "|" + invoked.returnTypeName + "|" +
                            invoked.paramTypeNames.joinToString(",")
                    }
                    .distinct()
                    .sorted()
                    .toList()
                DexFreeCopyMethodMatch(
                    ownerClassName = method.declaredClassName,
                    methodName = method.methodName,
                    returnTypeName = method.returnTypeName,
                    parameterTypeNames = params,
                    postDataIntNoArgMethodSpecs = postDataIntNoArgMethodSpecs,
                )
            }.distinctBy { match ->
                match.ownerClassName + "#" + match.methodName + "|" +
                    match.parameterTypeNames.joinToString(",")
            }
        }

    fun scanFreeCopyPostTitleLongPress(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): List<DexFreeCopyTitleMatch> =
        withBridge(sourcePaths, logger, "FreeCopyHook.TitleLongPressDex", emptyList()) { bridge ->
            val anchors = try {
                bridge.findMethod(
                    FindMethod.create()
                        .searchPackages("com.baidu.tieba")
                        .matcher(
                            MethodMatcher.create()
                                .addInvoke(
                                    MethodMatcher.create()
                                        .declaredClass(FREE_COPY_THREAD_DATA_CLASS)
                                        .name("getTitle")
                                        .returnType("java.lang.String")
                                        .paramTypes(),
                                ),
                        ),
                ).toList()
            } catch (t: Throwable) {
                recordIssue(
                    logger,
                    "$TAG.FreeCopyTitleAnchor",
                    HookSymbolScanDiagnostics.formatScanException(t),
                )
                emptyList()
            }
            anchors.flatMap { anchor ->
                if (
                    Modifier.isStatic(anchor.modifiers) ||
                    anchor.returnTypeName != "void" ||
                    anchor.paramCount != 1
                ) {
                    return@flatMap emptyList()
                }
                val invokes = anchor.invokes.toList()
                val hasSetText = invokes.any { invoked ->
                    invoked.declaredClassName == TEXT_VIEW_CLASS && invoked.methodName == "setText"
                }
                val hasSetMaxLines = invokes.any { invoked ->
                    invoked.declaredClassName == TEXT_VIEW_CLASS && invoked.methodName == "setMaxLines"
                }
                val hasSetEllipsize = invokes.any { invoked ->
                    invoked.declaredClassName == TEXT_VIEW_CLASS && invoked.methodName == "setEllipsize"
                }
                if (!hasSetText || !hasSetMaxLines || !hasSetEllipsize) {
                    return@flatMap emptyList()
                }

                val ownerClassName = anchor.declaredClassName
                val pageDataClassName = anchor.paramTypeNames.single()
                val titleFields = anchor.usingFields
                    .asSequence()
                    .map { it.field }
                    .filter { field ->
                        field.declaredClassName == ownerClassName &&
                            (field.typeName == TEXT_VIEW_CLASS || field.typeName.endsWith("TextView"))
                    }
                    .distinctBy { it.fieldName }
                    .toList()
                if (titleFields.size != 1) {
                    HookSymbolScanDiagnostics.log(
                        logger,
                        "$TAG.FreeCopyTitleAnchor: ${ownerClassName}.${anchor.methodName} " +
                            "textFields=${titleFields.map { it.fieldName }}",
                    )
                    return@flatMap emptyList()
                }
                val titleField = titleFields.single()
                val bindMethods = exactMethods(bridge, ownerClassName, logger)
                    .filter { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnTypeName == "void" &&
                            method.paramTypeNames == listOf(pageDataClassName)
                    }
                    .distinctBy { it.methodSign }
                if (bindMethods.isEmpty()) {
                    HookSymbolScanDiagnostics.log(
                        logger,
                        "$TAG.FreeCopyTitleAnchor: no bind method for $ownerClassName",
                    )
                    return@flatMap emptyList()
                }

                exactMethods(bridge, pageDataClassName, logger).mapNotNull { getter ->
                    if (
                        Modifier.isStatic(getter.modifiers) ||
                        getter.returnTypeName != FREE_COPY_POST_DATA_CLASS ||
                        getter.paramCount != 0
                    ) {
                        return@mapNotNull null
                    }
                    val postDataIntNoArgMethodSpecs = getter.invokes.asSequence()
                        .filter { invoked ->
                            invoked.declaredClassName == FREE_COPY_POST_DATA_CLASS &&
                                !Modifier.isStatic(invoked.modifiers) &&
                                invoked.returnTypeName == "int" &&
                                invoked.paramCount == 0
                        }
                        .map(::encodeDexMethodSpec)
                        .distinct()
                        .sorted()
                        .toList()
                    if (postDataIntNoArgMethodSpecs.isEmpty()) return@mapNotNull null
                    DexFreeCopyTitleMatch(
                        ownerClassName = ownerClassName,
                        bindMethodSpecs = bindMethods.map(::encodeDexMethodSpec).sorted(),
                        textFieldName = titleField.fieldName,
                        pageDataClassName = pageDataClassName,
                        postDataMethodSpec = encodeDexMethodSpec(getter),
                        postDataIntNoArgMethodSpecs = postDataIntNoArgMethodSpecs,
                        evidence = "threadTitle,textViewBind,maxLines,ellipsize," +
                            "controllerDataMethods=${bindMethods.size}," +
                            "postIntNoArg=${postDataIntNoArgMethodSpecs.size}",
                    )
                }
            }.distinctBy { match ->
                match.ownerClassName + "#" + match.textFieldName + "#" +
                    match.pageDataClassName + "#" + match.postDataMethodSpec
            }
        }

    fun scanFreeCopyFirstFloorPostGetter(
        sourcePaths: List<String>,
        ownerClassName: String,
        postDataClassName: String,
        floorMethodSpec: String,
        logger: ScanLogger? = null,
    ): List<DexFreeCopyMethodMatch> {
        val floorParts = floorMethodSpec.split('|', limit = 3)
        if (floorParts.size != 3) return emptyList()
        val floorMethodName = floorParts[0]
        return withBridge(
            sourcePaths,
            logger,
            "FreeCopyHook.WebViewLongPressDex",
            emptyList(),
        ) { bridge ->
            exactMethods(bridge, ownerClassName, logger).mapNotNull { method ->
                if (
                    Modifier.isStatic(method.modifiers) ||
                    method.returnTypeName != postDataClassName ||
                    method.paramCount != 0
                ) {
                    return@mapNotNull null
                }
                val readsFloor = method.invokes.any { invoked ->
                    invoked.declaredClassName == postDataClassName &&
                        invoked.methodName == floorMethodName &&
                        invoked.returnTypeName == "int" &&
                        invoked.paramCount == 0
                }
                if (!readsFloor) return@mapNotNull null
                DexFreeCopyMethodMatch(
                    ownerClassName = method.declaredClassName,
                    methodName = method.methodName,
                    returnTypeName = method.returnTypeName,
                    parameterTypeNames = method.paramTypeNames,
                )
            }
        }
    }

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

    /**
     * Scans [REC_PERSONALIZE_MODEL_CLASS] for the method that builds and sends a
     * [REC_PERSONALIZE_REQUEST_CLASS] network request. Every personalize feed refresh
     * path (including the cold-start B0() branch that bypasses the w1() UI trigger)
     * ultimately funnels into this request method, so blocking it covers all refresh
     * entry points regardless of the isColdNetDataOpt() AB split.
     */
    fun scanRecPersonalizeRequestMethods(
        sourcePaths: List<String>,
        ownerClassName: String = REC_PERSONALIZE_MODEL_CLASS,
        requestClassName: String = REC_PERSONALIZE_REQUEST_CLASS,
        logger: ScanLogger? = null,
    ): List<DexRecPersonalizeRequestMatch> =
        withBridge(sourcePaths, logger, "AutoRefreshHook.RecRequestDex", emptyList()) { bridge ->
            val methods = exactMethods(bridge, ownerClassName, logger)
            // Feed refresh funnels into two final request methods inside
            // RecPersonalizePageModel: m() sends through MessageManager
            // (sendMessage), r() issues an HTTP direct request (p50.g). Both
            // must be blocked so the cold-start refresh is covered regardless
            // of which transport the host picks.
            methods.mapNotNull { method ->
                if (Modifier.isStatic(method.modifiers) || method.returnTypeName != "void") {
                    return@mapNotNull null
                }
                val invokes = method.invokes.toList()
                val sendsMessage = invokes.any { invoked ->
                    invoked.declaredClassName == NET_MESSAGE_MANAGER_CLASS &&
                        invoked.methodName == "sendMessage"
                }
                val httpDirectSend = invokes.any { invoked ->
                    invoked.declaredClassName == REC_HTTP_SENDER_CLASS &&
                        invoked.methodName == "g"
                }
                if (!sendsMessage && !httpDirectSend) return@mapNotNull null
                val evidence = buildList {
                    if (sendsMessage) add("sendMessage")
                    if (httpDirectSend) add("httpDirectSend")
                }.joinToString(",")
                DexRecPersonalizeRequestMatch(
                    ownerMethodName = method.methodName,
                    paramTypes = method.paramTypeNames,
                    evidence = evidence,
                )
            }
        }

    /**
     * Scans LowScoreScheduler for the boolean(String) method used to decide
     * whether a host task is blocked by the low-score scheduler. When the host
     * disables home caching (disable_home_cache), the cold-start feed has no
     * cached data to render after the auto-refresh is blocked. Returning false
     * for that taskId restores the host's own home-cache read/write path so the
     * last-seen feed can be shown without a network refresh.
     */
    fun scanHomeCacheRestoreMethod(
        sourcePaths: List<String>,
        ownerClassName: String = LOW_SCORE_SCHEDULER_CLASS,
        logger: ScanLogger? = null,
    ): DexHomeCacheRestoreMatch? =
        withBridge(sourcePaths, logger, "AutoRefreshHook.CacheRestoreDex", null) { bridge ->
            val methods = exactMethods(bridge, ownerClassName, logger)
            val candidates = methods.filter { method ->
                if (Modifier.isStatic(method.modifiers)) {
                    return@filter false
                }
                if (method.returnTypeName != "boolean" || method.paramTypeNames != listOf("java.lang.String")) {
                    return@filter false
                }
                val invokes = method.invokes.toList()
                invokes.any { invoked ->
                    invoked.declaredClassName == COLD_START_DELAY_SCHEDULE_CLASS
                }
            }
            if (candidates.size != 1) {
                val details = candidates.joinToString(",") { it.methodName }.ifBlank { "-" }
                HookSymbolScanDiagnostics.log(
                    logger,
                    "cacheRestoreDex: expected=1 actual=${candidates.size} " +
                        "owner=${ownerClassName} candidates=$details",
                )
                return@withBridge null
            }
            val match = candidates.single()
            DexHomeCacheRestoreMatch(
                ownerMethodName = match.methodName,
                evidence = "coldStartDelayScheduleRef",
            )
        }

    fun scanOriginalImageMethods(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): DexOriginalImageMethodsMatch? =
        withBridge(sourcePaths, logger, "DefaultOriginalImageHook.MethodsDex") { bridge ->
            val methods = exactMethods(bridge, ownerClassName, logger)
            val triggerCandidates = methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnTypeName == "void" &&
                    method.paramCount == 0 &&
                    method.hasString(ORIGINAL_IMAGE_DOWNLOAD_TIP_PREF_KEY)
            }
            if (triggerCandidates.size != 1) {
                HookSymbolScanDiagnostics.log(
                    logger,
                    "origImageMethodsDex: trigger expected=1 actual=${triggerCandidates.size} " +
                        "candidates=${triggerCandidates.joinToString(",") { it.methodName }.ifBlank { "-" }}",
                )
                return@withBridge null
            }
            val triggerMethod = triggerCandidates.single()

            val directStartCandidates = triggerMethod.invokes
                .asSequence()
                .filter { method ->
                    method.declaredClassName == ownerClassName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnTypeName == "void" &&
                        method.paramTypeNames == listOf("java.lang.String")
                }
                .distinctBy { it.methodSign }
                .toList()
            val directStartMethod = directStartCandidates.singleOrNull()
            if (directStartCandidates.size != 1) {
                HookSymbolScanDiagnostics.log(
                    logger,
                    "origImageMethodsDex: directStart expected=1 actual=${directStartCandidates.size} " +
                        "candidates=${directStartCandidates.joinToString(",") { it.methodName }.ifBlank { "-" }}",
                )
            }

            val primaryReadyCandidates = methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnTypeName == "void" &&
                    method.paramCount == 0 &&
                    method.invokes.any { invoked ->
                        invoked.declaredClassName == ownerClassName &&
                            !Modifier.isStatic(invoked.modifiers) &&
                            invoked.returnTypeName == "boolean" &&
                            invoked.paramTypeNames == listOf("boolean")
                    }
            }
            val primaryReadyMethod = primaryReadyCandidates.singleOrNull()
            if (primaryReadyCandidates.size != 1) {
                HookSymbolScanDiagnostics.log(
                    logger,
                    "origImageMethodsDex: primaryReady expected=1 actual=${primaryReadyCandidates.size} " +
                        "candidates=${primaryReadyCandidates.joinToString(",") { it.methodName }.ifBlank { "-" }}",
                )
            }

            DexOriginalImageMethodsMatch(
                primaryReadyMethod = primaryReadyMethod?.methodName,
                triggerMethod = triggerMethod.methodName,
                directStartMethod = directStartMethod?.methodName,
                evidence = "downloadTipPrefKey,sameClassInvokeGraph",
            )
        }

    fun scanPbFirstFloorRecommendInsert(
        sourcePaths: List<String>,
        ownerClassName: String,
        postDataClassName: String,
        recommendDataClassName: String,
        logger: ScanLogger? = null,
    ): Set<String> = withBridge(
        sourcePaths,
        logger,
        "PbFirstFloorRecommendBlockHook.Dex",
        emptySet<String>(),
    ) { bridge ->
        val shapeCandidates = exactMethods(bridge, ownerClassName, logger).filter { method ->
            val params = method.paramTypeNames
            Modifier.isStatic(method.modifiers) &&
                method.returnTypeName == "boolean" &&
                params.size == 5 &&
                params[1] == postDataClassName &&
                params[2] == JAVA_LIST_CLASS &&
                params[3] == "int" &&
                params[4] == postDataClassName
        }
        val matches = shapeCandidates.filter { method ->
            val invokes = method.invokes
            invokes.any { invoked ->
                invoked.isConstructor && invoked.declaredClassName == recommendDataClassName
            } &&
                invokes.any { invoked ->
                    invoked.declaredClassName == LIST_UTILS_CLASS &&
                        invoked.methodName == "add"
                }
        }
        if (matches.size != 1) {
            val details = shapeCandidates.joinToString(",") { method ->
                val invokes = method.invokes
                val constructsRecommend = invokes.any { invoked ->
                    invoked.isConstructor && invoked.declaredClassName == recommendDataClassName
                }
                val insertsIntoList = invokes.any { invoked ->
                    invoked.declaredClassName == LIST_UTILS_CLASS &&
                        invoked.methodName == "add"
                }
                "${method.methodName}[constructsRecommend=$constructsRecommend," +
                    "listAdd=$insertsIntoList]"
            }.ifBlank { "-" }
            HookSymbolScanDiagnostics.log(
                logger,
                "pbFirstFloorRecommendInsertDex: expected=1 actual=${matches.size} " +
                    "shapeCandidates=$details",
            )
            emptySet()
        } else {
            setOf(matches.single().methodName)
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

    fun verifyCommentFloorWireBodies(
        sourcePaths: List<String>,
        candidates: List<Pair<String, String>>,
        logger: ScanLogger? = null,
    ): List<Pair<String, String>> =
        withBridge(sourcePaths, logger, "CommentAvatarDirectProfile.Dex", emptyList()) { bridge ->
            candidates.filter { (owner, methodName) ->
                val methods = exactMethods(bridge, owner, logger)
                methods.any { method ->
                    method.methodName == methodName &&
                        method.invokes.any { invoked ->
                            invoked.declaredClassName == HEAD_PENDANT_VIEW_CLASS &&
                                invoked.methodName == "getHeadView"
                        } &&
                        method.invokes.any { invoked -> invoked.methodName == "setOnClickListener" }
                }
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

    private fun encodeDexMethodSpec(method: MethodData): String {
        return method.methodName + "|" + method.returnTypeName + "|" +
            method.paramTypeNames.joinToString(",")
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
