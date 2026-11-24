package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

class RemoteEnvironmentStateTest {
    private val prefsField = ConfigManager::class.java.getDeclaredField("prefs").apply { isAccessible = true }
    private val warningField = RemoteEnvironmentState::class.java
        .getDeclaredField("environmentWarningDialogActive").apply { isAccessible = true }
    private var previousPrefs: SharedPreferences? = null
    private var previousWarningActive = false
    private var previousUnlockBlocked = false

    @Before
    fun preservePublishedState() {
        previousPrefs = prefsField.get(null) as? SharedPreferences
        previousWarningActive = warningField.getBoolean(null)
        previousUnlockBlocked = RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote
        warningField.setBoolean(null, false)
        RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote = false
    }

    @After
    fun restorePublishedState() {
        prefsField.set(null, previousPrefs)
        warningField.setBoolean(null, previousWarningActive)
        RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote = previousUnlockBlocked
    }

    @Test
    fun ordinaryUserRetainsThePendingWarningLifecycle() {
        val fixture = fixture(unlocked = false)
        assertTrue(RemoteEnvironmentState.shouldShowEnvironmentWarningDialog(fixture.context))
        RemoteEnvironmentState.markPostScanEnvironmentWarningPending(fixture.context)
        assertTrue(RemoteEnvironmentState.hasPendingPostScanEnvironmentWarning(fixture.context))
        assertTrue(RemoteEnvironmentState.consumePendingPostScanEnvironmentWarning(fixture.context))
        assertFalse(RemoteEnvironmentState.hasPendingPostScanEnvironmentWarning(fixture.context))
        assertEquals(2, fixture.state.applies)
    }

    @Test
    fun hiddenUnlockSkipsAcknowledgementAndConsumesAnExistingPendingWarning() {
        val unlocked = fixture(unlocked = true)
        assertFalse(RemoteEnvironmentState.shouldShowEnvironmentWarningDialog(unlocked.context))
        RemoteEnvironmentState.markPostScanEnvironmentWarningPending(unlocked.context)
        assertFalse(unlocked.user.prefs.contains(RemoteEnvironmentState.KEY_ENVIRONMENT_WARNING_ACKNOWLEDGED))
        assertEquals(0, unlocked.user.edits)
        assertEquals(0, unlocked.state.edits)

        val pending = fixture(unlocked = true, pending = true)
        assertFalse(RemoteEnvironmentState.consumePendingPostScanEnvironmentWarning(pending.context))
        assertFalse(RemoteEnvironmentState.hasPendingPostScanEnvironmentWarning(pending.context))
        assertEquals(1, pending.state.applies)
        assertEquals(0, pending.user.edits)
    }

    @Test
    fun acknowledgementAndRemoteLocksRemainDistinctWarningControls() {
        for (storedLock in listOf(false, true)) {
            val fixture = fixture(unlocked = true, unlockBlocked = storedLock)
            RemoteEnvironmentState.restrictedFeatureUnlockBlockedByRemote = !storedLock
            assertFalse(RemoteEnvironmentState.isRestrictedFeaturesUnlocked(fixture.context))
            assertTrue(RemoteEnvironmentState.shouldShowEnvironmentWarningDialog(fixture.context))
            RemoteEnvironmentState.markPostScanEnvironmentWarningPending(fixture.context)
            assertTrue(RemoteEnvironmentState.hasPendingPostScanEnvironmentWarning(fixture.context))
        }

        val acknowledged = fixture(unlocked = false, acknowledged = true)
        assertFalse(RemoteEnvironmentState.shouldShowEnvironmentWarningDialog(acknowledged.context))
        RemoteEnvironmentState.markPostScanEnvironmentWarningPending(acknowledged.context)
        assertFalse(RemoteEnvironmentState.hasPendingPostScanEnvironmentWarning(acknowledged.context))
        assertEquals(0, acknowledged.state.edits)
    }

    private fun fixture(
        unlocked: Boolean,
        acknowledged: Boolean? = null,
        pending: Boolean = false,
        warningActive: Boolean = true,
        unlockBlocked: Boolean = false,
    ): Fixture {
        val userValues = linkedMapOf<String, Any>(
            AccountPreferences.KEY_RESTRICTED_FEATURES_UNLOCKED to unlocked,
        )
        acknowledged?.let { userValues[RemoteEnvironmentState.KEY_ENVIRONMENT_WARNING_ACKNOWLEDGED] = it }
        val user = preferences(userValues)
        val state = preferences(linkedMapOf(
            RemoteEnvironmentState.REMOTE_ENVIRONMENT_WARNING_DIALOG_ACTIVE.key to warningActive,
            RemoteEnvironmentState.REMOTE_RESTRICTED_FEATURES_LOCK_ACTIVE.key to unlockBlocked,
            RemoteEnvironmentState.PENDING_POST_SCAN_ENVIRONMENT_WARNING.key to pending,
        ))
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val context = (unsafeField.get(null) as Unsafe).allocateInstance(PreferencesContext::class.java)
            as PreferencesContext
        context.moduleStatePrefs = state.prefs
        prefsField.set(null, user.prefs)
        return Fixture(context, user, state)
    }

    private fun preferences(values: MutableMap<String, Any>) = TestPreferences(
        values,
        readMethods = setOf("getBoolean"),
        editorMethods = setOf("putBoolean", "apply"),
    )

    private class PreferencesContext : ContextWrapper(null) {
        lateinit var moduleStatePrefs: SharedPreferences

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            check(name == ConfigManager.MODULE_STATE_PREFS_NAME) { "Unexpected preferences: $name" }
            return moduleStatePrefs
        }
    }

    private data class Fixture(
        val context: Context,
        val user: TestPreferences<Any>,
        val state: TestPreferences<Any>,
    )
}
