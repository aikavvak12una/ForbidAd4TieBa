package com.forbidad4tieba.hook.feature.ui

import android.view.View
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.WeakHashMap

/** A view role keeps its listener, queued refreshes and tracking in one entry. */
internal class HomeNativeGlassViewSessions(
    private val isEnabled: () -> Boolean,
    private val apply: (HomeNativeGlassViewRole, View) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val views = WeakHashMap<View, EnumMap<HomeNativeGlassViewRole, Entry>>()

    fun request(role: HomeNativeGlassViewRole, view: View) {
        if (!isEnabled() && !role.restoresWhenDisabled) return
        if (role.timing == HomeNativeGlassRefreshTiming.IMMEDIATE_THEN_POST) observe(role, view)
        entry(view, role).refresh.request()
    }

    fun observe(role: HomeNativeGlassViewRole, view: View) {
        if ((!isEnabled() && !role.restoresWhenDisabled) || role.attachment == HomeNativeGlassAttachRefresh.NONE) return
        val entry = entry(view, role)
        val listener = synchronized(entry) {
            if (entry.listener != null) return
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = entry.refresh.onAttached()
                override fun onViewDetachedFromWindow(v: View) = Unit
            }.also { entry.listener = it }
        }
        runCatching {
            view.addOnAttachStateChangeListener(listener)
            entry.refresh.onListenerInstalled(view.isAttachedToWindow)
        }.onFailure { error ->
            synchronized(views) {
                if (views[view]?.get(role) === entry) {
                    views[view]?.remove(role)
                    if (entry.tracked) entry(view, role).tracked = true
                }
            }
            runCatching { entry.refresh.close() }.onFailure(onFailure)
            runCatching { view.removeOnAttachStateChangeListener(listener) }.onFailure(onFailure)
            onFailure(error)
        }
    }

    fun track(view: View, role: HomeNativeGlassViewRole) {
        synchronized(views) { entry(view, role).tracked = true }
    }

    fun isTracked(view: View, role: HomeNativeGlassViewRole): Boolean = synchronized(views) {
        views[view]?.get(role)?.tracked == true
    }

    fun trackedViews(role: HomeNativeGlassViewRole): List<View> = synchronized(views) {
        views.entries.filter { it.value[role]?.tracked == true }.map { it.key }
    }

    private fun entry(view: View, role: HomeNativeGlassViewRole): Entry = synchronized(views) {
        views.getOrPut(view) { EnumMap(HomeNativeGlassViewRole::class.java) }.getOrPut(role) {
            val reference = WeakReference(view)
            Entry(HomeNativeGlassRefreshSession(
                role = role,
                isEnabled = isEnabled,
                isAttached = { reference.get()?.isAttachedToWindow == true },
                post = { task, delay ->
                    reference.get()?.let { target ->
                        if (delay <= 0L) target.post(task) else target.postDelayed(task, delay)
                    }
                },
                cancel = { task -> reference.get()?.removeCallbacks(task); Unit },
                apply = { reference.get()?.let { apply(role, it) } },
            ))
        }
    }

    private class Entry(val refresh: HomeNativeGlassRefreshSession) {
        var listener: View.OnAttachStateChangeListener? = null
        var tracked = false
    }
}
