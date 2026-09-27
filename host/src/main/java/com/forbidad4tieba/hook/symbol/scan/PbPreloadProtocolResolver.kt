package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.PbPreloadCardTargets
import com.forbidad4tieba.hook.symbol.model.PbPreloadProtoBuilder
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Stable protobuf fields are checked once, including copy compatibility and builder result types. */
internal object PbPreloadProtocolResolver {
    fun resolve(cl: ClassLoader): PbPreloadCardTargets {
        fun type(name: String) = Class.forName(name, false, cl)
        val card = type(StableTiebaHookPoints.THREAD_DATA_CLASS)
        val thread = type("tbclient.ThreadInfo")
        val user = type("tbclient.User")
        val post = builder(type("tbclient.Post"), cl)
        val page = builder(type("tbclient.Page"), cl)
        val forum = builder(type("tbclient.SimpleForum"), cl)
        val response = builder(type("tbclient.PbPage.DataRes"), cl)
        val pb = post.constructor.declaringClass
        val rb = response.constructor.declaringClass
        return PbPreloadCardTargets(
            getTid = method(card, "getTid", String::class.java),
            getThread = method(card, "getRawThreadInfo", thread),
            isNormal = method(card, "isNormalThreadType", Boolean::class.javaPrimitiveType!!),
            isPreloadType = method(type("com.baidu.tbadk.core.util.ThreadCardUtils"), "isPreloadType",
                Boolean::class.javaPrimitiveType!!, true, card),
            getForumId = method(card, "getFid", Long::class.javaPrimitiveType!!),
            getForumName = method(card, "getForum_name", String::class.java),
            threadId = field(thread, "id", Long::class.javaObjectType),
            firstPostId = field(thread, "first_post_id", Long::class.javaObjectType),
            content = field(thread, "first_post_content", List::class.java),
            contentType = type("tbclient.PbContent"),
            author = field(thread, "author", user),
            forum = field(thread, "forum_info", forum.build.returnType),
            postBuilder = post,
            postCopies = listOf("first_post_id" to "id", "id" to "tid", "author" to "author",
                "author_id" to "author_id", "create_time" to "time").map { (from, to) ->
                val source = thread.getField(from)
                field(thread, from, source.type) to field(pb, to, source.type, writable = true)
            },
            postFloor = field(pb, "floor", Int::class.javaObjectType, true),
            postContent = field(pb, "content", List::class.java, true),
            pageBuilder = page,
            pageNumber = field(page.constructor.declaringClass, "current_page", Int::class.javaObjectType, true),
            forumBuilder = forum,
            forumId = field(forum.constructor.declaringClass, "id", Long::class.javaObjectType, true),
            forumName = field(forum.constructor.declaringClass, "name", String::class.java, true),
            responseBuilder = response,
            responseThread = field(rb, "thread", thread, true),
            responseForum = field(rb, "forum", forum.build.returnType, true),
            responsePage = field(rb, "page", page.build.returnType, true),
            responseFirstFloor = field(rb, "first_floor", post.build.returnType, true),
            responseFirstFloorPost = field(rb, "first_floor_post", post.build.returnType, true),
            responsePosts = field(rb, "post_list", List::class.java, true),
            responseUsers = field(rb, "user_list", List::class.java, true),
        )
    }

    private fun builder(result: Class<*>, cl: ClassLoader): PbPreloadProtoBuilder {
        val owner = Class.forName(result.name + "\$Builder", false, cl)
        val constructor = owner.getConstructor()
        return PbPreloadProtoBuilder(constructor, method(owner, "build", result, false, Boolean::class.javaPrimitiveType!!))
    }

    private fun method(owner: Class<*>, name: String, result: Class<*>, static: Boolean = false, vararg params: Class<*>): Method =
        owner.getMethod(name, *params).apply {
            check(Modifier.isStatic(modifiers) == static && returnType == result) { "invalid preload protocol method: $this" }
            isAccessible = true
        }

    private fun field(owner: Class<*>, name: String, type: Class<*>, writable: Boolean = false): Field =
        owner.getField(name).apply {
            check(!Modifier.isStatic(modifiers) && this.type == type && (!writable || !Modifier.isFinal(modifiers))) {
                "invalid preload protocol field: $this"
            }
            isAccessible = true
        }
}
