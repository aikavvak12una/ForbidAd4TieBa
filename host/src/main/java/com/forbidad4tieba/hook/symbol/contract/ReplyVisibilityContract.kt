package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.ReplyVisibilityProbeScanSymbols
import com.forbidad4tieba.hook.symbol.model.ReplyVisibilityProbeSymbols
import com.forbidad4tieba.hook.symbol.scan.ReplyVisibilityProbeSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier
import org.json.JSONObject

/** Owns the cached descriptors and host rules for this capability. */
object ReplyVisibilityContract : SymbolContract("ReplyVisibility") {
    val replyVisibilityProbeReplyResponseClass = text("replyVisibilityProbeReplyResponseClass")
    val replyVisibilityProbeReplyDecodeMethod = text("replyVisibilityProbeReplyDecodeMethod")
    val replyVisibilityProbeReplyResultJsonField = text("replyVisibilityProbeReplyResultJsonField")
    val replyVisibilityProbeAddPostRequestClass = text("replyVisibilityProbeAddPostRequestClass")
    val replyVisibilityProbeAddPostRequestDataField = text("replyVisibilityProbeAddPostRequestDataField")
    val replyVisibilityProbeResponsedMessageClass = text("replyVisibilityProbeResponsedMessageClass")
    val replyVisibilityProbeGetOriginalMessageMethod = text("replyVisibilityProbeGetOriginalMessageMethod")
    val replyVisibilityProbeMessageClass = text("replyVisibilityProbeMessageClass")
    val replyVisibilityProbeMessageGetExtraMethod = text("replyVisibilityProbeMessageGetExtraMethod")
    val replyVisibilityProbeMessageGetTagMethod = text("replyVisibilityProbeMessageGetTagMethod")
    val replyVisibilityProbeMessageSetTagMethod = text("replyVisibilityProbeMessageSetTagMethod")
    val replyVisibilityProbeHttpMessageClass = text("replyVisibilityProbeHttpMessageClass")
    val replyVisibilityProbeHttpMessageConstructor = text("replyVisibilityProbeHttpMessageConstructor")
    val replyVisibilityProbeHttpMessageAddParamMethod = text("replyVisibilityProbeHttpMessageAddParamMethod")
    val replyVisibilityProbeHttpMessageAddHeaderMethod = text("replyVisibilityProbeHttpMessageAddHeaderMethod")
    val replyVisibilityProbeMessageManagerClass = text("replyVisibilityProbeMessageManagerClass")
    val replyVisibilityProbeMessageManagerGetInstanceMethod = text("replyVisibilityProbeMessageManagerGetInstanceMethod")
    val replyVisibilityProbeMessageManagerFindTaskMethod = text("replyVisibilityProbeMessageManagerFindTaskMethod")
    val replyVisibilityProbeMessageManagerRegisterTaskMethod = text("replyVisibilityProbeMessageManagerRegisterTaskMethod")
    val replyVisibilityProbeMessageManagerSendMethod = text("replyVisibilityProbeMessageManagerSendMethod")
    val replyVisibilityProbeTbHttpMessageTaskClass = text("replyVisibilityProbeTbHttpMessageTaskClass")
    val replyVisibilityProbeTbHttpMessageTaskConstructor = text("replyVisibilityProbeTbHttpMessageTaskConstructor")
    val replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod = text("replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod")
    val replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod = text("replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod")
    val replyVisibilityProbeBdUniqueIdClass = text("replyVisibilityProbeBdUniqueIdClass")
    val replyVisibilityProbeBdUniqueIdGenMethod = text("replyVisibilityProbeBdUniqueIdGenMethod")
    val replyVisibilityProbeTbadkCoreApplicationClass = text("replyVisibilityProbeTbadkCoreApplicationClass")
    val replyVisibilityProbeTbadkCoreApplicationGetInstMethod = text("replyVisibilityProbeTbadkCoreApplicationGetInstMethod")
    val replyVisibilityProbeTbadkCoreApplicationGetZidMethod = text("replyVisibilityProbeTbadkCoreApplicationGetZidMethod")
    val replyVisibilityProbeTbConfigClass = text("replyVisibilityProbeTbConfigClass")
    val replyVisibilityProbeTbConfigServerAddressField = text("replyVisibilityProbeTbConfigServerAddressField")
    val replyVisibilityProbeTbConfigPbFloorAgreeUrlField = text("replyVisibilityProbeTbConfigPbFloorAgreeUrlField")
    val replyVisibilityProbeCmdConfigHttpClass = text("replyVisibilityProbeCmdConfigHttpClass")
    val replyVisibilityProbeCmdPbFloorAgreeField = text("replyVisibilityProbeCmdPbFloorAgreeField")
    val replyVisibilityProbeAgreeResponseClass = text("replyVisibilityProbeAgreeResponseClass")
    val replyVisibilityProbeAgreeDecodeLogicMethod = text("replyVisibilityProbeAgreeDecodeLogicMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        var replyVisibilityProbeScan = ReplyVisibilityProbeScanSymbols()

        replyVisibilityProbeScan = runScanStep(
            "ReplyVisibilityProbeHook",
            logger,
            scanErrors,
            ReplyVisibilityProbeScanSymbols(),
        ) {
            ReplyVisibilityProbeSymbolScanner.scan(cl, logger)
        }

        output[ReplyVisibilityContract.replyVisibilityProbeReplyResponseClass] = replyVisibilityProbeScan.replyResponseClass
        output[ReplyVisibilityContract.replyVisibilityProbeReplyDecodeMethod] = replyVisibilityProbeScan.replyDecodeMethod
        output[ReplyVisibilityContract.replyVisibilityProbeReplyResultJsonField] = replyVisibilityProbeScan.replyResultJsonField
        output[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestClass] = replyVisibilityProbeScan.addPostRequestClass
        output[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestDataField] = replyVisibilityProbeScan.addPostRequestDataField
        output[ReplyVisibilityContract.replyVisibilityProbeResponsedMessageClass] = replyVisibilityProbeScan.responsedMessageClass
        output[ReplyVisibilityContract.replyVisibilityProbeGetOriginalMessageMethod] = replyVisibilityProbeScan.getOriginalMessageMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageClass] = replyVisibilityProbeScan.messageClass
        output[ReplyVisibilityContract.replyVisibilityProbeMessageGetExtraMethod] = replyVisibilityProbeScan.messageGetExtraMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageGetTagMethod] = replyVisibilityProbeScan.messageGetTagMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageSetTagMethod] = replyVisibilityProbeScan.messageSetTagMethod
        output[ReplyVisibilityContract.replyVisibilityProbeHttpMessageClass] = replyVisibilityProbeScan.httpMessageClass
        output[ReplyVisibilityContract.replyVisibilityProbeHttpMessageConstructor] = replyVisibilityProbeScan.httpMessageConstructor
        output[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddParamMethod] = replyVisibilityProbeScan.httpMessageAddParamMethod
        output[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddHeaderMethod] = replyVisibilityProbeScan.httpMessageAddHeaderMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageManagerClass] = replyVisibilityProbeScan.messageManagerClass
        output[ReplyVisibilityContract.replyVisibilityProbeMessageManagerGetInstanceMethod] = replyVisibilityProbeScan.messageManagerGetInstanceMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageManagerFindTaskMethod] = replyVisibilityProbeScan.messageManagerFindTaskMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageManagerRegisterTaskMethod] = replyVisibilityProbeScan.messageManagerRegisterTaskMethod
        output[ReplyVisibilityContract.replyVisibilityProbeMessageManagerSendMethod] = replyVisibilityProbeScan.messageManagerSendMethod
        output[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskClass] = replyVisibilityProbeScan.tbHttpMessageTaskClass
        output[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskConstructor] = replyVisibilityProbeScan.tbHttpMessageTaskConstructor
        output[ReplyVisibilityContract.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod] = replyVisibilityProbeScan.httpMessageTaskSetResponsedClassMethod
        output[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod] = replyVisibilityProbeScan.tbHttpMessageTaskSetIsNeedTbsMethod
        output[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdClass] = replyVisibilityProbeScan.bdUniqueIdClass
        output[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdGenMethod] = replyVisibilityProbeScan.bdUniqueIdGenMethod
        output[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationClass] = replyVisibilityProbeScan.tbadkCoreApplicationClass
        output[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetInstMethod] = replyVisibilityProbeScan.tbadkCoreApplicationGetInstMethod
        output[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetZidMethod] = replyVisibilityProbeScan.tbadkCoreApplicationGetZidMethod
        output[ReplyVisibilityContract.replyVisibilityProbeTbConfigClass] = replyVisibilityProbeScan.tbConfigClass
        output[ReplyVisibilityContract.replyVisibilityProbeTbConfigServerAddressField] = replyVisibilityProbeScan.tbConfigServerAddressField
        output[ReplyVisibilityContract.replyVisibilityProbeTbConfigPbFloorAgreeUrlField] = replyVisibilityProbeScan.tbConfigPbFloorAgreeUrlField
        output[ReplyVisibilityContract.replyVisibilityProbeCmdConfigHttpClass] = replyVisibilityProbeScan.cmdConfigHttpClass
        output[ReplyVisibilityContract.replyVisibilityProbeCmdPbFloorAgreeField] = replyVisibilityProbeScan.cmdPbFloorAgreeField
        output[ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass] = replyVisibilityProbeScan.agreeResponseClass
        output[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod] = replyVisibilityProbeScan.agreeDecodeLogicMethod
    }

    private const val MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.MessageTask"

    private const val HTTP_MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.HttpMessageTask"

    private const val HTTP_RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpResponsedMessage"

    fun resolveReplyVisibilityProbeSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ReplyVisibilityProbeSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ReplyVisibilityProbeHook] skipped: scan symbols unavailable")
                return null
            }

            fun requireSymbol(name: String, value: String?): String {
                return value?.takeIf { it.isNotBlank() } ?: error("$name missing")
            }

            val replyResponseClassName = requireSymbol(
                "replyVisibilityProbeReplyResponseClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeReplyResponseClass],
            )
            val replyDecodeMethodName = requireSymbol(
                "replyVisibilityProbeReplyDecodeMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeReplyDecodeMethod],
            )
            val replyResultJsonFieldName = requireSymbol(
                "replyVisibilityProbeReplyResultJsonField",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeReplyResultJsonField],
            )
            val addPostRequestClassName = requireSymbol(
                "replyVisibilityProbeAddPostRequestClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestClass],
            )
            val addPostRequestDataFieldName = requireSymbol(
                "replyVisibilityProbeAddPostRequestDataField",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestDataField],
            )
            val responsedMessageClassName = requireSymbol(
                "replyVisibilityProbeResponsedMessageClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeResponsedMessageClass],
            )
            val getOriginalMessageMethodName = requireSymbol(
                "replyVisibilityProbeGetOriginalMessageMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeGetOriginalMessageMethod],
            )
            val messageClassName = requireSymbol(
                "replyVisibilityProbeMessageClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageClass],
            )
            val messageGetExtraMethodName = requireSymbol(
                "replyVisibilityProbeMessageGetExtraMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageGetExtraMethod],
            )
            val messageGetTagMethodName = requireSymbol(
                "replyVisibilityProbeMessageGetTagMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageGetTagMethod],
            )
            val messageSetTagMethodName = requireSymbol(
                "replyVisibilityProbeMessageSetTagMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageSetTagMethod],
            )
            val httpMessageClassName = requireSymbol(
                "replyVisibilityProbeHttpMessageClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageClass],
            )
            requireSymbol(
                "replyVisibilityProbeHttpMessageConstructor",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageConstructor],
            )
            val httpMessageAddParamMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageAddParamMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddParamMethod],
            )
            val httpMessageAddHeaderMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageAddHeaderMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddHeaderMethod],
            )
            val messageManagerClassName = requireSymbol(
                "replyVisibilityProbeMessageManagerClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerClass],
            )
            val messageManagerGetInstanceMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerGetInstanceMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerGetInstanceMethod],
            )
            val messageManagerFindTaskMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerFindTaskMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerFindTaskMethod],
            )
            val messageManagerRegisterTaskMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerRegisterTaskMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerRegisterTaskMethod],
            )
            val messageManagerSendMethodName = requireSymbol(
                "replyVisibilityProbeMessageManagerSendMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerSendMethod],
            )
            val tbHttpMessageTaskClassName = requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskClass],
            )
            requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskConstructor",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskConstructor],
            )
            val httpMessageTaskSetResponsedClassMethodName = requireSymbol(
                "replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod],
            )
            val tbHttpMessageTaskSetIsNeedTbsMethodName = requireSymbol(
                "replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod],
            )
            val bdUniqueIdClassName = requireSymbol(
                "replyVisibilityProbeBdUniqueIdClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdClass],
            )
            val bdUniqueIdGenMethodName = requireSymbol(
                "replyVisibilityProbeBdUniqueIdGenMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdGenMethod],
            )
            val tbadkCoreApplicationClassName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationClass],
            )
            val tbadkCoreApplicationGetInstMethodName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationGetInstMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetInstMethod],
            )
            val tbadkCoreApplicationGetZidMethodName = requireSymbol(
                "replyVisibilityProbeTbadkCoreApplicationGetZidMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetZidMethod],
            )
            val tbConfigClassName = requireSymbol(
                "replyVisibilityProbeTbConfigClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigClass],
            )
            val tbConfigServerAddressFieldName = requireSymbol(
                "replyVisibilityProbeTbConfigServerAddressField",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigServerAddressField],
            )
            val tbConfigPbFloorAgreeUrlFieldName = requireSymbol(
                "replyVisibilityProbeTbConfigPbFloorAgreeUrlField",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigPbFloorAgreeUrlField],
            )
            val cmdConfigHttpClassName = requireSymbol(
                "replyVisibilityProbeCmdConfigHttpClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeCmdConfigHttpClass],
            )
            val cmdPbFloorAgreeFieldName = requireSymbol(
                "replyVisibilityProbeCmdPbFloorAgreeField",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeCmdPbFloorAgreeField],
            )
            val agreeResponseClassName = requireSymbol(
                "replyVisibilityProbeAgreeResponseClass",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass],
            )
            val agreeDecodeLogicMethodName = requireSymbol(
                "replyVisibilityProbeAgreeDecodeLogicMethod",
                resolvedSymbols[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod],
            )

            val replyResponseClass = ScanReflection.safeFindClass(replyResponseClassName, cl) ?: error("class not found: $replyResponseClassName")
            val addPostRequestClass =
                ScanReflection.safeFindClass(addPostRequestClassName, cl) ?: error("class not found: $addPostRequestClassName")
            val responsedMessageClass =
                ScanReflection.safeFindClass(responsedMessageClassName, cl) ?: error("class not found: $responsedMessageClassName")
            val messageClass = ScanReflection.safeFindClass(messageClassName, cl) ?: error("class not found: $messageClassName")
            val httpMessageClass =
                ScanReflection.safeFindClass(httpMessageClassName, cl) ?: error("class not found: $httpMessageClassName")
            val messageManagerClass =
                ScanReflection.safeFindClass(messageManagerClassName, cl) ?: error("class not found: $messageManagerClassName")
            val messageTaskClass = ScanReflection.safeFindClass(MESSAGE_TASK_CLASS, cl) ?: error("class not found: $MESSAGE_TASK_CLASS")
            val httpMessageTaskClass =
                ScanReflection.safeFindClass(HTTP_MESSAGE_TASK_CLASS, cl) ?: error("class not found: $HTTP_MESSAGE_TASK_CLASS")
            val httpResponsedMessageClass =
                ScanReflection.safeFindClass(HTTP_RESPONSED_MESSAGE_CLASS, cl) ?: error("class not found: $HTTP_RESPONSED_MESSAGE_CLASS")
            val tbHttpMessageTaskClass =
                ScanReflection.safeFindClass(tbHttpMessageTaskClassName, cl) ?: error("class not found: $tbHttpMessageTaskClassName")
            val bdUniqueIdClass =
                ScanReflection.safeFindClass(bdUniqueIdClassName, cl) ?: error("class not found: $bdUniqueIdClassName")
            val tbadkCoreApplicationClass = ScanReflection.safeFindClass(tbadkCoreApplicationClassName, cl)
                ?: error("class not found: $tbadkCoreApplicationClassName")
            val tbConfigClass = ScanReflection.safeFindClass(tbConfigClassName, cl) ?: error("class not found: $tbConfigClassName")
            val cmdConfigHttpClass =
                ScanReflection.safeFindClass(cmdConfigHttpClassName, cl) ?: error("class not found: $cmdConfigHttpClassName")
            val agreeResponseClass =
                ScanReflection.safeFindClass(agreeResponseClassName, cl) ?: error("class not found: $agreeResponseClassName")
            if (!httpResponsedMessageClass.isAssignableFrom(agreeResponseClass)) {
                error("$agreeResponseClassName is not a HttpResponsedMessage")
            }

            val replyDecodeMethod = replyResponseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isReplyServerResponseDecodeMethod(method, replyDecodeMethodName)
            } ?: error("method not found: $replyResponseClassName.$replyDecodeMethodName(int,byte[])")
            val replyResultJsonField = replyResponseClass.declaredFields.singleOrNull { field ->
                field.name == replyResultJsonFieldName && JSONObject::class.java.isAssignableFrom(field.type)
            } ?: error("field not found: $replyResponseClassName.$replyResultJsonFieldName")
            val addPostRequestDataField = addPostRequestClass.declaredFields.singleOrNull { field ->
                field.name == addPostRequestDataFieldName &&
                    ReplyVisibilityProbeSymbolScanner.isStringMapField(field)
            } ?: error("field not found: $addPostRequestClassName.$addPostRequestDataFieldName")
            val getOriginalMessageMethod = responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getOriginalMessageMethodName &&
                    method.parameterTypes.isEmpty() &&
                    messageClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $responsedMessageClassName.$getOriginalMessageMethodName()")
            val messageGetExtraMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageGetExtraMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: error("method not found: $messageClassName.$messageGetExtraMethodName()")
            val messageGetTagMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageGetTagMethodName &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageClassName.$messageGetTagMethodName()")
            val messageSetTagMethod = messageClass.declaredMethods.singleOrNull { method ->
                method.name == messageSetTagMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == bdUniqueIdClass
            } ?: error("method not found: $messageClassName.$messageSetTagMethodName(BdUniqueId)")
            val httpMessageConstructor = httpMessageClass.declaredConstructors.singleOrNull { constructor ->
                constructor.parameterTypes.size == 1 && ScanReflection.isIntType(constructor.parameterTypes[0])
            } ?: error("constructor not found: $httpMessageClassName(int)")
            val httpMessageAddParamMethod = httpMessageClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageAddParamMethodName &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == Any::class.java
            } ?: error("method not found: $httpMessageClassName.$httpMessageAddParamMethodName(String,Object)")
            val httpMessageAddHeaderMethod = httpMessageClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageAddHeaderMethodName &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
            } ?: error("method not found: $httpMessageClassName.$httpMessageAddHeaderMethodName(String,String)")
            val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    messageManagerClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageManagerClassName.$messageManagerGetInstanceMethodName()")
            val messageManagerFindTaskMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerFindTaskMethodName &&
                    method.parameterTypes.size == 1 &&
                    ScanReflection.isIntType(method.parameterTypes[0]) &&
                    messageTaskClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $messageManagerClassName.$messageManagerFindTaskMethodName(int)")
            val messageManagerRegisterTaskMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerRegisterTaskMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageTaskClass
            } ?: error("method not found: $messageManagerClassName.$messageManagerRegisterTaskMethodName(MessageTask)")
            val messageManagerSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerSendMethodName &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageClass
            } ?: error("method not found: $messageManagerClassName.$messageManagerSendMethodName(Message)")
            val tbHttpMessageTaskConstructor = tbHttpMessageTaskClass.declaredConstructors.singleOrNull { constructor ->
                constructor.parameterTypes.size == 2 &&
                    ScanReflection.isIntType(constructor.parameterTypes[0]) &&
                    constructor.parameterTypes[1] == String::class.java
            } ?: error("constructor not found: $tbHttpMessageTaskClassName(int,String)")
            val httpMessageTaskSetResponsedClassMethod = httpMessageTaskClass.declaredMethods.singleOrNull { method ->
                method.name == httpMessageTaskSetResponsedClassMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Class::class.java
            } ?: error("method not found: $HTTP_MESSAGE_TASK_CLASS.$httpMessageTaskSetResponsedClassMethodName(Class)")
            val tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskClass.declaredMethods.singleOrNull { method ->
                method.name == tbHttpMessageTaskSetIsNeedTbsMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            } ?: error("method not found: $tbHttpMessageTaskClassName.$tbHttpMessageTaskSetIsNeedTbsMethodName(boolean)")
            val bdUniqueIdGenMethod = bdUniqueIdClass.declaredMethods.singleOrNull { method ->
                method.name == bdUniqueIdGenMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            } ?: error("method not found: $bdUniqueIdClassName.$bdUniqueIdGenMethodName()")
            val tbadkCoreApplicationGetInstMethod =
                tbadkCoreApplicationClass.declaredMethods.singleOrNull { method ->
                    method.name == tbadkCoreApplicationGetInstMethodName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        tbadkCoreApplicationClass.isAssignableFrom(method.returnType)
                } ?: error("method not found: $tbadkCoreApplicationClassName.$tbadkCoreApplicationGetInstMethodName()")
            val tbadkCoreApplicationGetZidMethod =
                tbadkCoreApplicationClass.declaredMethods.singleOrNull { method ->
                    method.name == tbadkCoreApplicationGetZidMethodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == String::class.java
                } ?: error("method not found: $tbadkCoreApplicationClassName.$tbadkCoreApplicationGetZidMethodName()")
            val tbConfigServerAddressField = tbConfigClass.declaredFields.singleOrNull { field ->
                field.name == tbConfigServerAddressFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: error("field not found: $tbConfigClassName.$tbConfigServerAddressFieldName")
            val tbConfigPbFloorAgreeUrlField = tbConfigClass.declaredFields.singleOrNull { field ->
                field.name == tbConfigPbFloorAgreeUrlFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: error("field not found: $tbConfigClassName.$tbConfigPbFloorAgreeUrlFieldName")
            val cmdPbFloorAgreeField = cmdConfigHttpClass.declaredFields.singleOrNull { field ->
                field.name == cmdPbFloorAgreeFieldName &&
                    Modifier.isStatic(field.modifiers) &&
                    ScanReflection.isIntType(field.type)
            } ?: error("field not found: $cmdConfigHttpClassName.$cmdPbFloorAgreeFieldName")
            val agreeDecodeLogicMethod = agreeResponseClass.declaredMethods.singleOrNull { method ->
                ReplyVisibilityProbeSymbolScanner.isAgreeServerResponseDecodeLogicMethod(
                    method,
                    agreeDecodeLogicMethodName,
                )
            } ?: error("method not found: $agreeResponseClassName.$agreeDecodeLogicMethodName(int,JSONObject)")

            listOf(
                replyDecodeMethod,
                getOriginalMessageMethod,
                messageGetExtraMethod,
                messageGetTagMethod,
                messageSetTagMethod,
                httpMessageAddParamMethod,
                httpMessageAddHeaderMethod,
                messageManagerGetInstanceMethod,
                messageManagerFindTaskMethod,
                messageManagerRegisterTaskMethod,
                messageManagerSendMethod,
                httpMessageTaskSetResponsedClassMethod,
                tbHttpMessageTaskSetIsNeedTbsMethod,
                bdUniqueIdGenMethod,
                tbadkCoreApplicationGetInstMethod,
                tbadkCoreApplicationGetZidMethod,
                agreeDecodeLogicMethod,
            ).forEach { it.isAccessible = true }
            listOf(
                replyResultJsonField,
                addPostRequestDataField,
                tbConfigServerAddressField,
                tbConfigPbFloorAgreeUrlField,
                cmdPbFloorAgreeField,
            ).forEach { it.isAccessible = true }
            httpMessageConstructor.isAccessible = true
            tbHttpMessageTaskConstructor.isAccessible = true

            ReplyVisibilityProbeSymbols(
                replyDecodeMethod = replyDecodeMethod,
                replyResultJsonField = replyResultJsonField,
                addPostRequestClass = addPostRequestClass,
                addPostRequestDataField = addPostRequestDataField,
                getOriginalMessageMethod = getOriginalMessageMethod,
                messageGetExtraMethod = messageGetExtraMethod,
                messageGetTagMethod = messageGetTagMethod,
                messageSetTagMethod = messageSetTagMethod,
                httpMessageConstructor = httpMessageConstructor,
                httpMessageAddParamMethod = httpMessageAddParamMethod,
                httpMessageAddHeaderMethod = httpMessageAddHeaderMethod,
                messageManagerGetInstanceMethod = messageManagerGetInstanceMethod,
                messageManagerFindTaskMethod = messageManagerFindTaskMethod,
                messageManagerRegisterTaskMethod = messageManagerRegisterTaskMethod,
                messageManagerSendMethod = messageManagerSendMethod,
                tbHttpMessageTaskConstructor = tbHttpMessageTaskConstructor,
                httpMessageTaskSetResponsedClassMethod = httpMessageTaskSetResponsedClassMethod,
                tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskSetIsNeedTbsMethod,
                bdUniqueIdGenMethod = bdUniqueIdGenMethod,
                tbadkCoreApplicationGetInstMethod = tbadkCoreApplicationGetInstMethod,
                tbadkCoreApplicationGetZidMethod = tbadkCoreApplicationGetZidMethod,
                tbConfigServerAddressField = tbConfigServerAddressField,
                tbConfigPbFloorAgreeUrlField = tbConfigPbFloorAgreeUrlField,
                cmdPbFloorAgreeField = cmdPbFloorAgreeField,
                agreeResponseClass = agreeResponseClass,
                agreeDecodeLogicMethod = agreeDecodeLogicMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[ReplyVisibilityProbeHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val replyVisibilityProbeCritical = ArrayList<String>(36)
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeReplyResponseClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyResponseClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeReplyDecodeMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyDecodeMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeReplyResultJsonField].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeReplyResultJsonField")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAddPostRequestClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestDataField].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAddPostRequestDataField")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeResponsedMessageClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeResponsedMessageClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeGetOriginalMessageMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeGetOriginalMessageMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageGetExtraMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageGetExtraMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageGetTagMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageGetTagMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageSetTagMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageSetTagMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageConstructor].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageConstructor")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddParamMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageAddParamMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddHeaderMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageAddHeaderMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerGetInstanceMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerGetInstanceMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerFindTaskMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerFindTaskMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerRegisterTaskMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerRegisterTaskMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerSendMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeMessageManagerSendMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskConstructor].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskConstructor")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeBdUniqueIdClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdGenMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeBdUniqueIdGenMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetInstMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationGetInstMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetZidMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbadkCoreApplicationGetZidMethod")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigServerAddressField].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigServerAddressField")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigPbFloorAgreeUrlField].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeTbConfigPbFloorAgreeUrlField")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeCmdConfigHttpClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeCmdConfigHttpClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeCmdPbFloorAgreeField].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeCmdPbFloorAgreeField")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAgreeResponseClass")
        }
        if (symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod].isNullOrBlank()) {
            replyVisibilityProbeCritical.add("replyVisibilityProbeAgreeDecodeLogicMethod")
        }
        out[HookFeatureKey.VERIFY_REPLY_AFTER_POST] = if (replyVisibilityProbeCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = replyVisibilityProbeCritical,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "ReplyVisibilityProbeHook",
            "${symbols[ReplyVisibilityContract.replyVisibilityProbeReplyResponseClass]}.${symbols[ReplyVisibilityContract.replyVisibilityProbeReplyDecodeMethod]}" +
                " -> ${symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageClass]} / " +
                "${symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass]}.${symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod]}",
            listOf(
                ReplyVisibilityContract.replyVisibilityProbeReplyResponseClass.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeReplyDecodeMethod.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeReplyResultJsonField.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeAddPostRequestClass.check(symbols),
                "replyVisibilityProbeAddPostRequestDataField" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeAddPostRequestDataField],
                ),
                ReplyVisibilityContract.replyVisibilityProbeResponsedMessageClass.check(symbols),
                "replyVisibilityProbeGetOriginalMessageMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeGetOriginalMessageMethod],
                ),
                ReplyVisibilityContract.replyVisibilityProbeMessageClass.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeMessageGetExtraMethod.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeMessageGetTagMethod.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeMessageSetTagMethod.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeHttpMessageClass.check(symbols),
                "replyVisibilityProbeHttpMessageConstructor" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageConstructor],
                ),
                "replyVisibilityProbeHttpMessageAddParamMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddParamMethod],
                ),
                "replyVisibilityProbeHttpMessageAddHeaderMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageAddHeaderMethod],
                ),
                ReplyVisibilityContract.replyVisibilityProbeMessageManagerClass.check(symbols),
                "replyVisibilityProbeMessageManagerGetInstanceMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerGetInstanceMethod],
                ),
                "replyVisibilityProbeMessageManagerFindTaskMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerFindTaskMethod],
                ),
                "replyVisibilityProbeMessageManagerRegisterTaskMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerRegisterTaskMethod],
                ),
                "replyVisibilityProbeMessageManagerSendMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeMessageManagerSendMethod],
                ),
                "replyVisibilityProbeTbHttpMessageTaskClass" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskClass],
                ),
                "replyVisibilityProbeTbHttpMessageTaskConstructor" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskConstructor],
                ),
                "replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeHttpMessageTaskSetResponsedClassMethod],
                ),
                "replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbHttpMessageTaskSetIsNeedTbsMethod],
                ),
                ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdClass.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeBdUniqueIdGenMethod.check(symbols),
                "replyVisibilityProbeTbadkCoreApplicationClass" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationClass],
                ),
                "replyVisibilityProbeTbadkCoreApplicationGetInstMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetInstMethod],
                ),
                "replyVisibilityProbeTbadkCoreApplicationGetZidMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbadkCoreApplicationGetZidMethod],
                ),
                ReplyVisibilityContract.replyVisibilityProbeTbConfigClass.check(symbols),
                "replyVisibilityProbeTbConfigServerAddressField" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigServerAddressField],
                ),
                "replyVisibilityProbeTbConfigPbFloorAgreeUrlField" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeTbConfigPbFloorAgreeUrlField],
                ),
                ReplyVisibilityContract.replyVisibilityProbeCmdConfigHttpClass.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeCmdPbFloorAgreeField.check(symbols),
                ReplyVisibilityContract.replyVisibilityProbeAgreeResponseClass.check(symbols),
                "replyVisibilityProbeAgreeDecodeLogicMethod" to has(
                    symbols[ReplyVisibilityContract.replyVisibilityProbeAgreeDecodeLogicMethod],
                ),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("ReplyVisibilityProbeHook", false, listOf(HookFeatureKey.VERIFY_REPLY_AFTER_POST)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true
}
