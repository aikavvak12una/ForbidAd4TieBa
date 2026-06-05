package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 在代码层阻断统计和埋点 SDK 的数据采集。
 *
 * 这类 SDK 会消耗 CPU、内存、磁盘 IO 和网络；拦住入口后，后续开销也不会继续产生。
 *
 * 由现有性能优化开关 isMonitorSyncComponentsDisabled 控制。
 */
object TrackingBlockHook {
    private const val TAG = "[TrackingBlockHook]"
    private val installed = AtomicBoolean(false)

    /**
     * 已知统计和埋点 SDK 入口。
     * 格式为 className 对应需要短路的 methodNames。
     */
    private val TRACKING_TARGETS = arrayOf(
        // 字节 AppLog，很多百度应用会内置
        "com.bytedance.embedapplog.AppLog" to arrayOf("init", "onEventV3", "setHeaderInfo"),
        // 百度 Loki 日志系统
        "com.baidu.searchbox.logsystem.basic.LokiReporter" to arrayOf("report", "reportSync"),
        "com.baidu.searchbox.logsystem.basic.LokiService" to arrayOf("onStartCommand"),
        // 百度性能监控
        "com.baidu.tieba.service.PerformMonitorService" to arrayOf("onStartCommand"),
        // 百度 UBC 用户行为采集
        "com.baidu.tbadk.core.ubc.UBCAgent" to arrayOf("onEvent", "onPageStart", "onPageEnd"),
        // 百度统计
        "com.baidu.mobstat.StatService" to arrayOf("onEvent", "onPageStart", "onPageEnd", "start"),
    )

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isMonitorSyncComponentsDisabled) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for ((className, methodNames) in TRACKING_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: continue
            for (methodName in methodNames) {
                val methods = clazz.declaredMethods.filter { method ->
                    method.name == methodName
                }
                for (method in methods) {
                    try {
                        method.isAccessible = true
                        mod.hook(method).intercept { chain ->
                            if (ConfigManager.isMonitorSyncComponentsDisabled) {
                                return@intercept nullReturnValue(method)
                            }
                            chain.proceed()
                        }
                        totalInstalled++
                    } catch (t: Throwable) {
                        // Some methods may not be hookable; keep a concise diagnostic.
                        XposedCompat.logD { "$TAG hook $className.${method.name} skipped: ${t.message}" }
                    }
                }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no tracking SDK methods found in this version")
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
