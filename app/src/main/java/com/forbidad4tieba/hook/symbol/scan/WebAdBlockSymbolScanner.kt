package com.forbidad4tieba.hook.symbol.scan

import android.webkit.WebView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.HomeSideBarWebBlockScanSymbols
import com.forbidad4tieba.hook.symbol.model.MineTabWebBlockScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object WebAdBlockSymbolScanner {
    fun scanMineTab(cl: ClassLoader, logger: ScanLogger?): MineTabWebBlockScanSymbols {
        val tbWebViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_WEB_VIEW_CLASS, cl) ?: run {
            log(logger, "mineTabWebBlock: class not found: ${StableTiebaHookPoints.TB_WEB_VIEW_CLASS}")
            return MineTabWebBlockScanSymbols()
        }
        val loadUrlMethod = findMethodInHierarchy(
            "MineTabWebBlockHook.LoadUrl",
            tbWebViewClass,
            "loadUrl",
            logger,
            ::isStringLoadUrlMethod,
        )
        val getUrlMethod = findMethodInHierarchy(
            "MineTabWebBlockHook.GetUrl",
            tbWebViewClass,
            "getUrl",
            logger,
            ::isGetUrlMethod,
        )
        val getInnerWebViewMethod = findMethodInHierarchy(
            "MineTabWebBlockHook.GetInnerWebView",
            tbWebViewClass,
            "getInnerWebView",
            logger,
            ::isGetInnerWebViewMethod,
        )
        log(
            logger,
            "mineTabWebBlock matched: ${tbWebViewClass.name}.{" +
                listOfNotNull(loadUrlMethod?.name, getUrlMethod?.name, getInnerWebViewMethod?.name)
                    .joinToString(",").ifBlank { "-" } +
                "}",
        )
        return MineTabWebBlockScanSymbols(
            webViewClass = tbWebViewClass.name,
            loadUrlMethod = loadUrlMethod?.name,
            getUrlMethod = getUrlMethod?.name,
            getInnerWebViewMethod = getInnerWebViewMethod?.name,
        )
    }

    fun scanHomeSideBar(cl: ClassLoader, logger: ScanLogger?): HomeSideBarWebBlockScanSymbols {
        val sideBarWebViewClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.HOME_SIDE_BAR_WEB_VIEW_CLASS,
            cl,
        ) ?: run {
            log(
                logger,
                "homeSideBarWebBlock: class not found: " +
                    StableTiebaHookPoints.HOME_SIDE_BAR_WEB_VIEW_CLASS,
            )
            return HomeSideBarWebBlockScanSymbols()
        }
        val tbWebViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_WEB_VIEW_CLASS, cl) ?: run {
            log(logger, "homeSideBarWebBlock: class not found: ${StableTiebaHookPoints.TB_WEB_VIEW_CLASS}")
            return HomeSideBarWebBlockScanSymbols(sideBarWebViewClass = sideBarWebViewClass.name)
        }
        val getWebViewMethod = findMethodInHierarchy(
            "HomeSideBarWebBlockHook.GetWebView",
            sideBarWebViewClass,
            "getWebView",
            logger,
        ) { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                tbWebViewClass.isAssignableFrom(method.returnType)
        }
        val getUrlMethod = findMethodInHierarchy(
            "HomeSideBarWebBlockHook.GetUrl",
            tbWebViewClass,
            "getUrl",
            logger,
            ::isGetUrlMethod,
        )
        val getInnerWebViewMethod = findMethodInHierarchy(
            "HomeSideBarWebBlockHook.GetInnerWebView",
            tbWebViewClass,
            "getInnerWebView",
            logger,
            ::isGetInnerWebViewMethod,
        )
        val loadUrlMethods = scanDeclaredMethods("HomeSideBarWebBlockHook.LoadUrl", sideBarWebViewClass, logger)
            .orEmpty()
            .asSequence()
            .filter(::isStringLoadUrlMethod)
            .map { it.name }
            .distinct()
            .sorted()
            .toList()
        if (loadUrlMethods.isEmpty()) {
            log(logger, "homeSideBarWebBlock: load url methods not found")
        } else {
            log(logger, "homeSideBarWebBlock load methods: ${loadUrlMethods.joinToString(";")}")
        }
        return HomeSideBarWebBlockScanSymbols(
            sideBarWebViewClass = sideBarWebViewClass.name,
            tbWebViewClass = tbWebViewClass.name,
            getWebViewMethod = getWebViewMethod?.name,
            getUrlMethod = getUrlMethod?.name,
            getInnerWebViewMethod = getInnerWebViewMethod?.name,
            loadUrlMethods = loadUrlMethods,
        )
    }

    private fun findMethodInHierarchy(
        tag: String,
        clazz: Class<*>,
        methodName: String,
        logger: ScanLogger?,
        predicate: (Method) -> Boolean,
    ): Method? {
        val methods = scanInstanceMethods(tag, clazz, logger).orEmpty()
        val matches = methods.filter { it.name == methodName && predicate(it) }
        return selectUniqueScanCandidate(tag, matches, logger, ::describeMethodShape)
    }

    private fun isStringLoadUrlMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == String::class.java
    }

    private fun isGetUrlMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == String::class.java &&
            method.parameterTypes.isEmpty()
    }

    private fun isGetInnerWebViewMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            WebView::class.java.isAssignableFrom(method.returnType) &&
            method.parameterTypes.isEmpty()
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
