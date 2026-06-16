package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlainUrlMessageSymbolScanner {
    private const val CLICK_MESSAGE_CMD = 2001332
    private const val MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.ResponsedMessage"
    private const val CUSTOM_RESPONSED_MESSAGE_CLASS =
        "com.baidu.adp.framework.message.CustomResponsedMessage"
    private const val APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val GET_CMD_METHOD = "getCmd"
    private const val GET_DATA_METHOD = "getData"
    private const val GET_INST_METHOD = "getInst"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PlainUrlMessageDispatchScanSymbols {
        val messageManagerClass = safeFindClass(MESSAGE_MANAGER_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $MESSAGE_MANAGER_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val responsedMessageClass = safeFindClass(RESPONSED_MESSAGE_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $RESPONSED_MESSAGE_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val customResponsedMessageClass = safeFindClass(CUSTOM_RESPONSED_MESSAGE_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $CUSTOM_RESPONSED_MESSAGE_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val applicationClass = safeFindClass(APPLICATION_CLASS, cl) ?: run {
            log(logger, "plainUrlMessage: class not found: $APPLICATION_CLASS")
            return PlainUrlMessageDispatchScanSymbols()
        }
        if (!responsedMessageClass.isAssignableFrom(customResponsedMessageClass)) {
            log(logger, "plainUrlMessage: CustomResponsedMessage hierarchy mismatch")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val messageManagerMethods = declaredMethods("MessageManager", messageManagerClass, logger)
            ?: return PlainUrlMessageDispatchScanSymbols()
        val responsedMessageMethods = declaredMethods("ResponsedMessage", responsedMessageClass, logger)
            ?: return PlainUrlMessageDispatchScanSymbols()
        val customResponsedMessageMethods = declaredMethods("CustomResponsedMessage", customResponsedMessageClass, logger)
            ?: return PlainUrlMessageDispatchScanSymbols()
        val applicationMethods = declaredMethods("TbadkCoreApplication", applicationClass, logger)
            ?: return PlainUrlMessageDispatchScanSymbols()

        val dispatchMethod = messageManagerMethods.singleOrNull { method ->
            ScanReflection.isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
        } ?: run {
            log(logger, "plainUrlMessage: dispatchResponsedMessage(ResponsedMessage) not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getCmdMethod = responsedMessageMethods.singleOrNull { method ->
            method.name == GET_CMD_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                isIntType(method.returnType)
        } ?: run {
            log(logger, "plainUrlMessage: ResponsedMessage.getCmd() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getDataMethod = customResponsedMessageMethods.singleOrNull { method ->
            method.name == GET_DATA_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Any::class.java
        } ?: run {
            log(logger, "plainUrlMessage: CustomResponsedMessage.getData() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }
        val getInstMethod = applicationMethods.singleOrNull { method ->
            method.name == GET_INST_METHOD &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                applicationClass.isAssignableFrom(method.returnType)
        } ?: run {
            log(logger, "plainUrlMessage: TbadkCoreApplication.getInst() not found")
            return PlainUrlMessageDispatchScanSymbols()
        }

        log(
            logger,
            "plainUrlMessage matched: ${messageManagerClass.name}.${dispatchMethod.name} cmd=$CLICK_MESSAGE_CMD",
        )
        return PlainUrlMessageDispatchScanSymbols(
            messageManagerClass = messageManagerClass.name,
            dispatchMethod = dispatchMethod.name,
            responsedMessageClass = responsedMessageClass.name,
            getCmdMethod = getCmdMethod.name,
            customResponsedMessageClass = customResponsedMessageClass.name,
            getDataMethod = getDataMethod.name,
            applicationClass = applicationClass.name,
            getInstMethod = getInstMethod.name,
        )
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun isIntType(type: Class<*>): Boolean =
        ScanReflection.isIntType(type)

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("PlainUrlMessageHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
