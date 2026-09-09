package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.RelativeLayout

/** Releases the native bottom-tab margin on the Enter Forum and Retail Store web pages. */
internal object LiquidGlassWebContent {
    /**
     * [webContainer] is a validated host TbWebView found inside the glass-owned [pager].
     * These bottom tabs wrap their web content in a direct pager page and reserve the tab row with
     * a bottom margin on TbWebView. MATCH_PARENT then measures the WebView short by that margin.
     */
    fun expandIntoTabReserve(webContainer: ViewGroup, pager: ViewGroup, tabRowHeight: Int): Boolean {
        if (tabRowHeight <= 0) return false
        val content = webContainer.parent as? RelativeLayout ?: return false
        val page = content.parent as? RelativeLayout ?: return false
        // Home's upper tabs live inside a separate nested pager. Their padding is not part of the
        // bottom-tab web layout, so do not alter it.
        if (page.parent !== pager || page.childCount != 1 || page.getChildAt(0) !== content) return false
        if (page.height <= tabRowHeight) return false
        if (page.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true) return false
        if (!reachesPagerBottom(content, pager)) return false

        val lp = webContainer.layoutParams as? RelativeLayout.LayoutParams ?: return false
        if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            lp.height != ViewGroup.LayoutParams.MATCH_PARENT ||
            lp.leftMargin != 0 || lp.topMargin != 0 || lp.rightMargin != 0 ||
            lp.bottomMargin != tabRowHeight
        ) {
            return false
        }
        // Visible title/loading layers are siblings on these pages, but do not constrain the
        // WebView. Check its actual layout rules so a page-owned toolbar constraint stays intact.
        if (lp.rules.any { it != 0 }) return false
        // Require exactly one tab-row margin to explain the missing height. Keep constraints
        // imposed by a page's own toolbar, controls or insets rather than stretching past them.
        if (webContainer.left != content.paddingLeft ||
            webContainer.top != content.paddingTop ||
            webContainer.right != content.width - content.paddingRight ||
            webContainer.bottom != content.height - content.paddingBottom - lp.bottomMargin
        ) {
            return false
        }
        // Leave padding, other margins and MATCH_PARENT sizing intact, including IME resizes.
        // The equality guard also makes subsequent layout callbacks a no-op until the host writes
        // its tab reserve again (for example when recreating the page or updating dynamic tabs).
        lp.bottomMargin = 0
        webContainer.layoutParams = lp
        return true
    }

    private fun reachesPagerBottom(page: ViewGroup, pager: ViewGroup): Boolean {
        var current: View = page
        repeat(8) {
            val parent = current.parent as? ViewGroup ?: return false
            if (current.bottom != parent.height - parent.paddingBottom ||
                current.translationY != 0f || parent.scrollY != 0
            ) {
                return false
            }
            if (parent === pager) return true
            current = parent
        }
        return false
    }
}
