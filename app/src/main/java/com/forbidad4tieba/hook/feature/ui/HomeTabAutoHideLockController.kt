package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal object HomeTabAutoHideLockController {
    private const val METHOD_DISPATCH_TOUCH_EVENT = "dispatchTouchEvent"
    private const val METHOD_DISPATCH_KEY_EVENT = "dispatchKeyEvent"
    private const val TOUCH_SLOP_MULTIPLIER = 2
    private const val MIN_GESTURE_DISTANCE_DP = 24
    private const val AXIS_DOMINANCE_RATIO = 1.25f

    private val hooked = AtomicBoolean(false)
    private val gestureStates = Collections.synchronizedMap(WeakHashMap<View, OppositeSwipeState>())

    @Volatile private var lockedHidden = false

    fun hook(cl: ClassLoader): Int {
        if (!ConfigManager.isHomeTabAutoHideEnabled) return 0
        if (!hooked.compareAndSet(false, true)) return 0

        return try {
            val touchHooks = installPersonalizeTouchObserver(cl)
            val backHooks = installBackUnlockObserver(cl)
            val installed = touchHooks + backHooks
            if (installed == 0) {
                hooked.set(false)
                XposedCompat.log("[HomeTabAutoHideLockController] no hooks installed")
            } else {
                XposedCompat.log(
                    "[HomeTabAutoHideLockController] hook INSTALLED: " +
                        "touch=$touchHooks, back=$backHooks"
                )
            }
            installed
        } catch (t: Throwable) {
            hooked.set(false)
            XposedCompat.log("[HomeTabAutoHideLockController] install FAILED: ${t.message}")
            XposedCompat.log(t)
            0
        }
    }

    fun isLockedHidden(): Boolean {
        return lockedHidden && ConfigManager.isHomeTabAutoHideEnabled
    }

    fun unlockForTabSwitch(anchor: View): Boolean {
        return unlock(anchor)
    }

    private fun installPersonalizeTouchObserver(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val pageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.log(
                    "[HomeTabAutoHideLockController] class NOT FOUND: " +
                        StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS
                )
                return 0
            }
        val method = XposedCompat.findMethodOrNull(pageClass, METHOD_DISPATCH_TOUCH_EVENT, MotionEvent::class.java)
            ?: run {
                XposedCompat.log(
                    "[HomeTabAutoHideLockController] method NOT FOUND: " +
                        "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.$METHOD_DISPATCH_TOUCH_EVENT"
                )
                return 0
            }

        mod.hook(method).intercept { chain ->
            val page = chain.thisObject as? View
            val event = chain.args.getOrNull(0) as? MotionEvent
            if (page != null && event != null && ConfigManager.isHomeTabAutoHideEnabled) {
                handlePersonalizeTouch(page, event)
            }
            chain.proceed()
        }
        return 1
    }

    private fun installBackUnlockObserver(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val mainTabClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS, cl)
            ?: run {
                XposedCompat.log(
                    "[HomeTabAutoHideLockController] class NOT FOUND: " +
                        StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS
                )
                return 0
            }
        val method = XposedCompat.findMethodOrNull(mainTabClass, METHOD_DISPATCH_KEY_EVENT, KeyEvent::class.java)
            ?: run {
                XposedCompat.log(
                    "[HomeTabAutoHideLockController] method NOT FOUND: " +
                        "${StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS}.$METHOD_DISPATCH_KEY_EVENT"
                )
                return 0
            }

        mod.hook(method).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args.getOrNull(0) as? KeyEvent
            val page = if (activity != null && event != null && shouldHandleBack(event)) {
                findVisiblePersonalizePage(activity.window?.decorView)
            } else {
                null
            }
            if (page != null && unlock(page)) {
                return@intercept true
            }
            chain.proceed()
        }
        return 1
    }

    private fun shouldHandleBack(event: KeyEvent): Boolean {
        return isLockedHidden() &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.repeatCount == 0
    }

    private fun handlePersonalizeTouch(page: View, event: MotionEvent) {
        val state = gestureStateFor(page)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> state.reset()
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    state.start(page, event)
                } else {
                    state.reset()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.reachedOppositeSwipe(event)) {
                    lockFromGesture(page)
                }
            }
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> state.reset()
        }
    }

    private fun gestureStateFor(page: View): OppositeSwipeState {
        synchronized(gestureStates) {
            return gestureStates[page] ?: OppositeSwipeState().also {
                gestureStates[page] = it
            }
        }
    }

    private fun lockFromGesture(anchor: View): Boolean {
        if (!ConfigManager.isHomeTabAutoHideEnabled) return false

        val topApplied = HomeTopTabAutoHideHook.applyLockedHidden(anchor)
        val bottomApplied = HomeBottomTabAutoHideHook.applyLockedHidden(anchor)
        if (!topApplied && !bottomApplied) return false

        val showToast = !lockedHidden
        lockedHidden = true
        if (showToast) {
            Toast.makeText(
                anchor.context.applicationContext,
                UiText.HomeTabAutoHide.TOAST_LOCKED_HIDDEN,
                Toast.LENGTH_SHORT,
            ).show()
        }
        return true
    }

    private fun unlock(anchor: View): Boolean {
        if (!lockedHidden) return false
        lockedHidden = false
        HomeTopTabAutoHideHook.releaseLockedHidden(anchor)
        HomeBottomTabAutoHideHook.releaseLockedHidden(anchor)
        return true
    }

    private fun findVisiblePersonalizePage(view: View?): View? {
        if (view == null) return null
        if (view.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS && view.isShown) {
            return view
        }
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val found = findVisiblePersonalizePage(group.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    private class OppositeSwipeState {
        private var active = false
        private var triggered = false
        private var pointerId1 = -1
        private var pointerId2 = -1
        private var startX1 = 0f
        private var startY1 = 0f
        private var startX2 = 0f
        private var startY2 = 0f
        private var thresholdPx = 0

        fun start(view: View, event: MotionEvent) {
            pointerId1 = event.getPointerId(0)
            pointerId2 = event.getPointerId(1)
            startX1 = event.getX(0)
            startY1 = event.getY(0)
            startX2 = event.getX(1)
            startY2 = event.getY(1)
            thresholdPx = gestureThresholdPx(view)
            active = true
            triggered = false
        }

        fun reachedOppositeSwipe(event: MotionEvent): Boolean {
            if (!active || triggered || event.pointerCount < 2) return false
            val index1 = event.findPointerIndex(pointerId1)
            val index2 = event.findPointerIndex(pointerId2)
            if (index1 < 0 || index2 < 0) {
                reset()
                return false
            }

            val dx1 = event.getX(index1) - startX1
            val dy1 = event.getY(index1) - startY1
            val dx2 = event.getX(index2) - startX2
            val dy2 = event.getY(index2) - startY2
            val verticalOpposite = isOppositeAxisSwipe(dy1, dx1, dy2, dx2)
            val horizontalOpposite = isOppositeAxisSwipe(dx1, dy1, dx2, dy2)
            if (verticalOpposite || horizontalOpposite) {
                triggered = true
                return true
            }
            return false
        }

        fun reset() {
            active = false
            triggered = false
            pointerId1 = -1
            pointerId2 = -1
        }

        private fun isOppositeAxisSwipe(
            delta1: Float,
            cross1: Float,
            delta2: Float,
            cross2: Float,
        ): Boolean {
            val distance1 = abs(delta1)
            val distance2 = abs(delta2)
            if (distance1 < thresholdPx || distance2 < thresholdPx) return false
            if (distance1 < abs(cross1) * AXIS_DOMINANCE_RATIO) return false
            if (distance2 < abs(cross2) * AXIS_DOMINANCE_RATIO) return false
            return delta1 * delta2 < 0f
        }

        private fun gestureThresholdPx(view: View): Int {
            val bySlop = ViewConfiguration.get(view.context).scaledTouchSlop * TOUCH_SLOP_MULTIPLIER
            val byDensity = (MIN_GESTURE_DISTANCE_DP * view.resources.displayMetrics.density + 0.5f).toInt()
            return maxOf(bySlop, byDensity)
        }
    }
}
