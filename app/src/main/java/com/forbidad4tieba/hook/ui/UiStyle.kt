package com.forbidad4tieba.hook.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassDynamicTintCache

/**
 * 样式入口，集中管理亮暗模式色盘、对话框容器背景、进度条和轻量动效。
 *
 * 这些逻辑运行在 UI 事件路径上，比如 `onShow`、点击和扫描完成，不参与 hook 回调。
 * 每次按当前 `Configuration` 计算 tokens，避免跨 Activity 切换主题后状态过期。
 */
internal object UiStyle {
    private const val CARD_CORNER_DP = 22f
    private const val CARD_INSET_DP = 14f

    internal class Tokens(
        val dialogInsetPx: Int,
        val cardCornerPx: Float,
        val night: Boolean,
        val surface: Int,
        val surfaceAlt: Int,
        val surfaceRaised: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textMuted: Int,
        val divider: Int,
        val accent: Int,
        val accentSoft: Int,
        val accentTrackOn: Int,
        val accentTrackOff: Int,
        val accentThumbOff: Int,
        val danger: Int,
        val warning: Int,
        val success: Int,
        val inputFill: Int,
        val inputStroke: Int,
        val metricBgActive: Int,
        val metricBgIdle: Int,
        val metricStrokeActive: Int,
        val metricStrokeIdle: Int,
        val progressTrack: Int,
        val chartBg: Int,
        val chartAxis: Int,
        val chartGrid: Int,
        val chartBar: Int,
        val chartBarHighlight: Int,
    )

    internal fun tokens(context: Context): Tokens {
        val density = context.resources.displayMetrics.density
        val base = if (isNight(context)) darkTokens(density) else lightTokens(density)
        return if (ConfigManager.isHomeNativeGlassEnabled) {
            base.withHomeNativeGlassOverrides(density)
        } else {
            base
        }
    }

    private fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun lightTokens(density: Float): Tokens = Tokens(
        dialogInsetPx = (CARD_INSET_DP * density).toInt(),
        cardCornerPx = CARD_CORNER_DP * density,
        night = false,
        surface = 0xFFFFFFFF.toInt(),
        surfaceAlt = 0xFFF4F6FA.toInt(),
        surfaceRaised = 0xFFFAFBFD.toInt(),
        textPrimary = 0xFF14171C.toInt(),
        textSecondary = 0xB3161A22.toInt(),
        textMuted = 0x73161A22,
        divider = 0x14000000,
        accent = 0xFF4C87F7.toInt(),
        accentSoft = 0xFFEAF2FF.toInt(),
        accentTrackOn = 0x704C87F7,
        accentTrackOff = 0x33000000,
        accentThumbOff = 0xFFFAFAFA.toInt(),
        danger = 0xFFD53F43.toInt(),
        warning = 0xFFE08A3C.toInt(),
        success = 0xFF2E9E6B.toInt(),
        inputFill = 0xFFF7F9FC.toInt(),
        inputStroke = 0x334C87F7,
        metricBgActive = 0xFFEAF2FF.toInt(),
        metricBgIdle = 0xFFF5F7FB.toInt(),
        metricStrokeActive = 0x664C87F7,
        metricStrokeIdle = 0x244C87F7,
        progressTrack = 0x18000000,
        chartBg = 0xFFF7F9FC.toInt(),
        chartAxis = 0xFFDCE3EC.toInt(),
        chartGrid = 0xFFE8EDF4.toInt(),
        chartBar = 0x554C87F7,
        chartBarHighlight = 0xCC4C87F7.toInt(),
    )

    private fun darkTokens(density: Float): Tokens = Tokens(
        dialogInsetPx = (CARD_INSET_DP * density).toInt(),
        cardCornerPx = CARD_CORNER_DP * density,
        night = true,
        surface = 0xFF17181B.toInt(),
        surfaceAlt = 0xFF1D1F23.toInt(),
        surfaceRaised = 0xFF22252A.toInt(),
        textPrimary = 0xFFECEDF0.toInt(),
        textSecondary = 0xB8ECEDF0.toInt(),
        textMuted = 0x80ECEDF0.toInt(),
        divider = 0x1FFFFFFF,
        accent = 0xFF7FB0FF.toInt(),
        accentSoft = 0x332D5BB3,
        accentTrackOn = 0x807FB0FF.toInt(),
        accentTrackOff = 0x55FFFFFF,
        accentThumbOff = 0xFFD8DBE1.toInt(),
        danger = 0xFFFF6B6F.toInt(),
        warning = 0xFFF2B06B.toInt(),
        success = 0xFF4FC38B.toInt(),
        inputFill = 0xFF1D1F23.toInt(),
        inputStroke = 0x557FB0FF,
        metricBgActive = 0x332D5BB3,
        metricBgIdle = 0xFF1D1F23.toInt(),
        metricStrokeActive = 0x887FB0FF.toInt(),
        metricStrokeIdle = 0x447FB0FF,
        progressTrack = 0x22FFFFFF,
        chartBg = 0xFF1D1F23.toInt(),
        chartAxis = 0x44FFFFFF,
        chartGrid = 0x22FFFFFF,
        chartBar = 0x557FB0FF,
        chartBarHighlight = 0xCC7FB0FF.toInt(),
    )

    internal fun applyDialogCard(window: Window, tokens: Tokens) {
        val bg = GradientDrawable().apply {
            setColor(tokens.surface)
            cornerRadius = tokens.cardCornerPx
            if (ConfigManager.isHomeNativeGlassEnabled && ConfigManager.isHomeNativeGlassStrokeEnabled) {
                val density = window.context.resources.displayMetrics.density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.inputStroke)
            }
        }
        window.setBackgroundDrawable(InsetDrawable(bg, tokens.dialogInsetPx))
    }

    private fun Tokens.withHomeNativeGlassOverrides(density: Float): Tokens {
        val radiusDp = ConfigManager.homeNativeGlassCardRadiusDp.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        )
        val dynamicAccent = homeNativeGlassDynamicAccent() ?: accent
        val accentSoftColor = if (night) {
            withAlpha(dynamicAccent, 0x33)
        } else {
            blendRgb(surface, dynamicAccent, 0.12f)
        }
        return Tokens(
            dialogInsetPx = dialogInsetPx,
            cardCornerPx = radiusDp * density,
            night = night,
            surface = surface,
            surfaceAlt = surfaceAlt,
            surfaceRaised = surfaceRaised,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            divider = divider,
            accent = dynamicAccent,
            accentSoft = accentSoftColor,
            accentTrackOn = withAlpha(dynamicAccent, if (night) 0x80 else 0x70),
            accentTrackOff = accentTrackOff,
            accentThumbOff = accentThumbOff,
            danger = danger,
            warning = warning,
            success = success,
            inputFill = inputFill,
            inputStroke = withAlpha(dynamicAccent, if (night) 0x55 else 0x33),
            metricBgActive = accentSoftColor,
            metricBgIdle = metricBgIdle,
            metricStrokeActive = withAlpha(dynamicAccent, if (night) 0x88 else 0x66),
            metricStrokeIdle = withAlpha(dynamicAccent, if (night) 0x44 else 0x24),
            progressTrack = progressTrack,
            chartBg = chartBg,
            chartAxis = chartAxis,
            chartGrid = chartGrid,
            chartBar = withAlpha(dynamicAccent, 0x55),
            chartBarHighlight = withAlpha(dynamicAccent, 0xCC),
        )
    }

    private fun homeNativeGlassDynamicAccent(): Int? {
        return HomeNativeGlassDynamicTintCache.resolveAccentColor()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun blendRgb(base: Int, overlay: Int, overlayRatio: Float): Int {
        val ratio = overlayRatio.coerceIn(0f, 1f)
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(base) * inverse + Color.red(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.green(base) * inverse + Color.green(overlay) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(base) * inverse + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255),
        )
    }

    internal fun paintScanActionButton(button: Button, density: Float, textColor: Int) {
        button.textSize = 13.5f
        button.setAllCaps(false)
        button.setTextColor(textColor)
        val hPadding = (8 * density).toInt()
        val vPadding = (4 * density).toInt()
        button.setPadding(hPadding, vPadding, hPadding, vPadding)
        button.minWidth = 0
        button.minHeight = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.setBackgroundColor(0x00000000)
    }

    internal fun setButtonEnabledState(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.4f
    }

    internal fun animateDialogEntry(root: View, density: Float) {
        root.alpha = 0f
        root.translationY = 10f * density
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .start()
    }

    internal fun animateSectionHeadersReveal(headers: List<View>, density: Float) {
        if (headers.isEmpty()) return
        val interp = PathInterpolator(0.2f, 0f, 0f, 1f)
        headers.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationX = 6f * density
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(40L + index * 36L)
                .setDuration(200)
                .setInterpolator(interp)
                .start()
        }
    }

    internal fun animateResultReveal(view: View) {
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }

    internal fun animateIconTap(view: View) {
        view.animate()
            .rotationBy(360f)
            .setDuration(520)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .start()
    }

    /**
     * 开关行切换时的背景脉冲。
     * 短暂显示 accent 色后恢复透明。
     */
    internal fun animateSwitchPulse(row: View, tokens: Tokens) {
        val from = 0x00000000
        val peak = tokens.accentSoft
        val animator = ValueAnimator.ofArgb(from, peak, from).apply {
            duration = 360
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { v ->
                row.setBackgroundColor(v.animatedValue as Int)
            }
        }
        animator.start()
    }

    /**
     * 按钮启用时的缩放动画。
     */
    internal fun animateButtonEnable(button: View) {
        button.scaleX = 0.88f
        button.scaleY = 0.88f
        button.alpha = 0f
        button.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()
    }

    /**
     * 列表项逐条入场，用于子弹窗的 switch 行。
     */
    internal fun animateListItemsStagger(items: List<View>, density: Float) {
        if (items.isEmpty()) return
        val interp = DecelerateInterpolator(1.4f)
        items.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 8f * density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(30L + index * 28L)
                .setDuration(180)
                .setInterpolator(interp)
                .start()
        }
    }

    /**
     * 进度条完成时的亮度脉冲。
     */
    internal fun animateProgressComplete(progressView: View) {
        progressView.animate()
            .alpha(0.5f)
            .setDuration(150)
            .withEndAction {
                progressView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .withEndAction {
                        progressView.animate()
                            .alpha(0.7f)
                            .setDuration(120)
                            .withEndAction {
                                progressView.animate()
                                    .alpha(1f)
                                    .setDuration(120)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    /**
     * 对话框关闭时的退出动画，缩小并淡出。
     */
    internal fun animateDialogExit(root: View, density: Float, onEnd: () -> Unit) {
        root.animate()
            .alpha(0f)
            .translationY(6f * density)
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction { onEnd() }
            .start()
    }

    /**
     * 标题区域品牌 tag 的一次性淡入动画。
     */
    internal fun animateBrandTagShimmer(view: View) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setStartDelay(300)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * 操作图标按压缩放，用于小按钮点击。
     */
    internal fun animateActionPress(view: View) {
        view.animate().cancel()
        view.scaleX = 0.82f
        view.scaleY = 0.82f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(3.0f))
            .start()
    }

    /**
     * 展开和收起箭头的旋转动画。
     * 使用 U+2335 字符，初始角度 0 度，展开后旋转到 180 度。
     */
    internal fun animateExpandArrow(view: View, expanding: Boolean) {
        val target = if (expanding) 180f else 0f
        view.animate()
            .rotation(target)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    /**
     * 卡片展开动画，从高度 0 和透明状态展开到完整高度和不透明状态。
     */
    internal fun animateCardExpand(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleY = 0.92f
        view.pivotY = 0f
        view.animate()
            .alpha(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    /**
     * 卡片收起动画，缩小并淡出后隐藏。
     */
    internal fun animateCardCollapse(view: View) {
        view.pivotY = 0f
        view.animate()
            .alpha(0f)
            .scaleY(0.92f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .withEndAction {
                view.visibility = View.GONE
                view.scaleY = 1f
                view.alpha = 1f
            }
            .start()
    }

    internal fun createModelScoreInputBackground(tokens: Tokens, density: Float): Drawable {
        return UnderlineDrawable(density, tokens.accent)
    }

    internal fun createKeywordInputCardBackground(tokens: Tokens, density: Float): Drawable {
        return GradientDrawable().apply {
            setColor(tokens.inputFill)
            cornerRadius = 14f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.inputStroke)
        }
    }

    internal fun createMetricCellBackground(
        tokens: Tokens,
        density: Float,
        active: Boolean,
    ): Drawable {
        return GradientDrawable().apply {
            setColor(if (active) tokens.metricBgActive else tokens.metricBgIdle)
            cornerRadius = 9f * density
            setStroke(
                (1 * density).toInt().coerceAtLeast(1),
                if (active) tokens.metricStrokeActive else tokens.metricStrokeIdle,
            )
        }
    }

    internal fun createStatsCardBackground(tokens: Tokens, density: Float): Drawable {
        return GradientDrawable().apply {
            setColor(tokens.surfaceAlt)
            cornerRadius = 10f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    internal fun createResultCardBackground(tokens: Tokens, density: Float): Drawable {
        return GradientDrawable().apply {
            setColor(tokens.surfaceAlt)
            cornerRadius = 12f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    internal fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        ).toInt()
    }

    internal class ThinProgressBar(
        context: Context,
        private val tokens: Tokens,
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.progressTrack
            style = Paint.Style.FILL
        }
        private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.accent
            style = Paint.Style.FILL
        }
        private val rect = RectF()
        private var ratio = 0f
        private var animator: ValueAnimator? = null

        fun setProgress(target: Float, animated: Boolean = true) {
            val clamped = target.coerceIn(0f, 1f)
            if (clamped <= ratio + 0.0005f) {
                if (clamped > ratio) {
                    ratio = clamped
                    invalidate()
                }
                return
            }
            animator?.cancel()
            if (!animated) {
                ratio = clamped
                invalidate()
                return
            }
            animator = ValueAnimator.ofFloat(ratio, clamped).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { v ->
                    ratio = v.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val r = h / 2f
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, r, r, bgPaint)
            val fgRight = w * ratio
            if (fgRight > 0f) {
                rect.set(0f, 0f, fgRight, h)
                canvas.drawRoundRect(rect, r, r, fgPaint)
            }
        }
    }

    private class UnderlineDrawable(
        private val density: Float,
        private val color: Int,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@UnderlineDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.SQUARE
        }

        override fun draw(canvas: Canvas) {
            val stroke = (1.5f * density).coerceAtLeast(1f)
            paint.strokeWidth = stroke
            val y = bounds.bottom - stroke / 2f
            canvas.drawLine(bounds.left.toFloat(), y, bounds.right.toFloat(), y, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        @Suppress("DEPRECATION")
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
