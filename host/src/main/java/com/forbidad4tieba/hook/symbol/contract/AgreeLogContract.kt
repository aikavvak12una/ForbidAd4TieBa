package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.AgreeServerResponseLogSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ReplyVisibilityProbeSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import org.json.JSONObject

/** Owns the cached descriptors and host rules for this capability. */
object AgreeLogContract : SymbolContract("AgreeLog") {
    val agreeServerResponseClass = text("agreeServerResponseClass")
    val agreeServerResponseDecodeLogicMethod = text("agreeServerResponseDecodeLogicMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        var agreeServerResponseClass: String? = null

        var agreeServerResponseDecodeLogicMethod: String? = null

        runScanStep("AgreeServerResponseLogHook", logger, scanErrors, Unit) {
            ReplyVisibilityProbeSymbolScanner.scanAgreeServerResponseLog(cl, logger)?.let { scan ->
                agreeServerResponseClass = scan.responseClass
                agreeServerResponseDecodeLogicMethod = scan.decodeLogicMethod
            }
        }

        output[AgreeLogContract.agreeServerResponseClass] = agreeServerResponseClass
        output[AgreeLogContract.agreeServerResponseDecodeLogicMethod] = agreeServerResponseDecodeLogicMethod
    }

    fun resolveAgreeServerResponseLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AgreeServerResponseLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[AgreeServerResponseLogHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[AgreeLogContract.agreeServerResponseClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[AgreeServerResponseLogHook] skipped: response class missing")
                return null
            }
            val decodeLogicMethodName =
                resolvedSymbols[AgreeLogContract.agreeServerResponseDecodeLogicMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[AgreeServerResponseLogHook] skipped: decode logic method missing")
                    return null
                }
            val responseClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[AgreeServerResponseLogHook] skipped: class not found: $className")
                return null
            }
            val decodeLogicMethod = responseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isAgreeServerResponseDecodeLogicMethod(method, decodeLogicMethodName)
            } ?: run {
                Diagnostics.log(
                    "[AgreeServerResponseLogHook] skipped: method not found: " +
                        "$className.$decodeLogicMethodName(int,JSONObject)",
                )
                return null
            }
            decodeLogicMethod.isAccessible = true
            AgreeServerResponseLogSymbols(decodeLogicMethod = decodeLogicMethod)
        } catch (t: Throwable) {
            Diagnostics.log("[AgreeServerResponseLogHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AgreeServerResponseLogHook",
            "${symbols[AgreeLogContract.agreeServerResponseClass]}.${symbols[AgreeLogContract.agreeServerResponseDecodeLogicMethod]}",
            listOf(
                AgreeLogContract.agreeServerResponseClass.check(symbols),
                AgreeLogContract.agreeServerResponseDecodeLogicMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AgreeServerResponseLogHook", false, listOf(HookFeatureKey.DETAILED_LOGGING)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
