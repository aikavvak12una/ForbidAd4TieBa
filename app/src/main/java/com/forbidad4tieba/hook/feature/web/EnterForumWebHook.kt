package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.symbol.model.EnterForumWebSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object EnterForumWebHook {
    private const val ENTER_FORUM_ALL_PAGE_URL =
        "https://tieba.baidu.com/mo/q/hybrid-main-bawu/forumConcern?nonavigationbar=1&customfullscreen=1&loadingSignal=1"

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    internal fun hook(targets: EnterForumWebSymbols) {
        val mod = XposedCompat.module ?: return
        var attempted = false

        val sourceGetUrlMethod = targets.sourceGetUrlMethod
        if (sourceGetUrlMethod != null) {
            attempted = true
            hookInitInfoUrlSource(mod, sourceGetUrlMethod)
        }

        val loadMethod = targets.webLoadMethod
        if (loadMethod == null) {
            if (!attempted) {
                XposedCompat.log("[EnterForumWebHook] skipped: no resolved install targets")
            }
            return
        }

        try {
            if (!installUrlReplaceHook(mod, loadMethod)) {
                XposedCompat.log("[EnterForumWebHook] already installed: ${ReflectionUtils.methodSignature(loadMethod)}")
                return
            }
            XposedCompat.log("[EnterForumWebHook] hook INSTALLED: ${loadMethod.declaringClass.name}.${loadMethod.name}(String)")
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookInitInfoUrlSource(
        mod: io.github.libxposed.api.XposedModule,
        getUrlMethod: Method,
    ) {
        try {
            if (!installForumUrlSourceHook(mod, getUrlMethod)) {
                XposedCompat.log("[EnterForumWebHook] source already installed: ${ReflectionUtils.methodSignature(getUrlMethod)}")
                return
            }
            XposedCompat.log(
                "[EnterForumWebHook] source hook INSTALLED: " +
                    "${getUrlMethod.declaringClass.name}.${getUrlMethod.name}()",
            )
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
