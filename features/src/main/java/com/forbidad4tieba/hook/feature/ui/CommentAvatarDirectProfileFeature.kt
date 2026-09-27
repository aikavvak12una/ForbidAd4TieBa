package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.feature.shared.canInstallCommentAvatarDirectProfile
import com.forbidad4tieba.hook.symbol.contract.ProfileContract

internal val CommentAvatarDirectProfileFeature = FeatureDefinition.observed(
    id = "CommentAvatarDirectProfileHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    enabled = { settings -> canInstallCommentAvatarDirectProfile(settings) },
) { cl, settings ->
    ProfileContract.resolveGlobalDirectProfileSymbols(cl)?.let { targets ->
        CommentAvatarDirectProfileHook.hook(targets)
    }
}
