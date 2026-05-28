package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

object FeedInfoLogHook {
    private const val TAG = "[FeedInfoLogHook]"
    private val NO_METHOD = Any()
    private val sTemplateKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sInstanceFieldsCache = ConcurrentHashMap<Class<*>, Array<java.lang.reflect.Field>>(32)
    @Volatile private var templateKeyMethodName: String? = null

    internal fun hook(symbols: FeedInfoLogSymbols) {
        val newTemplateKeyMethodName = symbols.templateKeyMethodName
        if (templateKeyMethodName != newTemplateKeyMethodName) {
            sTemplateKeyMethodCache.clear()
            templateKeyMethodName = newTemplateKeyMethodName
        }

        val mod = XposedCompat.module ?: return
        mod.hook(symbols.bindMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val cardData = chain.args[0] ?: return@intercept result
                val json = buildCardJson(cardData)
                logLong("$TAG $json")
            } catch (t: Throwable) {
                XposedCompat.logD("$TAG error: ${t.message}")
            }
            result
        }
        XposedCompat.log(
            "$TAG hook INSTALLED: ${symbols.bindMethod.declaringClass.name}.${symbols.bindMethod.name}",
        )
    }

    private fun buildCardJson(cardData: Any): String {
        val json = JSONObject()
        val fields = getInstanceFields(cardData.javaClass)
        val nameMap = parseToStringNameMap(cardData)
        for (field in fields) {
            val value = try {
                field.get(cardData)
            } catch (_: Throwable) {
                continue
            }
            val displayName = nameMap[field.name] ?: field.name
            json.put(displayName, toJsonValue(value))
        }
        return json.toString()
    }

    private fun parseToStringNameMap(obj: Any): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val str = obj.toString()
            val openParen = str.indexOf('(')
            if (openParen < 0) return map
            val inner = str.substring(openParen + 1, str.length - 1)
            val keys = mutableListOf<String>()
            var i = 0
            while (i < inner.length) {
                val eq = inner.indexOf('=', i)
                if (eq < 0) break
                keys.add(inner.substring(i, eq).trim())
                var depth = 0
                var j = eq + 1
                while (j < inner.length) {
                    when (inner[j]) {
                        '(', '[', '{' -> depth++
                        ')', ']', '}' -> depth--
                        ',' -> if (depth == 0) break
                    }
                    j++
                }
                i = j + 1
                while (i < inner.length && inner[i] == ' ') i++
            }

            val instanceFields = getInstanceFields(obj.javaClass)
            for ((idx, field) in instanceFields.withIndex()) {
                if (idx < keys.size) {
                    map[field.name] = keys[idx]
                }
            }
        } catch (_: Throwable) {
            // Best-effort display names only.
        }
        return map
    }

    private fun toJsonValue(value: Any?): Any {
        if (value == null) return JSONObject.NULL
        return when (value) {
            is String -> value
            is Boolean -> value
            is Int -> value
            is Long -> value
            is Float -> value
            is Double -> value
            is List<*> -> buildTemplateListJson(value)
            is Map<*, *> -> mapToJson(value)
            else -> objectToJson(value)
        }
    }

    private fun buildTemplateListJson(list: List<*>): JSONArray {
        val arr = JSONArray()
        for (item in list) {
            if (item == null) {
                arr.put(JSONObject.NULL)
                continue
            }
            val itemJson = JSONObject()
            getTemplateKey(item)?.let { itemJson.put("templateKey", it) }
            itemJson.put("class", item.javaClass.simpleName)
            for (field in getInstanceFields(item.javaClass)) {
                val value = try {
                    field.get(item)
                } catch (_: Throwable) {
                    continue
                }
                itemJson.put(field.name, toSimpleValue(value))
            }
            arr.put(itemJson)
        }
        return arr
    }

    private fun toSimpleValue(value: Any?): Any {
        if (value == null) return JSONObject.NULL
        return when (value) {
            is String -> value
            is Boolean -> value
            is Int -> value
            is Long -> value
            is Float -> value
            is Double -> value
            is List<*> -> "[${value.size} items]"
            is Map<*, *> -> mapToJson(value)
            else -> {
                val str = try {
                    value.toString()
                } catch (_: Throwable) {
                    value.javaClass.simpleName
                }
                if (str.length > 200) str.take(200) + "..." else str
            }
        }
    }

    private fun mapToJson(map: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in map) {
            obj.put(key?.toString() ?: "null", toSimpleValue(value))
        }
        return obj
    }

    private fun objectToJson(value: Any): Any {
        val str = try {
            value.toString()
        } catch (_: Throwable) {
            return value.javaClass.simpleName
        }
        return if (str.length > 300) str.take(300) + "..." else str
    }

    private fun getTemplateKey(item: Any): String? {
        val methodName = templateKeyMethodName ?: return null
        val method = getTemplateKeyMethod(item.javaClass, methodName) ?: return null
        return try {
            method.invoke(item) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun getTemplateKeyMethod(cls: Class<*>, methodName: String): Method? {
        sTemplateKeyMethodCache[cls]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }
        return try {
            cls.getMethod(methodName).apply {
                if (parameterTypes.isNotEmpty() || returnType != String::class.java) {
                    sTemplateKeyMethodCache[cls] = NO_METHOD
                    return null
                }
                isAccessible = true
                sTemplateKeyMethodCache[cls] = this
            }
        } catch (_: Throwable) {
            sTemplateKeyMethodCache[cls] = NO_METHOD
            null
        }
    }

    private fun getInstanceFields(cls: Class<*>): Array<java.lang.reflect.Field> {
        return sInstanceFieldsCache.computeIfAbsent(cls) {
            it.declaredFields.filter { field ->
                !Modifier.isStatic(field.modifiers) && !field.name.startsWith("$")
            }.onEach { field ->
                field.isAccessible = true
            }.toTypedArray()
        }
    }

    private fun logLong(msg: String) {
        val content = if (msg.startsWith(TAG)) msg.substring(TAG.length).trimStart() else msg
        val chunkPrefix = "$TAG "
        val maxContent = 3900 - chunkPrefix.length - 12
        if (content.length <= maxContent) {
            XposedCompat.logD("$chunkPrefix$content")
            return
        }

        val totalParts = (content.length + maxContent - 1) / maxContent
        var offset = 0
        var part = 1
        while (offset < content.length) {
            val end = (offset + maxContent).coerceAtMost(content.length)
            XposedCompat.logD("$chunkPrefix[$part/$totalParts] ${content.substring(offset, end)}")
            offset = end
            part++
        }
    }
}
