package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewTreeObserver
import java.util.Collections
import java.util.WeakHashMap

/** Owns pre-draw registration and frame state; refresh policy stays with the page. */
internal class HomeNativeGlassPreDrawObservers(
    private val schedulePbSubPbLayoutRefresh: (View) -> Unit,
    private val scheduleCardComponentBootstrapRefresh: (View) -> Unit,
    private val ensureCardComponentGlassSafely: (View) -> Unit,
) {
    private val pbSubPbLayoutAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val cardComponentAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    fun installPbSubPbLayoutAttachRefresh(subPbLayout: View) {
        synchronized(pbSubPbLayoutAttachRefreshInstalled) {
            if (pbSubPbLayoutAttachRefreshInstalled.containsKey(subPbLayout)) return
            pbSubPbLayoutAttachRefreshInstalled[subPbLayout] = true
        }
        val frameState = PbSubPbLayoutFrameState()
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (
                subPbLayout.isAttachedToWindow &&
                subPbLayout.background is CardGlassDrawable &&
                updatePbSubPbLayoutFrameState(subPbLayout, frameState)
            ) {
                invalidatePbSubPbLayoutGlassFrame(subPbLayout)
            }
            true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                schedulePbSubPbLayoutRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                frameState.initialized = false
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    }
                }
            }
        }
        runCatching {
            subPbLayout.addOnAttachStateChangeListener(attachListener)
            if (subPbLayout.isAttachedToWindow) {
                subPbLayout.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                schedulePbSubPbLayoutRefresh(subPbLayout)
            }
        }.onFailure {
            pbSubPbLayoutAttachRefreshInstalled.remove(subPbLayout)
            runCatching { subPbLayout.removeOnAttachStateChangeListener(attachListener) }
            runCatching {
                if (subPbLayout.viewTreeObserver.isAlive) {
                    subPbLayout.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }
        }
    }

    private fun updatePbSubPbLayoutFrameState(
        subPbLayout: View,
        state: PbSubPbLayoutFrameState,
    ): Boolean {
        subPbLayout.getLocationInWindow(state.location)
        val x = state.location[0]
        val y = state.location[1]
        val width = subPbLayout.width
        val height = subPbLayout.height
        val changed = state.initialized && (
            state.x != x ||
                state.y != y ||
                state.width != width ||
                state.height != height
            )
        state.initialized = true
        state.x = x
        state.y = y
        state.width = width
        state.height = height
        return changed
    }

    private fun invalidatePbSubPbLayoutGlassFrame(subPbLayout: View) {
        subPbLayout.invalidate()
    }

    fun installCardComponentAttachRefresh(view: View) {
        synchronized(cardComponentAttachRefreshInstalled) {
            if (cardComponentAttachRefreshInstalled.containsKey(view)) return
            cardComponentAttachRefreshInstalled[view] = true
        }
        lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener
        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            runCatching {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }
            if (view.isAttachedToWindow) {
                ensureCardComponentGlassSafely(view)
            }
            true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                scheduleCardComponentBootstrapRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    }
                }
            }
        }
        runCatching {
            view.addOnAttachStateChangeListener(attachListener)
            if (view.isAttachedToWindow) {
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }.onFailure {
            cardComponentAttachRefreshInstalled.remove(view)
            runCatching { view.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private class PbSubPbLayoutFrameState {
        var initialized: Boolean = false
        var x: Int = 0
        var y: Int = 0
        var width: Int = 0
        var height: Int = 0
        val location: IntArray = IntArray(2)
    }
}
