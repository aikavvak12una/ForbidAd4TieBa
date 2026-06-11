package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeNativeGlassSymbolScanner {
    private const val SUB_PB_NEXT_PAGE_MORE_VIEW_RES_NAME = "pb_more_view"
    private const val PB_REPLY_TITLE_DIVIDER_VIEW_RES_NAME = "divider_bottom"
    private const val SORT_SWITCH_BACKGROUND_PAINT_FIELD = "o"
    private const val SORT_SWITCH_SLIDE_DRAW_METHOD = "y"
    private const val SORT_SWITCH_SLIDE_PATH_FIELD = "z"
    private const val ENTER_FORUM_CAPSULE_CONTROLLER_CLASS = "com.baidu.tieba.gcd"
    private const val ENTER_FORUM_CAPSULE_INIT_METHOD = "r"
    private const val ENTER_FORUM_CAPSULE_REFRESH_METHOD = "D"
    private const val ENTER_FORUM_CAPSULE_VIEW_FIELD = "l"
    private const val ENTER_FORUM_CAPSULE_TITLE_FIELD = "q"
    private const val BASE_FRAGMENT_CLASS = "com.baidu.tbadk.core.BaseFragment"
    private const val PB_BAR_IMAGE_VIEW_CLASS = "com.baidu.tbadk.core.view.PbBarImageView"
    private const val ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT = 24
    private const val ENTER_FORUM_CAPSULE_MIN_CLASS_SCORE = 170
    private const val ENTER_FORUM_CAPSULE_MIN_SCORE_GAP = 24
    private const val MORE_ACTIVITY_CLASS = "com.baidu.tieba.setting.more.MoreActivity"
    private const val BD_SWITCH_VIEW_CLASS = "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
    private const val HOST_DARK_MODE_SWITCH_GETTER_METHOD_HINT = "R"
    private const val HOST_SWITCH_STATE_FIELD_HINT = "a"
    private const val HOST_SWITCH_SET_ON_METHOD_HINT = "j"
    private const val HOST_SWITCH_SET_OFF_METHOD_HINT = "g"
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

    fun scanSortSwitch(cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassSortSwitchSymbols {
        val sortSwitchClass = safeFindClass(StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS, cl)
            ?: run {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: class missing ${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}",
                )
                return HomeNativeGlassSortSwitchSymbols()
            }
        val fields = try {
            sortSwitchClass.declaredFields.toList()
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassSortSwitch: declaredFields failed: ${t.message}")
            return HomeNativeGlassSortSwitchSymbols()
        }
        val paintFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name == "android.graphics.Paint"
        }
        val backgroundPaint = if (paintFields.size < 3) {
            log(logger, "homeNativeGlassSortSwitch: insufficient Paint fields count=${paintFields.size}")
            null
        } else {
            val structural = paintFields.first()
            val legacy = paintFields.singleOrNull { it.name == SORT_SWITCH_BACKGROUND_PAINT_FIELD }
            if (legacy != null && legacy != structural) {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: background Paint legacy hint differs, " +
                        "structural=${structural.name}, legacy=${legacy.name}",
                )
            }
            structural
        }
        val pathFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name == "android.graphics.Path"
        }
        val slidePath = if (pathFields.size < 2) {
            log(logger, "homeNativeGlassSortSwitch: insufficient Path fields count=${pathFields.size}")
            null
        } else {
            val structural = pathFields.last()
            val legacy = pathFields.singleOrNull { it.name == SORT_SWITCH_SLIDE_PATH_FIELD }
            if (legacy != null && legacy != structural) {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: slide Path legacy hint differs, " +
                        "structural=${structural.name}, legacy=${legacy.name}",
                )
            }
            structural
        }
        val slideDrawCandidates = try {
            sortSwitchClass.declaredMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == java.lang.Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].name == "android.graphics.Canvas"
            }
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassSortSwitch: declaredMethods failed: ${t.message}")
            emptyList()
        }
        val slideDrawInnerCandidates = slideDrawCandidates.filter { method ->
            method.name != "onDraw"
        }
        if (slideDrawInnerCandidates.size != slideDrawCandidates.size) {
            log(
                logger,
                "homeNativeGlassSortSwitch: slide draw ignored framework candidates=" +
                    slideDrawCandidates
                        .filterNot { it in slideDrawInnerCandidates }
                        .joinToString(",") { describeMethodShape(it) },
            )
        }
        val slideDrawMethod = when (slideDrawInnerCandidates.size) {
            0 -> {
                log(logger, "homeNativeGlassSortSwitch: slide draw method missing")
                null
            }
            1 -> slideDrawInnerCandidates.first()
            2 -> {
                val structural = slideDrawInnerCandidates.last()
                val legacy = slideDrawInnerCandidates.singleOrNull { it.name == SORT_SWITCH_SLIDE_DRAW_METHOD }
                if (legacy != null && legacy != structural) {
                    log(
                        logger,
                        "homeNativeGlassSortSwitch: slide draw legacy hint differs, " +
                            "structural=${structural.name}, legacy=${legacy.name}",
                    )
                }
                structural
            }
            else -> {
                log(
                    logger,
                    "homeNativeGlassSortSwitch: slide draw method ambiguous candidates=" +
                        slideDrawInnerCandidates.joinToString(",") { describeMethodShape(it) },
                )
                null
            }
        }
        log(
            logger,
            "homeNativeGlassSortSwitch: backgroundPaint=${backgroundPaint?.name}, " +
                "slideDraw=${slideDrawMethod?.name}, slidePath=${slidePath?.name}, " +
                "paintFields=${paintFields.joinToString(",") { it.name }}, " +
                "pathFields=${pathFields.joinToString(",") { it.name }}",
        )
        return HomeNativeGlassSortSwitchSymbols(
            backgroundPaintField = backgroundPaint?.name,
            slideDrawMethod = slideDrawMethod?.name,
            slidePathField = slidePath?.name,
        )
    }

    fun scanHostDarkModeSwitch(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeNativeGlassHostDarkModeSwitchSymbols {
        val moreActivityClass = safeFindClass(MORE_ACTIVITY_CLASS, cl) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: class missing $MORE_ACTIVITY_CLASS")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val switchClass = safeFindClass(BD_SWITCH_VIEW_CLASS, cl) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: class missing $BD_SWITCH_VIEW_CLASS")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val switchMethods = try {
            switchClass.declaredMethods.toList()
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassHostDarkModeSwitch: switch declaredMethods failed: ${t.message}")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val switchFields = try {
            collectInstanceFields(switchClass)
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassHostDarkModeSwitch: switch fields failed: ${t.message}")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val stateField = selectHostSwitchStateField(switchFields, logger) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: state field missing")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val setOnMethod = selectHostSwitchNamedNoArgVoidMethod(
            switchMethods,
            HOST_SWITCH_SET_ON_METHOD_HINT,
            "setOn",
            logger,
        ) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: set on method missing")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val setOffMethod = selectHostSwitchNamedNoArgVoidMethod(
            switchMethods,
            HOST_SWITCH_SET_OFF_METHOD_HINT,
            "setOff",
            logger,
        ) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: set off method missing")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }

        val fields = try {
            collectInstanceFields(moreActivityClass)
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassHostDarkModeSwitch: activity fields failed: ${t.message}")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val candidates = fields.mapNotNull { field ->
            val methods = try {
                collectInstanceMethods(field.type).filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        switchClass.isAssignableFrom(method.returnType)
                }
            } catch (t: Throwable) {
                log(
                    logger,
                    "homeNativeGlassHostDarkModeSwitch: controller methods failed " +
                        "${field.type.name}: ${t.message}",
                )
                return@mapNotNull null
            }
            val getter = selectHostDarkModeSwitchGetter(methods, logger) ?: return@mapNotNull null
            HostDarkModeSwitchCandidate(
                field = field,
                getter = getter,
                score = scoreHostDarkModeSwitchCandidate(field, getter, methods),
            )
        }.sortedWith(
            compareByDescending<HostDarkModeSwitchCandidate> { it.score }
                .thenBy { it.field.name }
                .thenBy { it.getter.name },
        )

        if (candidates.isEmpty()) {
            log(logger, "homeNativeGlassHostDarkModeSwitch: controller field missing")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val best = candidates.first()
        if (candidates.size > 1 && candidates[1].score == best.score) {
            log(
                logger,
                "homeNativeGlassHostDarkModeSwitch: ambiguous candidates=" +
                    candidates.joinToString(",") { "${it.field.name}.${it.getter.name}/score=${it.score}" },
            )
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val activityMethods = try {
            collectInstanceMethods(moreActivityClass)
        } catch (t: Throwable) {
            log(logger, "homeNativeGlassHostDarkModeSwitch: activity methods failed: ${t.message}")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        val callbackMethod = selectHostDarkModeSwitchCallbackMethod(
            activityMethods,
            best.getter.returnType,
            stateField.type,
            logger,
        ) ?: run {
            log(logger, "homeNativeGlassHostDarkModeSwitch: switch callback missing")
            return HomeNativeGlassHostDarkModeSwitchSymbols()
        }
        log(
            logger,
            "homeNativeGlassHostDarkModeSwitch: activity=${moreActivityClass.name}, " +
                "controllerField=${best.field.name}, getter=${best.getter.name}, " +
                "state=${stateField.name}, " +
                "setOn=${setOnMethod.name}, setOff=${setOffMethod.name}, " +
                "callback=${callbackMethod.name}",
        )
        return HomeNativeGlassHostDarkModeSwitchSymbols(
            moreActivityClass = moreActivityClass.name,
            controllerField = best.field.name,
            switchGetterMethod = best.getter.name,
            switchStateField = stateField.name,
            switchSetOnMethod = setOnMethod.name,
            switchSetOffMethod = setOffMethod.name,
            switchCallbackMethod = callbackMethod.name,
        )
    }

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
        val scannedCandidates = classCandidates.take(ENTER_FORUM_CAPSULE_CLASS_SCAN_LIMIT)
        val dexMatchesByClass = if (sourcePaths.isNotEmpty()) {
            DexShareIconScanner.scanEnterForumCapsules(
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
                )
            }
            .sortedWith(
                compareByDescending<HomeNativeGlassEnterForumCapsuleResolvedCandidate> { it.score }
                    .thenBy { it.symbols.controllerClass.orEmpty().length }
                    .thenBy { it.symbols.controllerClass.orEmpty() },
            )

        val best = resolvedCandidates.firstOrNull() ?: run {
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
        val second = resolvedCandidates.getOrNull(1)
        if (second != null && best.score - second.score < ENTER_FORUM_CAPSULE_MIN_SCORE_GAP) {
            log(
                logger,
                "homeNativeGlassEnterForumCapsule ambiguous: " +
                    "best=${best.symbols.controllerClass}:${best.score}[${best.evidence}], " +
                    "second=${second.symbols.controllerClass}:${second.score}[${second.evidence}]",
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
        val candidates = clazz.declaredMethods.filter(::isPbCommonLayoutPreloaderGetOrDefaultMethod)
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
        val names = (candidateClassNames + ENTER_FORUM_CAPSULE_CONTROLLER_CLASS).distinct()
        val out = ArrayList<HomeNativeGlassEnterForumCapsuleClassCandidate>(8)
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (className in names) {
            if (!className.startsWith("com.baidu.")) continue
            try {
                val clazz = safeFindClass(className, cl) ?: continue
                if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) continue
                val fields = collectInstanceFields(clazz)
                val methods = collectInstanceMethods(clazz)
                val scored = scoreEnterForumCapsuleClass(
                    clazz = clazz,
                    fields = fields,
                    methods = methods,
                    baseFragmentClass = baseFragmentClass,
                    navigationBarClass = navigationBarClass,
                    pbBarImageViewClass = pbBarImageViewClass,
                    emTextViewClass = emTextViewClass,
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
    ): Pair<Int, String>? {
        val constructors = try {
            clazz.declaredConstructors.toList()
        } catch (_: Throwable) {
            return null
        }
        val compatibleCtor = constructors.firstOrNull { ctor ->
            isEnterForumCapsuleConstructor(ctor, baseFragmentClass)
        } ?: return null
        val exactCtor = compatibleCtor.parameterTypes.size == 4
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
        if (clazz.name == ENTER_FORUM_CAPSULE_CONTROLLER_CLASS) {
            score += 8
            evidence.add("legacyClass")
        }
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
    ): HomeNativeGlassEnterForumCapsuleResolvedCandidate? {
        val validDexMatches = dexMatches.filter { match ->
            candidate.methods.any { method ->
                method.name == match.ownerMethodName && isNoArgVoidMethod(method)
            }
        }
        val initDex = validDexMatches
            .filter { it.kind == DexEnterForumCapsuleMethodKind.INIT }
            .maxWithOrNull(compareBy<DexEnterForumCapsuleMethodMatch> { it.score }.thenByDescending { -it.ownerMethodName.length })
        val refreshDex = validDexMatches
            .filter { it.kind == DexEnterForumCapsuleMethodKind.REFRESH }
            .maxWithOrNull(compareBy<DexEnterForumCapsuleMethodMatch> { it.score }.thenByDescending { -it.ownerMethodName.length })

        val legacyInit = candidate.methods.singleOrNull { method ->
            method.name == ENTER_FORUM_CAPSULE_INIT_METHOD && isNoArgVoidMethod(method)
        }?.name
        val legacyRefresh = candidate.methods.singleOrNull { method ->
            method.name == ENTER_FORUM_CAPSULE_REFRESH_METHOD && isNoArgVoidMethod(method)
        }?.name

        val initMethodName = initDex?.ownerMethodName ?: legacyInit ?: return null
        val refreshMethodName = refreshDex?.ownerMethodName ?: legacyRefresh ?: return null
        val viewField = selectEnterForumCapsuleViewField(
            fields = candidate.fields,
            preferredNames = listOfNotNull(
                initDex?.viewFieldName,
                refreshDex?.viewFieldName,
            ),
            legacyName = ENTER_FORUM_CAPSULE_VIEW_FIELD,
        ) ?: return null
        val titleField = selectEnterForumCapsuleTitleField(
            fields = candidate.fields,
            preferredNames = listOfNotNull(
                refreshDex?.titleFieldName,
            ),
            legacyName = ENTER_FORUM_CAPSULE_TITLE_FIELD,
        ) ?: return null

        var score = candidate.score
        val evidence = ArrayList<String>(6)
        evidence.add("class=${candidate.evidence}")
        if (initDex != null) {
            score += initDex.score
            evidence.add("initDex=${initDex.ownerMethodName}[${initDex.evidence}]")
        } else if (legacyInit != null) {
            score += 45
            evidence.add("initLegacy=$legacyInit")
        }
        if (refreshDex != null) {
            score += refreshDex.score
            evidence.add("refreshDex=${refreshDex.ownerMethodName}[${refreshDex.evidence}]")
        } else if (legacyRefresh != null) {
            score += 45
            evidence.add("refreshLegacy=$legacyRefresh")
        }
        if (initDex?.viewFieldName == viewField.name) score += 24
        if (refreshDex?.viewFieldName == viewField.name) score += 18
        if (refreshDex?.titleFieldName == titleField.name) score += 24
        if (viewField.name == ENTER_FORUM_CAPSULE_VIEW_FIELD) score += 8
        if (titleField.name == ENTER_FORUM_CAPSULE_TITLE_FIELD) score += 8

        return HomeNativeGlassEnterForumCapsuleResolvedCandidate(
            symbols = HomeNativeGlassEnterForumCapsuleSymbols(
                controllerClass = candidate.clazz.name,
                initMethod = initMethodName,
                refreshMethod = refreshMethodName,
                viewField = viewField.name,
                titleField = titleField.name,
            ),
            score = score,
            evidence = evidence.joinToString(";"),
        )
    }

    private fun selectEnterForumCapsuleViewField(
        fields: List<Field>,
        preferredNames: List<String>,
        legacyName: String,
    ): Field? {
        val viewFields = fields.filter { View::class.java.isAssignableFrom(it.type) }
        for (name in preferredNames.distinct()) {
            viewFields.singleOrNull { it.name == name }?.let { return it }
        }
        val primary = viewFields.filter { ViewGroup::class.java.isAssignableFrom(it.type) }
        primary.singleOrNull()?.let { return it }
        viewFields.singleOrNull { it.name == legacyName }?.let { return it }
        return viewFields.singleOrNull()
    }

    private fun selectEnterForumCapsuleTitleField(
        fields: List<Field>,
        preferredNames: List<String>,
        legacyName: String,
    ): Field? {
        val stringFields = fields.filter { it.type == String::class.java }
        for (name in preferredNames.distinct()) {
            stringFields.singleOrNull { it.name == name }?.let { return it }
        }
        stringFields.singleOrNull()?.let { return it }
        return stringFields.singleOrNull { it.name == legacyName }
    }

    private fun isNoArgVoidMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType == Void.TYPE
    }

    private fun selectHostDarkModeSwitchGetter(
        methods: List<Method>,
        logger: ScanLogger?,
    ): Method? {
        methods.singleOrNull { it.name == HOST_DARK_MODE_SWITCH_GETTER_METHOD_HINT }?.let { return it }
        if (methods.size == 1) return methods.first()
        if (methods.isNotEmpty()) {
            log(
                logger,
                "homeNativeGlassHostDarkModeSwitch: getter ambiguous candidates=" +
                    methods.joinToString(",") { describeMethodShape(it) },
            )
        }
        return null
    }

    private fun selectHostDarkModeSwitchCallbackMethod(
        methods: List<Method>,
        switchViewType: Class<*>,
        switchStateType: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = methods.filter { method ->
            val params = method.parameterTypes
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                params.size == 2 &&
                View::class.java.isAssignableFrom(params[0]) &&
                params[0].isAssignableFrom(switchViewType) &&
                params[1] == switchStateType
        }
        if (candidates.size == 1) return candidates.first()
        if (candidates.isNotEmpty()) {
            log(
                logger,
                "homeNativeGlassHostDarkModeSwitch: callback ambiguous candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) },
            )
        }
        return null
    }

    private fun selectHostSwitchStateField(
        fields: List<Field>,
        logger: ScanLogger?,
    ): Field? {
        val candidates = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type.isEnum
        }
        candidates.singleOrNull { it.name == HOST_SWITCH_STATE_FIELD_HINT }?.let { return it }
        if (candidates.size == 1) return candidates.first()
        if (candidates.isNotEmpty()) {
            log(
                logger,
                "homeNativeGlassHostDarkModeSwitch: state ambiguous candidates=" +
                    candidates.joinToString(",") { "${it.name}:${it.type.name}" },
            )
        }
        return null
    }

    private fun selectHostSwitchNamedNoArgVoidMethod(
        methods: List<Method>,
        name: String,
        role: String,
        logger: ScanLogger?,
    ): Method? {
        val candidates = methods.filter { method ->
            method.name == name && isNoArgVoidMethod(method)
        }
        if (candidates.size == 1) return candidates.first()
        if (candidates.size > 1) {
            log(
                logger,
                "homeNativeGlassHostDarkModeSwitch: $role ambiguous candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) },
            )
        }
        return null
    }

    private fun scoreHostDarkModeSwitchCandidate(
        field: Field,
        getter: Method,
        methods: List<Method>,
    ): Int {
        var score = 0
        if (field.name == "a") score += 8
        if (field.type.name.startsWith("com.baidu.tieba.")) score += 6
        if (getter.name == HOST_DARK_MODE_SWITCH_GETTER_METHOD_HINT) score += 48
        if (methods.any { it.name == "T" }) score += 12
        score += methods.size.coerceAtMost(4)
        return score
    }

    private data class HostDarkModeSwitchCandidate(
        val field: Field,
        val getter: Method,
        val score: Int,
    )

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
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<Method> =
        HookSymbolResolver.collectInstanceMethods(clazz)

    private fun isIntType(type: Class<*>): Boolean =
        HookSymbolResolver.isIntType(type)

    private fun isBooleanType(type: Class<*>): Boolean =
        HookSymbolResolver.isBooleanType(type)

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
