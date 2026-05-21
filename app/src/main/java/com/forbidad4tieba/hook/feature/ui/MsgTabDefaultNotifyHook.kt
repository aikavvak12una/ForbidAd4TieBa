package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method

/**
 * 消息 tab 默认策略：
 * - 显式定位目标保持不变，比如 selected_tab_id 和 ext。
 * - 只把默认兜底从私信 chat(-2) 改成通知 notify(-1)。
 */
object MsgTabDefaultNotifyHook {
    private const val TAB_ID_NOTIFY = -1L
    private const val TAB_ID_CHAT = -2L

    private data class RuntimeTargets(
        val locateToTabMethod: String,
    )

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        val targets = resolveTargets(symbols)
        if (targets == null) {
            XposedCompat.log(
                "[MsgTabDefaultNotifyHook] skipped: scan symbols missing " +
                    "(msgTabLocateToTabMethod)",
            )
            return
        }
        if (!installViewModelStrategy(cl, targets)) {
            XposedCompat.log("[MsgTabDefaultNotifyHook] skipped: ViewModel strategy unavailable")
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val locateToTabMethod = scanSymbols.msgTabLocateToTabMethod?.takeIf { it.isNotBlank() }
        if (locateToTabMethod == null) return null
        return RuntimeTargets(locateToTabMethod = locateToTabMethod)
    }

    private fun installViewModelStrategy(cl: ClassLoader, targets: RuntimeTargets): Boolean {
        val mod = XposedCompat.module ?: return false
        val vmClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS, cl)
            ?: return false
        val locateMethod = resolveLocateToTabMethod(vmClass, targets.locateToTabMethod) ?: return false
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
                "[MsgTabDefaultNotifyHook] hook INSTALLED: ${vmClass.name}.${locateMethod.name}(long,String)"
            )
            true
        } catch (t: Throwable) {
            XposedCompat.log("[MsgTabDefaultNotifyHook] ViewModel strategy FAILED: ${t.message}")
            XposedCompat.log(t)
            false
        }
    }

    private fun resolveLocateToTabMethod(vmClass: Class<*>, methodName: String): Method? {
        val method = XposedCompat.findMethodOrNull(
            vmClass,
            methodName,
            Long::class.javaPrimitiveType!!,
            String::class.java,
        ) ?: return null
        if (method.returnType != Long::class.javaPrimitiveType) return null
        method.isAccessible = true
        return method
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
