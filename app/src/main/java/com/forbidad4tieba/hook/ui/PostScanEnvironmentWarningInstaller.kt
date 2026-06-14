package com.forbidad4tieba.hook.ui

import android.app.Activity
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface

internal object PostScanEnvironmentWarningInstaller {
    @Volatile private var hookInstalled = false
    @Volatile private var dialogShown = false
    @Volatile private var resumeUnhooks: Array<XposedInterface.HookHandle>? = null

    fun ensureInstalled(onFirstResume: (Activity) -> Unit) {
        if (tryShow(ModuleForegroundActivityTracker.currentActivity(), onFirstResume)) return
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (hookInstalled) return
            hookInstalled = true
        }
        try {
            val method = Activity::class.java.getDeclaredMethod("onResume").apply {
                isAccessible = true
            }
            val handle = mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                tryShow(activity, onFirstResume)
                result
            }
            resumeUnhooks = arrayOf(handle)
            tryShow(ModuleForegroundActivityTracker.currentActivity(), onFirstResume)
        } catch (t: Throwable) {
            synchronized(this) { hookInstalled = false }
            XposedCompat.log("[PostScanEnvironmentWarningInstaller] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryShow(
        activity: Activity?,
        onFirstResume: (Activity) -> Unit,
    ): Boolean {
        if (
            activity == null ||
            activity.packageName != Constants.TARGET_PACKAGE ||
            activity.isFinishing ||
            activity.isDestroyed
        ) {
            return false
        }
        val shouldShow = synchronized(this) {
            if (dialogShown) false else {
                dialogShown = true
                true
            }
        }
        if (!shouldShow) return false
        unhookAll()
        onFirstResume(activity)
        return true
    }

    private fun unhookAll() {
        val handles = resumeUnhooks ?: return
        resumeUnhooks = null
        for (handle in handles) {
            try {
                handle.unhook()
            } catch (t: Throwable) {
                XposedCompat.logD { "PostScanEnvironmentWarningInstaller: ${t.message}" }
            }
        }
    }
}
