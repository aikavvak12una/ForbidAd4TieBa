package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager

import com.forbidad4tieba.hook.core.XposedCompat

object HomeTopBarRightSlotHook {
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val rightSlotClass = XposedCompat.findClassOrNull(
                "com.baidu.tieba.homepage.personalize.view.HomeTabBarRightSlot", cl
            )
            if (rightSlotClass == null) {
                XposedCompat.log("[HomeTopBarRightSlotHook] class NOT FOUND")
                return
            }

            val methods = rightSlotClass.declaredMethods.filter { it.returnType == Void.TYPE }
            for (method in methods) {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (ConfigManager.isHomeTabSimplifyEnabled) {
                        try {
                            val thisObj = chain.thisObject ?: return@intercept result
                            val searchIcon = XposedCompat.callMethod(thisObj, "getSearchIconView") as? View
                            val gameIcon = XposedCompat.callMethod(thisObj, "getGameIconView") as? View
                            val topBarTip = XposedCompat.callMethod(thisObj, "getTopBarTip") as? View
                            val redDotView = XposedCompat.callMethod(thisObj, "getRedDotView") as? View

                            searchIcon?.visibility = View.VISIBLE
                            searchIcon?.alpha = 1.0f

                            gameIcon?.visibility = View.GONE
                            gameIcon?.alpha = 0.0f

                            topBarTip?.visibility = View.GONE
                            topBarTip?.alpha = 0.0f

                            redDotView?.visibility = View.GONE
                            redDotView?.alpha = 0.0f
                        } catch (_: Throwable) {
                            // Ignore missing methods gracefully
                        }
                    }
                    result
                }
            }
            XposedCompat.log("[HomeTopBarRightSlotHook] hook INSTALLED on ${methods.size} void methods")
        } catch (t: Throwable) {
            XposedCompat.log("Failed to hook HomeTopBarRightSlot: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
