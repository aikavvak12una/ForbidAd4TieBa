package com.forbidad4tieba.hook.feature.perf

import java.util.WeakHashMap

/** A page that displayed native preview content keeps that body when accepted network data arrives. */
internal class PbPreloadBodyContinuity {
    private data class FirstFloor(val threadId: String, val postId: Long)
    private val pages = WeakHashMap<Any, FirstFloor>()
    private val nativeModels = WeakHashMap<Any, Boolean>()

    @Synchronized
    fun accept(
        page: Any,
        model: Any,
        threadId: String,
        postId: Long,
        isPreload: Boolean,
        hasWebBody: Boolean,
        supportsNativeBody: Boolean,
        applyNativeBody: () -> Unit,
    ): Boolean {
        if (threadId.isBlank() || postId <= 0 || !supportsNativeBody) {
            pages.remove(page)
            return false
        }
        val firstFloor = FirstFloor(threadId, postId)
        if (pages[page] != firstFloor) pages.remove(page)
        if (pages[page] != firstFloor && !(isPreload && !hasWebBody)) return false
        // Publish the decision only after the accepted model has a consistent body representation.
        applyNativeBody()
        pages[page] = firstFloor
        nativeModels[model] = true
        return true
    }

    @Synchronized
    fun isNative(model: Any): Boolean = nativeModels.containsKey(model)
}
