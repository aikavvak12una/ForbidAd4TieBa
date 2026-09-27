package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Measures the host tab row so the glass pill can hug its content instead of the full-bleed bar.
 *
 * The host sizes its bar for a bar: full screen width, and tall enough to hold an icon, a label and
 * a navigation-bar reserve. A floating capsule wants neither. The two measurements here are ported
 * from liuran001/WeChat-LiquidGlass (MIT) - `contentBarHeight` and `hugContentWidth` - which in turn
 * reproduce KernelSU's `IntrinsicSize.Min` row.
 *
 * Both fail soft: when a measurement comes back nonsensical the caller keeps the host's own value,
 * so the worst case is the previous full-width look rather than a broken bar.
 */
internal object LiquidGlassBarMetrics {

    /** KernelSU's 64dp outer surface contains a 56dp tab backdrop and selection. */
    const val DROPLET_INSET_DP = 4f
    const val HORIZONTAL_PADDING_DP = 6f

    /** Places the capsule above the actual navigation area, without counting consumed insets twice. */
    fun floatingTop(
        hostHeight: Int,
        hostBottomInWindow: Int,
        windowBottom: Int,
        navigationBottom: Int,
        pillHeight: Int,
        density: Float,
        bottomGapDp: Int = -1,
    ): Int {
        val navigation = navigationBottom.coerceAtLeast(0)
        val overlap = (hostBottomInWindow - (windowBottom - navigation)).coerceIn(0, hostHeight)
        val gapDp = if (bottomGapDp >= 0) bottomGapDp.toFloat() else if (navigation > 0) 8f else 28f
        val gap = (gapDp * density).roundToInt()
        return (hostHeight - overlap - gap - pillHeight).coerceAtLeast(0)
    }

    /** Breathing room added to the widest column's content, per column. Matches KernelSU. */
    private const val COLUMN_PADDING_DP = 32f
    /** The pill always leaves this much of the screen free, so it never touches the edges. */
    private const val SCREEN_RESERVE_DP = 24f

    /** Shape of the per-column top rule: hairline-thin, column-wide, flush with the column top. */
    private const val HAIRLINE_MAX_HEIGHT_DP = 2f
    private const val HAIRLINE_MAX_TOP_OFFSET_DP = 4f
    private const val HAIRLINE_MIN_WIDTH_RATIO = 0.75f

    /**
     * Bounds of what the row actually draws, in row coordinates.
     *
     * Writes `[left, top, right, bottom]` into [out] and returns true, or returns false when the row
     * draws nothing measurable and the caller should fall back to the host's own extent.
     *
     * Horizontal bounds are the plain union of the visible columns. Vertical bounds are the *median*
     * of the per-column extents rather than the union: an unread badge sits above its icon and only
     * on one column, and letting it drag the union upwards pushes the whole capsule up while the
     * visual mass of icons and labels stays put. With four or five columns the median ignores it.
     *
     * The reference project turns its bounds into `bottom + top` - the height that leaves as much
     * room below the content as the host left above it. That works when the host bar carries slack
     * above its icons. Tieba's does not: its content starts at row row 0, so the symmetric height
     * degrades to hugging the content exactly. The caller pads these bounds out instead.
     */
    fun measureContent(row: ViewGroup, out: IntArray): Boolean {
        var left = Int.MAX_VALUE
        var right = 0
        val tops = ArrayList<Int>(row.childCount)
        val bottoms = ArrayList<Int>(row.childCount)
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility != View.VISIBLE || tab.width <= 0) continue
            val bounds = intArrayOf(Int.MAX_VALUE, 0)
            leafBounds(tab, 0, bounds)
            if (bounds[0] >= bounds[1]) continue
            left = min(left, tab.left)
            right = max(right, tab.right)
            tops.add(tab.top + bounds[0])
            bottoms.add(tab.top + bounds[1])
        }
        if (tops.isEmpty() || left >= right) return false
        tops.sort()
        bottoms.sort()
        val top = tops[tops.size / 2]
        val bottom = bottoms[bottoms.size / 2]
        if (bottom <= top) return false
        out[0] = left
        out[1] = top
        out[2] = right
        out[3] = bottom
        return true
    }

    /** Vertical extent of a tab's drawn content, relative to the tab column. */
    private fun leafBounds(view: View, offset: Int, out: IntArray) {
        if (view.visibility != View.VISIBLE) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                leafBounds(child, offset + child.top, out)
            }
            return
        }
        if (view.width <= 0 || view.height <= 0) return
        out[0] = min(out[0], offset)
        out[1] = max(out[1], offset + view.height)
    }

    /**
     * Replaces the host's equal-weight tab columns with fixed, content-sized ones, centred.
     *
     * The host hands every tab `LinearLayout.LayoutParams(0, h, weight = 1)`, which only makes sense
     * when the bar spans the screen. For a capsule the width has to come from the content instead.
     *
     * The reference project can leave the columns packed to the left because it reparents the row
     * into a host sized to the hug width. This one does not reparent - the row stays full-screen
     * width - so the columns have to be centred inside it, or the content sits left of the capsule.
     *
     * Returns true when the row was hugged. Idempotent: re-running it on an already hugged row
     * writes nothing, so it is safe to call from a layout callback.
     */
    fun applyHugWidth(row: ViewGroup, density: Float, widthPercent: Int = 0): Boolean {
        if (row.childCount == 0) return false

        if (widthPercent > 0) {
            val count = (0 until row.childCount).count { row.getChildAt(it).visibility != View.GONE }
            val width = configuredColumnWidth(row.width, count, density, widthPercent)
            return applyColumnWidth(row, width)
        }

        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var count = 0
        var measured = 0
        var leaf = 0
        var slot = 0
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility == View.GONE) continue
            count++
            tab.measure(unspecified, unspecified)
            measured = max(measured, tab.measuredWidth)
            // The intrinsic probe touches the live tab tree. Restore its current constraints
            // before drawing, even when the computed hug width leaves LayoutParams unchanged.
            // Force descendants too: a cached parent measurement alone leaves their probe sizes.
            if (tab.width > 0 && tab.height > 0) {
                clearMeasureCache(tab)
                tab.measure(
                    View.MeasureSpec.makeMeasureSpec(tab.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(tab.height, View.MeasureSpec.EXACTLY),
                )
            }
            leaf = max(leaf, leafContentWidth(tab))
            slot = max(slot, tab.width)
        }
        if (count == 0) return false

        // An unbounded measure that comes back wider than the column the tab is already laid out in
        // has not measured content at all - measuring a MATCH_PARENT child against UNSPECIFIED is
        // undefined, and some layouts answer with something unrelated. Fall back to the leaves.
        if (slot > 0 && measured > slot) measured = 0
        val widest = max(measured, leaf)
        if (widest <= 0) return false

        var tabWidth = widest + (COLUMN_PADDING_DP * density).roundToInt()
        // The row's own width, not displayMetrics.widthPixels: in split-screen or a freeform window
        // the display is wider than the app, and capping against the display would let the capsule
        // run past the window edge.
        val available = row.width.takeIf { it > 0 } ?: row.resources.displayMetrics.widthPixels
        val maxTotal = available - (SCREEN_RESERVE_DP * density).roundToInt()
        if (tabWidth * count > maxTotal) {
            tabWidth = maxTotal / count
        }
        if (tabWidth <= 0) return false

        return applyColumnWidth(row, tabWidth)
    }

    /** The percentage describes the OUTER capsule, including its horizontal padding. */
    fun configuredColumnWidth(available: Int, count: Int, density: Float, percent: Int): Int {
        if (available <= 0 || count <= 0) return 0
        val outer = (available * percent.coerceIn(1, 100) / 100f).roundToInt()
        return ((outer - 2 * (HORIZONTAL_PADDING_DP * density).roundToInt()) / count).coerceAtLeast(1)
    }

    private fun applyColumnWidth(row: ViewGroup, tabWidth: Int): Boolean {
        if (tabWidth <= 0) return false

        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility == View.GONE) continue
            val lp = tab.layoutParams ?: continue
            val weighted = lp as? LinearLayout.LayoutParams
            if (lp.width == tabWidth && (weighted == null || weighted.weight == 0f)) continue
            lp.width = tabWidth
            weighted?.weight = 0f
            tab.layoutParams = lp
        }
        centreColumns(row)
        return true
    }

    private fun clearMeasureCache(view: View) {
        view.forceLayout()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                clearMeasureCache(view.getChildAt(i) ?: continue)
            }
        }
    }

    /**
     * Centres the now fixed-width columns inside the still full-width row.
     *
     * Only the horizontal component is touched; the host's vertical gravity decides where the icon
     * and label sit inside their column and must be left alone. When the host set no vertical
     * gravity at all, `CENTER_VERTICAL` is supplied - a horizontal `LinearLayout` defaults to `TOP`,
     * and letting that stand would drag the icons up.
     */
    private fun centreColumns(row: ViewGroup) {
        val linear = row as? LinearLayout ?: return
        val vertical = linear.gravity and Gravity.VERTICAL_GRAVITY_MASK
        val desired = (if (vertical == 0) Gravity.CENTER_VERTICAL else vertical) or
            Gravity.CENTER_HORIZONTAL
        if (linear.gravity != desired) linear.gravity = desired
        // Any horizontal padding the host left would offset the centred block; the capsule supplies
        // its own breathing room around the measured content instead.
        if (row.paddingLeft != 0 || row.paddingRight != 0) {
            row.setPadding(0, row.paddingTop, 0, row.paddingBottom)
        }
    }

    /**
     * Hides the 1px rule each tab column draws along its own top edge.
     *
     * The host's bottom-bar hairline is not painted by `FragmentTabWidget.draw()` - those three
     * booleans gate a different set of rects - but by a plain `View` sitting at the top of every
     * column, which together read as one continuous line across the bar. Matching it structurally
     * (a hairline-thin, column-wide, top-aligned leaf) avoids naming the host resource id.
     *
     * Idempotent, and narrow enough that a false positive can only ever hide something that is
     * already a hairline at the top of a tab column.
     */
    fun hideColumnHairlines(row: ViewGroup, density: Float) {
        val maxHeight = max(1, (HAIRLINE_MAX_HEIGHT_DP * density).roundToInt())
        val maxTop = (HAIRLINE_MAX_TOP_OFFSET_DP * density).roundToInt()
        for (i in 0 until row.childCount) {
            val column = row.getChildAt(i) as? ViewGroup ?: continue
            if (column.visibility != View.VISIBLE || column.width <= 0) continue
            val minWidth = (column.width * HAIRLINE_MIN_WIDTH_RATIO).roundToInt()
            for (j in 0 until column.childCount) {
                val child = column.getChildAt(j) ?: continue
                if (child is ViewGroup || child.visibility != View.VISIBLE) continue
                if (child.height !in 1..maxHeight) continue
                if (child.width < minWidth) continue
                if (child.top > maxTop) continue
                child.visibility = View.GONE
            }
        }
    }

    /**
     * Width of the widest thing actually drawn inside a tab.
     *
     * Only visible leaves count: the unread badge and the red dot sit at INVISIBLE rather than GONE,
     * so they are still laid out and are wide enough to stretch the column if allowed to. A
     * MATCH_PARENT leaf is only ever as wide as the column it was handed, so it says nothing about
     * what is drawn inside it.
     */
    private fun leafContentWidth(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        if (view is ViewGroup) {
            var widest = 0
            for (i in 0 until view.childCount) {
                widest = max(widest, leafContentWidth(view.getChildAt(i) ?: continue))
            }
            return widest
        }
        val lp = view.layoutParams
        if (lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) return 0
        return view.width
    }
}
