package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.net.Uri
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PlainUrlBrowserHelperScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlainUrlBrowserHelperSymbolScanner {
    private const val BROWSER_HELPER_CLASS = "com.baidu.tbadk.browser.BrowserHelper"
    private const val START_WEB_ACTIVITY_METHOD = "startWebActivity"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PlainUrlBrowserHelperScanSymbols {
        val browserHelperClass = ScanReflection.safeFindClass(BROWSER_HELPER_CLASS, cl) ?: run {
            log(logger, "plainUrlBrowserHelper: class not found: $BROWSER_HELPER_CLASS")
            return PlainUrlBrowserHelperScanSymbols()
        }
        val methods = scanSubStep("PlainUrlBrowserHelper.Methods", logger, emptyList<Method>()) {
            browserHelperClass.declaredMethods.toList()
        }
        val startWebActivityMethods = methods.filter { method ->
            isStartWebActivityMethod(method, START_WEB_ACTIVITY_METHOD)
        }
        if (startWebActivityMethods.isEmpty()) {
            log(logger, "plainUrlBrowserHelper: startWebActivity(Context, String/Uri...) not found")
            return PlainUrlBrowserHelperScanSymbols()
        }
        log(
            logger,
            "plainUrlBrowserHelper matched: ${browserHelperClass.name}.$START_WEB_ACTIVITY_METHOD " +
                "overloads=${startWebActivityMethods.size}",
        )
        return PlainUrlBrowserHelperScanSymbols(
            browserHelperClass = browserHelperClass.name,
            startWebActivityMethod = START_WEB_ACTIVITY_METHOD,
        )
    }

    fun isStartWebActivityMethod(method: Method, methodName: String): Boolean {
        if (method.name != methodName) return false
        if (!Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val parameterTypes = method.parameterTypes
        return parameterTypes.any { Context::class.java.isAssignableFrom(it) } &&
            parameterTypes.any { it == String::class.java || it == Uri::class.java }
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
