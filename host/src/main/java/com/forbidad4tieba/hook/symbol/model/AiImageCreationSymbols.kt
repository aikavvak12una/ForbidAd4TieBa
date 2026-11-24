package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.Method

enum class AiImageMethod {
    IMAGE_JUMP_DATA,
    IMAGE_JUMP_SCHEME,
    ROUTE_DISPATCH,
    WEB_CONFIG_BUILD,
    INPUT_AI_VISIBLE,
    INPUT_AI_SCHEMA,
    PB_GUIDE_BIND,
    PB_GUIDE_CAPSULE,
    IMAGE_BOTTOM_TIP_BIND,
}

enum class AiImageField { WEB_URL }

internal data class AiImageCreationScanSymbols(
    val methods: Map<AiImageMethod, Method> = emptyMap(),
    val fields: Map<AiImageField, Field> = emptyMap(),
)

/** Complete, restored members only. The route policy and installation belong to the feature. */
data class AiImageCreationSymbols(
    val methods: Map<AiImageMethod, Method>,
    val fields: Map<AiImageField, Field>,
) {
    init {
        require(methods.keys == AiImageMethod.entries.toSet()) { "Incomplete AI image methods" }
        require(fields.keys == AiImageField.entries.toSet()) { "Incomplete AI image fields" }
    }

    operator fun get(role: AiImageMethod): Method = methods.getValue(role)
    operator fun get(role: AiImageField): Field = fields.getValue(role)
}
