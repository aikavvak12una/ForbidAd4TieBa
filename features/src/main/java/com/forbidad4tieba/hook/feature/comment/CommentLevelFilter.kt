package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.config.CommentLevelFilterSettings
import com.forbidad4tieba.hook.symbol.model.CommentProtoField
import com.forbidad4tieba.hook.symbol.model.CommentProtocol

/** One response-local pass. Original counts/cursors survive; only changed branches are copied. */
internal class CommentLevelFilter(private val p: CommentProtocol) {
    fun filter(response: Any, nestedPage: Boolean, settings: CommentLevelFilterSettings): Any {
        if (!settings.enabled) return response
        return if (nestedPage) floor(response, settings) else page(response, settings)
    }

    private fun floor(response: Any, settings: CommentLevelFilterSettings): Any {
        val changes = linkedMapOf<CommentProtoField, Any?>()
        val children = p.floorChildren.list(response)
        val filtered = filterList(children) { child ->
            if (settings.hides(level(child, true, emptyMap()), true, false)) null else child
        }
        if (filtered !== children) changes[p.floorChildren] = filtered
        // Context parent always stays, but its inline preview must obey the same child policy.
        p.floorPost.get(response)?.let { parent ->
            val updated = filterChildren(parent, emptyMap(), settings)
            if (updated !== parent) changes[p.floorPost] = updated
        }
        return p.floor.copy(response, changes)
    }

    private fun page(response: Any, settings: CommentLevelFilterSettings): Any {
        val users = HashMap<Long, Any>()
        p.pageUsers.list(response)?.forEach { user ->
            if (user != null && p.user.type.isInstance(user)) {
                p.userId.number(user)?.takeIf { it > 0 }?.let { users[it] = user }
            }
        }
        val bodyIds = p.pageFirstPosts.mapNotNull { field ->
            field.get(response)?.let { p.postId.number(it) }?.takeIf { it > 0 }
        }.toSet()
        fun post(value: Any): Any? {
            if (!p.post.type.isInstance(value)) return value
            val body = p.postFloor.number(value) == 1L || p.postId.number(value) in bodyIds
            val children = p.postChildren.get(value)
            val hasReplies = (p.postChildCount.number(value) ?: 0) > 0 ||
                (children != null && !p.childList.list(children).isNullOrEmpty())
            if (!body && settings.hides(level(value, false, users), false, hasReplies)) return null
            return filterChildren(value, users, settings)
        }
        val changes = linkedMapOf<CommentProtoField, Any?>()
        val posts = p.pagePosts.list(response)
        val filtered = filterList(posts, ::post)
        if (filtered !== posts) changes[p.pagePosts] = filtered
        p.pageFirstPosts.forEach { field ->
            field.get(response)?.let { value ->
                val updated = filterChildren(value, users, settings)
                if (updated !== value) changes[field] = updated
            }
        }
        p.pageAnswer.get(response)?.let { value ->
            val updated = post(value)
            if (updated !== value) changes[p.pageAnswer] = updated
        }
        p.pageHot.get(response)?.let { value ->
            val updates = linkedMapOf<CommentProtoField, Any?>()
            p.hotLists.forEach { field ->
                val old = field.list(value)
                val updated = filterList(old, ::post)
                if (updated !== old) updates[field] = updated
            }
            if (updates.isNotEmpty()) changes[p.pageHot] = p.hot.copy(value, updates)
        }
        p.pageTop.get(response)?.let { value ->
            val old = p.topList.list(value)
            val updated = filterList(old, ::post)
            if (updated !== old) changes[p.pageTop] = p.top.copy(value, mapOf(p.topList to updated))
        }
        return p.page.copy(response, changes)
    }

    private fun filterChildren(post: Any, users: Map<Long, Any>, settings: CommentLevelFilterSettings): Any {
        val group = p.postChildren.get(post) ?: return post
        val original = p.childList.list(group)
        val updated = filterList(original) { child ->
            if (settings.hides(level(child, true, users), true, false)) null else child
        }
        if (updated === original) return post
        return p.post.copy(post, mapOf(p.postChildren to p.children.copy(group, mapOf(p.childList to updated))))
    }

    private fun level(value: Any, nested: Boolean, users: Map<Long, Any>): Int? {
        val type = if (nested) p.child else p.post
        if (!type.type.isInstance(value)) return null
        val authorField = if (nested) p.childAuthor else p.postAuthor
        val idField = if (nested) p.childAuthorId else p.postAuthorId
        val user = users[idField.number(value)] ?: authorField.get(value) ?: return null
        // Wire may fill an absent level_id with zero; both represent an ungraded author for filtering.
        return p.userLevel.number(user)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    private inline fun filterList(original: List<*>?, transform: (Any) -> Any?): List<*>? {
        if (original.isNullOrEmpty()) return original
        var output: ArrayList<Any?>? = null
        original.forEachIndexed { index, value ->
            val replacement = if (value == null) null else transform(value)
            if (replacement !== value && output == null) {
                output = ArrayList(original.size)
                for (i in 0 until index) output.add(original[i])
            }
            if (output != null && (replacement != null || value == null)) output.add(replacement)
        }
        return output ?: original
    }
}
