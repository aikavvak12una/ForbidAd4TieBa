package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class PbPreloadSymbols(
    val providerMethodSpec: String? = null,
    val cardGetterMethodSpec: String? = null,
    val pageStateMutableField: String? = null,
    val pageStateFlowField: String? = null,
    val body: PbPreloadBodySymbols = PbPreloadBodySymbols(),
)

internal data class PbPreloadBodySymbols(
    val commit: String? = null,
    val hybrid: String? = null,
    val success: String? = null,
    val model: String? = null,
    val stage: String? = null,
    val response: String? = null,
    val thread: String? = null,
    val webView: String? = null,
) {
    fun descriptors(): List<String?> = listOf(commit, hybrid, success, model, stage, response, thread, webView)
}

/** All host reflection is resolved and checked before installing the preload hooks. */
data class PbPreloadTargets(
    val preloadSwitch: Method,
    val provider: Method,
    val cardGetter: Method,
    val card: PbPreloadCardTargets,
    val pageState: PbPreloadPageStateTargets,
    val body: PbPreloadBodyTargets,
)

data class PbPreloadBodyTargets(
    val commit: Method,
    val hybrid: Method,
    val success: Field,
    val model: Field,
    val stage: Field,
    val response: Field,
    val thread: Field,
    val webView: Field,
    val preloadStage: Any,
    val responseData: Field,
    val protocolThread: Field,
    val firstFloor: Field,
    val postId: Field,
    val postFloor: Field,
    val postContent: Field,
    val forumBot: Field,
    val shopGoods: Field,
)

data class PbPreloadPageStateTargets(
    val constructor: Constructor<*>,
    val mutableField: Field,
    val flowField: Field,
    val createFlow: Method,
    val suspendOverflow: Any,
)

data class PbPreloadProtoBuilder(val constructor: Constructor<*>, val build: Method)

data class PbPreloadCardTargets(
    val getTid: Method,
    val getThread: Method,
    val isNormal: Method,
    val isPreloadType: Method,
    val getForumId: Method,
    val getForumName: Method,
    val threadId: Field,
    val firstPostId: Field,
    val content: Field,
    val contentType: Class<*>,
    val author: Field,
    val forum: Field,
    val postBuilder: PbPreloadProtoBuilder,
    val postCopies: List<Pair<Field, Field>>,
    val postFloor: Field,
    val postContent: Field,
    val pageBuilder: PbPreloadProtoBuilder,
    val pageNumber: Field,
    val forumBuilder: PbPreloadProtoBuilder,
    val forumId: Field,
    val forumName: Field,
    val responseBuilder: PbPreloadProtoBuilder,
    val responseThread: Field,
    val responseForum: Field,
    val responsePage: Field,
    val responseFirstFloor: Field,
    val responseFirstFloorPost: Field,
    val responsePosts: Field,
    val responseUsers: Field,
)
