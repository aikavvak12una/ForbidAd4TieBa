package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal inline fun <T> scanSubStep(
    tag: String,
    logger: ScanLogger?,
    fallback: T,
    block: () -> T,
): T {
    return try {
        block()
    } catch (t: Throwable) {
        val detail = HookSymbolScanDiagnostics.formatScanException(t)
        if (isRecoverableCandidateReflectionFailure(tag, t)) {
            return fallback
        }
        HookSymbolScanSession.get()?.scanErrors?.let { errors ->
            HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$tag scan exception: $detail")
        fallback
    }
}

internal fun isRecoverableCandidateReflectionFailure(tag: String, throwable: Throwable): Boolean {
    if (!isCandidateReflectionProbe(tag)) return false
    return throwable is NoClassDefFoundError || throwable is TypeNotPresentException
}

private fun isCandidateReflectionProbe(tag: String): Boolean {
    val normalized = tag.trim().lowercase()
    return normalized.endsWith(".classshape") ||
        normalized.endsWith(".methods") ||
        normalized.endsWith(".fields") ||
        normalized.endsWith(".constructors") ||
        normalized.endsWith(".classes") ||
        normalized.endsWith(".nestedclasses") ||
        normalized.endsWith(".instancefields") ||
        normalized.endsWith(".instancemethods") ||
        normalized.endsWith(".ownershape")
}

internal fun unpackScanParts(raw: String, expectedParts: Int): List<String?> {
    val parts = ArrayList<String?>(expectedParts)
    var start = 0
    while (parts.size < expectedParts - 1) {
        val idx = raw.indexOf(',', start)
        if (idx < 0) break
        val token = raw.substring(start, idx).trim()
        parts.add(token.ifEmpty { null })
        start = idx + 1
    }
    val tail = raw.substring(start).trim()
    parts.add(tail.ifEmpty { null })
    while (parts.size < expectedParts) {
        parts.add(null)
    }
    return parts
}
