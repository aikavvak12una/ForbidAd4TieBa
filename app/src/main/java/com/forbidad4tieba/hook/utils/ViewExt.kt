package com.forbidad4tieba.hook.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.AbsListView
import com.forbidad4tieba.hook.core.XposedCompat

object ViewExt {
    // Safe tag IDs (0x7E prefix avoids collision with app R.id 0x7F)
    const val TAG_SQUASH_STATE = 0x7E000001.toInt()
    const val TAG_EMPTY_VIEW = 0x7E000002.toInt()
    const val TAG_SETTINGS_BOUND = 0x7E000003.toInt()
    
    private const val LP_UNSET = Int.MIN_VALUE

    fun markSettingsLongPressBound(view: View): Boolean {
        if (view.getTag(TAG_SETTINGS_BOUND) == true) return false
        view.setTag(TAG_SETTINGS_BOUND, true)
        return true
    }

    fun isOurEmptyView(view: View): Boolean {
        return view.getTag(TAG_EMPTY_VIEW) == true
    }

    fun obtainEmptyView(context: Context?, reuseIfPossible: View?): View? {
        if (context == null) return reuseIfPossible
        if (reuseIfPossible != null && isOurEmptyView(reuseIfPossible)) return reuseIfPossible

        val emptyView = View(context)
        emptyView.setTag(TAG_EMPTY_VIEW, true)
        configureEmptyView(emptyView)
        return emptyView
    }

    private fun configureEmptyView(view: View) {
        try {
            view.visibility = View.GONE
            view.minimumHeight = 0
            view.minimumWidth = 0
            view.setPadding(0, 0, 0, 0)
            val lp = view.layoutParams
            if (lp !is AbsListView.LayoutParams || lp.width != 0 || lp.height != 0) {
                view.layoutParams = AbsListView.LayoutParams(0, 0)
            }
        } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
    }

    fun squashView(view: View?) {
        if (view == null) return
        if (view.visibility == View.GONE && view.minimumHeight == 0 && view.minimumWidth == 0) return
        try {
            view.visibility = View.GONE
            view.minimumHeight = 0
            view.minimumWidth = 0
            view.setPadding(0, 0, 0, 0)
            val lp = view.layoutParams
            if (lp != null) {
                lp.width = 0
                lp.height = 0
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.setMargins(0, 0, 0, 0)
                }
                view.layoutParams = lp
            }
        } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
    }

    fun squashAncestorFeedCard(v: View) {
        val feedCard = findAncestorViewByClassName(v, "com.baidu.tieba.feed.card.FeedCardView")
        if (feedCard != null) squashViewRemembering(feedCard)
    }

    fun findAncestorViewByClassName(view: View?, className: String?): View? {
        if (view == null || className == null) return null
        try {
            var parent: ViewParent? = view.parent
            while (parent is View) {
                if (className == parent.javaClass.name) return parent
                parent = parent.parent
            }
        } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
        return null
    }

    fun squashViewRemembering(view: View?) {
        if (view == null) return
        try {
            var state = view.getTag(TAG_SQUASH_STATE) as? SquashState
            if (state == null || !state.squashed) {
                state = SquashState(view)
                view.setTag(TAG_SQUASH_STATE, state)
            }
            state.squashed = true
        } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
        squashView(view)
    }

    fun restoreViewIfSquashed(view: View?) {
        if (view == null) return
        try {
            val obj = view.getTag(TAG_SQUASH_STATE) as? SquashState ?: return
            if (!obj.squashed) return
            obj.restore(view)
            obj.squashed = false
        } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
    }

    class SquashState(view: View) {
        val visibility: Int = view.visibility
        val minWidth: Int = view.minimumWidth
        val minHeight: Int = view.minimumHeight
        val padL: Int = view.paddingLeft
        val padT: Int = view.paddingTop
        val padR: Int = view.paddingRight
        val padB: Int = view.paddingBottom
        val lpW: Int
        val lpH: Int
        val mL: Int
        val mT: Int
        val mR: Int
        val mB: Int
        var squashed: Boolean = false

        init {
            val lp = view.layoutParams
            if (lp != null) {
                lpW = lp.width
                lpH = lp.height
                if (lp is ViewGroup.MarginLayoutParams) {
                    mL = lp.leftMargin
                    mT = lp.topMargin
                    mR = lp.rightMargin
                    mB = lp.bottomMargin
                } else {
                    mL = LP_UNSET
                    mT = LP_UNSET
                    mR = LP_UNSET
                    mB = LP_UNSET
                }
            } else {
                lpW = LP_UNSET
                lpH = LP_UNSET
                mL = LP_UNSET
                mT = LP_UNSET
                mR = LP_UNSET
                mB = LP_UNSET
            }
        }

        fun restore(view: View) {
            try {
                view.visibility = visibility
                view.minimumWidth = minWidth
                view.minimumHeight = minHeight
                view.setPadding(padL, padT, padR, padB)

                val lp = view.layoutParams
                if (lp != null) {
                    if (lpW != LP_UNSET) lp.width = lpW
                    if (lpH != LP_UNSET) lp.height = lpH
                    if (lp is ViewGroup.MarginLayoutParams && mL != LP_UNSET) {
                        lp.setMargins(mL, mT, mR, mB)
                    }
                    view.layoutParams = lp
                } else {
                    view.requestLayout()
                }
            } catch (t: Throwable) { XposedCompat.logD("ViewExt: ${t.message}") }
        }
    }
}
