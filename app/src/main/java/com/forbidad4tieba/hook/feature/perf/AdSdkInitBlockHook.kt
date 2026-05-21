package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 在代码层阻断广告 SDK 初始化，减少启动耗时、网络连接、线程池和内存占用。
 *
 * 这和 ComponentDisableHook 互补，后者负责禁用 Manifest 声明的组件。
 * 广告 SDK 初始化经常在 Application.onCreate 或 ContentProvider.onCreate 中执行，
 * 比组件启动更早。
 *
 * 由现有性能优化开关 isAdSdkComponentsDisabled 控制。
 */
object AdSdkInitBlockHook {
    private const val TAG = "[AdSdkInitBlockHook]"
    private val installed = AtomicBoolean(false)

    /**
     * 已知广告 SDK 初始化入口。
     * 每一项表示 className 对应需要提前返回的 methodName 列表。
     */
    private val AD_SDK_INIT_TARGETS = arrayOf(
        // 百度 Mobads SDK
        "com.baidu.mobads.sdk.api.BaiduAdManager" to arrayOf("init"),
        "com.baidu.mobads.sdk.internal.a" to arrayOf("a"),
        // 字节 Pangle SDK
        "com.bytedance.sdk.openadsdk.TTAdSdk" to arrayOf("init", "start"),
        // 快手广告 SDK
        "com.kwad.sdk.api.KsAdSDK" to arrayOf("init", "start"),
        // QQ 广告 SDK，腾讯广点通。
        "com.qq.e.comm.managers.GDTAdSdk" to arrayOf("init", "initWith"),
        // 广告聚合平台 Ubix SSP
        "com.ubix.ssp.UbixSdk" to arrayOf("init"),
    )

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAdSdkComponentsDisabled) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for ((className, methodNames) in AD_SDK_INIT_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: continue
            for (methodName in methodNames) {
                val methods = clazz.declaredMethods.filter { method ->
                    method.name == methodName && Modifier.isStatic(method.modifiers)
                }
                for (method in methods) {
                    try {
                        method.isAccessible = true
                        mod.hook(method).intercept { chain ->
                            if (ConfigManager.isAdSdkComponentsDisabled) {
                                return@intercept nullReturnValue(method)
                            }
                            chain.proceed()
                        }
                        totalInstalled++
                    } catch (t: Throwable) {
                        // 方法可能不可 hook，写入详细日志便于排查。
                        XposedCompat.logD { "$TAG hook $className.${method.name} skipped: ${t.message}" }
                    }
                }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no ad SDK init methods found in this version")
        }
    }

    private fun nullReturnValue(method: Method): Any? {
        return when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Void.TYPE -> null
            else -> null
        }
    }
}
