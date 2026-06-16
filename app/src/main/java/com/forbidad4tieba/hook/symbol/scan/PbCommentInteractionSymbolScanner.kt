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
        val bottomList = scanSubStep("PbCommentAutoLoadHook.List", logger, PbCommentInteractionScanSymbols()) {
            scanBottomList(candidates, cl, logger)
        }
        val bottomRecycler = scanSubStep("PbCommentAutoLoadHook.Recycler", logger, PbCommentInteractionScanSymbols()) {
            scanBottomRecycler(candidates, cl, logger)
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
            bottomListScrollClass = bottomList.bottomListScrollClass,
            bottomListScrollMethod = bottomList.bottomListScrollMethod,
            bottomListOwnerField = bottomList.bottomListOwnerField,
            bottomRecyclerScrollClass = bottomRecycler.bottomRecyclerScrollClass,
            bottomRecyclerScrollMethod = bottomRecycler.bottomRecyclerScrollMethod,
            bottomRecyclerOwnerField = bottomRecycler.bottomRecyclerOwnerField,
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

    private fun scanBottomList(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        val match = ScanReflection.runRules(
            bottomMechanismCandidates(candidates, StableTiebaHookPoints.BD_LIST_VIEW_CLASS),
            cl,
            listOf(BdListViewBottomScrollRule(StableTiebaHookPoints.BD_LIST_VIEW_CLASS)),
            logger,
            "pbCommentBottomList",
        ) ?: return PbCommentInteractionScanSymbols()

        return PbCommentInteractionScanSymbols(
            bottomListScrollClass = match.className,
            bottomListScrollMethod = match.methodName,
            bottomListOwnerField = match.fieldName,
        )
    }

    private fun scanBottomRecycler(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbCommentInteractionScanSymbols {
        val match = ScanReflection.runRules(
            bottomMechanismCandidates(candidates, StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS),
            cl,
            listOf(BdRecyclerViewBottomScrollRule(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS)),
            logger,
            "pbCommentBottomRecycler",
        ) ?: return PbCommentInteractionScanSymbols()

        return PbCommentInteractionScanSymbols(
            bottomRecyclerScrollClass = match.className,
            bottomRecyclerScrollMethod = match.methodName,
            bottomRecyclerOwnerField = match.fieldName,
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

    private fun bottomMechanismCandidates(candidates: List<String>, ownerClassName: String): List<String> {
        val nestedPrefix = "$ownerClassName\$"
        val nested = candidates
            .filter { it.startsWith(nestedPrefix) }
            .distinct()
        return if (nested.isNotEmpty()) nested else listOf(ownerClassName)
    }
}
