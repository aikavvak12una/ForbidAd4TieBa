package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.MsgTabDefaultNotifySymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

/**
 * Changes the message tab default fallback from chat to notifications while preserving explicit tab
 * selection targets.
 */
object MsgTabDefaultNotifyHook {
    private const val TAB_ID_NOTIFY = -1L
    private const val TAB_ID_CHAT = -2L

    internal fun hook(targets: MsgTabDefaultNotifySymbols) {
        if (!installViewModelStrategy(targets)) {
            XposedCompat.log("[MsgTabDefaultNotifyHook] skipped: ViewModel strategy unavailable")
        }
    }

    private fun installViewModelStrategy(targets: MsgTabDefaultNotifySymbols): Boolean {
        val mod = XposedCompat.module ?: return false
        val locateMethod = targets.locateToTabMethod
        return try {
            mod.hook(locateMethod).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isDefaultNotifyTabEnabled) return@intercept result

                val defaultValue = asLong(chain.args.getOrNull(0)) ?: return@intercept result
                if (defaultValue != TAB_ID_CHAT) return@intercept result

                val extData = (chain.args.getOrNull(1) as? String).orEmpty()
                if (extData.isNotBlank()) return@intercept result

                val resolved = asLong(result) ?: return@intercept result
                if (resolved != TAB_ID_CHAT) return@intercept result

                XposedCompat.logD("[MsgTabDefaultNotifyHook] switched default tab via ViewModel strategy")
                TAB_ID_NOTIFY
            }
            XposedCompat.log(
                "[MsgTabDefaultNotifyHook] hook INSTALLED: " +
                    "${locateMethod.declaringClass.name}.${locateMethod.name}(long,String)",
            )
            true
        } catch (t: Throwable) {
            XposedCompat.log("[MsgTabDefaultNotifyHook] ViewModel strategy FAILED: ${t.message}")
            XposedCompat.log(t)
            false
        }
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }
}
