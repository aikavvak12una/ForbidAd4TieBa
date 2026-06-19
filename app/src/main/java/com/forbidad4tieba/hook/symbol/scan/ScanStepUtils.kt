package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

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
            val context = HookSymbolScanSession.get()
            if (context?.shouldLogRecoverableProbeIssue(tag) == true) {
                HookSymbolScanDiagnostics.log(
                    logger,
                    "$tag recoverable probe skipped: " +
                        HookSymbolScanDiagnostics.sanitizeScanStatusText(detail),
                )
            }
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

internal fun scanDeclaredMethods(tag: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
    scanSubStep("$tag.Methods", logger, null) {
        clazz.declaredMethods.toList()
    }

internal fun scanDeclaredFields(tag: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
    scanSubStep("$tag.Fields", logger, null) {
        clazz.declaredFields.toList()
    }

internal fun scanDeclaredConstructors(
    tag: String,
    clazz: Class<*>,
    logger: ScanLogger?,
): List<Constructor<*>>? =
    scanSubStep("$tag.Constructors", logger, null) {
        clazz.declaredConstructors.toList()
    }

internal fun scanInstanceMethods(tag: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
    scanSubStep("$tag.InstanceMethods", logger, null) {
        ScanReflection.collectInstanceMethods(clazz)
    }

internal fun scanInstanceFields(tag: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
    scanSubStep("$tag.InstanceFields", logger, null) {
        ScanReflection.collectInstanceFields(clazz)
    }

internal fun <T> selectUniqueScanCandidate(
    tag: String,
    candidates: Iterable<T>,
    logger: ScanLogger?,
    describe: (T) -> String,
): T? {
    val matches = candidates.toList()
    if (matches.size == 1) return matches.single()
    HookSymbolScanDiagnostics.log(
        logger,
        "$tag candidates=" + matches.joinToString(",") { describe(it) }.ifBlank { "-" },
    )
    return null
}
