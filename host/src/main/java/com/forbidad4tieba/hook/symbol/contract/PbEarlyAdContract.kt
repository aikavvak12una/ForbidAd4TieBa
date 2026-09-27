package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbEarlyAdBlockMethodSymbols
import com.forbidad4tieba.hook.symbol.model.PbEarlyAdBlockSymbols
import com.forbidad4tieba.hook.symbol.model.PbEarlyAdInsertScanSymbols
import com.forbidad4tieba.hook.symbol.scan.PbEarlyAdInsertSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PbEarlyAdContract : SymbolContract("PbEarlyAd") {
    internal override val candidateClasses = listOf(
        PB_AD_INSERT_CLASS,
    )

    val pbEarlyAdInsertClass = text("pbEarlyAdInsertClass")
    val pbEarlyAdInsertMethodSpecs = texts("pbEarlyAdInsertMethodSpecs", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        var pbEarlyAdInsertClass: String? = null

        var pbEarlyAdInsertMethodSpecs: List<String>? = null

        val pbEarlyAdInsertScan = runScanStep(
            "PbEarlyAdBlockHook",
            logger,
            scanErrors,
            PbEarlyAdInsertScanSymbols(null, emptyList()),
        ) {
            PbEarlyAdInsertSymbolScanner.scan(cl, logger)
        }

        if (pbEarlyAdInsertScan.className != null && pbEarlyAdInsertScan.methodSpecs.isNotEmpty()) {
            pbEarlyAdInsertClass = pbEarlyAdInsertScan.className
            pbEarlyAdInsertMethodSpecs = pbEarlyAdInsertScan.methodSpecs
        }

        output[PbEarlyAdContract.pbEarlyAdInsertClass] = pbEarlyAdInsertClass
        output[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs] = pbEarlyAdInsertMethodSpecs
    }

    fun resolvePbEarlyAdBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbEarlyAdBlockSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbEarlyAdBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[PbEarlyAdContract.pbEarlyAdInsertClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbEarlyAdBlockHook] skipped: missing pbEarlyAdInsertClass")
                return null
            }
            val specs = resolvedSymbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs].orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (specs.isEmpty()) {
                Diagnostics.log("[PbEarlyAdBlockHook] skipped: missing pbEarlyAdInsertMethodSpecs")
                return null
            }

            val targetClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[PbEarlyAdBlockHook] skipped: class not found: $className")
                return null
            }

            val methods = specs.mapNotNull { spec ->
                resolvePbEarlyAdBlockMethod(targetClass, spec, cl)
            }
            if (methods.isEmpty()) {
                Diagnostics.log("[PbEarlyAdBlockHook] skipped: no methods resolved on $className")
                return null
            }

            PbEarlyAdBlockSymbols(
                targetClass = targetClass,
                methods = methods,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbEarlyAdBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbEarlyAdBlockMethod(
        targetClass: Class<*>,
        spec: String,
        cl: ClassLoader,
    ): PbEarlyAdBlockMethodSymbols? {
        val parts = spec.split('|', limit = 3)
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) {
            Diagnostics.logD("[PbEarlyAdBlockHook] invalid method spec: $spec")
            return null
        }
        val returnType = ScanReflection.resolveType(parts[1], cl) ?: run {
            Diagnostics.logD("[PbEarlyAdBlockHook] return type not found: ${parts[1]}")
            return null
        }
        val paramTypes = if (parts[2].isBlank()) {
            emptyArray<Class<*>>()
        } else {
            parts[2].split(',').map { typeName ->
                ScanReflection.resolveType(typeName, cl) ?: run {
                    Diagnostics.logD("[PbEarlyAdBlockHook] param type not found: $typeName")
                    return null
                }
            }.toTypedArray()
        }

        val method = try {
            targetClass.getDeclaredMethod(parts[0], *paramTypes).takeIf { candidate ->
                Modifier.isStatic(candidate.modifiers) &&
                    (candidate.returnType == returnType || returnType.isAssignableFrom(candidate.returnType))
            }
        } catch (_: NoSuchMethodException) {
            null
        } ?: run {
            Diagnostics.logD("[PbEarlyAdBlockHook] method not resolved: $spec")
            return null
        }
        method.isAccessible = true
        return PbEarlyAdBlockMethodSymbols(
            method = method,
            returnsSparseArray = android.util.SparseArray::class.java.isAssignableFrom(method.returnType),
        )
    }

    internal const val PB_AD_INSERT_CLASS = "com.baidu.tieba.pb.pb.main.underlayer.PbAdapterManagerInsertUtilKt"

    fun pathStatus(symbols: HookSymbols): HookFeatureStatus {
        val pbEarlyCritical = ArrayList<String>(2)
        if (symbols[PbEarlyAdContract.pbEarlyAdInsertClass].isNullOrBlank()) pbEarlyCritical.add("pbEarlyAdInsertClass")
        if (symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs].orEmpty().isEmpty()) {
            pbEarlyCritical.add("pbEarlyAdInsertMethodSpecs")
        }
        val pbEarlyOptional = if (
            symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs].orEmpty().size in 1 until 2
        ) {
            listOf("pbEarlyAdInsertMethodSpecs")
        } else {
            emptyList()
        }
        val pbEarlyStatus = statusFromMissing(pbEarlyCritical, pbEarlyOptional)
        return pbEarlyStatus
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbEarlyAdBlockHook",
            "${symbols[PbEarlyAdContract.pbEarlyAdInsertClass]}.{${listTarget(symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs])}}",
            listOf(
                PbEarlyAdContract.pbEarlyAdInsertClass.check(symbols),
                PbEarlyAdContract.pbEarlyAdInsertMethodSpecs.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbEarlyAdBlockHook", false, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbEarlyAdInsertSymbols =
            symbols[PbEarlyAdContract.pbEarlyAdInsertClass] != null ||
                symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs] != null
        if (hasPbEarlyAdInsertSymbols && !isPbEarlyAdInsertValid(symbols, cl)) return false
        return true
    }

    private fun isPbEarlyAdInsertValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PbEarlyAdContract.pbEarlyAdInsertClass] ?: return false
        val specs = symbols[PbEarlyAdContract.pbEarlyAdInsertMethodSpecs].orEmpty()
        if (specs.isEmpty()) return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            specs.all { spec ->
                val parts = spec.split('|', limit = 3)
                if (parts.size != 3) return@all false
                val methodName = parts[0]
                val returnType = ScanReflection.resolveType(parts[1], cl) ?: return@all false
                val paramTypes = if (parts[2].isBlank()) {
                    emptyArray<Class<*>>()
                } else {
                    parts[2].split(',').map { typeName ->
                        ScanReflection.resolveType(typeName, cl) ?: return@all false
                    }.toTypedArray()
                }
                val method = targetClass.getDeclaredMethod(methodName, *paramTypes)
                Modifier.isStatic(method.modifiers) &&
                    (method.returnType == returnType || returnType.isAssignableFrom(method.returnType))
            }
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
