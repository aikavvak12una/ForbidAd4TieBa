package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.Locale

/**
 * 精简进吧页面（WebView CSS 注入）。
 *
 * 通过 hook WebViewClient.onPageFinished 在页面加载完成后注入 CSS，
 * 隐藏进吧页面中的推广/引导类元素。
 *
 * 之前的方案 hook BaseWebView.loadUrl 有时序问题：
 * loadUrl 调用时新页面 DOM 尚未就绪，evaluateJavascript 会注入到旧页面中。
 */
object EnterForumWebHook {

    private val FILTER_JS = """
        javascript:(function() {
            if (window.__tbhook_jinba_injected) return;
            window.__tbhook_jinba_injected = true;

            // 1. 隐藏多余元素
            var style = document.createElement('style');
            style.id = 'tbhook-jinba-style';
            style.innerHTML = `
                .pull-con.animation-top .guideIpContainer,
                .pull-con.animation-top .forum-album-wrapper,
                .guide-container,
                .guideIpContainer,
                .forum-album-wrapper,
                .recommend-guide,
                .hot-forum-recommend,
                .topic-recommend
                { display: none !important; }
            `;
            var insert = function() { 
                if(document.head) document.head.appendChild(style); 
                else if(document.documentElement) document.documentElement.appendChild(style); 
            };
            if (document.head || document.documentElement) { insert(); }
            else { 
                var ob = new MutationObserver(function() { 
                    if (document.head || document.documentElement) { 
                        insert(); ob.disconnect(); 
                    } 
                }); 
                ob.observe(document, {childList: true, subtree: true}); 
            }

            // 2. 自动点击展开所有吧
            var counter = 0;
            var timer = setInterval(function() {
                counter++;
                try {
                    var btns = document.querySelectorAll('.more-forum');
                    var clicked = false;
                    for (var i = 0; i < btns.length; i++) {
                        var btn = btns[i];
                        if (btn && btn.textContent && btn.textContent.indexOf('展开') !== -1) {
                            btn.click();
                            clicked = true;
                        }
                    }
                    if (clicked || counter > 20) {
                        clearInterval(timer);
                    }
                } catch(e) {}
            }, 500);
        })();
    """.trimIndent()

    fun hook(classLoader: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val webViewClientClass = android.webkit.WebViewClient::class.java

            // 1. Hook onPageStarted (早期注入，解决页面闪现(FOUC)问题)
            try {
                val onPageStarted = webViewClientClass.getDeclaredMethod(
                    "onPageStarted",
                    android.webkit.WebView::class.java,
                    String::class.java,
                    android.graphics.Bitmap::class.java
                )
                mod.hook(onPageStarted).intercept { chain ->
                    val result = chain.proceed()
                    tryInjectCss(chain.args[0] as? android.webkit.WebView, chain.args[1] as? String, "onPageStarted")
                    result
                }
                XposedCompat.log("[EnterForumWebHook] hook INSTALLED on WebViewClient.onPageStarted")
            } catch (t: Throwable) {
                XposedCompat.log("[EnterForumWebHook] hook onPageStarted FAILED: ${t.message}")
            }

            // 2. Hook onPageFinished (晚期注入作为兜底)
            try {
                val onPageFinished = webViewClientClass.getDeclaredMethod(
                    "onPageFinished",
                    android.webkit.WebView::class.java,
                    String::class.java
                )
                mod.hook(onPageFinished).intercept { chain ->
                    val result = chain.proceed()
                    tryInjectCss(chain.args[0] as? android.webkit.WebView, chain.args[1] as? String, "onPageFinished")
                    result
                }
                XposedCompat.log("[EnterForumWebHook] hook INSTALLED on WebViewClient.onPageFinished")
            } catch (t: Throwable) {
                XposedCompat.log("[EnterForumWebHook] hook onPageFinished FAILED: ${t.message}")
            }

        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryInjectCss(webView: android.webkit.WebView?, url: String?, stage: String) {
        if (!ConfigManager.isEnterForumWebFilterEnabled) return
        if (webView == null || url == null || !shouldInject(url)) return
        XposedCompat.logD("[EnterForumWebHook] > $stage, injecting CSS for: $url")
        try {
            webView.evaluateJavascript(FILTER_JS, null)
        } catch (t: Throwable) {
            XposedCompat.log("[EnterForumWebHook] inject failed: ${t.message}")
        }
    }

    private fun shouldInject(url: String): Boolean {
        if (url.startsWith("javascript:", ignoreCase = true)) return false
        val lower = url.lowercase(Locale.ROOT)
        return lower.startsWith("https://tieba.baidu.com/") ||
            lower.startsWith("http://tieba.baidu.com/") ||
            lower.startsWith("https://tiebac.baidu.com/") ||
            lower.startsWith("http://tiebac.baidu.com/")
    }
}
