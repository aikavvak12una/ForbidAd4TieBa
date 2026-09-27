package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshContractTest {
    private fun core(gesture: String? = null) = buildHookSymbols {
        this[AutoRefreshContract.autoRefreshTriggerMethod] = "trigger"
        this[AutoRefreshContract.autoRefreshNetRequestMethod] = "request"
        this[AutoRefreshContract.autoRefreshCacheRestoreMethod] = "restore"
        this[AutoRefreshContract.autoRefreshPullGestureMethod] = gesture
    }

    @Test fun missingGestureOnlyReducesItsOwnOptionalCapability() {
        val without = core()
        val with = core("renamed")
        val missingStatus = HookFeatureStatusDeriver.derive(without)
        val foundStatus = HookFeatureStatusDeriver.derive(with)
        assertEquals(HookFeatureState.PARTIAL, missingStatus.getValue(HookFeatureKey.DISABLE_AUTO_REFRESH).state)
        assertEquals(HookFeatureState.FULL, foundStatus.getValue(HookFeatureKey.DISABLE_AUTO_REFRESH).state)
        assertEquals(missingStatus - HookFeatureKey.DISABLE_AUTO_REFRESH, foundStatus - HookFeatureKey.DISABLE_AUTO_REFRESH)
        val point = AutoRefreshContract.points(without).single { it.name == "AutoRefreshHook.PullGesture" }
        assertEquals(HookPointState.OPTIONAL, point.state)
        assertFalse(point.isUnavailable())
    }

    @Test fun aGestureAloneCannotEnableTheRefreshBlocker() {
        val symbols = buildHookSymbols { this[AutoRefreshContract.autoRefreshPullGestureMethod] = "renamed" }
        assertEquals(HookFeatureState.DISABLED,
            AutoRefreshContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_AUTO_REFRESH).state)
    }

    @Test fun cachedGestureRoundTripsAsADescriptor() {
        val original = core("renamed")
        val restored = HookSymbols.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals("renamed", restored?.get(AutoRefreshContract.autoRefreshPullGestureMethod))
    }

    @Test fun restorationRequiresTheSavedNameAndExactSignature() {
        val loader = javaClass.classLoader!!
        fun symbols(name: String?) = buildHookSymbols { this[AutoRefreshContract.autoRefreshPullGestureMethod] = name }
        assertTrue(AutoRefreshContract.isCacheValid(symbols(null), loader))
        assertTrue(AutoRefreshContract.isCacheValid(symbols("renamed"), loader))
        listOf("missing", "F", "wrongParameter", "wrongReturn", "staticGesture").forEach { name ->
            assertFalse(name, AutoRefreshContract.isCacheValid(symbols(name), loader))
        }
    }
}
