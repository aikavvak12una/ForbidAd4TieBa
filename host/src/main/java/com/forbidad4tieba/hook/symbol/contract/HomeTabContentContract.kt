package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.forbidad4tieba.hook.core.StableTiebaHookPoints

/** Stable home-page anchors, resolved once without adding a persisted symbol or a new hook. */
object HomeTabContentContract {
    class Targets internal constructor(
        private val homeType: Class<*>,
        private val pagerType: Class<*>,
        private val webType: Class<*>?,
        private val liveType: Class<*>?,
    ) {
        val hasWebContent: Boolean get() = webType != null
        val hasLiveContent: Boolean get() = liveType != null

        /** Only the full-page web/live container inside the home tab's own pager owns this reserve. */
        fun paddingOwner(content: ViewGroup, outerPager: ViewGroup): ViewGroup? {
            val web = webType?.isInstance(content) == true
            val type = if (web) webType else liveType?.takeIf { it.isInstance(content) }
            if (type == null) return null
            val page = content.parent as? ViewGroup ?: return null
            if (if (web) page !is RelativeLayout else page !is FrameLayout) return null
            if (!isHomePage(page, outerPager) || !fillsPage(content, page)) return null
            var matches = 0
            for (index in 0 until page.childCount) {
                if (type.isInstance(page.getChildAt(index)) && ++matches > 1) return null
            }
            return if (web) page else content
        }

        private fun isHomePage(page: ViewGroup, outerPager: ViewGroup): Boolean {
            val pager = page.parent as? ViewGroup ?: return false
            if (pager === outerPager || !pagerType.isInstance(pager)) return false
            val frame = pager.parent as? FrameLayout ?: return false
            val home = frame.parent as? ViewGroup ?: return false
            if (!homeType.isInstance(home) || home.parent !== outerPager) return false
            var pagers = 0
            for (index in 0 until frame.childCount) {
                if (pagerType.isInstance(frame.getChildAt(index)) && ++pagers > 1) return false
            }
            // Header height may change, but none of these ancestors may reserve another footer.
            var current: View = page
            while (current !== outerPager) {
                val parent = current.parent as? ViewGroup ?: return false
                if (current.bottom != parent.height - parent.paddingBottom ||
                    current.translationY != 0f || parent.scrollY != 0
                ) return false
                current = parent
            }
            return true
        }

        private fun fillsPage(content: ViewGroup, page: ViewGroup): Boolean {
            val lp = content.layoutParams ?: return false
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) return false
            if (lp is ViewGroup.MarginLayoutParams &&
                (lp.leftMargin != 0 || lp.topMargin != 0 || lp.rightMargin != 0 || lp.bottomMargin != 0)
            ) return false
            if (lp is RelativeLayout.LayoutParams && lp.rules.any { it != 0 }) return false
            return content.width > 0 && content.height > 0 &&
                content.left == page.paddingLeft && content.top == page.paddingTop &&
                content.right == page.width - page.paddingRight &&
                content.bottom == page.height - page.paddingBottom && content.translationY == 0f
        }
    }

    fun resolve(classLoader: ClassLoader, webType: Class<*>?): Targets {
        val home = Class.forName("com.baidu.tieba.homepage.framework.indicator.ScrollFragmentTabHost", false, classLoader)
        val pager = Class.forName(StableTiebaHookPoints.CUSTOM_VIEW_PAGER_CLASS, false, classLoader)
        val live = try {
            Class.forName("com.baidu.live.business.LiveTabFeedView", false, classLoader)
        } catch (_: ClassNotFoundException) {
            null
        }
        return validate(home, pager, webType, live)
    }

    internal fun validate(home: Class<*>, pager: Class<*>, web: Class<*>?, live: Class<*>?): Targets {
        check(ViewGroup::class.java.isAssignableFrom(home)) { "Invalid home tab host" }
        check(ViewGroup::class.java.isAssignableFrom(pager)) { "Invalid home content pager" }
        return Targets(home, pager,
            web?.takeIf { FrameLayout::class.java.isAssignableFrom(it) },
            live?.takeIf { FrameLayout::class.java.isAssignableFrom(it) })
    }
}
