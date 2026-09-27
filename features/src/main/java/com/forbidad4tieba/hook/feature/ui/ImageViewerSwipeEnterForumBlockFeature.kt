package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess

internal val ImageViewerSwipeEnterForumBlockFeature = FeatureDefinition.observed(
    id = "ImageViewerSwipeEnterForumBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.IMAGE_VIEWER,
) { cl, settings ->
    ImageViewerSwipeEnterForumBlockHook.hook(cl)
}
