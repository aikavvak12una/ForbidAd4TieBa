package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.AgreeServerResponseLogScanSymbols
import com.forbidad4tieba.hook.symbol.model.ReplyServerResponseLogScanSymbols
import com.forbidad4tieba.hook.symbol.model.ReplyVisibilityProbeScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

internal object ReplyVisibilityProbeSymbolScanner {
    private const val REPLY_SERVER_RESPONSE_CLASS = "com.baidu.tieba.write.message.AddPostHttpResponse"
    private const val REPLY_SERVER_RESPONSE_DECODE_METHOD = "decodeInBackGround"
    private const val REPLY_SERVER_RESPONSE_RESULT_JSON_FIELD = "resultJSON"
    private const val JSON_HTTP_RESPONSED_MESSAGE_CLASS = "com.baidu.tbadk.message.http.JsonHttpResponsedMessage"
    private const val AGREE_SERVER_RESPONSE_CLASS = "com.baidu.tieba.pb.data.PbFloorAgreeResponseMessage"
    private const val AGREE_SERVER_RESPONSE_DECODE_LOGIC_METHOD = "decodeLogicInBackGround"
    private const val ADD_POST_REQUEST_CLASS = "com.baidu.tieba.write.message.AddPostRequest"
    private const val ADD_POST_REQUEST_DATA_FIELD = "requestData"
    private const val RESPONSED_MESSAGE_CLASS = "com.baidu.adp.framework.message.ResponsedMessage"
    private const val RESPONSED_MESSAGE_GET_ORIGINAL_METHOD = "getOrginalMessage"
    private const val MESSAGE_CLASS = "com.baidu.adp.framework.message.Message"
    private const val MESSAGE_GET_EXTRA_METHOD = "getExtra"
    private const val MESSAGE_GET_TAG_METHOD = "getTag"
    private const val MESSAGE_SET_TAG_METHOD = "setTag"
    private const val HTTP_MESSAGE_CLASS = "com.baidu.adp.framework.message.HttpMessage"
    private const val HTTP_MESSAGE_ADD_PARAM_METHOD = "addParam"
    private const val HTTP_MESSAGE_ADD_HEADER_METHOD = "addHeader"
    private const val MESSAGE_MANAGER_CLASS = "com.baidu.adp.framework.MessageManager"
    private const val MESSAGE_MANAGER_GET_INSTANCE_METHOD = "getInstance"
    private const val MESSAGE_MANAGER_FIND_TASK_METHOD = "findTask"
    private const val MESSAGE_MANAGER_REGISTER_TASK_METHOD = "registerTask"
    private const val MESSAGE_MANAGER_SEND_MESSAGE_METHOD = "sendMessage"
    private const val MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.MessageTask"
    private const val HTTP_MESSAGE_TASK_CLASS = "com.baidu.adp.framework.task.HttpMessageTask"
    private const val HTTP_MESSAGE_TASK_SET_RESPONSE_CLASS_METHOD = "setResponsedClass"
    private const val TB_HTTP_MESSAGE_TASK_CLASS = "com.baidu.tbadk.task.TbHttpMessageTask"
    private const val TB_HTTP_MESSAGE_TASK_SET_NEED_TBS_METHOD = "setIsNeedTbs"
    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"
    private const val BD_UNIQUE_ID_GEN_METHOD = "gen"
    private const val TBADK_CORE_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val TBADK_CORE_APPLICATION_GET_INST_METHOD = "getInst"
    private const val TBADK_CORE_APPLICATION_GET_ZID_METHOD = "getZid"
    private const val TB_CONFIG_CLASS = "com.baidu.tbadk.TbConfig"
    private const val TB_CONFIG_SERVER_ADDRESS_FIELD = "SERVER_ADDRESS"
    private const val TB_CONFIG_PB_FLOOR_AGREE_URL_FIELD = "PB_FLOOR_AGREE_URL"
    private const val CMD_CONFIG_HTTP_CLASS = "com.baidu.tbadk.core.frameworkData.CmdConfigHttp"
    private const val CMD_CONFIG_HTTP_PB_FLOOR_AGREE_FIELD = "CMD_PB_FLOOR_AGREE"

    fun scanReplyServerResponseLog(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ReplyServerResponseLogScanSymbols? {
        val responseClass = safeFindClass(REPLY_SERVER_RESPONSE_CLASS, cl) ?: run {
            log(logger, "replyServerResponseLog: class missing $REPLY_SERVER_RESPONSE_CLASS")
            return null
        }
        val decodeCandidates = declaredMethods("ReplyServerResponseLog.Response", responseClass, logger)
            ?.filter { method ->
                isReplyServerResponseDecodeMethod(method, REPLY_SERVER_RESPONSE_DECODE_METHOD)
            } ?: return null
        val decodeMethod = decodeCandidates.singleOrNull() ?: run {
            log(
                logger,
                "replyServerResponseLog: decode method candidates=" +
                    decodeCandidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return null
        }
        val resultJsonField = declaredFields("ReplyServerResponseLog.Response", responseClass, logger)
            ?.singleOrNull { field ->
                field.name == REPLY_SERVER_RESPONSE_RESULT_JSON_FIELD &&
                    JSONObject::class.java.isAssignableFrom(field.type)
            } ?: run {
            log(logger, "replyServerResponseLog: result JSON field missing")
            return null
        }
        log(
            logger,
            "replyServerResponseLog matched: " +
                "${responseClass.name}.${decodeMethod.name}[${resultJsonField.name}]",
        )
        return ReplyServerResponseLogScanSymbols(
            responseClass = responseClass.name,
            decodeMethod = decodeMethod.name,
            resultJsonField = resultJsonField.name,
        )
    }

    fun isReplyServerResponseDecodeMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            isIntType(method.parameterTypes[0]) &&
            method.parameterTypes[1] == ByteArray::class.java
    }

    fun scanAgreeServerResponseLog(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): AgreeServerResponseLogScanSymbols? {
        val responseClass = safeFindClass(AGREE_SERVER_RESPONSE_CLASS, cl) ?: run {
            log(logger, "agreeServerResponseLog: class missing $AGREE_SERVER_RESPONSE_CLASS")
            return null
        }
        val jsonResponseClass = safeFindClass(JSON_HTTP_RESPONSED_MESSAGE_CLASS, cl) ?: run {
            log(logger, "agreeServerResponseLog: base class missing $JSON_HTTP_RESPONSED_MESSAGE_CLASS")
            return null
        }
        if (!jsonResponseClass.isAssignableFrom(responseClass)) {
            log(
                logger,
                "agreeServerResponseLog: $AGREE_SERVER_RESPONSE_CLASS is not a JsonHttpResponsedMessage",
            )
            return null
        }
        val decodeCandidates = declaredMethods("AgreeServerResponseLog.Response", responseClass, logger)
            ?.filter { method ->
                isAgreeServerResponseDecodeLogicMethod(method, AGREE_SERVER_RESPONSE_DECODE_LOGIC_METHOD)
            } ?: return null
        val decodeLogicMethod = decodeCandidates.singleOrNull() ?: run {
            log(
                logger,
                "agreeServerResponseLog: decode logic method candidates=" +
                    decodeCandidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return null
        }
        log(
            logger,
            "agreeServerResponseLog matched: ${responseClass.name}.${decodeLogicMethod.name}",
        )
        return AgreeServerResponseLogScanSymbols(
            responseClass = responseClass.name,
            decodeLogicMethod = decodeLogicMethod.name,
        )
    }

    fun isAgreeServerResponseDecodeLogicMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            isIntType(method.parameterTypes[0]) &&
            JSONObject::class.java.isAssignableFrom(method.parameterTypes[1])
    }

    fun scan(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ReplyVisibilityProbeScanSymbols {
        fun findClass(label: String, className: String): Class<*>? {
            return safeFindClass(className, cl) ?: run {
                log(logger, "replyVisibilityProbe: $label class missing $className")
                null
            }
        }

        fun logMissing(label: String, candidates: List<Method> = emptyList()) {
            val suffix = if (candidates.isEmpty()) {
                ""
            } else {
                " candidates=${candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" }}"
            }
            log(logger, "replyVisibilityProbe: $label missing$suffix")
        }

        fun declaredMethods(label: String, clazz: Class<*>?): List<Method> {
            if (clazz == null) return emptyList()
            return scanSubStep("ReplyVisibilityProbeHook.$label.Methods", logger, emptyList()) {
                clazz.declaredMethods.toList()
            }
        }

        fun declaredFields(label: String, clazz: Class<*>?): List<Field> {
            if (clazz == null) return emptyList()
            return scanSubStep("ReplyVisibilityProbeHook.$label.Fields", logger, emptyList()) {
                clazz.declaredFields.toList()
            }
        }

        fun declaredConstructors(label: String, clazz: Class<*>?): List<Constructor<*>> {
            if (clazz == null) return emptyList()
            return scanSubStep("ReplyVisibilityProbeHook.$label.Constructors", logger, emptyList()) {
                clazz.declaredConstructors.toList()
            }
        }

        val replyResponseClass = findClass("reply response", REPLY_SERVER_RESPONSE_CLASS)
        val addPostRequestClass = findClass("add post request", ADD_POST_REQUEST_CLASS)
        val responsedMessageClass = findClass("responsed message", RESPONSED_MESSAGE_CLASS)
        val messageClass = findClass("message", MESSAGE_CLASS)
        val httpMessageClass = findClass("http message", HTTP_MESSAGE_CLASS)
        val messageManagerClass = findClass("message manager", MESSAGE_MANAGER_CLASS)
        val messageTaskClass = findClass("message task", MESSAGE_TASK_CLASS)
        val httpMessageTaskClass = findClass("http message task", HTTP_MESSAGE_TASK_CLASS)
        val tbHttpMessageTaskClass = findClass("tb http message task", TB_HTTP_MESSAGE_TASK_CLASS)
        val bdUniqueIdClass = findClass("bd unique id", BD_UNIQUE_ID_CLASS)
        val tbadkCoreApplicationClass = findClass("tbadk core application", TBADK_CORE_APPLICATION_CLASS)
        val tbConfigClass = findClass("tb config", TB_CONFIG_CLASS)
        val cmdConfigHttpClass = findClass("cmd config http", CMD_CONFIG_HTTP_CLASS)
        val agreeResponseClass = findClass("agree response", AGREE_SERVER_RESPONSE_CLASS)
        val jsonResponseClass = findClass("json response", JSON_HTTP_RESPONSED_MESSAGE_CLASS)

        val replyResponseMethods = declaredMethods("ReplyResponse", replyResponseClass)
        val replyResponseFields = declaredFields("ReplyResponse", replyResponseClass)
        val addPostRequestFields = declaredFields("AddPostRequest", addPostRequestClass)
        val responsedMessageMethods = declaredMethods("ResponsedMessage", responsedMessageClass)
        val messageMethods = declaredMethods("Message", messageClass)
        val httpMessageConstructors = declaredConstructors("HttpMessage", httpMessageClass)
        val httpMessageMethods = declaredMethods("HttpMessage", httpMessageClass)
        val messageManagerMethods = declaredMethods("MessageManager", messageManagerClass)
        val tbHttpMessageTaskConstructors = declaredConstructors("TbHttpMessageTask", tbHttpMessageTaskClass)
        val tbHttpMessageTaskMethods = declaredMethods("TbHttpMessageTask", tbHttpMessageTaskClass)
        val httpMessageTaskMethods = declaredMethods("HttpMessageTask", httpMessageTaskClass)
        val bdUniqueIdMethods = declaredMethods("BdUniqueId", bdUniqueIdClass)
        val tbadkCoreApplicationMethods = declaredMethods("TbadkCoreApplication", tbadkCoreApplicationClass)
        val tbConfigFields = declaredFields("TbConfig", tbConfigClass)
        val cmdConfigHttpFields = declaredFields("CmdConfigHttp", cmdConfigHttpClass)
        val agreeResponseMethods = declaredMethods("AgreeResponse", agreeResponseClass)

        val replyDecodeMethod = replyResponseMethods
            .filter { method -> isReplyServerResponseDecodeMethod(method, REPLY_SERVER_RESPONSE_DECODE_METHOD) }
            .singleOrNull()
            ?: run {
                val candidates = replyResponseMethods.filter { it.name == REPLY_SERVER_RESPONSE_DECODE_METHOD }
                if (replyResponseClass != null) logMissing("reply decode method", candidates)
                null
            }
        val replyResultJsonField = replyResponseFields.singleOrNull { field ->
            field.name == REPLY_SERVER_RESPONSE_RESULT_JSON_FIELD &&
                JSONObject::class.java.isAssignableFrom(field.type)
        } ?: run {
            if (replyResponseClass != null) log(logger, "replyVisibilityProbe: reply result JSON field missing")
            null
        }
        val addPostRequestDataField = addPostRequestFields.singleOrNull { field ->
            field.name == ADD_POST_REQUEST_DATA_FIELD && isStringMapField(field)
        } ?: run {
            if (addPostRequestClass != null) log(logger, "replyVisibilityProbe: add post requestData field missing")
            null
        }
        val getOriginalMessageMethod = if (responsedMessageClass != null && messageClass != null) {
            val messageClassResolved = messageClass
            responsedMessageMethods.singleOrNull { method ->
                method.name == RESPONSED_MESSAGE_GET_ORIGINAL_METHOD &&
                    method.parameterTypes.isEmpty() &&
                    messageClassResolved.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("get original message method")
                null
            }
        } else {
            null
        }
        val messageGetExtraMethod = messageMethods.singleOrNull { method ->
            method.name == MESSAGE_GET_EXTRA_METHOD &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Any::class.java
        } ?: run {
            if (messageClass != null) logMissing("message getExtra method")
            null
        }
        val messageGetTagMethod = if (messageClass != null && bdUniqueIdClass != null) {
            val bdUniqueIdClassResolved = bdUniqueIdClass
            messageMethods.singleOrNull { method ->
                method.name == MESSAGE_GET_TAG_METHOD &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClassResolved.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("message getTag method")
                null
            }
        } else {
            null
        }
        val messageSetTagMethod = if (messageClass != null && bdUniqueIdClass != null) {
            messageMethods.singleOrNull { method ->
                method.name == MESSAGE_SET_TAG_METHOD &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == bdUniqueIdClass
            } ?: run {
                logMissing("message setTag method")
                null
            }
        } else {
            null
        }
        val httpMessageConstructor = httpMessageConstructors.singleOrNull { constructor ->
            constructor.parameterTypes.size == 1 && isIntType(constructor.parameterTypes[0])
        } ?: run {
            if (httpMessageClass != null) log(logger, "replyVisibilityProbe: HttpMessage(int) constructor missing")
            null
        }
        val httpMessageAddParamMethod = httpMessageMethods.singleOrNull { method ->
            method.name == HTTP_MESSAGE_ADD_PARAM_METHOD &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Any::class.java
        } ?: run {
            if (httpMessageClass != null) logMissing("HttpMessage addParam(String,Object) method")
            null
        }
        val httpMessageAddHeaderMethod = httpMessageMethods.singleOrNull { method ->
            method.name == HTTP_MESSAGE_ADD_HEADER_METHOD &&
                method.returnType == String::class.java &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
        } ?: run {
            if (httpMessageClass != null) logMissing("HttpMessage addHeader(String,String) method")
            null
        }
        val messageManagerGetInstanceMethod = if (messageManagerClass != null) {
            val messageManagerClassResolved = messageManagerClass
            messageManagerMethods.singleOrNull { method ->
                method.name == MESSAGE_MANAGER_GET_INSTANCE_METHOD &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    messageManagerClassResolved.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("MessageManager getInstance method")
                null
            }
        } else {
            null
        }
        val messageManagerFindTaskMethod = if (messageManagerClass != null && messageTaskClass != null) {
            val messageTaskClassResolved = messageTaskClass
            messageManagerMethods.singleOrNull { method ->
                method.name == MESSAGE_MANAGER_FIND_TASK_METHOD &&
                    method.parameterTypes.size == 1 &&
                    isIntType(method.parameterTypes[0]) &&
                    messageTaskClassResolved.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("MessageManager findTask method")
                null
            }
        } else {
            null
        }
        val messageManagerRegisterTaskMethod = if (messageManagerClass != null && messageTaskClass != null) {
            messageManagerMethods.singleOrNull { method ->
                method.name == MESSAGE_MANAGER_REGISTER_TASK_METHOD &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageTaskClass
            } ?: run {
                logMissing("MessageManager registerTask method")
                null
            }
        } else {
            null
        }
        val messageManagerSendMethod = if (messageManagerClass != null && messageClass != null) {
            messageManagerMethods.singleOrNull { method ->
                method.name == MESSAGE_MANAGER_SEND_MESSAGE_METHOD &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == messageClass
            } ?: run {
                logMissing("MessageManager sendMessage(Message) method")
                null
            }
        } else {
            null
        }
        val tbHttpMessageTaskConstructor = tbHttpMessageTaskConstructors.singleOrNull { constructor ->
            constructor.parameterTypes.size == 2 &&
                isIntType(constructor.parameterTypes[0]) &&
                constructor.parameterTypes[1] == String::class.java
        } ?: run {
            if (tbHttpMessageTaskClass != null) {
                log(logger, "replyVisibilityProbe: TbHttpMessageTask(int,String) constructor missing")
            }
            null
        }
        val httpMessageTaskSetResponsedClassMethod = httpMessageTaskMethods.singleOrNull { method ->
            method.name == HTTP_MESSAGE_TASK_SET_RESPONSE_CLASS_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Class::class.java
        } ?: run {
            if (httpMessageTaskClass != null) logMissing("HttpMessageTask setResponsedClass method")
            null
        }
        val tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskMethods.singleOrNull { method ->
            method.name == TB_HTTP_MESSAGE_TASK_SET_NEED_TBS_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType
        } ?: run {
            if (tbHttpMessageTaskClass != null) logMissing("TbHttpMessageTask setIsNeedTbs method")
            null
        }
        val bdUniqueIdGenMethod = if (bdUniqueIdClass != null) {
            val bdUniqueIdClassResolved = bdUniqueIdClass
            bdUniqueIdMethods.singleOrNull { method ->
                method.name == BD_UNIQUE_ID_GEN_METHOD &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClassResolved.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("BdUniqueId gen method")
                null
            }
        } else {
            null
        }
        val tbadkCoreApplicationGetInstMethod = if (tbadkCoreApplicationClass != null) {
            val appClass = tbadkCoreApplicationClass
            tbadkCoreApplicationMethods.singleOrNull { method ->
                method.name == TBADK_CORE_APPLICATION_GET_INST_METHOD &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    appClass.isAssignableFrom(method.returnType)
            } ?: run {
                logMissing("TbadkCoreApplication getInst method")
                null
            }
        } else {
            null
        }
        val tbadkCoreApplicationGetZidMethod = tbadkCoreApplicationMethods.singleOrNull { method ->
            method.name == TBADK_CORE_APPLICATION_GET_ZID_METHOD &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == String::class.java
        } ?: run {
            if (tbadkCoreApplicationClass != null) logMissing("TbadkCoreApplication getZid method")
            null
        }
        val tbConfigServerAddressField = tbConfigFields.singleOrNull { field ->
            field.name == TB_CONFIG_SERVER_ADDRESS_FIELD &&
                Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: run {
            if (tbConfigClass != null) log(logger, "replyVisibilityProbe: TbConfig SERVER_ADDRESS field missing")
            null
        }
        val tbConfigPbFloorAgreeUrlField = tbConfigFields.singleOrNull { field ->
            field.name == TB_CONFIG_PB_FLOOR_AGREE_URL_FIELD &&
                Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: run {
            if (tbConfigClass != null) log(logger, "replyVisibilityProbe: TbConfig PB_FLOOR_AGREE_URL field missing")
            null
        }
        val cmdPbFloorAgreeField = cmdConfigHttpFields.singleOrNull { field ->
            field.name == CMD_CONFIG_HTTP_PB_FLOOR_AGREE_FIELD &&
                Modifier.isStatic(field.modifiers) &&
                isIntType(field.type)
        } ?: run {
            if (cmdConfigHttpClass != null) {
                log(logger, "replyVisibilityProbe: CmdConfigHttp CMD_PB_FLOOR_AGREE field missing")
            }
            null
        }
        val agreeDecodeLogicMethod = if (agreeResponseClass != null) {
            val agreeClass = agreeResponseClass
            if (jsonResponseClass != null && !jsonResponseClass.isAssignableFrom(agreeClass)) {
                log(logger, "replyVisibilityProbe: agree response is not JsonHttpResponsedMessage")
                null
            } else {
                val candidates = agreeResponseMethods.filter { method ->
                    isAgreeServerResponseDecodeLogicMethod(method, AGREE_SERVER_RESPONSE_DECODE_LOGIC_METHOD)
                }
                candidates.singleOrNull() ?: run {
                    logMissing("agree decode logic method", candidates)
                    null
                }
            }
        } else {
            null
        }

        log(
            logger,
            "replyVisibilityProbe matched: " +
                "${replyResponseClass?.name}.${replyDecodeMethod?.name} -> " +
                "${httpMessageClass?.name} / ${agreeResponseClass?.name}.${agreeDecodeLogicMethod?.name}",
        )
        return ReplyVisibilityProbeScanSymbols(
            replyResponseClass = replyResponseClass?.name,
            replyDecodeMethod = replyDecodeMethod?.name,
            replyResultJsonField = replyResultJsonField?.name,
            addPostRequestClass = addPostRequestClass?.name,
            addPostRequestDataField = addPostRequestDataField?.name,
            responsedMessageClass = responsedMessageClass?.name,
            getOriginalMessageMethod = getOriginalMessageMethod?.name,
            messageClass = messageClass?.name,
            messageGetExtraMethod = messageGetExtraMethod?.name,
            messageGetTagMethod = messageGetTagMethod?.name,
            messageSetTagMethod = messageSetTagMethod?.name,
            httpMessageClass = httpMessageClass?.name,
            httpMessageConstructor = httpMessageConstructor?.let { "<init>(int)" },
            httpMessageAddParamMethod = httpMessageAddParamMethod?.name,
            httpMessageAddHeaderMethod = httpMessageAddHeaderMethod?.name,
            messageManagerClass = messageManagerClass?.name,
            messageManagerGetInstanceMethod = messageManagerGetInstanceMethod?.name,
            messageManagerFindTaskMethod = messageManagerFindTaskMethod?.name,
            messageManagerRegisterTaskMethod = messageManagerRegisterTaskMethod?.name,
            messageManagerSendMethod = messageManagerSendMethod?.name,
            tbHttpMessageTaskClass = tbHttpMessageTaskClass?.name,
            tbHttpMessageTaskConstructor = tbHttpMessageTaskConstructor?.let { "<init>(int,String)" },
            httpMessageTaskSetResponsedClassMethod = httpMessageTaskSetResponsedClassMethod?.name,
            tbHttpMessageTaskSetIsNeedTbsMethod = tbHttpMessageTaskSetIsNeedTbsMethod?.name,
            bdUniqueIdClass = bdUniqueIdClass?.name,
            bdUniqueIdGenMethod = bdUniqueIdGenMethod?.name,
            tbadkCoreApplicationClass = tbadkCoreApplicationClass?.name,
            tbadkCoreApplicationGetInstMethod = tbadkCoreApplicationGetInstMethod?.name,
            tbadkCoreApplicationGetZidMethod = tbadkCoreApplicationGetZidMethod?.name,
            tbConfigClass = tbConfigClass?.name,
            tbConfigServerAddressField = tbConfigServerAddressField?.name,
            tbConfigPbFloorAgreeUrlField = tbConfigPbFloorAgreeUrlField?.name,
            cmdConfigHttpClass = cmdConfigHttpClass?.name,
            cmdPbFloorAgreeField = cmdPbFloorAgreeField?.name,
            agreeResponseClass = agreeResponseClass?.name,
            agreeDecodeLogicMethod = agreeDecodeLogicMethod?.name,
        )
    }

    fun isStringMapField(field: Field): Boolean {
        if (!Map::class.java.isAssignableFrom(field.type)) return false
        val type = field.genericType
        if (type !is ParameterizedType) return true
        val args = type.actualTypeArguments
        return args.size == 2 && args.all(::isStringTypeArgument)
    }

    private fun isStringTypeArgument(type: Type): Boolean {
        return when (type) {
            String::class.java -> true
            is WildcardType -> type.upperBounds.any { it == String::class.java }
            else -> false
        }
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun declaredMethods(label: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? =
        scanSubStep("ReplyVisibilityProbeHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }

    private fun declaredFields(label: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? =
        scanSubStep("ReplyVisibilityProbeHook.$label.Fields", logger, null) {
            clazz.declaredFields.toList()
        }

    private fun isIntType(type: Class<*>): Boolean =
        ScanReflection.isIntType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
