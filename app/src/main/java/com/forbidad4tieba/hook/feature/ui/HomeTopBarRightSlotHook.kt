package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object HomeTopBarRightSlotHook {
    fun hook(cl: ClassLoader) {
        try {
            val rightSlotClass = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.homepage.personalize.view.HomeTabBarRightSlot", cl
            ) ?: return

            // We hook all methods returning void to ensure the search icon stays visible
            // and the game icon stays hidden whenever the view's internal state is updated
            val methods = rightSlotClass.declaredMethods.filter { it.returnType == Void.TYPE }
            for (method in methods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isHomeTabSimplifyEnabled) return

                        try {
                            val searchIcon = XposedHelpers.callMethod(param.thisObject, "getSearchIconView") as? View
                            val gameIcon = XposedHelpers.callMethod(param.thisObject, "getGameIconView") as? View
                            val topBarTip = XposedHelpers.callMethod(param.thisObject, "getTopBarTip") as? View
                            val redDotView = XposedHelpers.callMethod(param.thisObject, "getRedDotView") as? View

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
                })
            }
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook HomeTopBarRightSlot: ${t.message}")
        }
    }
}
