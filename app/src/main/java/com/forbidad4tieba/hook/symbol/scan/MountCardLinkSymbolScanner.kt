package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Modifier

internal object MountCardLinkSymbolScanner {
    private const val MOUNT_CARD_LINK_LAYOUT_CLASS =
        "com.baidu.tbadk.core.view.commonMountCard.TbMountCardLinkLayout"
    private const val MOUNT_CARD_LINK_INFO_DATA_CLASS = "com.baidu.tbadk.data.CardLinkInfoData"
    private const val MOUNT_CARD_LINK_LAYOUT_ON_CLICK_METHOD = "onClick"
    private const val MOUNT_CARD_LINK_INFO_GET_URL_METHOD = "getUrl"

    fun scan(cl: ClassLoader, logger: ScanLogger?): MountCardLinkLayoutScanSymbols {
        val layoutClass = safeFindClass(MOUNT_CARD_LINK_LAYOUT_CLASS, cl) ?: run {
            log(logger, "mountCardLinkLayout: class not found: $MOUNT_CARD_LINK_LAYOUT_CLASS")
            return MountCardLinkLayoutScanSymbols()
        }
        val dataClass = safeFindClass(MOUNT_CARD_LINK_INFO_DATA_CLASS, cl) ?: run {
            log(logger, "mountCardLinkLayout: data class not found: $MOUNT_CARD_LINK_INFO_DATA_CLASS")
            return MountCardLinkLayoutScanSymbols()
        }
        val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
            isOnClickMethod(method, MOUNT_CARD_LINK_LAYOUT_ON_CLICK_METHOD)
        } ?: run {
            log(logger, "mountCardLinkLayout: onClick(View) not found in ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        val dataField = resolveDataField(layoutClass, dataClass) ?: run {
            log(logger, "mountCardLinkLayout: data field not found in ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
            method.name == MOUNT_CARD_LINK_INFO_GET_URL_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterTypes.isEmpty()
        } ?: run {
            log(logger, "mountCardLinkLayout: getUrl() not found in ${dataClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        if (!isStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)) {
            log(logger, "mountCardLinkLayout: structure mismatch ${layoutClass.name}")
            return MountCardLinkLayoutScanSymbols()
        }
        log(
            logger,
            "mountCardLinkLayout matched: ${layoutClass.name}.${onClickMethod.name} " +
                "data=${dataField.name} url=${dataClass.name}.${getUrlMethod.name}",
        )
        return MountCardLinkLayoutScanSymbols(
            layoutClass = layoutClass.name,
            onClickMethod = onClickMethod.name,
            dataField = dataField.name,
            dataClass = dataClass.name,
            getUrlMethod = getUrlMethod.name,
        )
    }

    private fun isOnClickMethod(method: java.lang.reflect.Method, methodName: String): Boolean =
        HookSymbolResolver.isMountCardLinkLayoutOnClickMethod(method, methodName)

    private fun resolveDataField(layoutClass: Class<*>, dataClass: Class<*>): java.lang.reflect.Field? =
        HookSymbolResolver.resolveMountCardLinkLayoutDataField(layoutClass, dataClass)

    private fun isStructureValid(
        layoutClass: Class<*>,
        onClickMethod: java.lang.reflect.Method,
        dataClass: Class<*>,
        dataField: java.lang.reflect.Field,
        getUrlMethod: java.lang.reflect.Method,
    ): Boolean = HookSymbolResolver.isMountCardLinkLayoutStructureValid(
        layoutClass,
        onClickMethod,
        dataClass,
        dataField,
        getUrlMethod,
    )

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
