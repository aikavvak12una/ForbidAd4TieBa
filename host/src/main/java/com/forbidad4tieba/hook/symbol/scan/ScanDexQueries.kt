package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Queries borrow the current scan's bridge. No bridge or DexKit result escapes the scan. */
internal object ScanDexQueries {
    fun sourcePaths(context: Context): List<String> = buildList {
        context.applicationInfo?.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
        context.applicationInfo?.splitSourceDirs?.filter(String::isNotBlank)?.let(::addAll)
    }.distinct()

    fun <T> query(tag: String, logger: ScanLogger?, block: (DexKitBridge) -> T): T? {
        val session = HookSymbolScanSession.get()
        if (session == null) {
            HookSymbolScanDiagnostics.log(logger, "$tag: no active symbol scan session")
            return null
        }
        return scanSubStep(tag, logger, null) {
            session.dexKitBridge(session.sourcePaths, logger)?.bridge?.let(block)
        }
    }

    fun method(method: Method, logger: ScanLogger?): MethodData? =
        query("DexEvidence.${method.declaringClass.name}.${method.name}", logger) { it.getMethodData(method) }

    fun methods(className: String, logger: ScanLogger?): List<MethodData> =
        query("DexEvidence.$className", logger) { it.getClassData(className)?.methods?.toList() }.orEmpty()

    fun classesUsingStrings(logger: ScanLogger?, vararg strings: String): List<String> =
        query("DexCandidates.Strings", logger) { bridge ->
            bridge.findMethod(
                FindMethod.create().searchPackages("com.baidu.tieba", "com.baidu.tbadk")
                    .matcher(MethodMatcher.create().usingStrings(strings.toList(), StringMatchType.Equals)),
            ).map { it.declaredClassName }.distinct()
        }.orEmpty()

    fun classesWithFieldType(type: String, logger: ScanLogger?): List<String> =
        query("DexCandidates.FieldType", logger) { bridge ->
            bridge.findField(
                FindField.create().searchPackages("com.baidu.tieba", "com.baidu.tbadk", "com.baidu.adp.widget.ListView")
                    .matcher(FieldMatcher.create().type(type)),
            ).map { it.declaredClassName }.distinct()
        }.orEmpty()
}

internal fun MethodData.calls(owner: String, name: String): Boolean =
    invokes.any { it.declaredClassName == owner && it.methodName == name }

internal fun MethodData.instanceFields(owner: String, type: String, usage: FieldUsingType): List<FieldData> =
    usingFields.filter { it.usingType == usage }
        .map { it.field }
        .filter { it.declaredClassName == owner && it.typeName == type && !Modifier.isStatic(it.modifiers) }
        .distinctBy { it.descriptor }

internal fun <T> uniqueSemanticCandidate(tag: String, candidates: List<T>, logger: ScanLogger?): T? {
    if (candidates.size == 1) return candidates.single()
    HookSymbolScanDiagnostics.log(
        logger,
        "$tag: expected=1 actual=${candidates.size} candidates=${candidates.take(8).joinToString().ifBlank { "-" }}",
    )
    return null
}
