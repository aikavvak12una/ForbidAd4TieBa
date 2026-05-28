package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.os.Bundle
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 灞忚斀鏇存柊寮圭獥銆? *
 * 鐩爣搴旂敤閫氳繃鍚姩 `com.baidu.tieba.UpdateDialog` Activity 灞曠ず鏇存柊瀵硅瘽妗嗭紝
 * 瀵瑰畠鐨?onCreate 鍋?hook 骞剁珛鍗?finish锛岄樆姝㈠脊绐楁樉绀恒€? *
 * 鏇存柊寮圭獥绫?UpdateDialog 鏄湭娣锋穯鐨勭ǔ瀹氱被鍚嶏紝鏃犻渶閫氳繃 HookSymbolResolver 瑙ｆ瀽銆? */
object UpgradePopWindowBlockHook {
    private const val UPDATE_DIALOG_CLASS = "com.baidu.tieba.UpdateDialog"
    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[UpgradePopWindowBlockHook] already installed, skip")
            return
        }
        val mod = XposedCompat.module
        if (mod == null) {
            installed.set(false)
            return
        }

        try {
            val updateDialogClass = cl.loadClass(UPDATE_DIALOG_CLASS)
            val onCreateMethod = updateDialogClass.getDeclaredMethod("onCreate", Bundle::class.java)
            onCreateMethod.isAccessible = true

            mod.hook(onCreateMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    activity.finish()
                    XposedCompat.logD("[UpgradePopWindowBlockHook] UpdateDialog.onCreate -> finish()")
                }
                null
            }

            XposedCompat.log("[UpgradePopWindowBlockHook] hook INSTALLED: $UPDATE_DIALOG_CLASS.onCreate -> finish()")
        } catch (e: ClassNotFoundException) {
            installed.set(false)
            XposedCompat.log("[UpgradePopWindowBlockHook] class NOT FOUND: $UPDATE_DIALOG_CLASS")
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[UpgradePopWindowBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
