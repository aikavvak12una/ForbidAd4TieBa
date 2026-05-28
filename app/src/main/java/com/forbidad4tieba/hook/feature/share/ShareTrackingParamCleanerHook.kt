package com.forbidad4tieba.hook.feature.share

import com.forbidad4tieba.hook.symbol.model.ShareTrackingParamCleanerSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object ShareTrackingParamCleanerHook {

    private val TRACKING_PARAM_KEYS = hashSetOf(
        "sfc",
        "client_type",
        "client_version",
        "st",
        "is_video",
        "unique",
    )

    internal fun hook(targets: ShareTrackingParamCleanerSymbols) {
        val mod = XposedCompat.module ?: return
        val targetMethod = targets.buildUrlMethod

        try {
            mod.hook(targetMethod).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isCleanShareTrackingParamsEnabled) return@intercept result
                val url = result as? String ?: return@intercept result
                sanitizeTrackingParams(url)
            }
            XposedCompat.log(
                "[ShareTrackingParamCleanerHook] hook INSTALLED: " +
                    "${targetMethod.declaringClass.name}.${targetMethod.name}(String,String,String,boolean)",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ShareTrackingParamCleanerHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun sanitizeTrackingParams(url: String): String {
        val queryMarkIndex = url.indexOf('?')
        if (queryMarkIndex < 0 || queryMarkIndex >= url.length - 1) return url

        val fragmentIndex = url.indexOf('#', queryMarkIndex + 1)
        val queryEnd = if (fragmentIndex >= 0) fragmentIndex else url.length
        val query = url.substring(queryMarkIndex + 1, queryEnd)
        if (query.isEmpty()) return url

        var cursor = 0
        var removedCount = 0
        var keptCount = 0
        val kept = StringBuilder(query.length)
        while (cursor <= query.length) {
            val ampIndex = query.indexOf('&', cursor).let { if (it >= 0) it else query.length }
            val segment = query.substring(cursor, ampIndex)
            if (segment.isNotEmpty() && !isTrackingSegment(segment)) {
                if (keptCount > 0) kept.append('&')
                kept.append(segment)
                keptCount++
            } else if (segment.isNotEmpty()) {
                removedCount++
            }
            if (ampIndex == query.length) break
            cursor = ampIndex + 1
        }

        if (removedCount == 0) return url
        val base = url.substring(0, queryMarkIndex)
        val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
        return if (kept.isEmpty()) {
            base + fragment
        } else {
            "$base?$kept$fragment"
        }
    }

    private fun isTrackingSegment(segment: String): Boolean {
        val equalsIndex = segment.indexOf('=')
        val keyRaw = if (equalsIndex >= 0) segment.substring(0, equalsIndex) else segment
        if (keyRaw.isEmpty()) return false
        return TRACKING_PARAM_KEYS.contains(normalizeParamKey(keyRaw))
    }

    private fun normalizeParamKey(raw: String): String {
        val decoded = runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)
        return decoded.lowercase(Locale.ROOT)
    }
}
