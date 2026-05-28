package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 寮哄埗寮€鍚创鍚ц嚜韬€ц兘閰嶇疆閲屽凡鏈夌殑 AB 鏍囧織銆? *
 * 杩欓噷淇濇寔琛ㄩ┍鍔ㄥ啓娉曪紝璁╁涓€ц兘瀛愬紑鍏冲叡鐢ㄥ悓涓€涓?UbsABTestHelper hook 鐐癸紝
 * 閬垮厤閲嶅瀹夎 hook銆? */
object ColdStartOptHook {
    private const val TAG = "[ColdStartOptHook]"
    private val installed = AtomicBoolean(false)

    private data class BooleanOverride(
        val methodName: String,
        val value: Boolean,
        val isEnabled: () -> Boolean,
    )

    /**
     * 杩欓噷涓嶅姞鍏?isColdNetDataOpt銆?     * 鍚敤瀹冨悗鐩爣搴旂敤浼氱洿鎺ヨ皟鐢?B0() 鑰屼笉鏄?w1()锛?     * 浼氱粫杩?AutoRefreshHook 鐨勯樆鏂獥鍙ｃ€?     */
    private val overrides = arrayOf(
        BooleanOverride("coldStartTTIOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("coldStartTTIOpt2", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("idleTaskOpt", true) {
            ConfigManager.isHostPerformanceFlagsForced || ConfigManager.isFlutterPreinitDisabled
        },
        BooleanOverride("idleTaskOpt2", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("cookieRepeatedOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("isFeedUserIconOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("frsChatAsync", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("frsChatAsyncPre", true) { ConfigManager.isHostPerformanceFlagsForced },
        BooleanOverride("isLowScoreDeviceOpt", true) { ConfigManager.isLowEndDeviceConfigForced },
        BooleanOverride("isOpenApsarasSchedule", false) { ConfigManager.isApsarasScheduleDisabled },
        BooleanOverride("isFrsFunAdSdkTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        BooleanOverride("isDuplicateRemovalFunAdABTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        BooleanOverride("isAutoPlayNextVideo", false) { ConfigManager.isVideoComponentsDisabled },
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
