package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookInstallEntry
import com.forbidad4tieba.hook.symbol.contract.LowEndContract
import com.forbidad4tieba.hook.symbol.contract.PbPreloadContract
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.contract.TrackingContract
import java.lang.reflect.Method

internal val PerformanceFeature = FeatureDefinition(
    id = "Performance",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
) { context, settings ->
    val symbols = context.symbols
    val entries = ArrayList<HookInstallEntry>()
    var abMethods: Map<String, Method>? = null
    fun abSymbols(cl: ClassLoader): Map<String, Method> = abMethods
        ?: PerformanceContract.resolvePerformanceAbSymbols(cl, symbols).also { abMethods = it }
    if (settings.isPbPerformanceModeEnabled || settings.isPostPageAdBlockEnabled) {
        entries += HookInstallEntry.observed("PbPerformanceModeHook") { cl -> PbPerformanceModeHook.hook(abSymbols(cl)) }
    }
    if (settings.isPbPreloadForced) {
        entries += HookInstallEntry.observed("PbForcePreloadHook") { cl ->
            PbPreloadContract.resolvePbPreloadTargets(cl, symbols)?.let { targets ->
                PbForcePreloadHook.hook(targets, abSymbols(cl))
            }
        }
    }
    if (settings.isAdSdkComponentsDisabled) {
        entries += HookInstallEntry.observed("AdSdkInitBlockHook") { cl -> AdSdkInitBlockHook.hook(cl) }
    }
    if (settings.isMonitorSyncComponentsDisabled) {
        entries += HookInstallEntry.observed("TrackingBlockHook") { cl ->
            TrackingBlockHook.hook(TrackingContract.resolveTrackingSymbols(cl, symbols))
        }
    }
    if (settings.isVideoComponentsDisabled) {
        entries += HookInstallEntry.observed("VideoPreloadBlockHook") { cl -> VideoPreloadBlockHook.hook(cl) }
    }
    if (settings.isHostSlideAnimationDisabled) {
        entries += HookInstallEntry.observed("HostSlideAnimationBlockHook") { cl ->
            HostSlideAnimationBlockHook.hook(cl)
        }
    }
    if (
        settings.isHostPerformanceFlagsForced ||
        settings.isFlutterPreinitDisabled ||
        settings.isApsarasScheduleDisabled ||
        settings.isAdSdkComponentsDisabled ||
        settings.isVideoComponentsDisabled ||
        settings.isHostFeedColdOptEnabled
    ) {
        entries += HookInstallEntry.observed("ColdStartOptHook") { cl -> ColdStartOptHook.hook(abSymbols(cl)) }
    }
    if (
        settings.isAdSdkComponentsDisabled ||
        settings.isFlutterPreinitDisabled ||
        settings.isLowEndDeviceConfigForced
    ) {
        entries += HookInstallEntry("HostPerformanceConfigHook") { cl ->
            HostPerformanceConfigHook.hook(
                cl,
                if (settings.isLowEndDeviceConfigForced) symbols[LowEndContract.lowEndConfig].restore(cl) else emptyMap(),
            )
        }
    }
    entries
}
