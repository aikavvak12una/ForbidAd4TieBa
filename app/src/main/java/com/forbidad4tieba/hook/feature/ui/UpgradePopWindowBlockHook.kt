package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.os.Bundle
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 屏蔽更新弹窗。
 *
 * 目标应用通过启动 `com.baidu.tieba.UpdateDialog` Activity 展示更新对话框，
 * 对它的 onCreate 做 hook 并立即 finish，阻止弹窗显示。
 *
 * 更新弹窗类 UpdateDialog 是未混淆的稳定类名，无需通过 HookSymbolResolver 解析。
 */
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
