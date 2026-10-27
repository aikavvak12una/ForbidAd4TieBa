package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.PbCommentInteractionScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object PbCommentInteractionSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        return scanSubStep("PbDisableGestureFontScaleHook", logger, PbCommentInteractionScanSymbols()) {
            scanGesture(candidates, cl, logger)
        }
    }

    private fun scanGesture(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(PbGestureScaleRule()),
            logger,
            "pbGestureScale",
        ) ?: return PbCommentInteractionScanSymbols()

        val methods = unpackScanParts(match.methodName, 3)
        return PbCommentInteractionScanSymbols(
            gestureScaleManagerClass = match.className,
            gestureScaleDispatchMethod = methods[0],
            gestureScaleListenerSetterMethod = methods[1],
            gestureScaleListenerClass = match.fieldName.ifBlank { null },
            gestureScaleListenerOnScaleMethod = methods[2],
        )
    }

}
