package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat

/**
 * 强制帖子预加载。
 *
 * 1. 宿主帖子预加载开关 `com.baidu.tbadk.TbSingleton.isPbPreloadSwitchOn()` 由服务端 SwitchCenter
 *    下发（key=pb_preloading，默认开），客户端无设置入口。本 hook 在该方法上强制返回 true：
 *    即使服务端关闭开关，点击帖子时仍会标记 needPreLoad 并使用卡片数据秒开首屏，
 *    同时后台预取完整页数据。
 * 2. hybrid webview 预加载通道依赖 `UbsABTestHelper.hybridPbOpt()==false`（否则宿主在
 *    PbCommonWebView 写入数据时打印"过滤apiData"并丢弃预加载数据，webview 只能自行重新拉取）。
 *    强制预加载开启时同步把该 AB 方法强制为 false，保证预加载数据能注入 hybrid 页面。
 * 3. `UbsABTestHelper.isPbArchTest()==false` 允许宿主使用帖子数据缓存。必须保留 Activity
 *    原始的预加载分支判定：同一帖子再次进入时，宿主会按自身策略拒绝重复预加载；强制进入
 *    缓存渲染分支会跳过普通加载路径，导致评论请求完全不发起。
 *
 * 跨版本说明：isPbPreloadSwitchOn 与 hybridPbOpt 双版本稳定；isPbArchTest 在旧版本找不到时
 * 仅跳过对应 hook（fail closed），不影响其余 hook。
 */
object PbForcePreloadHook {
    private const val TAG = "[PbForcePreloadHook]"
    private const val METHOD_IS_PB_PRELOAD_SWITCH_ON = "isPbPreloadSwitchOn"

    private val abOverrides = arrayOf(
        // 保证 hybrid 页面能注入 apiData 预加载数据
        UbsAbTestBooleanOverride("hybridPbOpt", false) { ConfigManager.isPbPreloadForced },
        // 允许宿主使用帖子数据缓存；旧版找不到该公开 AB 方法时跳过
        UbsAbTestBooleanOverride("isPbArchTest", false) { ConfigManager.isPbPreloadForced },
    )

    @Volatile private var hooked = false

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isPbPreloadForced) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            var installed = 0

            // 1. 强制帖子预加载开关 -> true
            val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_SINGLETON_CLASS, cl)
            if (clazz == null) {
                resetHooked()
                XposedCompat.log("$TAG class NOT FOUND: ${StableTiebaHookPoints.TB_SINGLETON_CLASS}")
                return
            }
            val method = XposedCompat.findMethodOrNull(clazz, METHOD_IS_PB_PRELOAD_SWITCH_ON)
            if (
                method == null ||
                method.parameterTypes.isNotEmpty() ||
                method.returnType != Boolean::class.javaPrimitiveType
            ) {
                resetHooked()
                XposedCompat.log(
                    "$TAG method NOT FOUND or invalid: " +
                        "${StableTiebaHookPoints.TB_SINGLETON_CLASS}.$METHOD_IS_PB_PRELOAD_SWITCH_ON()",
                )
                return
            }
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isPbPreloadForced) {
                    true
                } else {
                    chain.proceed()
                }
            }
            installed++

            // 2. hybridPbOpt -> false + isPbArchTest -> false（AB 覆盖）
            val helperClass = UbsAbTestBooleanOverrideInstaller.findHelperClass(cl)
            if (helperClass == null) {
                XposedCompat.log(
                    "$TAG AB helper class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}",
                )
            } else {
                for (entry in abOverrides) {
                    val abMethod = UbsAbTestBooleanOverrideInstaller.findMethod(helperClass, entry.methodName)
                    if (abMethod == null) {
                        XposedCompat.log(
                            "$TAG AB method NOT FOUND or invalid: " +
                                UbsAbTestBooleanOverrideInstaller.methodSignature(entry.methodName),
                        )
                        continue
                    }
                    UbsAbTestBooleanOverrideInstaller.install(mod, abMethod, entry)
                    installed++
                }
            }

            XposedCompat.log("$TAG hooks INSTALLED: count=$installed/${1 + abOverrides.size}")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
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
