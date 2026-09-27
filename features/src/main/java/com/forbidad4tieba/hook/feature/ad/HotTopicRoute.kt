package com.forbidad4tieba.hook.feature.ad

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Classify the full card destination, never links or topic text inside its components. */
internal object HotTopicRoute {
    fun matches(schema: String?): Boolean {
        if (schema.isNullOrEmpty()) return false
        return try {
            val route = URI(schema)
            if (isTopicPage(route)) return true
            if (!route.scheme.equals("tiebaapp", ignoreCase = true) ||
                !route.host.equals("router", ignoreCase = true) || route.path != "/portal" ||
                route.userInfo != null || route.port != -1
            ) return false
            val params = queryValue(route.rawQuery, "params") ?: return false
            val page = JSONObject(params)
            if (page.opt("page") != "h5/openWebView") return false
            val url = page.optJSONObject("pageParams")?.opt("url") as? String ?: return false
            isTopicPage(URI(url))
        } catch (_: Exception) {
            false
        }
    }

    private fun isTopicPage(uri: URI): Boolean =
        (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
            uri.host.equals("tieba.baidu.com", ignoreCase = true) && uri.userInfo == null &&
            uri.path == "/mo/q/hybrid-usergrow-base/naTopicDetail"

    private fun queryValue(query: String?, key: String): String? {
        if (query == null) return null
        var value: String? = null
        var start = 0
        while (start < query.length) {
            val end = query.indexOf('&', start).let { if (it < 0) query.length else it }
            val equals = query.indexOf('=', start)
            if (equals in start until end && query.regionMatches(start, key, 0, key.length) &&
                equals - start == key.length
            ) {
                if (value != null) return null
                value = URLDecoder.decode(query.substring(equals + 1, end), "UTF-8")
            }
            start = end + 1
        }
        return value
    }
}
