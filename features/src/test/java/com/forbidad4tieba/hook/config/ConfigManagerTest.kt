package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.forbidad4tieba.hook.contracts.BuildIdentity
import com.forbidad4tieba.hook.contracts.ModuleBuildInfo
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class ConfigManagerTest {
    private val prefsField = ConfigManager::class.java.getDeclaredField("prefs").apply { isAccessible = true }
    private val contextField = ConfigManager::class.java.getDeclaredField("appContext").apply { isAccessible = true }
    private val listenerField = ConfigManager::class.java.getDeclaredField("prefsListener").apply { isAccessible = true }
    private val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply {
        isAccessible = true
    }
    private val versionField = ConfigManager::class.java.getDeclaredField("settingsSnapshotVersion").apply {
        isAccessible = true
    }
    private val availabilityField = ConfigManager::class.java.getDeclaredField("scanFeatureAvailability").apply {
        isAccessible = true
    }
    private val warningField = RemoteEnvironmentState::class.java
        .getDeclaredField("environmentWarningDialogActive").apply { isAccessible = true }
    private val buildIdentityField = BuildIdentity::class.java
        .getDeclaredField("installed").apply { isAccessible = true }

    @Test
    fun resetReinstallsOneListenerAndPublishesSavedChangesAtTheEventBoundary() {
        val previousPrefs = prefsField.get(null)
        val previousContext = contextField.get(null)
        val previousListener = listenerField.get(null)
        val previousSnapshot = snapshotField.get(null)
        val previousVersion = versionField.getLong(null)
        val previousAvailability = availabilityField.get(null)
        val previousWarningActive = warningField.getBoolean(null)
        val previousRemoteLock = RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote
        val previousBuildIdentity = buildIdentityField.get(null)
        val build = ModuleBuildInfo("test", 1, 1, debug = true)
        buildIdentityField.set(null, build)

        val user = preferences(
            mutableMapOf(
                "user_settings_version_code" to build.versionCode,
                BottomTabLiquidGlassPreferences.ENABLED to false,
                TabPreferences.KEY_ENABLE_TAB_CUSTOMIZATION to false,
                AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN to true,
                AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to true,
            ),
        )
        val state = TestPreferences(
            mutableMapOf(
                RemoteEnvironmentState.REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE.key to false,
                RemoteEnvironmentState.REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE.key to false,
                RemoteEnvironmentState.PENDING_POST_SCAN_ENVIRONMENT_WARNING.key to false,
            ),
            readMethods = setOf("getBoolean"),
        )
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val context = (unsafe.get(null) as Unsafe).allocateInstance(PreferencesContext::class.java)
            as PreferencesContext
        context.user = user.prefs
        context.moduleState = state.prefs
        try {
            prefsField.set(null, null)
            contextField.set(null, null)
            listenerField.set(null, null)
            snapshotField.set(ConfigManager, SettingsSnapshot.bootstrap().copy(isAutoSignInEnabled = true))
            versionField.setLong(ConfigManager, 7L)
            availabilityField.set(ConfigManager, emptyMap<String, ConfigManager.ScanFeatureAvailabilityState>())
            warningField.setBoolean(null, false)
            RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote = false

            ConfigManager.resetRuntimeAfterUserDataClear(context)

            assertTrue(ConfigManager.snapshotVersion() > 7L)
            assertFalse(ConfigManager.snapshot().isAutoSignInEnabled)
            assertFalse(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
            assertEquals(1, user.listenerCount)
            assertSame(context, ConfigManager.getAppContext())

            ConfigManager.applyScanAvailability(
                context,
                mapOf(HookFeatureKey.AUTO_SIGN_IN to HookFeatureStatus(state = HookFeatureState.PARTIAL)),
                refreshRuntime = true,
            )
            assertEquals(
                ConfigManager.ScanFeatureAvailabilityState.PARTIAL,
                ConfigManager.getScanFeatureAvailabilityState(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN),
            )
            assertTrue(ConfigManager.snapshot().isAutoSignInEnabled)

            val published = ConfigManager.snapshot()
            val publishedVersion = ConfigManager.snapshotVersion()
            user.prefs.edit().putBoolean(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN, false).apply()
            assertSame(published, ConfigManager.snapshot())
            assertEquals(publishedVersion, ConfigManager.snapshotVersion())

            user.notifyChanged(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN)
            assertNotSame(published, ConfigManager.snapshot())
            assertFalse(ConfigManager.snapshot().isAutoSignInEnabled)
            assertEquals(publishedVersion + 1, ConfigManager.snapshotVersion())

            ConfigManager.applyScanAvailability(
                context,
                mapOf(HookFeatureKey.AUTO_SIGN_IN to HookFeatureStatus(state = HookFeatureState.DISABLED)),
                refreshRuntime = true,
            )
            assertFalse(ConfigManager.isScanFeatureAvailable(AccountPreferences.KEY_ENABLE_AUTO_SIGN_IN))
            assertFalse(ConfigManager.snapshot().isAutoSignInEnabled)
        } finally {
            prefsField.set(null, previousPrefs)
            contextField.set(null, previousContext)
            listenerField.set(null, previousListener)
            snapshotField.set(null, previousSnapshot)
            versionField.setLong(null, previousVersion)
            availabilityField.set(null, previousAvailability)
            warningField.setBoolean(null, previousWarningActive)
            RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote = previousRemoteLock
            buildIdentityField.set(null, previousBuildIdentity)
        }
    }

    private fun preferences(values: MutableMap<String, Any>): TestPreferences<Any> = TestPreferences(
        values,
        readMethods = setOf("getBoolean", "getInt", "getString", "getAll"),
        editorMethods = setOf("putBoolean", "putInt", "remove", "apply"),
    )

    private class PreferencesContext : ContextWrapper(null) {
        lateinit var user: SharedPreferences
        lateinit var moduleState: SharedPreferences

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = when (name) {
            ConfigManager.USER_SETTINGS_PREFS_NAME -> user
            ConfigManager.MODULE_STATE_PREFS_NAME -> moduleState
            else -> error("Unexpected preferences: $name")
        }
    }
}
