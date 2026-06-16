package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HomeHeaderScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object HomeHeaderSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeHeaderScanSymbols {
        val search = scanSubStep("SearchBoxTextAdHook", logger, HomeHeaderScanSymbols()) {
            scanSearchBox(candidates, cl, logger)
        }
        val rightSlot = scanSubStep("HomeTopBarRightSlotHook", logger, HomeHeaderScanSymbols()) {
            scanRightSlot(cl, logger)
        }
        return HomeHeaderScanSymbols(
            searchBoxViewClass = search.searchBoxViewClass,
            searchBoxSetHintMethod = search.searchBoxSetHintMethod,
            homeSearchBoxOwnerClass = search.homeSearchBoxOwnerClass,
            homeSearchBoxInitMethod = search.homeSearchBoxInitMethod,
            homeSearchBoxGetterMethod = search.homeSearchBoxGetterMethod,
            homeRightSlotClass = rightSlot.homeRightSlotClass,
            homeRightSlotStateMethods = rightSlot.homeRightSlotStateMethods,
        )
    }

    private fun scanSearchBox(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeHeaderScanSymbols {
        val searchBoxMatch = ScanReflection.runRules(
            candidates,
            cl,
            listOf(SearchBoxSetHintRule()),
            logger,
            "searchBoxHint",
        ) ?: return HomeHeaderScanSymbols()
        val ownerMatch = ScanReflection.runRules(
            listOf(StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS),
            cl,
            listOf(
                HomeSearchBoxOwnerRule(
                    StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS,
                    searchBoxMatch.className,
                ),
            ),
            logger,
            "homeSearchBoxOwner",
        )
        return HomeHeaderScanSymbols(
            searchBoxViewClass = searchBoxMatch.className,
            searchBoxSetHintMethod = searchBoxMatch.methodName,
            homeSearchBoxOwnerClass = ownerMatch?.className,
            homeSearchBoxInitMethod = ownerMatch?.methodName,
            homeSearchBoxGetterMethod = ownerMatch?.fieldName,
        )
    }

    private fun scanRightSlot(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeHeaderScanSymbols {
        val className = StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS
        val match = ScanReflection.runRules(
            listOf(className),
            cl,
            listOf(HomeTopBarRightSlotRule(className)),
            logger,
            "homeRightSlot",
        ) ?: return HomeHeaderScanSymbols()
        val stateMethods = match.methodName
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return HomeHeaderScanSymbols(
            homeRightSlotClass = match.className,
            homeRightSlotStateMethods = stateMethods,
        )
    }

}
