package com.forbidad4tieba.hook.symbol.dexkit

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanSession
import com.forbidad4tieba.hook.symbol.scan.AutoRefreshDexScanner
import com.forbidad4tieba.hook.symbol.scan.EnterForumCapsuleDexScanner
import com.forbidad4tieba.hook.symbol.scan.GameFloatingBarDexScanner
import com.forbidad4tieba.hook.symbol.scan.PbPageBrowserAiEmojiDexScanner
import com.forbidad4tieba.hook.symbol.scan.selectUniqueScoredCandidate
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.io.File
import java.lang.reflect.Modifier

internal object DexKitSemanticScanner {
    private const val TAG = "DexKitSemantic"
    private const val AGREE_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.AgreeData"
    private const val AGREE_DATA_HAS_AGREE_FIELD = "hasAgree"
    private const val AGREE_DATA_AGREE_TYPE_FIELD = "agreeType"
    private const val HEAD_PENDANT_VIEW_CLASS = "com.baidu.tbadk.core.view.HeadPendantView"
    private const val PAGE_BROWSER_AI_EMOJI_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
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
    /**
     * Log literal emitted by the host's cold-start tracker when a homepage
     * network request begins. Used as the semantic anchor for locating every
     * feed refresh entry point; see [scanRecPersonalizeRequestMethods].
     */
    private const val HOME_NET_START_ANCHOR = "onHomepageNetStart"
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

    fun scanAutoRefresh(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAutoRefreshMatch> = withBridge(sourcePaths, logger, "AutoRefreshHook.Dex", emptyList()) { bridge ->
        exactMethods(bridge, ownerClassName, logger).mapNotNull(AutoRefreshDexScanner::match)
    }

    /**
     * Finds every home-feed network request entry point the host exposes.
     *
     * The host marks the start of a homepage network request by calling its
     * cold-start tracker, whose body carries the literal "onHomepageNetStart".
     * That string is the semantic anchor: R8 renames the tracker class, the
     * tracker method and the request methods, but the log literal survives, and
     * every refresh transport has to pass through the tracker. The callers of
     * that tracker are therefore exactly the set of methods that must be blocked
     * — across whatever classes the host currently splits them into.
     *
     * Matching a fixed owner class plus a fixed invoked method name instead
     * (the previous rule) silently loses refresh paths: on 22.10.1.0 it found
     * only 1 of the 4 real entry points, because the host had moved two of them
     * onto a second class and renamed the tracker method.
     */
    fun scanRecPersonalizeRequestMethods(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): List<DexRecPersonalizeRequestMatch> =
        withBridge(sourcePaths, logger, "AutoRefreshHook.RecRequestDex", emptyList()) { bridge ->
            val trackers = try {
                bridge.findMethod(
                    FindMethod.create()
                        .searchPackages("com.baidu.tieba")
                        .matcher(
                            MethodMatcher.create()
                                .addUsingString(HOME_NET_START_ANCHOR, StringMatchType.Contains),
                        ),
                ).toList()
            } catch (t: Throwable) {
                recordIssue(
                    logger,
                    "$TAG.RecRequestTracker",
                    HookSymbolScanDiagnostics.formatScanException(t),
                )
                return@withBridge emptyList()
            }
            if (trackers.isEmpty()) {
                recordIssue(
                    logger,
                    "$TAG.RecRequestTracker",
                    "no method carries anchor '$HOME_NET_START_ANCHOR'",
                )
                return@withBridge emptyList()
            }
            trackers
                .flatMap { tracker -> tracker.callers.orEmpty().toList() }
                .distinctBy { it.descriptor }
                .mapNotNull { method ->
                    if (Modifier.isStatic(method.modifiers) || method.returnTypeName != "void") {
                        return@mapNotNull null
                    }
                    DexRecPersonalizeRequestMatch(
                        ownerClassName = method.declaredClassName,
                        ownerMethodName = method.methodName,
                        paramTypes = method.paramTypeNames,
                        evidence = "homeNetStart",
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

    fun scanGameFloatingBar(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexGameFloatingBarMatch? =
        withBridge(sourcePaths, logger, "ForumPageAdBlockHook.GameFloatingBarDex") { bridge ->
            val classes = findClassesByName(bridge, "GameFloatingBarController", logger) +
                exactClassOrNull(bridge, "com.baidu.tieba.forum.controller.GameFloatingBarController", logger)
            val candidates = classes.filterNotNull().distinctBy { it.name }.flatMap { cls ->
                cls.methods.orEmpty().mapNotNull(GameFloatingBarDexScanner::match)
            }
            selectUniqueScoredCandidate("ForumPageAdBlockHook.GameFloatingBarDex", candidates, 24, logger,
                { it.score }, { "${it.controllerClassName}#${it.showMethodName}" })
        }

    fun scanPbPageBrowserAiEmojiCreation(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexPbPageBrowserAiEmojiCreationMatch? =
        withBridge(sourcePaths, logger, "AiComponentDisableHook.PbPageBrowserAiEmojiCreationDex") { bridge ->
            val classes = findClassesByName(bridge, "CommentFloorAiEmojiCreationView", logger) +
                exactClassOrNull(bridge, PAGE_BROWSER_AI_EMOJI_VIEW_CLASS, logger)
            val candidates = classes.filterNotNull().distinctBy { it.name }.flatMap { cls ->
                cls.methods.orEmpty().mapNotNull(PbPageBrowserAiEmojiDexScanner::match)
            }
            selectUniqueScoredCandidate("AiComponentDisableHook.PbPageBrowserAiEmojiCreationDex", candidates, 24, logger,
                { it.score }, { "${it.viewClassName}#${it.bindMethodName}" })
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
                    EnterForumCapsuleDexScanner.scanMethod(method)
                }
            }.filterValues { it.isNotEmpty() }
        }

    fun scanEnterForumCapsule(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexEnterForumCapsuleMethodMatch> =
        scanEnterForumCapsules(sourcePaths, listOf(ownerClassName), logger)[ownerClassName].orEmpty()

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

    private inline fun <T> withBridge(
        sourcePaths: List<String>,
        logger: ScanLogger?,
        tag: String,
        fallback: T,
        block: (DexKitBridge) -> T,
    ): T {
        return try {
            HookSymbolScanSession.withDexKitBridge(sourcePaths, logger) { block(it.bridge) } ?: fallback
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

    private fun MethodData.hasString(value: String): Boolean =
        usingStrings.any { it == value || it.contains(value) }

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
