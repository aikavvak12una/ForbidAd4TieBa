package com.forbidad4tieba.hook.feature.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSideBarWebBlockHookTest {
    @Test fun styleInstallationIsIdempotentAndDoesNotWaitForThePageRoot() {
        val script = HomeSideBarWebBlockHook.HIDE_SIDE_BAR_BLOCKS_JS
        assertTrue(script.contains("if (document.getElementById(styleId)) return true;"))
        assertTrue(script.contains("document.head || document.documentElement"))
        assertTrue(script.contains("if (!host) return false;"))
        assertTrue(script.contains("return ensureStyle();"))
        assertFalse(script.contains("querySelector"))
    }

    @Test fun persistentScopedCssDoesNotRegisterMutationOrTimerWork() {
        val script = HomeSideBarWebBlockHook.HIDE_SIDE_BAR_BLOCKS_JS
        assertTrue(script.contains("var rootSelector = '.main-sidebar';"))
        for (selector in listOf(" .vip-box", " .main-container", " .exchange-task-wrapper", " .normal-box.no-foot-btn-mb")) {
            assertTrue(script.contains("rootSelector + '$selector"))
        }
        assertTrue(script.contains("display:none!important"))
        for (operation in listOf("MutationObserver", "setTimeout", "setInterval", "addEventListener")) {
            assertFalse(script.contains(operation))
        }
    }
}
