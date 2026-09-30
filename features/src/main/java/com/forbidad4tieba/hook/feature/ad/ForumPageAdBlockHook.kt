package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.core.RuntimeHooks
import android.view.View
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ForumPageAdBlockSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object ForumPageAdBlockHook {
    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }

    private val responseErrorLogged = AtomicBoolean(false)
    private val bottomErrorLogged = AtomicBoolean(false)
    private val rainErrorLogged = AtomicBoolean(false)
    private val floatingErrorLogged = AtomicBoolean(false)

    @Synchronized
    internal fun hook(targets: ForumPageAdBlockSymbols): InstallOutcome {
        if (!ConfigManager.snapshot().isForumPageAdBlockEnabled) {
            return InstallOutcome.skipped("config disabled")
        }
        if (XposedCompat.module == null) return InstallOutcome.skipped("module unavailable")
        val before = installedMethods.size()
        var count = 0
        var missing = 0
        val failures = ArrayList<String>()

        // Each path works independently; retain successful handles while retrying absent points.
        fun install(label: String, action: () -> Int) {
            try {
                val installed = action()
                if (installed == 0) {
                    missing++
                    failures += "$label missing"
                }
                count += installed
            } catch (failure: Throwable) {
                failures += "$label: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }

        install("response") { installResponseSanitizer(targets) }
        install("bottom") { installBottomDataSanitizer(targets) }
        install("game") { installBottomGameBarBlocker(targets) }
        install("rain") { installHeaderRainSanitizer(targets) }
        install("dialog") { installBusinessPromotDialogBlocker(targets) }
        install("floating") { installFloatingBarBlocker(targets) }
        install("biz") { installBusinessPromotBizBlocker(targets) }

        val state = when {
            count == 0 && missing == failures.size -> InstallState.SKIPPED
            count == 0 -> InstallState.FAILED
            failures.isNotEmpty() -> InstallState.PARTIAL
            installedMethods.size() == before -> InstallState.ALREADY_INSTALLED
            else -> InstallState.INSTALLED
        }
        return InstallOutcome(state, count, failures.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    private fun installResponseSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.responseParserMethod ?: return 0
        val fields = targets.responseAdFields
        if (fields.isEmpty()) return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installResponseSanitizer:method").intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    clearFields(chain.thisObject, fields, responseErrorLogged, "response fields")
                }
                result
            }
        }
        return 1
    }

    private fun installBottomDataSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.bottomDataMapperMethod ?: return 0
        val setters = targets.bottomDataSetterMethods
        if (setters.isEmpty()) return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installBottomDataSanitizer:method").intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    invokeNullSetters(result, setters, bottomErrorLogged, "bottom data")
                }
                result
            }
        }
        return 1
    }

    private fun installBottomGameBarBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.bottomGameBarMapperMethod ?: return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installBottomGameBarBlocker:method").intercept { chain ->
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    null
                } else {
                    chain.proceed()
                }
            }
        }
        return 1
    }

    private fun installHeaderRainSanitizer(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.headerDataMapperMethod ?: return 0
        val setter = targets.rainSetterMethod ?: return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installHeaderRainSanitizer:method").intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    invokeNullSetters(result, listOf(setter), rainErrorLogged, "rain data")
                }
                result
            }
        }
        return 1
    }

    private fun installBusinessPromotDialogBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.businessPromotShowMethod ?: return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installBusinessPromotDialogBlocker:method").intercept { chain ->
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    BlockCountStats.recordAd()
                    false
                } else {
                    chain.proceed()
                }
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
            installedMethods.install(method) {
                RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installFloatingBarBlocker:method").intercept { chain ->
                    if (!ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                        return@intercept chain.proceed()
                    }
                    hideFloatingBar(chain.thisObject, field)
                    null
                }
            }
            return 1
        }
        return 0
    }

    private fun installBusinessPromotBizBlocker(targets: ForumPageAdBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val method = targets.businessPromotJumpMethod ?: return 0

        installedMethods.install(method) {
            RuntimeHooks.builder(mod, method, "ForumPageAdBlockHook", "installBusinessPromotBizBlocker:method").intercept { chain ->
                if (ConfigManager.snapshot().isForumPageAdBlockEnabled) {
                    null
                } else {
                    chain.proceed()
                }
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

}
