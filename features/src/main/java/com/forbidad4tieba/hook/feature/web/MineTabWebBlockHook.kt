package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.contracts.MemberAccess
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
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Hides ad and promotion blocks in the verified Mine tab WebView.
 *
 * This hooks TbWebView.loadUrl rather than android.webkit.WebView.loadUrl because the host can show
 * an internal MonitorWebView or cached page directly, bypassing the inner WebView load callbacks.
 */
object MineTabWebBlockHook {
    private const val MINE_TAB_PATH = "/mo/q/hybrid-main-forumtab/mineTab"

    private val INJECT_DELAYS_MS = longArrayOf(0L, 80L, 260L, 900L, 1800L)
    private const val SCHEDULE_DEDUPE_WINDOW_MS = 1200L

    private const val HIDE_PROMO_JS = """
        (function () {
          var styleId = 'tbhook_mine_tab_hide_promo_style_v1';
          var rootSelector = '.mine-tab-page';
          var hideSelector =
            rootSelector + ' .xiaoman-wallet,' +
            rootSelector + ' #banner-swiper.banner-container,' +
            rootSelector + ' .banner-container.swiper-initialized.swiper-horizontal.swiper-android.swiper-backface-hidden,' +
            rootSelector + ' .activity-area,' +
            rootSelector + ' .image-design,' +
            rootSelector + ' .vip-banner,' +
            rootSelector + ' .game-zone';

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

          return run();
        })();
    """

    private data class RuntimeTargets(
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
            val className = symbols[MineTabWebContract.mineTabWebViewClass]?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: missing cached class")
                return
            }
            val loadUrlMethodName = symbols[MineTabWebContract.mineTabWebLoadUrlMethod]?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: missing cached loadUrl method")
                return
            }
            val getUrlMethodName = symbols[MineTabWebContract.mineTabWebGetUrlMethod]?.takeIf { it.isNotBlank() } ?: run {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: missing cached getUrl method")
                return
            }
            val getInnerWebViewMethodName =
                symbols[MineTabWebContract.mineTabWebGetInnerWebViewMethod]?.takeIf { it.isNotBlank() } ?: run {
                    XposedCompat.logW("[MineTabWebBlockHook] skipped: missing cached inner WebView method")
                    return
                }

            val tbWebViewClass = MemberAccess.findClassOrNull(className, classLoader)
            if (tbWebViewClass == null) {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: class not found: $className")
                return
            }

            val loadUrlMethod = ReflectionUtils.findMethodInHierarchy(
                tbWebViewClass,
                loadUrlMethodName,
                String::class.java,
            )?.takeIf { method ->
                method.returnType == Void.TYPE
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
            if (loadUrlMethod == null || getUrlMethod == null || getInnerWebViewMethod == null) {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: cached methods mismatch")
                return
            }
            if (Modifier.isStatic(loadUrlMethod.modifiers)) {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: static loadUrl target")
                return
            }

            val key = ReflectionUtils.methodSignature(loadUrlMethod)
            if (!installedMethodKeys.add(key)) return

            runtimeTargets = RuntimeTargets(
                getUrlMethod = getUrlMethod,
                getInnerWebViewMethod = getInnerWebViewMethod,
            )

            RuntimeHooks.builder(mod, loadUrlMethod, "MineTabWebBlockHook", "hook:loadUrlMethod").intercept { chain ->
                val target = chain.thisObject
                val url = chain.args.firstOrNull() as? String

                val result = chain.proceed()

                val view = target as? View
                if (view != null && isMineTabUrl(url) && ConfigManager.isMineTabWebAdBlockEnabled) {
                    scheduleInjection(target, view, url.orEmpty())
                }
                result
            }

            XposedCompat.log("[MineTabWebBlockHook] hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[MineTabWebBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun scheduleInjection(target: Any, hostView: View, triggerUrl: String) {
        val now = SystemClock.uptimeMillis()
        val shouldSkip = synchronized(scheduledStamp) {
            val previous = scheduledStamp[target]
            if (previous != null &&
                previous.url == triggerUrl &&
                now - previous.uptimeMs <= SCHEDULE_DEDUPE_WINDOW_MS
            ) {
                true
            } else {
                scheduledStamp[target] = ScheduleStamp(triggerUrl, now)
                false
            }
        }
        if (shouldSkip) return

        for (delay in INJECT_DELAYS_MS) {
            hostView.postDelayed({
                if (!ConfigManager.isMineTabWebAdBlockEnabled) {
                    clearState(target)
                    return@postDelayed
                }

                val currentUrl = getCurrentUrl(target)
                if (currentUrl == null || !isMineTabUrl(currentUrl)) {
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
            innerWebView.evaluateJavascript(HIDE_PROMO_JS) {
                maybeLogInjected(target)
            }
        } catch (t: Throwable) {
            XposedCompat.logD { "[MineTabWebBlockHook] inject ignored: ${t.message}" }
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
        XposedCompat.logD { "[MineTabWebBlockHook] promo blocks hidden for url=$url" }
    }

    private fun getCurrentUrl(target: Any): String? {
        val method = runtimeTargets?.getUrlMethod ?: return null
        return runCatching { normalizeUrl(method.invoke(target) as? String) }.getOrNull()
    }

    private fun getInnerWebView(target: Any): WebView? {
        val method = runtimeTargets?.getInnerWebViewMethod ?: return null
        return runCatching { method.invoke(target) as? WebView }.getOrNull()
    }

    private fun clearState(target: Any) {
        scheduledStamp.remove(target)
        lastLoggedUrl.remove(target)
    }

    private fun isMineTabUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && url.contains(MINE_TAB_PATH, ignoreCase = true)
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim()
    }
}
