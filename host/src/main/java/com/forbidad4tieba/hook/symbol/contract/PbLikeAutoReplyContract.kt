package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbAutoReplyFlowSymbols
import com.forbidad4tieba.hook.symbol.model.PbLikeAutoReplyScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbLikeAutoReplySymbols
import com.forbidad4tieba.hook.symbol.scan.PbAutoReplyFlowSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbLikeAutoReplySymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PbLikeAutoReplyContract : SymbolContract("PbLikeAutoReply") {
    internal override val candidateClasses = listOf(
        StableTiebaHookPoints.AGREE_VIEW_CLASS,
        StableTiebaHookPoints.AGREE_DATA_CLASS,
        StableTiebaHookPoints.PB_NEW_INPUT_CONTAINER_CLASS,
    )

    val pbLikeAutoReplyAgreeViewClass = text("pbLikeAutoReplyAgreeViewClass")
    val pbLikeAutoReplyAgreeClickMethod = text("pbLikeAutoReplyAgreeClickMethod")
    val pbLikeAutoReplyAgreeViewGetDataMethod = text("pbLikeAutoReplyAgreeViewGetDataMethod")
    val pbLikeAutoReplyAgreeDataClass = text("pbLikeAutoReplyAgreeDataClass")
    val pbLikeAutoReplyAgreeDataHasAgreeField = text("pbLikeAutoReplyAgreeDataHasAgreeField")
    val pbLikeAutoReplyAgreeDataAgreeTypeField = text("pbLikeAutoReplyAgreeDataAgreeTypeField")
    val pbLikeAutoReplyAgreeDataIsInThreadField = text("pbLikeAutoReplyAgreeDataIsInThreadField")
    val pbLikeAutoReplyInputContainerClass = text("pbLikeAutoReplyInputContainerClass")
    val pbLikeAutoReplyInputContainerGetInputViewMethod = text("pbLikeAutoReplyInputContainerGetInputViewMethod")
    val pbLikeAutoReplyInputContainerGetSendViewMethod = text("pbLikeAutoReplyInputContainerGetSendViewMethod")
    val pbAutoReplyFlow = nested("pbAutoReplyFlow", PbAutoReplyFlowSymbols(), PbAutoReplyFlowSymbols::fromJson, PbAutoReplyFlowSymbols::toJson)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val pbLikeAutoReplyScan = runScanStep(
            "PbLikeAutoReplyHook",
            logger,
            scanErrors,
            PbLikeAutoReplyScanSymbols(),
        ) {
            PbLikeAutoReplySymbolScanner.scan(context, cl, logger)
        }

        val pbLikeAutoReplyAgreeViewClass: String? = pbLikeAutoReplyScan.agreeViewClass

        val pbLikeAutoReplyAgreeClickMethod: String? = pbLikeAutoReplyScan.agreeClickMethod

        val pbLikeAutoReplyAgreeViewGetDataMethod: String? = pbLikeAutoReplyScan.agreeViewGetDataMethod

        val pbLikeAutoReplyAgreeDataClass: String? = pbLikeAutoReplyScan.agreeDataClass

        val pbLikeAutoReplyAgreeDataHasAgreeField: String? = pbLikeAutoReplyScan.agreeDataHasAgreeField

        val pbLikeAutoReplyAgreeDataAgreeTypeField: String? = pbLikeAutoReplyScan.agreeDataAgreeTypeField

        val pbLikeAutoReplyAgreeDataIsInThreadField: String? = pbLikeAutoReplyScan.agreeDataIsInThreadField

        val pbLikeAutoReplyInputContainerClass: String? = pbLikeAutoReplyScan.inputContainerClass

        val pbLikeAutoReplyInputContainerGetInputViewMethod: String? = pbLikeAutoReplyScan.inputContainerGetInputViewMethod

        val pbLikeAutoReplyInputContainerGetSendViewMethod: String? = pbLikeAutoReplyScan.inputContainerGetSendViewMethod

        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass] = pbLikeAutoReplyAgreeViewClass
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod] = pbLikeAutoReplyAgreeClickMethod
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod] = pbLikeAutoReplyAgreeViewGetDataMethod
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass] = pbLikeAutoReplyAgreeDataClass
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField] = pbLikeAutoReplyAgreeDataHasAgreeField
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField] = pbLikeAutoReplyAgreeDataAgreeTypeField
        output[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField] = pbLikeAutoReplyAgreeDataIsInThreadField
        output[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass] = pbLikeAutoReplyInputContainerClass
        output[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod] = pbLikeAutoReplyInputContainerGetInputViewMethod
        output[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod] = pbLikeAutoReplyInputContainerGetSendViewMethod
        output[PbLikeAutoReplyContract.pbAutoReplyFlow] = pbLikeAutoReplyScan.flow
    }

    fun resolvePbLikeAutoReplySymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbLikeAutoReplySymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        fun resolveClass(name: String): Class<*> {
            return ScanReflection.safeFindClass(name, cl) ?: error("class not found: $name")
        }

        fun resolveAgreeClickMethod(clazz: Class<*>, methodName: String): Method {
            return clazz.declaredMethods.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == View::class.java
            }?.apply { isAccessible = true }
                ?: error("method not found: ${clazz.name}.$methodName(View)")
        }

        fun resolveNoArgMethod(clazz: Class<*>, methodName: String): Method {
            return clazz.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true }
                ?: error("method not found: ${clazz.name}.$methodName()")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbLikeAutoReplyHook] skipped: scan symbols unavailable")
                return null
            }
            val agreeViewClass = resolveClass(
                requireSymbol("pbLikeAutoReplyAgreeViewClass", resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass]),
            )
            val inputContainerClass = resolveClass(
                requireSymbol("pbLikeAutoReplyInputContainerClass", resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass]),
            )
            val agreeClickMethod = resolveAgreeClickMethod(
                agreeViewClass,
                requireSymbol("pbLikeAutoReplyAgreeClickMethod", resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod]),
            )
            val getDataMethod = resolveNoArgMethod(
                agreeViewClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeViewGetDataMethod",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod],
                ),
            )
            val agreeDataClass = resolveClass(
                requireSymbol("pbLikeAutoReplyAgreeDataClass", resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass]),
            )
            if (!agreeDataClass.isAssignableFrom(getDataMethod.returnType)) {
                error("agreeViewGetDataMethod return mismatch: ${getDataMethod.returnType.name}")
            }
            val hasAgreeField = MemberAccess.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataHasAgreeField",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField],
                ),
            )
            val agreeTypeField = MemberAccess.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataAgreeTypeField",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField],
                ),
            )
            val isInThreadField = MemberAccess.findField(
                agreeDataClass,
                requireSymbol(
                    "pbLikeAutoReplyAgreeDataIsInThreadField",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField],
                ),
            )
            val getInputViewMethod = resolveNoArgMethod(
                inputContainerClass,
                requireSymbol(
                    "pbLikeAutoReplyInputContainerGetInputViewMethod",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod],
                ),
            )
            if (
                !EditText::class.java.isAssignableFrom(getInputViewMethod.returnType) &&
                !View::class.java.isAssignableFrom(getInputViewMethod.returnType)
            ) {
                error("inputContainerGetInputViewMethod return mismatch: ${getInputViewMethod.returnType.name}")
            }
            val getSendViewMethod = resolveNoArgMethod(
                inputContainerClass,
                requireSymbol(
                    "pbLikeAutoReplyInputContainerGetSendViewMethod",
                    resolvedSymbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod],
                ),
            )
            if (!View::class.java.isAssignableFrom(getSendViewMethod.returnType)) {
                error("inputContainerGetSendViewMethod return mismatch: ${getSendViewMethod.returnType.name}")
            }

            PbLikeAutoReplySymbols(
                agreeViewClass = agreeViewClass,
                inputContainerClass = inputContainerClass,
                agreeClickMethod = agreeClickMethod,
                getDataMethod = getDataMethod,
                hasAgreeField = hasAgreeField,
                agreeTypeField = agreeTypeField,
                isInThreadField = isInThreadField,
                getInputViewMethod = getInputViewMethod,
                getSendViewMethod = getSendViewMethod,
                flow = PbAutoReplyFlowSymbolScanner.restore(cl, resolvedSymbols[PbLikeAutoReplyContract.pbAutoReplyFlow])
                    ?: error("native ordinary reply flow unavailable"),
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbLikeAutoReplyHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val pbLikeAutoReplyCritical = ArrayList<String>(8)
        pbLikeAutoReplyCritical.addAll(symbols[PbLikeAutoReplyContract.pbAutoReplyFlow].missing())
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeViewClass")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeClickMethod")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeViewGetDataMethod")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataClass")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataHasAgreeField")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataAgreeTypeField")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyAgreeDataIsInThreadField")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerClass")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerGetInputViewMethod")
        }
        if (symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod].isNullOrBlank()) {
            pbLikeAutoReplyCritical.add("pbLikeAutoReplyInputContainerGetSendViewMethod")
        }
        out[HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY] = if (pbLikeAutoReplyCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbLikeAutoReplyCritical,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbLikeAutoReplyHook",
            "${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass]}.${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod]}(View) / " +
                "${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod]} -> " +
                "${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass]}.{${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod]}," +
                "${symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod]}}",
            listOf(
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField.check(symbols),
                PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass.check(symbols),
                "pbLikeAutoReplyInputContainerGetInputViewMethod" to
                    has(symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod]),
                "pbLikeAutoReplyInputContainerGetSendViewMethod" to
                    has(symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod]),
                "pbAutoReplyFlow" to symbols[PbLikeAutoReplyContract.pbAutoReplyFlow].missing().isEmpty(),
            ),
        )
        add(
            "PbLikeAutoReplyHook.Flow",
            symbols[PbLikeAutoReplyContract.pbAutoReplyFlow].fields().joinToString { "${it.first}=${it.second}" },
            symbols[PbLikeAutoReplyContract.pbAutoReplyFlow].fields().map { (name, value) -> "pbAutoReplyFlow.$name" to has(value) },
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbLikeAutoReplyHook", false, listOf(HookFeatureKey.ENABLE_PB_LIKE_AUTO_REPLY)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbLikeAutoReplySymbols =
            symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod] != null ||
                symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod] != null
        if (hasPbLikeAutoReplySymbols && !isPbLikeAutoReplyValid(symbols, cl)) return false
        return true
    }

    private fun isPbLikeAutoReplyValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (com.forbidad4tieba.hook.symbol.scan.PbAutoReplyFlowSymbolScanner.restore(cl, symbols[PbLikeAutoReplyContract.pbAutoReplyFlow]) == null) return false
        val agreeViewClassName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewClass] ?: return false
        val agreeClickMethodName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeClickMethod] ?: return false
        val getDataMethodName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeViewGetDataMethod] ?: return false
        val agreeDataClassName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataClass] ?: return false
        val hasAgreeFieldName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataHasAgreeField] ?: return false
        val agreeTypeFieldName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataAgreeTypeField] ?: return false
        val isInThreadFieldName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyAgreeDataIsInThreadField] ?: return false
        val inputContainerClassName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerClass] ?: return false
        val getInputViewMethodName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetInputViewMethod] ?: return false
        val getSendViewMethodName = symbols[PbLikeAutoReplyContract.pbLikeAutoReplyInputContainerGetSendViewMethod] ?: return false
        return try {
            val agreeViewClass = ScanReflection.safeFindClass(agreeViewClassName, cl) ?: return false
            val agreeDataClass = ScanReflection.safeFindClass(agreeDataClassName, cl) ?: return false
            val inputContainerClass = ScanReflection.safeFindClass(inputContainerClassName, cl) ?: return false
            if (!LinearLayout::class.java.isAssignableFrom(agreeViewClass)) return false
            if (!LinearLayout::class.java.isAssignableFrom(inputContainerClass)) return false

            val clickMethodOk = agreeViewClass.declaredMethods.any { method ->
                method.name == agreeClickMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == View::class.java
            }
            if (!clickMethodOk) return false

            val getDataOk = ScanReflection.collectInstanceMethods(agreeViewClass).any { method ->
                method.name == getDataMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == agreeDataClass
            }
            if (!getDataOk) return false

            val fields = ScanReflection.collectInstanceFields(agreeDataClass)
            val hasAgreeOk = fields.any { field ->
                field.name == hasAgreeFieldName && ScanReflection.isBooleanType(field.type)
            }
            if (!hasAgreeOk) return false
            val agreeTypeOk = fields.any { field ->
                field.name == agreeTypeFieldName && ScanReflection.isIntType(field.type)
            }
            if (!agreeTypeOk) return false
            val isInThreadOk = fields.any { field ->
                field.name == isInThreadFieldName && ScanReflection.isBooleanType(field.type)
            }
            if (!isInThreadOk) return false

            val inputMethods = ScanReflection.collectInstanceMethods(inputContainerClass)
            val inputOk = inputMethods.any { method ->
                method.name == getInputViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    (EditText::class.java.isAssignableFrom(method.returnType) ||
                        View::class.java.isAssignableFrom(method.returnType))
            }
            if (!inputOk) return false
            inputMethods.any { method ->
                method.name == getSendViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    View::class.java.isAssignableFrom(method.returnType)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
