package com.forbidad4tieba.hook.feature.web

import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object HomeSideBarWebBlockHook {
    private const val TAG = "[HomeSideBarWebBlockHook]"
    private const val SIDE_BAR_PATH = "/mo/q/hybrid-main-forumtab/mainSidebar"
    private const val SCHEDULE_DEDUPE_WINDOW_MS = 1200L
    private val INJECT_DELAYS_MS = longArrayOf(0L, 80L, 260L, 900L, 1800L)

    private const val HIDE_SIDE_BAR_BLOCKS_JS = """
        (function () {
          var styleId = 'tbhook_home_sidebar_hide_blocks_style_v1';
          var rootSelector = '.main-sidebar';
          var hideSelector =
            rootSelector + ' .vip-box,' +
            rootSelector + ' .main-container,' +
            rootSelector + ' .exchange-task-wrapper,' +
            rootSelector + ' .normal-box.no-foot-btn-mb';

          function ensureStyle() {
            if (document.getElementById(styleId)) return true;
            var host = document.head || document.documentElement;
            if (!host) return false;
            var style = document.createElement('style');
            style.id = styleId;
            style.textContent = hideSelector +
              '{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;max-height:0!important;margin:0!important;padding:0!important;border:0!important;overflow:hidden!important;pointer-events:none!important;}';
            host.appendChild(style);
            return true;
          }

          function run() {
            ensureStyle();
            return !!document.querySelector(rootSelector);
          }

          run();
          if (!window.__tbhookHomeSideBarHideObserverV1) {
            var observer = new MutationObserver(function () { run(); });
            observer.observe(document.body || document.documentElement || document, {
              childList: true,
              subtree: true
            });
            window.__tbhookHomeSideBarHideObserverV1 = observer;
            setTimeout(function () {
              try { observer.disconnect(); } catch (e) {}
              try { delete window.__tbhookHomeSideBarHideObserverV1; } catch (e) {}
            }, 6000);
          }
          return true;
        })();
    """

    private data class RuntimeTargets(
        val getWebViewMethod: Method,
        val getUrlMethod: Method,
        val getInnerWebViewMethod: Method,
    )

    private data class ScheduleStamp(
        val url: String,
        val uptimeMs: Long,
    )

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val scheduledStamp = Collections.synchronizedMap(WeakHashMap<Any, ScheduleStamp>())
    private val lastLoggedUrl = Collections.synchronizedMap(WeakHashMap<Any, String>())

    @Volatile
    private var runtimeTargets: RuntimeTargets? = null

    fun hook(classLoader: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        try {
            val sideBarClassName = symbols.homeSideBarWebViewClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("$TAG skipped: missing cached SideBarWebView class")
                return
            }
            val tbWebViewClassName = symbols.homeSideBarTbWebViewClass?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("$TAG skipped: missing cached TbWebView class")
                return
            }
            val getWebViewMethodName = symbols.homeSideBarWebGetWebViewMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("$TAG skipped: missing cached getWebView method")
                return
            }
            val getUrlMethodName = symbols.homeSideBarWebGetUrlMethod?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("$TAG skipped: missing cached getUrl method")
                return
            }
            val getInnerWebViewMethodName =
                symbols.homeSideBarWebGetInnerWebViewMethod?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.logW("$TAG skipped: missing cached inner WebView method")
                    return
                }
            val loadUrlMethodNames = symbols.homeSideBarWebLoadUrlMethods.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
            if (loadUrlMethodNames.isEmpty()) {
                XposedCompat.logW("$TAG skipped: missing cached load methods")
                return
            }

            val sideBarWebViewClass = XposedCompat.findClassOrNull(sideBarClassName, classLoader)
            if (sideBarWebViewClass == null) {
                XposedCompat.logW("$TAG skipped: class not found: $sideBarClassName")
                return
            }

            val tbWebViewClass = XposedCompat.findClassOrNull(tbWebViewClassName, classLoader)
            if (tbWebViewClass == null) {
                XposedCompat.logW("$TAG skipped: class not found: $tbWebViewClassName")
                return
            }

            val getWebViewMethod = ReflectionUtils.findMethodInHierarchy(
                sideBarWebViewClass,
                getWebViewMethodName,
            )?.takeIf { method ->
                tbWebViewClass.isAssignableFrom(method.returnType)
            }
            val getUrlMethod = ReflectionUtils.findMethodInHierarchy(
                tbWebViewClass,
                getUrlMethodName,
            )?.takeIf { method ->
                method.returnType == String::class.java
            }
            val getInnerWebViewMethod = ReflectionUtils.findMethodInHierarchy(
                tbWebViewClass,
                getInnerWebViewMethodName,
            )?.takeIf { method ->
                WebView::class.java.isAssignableFrom(method.returnType)
            }
            if (getWebViewMethod == null || getUrlMethod == null || getInnerWebViewMethod == null) {
                XposedCompat.logW("$TAG skipped: cached WebView methods mismatch")
                return
            }

            runtimeTargets = RuntimeTargets(
                getWebViewMethod = getWebViewMethod,
                getUrlMethod = getUrlMethod,
                getInnerWebViewMethod = getInnerWebViewMethod,
            )

            var installedCount = 0
            for (methodName in loadUrlMethodNames) {
                val method = findSideBarUrlLoadMethod(sideBarWebViewClass, methodName) ?: run {
                    XposedCompat.logW("$TAG load method mismatch: $methodName")
                    continue
                }
                val key = ReflectionUtils.methodSignature(method)
                if (!installedMethodKeys.add(key)) continue

                mod.hook(method).intercept { chain ->
                    val target = chain.thisObject
                    val url = chain.args.firstOrNull() as? String
                    val result = chain.proceed()
                    if (target != null && ConfigManager.isHomeSideBarWebAdBlockEnabled && isSideBarUrl(url)) {
                        scheduleInjection(target, url.orEmpty())
                    }
                    result
                }
                installedCount++
            }

            if (installedCount > 0) {
                XposedCompat.log("$TAG hooks INSTALLED: $installedCount")
            } else {
                XposedCompat.logW("$TAG no sidebar url load hook target found")
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun findSideBarUrlLoadMethod(sideBarWebViewClass: Class<*>, methodName: String): Method? {
        return try {
            sideBarWebViewClass.getDeclaredMethod(methodName, String::class.java)
                .takeIf(::isSideBarUrlLoadMethod)
                ?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun isSideBarUrlLoadMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == String::class.java
    }

    private fun scheduleInjection(target: Any, triggerUrl: String) {
        val hostView = target as? View ?: return
        val normalizedUrl = normalizeUrl(triggerUrl) ?: return
        val now = SystemClock.uptimeMillis()
        val shouldSkip = synchronized(scheduledStamp) {
            val previous = scheduledStamp[target]
            if (previous != null &&
                previous.url == normalizedUrl &&
                now - previous.uptimeMs <= SCHEDULE_DEDUPE_WINDOW_MS
            ) {
                true
            } else {
                scheduledStamp[target] = ScheduleStamp(normalizedUrl, now)
                false
            }
        }
        if (shouldSkip) return

        for (delay in INJECT_DELAYS_MS) {
            hostView.postDelayed({
                if (!ConfigManager.isHomeSideBarWebAdBlockEnabled) {
                    clearState(target)
                    return@postDelayed
                }
                val currentUrl = getCurrentUrl(target)
                if (currentUrl == null || !isSideBarUrl(currentUrl)) {
                    clearState(target)
                    return@postDelayed
                }
                injectHideStyle(target)
            }, delay)
        }
    }

    private fun injectHideStyle(target: Any) {
        val innerWebView = getInnerWebView(target) ?: return
        try {
            innerWebView.evaluateJavascript(HIDE_SIDE_BAR_BLOCKS_JS) {
                maybeLogInjected(target)
            }
        } catch (t: Throwable) {
            XposedCompat.logD { "$TAG inject ignored: ${t.message}" }
        }
    }

    private fun maybeLogInjected(target: Any) {
        val url = getCurrentUrl(target) ?: return
        val shouldSkip = synchronized(lastLoggedUrl) {
            val previous = lastLoggedUrl[target]
            if (previous == url) {
                true
            } else {
                lastLoggedUrl[target] = url
                false
            }
        }
        if (shouldSkip) return
        XposedCompat.logD { "$TAG sidebar blocks hidden for url=$url" }
    }

    private fun getCurrentUrl(target: Any): String? {
        val targets = runtimeTargets ?: return null
        val tbWebView = runCatching { targets.getWebViewMethod.invoke(target) }.getOrNull() ?: return null
        return runCatching {
            normalizeUrl(targets.getUrlMethod.invoke(tbWebView) as? String)
        }.getOrNull()
    }

    private fun getInnerWebView(target: Any): WebView? {
        val targets = runtimeTargets ?: return null
        val tbWebView = runCatching { targets.getWebViewMethod.invoke(target) }.getOrNull() ?: return null
        return runCatching { targets.getInnerWebViewMethod.invoke(tbWebView) as? WebView }.getOrNull()
    }

    private fun clearState(target: Any) {
        scheduledStamp.remove(target)
        lastLoggedUrl.remove(target)
    }

    private fun isSideBarUrl(url: String?): Boolean {
        val normalizedUrl = normalizeUrl(url) ?: return false
        return normalizedUrl.contains(SIDE_BAR_PATH.lowercase(Locale.ROOT))
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim().lowercase(Locale.ROOT)
    }
}
