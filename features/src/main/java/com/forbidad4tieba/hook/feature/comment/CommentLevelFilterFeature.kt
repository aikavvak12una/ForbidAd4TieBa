package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.symbol.contract.CommentFilterContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val CommentLevelFilterFeature = FeatureDefinition.single(
    id = "CommentLevelFilter",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { available(HookFeatureKey.COMMENT_LEVEL_FILTER) &&
        (it.commentLevelFilter.enabled || (it.isCommentShortcutEnabled && available(HookFeatureKey.COMMENT_SHORTCUT))) },
) { cl, _ ->
    CommentFilterContract.resolve(cl, symbols)?.let(CommentLevelFilterHook::hook)
        ?: InstallOutcome.skipped("comment filter targets unavailable")
}
