package com.forbidad4tieba.hook.ui.settings

import android.widget.SeekBar

/** Touch gestures commit once on release; keyboard/accessibility actions commit immediately. */
internal class SettingsSliderChangeListener(
    private val onValueChanged: (Int) -> Unit,
    private val onValueCommitted: (Int) -> Unit,
) : SeekBar.OnSeekBarChangeListener {
    private var trackingTouch = false
    private var pendingProgress: Int? = null

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        onValueChanged(progress)
        if (!fromUser) return
        if (trackingTouch) {
            pendingProgress = progress
        } else {
            onValueCommitted(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        trackingTouch = true
        pendingProgress = null
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        trackingTouch = false
        val progress = pendingProgress
        pendingProgress = null
        if (progress != null) onValueCommitted(progress)
    }
}
