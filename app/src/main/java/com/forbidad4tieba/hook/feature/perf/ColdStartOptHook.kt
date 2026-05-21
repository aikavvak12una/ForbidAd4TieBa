package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 强制开启贴吧自身性能配置里已有的 AB 标志。
 *
 * 这里保持表驱动写法，让多个性能子开关共用同一个 UbsABTestHelper hook 点，
 * 避免重复安装 hook。
 */
object ColdStartOptHook {
    private const val TAG = "[ColdStartOptHook]"
    private val installed = AtomicBoolean(false)

    private data class BooleanOverride(
        val methodName: String,
        val value: Boolean,
        val isEnabled: () -> Boolean,
    )

    /**
     * 这里不加入 isColdNetDataOpt。
     * 启用它后目标应用会直接调用 B0() 而不是 w1()，
     * 会绕过 AutoRefreshHook 的阻断窗口。
     */
    private val overrides = arrayOf(
        BooleanOverride("coldStartTTIOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("coldStartTTIOpt2", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("idleTaskOpt", true) {
            ConfigManager.isHostPerformanceFlagsForced || ConfigManager.isFlutterPreinitDisabled
        },
        BooleanOverride("idleTaskOpt2", true) {
            ConfigManager.isHostPerformanceFlagsForced || ConfigManager.isPreloadRuntimeDisabled
        },
        BooleanOverride("cookieRepeatedOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("isFeedUserIconOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("frsChatAsync", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("frsChatAsyncPre", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("isLowScoreDeviceOpt", true) { ConfigManager.isLowEndDeviceConfigForced },
        BooleanOverride("isOpenApsarasSchedule", false) { ConfigManager.isApsarasScheduleDisabled },
        BooleanOverride("isFrsFunAdSdkTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        BooleanOverride("isDuplicateRemovalFunAdABTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        BooleanOverride("isAutoPlayNextVideo", false) {
            ConfigManager.isPreloadRuntimeDisabled || ConfigManager.isVideoComponentsDisabled
        },
    )

    fun hook(cl: ClassLoader) {
        if (!isAnyOverrideEnabled()) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        val helperClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS, cl)
        if (helperClass == null) {
            installed.set(false)
            XposedCompat.logD("$TAG class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
            return
        }

        var totalInstalled = 0
        for (override in overrides) {
            val method = XposedCompat.findMethodOrNull(helperClass, override.methodName)
            if (method == null || !isStaticNoArgBoolean(method)) continue
            try {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (override.isEnabled()) return@intercept override.value
                    chain.proceed()
                }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG hook ${helperClass.name}.${override.methodName} skipped: ${t.message}" }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled/${overrides.size}")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no AB test methods found in this version")
        }
    }

    private fun isAnyOverrideEnabled(): Boolean {
        return ConfigManager.isHostPerformanceFlagsForced ||
            ConfigManager.isFlutterPreinitDisabled ||
            ConfigManager.isPreloadRuntimeDisabled ||
            ConfigManager.isLowEndDeviceConfigForced ||
            ConfigManager.isApsarasScheduleDisabled ||
            ConfigManager.isAdSdkComponentsDisabled ||
            ConfigManager.isVideoComponentsDisabled
    }

    private fun isStaticNoArgBoolean(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            (method.returnType == Boolean::class.javaPrimitiveType ||
                method.returnType == Boolean::class.javaObjectType)
    }
}
