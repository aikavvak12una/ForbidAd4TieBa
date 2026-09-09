package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The selection droplet, reproducing KernelSU's floating-bar indicator.
 *
 * Ported from liuran001/WeChat-LiquidGlass (MIT), `DropletPanel`:
 *
 * ```
 * drawBackdrop(
 *     backdrop = combinedBackdrop,          // page + a scaled copy of the tabs
 *     effects  = { lens(refractionHeight = 10.dp * p, refractionAmount = 14.dp * p,
 *                       depthEffect = true, chromaticAberration = 0.5f) },
 *     onDrawSurface = { drawRect(black @ 0.1f, alpha = 1 - p); drawRect(black @ 0.03f * p) })
 * .innerShadow { InnerShadow(radius = 8.dp * p, black @ 0.15f, alpha = p) }
 * ```
 *
 * At rest (`progress == 0`) the lens vanishes while the flat wash and selected tab copy remain, so
 * the chosen tab still has a visible droplet at rest. While held, the backdrop is the page *plus*
 * the tab row drawn again slightly enlarged - that scaled copy is what makes the icon under the
 * droplet read as magnified and bent, and it is why the droplet sits above the real tabs rather than
 * below them.
 */
internal class DropletPanel(
    context: Context,
    backdrop: LiquidGlassPanel,
    tabRow: ViewGroup,
    private val density: Float,
    night: Boolean,
    private val config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) : View(context) {

    private companion object {
        /** KernelSU: refractionHeight = 10.dp * progress. */
        const val REFRACTION_DP = 10f
        /** KernelSU: refractionAmount = 14.dp * progress. */
        const val AMOUNT_DP = 14f
        /** KernelSU: chromaticAberration = 0.5f. */
        const val ABERRATION = 0.5f
        const val ACTIVE_PROGRESS_THRESHOLD = 0.01f

        /** KernelSU: `LocalFloatingBottomBarTabScale = lerp(1f, 1.2f, progress)`. */
        const val TAB_ZOOM = 0.2f

        /** Depth of the tab tree walked when collecting badge bounds. */
        const val BADGE_SEARCH_DEPTH = 4

        val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
""" + LiquidGlassPanel.SDF_SOURCE + """
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) { return content.eval(coord); }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
            + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration
            * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5; color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5; color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0; color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
    return color;
}
"""

    }

    private val tabRowRef = WeakReference(tabRow)
    private var pillRef = WeakReference(backdrop)

    private val pad = (AMOUNT_DP * density).roundToInt() + (density * 4f).roundToInt()

    private val node = RenderNode("tbDroplet")
    private val clearNode = RenderNode("tbDropletClear")
    /** Recorded once: both materials keep the same enlarged, untinted badges and tab content. */
    private val tabsNode = RenderNode("tbDropletTabs")
    /** First layer of KernelSU's CombinedBackdrop: the original, unfiltered page. */
    private val backdropNode = RenderNode("tbDropletBackdrop")
    /** RenderEffect snapshots uniforms. Reuse only while progress AND geometry are unchanged. */
    private var lensEffect: RenderEffect? = null
    private var lensWidth = 0
    private var lensHeight = 0
    private var lensProgress = Float.NaN
    private var lens: RuntimeShader? = null
    private var lighting: LiquidGlassLighting? = null
    private var lightAngle = LiquidGlassTilt.REST_ANGLE
    private val innerNode = RenderNode("tbDropletInnerShadow")
    private var innerRadius = Float.NaN
    private var innerWidth = 0
    private var innerHeight = 0

    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressTint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 }
    private val innerClear = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val clip = Path()
    private val pillClip = Path()
    private val overflowClip = Path()
    private val pillMatrix = Matrix()
    private val dropletMatrix = Matrix()
    private val inverseMatrix = Matrix()

    // Reused across draws rather than allocated per frame.
    private val tmp = IntArray(2)
    private val selfPos = IntArray(2)
    private val srcPos = IntArray(2)
    private val captureScale = FloatArray(2)
    private val badges = ArrayList<RectF>(4)
    private var badgeCount = 0
    private val badgeClip = Path()

    private var accentCache = 0
    /** Press/drag progress. A value of zero is still a valid resting selection state. */
    private var progress = 0f
    /** Hint supplied by the controller so the resting layer survives host redraw ordering. */
    private var selectedTabIndex = -1

    var isSupported: Boolean = false
        private set

    init {
        isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (isSupported) {
            try {
                if (config.refractionEnabled) lens = RuntimeShader(LENS_SHADER)
                if (config.highlightEnabled) lighting = LiquidGlassLighting(density, (PI / 2).toFloat())
            } catch (t: Throwable) {
                isSupported = false
                XposedCompat.log("[DropletPanel] droplet shader rejected: ${t.message}")
                XposedCompat.log(t)
            }
        }
        setTheme(night)
        pressTint.color = 0x08000000
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    /** The glass pill, redrawn into the droplet's backdrop as KernelSU does. */
    fun setPill(pill: LiquidGlassPanel) {
        pillRef = WeakReference(pill)
        pill.setLightListener { angle ->
            lightAngle = angle
            if (progress > ACTIVE_PROGRESS_THRESHOLD) invalidate()
        }
    }

    fun setTheme(isNight: Boolean) {
        wash.color = if (isNight) 0x1AFFFFFF else 0x1A000000
        invalidate()
    }

    /** Press progress, 0..1, driven by the controller's spring. */
    fun setProgress(p: Float) {
        val next = p.coerceIn(0f, 1f)
        if (progress != next) {
            val wasActive = progress > ACTIVE_PROGRESS_THRESHOLD
            progress = next
            if (wasActive && next <= ACTIVE_PROGRESS_THRESHOLD) {
                // The static path below does not use the transient display lists. Discard them
                // when the spring reaches rest so a stale pressed frame cannot be composited a
                // few pixels above the current tab after an idle layout/display-list refresh.
                clearTransientNodes()
            }
            invalidate()
        }
    }

    /**
     * Publishes the tab that owns the resting selection layer.
     *
     * The host can redraw its row without changing the press spring (which is normally already at
     * zero). Keeping this small piece of state separate means a settled selection still invalidates
     * the droplet and never relies on a gesture to make the layer appear.
     */
    fun setSelectedTabIndex(index: Int) {
        val next = index.coerceAtLeast(-1)
        if (selectedTabIndex == next) return
        selectedTabIndex = next
        if (progress <= ACTIVE_PROGRESS_THRESHOLD) clearTransientNodes()
        invalidate()
    }

    /**
     * Re-captures the backdrop.
     *
     * `translationX` moves the view on the render thread without redrawing it, so without this the
     * refracted content freezes at whatever was underneath when the press began.
     */
    fun refresh() {
        if (progress > ACTIVE_PROGRESS_THRESHOLD || selectedTabIndex >= 0) invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        val r = min(w, h) * 0.5f
        clip.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val radius = min(w, h) * 0.5f
        val p = progress
        val selected = selectedTab(tabRowRef.get())
        // A zero-progress frame is the normal resting state, not an empty state. Only skip the view
        // when there is genuinely no selected tab to paint (for example while the host is rebuilding
        // its row).
        if (p <= ACTIVE_PROGRESS_THRESHOLD && selected == null) return

        var drewLens = false
        if (isSupported && p > ACTIVE_PROGRESS_THRESHOLD && canvas.isHardwareAccelerated) {
            try {
                drewLens = drawLens(canvas, w, h, radius, p)
            } catch (t: Throwable) {
                isSupported = false
                XposedCompat.log("[DropletPanel] droplet lens failed: ${t.message}")
                XposedCompat.log(t)
            }
        }

        // When the lens is active these surface tints are already inside its backdrop, below the
        // clear tab copy. The fallback path keeps the same order by repainting the tab afterwards.
        if (!drewLens) {
            drawSurfaceTints(canvas, 0f, 0f, w.toFloat(), h.toFloat(), radius, p)
            drawRestingTab(canvas, selected)
        }
        if (p > 0f) {
            lighting?.draw(canvas, w, h, lightAngle, p)
            if (config.shadowEnabled) drawInnerShadow(canvas, w, h, p)
        }
    }

    /** KernelSU's blurred, downward-offset cutout, not a symmetric painted rim. */
    private fun drawInnerShadow(canvas: Canvas, w: Int, h: Int, p: Float) {
        val blur = 8f * density * p
        if (blur <= 0.01f || !canvas.isHardwareAccelerated) return
        if (!innerNode.hasDisplayList() || blur != innerRadius || w != innerWidth || h != innerHeight) {
            innerNode.setPosition(0, 0, w, h)
            val c = innerNode.beginRecording(w, h)
            c.clipPath(clip)
            c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h * 0.5f, h * 0.5f, innerShadow)
            c.translate(0f, blur)
            c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h * 0.5f, h * 0.5f, innerClear)
            innerNode.endRecording()
            innerNode.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL))
            innerRadius = blur
            innerWidth = w
            innerHeight = h
        }
        innerNode.setAlpha(p)
        val save = canvas.save()
        canvas.clipPath(clip)
        canvas.drawRenderNode(innerNode)
        canvas.restoreToCount(save)
    }

    /** Draws the resting wash and press tint below the tab content. */
    private fun drawSurfaceTints(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        p: Float,
    ) {
        val restingAlpha = 0x1A
        val washAlpha = (restingAlpha * (1f - p)).roundToInt()
        if (washAlpha > 0) {
            wash.alpha = washAlpha
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, wash)
        }
        val pressAlpha = (0x08 * p).roundToInt()
        if (pressAlpha > 0) {
            pressTint.alpha = pressAlpha
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, pressTint)
        }
    }

    /**
     * Repaints the selected tab above the resting capsule.
     *
     * Without this the capsule would sit over the host's own selected icon and swallow its colour.
     */
    private fun drawRestingTab(canvas: Canvas, selected: View?) {
        tabRowRef.get() ?: return
        val tab = selected ?: return
        if (tab.visibility != VISIBLE) return
        LiquidGlassGeom.unscaledScreenPos(this, selfPos)
        LiquidGlassGeom.unscaledScreenPos(tab, srcPos)
        val save = canvas.save()
        // Keep a fallback/static copy inside the droplet's own rounded bounds. This also prevents a
        // stale translated child display list from leaking through a corner during a host relayout.
        canvas.clipPath(clip)
        // Press progress can reach zero before the scale springs settle. Keep the resting copy
        // fixed in screen space through those final frames as well as through the lens frames.
        LiquidGlassGeom.cumulativeScale(this, captureScale)
        canvas.scale(1f / captureScale[0], 1f / captureScale[1], width * 0.5f, height * 0.5f)
        canvas.translate((srcPos[0] - selfPos[0]).toFloat(), (srcPos[1] - selfPos[1]).toFloat())
        tab.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun selectedTab(row: ViewGroup?): View? {
        row ?: return null
        if (selectedTabIndex >= 0) {
            val hinted = row.getChildAt(selectedTabIndex)
            // The hint is only a fast path. During a host tab switch the controller can publish
            // the target index one traversal before the host flips isSelected; drawing that
            // unselected child here would briefly duplicate the wrong icon in the resting droplet.
            if (hinted != null && hinted.visibility == VISIBLE && hinted.isSelected) return hinted
        }
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.isSelected) return tab
        }
        return null
    }

    private fun clearTransientNodes() {
        node.setRenderEffect(null)
        node.discardDisplayList()
        clearNode.setRenderEffect(null)
        clearNode.discardDisplayList()
        tabsNode.discardDisplayList()
        backdropNode.discardDisplayList()
        innerNode.setRenderEffect(null)
        innerNode.discardDisplayList()
        innerRadius = Float.NaN
    }

    override fun onDetachedFromWindow() {
        clearTransientNodes()
        super.onDetachedFromWindow()
    }

    /** Reuses the same unfiltered page band as both glass surfaces in this frame. */
    private fun recordBackdrop(nw: Int, nh: Int): Boolean {
        val pill = pillRef.get() ?: return false
        backdropNode.setPosition(0, 0, nw, nh)
        val c = backdropNode.beginRecording(nw, nh)
        try {
            return pill.drawBackdrop(c, selfPos[0], selfPos[1], pad)
        } finally {
            backdropNode.endRecording()
        }
    }

    /**
     * The page and enlarged tabs are shared by both materials. The blurred pill is included only
     * for the part of the final lens that overlaps the visible bar: clipping it before refraction
     * alone lets the lens pull that blur back out across the bar's edge while held.
     */
    private fun paintBackdrop(
        c: Canvas,
        nw: Int,
        nh: Int,
        p: Float,
        viewScaleX: Float,
        viewScaleY: Float,
        includePill: Boolean,
    ) {
        val row = tabRowRef.get()
        // Compensate only the background; the lens itself grows with the selection.
        val pageSave = c.save()
        if (abs(viewScaleX - 1f) > 0.001f || abs(viewScaleY - 1f) > 0.001f) {
            c.scale(1f / viewScaleX, 1f / viewScaleY, nw * 0.5f, nh * 0.5f)
        }
        c.drawRenderNode(backdropNode)
        c.restoreToCount(pageSave)

        val radius = (nh - pad * 2) * 0.5f

        if (row == null) return

        // The second backdrop contains the original pill's blur, vibrancy and surface wash.
        val pill = pillRef.get()
        if (includePill && pill != null) {
            LiquidGlassGeom.unscaledScreenPos(row, srcPos)
            val save = c.save()
            if (abs(viewScaleX - 1f) > 0.001f || abs(viewScaleY - 1f) > 0.001f) {
                c.scale(1f / viewScaleX, 1f / viewScaleY, nw * 0.5f, nh * 0.5f)
            }
            c.translate((pad - (selfPos[0] - srcPos[0])).toFloat(), (pad - (selfPos[1] - srcPos[1])).toFloat())
            LiquidGlassGeom.unscaledScreenPos(pill, tmp)
            val ps = c.save()
            c.translate((tmp[0] - srcPos[0]).toFloat(), (tmp[1] - srcPos[1]).toFloat())
            pill.drawEmbedded(c)
            c.restoreToCount(ps)
            c.restoreToCount(save)
        }

        // These tints belong to the surface, not to the icon/text. Keeping them below the tab copy
        // removes the press-dark / release-light colour jump.
        drawSurfaceTints(
            c,
            pad.toFloat(),
            pad.toFloat(),
            (nw - pad).toFloat(),
            (nh - pad).toFloat(),
            radius,
            p,
        )

        c.drawRenderNode(tabsNode)
    }

    /** KernelSU's tabsBackdrop, shared without drawing the host tab tree twice per frame. */
    private fun recordTabs(nw: Int, nh: Int, p: Float, viewScaleX: Float, viewScaleY: Float) {
        tabsNode.setPosition(0, 0, nw, nh)
        val c = tabsNode.beginRecording(nw, nh)
        try {
            val row = tabRowRef.get() ?: return
            LiquidGlassGeom.unscaledScreenPos(row, srcPos)
            paintTabs(c, row, nw, nh, p, viewScaleX, viewScaleY)
        } finally {
            tabsNode.endRecording()
        }
    }

    private fun paintTabs(
        c: Canvas,
        row: ViewGroup,
        nw: Int,
        nh: Int,
        p: Float,
        viewScaleX: Float,
        viewScaleY: Float,
    ) {
        val save = c.save()
        if (abs(viewScaleX - 1f) > 0.001f || abs(viewScaleY - 1f) > 0.001f) {
            c.scale(1f / viewScaleX, 1f / viewScaleY, nw * 0.5f, nh * 0.5f)
        }
        c.translate((pad - (selfPos[0] - srcPos[0])).toFloat(), (pad - (selfPos[1] - srcPos[1])).toFloat())
        // Each tab scales about its OWN centre, exactly as KernelSU does. Scaling the whole row
        // about one point instead shoves distant tabs outward and blows the nearby one up.
        val scale = 1f + TAB_ZOOM * p
        val accent = accentColour(row)
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility != VISIBLE) continue
            val ts = c.save()
            c.scale(scale, scale, tab.left + tab.width * 0.5f, tab.top + tab.height * 0.5f)
            c.translate(tab.left.toFloat(), tab.top.toFloat())
            drawTab(c, tab, accent)
            c.restoreToCount(ts)
        }
        c.restoreToCount(save)
    }

    /**
     * Draws one tab with KernelSU's `LocalContentColor` applied.
     *
     * The tint has to be a single layer over the tab's own `draw()`. Walking down to leaf views and
     * tinting those instead looks equivalent but silently drops anything a *container* paints for
     * itself - no group ever gets drawn, only its children - and that is where the host keeps the
     * unread bubble. Whatever carries its own colour is repainted on top, untinted, so the badge
     * stays red instead of going accent-coloured with the rest of the tab.
     */
    private fun drawTab(c: Canvas, tab: View, accent: Int) {
        if (tab.visibility != VISIBLE || tab.width <= 0 || tab.height <= 0) return
        if (tab.isSelected) {
            // The selected tab is already painted in the accent colour by the host, so the tint has
            // nothing to add. The tint is for the tabs the droplet is sliding towards.
            tab.draw(c)
            return
        }
        val w = tab.width
        val h = tab.height
        // The badge hangs off the icon's top-right and can reach past the tab's own bounds, so the
        // layer is grown rather than clipped to them.
        val grow = h * 0.5f
        val layer = c.saveLayer(-grow, -grow, w + grow, h + grow, null)
        tab.draw(c)
        accentPaint.color = accent
        c.drawRect(-grow, -grow, w + grow, h + grow, accentPaint)
        c.restoreToCount(layer)

        if (tab !is ViewGroup) return
        badgeCount = 0
        collectBadges(tab, 0f, 0f, 0)
        if (badgeCount == 0) return
        val save = c.save()
        badgeClip.reset()
        for (i in 0 until badgeCount) {
            val r = badges[i]
            val rr = r.height() * 0.5f
            badgeClip.addRoundRect(r, rr, rr, Path.Direction.CW)
        }
        c.clipPath(badgeClip)
        // Drawing the badge view itself renders nothing when its parent paints the bubble, so the
        // tab is redrawn whole and clipped to the badge capsule instead.
        tab.draw(c)
        c.restoreToCount(save)
    }

    /**
     * True for views that own their colour and must survive the accent tint.
     *
     * The content colour is meant to reach the icon and the label; the unread bubble is styled in
     * its own right and would read as an accent blob if it were tinted along with them. The host's
     * badge view is a stable, unobfuscated class, so it can be named directly.
     */
    private fun ownsItsColour(view: View): Boolean {
        var clazz: Class<*>? = view.javaClass
        var depth = 0
        while (clazz != null && depth < 8) {
            if (clazz.name == StableTiebaHookPoints.MESSAGE_RED_DOT_VIEW_CLASS) return true
            clazz = clazz.superclass
            depth++
        }
        // A TextView with its own background is drawing a chip rather than a label.
        return view is TextView && view.background != null
    }

    private fun addBadge(l: Float, t: Float, r: Float, b: Float) {
        if (badgeCount == badges.size) badges.add(RectF())
        badges[badgeCount++].set(l, t, r, b)
    }

    /** Collects the bounds of everything in the tab that owns its colour. */
    private fun collectBadges(parent: ViewGroup, ox: Float, oy: Float, depth: Int) {
        if (depth > BADGE_SEARCH_DEPTH) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            if (child.visibility != VISIBLE || child.width <= 0 || child.height <= 0) continue
            // Some badges are centred with translationY rather than relaid out, so the clip has to
            // follow where they are actually drawn.
            val cx = ox + child.left + child.translationX
            val cy = oy + child.top + child.translationY
            if (child is ViewGroup && child.childCount > 0) {
                collectBadges(child, cx, cy, depth + 1)
            } else if (ownsItsColour(child) && !child.willNotDraw()) {
                addBadge(cx, cy, cx + child.width, cy + child.height)
            }
        }
    }

    /**
     * The host's selected-tab colour, read off whichever tab is currently selected so it follows
     * the app's own theme rather than being hard-coded.
     */
    private fun accentColour(row: ViewGroup): Int {
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (!tab.isSelected) continue
            val c = firstLabelColour(tab, 0)
            // Reject near-white / near-black: before the selection settles this reads an
            // *unselected* label, and caching that would tint the whole copy grey.
            if (c != 0 && !isNeutral(c)) {
                accentCache = c
                return c
            }
        }
        return if (accentCache != 0) accentCache else 0xFF4E6EF2.toInt()
    }

    private fun isNeutral(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return max(r, max(g, b)) - min(r, min(g, b)) < 24
    }

    private fun firstLabelColour(view: View, depth: Int): Int {
        if (depth > 4 || view.visibility != VISIBLE) return 0
        if (view is TextView) {
            val text = view.text
            return if (view.background == null && text != null && text.isNotEmpty()) {
                view.currentTextColor or 0xFF000000.toInt()
            } else {
                0
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val c = firstLabelColour(view.getChildAt(i) ?: continue, depth + 1)
                if (c != 0) return c
            }
        }
        return 0
    }

    private fun drawLens(canvas: Canvas, w: Int, h: Int, radius: Float, p: Float): Boolean {
        val pill = pillRef.get() ?: return false
        val shader = lens

        val nw = w + pad * 2
        val nh = h + pad * 2
        node.setPosition(0, 0, nw, nh)

        // getLocationOnScreen() reports the *scaled* position - the droplet is blown up while held
        // - but this canvas is in unscaled local coordinates.
        LiquidGlassGeom.unscaledScreenPos(this, selfPos)
        LiquidGlassGeom.cumulativeScale(this, captureScale)
        if (!recordBackdrop(nw, nh)) return false
        recordTabs(nw, nh, p, captureScale[0], captureScale[1])

        // Use the bar's drawn outline, including its own press growth and the droplet's independent
        // translation/stretch. Layout bounds alone do not describe where these two surfaces meet.
        pillMatrix.reset()
        pill.transformMatrixToGlobal(pillMatrix)
        dropletMatrix.reset()
        transformMatrixToGlobal(dropletMatrix)
        if (!dropletMatrix.invert(inverseMatrix)) return false
        pillMatrix.postConcat(inverseMatrix)
        pillClip.reset()
        val pillRadius = min(pill.width, pill.height) * 0.5f
        pillClip.addRoundRect(0f, 0f, pill.width.toFloat(), pill.height.toFloat(),
            pillRadius, pillRadius, Path.Direction.CW)
        pillClip.transform(pillMatrix)
        check(overflowClip.op(clip, pillClip, Path.Op.DIFFERENCE))
        val hasOverflow = !overflowClip.isEmpty

        val rc: RecordingCanvas = node.beginRecording(nw, nh)
        try {
            paintBackdrop(rc, nw, nh, p, captureScale[0], captureScale[1], includePill = true)
        } finally {
            node.endRecording()
        }
        if (hasOverflow) {
            clearNode.setPosition(0, 0, nw, nh)
            val clearCanvas = clearNode.beginRecording(nw, nh)
            try {
                paintBackdrop(clearCanvas, nw, nh, p, captureScale[0], captureScale[1], includePill = false)
            } finally {
                clearNode.endRecording()
            }
        }

        if (shader != null && (lensEffect == null || lensWidth != w || lensHeight != h || lensProgress != p)) {
            shader.setFloatUniform("size", w.toFloat(), h.toFloat())
            shader.setFloatUniform("offset", -pad.toFloat(), -pad.toFloat())
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            shader.setFloatUniform("refractionHeight", REFRACTION_DP * density * p)
            shader.setFloatUniform("refractionAmount", -AMOUNT_DP * density * p)
            shader.setFloatUniform("depthEffect", 1f)
            shader.setFloatUniform("chromaticAberration", ABERRATION)
            lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
            lensWidth = w
            lensHeight = h
            lensProgress = p
        }
        node.setRenderEffect(lensEffect)

        val save = canvas.save()
        canvas.clipPath(clip)
        if (hasOverflow) {
            clearNode.setRenderEffect(lensEffect)
            val overflowSave = canvas.save()
            canvas.clipPath(overflowClip)
            canvas.translate(-pad.toFloat(), -pad.toFloat())
            canvas.drawRenderNode(clearNode)
            canvas.restoreToCount(overflowSave)
            canvas.clipPath(pillClip)
        }
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
        return true
    }
}
