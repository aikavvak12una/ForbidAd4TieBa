package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean


object ColdStartOptHook {
    private const val TAG = "[ColdStartOptHook]"
    private val installed = AtomicBoolean(false)


    private val overrides = arrayOf(
        UbsAbTestBooleanOverride("coldStartTTIOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("coldStartTTIOpt2", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("idleTaskOpt", true) {
            ConfigManager.isHostPerformanceFlagsForced || ConfigManager.isFlutterPreinitDisabled
        },
        UbsAbTestBooleanOverride("idleTaskOpt2", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("cookieRepeatedOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("isFeedUserIconOpt", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("frsChatAsync", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("frsChatAsyncPre", true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride("isLowScoreDeviceOpt", true) { ConfigManager.isLowEndDeviceConfigForced },
        UbsAbTestBooleanOverride("isOpenApsarasSchedule", false) { ConfigManager.isApsarasScheduleDisabled },
        UbsAbTestBooleanOverride("isFrsFunAdSdkTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        UbsAbTestBooleanOverride("isDuplicateRemovalFunAdABTest", false) { ConfigManager.isAdSdkComponentsDisabled },
        UbsAbTestBooleanOverride("isAutoPlayNextVideo", false) { ConfigManager.isVideoComponentsDisabled },
    )

    fun hook(cl: ClassLoader) {
        if (!UbsAbTestBooleanOverrideInstaller.hasEnabledOverride(overrides)) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        val helperClass = UbsAbTestBooleanOverrideInstaller.findHelperClass(cl)
        if (helperClass == null) {
            installed.set(false)
            XposedCompat.logD("$TAG class NOT FOUND: ${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}")
            return
        }

        var totalInstalled = 0
        for (override in overrides) {
            val method = UbsAbTestBooleanOverrideInstaller.findMethod(helperClass, override.methodName) ?: continue
            try {
                UbsAbTestBooleanOverrideInstaller.install(mod, method, override)
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
}
