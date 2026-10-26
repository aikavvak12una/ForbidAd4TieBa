package com.forbidad4tieba.hook.ui

import android.view.View
import com.forbidad4tieba.hook.symbol.model.SettingsMenuSymbols
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.settings.SettingsMenuController.showModuleSettingsDialog
import com.forbidad4tieba.hook.utils.ViewExt

internal object SettingsMenuHook {
    internal fun hook(
        cl: ClassLoader,
        targets: SettingsMenuSymbols,
        onSettingsController: (Any?) -> Unit = {},
    ) {
        val mod = XposedCompat.module ?: return
        val method = targets.initMethod
        try {
            RuntimeHooks.builder(mod, method, "SettingsMenuHook", "hook:method").intercept { chain ->
                val result = chain.proceed()
                val settingsOwner = chain.thisObject
                onSettingsController(settingsOwner)
                XposedCompat.logD("[SettingsMenuHook] > settings init intercepted")
                val context = (chain.args.firstOrNull() as? View)?.context
                try {
                    val settingsContainer = settingsOwner?.let { targets.containerField.get(it) as? View }
                    if (settingsContainer != null && context != null && ViewExt.markSettingsLongPressBound(settingsContainer)) {
                        settingsContainer.setOnLongClickListener {
                            onSettingsController(settingsOwner)
                            showModuleSettingsDialog(settingsContainer.context ?: context, cl)
                            true
                        }
                        XposedCompat.logD("[SettingsMenuHook] > long-press listener bound")
                    }
                } catch (t: Throwable) { XposedCompat.logD { "SettingsMenuHook: ${t.message}" } }
                result
            }
            XposedCompat.log("[SettingsMenuHook] hook INSTALLED: ${method.declaringClass.name}.${method.name}")
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED (${method.declaringClass.name}.${method.name}): ${t.message}")
            XposedCompat.log(t)
        }
    }

}
