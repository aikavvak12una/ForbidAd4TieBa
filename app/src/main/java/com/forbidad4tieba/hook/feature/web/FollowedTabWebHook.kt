package com.forbidad4tieba.hook.feature.web

import android.os.SystemClock
import android.webkit.WebView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 在严格范围内移除关注页导航条。
 *
 * 1. 用户启用了自定义首页顶部 tab。
 * 2. 用户启用了关注 tab。
 * 3. URL 是关注页，并带有顶部 tab 标记 `tbhook_from_home_top_tab=1`。
 */
object FollowedTabWebHook {
    private const val FOLLOWED_PAGE_PATH = "/hybrid-usergrow-base/myfollowed/hybrid"
    private const val HOME_TOP_SCOPE_MARKER = "tbhook_from_home_top_tab=1"

    private const val JS_INJECT_TRY_1_DELAY_MS = 80L
    private const val JS_INJECT_TRY_2_DELAY_MS = 260L
    private const val JS_INJECT_TRY_3_DELAY_MS = 900L
    private const val JS_INJECT_TRY_4_DELAY_MS = 1800L
    private const val SCHEDULE_DEDUPE_WINDOW_MS = 1200L

    private const val REMOVE_NAV_JS = """
        (function () {
          var rootSelector = '.my-follow-container';
          var navSelector = '.navigation.tbm-navigation.tbm-navigation-active';
          var styleId = 'tbhook_followed_hide_nav_style_v2';

          function applyStyle(el, styleMap) {
            if (!el || !el.style || !styleMap) return;
            var p = 'important';
            for (var key in styleMap) {
              if (!Object.prototype.hasOwnProperty.call(styleMap, key)) continue;
              el.style.setProperty(key, styleMap[key], p);
            }
          }

          function ensureStyle(doc) {
            if (!doc || !doc.head || doc.getElementById(styleId)) return;
            var style = doc.createElement('style');
            style.id = styleId;
            style.textContent =
              rootSelector + ' ' + navSelector + '{display:none!important;height:0!important;min-height:0!important;max-height:0!important;margin:0!important;padding:0!important;border:0!important;visibility:hidden!important;overflow:hidden!important;pointer-events:none!important;}' +
              rootSelector + '{padding-top:0!important;margin-top:0!important;}' +
              rootSelector + ' .tbm-status.status{padding-top:0!important;margin-top:0!important;top:0!important;}' +
              rootSelector + ' .thread-container{margin-top:0!important;padding-top:0!important;}';
            doc.head.appendChild(style);
          }

          function normalizeTopGap(root) {
            var status = root.querySelector('.tbm-status.status');
            applyStyle(status, {
              'padding-top': '0',
              'margin-top': '0',
              'top': '0'
            });

            var thread = root.querySelector('.thread-container');
            applyStyle(thread, {
              'margin-top': '0',
              'padding-top': '0'
            });

            // Collapse tiny virtual-list phantom spacing only.
            // These values usually come from navigation compensation.
            var phantom = root.querySelector('.render-list-phantom');
            if (phantom && phantom.style) {
              var rawHeight = (phantom.style.height || '').trim();
              if (rawHeight) {
                var numeric = parseFloat(rawHeight);
                if (!isNaN(numeric) && numeric > 0 && numeric <= 2.5) {
                  applyStyle(phantom, {
                    'height': '0',
                    'min-height': '0',
                    'margin': '0',
                    'padding': '0'
                  });
                }
              }
            }
          }

          function processDoc(doc) {
            if (!doc || !doc.querySelector) return;
            ensureStyle(doc);
            var root = doc.querySelector(rootSelector);
            if (!root) return;

            var nav = root.querySelector(navSelector);
            if (nav) {
              applyStyle(nav, {
                'display': 'none',
                'height': '0',
                'min-height': '0',
                'max-height': '0',
                'margin': '0',
                'padding': '0',
                'border': '0',
                'visibility': 'hidden',
                'overflow': 'hidden',
                'pointer-events': 'none'
              });
            }

            normalizeTopGap(root);
          }

          function run() {
            processDoc(document);
          }

          run();
          if (!window.__tbhookFollowedNavObserverV2) {
            var observer = new MutationObserver(function () { run(); });
            observer.observe(document.body || document.documentElement || document, {
              childList: true,
              subtree: true
            });
            window.__tbhookFollowedNavObserverV2 = observer;
            setTimeout(function () {
              try { observer.disconnect(); } catch (e) {}
              try { delete window.__tbhookFollowedNavObserverV2; } catch (e) {}
            }, 6000);
          }
          return true;
        })();
    """

    private val sInstalledMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private data class ScheduleStamp(
        val url: String,
        val uptimeMs: Long,
    )

    private val sScheduledStamp = Collections.synchronizedMap(WeakHashMap<WebView, ScheduleStamp>())
    private val sLastLoggedUrl = Collections.synchronizedMap(WeakHashMap<WebView, String>())

    fun hook(classLoader: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val candidateClasses = linkedSetOf<Class<*>>()
            candidateClasses.add(WebView::class.java)
            for (className in StableTiebaHookPoints.FOLLOWED_TAB_WEB_VIEW_CLASS_NAMES) {
                XposedCompat.findClassOrNull(className, classLoader)?.let { candidateClasses.add(it) }
            }

            val methods = linkedSetOf<Method>()
            for (clazz in candidateClasses) {
                ReflectionUtils.findMethodInHierarchy(clazz, "loadUrl", String::class.java)?.let { methods.add(it) }
                ReflectionUtils.findMethodInHierarchy(clazz, "loadUrl", String::class.java, Map::class.java)?.let { methods.add(it) }
                ReflectionUtils.findMethodInHierarchy(clazz, "postUrl", String::class.java, ByteArray::class.java)?.let { methods.add(it) }
            }

            var installedCount = 0
            for (method in methods) {
                if (!isTargetUrlMethod(method)) continue
                val key = ReflectionUtils.methodSignature(method)
                if (!sInstalledMethodKeys.add(key)) continue

                mod.hook(method).intercept { chain ->
                    val webView = chain.thisObject as? WebView
                    val url = chain.args.firstOrNull() as? String
                    val normalizedUrl = normalizeUrl(url)
                    val enabled = webView != null && isFeatureEnabled()

                    if (webView != null) {
                        onBeforeProceed(webView, normalizedUrl, enabled)
                    }
                    val result = chain.proceed()
                    if (webView != null) {
                        onAfterProceed(webView, normalizedUrl, enabled)
                    }
                    result
                }
                installedCount++
            }

            if (installedCount > 0) {
                XposedCompat.log("[FollowedTabWebHook] hooks INSTALLED: $installedCount")
            } else {
                XposedCompat.logW("[FollowedTabWebHook] no url hook target found")
            }
        } catch (t: Throwable) {
            XposedCompat.log("[FollowedTabWebHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun onBeforeProceed(webView: WebView, normalizedUrl: String?, enabled: Boolean) {
        if (!enabled) {
            clearState(webView)
            return
        }
        if (normalizedUrl != null && !isScopedFollowedUrl(normalizedUrl)) {
            clearState(webView)
        }
    }

    private fun onAfterProceed(webView: WebView, normalizedUrl: String?, enabled: Boolean) {
        if (!enabled) {
            clearState(webView)
            return
        }
        if (normalizedUrl == null || !isScopedFollowedUrl(normalizedUrl)) return

        scheduleRemovalInjection(webView, normalizedUrl)
    }

    private fun scheduleRemovalInjection(webView: WebView, triggerUrl: String) {
        val now = SystemClock.uptimeMillis()
        val shouldSkip = synchronized(sScheduledStamp) {
            val previous = sScheduledStamp[webView]
            if (previous != null &&
                previous.url == triggerUrl &&
                now - previous.uptimeMs <= SCHEDULE_DEDUPE_WINDOW_MS
            ) {
                true
            } else {
                sScheduledStamp[webView] = ScheduleStamp(
                    url = triggerUrl,
                    uptimeMs = now,
                )
                false
            }
        }
        if (shouldSkip) return

        val task = Runnable {
            if (!isFeatureEnabled()) {
                clearState(webView)
                return@Runnable
            }
            val currentUrl = normalizeUrl(webView.url)
            if (currentUrl == null || !isScopedFollowedUrl(currentUrl)) {
                clearState(webView)
                return@Runnable
            }
            injectRemovalJs(webView)
        }

        webView.postDelayed(task, JS_INJECT_TRY_1_DELAY_MS)
        webView.postDelayed(task, JS_INJECT_TRY_2_DELAY_MS)
        webView.postDelayed(task, JS_INJECT_TRY_3_DELAY_MS)
        webView.postDelayed(task, JS_INJECT_TRY_4_DELAY_MS)
    }

    private fun injectRemovalJs(webView: WebView) {
        try {
            webView.evaluateJavascript(REMOVE_NAV_JS) {
                maybeLogInjected(webView)
            }
        } catch (t: Throwable) {
            XposedCompat.logD { "[FollowedTabWebHook] inject ignored: ${t.message}" }
        }
    }

    private fun maybeLogInjected(webView: WebView) {
        val url = normalizeUrl(webView.url) ?: return
        val shouldSkip = synchronized(sLastLoggedUrl) {
            val previous = sLastLoggedUrl[webView]
            if (previous == url) {
                true
            } else {
                sLastLoggedUrl[webView] = url
                false
            }
        }
        if (shouldSkip) return
        XposedCompat.logD { "[FollowedTabWebHook] navigation strip injected for url=$url" }
    }

    private fun clearState(webView: WebView) {
        sScheduledStamp.remove(webView)
        sLastLoggedUrl.remove(webView)
    }

    private fun isFeatureEnabled(): Boolean {
        return ConfigManager.isHomeTopTabsCustomEnabled && ConfigManager.isHomeTopTabFollowedEnabled
    }

    private fun isScopedFollowedUrl(normalizedUrl: String): Boolean {
        return isFollowedPageUrl(normalizedUrl) && hasHomeTopScopeMarker(normalizedUrl)
    }

    private fun isFollowedPageUrl(normalizedUrl: String): Boolean {
        return normalizedUrl.contains(FOLLOWED_PAGE_PATH)
    }

    private fun hasHomeTopScopeMarker(normalizedUrl: String): Boolean {
        return normalizedUrl.contains(HOME_TOP_SCOPE_MARKER)
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim().lowercase(Locale.ROOT)
    }

    private fun isTargetUrlMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.name != "loadUrl" && method.name != "postUrl") return false
        val p = method.parameterTypes
        if (p.isEmpty()) return false
        return p[0] == String::class.java
    }

}
