package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class CommentFilterTargets(
    val parsers: List<Pair<Method, Boolean>>,
    val protocol: CommentProtocol,
)

/** Stable Wire protocol access, fully restored before installing callbacks. */
class CommentProtocol(cl: ClassLoader) {
    val user = CommentProtoType("tbclient.User", cl)
    val userId = user.member("id", java.lang.Long::class.java)
    val userLevel = user.member("level_id", Integer::class.java)
    val child = CommentProtoType("tbclient.SubPostList", cl)
    val childAuthor = child.member("author", user.type)
    val childAuthorId = child.member("author_id", java.lang.Long::class.java)
    val children = CommentProtoType("tbclient.SubPost", cl)
    val childList = children.member("sub_post_list", List::class.java)
    val post = CommentProtoType("tbclient.Post", cl)
    val postId = post.member("id", java.lang.Long::class.java)
    val postFloor = post.member("floor", Integer::class.java)
    val postAuthor = post.member("author", user.type)
    val postAuthorId = post.member("author_id", java.lang.Long::class.java)
    val postChildCount = post.member("sub_post_number", Integer::class.java)
    val postChildren = post.member("sub_post_list", children.type)
    val hot = CommentProtoType("tbclient.PbHotPost", cl)
    val hotLists = listOf(hot.member("hot_post_list", List::class.java), hot.member("post_list", List::class.java))
    val top = CommentProtoType("tbclient.PbTopAgreePost", cl)
    val topList = top.member("post_list", List::class.java)
    val page = CommentProtoType("tbclient.PbPage.DataRes", cl)
    val pagePosts = page.member("post_list", List::class.java)
    val pageUsers = page.member("user_list", List::class.java)
    val pageFirstPosts = listOf(page.member("first_floor", post.type), page.member("first_floor_post", post.type))
    val pageAnswer = page.member("top_answer", post.type)
    val pageHot = page.member("hot_post_list", hot.type)
    val pageTop = page.member("top_agree_post_list", top.type)
    val floor = CommentProtoType("tbclient.PbFloor.DataRes", cl)
    val floorPost = floor.member("post", post.type)
    val floorChildren = floor.member("subpost_list", List::class.java)
}

class CommentProtoField internal constructor(private val source: Field, internal val builder: Field) {
    fun get(value: Any): Any? = source.get(value)
    fun number(value: Any): Long? = (get(value) as? Number)?.toLong()
    fun list(value: Any): List<*>? = get(value) as? List<*>
}

class CommentProtoType internal constructor(name: String, cl: ClassLoader) {
    val type: Class<*> = Class.forName(name, false, cl)
    private val builderType = Class.forName("$name\$Builder", false, cl)
    private val constructor: Constructor<*> = builderType.getDeclaredConstructor(type).apply { isAccessible = true }
    private val build: Method = builderType.declaredMethods.single {
        it.name == "build" && !it.isBridge && !Modifier.isStatic(it.modifiers) &&
            it.returnType == type && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
    }.apply { isAccessible = true }

    internal fun member(name: String, expected: Class<*>): CommentProtoField {
        fun field(owner: Class<*>): Field = owner.getDeclaredField(name).apply {
            check(!Modifier.isStatic(modifiers) && type == expected) { "Invalid comment protocol field: $this" }
            isAccessible = true
        }
        val source = field(type)
        val destination = field(builderType)
        check(!Modifier.isFinal(destination.modifiers)) { "Immutable comment builder field: $destination" }
        return CommentProtoField(source, destination)
    }

    /** build(false) preserves nullable values; the copy constructor also preserves unknown Wire fields. */
    fun copy(value: Any, changes: Map<CommentProtoField, Any?>): Any {
        if (changes.isEmpty()) return value
        val builder = constructor.newInstance(value)
        changes.forEach { (field, replacement) -> field.builder.set(builder, replacement) }
        return checkNotNull(build.invoke(builder, false))
    }
}
