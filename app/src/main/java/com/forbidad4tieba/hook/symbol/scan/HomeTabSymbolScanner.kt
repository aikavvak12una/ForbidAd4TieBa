package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.HomeTabScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object HomeTabSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeTabScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(HomeTabsLevel1Rule()),
            logger,
            "home",
        ) ?: return HomeTabScanSymbols()

        return HomeTabScanSymbols(
            tabClass = match.className,
            rebuildMethod = match.methodName,
            listField = match.fieldName,
        )
    }
}
