package com.forbidad4tieba.hook.symbol.scan

import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ForumBottomSheetScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal object ForumBottomSheetSymbolScanner {
    fun scan(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ForumBottomSheetScanSymbols {
        val className = StableTiebaHookPoints.FORUM_BOTTOM_SHEET_VIEW_CLASS
        val viewClass = ScanReflection.safeFindClass(className, cl)
        if (viewClass == null) {
            recordScanIssue(logger, "class not found: $className")
            return ForumBottomSheetScanSymbols()
        }
        if (!View::class.java.isAssignableFrom(viewClass)) {
            recordScanIssue(logger, "class is not a View: $className")
            return ForumBottomSheetScanSymbols()
        }

        val methods = viewClass.declaredMethods
        val initScrollCandidates = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
        }
        val smoothInitGetterCandidates = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER &&
                method.returnType == Int::class.javaPrimitiveType &&
                method.parameterTypes.isEmpty()
        }
        val setupCandidates = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SETUP_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    ),
                )
        }
        val maxScrollGetterCandidates =
            ScanReflection.collectInstanceMethods(viewClass).filter { method ->
                method.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterTypes.isEmpty()
            }
        if (
            initScrollCandidates.size != 1 ||
            smoothInitGetterCandidates.size != 1 ||
            setupCandidates.size != 1 ||
            maxScrollGetterCandidates.size != 1
        ) {
            recordScanIssue(
                logger,
                "candidates initScroll=${initScrollCandidates.size}, " +
                    "smoothInitGetter=${smoothInitGetterCandidates.size}, " +
                    "setup=${setupCandidates.size}, " +
                    "maxScrollGetter=${maxScrollGetterCandidates.size}: $className",
            )
            return ForumBottomSheetScanSymbols()
        }

        val setupMethod = setupCandidates.single()
        HookSymbolScanDiagnostics.log(
            logger,
            "ForumNativeTopShiftBlockHook matched: $className." +
                "${setupMethod.name}(Int,Int,Int,Boolean), " +
                "${initScrollCandidates.single().name}(Int,Boolean,Function0), " +
                "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER}(), " +
                "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER}()",
        )
        return ForumBottomSheetScanSymbols(
            viewClass = viewClass.name,
            initScrollMethod = initScrollCandidates.single().name,
        )
    }

    private fun recordScanIssue(logger: ScanLogger?, detail: String) {
        HookSymbolScanSession.get()?.scanErrors?.let { errors ->
            HookSymbolScanDiagnostics.recordScanIssue(
                logger,
                "ForumNativeTopShiftBlockHook",
                errors,
                detail,
            )
        } ?: HookSymbolScanDiagnostics.log(
            logger,
            "ForumNativeTopShiftBlockHook scan issue: $detail",
        )
    }
}
