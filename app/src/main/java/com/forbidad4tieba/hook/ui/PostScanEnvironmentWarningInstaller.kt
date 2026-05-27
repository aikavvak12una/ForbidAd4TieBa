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
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (hookInstalled) return
            hookInstalled = true
        }
        try {
            val handles = mutableListOf<XposedInterface.HookHandle>()
            for (method in Activity::class.java.declaredMethods) {
                if (method.name != "onResume") continue
                method.isAccessible = true
                val handle = mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.thisObject as? Activity
                    if (
                        activity != null &&
                        activity.packageName == Constants.TARGET_PACKAGE &&
                        !activity.isFinishing
                    ) {
                        val shouldShow = synchronized(this@PostScanEnvironmentWarningInstaller) {
                            if (dialogShown) false else {
                                dialogShown = true
                                true
                            }
                        }
                        if (shouldShow) {
                            unhookAll()
                            onFirstResume(activity)
                        }
                    }
                    result
                }
                handles.add(handle)
            }
            resumeUnhooks = handles.toTypedArray()
        } catch (t: Throwable) {
            XposedCompat.log("[PostScanEnvironmentWarningInstaller] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
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
