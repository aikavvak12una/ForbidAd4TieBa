package com.forbidad4tieba.hook.feature.ui

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Associations live with the page and never keep a Fragment alive through its adapter. */
internal class CollectionSearchSessions {
    private val pages = WeakHashMap<Any, CollectionSearchPageSession>()
    private val presenters = WeakHashMap<Any, WeakReference<Any>>()
    private val adapters = WeakHashMap<Any, WeakReference<Any>>()

    @Synchronized
    operator fun get(fragment: Any): CollectionSearchPageSession? = pages[fragment]

    @Synchronized
    fun ensure(fragment: Any, sourceAccount: String?): CollectionSearchPageSession {
        val previous = pages[fragment]
        if (previous != null && previous.sourceAccount == sourceAccount) return previous
        previous?.close()
        return CollectionSearchPageSession(sourceAccount).also {
            // A typed query remains user intent; data and adapter indexes belong
            // to the old account and are never carried into its replacement.
            it.query = previous?.query.orEmpty()
            it.active = previous?.active == true
            pages[fragment] = it
        }
    }

    @Synchronized
    fun isCurrent(fragment: Any, session: CollectionSearchPageSession, currentAccount: String?): Boolean =
        pages[fragment] === session && session.sourceAccount == currentAccount

    @Synchronized
    fun current(fragment: Any, request: CollectionSearchRequest): CollectionSearchPageSession? =
        pages[fragment]?.takeIf { it.owns(request) }

    @Synchronized
    fun accepts(fragment: Any, request: CollectionSearchRequest, currentAccount: String?): Boolean =
        current(fragment, request) != null && request.sourceAccount == currentAccount

    @Synchronized
    fun bindPresenter(presenter: Any, fragment: Any) { presenters[presenter] = WeakReference(fragment) }

    @Synchronized
    fun presenterOwner(presenter: Any): Any? = presenters[presenter]?.get()

    @Synchronized
    fun bindAdapter(adapter: Any, fragment: Any) { adapters[adapter] = WeakReference(fragment) }

    @Synchronized
    fun adapterOwner(adapter: Any): Any? = adapters[adapter]?.get()

    @Synchronized
    fun clear(fragment: Any) {
        pages.remove(fragment)?.close()
        presenters.entries.removeAll { it.value.get().let { owner -> owner == null || owner === fragment } }
        adapters.entries.removeAll { it.value.get().let { owner -> owner == null || owner === fragment } }
    }
}
