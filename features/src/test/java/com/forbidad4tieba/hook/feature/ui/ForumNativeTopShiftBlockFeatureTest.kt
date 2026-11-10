package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.symbol.contract.ForumTopShiftContract
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumNativeTopShiftBlockFeatureTest {
    @Test fun declaredCapabilityWithMissingRuntimeTargetsReportsFailure() {
        val targetClass = javaClass.name + ".MissingBottomSheet"
        val symbols = symbols(JSONObject()
            .put(ForumTopShiftContract.forumBottomSheetViewClass.cacheKey, targetClass)
            .put(ForumTopShiftContract.forumBottomSheetInitScrollMethod.cacheKey, "initializeScroll"))
        val context = HookInstallContext(Constants.TARGET_PACKAGE, symbols)
        assertTrue(context.available(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT))

        val entries = ForumNativeTopShiftBlockFeature.entries(context, SettingsSnapshot.bootstrap())
        assertEquals(1, entries.size)
        var restorationAttempts = 0
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == targetClass) {
                    restorationAttempts++
                    throw ClassNotFoundException(name)
                }
                return super.loadClass(name, resolve)
            }
        }

        val outcome = entries.single().install(loader)

        assertTrue(restorationAttempts > 0)
        assertNotNull(outcome)
        assertEquals(InstallState.FAILED, outcome!!.state)
        assertEquals(0, outcome.installedCount)
        assertFalse(outcome.reason.isNullOrBlank())
    }

    @Test fun unavailableCapabilityDoesNotPlanAnInstallation() {
        val incomplete = listOf(
            JSONObject(),
            JSONObject().put(ForumTopShiftContract.forumBottomSheetViewClass.cacheKey, javaClass.name),
            JSONObject().put(ForumTopShiftContract.forumBottomSheetInitScrollMethod.cacheKey, "initializeScroll"),
        )
        for (json in incomplete) {
            val context = HookInstallContext(Constants.TARGET_PACKAGE, symbols(json))

            assertFalse(context.available(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT))
            assertTrue(ForumNativeTopShiftBlockFeature.entries(context, SettingsSnapshot.bootstrap()).isEmpty())
        }
    }

    private fun symbols(json: JSONObject): HookSymbols = checkNotNull(HookSymbols.fromJson(json.toString()))
}
