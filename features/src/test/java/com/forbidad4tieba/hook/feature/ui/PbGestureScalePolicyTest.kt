package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.*
import org.junit.Test

class PbGestureScalePolicyTest {
    @Test fun disabledScalePreservesTheExistingDispatchSuppression() {
        assertEquals(false, PbDisableGestureFontScaleHook.dispatchResult(true) { error("gesture dispatch") })
    }

    @Test fun enabledScalePreservesHostReturnAndExceptions() {
        var calls = 0
        assertEquals(false, PbDisableGestureFontScaleHook.dispatchResult(false) { calls++; false })
        assertEquals(true, PbDisableGestureFontScaleHook.dispatchResult(false) { true })
        assertEquals(1, calls)
        assertThrows(IllegalStateException::class.java) {
            PbDisableGestureFontScaleHook.dispatchResult(false) { error("host failure") }
        }
    }
}
