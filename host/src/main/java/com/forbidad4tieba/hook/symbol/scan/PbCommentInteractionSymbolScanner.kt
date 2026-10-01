package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.PbCommentInteractionScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object PbCommentInteractionSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        val scroll = scanSubStep("PbScrollCoalesceHook", logger, PbCommentInteractionScanSymbols()) {
            scanScroll(candidates, cl, logger)
        }
        val gesture = scanSubStep("PbDisableGestureFontScaleHook", logger, PbCommentInteractionScanSymbols()) {
            scanGesture(candidates, cl, logger)
        }

        return PbCommentInteractionScanSymbols(
            scrollListenerClass = scroll.scrollListenerClass,
            scrollMethod = scroll.scrollMethod,
            scrollFragmentField = scroll.scrollFragmentField,
            scrollBottomListenerField = scroll.scrollBottomListenerField,
            scrollBottomMethod = scroll.scrollBottomMethod,
            gestureScaleManagerClass = gesture.gestureScaleManagerClass,
            gestureScaleDispatchMethod = gesture.gestureScaleDispatchMethod,
            gestureScaleListenerSetterMethod = gesture.gestureScaleListenerSetterMethod,
            gestureScaleListenerClass = gesture.gestureScaleListenerClass,
            gestureScaleListenerOnScaleMethod = gesture.gestureScaleListenerOnScaleMethod,
        )
    }

    private fun scanScroll(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(PbCommentScrollRule(StableTiebaHookPoints.PB_FRAGMENT_CLASS)),
            logger,
            "pbCommentScroll",
        ) ?: return PbCommentInteractionScanSymbols()

        val fields = unpackScanParts(match.fieldName, 3)
        return PbCommentInteractionScanSymbols(
            scrollListenerClass = match.className,
            scrollMethod = match.methodName,
            scrollFragmentField = fields[0],
            scrollBottomListenerField = fields[1],
            scrollBottomMethod = fields[2],
        )
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
