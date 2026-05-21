package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object ForumNativeTopShiftBlockHook {
    private const val REFERENCE_DENSITY_DPI = 375f
    private const val REFERENCE_TOP_SHIFT_PX = -175
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    fun hook(
        cl: ClassLoader,
        symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols(),
    ) {
        val mod = XposedCompat.module ?: return
        val className = symbols?.forumBottomSheetViewClass
        val methodName = symbols?.forumBottomSheetInitScrollMethod
        if (className.isNullOrBlank() || methodName.isNullOrBlank()) {
            XposedCompat.log(
                "[ForumNativeTopShiftBlockHook] skipped: missing symbol " +
                    "forumBottomSheetViewClass/forumBottomSheetInitScrollMethod",
            )
            return
        }

        try {
            val targetClass = XposedCompat.findClassOrNull(className, cl)
            if (targetClass == null) {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: class not found $className")
                return
            }
            val method = targetClass.declaredMethods.firstOrNull {
                isInitScrollMethod(it, methodName)
            }
            if (method == null) {
                XposedCompat.log("[ForumNativeTopShiftBlockHook] skipped: method not found $className.$methodName")
                return
            }
            method.isAccessible = true
            if (!installHook(mod, method)) {
                XposedCompat.logD(
                    "[ForumNativeTopShiftBlockHook] already installed: " +
                        ReflectionUtils.methodSignature(method),
                )
                return
            }
            XposedCompat.log(
                "[ForumNativeTopShiftBlockHook] hook INSTALLED: " +
                    "${targetClass.name}.${method.name}(Int, Boolean, Function0)",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ForumNativeTopShiftBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installHook(
        mod: io.github.libxposed.api.XposedModule,
        method: Method,
    ): Boolean {
        val methodKey = ReflectionUtils.methodSignature(method)
        if (!installedMethodKeys.add(methodKey)) return false

        mod.hook(method).intercept { chain ->
            val scrollY = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
            if (scrollY >= 0) return@intercept chain.proceed()
            chain.proceed(
                arrayOf<Any?>(
                    topShiftPx(chain.thisObject),
                    chain.args.getOrNull(1),
                    chain.args.getOrNull(2),
                ),
            )
        }
        return true
    }

    private fun topShiftPx(target: Any?): Int {
        val density = (target as? View)
            ?.resources
            ?.displayMetrics
            ?.density
            ?.takeIf { it > 0f }
            ?: return REFERENCE_TOP_SHIFT_PX
        val referenceDensity = REFERENCE_DENSITY_DPI / 160f
        return (REFERENCE_TOP_SHIFT_PX * density / referenceDensity).roundToInt()
    }

    private fun isInitScrollMethod(method: Method, methodName: String): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.name != methodName) return false
        if (method.returnType != Void.TYPE) return false
        val p = method.parameterTypes
        return p.size == 3 &&
            p[0] == Int::class.javaPrimitiveType &&
            p[1] == Boolean::class.javaPrimitiveType &&
            p[2].name == "kotlin.jvm.functions.Function0"
    }
}
