package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

internal class HomeNativeGlassPageSessions(
    private val onContentHostFound: (View) -> Unit,
) {
    private val pbActivityByContext = Collections.synchronizedMap(WeakHashMap<Context, WeakReference<Activity>>())
    private val pbActivityContentHosts = Collections.synchronizedMap(WeakHashMap<Activity, WeakReference<View>>())
    private val pbActivityTypes = Collections.synchronizedMap(WeakHashMap<Activity, PbActivityType>())

    fun findPbCommentActivityContentHost(source: View): View? {
        val activity = findCachedActivityFromContext(source.context) ?: return null
        if (!isPbActivity(activity)) return null
        return findPbActivityContentHost(activity)
    }

    fun findPbActivityContentHost(activity: Activity): View? {
        synchronized(pbActivityContentHosts) {
            val cached = pbActivityContentHosts[activity]?.get()
            if (cached != null) return cached
            pbActivityContentHosts.remove(activity)
        }
        val host = runCatching { activity.findViewById<View>(android.R.id.content) }.getOrNull()
        if (host != null) {
            onContentHostFound(host)
            pbActivityContentHosts[activity] = WeakReference(host)
        }
        return host
    }

    fun findCachedActivityFromContext(context: Context?): Activity? {
        context ?: return null
        synchronized(pbActivityByContext) {
            val cached = pbActivityByContext[context]?.get()
            if (cached != null) return cached
            pbActivityByContext.remove(context)
        }
        val activity = ReflectionUtils.findActivityFromContext(context) ?: return null
        pbActivityByContext[context] = WeakReference(activity)
        return activity
    }

    fun isPbActivity(activity: Activity): Boolean {
        return pbActivityTypeFor(activity).isPb
    }

    fun isSubPbReplyHostActivity(activity: Activity): Boolean {
        return pbActivityTypeFor(activity).isSubPbReplyHost
    }

    private fun pbActivityTypeFor(activity: Activity): PbActivityType {
        synchronized(pbActivityTypes) {
            pbActivityTypes[activity]?.let { return it }
        }
        val resolved = resolvePbActivityType(activity)
        pbActivityTypes[activity] = resolved
        return resolved
    }

    private fun resolvePbActivityType(activity: Activity): PbActivityType {
        if (activity.javaClass.name == StableTiebaHookPoints.PB_COMMENT_FLOAT_ACTIVITY_CLASS) {
            return PbActivityType(isPb = false, isSubPbReplyHost = false)
        }
        var isPb = false
        var isSubPbReplyHost = false
        var current: Class<*>? = activity.javaClass
        while (current != null && current != Any::class.java) {
            val name = current.name
            if (
                name == StableTiebaHookPoints.PB_ACTIVITY_CLASS ||
                name == StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS
            ) {
                isPb = true
            }
            if (
                name == StableTiebaHookPoints.NEW_SUB_PB_ACTIVITY_CLASS ||
                name == StableTiebaHookPoints.FOLD_COMMENT_ACTIVITY_CLASS
            ) {
                isSubPbReplyHost = true
            }
            if (isPb && isSubPbReplyHost) break
            current = current.superclass
        }
        return PbActivityType(isPb = isPb, isSubPbReplyHost = isSubPbReplyHost)
    }

    private data class PbActivityType(
        val isPb: Boolean,
        val isSubPbReplyHost: Boolean,
    )
}
