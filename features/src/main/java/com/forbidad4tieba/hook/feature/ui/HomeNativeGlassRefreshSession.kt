package com.forbidad4tieba.hook.feature.ui

internal val HOME_SEARCH_BOX_BOOTSTRAP_REFRESH_DELAYS_MS = longArrayOf(0L, 160L)
internal val CARD_COMPONENT_BOOTSTRAP_REFRESH_DELAYS_MS = longArrayOf(0L, 160L)

internal enum class HomeNativeGlassRefreshTiming {
    POST, IMMEDIATE_THEN_POST, SEARCH_BOOTSTRAP, COMPONENT_BOOTSTRAP,
}

internal enum class HomeNativeGlassAttachRefresh {
    NONE, ON_ATTACH, ALSO_WHEN_ALREADY_ATTACHED, SEARCH_BOX,
}

/** The timing and attachment policy of each existing glass view role. */
internal enum class HomeNativeGlassViewRole(
    val timing: HomeNativeGlassRefreshTiming,
    val attachment: HomeNativeGlassAttachRefresh = HomeNativeGlassAttachRefresh.NONE,
    val restoresWhenDisabled: Boolean = false,
) {
    TOP_CHROME(HomeNativeGlassRefreshTiming.IMMEDIATE_THEN_POST, HomeNativeGlassAttachRefresh.ON_ATTACH, true),
    BOTTOM_TAB(HomeNativeGlassRefreshTiming.IMMEDIATE_THEN_POST, HomeNativeGlassAttachRefresh.ON_ATTACH, true),
    SEARCH_BOX(HomeNativeGlassRefreshTiming.SEARCH_BOOTSTRAP, HomeNativeGlassAttachRefresh.SEARCH_BOX, true),
    CARD_COMPONENT(HomeNativeGlassRefreshTiming.COMPONENT_BOOTSTRAP, restoresWhenDisabled = true),
    PAGE(HomeNativeGlassRefreshTiming.POST),
    FEED_CARD(HomeNativeGlassRefreshTiming.POST),
    PB_SURFACE(HomeNativeGlassRefreshTiming.POST, HomeNativeGlassAttachRefresh.ALSO_WHEN_ALREADY_ATTACHED),
    PB_ITEM_FRAME(HomeNativeGlassRefreshTiming.POST, HomeNativeGlassAttachRefresh.ALSO_WHEN_ALREADY_ATTACHED),
    PB_SUB_LAYOUT(HomeNativeGlassRefreshTiming.POST),
    PB_REPLY_TITLE(HomeNativeGlassRefreshTiming.POST),
}

/**
 * One view role owns its queued work and one-time bootstrap. Detachment does not
 * reset bootstrap or discard constructor posts: Android delivers those on attach.
 * The Android binding supplies weak view access, so queued work retains no page.
 */
internal class HomeNativeGlassRefreshSession(
    private val role: HomeNativeGlassViewRole,
    private val isEnabled: () -> Boolean,
    private val isAttached: () -> Boolean,
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val apply: () -> Unit,
) {
    private val pending = LinkedHashSet<Refresh>()
    private var coalesced: Refresh? = null
    private var bootstrapped = false
    private var closed = false

    fun request() {
        if (!canApply()) return
        when (role.timing) {
            HomeNativeGlassRefreshTiming.POST -> {
                val refresh = reserveCoalesced(releaseAfterApply = false) ?: return
                submit(refresh, 0L)
            }
            HomeNativeGlassRefreshTiming.IMMEDIATE_THEN_POST -> {
                val refresh = reserveCoalesced(releaseAfterApply = true) ?: return
                apply()
                submit(refresh, 0L)
            }
            HomeNativeGlassRefreshTiming.SEARCH_BOOTSTRAP -> bootstrap(HOME_SEARCH_BOX_BOOTSTRAP_REFRESH_DELAYS_MS)
            HomeNativeGlassRefreshTiming.COMPONENT_BOOTSTRAP -> bootstrap(CARD_COMPONENT_BOOTSTRAP_REFRESH_DELAYS_MS)
        }
    }

    fun onAttached() {
        if (!canApply()) return
        when (role.attachment) {
            HomeNativeGlassAttachRefresh.NONE -> Unit
            HomeNativeGlassAttachRefresh.ON_ATTACH,
            HomeNativeGlassAttachRefresh.ALSO_WHEN_ALREADY_ATTACHED -> request()
            HomeNativeGlassAttachRefresh.SEARCH_BOX -> {
                request()
                postIndependent(0L)
            }
        }
    }

    fun onListenerInstalled(alreadyAttached: Boolean) {
        if (!alreadyAttached || !canApply()) return
        when (role.attachment) {
            HomeNativeGlassAttachRefresh.NONE, HomeNativeGlassAttachRefresh.ON_ATTACH -> Unit
            HomeNativeGlassAttachRefresh.ALSO_WHEN_ALREADY_ATTACHED -> request()
            HomeNativeGlassAttachRefresh.SEARCH_BOX -> postIndependent(0L)
        }
    }

    fun close() {
        val queued = synchronized(this) {
            closed = true
            coalesced = null
            pending.toList().also { pending.clear() }
        }
        queued.forEach(cancel)
    }

    private fun reserveCoalesced(releaseAfterApply: Boolean): Refresh? = synchronized(this) {
        if (closed || coalesced != null) return null
        Refresh(releaseAfterApply).also {
            coalesced = it
            pending.add(it)
        }
    }

    private fun bootstrap(delays: LongArray) {
        synchronized(this) {
            if (closed || bootstrapped) return
            bootstrapped = true
        }
        delays.forEach(::postIndependent)
    }

    private fun postIndependent(delay: Long) {
        val refresh = synchronized(this) {
            if (closed) return
            Refresh(releaseAfterApply = false).also { pending.add(it) }
        }
        submit(refresh, delay)
    }

    private fun submit(refresh: Refresh, delay: Long) {
        try {
            post(refresh, delay)
        } catch (error: Throwable) {
            release(refresh)
            throw error
        }
    }

    private fun release(refresh: Refresh) = synchronized(this) {
        pending.remove(refresh)
        if (coalesced === refresh) coalesced = null
    }

    // Chrome callbacks also restore original styles; disabling must not suppress cleanup.
    private fun canApply(): Boolean = isEnabled() || role.restoresWhenDisabled

    private inner class Refresh(private val releaseAfterApply: Boolean) : Runnable {
        override fun run() {
            synchronized(this@HomeNativeGlassRefreshSession) {
                // Cancellation may race a callback already taken by the UI queue.
                if (closed || this !in pending) return
                if (!releaseAfterApply) release(this)
            }
            try {
                if (canApply() && (!releaseAfterApply || isAttached())) apply()
            } finally {
                if (releaseAfterApply) release(this)
            }
        }
    }
}
