package com.forbidad4tieba.hook.symbol

import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject

/** Literal input keys keep capability expectations independent of production declarations. */
internal fun textSymbols(
    keys: List<String>,
    missing: Set<String> = emptySet(),
    extra: JSONObject.() -> Unit = {},
): HookSymbols {
    val json = JSONObject(buildHookSymbols {}.toJson())
    keys.filterNot { it in missing }.forEach { json.put(it, "fixture.$it") }
    json.extra()
    return requireNotNull(HookSymbols.fromJson(json.toString()))
}
