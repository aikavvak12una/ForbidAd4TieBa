package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

/**
 * Resolves the symbols the force-PB-preload feature needs.
 *
 * Currently one hook point: the boolean gate that opens the native
 * preload-render branch inside the host's abstract PB activity. The gate is
 * renamed on every host build (`A1` on 22.9.1.0, `B1` on 22.10.1.0), so it is
 * located through the unobfuscated `PbPreloadHelperKt` anchor rather than by
 * name, then re-checked against the concrete activity's method shape.
 */
internal object PbForcePreloadSymbolScanner {

    fun scanRenderGate(context: Context, cl: ClassLoader, logger: ScanLogger?): String? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "pbPreloadRenderGate: apk source path unavailable")
            return null
        }
        val gateName = DexKitSemanticScanner.scanPbPreloadRenderGate(
            sourcePaths = sourcePaths,
            absActivityClassName = StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS,
            logger = logger,
        ) ?: run {
            log(logger, "pbPreloadRenderGate: no semantic match")
            return null
        }
        // The gate is invoked on the abstract base but overridden on the concrete
        // activity, which is where the hook installs. Require that override to
        // exist with the exact boolean()/no-arg shape, else fail closed.
        val activityClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_ACTIVITY_CLASS, cl)
        if (activityClass == null) {
            log(logger, "pbPreloadRenderGate: class not found: ${StableTiebaHookPoints.PB_ACTIVITY_CLASS}")
            return null
        }
        val methods = scanDeclaredMethods("PbForcePreloadHook.RenderGate", activityClass, logger)
            ?: return null
        val shapeOk = methods.any { method ->
            method.name == gateName &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.isEmpty()
        }
        if (!shapeOk) {
            log(
                logger,
                "pbPreloadRenderGate: method shape mismatch: " +
                    "${StableTiebaHookPoints.PB_ACTIVITY_CLASS}.$gateName():boolean",
            )
            return null
        }
        if (!isPreloadSwitchValid(cl)) {
            log(logger, "pbPreloadRenderGate: isPbPreloadSwitchOn signature invalid or missing")
            return null
        }
        log(
            logger,
            "pbPreloadRenderGate matched: ${StableTiebaHookPoints.PB_ACTIVITY_CLASS}.$gateName",
        )
        return gateName
    }

    fun isCacheValid(cl: ClassLoader, gateName: String?): Boolean {
        if (gateName == null) return true
        if (!isPreloadSwitchValid(cl)) return false
        val clazz = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_ACTIVITY_CLASS, cl) ?: return false
        return scanSubStep("PbForcePreloadHook.RenderGate.Restore", null, false) {
            val method = clazz.getDeclaredMethod(gateName)
            !Modifier.isStatic(method.modifiers) && method.returnType == Boolean::class.javaPrimitiveType
        }
    }

    private fun isPreloadSwitchValid(cl: ClassLoader): Boolean {
        val clazz = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_SINGLETON_CLASS, cl) ?: return false
        return scanSubStep("PbForcePreloadHook.Switch", null, false) {
            val method = clazz.getDeclaredMethod("isPbPreloadSwitchOn")
            !Modifier.isStatic(method.modifiers) && method.returnType == Boolean::class.javaPrimitiveType
        }
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
