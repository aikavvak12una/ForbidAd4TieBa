package com.forbidad4tieba.hook.symbol.status

internal object HookPointState {
    const val FOUND = "FOUND"
    const val MISSING = "MISSING"
    const val PARTIAL = "PARTIAL"
    const val OPTIONAL = "OPTIONAL"
    const val ERROR = "ERROR"
}

internal data class HookPointStatus(
    val name: String,
    val state: String,
    val missing: List<String> = emptyList(),
    val target: String = "-",
) {
    fun formatLine(): String {
        val missingText = missing.joinToString(",").ifBlank { "-" }
        return "HookPoint[$name] state=$state missing=$missingText target=$target"
    }

    fun isUnavailable(): Boolean {
        return state == HookPointState.MISSING ||
            state == HookPointState.PARTIAL ||
            state == HookPointState.ERROR
    }
}

internal fun buildHookPointStatus(
    name: String,
    target: String,
    checks: List<Pair<String, Boolean>>,
    missingState: String = HookPointState.MISSING,
): HookPointStatus {
    val missing = checks.asSequence()
        .filter { !it.second }
        .map { it.first }
        .toList()
    return HookPointStatus(
        name = name,
        state = if (missing.isEmpty()) HookPointState.FOUND else missingState,
        missing = missing,
        target = target,
    )
}
