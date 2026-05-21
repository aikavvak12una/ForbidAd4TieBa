package com.forbidad4tieba.hook.ui

import android.app.Activity
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface

/**
 * 一次性安装器。
 * 模块启动后如果没有可用符号缓存，目标应用 Activity 第一次进入 onResume 时显示初始符号扫描弹窗。
 *
 * 弹窗显示一次后，移除这里安装的所有 onResume hook，后续不再承担这部分开销。
 */
internal object InitialScanDialogInstaller {
    @Volatile private var hookInstalled = false
    @Volatile private var dialogShown = false
    @Volatile private var resumeUnhooks: Array<XposedInterface.HookHandle>? = null

    fun ensureInstalled(
        classLoader: ClassLoader,
        onFirstResume: (Activity, ClassLoader) -> Unit,
    ) {
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
                        val shouldShow = synchronized(this@InitialScanDialogInstaller) {
                            if (dialogShown) false else {
                                dialogShown = true
                                true
                            }
                        }
                        if (shouldShow) {
                            unhookAll()
                            onFirstResume(activity, classLoader)
                        }
                    }
                    result
                }
                handles.add(handle)
            }
            resumeUnhooks = handles.toTypedArray()
        } catch (t: Throwable) {
            XposedCompat.log("[InitialScanDialogInstaller] install FAILED: ${t.message}")
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
                XposedCompat.logD { "InitialScanDialogInstaller: ${t.message}" }
            }
        }
    }
}
