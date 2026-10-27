package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

private const val YY_LOAD_CALLBACK = "com.baidu.searchbox.live.host2live.video.IVideoInsertLiveService\$LoadResultCallback"

enum class YyBackgroundTarget(val methodName: String, val result: String, val params: List<String>, val anchor: String? = null) {
    CONSTRUCTOR("<init>", "void", emptyList()),
    PRELOAD("preHandleLiveNpsWithoutLoad", "void", emptyList(), "entrance 预加载 already handle, return"),
    SUBPLUGINS("preHandleYYLiveSubPluginWithoutLoad", "void", emptyList(), "preHandleYYLivePluginWithoutLoad 预加载 already handle, return"),
    INSERT_SUPPORT("isSupportInsertVideo", "boolean", emptyList(), "no entrance for insertVideo"),
    PRELOAD_CALLBACK("preHandleYYLivePluginWithoutLoadV2", "void", listOf(YY_LOAD_CALLBACK), "preHandleYYLivePluginWithoutLoadV2"),
    CALLBACK_RESULT("onResult", "void", listOf("boolean")),
    CALLBACK_EVENT("onEvent", "void", listOf("boolean", "int"));

    val isCallback get() = this == CALLBACK_RESULT || this == CALLBACK_EVENT
    val owner get() = if (isCallback) CALLBACK else MANAGER

    companion object {
        const val MANAGER = "com.baidu.searchbox.live.nps.LiveYYPluginManager"
        const val CALLBACK = YY_LOAD_CALLBACK
    }
}

data class YyBackgroundSymbols(
    val members: Map<YyBackgroundTarget, Executable>,
    val readiness: Method,
) {
    val constructor get() = members.getValue(YyBackgroundTarget.CONSTRUCTOR) as Constructor<*>
    fun method(target: YyBackgroundTarget) = members.getValue(target) as Method
}
