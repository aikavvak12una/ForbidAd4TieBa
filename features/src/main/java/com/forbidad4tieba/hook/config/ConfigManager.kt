package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote
import android.content.Context
import android.content.SharedPreferences
import com.forbidad4tieba.hook.contracts.BuildIdentity
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.contracts.Diagnostics

object ConfigManager {
    enum class ScanFeatureAvailabilityState {
        UNKNOWN,
        AVAILABLE,
        PARTIAL,
        DISABLED,
    }

    const val USER_SETTINGS_PREFS_NAME = "tbhook_user_settings"

    const val MODULE_STATE_PREFS_NAME = "tbhook_module_state"

    const val SYMBOL_CACHE_PREFS_NAME = "tbhook_symbol_cache"

    const val LEGACY_MIXED_PREFS_NAME = "tiebahook_settings"

    const val PREFS_NAME = USER_SETTINGS_PREFS_NAME

    private const val KEY_USER_SETTINGS_VERSION_CODE = "user_settings_version_code"

    @Volatile private var prefs: SharedPreferences? = null

    @Volatile private var appContext: Context? = null

    @Volatile private var scanFeatureAvailability: Map<String, ScanFeatureAvailabilityState> = emptyMap()

    @Volatile private var settingsSnapshot: SettingsSnapshot = SettingsSnapshot.bootstrap()

    @Volatile private var settingsSnapshotVersion: Long = 0L

    @Volatile private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    internal fun updateRuntimeSettings(update: (SettingsSnapshot) -> SettingsSnapshot) {
        replaceSettingsSnapshot(update(settingsSnapshot))
    }

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appCtx = context.applicationContext ?: context
            val localPrefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appContext = appCtx
            prefs = localPrefs
            ensureUserSettingsVersion(localPrefs)
            TabCustomizationPreferences.ensureInitialized(localPrefs)

            RemoteEnvironmentState.initialize(appCtx, localPrefs)
            refreshUserSettingsSnapshot(localPrefs)
            ensurePrefsListener(localPrefs)
        }
    }

    internal fun currentValues(): SettingsValues? = prefs?.readOnlyValues()

    fun snapshot(): SettingsSnapshot = settingsSnapshot

    fun snapshotVersion(): Long = settingsSnapshotVersion

    fun refreshRuntimeSettings(context: Context? = null) {
        val p = prefs ?: context?.let {
            init(it)
            prefs
        } ?: return
        synchronized(this) {
            refreshUserSettingsSnapshot(p)
        }
    }

    private fun replaceSettingsSnapshot(snapshot: SettingsSnapshot) {
        settingsSnapshot = snapshot
        settingsSnapshotVersion++
    }

    private fun ensurePrefsListener(p: SharedPreferences) {
        if (prefsListener != null) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, _ ->
            synchronized(this@ConfigManager) {
                if (prefs !== sharedPrefs) return@OnSharedPreferenceChangeListener
                replaceSettingsSnapshot(buildSettingsSnapshot(sharedPrefs))
            }
        }
        prefsListener = listener
        p.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun ensureUserSettingsVersion(p: SharedPreferences) {
        val currentVersion = BuildIdentity.current.versionCode
        val minSupportedVersion = BuildIdentity.current.minSupportedSettingsVersionCode
            .coerceAtMost(currentVersion)
        val storedVersion = p.getInt(KEY_USER_SETTINGS_VERSION_CODE, 0)

        if (storedVersion < minSupportedVersion) {
            p.edit()
                .clear()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
            Diagnostics.log(
                "[ConfigManager] user settings reset: " +
                    "storedVersion=$storedVersion, minSupportedVersion=$minSupportedVersion, " +
                    "currentVersion=$currentVersion"
            )
            return
        }

        if (storedVersion != currentVersion) {
            p.edit()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        init(context)
        return prefs ?: (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getModuleStatePrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext ?: context
        return appCtx.getSharedPreferences(MODULE_STATE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppContext(): Context? = appContext

    fun resetRuntimeAfterUserDataClear(context: Context) {
        synchronized(this) {
            prefsListener?.let { listener ->
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            prefsListener = null
            prefs = null
            appContext = null
            settingsSnapshot = SettingsSnapshot.bootstrap()
            settingsSnapshotVersion++
            HomeGlassPreferences.resetRuntime()
        }
        init(context.applicationContext ?: context)
    }

    private fun refreshUserSettingsSnapshot(p: SharedPreferences) {
        replaceSettingsSnapshot(buildSettingsSnapshot(p))
    }

    private fun buildSettingsSnapshot(p: SharedPreferences): SettingsSnapshot {
        val evaluation = EffectiveSettingsPolicy.derive(
            UserSettingsReader.read(p),
            HostCapabilities(scanFeatureAvailability),
            RemoteSettingsPolicy(restrictedFeatureUnlockBlockedByRemote),
        )
        evaluation.normalizedBottomTabs?.let { normalized ->
            TabPreferences.saveBottomTabSelection(p, normalized)
        }
        return evaluation.snapshot
    }

    fun getScanFeatureAvailabilityState(prefOrFeatureKey: String): ScanFeatureAvailabilityState {
        return HostCapabilities.availabilityOf(scanFeatureAvailability, prefOrFeatureKey)
    }

    fun isScanFeatureAvailable(prefOrFeatureKey: String): Boolean {
        return when (getScanFeatureAvailabilityState(prefOrFeatureKey)) {
            ScanFeatureAvailabilityState.AVAILABLE,
            ScanFeatureAvailabilityState.PARTIAL,
            -> true
            ScanFeatureAvailabilityState.UNKNOWN,
            ScanFeatureAvailabilityState.DISABLED,
            -> false
        }
    }

    fun applyScanAvailability(
        context: Context,
        featureStatusMap: Map<String, HookFeatureStatus>,
        refreshRuntime: Boolean = false,
    ) {
        if (featureStatusMap.isEmpty()) return
        scanFeatureAvailability = featureStatusMap.mapValues { (_, status) ->
            when (status.state) {
                HookFeatureState.FULL,
                HookFeatureState.HARD_CODED,
                -> ScanFeatureAvailabilityState.AVAILABLE
                HookFeatureState.PARTIAL -> ScanFeatureAvailabilityState.PARTIAL
                HookFeatureState.DISABLED -> ScanFeatureAvailabilityState.DISABLED
                else -> ScanFeatureAvailabilityState.UNKNOWN
            }
        }

        if (refreshRuntime) {
            refreshRuntimeSettings(context)
        }
    }


}
