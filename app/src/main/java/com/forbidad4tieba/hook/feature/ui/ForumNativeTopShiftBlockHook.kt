package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ForumNativeTopShiftSymbols
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ForumNativeTopShiftBlockHook {
    private const val NATIVE_COLLAPSED_MODE = 1
    private const val NATIVE_PARTIAL_EXPANDED_MODE = 2
    private const val NATIVE_FULLY_EXPANDED_MODE = 3
    private const val NATIVE_WEB_HEIGHT_INIT_STATE = 2
    private const val TERMINAL_STATE_RECHECK_MS = 32L
    private const val NATIVE_STATE_TIMEOUT_MS = 2_000L
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    private data class DeferredInitScroll(
        val originalScrollY: Int,
        val showBounceGuide: Boolean,
        val completion: Any?,
    )

    private class PendingNativeExpansion {
        var layoutReady = false
        var deferredInitScroll: DeferredInitScroll? = null
        var layoutListener: View.OnLayoutChangeListener? = null
        var attachListener: View.OnAttachStateChangeListener? = null
        var timeoutRunnable: Runnable? = null
    }

    internal fun hook(targets: ForumNativeTopShiftSymbols) {
        val mod = XposedCompat.module ?: return
        val method = targets.initScrollMethod

        try {
            if (!installHook(mod, targets)) {
                XposedCompat.logD(
                    "[ForumNativeTopShiftBlockHook] already installed: " +
                        ReflectionUtils.methodSignature(method),
                )
                return
            }
            XposedCompat.log(
                "[ForumNativeTopShiftBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.{${targets.setupMethod.name}" +
                    "(Int, Int, Int, Boolean),${method.name}(Int, Boolean, Function0)} -> " +
                    "native mode=$NATIVE_FULLY_EXPANDED_MODE / " +
                    "${targets.maxScrollGetterMethod.name}() terminal state",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ForumNativeTopShiftBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installHook(
        mod: io.github.libxposed.api.XposedModule,
        targets: ForumNativeTopShiftSymbols,
    ): Boolean {
        val nativeScrollChangedMethod =
            View::class.java.getDeclaredMethod(
                "onScrollChanged",
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
            ).apply {
                isAccessible = true
            }
        val methodKey = ReflectionUtils.methodSignature(targets.initScrollMethod)
        if (!installedMethodKeys.add(methodKey)) return false

        // Host setup, init-scroll, and layout callbacks all run on the View thread.
        val pendingNativeExpansionTargets =
            WeakHashMap<View, PendingNativeExpansion>()
        val terminalStateSyncFailureLogged = AtomicBoolean()

        // Install the pass-through hook first so a later setup-hook failure stays fail closed.
        val initScrollHook = try {
            mod.hook(targets.initScrollMethod).intercept { chain ->
                val targetView = chain.thisObject as? View ?: return@intercept chain.proceed()
                val pendingExpansion =
                    pendingNativeExpansionTargets[targetView]
                        ?: return@intercept chain.proceed()
                val originalScrollY =
                    chain.args.getOrNull(0) as? Int
                val showBounceGuide =
                    chain.args.getOrNull(1) as? Boolean
                if (originalScrollY == null || showBounceGuide == null) {
                    finishPendingNativeExpansion(
                        targetView = targetView,
                        pendingExpansion = pendingExpansion,
                        pendingTargets = pendingNativeExpansionTargets,
                    )
                    return@intercept chain.proceed()
                }
                val completion = chain.args.getOrNull(2)
                pendingExpansion.deferredInitScroll =
                    DeferredInitScroll(
                        originalScrollY = originalScrollY,
                        showBounceGuide = showBounceGuide,
                        completion = completion,
                    )
                replayDeferredNativeInit(
                    targets = targets,
                    targetView = targetView,
                    pendingExpansion = pendingExpansion,
                    pendingTargets = pendingNativeExpansionTargets,
                    nativeScrollChangedMethod = nativeScrollChangedMethod,
                    failureLogged = terminalStateSyncFailureLogged,
                )
                null
            }
        } catch (t: Throwable) {
            installedMethodKeys.remove(methodKey)
            throw t
        }

        try {
            mod.hook(targets.setupMethod).intercept { chain ->
                val targetView = chain.thisObject as? View ?: return@intercept chain.proceed()
                val originalMode = chain.args.getOrNull(0) as? Int
                if (originalMode != NATIVE_COLLAPSED_MODE &&
                    originalMode != NATIVE_PARTIAL_EXPANDED_MODE
                ) {
                    return@intercept chain.proceed()
                }
                val smoothInit = readSmoothInit(targets, targetView, terminalStateSyncFailureLogged)
                if (smoothInit != NATIVE_WEB_HEIGHT_INIT_STATE) {
                    return@intercept chain.proceed()
                }

                val args = chain.args.toTypedArray()
                args[0] = NATIVE_FULLY_EXPANDED_MODE
                fallbackPendingNativeInit(
                    targets = targets,
                    targetView = targetView,
                    pendingExpansion = pendingNativeExpansionTargets[targetView],
                    pendingTargets = pendingNativeExpansionTargets,
                    failureLogged = terminalStateSyncFailureLogged,
                )
                val pendingExpansion = PendingNativeExpansion()
                pendingNativeExpansionTargets[targetView] = pendingExpansion
                val terminalStateListener = object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        view: View,
                        left: Int,
                        top: Int,
                        right: Int,
                        bottom: Int,
                        oldLeft: Int,
                        oldTop: Int,
                        oldRight: Int,
                        oldBottom: Int,
                    ) {
                        pendingExpansion.layoutReady = true
                        replayDeferredNativeInit(
                            targets = targets,
                            targetView = view,
                            pendingExpansion = pendingExpansion,
                            pendingTargets = pendingNativeExpansionTargets,
                            nativeScrollChangedMethod = nativeScrollChangedMethod,
                            failureLogged = terminalStateSyncFailureLogged,
                        )
                    }
                }
                val detachListener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) = Unit

                    override fun onViewDetachedFromWindow(view: View) {
                        fallbackPendingNativeInit(
                            targets = targets,
                            targetView = view,
                            pendingExpansion = pendingExpansion,
                            pendingTargets = pendingNativeExpansionTargets,
                            failureLogged = terminalStateSyncFailureLogged,
                        )
                    }
                }
                val timeoutFallback = Runnable {
                    fallbackPendingNativeInit(
                        targets = targets,
                        targetView = targetView,
                        pendingExpansion = pendingExpansion,
                        pendingTargets = pendingNativeExpansionTargets,
                        failureLogged = terminalStateSyncFailureLogged,
                    )
                }
                pendingExpansion.layoutListener = terminalStateListener
                pendingExpansion.attachListener = detachListener
                pendingExpansion.timeoutRunnable = timeoutFallback
                try {
                    targetView.addOnLayoutChangeListener(terminalStateListener)
                    targetView.addOnAttachStateChangeListener(detachListener)
                    targetView.postDelayed(timeoutFallback, NATIVE_STATE_TIMEOUT_MS)
                    chain.proceed(args)
                } catch (t: Throwable) {
                    fallbackPendingNativeInit(
                        targets = targets,
                        targetView = targetView,
                        pendingExpansion = pendingExpansion,
                        pendingTargets = pendingNativeExpansionTargets,
                        failureLogged = terminalStateSyncFailureLogged,
                    )
                    throw t
                }
            }
        } catch (t: Throwable) {
            var initScrollUnhooked = false
            try {
                initScrollHook.unhook()
                initScrollUnhooked = true
            } catch (rollbackFailure: Throwable) {
                t.addSuppressed(rollbackFailure)
            }
            if (initScrollUnhooked) {
                installedMethodKeys.remove(methodKey)
            }
            throw t
        }
        return true
    }

    private fun replayDeferredNativeInit(
        targets: ForumNativeTopShiftSymbols,
        targetView: View,
        pendingExpansion: PendingNativeExpansion,
        pendingTargets: MutableMap<View, PendingNativeExpansion>,
        nativeScrollChangedMethod: java.lang.reflect.Method,
        failureLogged: AtomicBoolean,
    ) {
        if (!pendingExpansion.layoutReady) return
        if (pendingTargets[targetView] !== pendingExpansion) {
            clearPendingCallbacks(targetView, pendingExpansion)
            return
        }
        val deferredInitScroll = pendingExpansion.deferredInitScroll ?: return

        val fullyExpandedTarget = try {
            targets.maxScrollGetterMethod.invoke(targetView) as? Int
        } catch (t: Throwable) {
            logTerminalStateFailureOnce(
                failureLogged,
                "native max-scroll read FAILED: ${t.message}",
            )
            null
        }
        if (fullyExpandedTarget == null) {
            fallbackPendingNativeInit(
                targets = targets,
                targetView = targetView,
                pendingExpansion = pendingExpansion,
                pendingTargets = pendingTargets,
                failureLogged = failureLogged,
            )
            return
        }
        if (
            !finishPendingNativeExpansion(
                targetView = targetView,
                pendingExpansion = pendingExpansion,
                pendingTargets = pendingTargets,
            )
        ) {
            return
        }
        try {
            targets.initScrollMethod.invoke(
                targetView,
                fullyExpandedTarget,
                deferredInitScroll.showBounceGuide,
                deferredInitScroll.completion,
            )
        } catch (t: Throwable) {
            logTerminalStateFailureOnce(
                failureLogged,
                "native init-scroll replay FAILED: ${t.message}",
            )
            return
        }

        val deadline = android.os.SystemClock.uptimeMillis() + NATIVE_STATE_TIMEOUT_MS
        val terminalStateSync = object : Runnable {
            override fun run() {
                if (!targetView.isAttachedToWindow) return
                val smoothInit = readSmoothInit(targets, targetView, failureLogged) ?: return
                if (smoothInit == NATIVE_WEB_HEIGHT_INIT_STATE) {
                    if (android.os.SystemClock.uptimeMillis() >= deadline) {
                        logTerminalStateFailureOnce(
                            failureLogged,
                            "native terminal state sync FAILED: smooth-init timeout",
                        )
                        return
                    }
                    targetView.postDelayed(this, TERMINAL_STATE_RECHECK_MS)
                    return
                }
                try {
                    nativeScrollChangedMethod.invoke(
                        targetView,
                        targetView.scrollX,
                        targetView.scrollY,
                        targetView.scrollX,
                        targetView.scrollY,
                    )
                } catch (t: Throwable) {
                    logTerminalStateFailureOnce(
                        failureLogged,
                        "native terminal state sync FAILED: ${t.message}",
                    )
                }
            }
        }
        targetView.postOnAnimation(terminalStateSync)
    }

    private fun fallbackPendingNativeInit(
        targets: ForumNativeTopShiftSymbols,
        targetView: View,
        pendingExpansion: PendingNativeExpansion?,
        pendingTargets: MutableMap<View, PendingNativeExpansion>,
        failureLogged: AtomicBoolean,
    ) {
        pendingExpansion ?: return
        val deferredInitScroll = pendingExpansion.deferredInitScroll
        if (
            !finishPendingNativeExpansion(
                targetView = targetView,
                pendingExpansion = pendingExpansion,
                pendingTargets = pendingTargets,
            ) ||
            deferredInitScroll == null
        ) {
            return
        }
        try {
            targets.initScrollMethod.invoke(
                targetView,
                deferredInitScroll.originalScrollY,
                deferredInitScroll.showBounceGuide,
                deferredInitScroll.completion,
            )
        } catch (t: Throwable) {
            logTerminalStateFailureOnce(
                failureLogged,
                "native init-scroll fallback FAILED: ${t.message}",
            )
        }
    }

    private fun finishPendingNativeExpansion(
        targetView: View,
        pendingExpansion: PendingNativeExpansion,
        pendingTargets: MutableMap<View, PendingNativeExpansion>,
    ): Boolean {
        if (pendingTargets[targetView] !== pendingExpansion) {
            clearPendingCallbacks(targetView, pendingExpansion)
            return false
        }
        pendingTargets.remove(targetView)
        clearPendingCallbacks(targetView, pendingExpansion)
        return true
    }

    private fun clearPendingCallbacks(
        targetView: View,
        pendingExpansion: PendingNativeExpansion,
    ) {
        pendingExpansion.layoutListener?.let(targetView::removeOnLayoutChangeListener)
        pendingExpansion.attachListener?.let(targetView::removeOnAttachStateChangeListener)
        pendingExpansion.timeoutRunnable?.let(targetView::removeCallbacks)
        pendingExpansion.layoutListener = null
        pendingExpansion.attachListener = null
        pendingExpansion.timeoutRunnable = null
    }

    private fun readSmoothInit(
        targets: ForumNativeTopShiftSymbols,
        target: View,
        failureLogged: AtomicBoolean,
    ): Int? = try {
        targets.smoothInitGetterMethod.invoke(target) as? Int
    } catch (t: Throwable) {
        logTerminalStateFailureOnce(
            failureLogged,
            "smooth-init read FAILED: ${t.message}",
        )
        null
    }

    private fun logTerminalStateFailureOnce(
        failureLogged: AtomicBoolean,
        message: String,
    ) {
        if (failureLogged.compareAndSet(false, true)) {
            XposedCompat.log("[ForumNativeTopShiftBlockHook] $message")
        }
    }
}
