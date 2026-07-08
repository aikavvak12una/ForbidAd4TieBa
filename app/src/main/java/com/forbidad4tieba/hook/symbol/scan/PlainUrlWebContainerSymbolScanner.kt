package com.forbidad4tieba.hook.symbol.scan

import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PlainUrlWebContainerScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlainUrlWebContainerSymbolScanner {
    private const val WEB_CONTAINER_ACTIVITY_CLASS = "com.baidu.tbadk.browser.BaseWebViewActivity"
    private const val TB_WEB_CONTAINER_ACTIVITY_CLASS = "com.baidu.tbadk.browser.TBWebContainerActivity"
    private const val INIT_DATA_METHOD = "initData"
    private const val SHOULD_OVERRIDE_URL_LOADING_METHOD = "shouldOverrideUrlLoading"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PlainUrlWebContainerScanSymbols {
        val activityClass = ScanReflection.safeFindClass(WEB_CONTAINER_ACTIVITY_CLASS, cl) ?: run {
            log(logger, "plainUrlWebContainer: class not found: $WEB_CONTAINER_ACTIVITY_CLASS")
            return PlainUrlWebContainerScanSymbols()
        }
        if (!Activity::class.java.isAssignableFrom(activityClass)) {
            log(logger, "plainUrlWebContainer: class is not Activity: ${activityClass.name}")
            return PlainUrlWebContainerScanSymbols()
        }
        val initDataMethod = activityClass.declaredMethods.firstOrNull { method ->
            isInitDataMethod(method, INIT_DATA_METHOD)
        } ?: run {
            log(logger, "plainUrlWebContainer: initData() not found")
            null
        }
        val concreteActivityClass = ScanReflection.safeFindClass(TB_WEB_CONTAINER_ACTIVITY_CLASS, cl)
        val shouldOverrideUrlLoadingMethod = concreteActivityClass?.let { findShouldOverrideUrlLoadingMethod(it, logger) }
        if (initDataMethod == null && shouldOverrideUrlLoadingMethod == null) {
            return PlainUrlWebContainerScanSymbols()
        }
        initDataMethod?.let {
            log(logger, "plainUrlWebContainer init matched: ${activityClass.name}.${it.name}()")
        }
        shouldOverrideUrlLoadingMethod?.let {
            log(logger, "plainUrlWebContainer navigation matched: ${it.declaringClass.name}.${it.name}(WebView,String)")
        }
        return PlainUrlWebContainerScanSymbols(
            webContainerActivityClass = activityClass.name,
            initDataMethod = initDataMethod?.name,
            webViewClientClass = shouldOverrideUrlLoadingMethod?.declaringClass?.name,
            shouldOverrideUrlLoadingMethod = shouldOverrideUrlLoadingMethod?.name,
        )
    }

    fun isInitDataMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.isEmpty()
    }

    fun isShouldOverrideUrlLoadingMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            method.parameterTypes.contentEquals(arrayOf(WebView::class.java, String::class.java))
    }

    private fun findShouldOverrideUrlLoadingMethod(activityClass: Class<*>, logger: ScanLogger?): Method? {
        if (!Activity::class.java.isAssignableFrom(activityClass)) {
            log(logger, "plainUrlWebContainer: concrete class is not Activity: ${activityClass.name}")
            return null
        }
        val candidates = activityClass.declaredClasses.mapNotNull { candidateClass ->
            if (!WebViewClient::class.java.isAssignableFrom(candidateClass)) return@mapNotNull null
            if (!hasActivityConstructor(candidateClass, activityClass)) return@mapNotNull null
            val method = candidateClass.declaredMethods.singleOrNull { method ->
                isShouldOverrideUrlLoadingMethod(method, SHOULD_OVERRIDE_URL_LOADING_METHOD)
            } ?: return@mapNotNull null
            method
        }
        return when (candidates.size) {
            1 -> candidates.single()
            0 -> {
                log(logger, "plainUrlWebContainer: shouldOverrideUrlLoading(WebView,String) not found")
                null
            }
            else -> {
                val names = candidates.joinToString { "${it.declaringClass.name}.${it.name}" }
                log(logger, "plainUrlWebContainer: non-unique shouldOverrideUrlLoading candidates: $names")
                null
            }
        }
    }

    private fun hasActivityConstructor(candidateClass: Class<*>, activityClass: Class<*>): Boolean {
        return candidateClass.declaredConstructors.any { constructor ->
            constructor.parameterTypes.contentEquals(arrayOf(activityClass))
        }
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
