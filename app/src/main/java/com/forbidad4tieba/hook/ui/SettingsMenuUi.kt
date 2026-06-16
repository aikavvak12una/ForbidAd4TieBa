package com.forbidad4tieba.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassDynamicTintCache
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassImageCache
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal const val SETTINGS_DIALOG_PADDING_DP = 20f
internal const val SETTINGS_DIALOG_TITLE_SP = 22f
internal const val SETTINGS_SECTION_TITLE_SP = 13.5f
internal const val SETTINGS_ROW_TITLE_SP = 14.5f
internal const val SETTINGS_ROW_DESC_SP = 11.5f
internal const val SETTINGS_BRAND_TAG_SP = 11.5f
internal const val SETTINGS_INPUT_TEXT_SP = 14.5f
internal const val SETTINGS_VALUE_TEXT_SP = 13f
internal const val SETTINGS_ROW_VERTICAL_PADDING_RATIO = 0.46f
internal const val SETTINGS_DIALOG_TITLE_BOTTOM_PADDING_RATIO = 0.30f
internal const val SETTINGS_DIALOG_CONTENT_TOP_PADDING_RATIO = 0.50f
internal const val SETTINGS_ROOT_CONTENT_VERTICAL_PADDING_RATIO = 0.40f
internal const val SETTINGS_ROOT_GROUP_GAP_DP = 8f
internal const val SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO = 0.55f
internal const val SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO = 0.24f
internal const val SETTINGS_SUBMENU_SECTION_TOP_PADDING_RATIO = 0.15f
internal const val SETTINGS_SUBMENU_SECTION_BOTTOM_PADDING_RATIO = 0.06f
internal const val SETTINGS_DIVIDER_VERTICAL_MARGIN_RATIO = 0.25f
internal const val SETTINGS_DESC_TOP_PADDING_DP = 3f
internal const val SETTINGS_DESC_RIGHT_PADDING_DP = 14f

private val firstSettingsDialogBackgroundErrorLogged = AtomicBoolean(false)
private const val SETTINGS_BRAND_TAG_VIEW_TAG = "settings_brand_tag"

internal data class HomeNativeGlassDialogPreviewStyle(
    val style: ConfigManager.HomeNativeGlassStyleConfig,
    val darkMode: Boolean,
    val previewBitmap: Bitmap? = null,
)

internal fun settingsDialogPadding(density: Float): Int {
    return (SETTINGS_DIALOG_PADDING_DP * density).toInt()
}

internal fun settingsRowVerticalPadding(density: Float): Int {
    return (settingsDialogPadding(density) * SETTINGS_ROW_VERTICAL_PADDING_RATIO).toInt()
}

internal fun settingsDialogTitleBottomPadding(padding: Int): Int {
    return (padding * SETTINGS_DIALOG_TITLE_BOTTOM_PADDING_RATIO).toInt()
}

internal fun settingsDialogContentTopPadding(padding: Int): Int {
    return (padding * SETTINGS_DIALOG_CONTENT_TOP_PADDING_RATIO).toInt()
}

internal fun settingsRootContentVerticalPadding(padding: Int): Int {
    return (padding * SETTINGS_ROOT_CONTENT_VERTICAL_PADDING_RATIO).toInt()
}

internal fun TextView.applySettingsDialogTitleStyle(
    tokens: UiStyle.Tokens,
    density: Float,
) {
    textSize = SETTINGS_DIALOG_TITLE_SP
    letterSpacing = 0.02f
    setTextColor(tokens.textPrimary)
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
    includeFontPadding = false
    setLineSpacing(1.5f * density, 1f)
}

internal fun TextView.applySettingsBrandTagStyle(
    tokens: UiStyle.Tokens,
    density: Float,
) {
    textSize = SETTINGS_BRAND_TAG_SP
    letterSpacing = 0.06f
    typeface = Typeface.MONOSPACE
    setTextColor(tokens.textMuted)
    includeFontPadding = false
    setPadding(0, (2 * density).toInt(), 0, 0)
}

internal fun TextView.applySettingsSectionTitleStyle(
    tokens: UiStyle.Tokens,
    density: Float,
) {
    textSize = SETTINGS_SECTION_TITLE_SP
    letterSpacing = 0.04f
    setTextColor(tokens.accent)
    typeface = Typeface.DEFAULT_BOLD
    includeFontPadding = false
    setLineSpacing(1f * density, 1f)
}

internal fun TextView.applySettingsRowTitleStyle(
    tokens: UiStyle.Tokens,
    density: Float,
    enabled: Boolean = true,
) {
    textSize = SETTINGS_ROW_TITLE_SP
    setTextColor(if (enabled) tokens.textPrimary else tokens.textMuted)
    typeface = Typeface.DEFAULT_BOLD
    includeFontPadding = false
    setLineSpacing(1.5f * density, 1f)
}

internal fun TextView.applySettingsRowDescriptionStyle(
    tokens: UiStyle.Tokens,
    density: Float,
    enabled: Boolean = true,
    rightPaddingDp: Float = SETTINGS_DESC_RIGHT_PADDING_DP,
    bottomPaddingDp: Float = 0f,
) {
    textSize = SETTINGS_ROW_DESC_SP
    setTextColor(if (enabled) tokens.textSecondary else tokens.textMuted)
    setPadding(
        0,
        (SETTINGS_DESC_TOP_PADDING_DP * density).toInt(),
        (rightPaddingDp * density).toInt(),
        (bottomPaddingDp * density).toInt(),
    )
    includeFontPadding = false
    setLineSpacing(1f * density, 1f)
}

internal fun TextView.applySettingsMessageStyle(
    tokens: UiStyle.Tokens,
    density: Float,
    primary: Boolean = true,
) {
    textSize = SETTINGS_ROW_TITLE_SP
    setTextColor(if (primary) tokens.textPrimary else tokens.textSecondary)
    includeFontPadding = false
    setLineSpacing(2f * density, 1f)
}

internal fun TextView.applySettingsCodeTextStyle(
    tokens: UiStyle.Tokens,
    density: Float,
    muted: Boolean = false,
) {
    textSize = SETTINGS_ROW_DESC_SP
    typeface = Typeface.MONOSPACE
    setTextColor(if (muted) tokens.textMuted else tokens.textPrimary)
    includeFontPadding = false
    setLineSpacing(1.5f * density, 1f)
}

internal fun createSettingsDialogTitleView(context: Context, title: String): View {
    val density = context.resources.displayMetrics.density
    val padding = settingsDialogPadding(density)
    val tokens = UiStyle.tokens(context)
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding, padding, settingsDialogTitleBottomPadding(padding))
        addView(TextView(context).apply {
            id = android.R.id.title
            text = title
            applySettingsDialogTitleStyle(tokens, density)
        })
        addView(TextView(context).apply {
            tag = SETTINGS_BRAND_TAG_VIEW_TAG
            text = UiText.Settings.BRAND_TAG
            applySettingsBrandTagStyle(tokens, density)
        }.also { UiStyle.animateBrandTagShimmer(it) })
    }
}

internal fun AlertDialog.Builder.setSettingsTitle(
    context: Context,
    title: String,
): AlertDialog.Builder {
    return setCustomTitle(createSettingsDialogTitleView(context, title))
}

internal fun applyUnifiedDialogCardStyle(
    window: Window,
    density: Float,
    useCustomBackground: Boolean = true,
    homeNativeGlassPreviewStyle: HomeNativeGlassDialogPreviewStyle? = null,
    accountWatermark: Boolean = true,
) {
    val activeStyle = homeNativeGlassPreviewStyle?.style
        ?: if (ConfigManager.isHomeNativeGlassEnabled) ConfigManager.activeHomeNativeGlassStyle() else null
    val tokens = if (homeNativeGlassPreviewStyle != null) {
        UiStyle.homeNativeGlassPreviewTokens(
            window.context,
            homeNativeGlassPreviewStyle.style,
            homeNativeGlassPreviewStyle.darkMode,
        )
    } else {
        UiStyle.tokens(window.context)
    }
    applySettingsDialogShadow(
        window = window,
        density = density,
        shadowStrengthPercent = activeStyle?.shadowStrengthPercent
            ?: ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    )
    refreshSettingsDialogTitle(window, tokens, density)
    tintSettingsDialogActionButtons(window, tokens.accent)
    val background = if (useCustomBackground) {
        createSettingsDialogCustomBackground(
            window = window,
            tokens = tokens,
            density = density,
            previewStyle = homeNativeGlassPreviewStyle,
        )
    } else {
        null
    }
    val cardBackground = background ?: createSettingsDialogCardBackground(tokens, density)
    val finalBackground = if (accountWatermark) {
        createAccountIdWatermarkedBackground(
            context = window.context,
            sourceBackground = cardBackground,
            density = density,
            cornerRadiusPx = tokens.cardCornerPx,
        )
    } else {
        cardBackground
    }
    window.setBackgroundDrawable(NoIntrinsicInsetDrawable(finalBackground, tokens.dialogInsetPx))
    clearSystemDialogCustomPanelPadding(window)
    applyStableDialogWindowLayout(window, density)
}

private fun createSettingsDialogCardBackground(
    tokens: UiStyle.Tokens,
    density: Float,
): Drawable {
    return GradientDrawable().apply {
        setColor(tokens.surface)
        cornerRadius = tokens.cardCornerPx
        if (ConfigManager.isHomeNativeGlassEnabled && ConfigManager.isHomeNativeGlassStrokeEnabled) {
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.inputStroke)
        }
    }
}

private fun refreshSettingsDialogTitle(
    window: Window,
    tokens: UiStyle.Tokens,
    density: Float,
) {
    (window.decorView.findViewById<View>(android.R.id.title) as? TextView)
        ?.applySettingsDialogTitleStyle(tokens, density)
    findTaggedTextView(window.decorView, SETTINGS_BRAND_TAG_VIEW_TAG)
        ?.applySettingsBrandTagStyle(tokens, density)
}

private fun findTaggedTextView(view: View, tag: String): TextView? {
    if (view is TextView && view.tag == tag) return view
    val group = view as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        findTaggedTextView(group.getChildAt(index), tag)?.let { return it }
    }
    return null
}

private fun clearSystemDialogCustomPanelPadding(window: Window) {
    val customPanel = window.decorView.findViewById<View>(android.R.id.custom) ?: return
    if (
        customPanel.paddingLeft != 0 ||
        customPanel.paddingTop != 0 ||
        customPanel.paddingRight != 0 ||
        customPanel.paddingBottom != 0
    ) {
        customPanel.setPadding(0, 0, 0, 0)
    }
}

private fun applyStableDialogWindowLayout(window: Window, density: Float) {
    val screenWidth = window.context.resources.displayMetrics.widthPixels
    val horizontalMargin = (12f * density).toInt().coerceAtLeast(1)
    val availableWidth = screenWidth - horizontalMargin * 2
    if (availableWidth <= 0) return

    val maxWidth = (560f * density).toInt().coerceAtLeast(1)
    val minWidth = (280f * density).toInt().coerceAtMost(availableWidth)
    val targetWidth = availableWidth
        .coerceAtMost(maxWidth)
        .coerceAtLeast(minWidth)

    window.setGravity(Gravity.CENTER)
    val attrs = window.attributes
    attrs.gravity = Gravity.CENTER
    attrs.x = 0
    attrs.y = 0
    attrs.width = targetWidth
    attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT
    window.attributes = attrs
    window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
}

private class NoIntrinsicInsetDrawable(
    val contentDrawable: Drawable,
    inset: Int,
) : InsetDrawable(contentDrawable, inset) {
    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1
}

internal class MaxHeightScrollView(
    context: Context,
    private val maxHeightPx: Int,
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentSize = MeasureSpec.getSize(heightMeasureSpec)
        val cappedHeight = if (parentMode == MeasureSpec.UNSPECIFIED) {
            maxHeightPx
        } else {
            maxHeightPx.coerceAtMost(parentSize)
        }.coerceAtLeast(1)
        val cappedHeightSpec = MeasureSpec.makeMeasureSpec(cappedHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, cappedHeightSpec)
    }
}

private fun tintSettingsDialogActionButtons(window: Window, color: Int) {
    val decor = window.decorView
    intArrayOf(android.R.id.button1, android.R.id.button2, android.R.id.button3).forEach { id ->
        (decor.findViewById(id) as? Button)?.setTextColor(color)
    }
}

private fun applySettingsDialogShadow(window: Window, density: Float, shadowStrengthPercent: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
    val decor = window.decorView
    val shadowScale = shadowStrengthPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    ) / 100f
    decor.elevation = 10f * density * shadowScale
    decor.translationZ = 2f * density * shadowScale
}

private fun createSettingsDialogCustomBackground(
    window: Window,
    tokens: UiStyle.Tokens,
    density: Float,
    previewStyle: HomeNativeGlassDialogPreviewStyle? = null,
): Drawable? {
    val context = window.context
    val style = previewStyle?.style
        ?: if (ConfigManager.isHomeNativeGlassEnabled) ConfigManager.activeHomeNativeGlassStyle() else null
    val source = resolveSettingsDialogBackgroundImageSource(
        style = style,
        realtimePreviewEnabled = previewStyle != null,
        previewBitmap = previewStyle?.previewBitmap,
    ) ?: return null
    val overlayColor = Color.TRANSPARENT
    val cornerRadiusPx = previewStyle?.style?.cardRadiusDp?.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
        ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
    )?.let { it * density } ?: tokens.cardCornerPx
    val strokeEnabled = style?.strokeEnabled ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED
    val shadowStrengthPercent = style?.shadowStrengthPercent
        ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT

    findSettingsDialogCustomBackgroundDrawable(window)?.let { existing ->
        if (existing.updateIfSameImage(
                imagePath = source.path,
                tokens = tokens,
                overlayColor = overlayColor,
                cornerRadiusPx = cornerRadiusPx,
                strokeEnabled = strokeEnabled,
                shadowStrengthPercent = shadowStrengthPercent,
                previewBitmap = source.previewBitmap,
            )
        ) {
            return existing
        }
    }

    val metrics = context.resources.displayMetrics
    val targetWidth = (metrics.widthPixels - tokens.dialogInsetPx * 2).coerceAtLeast(1)
    val targetHeight = (metrics.heightPixels - tokens.dialogInsetPx * 2).coerceAtLeast(1)
    val bitmap = runCatching {
        HomeNativeGlassImageCache.decodeSampledBitmap(source.path, targetWidth, targetHeight)
    }.getOrNull()
    if (bitmap == null) {
        if (firstSettingsDialogBackgroundErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logD { "[SettingsMenuHook] settings dialog background unavailable: ${source.path}" }
        }
        return null
    }
    return SettingsDialogCustomBackgroundDrawable(
        imagePath = source.path,
        bitmap = bitmap,
        previewBitmap = source.previewBitmap,
        tokens = tokens,
        overlayColor = overlayColor,
        density = density,
        cornerRadiusPx = cornerRadiusPx,
        strokeEnabled = strokeEnabled,
        shadowStrengthPercent = shadowStrengthPercent,
    )
}

private fun findSettingsDialogCustomBackgroundDrawable(window: Window): SettingsDialogCustomBackgroundDrawable? {
    val contentDrawable = (window.decorView.background as? NoIntrinsicInsetDrawable)
        ?.contentDrawable
    return when (contentDrawable) {
        is SettingsDialogCustomBackgroundDrawable -> contentDrawable
        is SettingsAccountIdWatermarkDrawable -> {
            contentDrawable.baseDrawable as? SettingsDialogCustomBackgroundDrawable
        }
        else -> null
    }
}

private data class SettingsDialogBackgroundImageSource(
    val path: String,
    val previewBitmap: Bitmap?,
)

private fun resolveSettingsDialogBackgroundImageSource(
    style: ConfigManager.HomeNativeGlassStyleConfig?,
    realtimePreviewEnabled: Boolean,
    previewBitmap: Bitmap?,
): SettingsDialogBackgroundImageSource? {
    if (style == null) return null
    val sourcePath = style.backgroundImagePath.trim()
    if (sourcePath.isBlank()) return null
    if (realtimePreviewEnabled) {
        if (!runCatching { File(sourcePath).isFile }.getOrDefault(false)) return null
        return SettingsDialogBackgroundImageSource(
            path = sourcePath,
            previewBitmap = previewBitmap,
        )
    }
    val blurCachePath = style.blurCacheImagePath.trim()
    if (
        blurCachePath.isNotBlank() &&
        runCatching { File(blurCachePath).isFile }.getOrDefault(false)
    ) {
        return SettingsDialogBackgroundImageSource(
            path = blurCachePath,
            previewBitmap = null,
        )
    }
    if (!runCatching { File(sourcePath).isFile }.getOrDefault(false)) return null
    return SettingsDialogBackgroundImageSource(
        path = sourcePath,
        previewBitmap = null,
    )
}

private fun settingsDialogStrokeColor(tokens: UiStyle.Tokens): Int {
    val tintColor = HomeNativeGlassDynamicTintCache.resolveAccentColor()
    val baseColor = if (tintColor == null) {
        Color.WHITE
    } else {
        blendRgb(tintColor, Color.WHITE, if (tokens.night) 0.35f else 0.55f)
    }
    val alpha = if (tokens.night) 92 else 118
    return Color.argb(
        alpha,
        Color.red(baseColor),
        Color.green(baseColor),
        Color.blue(baseColor),
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

private class SettingsDialogCustomBackgroundDrawable(
    private val imagePath: String,
    private val bitmap: Bitmap,
    private var previewBitmap: Bitmap?,
    private var tokens: UiStyle.Tokens,
    private var overlayColor: Int,
    density: Float,
    private var cornerRadiusPx: Float,
    private var strokeEnabled: Boolean,
    shadowStrengthPercent: Int,
) : Drawable() {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val strokeWidth = max(1f, density)
    private var shadowStrengthPercent = shadowStrengthPercent.coerceIn(
        ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
    )
    private val src = Rect()
    private val dst = Rect()
    private val rect = RectF()
    private val insetRect = RectF()
    private val clipPath = Path()
    private var drawableAlpha = 255

    fun updateIfSameImage(
        imagePath: String,
        tokens: UiStyle.Tokens,
        overlayColor: Int,
        cornerRadiusPx: Float,
        strokeEnabled: Boolean,
        shadowStrengthPercent: Int,
        previewBitmap: Bitmap?,
    ): Boolean {
        if (this.imagePath != imagePath) return false
        this.tokens = tokens
        this.overlayColor = overlayColor
        this.cornerRadiusPx = cornerRadiusPx
        this.strokeEnabled = strokeEnabled
        this.previewBitmap = previewBitmap
        this.shadowStrengthPercent = shadowStrengthPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        )
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        dst.set(bounds)
        val width = dst.width()
        val height = dst.height()
        val activeBitmap = previewBitmap ?: bitmap
        val bitmapWidth = activeBitmap.width
        val bitmapHeight = activeBitmap.height
        if (width <= 0 || height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return

        rect.set(dst)
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)

        drawBitmapCover(canvas, activeBitmap, drawableAlpha)
        val scaledOverlayColor = scaleAlpha(overlayColor, drawableAlpha)
        if (Color.alpha(scaledOverlayColor) > 0) {
            overlayPaint.color = scaledOverlayColor
            canvas.drawRect(rect, overlayPaint)
        }
        if (shadowStrengthPercent > 0) {
            drawSoftShadow(canvas)
        }
        canvas.restoreToCount(save)
        if (strokeEnabled) {
            drawStroke(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1

    private fun scaleAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            (Color.alpha(color) * alpha / 255f).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun drawSoftShadow(canvas: Canvas) {
        val alpha = (24 * shadowStrengthPercent / 100f * drawableAlpha / 255f).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        shadowPaint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(alpha, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, shadowPaint)
        shadowPaint.shader = null
    }

    private fun drawStroke(canvas: Canvas) {
        insetRect.set(rect)
        val inset = strokeWidth * 0.5f
        insetRect.inset(inset, inset)
        val radius = (cornerRadiusPx - inset).coerceAtLeast(0f)
        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = scaleAlpha(settingsDialogStrokeColor(tokens), drawableAlpha)
        canvas.drawRoundRect(insetRect, radius, radius, strokePaint)
    }

    private fun drawBitmapCover(canvas: Canvas, source: Bitmap, alpha: Int) {
        val bitmapWidth = source.width
        val bitmapHeight = source.height
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || alpha <= 0) return
        if (bitmapWidth.toLong() * dst.height() > dst.width().toLong() * bitmapHeight) {
            val srcWidth = (bitmapHeight.toLong() * dst.width() / dst.height()).toInt()
                .coerceIn(1, bitmapWidth)
            val left = (bitmapWidth - srcWidth) / 2
            src.set(left, 0, left + srcWidth, bitmapHeight)
        } else {
            val srcHeight = (bitmapWidth.toLong() * dst.height() / dst.width()).toInt()
                .coerceIn(1, bitmapHeight)
            val top = (bitmapHeight - srcHeight) / 2
            src.set(0, top, bitmapWidth, top + srcHeight)
        }
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(source, src, dst, bitmapPaint)
    }
}

internal fun dialogThemeFor(context: Context): Int {
    return if (UiStyle.tokens(context).night) {
        android.R.style.Theme_DeviceDefault_Dialog_Alert
    } else {
        android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    }
}

private fun createAccountIdWatermarkedBackground(
    context: Context,
    sourceBackground: Drawable,
    density: Float,
    cornerRadiusPx: Float,
): Drawable {
    val accountId = TiebaAccountIdentity.currentAccountId(context) ?: return sourceBackground
    if (
        sourceBackground is SettingsAccountIdWatermarkDrawable &&
        sourceBackground.watermarkText == accountId
    ) {
        return sourceBackground
    }
    val baseBackground = (sourceBackground as? SettingsAccountIdWatermarkDrawable)?.baseDrawable
        ?: sourceBackground
    return SettingsAccountIdWatermarkDrawable(
        baseDrawable = baseBackground,
        watermarkText = accountId,
        density = density,
        cornerRadiusPx = cornerRadiusPx,
    )
}

private class SettingsAccountIdWatermarkDrawable(
    val baseDrawable: Drawable?,
    val watermarkText: String,
    density: Float,
    private val cornerRadiusPx: Float,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
        typeface = Typeface.MONOSPACE
    }
    private val fillFontMetrics = fillPaint.fontMetrics
    private val textCenterOffsetY = (fillFontMetrics.ascent + fillFontMetrics.descent) * 0.5f
    private val textHeightPx = (fillFontMetrics.descent - fillFontMetrics.ascent).coerceAtLeast(1f)
    private val horizontalGapPx = 64f * density
    private val minHorizontalStepPx = 126f * density
    private val verticalStepPx = 80f * density
    private val clipPath = Path()
    private val rectF = RectF()
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val rect = bounds
        if (rect.width() <= 0 || rect.height() <= 0) return

        baseDrawable?.let { base ->
            base.setBounds(rect)
            base.alpha = drawableAlpha
            base.colorFilter = drawableColorFilter
            base.draw(canvas)
        }

        val text = watermarkText.takeIf { it.isNotBlank() } ?: return
        val textWidth = fillPaint.measureText(text).coerceAtLeast(1f)
        val horizontalStep = max(textWidth + horizontalGapPx, minHorizontalStepPx)
        val diagonalSpan = rect.width() + rect.height().toFloat()
        val leftLimit = rect.left - diagonalSpan
        val rightLimit = rect.right + diagonalSpan
        val topLimit = rect.top - diagonalSpan
        val bottomLimit = rect.bottom + diagonalSpan

        fillPaint.color = Color.argb(scaleAlpha(108), 210, 210, 210)

        val save = canvas.save()
        rectF.set(rect)
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.rotate(-26f, rect.exactCenterX(), rect.exactCenterY())
        var rowIndex = 0
        var y = topLimit
        while (y <= bottomLimit) {
            var columnIndex = 0
            var x = leftLimit + if (rowIndex % 2 == 0) 0f else horizontalStep * 0.5f
            while (x <= rightLimit) {
                fillPaint.shader = watermarkTextGradient(
                    x = x,
                    baselineY = y,
                    textWidth = textWidth,
                    rowIndex = rowIndex,
                    columnIndex = columnIndex,
                )
                canvas.drawText(text, x, y, fillPaint)
                columnIndex += 1
                x += horizontalStep
            }
            rowIndex += 1
            y += verticalStepPx
        }
        fillPaint.shader = null
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1

    private fun scaleAlpha(alpha: Int): Int {
        return (alpha * drawableAlpha / 255f).toInt().coerceIn(0, 255)
    }

    private fun watermarkTextGradient(
        x: Float,
        baselineY: Float,
        textWidth: Float,
        rowIndex: Int,
        columnIndex: Int,
    ): LinearGradient {
        val variant = watermarkVariant(rowIndex, columnIndex)
        val alpha = scaleAlpha(96 + variant % 32)
        val darkGray = 42 + (variant ushr 5) % 58
        val lightGray = 188 + (variant ushr 11) % 56
        val startColor = Color.argb(alpha, darkGray, darkGray, darkGray)
        val endColor = Color.argb(alpha, lightGray, lightGray, lightGray)
        val halfWidth = textWidth * 0.5f
        val halfHeight = textHeightPx * 0.5f
        val centerY = baselineY + textCenterOffsetY
        val direction = variant % 6
        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float
        when (direction) {
            0 -> {
                startX = x - halfWidth
                startY = centerY
                endX = x + halfWidth
                endY = centerY
            }
            1 -> {
                startX = x + halfWidth
                startY = centerY
                endX = x - halfWidth
                endY = centerY
            }
            2 -> {
                startX = x - halfWidth
                startY = centerY - halfHeight
                endX = x + halfWidth
                endY = centerY + halfHeight
            }
            3 -> {
                startX = x + halfWidth
                startY = centerY - halfHeight
                endX = x - halfWidth
                endY = centerY + halfHeight
            }
            4 -> {
                startX = x
                startY = centerY - halfHeight
                endX = x
                endY = centerY + halfHeight
            }
            else -> {
                startX = x - halfWidth
                startY = centerY + halfHeight
                endX = x + halfWidth
                endY = centerY - halfHeight
            }
        }
        return LinearGradient(startX, startY, endX, endY, startColor, endColor, Shader.TileMode.CLAMP)
    }

    private fun watermarkVariant(rowIndex: Int, columnIndex: Int): Int {
        var value = watermarkText.hashCode()
        value = value * 31 + rowIndex * 73856093
        value = value * 31 + columnIndex * 19349663
        return value and Int.MAX_VALUE
    }
}

internal fun createDialogScrollContainer(context: Context, content: View): ScrollView {
    return ScrollView(context).apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        clipToPadding = false
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
    }
}

internal fun Button.updateButtonEnabledState(enabled: Boolean) {
    UiStyle.setButtonEnabledState(this, enabled)
}

internal fun findSwitchView(root: View): Switch? {
    if (root is Switch) return root
    if (root !is ViewGroup) return null
    for (index in 0 until root.childCount) {
        val found = findSwitchView(root.getChildAt(index))
        if (found != null) return found
    }
    return null
}

internal fun createSwitchRow(
    context: Context,
    prefs: SharedPreferences,
    label: String,
    description: String?,
    prefKey: String?,
    padding: Int,
    enabled: Boolean = true,
    defaultValue: Boolean = false,
    actionIcon: String? = null,
    onActionClick: (() -> Unit)? = null,
    linkedPrefKeys: List<String> = emptyList(),
): View {
    val tokens = UiStyle.tokens(context)
    val density = context.resources.displayMetrics.density
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            0,
            (padding * SETTINGS_ROW_VERTICAL_PADDING_RATIO).toInt(),
            0,
            (padding * SETTINGS_ROW_VERTICAL_PADDING_RATIO).toInt(),
        )
    }

    val textContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    val tvLabel = TextView(context).apply {
        text = label
        textSize = SETTINGS_ROW_TITLE_SP
        setTextColor(if (enabled) tokens.textPrimary else tokens.textMuted)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(1.5f * density, 1f)
    }
    textContainer.addView(tvLabel)

    if (description != null) {
        val tvDesc = TextView(context).apply {
            text = description
            textSize = SETTINGS_ROW_DESC_SP
            setTextColor(if (enabled) tokens.textSecondary else tokens.textMuted)
            setPadding(
                0,
                (SETTINGS_DESC_TOP_PADDING_DP * density).toInt(),
                (SETTINGS_DESC_RIGHT_PADDING_DP * density).toInt(),
                0,
            )
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
        }
        textContainer.addView(tvDesc)
    }

    row.addView(textContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f))

    if (actionIcon != null && onActionClick != null) {
        val actionBtn = TextView(context).apply {
            text = actionIcon
            textSize = 18f
            setTextColor(if (enabled) tokens.accent else tokens.textMuted)
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                if (enabled) {
                    UiStyle.animateActionPress(this)
                    onActionClick()
                }
            }
        }
        row.addView(actionBtn)
    }

    @Suppress("DEPRECATION")
    val sw = Switch(context).apply {
        isChecked = if (enabled && prefKey != null) {
            resolveSwitchChecked(prefs, prefKey, linkedPrefKeys, defaultValue)
        } else {
            defaultValue
        }
        isEnabled = enabled

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        thumbTintList = ColorStateList(
            states,
            intArrayOf(
                tokens.accent,
                tokens.accentThumbOff,
            )
        )
        trackTintList = ColorStateList(
            states,
            intArrayOf(
                tokens.accentTrackOn,
                tokens.accentTrackOff,
            )
        )

        setOnCheckedChangeListener { _, isChecked ->
            if (enabled) {
                if (prefKey != null) {
                    val editor = prefs.edit().putBoolean(prefKey, isChecked)
                    for (linkedPrefKey in linkedPrefKeys) {
                        editor.putBoolean(linkedPrefKey, isChecked)
                    }
                    editor.apply()
                }
            }
        }
    }
    row.addView(sw)

    return row
}

private fun resolveSwitchChecked(
    prefs: SharedPreferences,
    prefKey: String,
    linkedPrefKeys: List<String>,
    defaultValue: Boolean,
): Boolean {
    if (!prefs.contains(prefKey) && linkedPrefKeys.none { prefs.contains(it) }) {
        return defaultValue
    }
    if (prefs.getBoolean(prefKey, false)) return true
    return linkedPrefKeys.any { prefs.getBoolean(it, false) }
}

internal fun createAboutItem(
    context: Context,
    density: Float,
    padding: Int,
    title: String,
    content: String,
    url: String? = null,
    onClick: (() -> Unit)? = null,
): View {
    val tokens = UiStyle.tokens(context)
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, (padding * 0.4f).toInt(), 0, (padding * 0.4f).toInt())
        if (onClick != null) {
            isClickable = true
            setOnClickListener { onClick() }
        }

        addView(TextView(context).apply {
            text = title
            textSize = 14.5f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        })

        addView(TextView(context).apply {
            text = content
            textSize = 13f
            setTextColor(if (url != null) tokens.accent else tokens.textSecondary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(0, (3 * density).toInt(), 0, 0)

            if (url != null) {
                setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (t: Throwable) {
                        XposedCompat.logW("[SettingsMenuHook] open about link failed: url=$url, msg=${t.message}")
                    }
                }
            }
        })
    }
}

internal fun createDivider(context: Context, padding: Int): View {
    val tokens = UiStyle.tokens(context)
    val density = context.resources.displayMetrics.density
    val divider = View(context)
    divider.setBackgroundColor(tokens.divider)
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (0.8f * density).toInt().coerceAtLeast(1))
    lp.setMargins(
        0,
        (padding * SETTINGS_DIVIDER_VERTICAL_MARGIN_RATIO).toInt(),
        0,
        (padding * SETTINGS_DIVIDER_VERTICAL_MARGIN_RATIO).toInt(),
    )
    divider.layoutParams = lp
    return divider
}
