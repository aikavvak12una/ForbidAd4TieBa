package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.ConstantReturnMethodSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.StrategyAdScanSymbols
import com.forbidad4tieba.hook.symbol.model.StrategyAdSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.scan.StrategyAdSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object StrategyAdContract : SymbolContract("StrategyAd") {
    internal override val candidateClasses = listOf(
        "com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt",
        "com.baidu.tbadk.data.CloseAdData",
    )

    val splashAdHelperClass = text("splashAdHelperClass")
    val splashAdHelperMethod = text("splashAdHelperMethod")
    val closeAdDataClass = text("closeAdDataClass")
    val closeAdDataMethodG1 = text("closeAdDataMethodG1")
    val closeAdDataMethodJ1 = text("closeAdDataMethodJ1")
    val zgaClass = text("zgaClass")
    val zgaMethods = texts("zgaMethods", preserveEmpty = true)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val strategyAdScan = runScanStep(
            "StrategyAdHook",
            logger,
            scanErrors,
            StrategyAdScanSymbols(),
        ) {
            StrategyAdSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        val splashAdHelperClass: String? = strategyAdScan.splashAdHelperClass

        val splashAdHelperMethod: String? = strategyAdScan.splashAdHelperMethod

        val closeAdDataClass: String? = strategyAdScan.closeAdDataClass

        val closeAdDataMethodG1: String? = strategyAdScan.closeAdDataMethodG1

        val closeAdDataMethodJ1: String? = strategyAdScan.closeAdDataMethodJ1

        val zgaClass: String? = strategyAdScan.zgaClass

        val zgaMethodsList: List<String>? = strategyAdScan.zgaMethods

        output[StrategyAdContract.splashAdHelperClass] = splashAdHelperClass
        output[StrategyAdContract.splashAdHelperMethod] = splashAdHelperMethod
        output[StrategyAdContract.closeAdDataClass] = closeAdDataClass
        output[StrategyAdContract.closeAdDataMethodG1] = closeAdDataMethodG1
        output[StrategyAdContract.closeAdDataMethodJ1] = closeAdDataMethodJ1
        output[StrategyAdContract.zgaClass] = zgaClass
        output[StrategyAdContract.zgaMethods] = zgaMethodsList
    }

    fun resolveStrategyAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): StrategyAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[StrategyAdHook] skipped: scan symbols unavailable")
                return null
            }
            val constants = ArrayList<ConstantReturnMethodSymbols>(4)
            resolveConstantReturnMethod(
                cl = cl,
                className = resolvedSymbols[StrategyAdContract.splashAdHelperClass],
                methodName = resolvedSymbols[StrategyAdContract.splashAdHelperMethod],
                value = true,
                label = "splash helper",
            )?.let(constants::add)

            val closeAdMethods = listOfNotNull(
                resolvedSymbols[StrategyAdContract.closeAdDataMethodG1],
                resolvedSymbols[StrategyAdContract.closeAdDataMethodJ1],
            ).flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (!resolvedSymbols[StrategyAdContract.closeAdDataClass].isNullOrBlank() && closeAdMethods.isNotEmpty()) {
                for (methodName in closeAdMethods) {
                    resolveConstantReturnMethod(
                        cl = cl,
                        className = resolvedSymbols[StrategyAdContract.closeAdDataClass],
                        methodName = methodName,
                        value = 1,
                        label = "CloseAdData",
                    )?.let(constants::add)
                }
            } else {
                Diagnostics.logD("[StrategyAdHook] CloseAdData skipped: methods not resolved by scan")
            }

            val zgaMethods = resolveZgaStringMethods(cl, resolvedSymbols)
            if (constants.isEmpty() && zgaMethods.isEmpty()) {
                Diagnostics.log("[StrategyAdHook] skipped: no resolved symbol targets")
                return null
            }
            StrategyAdSymbols(
                constantReturnMethods = constants,
                zgaMethods = zgaMethods,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[StrategyAdHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
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
            Diagnostics.logD("[StrategyAdHook] $label skipped: scan symbols missing")
            return null
        }
        val clazz = ScanReflection.safeFindClass(className, cl) ?: run {
            Diagnostics.logD { "[StrategyAdHook] class NOT FOUND: $className (for $methodName)" }
            return null
        }
        val method = MemberAccess.findMethodOrNull(clazz, methodName) ?: run {
            Diagnostics.logD { "[StrategyAdHook] method NOT FOUND: ${clazz.simpleName}.$methodName" }
            return null
        }
        method.isAccessible = true
        return ConstantReturnMethodSymbols(method = method, value = value)
    }

    private fun resolveZgaStringMethods(cl: ClassLoader, symbols: HookSymbols): List<Method> {
        val zgaClassName = symbols[StrategyAdContract.zgaClass]?.takeIf { it.isNotBlank() } ?: return emptyList()
        val zgaMethods = symbols[StrategyAdContract.zgaMethods].orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (zgaMethods.isEmpty()) return emptyList()
        val zgaClass = ScanReflection.safeFindClass(zgaClassName, cl) ?: return emptyList()
        if (zgaClass.isInterface || Modifier.isAbstract(zgaClass.modifiers)) {
            Diagnostics.logD { "[StrategyAdHook] zga skipped: target class is abstract/interface: $zgaClassName" }
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

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val strategyOptional = ArrayList<String>(9)
        strategyOptional.addAll(FeedContract.capabilities(symbols).getValue(HookFeatureKey.BLOCK_AD_FEED).missingCritical)
        strategyOptional.addAll(FeedContract.capabilities(symbols).getValue(HookFeatureKey.BLOCK_AD_FEED).missingOptional)
        if (symbols[StrategyAdContract.splashAdHelperClass].isNullOrBlank()) strategyOptional.add("splashAdHelperClass")
        if (symbols[StrategyAdContract.splashAdHelperMethod].isNullOrBlank()) strategyOptional.add("splashAdHelperMethod")
        if (symbols[StrategyAdContract.closeAdDataClass].isNullOrBlank()) strategyOptional.add("closeAdDataClass")
        if (symbols[StrategyAdContract.closeAdDataMethodG1].isNullOrBlank()) strategyOptional.add("closeAdDataMethodG1")
        if (symbols[StrategyAdContract.closeAdDataMethodJ1].isNullOrBlank()) strategyOptional.add("closeAdDataMethodJ1")
        if (symbols[StrategyAdContract.zgaClass].isNullOrBlank()) strategyOptional.add("zgaClass")
        if (symbols[StrategyAdContract.zgaMethods].isNullOrEmpty()) strategyOptional.add("zgaMethods")
        out[HookFeatureKey.BLOCK_AD_STRATEGY] = HookFeatureStatus(
            state = if (strategyOptional.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL,
            missingOptional = strategyOptional,
        )
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "StrategyAdHook.Splash",
            "${symbols[StrategyAdContract.splashAdHelperClass]}.${symbols[StrategyAdContract.splashAdHelperMethod]}",
            listOf(
                StrategyAdContract.splashAdHelperClass.check(symbols),
                StrategyAdContract.splashAdHelperMethod.check(symbols),
            ),
        )
        add(
            "StrategyAdHook.CloseAd",
            "${symbols[StrategyAdContract.closeAdDataClass]}.{${symbols[StrategyAdContract.closeAdDataMethodG1]},${symbols[StrategyAdContract.closeAdDataMethodJ1]}}",
            listOf(
                StrategyAdContract.closeAdDataClass.check(symbols),
                StrategyAdContract.closeAdDataMethodG1.check(symbols),
                StrategyAdContract.closeAdDataMethodJ1.check(symbols),
            ),
        )
        add(
            "StrategyAdHook.Zga",
            "${symbols[StrategyAdContract.zgaClass]}.{${listTarget(symbols[StrategyAdContract.zgaMethods])}}",
            listOf(
                StrategyAdContract.zgaClass.check(symbols),
                StrategyAdContract.zgaMethods.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("StrategyAdHook.", true, listOf(HookFeatureKey.BLOCK_AD_STRATEGY)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true
}
