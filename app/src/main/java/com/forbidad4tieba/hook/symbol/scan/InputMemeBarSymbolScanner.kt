package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.InputMemeBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object InputMemeBarSymbolScanner {
    private const val CONTROLLER_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePanController"
    private const val INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"

    fun scan(cl: ClassLoader, logger: ScanLogger?): InputMemeBarScanSymbols {
        val controllerClass = ScanReflection.safeFindClass(CONTROLLER_CLASS, cl) ?: run {
            log(logger, "inputMemeBar: class not found: $CONTROLLER_CLASS")
            return InputMemeBarScanSymbols()
        }
        val inputShowTypeClass = ScanReflection.safeFindClass(INPUT_SHOW_TYPE_CLASS, cl) ?: run {
            log(logger, "inputMemeBar: class not found: $INPUT_SHOW_TYPE_CLASS")
            return InputMemeBarScanSymbols()
        }
        return scanResolvedClass(controllerClass, inputShowTypeClass, logger)
    }

    internal fun scanResolvedClass(
        controllerClass: Class<*>,
        inputShowTypeClass: Class<*>,
        logger: ScanLogger?,
    ): InputMemeBarScanSymbols {
        val methods = scanSubStep(
            "InputMemeBarBlockHook.Methods",
            logger,
            null,
        ) {
            controllerClass.declaredMethods.toList()
        } ?: return InputMemeBarScanSymbols()
        val candidates = methods.filter { method ->
            isInputMemeBarEnableMethod(method, inputShowTypeClass)
        }
        val method = candidates.singleOrNull()
        if (method == null) {
            log(
                logger,
                "inputMemeBar: expected=1 actual=${candidates.size} candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return InputMemeBarScanSymbols()
        }
        return InputMemeBarScanSymbols(
            controllerClass = controllerClass.name,
            enableMethod = method.name,
        )
    }

    internal fun isInputMemeBarEnableMethod(
        method: Method,
        inputShowTypeClass: Class<*>? = null,
    ): Boolean {
        val params = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 3 &&
            params[0] == Context::class.java &&
            (inputShowTypeClass?.let { params[1] == it } ?: (params[1].name == INPUT_SHOW_TYPE_CLASS)) &&
            params[2] == Boolean::class.javaPrimitiveType
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val returnType = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$returnType"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
