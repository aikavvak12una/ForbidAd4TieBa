package com.forbidad4tieba.hook.ui

import android.content.Context
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.symbol.model.HookSymbols

internal object SettingsVersionInfoProvider {
    fun build(context: Context, symbols: HookSymbols? = null): VersionDisplayInfo {
        val tiebaVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: UiText.Settings.UNKNOWN
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        val versionType = symbols?.scanTargetVersionType ?: HookSymbolResolver.readTargetVersionType(context)
        val tiebaBuildType = if (HookSymbolResolver.isOfficialTiebaVersionType(versionType)) {
            UiText.Settings.TIEBA_OFFICIAL_VERSION
        } else {
            UiText.Settings.TIEBA_TEST_VERSION
        }
        val moduleBuildType = if (com.forbidad4tieba.hook.BuildConfig.DEBUG) {
            UiText.Settings.MODULE_DEBUG_VERSION
        } else {
            UiText.Settings.MODULE_RELEASE_VERSION
        }
        return VersionDisplayInfo(
            tiebaVersion = tiebaVersion,
            tiebaBuildType = tiebaBuildType,
            moduleVersion = HookSymbolResolver.runtimeModuleVersionName(),
            moduleBuildType = moduleBuildType,
        )
    }
}
