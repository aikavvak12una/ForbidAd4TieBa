package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ForumPageAdScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object ForumPageAdSymbolScanner {
    private const val RESPONSE_DATA_CLASS = "com.baidu.tieba.cke"
    private const val MAPPER_CLASS = "com.baidu.tieba.k1a"
    private const val DIALOG_CONTROLLER_CLASS = "com.baidu.tieba.forum.controller.ForumDialogController"
    private const val GAME_FLOATING_BAR_CONTROLLER_CLASS =
        "com.baidu.tieba.forum.controller.GameFloatingBarController"
    private const val BUSINESS_PROMOT_BIZ_CLASS = "com.baidu.tieba.forum.hybrid.biz.BusinessPromotBiz"

    private const val I_RESPONSE_DATA_CLASS = "com.baidu.tbadk.mvc.data.IResponseData"
    private const val DATA_RES_CLASS = "tbclient.FrsPage\$DataRes"
    private const val BUSINESS_PROMOT_CLASS = "tbclient.FrsPage\$BusinessPromot"
    private const val PRIVATE_POP_CLASS = "tbclient.PrivatePopInfo"
    private const val FRS_SPRITE_BUBBLE_CLASS = "tbclient.FrsPage\$FrsSpriteBubble"
    private const val POP_INFO_CLASS = "tbclient.PopInfo"
    private const val FRS_RAIN_INFO_CLASS = "tbclient.FrsPage\$FrsRainInfo"
    private const val TB_FLOATING_BAR_CLASS = "com.baidu.tieba.feed.component.view.TbFloatingBar"
    private const val MIN_RESPONSE_AD_FIELD_COUNT = 4
    private const val MIN_BOTTOM_AD_SETTER_COUNT = 3
    private const val MIN_DATA_RES_FIELD_SCORE = 6

    private const val DATA_RES_BUSINESS_PROMOT_FIELD = "business_promot"
    private const val DATA_RES_PRIVATE_POP_FIELD = "bawutask_pop"
    private const val DATA_RES_SPRITE_BUBBLE_FIELD = "sprite_bubble_guide"
    private const val DATA_RES_MASK_POP_FIELD = "frsmask_pop_info"
    private const val DATA_RES_ENTER_POP_FIELD = "enter_pop_info"
    private const val DATA_RES_RAIN_FIELD = "frs_rain_info"

    private val responseFieldSpecs = listOf(
        FieldSpec("adMixFloor") { isListType(it.type) },
        FieldSpec("adShowSelect") { isIntType(it.type) },
        FieldSpec("adSampleMapKey") { it.type == String::class.java },
        FieldSpec("businessPromot") { !it.type.isPrimitive },
        FieldSpec("enterFrsDialogInfo") { !it.type.isPrimitive },
        FieldSpec("agreeBanner") { !it.type.isPrimitive },
        FieldSpec("mWindowToast") { isListType(it.type) },
        FieldSpec("showAdsense") { isIntType(it.type) },
        FieldSpec("frsMaskPopInfo") { !it.type.isPrimitive },
    )

    fun scan(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): ForumPageAdScanSymbols {
        val classNames = (preferredClassNames() + candidates).distinct()
        val preferredDataResClass = safeFindClass(DATA_RES_CLASS, cl)

        val response = step("responseData", logger, null as ResponsePath?) {
            resolveResponsePath(classNames, cl, preferredDataResClass, logger)
        }
        val dataResClass = response?.dataResClass ?: preferredDataResClass
        if (dataResClass == null) {
            log(logger, "forumPageAd: DataRes class not inferred from response parser")
        }
        val protoTypes = resolveProtoFieldTypes(dataResClass, cl, logger)
        val bottom = step("bottomData", logger, null as BottomPath?) {
            dataResClass?.let { resolveBottomPath(classNames, cl, it, protoTypes, logger) }
        }
        val header = step("headerRain", logger, null as HeaderRainPath?) {
            dataResClass?.let { resolveHeaderRainPath(bottom?.mapperClass, classNames, cl, it, protoTypes, logger) }
        }
        val gameBar = step("bottomGameBar", logger, null as Method?) {
            dataResClass?.let { resolveBottomGameBarMapper(bottom?.mapperClass, classNames, cl, it, bottom, header, logger) }
        }
        val dialog = step("dialogController", logger, null as DialogPath?) {
            resolveDialogPath(cl, protoTypes.businessPromotClass, logger)
        }
        val floating = step("gameFloatingBar", logger, null as FloatingBarPath?) {
            resolveGameFloatingBarPath(cl, logger)
        }
        val businessBiz = step("businessPromotBiz", logger, null as BusinessPromotBizPath?) {
            resolveBusinessPromotBizPath(cl, logger)
        }

        return ForumPageAdScanSymbols(
            responseDataClass = response?.dataClass?.name,
            responseParserMethod = response?.parserMethod?.name,
            responseAdFields = response?.adFields.orEmpty().map { it.name },
            mapperClass = bottom?.mapperClass?.name ?: header?.mapperClass?.name,
            bottomDataMapperMethod = bottom?.mapperMethod?.name,
            bottomDataClass = bottom?.dataClass?.name,
            businessPromotSetterMethod = bottom?.businessPromotSetter?.name,
            privatePopSetterMethod = bottom?.privatePopSetter?.name,
            spriteBubbleSetterMethod = bottom?.spriteBubbleSetter?.name,
            maskPopSetterMethod = bottom?.maskPopSetter?.name,
            bottomGameBarMapperMethod = gameBar?.name,
            headerDataMapperMethod = header?.mapperMethod?.name,
            headerDataClass = header?.headerDataClass?.name,
            rainDataClass = header?.rainDataClass?.name,
            rainSetterMethod = header?.rainSetter?.name,
            dialogControllerClass = dialog?.controllerClass?.name,
            businessPromotShowMethod = dialog?.businessPromotShowMethod?.name,
            animationShowMethod = dialog?.animationShowMethod?.name,
            gameFloatingBarControllerClass = floating?.controllerClass?.name,
            gameFloatingBarShowMethod = floating?.showMethod?.name,
            gameFloatingBarField = floating?.floatingBarField?.name,
            businessPromotBizClass = businessBiz?.bizClass?.name,
            businessPromotJumpMethod = businessBiz?.jumpMethod?.name,
        )
    }

    private fun resolveResponsePath(
        candidates: List<String>,
        cl: ClassLoader,
        preferredDataResClass: Class<*>?,
        logger: ScanLogger?,
    ): ResponsePath? {
        val iResponseDataClass = safeFindClass(I_RESPONSE_DATA_CLASS, cl) ?: run {
            log(logger, "forumPageAd.response: IResponseData class not found: $I_RESPONSE_DATA_CLASS")
            return null
        }
        val matches = ArrayList<ResponsePath>()
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (className in candidates) {
            try {
                val clazz = safeFindClass(className, cl) ?: continue
                if (!iResponseDataClass.isAssignableFrom(clazz)) continue
                val adFields = resolveResponseAdFields(clazz)
                if (adFields.size < MIN_RESPONSE_AD_FIELD_COUNT) continue
                val parserMethod = resolveResponseParserMethod(clazz, preferredDataResClass) ?: continue
                val dataResClass = parserMethod.parameterTypes[0]
                matches += ResponsePath(
                    dataClass = clazz,
                    parserMethod = parserMethod,
                    dataResClass = dataResClass,
                    adFields = adFields,
                    dataResScore = scoreDataResClass(dataResClass, preferredDataResClass),
                )
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = describeThrowable(t)
                }
            }
        }
        logReflectionSkips(logger, "forumPageAd.response", skippedByReflection, firstReflectionError)
        return bestResponsePath(matches, logger)
    }

    private fun resolveResponseParserMethod(clazz: Class<*>, preferredDataResClass: Class<*>?): Method? {
        val candidates = clazz.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive &&
                !method.parameterTypes[0].isArray
        }
        if (candidates.isEmpty()) return null
        val preferred = preferredDataResClass?.let { preferred ->
            candidates.filter { it.parameterTypes[0] == preferred }
        }.orEmpty()
        if (preferred.size == 1) return preferred.first()

        val ranked = candidates
            .map { method ->
                ParserCandidate(
                    method = method,
                    score = scoreDataResClass(method.parameterTypes[0], preferredDataResClass) +
                        if (method.name == "parserProtobuf") 3 else 0,
                )
            }
            .sortedWith(
                compareByDescending<ParserCandidate> { it.score }
                    .thenByDescending { if (it.method.name == "parserProtobuf") 1 else 0 }
                    .thenBy { it.method.name },
            )
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        if (best.score >= MIN_DATA_RES_FIELD_SCORE && (second == null || best.score > second.score)) {
            return best.method
        }
        val named = candidates.filter { it.name == "parserProtobuf" }
        if (named.size == 1) return named.first()
        return candidates.singleOrNull()
    }

    private fun bestResponsePath(matches: List<ResponsePath>, logger: ScanLogger?): ResponsePath? {
        if (matches.isEmpty()) {
            log(logger, "forumPageAd.response: no validated response parser found")
            return null
        }
        val ranked = matches.sortedWith(
            compareByDescending<ResponsePath> { it.adFields.size }
                .thenByDescending { it.dataResScore }
                .thenByDescending { if (it.dataClass.name == RESPONSE_DATA_CLASS) 1 else 0 }
                .thenBy { it.dataClass.name },
        )
        val best = ranked.first()
        val ambiguous = ranked.drop(1).firstOrNull {
            it.adFields.size == best.adFields.size && it.dataResScore == best.dataResScore
        }
        if (ambiguous != null && best.dataClass.name != RESPONSE_DATA_CLASS) {
            log(
                logger,
                "forumPageAd.response: ambiguous candidates=" +
                    ranked.take(4).joinToString(",") { "${it.dataClass.name}:${it.adFields.size}/${it.dataResScore}" },
            )
            return null
        }
        log(
            logger,
            "forumPageAd.response: ${best.dataClass.name}.${best.parserMethod.name} " +
                "dataRes=${best.dataResClass.name} " +
                "fields=${best.adFields.joinToString(",") { it.name }}",
        )
        return best
    }

    private fun resolveBottomPath(
        candidates: List<String>,
        cl: ClassLoader,
        dataResClass: Class<*>,
        protoTypes: ProtoFieldTypes,
        logger: ScanLogger?,
    ): BottomPath? {
        val availableTypeCount = listOf(
            protoTypes.businessPromotClass,
            protoTypes.privatePopClass,
            protoTypes.spriteBubbleClass,
            protoTypes.popInfoClass,
        ).count { it != null }
        if (availableTypeCount < MIN_BOTTOM_AD_SETTER_COUNT) {
            log(logger, "forumPageAd.bottom: insufficient DataRes ad field types, count=$availableTypeCount")
            return null
        }

        val matches = ArrayList<BottomPath>()
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (className in candidates) {
            try {
                val mapperClass = safeFindClass(className, cl) ?: continue
                val mapperMethods = mapperClass.declaredMethods.filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == dataResClass &&
                        method.returnType != Void.TYPE
                }
                for (method in mapperMethods) {
                    val dataClass = method.returnType
                    val businessPromotSetter = resolveSetter(dataClass, protoTypes.businessPromotClass)
                    val privatePopSetter = resolveSetter(dataClass, protoTypes.privatePopClass)
                    val spriteBubbleSetter = resolveSetter(dataClass, protoTypes.spriteBubbleClass)
                    val maskPopSetter = resolveSetter(dataClass, protoTypes.popInfoClass)
                    val setterCount = listOf(
                        businessPromotSetter,
                        privatePopSetter,
                        spriteBubbleSetter,
                        maskPopSetter,
                    ).count { it != null }
                    if (setterCount < MIN_BOTTOM_AD_SETTER_COUNT) continue
                    matches += BottomPath(
                        mapperClass = mapperClass,
                        mapperMethod = method,
                        dataClass = dataClass,
                        businessPromotSetter = businessPromotSetter,
                        privatePopSetter = privatePopSetter,
                        spriteBubbleSetter = spriteBubbleSetter,
                        maskPopSetter = maskPopSetter,
                        setterCount = setterCount,
                    )
                }
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = describeThrowable(t)
                }
            }
        }
        logReflectionSkips(logger, "forumPageAd.bottom", skippedByReflection, firstReflectionError)

        val ranked = matches.sortedWith(
            compareByDescending<BottomPath> { it.setterCount }
                .thenByDescending { if (it.mapperClass.name == MAPPER_CLASS) 1 else 0 }
                .thenBy { it.mapperClass.name },
        )
        val best = ranked.firstOrNull() ?: run {
            log(logger, "forumPageAd.bottom: no validated bottom mapper found")
            return null
        }
        val ambiguous = ranked.drop(1).firstOrNull { it.setterCount == best.setterCount }
        if (ambiguous != null && best.mapperClass.name != MAPPER_CLASS) {
            log(
                logger,
                "forumPageAd.bottom: ambiguous candidates=" +
                    ranked.take(4).joinToString(",") { "${it.mapperClass.name}.${it.mapperMethod.name}:${it.setterCount}" },
            )
            return null
        }
        log(
            logger,
            "forumPageAd.bottom: ${best.mapperClass.name}.${best.mapperMethod.name} -> ${best.dataClass.name} " +
                "setters=${best.setterCount}",
        )
        return best
    }

    private fun resolveHeaderRainPath(
        preferredMapperClass: Class<*>?,
        candidates: List<String>,
        cl: ClassLoader,
        dataResClass: Class<*>,
        protoTypes: ProtoFieldTypes,
        logger: ScanLogger?,
    ): HeaderRainPath? {
        val rainProtoClass = protoTypes.rainInfoClass ?: run {
            log(logger, "forumPageAd.rain: FrsRainInfo type not inferred")
            return null
        }
        val mapperClasses = collectMapperClasses(preferredMapperClass, candidates, cl)
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (mapperClass in mapperClasses) {
            try {
                val methods = mapperClass.declaredMethods
                val rainDataMapper = methods.singleOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == rainProtoClass &&
                        method.returnType != Void.TYPE
                } ?: continue
                val rainDataClass = rainDataMapper.returnType
                val headerMapper = methods.firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == dataResClass &&
                        method.returnType != Void.TYPE &&
                        resolveSetter(method.returnType, rainDataClass) != null
                } ?: continue
                val rainSetter = resolveSetter(headerMapper.returnType, rainDataClass) ?: continue
                log(
                    logger,
                    "forumPageAd.rain: ${mapperClass.name}.${headerMapper.name} -> " +
                        "${headerMapper.returnType.name}.${rainSetter.name}",
                )
                return HeaderRainPath(mapperClass, headerMapper, headerMapper.returnType, rainDataClass, rainSetter)
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = describeThrowable(t)
                }
            }
        }
        logReflectionSkips(logger, "forumPageAd.rain", skippedByReflection, firstReflectionError)
        log(logger, "forumPageAd.rain: no validated header rain mapper found")
        return null
    }

    private fun resolveBottomGameBarMapper(
        preferredMapperClass: Class<*>?,
        candidates: List<String>,
        cl: ClassLoader,
        dataResClass: Class<*>,
        bottom: BottomPath?,
        header: HeaderRainPath?,
        logger: ScanLogger?,
    ): Method? {
        val mapperClasses = collectMapperClasses(preferredMapperClass, candidates, cl)
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        for (mapperClass in mapperClasses) {
            try {
                val candidatesForClass = mapperClass.declaredMethods.filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == dataResClass &&
                        method.returnType != Void.TYPE &&
                        method != bottom?.mapperMethod &&
                        method != header?.mapperMethod &&
                        isFloatingBarDataClass(method.returnType)
                }
                val resolved = candidatesForClass.singleOrNull()
                    ?: candidatesForClass.firstOrNull { it.name == "d" }
                if (resolved != null) {
                    log(logger, "forumPageAd.gameBar: ${mapperClass.name}.${resolved.name} -> ${resolved.returnType.name}")
                    return resolved
                }
            } catch (t: Throwable) {
                skippedByReflection++
                if (firstReflectionError == null) {
                    firstReflectionError = describeThrowable(t)
                }
            }
        }
        logReflectionSkips(logger, "forumPageAd.gameBar", skippedByReflection, firstReflectionError)
        log(logger, "forumPageAd.gameBar: no validated bottom game bar mapper found")
        return null
    }

    private fun resolveDialogPath(cl: ClassLoader, businessPromotClass: Class<*>?, logger: ScanLogger?): DialogPath? {
        val controllerClass = safeFindClass(DIALOG_CONTROLLER_CLASS, cl) ?: run {
            log(logger, "forumPageAd.dialog: class not found: $DIALOG_CONTROLLER_CLASS")
            return null
        }
        val businessPromotShow = if (businessPromotClass == null) {
            log(logger, "forumPageAd.dialog: BusinessPromot type not inferred")
            null
        } else {
            controllerClass.declaredMethods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == businessPromotClass
            }
        }
        val animationShow = resolvePreferredNoArgVoidMethod(
            controllerClass,
            preferredName = "h1",
            semanticSignal = "showAnimationView",
        )
        if (businessPromotShow == null && animationShow == null) {
            log(logger, "forumPageAd.dialog: no validated display fallback methods found")
            return null
        }
        log(
            logger,
            "forumPageAd.dialog: ${controllerClass.name} business=${businessPromotShow?.name ?: "-"} " +
                "animation=${animationShow?.name ?: "-"}",
        )
        return DialogPath(controllerClass, businessPromotShow, animationShow)
    }

    private fun resolveGameFloatingBarPath(cl: ClassLoader, logger: ScanLogger?): FloatingBarPath? {
        val controllerClass = safeFindClass(GAME_FLOATING_BAR_CONTROLLER_CLASS, cl) ?: run {
            log(logger, "forumPageAd.floatingBar: class not found: $GAME_FLOATING_BAR_CONTROLLER_CLASS")
            return null
        }
        val floatingBarClass = safeFindClass(TB_FLOATING_BAR_CLASS, cl)
        if (floatingBarClass == null) {
            log(logger, "forumPageAd.floatingBar: TbFloatingBar class not found: $TB_FLOATING_BAR_CLASS")
        }
        val field = floatingBarClass?.let { barClass ->
            controllerClass.declaredFields.singleOrNull { field ->
                barClass.isAssignableFrom(field.type)
            }
        }
        val showMethod = resolvePreferredNoArgVoidMethod(
            controllerClass,
            preferredName = "k2",
            semanticSignal = "showFloatingBar",
        )
        if (showMethod == null) {
            log(logger, "forumPageAd.floatingBar: no validated show method found")
            return null
        }
        log(
            logger,
            "forumPageAd.floatingBar: ${controllerClass.name} show=${showMethod.name} " +
                "field=${field?.name ?: "-"}",
        )
        return FloatingBarPath(controllerClass, showMethod, field)
    }

    private fun resolveBusinessPromotBizPath(cl: ClassLoader, logger: ScanLogger?): BusinessPromotBizPath? {
        val bizClass = safeFindClass(BUSINESS_PROMOT_BIZ_CLASS, cl) ?: run {
            log(logger, "forumPageAd.biz: class not found: $BUSINESS_PROMOT_BIZ_CLASS")
            return null
        }
        val methods = bizClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        val resolved = methods.singleOrNull()
            ?: methods.firstOrNull { it.name == "l" && hasKotlinMetadataSignal(bizClass, "businessPromotJump") }
        if (resolved == null) {
            log(logger, "forumPageAd.biz: no validated businessPromotJump method found")
            return null
        }
        log(logger, "forumPageAd.biz: ${bizClass.name}.${resolved.name}(String)")
        return BusinessPromotBizPath(bizClass, resolved)
    }

    private fun resolveProtoFieldTypes(
        dataResClass: Class<*>?,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ProtoFieldTypes {
        val businessPromotClass =
            resolveDataResFieldType(dataResClass, DATA_RES_BUSINESS_PROMOT_FIELD)
                ?: safeFindClass(BUSINESS_PROMOT_CLASS, cl)
        val privatePopClass =
            resolveDataResFieldType(dataResClass, DATA_RES_PRIVATE_POP_FIELD)
                ?: safeFindClass(PRIVATE_POP_CLASS, cl)
        val spriteBubbleClass =
            resolveDataResFieldType(dataResClass, DATA_RES_SPRITE_BUBBLE_FIELD)
                ?: safeFindClass(FRS_SPRITE_BUBBLE_CLASS, cl)
        val popInfoClass =
            resolveDataResFieldType(dataResClass, DATA_RES_MASK_POP_FIELD)
                ?: resolveDataResFieldType(dataResClass, DATA_RES_ENTER_POP_FIELD)
                ?: safeFindClass(POP_INFO_CLASS, cl)
        val rainInfoClass =
            resolveDataResFieldType(dataResClass, DATA_RES_RAIN_FIELD)
                ?: safeFindClass(FRS_RAIN_INFO_CLASS, cl)

        if (dataResClass != null) {
            log(
                logger,
                "forumPageAd.proto: dataRes=${dataResClass.name} " +
                    "business=${businessPromotClass?.name ?: "-"} " +
                    "privatePop=${privatePopClass?.name ?: "-"} " +
                    "sprite=${spriteBubbleClass?.name ?: "-"} " +
                    "pop=${popInfoClass?.name ?: "-"} " +
                    "rain=${rainInfoClass?.name ?: "-"}",
            )
        }

        return ProtoFieldTypes(
            businessPromotClass = businessPromotClass,
            privatePopClass = privatePopClass,
            spriteBubbleClass = spriteBubbleClass,
            popInfoClass = popInfoClass,
            rainInfoClass = rainInfoClass,
        )
    }

    private fun resolveResponseAdFields(clazz: Class<*>): List<Field> {
        return responseFieldSpecs.mapNotNull { spec ->
            resolveDeclaredField(clazz, spec.name)?.takeIf(spec.matches)
        }
    }

    private fun scoreDataResClass(clazz: Class<*>, preferredDataResClass: Class<*>?): Int {
        if (clazz == preferredDataResClass || clazz.name == DATA_RES_CLASS) return 100
        return try {
            val names = clazz.declaredFields.mapTo(HashSet()) { it.name }
            var score = 0
            fun add(name: String, weight: Int) {
                if (names.contains(name)) score += weight
            }
            add(DATA_RES_BUSINESS_PROMOT_FIELD, 3)
            add(DATA_RES_PRIVATE_POP_FIELD, 2)
            add(DATA_RES_SPRITE_BUBBLE_FIELD, 2)
            add(DATA_RES_MASK_POP_FIELD, 2)
            add(DATA_RES_RAIN_FIELD, 2)
            add("bottom_game_bar", 2)
            add("ad_mix_list", 1)
            add("ad_show_select", 1)
            add("ad_sample_map_key", 1)
            add(DATA_RES_ENTER_POP_FIELD, 1)
            add("agree_banner", 1)
            add("window_toast", 1)
            add("show_adsense", 1)
            add("forum", 1)
            add("user", 1)
            score
        } catch (_: Throwable) {
            0
        }
    }

    private fun resolveDataResFieldType(dataResClass: Class<*>?, fieldName: String): Class<*>? {
        if (dataResClass == null) return null
        return try {
            resolveDeclaredField(dataResClass, fieldName)?.type
        } catch (_: Throwable) {
            null
        }
    }

    private fun collectMapperClasses(
        preferredMapperClass: Class<*>?,
        candidates: List<String>,
        cl: ClassLoader,
    ): List<Class<*>> {
        val out = ArrayList<Class<*>>()
        val seen = HashSet<String>()

        fun add(clazz: Class<*>) {
            if (seen.add(clazz.name)) out += clazz
        }

        preferredMapperClass?.let(::add)
        for (className in candidates) {
            if (seen.contains(className)) continue
            val clazz = safeFindClass(className, cl) ?: continue
            add(clazz)
        }
        return out
    }

    private fun resolvePreferredNoArgVoidMethod(
        clazz: Class<*>,
        preferredName: String,
        semanticSignal: String,
    ): Method? {
        if (!hasKotlinMetadataSignal(clazz, semanticSignal)) return null
        val method = clazz.declaredMethods.firstOrNull { candidate ->
            !Modifier.isStatic(candidate.modifiers) &&
                candidate.name == preferredName &&
                candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.isEmpty()
        }
        return method
    }

    private fun resolveSetter(clazz: Class<*>, paramClass: Class<*>?): Method? {
        if (paramClass == null) return null
        return clazz.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == paramClass
        }
    }

    private fun isFloatingBarDataClass(clazz: Class<*>): Boolean {
        return clazz.declaredConstructors.any { constructor ->
            val params = constructor.parameterTypes
            params.size == 8 &&
                params.count { isListType(it) } >= 3 &&
                params.any { it == String::class.java } &&
                params.any { it == Integer.TYPE }
        }
    }

    private fun hasKotlinMetadataSignal(clazz: Class<*>, signal: String): Boolean {
        val metadata = clazz.getAnnotation(Metadata::class.java) ?: return false
        return metadata.data1.any { it.contains(signal) } || metadata.data2.any { it.contains(signal) }
    }

    private fun resolveDeclaredField(clazz: Class<*>, name: String): Field? {
        return try {
            clazz.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    private fun preferredClassNames(): List<String> = listOf(
        RESPONSE_DATA_CLASS,
        MAPPER_CLASS,
        DIALOG_CONTROLLER_CLASS,
        GAME_FLOATING_BAR_CONTROLLER_CLASS,
        BUSINESS_PROMOT_BIZ_CLASS,
    )

    private inline fun <T> step(
        name: String,
        logger: ScanLogger?,
        fallback: T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            log(logger, "forumPageAd.$name: scan failed: ${t.javaClass.name}: ${t.message.orEmpty()}")
            fallback
        }
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun isListType(type: Class<*>): Boolean =
        HookSymbolResolver.isListType(type)

    private fun isIntType(type: Class<*>): Boolean =
        HookSymbolResolver.isIntType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }

    private fun logReflectionSkips(
        logger: ScanLogger?,
        label: String,
        skippedByReflection: Int,
        firstReflectionError: String?,
    ) {
        if (skippedByReflection <= 0) return
        log(
            logger,
            "$label: skipped classes by reflection=$skippedByReflection" +
                (firstReflectionError?.let { ", firstException=$it" } ?: ""),
        )
    }

    private fun describeThrowable(t: Throwable): String =
        HookSymbolScanDiagnostics.sanitizeScanStatusText(
            HookSymbolScanDiagnostics.formatScanException(t),
        )

    private data class FieldSpec(
        val name: String,
        val matches: (Field) -> Boolean,
    )

    private data class ParserCandidate(
        val method: Method,
        val score: Int,
    )

    private data class ResponsePath(
        val dataClass: Class<*>,
        val parserMethod: Method,
        val dataResClass: Class<*>,
        val adFields: List<Field>,
        val dataResScore: Int,
    )

    private data class ProtoFieldTypes(
        val businessPromotClass: Class<*>?,
        val privatePopClass: Class<*>?,
        val spriteBubbleClass: Class<*>?,
        val popInfoClass: Class<*>?,
        val rainInfoClass: Class<*>?,
    )

    private data class BottomPath(
        val mapperClass: Class<*>,
        val mapperMethod: Method,
        val dataClass: Class<*>,
        val businessPromotSetter: Method?,
        val privatePopSetter: Method?,
        val spriteBubbleSetter: Method?,
        val maskPopSetter: Method?,
        val setterCount: Int,
    )

    private data class HeaderRainPath(
        val mapperClass: Class<*>,
        val mapperMethod: Method,
        val headerDataClass: Class<*>,
        val rainDataClass: Class<*>,
        val rainSetter: Method,
    )

    private data class DialogPath(
        val controllerClass: Class<*>,
        val businessPromotShowMethod: Method?,
        val animationShowMethod: Method?,
    )

    private data class FloatingBarPath(
        val controllerClass: Class<*>,
        val showMethod: Method?,
        val floatingBarField: Field?,
    )

    private data class BusinessPromotBizPath(
        val bizClass: Class<*>,
        val jumpMethod: Method,
    )
}
