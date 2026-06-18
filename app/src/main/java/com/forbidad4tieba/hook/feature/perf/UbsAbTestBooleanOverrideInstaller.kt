package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.core.Api102ModuleFacade
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal data class UbsAbTestBooleanOverride(
    val methodName: String,
    val value: Boolean,
    val enabled: () -> Boolean,
)

internal object UbsAbTestBooleanOverrideInstaller {
    fun hasEnabledOverride(overrides: Array<UbsAbTestBooleanOverride>): Boolean {
        return overrides.any { it.enabled() }
    }

    fun findHelperClass(cl: ClassLoader): Class<*>? {
        return XposedCompat.findClassOrNull(StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS, cl)
    }

    fun findMethod(helperClass: Class<*>, methodName: String): Method? {
        val method = XposedCompat.findMethodOrNull(helperClass, methodName) ?: return null
        return method.takeIf(::isStaticNoArgBoolean)
    }

    fun install(module: Api102ModuleFacade, method: Method, override: UbsAbTestBooleanOverride) {
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            if (override.enabled()) {
                override.value
            } else {
                chain.proceed()
            }
        }
    }

    fun methodSignature(methodName: String): String {
        return "${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}.$methodName()"
    }

    private fun isStaticNoArgBoolean(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            (method.returnType == Boolean::class.javaPrimitiveType ||
                method.returnType == Boolean::class.javaObjectType)
    }
}
