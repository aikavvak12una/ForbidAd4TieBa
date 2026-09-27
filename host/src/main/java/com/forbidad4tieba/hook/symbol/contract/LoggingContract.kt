package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

object LoggingContract : SymbolContract("Logging") {
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = Unit

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val replyServerLogReady =
            !symbols[ReplyLogContract.replyServerResponseClass].isNullOrBlank() &&
                !symbols[ReplyLogContract.replyServerResponseDecodeMethod].isNullOrBlank() &&
                !symbols[ReplyLogContract.replyServerResponseResultJsonField].isNullOrBlank()
        val agreeServerLogReady =
            !symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass].isNullOrBlank() &&
                !symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod].isNullOrBlank()
        val feedInfoLogReady = !symbols[FeedContract.feedCardBindMethodSpec].isNullOrBlank()
        val detailedLoggingMissing = buildList {
            if (!replyServerLogReady) add("ReplyServerResponseLogHook")
            if (!agreeServerLogReady) add("AgreeServerResponseLogHook")
            if (!feedInfoLogReady) add("FeedInfoLogHook.Bind")
        }
        out[HookFeatureKey.DETAILED_LOGGING] = when {
            detailedLoggingMissing.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            else -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = detailedLoggingMissing,
            )
        }
        return out
    }

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // Diagnostics are supplied by the consuming capability or the fixed entry installer.
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = emptyList()
}
