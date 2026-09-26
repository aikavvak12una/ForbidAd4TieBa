package com.forbidad4tieba.hook.feature.diagnostic

import java.util.regex.Pattern

internal object HostLogContent {
    private const val REDACTED = "<redacted>"
    private const val TRUNCATED_SUFFIX = "...[truncated]"
    private val credentialMarker = Pattern.compile(
        "(?i)(?<![a-z0-9_])(?:cookie|set-cookie|authorization|proxy-authorization|" +
            "(?:bduss|stoken|ptoken|baiduid)(?:_bfess)?|tbs|csrf_token|xsrf_token|" +
            "access_token|refresh_token|id_token|token|password|passwd)(?![a-z0-9_])",
    )

    fun boundedCharacterCount(value: String, limit: Int): Int =
        maxOf(REDACTED.length, minOf(value.length, limit))

    fun sanitize(value: String, limit: Int): String {
        // A free-form field has no reliable value boundary. Drop the whole field when marked.
        if (credentialMarker.matcher(value).region(0, minOf(value.length, limit)).find()) {
            return REDACTED
        }
        if (value.length <= limit) return value
        return value.take(limit - TRUNCATED_SUFFIX.length) + TRUNCATED_SUFFIX
    }
}

/** A process-local, one-second budget. It never schedules work or retains log content. */
internal class HostLogRateBudget(
    private val maxEntries: Int,
    private val maxCharacters: Int,
) {
    private var windowStart = Long.MIN_VALUE
    private var entries = 0
    private var characters = 0
    private var suppressed = 0L

    init {
        require(maxEntries > 0 && maxCharacters > 0)
    }

    // Returns -1 when rejected, otherwise a suppression count on the first admitted entry.
    @Synchronized
    fun acquire(characterCount: Int, nowMillis: Long): Long {
        require(characterCount >= 0)
        if (windowStart == Long.MIN_VALUE || nowMillis - windowStart >= 1_000L) {
            windowStart = nowMillis
            entries = 0
            characters = 0
        }
        if (entries >= maxEntries || characterCount > maxCharacters - characters) {
            if (suppressed < Long.MAX_VALUE) suppressed++
            return -1L
        }
        characters += characterCount
        entries++
        if (entries != 1) return 0L
        val pending = suppressed
        suppressed = 0L
        return pending
    }
}
