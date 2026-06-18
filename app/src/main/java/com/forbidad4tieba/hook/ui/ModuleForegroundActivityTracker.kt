package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

internal object ModuleForegroundActivityTracker {
    private val registered = AtomicBoolean(false)
    @Volatile private var registeredApp: Application? = null
    @Volatile private var registeredCallback: Application.ActivityLifecycleCallbacks? = null
    @Volatile private var resumedActivityRef: WeakReference<Activity>? = null

    fun register(app: Application) {
        if (!registered.compareAndSet(false, true)) return
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                if (isTargetActivity(activity)) {
                    resumedActivityRef = WeakReference(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                clearIfCurrent(activity)
            }

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                clearIfCurrent(activity)
            }
        }
        registeredApp = app
        registeredCallback = callback
        app.registerActivityLifecycleCallbacks(callback)
    }

    fun prepareForHotReload() {
        val app = registeredApp
        val callback = registeredCallback
        if (app != null && callback != null) {
            runCatching {
                app.unregisterActivityLifecycleCallbacks(callback)
            }.onFailure { t ->
                XposedCompat.logW("[ModuleForegroundActivityTracker] unregister failed: ${t.message}")
            }
        }
        registeredApp = null
        registeredCallback = null
        resumedActivityRef = null
        registered.set(false)
    }

    fun currentActivity(): Activity? {
        val activity = resumedActivityRef?.get() ?: return null
        return if (isUsableActivity(activity)) activity else null
    }

    private fun clearIfCurrent(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
    }

    private fun isUsableActivity(activity: Activity): Boolean {
        return isTargetActivity(activity) && !activity.isFinishing && !activity.isDestroyed
    }

    private fun isTargetActivity(activity: Activity): Boolean {
        return activity.packageName == Constants.TARGET_PACKAGE
    }
}
