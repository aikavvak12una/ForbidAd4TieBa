package com.forbidad4tieba.hook.feature.web

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object HelpCenterFooterBlockHook {
    private const val TAG = "[HelpCenterFooterBlockHook]"
    private const val UFO_WEB_LOADER_ACTIVITY_CLASS = "com.baidu.ufosdk.hybrid.base.WebLoaderActivity"
    private const val BRIDGE_NAME = "__tbhookHelpCenterBridgeV1"
    private const val SCHEDULE_DEDUPE_WINDOW_MS = 1200L
    private val INJECT_DELAYS_MS = longArrayOf(0L, 120L, 420L, 1000L, 2500L, 5000L)

    private const val BLOCK_FOOTER_CLICK_JS = """
        (function () {
          var bridgeName = '__tbhookHelpCenterBridgeV1';
          var stateName = '__tbhookHelpCenterFooterBlockStateV1';
          var installedName = '__tbhookHelpCenterFooterBlockInstalledV1';
          var observerName = '__tbhookHelpCenterFooterBlockObserverV1';
          var targetSelector = '.fotter-switch.align-center.fixed-position.no-back-container';
          var state = window[stateName] || (window[stateName] = { lastToastAt: 0 });

          function matches(el, selector) {
            if (!el || el.nodeType !== 1) return false;
            var fn = el.matches || el.webkitMatchesSelector || el.msMatchesSelector;
            return !!fn && fn.call(el, selector);
          }

          function closest(el, selector) {
            var cur = el;
            while (cur && cur !== document) {
              if (matches(cur, selector)) return cur;
              cur = cur.parentNode;
            }
            return null;
          }

          function hasBailingAsset() {
            var nodes = document.querySelectorAll('script[src],link[href]');
            for (var i = 0; i < nodes.length; i++) {
              var value = nodes[i].src || nodes[i].href || '';
              if (value.indexOf('/bailing/') >= 0 || value.indexOf('web-fe-static-release.cdn.bcebos.com/bailing') >= 0) {
                return true;
              }
            }
            return false;
          }

          function isHelpCenter() {
            if (document.querySelector('.channel-help')) return true;
            if (document.querySelector(targetSelector) && document.querySelector('.my-feedback')) return true;
            if ((document.title || '').indexOf('\u5e2e\u52a9\u4e2d\u5fc3') >= 0) return true;
            return hasBailingAsset();
          }

          function notifyOnce() {
            var now = Date.now ? Date.now() : new Date().getTime();
            if (now - state.lastToastAt < 900) return;
            state.lastToastAt = now;
            try {
              var bridge = window[bridgeName];
              if (bridge && typeof bridge.showDisabledToast === 'function') {
                bridge.showDisabledToast();
              }
            } catch (e) {}
          }

          function blockEvent(event) {
            var target = event && event.target;
            var node = closest(target, targetSelector);
            if (!node || !isHelpCenter()) return;
            try { event.preventDefault(); } catch (e) {}
            try { event.stopImmediatePropagation(); } catch (e) {
              try { event.stopPropagation(); } catch (ignored) {}
            }
            notifyOnce();
            return false;
          }

          function markTargets() {
            if (!isHelpCenter()) return false;
            var nodes = document.querySelectorAll(targetSelector);
            for (var i = 0; i < nodes.length; i++) {
              nodes[i].setAttribute('aria-disabled', 'true');
              nodes[i].setAttribute('data-tbhook-disabled', '1');
            }
            return nodes.length > 0;
          }

          function install() {
            if (!isHelpCenter()) return false;
            if (!window[installedName]) {
              var events = ['touchstart', 'touchend', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'click'];
              for (var i = 0; i < events.length; i++) {
                document.addEventListener(events[i], blockEvent, true);
              }
              window[installedName] = true;
            }
            return markTargets();
          }

          var installed = install();
          if (!window[observerName] && typeof MutationObserver === 'function') {
            var observer = new MutationObserver(function () { install(); });
            observer.observe(document.documentElement || document, {
              childList: true,
              subtree: true
            });
            window[observerName] = observer;
            setTimeout(function () {
              try { observer.disconnect(); } catch (e) {}
              try { delete window[observerName]; } catch (e) {}
            }, 10000);
          }
          return installed;
        })();
    """

    private data class ScheduleStamp(
        val url: String,
        val uptimeMs: Long,
    )

    private class ToastBridge(private val appContext: android.content.Context) {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun showDisabledToast() {
            mainHandler.post {
                Toast.makeText(
                    appContext,
                    UiText.HelpCenter.TOAST_MODULE_DISABLED_FEATURE,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val scheduledStamp = Collections.synchronizedMap(WeakHashMap<WebView, ScheduleStamp>())
    private val bridgedViews = Collections.synchronizedMap(WeakHashMap<WebView, Boolean>())
    private val lastLoggedUrl = Collections.synchronizedMap(WeakHashMap<WebView, String>())

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
                if (!installedMethodKeys.add(key)) continue

                mod.hook(method).intercept { chain ->
                    val webView = chain.thisObject as? WebView
                    val triggerUrl = chain.args.firstOrNull() as? String
                    if (webView == null) {
                        return@intercept chain.proceed()
                    }

                    val candidate = shouldScheduleFor(webView, triggerUrl)
                    if (candidate) {
                        ensureBridge(webView)
                    }

                    val result = chain.proceed()

                    if (candidate) {
                        scheduleInjection(webView, triggerUrl)
                    } else if (!shouldKeepState(webView, triggerUrl)) {
                        clearState(webView)
                    }
                    result
                }
                installedCount++
            }

            if (installedCount > 0) {
                XposedCompat.log("$TAG hooks INSTALLED: $installedCount")
            } else {
                XposedCompat.logW("$TAG no WebView url hook target found")
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun scheduleInjection(webView: WebView, triggerUrl: String?) {
        val normalizedUrl = normalizeUrl(triggerUrl ?: webView.url) ?: "<unknown>"
        val now = SystemClock.uptimeMillis()
        val shouldSkip = synchronized(scheduledStamp) {
            val previous = scheduledStamp[webView]
            if (previous != null &&
                previous.url == normalizedUrl &&
                now - previous.uptimeMs <= SCHEDULE_DEDUPE_WINDOW_MS
            ) {
                true
            } else {
                scheduledStamp[webView] = ScheduleStamp(normalizedUrl, now)
                false
            }
        }
        if (shouldSkip) return

        for (delay in INJECT_DELAYS_MS) {
            webView.postDelayed({
                if (!shouldScheduleFor(webView, webView.url)) {
                    clearState(webView)
                    return@postDelayed
                }
                injectBlockJs(webView)
            }, delay)
        }
    }

    private fun ensureBridge(webView: WebView) {
        synchronized(bridgedViews) {
            if (bridgedViews[webView] == true) return
            try {
                val appContext = webView.context.applicationContext ?: webView.context
                webView.addJavascriptInterface(ToastBridge(appContext), BRIDGE_NAME)
                bridgedViews[webView] = true
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG bridge ignored: ${t.message}" }
            }
        }
    }

    private fun injectBlockJs(webView: WebView) {
        try {
            ensureBridge(webView)
            webView.evaluateJavascript(BLOCK_FOOTER_CLICK_JS) { result ->
                if (result == "true") {
                    maybeLogInjected(webView)
                }
            }
        } catch (t: Throwable) {
            XposedCompat.logD { "$TAG inject ignored: ${t.message}" }
        }
    }

    private fun maybeLogInjected(webView: WebView) {
        val url = normalizeUrl(webView.url) ?: "<unknown>"
        val shouldSkip = synchronized(lastLoggedUrl) {
            val previous = lastLoggedUrl[webView]
            if (previous == url) {
                true
            } else {
                lastLoggedUrl[webView] = url
                false
            }
        }
        if (shouldSkip) return
        XposedCompat.logD { "$TAG footer click blocked for url=$url" }
    }

    private fun clearState(webView: WebView) {
        scheduledStamp.remove(webView)
        lastLoggedUrl.remove(webView)
    }

    private fun shouldKeepState(webView: WebView, url: String?): Boolean {
        return isUfoWebLoader(webView) || isHelpCenterUrl(url) || isHelpCenterUrl(webView.url)
    }

    private fun shouldScheduleFor(webView: WebView, url: String?): Boolean {
        return isUfoWebLoader(webView) || isHelpCenterUrl(url) || isHelpCenterUrl(webView.url)
    }

    private fun isUfoWebLoader(webView: WebView): Boolean {
        val activity = ReflectionUtils.findActivityFromContext(webView.context) ?: return false
        return activity.javaClass.name == UFO_WEB_LOADER_ACTIVITY_CLASS
    }

    private fun isHelpCenterUrl(url: String?): Boolean {
        val lower = normalizeUrl(url) ?: return false
        return lower.contains("bailing") ||
            lower.contains("help_center") ||
            lower.contains("helpcenter") ||
            lower.contains("ufosdk.baidu.com")
    }

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim().lowercase(Locale.ROOT)
    }

    private fun isTargetUrlMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.name != "loadUrl" && method.name != "postUrl") return false
        val params = method.parameterTypes
        if (params.isEmpty() || params[0] != String::class.java) return false
        return method.name != "postUrl" || (params.size == 2 && params[1] == ByteArray::class.java)
    }
}
