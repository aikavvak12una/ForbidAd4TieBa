package com.forbidad4tieba.hook.feature.ad

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Method

/**
 * 屏蔽 PB 页的掉落广告和蛋雨层，归到广告屏蔽开关里。
 *
 * 处理方式：
 * 1. 目标类和方法严格通过 HookSymbolResolver 解析。
 * 2. 混淆方法不做硬编码或结构推断兜底。
 * 3. 拦截初始化、显示和清理入口，让整层不再生效。
 */
object PbFallingAdHook {
    private data class RuntimeTargets(
        val targetClass: String,
        val initMethod: String?,
        val showMethod: String?,
        val clearMethod: String?,
    )

    @Volatile private var hooked = false
    @Volatile private var hooking = false

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        if (!tryBeginHook()) return
        var success = false

        try {
            val mod = XposedCompat.module ?: return
            val targets = resolveTargets(symbols) ?: run {
                XposedCompat.log("[PbFallingAdHook] skipped: scan symbols missing target class")
                return
            }
            val targetClass = resolveTargetClass(cl, targets) ?: run {
                XposedCompat.log("[PbFallingAdHook] class NOT FOUND: symbol=${targets.targetClass}")
                return
            }

            val initMethod = resolveInitMethod(targetClass, targets.initMethod)
            val showMethod = resolveShowMethod(targetClass, targets.showMethod)
            val clearMethod = resolveClearMethod(targetClass, targets.clearMethod)

            val methods = listOfNotNull(initMethod, showMethod, clearMethod)
            if (methods.isEmpty()) {
                XposedCompat.log("[PbFallingAdHook] no usable methods resolved on ${targetClass.name}")
                return
            }

            initMethod?.let { hookInitMethod(mod, it) }
            showMethod?.let { hookShowMethod(mod, it) }
            clearMethod?.let { hookClearMethod(mod, it) }

            XposedCompat.log(
                "[PbFallingAdHook] hooks dispatched: class=${targetClass.name}, " +
                    "init=${initMethod?.name ?: "-"}, show=${showMethod?.name ?: "-"}, clear=${clearMethod?.name ?: "-"}",
            )
            success = true
        } catch (t: Throwable) {
            XposedCompat.log("[PbFallingAdHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val targetClass = scanSymbols.pbFallingViewClass?.takeIf { it.isNotBlank() } ?: return null
        val initMethod = scanSymbols.pbFallingInitMethod?.takeIf { it.isNotBlank() }
        val showMethod = scanSymbols.pbFallingShowMethod?.takeIf { it.isNotBlank() }
        val clearMethod = scanSymbols.pbFallingClearMethod?.takeIf { it.isNotBlank() }
        return RuntimeTargets(
            targetClass = targetClass,
            initMethod = initMethod,
            showMethod = showMethod,
            clearMethod = clearMethod,
        )
    }

    private fun resolveTargetClass(cl: ClassLoader, targets: RuntimeTargets): Class<*>? {
        return XposedCompat.findClassOrNull(targets.targetClass, cl)
    }

    private fun resolveInitMethod(targetClass: Class<*>, methodName: String?): Method? {
        return resolveMethod(
            targetClass = targetClass,
            methodName = methodName,
            signature = ::isInitSignature,
        )
    }

    private fun resolveShowMethod(targetClass: Class<*>, methodName: String?): Method? {
        return resolveMethod(
            targetClass = targetClass,
            methodName = methodName,
            signature = ::isShowSignature,
        )
    }

    private fun resolveClearMethod(targetClass: Class<*>, methodName: String?): Method? {
        return resolveMethod(
            targetClass = targetClass,
            methodName = methodName,
            signature = ::isClearSignature,
        )
    }

    private fun resolveMethod(
        targetClass: Class<*>,
        methodName: String?,
        signature: (Method) -> Boolean,
    ): Method? {
        if (methodName.isNullOrBlank()) return null
        val method = targetClass.declaredMethods.firstOrNull {
            it.name == methodName && signature(it)
        } ?: run {
            XposedCompat.log("[PbFallingAdHook] method NOT FOUND: ${targetClass.name}.$methodName")
            return null
        }
        method.isAccessible = true
        return method
    }

    private fun isInitSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            Context::class.java.isAssignableFrom(method.parameterTypes[0])
    }

    private fun isShowSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[2] == Int::class.javaPrimitiveType &&
            method.parameterTypes[3] == Boolean::class.javaPrimitiveType
    }

    private fun isClearSignature(method: Method): Boolean {
        return method.returnType == Void.TYPE && method.parameterTypes.isEmpty()
    }

    private fun hookInitMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun hookShowMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun hookClearMethod(mod: io.github.libxposed.api.XposedModule, method: Method) {
        mod.hook(method).intercept { chain ->
            if (!ConfigManager.isAdBlockEnabled) return@intercept chain.proceed()
            squashSelf(chain.thisObject)
            Unit
        }
        XposedCompat.log("[PbFallingAdHook] hook INSTALLED: ${ReflectionUtils.methodSignature(method)}")
    }

    private fun squashSelf(target: Any?) {
        val view = target as? View ?: return
        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
        ViewExt.squashViewRemembering(view)
    }

    private fun tryBeginHook(): Boolean {
        synchronized(this) {
            if (hooked || hooking) return false
            hooking = true
            return true
        }
    }

    private fun finishHook(success: Boolean) {
        synchronized(this) {
            hooking = false
            if (success) hooked = true
        }
    }
}
