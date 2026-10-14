package com.forbidad4tieba.hook.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSliderChangeListenerTest {
    @Test fun dragPreviewsEveryValueButSavesOnlyTheReleasedValue() {
        val previews = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({ previews += it }, { saved += it })

        listener.onStartTrackingTouch(null)
        for (value in 1..100) listener.onProgressChanged(null, value, true)
        assertEquals((1..100).toList(), previews)
        assertTrue(saved.isEmpty())
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(100), saved)
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(100), saved)
    }

    @Test fun loadingAndSwitchingModesOnlyUpdatesThePreview() {
        val previews = mutableListOf<Int>()
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({ previews += it }, { saved += it })

        listener.onProgressChanged(null, 18, false)
        listener.onProgressChanged(null, 42, false)
        listener.onStartTrackingTouch(null)
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(18, 42), previews)
        assertTrue(saved.isEmpty())
    }

    @Test fun keyboardAndAccessibilityChangesSaveWithoutATouchRelease() {
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({}, { saved += it })

        listener.onProgressChanged(null, 4, true)
        listener.onProgressChanged(null, 5, true)
        assertEquals(listOf(4, 5), saved)
    }

    @Test fun eachGestureHasItsOwnPendingValue() {
        val saved = mutableListOf<Int>()
        val listener = SettingsSliderChangeListener({}, { saved += it })

        listener.onStartTrackingTouch(null)
        listener.onProgressChanged(null, 40, true)
        listener.onStopTrackingTouch(null)
        listener.onStartTrackingTouch(null)
        listener.onProgressChanged(null, 8, true)
        listener.onProgressChanged(null, 12, true)
        assertEquals(listOf(40), saved)
        listener.onStopTrackingTouch(null)
        assertEquals(listOf(40, 12), saved)
    }
}
