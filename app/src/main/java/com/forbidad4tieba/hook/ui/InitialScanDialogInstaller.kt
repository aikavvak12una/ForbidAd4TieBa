package com.forbidad4tieba.hook.ui

import android.app.Activity
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface

/**
 * 涓€娆℃€у畨瑁呭櫒銆? * 妯″潡鍚姩鍚庡鏋滄病鏈夊彲鐢ㄧ鍙风紦瀛橈紝鐩爣搴旂敤 Activity 绗竴娆¤繘鍏?onResume 鏃舵樉绀哄垵濮嬬鍙锋壂鎻忓脊绐椼€? *
 * 寮圭獥鏄剧ず涓€娆″悗锛岀Щ闄よ繖閲屽畨瑁呯殑鎵€鏈?onResume hook锛屽悗缁笉鍐嶆壙鎷呰繖閮ㄥ垎寮€閿€銆? */
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
