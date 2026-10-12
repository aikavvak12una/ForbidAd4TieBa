package com.forbidad4tieba.hook.feature.perf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PbPreloadBodyContinuityTest {
    private class Body(var web: Boolean)

    private fun PbPreloadBodyContinuity.accept(page: Any, body: Body, tid: String = "thread", preload: Boolean = false, supported: Boolean = true, postId: Long = 1L) =
        accept(page, body, tid, postId, preload, body.web, supported) { body.web = false }

    @Test fun nativeDecisionCarriesAcrossPreviewFirstPageAndNetworkModels() {
        val state = PbPreloadBodyContinuity()
        val page = Any()
        val preview = Body(false)
        assertTrue(state.accept(page, preview, preload = true))
        for (body in listOf(Body(true), Body(true))) {
            assertTrue(state.accept(page, body))
            assertFalse(body.web)
            assertTrue(state.isNative(body))
        }
    }

    @Test fun completeHybridCacheAndNetworkOnlyPagesKeepHostBodyChoice() {
        val state = PbPreloadBodyContinuity()
        for (preload in listOf(false, true)) {
            val body = Body(true)
            assertFalse(state.accept(Any(), body, preload = preload))
            assertTrue(body.web)
            assertFalse(state.isNative(body))
        }
    }

    @Test fun simultaneousPagesForSameThreadDoNotShareTheirRendererChoice() {
        val state = PbPreloadBodyContinuity()
        val nativePage = Any()
        assertTrue(state.accept(nativePage, Body(false), preload = true))
        val otherPage = Body(true)
        assertFalse(state.accept(Any(), otherPage))
        assertTrue(otherPage.web)
        assertTrue(state.accept(nativePage, Body(true)))
    }

    @Test fun changedThreadOrUnsupportedBodyEndsThePageDecision() {
        val state = PbPreloadBodyContinuity()
        val page = Any()
        assertTrue(state.accept(page, Body(false), preload = true))
        assertFalse(state.accept(page, Body(true), tid = "other"))
        assertFalse(state.accept(page, Body(true)))
        assertTrue(state.accept(page, Body(false), preload = true))
        assertFalse(state.accept(page, Body(true), postId = 2L))
        assertFalse(state.accept(page, Body(true)))
        assertTrue(state.accept(page, Body(false), preload = true))
        val special = Body(true)
        assertFalse(state.accept(page, special, supported = false))
        assertTrue(special.web)
        assertFalse(state.accept(page, Body(true)))
    }

    @Test fun failedModelUpdateDoesNotPublishNativeRenderer() {
        val state = PbPreloadBodyContinuity()
        val page = Any()
        val model = Any()
        try {
            state.accept(page, model, "thread", 1L, true, false, true) { error("write failed") }
            throw AssertionError("expected failure")
        } catch (_: IllegalStateException) {
            assertFalse(state.isNative(model))
            assertFalse(state.accept(page, Body(true)))
        }
    }
}
