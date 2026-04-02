package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Locale

object EnterForumWebHook {
    // Safe tag ID (0x7E prefix avoids collisions with app R.id 0x7F)
    private const val TAG_LAST_FILTER_URL = 0x7E000011.toInt()

    private const val FILTER_JS = "javascript:(function() {" +
        "if (document.getElementById('tbhook-jinba-style')) return;" +
        "var style = document.createElement('style');" +
        "style.id = 'tbhook-jinba-style';" +
        "style.innerHTML = '.pull-con.animation-top .guideIpContainer, .pull-con.animation-top .forum-album-wrapper { display: none !important; }';" +
        "var insert = function() { if(document.head) document.head.appendChild(style); else if(document.documentElement) document.documentElement.appendChild(style); };" +
        "if (document.head || document.documentElement) { insert(); }" +
        "else { var ob = new MutationObserver(function() { if (document.head || document.documentElement) { insert(); ob.disconnect(); } }); ob.observe(document, {childList: true}); }" +
        "})();"

    fun hook(classLoader: ClassLoader) {
        try {
            val baseWebViewClass = XposedHelpers.findClassIfExists("com.baidu.tieba.browser.core.webview.base.BaseWebView", classLoader)
            if (baseWebViewClass == null) {
                XposedBridge.log("${Constants.TAG}: BaseWebView class not found")
                return
            }

            XposedBridge.hookAllMethods(
                baseWebViewClass,
                "loadUrl",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isEnterForumWebFilterEnabled) return
                        val args = param.args ?: return
                        if (args.isEmpty()) return
                        val url = args[0] as? String ?: return
                        if (!shouldInject(url)) return

                        val webView = param.thisObject as? android.webkit.WebView ?: return
                        val urlKey = url.substringBefore('#')
                        if (webView.getTag(TAG_LAST_FILTER_URL) == urlKey) return
                        webView.setTag(TAG_LAST_FILTER_URL, urlKey)

                        webView.post {
                            try {
                                webView.evaluateJavascript(FILTER_JS, null)
                            } catch (t: Throwable) {
                                XposedBridge.log("${Constants.TAG}: WebView inject failed: ${t.message}")
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook WebView for EnterForum: ${t.message}")
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
