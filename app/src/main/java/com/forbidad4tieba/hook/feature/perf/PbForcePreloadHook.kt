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
 *    同时后台预取完整页数据（xfa / u8d / CDN 路径）。
 * 2. hybrid webview 预加载通道依赖 `UbsABTestHelper.hybridPbOpt()==false`（否则宿主在
 *    PbCommonWebView 写入数据时打印"过滤apiData"并丢弃预加载数据，webview 只能自行重新拉取）。
 *    强制预加载开启时同步把该 AB 方法强制为 false，保证预加载数据能注入 hybrid 页面。
 * 3. native 帖子打开时 `AbsPbActivity.w1` 的预加载渲染分支需要 `A1()==true`（PbActivity 默认 false）
 *    且 `PbPreloadHelperKt.c()`(isPbNoCacheDataSwitchOn)==false（账号在 PB 新架构实验时默认 true）。
 *    强制预加载开启时把 `PbActivity.A1()` 强制为 true、`UbsABTestHelper.isPbArchTest()` 强制为 false，
 *    使 xfa 中已缓存的卡片数据（tid 匹配时）能直接渲染首屏。
 *
 * 跨版本说明：isPbPreloadSwitchOn 与 hybridPbOpt 双版本稳定；A1() 与 isPbArchTest 为
 * 22.9.1.0 专属结构，旧版本找不到时仅跳过对应 hook（fail closed），不影响其余 hook。
 */
object PbForcePreloadHook {
    private const val TAG = "[PbForcePreloadHook]"
    private const val METHOD_IS_PB_PRELOAD_SWITCH_ON = "isPbPreloadSwitchOn"
    private const val PB_ACTIVITY_CLASS = "com.baidu.tieba.pb.pb.main.PbActivity"
    private const val METHOD_A1 = "A1"

    private val abOverrides = arrayOf(
        // 保证 hybrid 页面能注入 apiData 预加载数据
        UbsAbTestBooleanOverride("hybridPbOpt", false) { ConfigManager.isPbPreloadForced },
        // isPbArchTest=false → PbPreloadHelperKt.c()(isPbNoCacheDataSwitchOn)=false：
        // 让 w1 预加载渲染分支通过、xdd.t 走 u8d/CDN 缓存（22.9.1.0 专属，旧版找不到则跳过）
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

            // 3. PbActivity.A1() -> true：放行 w1 的 native 预加载渲染分支（22.9.1.0 专属）
            val pbActivityClass = XposedCompat.findClassOrNull(PB_ACTIVITY_CLASS, cl)
            if (pbActivityClass == null) {
                XposedCompat.log("$TAG $PB_ACTIVITY_CLASS NOT FOUND, A1 override skipped (old version)")
            } else {
                val a1Method = XposedCompat.findMethodOrNull(pbActivityClass, METHOD_A1)
                if (
                    a1Method == null ||
                    a1Method.parameterTypes.isNotEmpty() ||
                    a1Method.returnType != Boolean::class.javaPrimitiveType
                ) {
                    XposedCompat.log("$TAG $PB_ACTIVITY_CLASS.A1() NOT FOUND or invalid, override skipped")
                } else {
                    a1Method.isAccessible = true
                    mod.hook(a1Method).intercept { chain ->
                        if (ConfigManager.isPbPreloadForced) {
                            true
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                }
            }

            XposedCompat.log("$TAG hooks INSTALLED: count=$installed/${2 + abOverrides.size}")
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
