package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Modifier

internal object EnterForumWebSymbolScanner {
    private const val INIT_INFO_DATA_CLASS = "com.baidu.tbadk.coreExtra.data.InitInfoData"
    private const val INIT_INFO_GET_URL_METHOD = "getForumEnterUrl"

    fun scanInitInfoData(cl: ClassLoader, logger: ScanLogger?): Pair<String, String>? {
        val cls = safeFindClass(INIT_INFO_DATA_CLASS, cl)
        if (cls == null) {
            log(logger, "enterForumWeb.initInfoData: class not found: $INIT_INFO_DATA_CLASS")
            return null
        }
        val getter = cls.methods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == INIT_INFO_GET_URL_METHOD &&
                method.returnType == String::class.java &&
                method.parameterTypes.isEmpty()
        }
        if (getter == null) {
            log(
                logger,
                "enterForumWeb.initInfoData: method not found: " +
                    "$INIT_INFO_DATA_CLASS.$INIT_INFO_GET_URL_METHOD()",
            )
            return null
        }
        log(logger, "enterForumWeb.initInfoData matched: ${cls.name}.${getter.name}")
        return cls.name to getter.name
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
