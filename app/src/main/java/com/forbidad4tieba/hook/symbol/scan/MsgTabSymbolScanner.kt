package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints

internal object MsgTabSymbolScanner {
    private const val MSG_TAB_VIEW_MODEL_CLASS = StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS
    private const val MSG_TAB_CONTAINER_VIEW_CLASS =
        "com.baidu.tieba.immessagecenter.msgtab.ui.view.MsgCenterContainerView"

    fun scan(cl: ClassLoader, logger: ScanLogger?): MsgTabScanSymbols {
    val vmClass = safeFindClass(MSG_TAB_VIEW_MODEL_CLASS, cl)
    val containerClass = safeFindClass(MSG_TAB_CONTAINER_VIEW_CLASS, cl)

    val locateMethod = vmClass?.let { cls ->
        val candidates = collectInstanceMethods(cls).filter { method ->
            method.returnType == Long::class.javaPrimitiveType &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                method.parameterTypes[1] == String::class.java
        }
        pickMethodName(candidates, preferredName = "m")
    }

    val containerSelectMethod = containerClass?.let { cls ->
        val candidates = collectInstanceMethods(cls).filter { method ->
            method.parameterTypes.isEmpty() && method.returnType == Long::class.javaPrimitiveType
        }
        pickMethodName(candidates, preferredName = "L")
    }

    val containerExtDataField = containerClass?.let { cls ->
        val candidates = collectInstanceFields(cls).filter { it.type == String::class.java }
        candidates.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == "C") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )?.name
    }

    val out = MsgTabScanSymbols(
        locateToTabMethod = locateMethod,
        containerSelectMethod = containerSelectMethod,
        containerExtDataField = containerExtDataField,
    )
    log(
        logger,
        "msgTabScan: locate=${out.locateToTabMethod}, container=${out.containerSelectMethod}, ext=${out.containerExtDataField}",
    )
    return out
}


    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        HookSymbolResolver.collectInstanceMethods(clazz)

    private fun pickMethodName(methods: List<java.lang.reflect.Method>, preferredName: String): String? =
        HookSymbolResolver.pickMethodName(methods, preferredName)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
