package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ForumPageAdBlockSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object ForumPageAdBlockHook {
    @Volatile private var hooked = false

    private val responseErrorLogged = AtomicBoolean(false)
    private val bottomErrorLogged = AtomicBoolean(false)
    private val rainErrorLogged = AtomicBoolean(false)
    private val floatingErrorLogged = AtomicBoolean(false)

    internal fun hook(targets: ForumPageAdBlockSymbols) {
        if (!ConfigManager.isForumPageAdBlockEnabled) {
            XposedCompat.log("[ForumPageAdBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!tryMarkHooked()) return

        try {
            var installed = 0
            installed += installResponseSanitizer(targets)
            installed += installBottomDataSanitizer(targets)
            installed += installBottomGameBarBlocker(targets)
            installed += installHeaderRainSanitizer(targets)
            installed += installBusinessPromotDialogBlocker(targets)
            installed += installAnimationShowBlocker(targets)
            installed += installFloatingBarBlocker(targets)
            installed += installBusinessPromotBizBlocker(targets)

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[ForumPageAdBlockHook] no hooks installed")
                return
            }
            XposedCompat.log("[ForumPageAdBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[ForumPageAdBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installResponseSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.responseParserMethod ?: return 0
        val fields = targets.responseAdFields
        if (fields.isEmpty()) return 0

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isForumPageAdBlockEnabled) {
                clearFields(chain.thisObject, fields, responseErrorLogged, "response fields")
            }
            result
        }
        return 1
    }

    private fun installBottomDataSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.bottomDataMapperMethod ?: return 0
        val setters = targets.bottomDataSetterMethods
        if (setters.isEmpty()) return 0

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isForumPageAdBlockEnabled) {
                invokeNullSetters(result, setters, bottomErrorLogged, "bottom data")
            }
            result
        }
        return 1
    }

    private fun installBottomGameBarBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.bottomGameBarMapperMethod ?: return 0

        mod.hook(method).intercept { chain ->
            if (ConfigManager.isForumPageAdBlockEnabled) {
                BlockCountStats.recordAd()
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun installHeaderRainSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.headerDataMapperMethod ?: return 0
        val setter = targets.rainSetterMethod ?: return 0

        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isForumPageAdBlockEnabled) {
                invokeNullSetters(result, listOf(setter), rainErrorLogged, "rain data")
            }
            result
        }
        return 1
    }

    private fun installBusinessPromotDialogBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.businessPromotShowMethod ?: return 0

        mod.hook(method).intercept { chain ->
            if (ConfigManager.isForumPageAdBlockEnabled) {
                BlockCountStats.recordAd()
                false
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun installAnimationShowBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.animationShowMethod ?: return 0

        mod.hook(method).intercept { chain ->
            if (ConfigManager.isForumPageAdBlockEnabled) {
                BlockCountStats.recordAd()
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun installFloatingBarBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.gameFloatingBarShowMethod
        val field = targets.gameFloatingBarField
        if (method == null && field == null) return 0

        if (method != null) {
            mod.hook(method).intercept { chain ->
                if (!ConfigManager.isForumPageAdBlockEnabled) {
                    return@intercept chain.proceed()
                }
                if (hideFloatingBar(chain.thisObject, field)) {
                    BlockCountStats.recordAd()
                }
                null
            }
            return 1
        }
        return 0
    }

    private fun installBusinessPromotBizBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.businessPromotJumpMethod ?: return 0

        mod.hook(method).intercept { chain ->
            if (ConfigManager.isForumPageAdBlockEnabled) {
                BlockCountStats.recordAd()
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun clearFields(
        target: Any?,
        fields: List<Field>,
        errorLogged: AtomicBoolean,
        label: String,
    ) {
        if (target == null) return
        try {
            for (field in fields) {
                field.set(target, defaultValue(field.type))
            }
        } catch (t: Throwable) {
            logErrorOnce(errorLogged, "clear $label FAILED", t)
        }
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            else -> null
        }
    }

    private fun invokeNullSetters(
        target: Any?,
        setters: List<Method>,
        errorLogged: AtomicBoolean,
        label: String,
    ) {
        if (target == null) return
        try {
            for (setter in setters) {
                setter.invoke(target, null)
            }
        } catch (t: Throwable) {
            logErrorOnce(errorLogged, "clear $label FAILED", t)
        }
    }

    private fun hideFloatingBar(owner: Any?, field: Field?): Boolean {
        if (owner == null || field == null) return false
        try {
            val view = field.get(owner) as? View ?: return false
            view.visibility = View.GONE
            view.alpha = 0f
            return true
        } catch (t: Throwable) {
            logErrorOnce(floatingErrorLogged, "hide floating bar FAILED", t)
            return false
        }
    }

    private fun logErrorOnce(flag: AtomicBoolean, message: String, t: Throwable) {
        if (flag.compareAndSet(false, true)) {
            XposedCompat.log("[ForumPageAdBlockHook] $message: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
