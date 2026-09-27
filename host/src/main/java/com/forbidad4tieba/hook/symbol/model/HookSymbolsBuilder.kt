package com.forbidad4tieba.hook.symbol.model

import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.contract.SymbolField
import org.json.JSONObject

/** Mutable only during a scan/cache decode; no feature-specific fields live here. */
internal class HookSymbolsBuilder {
    private val values = LinkedHashMap<SymbolField<*>, Any?>()

    operator fun <T> set(field: SymbolField<T>, value: T) { values[field] = value }
    internal fun <T> read(field: SymbolField<T>, json: JSONObject) { this[field] = field.decode(json) }

    var scanSupportState: String = ScanSupportState.UNKNOWN
    var scanTargetVersionCode: Long? = null
    var scanTargetVersionName: String? = null
    var scanTargetVersionType: String? = null
    var scanErrors: List<String> = emptyList()
    var source: String = "unsupported"
    var createdAt: Long = 0L
    var cacheSchemaVersion: Int = HookSymbols.CACHE_SCHEMA_VERSION
    var dexKitRuleVersion: Int = HookSymbols.DEXKIT_RULE_VERSION

    fun build(): HookSymbols = HookSymbols(
        SymbolContracts.all.flatMap { it.fields }.associateWith { if (values.containsKey(it)) values[it] else it.default },
        buildScanMeta(),
    )

    private fun buildScanMeta(): ScanMeta {
        return ScanMeta(
            availability = ScanAvailabilityMeta(
                scanSupportState,
                scanTargetVersionCode,
                scanTargetVersionName,
                scanTargetVersionType,
            ),
            scanErrors = scanErrors,
            source = source,
            createdAt = createdAt,
            cacheSchemaVersion = cacheSchemaVersion,
            dexKitRuleVersion = dexKitRuleVersion,
        )
    }
}

internal inline fun buildHookSymbols(block: HookSymbolsBuilder.() -> Unit): HookSymbols =
    HookSymbolsBuilder().apply(block).build()
