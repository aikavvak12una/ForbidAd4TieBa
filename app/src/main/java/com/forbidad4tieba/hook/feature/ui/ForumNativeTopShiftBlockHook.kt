package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.symbol.model.ForumNativeTopShiftSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object ForumNativeTopShiftBlockHook {
    private const val REFERENCE_DENSITY_DPI = 375f
    private const val REFERENCE_TOP_SHIFT_PX = -175
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()

    internal fun hook(targets: ForumNativeTopShiftSymbols) {
        val mod = XposedCompat.module ?: return
        val method = targets.initScrollMethod

        try {
            if (!installHook(mod, method)) {
                XposedCompat.logD(
                    "[ForumNativeTopShiftBlockHook] already installed: " +
                        ReflectionUtils.methodSignature(method),
                )
                return
            }
            XposedCompat.log(
                "[ForumNativeTopShiftBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}(Int, Boolean, Function0)",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[ForumNativeTopShiftBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installHook(
        mod: com.forbidad4tieba.hook.core.Api102ModuleFacade,
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
}
