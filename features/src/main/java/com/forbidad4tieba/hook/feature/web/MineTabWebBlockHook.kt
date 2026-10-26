package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.contracts.MemberAccess
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * Hides ad and promotion blocks in the verified Mine tab WebView.
 *
 * This hooks TbWebView.loadUrl rather than android.webkit.WebView.loadUrl because the host can show
 * an internal MonitorWebView or cached page directly, bypassing the inner WebView load callbacks.
 */
object MineTabWebBlockHook {
    private const val MINE_TAB_PATH = "/mo/q/hybrid-main-forumtab/mineTab"

    private const val REVEAL_TIMEOUT_MS = 1800L

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

          var host = document.head || document.documentElement;
          if (!host) return false;
          if (!document.getElementById(styleId)) {
            var style = document.createElement('style');
            style.id = styleId;
            style.textContent = hideSelector +
              '{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;max-height:0!important;margin:0!important;padding:0!important;border:0!important;overflow:hidden!important;pointer-events:none!important;}';
            host.appendChild(style);
          }
          return !!document.querySelector(rootSelector);
        })();
    """

    private class RuntimeTargets(
        val getUrlMethod: Method,
        val getInnerWebViewMethod: Method,
    )

    // WebView loads and their callbacks run on the UI thread. Values retain no view strongly.
    private val pending = WeakHashMap<View, RevealGate>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

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

            val targets = RuntimeTargets(
                getUrlMethod = getUrlMethod,
                getInnerWebViewMethod = getInnerWebViewMethod,
            )

            RuntimeHooks.builder(mod, loadUrlMethod, "MineTabWebBlockHook", "hook:loadUrlMethod").intercept { chain ->
                val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                val url = chain.args.firstOrNull() as? String
                pending[view]?.close()
                val gate = if (ConfigManager.snapshot().isMineTabWebAdBlockEnabled && isMineTabUrl(url)) {
                    RevealGate(view, targets).also {
                        pending[view] = it
                        it.hold()
                    }
                } else null
                val result = try {
                    chain.proceed()
                } catch (failure: Throwable) {
                    gate?.close()
                    throw failure
                }
                gate?.run()
                result
            }

            XposedCompat.log("[MineTabWebBlockHook] hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[MineTabWebBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    /** Owns just one load, including cached common pages that emit no page-finished callback. */
    private class RevealGate(view: View, private val targets: RuntimeTargets) :
        Runnable, View.OnAttachStateChangeListener {
        private val viewRef = WeakReference(view)
        private val originalAlpha = view.alpha
        private var closed = false
        private var retryDelayMs = 80L
        private val timeout = Runnable {
            close()
            XposedCompat.logD { "[MineTabWebBlockHook] first frame wait timed out" }
        }

        fun hold() = guarded {
            // A Handler timeout also runs when the wrapper has not attached yet.
            handler.postDelayed(timeout, REVEAL_TIMEOUT_MS)
            viewRef.get()?.let { view ->
                view.addOnAttachStateChangeListener(this)
                // Keep layout and WebView rendering active while suppressing the unstyled frame.
                view.alpha = 0f
            }
        }

        override fun run() = withCurrentWebView { web ->
            // Seed CSS during loading too, so timeout recovery retains any completed purification.
            web.evaluateJavascript(HIDE_PROMO_JS) { result ->
                withCurrentWebView { current ->
                    if (current !== web || result != "true" || web.progress < 100) {
                        retry()
                    } else {
                        // A JS result alone does not guarantee the renderer has applied the CSS.
                        web.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) = withCurrentWebView { current ->
                                if (current !== web || web.progress < 100) retry() else {
                                    close()
                                    XposedCompat.logD { "[MineTabWebBlockHook] purified first frame ready" }
                                }
                            }
                        })
                    }
                }
            }
        }

        private fun withCurrentWebView(action: (WebView) -> Unit) = guarded {
            val view = viewRef.get()
            if (view == null || !ConfigManager.snapshot().isMineTabWebAdBlockEnabled) {
                close()
                return@guarded
            }
            val url = targets.getUrlMethod.invoke(view) as? String
            val web = targets.getInnerWebViewMethod.invoke(view) as? WebView
            if (!isMineTabUrl(url) || web == null) retry() else action(web)
        }

        private fun retry() {
            // One pending retry, with bounded backoff while a slow page remains unready.
            if (closed) return
            handler.postDelayed(this, retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(640L)
        }

        private inline fun guarded(action: () -> Unit) {
            if (closed) return
            try {
                action()
            } catch (failure: Throwable) {
                close()
                XposedCompat.logD { "[MineTabWebBlockHook] first frame wait failed: ${failure.message}" }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacks(this)
            handler.removeCallbacks(timeout)
            viewRef.get()?.let { view ->
                view.removeOnAttachStateChangeListener(this)
                if (pending[view] === this) {
                    pending.remove(view)
                    if (view.alpha == 0f) view.alpha = originalAlpha
                }
            }
        }

        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = close()
    }

    private fun isMineTabUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && url.contains(MINE_TAB_PATH, ignoreCase = true)
    }
}
