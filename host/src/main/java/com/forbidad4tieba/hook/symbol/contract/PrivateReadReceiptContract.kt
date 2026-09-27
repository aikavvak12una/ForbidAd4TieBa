package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PrivateReadReceiptScanSymbols
import com.forbidad4tieba.hook.symbol.model.PrivateReadReceiptSymbols
import com.forbidad4tieba.hook.symbol.scan.PrivateReadReceiptSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PrivateReadReceiptContract : SymbolContract("PrivateReadReceipt") {
    val privateReadReceiptModelClass = text("privateReadReceiptModelClass")
    val privateReadReceiptModelReadDispatchMethod = text("privateReadReceiptModelReadDispatchMethod")
    val privateReadReceiptMessageManagerClass = text("privateReadReceiptMessageManagerClass")
    val privateReadReceiptMessageManagerGetInstanceMethod = text("privateReadReceiptMessageManagerGetInstanceMethod")
    val privateReadReceiptMessageManagerGetSocketClientMethod = text("privateReadReceiptMessageManagerGetSocketClientMethod")
    val privateReadReceiptMessageSendMethod = text("privateReadReceiptMessageSendMethod")
    val privateReadReceiptMessageBaseClass = text("privateReadReceiptMessageBaseClass")
    val privateReadReceiptSocketClientClass = text("privateReadReceiptSocketClientClass")
    val privateReadReceiptSocketDuplicateCheckMethod = text("privateReadReceiptSocketDuplicateCheckMethod")
    val privateReadReceiptRequestClass = text("privateReadReceiptRequestClass")
    val privateReadReceiptModelBaseClass = text("privateReadReceiptModelBaseClass")
    val privateReadReceiptCommitResponseClass = text("privateReadReceiptCommitResponseClass")
    val privateReadReceiptProcessAckMethod = text("privateReadReceiptProcessAckMethod")
    val privateReadReceiptResponseErrorMethod = text("privateReadReceiptResponseErrorMethod")
    val privateReadReceiptRequestMsgIdField = text("privateReadReceiptRequestMsgIdField")
    val privateReadReceiptRequestToUidField = text("privateReadReceiptRequestToUidField")
    val privateReadReceiptModelDataField = text("privateReadReceiptModelDataField")
    val privateReadReceiptPageDataClass = text("privateReadReceiptPageDataClass")
    val privateReadReceiptPageDataChatListMethod = text("privateReadReceiptPageDataChatListMethod")
    val privateReadReceiptChatMessageClass = text("privateReadReceiptChatMessageClass")
    val privateReadReceiptChatMessageMsgIdMethod = text("privateReadReceiptChatMessageMsgIdMethod")
    val privateReadReceiptChatMessageUserIdMethod = text("privateReadReceiptChatMessageUserIdMethod")
    val privateReadReceiptAccountClass = text("privateReadReceiptAccountClass")
    val privateReadReceiptCurrentAccountMethod = text("privateReadReceiptCurrentAccountMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        var privateReadReceiptMessageManagerGetSocketClientMethod: String? = null

        val privateReadReceiptScan = runScanStep(
            "PrivateReadReceiptBlockHook",
            logger,
            scanErrors,
            PrivateReadReceiptScanSymbols(),
        ) {
            PrivateReadReceiptSymbolScanner.scan(context, cl, logger)
        }

        val privateReadReceiptModelClass: String? = privateReadReceiptScan.modelClass

        val privateReadReceiptModelReadDispatchMethod: String? = privateReadReceiptScan.modelReadDispatchMethod

        val privateReadReceiptMessageManagerClass: String? = privateReadReceiptScan.messageManagerClass

        val privateReadReceiptMessageManagerGetInstanceMethod: String? = privateReadReceiptScan.messageManagerGetInstanceMethod

        privateReadReceiptMessageManagerGetSocketClientMethod =
            privateReadReceiptScan.messageManagerGetSocketClientMethod

        val privateReadReceiptMessageSendMethod: String? = privateReadReceiptScan.messageSendMethod

        val privateReadReceiptMessageBaseClass: String? = privateReadReceiptScan.messageBaseClass

        val privateReadReceiptSocketClientClass: String? = privateReadReceiptScan.socketClientClass

        val privateReadReceiptSocketDuplicateCheckMethod: String? = privateReadReceiptScan.socketDuplicateCheckMethod

        val privateReadReceiptRequestClass: String? = privateReadReceiptScan.requestClass

        val privateReadReceiptModelBaseClass: String? = privateReadReceiptScan.modelBaseClass

        val privateReadReceiptCommitResponseClass: String? = privateReadReceiptScan.commitResponseClass

        val privateReadReceiptProcessAckMethod: String? = privateReadReceiptScan.processAckMethod

        val privateReadReceiptResponseErrorMethod: String? = privateReadReceiptScan.responseErrorMethod

        val privateReadReceiptRequestMsgIdField: String? = privateReadReceiptScan.requestMsgIdField

        val privateReadReceiptRequestToUidField: String? = privateReadReceiptScan.requestToUidField

        val privateReadReceiptModelDataField: String? = privateReadReceiptScan.modelDataField

        val privateReadReceiptPageDataClass: String? = privateReadReceiptScan.pageDataClass

        val privateReadReceiptPageDataChatListMethod: String? = privateReadReceiptScan.pageDataChatListMethod

        val privateReadReceiptChatMessageClass: String? = privateReadReceiptScan.chatMessageClass

        val privateReadReceiptChatMessageMsgIdMethod: String? = privateReadReceiptScan.chatMessageMsgIdMethod

        val privateReadReceiptChatMessageUserIdMethod: String? = privateReadReceiptScan.chatMessageUserIdMethod

        val privateReadReceiptAccountClass: String? = privateReadReceiptScan.accountClass

        val privateReadReceiptCurrentAccountMethod: String? = privateReadReceiptScan.currentAccountMethod

        output[PrivateReadReceiptContract.privateReadReceiptModelClass] = privateReadReceiptModelClass
        output[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod] = privateReadReceiptModelReadDispatchMethod
        output[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass] = privateReadReceiptMessageManagerClass
        output[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod] = privateReadReceiptMessageManagerGetInstanceMethod
        output[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod] = privateReadReceiptMessageManagerGetSocketClientMethod
        output[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod] = privateReadReceiptMessageSendMethod
        output[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass] = privateReadReceiptMessageBaseClass
        output[PrivateReadReceiptContract.privateReadReceiptSocketClientClass] = privateReadReceiptSocketClientClass
        output[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod] = privateReadReceiptSocketDuplicateCheckMethod
        output[PrivateReadReceiptContract.privateReadReceiptRequestClass] = privateReadReceiptRequestClass
        output[PrivateReadReceiptContract.privateReadReceiptModelBaseClass] = privateReadReceiptModelBaseClass
        output[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass] = privateReadReceiptCommitResponseClass
        output[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod] = privateReadReceiptProcessAckMethod
        output[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod] = privateReadReceiptResponseErrorMethod
        output[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField] = privateReadReceiptRequestMsgIdField
        output[PrivateReadReceiptContract.privateReadReceiptRequestToUidField] = privateReadReceiptRequestToUidField
        output[PrivateReadReceiptContract.privateReadReceiptModelDataField] = privateReadReceiptModelDataField
        output[PrivateReadReceiptContract.privateReadReceiptPageDataClass] = privateReadReceiptPageDataClass
        output[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod] = privateReadReceiptPageDataChatListMethod
        output[PrivateReadReceiptContract.privateReadReceiptChatMessageClass] = privateReadReceiptChatMessageClass
        output[PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod] = privateReadReceiptChatMessageMsgIdMethod
        output[PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod] = privateReadReceiptChatMessageUserIdMethod
        output[PrivateReadReceiptContract.privateReadReceiptAccountClass] = privateReadReceiptAccountClass
        output[PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod] = privateReadReceiptCurrentAccountMethod
    }

    fun resolvePrivateReadReceiptSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PrivateReadReceiptSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PrivateReadReceiptBlockHook] skipped: scan symbols unavailable")
                return null
            }

            fun required(name: String, value: String?): String? {
                val normalized = value?.takeIf { it.isNotBlank() }
                if (normalized == null) {
                    Diagnostics.log("[PrivateReadReceiptBlockHook] skipped: missing $name")
                }
                return normalized
            }

            val modelClassName = required("privateReadReceiptModelClass", resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptModelClass])
                ?: return null
            val modelReadMethodName = required(
                "privateReadReceiptModelReadDispatchMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod],
            ) ?: return null
            val messageManagerClassName = required(
                "privateReadReceiptMessageManagerClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass],
            ) ?: return null
            val messageManagerGetInstanceMethodName = required(
                "privateReadReceiptMessageManagerGetInstanceMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod],
            ) ?: return null
            val messageManagerGetSocketClientMethodName = required(
                "privateReadReceiptMessageManagerGetSocketClientMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod],
            ) ?: return null
            val messageSendMethodName = required(
                "privateReadReceiptMessageSendMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod],
            ) ?: return null
            val messageBaseClassName = required(
                "privateReadReceiptMessageBaseClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass],
            ) ?: return null
            val socketClientClassName = required(
                "privateReadReceiptSocketClientClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass],
            ) ?: return null
            val socketDuplicateCheckMethodName = required(
                "privateReadReceiptSocketDuplicateCheckMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod],
            ) ?: return null
            val requestClassName = required("privateReadReceiptRequestClass", resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptRequestClass])
                ?: return null
            val modelBaseClassName = required(
                "privateReadReceiptModelBaseClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptModelBaseClass],
            ) ?: return null
            val commitResponseClassName = required(
                "privateReadReceiptCommitResponseClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass],
            ) ?: return null
            val processAckMethodName = required(
                "privateReadReceiptProcessAckMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod],
            ) ?: return null
            val responseErrorMethodName = required(
                "privateReadReceiptResponseErrorMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod],
            ) ?: return null
            val requestMsgIdFieldName = required(
                "privateReadReceiptRequestMsgIdField",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField],
            ) ?: return null
            val requestToUidFieldName = required(
                "privateReadReceiptRequestToUidField",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField],
            ) ?: return null
            val modelDataFieldName = required(
                "privateReadReceiptModelDataField",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptModelDataField],
            ) ?: return null
            val pageDataClassName = required(
                "privateReadReceiptPageDataClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptPageDataClass],
            ) ?: return null
            val pageDataChatListMethodName = required(
                "privateReadReceiptPageDataChatListMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod],
            ) ?: return null
            val chatMessageClassName = required(
                "privateReadReceiptChatMessageClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptChatMessageClass],
            ) ?: return null
            val chatMsgIdMethodName = required(
                "privateReadReceiptChatMessageMsgIdMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod],
            ) ?: return null
            val chatUserIdMethodName = required(
                "privateReadReceiptChatMessageUserIdMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod],
            ) ?: return null
            val accountClassName = required(
                "privateReadReceiptAccountClass",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptAccountClass],
            ) ?: return null
            val currentAccountMethodName = required(
                "privateReadReceiptCurrentAccountMethod",
                resolvedSymbols[PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod],
            ) ?: return null

            val modelClass = ScanReflection.safeFindClass(modelClassName, cl) ?: return null
            val messageManagerClass = ScanReflection.safeFindClass(messageManagerClassName, cl) ?: return null
            val messageBaseClass = ScanReflection.safeFindClass(messageBaseClassName, cl) ?: return null
            val socketClientClass = ScanReflection.safeFindClass(socketClientClassName, cl) ?: return null
            val requestClass = ScanReflection.safeFindClass(requestClassName, cl) ?: return null
            val modelBaseClass = ScanReflection.safeFindClass(modelBaseClassName, cl) ?: return null
            val commitResponseClass = ScanReflection.safeFindClass(commitResponseClassName, cl) ?: return null
            val pageDataClass = ScanReflection.safeFindClass(pageDataClassName, cl) ?: return null
            val chatMessageClass = ScanReflection.safeFindClass(chatMessageClassName, cl) ?: return null
            val accountClass = ScanReflection.safeFindClass(accountClassName, cl) ?: return null
            if (!messageBaseClass.isAssignableFrom(requestClass)) return null

            val modelReadDispatchMethod = modelClass.declaredMethods.singleOrNull { method ->
                method.name == modelReadMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: return null
            val messageManagerSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageSendMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageBaseClass
            } ?: return null
            val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == messageManagerClass
            } ?: return null
            val messageManagerGetSocketClientMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetSocketClientMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == socketClientClass
            } ?: return null
            val socketDuplicateCheckMethod = socketClientClass.declaredMethods.singleOrNull { method ->
                method.name == socketDuplicateCheckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(requestClass)
            } ?: return null
            val requestConstructor = requestClass.declaredConstructors.singleOrNull { ctor ->
                ctor.parameterTypes.size == 2 &&
                    ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    ctor.parameterTypes[1] == Long::class.javaPrimitiveType
            } ?: return null
            val processAckMethod = modelBaseClass.declaredMethods.singleOrNull { method ->
                method.name == processAckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == commitResponseClass
            } ?: return null
            val responseErrorMethod = ScanReflection.collectInstanceMethods(commitResponseClass).singleOrNull { method ->
                method.name == responseErrorMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isIntType(method.returnType)
            } ?: return null
            val requestMsgIdField = ScanReflection.collectInstanceFields(requestClass).singleOrNull { field ->
                field.name == requestMsgIdFieldName && field.type == Long::class.javaPrimitiveType
            } ?: return null
            val requestToUidField = ScanReflection.collectInstanceFields(requestClass).singleOrNull { field ->
                field.name == requestToUidFieldName && field.type == Long::class.javaPrimitiveType
            } ?: return null
            val modelDataField = ScanReflection.collectInstanceFields(modelClass).singleOrNull { field ->
                field.name == modelDataFieldName && field.type == pageDataClass
            } ?: return null
            val pageDataChatListMethod = pageDataClass.declaredMethods.singleOrNull { method ->
                method.name == pageDataChatListMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isListType(method.returnType)
            } ?: return null
            val chatMessageMsgIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatMsgIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return null
            val chatMessageUserIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatUserIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return null
            val currentAccountMethod = accountClass.declaredMethods.singleOrNull { method ->
                method.name == currentAccountMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == String::class.java
            } ?: return null

            listOf(
                modelReadDispatchMethod,
                processAckMethod,
                responseErrorMethod,
                messageManagerGetInstanceMethod,
                messageManagerGetSocketClientMethod,
                messageManagerSendMethod,
                socketDuplicateCheckMethod,
                pageDataChatListMethod,
                chatMessageMsgIdMethod,
                chatMessageUserIdMethod,
                currentAccountMethod,
            ).forEach { it.isAccessible = true }
            listOf(requestMsgIdField, requestToUidField, modelDataField).forEach { it.isAccessible = true }
            requestConstructor.isAccessible = true

            PrivateReadReceiptSymbols(
                modelClass = modelClass,
                modelReadDispatchMethod = modelReadDispatchMethod,
                processAckMethod = processAckMethod,
                responseErrorMethod = responseErrorMethod,
                messageManagerGetInstanceMethod = messageManagerGetInstanceMethod,
                messageManagerGetSocketClientMethod = messageManagerGetSocketClientMethod,
                messageManagerSendMethod = messageManagerSendMethod,
                socketDuplicateCheckMethod = socketDuplicateCheckMethod,
                requestConstructor = requestConstructor,
                requestMessageClass = requestClass,
                requestMsgIdField = requestMsgIdField,
                requestToUidField = requestToUidField,
                modelDataField = modelDataField,
                pageDataChatListMethod = pageDataChatListMethod,
                chatMessageMsgIdMethod = chatMessageMsgIdMethod,
                chatMessageUserIdMethod = chatMessageUserIdMethod,
                currentAccountMethod = currentAccountMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PrivateReadReceiptBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val privateReadReceiptCritical = ArrayList<String>(27)
        if (symbols[PrivateReadReceiptContract.privateReadReceiptModelClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelReadDispatchMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerGetInstanceMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageManagerGetSocketClientMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageSendMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptMessageBaseClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptSocketClientClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptSocketDuplicateCheckMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptRequestClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptModelBaseClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelBaseClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCommitResponseClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptProcessAckMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptResponseErrorMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestMsgIdField")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptRequestToUidField")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptModelDataField].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptModelDataField")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptPageDataClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptPageDataChatListMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageMsgIdMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptChatMessageUserIdMethod")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptAccountClass].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptAccountClass")
        }
        if (symbols[PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod].isNullOrBlank()) {
            privateReadReceiptCritical.add("privateReadReceiptCurrentAccountMethod")
        }
        out[HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE] = if (privateReadReceiptCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = privateReadReceiptCritical,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PrivateReadReceiptBlockHook",
            "${symbols[PrivateReadReceiptContract.privateReadReceiptModelClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod]} / " +
                "${symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod]}" +
                "(${symbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass]}) -> " +
                "${symbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass]}.${symbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod]} / " +
                "${symbols[PrivateReadReceiptContract.privateReadReceiptRequestClass]}[${symbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField]},${symbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField]}]",
            listOf(
                PrivateReadReceiptContract.privateReadReceiptModelClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptMessageManagerClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptMessageSendMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptMessageBaseClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptSocketClientClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptRequestClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptModelBaseClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptCommitResponseClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptProcessAckMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptRequestToUidField.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptModelDataField.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptPageDataClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptChatMessageClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptAccountClass.check(symbols),
                PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PrivateReadReceiptBlockHook", false, listOf(HookFeatureKey.PRIVATE_READ_RECEIPT_INVISIBLE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPrivateReadReceiptSymbols =
                symbols[PrivateReadReceiptContract.privateReadReceiptModelClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptRequestClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptModelBaseClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptModelDataField] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptPageDataClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptAccountClass] != null ||
                symbols[PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod] != null
        if (hasPrivateReadReceiptSymbols && !isPrivateReadReceiptValid(symbols, cl)) return false
        return true
    }

    private fun isPrivateReadReceiptValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val modelClassName = symbols[PrivateReadReceiptContract.privateReadReceiptModelClass] ?: return false
        val modelReadMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptModelReadDispatchMethod] ?: return false
        val messageManagerClassName = symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerClass] ?: return false
        val messageManagerGetInstanceMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetInstanceMethod] ?: return false
        val messageManagerGetSocketClientMethodName =
            symbols[PrivateReadReceiptContract.privateReadReceiptMessageManagerGetSocketClientMethod] ?: return false
        val messageSendMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptMessageSendMethod] ?: return false
        val messageBaseClassName = symbols[PrivateReadReceiptContract.privateReadReceiptMessageBaseClass] ?: return false
        val socketClientClassName = symbols[PrivateReadReceiptContract.privateReadReceiptSocketClientClass] ?: return false
        val socketDuplicateCheckMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptSocketDuplicateCheckMethod] ?: return false
        val requestClassName = symbols[PrivateReadReceiptContract.privateReadReceiptRequestClass] ?: return false
        val modelBaseClassName = symbols[PrivateReadReceiptContract.privateReadReceiptModelBaseClass] ?: return false
        val commitResponseClassName = symbols[PrivateReadReceiptContract.privateReadReceiptCommitResponseClass] ?: return false
        val processAckMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptProcessAckMethod] ?: return false
        val responseErrorMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptResponseErrorMethod] ?: return false
        val requestMsgIdFieldName = symbols[PrivateReadReceiptContract.privateReadReceiptRequestMsgIdField] ?: return false
        val requestToUidFieldName = symbols[PrivateReadReceiptContract.privateReadReceiptRequestToUidField] ?: return false
        val modelDataFieldName = symbols[PrivateReadReceiptContract.privateReadReceiptModelDataField] ?: return false
        val pageDataClassName = symbols[PrivateReadReceiptContract.privateReadReceiptPageDataClass] ?: return false
        val pageDataChatListMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptPageDataChatListMethod] ?: return false
        val chatMessageClassName = symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageClass] ?: return false
        val chatMsgIdMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageMsgIdMethod] ?: return false
        val chatUserIdMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptChatMessageUserIdMethod] ?: return false
        val accountClassName = symbols[PrivateReadReceiptContract.privateReadReceiptAccountClass] ?: return false
        val currentAccountMethodName = symbols[PrivateReadReceiptContract.privateReadReceiptCurrentAccountMethod] ?: return false
        return try {
            val modelClass = ScanReflection.safeFindClass(modelClassName, cl) ?: return false
            val messageManagerClass = ScanReflection.safeFindClass(messageManagerClassName, cl) ?: return false
            val messageBaseClass = ScanReflection.safeFindClass(messageBaseClassName, cl) ?: return false
            val socketClientClass = ScanReflection.safeFindClass(socketClientClassName, cl) ?: return false
            val requestClass = ScanReflection.safeFindClass(requestClassName, cl) ?: return false
            requestClass.declaredConstructors.singleOrNull { ctor ->
                ctor.parameterTypes.size == 2 &&
                    ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    ctor.parameterTypes[1] == Long::class.javaPrimitiveType
            } ?: return false
            val modelBaseClass = ScanReflection.safeFindClass(modelBaseClassName, cl) ?: return false
            val commitResponseClass = ScanReflection.safeFindClass(commitResponseClassName, cl) ?: return false
            val pageDataClass = ScanReflection.safeFindClass(pageDataClassName, cl) ?: return false
            val chatMessageClass = ScanReflection.safeFindClass(chatMessageClassName, cl) ?: return false
            val accountClass = ScanReflection.safeFindClass(accountClassName, cl) ?: return false
            if (!messageBaseClass.isAssignableFrom(requestClass)) return false
            modelClass.declaredMethods.singleOrNull { method ->
                method.name == modelReadMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            } ?: return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageSendMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageBaseClass
            } ?: return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetInstanceMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == messageManagerClass
            } ?: return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == messageManagerGetSocketClientMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == socketClientClass
            } ?: return false
            socketClientClass.declaredMethods.singleOrNull { method ->
                method.name == socketDuplicateCheckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(requestClass)
            } ?: return false
            modelBaseClass.declaredMethods.singleOrNull { method ->
                method.name == processAckMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == commitResponseClass
            } ?: return false
            ScanReflection.collectInstanceMethods(commitResponseClass).singleOrNull { method ->
                method.name == responseErrorMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isIntType(method.returnType)
            } ?: return false
            val requestFields = ScanReflection.collectInstanceFields(requestClass)
            requestFields.singleOrNull {
                it.name == requestMsgIdFieldName && it.type == Long::class.javaPrimitiveType
            } ?: return false
            requestFields.singleOrNull {
                it.name == requestToUidFieldName && it.type == Long::class.javaPrimitiveType
            } ?: return false
            ScanReflection.collectInstanceFields(modelClass).singleOrNull {
                it.name == modelDataFieldName && it.type == pageDataClass
            } ?: return false
            pageDataClass.declaredMethods.singleOrNull { method ->
                method.name == pageDataChatListMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isListType(method.returnType)
            } ?: return false
            chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatMsgIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return false
            chatMessageClass.declaredMethods.singleOrNull { method ->
                method.name == chatUserIdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Long::class.javaPrimitiveType
            } ?: return false
            accountClass.declaredMethods.singleOrNull { method ->
                method.name == currentAccountMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == String::class.java
            } != null
        } catch (_: Throwable) {
            false
        }
    }
}
