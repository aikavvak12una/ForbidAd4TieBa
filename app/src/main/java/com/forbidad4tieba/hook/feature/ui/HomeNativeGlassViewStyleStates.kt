package com.forbidad4tieba.hook.feature.ui

import android.view.View
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

internal class HomeNativeGlassViewStyleStates {
    private val feedCards = Collections.synchronizedMap(WeakHashMap<View, AppliedStyle<HomeFeedCardStyleState>>())
    private val subPbLayouts = Collections.synchronizedMap(WeakHashMap<View, AppliedStyle<PbSubPbLayoutCardState>>())

    fun feedCardMatches(card: View, page: View?, state: HomeFeedCardStyleState): Boolean =
        feedCards[card]?.matches(page, state) == true

    fun rememberFeedCard(card: View, page: View?, state: HomeFeedCardStyleState) {
        feedCards[card] = AppliedStyle(page, state)
    }

    fun subPbLayoutMatches(layout: View, host: View, state: PbSubPbLayoutCardState): Boolean =
        subPbLayouts[layout]?.matches(host, state) == true

    fun rememberSubPbLayout(layout: View, host: View, state: PbSubPbLayoutCardState) {
        subPbLayouts[layout] = AppliedStyle(host, state)
    }

    fun collectAndClearFeedCards(onCard: (View) -> Unit) {
        synchronized(feedCards) {
            feedCards.keys.forEach(onCard)
            feedCards.clear()
        }
    }

    fun clearSubPbLayouts() {
        subPbLayouts.clear()
    }

    private class AppliedStyle<S>(owner: View?, private val state: S) {
        // A strong parent reference would keep the weak key alive through its View tree.
        private val ownerRef = owner?.let(::WeakReference)

        fun matches(owner: View?, next: S): Boolean {
            if (state != next) return false
            val reference = ownerRef ?: return owner == null
            // A collected former parent is distinct from a deliberately absent page.
            val previous = reference.get() ?: return false
            return previous == owner
        }
    }
}

internal data class HomeFeedCardStyleState(
    val width: Int,
    val height: Int,
    val childCount: Int,
    val sourcePath: String,
    val blurCachePath: String,
    val sourceLastModified: Long,
    val sourceLength: Long,
    val blurCacheLastModified: Long,
    val blurCacheLength: Long,
    val tintAlphaPercent: Int,
    val cardBlurPercent: Int,
    val cardRadiusDp: Int,
    val strokeEnabled: Boolean,
    val shadowStrengthPercent: Int,
)

internal data class PbSubPbLayoutCardState(
    val blurCachePath: String,
    val sourcePath: String,
    val tintAlphaPercent: Int,
    val cardBlurPercent: Int,
    val cardRadiusDp: Int,
    val strokeEnabled: Boolean,
    val shadowStrengthPercent: Int,
)

