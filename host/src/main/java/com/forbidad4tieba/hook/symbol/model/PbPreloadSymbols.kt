package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class PbPreloadSymbols(
    val providerMethodSpec: String? = null,
    val cardGetterMethodSpec: String? = null,
    val pageStateMutableField: String? = null,
    val pageStateFlowField: String? = null,
)

/** All host reflection is resolved and checked before installing the preload hooks. */
data class PbPreloadTargets(
    val preloadSwitch: Method,
    val provider: Method,
    val cardGetter: Method,
    val card: PbPreloadCardTargets,
    val pageState: PbPreloadPageStateTargets,
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
