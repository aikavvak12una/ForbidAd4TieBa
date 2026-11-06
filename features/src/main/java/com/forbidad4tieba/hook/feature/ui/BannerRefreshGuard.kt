package com.forbidad4tieba.hook.feature.ui

import java.util.Collections
import java.util.IdentityHashMap
import com.forbidad4tieba.hook.core.XposedCompat

internal class BannerRefreshGuard(
    private val onRefreshFailure: (Throwable) -> Unit = {
        XposedCompat.logW("[PbBottomEnterBarHook] refresh failed: ${it.message}")
    },
) {
    private val active = ThreadLocal.withInitial {
        Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    }

    fun <T> run(target: Any, original: () -> T, refresh: () -> Unit): T {
        val targets = checkNotNull(active.get())
        if (!targets.add(target)) return original()
        try {
            val result = original()
            try {
                refresh()
            } catch (failure: Throwable) {
                onRefreshFailure(failure)
            }
            return result
        } finally {
            targets.remove(target)
        }
    }
}
