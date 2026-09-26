package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassSortSwitchSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** The background fills a clipped path; the selected slide rebuilds its own path before drawing. */
internal object SortSwitchSymbolScanner {
    private const val TAG = "HomeNativeGlassHook.SortSwitch"
    private const val OWNER = StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS
    private const val CANVAS = "android.graphics.Canvas"
    private const val PATH = "android.graphics.Path"
    private const val PAINT = "android.graphics.Paint"
    private const val RECT = "android.graphics.RectF"
    private const val DIRECTION = "$PATH\$Direction"
    private const val SKIN = "com.baidu.tbadk.core.util.SkinManager"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"

    fun scan(context: Context, logger: ScanLogger?): HomeNativeGlassSortSwitchSymbols =
        scanSubStep(TAG, logger, HomeNativeGlassSortSwitchSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) {
                scan(it.bridge, logger)
            } ?: missing(logger, "DexKit bridge unavailable")
        }

    private fun scan(bridge: DexKitBridge, logger: ScanLogger?): HomeNativeGlassSortSwitchSymbols {
        val owner = bridge.getClassData(OWNER) ?: return missing(logger, "class missing: $OWNER")
        val onDraw = unique("OnDraw", owner.methods.filter {
            it.methodName == "onDraw" && isCanvasMethod(it)
        }, logger) { it.descriptor } ?: return HomeNativeGlassSortSwitchSymbols()
        if (!onDraw.calls(CANVAS, "clipPath", "boolean", PATH)) {
            return missing(logger, "onDraw does not clip a path")
        }
        val clippedPaths = onDraw.readFields(PATH).mapTo(HashSet()) { it.descriptor }
        val drawMethods = onDraw.invokes.filter {
            it.declaredClassName == OWNER && isCanvasMethod(it) && it.descriptor != onDraw.descriptor
        }.distinctBy { it.descriptor }
        val backgrounds = drawMethods.mapNotNull { method ->
            initialFill(method)?.takeIf { it.path.descriptor in clippedPaths }
        }
        val background = unique("BackgroundFill", backgrounds, logger) { it.method.descriptor }
        val slide = unique("SlideDraw", drawMethods.filter(::isSlideDraw), logger) { it.descriptor }
        val slidePath = slide?.let {
            unique("SlidePath", it.readFields(PATH), logger) { field -> field.descriptor }
        }
        return HomeNativeGlassSortSwitchSymbols(
            backgroundPaintField = background?.paint?.fieldName,
            slideDrawMethod = slide?.takeIf { slidePath != null }?.methodName,
            slidePathField = slidePath?.fieldName,
        )
    }

    private data class Fill(val method: MethodData, val path: FieldData, val paint: FieldData)

    private fun initialFill(method: MethodData): Fill? {
        val firstCall = method.invokes.firstOrNull() ?: return null
        if (!firstCall.matches(CANVAS, "drawPath", "void", PATH, PAINT)) return null
        val ops = method.opNames
        val callIndex = ops.indexOfFirst { it.startsWith("invoke-") }
        if (callIndex < 0) return null
        var prefix = ops.take(callIndex)
        var fields = method.usingFields
        if (prefix.take(2) == listOf("sget-object", "if-nez")) {
            val guard = fields.firstOrNull() ?: return null
            if (!guard.usingType.isRead() || !Modifier.isStatic(guard.field.modifiers) ||
                guard.field.typeName != INTERCEPTABLE) return null
            prefix = prefix.drop(2)
            fields = fields.drop(1)
        }
        // No constants, helper results, casts, or other object sources precede this draw.
        // Its Path/Paint arguments must come from this one pair of instance reads; declaration
        // order and method order carry no meaning. Unknown instruction shapes are rejected.
        if (prefix != listOf("iget-object", "iget-object") ||
            ops[callIndex] !in setOf("invoke-virtual", "invoke-virtual/range")) return null
        val pair = fields.take(2)
        if (pair.size != 2 || pair.any {
                !it.usingType.isRead() || it.field.declaredClassName != OWNER ||
                    Modifier.isStatic(it.field.modifiers)
            }) return null
        val path = pair.singleOrNull { it.field.typeName == PATH }?.field ?: return null
        val paint = pair.singleOrNull { it.field.typeName == PAINT }?.field ?: return null
        return Fill(method, path, paint)
    }

    private fun isSlideDraw(method: MethodData): Boolean =
        method.calls(PATH, "reset", "void") &&
            method.calls(PATH, "addRoundRect", "void", RECT, "float", "float", DIRECTION) &&
            method.calls(RECT, "set", "void", "float", "float", "float", "float") &&
            method.calls(CANVAS, "drawPath", "void", PATH, PAINT) &&
            method.calls(CANVAS, "drawRoundRect", "void", RECT, "float", "float", PAINT) &&
            method.calls(PAINT, "reset", "void") && method.calls(PAINT, "setColor", "void", "int") &&
            method.invokes.any { it.declaredClassName == SKIN && it.methodName == "getColor" && it.returnTypeName == "int" } &&
            method.readFields(PAINT).size == 1 &&
            method.invokes.none {
                it.returnTypeName == PATH || (it.declaredClassName == PATH && it.isConstructor) ||
                    (it.declaredClassName != CANVAS && CANVAS in it.paramTypeNames) ||
                    (it.declaredClassName == CANVAS && it.methodName !in setOf("drawPath", "drawRoundRect"))
            }

    private fun isCanvasMethod(method: MethodData): Boolean =
        !Modifier.isStatic(method.modifiers) && method.returnTypeName == "void" &&
            method.paramTypeNames == listOf(CANVAS)

    private fun MethodData.readFields(type: String): List<FieldData> = usingFields.filter {
        it.usingType.isRead() && it.field.declaredClassName == OWNER &&
            !Modifier.isStatic(it.field.modifiers) && it.field.typeName == type
    }.map { it.field }.distinctBy { it.descriptor }

    private fun MethodData.calls(owner: String, name: String, returns: String, vararg params: String): Boolean =
        invokes.any { it.matches(owner, name, returns, *params) }

    private fun MethodData.matches(owner: String, name: String, returns: String, vararg params: String): Boolean =
        declaredClassName == owner && methodName == name && returnTypeName == returns && paramTypeNames == params.toList()

    private fun <T> unique(role: String, candidates: List<T>, logger: ScanLogger?, describe: (T) -> String): T? {
        if (candidates.size == 1) return candidates.single()
        missing(logger, "$role candidates=" + candidates.joinToString(",", transform = describe).ifEmpty { "-" })
        return null
    }

    private fun missing(logger: ScanLogger?, detail: String): HomeNativeGlassSortSwitchSymbols {
        HookSymbolScanSession.get()?.scanErrors?.let {
            HookSymbolScanDiagnostics.recordScanIssue(logger, TAG, it, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$TAG $detail")
        return HomeNativeGlassSortSwitchSymbols()
    }
}
