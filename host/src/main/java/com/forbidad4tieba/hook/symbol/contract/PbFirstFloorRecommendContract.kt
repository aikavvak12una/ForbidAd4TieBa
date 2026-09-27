package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbFirstFloorRecommendInsertScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbFirstFloorRecommendInsertSymbols
import com.forbidad4tieba.hook.symbol.scan.PbFirstFloorRecommendInsertSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object PbFirstFloorRecommendContract : SymbolContract("PbFirstFloorRecommend") {
    val pbFirstFloorRecommendInsertClass = text("pbFirstFloorRecommendInsertClass")
    val pbFirstFloorRecommendInsertMethod = text("pbFirstFloorRecommendInsertMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val pbFirstFloorRecommendInsertScan = runScanStep(
            "PbFirstFloorRecommendBlockHook",
            logger,
            scanErrors,
            PbFirstFloorRecommendInsertScanSymbols(),
        ) {
            PbFirstFloorRecommendInsertSymbolScanner.scan(context, cl, logger)
        }

        val pbFirstFloorRecommendInsertClass: String? = pbFirstFloorRecommendInsertScan.className

        val pbFirstFloorRecommendInsertMethod: String? = pbFirstFloorRecommendInsertScan.methodName

        output[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass] = pbFirstFloorRecommendInsertClass
        output[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod] = pbFirstFloorRecommendInsertMethod
    }

    fun resolvePbFirstFloorRecommendInsertSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbFirstFloorRecommendInsertSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log(
                    "[PbFirstFloorRecommendBlockHook] skipped: scan symbols unavailable",
                )
                return null
            }
            val className = resolvedSymbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass]
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log(
                        "[PbFirstFloorRecommendBlockHook] skipped: missing insert class",
                    )
                    return null
                }
            val methodName = resolvedSymbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod]
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log(
                        "[PbFirstFloorRecommendBlockHook] skipped: missing insert method",
                    )
                    return null
                }
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log(
                    "[PbFirstFloorRecommendBlockHook] class NOT FOUND: $className",
                )
                return null
            }
            val postDataClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_POST_DATA_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[PbFirstFloorRecommendBlockHook] class NOT FOUND: " +
                        StableTiebaHookPoints.PB_POST_DATA_CLASS,
                )
                return null
            }
            val method = targetClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == methodName &&
                    PbFirstFloorRecommendInsertSymbolScanner.isInsertMethod(
                        candidate,
                        postDataClass,
                    )
            } ?: run {
                Diagnostics.log(
                    "[PbFirstFloorRecommendBlockHook] method NOT FOUND: " +
                        "$className.$methodName",
                )
                return null
            }
            method.isAccessible = true
            PbFirstFloorRecommendInsertSymbols(method)
        } catch (t: Throwable) {
            Diagnostics.log(
                "[PbFirstFloorRecommendBlockHook] symbol resolve FAILED: ${t.message}",
            )
            Diagnostics.log(t)
            null
        }
    }

    fun pathStatus(symbols: HookSymbols): HookFeatureStatus {
        val pbFirstFloorRecommendStatus = statusFromMissing(
            listOfNotNull(
                "pbFirstFloorRecommendInsertClass".takeIf {
                    symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass].isNullOrBlank()
                },
                "pbFirstFloorRecommendInsertMethod".takeIf {
                    symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod].isNullOrBlank()
                },
            ),
        )
        return pbFirstFloorRecommendStatus
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbFirstFloorRecommendBlockHook",
            "${symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass]}." +
                symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod],
            listOf(
                "pbFirstFloorRecommendInsertClass" to
                    has(symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass]),
                "pbFirstFloorRecommendInsertMethod" to
                    has(symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod]),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbFirstFloorRecommendBlockHook", false, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbFirstFloorRecommendInsertSymbols =
            symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass] != null ||
                symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod] != null
        if (
            hasPbFirstFloorRecommendInsertSymbols &&
            !isPbFirstFloorRecommendInsertValid(symbols, cl)
        ) {
            return false
        }
        return true
    }

    private fun isPbFirstFloorRecommendInsertValid(
        symbols: HookSymbols,
        cl: ClassLoader,
    ): Boolean {
        val className = symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertClass] ?: return false
        val methodName = symbols[PbFirstFloorRecommendContract.pbFirstFloorRecommendInsertMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            val postDataClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_POST_DATA_CLASS, cl)
                ?: return false
            targetClass.declaredMethods.count { method ->
                method.name == methodName &&
                    PbFirstFloorRecommendInsertSymbolScanner.isInsertMethod(method, postDataClass)
            } == 1
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
