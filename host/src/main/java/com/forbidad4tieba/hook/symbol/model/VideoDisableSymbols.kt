package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

enum class VideoDisableResult { BOOLEAN_FALSE, VOID }

/** Background producers only. Playback, real kernels and their completion paths remain native. */
enum class VideoDisableTarget(
    val className: String,
    val methodName: String,
    val blockedResult: VideoDisableResult = VideoDisableResult.VOID,
    val parameterTypeNames: List<String> = emptyList(),
    val isStatic: Boolean = false,
    val isPrivate: Boolean = false,
    val requiresExternalConsumer: Boolean = false,
) {
    FEED_PRELOAD_OPEN(
        "com.baidu.tbadk.core.util.videoPreload.PreLoadVideoSwitchManager", "isOpen", VideoDisableResult.BOOLEAN_FALSE,
    ),
    PREBOOT_DELIVER(
        "com.baidu.searchbox.player.preboot.PrebootEngine", "deliverTask",
        parameterTypeNames = listOf("com.baidu.searchbox.player.preboot.env.PrebootInfo"), isPrivate = true,
    ),
    PREBOOT_WORK("com.baidu.searchbox.player.preboot.task.AbsPrebootTask", "work\$preboot_release");


    internal val returnTypeName: String get() = when (blockedResult) {
        VideoDisableResult.BOOLEAN_FALSE -> "boolean"
        VideoDisableResult.VOID -> "void"
    }

    internal fun matchesSignature(modifiers: Int, returnType: String, parameters: List<String>): Boolean =
        (if (isPrivate) Modifier.isPrivate(modifiers) else Modifier.isPublic(modifiers)) &&
            !Modifier.isAbstract(modifiers) && Modifier.isStatic(modifiers) == isStatic &&
            returnType == returnTypeName && parameters == parameterTypeNames

    internal fun matches(method: Method): Boolean =
        method.declaringClass.name == className && method.name == methodName &&
            matchesSignature(method.modifiers, method.returnType.name, method.parameterTypes.map { it.name })

    internal fun hasConsumer(totalCallers: Int, externalCallers: Int): Boolean =
        if (requiresExternalConsumer) externalCallers > 0 else totalCallers > 0
}

/** Present only after every required member, including the semantic PCDN entry, is restored. */
data class VideoDisableSymbols(
    val targets: Map<VideoDisableTarget, List<Method>>,
    val pcdnStart: Method,
)

/** Hierarchy evidence is retained separately so removing a cached override cannot silently pass. */
data class VideoDisableFamily(val hierarchy: List<String>, val methods: List<String>) {
    internal fun hasValidMetadata(target: VideoDisableTarget): Boolean =
        hierarchy.isNotEmpty() && hierarchy.size == hierarchy.distinct().size && target.className in hierarchy &&
            methods.isNotEmpty() && methods.size == methods.distinct().size &&
            "${target.className}#${target.methodName}" in methods && methods.all { descriptor ->
                val parts = descriptor.split('#')
                parts.size == 2 && parts[0] in hierarchy && parts[1] == target.methodName
            }
}

data class VideoDisableCache(val families: Map<String, VideoDisableFamily> = emptyMap()) {
    internal fun hasValidMetadata(): Boolean = families.all { (name, family) ->
        VideoDisableTarget.entries.singleOrNull { it.name == name }?.let(family::hasValidMetadata) == true
    }

    internal fun isComplete(): Boolean = hasValidMetadata() && families.keys == VideoDisableTarget.entries.map { it.name }.toSet()

    internal fun toJson(): JSONObject = JSONObject().apply {
        families.toSortedMap().forEach { (name, family) ->
            put(name, JSONObject().put("hierarchy", JSONArray(family.hierarchy)).put("methods", JSONArray(family.methods)))
        }
    }

    internal companion object {
        fun fromJson(json: JSONObject?): VideoDisableCache {
            if (json == null) return VideoDisableCache()
            fun strings(array: JSONArray?): List<String> = array?.let { value ->
                List(value.length()) { value.optString(it) }
            }.orEmpty()
            return VideoDisableCache(json.keys().asSequence().associateWith { key ->
                val family = json.optJSONObject(key)
                VideoDisableFamily(strings(family?.optJSONArray("hierarchy")), strings(family?.optJSONArray("methods")))
            })
        }
    }
}

internal data class VideoDisableScan(
    val methods: VideoDisableCache,
    val pcdnStart: String?,
)
