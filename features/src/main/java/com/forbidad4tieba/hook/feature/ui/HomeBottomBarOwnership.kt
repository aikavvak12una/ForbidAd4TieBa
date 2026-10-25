package com.forbidad4tieba.hook.feature.ui

import android.view.View

/** Shared chrome asks the installed owner without loading its API-specific rendering classes. */
internal object HomeBottomBarOwnership {
    interface Owner {
        fun isRuntimeActive(): Boolean
        fun ownsBottomBar(view: View): Boolean
    }

    @Volatile private var owner: Owner? = null

    /** Publish only after installation succeeds; page attachment remains the owner's decision. */
    fun publish(owner: Owner) { this.owner = owner }

    fun isRuntimeActive(): Boolean = owner?.isRuntimeActive() == true

    fun ownsBottomBar(view: View): Boolean = owner?.ownsBottomBar(view) == true
}
