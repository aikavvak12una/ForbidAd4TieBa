package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object EnterForumWebHook {
    private const val ENTER_FORUM_ALL_PAGE_URL =
        "https://tieba.baidu.com/mo/q/hybrid-main-bawu/forumConcern?nonavigationbar=1&customfullscreen=1&loadingSignal=1"

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    fun hook(
        classLoader: ClassLoader,
        symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols(),
    ) {
        val mod = XposedCompat.module ?: return
        var attempted = false

        val initInfoDataClassName = symbols?.enterForumInitInfoDataClass
        val initInfoGetUrlMethodName = symbols?.enterForumInitInfoGetUrlMethod
        if (!initInfoDataClassName.isNullOrBlank() && !initInfoGetUrlMethodName.isNullOrBlank()) {
            attempted = true
            hookInitInfoUrlSource(mod, classLoader, initInfoDataClassName, initInfoGetUrlMethodName)
        }

        val controllerClassName = symbols?.enterForumWebControllerClass
        val loadMethodName = symbols?.enterForumWebLoadMethod
        if (controllerClassName.isNullOrBlank() || loadMethodName.isNullOrBlank()) {
            if (!attempted) {
                XposedCompat.log(
                    "[EnterForumWebHook] skipped: missing symbols " +
                        "enterForumInitInfoDataClass/enterForumInitInfoGetUrlMethod and " +
                        "enterForumWebControllerClass/enterForumWebLoadMethod",
                )
            }
            return
        }

        try {
            val controllerClass = XposedCompat.findClassOrNull(controllerClassName, classLoader)
            if (controllerClass == null) {
                XposedCompat.log("[EnterForumWebHook] skipped: class not found $controllerClassName")
                return
            }

            val loadMethod = ReflectionUtils.findMethodInHierarchy(
                controllerClass,
                loadMethodName,
                String::class.java,
            )
            if (loadMethod == null || !isStringVoidInstanceMethod(loadMethod)) {
                XposedCompat.log(
                    "[EnterForumWebHook] skipped: invalid target ${controllerClass.name}.$loadMethodName(String)",
                )
                return
            }
            loadMethod.isAccessible = true
            if (!installUrlReplaceHook(mod, loadMethod)) {
                XposedCompat.log("[EnterForumWebHook] already installed: ${ReflectionUtils.methodSignature(loadMethod)}")
                return
            }
            XposedCompat.log("[EnterForumWebHook] hook INSTALLED: ${controllerClass.name}.${loadMethod.name}(String)")
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookInitInfoUrlSource(
        mod: io.github.libxposed.api.XposedModule,
        classLoader: ClassLoader,
        dataClassName: String,
        getUrlMethodName: String,
    ) {
        try {
            val dataClass = XposedCompat.findClassOrNull(dataClassName, classLoader)
            if (dataClass == null) {
                XposedCompat.log("[EnterForumWebHook] source skipped: class not found $dataClassName")
                return
            }

            val getUrlMethod = ReflectionUtils.findMethodInHierarchy(dataClass, getUrlMethodName) { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            }
            if (getUrlMethod == null) {
                XposedCompat.log("[EnterForumWebHook] source skipped: method not found $dataClassName.$getUrlMethodName()")
                return
            }

            if (!installForumUrlSourceHook(mod, getUrlMethod)) {
                XposedCompat.log("[EnterForumWebHook] source already installed: ${ReflectionUtils.methodSignature(getUrlMethod)}")
                return
            }
            XposedCompat.log("[EnterForumWebHook] source hook INSTALLED: ${dataClass.name}.${getUrlMethod.name}()")
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] source FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installUrlReplaceHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isEnterForumWebFilterEnabled) {
                return@intercept chain.proceed()
            }

            val originalUrl = chain.args.firstOrNull() as? String
            if (!shouldReplaceUrl(originalUrl)) {
                return@intercept chain.proceed()
            }

            XposedCompat.logD { "[EnterForumWebHook] replace url: $originalUrl -> $ENTER_FORUM_ALL_PAGE_URL" }
            chain.proceed(arrayOf<Any?>(ENTER_FORUM_ALL_PAGE_URL))
        }
        return true
    }

    private fun installForumUrlSourceHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (!ConfigManager.isEnterForumWebFilterEnabled) {
                return@intercept result
            }

            val replacement = replacementForForumUrlSource(result as? String) ?: return@intercept result
            XposedCompat.logD { "[EnterForumWebHook] replace source url: $result -> $replacement" }
            replacement
        }
        return true
    }

    private fun isStringVoidInstanceMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val p = method.parameterTypes
        return p.size == 1 && p[0] == String::class.java
    }

    private fun shouldReplaceUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        if (isAllPageUrl(lower)) return false
        return isEnterForumMainUrl(lower)
    }

    private fun replacementForForumUrlSource(url: String?): String? {
        if (url.isNullOrBlank()) return ENTER_FORUM_ALL_PAGE_URL
        return if (shouldReplaceUrl(url)) ENTER_FORUM_ALL_PAGE_URL else null
    }

    private fun isAllPageUrl(lower: String): Boolean {
        return lower.contains("hybrid-main-bawu/forumconcern")
    }

    private fun isEnterForumMainUrl(lower: String): Boolean {
        return lower.contains("hybrid-main-forumtab/mainpage") ||
            lower.contains("hybrid-main-forumtab") ||
            lower.contains("hybrid-main-frs/mainpage/hybrid") ||
            lower.contains("hybrid-main-frs/mainpage")
    }
}
