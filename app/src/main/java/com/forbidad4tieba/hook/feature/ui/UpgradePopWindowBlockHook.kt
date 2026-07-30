package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks host-initiated update dialogs while preserving user-triggered update checks.
 *
 * The manual path sends UpdateDialogConfig from AboutActivity through the stable
 * BdBaseActivity.sendMessage(Message) API. Marking that config's Intent keeps the runtime
 * UpdateDialog callback lightweight and avoids relying on stack traces or obfuscated symbols.
 */
object UpgradePopWindowBlockHook {
    private const val UPDATE_DIALOG_CLASS = "com.baidu.tieba.UpdateDialog"
    private const val ABOUT_ACTIVITY_CLASS = "com.baidu.tieba.setting.more.AboutActivity"
    private const val MESSAGE_CLASS = "com.baidu.adp.framework.message.Message"
    private const val CUSTOM_MESSAGE_CLASS = "com.baidu.adp.framework.message.CustomMessage"
    private const val UPDATE_DIALOG_CONFIG_CLASS = "com.baidu.tbadk.core.atomData.UpdateDialogConfig"
    private const val MANUAL_UPDATE_CHECK_EXTRA =
        "com.forbidad4tieba.extra.MANUAL_UPDATE_CHECK"

    private val installed = AtomicBoolean(false)
    private val manualExemptionHealthy = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[UpgradePopWindowBlockHook] already installed, skip")
            return
        }
        val mod = XposedCompat.module
        if (mod == null) {
            installed.set(false)
            manualExemptionHealthy.set(false)
            return
        }

        var anyHookInstalled = false
        try {
            val updateDialogClass = cl.loadClass(UPDATE_DIALOG_CLASS)
            val aboutActivityClass = cl.loadClass(ABOUT_ACTIVITY_CLASS)
            val messageClass = cl.loadClass(MESSAGE_CLASS)
            val customMessageClass = cl.loadClass(CUSTOM_MESSAGE_CLASS)
            val updateDialogConfigClass = cl.loadClass(UPDATE_DIALOG_CONFIG_CLASS)

            require(Activity::class.java.isAssignableFrom(updateDialogClass)) {
                "$UPDATE_DIALOG_CLASS is not an Activity"
            }
            require(messageClass.isAssignableFrom(customMessageClass)) {
                "$CUSTOM_MESSAGE_CLASS is not a $MESSAGE_CLASS"
            }

            val onCreateMethod = updateDialogClass.getDeclaredMethod("onCreate", Bundle::class.java)
            val sendMessageMethod =
                aboutActivityClass.getMethod("sendMessage", messageClass)
            val getDataMethod = customMessageClass.getDeclaredMethod("getData")
            val getIntentMethod = updateDialogConfigClass.getMethod("getIntent")

            require(Intent::class.java.isAssignableFrom(getIntentMethod.returnType)) {
                "$UPDATE_DIALOG_CONFIG_CLASS.getIntent return type mismatch"
            }

            onCreateMethod.isAccessible = true
            sendMessageMethod.isAccessible = true
            getDataMethod.isAccessible = true
            getIntentMethod.isAccessible = true

            manualExemptionHealthy.set(true)

            mod.hook(sendMessageMethod).intercept { chain ->
                try {
                    val sender = chain.thisObject
                    val message = chain.args.firstOrNull()
                    if (
                        sender != null &&
                        aboutActivityClass.isInstance(sender) &&
                        message != null &&
                        customMessageClass.isInstance(message)
                    ) {
                        val config = getDataMethod.invoke(message)
                        if (config != null && updateDialogConfigClass.isInstance(config)) {
                            val intent = getIntentMethod.invoke(config) as? Intent
                                ?: error("manual update config Intent unavailable")
                            intent.putExtra(MANUAL_UPDATE_CHECK_EXTRA, true)
                            XposedCompat.logD(
                                "[UpgradePopWindowBlockHook] manual update check marked",
                            )
                        }
                    }
                } catch (t: Throwable) {
                    manualExemptionHealthy.set(false)
                    XposedCompat.logW(
                        "[UpgradePopWindowBlockHook] manual exemption FAILED; " +
                            "update blocking disabled: ${t.message}",
                    )
                }
                chain.proceed()
            }
            anyHookInstalled = true

            mod.hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                val isManualCheck =
                    activity?.intent?.getBooleanExtra(MANUAL_UPDATE_CHECK_EXTRA, false) == true
                if (isManualCheck) {
                    XposedCompat.logD(
                        "[UpgradePopWindowBlockHook] manual UpdateDialog allowed",
                    )
                } else if (activity != null && manualExemptionHealthy.get()) {
                    activity.finish()
                    XposedCompat.logD(
                        "[UpgradePopWindowBlockHook] automatic UpdateDialog.onCreate -> finish()",
                    )
                }
                result
            }

            XposedCompat.log(
                "[UpgradePopWindowBlockHook] hooks INSTALLED: " +
                    "automatic UpdateDialog -> finish, manual check -> allow",
            )
        } catch (e: ClassNotFoundException) {
            installed.set(false)
            manualExemptionHealthy.set(false)
            XposedCompat.log("[UpgradePopWindowBlockHook] class NOT FOUND: ${e.message}")
        } catch (t: Throwable) {
            if (!anyHookInstalled) {
                installed.set(false)
            }
            manualExemptionHealthy.set(false)
            XposedCompat.log("[UpgradePopWindowBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
