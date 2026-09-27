package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.ForumBottomSheetScanSymbols
import com.forbidad4tieba.hook.symbol.model.ForumNativeTopShiftSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ForumBottomSheetSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object ForumTopShiftContract : SymbolContract("ForumTopShift") {
    val forumBottomSheetViewClass = text("forumBottomSheetViewClass")
    val forumBottomSheetInitScrollMethod = text("forumBottomSheetInitScrollMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val forumBottomSheetScan = runScanStep(
            "ForumNativeTopShiftBlockHook",
            logger,
            scanErrors,
            ForumBottomSheetScanSymbols(),
        ) {
            ForumBottomSheetSymbolScanner.scan(cl, logger)
        }

        val forumBottomSheetViewClass: String? = forumBottomSheetScan.viewClass

        val forumBottomSheetInitScrollMethod: String? = forumBottomSheetScan.initScrollMethod

        output[ForumTopShiftContract.forumBottomSheetViewClass] = forumBottomSheetViewClass
        output[ForumTopShiftContract.forumBottomSheetInitScrollMethod] = forumBottomSheetInitScrollMethod
    }

    fun resolveForumNativeTopShiftSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ForumNativeTopShiftSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ForumNativeTopShiftBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[ForumTopShiftContract.forumBottomSheetViewClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[ForumNativeTopShiftBlockHook] skipped: missing forumBottomSheetViewClass")
                return null
            }
            val initScrollMethodName =
                resolvedSymbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log(
                        "[ForumNativeTopShiftBlockHook] skipped: " +
                            "missing forumBottomSheetInitScrollMethod",
                    )
                    return null
                }
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[ForumNativeTopShiftBlockHook] skipped: class not found: $className")
                return null
            }
            val initScrollMethod = targetClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == initScrollMethodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 3 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
            } ?: run {
                Diagnostics.log(
                    "[ForumNativeTopShiftBlockHook] skipped: " +
                        "method mismatch: $className.$initScrollMethodName(Int, Boolean, Function0)",
                )
                return null
            }
            val smoothInitGetterMethod = targetClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER &&
                    candidate.returnType == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes.isEmpty()
            } ?: run {
                Diagnostics.log(
                    "[ForumNativeTopShiftBlockHook] skipped: " +
                        "method mismatch: $className." +
                        "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER}()",
                )
                return null
            }
            val setupMethod = targetClass.declaredMethods.singleOrNull { candidate ->
                candidate.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SETUP_METHOD &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.contentEquals(
                        arrayOf(
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                        ),
                    )
            } ?: run {
                Diagnostics.log(
                    "[ForumNativeTopShiftBlockHook] skipped: " +
                        "method mismatch: $className." +
                        "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SETUP_METHOD}" +
                        "(Int, Int, Int, Boolean)",
                )
                return null
            }
            val maxScrollGetterMethod =
                ScanReflection.collectInstanceMethods(targetClass).singleOrNull { candidate ->
                    candidate.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER &&
                        candidate.returnType == Int::class.javaPrimitiveType &&
                        candidate.parameterTypes.isEmpty()
                } ?: run {
                    Diagnostics.log(
                        "[ForumNativeTopShiftBlockHook] skipped: " +
                            "method mismatch: $className." +
                            "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER}()",
                    )
                    return null
                }
            initScrollMethod.isAccessible = true
            smoothInitGetterMethod.isAccessible = true
            setupMethod.isAccessible = true
            maxScrollGetterMethod.isAccessible = true
            ForumNativeTopShiftSymbols(
                initScrollMethod = initScrollMethod,
                smoothInitGetterMethod = smoothInitGetterMethod,
                setupMethod = setupMethod,
                maxScrollGetterMethod = maxScrollGetterMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[ForumNativeTopShiftBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val forumTopShiftCritical = ArrayList<String>(2)
        if (symbols[ForumTopShiftContract.forumBottomSheetViewClass].isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetViewClass")
        }
        if (symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod].isNullOrBlank()) {
            forumTopShiftCritical.add("forumBottomSheetInitScrollMethod")
        }
        out[HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT] = if (forumTopShiftCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = forumTopShiftCritical)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "ForumNativeTopShiftBlockHook",
            "${symbols[ForumTopShiftContract.forumBottomSheetViewClass]}." +
                "${symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod]}(Int,Boolean,Function0) / " +
                "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER}()[stable] / " +
                "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SETUP_METHOD}(Int,Int,Int,Boolean)[stable] / " +
                "${StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER}()[stable]",
            listOf(
                ForumTopShiftContract.forumBottomSheetViewClass.check(symbols),
                "forumBottomSheetInitScrollMethod" to
                    has(symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod]),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("ForumNativeTopShiftBlockHook", false, listOf(HookFeatureKey.DISABLE_FORUM_NATIVE_TOP_SHIFT)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasForumBottomSheetSymbols =
            symbols[ForumTopShiftContract.forumBottomSheetViewClass] != null ||
                symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod] != null
        if (hasForumBottomSheetSymbols && !isForumBottomSheetValid(symbols, cl)) return false
        return true
    }

    private fun isForumBottomSheetValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[ForumTopShiftContract.forumBottomSheetViewClass] ?: return false
        val initScrollMethodName = symbols[ForumTopShiftContract.forumBottomSheetInitScrollMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            if (!android.view.View::class.java.isAssignableFrom(targetClass)) return false
            val initScrollValid = targetClass.declaredMethods.count { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == initScrollMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
            } == 1
            val smoothInitGetterValid = targetClass.declaredMethods.count { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_SMOOTH_INIT_GETTER &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterTypes.isEmpty()
            } == 1
            val setupValid = targetClass.declaredMethods.count { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
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
            } == 1
            val maxScrollGetterValid = ScanReflection.collectInstanceMethods(targetClass).count { method ->
                method.name == StableTiebaHookPoints.FORUM_BOTTOM_SHEET_MAX_SCROLL_GETTER &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterTypes.isEmpty()
            } == 1
            initScrollValid &&
                smoothInitGetterValid &&
                setupValid &&
                maxScrollGetterValid
        } catch (_: Throwable) {
            false
        }
    }
}
