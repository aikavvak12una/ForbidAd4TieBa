package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.FeedInfoLogObjectSchema
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSchema
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * Limits the structural work performed while building one detailed feed log entry.
 *
 * `maxOutputChars` is applied after the bounded JSON graph is serialized; it is
 * not a construction-time byte budget. The node, reflection-read, and string
 * limits bound construction before serialization.
 */
internal data class FeedInfoLogLimits(
    val maxFieldsPerObject: Int = 32,
    val maxListItems: Int = 8,
    val maxMapEntries: Int = 16,
    val maxReflectionReads: Int = 64,
    val maxObjects: Int = 32,
    val maxDepth: Int = 4,
    val maxOutputChars: Int = 6_000,
    val maxStringChars: Int = 160,
) {
    init {
        require(maxFieldsPerObject > 0)
        require(maxListItems > 0)
        require(maxMapEntries > 0)
        require(maxReflectionReads > 0)
        require(maxObjects > 0)
        require(maxDepth >= 0)
        require(maxOutputChars > 0)
        require(maxStringChars > 0)
    }
}

/** Accepts a short diagnostic burst, then samples without scheduling work. */
internal class FeedInfoLogSampler(
    private val initialSamples: Int = 3,
    private val interval: Int = 20,
) {
    private val sequence = AtomicLong()

    init {
        require(initialSamples >= 0)
        require(interval > 0)
    }

    fun shouldSample(): Boolean {
        val current = sequence.incrementAndGet()
        return current <= initialSamples.toLong() || current % interval.toLong() == 0L
    }
}

internal class FeedInfoLogWorkBudget(
    private val limits: FeedInfoLogLimits,
) {
    var reflectionReads = 0
        private set
    var nodes = 0
        private set
    var listItems = 0
        private set
    var mapEntries = 0
        private set
    var truncated = false
        private set

    fun enterNode(depth: Int): Boolean {
        if (depth > limits.maxDepth || nodes >= limits.maxObjects) {
            truncated = true
            return false
        }
        nodes++
        return true
    }

    fun consumeReflectionRead(): Boolean {
        if (reflectionReads >= limits.maxReflectionReads) {
            truncated = true
            return false
        }
        reflectionReads++
        return true
    }

    fun consumeListItem(): Boolean {
        if (listItems >= limits.maxListItems) {
            truncated = true
            return false
        }
        listItems++
        return true
    }

    fun consumeMapEntry(): Boolean {
        if (mapEntries >= limits.maxMapEntries) {
            truncated = true
            return false
        }
        mapEntries++
        return true
    }

    fun markTruncated() {
        truncated = true
    }
}

/** Formats only precomputed schemas and bounded primitive/list/map values. */
internal class FeedInfoLogFormatter(
    private val schema: FeedInfoLogSchema,
    private val limits: FeedInfoLogLimits = FeedInfoLogLimits(),
) {
    fun format(cardData: Any): String {
        val budget = FeedInfoLogWorkBudget(limits)
        val json = formatObject(cardData, 0, budget)
        if (schema.truncated || budget.truncated) json.put("_truncated", true)
        val text = json.toString()
        if (text.length <= limits.maxOutputChars) return text
        return JSONObject().apply {
            put("_class", className(cardData.javaClass))
            put("_truncated", true)
            put("_outputChars", text.length)
        }.toString()
    }

    private fun formatObject(value: Any, depth: Int, budget: FeedInfoLogWorkBudget): JSONObject =
        formatObject(value, schema.schemaFor(value), depth, budget)

    private fun formatObject(
        value: Any,
        objectSchema: FeedInfoLogObjectSchema?,
        depth: Int,
        budget: FeedInfoLogWorkBudget,
    ): JSONObject {
        val json = JSONObject()
        if (!budget.enterNode(depth)) {
            json.put("_class", className(value.javaClass))
            json.put("_truncated", true)
            return json
        }

        if (objectSchema == null) {
            json.put("_class", className(value.javaClass))
            return json
        }

        objectSchema.templateKeyMethod?.let { method ->
            if (method.declaringClass.isInstance(value)) {
                if (budget.consumeReflectionRead()) {
                    try {
                        (method.invoke(value) as? String)?.let { json.put("templateKey", boundedString(it, budget)) }
                    } catch (_: Throwable) {
                        // A failed accessor must not hide the remaining bounded fields.
                    }
                }
            }
        }

        var fieldCount = 0
        for (field in objectSchema.fields) {
            if (fieldCount >= limits.maxFieldsPerObject) {
                budget.markTruncated()
                break
            }
            if (!budget.consumeReflectionRead()) break
            fieldCount++
            val fieldValue = try {
                field.get(value)
            } catch (_: Throwable) {
                continue
            }
            json.put(field.name, formatValue(fieldValue, depth + 1, budget))
        }

        json.put("_class", className(value.javaClass))
        if (objectSchema.truncated) budget.markTruncated()
        return json
    }

    private fun formatValue(value: Any?, depth: Int, budget: FeedInfoLogWorkBudget): Any {
        if (value == null) return JSONObject.NULL
        return when (value) {
            is String -> boundedString(value, budget)
            is Boolean -> value
            is Byte -> value
            is Short -> value
            is Int -> value
            is Long -> value
            is Float -> if (value.isFinite()) value else boundedString(value.toString(), budget)
            is Double -> if (value.isFinite()) value else boundedString(value.toString(), budget)
            is Char -> value.toString()
            is Enum<*> -> value.name
            is Class<*> -> value.name
            is List<*> -> formatList(value, depth, budget)
            is Map<*, *> -> formatMap(value, depth, budget)
            is Array<*> -> formatArray(value, depth, budget)
            else -> {
                val objectSchema = schema.schemaFor(value)
                if (objectSchema != null) {
                    formatObject(value, objectSchema, depth, budget)
                } else {
                    JSONObject().put("_class", className(value.javaClass))
                }
            }
        }
    }

    private fun formatList(list: List<*>, depth: Int, budget: FeedInfoLogWorkBudget): Any {
        if (!budget.enterNode(depth)) return truncatedSummary(list)
        val array = JSONArray()
        val iterator = list.iterator()
        var emitted = 0
        while (iterator.hasNext() && emitted < limits.maxListItems) {
            if (!budget.consumeListItem()) break
            array.put(formatValue(iterator.next(), depth + 1, budget))
            emitted++
        }
        if (emitted < list.size) {
            array.put("... ${list.size - emitted} more items")
            budget.markTruncated()
        }
        return array
    }

    private fun formatMap(map: Map<*, *>, depth: Int, budget: FeedInfoLogWorkBudget): Any {
        if (!budget.enterNode(depth)) return truncatedSummary(map)
        val json = JSONObject()
        val iterator = map.entries.iterator()
        var emitted = 0
        while (iterator.hasNext() && emitted < limits.maxMapEntries) {
            if (!budget.consumeMapEntry()) break
            val entry = iterator.next()
            json.put(safeKey(entry.key, budget), formatValue(entry.value, depth + 1, budget))
            emitted++
        }
        if (emitted < map.size) {
            json.put("_truncated", "${map.size - emitted} more entries")
            budget.markTruncated()
        }
        return json
    }

    private fun formatArray(array: Array<*>, depth: Int, budget: FeedInfoLogWorkBudget): Any {
        if (!budget.enterNode(depth)) return truncatedSummary(array)
        val json = JSONArray()
        var emitted = 0
        while (emitted < array.size && emitted < limits.maxListItems) {
            if (!budget.consumeListItem()) break
            json.put(formatValue(array[emitted], depth + 1, budget))
            emitted++
        }
        if (emitted < array.size) {
            json.put("... ${array.size - emitted} more items")
            budget.markTruncated()
        }
        return json
    }

    private fun safeKey(key: Any?, budget: FeedInfoLogWorkBudget): String {
        return when (key) {
            null -> "null"
            is String -> boundedString(key, budget)
            is Boolean, is Byte, is Short, is Int, is Long -> key.toString()
            is Float -> if (key.isFinite()) key.toString() else boundedString(key.toString(), budget)
            is Double -> if (key.isFinite()) key.toString() else boundedString(key.toString(), budget)
            is Char -> key.toString()
            is Enum<*> -> key.name
            is Class<*> -> key.name
            else -> "<${className(key.javaClass)}>"
        }
    }

    private fun truncatedSummary(value: Any): JSONObject = JSONObject().apply {
        put("_class", className(value.javaClass))
        put("_truncated", true)
    }

    private fun boundedString(value: String, budget: FeedInfoLogWorkBudget? = null): String {
        return if (value.length <= limits.maxStringChars) {
            value
        } else {
            budget?.markTruncated()
            value.take(limits.maxStringChars) + "..."
        }
    }

    private fun className(type: Class<*>): String = type.simpleName.ifBlank { type.name }
}
