package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import org.json.JSONArray
import org.json.JSONObject

/** A typed descriptor key. The owning contract supplies its only cache codec. */
class SymbolField<T> internal constructor(
    val cacheKey: String,
    internal val default: T,
    internal val decode: (JSONObject) -> T,
    internal val encode: (JSONObject, T) -> Unit,
) {
    internal fun write(json: JSONObject, symbols: HookSymbols) = encode(json, symbols[this])
}

/** Small host-side contract; no runtime discovery or hook installation belongs here. */
abstract class SymbolContract(val id: String) {
    private val declarations = ArrayList<SymbolField<*>>()
    internal val fields: List<SymbolField<*>> get() = declarations

    internal abstract fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder)
    internal abstract fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean
    internal abstract fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus>
    internal abstract fun points(symbols: HookSymbols): List<HookPointStatus>
    internal open val candidateClasses: List<String> = emptyList()
    internal open val pointOwners: List<PointOwner> = emptyList()
    internal open val aggregates: Map<String, List<String>> = emptyMap()

    private fun <T> field(
        name: String,
        default: T,
        decode: (JSONObject) -> T,
        encode: (JSONObject, T) -> Unit,
    ): SymbolField<T> = SymbolField(name, default, decode, encode).also(declarations::add)

    protected fun text(name: String): SymbolField<String?> = field(
        name, null,
        { json -> if (json.isNull(name)) null else json.optString(name).ifEmpty { null } },
        { json, value -> json.put(name, value); Unit },
    )

    protected fun number(name: String): SymbolField<Int?> = field(
        name, null,
        { json -> if (json.isNull(name)) null else json.optInt(name).takeIf { it != 0 } },
        { json, value -> json.put(name, value); Unit },
    )

    protected fun texts(name: String, preserveEmpty: Boolean = false): SymbolField<List<String>?> = field(
        name, null,
        { json ->
            json.optJSONArray(name)?.let { array ->
                if (preserveEmpty) {
                    List(array.length()) { array.getString(it) }
                } else {
                    List(array.length()) { array.optString(it).trim() }
                        .filter(String::isNotEmpty).takeIf(List<String>::isNotEmpty)
                }
            }
        },
        { json, value -> if (value != null) json.put(name, JSONArray(value)); Unit },
    )

    protected fun numbers(name: String): SymbolField<List<Int>> = field(
        name, emptyList(),
        { json -> json.optJSONArray(name)?.let { array ->
            List(array.length()) { array.optInt(it, 0) }.filter { it != 0 }
        }.orEmpty() },
        { json, value -> if (value.isNotEmpty()) json.put(name, JSONArray(value)); Unit },
    )

    protected fun <T> nested(
        name: String,
        empty: T,
        read: (JSONObject?) -> T,
        write: (T) -> JSONObject,
    ): SymbolField<T> = field(
        name, empty, { read(it.optJSONObject(name)) },
        { json, value -> json.put(name, write(value)); Unit },
    )
}
