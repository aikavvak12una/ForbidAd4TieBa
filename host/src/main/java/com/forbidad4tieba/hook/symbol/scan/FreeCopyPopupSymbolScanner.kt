package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.FreeCopyPopupScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object FreeCopyPopupSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FreeCopyPopupScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(FreeCopyPopupRule()),
            logger,
            "freeCopyPopup",
        ) ?: return FreeCopyPopupScanSymbols()
        return FreeCopyPopupScanSymbols(
            menuClass = match.className,
            contentViewMethod = match.methodName,
            textField = match.fieldName,
        )
    }
}
