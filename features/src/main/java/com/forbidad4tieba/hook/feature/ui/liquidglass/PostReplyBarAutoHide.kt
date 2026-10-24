package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.forbidad4tieba.hook.feature.ui.HomeTabAutoHideStrategy

/** Owns only the reply slot's slide/fade; host row visibility and editor transitions stay native. */
internal class PostReplyBarAutoHide(private val view: View, private val changed: () -> Unit) {
    private data class Native(val alpha: Float, val translationY: Float, val visibility: Int)
    private var native: Native? = null
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private val state = PostReplyAutoHideState()
    val hidden: Boolean get() = state.hidden
    val suspended: Boolean get() = state.hidden && progress == 1f

    fun onScroll(dy: Int) {
        if (dy == 0) return
        val direction = HomeTabAutoHideStrategy.directionOf(dy)
        val threshold = HomeTabAutoHideStrategy.thresholdPx(view, state, direction)
        val target = state.onScroll(dy, SystemClock.uptimeMillis(), threshold) ?: return
        animate(target)
    }

    fun startHidden() {
        state.reset(initiallyHidden = true)
        remember()
        apply(1f)
    }

    fun reset(): Boolean {
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
        val previous = native
        native = null
        progress = 0f
        state.reset()
        if (previous == null) return false
        view.alpha = previous.alpha
        view.translationY = previous.translationY
        view.visibility = previous.visibility
        return true
    }

    private fun remember() {
        if (native == null) native = Native(view.alpha, view.translationY, view.visibility)
    }

    private fun animate(hide: Boolean) {
        remember()
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        val target = if (hide) 1f else 0f
        if (!HomeTabAutoHideStrategy.ANIMATE_SCROLL_ACTIONS || !ValueAnimator.areAnimatorsEnabled()) {
            animator = null
            apply(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = HomeTabAutoHideStrategy.TRANSITION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { apply(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                }
            })
            start()
        }
    }

    private fun apply(value: Float) {
        val original = native ?: return
        progress = value
        val margin = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        view.translationY = original.translationY + (view.height + margin).coerceAtLeast(1) * value
        view.alpha = original.alpha * (1f - value)
        view.visibility = if (value == 1f) View.INVISIBLE else original.visibility
        changed()
    }
}
