package com.forbidad4tieba.hook.feature.privacy

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.symbol.contract.CrashReportContract

internal val CrashReportBlockFeature = FeatureDefinition.observed(
    id = "CrashReportBlockHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.ANY,
) { cl, settings ->
    CrashReportBlockHook.hook(cl, CrashReportContract.resolveExceptionReportMethod(cl, symbols))
}
