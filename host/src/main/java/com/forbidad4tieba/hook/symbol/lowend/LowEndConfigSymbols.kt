package com.forbidad4tieba.hook.symbol.lowend

import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.scan.scanSubStep
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Shared signature and semantic contract for all low-end configuration targets. */
enum class LowEndConfigTarget(
    val owner: String,
    val returnType: String,
    val parameters: List<String>,
    val callOwner: String,
    val callName: String,
) {
    THRESHOLD(
        "com.baidu.adp.baes.sharedperf.MultiSharedPrefHelper", "float", listOf("java.lang.String", "float"),
        "android.content.SharedPreferences", "getFloat",
    ),
    STRING_CONFIG(
        "com.baidu.adp.baes.sharedperf.MultiSharedPrefHelper", "java.lang.String", listOf("java.lang.String", "java.lang.String"),
        "android.content.SharedPreferences", "getString",
    ),
    DEVICE_SCORE(
        "com.baidu.searchbox.launch.ScheduleStrategy", "double", emptyList(),
        "com.baidu.searchbox.devicescore.IDeviceScore", "getFinalScore",
    );

    fun matches(method: Method): Boolean =
        Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers) &&
            method.returnType.name == returnType && method.parameterTypes.map { it.name } == parameters
}

/** Owns cached names, recovery, validation and the required/optional capability decision. */
data class LowEndConfigSymbols(val methods: Map<LowEndConfigTarget, String> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().apply {
        methods.forEach { (target, method) -> put(target.name, method) }
    }

    fun restore(cl: ClassLoader): Map<LowEndConfigTarget, Method> = methods.mapNotNull { (target, name) ->
        scanSubStep("HostPerformanceConfigHook.${target.name}.Restore", null, null) {
            val owner = Class.forName(target.owner, false, cl)
            val params = target.parameters.map {
                when (it) {
                    "float" -> Float::class.javaPrimitiveType!!
                    else -> Class.forName(it, false, cl)
                }
            }.toTypedArray()
            val method = owner.getDeclaredMethod(name, *params)
            check(target.matches(method)) { "invalid ${target.name} signature: $method" }
            target to method.apply { isAccessible = true }
        }
    }.toMap()

    fun isCacheValid(cl: ClassLoader): Boolean = restore(cl).size == methods.size

    internal fun hookPoints(): List<HookPointStatus> = LowEndConfigTarget.entries.map { target ->
        val name = methods[target]
        HookPointStatus(
            name = "HostPerformanceConfigHook.${target.name}",
            state = if (name != null) HookPointState.FOUND else HookPointState.MISSING,
            missing = if (name == null) listOf(target.name) else emptyList(),
            target = "${target.owner}.${name ?: "?"}(${target.parameters.joinToString()})",
        )
    }

    fun featureStatus(): HookFeatureStatus {
        val missing = LowEndConfigTarget.entries.filter { it !in methods }.map { it.name }
        return when {
            missing.isEmpty() -> HookFeatureStatus(HookFeatureState.FULL)
            methods.isEmpty() -> HookFeatureStatus(HookFeatureState.DISABLED, missingCritical = missing)
            // These overrides previously installed independently; keep the surviving capabilities.
            else -> HookFeatureStatus(HookFeatureState.PARTIAL, missingOptional = missing)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): LowEndConfigSymbols = LowEndConfigSymbols(
            LowEndConfigTarget.entries.mapNotNull { target ->
                (json?.opt(target.name) as? String)?.takeIf { it.isNotBlank() }?.let { target to it }
            }.toMap(),
        )
    }
}
