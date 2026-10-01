package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.config.CommentLevelFilterSettings
import java.util.WeakHashMap

/** A visit owns its override; settings and other post visits remain unchanged. */
internal class CommentFilterVisits {
    private data class Visit(val tid: String, var enabled: Boolean? = null)
    private val visits = WeakHashMap<Any, Visit>()

    @Synchronized fun observe(owner: Any, tid: String) {
        if (visits[owner]?.tid != tid) visits[owner] = Visit(tid)
    }
    @Synchronized fun enabled(owner: Any, tid: String, global: Boolean): Boolean {
        observe(owner, tid)
        return visits[owner]?.enabled ?: global
    }
    @Synchronized fun toggle(owner: Any, tid: String, global: Boolean): Boolean {
        val next = !enabled(owner, tid, global)
        visits.getValue(owner).enabled = next
        return next
    }
    @Synchronized fun remove(owner: Any) { visits.remove(owner) }
    @Synchronized fun clear() { visits.clear() }
    @Synchronized fun forResponse(tid: String?, global: Boolean): Boolean {
        // A response with no unambiguous live owner cannot borrow another visit's state.
        return visits.values.filter { it.tid == tid }.singleOrNull()?.enabled ?: global
    }
}

internal object CommentFilterOverrides {
    val visits = CommentFilterVisits()
    @Volatile var responseThreadId: ((Any) -> String?)? = null
    fun settings(response: Any, global: CommentLevelFilterSettings, shortcutEnabled: Boolean): CommentLevelFilterSettings {
        if (!shortcutEnabled) { visits.clear(); return global }
        val resolve = responseThreadId ?: return global
        val tid = try { resolve(response) } catch (_: Exception) { return global }
        val enabled = visits.forResponse(tid, global.enabled)
        return if (enabled == global.enabled) global else global.copy(enabled = enabled)
    }
}
