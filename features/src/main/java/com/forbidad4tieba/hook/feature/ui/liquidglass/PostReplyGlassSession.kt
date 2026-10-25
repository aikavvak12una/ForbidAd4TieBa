package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.PostReplyBarContract
import kotlin.math.roundToInt

/** Keeps native parents, click listeners, accessibility and host visibility ownership intact. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class PostReplyGlassSession(
    private val page: PostReplyBarContract.Page,
    private val targets: PostReplyBarContract.Targets,
    private val config: BottomTabLiquidGlassConfig,
    private val navigationInsetBottom: () -> Int,
    private val onHierarchyChanged: (Boolean) -> Unit,
    private val onUnavailable: () -> Unit,
    autoHideAvailable: Boolean = false,
    private val initiallyHidden: Boolean = false,
) : ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnWindowFocusChangeListener {
    private var density = LiquidGlassBarMetrics.density(page.root.resources)
    private var fontScale = page.root.resources.configuration.fontScale
    private var night = targets.isNight()
    private val panel = LiquidGlassPanel(page.root.context, page.backdrop, density, night, config)
    private var original: NativeState? = null
    private val autoHide = if (autoHideAvailable) PostReplyBarAutoHide(page.slot, ::onAutoHideChanged) else null
    private var autoHideFailed = false
    private var comments: CommentState? = null
    private var bounds: PostReplyGlassGeometry.Bounds? = null
    private var layoutDirty = true
    private var loadingGuard: View? = null
    private var disposed = false
    private var ownsBackdropLayer = false
    private var lift = 0f
    private var horizontalShift = 0f
    private var pressed = false
    private var pressProgress = 0f
    private var pressX = 0f
    private var animator: ValueAnimator? = null
    private val hostPosition = IntArray(2)
    private val windowPosition = IntArray(2)
    private val slotOffset = Rect()
    private val contentOffset = Rect()
    private val chrome = listOfNotNull(page.underlay, page.stack, page.slot, page.row, page.input, page.divider)
    private val unclipped = listOf(page.root, page.underlay, page.stack, page.slot, page.row)

    private class NativeState(page: PostReplyBarContract.Page, groups: List<ViewGroup>, chrome: List<View>) {
        val slotLayout = LinearLayout.LayoutParams(page.slot.layoutParams as LinearLayout.LayoutParams)
        val rowLayout = FrameLayout.LayoutParams(page.row.layoutParams as FrameLayout.LayoutParams)
        val inputLayout = LinearLayout.LayoutParams(page.input.layoutParams as LinearLayout.LayoutParams)
        // The host reserves its final bar height even while only the loading input exists.
        val minimumHeight = maxOf(slotLayout.height, rowLayout.height, 0)
        val rowPadding = padding(page.row)
        val inputPadding = padding(page.input)
        val rowGravity = page.row.gravity
        val inputGravity = page.input.gravity
        val actionLayouts = linkedMapOf<View, LinearLayout.LayoutParams>()
        val actionContent = linkedMapOf<ViewGroup, PostReplyActionLayout>()
        val scaleX = page.slot.scaleX
        val scaleY = page.slot.scaleY
        val clips = groups.associateWith { it.clipChildren to it.clipToPadding }
        val backgrounds = linkedMapOf<View, Drawable?>().apply {
            chrome.forEach { put(it, it.background) }
        }
    }

    private class CommentState(val view: ViewGroup) {
        val bottomPadding = view.paddingBottom
        val clip = view.clipToPadding

        fun restore() {
            if (view.paddingBottom != bottomPadding) {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomPadding)
            }
            if (view.clipToPadding != clip) view.clipToPadding = clip
        }
    }

    fun start() {
        check(panel.isSupported) { "Liquid glass shader unavailable" }
        panel.visibility = View.INVISIBLE
        page.slot.addView(panel, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val native = NativeState(page, unclipped, chrome).also { original = it }
        // Prepare before the first host measure, including before the window first receives focus.
        configureRow(native, null, PostReplyGlassGeometry.height(0, density, native.minimumHeight), null)
        clearChrome(native)
        page.root.viewTreeObserver.apply {
            addOnPreDrawListener(this@PostReplyGlassSession)
            addOnGlobalLayoutListener(this@PostReplyGlassSession)
            if (config.needsBackdrop) addOnScrollChangedListener(this@PostReplyGlassSession)
            addOnWindowFocusChangeListener(this@PostReplyGlassSession)
        }
        if (autoHide != null) PostReplyScrollDispatcher.bind(page.recycler, this)
        if (initiallyHidden && ConfigManager.snapshot().isHomeTabAutoHideEnabled) autoHide?.startHidden()
        layoutDirty = true
        // A replacement session may start inside an existing pre-draw dispatch. Its newly added
        // listener will only run next frame, so settle this bar immediately when it is laid out.
        updateSafely()
        page.root.invalidate()
    }

    override fun onPreDraw(): Boolean {
        updateSafely()
        return true // Decorative work must never cancel the host's entire window draw.
    }

    override fun onGlobalLayout() {
        if (disposed || panel.isCapturing) return
        page.refreshEditors()
        layoutDirty = true
        // A local bar measure here settles geometry before this traversal's first draw.
        updateSafely()
    }

    override fun onScrollChanged() {
        if (!disposed && !panel.isCapturing && original != null && panel.isShown) refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        layoutDirty = true
        page.root.invalidate()
    }

    private fun updateSafely() {
        if (disposed || panel.isCapturing) return
        try {
            update()
        } catch (failure: Throwable) {
            XposedCompat.logW("[PostReplyLiquidGlassHook] page disabled: ${failure.message}")
            disable()
        }
    }

    private fun refresh() {
        panel.refreshBackdrop()
    }

    private fun update() {
        val settings = ConfigManager.snapshot()
        val insets = page.root.rootWindowInsets
        if (!PostReplyGlassGeometry.shouldFloat(settings.isFloatingBottomBarEnabled,
                page.row.visibility == View.VISIBLE && page.underlay.visibility == View.VISIBLE && page.underlay.alpha > 0f,
                insets?.isVisible(WindowInsets.Type.ime()) == true, page.isEditorShown())) {
            restore()
            return
        }
        if ((!settings.isHomeTabAutoHideEnabled || autoHideFailed) && autoHide?.reset() == true) {
            layoutDirty = true
            refresh()
        }
        // A hidden bar still detects editor entry and replacement of its fixed native anchors.
        // It does not walk actions, measure content, inspect themes, capture or render the page.
        if (autoHide?.suspended == true && page.slot.parent === page.stack &&
            page.row.parent === page.slot && page.input.parent === page.row && page.row.getChildAt(0) === page.input) return
        if (!page.isIntact()) {
            if (!page.refreshActions() || !page.isIntact()) {
                XposedCompat.logD("[PostReplyLiquidGlassHook] reply controls replaced; rebinding")
                val hidden = autoHide?.hidden == true
                dispose()
                onHierarchyChanged(hidden)
                return
            }
            layoutDirty = true
        }
        val resources = page.root.resources
        val currentDensity = LiquidGlassBarMetrics.density(resources)
        val currentFontScale = resources.configuration.fontScale
        if (currentDensity.isFinite() && currentDensity > 0f &&
            (density != currentDensity || fontScale != currentFontScale)) {
            animator?.cancel()
            pressed = false
            pressProgress = 0f
            original?.let { page.slot.scaleX = it.scaleX; page.slot.scaleY = it.scaleY }
            density = currentDensity
            fontScale = currentFontScale
            panel.updateDensity(currentDensity)
            panel.setInteraction(0f, 0f)
            autoHide?.reset()
            layoutDirty = true
        }
        if (page.root.width <= 0) return
        val native = original ?: NativeState(page, unclipped, chrome).also {
            original = it
            layoutDirty = true
        }
        if (layoutDirty) {
            layoutDirty = false
            if (!updateLayout(native, insets)) return
        }
        if (chrome.any { it.background != null }) {
            clearChrome(native)
            updateTheme()
            refresh()
        }
        // The screen and glass reuse the browser subtree at different GPU transforms. Without
        // a layer, its WebView functor receives competing viewport/tile constraints and can
        // alternate between content and empty frames. Rasterize this subtree once, then sample
        // its texture. Preserve any layer (and layer paint) already selected by the host.
        if (config.needsBackdrop && !ownsBackdropLayer && page.recycler.layerType == View.LAYER_TYPE_NONE) {
            page.recycler.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            ownsBackdropLayer = true
        }
        followPlacement()
        panel.setContainerAlpha(page.row.alpha)
        updatePress()
        if (config.needsBackdrop && panel.visibility == View.VISIBLE && page.backdrop.isDirty) {
            panel.refreshBackdrop()
        }
    }

    private fun updateLayout(native: NativeState, insets: WindowInsets?): Boolean {
        updateTheme()
        page.root.getLocationInWindow(hostPosition)
        val window = page.root.rootView
        window.getLocationInWindow(windowPosition)
        if (!LiquidGlassGeom.layoutOffsetInAncestor(page.recycler, page.root, contentOffset)) {
            disable()
            return false
        }
        val safe = insets?.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val actionCount = displayedActionCount()
        fun calculate(controlHeight: Int) = PostReplyGlassGeometry.calculate(
            page.root.width, page.root.height, hostPosition[0], hostPosition[1],
            windowPosition[0], windowPosition[0] + window.width, windowPosition[1] + window.height,
            safe?.left ?: 0, safe?.right ?: 0, maxOf(safe?.bottom ?: 0, navigationInsetBottom()),
            contentOffset.top, actionCount, controlHeight, density, config, native.minimumHeight,
        )
        val available = calculate(0) ?: run { restore(); return false }
        val surfaces = PostReplyGlassGeometry.separate(available.width, actionCount, density)
        if (actionCount > 0 && surfaces == null) { restore(); return false }
        val content = prepareActionContent(native, surfaces)
        val loadingChanged = updateLoadingGuard(available, surfaces)
        val controlHeight = maxOf(nativeControlHeight(native, available.width, surfaces),
            (loadingGuard as? PostReplyPendingActions)?.contentHeight ?: 0)
        val geometry = calculate(controlHeight)
            ?: run { restore(); return false }
        var changed = bounds != geometry || loadingChanged
        bounds = geometry
        clearChrome(native)
        changed = configureRow(native, geometry.width, geometry.height, surfaces) || changed
        // Settle only the bar subtree. Never cancel a window draw or remeasure the comment page.
        if (page.slot.isLayoutRequested || page.slot.width != geometry.width || page.slot.height != geometry.height) {
            page.slot.measure(View.MeasureSpec.makeMeasureSpec(geometry.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(geometry.height, View.MeasureSpec.EXACTLY))
            page.slot.layout(page.slot.left, page.slot.top,
                page.slot.left + geometry.width, page.slot.top + geometry.height)
            changed = true
        }
        changed = panel.setSeparatedSurfaces(surfaces?.inputWidth ?: 0, surfaces?.actionsLeft ?: 0) || changed
        content.forEach { it.layout() }
        changed = updateCommentReserve(geometry) || changed
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
            changed = true
        }
        if (changed) refresh()
        return true
    }

    // An empty row is still loading. Existing GONE actions are an intentional input-only bar.
    private fun displayedActionCount(): Int = if (page.actions.isEmpty()) PostReplyGlassGeometry.PENDING_ACTION_COUNT
        else page.actions.count { it.visibility != View.GONE }

    /** Early artwork also consumes taps until the native data-bound controls take ownership. */
    private fun updateLoadingGuard(geometry: PostReplyGlassGeometry.Bounds,
        surfaces: PostReplyGlassGeometry.Surfaces?): Boolean {
        if (page.actions.isNotEmpty() || surfaces == null) return removeLoadingGuard()
        val width = geometry.width - surfaces.actionsLeft
        val guard = loadingGuard ?: (targets.loadingArtwork?.let {
            PostReplyPendingActions(page.root.context, it, density, night)
        } ?: View(page.root.context)).also {
            it.isClickable = true
            it.isEnabled = false
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            loadingGuard = it
            page.slot.addView(it, page.slot.indexOfChild(page.row) + 1,
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    leftMargin = surfaces.actionsLeft
                })
            return true
        }
        (guard as? PostReplyPendingActions)?.update(density, night)
        val layout = guard.layoutParams as FrameLayout.LayoutParams
        if (layout.width == width && layout.leftMargin == surfaces.actionsLeft) return false
        layout.width = width
        layout.leftMargin = surfaces.actionsLeft
        guard.layoutParams = layout
        return true
    }

    private fun removeLoadingGuard(): Boolean {
        val guard = loadingGuard ?: return false
        (guard.parent as? ViewGroup)?.removeView(guard)
        loadingGuard = null
        return true
    }

    private fun prepareActionContent(native: NativeState,
        surfaces: PostReplyGlassGeometry.Surfaces?): List<PostReplyActionLayout> {
        if (surfaces == null || page.actions.isEmpty()) return emptyList()
        val content = checkNotNull(targets.actionContent(page)) { "Invalid reply icon/label content" }
        if (content.isEmpty()) return emptyList()
        // The native static artwork defines the visible icon box, including host density scaling.
        val size = content.filter { it.icon === it.artwork }.map {
            val layout = it.icon.layoutParams
            check(layout.width > 0 && layout.height == layout.width) { "Invalid reply icon size" }
            layout.width
        }.distinct().singleOrNull() ?: error("Ambiguous reply icon size")
        val actions = page.actions.filter { it.visibility != View.GONE }
        return content.map { item ->
            val index = actions.indexOfFirst { it === item.body.parent }
            check(index >= 0) { "Detached reply content" }
            native.actionContent.getOrPut(item.body) { PostReplyActionLayout(item) }.also {
                it.prepare(surfaces.columnWidth(index, actions.size), size, density)
            }
        }
    }

    private fun nativeControlHeight(native: NativeState, width: Int,
        surfaces: PostReplyGlassGeometry.Surfaces?): Int {
        fun intrinsicHeight(view: View, nativeHeight: Int, width: Int): Int {
            if (nativeHeight > 0) return nativeHeight
            // MATCH_PARENT and a stale measurement from the smaller loading row are not intrinsic
            // sizes. Measure only these native controls, at their final width, without a height cap.
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return view.measuredHeight
        }
        var height = intrinsicHeight(page.input, native.inputLayout.height, surfaces?.inputWidth ?: width)
        val actions = page.actions.filter { it.visibility != View.GONE }
        for ((index, action) in actions.withIndex()) {
            val columnWidth = checkNotNull(surfaces).columnWidth(index, actions.size)
            val params = native.actionLayouts[action] ?: action.layoutParams
            height = maxOf(height, intrinsicHeight(action, params.height, columnWidth))
        }
        // The local intrinsic measurements must be followed by the final slot measurement/layout.
        // forceLayout marks only this subtree; it does not request another whole-window traversal.
        page.slot.forceLayout()
        return height
    }

    private fun configureRow(native: NativeState, width: Int?, height: Int,
        surfaces: PostReplyGlassGeometry.Surfaces?): Boolean {
        var changed = false
        val slot = page.slot.layoutParams as LinearLayout.LayoutParams
        if ((width != null && slot.width != width) || slot.height != height || slot.gravity != Gravity.CENTER_HORIZONTAL) {
            if (width != null) slot.width = width
            slot.height = height
            slot.gravity = Gravity.CENTER_HORIZONTAL
            page.slot.layoutParams = slot
            changed = true
        }
        val row = page.row.layoutParams as FrameLayout.LayoutParams
        if (row.width != ViewGroup.LayoutParams.MATCH_PARENT || row.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            row.width = ViewGroup.LayoutParams.MATCH_PARENT
            row.height = ViewGroup.LayoutParams.MATCH_PARENT
            page.row.layoutParams = row
            changed = true
        }
        val hasActions = displayedActionCount() > 0
        val pad = if (hasActions) dp(PostReplyGlassGeometry.ACTION_PADDING_DP) else 0
        if (page.row.paddingLeft != 0 || page.row.paddingRight != pad ||
            page.row.paddingTop != 0 || page.row.paddingBottom != 0) {
            page.row.setPadding(0, 0, pad, 0)
            changed = true
        }
        for (linear in arrayOf(page.row, page.input)) {
            if (PostReplyGlassGeometry.needsVerticalCenter(linear.gravity)) {
                linear.gravity = (linear.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or Gravity.CENTER_VERTICAL
                changed = true
            }
        }
        val input = page.input.layoutParams as LinearLayout.LayoutParams
        val gap = if (hasActions) dp(PostReplyGlassGeometry.SURFACE_GAP_DP) + pad else 0
        val inputWidth = surfaces?.inputWidth ?: width
        if (input.leftMargin != 0 || input.rightMargin != gap || input.height != ViewGroup.LayoutParams.MATCH_PARENT ||
            (inputWidth != null && (input.width != inputWidth || input.weight != 0f))) {
            if (inputWidth != null) { input.width = inputWidth; input.weight = 0f }
            input.leftMargin = 0
            input.rightMargin = gap
            input.height = ViewGroup.LayoutParams.MATCH_PARENT
            page.input.layoutParams = input
            changed = true
        }
        if (surfaces != null) {
            val actions = page.actions.filter { it.visibility != View.GONE }
            for ((index, action) in actions.withIndex()) {
                val params = action.layoutParams as LinearLayout.LayoutParams
                native.actionLayouts.getOrPut(action) { LinearLayout.LayoutParams(params) }
                val columnWidth = surfaces.columnWidth(index, actions.size)
                if (params.width != columnWidth || params.weight != 0f || params.gravity != Gravity.CENTER_VERTICAL ||
                    params.height != ViewGroup.LayoutParams.MATCH_PARENT ||
                    params.leftMargin != 0 || params.rightMargin != 0 || params.topMargin != 0 || params.bottomMargin != 0) {
                    params.width = columnWidth
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    params.weight = 0f
                    params.gravity = Gravity.CENTER_VERTICAL
                    params.setMargins(0, 0, 0, 0)
                    action.layoutParams = params
                    changed = true
                }
            }
        }
        val inputLeft = dp(16f)
        val inputRight = dp(12f)
        if (page.input.paddingLeft != inputLeft || page.input.paddingRight != inputRight) {
            page.input.setPadding(inputLeft, page.input.paddingTop, inputRight, page.input.paddingBottom)
            changed = true
        }
        return changed
    }

    /** Outer page padding shortens the nested comment viewport and exposes a solid bottom band. */
    private fun updateCommentReserve(geometry: PostReplyGlassGeometry.Bounds): Boolean {
        val current = targets.currentComments(page)
        if (comments?.view !== current) {
            comments?.restore()
            comments = current?.let(::CommentState)
        }
        val state = comments ?: return false
        val view = state.view
        val bottom = PostReplyGlassGeometry.contentBottomPadding(state.bottomPadding,
            page.root.height, geometry.top, density)
        var changed = false
        if (view.paddingBottom != bottom) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            changed = true
        }
        // RecyclerView requests layout even when assigned its current value.
        if (view.clipToPadding) {
            view.clipToPadding = false
            changed = true
        }
        return changed
    }

    private fun followPlacement() {
        val geometry = bounds ?: return
        if (!LiquidGlassGeom.layoutOffsetInAncestor(page.slot, page.root, slotOffset)) { disable(); return }
        val nextLift = (geometry.top - slotOffset.top).toFloat()
        val nextShift = (geometry.left - slotOffset.left).toFloat()
        if (nextLift != lift) {
            page.underlay.translationY += nextLift - lift
            lift = nextLift
            refresh()
        }
        if (nextShift != horizontalShift) {
            page.slot.translationX += nextShift - horizontalShift
            horizontalShift = nextShift
            refresh()
        }
    }

    private fun updateTheme() {
        val current = targets.isNight()
        if (current != night) {
            night = current
            panel.setTheme(night)
            (loadingGuard as? PostReplyPendingActions)?.setTheme(night)
        }
    }

    private fun clearChrome(native: NativeState) {
        for (view in chrome) {
            if (view.background != null) {
                native.backgrounds[view] = view.background
                view.background = null
            }
        }
        for (group in unclipped) {
            if (group.clipChildren) group.clipChildren = false
            if (group.clipToPadding) group.clipToPadding = false
        }
    }

    /** Observe native pressed state instead of replacing listeners or synthesizing clicks. */
    private fun updatePress() {
        if (!config.liquidGlassEnabled) return
        val target = page.actions.firstOrNull { it.isPressed }
            ?: page.input.takeIf { it.isPressed } ?: page.row.takeIf { it.isPressed }
        val next = target != null
        if (target != null) pressX = if (target === page.row) page.row.width / 2f
            else page.row.left + target.left + target.width / 2f
        if (pressed == next) return
        pressed = next
        animator?.cancel()
        animator = ValueAnimator.ofFloat(pressProgress, if (next) 1f else 0f).apply {
            duration = if (next) LiquidGlassStyle.PRESS_IN_MS else LiquidGlassStyle.PRESS_OUT_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val native = original ?: return@addUpdateListener
                pressProgress = it.animatedValue as Float
                val scale = 1f + (LiquidGlassStyle.PRESS_SCALE - 1f) * pressProgress
                page.slot.scaleX = native.scaleX * scale
                page.slot.scaleY = native.scaleY * scale
                panel.setInteraction(pressProgress, pressX)
            }
            if (ValueAnimator.areAnimatorsEnabled()) start() else end()
        }
    }

    fun onScrolled(dy: Int) {
        if (disposed || autoHideFailed || panel.isCapturing || original == null ||
            page.row.visibility != View.VISIBLE || page.underlay.visibility != View.VISIBLE || page.isEditorShown()) return
        try {
            autoHide?.onScroll(dy)
        } catch (failure: Exception) {
            autoHideFailed = true
            autoHide?.reset()
            PostReplyScrollDispatcher.unbind(page.recycler, this)
            layoutDirty = true
            refresh()
            XposedCompat.logW("[PostReplyLiquidGlassHook] page scroll collapse disabled: ${failure.message}")
        }
    }

    private fun onAutoHideChanged() {
        if (disposed) return
        if (autoHide?.suspended == true) {
            animator?.cancel()
            animator = null
            pressed = false
            pressProgress = 0f
            original?.let { page.slot.scaleX = it.scaleX; page.slot.scaleY = it.scaleY }
            panel.setInteraction(0f, 0f)
            panel.visibility = View.INVISIBLE
            releaseBackdropLayer()
            comments?.restore()
            comments = null
            layoutDirty = true
        } else {
            if (panel.visibility != View.VISIBLE) layoutDirty = true
            refresh()
            page.root.invalidate()
        }
    }

    private fun restore() {
        autoHide?.reset()
        releaseBackdropLayer()
        if (panel.visibility != View.GONE) panel.visibility = View.GONE
        removeLoadingGuard()
        comments?.restore()
        comments = null
        bounds = null
        layoutDirty = true
        val native = original ?: return
        animator?.cancel()
        animator = null
        pressed = false
        pressProgress = 0f
        panel.setInteraction(0f, 0f)
        page.underlay.translationY -= lift
        page.slot.translationX -= horizontalShift
        lift = 0f
        horizontalShift = 0f
        page.slot.scaleX = native.scaleX
        page.slot.scaleY = native.scaleY
        page.slot.layoutParams = native.slotLayout
        page.row.layoutParams = native.rowLayout
        page.input.layoutParams = native.inputLayout
        for ((action, layout) in native.actionLayouts) action.layoutParams = layout
        for (content in native.actionContent.values) content.restore()
        page.row.gravity = native.rowGravity
        page.input.gravity = native.inputGravity
        setPadding(page.row, native.rowPadding)
        setPadding(page.input, native.inputPadding)
        for ((group, clip) in native.clips) {
            if (group.clipChildren != clip.first) group.clipChildren = clip.first
            if (group.clipToPadding != clip.second) group.clipToPadding = clip.second
        }
        for ((view, background) in native.backgrounds) {
            if (view.background == null) view.background = background
        }
        original = null
    }

    private fun releaseBackdropLayer() {
        if (!ownsBackdropLayer) return
        ownsBackdropLayer = false
        if (page.recycler.layerType == View.LAYER_TYPE_HARDWARE) {
            page.recycler.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        if (autoHide != null) PostReplyScrollDispatcher.unbind(page.recycler, this)
        if (page.root.viewTreeObserver.isAlive) page.root.viewTreeObserver.apply {
            removeOnPreDrawListener(this@PostReplyGlassSession)
            removeOnGlobalLayoutListener(this@PostReplyGlassSession)
            removeOnScrollChangedListener(this@PostReplyGlassSession)
            removeOnWindowFocusChangeListener(this@PostReplyGlassSession)
        }
        restore()
        (panel.parent as? ViewGroup)?.removeView(panel)
    }

    private fun disable() {
        dispose()
        onUnavailable()
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        fun padding(view: View) = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        fun setPadding(view: View, values: IntArray) = view.setPadding(values[0], values[1], values[2], values[3])
    }
}
