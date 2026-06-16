package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Modifier

internal object EnterForumWebSymbolScanner {
    private const val INIT_INFO_DATA_CLASS = "com.baidu.tbadk.coreExtra.data.InitInfoData"
    private const val INIT_INFO_GET_URL_METHOD = "getForumEnterUrl"

    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): EnterForumWebScanSymbols {
        val webController = scanSubStep(
            "EnterForumWebHook.WebController",
            logger,
            EnterForumWebScanSymbols(),
        ) {
            scanWebController(candidates, cl, logger)
        }
        val initInfo = scanSubStep(
            "EnterForumWebHook.InitInfoData",
            logger,
            EnterForumWebScanSymbols(),
        ) {
            scanInitInfoData(cl, logger)
        }
        return EnterForumWebScanSymbols(
            controllerClass = webController.controllerClass,
            webLoadMethod = webController.webLoadMethod,
            initInfoDataClass = initInfo.initInfoDataClass,
            initInfoGetUrlMethod = initInfo.initInfoGetUrlMethod,
        )
    }

    private fun scanWebController(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): EnterForumWebScanSymbols {
        val tbWebViewClass = safeFindClass(StableTiebaHookPoints.TB_WEB_VIEW_CLASS, cl)
        if (tbWebViewClass == null) {
            log(logger, "enterForumWeb: TbWebView class not found")
            return EnterForumWebScanSymbols()
        }
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(EnterForumWebControllerRule(tbWebViewClass)),
            logger,
            "enterForumWeb",
        ) ?: return EnterForumWebScanSymbols()
        return EnterForumWebScanSymbols(
            controllerClass = match.className,
            webLoadMethod = match.methodName,
        )
    }

    private fun scanInitInfoData(cl: ClassLoader, logger: ScanLogger?): EnterForumWebScanSymbols {
        val cls = safeFindClass(INIT_INFO_DATA_CLASS, cl)
        if (cls == null) {
            log(logger, "enterForumWeb.initInfoData: class not found: $INIT_INFO_DATA_CLASS")
            return EnterForumWebScanSymbols()
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
            return EnterForumWebScanSymbols()
        }
        log(logger, "enterForumWeb.initInfoData matched: ${cls.name}.${getter.name}")
        return EnterForumWebScanSymbols(
            initInfoDataClass = cls.name,
            initInfoGetUrlMethod = getter.name,
        )
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
