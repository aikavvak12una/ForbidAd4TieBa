package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

internal class HomeNativeGlassRecyclerObservers(
    private val shouldTrackPosition: () -> Boolean,
    private val isFeedCardView: (View) -> Boolean,
    private val onInvalidation: (View) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val installed = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pendingFrame = AtomicReference<FrameRequest?>()

    fun observe(recycler: View) {
        synchronized(installed) {
            if (installed.containsKey(recycler)) return
            installed[recycler] = true
        }
        val binding = Binding(recycler)
        runCatching {
            recycler.addOnAttachStateChangeListener(binding)
            if (recycler.isAttachedToWindow) binding.bind()
        }.onFailure { error ->
            binding.unbind()
            runCatching { recycler.removeOnAttachStateChangeListener(binding) }.onFailure(onFailure)
            installed.remove(recycler)
            onFailure(error)
        }
    }

    private inner class Binding(val view: View) :
        View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener,
        ViewTreeObserver.OnScrollChangedListener {
        private var observer: ViewTreeObserver? = null
        private val state = HomeRecyclerFrameState()

        fun bind() {
            val next = view.viewTreeObserver
            if (observer === next) return
            unbind()
            observer = next
            try {
                next.addOnPreDrawListener(this)
                next.addOnScrollChangedListener(this)
            } catch (error: Throwable) {
                unbind()
                throw error
            }
        }

        fun unbind() {
            cancelFrame(this)
            val previous = observer ?: return
            observer = null
            val registered = if (previous.isAlive) previous else view.viewTreeObserver
            if (registered.isAlive) {
                runCatching { registered.removeOnPreDrawListener(this) }.onFailure(onFailure)
                runCatching { registered.removeOnScrollChangedListener(this) }.onFailure(onFailure)
            }
        }

        override fun onViewAttachedToWindow(v: View) {
            // A detached observer is merged before this callback. Register only here.
            runCatching { bind() }.onFailure(onFailure)
        }

        override fun onViewDetachedFromWindow(v: View) {
            unbind()
        }

        override fun onScrollChanged() {
            if (observer != null && view.isAttachedToWindow) scheduleFrame(this)
        }

        override fun onPreDraw(): Boolean {
            if (
                observer != null && view.isAttachedToWindow &&
                shouldTrackPosition() &&
                updateHomeRecyclerFrameState(view, state)
            ) {
                scheduleFrame(this)
            }
            return true
        }
    }

    private fun scheduleFrame(binding: Binding) {
        if (pendingFrame.get() != null) return
        val request = FrameRequest(binding)
        if (!pendingFrame.compareAndSet(null, request)) return
        runCatching { binding.view.postOnAnimation(request) }.onFailure { error ->
            pendingFrame.compareAndSet(request, null)
            onFailure(error)
        }
    }

    private fun cancelFrame(binding: Binding) {
        val request = pendingFrame.get() ?: return
        if (request.binding !== binding || !pendingFrame.compareAndSet(request, null)) return
        runCatching { binding.view.removeCallbacks(request) }.onFailure(onFailure)
    }

    private inner class FrameRequest(val binding: Binding) : Runnable {
        override fun run() {
            // A removed frame may already have been taken by Choreographer.
            if (!pendingFrame.compareAndSet(this, null)) return
            if (!binding.view.isAttachedToWindow) return
            runCatching { onInvalidation(binding.view) }.onFailure(onFailure)
        }
    }

    private fun updateHomeRecyclerFrameState(recycler: View, state: HomeRecyclerFrameState): Boolean {
        recycler.getLocationInWindow(state.location)
        val recyclerX = state.location[0]
        val recyclerY = state.location[1]
        val trackedChild = findFirstVisibleRecyclerChild(recycler)
        if (trackedChild != null) {
            trackedChild.getLocationInWindow(state.location)
        }
        val firstChildX = if (trackedChild == null) Int.MIN_VALUE else state.location[0]
        val firstChildY = if (trackedChild == null) Int.MIN_VALUE else state.location[1]
        val canScrollUp = recycler.canScrollVertically(-1)
        val changed = state.initialized && (
            state.recyclerX != recyclerX ||
                state.recyclerY != recyclerY ||
                state.firstChildX != firstChildX ||
                state.firstChildY != firstChildY ||
                state.canScrollUp != canScrollUp
            )
        state.initialized = true
        state.recyclerX = recyclerX
        state.recyclerY = recyclerY
        state.firstChildX = firstChildX
        state.firstChildY = firstChildY
        state.canScrollUp = canScrollUp
        return changed
    }

    private fun findFirstVisibleRecyclerChild(recycler: View): View? {
        val group = recycler as? ViewGroup ?: return null
        var firstVisibleChild: View? = null
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (child.visibility != View.VISIBLE || child.width <= 0 || child.height <= 0) continue
            if (firstVisibleChild == null) firstVisibleChild = child
            if (isFeedCardView(child)) return child
        }
        return firstVisibleChild
    }

    private class HomeRecyclerFrameState {
        var initialized: Boolean = false
        var recyclerX: Int = 0
        var recyclerY: Int = 0
        var firstChildX: Int = 0
        var firstChildY: Int = 0
        var canScrollUp: Boolean = false
        val location: IntArray = IntArray(2)
    }
}
