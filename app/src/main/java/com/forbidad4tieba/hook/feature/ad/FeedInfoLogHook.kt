package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * 对 FeedCardView 的绑定方法做 hook，记录首页信息流每张帖子卡片的信息。
 *
 * 输出为每张卡片一行结构化 JSON，便于阅读和筛选。
 */
object FeedInfoLogHook {
    private const val TAG = "[FeedInfoLogHook]"
    private val NO_METHOD = Any()
    private val sTemplateKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sInstanceFieldsCache = ConcurrentHashMap<Class<*>, Array<java.lang.reflect.Field>>(32)
    @Volatile private var templateKeyMethodName: String? = null

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        val newTemplateKeyMethodName = symbols.feedTemplateKeyMethod?.takeIf { it.isNotBlank() }
        if (templateKeyMethodName != newTemplateKeyMethodName) {
            sTemplateKeyMethodCache.clear()
            templateKeyMethodName = newTemplateKeyMethodName
        }
        val bindMethodName = symbols.feedCardBindMethod
        if (bindMethodName.isNullOrBlank()) {
            XposedCompat.log("$TAG SKIP: feedCardBindMethod not resolved")
            return
        }

        val mod = XposedCompat.module ?: return
        val feedCardViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl)
        if (feedCardViewClass == null) {
            XposedCompat.log("$TAG SKIP: ${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS} not found")
            return
        }

        // 安装时只查找一次，绑定方法名由 HookSymbolResolver 解析。
        // 这里只按已知签名过滤，void 返回值加一个非基本类型参数。
        val bindMethod = ReflectionUtils.findMethodInHierarchy(feedCardViewClass, bindMethodName) { m ->
            m.returnType == Void.TYPE &&
                m.parameterTypes.size == 1 &&
                !m.parameterTypes[0].isPrimitive
        }
        if (bindMethod == null) {
            XposedCompat.log("$TAG SKIP: method $bindMethodName not found on ${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}")
            return
        }
        bindMethod.isAccessible = true

        mod.hook(bindMethod).intercept { chain ->
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
        XposedCompat.log("$TAG hook INSTALLED: ${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodName")
    }

    /**
     * 为单张卡片构建精简 JSON 对象。
     * 通过反射从 CardData，也就是 y99 实例里取有效字段。
     */
    private fun buildCardJson(cardData: Any): String {
        val json = JSONObject()
        val cls = cardData.javaClass
        val fields = getInstanceFields(cls)

        // 根据已知 toString() 格式建立字段名映射。
        // y99.toString() 会输出 "CardData(dataList=..., schema=..., ...)"。
        val nameMap = parseToStringNameMap(cardData)

        for (field in fields) {
            val value = try { field.get(cardData) } catch (_: Throwable) { continue }
            val displayName = nameMap[field.name] ?: field.name
            json.put(displayName, toJsonValue(value))
        }

        return json.toString()
    }

    /**
     * 解析 toString() 输出，把混淆字段名映射为可读名称。
     * 例如 "CardData(dataList=..., schema=..., threadId=...)"。
     * 可以得到 {a -> dataList, b -> schema, e -> threadId, ...}。
     */
    private fun parseToStringNameMap(obj: Any): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val str = obj.toString()
            // 提取 "CardData(key1=val1, key2=val2, ...)" 内部内容。
            val openParen = str.indexOf('(')
            if (openParen < 0) return map
            val inner = str.substring(openParen + 1, str.length - 1)

            // 按顺序从 toString 里提取 key 名称。
            val keys = mutableListOf<String>()
            var i = 0
            while (i < inner.length) {
                val eq = inner.indexOf('=', i)
                if (eq < 0) break
                val key = inner.substring(i, eq).trim()
                keys.add(key)
                // 跳过 value，并跟踪嵌套深度。
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
                i = j + 1 // 跳过逗号。
                // 去掉逗号后的前导空格。
                while (i < inner.length && inner[i] == ' ') i++
            }

            // 按字段声明顺序匹配 toString key 顺序。
            val cls = obj.javaClass
            val instanceFields = getInstanceFields(cls)
            for ((idx, field) in instanceFields.withIndex()) {
                if (idx < keys.size) {
                    map[field.name] = keys[idx]
                }
            }
        } catch (_: Throwable) { /* 尽量解析 */ }
        return map
    }

    /**
     * 把值转换成适合放进 JSON 的形式。
     */
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

    /**
     * 把模板项列表 dataList 或 List<vk9> 转成 JSON 数组。
     * 每一项包含 template key 和精简摘要。
     */
    private fun buildTemplateListJson(list: List<*>): JSONArray {
        val arr = JSONArray()
        for (item in list) {
            if (item == null) { arr.put(JSONObject.NULL); continue }
            val itemJson = JSONObject()
            val key = getTemplateKey(item)
            if (key != null) itemJson.put("templateKey", key)
            itemJson.put("class", item.javaClass.simpleName)

            // 输出每个模板项的实例字段。
            val fields = getInstanceFields(item.javaClass)
            for (field in fields) {
                val v = try { field.get(item) } catch (_: Throwable) { continue }
                itemJson.put(field.name, toSimpleValue(v))
            }
            arr.put(itemJson)
        }
        return arr
    }

    /**
     * 浅层转换，避免复杂嵌套对象递归过深。
     */
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
                val str = try { value.toString() } catch (_: Throwable) { value.javaClass.simpleName }
                if (str.length > 200) str.take(200) + "..." else str
            }
        }
    }

    private fun mapToJson(map: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) {
            val key = k?.toString() ?: "null"
            obj.put(key, toSimpleValue(v))
        }
        return obj
    }

    private fun objectToJson(value: Any): Any {
        val str = try { value.toString() } catch (_: Throwable) { return value.javaClass.simpleName }
        if (str.length > 300) return str.take(300) + "..."
        return str
    }

    private fun getTemplateKey(item: Any): String? {
        val methodName = templateKeyMethodName ?: return null
        val method = getTemplateKeyMethod(item.javaClass, methodName) ?: return null
        return try { method.invoke(item) as? String } catch (_: Throwable) { null }
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

    /**
     * 分块记录可能超过 logcat 约 4096 字节限制的消息。
     * 每块都带 [FeedInfoLogHook]，方便用 `grep FeedInfoLogHook` 找全。
     */
    private fun logLong(msg: String) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        // 去掉 msg 前面的 TAG，后面给每个分块重新加上。
        val content = if (msg.startsWith(TAG)) msg.substring(TAG.length).trimStart() else msg
        val chunkPrefix = "$TAG "
        val maxContent = 3900 - chunkPrefix.length - 12 // 预留 [nn/nn] 前缀空间。

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
