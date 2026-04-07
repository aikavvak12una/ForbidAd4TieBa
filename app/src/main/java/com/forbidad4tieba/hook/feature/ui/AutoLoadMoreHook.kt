package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager

import com.forbidad4tieba.hook.core.XposedCompat

/**
 * 提前并且无缝地自动加载更多（分页）。
 * 通过骗过应用的配置，使得信息流列表在滑动到底部前就提前触发预加载。
 */
object AutoLoadMoreHook {

    private const val UBS_AB_TEST_HELPER = "com.baidu.tbadk.abtest.UbsABTestHelper"
    private const val HOME_PRELOAD_CONFIG_PARSER_A = "com.baidu.tieba.homepage.switchs.HomePreloadMoreConfigParser\$a"

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAutoLoadMoreEnabled) return
        val mod = XposedCompat.module ?: return

        try {
            // 强制启用预加载实验
            val ubsMethod = XposedCompat.findMethodOrNull(UBS_AB_TEST_HELPER, cl, "isHomePreLoadMoreOpt")
            if (ubsMethod != null) {
                mod.hook(ubsMethod).intercept { true }
            } else {
                XposedCompat.log("[AutoLoadMoreHook] isHomePreLoadMoreOpt NOT FOUND")
            }

            // 修改预加载卡片的阈值大小，设置得比较大可以使其无缝加载
            val configMethod = XposedCompat.findMethodOrNull(HOME_PRELOAD_CONFIG_PARSER_A, cl, "a")
            if (configMethod != null) {
                mod.hook(configMethod).intercept { 20 } // 当剩余20项时就提前触发预加载
                XposedCompat.log("[AutoLoadMoreHook] hook INSTALLED")
            } else {
                XposedCompat.log("[AutoLoadMoreHook] HomePreloadMoreConfigParser.a NOT FOUND")
            }

        } catch (t: Throwable) {
            XposedCompat.log("[AutoLoadMoreHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
