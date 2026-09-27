package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.TAG
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.ForumPageAdBlockSymbols
import com.forbidad4tieba.hook.symbol.model.ForumPageAdScanSymbols
import com.forbidad4tieba.hook.symbol.model.ForumPageAdSymbolReadiness
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ForumPageAdSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object ForumPageAdContract : SymbolContract("ForumPageAd") {
    internal override val candidateClasses = listOf(
        "com.baidu.tieba.forum.controller.ForumDialogController",
        "com.baidu.tieba.forum.controller.GameFloatingBarController",
        "com.baidu.tieba.forum.hybrid.biz.BusinessPromotBiz",
    )

    val forumResponseDataClass = text("forumResponseDataClass")
    val forumResponseParserMethod = text("forumResponseParserMethod")
    val forumResponseAdFields = texts("forumResponseAdFields", preserveEmpty = false)
    val forumPageMapperClass = text("forumPageMapperClass")
    val forumBottomDataMapperMethod = text("forumBottomDataMapperMethod")
    val forumBottomDataClass = text("forumBottomDataClass")
    val forumBusinessPromotSetterMethod = text("forumBusinessPromotSetterMethod")
    val forumPrivatePopSetterMethod = text("forumPrivatePopSetterMethod")
    val forumSpriteBubbleSetterMethod = text("forumSpriteBubbleSetterMethod")
    val forumMaskPopSetterMethod = text("forumMaskPopSetterMethod")
    val forumBottomGameBarMapperMethod = text("forumBottomGameBarMapperMethod")
    val forumHeaderDataMapperMethod = text("forumHeaderDataMapperMethod")
    val forumHeaderDataClass = text("forumHeaderDataClass")
    val forumRainDataClass = text("forumRainDataClass")
    val forumRainSetterMethod = text("forumRainSetterMethod")
    val forumDialogControllerClass = text("forumDialogControllerClass")
    val forumBusinessPromotShowMethod = text("forumBusinessPromotShowMethod")
    val forumGameFloatingBarControllerClass = text("forumGameFloatingBarControllerClass")
    val forumGameFloatingBarShowMethod = text("forumGameFloatingBarShowMethod")
    val forumGameFloatingBarField = text("forumGameFloatingBarField")
    val forumBusinessPromotBizClass = text("forumBusinessPromotBizClass")
    val forumBusinessPromotJumpMethod = text("forumBusinessPromotJumpMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        var forumPageAdScan = ForumPageAdScanSymbols()

        forumPageAdScan = runScanStep(
            "ForumPageAdBlockHook",
            logger,
            scanErrors,
            ForumPageAdScanSymbols(),
        ) {
            ForumPageAdSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
        }

        output[ForumPageAdContract.forumResponseDataClass] = forumPageAdScan.responseDataClass
        output[ForumPageAdContract.forumResponseParserMethod] = forumPageAdScan.responseParserMethod
        output[ForumPageAdContract.forumResponseAdFields] = forumPageAdScan.responseAdFields.takeIf { it.isNotEmpty() }
        output[ForumPageAdContract.forumPageMapperClass] = forumPageAdScan.mapperClass
        output[ForumPageAdContract.forumBottomDataMapperMethod] = forumPageAdScan.bottomDataMapperMethod
        output[ForumPageAdContract.forumBottomDataClass] = forumPageAdScan.bottomDataClass
        output[ForumPageAdContract.forumBusinessPromotSetterMethod] = forumPageAdScan.businessPromotSetterMethod
        output[ForumPageAdContract.forumPrivatePopSetterMethod] = forumPageAdScan.privatePopSetterMethod
        output[ForumPageAdContract.forumSpriteBubbleSetterMethod] = forumPageAdScan.spriteBubbleSetterMethod
        output[ForumPageAdContract.forumMaskPopSetterMethod] = forumPageAdScan.maskPopSetterMethod
        output[ForumPageAdContract.forumBottomGameBarMapperMethod] = forumPageAdScan.bottomGameBarMapperMethod
        output[ForumPageAdContract.forumHeaderDataMapperMethod] = forumPageAdScan.headerDataMapperMethod
        output[ForumPageAdContract.forumHeaderDataClass] = forumPageAdScan.headerDataClass
        output[ForumPageAdContract.forumRainDataClass] = forumPageAdScan.rainDataClass
        output[ForumPageAdContract.forumRainSetterMethod] = forumPageAdScan.rainSetterMethod
        output[ForumPageAdContract.forumDialogControllerClass] = forumPageAdScan.dialogControllerClass
        output[ForumPageAdContract.forumBusinessPromotShowMethod] = forumPageAdScan.businessPromotShowMethod
        output[ForumPageAdContract.forumGameFloatingBarControllerClass] = forumPageAdScan.gameFloatingBarControllerClass
        output[ForumPageAdContract.forumGameFloatingBarShowMethod] = forumPageAdScan.gameFloatingBarShowMethod
        output[ForumPageAdContract.forumGameFloatingBarField] = forumPageAdScan.gameFloatingBarField
        output[ForumPageAdContract.forumBusinessPromotBizClass] = forumPageAdScan.businessPromotBizClass
        output[ForumPageAdContract.forumBusinessPromotJumpMethod] = forumPageAdScan.businessPromotJumpMethod
    }

    fun resolveForumPageAdBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ForumPageAdBlockSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ForumPageAdBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val responseTargets = resolveForumPageResponseTargets(cl, resolvedSymbols)
            val bottomTargets = resolveForumPageBottomTargets(cl, resolvedSymbols)
            val bottomGameBarMapper = resolveForumPageMapperMethod(
                cl = cl,
                mapperClassName = resolvedSymbols[ForumPageAdContract.forumPageMapperClass],
                methodName = resolvedSymbols[ForumPageAdContract.forumBottomGameBarMapperMethod],
                returnClassName = null,
                label = "bottom game bar mapper",
            )
            val headerTargets = resolveForumPageHeaderTargets(cl, resolvedSymbols)
            val businessPromotShowMethod = resolveForumPageBusinessDialogMethod(cl, resolvedSymbols)
            val floatingTargets = resolveForumPageFloatingTargets(cl, resolvedSymbols)
            val businessPromotJumpMethod = resolveForumPageBusinessPromotJumpMethod(cl, resolvedSymbols)

            if (
                responseTargets == null &&
                bottomTargets == null &&
                bottomGameBarMapper == null &&
                headerTargets == null &&
                businessPromotShowMethod == null &&
                floatingTargets.first == null &&
                businessPromotJumpMethod == null
            ) {
                Diagnostics.log("[ForumPageAdBlockHook] skipped: no resolved install targets")
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
                businessPromotShowMethod = businessPromotShowMethod,
                gameFloatingBarShowMethod = floatingTargets.first,
                gameFloatingBarField = floatingTargets.second,
                businessPromotJumpMethod = businessPromotJumpMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[ForumPageAdBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolveForumPageResponseTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method, List<Field>>? {
        val className = symbols[ForumPageAdContract.forumResponseDataClass]?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols[ForumPageAdContract.forumResponseParserMethod]?.takeIf { it.isNotBlank() } ?: return null
        val fieldNames = symbols[ForumPageAdContract.forumResponseAdFields].orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (fieldNames.size < 4) return null

        val responseClass = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] response class NOT FOUND: $className")
            return null
        }
        val parser = ScanReflection.collectInstanceMethods(responseClass).singleOrNull { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        } ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] parser method NOT FOUND: $className.$methodName")
            return null
        }
        val fields = fieldNames.mapNotNull { fieldName ->
            findForumPageField(responseClass, fieldName)
        }
        if (fields.size < 4) {
            Diagnostics.log("[ForumPageAdBlockHook] response ad fields not resolved on $className")
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
            mapperClassName = symbols[ForumPageAdContract.forumPageMapperClass],
            methodName = symbols[ForumPageAdContract.forumBottomDataMapperMethod],
            returnClassName = symbols[ForumPageAdContract.forumBottomDataClass],
            label = "bottom data mapper",
        ) ?: return null
        val dataClassName = symbols[ForumPageAdContract.forumBottomDataClass]?.takeIf { it.isNotBlank() } ?: return null
        val dataClass = ScanReflection.safeFindClass(dataClassName, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] bottom data class NOT FOUND: $dataClassName")
            return null
        }
        val setters = listOf(
            symbols[ForumPageAdContract.forumBusinessPromotSetterMethod],
            symbols[ForumPageAdContract.forumPrivatePopSetterMethod],
            symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod],
            symbols[ForumPageAdContract.forumMaskPopSetterMethod],
        ).mapNotNull { methodName ->
            resolveForumPageNullableSetter(dataClass, methodName)
        }.distinct()
        if (setters.size < 3) {
            Diagnostics.log("[ForumPageAdBlockHook] bottom data setters not resolved on $dataClassName")
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
            mapperClassName = symbols[ForumPageAdContract.forumPageMapperClass],
            methodName = symbols[ForumPageAdContract.forumHeaderDataMapperMethod],
            returnClassName = symbols[ForumPageAdContract.forumHeaderDataClass],
            label = "header data mapper",
        ) ?: return null
        val headerClassName = symbols[ForumPageAdContract.forumHeaderDataClass]?.takeIf { it.isNotBlank() } ?: return null
        val rainClassName = symbols[ForumPageAdContract.forumRainDataClass]?.takeIf { it.isNotBlank() } ?: return null
        val setterName = symbols[ForumPageAdContract.forumRainSetterMethod]?.takeIf { it.isNotBlank() } ?: return null
        val headerClass = ScanReflection.safeFindClass(headerClassName, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] header data class NOT FOUND: $headerClassName")
            return null
        }
        val rainClass = ScanReflection.safeFindClass(rainClassName, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] rain data class NOT FOUND: $rainClassName")
            return null
        }
        val setter = ScanReflection.collectInstanceMethods(headerClass).singleOrNull { method ->
            method.name == setterName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(rainClass))
        } ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] rain setter NOT FOUND: $headerClassName.$setterName")
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
        val mapperClass = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] mapper class NOT FOUND: $className")
            return null
        }
        val expectedReturn = returnClassName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                ScanReflection.safeFindClass(it, cl) ?: run {
                    Diagnostics.log("[ForumPageAdBlockHook] $label return class NOT FOUND: $it")
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
            Diagnostics.log("[ForumPageAdBlockHook] $label NOT FOUND: $className.$normalizedMethodName")
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
        return ScanReflection.collectInstanceMethods(dataClass).singleOrNull { method ->
            method.name == normalizedName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive
        }?.apply { isAccessible = true }
    }

    private fun resolveForumPageBusinessDialogMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        val className = symbols[ForumPageAdContract.forumDialogControllerClass]?.takeIf { it.isNotBlank() } ?: return null
        val controllerClass = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] dialog controller class NOT FOUND: $className")
            return null
        }
        val businessShowName = symbols[ForumPageAdContract.forumBusinessPromotShowMethod]?.takeIf { it.isNotBlank() } ?: return null
        return ScanReflection.collectInstanceMethods(controllerClass).singleOrNull { method ->
            method.name == businessShowName &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                !method.parameterTypes[1].isPrimitive
        }?.apply { isAccessible = true }
    }

    private fun resolveForumPageFloatingTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Pair<Method?, Field?> {
        val className = symbols[ForumPageAdContract.forumGameFloatingBarControllerClass]?.takeIf { it.isNotBlank() } ?: return null to null
        val controllerClass = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] floating bar controller class NOT FOUND: $className")
            return null to null
        }
        val showName = symbols[ForumPageAdContract.forumGameFloatingBarShowMethod]?.takeIf { it.isNotBlank() }
        val showMethod = showName?.let { methodName ->
            ScanReflection.collectInstanceMethods(controllerClass).singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
        }
        val fieldName = symbols[ForumPageAdContract.forumGameFloatingBarField]?.takeIf { it.isNotBlank() }
        val floatingBarField = fieldName?.let { findForumPageField(controllerClass, it) }
            ?.apply { isAccessible = true }
        return showMethod to floatingBarField
    }

    private fun resolveForumPageBusinessPromotJumpMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        val className = symbols[ForumPageAdContract.forumBusinessPromotBizClass]?.takeIf { it.isNotBlank() } ?: return null
        val methodName = symbols[ForumPageAdContract.forumBusinessPromotJumpMethod]?.takeIf { it.isNotBlank() } ?: return null
        val bizClass = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] business promot biz class NOT FOUND: $className")
            return null
        }
        return ScanReflection.collectInstanceMethods(bizClass).singleOrNull { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.apply { isAccessible = true } ?: run {
            Diagnostics.log("[ForumPageAdBlockHook] business promot jump method NOT FOUND: $className.$methodName")
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
        Diagnostics.logD("[ForumPageAdBlockHook] field not resolved: ${clazz.name}.$fieldName")
        return null
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.BLOCK_AD_FORUM_PAGE] = if (ForumPageAdSymbolReadiness.evaluate(symbols).any) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = listOf("forumPageAdBlock"),
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        run {
            val readiness = ForumPageAdSymbolReadiness.evaluate(symbols)
            val state = if (readiness.any) HookPointState.FOUND else HookPointState.MISSING
            val missing = if (readiness.any) emptyList() else listOf("forumPageAdPath")
            addStatus(
                HookPointStatus(
                    name = "ForumPageAdBlockHook",
                    state = state,
                    missing = missing,
                    target = readiness.readyLabels.joinToString(",").ifBlank { "-" },
                ),
            )
        }
        addOptional(
            "ForumPageAdBlockHook.Response",
            "${symbols[ForumPageAdContract.forumResponseDataClass]}.${symbols[ForumPageAdContract.forumResponseParserMethod]}" +
                "[${listTarget(symbols[ForumPageAdContract.forumResponseAdFields])}]",
            listOf(
                ForumPageAdContract.forumResponseDataClass.check(symbols),
                ForumPageAdContract.forumResponseParserMethod.check(symbols),
                "forumResponseAdFields" to (symbols[ForumPageAdContract.forumResponseAdFields].orEmpty().count { it.isNotBlank() } >= 4),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.BottomData",
            "${symbols[ForumPageAdContract.forumPageMapperClass]}.${symbols[ForumPageAdContract.forumBottomDataMapperMethod]} -> " +
                "${symbols[ForumPageAdContract.forumBottomDataClass]}.{${symbols[ForumPageAdContract.forumBusinessPromotSetterMethod]}," +
                "${symbols[ForumPageAdContract.forumPrivatePopSetterMethod]},${symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod]}," +
                "${symbols[ForumPageAdContract.forumMaskPopSetterMethod]}}",
            listOf(
                ForumPageAdContract.forumPageMapperClass.check(symbols),
                ForumPageAdContract.forumBottomDataMapperMethod.check(symbols),
                ForumPageAdContract.forumBottomDataClass.check(symbols),
                "forumBottomSetterMethods" to (listOf(
                    symbols[ForumPageAdContract.forumBusinessPromotSetterMethod],
                    symbols[ForumPageAdContract.forumPrivatePopSetterMethod],
                    symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod],
                    symbols[ForumPageAdContract.forumMaskPopSetterMethod],
                ).count { has(it) } >= 3),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.GameBarMapper",
            if (has(symbols[ForumPageAdContract.forumPageMapperClass]) && has(symbols[ForumPageAdContract.forumBottomGameBarMapperMethod])) {
                "${symbols[ForumPageAdContract.forumPageMapperClass]}.${symbols[ForumPageAdContract.forumBottomGameBarMapperMethod]}"
            } else {
                "optional-absent"
            },
            listOf(
                ForumPageAdContract.forumPageMapperClass.check(symbols),
                ForumPageAdContract.forumBottomGameBarMapperMethod.check(symbols),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.Rain",
            "${symbols[ForumPageAdContract.forumPageMapperClass]}.${symbols[ForumPageAdContract.forumHeaderDataMapperMethod]} -> " +
                "${symbols[ForumPageAdContract.forumHeaderDataClass]}.${symbols[ForumPageAdContract.forumRainSetterMethod]}(${symbols[ForumPageAdContract.forumRainDataClass]})",
            listOf(
                ForumPageAdContract.forumPageMapperClass.check(symbols),
                ForumPageAdContract.forumHeaderDataMapperMethod.check(symbols),
                ForumPageAdContract.forumHeaderDataClass.check(symbols),
                ForumPageAdContract.forumRainDataClass.check(symbols),
                ForumPageAdContract.forumRainSetterMethod.check(symbols),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.Dialog",
            if (
                has(symbols[ForumPageAdContract.forumDialogControllerClass]) &&
                has(symbols[ForumPageAdContract.forumBusinessPromotShowMethod])
            ) {
                "${symbols[ForumPageAdContract.forumDialogControllerClass]}.{${symbols[ForumPageAdContract.forumBusinessPromotShowMethod]}}"
            } else {
                "optional-absent"
            },
            listOf(
                ForumPageAdContract.forumDialogControllerClass.check(symbols),
                "forumDialogDisplayMethod" to has(symbols[ForumPageAdContract.forumBusinessPromotShowMethod]),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.FloatingBar",
            if (
                has(symbols[ForumPageAdContract.forumGameFloatingBarControllerClass]) &&
                has(symbols[ForumPageAdContract.forumGameFloatingBarShowMethod])
            ) {
                "${symbols[ForumPageAdContract.forumGameFloatingBarControllerClass]}.{${symbols[ForumPageAdContract.forumGameFloatingBarShowMethod]}}" +
                    "[${symbols[ForumPageAdContract.forumGameFloatingBarField]}]"
            } else {
                "optional-absent"
            },
            listOf(
                ForumPageAdContract.forumGameFloatingBarControllerClass.check(symbols),
                ForumPageAdContract.forumGameFloatingBarShowMethod.check(symbols),
            ),
        )
        addOptional(
            "ForumPageAdBlockHook.BusinessPromotBiz",
            "${symbols[ForumPageAdContract.forumBusinessPromotBizClass]}.${symbols[ForumPageAdContract.forumBusinessPromotJumpMethod]}(String)",
            listOf(
                ForumPageAdContract.forumBusinessPromotBizClass.check(symbols),
                ForumPageAdContract.forumBusinessPromotJumpMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("ForumPageAdBlockHook", true, listOf(HookFeatureKey.BLOCK_AD_FORUM_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasForumPageAdSymbols =
            symbols[ForumPageAdContract.forumResponseDataClass] != null ||
                symbols[ForumPageAdContract.forumResponseParserMethod] != null ||
                !symbols[ForumPageAdContract.forumResponseAdFields].isNullOrEmpty() ||
                symbols[ForumPageAdContract.forumPageMapperClass] != null ||
                symbols[ForumPageAdContract.forumBottomDataMapperMethod] != null ||
                symbols[ForumPageAdContract.forumBottomDataClass] != null ||
                symbols[ForumPageAdContract.forumBusinessPromotSetterMethod] != null ||
                symbols[ForumPageAdContract.forumPrivatePopSetterMethod] != null ||
                symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod] != null ||
                symbols[ForumPageAdContract.forumMaskPopSetterMethod] != null ||
                symbols[ForumPageAdContract.forumBottomGameBarMapperMethod] != null ||
                symbols[ForumPageAdContract.forumHeaderDataMapperMethod] != null ||
                symbols[ForumPageAdContract.forumHeaderDataClass] != null ||
                symbols[ForumPageAdContract.forumRainDataClass] != null ||
                symbols[ForumPageAdContract.forumRainSetterMethod] != null ||
                symbols[ForumPageAdContract.forumDialogControllerClass] != null ||
                symbols[ForumPageAdContract.forumBusinessPromotShowMethod] != null ||
                symbols[ForumPageAdContract.forumGameFloatingBarControllerClass] != null ||
                symbols[ForumPageAdContract.forumGameFloatingBarShowMethod] != null ||
                symbols[ForumPageAdContract.forumGameFloatingBarField] != null ||
                symbols[ForumPageAdContract.forumBusinessPromotBizClass] != null ||
                symbols[ForumPageAdContract.forumBusinessPromotJumpMethod] != null
        if (hasForumPageAdSymbols && !isForumPageAdValid(symbols, cl)) return false
        return true
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
                if (!isForumPageStaticMapperValid(symbols[ForumPageAdContract.forumPageMapperClass], symbols[ForumPageAdContract.forumBottomGameBarMapperMethod], null, cl)) {
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
            Diagnostics.log("$TAG forumPageAd validation failed: ${HookSymbolScanDiagnostics.sanitizeScanStatusText(HookSymbolScanDiagnostics.formatScanException(t))}")
            false
        }
    }

    private fun isForumPageResponsePathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[ForumPageAdContract.forumResponseDataClass] ?: return false
        val methodName = symbols[ForumPageAdContract.forumResponseParserMethod] ?: return false
        val responseClass = ScanReflection.safeFindClass(className, cl) ?: return false
        val hasParser = ScanReflection.collectInstanceMethods(responseClass).any { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        }
        if (!hasParser) return false
        val fieldNames = symbols[ForumPageAdContract.forumResponseAdFields].orEmpty()
            .filter { it.isNotBlank() }
        if (fieldNames.size < 4) return false
        return fieldNames.all { fieldName -> validateFindForumPageField(responseClass, fieldName) != null }
    }

    private fun isForumPageBottomPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!isForumPageStaticMapperValid(
                symbols[ForumPageAdContract.forumPageMapperClass],
                symbols[ForumPageAdContract.forumBottomDataMapperMethod],
                symbols[ForumPageAdContract.forumBottomDataClass],
                cl,
            )
        ) {
            return false
        }
        val dataClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumBottomDataClass] ?: return false, cl) ?: return false
        val setterNames = listOf(
            symbols[ForumPageAdContract.forumBusinessPromotSetterMethod],
            symbols[ForumPageAdContract.forumPrivatePopSetterMethod],
            symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod],
            symbols[ForumPageAdContract.forumMaskPopSetterMethod],
        ).filterNot { it.isNullOrBlank() }
        return setterNames.size >= 3 && setterNames.all { name ->
            ScanReflection.collectInstanceMethods(dataClass).any { method ->
                method.name == name &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    !method.parameterTypes[0].isPrimitive
            }
        }
    }

    private fun isForumPageRainPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!isForumPageStaticMapperValid(
                symbols[ForumPageAdContract.forumPageMapperClass],
                symbols[ForumPageAdContract.forumHeaderDataMapperMethod],
                symbols[ForumPageAdContract.forumHeaderDataClass],
                cl,
            )
        ) {
            return false
        }
        val headerClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumHeaderDataClass] ?: return false, cl) ?: return false
        val rainClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumRainDataClass] ?: return false, cl) ?: return false
        val setterName = symbols[ForumPageAdContract.forumRainSetterMethod] ?: return false
        return ScanReflection.collectInstanceMethods(headerClass).any { method ->
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
        val mapperClass = ScanReflection.safeFindClass(mapperClassName ?: return false, cl) ?: return false
        val expectedReturn = returnClassName
            ?.takeIf { it.isNotBlank() }
            ?.let { ScanReflection.safeFindClass(it, cl) ?: return false }
        return mapperClass.declaredMethods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.returnType != Void.TYPE &&
                (expectedReturn == null || method.returnType == expectedReturn)
        }
    }

    private fun isForumPageDialogPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumDialogControllerClass] ?: return false, cl) ?: return false
        val businessName = symbols[ForumPageAdContract.forumBusinessPromotShowMethod]?.takeIf { it.isNotBlank() } ?: return false
        return ScanReflection.collectInstanceMethods(controllerClass).any { method ->
            method.name == businessName &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                !method.parameterTypes[1].isPrimitive
        }
    }

    private fun isForumPageFloatingPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumGameFloatingBarControllerClass] ?: return false, cl) ?: return false
        val showName = symbols[ForumPageAdContract.forumGameFloatingBarShowMethod]
        if (!showName.isNullOrBlank()) {
            val ok = ScanReflection.collectInstanceMethods(controllerClass).any { method ->
                method.name == showName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
            if (!ok) return false
        }
        val fieldName = symbols[ForumPageAdContract.forumGameFloatingBarField]
        if (!fieldName.isNullOrBlank() && validateFindForumPageField(controllerClass, fieldName) == null) {
            return false
        }
        return !showName.isNullOrBlank() || !fieldName.isNullOrBlank()
    }

    private fun isForumPageBusinessBizPathValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val bizClass = ScanReflection.safeFindClass(symbols[ForumPageAdContract.forumBusinessPromotBizClass] ?: return false, cl) ?: return false
        val methodName = symbols[ForumPageAdContract.forumBusinessPromotJumpMethod] ?: return false
        return ScanReflection.collectInstanceMethods(bizClass).any { method ->
            method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
    }

    private fun validateFindForumPageField(clazz: Class<*>, fieldName: String): Field? {
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
}
