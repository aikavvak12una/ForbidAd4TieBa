package com.forbidad4tieba.hook.symbol.scan

import android.util.SparseArray
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PbEarlyAdInsertScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal object PbEarlyAdInsertSymbolScanner {
    private const val PB_FRAGMENT_CLASS = "com.baidu.tieba.pb.pb.main.PbFragment"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PbEarlyAdInsertScanSymbols {
        val matches = ScanDexQueries.classesUsingStrings(logger, "mHistoryFunAdList").mapNotNull { owner ->
            scanSubStep("PbEarlyAdInsert.$owner", logger, null) { matchOwner(owner, cl, logger) }
        }
        return uniqueSemanticCandidate("PbEarlyAdInsert.Owner", matches, logger)
            ?: PbEarlyAdInsertScanSymbols(null, emptyList())
    }

    private fun matchOwner(owner: String, cl: ClassLoader, logger: ScanLogger?): PbEarlyAdInsertScanSymbols? {
        val methods = ScanDexQueries.methods(owner, logger).filter { Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) }
        val history = uniqueSemanticCandidate("PbEarlyAdInsert.History", methods.filter {
            it.returnTypeName == "void" && it.paramCount == 5 &&
                it.paramTypeNames.take(4) == listOf("java.util.ArrayList", "boolean", "java.util.ArrayList", PB_FRAGMENT_CLASS) &&
                "mHistoryFunAdList" in it.usingStrings
        }, logger) ?: return null
        val dataType = history.paramTypeNames.last()
        val sparse = uniqueSemanticCandidate("PbEarlyAdInsert.Materials", methods.filter {
            it.returnTypeName == SparseArray::class.java.name &&
                it.paramTypeNames == listOf("java.util.ArrayList", dataType, PB_FRAGMENT_CLASS) &&
                it.usingStrings.containsAll(listOf("PB_COMMENT", "getAdFunPbCommentFeedSid()"))
        }, logger) ?: return null
        val direct = methods.filter {
            it.returnTypeName == "void" && it.paramTypeNames == listOf(dataType, PB_FRAGMENT_CLASS, "boolean") &&
                it.usingStrings.containsAll(listOf("mData.post_list", "mData.apps", "pb_show_app"))
        }
        val placeholder = methods.filter {
            it.returnTypeName == "void" && it.paramTypeNames == listOf("int", "int", "int", "java.util.ArrayList", dataType, "java.util.ArrayList") &&
                "funAdPlaceholder" in it.usingStrings
        }
        // The material/history pair is required. The other insertion routes remain independent optional points.
        val optional = direct.takeIf { it.size in 1..2 }.orEmpty() +
            uniqueSemanticCandidate("PbEarlyAdInsert.Placeholder", placeholder, logger)?.let(::listOf).orEmpty()
        if (direct.size > 2) {
            HookSymbolScanDiagnostics.log(
                logger,
                "PbEarlyAdInsert.Direct: expected=0..2 actual=${direct.size} " +
                    "candidates=${direct.take(8).joinToString { it.descriptor }}",
            )
        }
        val selected = listOf(sparse, history) + optional
        val specs = selected.map { dex ->
            val method = dex.getMethodInstance(cl)
            if (!Modifier.isStatic(method.modifiers) || method.declaringClass.name != owner) return null
            encode(method.name, method.returnType.name, method.parameterTypes.map { it.name })
        }.distinct().sorted()
        return PbEarlyAdInsertScanSymbols(owner, specs)
    }

    private fun encode(name: String, returnType: String, params: List<String>): String =
        "$name|$returnType|${params.joinToString(",")}"
}
