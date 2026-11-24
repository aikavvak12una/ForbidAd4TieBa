package com.forbidad4tieba.hook.feature.perf

import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiImageRouteTest {
    private val page = "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-create-pic"
    private fun portal(url: String, extra: String = ""): String {
        val data = JSONObject().put("page", "h5/openWebView")
            .put("pageParams", JSONObject().put("url", url).put("initData", extra))
        return "tiebaapp://router/portal?params=" + URLEncoder.encode(data.toString(), "UTF-8")
    }

    @Test fun directAndPortalDestinationsCoverQueriesAndHybridVariant() {
        listOf(page, "$page?from=bigimage_first_create", "$page?payload=${"x".repeat(70_000)}", "$page/hybrid", "$page/",
            portal(page), portal(page, "x".repeat(70_000)), portal("$page/hybrid?from=pb")).forEach { assertTrue(it, AiImageRoute.matches(it)) }
    }

    @Test fun ordinaryPagesUserTextAndOtherServicesAreUnaffected() {
        listOf("https://tieba.baidu.com/", "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot",
            "https://example.com/mo/q/hybrid-main-chatgroup/ai-create-pic", "$page-extra",
            "https://tieba.baidu.com/?next=$page", portal("https://tieba.baidu.com/", page),
            portal(page).replace("tiebaapp:", "other:"), portal(page).replace("tiebaapp:", "TIEBAAPP:"),
            "text $page", "tiebaapp://router/portal?params=broken", "").forEach {
            assertFalse(it, AiImageRoute.matches(it))
        }
    }
}
