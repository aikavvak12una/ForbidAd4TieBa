package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.HomeTabScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object HomeTabSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeTabScanSymbols {
        val rule = HomeTabsLevel1Rule()
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(rule),
            logger,
            "home",
        ) ?: return HomeTabScanSymbols()

        return rule.targets.getValue(match.className)
    }
}
