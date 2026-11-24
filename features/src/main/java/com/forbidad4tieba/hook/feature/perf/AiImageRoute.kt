package com.forbidad4tieba.hook.feature.perf

import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject

/** Matches the page destination, never a link embedded in text or unrelated initData. */
internal object AiImageRoute {
    private const val PATH = "/mo/q/hybrid-main-chatgroup/ai-create-pic"

    private fun isPage(uri: URI): Boolean =
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            uri.host.equals("tieba.baidu.com", true) &&
            uri.path.trimEnd('/').let { it == PATH || it == "$PATH/hybrid" }

    fun matches(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching {
            if (value.startsWith("https:", true) || value.startsWith("http:", true)) {
                // Query payloads can be large; only the destination is relevant here.
                val destination = value.substringBefore('?').substringBefore('#')
                return@runCatching destination.contains("ai-create-pic") && isPage(URI(destination))
            }
            if (!value.startsWith("tiebaapp:") || !value.contains("ai-create-pic")) return@runCatching false
            val uri = URI(value)
            if (uri.host != "router" || uri.path != "/portal") return@runCatching false
            val encoded = uri.rawQuery?.split('&')?.singleOrNull { it.substringBefore('=') == "params" }
                ?.substringAfter('=', "") ?: return@runCatching false
            val target = JSONObject(URLDecoder.decode(encoded, "UTF-8"))
            if (target.optString("page") != "h5/openWebView") return@runCatching false
            val url = target.optJSONObject("pageParams")?.optString("url") ?: return@runCatching false
            isPage(URI(url.substringBefore('?').substringBefore('#')))
        }.getOrDefault(false)
    }
}
