package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean


object ColdStartOptHook {
    private const val TAG = "[ColdStartOptHook]"
    private val installed = AtomicBoolean(false)


    private val overrides = arrayOf(
        UbsAbTestBooleanOverride(PerformanceAbTarget.COLD_START_TTI, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.COLD_START_TTI_2, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.IDLE_TASK, true) {
            ConfigManager.isHostPerformanceFlagsForced || ConfigManager.isFlutterPreinitDisabled
        },
        UbsAbTestBooleanOverride(PerformanceAbTarget.IDLE_TASK_2, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.COOKIE_REPEATED, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.FEED_ICON, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.FRS_CHAT_ASYNC, true) { ConfigManager.isHostPerformanceFlagsForced },
        UbsAbTestBooleanOverride(PerformanceAbTarget.FRS_CHAT_PRELOAD, true) { ConfigManager.isHostPerformanceFlagsForced },
        // 首页框架优化 + 冷启动网络数据优化（独立开关 KEY_FORCE_HOST_FEED_COLD_OPT）
        UbsAbTestBooleanOverride(PerformanceAbTarget.FEED_UI, true) { ConfigManager.isHostFeedColdOptEnabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.COLD_NET_DATA, true) { ConfigManager.isHostFeedColdOptEnabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.APSARAS_SCHEDULE, false) { ConfigManager.isApsarasScheduleDisabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.FRS_AD_SDK, false) { ConfigManager.isAdSdkComponentsDisabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.DUPLICATE_AD, false) { ConfigManager.isAdSdkComponentsDisabled },
        UbsAbTestBooleanOverride(PerformanceAbTarget.AUTO_PLAY_NEXT_VIDEO, false) { ConfigManager.isVideoComponentsDisabled },
    )

    fun hook(abMethods: Map<String, Method>) {
        if (!UbsAbTestBooleanOverrideInstaller.hasEnabledOverride(overrides)) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        val totalInstalled = UbsAbTestBooleanOverrideInstaller.installEnabled(TAG, mod, abMethods, overrides)
        if (totalInstalled == 0) {
            installed.set(false)
            XposedCompat.logD("$TAG no AB test methods found in this version")
        }
    }
}
