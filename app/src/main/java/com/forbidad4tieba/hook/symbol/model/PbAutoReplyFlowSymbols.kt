package com.forbidad4tieba.hook.symbol.model

import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Cached evidence for the native ordinary-reply path and its success-only navigation. */
data class PbAutoReplyFlowSymbols(
    val sendMethodSpec: String? = null,
    val writeModelField: String? = null,
    val writeDataField: String? = null,
    val uploadMethod: String? = null,
    val callbackMethodSpec: String? = null,
    val transientMethod: String? = null,
    val transientParamsClass: String? = null,
) {
    internal fun fields(): List<Pair<String, String?>> = listOf(
        "sendMethodSpec" to sendMethodSpec,
        "writeModelField" to writeModelField,
        "writeDataField" to writeDataField,
        "uploadMethod" to uploadMethod,
        "callbackMethodSpec" to callbackMethodSpec,
        "transientMethod" to transientMethod,
        "transientParamsClass" to transientParamsClass,
    )

    internal fun missing(): List<String> = fields().filter { it.second.isNullOrBlank() }
        .map { "pbAutoReplyFlow.${it.first}" }

    internal fun toJson(): JSONObject = JSONObject().apply {
        fields().forEach { (name, value) -> put(name, value) }
    }

    internal companion object {
        fun fromJson(json: JSONObject?): PbAutoReplyFlowSymbols {
            fun value(key: String): String? = json?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
            return PbAutoReplyFlowSymbols(
                value("sendMethodSpec"), value("writeModelField"), value("writeDataField"),
                value("uploadMethod"), value("callbackMethodSpec"), value("transientMethod"),
                value("transientParamsClass"),
            )
        }
    }
}

internal data class PbAutoReplyFlowTargets(
    val send: Method,
    val writeModel: Field,
    val writeData: Field,
    val upload: Method,
    val callback: Method,
    val transientPost: Method,
    val getType: Method,
    val getFloor: Method,
    val getSubPostId: Method,
    val setWriteData: Method,
    val getErrorCode: Method,
)
