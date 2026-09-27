package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.ViewGroup
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbFallingAdSymbols
import com.forbidad4tieba.hook.symbol.model.PbFallingScanSymbols
import com.forbidad4tieba.hook.symbol.scan.PbFallingSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object PbFallingContract : SymbolContract("PbFalling") {
    internal override val candidateClasses = listOf(
        StableTiebaHookPoints.PB_FALLING_VIEW_CLASS,
    )

    val pbFallingViewClass = text("pbFallingViewClass")
    val pbFallingInitMethod = text("pbFallingInitMethod")
    val pbFallingShowMethod = text("pbFallingShowMethod")
    val pbFallingClearMethod = text("pbFallingClearMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val pbFallingScan = runScanStep(
            "PbFallingAdHook",
            logger,
            scanErrors,
            PbFallingScanSymbols(),
        ) {
            PbFallingSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        val pbFallingViewClass: String? = pbFallingScan.viewClass

        val pbFallingInitMethod: String? = pbFallingScan.initMethod

        val pbFallingShowMethod: String? = pbFallingScan.showMethod

        val pbFallingClearMethod: String? = pbFallingScan.clearMethod

        output[PbFallingContract.pbFallingViewClass] = pbFallingViewClass
        output[PbFallingContract.pbFallingInitMethod] = pbFallingInitMethod
        output[PbFallingContract.pbFallingShowMethod] = pbFallingShowMethod
        output[PbFallingContract.pbFallingClearMethod] = pbFallingClearMethod
    }

    fun resolvePbFallingAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbFallingAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbFallingAdHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[PbFallingContract.pbFallingViewClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbFallingAdHook] skipped: missing pbFallingViewClass")
                return null
            }
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[PbFallingAdHook] skipped: class not found: $className")
                return null
            }
            if (!ViewGroup::class.java.isAssignableFrom(targetClass)) {
                Diagnostics.log("[PbFallingAdHook] skipped: class is not a ViewGroup: $className")
                return null
            }

            val initMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols[PbFallingContract.pbFallingInitMethod],
                label = "init",
                signature = ::isPbFallingInitSignature,
            )
            val showMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols[PbFallingContract.pbFallingShowMethod],
                label = "show",
                signature = ::isPbFallingShowSignature,
            )
            val clearMethod = resolvePbFallingMethod(
                targetClass = targetClass,
                methodName = resolvedSymbols[PbFallingContract.pbFallingClearMethod],
                label = "clear",
                signature = ::isPbFallingClearSignature,
            )

            if (listOfNotNull(initMethod, showMethod, clearMethod).isEmpty()) {
                Diagnostics.log("[PbFallingAdHook] skipped: no usable methods resolved on $className")
                return null
            }

            PbFallingAdSymbols(
                targetClass = targetClass,
                initMethod = initMethod,
                showMethod = showMethod,
                clearMethod = clearMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbFallingAdHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbFallingMethod(
        targetClass: Class<*>,
        methodName: String?,
        label: String,
        signature: (Method) -> Boolean,
    ): Method? {
        val normalizedName = methodName?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[PbFallingAdHook] $label skipped: missing method name")
            return null
        }
        val method = ScanReflection.collectInstanceMethods(targetClass).singleOrNull { candidate ->
            candidate.name == normalizedName && signature(candidate)
        } ?: run {
            Diagnostics.log("[PbFallingAdHook] $label skipped: method mismatch: ${targetClass.name}.$normalizedName")
            return null
        }
        method.isAccessible = true
        return method
    }

    private fun isPbFallingInitSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            Context::class.java.isAssignableFrom(method.parameterTypes[0])
    }

    private fun isPbFallingShowSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[2] == Int::class.javaPrimitiveType &&
            method.parameterTypes[3] == Boolean::class.javaPrimitiveType
    }

    private fun isPbFallingClearSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE && method.parameterTypes.isEmpty()
    }

    fun pathStatus(symbols: HookSymbols): HookFeatureStatus {
        val pbFallingCritical = ArrayList<String>(2)
        val pbFallingOptional = ArrayList<String>(3)
        if (symbols[PbFallingContract.pbFallingViewClass].isNullOrBlank()) pbFallingCritical.add("pbFallingViewClass")
        val pbFallingMethodNames = listOf(
            "pbFallingInitMethod" to symbols[PbFallingContract.pbFallingInitMethod],
            "pbFallingShowMethod" to symbols[PbFallingContract.pbFallingShowMethod],
            "pbFallingClearMethod" to symbols[PbFallingContract.pbFallingClearMethod],
        )
        val missingPbFallingMethods = pbFallingMethodNames
            .filter { (_, value) -> value.isNullOrBlank() }
            .map { (name, _) -> name }
        if (missingPbFallingMethods.size == pbFallingMethodNames.size) {
            pbFallingCritical.add("pbFallingMethods")
        } else {
            pbFallingOptional.addAll(missingPbFallingMethods)
        }
        val pbFallingStatus = statusFromMissing(pbFallingCritical, pbFallingOptional)
        return pbFallingStatus
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbFallingAdHook",
            "${symbols[PbFallingContract.pbFallingViewClass]}.{${symbols[PbFallingContract.pbFallingInitMethod]},${symbols[PbFallingContract.pbFallingShowMethod]},${symbols[PbFallingContract.pbFallingClearMethod]}}",
            listOf(
                PbFallingContract.pbFallingViewClass.check(symbols),
                PbFallingContract.pbFallingInitMethod.check(symbols),
                PbFallingContract.pbFallingShowMethod.check(symbols),
                PbFallingContract.pbFallingClearMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbFallingAdHook", false, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbFallingSymbols =
            symbols[PbFallingContract.pbFallingViewClass] != null ||
                symbols[PbFallingContract.pbFallingInitMethod] != null ||
                symbols[PbFallingContract.pbFallingShowMethod] != null ||
                symbols[PbFallingContract.pbFallingClearMethod] != null
        if (hasPbFallingSymbols && !isPbFallingValid(symbols, cl)) return false
        return true
    }

    private fun isPbFallingValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PbFallingContract.pbFallingViewClass] ?: return false
        val initMethod = symbols[PbFallingContract.pbFallingInitMethod] ?: return false
        val showMethod = symbols[PbFallingContract.pbFallingShowMethod] ?: return false
        val clearMethod = symbols[PbFallingContract.pbFallingClearMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            val hasInit = targetClass.declaredMethods.any { method ->
                method.name == initMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            if (!hasInit) return false
            val hasShow = targetClass.declaredMethods.any { method ->
                method.name == showMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            if (!hasShow) return false
            targetClass.declaredMethods.any { method ->
                method.name == clearMethod &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
