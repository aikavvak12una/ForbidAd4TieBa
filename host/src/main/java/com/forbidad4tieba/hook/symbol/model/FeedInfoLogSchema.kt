package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.ArrayDeque

class FeedInfoLogObjectSchema(
    val owner: Class<*>,
    val fields: List<Field>,
    val templateKeyMethod: Method?,
    val truncated: Boolean,
)

/**
 * Precomputes the known card shape while the feed symbol contract is resolved.
 *
 * Discovery is bounded to a small class graph. Runtime formatting only looks up
 * these prepared schemas; it never enumerates host members.
 */
class FeedInfoLogSchema private constructor(
    private val schemas: List<FeedInfoLogObjectSchema>,
    val truncated: Boolean,
    val discoveryError: String? = null,
) {
    fun schemaFor(value: Any): FeedInfoLogObjectSchema? {
        val actual = value.javaClass
        return schemas.firstOrNull { it.owner == actual }
            ?: schemas.firstOrNull { it.owner.isAssignableFrom(actual) }
    }

    companion object {
        private const val MAX_SCHEMA_CLASSES = 8
        private const val MAX_SCHEMA_FIELDS = 32
        private const val MAX_DISCOVERY_ERROR_CHARS = 240

        fun from(bindMethod: Method, templateKeyMethodName: String?): FeedInfoLogSchema {
            val cardClass = bindMethod.parameterTypes.singleOrNull()
                ?: return FeedInfoLogSchema(
                    emptyList(),
                    true,
                    "bind method must have exactly one parameter",
                )
            val queue = ArrayDeque<Class<*>>()
            val seen = mutableSetOf<Class<*>>()
            val schemas = mutableListOf<FeedInfoLogObjectSchema>()
            var truncated = false
            var discoveryError: String? = null

            fun recordFailure(failure: Throwable) {
                if (discoveryError == null) {
                    discoveryError = failure.message
                        ?.take(MAX_DISCOVERY_ERROR_CHARS)
                        ?.takeIf { it.isNotBlank() }
                        ?: failure.javaClass.name
                }
                truncated = true
            }

            queue.add(cardClass)
            try {
                while (queue.isNotEmpty()) {
                    if (schemas.size >= MAX_SCHEMA_CLASSES) {
                        truncated = true
                        break
                    }
                    val owner = queue.removeFirst()
                    if (!seen.add(owner)) continue

                    val declaredFields = try {
                        owner.declaredFields
                            .asSequence()
                            .filter {
                                !Modifier.isStatic(it.modifiers) &&
                                    !it.isSynthetic &&
                                    !it.name.startsWith("$")
                            }
                            .toList()
                    } catch (t: Throwable) {
                        recordFailure(t)
                        continue
                    }
                    val fields = declaredFields.take(MAX_SCHEMA_FIELDS).mapNotNull { field ->
                        try {
                            field.isAccessible = true
                            field
                        } catch (t: Throwable) {
                            recordFailure(t)
                            null
                        }
                    }
                    val schemaTruncated = declaredFields.size > fields.size
                    if (schemaTruncated) truncated = true
                    schemas += FeedInfoLogObjectSchema(
                        owner = owner,
                        fields = fields,
                        templateKeyMethod = resolveTemplateKeyMethod(
                            owner,
                            templateKeyMethodName,
                            { recordFailure(it) },
                        ),
                        truncated = schemaTruncated,
                    )

                    for (field in fields) {
                        val types = try {
                            schemaTypes(field.type, field.genericType)
                        } catch (t: Throwable) {
                            recordFailure(t)
                            emptyList()
                        }
                        for (type in types) {
                            if (type !in seen) {
                                if (queue.size < MAX_SCHEMA_CLASSES * 2) {
                                    queue.add(type)
                                } else {
                                    truncated = true
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                recordFailure(t)
            }
            return FeedInfoLogSchema(schemas, truncated, discoveryError)
        }

        private fun resolveTemplateKeyMethod(
            owner: Class<*>,
            name: String?,
            recordFailure: (Throwable) -> Unit,
        ): Method? {
            if (name.isNullOrBlank()) return null
            return try {
                owner.getMethod(name).takeIf {
                    !Modifier.isStatic(it.modifiers) &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == String::class.java
                }?.apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                recordFailure(t)
                null
            }
        }

        private fun schemaTypes(rawType: Class<*>, genericType: Type): List<Class<*>> {
            val types = mutableListOf<Class<*>>()
            addSchemaType(rawType, types)
            collectGenericTypes(genericType, types)
            return types.distinct()
        }

        private fun addSchemaType(type: Class<*>, output: MutableList<Class<*>>) {
            if (type.isArray) {
                type.componentType?.let { addSchemaType(it, output) }
                return
            }
            if (isSchemaCandidate(type)) output += type
        }

        private fun collectGenericTypes(type: Type?, output: MutableList<Class<*>>) {
            when (type) {
                is Class<*> -> addSchemaType(type, output)
                is ParameterizedType -> {
                    (type.rawType as? Class<*>)?.let { addSchemaType(it, output) }
                    type.actualTypeArguments.forEach { collectGenericTypes(it, output) }
                }
                is WildcardType -> type.upperBounds.forEach { collectGenericTypes(it, output) }
                is GenericArrayType -> collectGenericTypes(type.genericComponentType, output)
            }
        }

        private fun isSchemaCandidate(type: Class<*>): Boolean {
            if (type.isPrimitive || type.isEnum || type.isArray) return false
            if (type == String::class.java || type == Any::class.java || type == CharSequence::class.java) return false
            if (type == Boolean::class.java || type == Char::class.java || type == Class::class.java) return false
            if (Number::class.java.isAssignableFrom(type)) return false
            val name = type.name
            return type.classLoader != null &&
                !name.startsWith("java.") &&
                !name.startsWith("javax.") &&
                !name.startsWith("android.") &&
                !name.startsWith("androidx.") &&
                !name.startsWith("kotlin.") &&
                !name.startsWith("org.json.")
        }
    }
}
