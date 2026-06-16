package com.forbidad4tieba.hook.ui

import android.app.Activity
import com.forbidad4tieba.hook.config.ConfigManager

internal object SettingsScanDialogHooks {
    fun ensureInitialScanDialogHook(
        classLoader: ClassLoader,
        onScanRequested: (Activity, ClassLoader) -> Unit,
    ) {
        InitialScanDialogInstaller.ensureInstalled(classLoader) { activity, cl ->
            onScanRequested(activity, cl)
        }
    }

    fun ensurePostScanEnvironmentWarningHook(
        onWarningRequested: (Activity, () -> Unit) -> Unit,
    ) {
        PostScanEnvironmentWarningInstaller.ensureInstalled { activity ->
            if (!ConfigManager.hasPendingPostScanEnvironmentWarning(activity)) {
                return@ensureInstalled
            }
            ModuleDialogQueue.enqueue {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed ||
                    !ConfigManager.consumePendingPostScanEnvironmentWarning(activity)
                ) {
                    ModuleDialogQueue.finishCurrent()
                    return@enqueue
                }
                onWarningRequested(activity) {
                    ModuleDialogQueue.finishCurrent()
                }
            }
        }
    }
}
