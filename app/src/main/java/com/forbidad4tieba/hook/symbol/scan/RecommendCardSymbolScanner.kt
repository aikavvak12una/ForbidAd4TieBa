package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Modifier

internal object RecommendCardSymbolScanner {
    private const val RECOMMEND_CARD_VIEW_CLASS = "com.baidu.tieba.feed.component.RecommendCardView"

    fun scanNestedData(cl: ClassLoader, logger: ScanLogger?): RecommendCardNestedDataScanSymbols {
        val viewClass = safeFindClass(RECOMMEND_CARD_VIEW_CLASS, cl) ?: run {
            log(logger, "recommendCard nestedData: class not found: $RECOMMEND_CARD_VIEW_CLASS")
            return RecommendCardNestedDataScanSymbols()
        }
        val candidates = ArrayList<RecommendCardNestedDataCandidate>(4)
        val bindMethods = viewClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                !method.isSynthetic &&
                !method.isBridge &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive &&
                method.parameterTypes[0].name.startsWith("com.baidu.tieba.")
        }
        for (bindMethod in bindMethods) {
            val stateClass = bindMethod.parameterTypes[0]
            val stateFields = collectInstanceFields(stateClass)
            val stateMethods = collectInstanceMethods(stateClass)
            for (method in stateMethods) {
                if (method.isSynthetic || method.isBridge) continue
                if (method.parameterTypes.isNotEmpty()) continue
                if (method.returnType.isPrimitive || method.returnType == Void.TYPE || method.returnType == String::class.java) {
                    continue
                }
                val nestedDataClass = method.returnType
                if (stateFields.none { it.type == nestedDataClass }) continue
                val listFields = collectInstanceFields(nestedDataClass)
                    .filter { isListType(it.type) }
                if (listFields.size != 1) continue
                candidates.add(
                    RecommendCardNestedDataCandidate(
                        bindMethodName = bindMethod.name,
                        stateClassName = stateClass.name,
                        nestedDataMethodName = method.name,
                        nestedDataClassName = nestedDataClass.name,
                        listFieldName = listFields.single().name,
                    ),
                )
            }
        }

        val distinct = candidates.distinctBy { "${it.stateClassName}.${it.nestedDataMethodName}:${it.listFieldName}" }
        return when (distinct.size) {
            1 -> {
                val match = distinct.single()
                log(
                    logger,
                    "recommendCard nestedData matched: " +
                        "$RECOMMEND_CARD_VIEW_CLASS.${match.bindMethodName} -> " +
                        "${match.stateClassName}.${match.nestedDataMethodName}()[${match.nestedDataClassName}.${match.listFieldName}]",
                )
                RecommendCardNestedDataScanSymbols(
                    nestedDataMethod = match.nestedDataMethodName,
                    nestedDataListField = match.listFieldName,
                )
            }
            0 -> {
                log(logger, "recommendCard nestedData not found")
                RecommendCardNestedDataScanSymbols()
            }
            else -> {
                log(
                    logger,
                    "recommendCard nestedData not unique: " +
                        distinct.joinToString(";") {
                            "${it.stateClassName}.${it.nestedDataMethodName}[${it.nestedDataClassName}.${it.listFieldName}]"
                        },
                )
                RecommendCardNestedDataScanSymbols()
            }
        }
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        HookSymbolResolver.collectInstanceMethods(clazz)

    private fun isListType(type: Class<*>): Boolean =
        HookSymbolResolver.isListType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
