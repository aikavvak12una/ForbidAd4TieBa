package com.forbidad4tieba.hook.symbol.model

import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.contract.SymbolField
import org.json.JSONObject

/** Immutable values keyed by their owning contract, plus scan-wide metadata. */
@ConsistentCopyVisibility
data class HookSymbols internal constructor(
    private val values: Map<SymbolField<*>, Any?> = emptyMap(),
    val meta: ScanMeta = ScanMeta(),
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(field: SymbolField<T>): T =
        if (values.containsKey(field)) values[field] as T else field.default

    val scanSupportState: String
        get() = meta.availability.scanSupportState
    val scanTargetVersionCode: Long?
        get() = meta.availability.scanTargetVersionCode
    val scanTargetVersionName: String?
        get() = meta.availability.scanTargetVersionName
    val scanTargetVersionType: String?
        get() = meta.availability.scanTargetVersionType
    val scanErrors: List<String>
        get() = meta.scanErrors
    val source: String
        get() = meta.source
    val createdAt: Long
        get() = meta.createdAt
    val cacheSchemaVersion: Int
        get() = meta.cacheSchemaVersion
    val dexKitRuleVersion: Int
        get() = meta.dexKitRuleVersion

    fun withScanSupport(
        state: String,
        targetVersionCode: Long? = scanTargetVersionCode,
        targetVersionName: String? = scanTargetVersionName,
        targetVersionType: String? = scanTargetVersionType,
    ): HookSymbols {
        return withMeta(
            ScanMeta(
                availability = ScanAvailabilityMeta(
                    scanSupportState = state,
                    scanTargetVersionCode = targetVersionCode,
                    scanTargetVersionName = targetVersionName,
                    scanTargetVersionType = targetVersionType,
                ),
                scanErrors = meta.scanErrors,
                source = meta.source,
                createdAt = meta.createdAt,
                cacheSchemaVersion = meta.cacheSchemaVersion,
                dexKitRuleVersion = meta.dexKitRuleVersion,
            ),
        )
    }

    private fun withMeta(meta: ScanMeta): HookSymbols {
        return HookSymbols(
            values = values,
            meta = meta,
        )
    }

    fun toJson(): String = JSONObject().apply {
        SymbolContracts.all.forEach { contract ->
            contract.fields.forEach { it.write(this, this@HookSymbols) }
        }
            put("scanSupportState", scanSupportState)
            put("scanTargetVersionCode", scanTargetVersionCode)
            put("scanTargetVersionName", scanTargetVersionName)
            put("scanTargetVersionType", scanTargetVersionType)
            if (scanErrors.isNotEmpty()) {
                val array = org.json.JSONArray()
                scanErrors.forEach { array.put(it) }
                put("scanErrors", array)
            }

            put("source", source)
            put("createdAt", createdAt)
            put("cacheSchemaVersion", cacheSchemaVersion)
            put("dexKitRuleVersion", dexKitRuleVersion)
    }.toString()

    companion object {
        const val CACHE_SCHEMA_VERSION = 60
        const val DEXKIT_RULE_VERSION = 70

        fun unsupported(scanErrors: List<String> = emptyList(), createdAt: Long = 0L): HookSymbols =
            buildHookSymbols {
                this.scanErrors = scanErrors
                this.createdAt = createdAt
            }

        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                buildHookSymbols {
                    SymbolContracts.all.forEach { contract ->
                        contract.fields.forEach { read(it, obj) }
                    }
                    scanSupportState = obj.optString("scanSupportState", ScanSupportState.UNKNOWN)
                    scanTargetVersionCode = obj.optLongOrNull("scanTargetVersionCode")
                    scanTargetVersionName = obj.optStringOrNull("scanTargetVersionName")
                    scanTargetVersionType = obj.optStringOrNull("scanTargetVersionType")
                    scanErrors = obj.optStringArray("scanErrors")

                    source = obj.optString("source", "unsupported")
                    createdAt = obj.optLong("createdAt", 0L)
                    cacheSchemaVersion = obj.optInt("cacheSchemaVersion", 0)
                    dexKitRuleVersion = obj.optInt("dexKitRuleVersion", 0)
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (isNull(name)) return null
            val s = optString(name)
            return s.ifEmpty { null }
        }

        private fun JSONObject.optIntOrNull(name: String): Int? {
            if (!has(name) || isNull(name)) return null
            return optInt(name).takeIf { it != 0 }
        }

        private fun JSONObject.optLongOrNull(name: String): Long? {
            if (!has(name) || isNull(name)) return null
            return try {
                getLong(name)
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringArray(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) out.add(value)
            }
            return out
        }

        private fun JSONObject.optIntArray(name: String): List<Int> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<Int>(array.length())
            for (i in 0 until array.length()) {
                val value = array.optInt(i, 0)
                if (value != 0) out.add(value)
            }
            return out
        }
    }
}
