package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSymbols
import com.forbidad4tieba.hook.core.XposedCompat

object FeedInfoLogHook {
    private const val TAG = "[FeedInfoLogHook]"
    private const val MAX_LOG_CONTENT_CHARS = 8192
    private const val MAX_LOG_CHUNKS = 3

    internal fun hook(symbols: FeedInfoLogSymbols) {
        val mod = XposedCompat.module ?: return
        val formatter = FeedInfoLogFormatter(symbols.schema)
        val sampler = FeedInfoLogSampler()
        RuntimeHooks.builder(mod, symbols.bindMethod, "FeedInfoLogHook", "hook:symbols.bindMethod").intercept { chain ->
            val result = chain.proceed()
            if (!ConfigManager.snapshot().isDetailedLoggingEnabled) return@intercept result
            if (!sampler.shouldSample()) return@intercept result
            try {
                val cardData = chain.args.firstOrNull() ?: return@intercept result
                val json = formatter.format(cardData)
                logLong("$TAG $json")
            } catch (t: Throwable) {
                XposedCompat.logD("$TAG error: ${t.message}")
            }
            result
        }
        XposedCompat.log(
            "$TAG hook INSTALLED: ${symbols.bindMethod.declaringClass.name}.${symbols.bindMethod.name}",
        )
    }

    private fun logLong(msg: String) {
        val rawContent = if (msg.startsWith(TAG)) msg.substring(TAG.length).trimStart() else msg
        val content = if (rawContent.length > MAX_LOG_CONTENT_CHARS) {
            rawContent.take(MAX_LOG_CONTENT_CHARS) + "...[truncated ${rawContent.length - MAX_LOG_CONTENT_CHARS} chars]"
        } else {
            rawContent
        }
        val chunkPrefix = "$TAG "
        val maxContent = 3900 - chunkPrefix.length - 12
        if (content.length <= maxContent) {
            XposedCompat.logD("$chunkPrefix$content")
            return
        }

        val totalParts = ((content.length + maxContent - 1) / maxContent).coerceAtMost(MAX_LOG_CHUNKS)
        var offset = 0
        var part = 1
        while (offset < content.length && part <= MAX_LOG_CHUNKS) {
            val end = (offset + maxContent).coerceAtMost(content.length)
            XposedCompat.logD("$chunkPrefix[$part/$totalParts] ${content.substring(offset, end)}")
            offset = end
            part++
        }
    }
}
