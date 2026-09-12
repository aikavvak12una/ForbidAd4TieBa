package com.forbidad4tieba.hook.feature.perf

import android.app.Activity
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import java.lang.reflect.Method

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
 * 3. native 帖子打开时 `AbsPbActivity.w1` 的预加载渲染分支需要 `A1()==true`（PbActivity 默认
 *    false）且 `PbPreloadHelperKt.c()`(isPbNoCacheDataSwitchOn)==false。强制预加载开启时把
 *    `UbsABTestHelper.isPbArchTest()` 强制为 false，并让 `PbActivity.A1()` 仅在每个 tid 首次
 *    进入时强制 true（用 xfa 中已缓存的卡片数据直接渲染首屏）；同一 tid 再次进入时放行宿主
 *    原始 false，走普通加载路径发起完整请求，保证评论正常加载。
 *    （无条件强制 A1()==true 会让重复进入同一帖子时也跳过普通加载路径，而宿主会按自身策略
 *    拒绝重复预取完整页数据，导致评论请求完全不发起。）
 *
 * 门控与 AB 方法通过当前宿主符号校验；缺失时关闭强制预加载，避免只覆盖部分开关。
 */
object PbForcePreloadHook {
    private const val TAG = "[PbForcePreloadHook]"
    private const val METHOD_IS_PB_PRELOAD_SWITCH_ON = "isPbPreloadSwitchOn"

    // 宿主 PbActivity intent 稳定携带的帖子 id extra（PbActivityConfig.KEY_THREAD_ID），
    // 用于区分同一帖子的首次/重复进入；属于协议字符串，非混淆符号。
    private const val INTENT_EXTRA_THREAD_ID = "thread_id"

    // 已走完"首次预加载渲染"的 tid 上限，防止长会话中集合无界增长（进程内、不持久化）。
    private const val MAX_TRACKED_TIDS = 128

    private val abOverrides = arrayOf(
        // 保证 hybrid 页面能注入 apiData 预加载数据
        UbsAbTestBooleanOverride(PerformanceAbTarget.HYBRID_PB, false) { ConfigManager.isPbPreloadForced },
        // 允许宿主使用帖子数据缓存。
        UbsAbTestBooleanOverride(PerformanceAbTarget.PB_ARCH, false) { ConfigManager.isPbPreloadForced },
    )

    @Volatile private var hooked = false

    // 已通过预加载渲染分支"秒开"过的 tid（插入序，供有界淘汰）。
    private val preloadRenderedTids = LinkedHashSet<String>(64)

    /**
     * 记录一个 tid 的首次预加载渲染：首次进入返回 true（可强制 A1()==true），
     * 同一 tid 再次进入返回 false（放行宿主原始判定，保证评论请求正常发起）。
     */
    @Synchronized
    private fun markFirstPreloadRender(tid: String): Boolean {
        if (!preloadRenderedTids.add(tid)) return false
        while (preloadRenderedTids.size > MAX_TRACKED_TIDS) {
            val oldest = preloadRenderedTids.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return true
    }
    fun hook(cl: ClassLoader, renderGateMethodName: String?, abMethods: Map<String, Method>) {
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
            installed += UbsAbTestBooleanOverrideInstaller.installEnabled("$TAG.AB", mod, abMethods, abOverrides)

            // 3. 预加载渲染门控 -> 首次进入 true：放行 AbsPbActivity 的 native 预加载渲染分支。
            //    同一 tid 再次进入时放行原始 false，走普通加载路径保证评论请求正常发起。
            //    门控方法名逐版本变化（22.9.1.0 为 A1、22.10.1.0 为 B1），由符号解析器按
            //    PbPreloadHelperKt 锚点解析后传入；解析不到即跳过本 hook（fail closed）。
            val gateMethodName = renderGateMethodName?.takeIf { it.isNotBlank() }
            val pbActivityClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_ACTIVITY_CLASS, cl)
            if (gateMethodName == null) {
                XposedCompat.log("$TAG preload render gate unresolved, override skipped")
            } else if (pbActivityClass == null) {
                XposedCompat.log("$TAG ${StableTiebaHookPoints.PB_ACTIVITY_CLASS} NOT FOUND, gate override skipped")
            } else {
                val gateMethod = XposedCompat.findMethodOrNull(pbActivityClass, gateMethodName)
                if (
                    gateMethod == null ||
                    gateMethod.parameterTypes.isNotEmpty() ||
                    gateMethod.returnType != Boolean::class.javaPrimitiveType
                ) {
                    XposedCompat.log(
                        "$TAG ${StableTiebaHookPoints.PB_ACTIVITY_CLASS}.$gateMethodName() " +
                            "NOT FOUND or invalid, override skipped",
                    )
                } else {
                    gateMethod.isAccessible = true
                    mod.hook(gateMethod).intercept { chain ->
                        if (ConfigManager.isPbPreloadForced) {
                            val activity = chain.thisObject as? Activity
                            val tid = activity?.intent?.getStringExtra(INTENT_EXTRA_THREAD_ID)
                            if (!tid.isNullOrEmpty() && markFirstPreloadRender(tid)) {
                                XposedCompat.logD("$TAG gate forced true (first entry, tid=$tid)")
                                true
                            } else {
                                XposedCompat.logD("$TAG gate kept original (repeated entry or missing tid)")
                                chain.proceed()
                            }
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
