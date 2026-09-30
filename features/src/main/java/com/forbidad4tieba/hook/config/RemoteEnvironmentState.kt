package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED
import android.content.Context
import android.content.SharedPreferences

object RemoteEnvironmentState {
    private const val KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE =
        "remote_environment_warning_dialog_active"

    private const val KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE = "remote_restricted_features_lock_active"

    private const val KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING =
        "pending_post_scan_environment_warning"

    @Volatile internal var restrictedFeatureUnlockBlockedByRemote: Boolean = false

    @Volatile private var environmentWarningDialogActive: Boolean = false

    internal fun initialize(context: Context, localPrefs: SharedPreferences) {
        restrictedFeatureUnlockBlockedByRemote = REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE.read(ConfigManager.getModuleStatePrefs(context))
        environmentWarningDialogActive = REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE.read(ConfigManager.getModuleStatePrefs(context))
        if (restrictedFeatureUnlockBlockedByRemote && AccountPreferences.RESTRICTED_FEATURES_UNLOCKED.read(localPrefs)) {
            localPrefs.edit().putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false).apply()
        }
    }

    fun isRestrictedFeaturesUnlocked(context: Context): Boolean {
        return AccountPreferences.RESTRICTED_FEATURES_UNLOCKED.read(ConfigManager.getPrefs(context)) &&
            !isRestrictedFeatureUnlockBlocked(context)
    }

    fun setRestrictedFeaturesUnlocked(context: Context, unlocked: Boolean) {
        val p = ConfigManager.getPrefs(context)
        val finalUnlocked = unlocked && !isRestrictedFeatureUnlockBlocked(context)
        p.edit()
            .putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, finalUnlocked)
            .apply()
    }

    fun isRestrictedFeatureUnlockBlocked(context: Context): Boolean {
        if (restrictedFeatureUnlockBlockedByRemote) return true
        return REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE.read(ConfigManager.getModuleStatePrefs(context))
    }

    fun shouldShowEnvironmentWarningDialog(context: Context): Boolean {
        if (environmentWarningDialogActive) return true
        return REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE.read(ConfigManager.getModuleStatePrefs(context))
    }

    fun markPostScanEnvironmentWarningPending(context: Context) {
        ConfigManager.getModuleStatePrefs(context).edit()
            .putBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, true)
            .apply()
    }

    fun hasPendingPostScanEnvironmentWarning(context: Context): Boolean {
        return PENDING_POST_SCAN_ENVIRONMENT_WARNING.read(ConfigManager.getModuleStatePrefs(context))
    }

    fun consumePendingPostScanEnvironmentWarning(context: Context): Boolean {
        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        if (!RemoteEnvironmentState.PENDING_POST_SCAN_ENVIRONMENT_WARNING.read(statePrefs)) return false
        statePrefs.edit()
            .putBoolean(KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, false)
            .apply()
        return shouldShowEnvironmentWarningDialog(context)
    }

    fun applyRemoteEnvironmentControls(
        context: Context,
        showWarningDialog: Boolean,
        lockHiddenFeatures: Boolean,
    ) {
        val appCtx = context.applicationContext ?: context
        ConfigManager.getModuleStatePrefs(appCtx).edit()
            .putBoolean(KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE, showWarningDialog)
            .putBoolean(KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE, lockHiddenFeatures)
            .apply()

        val p = ConfigManager.getPrefs(appCtx)
        environmentWarningDialogActive = showWarningDialog
        restrictedFeatureUnlockBlockedByRemote = lockHiddenFeatures
        if (lockHiddenFeatures && AccountPreferences.RESTRICTED_FEATURES_UNLOCKED.read(p)) {
            p.edit().putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false).apply()
        }
        ConfigManager.refreshRuntimeSettings(appCtx)
    }

    internal val REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE = BooleanPreference(
        KEY_REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE, false, PreferenceUse.MODULE_STATE, null,
    )
    internal val REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE = BooleanPreference(
        KEY_REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE, false, PreferenceUse.MODULE_STATE, null,
    )
    internal val PENDING_POST_SCAN_ENVIRONMENT_WARNING = BooleanPreference(
        KEY_PENDING_POST_SCAN_ENVIRONMENT_WARNING, false, PreferenceUse.MODULE_STATE, null,
    )

    internal val preferences: List<Preference<*>> = listOf(
        REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE,
        REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE,
        PENDING_POST_SCAN_ENVIRONMENT_WARNING,
    )

}
