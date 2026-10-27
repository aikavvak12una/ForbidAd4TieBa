package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Method
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

private const val BD_PLAYER = "com.baidu.searchbox.player.BDVideoPlayer"
private const val VIDEO_SERIES = "com.baidu.searchbox.player.model.BasicVideoSeries"
private const val TB_VIDEO_VIEW = "com.baidu.tieba.play.TbVideoView"
private const val DU_MEDIA_VIDEO_VIEW = "com.baidu.cyberplayer.sdk.DuMediaVideoView"

enum class VideoDisableResult { BOOLEAN_FALSE, VOID, EMPTY_KERNEL }

/** One complete group: producers are blocked before the lower-level fallback targets. */
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
    CYBER_INIT("com.baidu.tbadk.core.TbadkCoreApplication", "initCyberPlayer"),
    PREBOOT_DELIVER(
        "com.baidu.searchbox.player.preboot.PrebootEngine", "deliverTask",
        parameterTypeNames = listOf("com.baidu.searchbox.player.preboot.env.PrebootInfo"), isPrivate = true,
    ),
    PREBOOT_WORK("com.baidu.searchbox.player.preboot.task.AbsPrebootTask", "work\$preboot_release"),
    DUMEDIA_CREATE_KERNEL(
        "com.baidu.searchbox.player.kernel.DumediaKernelFactory", "create", VideoDisableResult.EMPTY_KERNEL,
        parameterTypeNames = listOf("java.lang.String"),
    ),
    BD_PREPARE(BD_PLAYER, "prepare"),
    BD_START(BD_PLAYER, "start"),
    BD_RESUME(BD_PLAYER, "resume"),
    BD_DO_PLAY(BD_PLAYER, "doPlay"),
    BD_PLAY_URL(BD_PLAYER, "play", parameterTypeNames = listOf("java.lang.String")),
    BD_SET_URL(BD_PLAYER, "setVideoUrl", parameterTypeNames = listOf("java.lang.String")),
    BD_SET_URL_PREPARE(BD_PLAYER, "setVideoUrl", parameterTypeNames = listOf("java.lang.String", "boolean")),
    BD_SET_SERIES(BD_PLAYER, "setVideoSeries", parameterTypeNames = listOf(VIDEO_SERIES)),
    BD_SET_SERIES_PREPARE(BD_PLAYER, "setVideoSeries", parameterTypeNames = listOf(VIDEO_SERIES, "boolean")),
    TB_START(TB_VIDEO_VIEW, "start", requiresExternalConsumer = true),
    TB_SET_PATH(TB_VIDEO_VIEW, "setVideoPath", parameterTypeNames = listOf("java.lang.String"), requiresExternalConsumer = true),
    CYBER_START(DU_MEDIA_VIDEO_VIEW, "start", requiresExternalConsumer = true),
    CYBER_SET_PATH(DU_MEDIA_VIDEO_VIEW, "setVideoPath", parameterTypeNames = listOf("java.lang.String"), requiresExternalConsumer = true),
    DUMEDIA_OPEN(DU_MEDIA_VIDEO_VIEW, "openVideo", isPrivate = true),
    BVIDEO_OPEN("com.baidu.cyberplayer.sdk.BVideoView", "openVideo", isPrivate = true),
    MEDIA_PREFETCH(
        "com.baidu.cyberplayer.sdk.DuMediaPrefetch", "prefetch",
        parameterTypeNames = listOf("java.lang.String", "int", "com.baidu.cyberplayer.sdk.DuMediaPrefetch\$PrefetchConfig"), isStatic = true,
    );

    internal val returnTypeName: String get() = when (blockedResult) {
        VideoDisableResult.BOOLEAN_FALSE -> "boolean"
        VideoDisableResult.VOID -> "void"
        VideoDisableResult.EMPTY_KERNEL -> "com.baidu.searchbox.player.kernel.AbsVideoKernel"
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
    val playDispatch: Method,
    val adTargets: Map<PerformanceRuntimeTarget, Method>,
    val emptyKernelConstructor: Constructor<*>,
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
    val playDispatch: String?,
    val emptyKernelConstructor: String?,
)
