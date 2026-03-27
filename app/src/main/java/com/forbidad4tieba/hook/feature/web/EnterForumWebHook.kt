package com.forbidad4tieba.hook.feature.web

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.Locale

object EnterForumWebHook {
    // Safe tag ID (0x7E prefix avoids collisions with app R.id 0x7F)
    private const val TAG_LAST_FILTER_URL = 0x7E000011.toInt()

    private const val FILTER_JS = "javascript:(function() {" +
        "if (window.__tbhook_injected) return;" +
        "window.__tbhook_injected = true;" +
        "var interval = setInterval(function() {" +
        "  var containers = document.getElementsByClassName('pull-con animation-top');" +
        "  if (containers.length > 0) {" +
        "    var foundLikeForum = false;" +
        "    for (var i = 0; i < containers.length; i++) {" +
        "      var container = containers[i];" +
        "      var children = container.children;" +
        "      for (var j = children.length - 1; j >= 0; j--) {" +
        "        var child = children[j];" +
        "        if (child.querySelector('.like-forum') !== null) {" +
        "          foundLikeForum = true;" +
        "        } else {" +
        "          child.style.display = 'none';" +
        "        }" +
        "      }" +
        "    }" +
        "    if (foundLikeForum) { clearInterval(interval); }" +
        "  }" +
        "}, 200);" +
        "setTimeout(function(){ clearInterval(interval); window.__tbhook_injected = false; }, 5000);" +
        "})();"

    fun hook() {
        try {
            XposedBridge.hookAllMethods(
                android.webkit.WebView::class.java,
                "loadUrl",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isEnterForumWebFilterEnabled) return
                        val args = param.args ?: return
                        if (args.isEmpty()) return
                        val url = args[0] as? String ?: return
                        if (!shouldInject(url)) return

                        val webView = param.thisObject as? android.webkit.WebView ?: return
                        if (webView.context?.packageName != Constants.TARGET_PACKAGE) return

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
