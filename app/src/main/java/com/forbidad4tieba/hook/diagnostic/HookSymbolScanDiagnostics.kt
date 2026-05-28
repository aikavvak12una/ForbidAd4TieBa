package com.forbidad4tieba.hook.diagnostic

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.core.XposedCompat

internal object HookSymbolScanDiagnostics {
    private const val TAG = "[HookSymbolResolver]"

    fun log(logger: ScanLogger?, line: String) {
        try {
            XposedCompat.log("$TAG: $line")
        } catch (t: Throwable) {
            XposedCompat.logD("HookSymbolResolver: ${t.message}")
        }
        try {
            logger?.log(line)
        } catch (t: Throwable) {
            XposedCompat.logD("HookSymbolResolver: ${t.message}")
        }
    }

    fun formatScanException(t: Throwable): String {
        val type = t::class.java.name
        val message = t.message?.trim().orEmpty()
        return if (message.isNotEmpty()) "$type: $message" else type
    }

    fun sanitizeScanStatusText(raw: String): String {
        return raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(240)
            .ifBlank { "unknown" }
    }

    fun recordScanIssue(
        logger: ScanLogger?,
        tag: String,
        errors: MutableList<String>,
        detail: String,
    ) {
        val error = "${tag.trim()} :: ${sanitizeScanStatusText(detail)}"
        if (!errors.contains(error)) {
            errors.add(error)
        }
        log(logger, "$tag scan exception: ${splitScanError(error).second}")
    }

    private fun splitScanError(error: String): Pair<String, String> {
        val separator = " :: "
        val idx = error.indexOf(separator)
        if (idx <= 0) return "ScanException" to sanitizeScanStatusText(error)
        val tag = error.substring(0, idx).trim().ifEmpty { "ScanException" }
        val detail = error.substring(idx + separator.length).trim().ifEmpty { "unknown" }
        return tag to sanitizeScanStatusText(detail)
    }
}
