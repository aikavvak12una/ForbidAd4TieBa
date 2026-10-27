package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.PerformanceContract
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget

/** The retained PB ad experiments belong to the independent post-ad setting. */
internal val PostPageAdAbFeature = FeatureDefinition.single(
    id = "PostPageAdAbHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { settings -> settings.isPostPageAdBlockEnabled },
) { cl, _ ->
        val api = XposedCompat.module ?: return@single InstallOutcome.skipped("module unavailable")
        val methods = PerformanceContract.resolvePerformanceAbSymbols(cl, symbols)
        val issues = ArrayList<String>()
        for (target in listOf(PerformanceAbTarget.PB_COMMENT_AD, PerformanceAbTarget.PB_BANNER_AD)) {
            val method = methods[target.methodName]
            if (method == null) {
                issues += "${target.methodName} missing"
                continue
            }
            try {
                RuntimeHooks.builder(api, method, "PostPageAdAbHook", target.name).intercept { chain ->
                    if (ConfigManager.snapshot().isPostPageAdBlockEnabled) false else chain.proceed()
                }
            } catch (failure: Throwable) {
                issues += "${target.methodName}: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }
        if (issues.isEmpty()) null else InstallOutcome(InstallState.PARTIAL, reason = issues.joinToString("; "))
}
