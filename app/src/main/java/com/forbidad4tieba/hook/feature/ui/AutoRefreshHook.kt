package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AutoRefreshHook {

    private const val AUTO_REFRESH_BLOCK_WINDOW_MS = 5000L
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val lifecycleCallbackRegistered = AtomicBoolean(false)
    private val foregroundLock = Any()
    private var startedActivityCount = 0
    @Volatile private var autoRefreshBlockUntilUptime = 0L

    fun hook(
        cl: ClassLoader,
        symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols(),
    ) {
        if (!ConfigManager.isAutoRefreshDisabled) {
            XposedCompat.log("[AutoRefreshHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        val triggerMethodName = symbols?.autoRefreshTriggerMethod
        if (triggerMethodName.isNullOrBlank()) {
            XposedCompat.log("[AutoRefreshHook] skipped: missing symbol autoRefreshTriggerMethod")
            return
        }

        val ppvClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl)
        if (ppvClass == null) {
            XposedCompat.log("[AutoRefreshHook] skipped: class ${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS} not found")
            return
        }

        val triggerMethod = XposedCompat.findMethodOrNull(ppvClass, triggerMethodName)
        if (triggerMethod == null || !isVoidNoArgInstanceMethod(triggerMethod)) {
            XposedCompat.log(
                "[AutoRefreshHook] skipped: invalid target " +
                    "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.$triggerMethodName()",
            )
            return
        }
        val lifecycleGuardInstalled = installForegroundLifecycleGuard()
        markAutoRefreshBlockWindow()
        triggerMethod.isAccessible = true
        if (!installDirectHook(mod, triggerMethod)) {
            XposedCompat.log("[AutoRefreshHook] already installed: ${ReflectionUtils.methodSignature(triggerMethod)}")
            return
        }
        XposedCompat.log(
            "[AutoRefreshHook] hook INSTALLED: " +
                "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${triggerMethod.name}() " +
                "foregroundGuard=$lifecycleGuardInstalled blockWindowMs=$AUTO_REFRESH_BLOCK_WINDOW_MS",
        )
    }

    private fun installDirectHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            if (isInAutoRefreshBlockWindow()) {
                XposedCompat.logD {
                    "[AutoRefreshHook] blocked: ${method.name}() " +
                        "remainingMs=${autoRefreshBlockUntilUptime - SystemClock.uptimeMillis()}"
                }
                return@intercept null
            }
            chain.proceed()
        }
        return true
    }

    /**
     * 使用 Application.registerActivityLifecycleCallbacks，
     * 不直接 hook Activity.onStart 和 onStop。
     * 这样可以避免全应用 Activity 生命周期调用都经过 Xposed hook。
     */
    private fun installForegroundLifecycleGuard(): Boolean {
        if (!lifecycleCallbackRegistered.compareAndSet(false, true)) return false
        val app = ConfigManager.getAppContext() as? Application
        if (app == null) {
            lifecycleCallbackRegistered.set(false)
            return false
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                this@AutoRefreshHook.onActivityStarted()
            }
            override fun onActivityStopped(activity: Activity) {
                this@AutoRefreshHook.onActivityStopped()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        return true
    }

    private fun onActivityStarted() {
        synchronized(foregroundLock) {
            if (startedActivityCount == 0) {
                markAutoRefreshBlockWindowLocked()
            }
            startedActivityCount++
        }
    }

    private fun onActivityStopped() {
        synchronized(foregroundLock) {
            if (startedActivityCount > 0) {
                startedActivityCount--
            } else {
                startedActivityCount = 0
            }
        }
    }

    private fun markAutoRefreshBlockWindow() {
        synchronized(foregroundLock) {
            markAutoRefreshBlockWindowLocked()
        }
    }

    private fun markAutoRefreshBlockWindowLocked() {
        val until = SystemClock.uptimeMillis() + AUTO_REFRESH_BLOCK_WINDOW_MS
        if (until > autoRefreshBlockUntilUptime) autoRefreshBlockUntilUptime = until
    }

    private fun isInAutoRefreshBlockWindow(): Boolean {
        return SystemClock.uptimeMillis() <= autoRefreshBlockUntilUptime
    }

    private fun isVoidNoArgInstanceMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        return method.parameterTypes.isEmpty()
    }

}
