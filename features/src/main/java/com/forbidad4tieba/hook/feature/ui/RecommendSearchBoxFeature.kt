package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.SearchBoxContract

internal val RecommendSearchBoxFeature = FeatureDefinition.observed(
    id = "RecommendSearchBoxHook",
    phase = FeaturePhase.SYMBOL,
    process = FeatureProcess.MAIN,
    toggle = SimpleToggle.HIDE_RECOMMEND_SEARCH_BOX,
) { cl, _ ->
    val targets = SearchBoxContract.resolveHomeSearchBoxSymbols(cl, symbols) ?: return@observed
    val module = XposedCompat.module ?: return@observed
    RuntimeHooks.builder(module, targets.ownerInitMethod, "RecommendSearchBoxHook", "hideSearchBox").intercept { chain ->
        val result = chain.proceed()
        if (ConfigManager.snapshot()[SimpleToggle.HIDE_RECOMMEND_SEARCH_BOX]) {
            // Keep the controller initialized. GONE excludes the search box and its margins from
            // both wrap-content header paths; later hint updates only change its descendants.
            val searchBox = targets.ownerGetterMethod.invoke(chain.thisObject) as? View
            searchBox?.visibility = View.GONE
        }
        result
    }
}
