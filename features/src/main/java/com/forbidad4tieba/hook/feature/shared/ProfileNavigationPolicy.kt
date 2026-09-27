package com.forbidad4tieba.hook.feature.shared

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal fun HookInstallContext.canInstallCommentAvatarDirectProfile(settings: SettingsSnapshot): Boolean {
    return isMain &&
        settings.isCommentAvatarDirectProfileEnabled &&
        available(HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)
}
