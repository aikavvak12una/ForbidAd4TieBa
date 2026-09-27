package com.forbidad4tieba.hook.feature.shared

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey

internal fun HookInstallContext.canInstallHomeTopTabs(settings: SettingsSnapshot): Boolean {
    return settings.isHomeTopTabsCustomEnabled &&
        available(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS)
}
