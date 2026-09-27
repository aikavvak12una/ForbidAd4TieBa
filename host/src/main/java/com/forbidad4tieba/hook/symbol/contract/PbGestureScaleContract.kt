package com.forbidad4tieba.hook.symbol.contract

import android.view.MotionEvent
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbCommentInteractionScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbGestureScaleSymbols
import com.forbidad4tieba.hook.symbol.scan.PbCommentInteractionSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PbGestureScaleContract : SymbolContract("PbGestureScale") {
    val pbGestureScaleManagerClass = text("pbGestureScaleManagerClass")
    val pbGestureScaleDispatchMethod = text("pbGestureScaleDispatchMethod")
    val pbGestureScaleListenerSetterMethod = text("pbGestureScaleListenerSetterMethod")
    val pbGestureScaleListenerClass = text("pbGestureScaleListenerClass")
    val pbGestureScaleListenerOnScaleMethod = text("pbGestureScaleListenerOnScaleMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val pbCommentInteractionScan = once(PbCommentInteractionSymbolScanner) {
runScanStep(
            "PbCommentInteractionHooks",
            logger,
            scanErrors,
            PbCommentInteractionScanSymbols(),
        ) {
            PbCommentInteractionSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        }

        val pbGestureScaleManagerClass: String? = pbCommentInteractionScan.gestureScaleManagerClass

        val pbGestureScaleDispatchMethod: String? = pbCommentInteractionScan.gestureScaleDispatchMethod

        val pbGestureScaleListenerSetterMethod: String? = pbCommentInteractionScan.gestureScaleListenerSetterMethod

        val pbGestureScaleListenerClass: String? = pbCommentInteractionScan.gestureScaleListenerClass

        val pbGestureScaleListenerOnScaleMethod: String? = pbCommentInteractionScan.gestureScaleListenerOnScaleMethod

        output[PbGestureScaleContract.pbGestureScaleManagerClass] = pbGestureScaleManagerClass
        output[PbGestureScaleContract.pbGestureScaleDispatchMethod] = pbGestureScaleDispatchMethod
        output[PbGestureScaleContract.pbGestureScaleListenerSetterMethod] = pbGestureScaleListenerSetterMethod
        output[PbGestureScaleContract.pbGestureScaleListenerClass] = pbGestureScaleListenerClass
        output[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod] = pbGestureScaleListenerOnScaleMethod
    }

    fun resolvePbGestureScaleSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbGestureScaleSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbDisableGestureFontScaleHook] skipped: scan symbols unavailable")
                return null
            }
            val managerClassName = resolvedSymbols[PbGestureScaleContract.pbGestureScaleManagerClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleManagerClass")
                return null
            }
            val dispatchMethodName = resolvedSymbols[PbGestureScaleContract.pbGestureScaleDispatchMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbDisableGestureFontScaleHook] skipped: missing pbGestureScaleDispatchMethod")
                return null
            }
            val managerClass = ScanReflection.safeFindClass(managerClassName, cl) ?: run {
                Diagnostics.log("[PbDisableGestureFontScaleHook] skipped: class not found: $managerClassName")
                return null
            }
            val dispatchMethod = managerClass.declaredMethods.singleOrNull { candidate ->
                !Modifier.isStatic(candidate.modifiers) &&
                    candidate.name == dispatchMethodName &&
                    candidate.returnType == Boolean::class.javaPrimitiveType &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == MotionEvent::class.java
            } ?: run {
                Diagnostics.log(
                    "[PbDisableGestureFontScaleHook] skipped: method mismatch: " +
                        "$managerClassName.$dispatchMethodName(MotionEvent)",
                )
                return null
            }
            dispatchMethod.isAccessible = true
            PbGestureScaleSymbols(dispatchMethod = dispatchMethod)
        } catch (t: Throwable) {
            Diagnostics.log("[PbDisableGestureFontScaleHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val pbGestureScaleCritical = ArrayList<String>(5)
        if (symbols[PbGestureScaleContract.pbGestureScaleManagerClass].isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleManagerClass")
        }
        if (symbols[PbGestureScaleContract.pbGestureScaleDispatchMethod].isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleDispatchMethod")
        }
        if (symbols[PbGestureScaleContract.pbGestureScaleListenerSetterMethod].isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerSetterMethod")
        }
        if (symbols[PbGestureScaleContract.pbGestureScaleListenerClass].isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerClass")
        }
        if (symbols[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod].isNullOrBlank()) {
            pbGestureScaleCritical.add("pbGestureScaleListenerOnScaleMethod")
        }
        out[HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE] = if (pbGestureScaleCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = pbGestureScaleCritical)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbDisableGestureFontScaleHook.Manager",
            "${symbols[PbGestureScaleContract.pbGestureScaleManagerClass]}.${symbols[PbGestureScaleContract.pbGestureScaleDispatchMethod]}/${symbols[PbGestureScaleContract.pbGestureScaleListenerSetterMethod]}",
            listOf(
                PbGestureScaleContract.pbGestureScaleManagerClass.check(symbols),
                PbGestureScaleContract.pbGestureScaleDispatchMethod.check(symbols),
                PbGestureScaleContract.pbGestureScaleListenerSetterMethod.check(symbols),
            ),
        )
        add(
            "PbDisableGestureFontScaleHook.Listener",
            "${symbols[PbGestureScaleContract.pbGestureScaleListenerClass]}.${symbols[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod]}",
            listOf(
                PbGestureScaleContract.pbGestureScaleListenerClass.check(symbols),
                PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbDisableGestureFontScaleHook.", true, listOf(HookFeatureKey.DISABLE_PB_GESTURE_FONT_SCALE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbGestureScaleSymbols =
            symbols[PbGestureScaleContract.pbGestureScaleManagerClass] != null ||
                symbols[PbGestureScaleContract.pbGestureScaleDispatchMethod] != null ||
                symbols[PbGestureScaleContract.pbGestureScaleListenerSetterMethod] != null ||
                symbols[PbGestureScaleContract.pbGestureScaleListenerClass] != null ||
                symbols[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod] != null
        if (hasPbGestureScaleSymbols && !isPbGestureScaleValid(symbols, cl)) return false
        return true
    }

    private fun isPbGestureScaleValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val managerClassName = symbols[PbGestureScaleContract.pbGestureScaleManagerClass] ?: return false
        val dispatchMethodName = symbols[PbGestureScaleContract.pbGestureScaleDispatchMethod] ?: return false
        val listenerSetterMethodName = symbols[PbGestureScaleContract.pbGestureScaleListenerSetterMethod] ?: return false
        val listenerClassName = symbols[PbGestureScaleContract.pbGestureScaleListenerClass] ?: return false
        val onScaleMethodName = symbols[PbGestureScaleContract.pbGestureScaleListenerOnScaleMethod] ?: return false
        return try {
            val managerClass = ScanReflection.safeFindClass(managerClassName, cl) ?: return false
            val listenerClass = ScanReflection.safeFindClass(listenerClassName, cl) ?: return false

            val hasScaleDetectorField = managerClass.declaredFields.any {
                it.type == android.view.ScaleGestureDetector::class.java
            }
            if (!hasScaleDetectorField) return false

            val dispatchMethod = managerClass.declaredMethods.any { method ->
                method.name == dispatchMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == android.view.MotionEvent::class.java
            }
            if (!dispatchMethod) return false

            val setterMethod = managerClass.declaredMethods.any { method ->
                method.name == listenerSetterMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isInterface
            }
            if (!setterMethod) return false

            val parentClass = listenerClass.superclass ?: return false
            if (!android.view.ScaleGestureDetector.SimpleOnScaleGestureListener::class.java.isAssignableFrom(parentClass)) {
                return false
            }
            listenerClass.declaredMethods.any { method ->
                method.name == onScaleMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == android.view.ScaleGestureDetector::class.java
            }
        } catch (_: Throwable) {
            false
        }
    }
}
