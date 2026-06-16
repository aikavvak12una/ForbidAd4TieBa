package com.forbidad4tieba.hook.symbol.model

internal fun HookSymbolsBuilder.applyHomeHeaderScan(scan: HomeHeaderScanSymbols) {
    searchBoxViewClass = scan.searchBoxViewClass
    searchBoxSetHintMethod = scan.searchBoxSetHintMethod
    homeSearchBoxOwnerClass = scan.homeSearchBoxOwnerClass
    homeSearchBoxInitMethod = scan.homeSearchBoxInitMethod
    homeSearchBoxGetterMethod = scan.homeSearchBoxGetterMethod
    homeRightSlotClass = scan.homeRightSlotClass
    homeRightSlotStateMethods = scan.homeRightSlotStateMethods
}
