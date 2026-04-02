package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 提前并且无缝地自动加载更多（分页）。
 * 通过骗过应用的配置，使得信息流列表在滑动到底部前就提前触发预加载。
 */
object AutoLoadMoreHook {

    private const val UBS_AB_TEST_HELPER = "com.baidu.tbadk.abtest.UbsABTestHelper"
    private const val HOME_PRELOAD_CONFIG_PARSER_A = "com.baidu.tieba.homepage.switchs.HomePreloadMoreConfigParser\$a"

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAutoLoadMoreEnabled) return

        try {
            // 强制启用预加载实验
            val ubsHelperClass = XposedHelpers.findClassIfExists(UBS_AB_TEST_HELPER, cl)
            if (ubsHelperClass != null) {
                XposedHelpers.findAndHookMethod(
                    ubsHelperClass,
                    "isHomePreLoadMoreOpt",
                    XC_MethodReplacement.returnConstant(true)
                )
            } else {
                XposedBridge.log("${Constants.TAG}: class $UBS_AB_TEST_HELPER not found")
            }

            // 修改预加载卡片的阈值大小，设置得比较大可以使其无缝加载
            val configParserClass = XposedHelpers.findClassIfExists(HOME_PRELOAD_CONFIG_PARSER_A, cl)
            if (configParserClass != null) {
                XposedHelpers.findAndHookMethod(
                    configParserClass,
                    "a",
                    XC_MethodReplacement.returnConstant(20) // 当剩余20项时就提前触发预加载
                )
                XposedBridge.log("${Constants.TAG}: AutoLoadMoreHook installed")
            } else {
                XposedBridge.log("${Constants.TAG}: class $HOME_PRELOAD_CONFIG_PARSER_A not found")
            }

        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: AutoLoadMoreHook failed: ${t.message}")
        }
    }
}
