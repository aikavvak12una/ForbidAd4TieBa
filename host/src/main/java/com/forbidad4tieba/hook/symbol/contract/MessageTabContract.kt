package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.MsgTabDefaultNotifySymbols
import com.forbidad4tieba.hook.symbol.model.MsgTabScanSymbols
import com.forbidad4tieba.hook.symbol.scan.MsgTabSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object MessageTabContract : SymbolContract("MessageTab") {
    val msgTabLocateToTabMethod = text("msgTabLocateToTabMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val msgTabScan = runScanStep(
            "MsgTabDefaultNotifyHook",
            logger,
            scanErrors,
            MsgTabScanSymbols(),
        ) {
            MsgTabSymbolScanner.scan(context, cl, logger)
        }

        val msgTabLocateToTabMethod: String? = msgTabScan.locateToTabMethod

        output[MessageTabContract.msgTabLocateToTabMethod] = msgTabLocateToTabMethod
    }

    fun resolveMsgTabDefaultNotifySymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): MsgTabDefaultNotifySymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[MsgTabDefaultNotifyHook] skipped: scan symbols unavailable")
                return null
            }
            val methodName = resolvedSymbols[MessageTabContract.msgTabLocateToTabMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[MsgTabDefaultNotifyHook] skipped: missing msgTabLocateToTabMethod")
                return null
            }
            val viewModelClass = ScanReflection.safeFindClass(StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[MsgTabDefaultNotifyHook] skipped: class not found: " +
                        StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS,
                )
                return null
            }
            val method = viewModelClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Long::class.javaPrimitiveType &&
                    candidate.parameterTypes.size == 2 &&
                    candidate.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    candidate.parameterTypes[1] == String::class.java
            } ?: run {
                Diagnostics.log(
                    "[MsgTabDefaultNotifyHook] skipped: method mismatch: " +
                        "${StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS}.$methodName(long,String)",
                )
                return null
            }
            method.isAccessible = true
            MsgTabDefaultNotifySymbols(locateToTabMethod = method)
        } catch (t: Throwable) {
            Diagnostics.log("[MsgTabDefaultNotifyHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val notifyMissingCritical = ArrayList<String>(1)
        if (symbols[MessageTabContract.msgTabLocateToTabMethod].isNullOrBlank()) {
            notifyMissingCritical.add("msgTabLocateToTabMethod")
        }
        out[HookFeatureKey.DEFAULT_NOTIFY_TAB] = if (notifyMissingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = notifyMissingCritical,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "MsgTabDefaultNotifyHook",
            "${StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS}.${symbols[MessageTabContract.msgTabLocateToTabMethod]}(long,String)",
            listOf(MessageTabContract.msgTabLocateToTabMethod.check(symbols)),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("MsgTabDefaultNotifyHook", false, listOf(HookFeatureKey.DEFAULT_NOTIFY_TAB)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true
}
