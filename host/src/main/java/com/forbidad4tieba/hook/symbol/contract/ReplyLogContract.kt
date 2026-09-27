package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.ReplyServerResponseLogSymbols
import com.forbidad4tieba.hook.symbol.scan.ReplyVisibilityProbeSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import org.json.JSONObject

/** Owns the cached descriptors and host rules for this capability. */
object ReplyLogContract : SymbolContract("ReplyLog") {
    val replyServerResponseClass = text("replyServerResponseClass")
    val replyServerResponseDecodeMethod = text("replyServerResponseDecodeMethod")
    val replyServerResponseResultJsonField = text("replyServerResponseResultJsonField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        var replyServerResponseClass: String? = null

        var replyServerResponseDecodeMethod: String? = null

        var replyServerResponseResultJsonField: String? = null

        runScanStep("ReplyServerResponseLogHook", logger, scanErrors, Unit) {
            ReplyVisibilityProbeSymbolScanner.scanReplyServerResponseLog(cl, logger)?.let { scan ->
                replyServerResponseClass = scan.responseClass
                replyServerResponseDecodeMethod = scan.decodeMethod
                replyServerResponseResultJsonField = scan.resultJsonField
            }
        }

        output[ReplyLogContract.replyServerResponseClass] = replyServerResponseClass
        output[ReplyLogContract.replyServerResponseDecodeMethod] = replyServerResponseDecodeMethod
        output[ReplyLogContract.replyServerResponseResultJsonField] = replyServerResponseResultJsonField
    }

    fun resolveReplyServerResponseLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ReplyServerResponseLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ReplyServerResponseLogHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[ReplyLogContract.replyServerResponseClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[ReplyServerResponseLogHook] skipped: response class missing")
                return null
            }
            val decodeMethodName = resolvedSymbols[ReplyLogContract.replyServerResponseDecodeMethod]?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log("[ReplyServerResponseLogHook] skipped: decode method missing")
                    return null
                }
            val resultJsonFieldName =
                resolvedSymbols[ReplyLogContract.replyServerResponseResultJsonField]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[ReplyServerResponseLogHook] skipped: result JSON field missing")
                    return null
                }
            val responseClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[ReplyServerResponseLogHook] skipped: class not found: $className")
                return null
            }
            val decodeMethod = responseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isReplyServerResponseDecodeMethod(method, decodeMethodName)
            } ?: run {
                Diagnostics.log("[ReplyServerResponseLogHook] skipped: method not found: $className.$decodeMethodName")
                return null
            }
            val resultJsonField = responseClass.declaredFields.singleOrNull { field ->
                field.name == resultJsonFieldName && JSONObject::class.java.isAssignableFrom(field.type)
            } ?: run {
                Diagnostics.log(
                    "[ReplyServerResponseLogHook] skipped: field not found: $className.$resultJsonFieldName",
                )
                return null
            }
            decodeMethod.isAccessible = true
            resultJsonField.isAccessible = true
            ReplyServerResponseLogSymbols(
                decodeMethod = decodeMethod,
                resultJsonField = resultJsonField,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[ReplyServerResponseLogHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "ReplyServerResponseLogHook",
            "${symbols[ReplyLogContract.replyServerResponseClass]}.${symbols[ReplyLogContract.replyServerResponseDecodeMethod]}" +
                "[${symbols[ReplyLogContract.replyServerResponseResultJsonField]}]",
            listOf(
                ReplyLogContract.replyServerResponseClass.check(symbols),
                ReplyLogContract.replyServerResponseDecodeMethod.check(symbols),
                ReplyLogContract.replyServerResponseResultJsonField.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("ReplyServerResponseLogHook", false, listOf(HookFeatureKey.DETAILED_LOGGING)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
