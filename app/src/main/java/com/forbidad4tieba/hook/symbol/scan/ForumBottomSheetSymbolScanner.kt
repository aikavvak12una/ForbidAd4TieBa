package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ForumBottomSheetScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object ForumBottomSheetSymbolScanner {
    fun scan(cl: ClassLoader, logger: ScanLogger?): ForumBottomSheetScanSymbols {
        val className = StableTiebaHookPoints.FORUM_BOTTOM_SHEET_VIEW_CLASS
        val match = ScanReflection.runRules(
            listOf(className),
            cl,
            listOf(ForumBottomSheetInitScrollRule(className)),
            logger,
            "forumBottomSheet",
        ) ?: return ForumBottomSheetScanSymbols()
        return ForumBottomSheetScanSymbols(
            viewClass = match.className,
            initScrollMethod = match.methodName,
        )
    }
}
