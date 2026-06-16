package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.view.View
import android.widget.EditText
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PbLikeAutoReplySymbolScanner {
    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): PbLikeAutoReplyScanSymbols {
        val agreeViewClass = safeFindClass(StableTiebaHookPoints.AGREE_VIEW_CLASS, cl)
        if (agreeViewClass == null) {
            log(logger, "pbLikeAutoReply: AgreeView class not found")
            return PbLikeAutoReplyScanSymbols()
        }

        val agreeViewMethods = instanceMethods("AgreeView", agreeViewClass, logger)
            ?: return PbLikeAutoReplyScanSymbols(agreeViewClass = agreeViewClass.name)
        val getDataMethod = agreeViewMethods.firstOrNull { method ->
            method.name == StableTiebaHookPoints.METHOD_GET_DATA &&
                method.parameterTypes.isEmpty() &&
                method.returnType.name == StableTiebaHookPoints.AGREE_DATA_CLASS
        }
        if (getDataMethod == null) {
            log(logger, "pbLikeAutoReply: AgreeView.getData() not found or return mismatch")
            return PbLikeAutoReplyScanSymbols(agreeViewClass = agreeViewClass.name)
        }

        val agreeDataClass = getDataMethod.returnType
        val agreeDataFields = instanceFields("AgreeData", agreeDataClass, logger).orEmpty()
        val hasAgreeField = agreeDataFields.firstOrNull { field ->
            field.name == "hasAgree" && isBooleanType(field.type)
        }
        val agreeTypeField = agreeDataFields.firstOrNull { field ->
            field.name == "agreeType" && isIntType(field.type)
        }
        val isInThreadField = agreeDataFields.firstOrNull { field ->
            field.name == "isInThread" && isBooleanType(field.type)
        }
        if (hasAgreeField == null || agreeTypeField == null || isInThreadField == null) {
            log(
                logger,
                "pbLikeAutoReply: AgreeData fields missing, hasAgree=${hasAgreeField?.name}, " +
                    "agreeType=${agreeTypeField?.name}, isInThread=${isInThreadField?.name}",
            )
        }

        val agreeClickMethod = scanAgreeClickMethodFromDex(context, agreeViewClass, logger)
        if (agreeClickMethod == null) {
            log(logger, "pbLikeAutoReply: AgreeView like click method not found")
        }

        val inputContainerClass = safeFindClass(StableTiebaHookPoints.PB_NEW_INPUT_CONTAINER_CLASS, cl)
        if (inputContainerClass == null) {
            log(logger, "pbLikeAutoReply: PbNewInputContainer class not found")
            return PbLikeAutoReplyScanSymbols(
                agreeViewClass = agreeViewClass.name,
                agreeClickMethod = agreeClickMethod,
                agreeViewGetDataMethod = getDataMethod.name,
                agreeDataClass = agreeDataClass.name,
                agreeDataHasAgreeField = hasAgreeField?.name,
                agreeDataAgreeTypeField = agreeTypeField?.name,
                agreeDataIsInThreadField = isInThreadField?.name,
            )
        }

        val inputMethods = instanceMethods("PbNewInputContainer", inputContainerClass, logger).orEmpty()
        val getInputViewMethod = inputMethods.firstOrNull { method ->
            method.name == StableTiebaHookPoints.METHOD_GET_INPUT_VIEW &&
                method.parameterTypes.isEmpty() &&
                (EditText::class.java.isAssignableFrom(method.returnType) ||
                    View::class.java.isAssignableFrom(method.returnType))
        }
        val getSendViewMethod = inputMethods.firstOrNull { method ->
            method.name == StableTiebaHookPoints.METHOD_GET_SEND_VIEW &&
                method.parameterTypes.isEmpty() &&
                View::class.java.isAssignableFrom(method.returnType)
        }
        if (getInputViewMethod == null || getSendViewMethod == null) {
            log(
                logger,
                "pbLikeAutoReply: PbNewInputContainer methods missing, " +
                    "input=${getInputViewMethod?.name}, send=${getSendViewMethod?.name}",
            )
        }

        val symbols = PbLikeAutoReplyScanSymbols(
            agreeViewClass = agreeViewClass.name,
            agreeClickMethod = agreeClickMethod,
            agreeViewGetDataMethod = getDataMethod.name,
            agreeDataClass = agreeDataClass.name,
            agreeDataHasAgreeField = hasAgreeField?.name,
            agreeDataAgreeTypeField = agreeTypeField?.name,
            agreeDataIsInThreadField = isInThreadField?.name,
            inputContainerClass = inputContainerClass.name,
            inputContainerGetInputViewMethod = getInputViewMethod?.name,
            inputContainerGetSendViewMethod = getSendViewMethod?.name,
        )
        log(
            logger,
            "pbLikeAutoReply matched: ${symbols.agreeViewClass}.${symbols.agreeClickMethod}/" +
                "${symbols.agreeViewGetDataMethod} " +
                "data=${symbols.agreeDataClass}[${symbols.agreeDataHasAgreeField}," +
                "${symbols.agreeDataAgreeTypeField},${symbols.agreeDataIsInThreadField}] input=${symbols.inputContainerClass}." +
                "{${symbols.inputContainerGetInputViewMethod},${symbols.inputContainerGetSendViewMethod}}",
        )
        return symbols
    }

    private fun scanAgreeClickMethodFromDex(
        context: Context,
        agreeViewClass: Class<*>,
        logger: ScanLogger?,
    ): String? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "pbLikeAutoReplyDex: apk source path unavailable")
            return null
        }
        val matches = DexShareIconScanner.scanPbLikeAgreeClick(
            sourcePaths = sourcePaths,
            ownerClassName = agreeViewClass.name,
            logger = logger,
        )
            .filter { match -> isAgreeClickMethodNameValid(agreeViewClass, match.ownerMethodName, logger) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexPbLikeAgreeClickMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )

        val best = matches.firstOrNull() ?: run {
            log(logger, "pbLikeAutoReplyDex: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 12) {
            log(
                logger,
                "pbLikeAutoReplyDex ambiguous: best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }
        log(
            logger,
            "pbLikeAutoReplyDex matched: ${agreeViewClass.name}.${best.ownerMethodName} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return best.ownerMethodName
    }

    private fun isAgreeClickMethodNameValid(
        agreeViewClass: Class<*>,
        methodName: String,
        logger: ScanLogger?,
    ): Boolean {
        val methods = declaredMethods("AgreeView.ValidateClick", agreeViewClass, logger) ?: return false
        return methods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == View::class.java
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

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        ScanReflection.collectInstanceMethods(clazz)

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("PbLikeAutoReplyHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun instanceFields(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Field>? {
        return scanSubStep("PbLikeAutoReplyHook.$label.InstanceFields", logger, null) {
            collectInstanceFields(clazz)
        }
    }

    private fun instanceMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("PbLikeAutoReplyHook.$label.InstanceMethods", logger, null) {
            collectInstanceMethods(clazz)
        }
    }

    private fun isBooleanType(type: Class<*>): Boolean =
        ScanReflection.isBooleanType(type)

    private fun isIntType(type: Class<*>): Boolean =
        ScanReflection.isIntType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
