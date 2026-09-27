package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.hasList
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarHotTopicGuideSymbols
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarStableSymbols
import com.forbidad4tieba.hook.symbol.scan.PbBottomEnterBarSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PbBottomBannerContract : SymbolContract("PbBottomBanner") {
    val pbBottomEnterBarViewClass = text("pbBottomEnterBarViewClass")
    val pbBottomEnterBarConstructorCount = number("pbBottomEnterBarConstructorCount")
    val pbBottomEnterBarRefreshMethodSpecs = texts("pbBottomEnterBarRefreshMethodSpecs", preserveEmpty = false)
    val pbEnterFrsAnimationTipViewClass = text("pbEnterFrsAnimationTipViewClass")
    val pbEnterFrsAnimationTipConstructorCount = number("pbEnterFrsAnimationTipConstructorCount")
    val pbEnterFrsAnimationTipCallerClasses = texts("pbEnterFrsAnimationTipCallerClasses", preserveEmpty = false)
    val pbHotTopicGuideTotalViewMethod = text("pbHotTopicGuideTotalViewMethod")
    val pbHotTopicGuideRefreshMethodSpecs = texts("pbHotTopicGuideRefreshMethodSpecs", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        var pbBottomEnterBarRefreshMethodSpecs: List<String>? = null

        var pbEnterFrsAnimationTipCallerClasses: List<String>? = null

        var pbHotTopicGuideRefreshMethodSpecs: List<String>? = null

        val pbBottomEnterBarScan = runScanStep(
            "PbBottomEnterBarHook",
            logger,
            scanErrors,
            PbBottomEnterBarScanSymbols(),
        ) {
            PbBottomEnterBarSymbolScanner.scan(cl, logger)
        }

        val pbBottomEnterBarViewClass: String? = pbBottomEnterBarScan.bottomEnterBarViewClass

        val pbBottomEnterBarConstructorCount: Int? = pbBottomEnterBarScan.bottomEnterBarConstructorCount

        pbBottomEnterBarRefreshMethodSpecs =
            pbBottomEnterBarScan.bottomEnterBarRefreshMethodSpecs.takeIf { it.isNotEmpty() }

        val pbEnterFrsAnimationTipViewClass: String? = pbBottomEnterBarScan.enterFrsAnimationTipViewClass

        val pbEnterFrsAnimationTipConstructorCount: Int? = pbBottomEnterBarScan.enterFrsAnimationTipConstructorCount

        pbEnterFrsAnimationTipCallerClasses =
            pbBottomEnterBarScan.enterFrsAnimationTipCallerClasses.takeIf { it.isNotEmpty() }

        val pbHotTopicGuideTotalViewMethod: String? = pbBottomEnterBarScan.hotTopicGuideTotalViewMethod

        pbHotTopicGuideRefreshMethodSpecs =
            pbBottomEnterBarScan.hotTopicGuideRefreshMethodSpecs.takeIf { it.isNotEmpty() }

        output[PbBottomBannerContract.pbBottomEnterBarViewClass] = pbBottomEnterBarViewClass
        output[PbBottomBannerContract.pbBottomEnterBarConstructorCount] = pbBottomEnterBarConstructorCount
        output[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs] = pbBottomEnterBarRefreshMethodSpecs
        output[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass] = pbEnterFrsAnimationTipViewClass
        output[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount] = pbEnterFrsAnimationTipConstructorCount
        output[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses] = pbEnterFrsAnimationTipCallerClasses
        output[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod] = pbHotTopicGuideTotalViewMethod
        output[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs] = pbHotTopicGuideRefreshMethodSpecs
    }

    fun resolvePbBottomEnterBarStableSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbBottomEnterBarStableSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbBottomEnterBarHook] stable skipped: scan symbols unavailable")
                return null
            }
            val bottomClass = resolvedSymbols[PbBottomBannerContract.pbBottomEnterBarViewClass]
                ?.takeIf { it.isNotBlank() }
                ?.let { className ->
                    ScanReflection.safeFindClass(className, cl) ?: run {
                        Diagnostics.log("[PbBottomEnterBarHook] stable skipped: bottom class not found: $className")
                        null
                    }
                }
            val bottomRefreshMethods = bottomClass?.let { clazz ->
                resolvedSymbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs].orEmpty()
                    .mapNotNull { spec -> resolvePbBottomEnterBarRefreshMethod(clazz, spec, cl) }
                    .distinct()
            }.orEmpty()
            val animationTipClass = resolvedSymbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass]
                ?.takeIf { it.isNotBlank() }
                ?.let { className ->
                    ScanReflection.safeFindClass(className, cl) ?: run {
                        Diagnostics.log("[PbBottomEnterBarHook] stable skipped: animation tip class not found: $className")
                        null
                    }
                }
            if (bottomClass == null && animationTipClass == null) {
                Diagnostics.log("[PbBottomEnterBarHook] stable skipped: no cached stable targets resolved")
                return null
            }
            bottomRefreshMethods.forEach { it.isAccessible = true }
            PbBottomEnterBarStableSymbols(
                bottomEnterBarViewClass = bottomClass,
                bottomEnterBarRefreshMethods = bottomRefreshMethods,
                enterFrsAnimationTipViewClass = animationTipClass,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbBottomEnterBarHook] stable symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
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
            Diagnostics.logD("[PbBottomEnterBarHook] invalid bottom refresh method spec: $spec")
            return null
        }
        val paramTypes = if (parts[1].isBlank()) {
            emptyArray<Class<*>>()
        } else {
            parts[1].split(',').map { typeName ->
                ScanReflection.resolveType(typeName, cl) ?: run {
                    Diagnostics.logD("[PbBottomEnterBarHook] bottom refresh param type not found: $typeName")
                    return null
                }
            }.toTypedArray()
        }
        val method = try {
            targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        } catch (_: NoSuchMethodException) {
            Diagnostics.logD("[PbBottomEnterBarHook] bottom refresh method not resolved: $spec")
            return null
        }
        return method.takeIf(::isPbBottomEnterBarRefreshSignature) ?: run {
            Diagnostics.logD("[PbBottomEnterBarHook] bottom refresh method mismatch: $spec")
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
        symbols: HookSymbols?,
    ): PbBottomEnterBarHotTopicGuideSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbBottomEnterBarHook] hot topic guide skipped: scan symbols unavailable")
                return null
            }
            val totalViewMethodName = resolvedSymbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod]
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log("[PbBottomEnterBarHook] hot topic guide skipped: missing total view method")
                    return null
                }
            val guideClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS, cl) ?: run {
                Diagnostics.log(
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
                Diagnostics.log(
                    "[PbBottomEnterBarHook] hot topic guide skipped: total view method mismatch: " +
                        "${guideClass.name}.$totalViewMethodName",
                )
                return null
            }
            val refreshMethods = resolvedSymbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs].orEmpty()
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
            Diagnostics.log("[PbBottomEnterBarHook] hot topic guide symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbHotTopicGuideRefreshMethod(targetClass: Class<*>, spec: String): Method? {
        val parts = spec.split('|', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) {
            Diagnostics.logD("[PbBottomEnterBarHook] invalid hot topic guide method spec: $spec")
            return null
        }
        val paramTypes: Array<Class<*>> = when (parts[1].trim()) {
            "" -> emptyArray()
            "int" -> arrayOf(Integer.TYPE)
            else -> {
                Diagnostics.logD("[PbBottomEnterBarHook] unsupported hot topic guide method spec: $spec")
                return null
            }
        }
        val method = try {
            targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        } catch (_: NoSuchMethodException) {
            Diagnostics.logD("[PbBottomEnterBarHook] hot topic guide method not resolved: $spec")
            return null
        }
        return method.takeIf(::isPbHotTopicGuideRefreshSignature) ?: run {
            Diagnostics.logD("[PbBottomEnterBarHook] hot topic guide method mismatch: $spec")
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

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val pbBottomEnterBarReady =
            isBottomEnterBarReady(symbols)
        val pbBottomEnterBarComplete =
            pbBottomEnterBarReady &&
                !symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs].isNullOrEmpty()
        val pbEnterFrsAnimationTipReady =
            isAnimationTipReady(symbols)
        val pbEnterFrsAnimationTipComplete =
            pbEnterFrsAnimationTipReady &&
                symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses].orEmpty().containsAll(
                    listOf(
                        StableTiebaHookPoints.PB_VIEW_UTIL_KT_CLASS,
                        StableTiebaHookPoints.SPRITE_ANIMATION_TIP_MANAGER_CLASS,
                    ),
                )
        val pbHotTopicGuideReady =
            isHotTopicGuideReady(symbols)
        val pbBottomBannerMissing = ArrayList<String>(3)
        if (!pbBottomEnterBarComplete) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.BottomEnterBarView")
        }
        if (!pbEnterFrsAnimationTipComplete) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.AnimationTip")
        }
        if (!pbHotTopicGuideReady) {
            pbBottomBannerMissing.add("PbBottomEnterBarHook.HotTopicGuide")
        }
        out[HookFeatureKey.HIDE_PB_BOTTOM_BANNER] = when {
            pbBottomBannerMissing.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            pbBottomEnterBarReady || pbEnterFrsAnimationTipReady || pbHotTopicGuideReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = pbBottomBannerMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbBottomBannerMissing,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        run {
            val hasViewClass = has(symbols[PbBottomBannerContract.pbBottomEnterBarViewClass])
            val hasConstructors = (symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount] ?: 0) > 0
            val hasRefreshMethods = hasList(symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs])
            val state = when {
                !hasViewClass || !hasConstructors -> HookPointState.MISSING
                !hasRefreshMethods -> HookPointState.PARTIAL
                else -> HookPointState.FOUND
            }
            val missing = buildList {
                if (!hasViewClass) add("pbBottomEnterBarViewClass")
                if (!hasConstructors) add("pbBottomEnterBarConstructorCount")
                if (!hasRefreshMethods) add("pbBottomEnterBarRefreshMethodSpecs")
            }
            addStatus(
                HookPointStatus(
                    name = "PbBottomEnterBarHook.BottomEnterBarView",
                    state = state,
                    missing = missing,
                    target = (
                        "${symbols[PbBottomBannerContract.pbBottomEnterBarViewClass] ?: StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS}" +
                            " constructors=${symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount] ?: "-"} refresh={" +
                            listTarget(symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs]) +
                            "}"
                        ),
                ),
            )
        }
        run {
            val requiredCallers = listOf(
                StableTiebaHookPoints.PB_VIEW_UTIL_KT_CLASS,
                StableTiebaHookPoints.SPRITE_ANIMATION_TIP_MANAGER_CLASS,
            )
            val callerClasses = symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses].orEmpty()
            val hasViewClass = has(symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass])
            val hasConstructors = (symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount] ?: 0) > 0
            val hasCallers = callerClasses.containsAll(requiredCallers)
            val state = when {
                !hasViewClass || !hasConstructors -> HookPointState.MISSING
                !hasCallers -> HookPointState.PARTIAL
                else -> HookPointState.FOUND
            }
            val missing = buildList {
                if (!hasViewClass) add("pbEnterFrsAnimationTipViewClass")
                if (!hasConstructors) add("pbEnterFrsAnimationTipConstructorCount")
                requiredCallers
                    .filterNot { callerClasses.contains(it) }
                    .forEach { add(it.substringAfterLast('.')) }
            }
            addStatus(
                HookPointStatus(
                    name = "PbBottomEnterBarHook.AnimationTip",
                    state = state,
                    missing = missing,
                    target = (
                        "${symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass] ?: StableTiebaHookPoints.TB_ANIMATION_TIP_VIEW_CLASS}" +
                            " constructors=${symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount] ?: "-"} callers={" +
                            listTarget(symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses]) +
                            "}"
                        ),
                ),
            )
        }
        run {
            val hasTotalView = has(symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod])
            val hasRefreshMethods = hasList(symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs])
            val state = when {
                !hasTotalView -> HookPointState.MISSING
                !hasRefreshMethods -> HookPointState.PARTIAL
                else -> HookPointState.FOUND
            }
            val missing = buildList {
                if (!hasTotalView) add("pbHotTopicGuideTotalViewMethod")
                if (!hasRefreshMethods) add("pbHotTopicGuideRefreshMethodSpecs")
            }
            addStatus(
                HookPointStatus(
                    name = "PbBottomEnterBarHook.HotTopicGuide",
                    state = state,
                    missing = missing,
                    target = (
                        "${StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS}." +
                            "${symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod]} refresh={" +
                            listTarget(symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs]) +
                            "}"
                        ),
                ),
            )
        }
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbBottomEnterBarHook", false, listOf(HookFeatureKey.HIDE_PB_BOTTOM_BANNER)),
        PointOwner("PbBottomEnterBarHook.", true, listOf(HookFeatureKey.HIDE_PB_BOTTOM_BANNER)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbBottomEnterBarSymbols =
            symbols[PbBottomBannerContract.pbBottomEnterBarViewClass] != null ||
                symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount] != null ||
                !symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs].isNullOrEmpty() ||
                symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass] != null ||
                symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount] != null ||
                !symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses].isNullOrEmpty() ||
            symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod] != null ||
                !symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs].isNullOrEmpty()
        if (hasPbBottomEnterBarSymbols && !isPbBottomEnterBarValid(symbols, cl)) return false
        return true
    }

    private fun isPbBottomEnterBarValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return isPbBottomEnterBarViewValid(symbols, cl) &&
            isPbEnterFrsAnimationTipValid(symbols, cl) &&
            isPbBottomEnterBarHotTopicGuideValid(symbols, cl)
    }

    private fun isPbBottomEnterBarViewValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PbBottomBannerContract.pbBottomEnterBarViewClass] ?: return true
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount]?.let { expected ->
                if (expected != targetClass.declaredConstructors.size) return false
            }
            symbols[PbBottomBannerContract.pbBottomEnterBarRefreshMethodSpecs].orEmpty().all { spec ->
                isPbBottomEnterBarRefreshMethodValid(targetClass, spec)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbBottomEnterBarRefreshMethodValid(targetClass: Class<*>, spec: String): Boolean {
        val parts = spec.split('|', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return false
        val classLoader = targetClass.classLoader ?: return false
        val paramTypes = when (parts[1].trim()) {
            "" -> emptyArray<Class<*>>()
            else -> arrayOf<Class<*>>(
                resolveParameterType(parts[1].trim(), classLoader) ?: return false,
            )
        }
        val method = targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        return (method.name == "setData" && method.parameterTypes.size == 1) ||
            (method.name == "onChangeSkinType" && method.parameterTypes.isEmpty())
    }

    private fun resolveParameterType(name: String, cl: ClassLoader): Class<*>? {
        return when (name) {
            "boolean" -> java.lang.Boolean.TYPE
            "byte" -> java.lang.Byte.TYPE
            "char" -> java.lang.Character.TYPE
            "double" -> java.lang.Double.TYPE
            "float" -> java.lang.Float.TYPE
            "int" -> Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "short" -> java.lang.Short.TYPE
            else -> ScanReflection.safeFindClass(name, cl)
        }
    }

    private fun isPbEnterFrsAnimationTipValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass] ?: return true
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount]?.let { expected ->
                if (expected != targetClass.declaredConstructors.size) return false
            }
            symbols[PbBottomBannerContract.pbEnterFrsAnimationTipCallerClasses].orEmpty().all { callerClass ->
                ScanReflection.safeFindClass(callerClass, cl) != null
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbBottomEnterBarHotTopicGuideValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (
            symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod] == null &&
            symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs].isNullOrEmpty()
        ) {
            return true
        }
        val totalViewMethodName = symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS, cl) ?: return false
            val totalViewMethod = targetClass.getDeclaredMethod(totalViewMethodName)
            if (
                Modifier.isStatic(totalViewMethod.modifiers) ||
                totalViewMethod.parameterTypes.isNotEmpty() ||
                totalViewMethod.returnType != View::class.java
            ) {
                return false
            }
            symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs].orEmpty().all { spec ->
                isPbHotTopicGuideRefreshMethodValid(targetClass, spec)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbHotTopicGuideRefreshMethodValid(targetClass: Class<*>, spec: String): Boolean {
        val parts = spec.split('|', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return false
        val paramTypes = when (parts[1].trim()) {
            "" -> emptyArray<Class<*>>()
            "int" -> arrayOf<Class<*>>(Integer.TYPE)
            else -> return false
        }
        val method = targetClass.getDeclaredMethod(parts[0].trim(), *paramTypes)
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        return true
    }
    private fun isBottomEnterBarReady(symbols: HookSymbols): Boolean =
        !symbols[PbBottomBannerContract.pbBottomEnterBarViewClass].isNullOrBlank() &&
            (symbols[PbBottomBannerContract.pbBottomEnterBarConstructorCount] ?: 0) > 0

    private fun isAnimationTipReady(symbols: HookSymbols): Boolean =
        !symbols[PbBottomBannerContract.pbEnterFrsAnimationTipViewClass].isNullOrBlank() &&
            (symbols[PbBottomBannerContract.pbEnterFrsAnimationTipConstructorCount] ?: 0) > 0

    fun isHotTopicGuideReady(symbols: HookSymbols): Boolean =
        !symbols[PbBottomBannerContract.pbHotTopicGuideTotalViewMethod].isNullOrBlank() &&
            !symbols[PbBottomBannerContract.pbHotTopicGuideRefreshMethodSpecs].isNullOrEmpty()

    fun isStableReady(symbols: HookSymbols): Boolean =
        isBottomEnterBarReady(symbols) || isAnimationTipReady(symbols)

}
