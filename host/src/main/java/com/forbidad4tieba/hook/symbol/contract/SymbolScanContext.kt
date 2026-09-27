package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger

/** Lives only inside the existing DexKit scan session; never retained by a callback. */
internal class SymbolScanContext(
    val context: Context,
    val cl: ClassLoader,
    val logger: ScanLogger?,
    val scanErrors: MutableList<String>,
    val candidatesWithWhitelist: List<String>,
) {
    private val sharedScans = java.util.IdentityHashMap<Any, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> once(key: Any, scan: () -> T): T {
        if (sharedScans.containsKey(key)) return sharedScans[key] as T
        return scan().also { sharedScans[key] = it }
    }

    inline fun <T> runScanStep(
        tag: String,
        logger: ScanLogger?,
        errors: MutableList<String>,
        fallback: T,
        block: () -> T,
    ): T = try {
        block()
    } catch (t: Throwable) {
        HookSymbolScanDiagnostics.recordScanIssue(
            logger, tag, errors, HookSymbolScanDiagnostics.formatScanException(t),
        )
        fallback
    }
}
