package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.HookProcess
import com.forbidad4tieba.hook.symbol.contract.VideoDisableContract

internal val VideoComponentsFeature = FeatureDefinition.single(
    id = "VideoComponentsBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.ANY,
    enabled = { settings ->
        settings.isVideoComponentsDisabled && HookProcess.isVideoPlaybackProcess(processName)
    },
) { cl, _ ->
    VideoComponentsBlockHook.hook(VideoDisableContract.resolve(cl, symbols))
}
