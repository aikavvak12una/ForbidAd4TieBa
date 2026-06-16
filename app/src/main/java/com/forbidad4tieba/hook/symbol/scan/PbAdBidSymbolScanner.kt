package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.DexPbAdBidModelMatch
import com.forbidad4tieba.hook.symbol.model.DexPbAdBidScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdBidScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal object PbAdBidSymbolScanner {
    private const val PB_COMMON_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
    private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
    private const val PB_AD_BID_RES_IDL_CLASS = "tbclient.AdBid.AdBidResIdl"
    private const val HTTP_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpMessage"
    private const val TIEBA_REQUEST_INTERFACE_CLASS = "com.baidu.tieba.u3b"
    private const val TIEBA_REQUEST_CALLBACK_CLASS = "com.baidu.tieba.d8d"
    private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
    private const val PB_AD_BID_DEX_KIND_COMMON = "common"
    private const val PB_AD_BID_DEX_KIND_PAGE_BROWSER = "pageBrowser"

    private data class ClassShape(
        val methods: List<Method>,
        val fields: List<Field>,
        val constructors: List<Constructor<*>>,
        val nestedClasses: List<Class<*>>,
    )

    fun scan(
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

        val commonStartMethods = scanSubStep(
            "PbAdRequestBlockHook.AdBid.CommonStartMethods",
            logger,
            emptyList<String>(),
        ) {
            commonBaseClass?.let { resolveCommonStartMethods(it, logger) }.orEmpty()
        }
        val commonNotifyMethod = scanSubStep(
            "PbAdRequestBlockHook.AdBid.CommonNotifyMethod",
            logger,
            null as Method?,
        ) {
            commonBaseClass?.let { resolveCommonNotifyMethod(it, logger) }
        }
        val pageBrowserBaseRequestDataMethod = scanSubStep(
            "PbAdRequestBlockHook.AdBid.PageBrowserRequestDataMethod",
            logger,
            null as Method?,
        ) {
            if (continuationClass != null) {
                pageBrowserBaseClass?.let {
                    resolvePageBrowserRequestDataMethod(it, continuationClass, logger)
                }
            } else {
                log(logger, "pbAdBid: continuation class not found: $KOTLIN_CONTINUATION_CLASS")
                null
            }
        }

        val targetClassNames = candidates
            .filter(::isCandidateClassName)
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
                val shape = classShape("Candidate.$className", cls, logger) ?: run {
                    skippedByReflection++
                    continue
                }

                if (commonBaseClass != null) {
                    try {
                        scoreCommonModelClass(
                            cls,
                            shape,
                            commonBaseClass,
                            adBidResClass,
                            httpMessageClass,
                            requestClass,
                            logger,
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
                        scorePageBrowserModelClass(
                            cls,
                            shape,
                            pageBrowserBaseClass,
                            adBidResClass,
                            requestClass,
                            continuationClass,
                            pageBrowserBaseRequestDataMethod,
                            logger,
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

        val commonMatch = ScanReflection.chooseUniqueScanMatch(
            tag = "pbAdBid.common",
            ruleName = "PbAdBidCommonModelRule",
            matches = commonMatches,
            logger = logger,
            minScore = 150,
            minScoreGap = 20,
        )
        val pageBrowserMatch = ScanReflection.chooseUniqueScanMatch(
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
            scanSubStep("PbAdRequestBlockHook.AdBid.Dex", logger, DexPbAdBidScanSymbols()) {
                scanFromDex(context, logger)
            }
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

    fun resolvePageBrowserRequestDataMethod(
        pageBrowserBaseClass: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val methods = declaredMethods("PageBrowserBase.RequestData", pageBrowserBaseClass, logger)
            ?.filter { method ->
                isPageBrowserRequestDataMethod(method, continuationClass)
            } ?: return null
        val resolved = methods.singleOrNull()
        if (resolved == null) {
            HookSymbolScanDiagnostics.log(
                logger,
                "pbAdBid.pageBrowser: requestData method mismatch candidates=" +
                    methods.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    fun resolvePageBrowserBaseFromCandidate(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Class<*>? {
        var current: Class<*>? = cls.superclass
        while (current != null && current != Any::class.java) {
            if (findPageBrowserRequestDataMethod(current, continuationClass, null) != null) {
                return current
            }
            current = current.superclass
        }
        return null
    }

    fun findPageBrowserRequestDataMethodInHierarchy(
        cls: Class<*>,
        continuationClass: Class<*>,
    ): Method? {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            findPageBrowserRequestDataMethod(current, continuationClass, null)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun findPageBrowserRequestDataMethod(
        cls: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val methods = declaredMethods("PageBrowserBase.${cls.name}.RequestData", cls, logger)
            ?.filter { method -> isPageBrowserRequestDataMethod(method, continuationClass) }
            ?: return null
        return methods.singleOrNull()
    }

    private fun findPageBrowserRequestDataMethodForScan(
        cls: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        return findPageBrowserRequestDataMethod(cls, continuationClass, logger)
    }

    private fun resolvePageBrowserBaseFromCandidateForScan(
        cls: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Class<*>? {
        var current: Class<*>? = cls.superclass
        while (current != null && current != Any::class.java) {
            if (findPageBrowserRequestDataMethodForScan(current, continuationClass, logger) != null) {
                return current
            }
            current = current.superclass
        }
        return null
    }

    private fun findPageBrowserRequestDataMethodInHierarchyForScan(
        cls: Class<*>,
        continuationClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            findPageBrowserRequestDataMethodForScan(current, continuationClass, logger)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun scanFromDex(
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
        val commonMatch = chooseUniqueDexMatch(
            tag = "pbAdBid.commonDex",
            matches = matches.filter { it.kind == PB_AD_BID_DEX_KIND_COMMON },
            logger = logger,
        )
        val pageBrowserMatch = chooseUniqueDexMatch(
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

    private fun chooseUniqueDexMatch(
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

    private fun isCandidateClassName(className: String): Boolean {
        if (!className.startsWith("com.baidu.tieba.")) return false
        val shortName = className.substringAfterLast('.')
        return isLikelyObfuscatedShortName(shortName) ||
            className.startsWith("com.baidu.tieba.pb.")
    }

    private fun resolveCommonStartMethods(
        commonBaseClass: Class<*>,
        logger: ScanLogger?,
    ): List<String> {
        val methods = declaredMethods("CommonBase.StartMethods", commonBaseClass, logger)
            .orEmpty()
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

    private fun resolveCommonNotifyMethod(
        commonBaseClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val methods = declaredMethods("CommonBase.NotifyMethod", commonBaseClass, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isIntType(method.parameterTypes[0])
        } ?: return null
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

    private fun scoreCommonModelClass(
        cls: Class<*>,
        shape: ClassShape,
        commonBaseClass: Class<*>,
        adBidResClass: Class<*>,
        httpMessageClass: Class<*>?,
        requestClass: Class<*>,
        logger: ScanLogger?,
    ): ScanMatch? {
        if (cls == commonBaseClass || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!commonBaseClass.isAssignableFrom(cls)) return null
        val hasGenericAdBidType = hasGenericSuperclassArgument(cls, commonBaseClass, adBidResClass)
        val hasStructuralAdBidType = !hasGenericAdBidType &&
            referencesClassInShapeOrNestedClasses(cls, shape, adBidResClass, logger)
        if (!hasGenericAdBidType && !hasStructuralAdBidType) return null

        val requestMethods = shape.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                requestClass.isAssignableFrom(method.returnType)
        }
        val requestMethod = requestMethods.singleOrNull() ?: return null

        val setterMethods = if (httpMessageClass != null) {
            shape.methods.filter { method ->
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
        if (shape.constructors.any { it.parameterTypes.isEmpty() }) score += 12
        if (httpMessageClass != null && shape.fields.any { field ->
                !Modifier.isStatic(field.modifiers) && httpMessageClass.isAssignableFrom(field.type)
            }
        ) {
            score += 20
        }
        if (setterMethod != null) score += 16
        if (shape.methods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].name == TIEBA_REQUEST_CALLBACK_CLASS
            }
        ) {
            score += 12
        }
        score -= shape.methods.size / 8
        score -= shape.fields.size / 6
        return ScanMatch(cls.name, requestMethod.name, setterMethod?.name.orEmpty(), score)
    }

    private fun scorePageBrowserModelClass(
        cls: Class<*>,
        shape: ClassShape,
        pageBrowserBaseClass: Class<*>?,
        adBidResClass: Class<*>,
        requestClass: Class<*>,
        continuationClass: Class<*>,
        pageBrowserBaseRequestDataMethod: Method?,
        logger: ScanLogger?,
    ): ScanMatch? {
        if (cls == pageBrowserBaseClass || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val resolvedBaseClass = pageBrowserBaseClass?.takeIf { it.isAssignableFrom(cls) }
            ?: resolvePageBrowserBaseFromCandidateForScan(cls, continuationClass, logger)
            ?: return null
        val hasGenericAdBidType = hasGenericSuperclassArgument(cls, resolvedBaseClass, adBidResClass)
        val hasStructuralAdBidType = !hasGenericAdBidType &&
            referencesClassInShapeOrNestedClasses(cls, shape, adBidResClass, logger)
        if (!hasGenericAdBidType && !hasStructuralAdBidType) return null

        val requestMethods = shape.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                requestClass.isAssignableFrom(method.returnType)
        }
        val requestMethod = requestMethods.singleOrNull() ?: return null
        val requestDataMethod = pageBrowserBaseRequestDataMethod
            ?.takeIf { it.declaringClass.isAssignableFrom(cls) }
            ?: findPageBrowserRequestDataMethodInHierarchyForScan(cls, continuationClass, logger)
            ?: return null

        var score = 140
        if (hasGenericAdBidType) score += 34
        if (hasStructuralAdBidType) score += 24
        if (cls.superclass == resolvedBaseClass) score += 18
        if (shape.constructors.any { ctor ->
                ctor.parameterTypes.size == 1 && isIntType(ctor.parameterTypes[0])
            }
        ) {
            score += 24
        }
        if (shape.fields.any { field -> !Modifier.isStatic(field.modifiers) && isIntType(field.type) }) {
            score += 12
        }
        score -= shape.methods.size / 8
        score -= shape.fields.size / 6
        return ScanMatch(cls.name, requestMethod.name, requestDataMethod.name, score)
    }

    private fun referencesClassInShapeOrNestedClasses(
        cls: Class<*>,
        shape: ClassShape,
        targetClass: Class<*>,
        logger: ScanLogger?,
        maxDepth: Int = 1,
    ): Boolean {
        for (method in shape.methods) {
            if (typeReferencesClass(method.genericReturnType, targetClass.name)) return true
            if (method.genericParameterTypes.any { typeReferencesClass(it, targetClass.name) }) return true
            if (method.returnType == targetClass || method.parameterTypes.any { it == targetClass }) return true
        }
        for (field in shape.fields) {
            if (typeReferencesClass(field.genericType, targetClass.name) || field.type == targetClass) return true
        }
        if (maxDepth <= 0) return false
        for (nested in shape.nestedClasses) {
            val nestedShape = classShape("Nested.${cls.name}.${nested.name}", nested, logger) ?: continue
            if (referencesClassInShapeOrNestedClasses(nested, nestedShape, targetClass, logger, maxDepth - 1)) {
                return true
            }
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

    private fun isPageBrowserRequestDataMethod(method: Method, continuationClass: Class<*>): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Any::class.java &&
            method.parameterTypes.size == 1 &&
            continuationClass.isAssignableFrom(method.parameterTypes[0])
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

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun declaredMethods(label: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
        scanSubStep("PbAdRequestBlockHook.AdBid.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }

    private fun declaredFields(label: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
        scanSubStep("PbAdRequestBlockHook.AdBid.$label.Fields", logger, null) {
            clazz.declaredFields.toList()
        }

    private fun declaredConstructors(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Constructor<*>>? =
        scanSubStep("PbAdRequestBlockHook.AdBid.$label.Constructors", logger, null) {
            clazz.declaredConstructors.toList()
        }

    private fun declaredClasses(label: String, clazz: Class<*>, logger: ScanLogger?): List<Class<*>>? =
        scanSubStep("PbAdRequestBlockHook.AdBid.$label.Classes", logger, null) {
            clazz.declaredClasses.toList()
        }

    private fun classShape(label: String, clazz: Class<*>, logger: ScanLogger?): ClassShape? {
        val methods = declaredMethods(label, clazz, logger) ?: return null
        val fields = declaredFields(label, clazz, logger) ?: return null
        val constructors = declaredConstructors(label, clazz, logger) ?: return null
        val nestedClasses = declaredClasses(label, clazz, logger) ?: return null
        return ClassShape(methods, fields, constructors, nestedClasses)
    }

    private fun isIntType(type: Class<*>): Boolean =
        ScanReflection.isIntType(type)

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
