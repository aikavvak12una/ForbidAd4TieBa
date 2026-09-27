package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.EnterForumWebSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object EnterForumWebHook {
    private const val ENTER_FORUM_ALL_PAGE_URL =
        "https://tieba.baidu.com/mo/q/hybrid-main-bawu/forumConcern?nonavigationbar=1&customfullscreen=1&loadingSignal=1"

    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }

    @Synchronized
    internal fun hook(targets: EnterForumWebSymbols) {
        val mod = XposedCompat.module ?: return
        val loadMethod = targets.webLoadMethod
        try {
            installUrlReplaceHook(mod, targets)
            XposedCompat.log("[EnterForumWebHook] hook INSTALLED: ${loadMethod.declaringClass.name}.${loadMethod.name}(String)")
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] FAILED: ${t.message}")
            XposedCompat.log(t)
            return
        }
        // Never rewrite the source unless the controller can also apply the matching load policy.
        targets.sourceGetUrlMethod?.let { hookInitInfoUrlSource(mod, it) }
    }

    internal fun targetUrlForLoad(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase(Locale.ROOT)
        return when {
            isAllPageUrl(lower) -> url
            isEnterForumMainUrl(lower) -> ENTER_FORUM_ALL_PAGE_URL
            else -> null
        }
    }

    private fun hookInitInfoUrlSource(
        mod: io.github.libxposed.api.XposedModule,
        getUrlMethod: Method,
    ) {
        try {
            if (installedMethods.contains(getUrlMethod)) {
                XposedCompat.log("[EnterForumWebHook] source already installed: ${ReflectionUtils.methodSignature(getUrlMethod)}")
                return
            }
            installForumUrlSourceHook(mod, getUrlMethod)
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
        targets: EnterForumWebSymbols,
    ) {
        installedMethods.install(targets.webLoadMethod) {
            val failureLogged = AtomicBoolean()
            RuntimeHooks.builder(mod, targets.webLoadMethod, "EnterForumWebHook", "installUrlReplaceHook:targets.webLoadMethod").intercept { chain ->
                if (!ConfigManager.isEnterForumWebFilterEnabled) return@intercept chain.proceed()

                val originalUrl = chain.args.firstOrNull() as? String
                val targetUrl = targetUrlForLoad(originalUrl) ?: return@intercept chain.proceed()
                try {
                    val webView = targets.webViewField.get(chain.thisObject) ?: error("controller WebView unavailable")
                    // forumConcern is outside the common H5 whitelist. Forced reuse waits for
                    // an insertion acknowledgement that this page cannot send (3 s timeout).
                    // Apply this even when InitInfoData has already supplied the purified URL.
                    targets.setForceCommonMethod.invoke(webView, false)
                } catch (failure: Throwable) {
                    if (failureLogged.compareAndSet(false, true)) {
                        XposedCompat.log("[EnterForumWebHook] load policy FAILED: $failure")
                    }
                    return@intercept chain.proceed()
                }
                if (targetUrl == originalUrl) return@intercept chain.proceed()
                XposedCompat.logD { "[EnterForumWebHook] replace url: $originalUrl -> $targetUrl" }
                chain.proceed(arrayOf<Any?>(targetUrl))
            }
        }
    }

    private fun installForumUrlSourceHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ) {
        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "EnterForumWebHook", "installForumUrlSourceHook:method").intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isEnterForumWebFilterEnabled) {
                    return@intercept result
                }

                val replacement = replacementForForumUrlSource(result as? String) ?: return@intercept result
                XposedCompat.logD { "[EnterForumWebHook] replace source url: $result -> $replacement" }
                replacement
            }
        }
    }

    private fun replacementForForumUrlSource(url: String?): String? {
        if (url.isNullOrBlank()) return ENTER_FORUM_ALL_PAGE_URL
        return targetUrlForLoad(url)?.takeIf { it != url }
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
