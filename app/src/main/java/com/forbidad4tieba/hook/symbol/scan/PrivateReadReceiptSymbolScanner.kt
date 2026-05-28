package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Modifier

internal object PrivateReadReceiptSymbolScanner {
    private const val MODEL_CLASS =
        "com.baidu.tieba.immessagecenter.im.model.PersonalMsglistModel"
    private val MODEL_READ_DISPATCH_CANDIDATES = arrayOf("h1", "K2")
    private const val MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val MESSAGE_MANAGER_GET_INSTANCE_METHOD = "getInstance"
    private const val MESSAGE_BASE_CLASS = "com.baidu.adp.framework.message.Message"
    private const val MESSAGE_SEND_METHOD = "sendMessage"
    private const val REQUEST_CLASS =
        "com.baidu.tieba.im.message.RequestPersonalMsgReadMessage"
    private const val MODEL_BASE_CLASS = "com.baidu.tieba.im.model.MsglistModel"
    private const val COMMIT_RESPONSE_CLASS = "com.baidu.tieba.im.message.ResponseCommitMessage"
    private const val PROCESS_ACK_METHOD = "processMsgACK"
    private const val RESPONSE_ERROR_METHOD = "getError"
    private const val REQUEST_MSG_ID_FIELD = "hasSentMsgId"
    private const val REQUEST_TO_UID_FIELD = "toUid"
    private const val MODEL_DATA_FIELD = "mDatas"
    private const val PAGE_DATA_CLASS = "com.baidu.tieba.im.data.MsgPageData"
    private const val PAGE_DATA_CHAT_LIST_METHOD = "getChatMessages"
    private const val CHAT_MESSAGE_CLASS = "com.baidu.tieba.im.message.chat.ChatMessage"
    private const val CHAT_MESSAGE_MSG_ID_METHOD = "getMsgId"
    private const val CHAT_MESSAGE_USER_ID_METHOD = "getUserId"
    private const val CHAT_MESSAGE_LOCAL_DATA_METHOD = "getLocalData"
    private const val LOCAL_DATA_CLASS = "com.baidu.tieba.im.data.MsgLocalData"
    private const val LOCAL_DATA_STATUS_METHOD = "getStatus"
    private const val ACCOUNT_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val CURRENT_ACCOUNT_METHOD = "getCurrentAccount"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PrivateReadReceiptScanSymbols {
        val modelClass = safeFindClass(MODEL_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $MODEL_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val messageManagerClass = safeFindClass(MESSAGE_MANAGER_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $MESSAGE_MANAGER_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val messageBaseClass = safeFindClass(MESSAGE_BASE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $MESSAGE_BASE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val requestClass = safeFindClass(REQUEST_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $REQUEST_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        requestClass.declaredConstructors.singleOrNull { ctor ->
            ctor.parameterTypes.size == 2 &&
                ctor.parameterTypes[0] == Long::class.javaPrimitiveType &&
                ctor.parameterTypes[1] == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage(long,long) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val modelBaseClass = safeFindClass(MODEL_BASE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $MODEL_BASE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val commitResponseClass = safeFindClass(COMMIT_RESPONSE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $COMMIT_RESPONSE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val pageDataClass = safeFindClass(PAGE_DATA_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $PAGE_DATA_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val chatMessageClass = safeFindClass(CHAT_MESSAGE_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $CHAT_MESSAGE_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val localDataClass = safeFindClass(LOCAL_DATA_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $LOCAL_DATA_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        val accountClass = safeFindClass(ACCOUNT_CLASS, cl) ?: run {
            log(logger, "privateReadReceipt: class not found: $ACCOUNT_CLASS")
            return PrivateReadReceiptScanSymbols()
        }
        if (!messageBaseClass.isAssignableFrom(requestClass)) {
            log(logger, "privateReadReceipt: request class hierarchy mismatch")
            return PrivateReadReceiptScanSymbols()
        }

        val modelReadDispatchMethod = run {
            val byName = MODEL_READ_DISPATCH_CANDIDATES.firstNotNullOfOrNull { name ->
                modelClass.declaredMethods.singleOrNull { method ->
                    method.name == name &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty()
                }
            }
            if (byName != null) {
                byName
            } else {
                val firstSuperclass: Class<*>? = modelClass.superclass
                val ancestorMethodNames = generateSequence(firstSuperclass) { clazz -> clazz.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .filter { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterTypes.isEmpty() }
                    .map { it.name }
                    .toSet()
                modelClass.declaredMethods.singleOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isFinal(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty() &&
                        method.name !in ancestorMethodNames
                }
            }
        } ?: run {
            log(logger, "privateReadReceipt: PersonalMsglistModel read dispatch method not found")
            return PrivateReadReceiptScanSymbols()
        }
        val messageSendMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
            method.name == MESSAGE_SEND_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == messageBaseClass
        } ?: run {
            log(logger, "privateReadReceipt: MessageManager.sendMessage(Message) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val messageManagerGetInstanceMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
            method.name == MESSAGE_MANAGER_GET_INSTANCE_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == messageManagerClass
        } ?: run {
            log(logger, "privateReadReceipt: MessageManager.getInstance() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val processAckMethod = modelBaseClass.declaredMethods.singleOrNull { method ->
            method.name == PROCESS_ACK_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == commitResponseClass
        } ?: run {
            log(logger, "privateReadReceipt: MsglistModel.processMsgACK(ResponseCommitMessage) not found")
            return PrivateReadReceiptScanSymbols()
        }
        val responseErrorMethod = collectInstanceMethods(commitResponseClass).singleOrNull { method ->
            method.name == RESPONSE_ERROR_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isIntType(method.returnType)
        } ?: run {
            log(logger, "privateReadReceipt: ResponseCommitMessage.getError() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val requestFields = collectInstanceFields(requestClass)
        val requestMsgIdField = requestFields.singleOrNull { field ->
            field.name == REQUEST_MSG_ID_FIELD &&
                field.type == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage.hasSentMsgId not found")
            return PrivateReadReceiptScanSymbols()
        }
        val requestToUidField = requestFields.singleOrNull { field ->
            field.name == REQUEST_TO_UID_FIELD &&
                field.type == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: RequestPersonalMsgReadMessage.toUid not found")
            return PrivateReadReceiptScanSymbols()
        }
        val modelDataField = collectInstanceFields(modelClass).singleOrNull { field ->
            field.name == MODEL_DATA_FIELD &&
                field.type == pageDataClass
        } ?: run {
            log(logger, "privateReadReceipt: PersonalMsglistModel.mDatas not found")
            return PrivateReadReceiptScanSymbols()
        }
        val pageDataChatListMethod = pageDataClass.declaredMethods.singleOrNull { method ->
            method.name == PAGE_DATA_CHAT_LIST_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isListType(method.returnType)
        } ?: run {
            log(logger, "privateReadReceipt: MsgPageData.getChatMessages() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatMsgIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == CHAT_MESSAGE_MSG_ID_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getMsgId() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatUserIdMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == CHAT_MESSAGE_USER_ID_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Long::class.javaPrimitiveType
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getUserId() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val chatLocalDataMethod = chatMessageClass.declaredMethods.singleOrNull { method ->
            method.name == CHAT_MESSAGE_LOCAL_DATA_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == localDataClass
        } ?: run {
            log(logger, "privateReadReceipt: ChatMessage.getLocalData() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val localStatusMethod = localDataClass.declaredMethods.singleOrNull { method ->
            method.name == LOCAL_DATA_STATUS_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Short::class.javaObjectType
        } ?: run {
            log(logger, "privateReadReceipt: MsgLocalData.getStatus() not found")
            return PrivateReadReceiptScanSymbols()
        }
        val currentAccountMethod = accountClass.declaredMethods.singleOrNull { method ->
            method.name == CURRENT_ACCOUNT_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == String::class.java
        } ?: run {
            log(logger, "privateReadReceipt: TbadkCoreApplication.getCurrentAccount() not found")
            return PrivateReadReceiptScanSymbols()
        }

        log(
            logger,
            "privateReadReceipt matched: ${modelClass.name}.${modelReadDispatchMethod.name} " +
                "${messageManagerClass.name}.${messageSendMethod.name}(${messageBaseClass.name})",
        )
        return PrivateReadReceiptScanSymbols(
            modelClass = modelClass.name,
            modelReadDispatchMethod = modelReadDispatchMethod.name,
            messageManagerClass = messageManagerClass.name,
            messageManagerGetInstanceMethod = messageManagerGetInstanceMethod.name,
            messageSendMethod = messageSendMethod.name,
            messageBaseClass = messageBaseClass.name,
            requestClass = requestClass.name,
            modelBaseClass = modelBaseClass.name,
            commitResponseClass = commitResponseClass.name,
            processAckMethod = processAckMethod.name,
            responseErrorMethod = responseErrorMethod.name,
            requestMsgIdField = requestMsgIdField.name,
            requestToUidField = requestToUidField.name,
            modelDataField = modelDataField.name,
            pageDataClass = pageDataClass.name,
            pageDataChatListMethod = pageDataChatListMethod.name,
            chatMessageClass = chatMessageClass.name,
            chatMessageMsgIdMethod = chatMsgIdMethod.name,
            chatMessageUserIdMethod = chatUserIdMethod.name,
            chatMessageLocalDataMethod = chatLocalDataMethod.name,
            localDataClass = localDataClass.name,
            localDataStatusMethod = localStatusMethod.name,
            accountClass = accountClass.name,
            currentAccountMethod = currentAccountMethod.name,
        )
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        HookSymbolResolver.collectInstanceMethods(clazz)

    private fun isIntType(type: Class<*>): Boolean =
        HookSymbolResolver.isIntType(type)

    private fun isListType(type: Class<*>): Boolean =
        HookSymbolResolver.isListType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
