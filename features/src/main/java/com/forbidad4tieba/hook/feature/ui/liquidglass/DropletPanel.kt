package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.symbol.contract.BottomTabArtworkContract
import java.lang.ref.WeakReference

/** A painted selection pill, or an independent lens over the page and inner tab backdrop. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal class DropletPanel(
    context: Context,
    backdrop: LiquidGlassPanel,
    tabRow: ViewGroup,
    private val density: Float,
    private var night: Boolean,
    private val config: BottomTabLiquidGlassConfig = BottomTabLiquidGlassConfig.DEFAULT,
) : View(context) {
    private val panelRef = WeakReference(backdrop)
    private val rowRef = WeakReference(tabRow)
    private val renderer = if (config.liquidGlassEnabled) LiquidGlassRenderer(selectionLens = true) else null
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionClip = Path()
    private val screenToLens = Matrix()
    private val rowToScreen = Matrix()
    private val lensCentre = FloatArray(2)
    private var selectedTabIndex = -1
    private var artwork: List<BottomTabArtworkContract.Artwork> = emptyList()
    private var pressProgress = 0f
    private var barScale = 1f
    private var artworkCentreY = 0f
    private var frame = 0L
    private var ready = false

    val isSupported: Boolean get() = renderer?.isAvailable != false

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (config.liquidGlassEnabled) backdrop.setDependent(this)
        ready = true
    }

    fun setTheme(value: Boolean) {
        if (night == value) return
        night = value
        updateArtworkColor()
        invalidate()
    }

    fun setSelectedTabIndex(index: Int) {
        if (selectedTabIndex == index) return
        selectedTabIndex = index
        updateArtworkColor()
        invalidate()
    }

    fun refresh() { invalidate() }

    fun setArtwork(value: List<BottomTabArtworkContract.Artwork>) {
        if (value.size == artwork.size && value.indices.all {
                value[it].content === artwork[it].content && value[it].label === artwork[it].label
            }) return
        artwork = value
        updateArtworkColor()
        invalidate()
    }

    fun setInteraction(progress: Float, barScale: Float, artworkCentreY: Float) {
        if (pressProgress == progress && this.barScale == barScale && this.artworkCentreY == artworkCentreY) return
        val changedActivity = (pressProgress > 0f) != (progress > 0f)
        pressProgress = progress
        this.barScale = barScale
        this.artworkCentreY = artworkCentreY
        if (changedActivity) updateLightSource()
        invalidate()
    }

    private fun updateLightSource() {
        if (!ready) return
        if (pressProgress > 0f && config.liquidGlassEnabled && isAttachedToWindow && isShown &&
            windowVisibility == VISIBLE && hasWindowFocus() && isSupported) {
            LiquidGlassLightSource.register(this)
        } else LiquidGlassLightSource.unregister(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLightSource()
    }

    override fun onDetachedFromWindow() {
        LiquidGlassLightSource.unregister(this)
        renderer?.release()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateLightSource()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLightSource()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateLightSource()
        if (hasWindowFocus) invalidate()
    }

    private val record: (Canvas, Float, Int, FloatArray) -> Unit = { canvas, padding, _, _ ->
        val panel = panelRef.get()
        val row = rowRef.get()
        if (panel != null && row != null) {
            val save = canvas.save()
            canvas.translate(padding, padding)
            // Undo the lens's visual expansion when sampling screen pixels. Scaling the entire
            // backdrop with the lens magnifies the host artwork and hides the clear outer edge.
            canvas.concat(screenToLens)
            if (config.liquidGlassEnabled) {
                panel.drawPageForDroplet(canvas)
                val material = canvas.save()
                val backdropScale = LiquidGlassStyle.tabBackdropScale(pressProgress)
                canvas.scale(backdropScale, backdropScale, lensCentre[0], lensCentre[1])
                panel.drawTabBackdrop(canvas)
                canvas.restoreToCount(material)
                panel.clipTabBackdrop(canvas)
            }
            rowToScreen.reset()
            row.transformMatrixToGlobal(rowToScreen)
            canvas.concat(rowToScreen)
            canvas.scale(1f / barScale, 1f / barScale, row.pivotX, row.pivotY)
            canvas.translate(-row.scrollX.toFloat(), -row.scrollY.toFloat())
            val artworkScale = 1f + (LiquidGlassStyle.ARTWORK_SCALE - 1f) * pressProgress
            panel.capture {
                for (index in 0 until row.childCount) {
                    val tab = row.getChildAt(index)
                    if (tab.visibility != VISIBLE) continue
                    val content = artwork.getOrNull(index)?.content ?: continue
                    val child = if (tab.alpha < 1f && content !== tab) {
                        canvas.saveLayerAlpha(null, (tab.alpha * 255f).toInt().coerceIn(0, 255))
                    } else canvas.save()
                    canvas.translate(tab.left.toFloat(), tab.top.toFloat())
                    canvas.concat(tab.matrix)
                    canvas.scale(artworkScale, artworkScale, tab.width / 2f, artworkCentreY - tab.top)
                    if (content === tab) {
                        drawArtwork(canvas, tab, tinted = true)
                    } else if (tab is ViewGroup && content.parent === tab) {
                        if (tab.clipToPadding) canvas.clipRect(tab.paddingLeft, tab.paddingTop,
                            tab.width - tab.paddingRight, tab.height - tab.paddingBottom)
                        canvas.translate(-tab.scrollX.toFloat(), -tab.scrollY.toFloat())
                        // The reference colors its icon/text content. Host unread badges are
                        // separate siblings and must not enter that monochrome layer.
                        for (i in 0 until tab.childCount) {
                            val item = tab.getChildAt(i)
                            if (item.visibility != VISIBLE || item.alpha <= 0f) continue
                            val layer = canvas.save()
                            canvas.translate(item.left.toFloat(), item.top.toFloat())
                            canvas.concat(item.matrix)
                            if (tab.clipChildren) canvas.clipRect(0, 0, item.width, item.height)
                            drawArtwork(canvas, item, tinted = item === content)
                            canvas.restoreToCount(layer)
                        }
                    }
                    canvas.restoreToCount(child)
                }
            }
            canvas.restoreToCount(save)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || selectedTabIndex < 0) return
        screenToLens.reset()
        transformMatrixToGlobal(screenToLens)
        lensCentre[0] = width / 2f
        lensCentre[1] = height / 2f
        screenToLens.mapPoints(lensCentre)
        if (!screenToLens.invert(screenToLens)) return
        val glass = renderer
        if (glass == null) {
            val radius = minOf(width, height) / 2f
            selectionClip.reset()
            selectionClip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(selectionClip)
            canvas.drawPaint(selectionPaint)
            record(canvas, 0f, 1, lensCentre)
            canvas.restoreToCount(save)
            return
        }
        if (!canvas.isHardwareAccelerated) return
        val params = LiquidGlassStyle.params(width, height, density, night, config, droplet = true,
            press = pressProgress,
            lightAngle = LiquidGlassLightSource.angle)
        // Translation changes which icon pixels enter the lens. Only this small composite is
        // rerecorded; a stationary page is not captured again during selection or gravity updates.
        glass.draw(canvas, width, height, params, ++frame, record)
    }

    private fun updateArtworkColor() {
        val row = rowRef.get() ?: return
        val selected = (0 until row.childCount).firstOrNull { row.getChildAt(it).isSelected } ?: return
        val color = artwork.getOrNull(selected)?.label?.currentTextColor ?: return
        artworkPaint.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
        selectionPaint.color = (color and 0xFFFFFF) or (38 shl 24)
    }

    private fun drawArtwork(canvas: Canvas, view: View, tinted: Boolean) {
        val layer = when {
            tinted -> {
                artworkPaint.alpha = (view.alpha * 255f).toInt().coerceIn(0, 255)
                canvas.saveLayer(null, artworkPaint)
            }
            view.alpha < 1f -> canvas.saveLayerAlpha(null, (view.alpha * 255f).toInt().coerceIn(0, 255))
            else -> canvas.save()
        }
        view.draw(canvas)
        canvas.restoreToCount(layer)
    }
}
