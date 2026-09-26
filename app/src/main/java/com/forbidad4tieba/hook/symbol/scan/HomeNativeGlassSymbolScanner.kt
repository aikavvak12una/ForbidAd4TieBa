package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeNativeGlassSymbolScanner {
    private const val SUB_PB_NEXT_PAGE_MORE_VIEW_RES_NAME = "pb_more_view"
    private const val PB_REPLY_TITLE_DIVIDER_VIEW_RES_NAME = "divider_bottom"
    private const val BASE_FRAGMENT_CLASS = "com.baidu.tbadk.core.BaseFragment"
    private const val PB_BAR_IMAGE_VIEW_CLASS = "com.baidu.tbadk.core.view.PbBarImageView"
    private const val ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT = 24
    private const val ENTER_FORUM_CAPSULE_MIN_CLASS_SCORE = 170
    private const val ENTER_FORUM_CAPSULE_MIN_SCORE_GAP = 24
    private val DYNAMIC_BACKGROUND_COLOR_RES_NAMES = arrayOf(
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
    private val TOP_CHROME_CLASSES = listOf(
        StableTiebaHookPoints.HOME_SCROLL_TAB_BAR_LAYOUT_CLASS,
        StableTiebaHookPoints.HOME_FIXED_APP_BAR_LAYOUT_CLASS,
    )

    fun scanResourceIds(context: Context, cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassResourceIds {
        fun resolveId(resourceName: String): Int? {
            val name = resourceName.trim()
            if (name.isEmpty()) return null

            val resourceId = context.resources.getIdentifier(name, "id", context.packageName)
            if (resourceId > 0 && isResolvedIdResource(context, resourceId, logger, "resources.getIdentifier($name)")) {
                log(logger, "homeNativeGlassResources: id/$resourceName resolved by resources name $name")
                return resourceId
            }

            for (className in TARGET_APP_R_ID_CLASSES) {
                val id = resolveRIdField(cl, className, name, logger) ?: continue
                if (isResolvedIdResource(context, id, logger, "$className.$name")) {
                    log(logger, "homeNativeGlassResources: id/$resourceName resolved by $className.$name")
                    return id
                }
            }

            log(logger, "homeNativeGlassResources: missing id/$resourceName")
            return null
        }

        fun resolveColor(resourceName: String): Int? {
            val name = resourceName.trim()
            if (name.isEmpty()) return null

            val resourceId = context.resources.getIdentifier(name, "color", context.packageName)
            if (resourceId > 0 && isResolvedColorResource(context, resourceId, logger, "resources.getIdentifier($name)")) {
                log(logger, "homeNativeGlassResources: color/$resourceName resolved by resources name $name")
                return resourceId
            }

            for (className in TARGET_APP_R_COLOR_CLASSES) {
                val id = resolveRIdField(cl, className, name, logger) ?: continue
                if (isResolvedColorResource(context, id, logger, "$className.$name")) {
                    log(logger, "homeNativeGlassResources: color/$resourceName resolved by $className.$name")
                    return id
                }
            }

            log(logger, "homeNativeGlassResources: missing color/$resourceName")
            return null
        }

        val out = HomeNativeGlassResourceIds(
            subPbNextPageMoreViewId = resolveId(SUB_PB_NEXT_PAGE_MORE_VIEW_RES_NAME),
            pbReplyTitleDividerViewId = resolveId(PB_REPLY_TITLE_DIVIDER_VIEW_RES_NAME),
            dynamicBackgroundColorIds = DYNAMIC_BACKGROUND_COLOR_RES_NAMES
                .mapNotNull(::resolveColor)
                .distinct(),
        )
        log(
            logger,
            "homeNativeGlassResources: " +
                "subPbNext=${formatResourceId(out.subPbNextPageMoreViewId)}, " +
                "titleDivider=${formatResourceId(out.pbReplyTitleDividerViewId)}, " +
                "dynamicBackgroundColors=${out.dynamicBackgroundColorIds.size}",
        )
        return out
    }

    fun scanSortSwitch(context: Context, logger: ScanLogger?): HomeNativeGlassSortSwitchSymbols =
        SortSwitchSymbolScanner.scan(context, logger)

    fun scanTopChrome(cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassTopChromeSymbols {
        val specs = ArrayList<String>(TOP_CHROME_CLASSES.size)
        for (className in TOP_CHROME_CLASSES) {
            val clazz = safeFindClass(className, cl)
            if (clazz == null) {
                log(logger, "homeNativeGlassTopChrome: class missing $className")
                continue
            }
            val candidates = declaredMethods("TopChrome.${clazz.name}", clazz, logger)
                ?.filter(::isTopChromeTabSelectedMethod)
                ?: continue
            val method = candidates.singleOrNull()
            if (method == null) {
                log(
                    logger,
                    "homeNativeGlassTopChrome: tab selected method candidates for $className=" +
                        candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
                )
                continue
            }
            specs.add(encodeClassMethodSpec(clazz, method))
        }
        log(
            logger,
            "homeNativeGlassTopChrome: tabSelected=" +
                specs.joinToString(",").ifBlank { "-" },
        )
        return HomeNativeGlassTopChromeSymbols(tabSelectedMethodSpecs = specs)
    }

    fun scanSubPbNextPage(cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassSubPbNextPageSymbols {
        val clazz = safeFindClass(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassSubPbNextPage: class missing ${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}")
                return HomeNativeGlassSubPbNextPageSymbols()
            }
        val candidates = declaredMethods("SubPbNextPage.${clazz.name}", clazz, logger)
            ?.filter(::isSubPbSetNextPageMethod)
            ?: return HomeNativeGlassSubPbNextPageSymbols()
        val method = candidates.singleOrNull()
        if (method == null) {
            log(
                logger,
                "homeNativeGlassSubPbNextPage: setNextPage candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return HomeNativeGlassSubPbNextPageSymbols()
        }
        val paramType = method.parameterTypes[0].name
        log(
            logger,
            "homeNativeGlassSubPbNextPage matched: ${clazz.name}.${method.name}($paramType)",
        )
        return HomeNativeGlassSubPbNextPageSymbols(
            methodName = method.name,
            parameterTypeName = paramType,
        )
    }

    fun scanHostDarkModeSwitch(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassHostDarkModeSwitchSymbols = HostDarkModeSwitchSymbolScanner.scan(context, cl, logger)

    fun scanEnterForumCapsule(
        context: Context,
        candidateClassNames: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassEnterForumCapsuleSymbols {
        val baseFragmentClass = safeFindClass(BASE_FRAGMENT_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: BaseFragment class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val navigationBarClass = safeFindClass(StableTiebaHookPoints.NAVIGATION_BAR_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: NavigationBar class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val pbBarImageViewClass = safeFindClass(PB_BAR_IMAGE_VIEW_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: PbBarImageView class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }
        val emTextViewClass = safeFindClass(StableTiebaHookPoints.EM_TEXT_VIEW_CLASS, cl)
            ?: run {
                log(logger, "homeNativeGlassEnterForumCapsule: EMTextView class missing")
                return HomeNativeGlassEnterForumCapsuleSymbols()
            }

        val classCandidates = findEnterForumCapsuleClassCandidates(
            candidateClassNames = candidateClassNames,
            cl = cl,
            baseFragmentClass = baseFragmentClass,
            navigationBarClass = navigationBarClass,
            pbBarImageViewClass = pbBarImageViewClass,
            emTextViewClass = emTextViewClass,
            logger = logger,
        )
        if (classCandidates.isEmpty()) {
            log(logger, "homeNativeGlassEnterForumCapsule: no semantic class candidates")
            return HomeNativeGlassEnterForumCapsuleSymbols()
        }

        val sourcePaths = appSourcePaths(context)
        // A scan budget must not remove equally plausible candidates according to their names.
        val cutoffScore = classCandidates.getOrNull(ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT - 1)?.score
        val scannedCandidates = classCandidates.takeWhile { cutoffScore == null || it.score >= cutoffScore }
        val dexMatchesByClass = if (sourcePaths.isNotEmpty()) {
            DexKitSemanticScanner.scanEnterForumCapsules(
                sourcePaths = sourcePaths,
                ownerClassNames = scannedCandidates.map { it.clazz.name },
                logger = logger,
            )
        } else {
            emptyMap()
        }
        val resolvedCandidates = scannedCandidates
            .mapNotNull { candidate ->
                resolveEnterForumCapsuleCandidate(
                    candidate,
                    dexMatchesByClass[candidate.clazz.name].orEmpty(),
                    logger,
                )
            }

        val best = selectUniqueScoredCandidate(
            "HomeNativeGlassHook.EnterForumCapsule", resolvedCandidates,
            ENTER_FORUM_CAPSULE_MIN_SCORE_GAP, logger, { it.score }, { it.symbols.controllerClass.orEmpty() },
        ) ?: run {
            val preview = classCandidates
                .take(6)
                .joinToString(" || ") { "${it.clazz.name}:${it.score}[${it.evidence}]" }
            log(
                logger,
                "homeNativeGlassEnterForumCapsule: no complete semantic match, " +
                    "sourcePaths=${sourcePaths.size}, top=$preview",
            )
            return HomeNativeGlassEnterForumCapsuleSymbols()
        }
        val symbols = best.symbols
        log(
            logger,
            "homeNativeGlassEnterForumCapsule matched: " +
                "${symbols.controllerClass}.${symbols.initMethod}/${symbols.refreshMethod} " +
                "view=${symbols.viewField} title=${symbols.titleField} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return symbols
    }

    fun scanPbCommonLayoutPreloaderGetOrDefaultMethod(cl: ClassLoader, logger: ScanLogger?): String? {
        val clazz = safeFindClass(StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS, cl) ?: run {
            log(logger, "pbCommonLayoutPreloader: class not found: ${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}")
            return null
        }
        val candidates = declaredMethods("PbCommonLayoutPreloader", clazz, logger)
            ?.filter(::isPbCommonLayoutPreloaderGetOrDefaultMethod)
            ?: return null
        val method = candidates.singleOrNull() ?: run {
            log(
                logger,
                "pbCommonLayoutPreloader: method candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return null
        }
        log(logger, "pbCommonLayoutPreloader matched: ${clazz.name}.${method.name}")
        return method.name
    }

    private fun findEnterForumCapsuleClassCandidates(
        candidateClassNames: List<String>,
        cl: ClassLoader,
        baseFragmentClass: Class<*>,
        navigationBarClass: Class<*>,
        pbBarImageViewClass: Class<*>,
        emTextViewClass: Class<*>,
        logger: ScanLogger?,
    ): List<HomeNativeGlassEnterForumCapsuleClassCandidate> {
        val names = candidateClassNames.distinct()
        val out = ArrayList<HomeNativeGlassEnterForumCapsuleClassCandidate>(8)
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (className in names) {
            if (!className.startsWith("com.baidu.")) continue
            try {
                val clazz = safeFindClass(className, cl) ?: continue
                if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) continue
                val fields = collectInstanceFields("EnterForumCapsule.${clazz.name}", clazz, logger) ?: continue
                val methods = collectInstanceMethods("EnterForumCapsule.${clazz.name}", clazz, logger) ?: continue
                val scored = scoreEnterForumCapsuleClass(
                    clazz = clazz,
                    fields = fields,
                    methods = methods,
                    baseFragmentClass = baseFragmentClass,
                    navigationBarClass = navigationBarClass,
                    pbBarImageViewClass = pbBarImageViewClass,
                    emTextViewClass = emTextViewClass,
                    logger = logger,
                ) ?: continue
                out.add(
                    HomeNativeGlassEnterForumCapsuleClassCandidate(
                        clazz = clazz,
                        fields = fields,
                        methods = methods,
                        score = scored.first,
                        evidence = scored.second,
                    ),
                )
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                        HookSymbolScanDiagnostics.formatScanException(t),
                    )
                }
            }
        }
        if (skippedByReflection > 0) {
            log(
                logger,
                "homeNativeGlassEnterForumCapsule: skipped classes by reflection=$skippedByReflection" +
                    (firstReflectionError?.let { ", firstException=$it" } ?: ""),
            )
        }
        return out.sortedWith(
            compareByDescending<HomeNativeGlassEnterForumCapsuleClassCandidate> { it.score }
                .thenBy { it.clazz.name.length }
                .thenBy { it.clazz.name },
        )
    }

    private fun scoreEnterForumCapsuleClass(
        clazz: Class<*>,
        fields: List<Field>,
        methods: List<Method>,
        baseFragmentClass: Class<*>,
        navigationBarClass: Class<*>,
        pbBarImageViewClass: Class<*>,
        emTextViewClass: Class<*>,
        logger: ScanLogger?,
    ): Pair<Int, String>? {
        val constructors = declaredConstructors("EnterForumCapsule.${clazz.name}", clazz, logger) ?: return null
        val compatibleConstructors = constructors.filter { ctor ->
            isEnterForumCapsuleConstructor(ctor, baseFragmentClass)
        }
        if (compatibleConstructors.isEmpty()) return null
        val exactCtor = compatibleConstructors.any { it.parameterTypes.size == 4 }
        val hasNavigationBarField = fields.any { navigationBarClass.isAssignableFrom(it.type) }
        val hasBaseFragmentField = fields.any { baseFragmentClass.isAssignableFrom(it.type) }
        val hasPbBarImageField = fields.any { pbBarImageViewClass.isAssignableFrom(it.type) }
        val hasEmTextField = fields.any { emTextViewClass.isAssignableFrom(it.type) }
        val viewFields = fields.filter { View::class.java.isAssignableFrom(it.type) }
        val primaryViewFields = viewFields.filter { ViewGroup::class.java.isAssignableFrom(it.type) }
        val stringFields = fields.filter { it.type == String::class.java }
        val noArgVoidCount = methods.count(::isNoArgVoidMethod)

        if (!hasNavigationBarField || primaryViewFields.isEmpty() || stringFields.isEmpty()) {
            return null
        }

        var score = if (exactCtor) 80 else 62
        val evidence = ArrayList<String>(10)
        evidence.add(if (exactCtor) "ctorExact" else "ctorCompatible")
        if (hasNavigationBarField) {
            score += 62
            evidence.add("navigationBar")
        }
        if (hasBaseFragmentField) {
            score += 28
            evidence.add("baseFragment")
        }
        if (hasPbBarImageField) {
            score += 35
            evidence.add("pbBarImage")
        }
        if (hasEmTextField) {
            score += 35
            evidence.add("emText")
        }
        score += primaryViewFields.size.coerceAtMost(3) * 12
        score += stringFields.size.coerceAtMost(2) * 8
        if (noArgVoidCount >= 2) {
            score += 18
            evidence.add("voidNoArg=$noArgVoidCount")
        }
        score -= fields.size / 12
        score -= methods.size / 18
        return if (score >= ENTER_FORUM_CAPSULE_MIN_CLASS_SCORE) {
            score to evidence.joinToString(",")
        } else {
            null
        }
    }

    private fun isEnterForumCapsuleConstructor(
        ctor: Constructor<*>,
        baseFragmentClass: Class<*>,
    ): Boolean {
        val params = ctor.parameterTypes
        if (params.size < 3 || params.size > 6) return false
        val hasBaseFragment = params.any { baseFragmentClass.isAssignableFrom(it) }
        val hasRootView = params.any { View::class.java.isAssignableFrom(it) }
        val hasBoolean = params.any { isBooleanType(it) }
        return hasBaseFragment && hasRootView && hasBoolean
    }

    private fun resolveEnterForumCapsuleCandidate(
        candidate: HomeNativeGlassEnterForumCapsuleClassCandidate,
        dexMatches: List<DexEnterForumCapsuleMethodMatch>,
        logger: ScanLogger?,
    ): HomeNativeGlassEnterForumCapsuleResolvedCandidate? {
        val validDexMatches = dexMatches.filter { match ->
            candidate.methods.any { it.name == match.ownerMethodName && isNoArgVoidMethod(it) }
        }
        val pairs = enterForumCapsulePairs(validDexMatches).filter { pair ->
            candidate.fields.any { it.name == pair.init.viewFieldName && View::class.java.isAssignableFrom(it.type) } &&
                candidate.fields.any { it.name == pair.refresh.titleFieldName && it.type == String::class.java }
        }
        val pair = selectUniqueScoredCandidate(
            "HomeNativeGlassHook.EnterForumCapsule.${candidate.clazz.name}", pairs,
            ENTER_FORUM_CAPSULE_MIN_SCORE_GAP, logger, { it.score },
            { "${it.init.ownerMethodName}/${it.refresh.ownerMethodName}:${it.init.viewFieldName}/${it.refresh.titleFieldName}" },
        ) ?: return null
        return HomeNativeGlassEnterForumCapsuleResolvedCandidate(
            symbols = HomeNativeGlassEnterForumCapsuleSymbols(
                controllerClass = candidate.clazz.name,
                initMethod = pair.init.ownerMethodName,
                refreshMethod = pair.refresh.ownerMethodName,
                viewField = pair.init.viewFieldName,
                titleField = pair.refresh.titleFieldName,
            ),
            score = candidate.score + pair.score,
            evidence = "class=${candidate.evidence};init=${pair.init.evidence};refresh=${pair.refresh.evidence}",
        )
    }

    private fun isNoArgVoidMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType == Void.TYPE
    }

    private fun isTopChromeTabSelectedMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            isIntType(method.parameterTypes[0]) &&
            isIntType(method.parameterTypes[1])
    }

    private fun isSubPbSetNextPageMethod(method: Method): Boolean {
        return method.name == StableTiebaHookPoints.METHOD_SET_NEXT_PAGE &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1
    }

    private fun encodeClassMethodSpec(clazz: Class<*>, method: Method): String {
        return "${clazz.name}#${method.name}"
    }

    private fun resolveRIdField(
        cl: ClassLoader,
        className: String,
        fieldName: String,
        logger: ScanLogger?,
    ): Int? {
        val clazz = safeFindClass(className, cl) ?: return null
        val field = try {
            clazz.getDeclaredField(fieldName)
        } catch (_: NoSuchFieldException) {
            null
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: read field failed $className.$fieldName: ${t.message}")
            null
        } ?: return null

        if (field.type != Int::class.javaPrimitiveType && field.type != Integer.TYPE) {
            log(logger, "homeNativeGlassResources: field is not int $className.$fieldName")
            return null
        }
        return try {
            field.isAccessible = true
            field.getInt(null).takeIf { it != 0 }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: get field failed $className.$fieldName: ${t.message}")
            null
        }
    }

    private fun isResolvedIdResource(context: Context, id: Int, logger: ScanLogger?, source: String): Boolean {
        if (id <= 0) return false
        return try {
            val typeName = context.resources.getResourceTypeName(id)
            if (typeName == "id") {
                true
            } else {
                log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)} type=$typeName")
                false
            }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)}: ${t.message}")
            false
        }
    }

    private fun isResolvedColorResource(context: Context, id: Int, logger: ScanLogger?, source: String): Boolean {
        if (id <= 0) return false
        return try {
            val typeName = context.resources.getResourceTypeName(id)
            if (typeName == "color") {
                true
            } else {
                log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)} type=$typeName")
                false
            }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassResources: rejected $source=${formatResourceId(id)}: ${t.message}")
            false
        }
    }

    private fun formatResourceId(value: Int?): String {
        return if (value != null && value != 0) {
            "0x${Integer.toHexString(value)}"
        } else {
            "-"
        }
    }

    private fun isPbCommonLayoutPreloaderGetOrDefaultMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (!View::class.java.isAssignableFrom(method.returnType)) return false
        val params = method.parameterTypes
        return params.size == 4 &&
            Context::class.java.isAssignableFrom(params[0]) &&
            params[1] == Boolean::class.javaPrimitiveType &&
            isIntType(params[2]) &&
            Class::class.java.isAssignableFrom(params[3])
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun declaredMethods(label: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
        scanSubStep("HomeNativeGlass.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }

    private fun declaredFields(label: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
        scanSubStep("HomeNativeGlass.$label.Fields", logger, null) {
            clazz.declaredFields.toList()
        }

    private fun declaredConstructors(label: String, clazz: Class<*>, logger: ScanLogger?): List<Constructor<*>>? =
        scanSubStep("HomeNativeGlass.$label.Constructors", logger, null) {
            clazz.declaredConstructors.toList()
        }

    private fun collectInstanceFields(label: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
        scanSubStep("HomeNativeGlass.$label.InstanceFields", logger, null) {
            ScanReflection.collectInstanceFields(clazz)
        }

    private fun collectInstanceMethods(label: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
        scanSubStep("HomeNativeGlass.$label.InstanceMethods", logger, null) {
            ScanReflection.collectInstanceMethods(clazz)
        }

    private fun isIntType(type: Class<*>): Boolean =
        ScanReflection.isIntType(type)

    private fun isBooleanType(type: Class<*>): Boolean =
        ScanReflection.isBooleanType(type)

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
