package com.forbidad4tieba.hook.symbol.model

import org.json.JSONObject
import java.lang.reflect.Method

data class DefaultPopupSymbols(
    val firstLikeResponseClass: String? = null,
    val firstLikeToastMethod: String? = null,
    val notificationGuideClass: String? = null,
    val notificationGuideMethod: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("firstLikeResponseClass", firstLikeResponseClass)
        put("firstLikeToastMethod", firstLikeToastMethod)
        put("notificationGuideClass", notificationGuideClass)
        put("notificationGuideMethod", notificationGuideMethod)
    }

    companion object {
        fun fromJson(value: JSONObject?): DefaultPopupSymbols {
            fun name(key: String): String? = (value?.opt(key) as? String)?.takeIf { it.isNotBlank() }
            return DefaultPopupSymbols(
                firstLikeResponseClass = name("firstLikeResponseClass"),
                firstLikeToastMethod = name("firstLikeToastMethod"),
                notificationGuideClass = name("notificationGuideClass"),
                notificationGuideMethod = name("notificationGuideMethod"),
            )
        }
    }
}

data class FirstLikePopupTargets(
    val responseClass: Class<*>,
    val parseToastMethod: Method,
)
