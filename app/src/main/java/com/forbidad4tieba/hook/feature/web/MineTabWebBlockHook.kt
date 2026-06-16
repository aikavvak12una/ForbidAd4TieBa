package com.forbidad4tieba.hook.feature.web

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hides ad and promotion blocks in the Mine tab WebView for supported host versions.
 *
 * This hooks TbWebView.loadUrl rather than android.webkit.WebView.loadUrl because the host can show
 * an internal MonitorWebView or cached page directly, bypassing the inner WebView load callbacks.
 */
object MineTabWebBlockHook {
    private const val MIN_WEB_MINE_TAB_VERSION_CODE = 369491968L
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
    private val versionWarningLogged = AtomicBoolean(false)

    @Volatile
    private var runtimeTargets: RuntimeTargets? = null

    @Volatile
    private var versionEligible: Boolean? = null

    fun hook(classLoader: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val tbWebViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_WEB_VIEW_CLASS, classLoader)
            if (tbWebViewClass == null) {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: TbWebView class not found")
                return
            }

            val loadUrlMethod = ReflectionUtils.findMethodInHierarchy(
                tbWebViewClass,
                "loadUrl",
                String::class.java,
            )
            val getUrlMethod = ReflectionUtils.findMethodInHierarchy(tbWebViewClass, "getUrl")
            val getInnerWebViewMethod = ReflectionUtils.findMethodInHierarchy(tbWebViewClass, "getInnerWebView")
            if (loadUrlMethod == null || getUrlMethod == null || getInnerWebViewMethod == null) {
                XposedCompat.logW("[MineTabWebBlockHook] skipped: TbWebView methods missing")
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

            mod.hook(loadUrlMethod).intercept { chain ->
                val target = chain.thisObject
                val url = chain.args.firstOrNull() as? String

                val result = chain.proceed()

                val view = target as? View
                if (view != null && isMineTabUrl(url) && isFeatureEnabledFor(view.context)) {
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

    fun onAppContextReady(context: Context) {
        if (versionEligible != null) return
        versionEligible = resolveVersionEligible(context)
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
                if (!isFeatureEnabledFor(hostView.context)) {
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

    private fun isFeatureEnabledFor(context: Context?): Boolean {
        return ConfigManager.isMineTabWebAdBlockEnabled && isTargetVersionEligible(context)
    }

    private fun isTargetVersionEligible(context: Context?): Boolean {
        val cached = versionEligible
        if (cached != null) return cached
        if (context == null) return false
        return resolveVersionEligible(context).also { versionEligible = it }
    }

    private fun resolveVersionEligible(context: Context): Boolean {
        return try {
            val appContext = context.applicationContext ?: context
            val info = appContext.packageManager.getPackageInfo(Constants.TARGET_PACKAGE, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            versionCode >= MIN_WEB_MINE_TAB_VERSION_CODE
        } catch (t: Throwable) {
            if (versionWarningLogged.compareAndSet(false, true)) {
                XposedCompat.logW("[MineTabWebBlockHook] target version unavailable: ${t.message}")
            }
            false
        }
    }

    private fun isMineTabUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && url.contains(MINE_TAB_PATH, ignoreCase = true)
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim()
    }
}
