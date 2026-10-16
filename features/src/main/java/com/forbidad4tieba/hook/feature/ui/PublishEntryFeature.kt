package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.PublishEntryContract
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal val PublishEntryFeature = FeatureDefinition.observed(
    id = "PublishEntryHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { PublishEntryHook.isEnabled(it) },
) { cl, _ ->
    val module = XposedCompat.module ?: return@observed
    PublishEntryHook.install(module, PublishEntryContract.resolve(cl)) {
        ConfigManager.snapshot()
    }
}

internal object PublishEntryHook {
    private const val EXPERIMENT_KEY = "22.12_publish_button_right_top"

    fun isEnabled(settings: SettingsSnapshot): Boolean =
        settings.isBottomTabsCustomEnabled || settings.isFloatingBottomBarEnabled

    fun install(
        api: XposedInterface,
        readers: List<Method>,
        settings: () -> SettingsSnapshot,
    ): List<XposedInterface.HookHandle> {
        val handles = ArrayList<XposedInterface.HookHandle>(readers.size)
        try {
            for (reader in readers) {
                handles += RuntimeHooks.builder(api, reader, "PublishEntryHook", reader.name).intercept { chain ->
                    if (chain.args.firstOrNull() == EXPERIMENT_KEY && isEnabled(settings())) false else chain.proceed()
                }
            }
        } catch (failure: Throwable) {
            // Both readers must agree; do not leave an active partial override after installation fails.
            for (handle in handles.asReversed()) {
                try { handle.unhook() } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            }
            throw failure
        }
        return handles
    }
}
