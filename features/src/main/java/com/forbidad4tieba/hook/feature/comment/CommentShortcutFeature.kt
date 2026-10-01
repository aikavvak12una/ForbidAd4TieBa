package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.symbol.contract.CommentShortcutContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal val CommentShortcutFeature = FeatureDefinition.single(
    id = "CommentShortcut", phase = FeaturePhase.SYMBOL, process = FeatureProcess.MAIN,
    enabled = { it.isCommentShortcutEnabled && available(HookFeatureKey.COMMENT_SHORTCUT) && available(HookFeatureKey.COMMENT_LEVEL_FILTER) },
) { cl, _ ->
    val targets = CommentShortcutContract.resolve(cl, symbols)
    val context = ConfigManager.getAppContext()
    if (targets == null || context == null || !CommentLevelFilterHook.isReady()) {
        InstallOutcome.skipped("comment shortcut prerequisite unavailable")
    } else CommentShortcutHook.hook(targets, targets.icons(context))
}
