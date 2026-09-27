package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.buildHookPointStatus

internal data class PointOwner(val pattern: String, val prefix: Boolean, val features: List<String>) {
    fun matches(name: String): Boolean = if (prefix) name.startsWith(pattern) else name == pattern
}

internal fun SymbolField<*>.check(symbols: HookSymbols): Pair<String, Boolean> = cacheKey to when (val value = symbols[this]) {
    is String -> value.isNotBlank()
    is Int -> value != 0
    is List<*> -> value.any { it is String && it.isNotBlank() }
    else -> value != null
}

internal class SymbolPointCollector {
    private val result = ArrayList<HookPointStatus>()

    fun add(name: String, target: String, checks: List<Pair<String, Boolean>>) {
        result += buildHookPointStatus(name, target, checks)
    }
    fun addOptional(name: String, target: String, checks: List<Pair<String, Boolean>>) {
        result += buildHookPointStatus(name, target, checks, HookPointState.OPTIONAL)
    }
    fun addStatus(status: HookPointStatus) { result += status }
    fun addAll(statuses: List<HookPointStatus>) { result += statuses }
    fun build(): List<HookPointStatus> = result.toList()

    companion object {
        fun has(value: String?): Boolean = !value.isNullOrBlank()
        fun has(value: Int?): Boolean = value != null && value != 0
        fun hasList(values: List<String>?): Boolean = values.orEmpty().any { it.isNotBlank() }
        fun listTarget(values: List<String>?): String = values.orEmpty().filter { it.isNotBlank() }.joinToString(",").ifBlank { "-" }
    }
}
