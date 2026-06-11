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

internal fun createSettingsDialogTitleView(context: Context, title: String): TextView {
    val density = context.resources.displayMetrics.density
    val padding = settingsDialogPadding(density)
    val tokens = UiStyle.tokens(context)
    return TextView(context).apply {
        text = title
        applySettingsDialogTitleStyle(tokens, density)
        setPadding(padding, padding, padding, settingsDialogTitleBottomPadding(padding))
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
) {
    val tokens = UiStyle.tokens(window.context)
    val homeNativeGlassEnabled = ConfigManager.isHomeNativeGlassEnabled
    applySettingsDialogShadow(
        window = window,
        density = density,
        enabled = homeNativeGlassEnabled && ConfigManager.isHomeNativeGlassShadowEnabled,
    )
    tintSettingsDialogActionButtons(window, tokens.accent)
    val background = if (useCustomBackground) {
        createSettingsDialogCustomBackground(window.context, tokens, density)
    } else {
        null
    }
    if (background != null) {
        window.setBackgroundDrawable(NoIntrinsicInsetDrawable(background, tokens.dialogInsetPx))
    } else {
        UiStyle.applyDialogCard(window, tokens)
    }
    clearSystemDialogCustomPanelPadding(window)
    applyStableDialogWindowLayout(window, density)
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
    drawable: Drawable,
    inset: Int,
) : InsetDrawable(drawable, inset) {
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

private fun applySettingsDialogShadow(window: Window, density: Float, enabled: Boolean) {
    if (!ConfigManager.isHomeNativeGlassEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
    val decor = window.decorView
    decor.elevation = if (enabled) 10f * density else 0f
    decor.translationZ = if (enabled) 2f * density else 0f
}

private fun createSettingsDialogCustomBackground(
    context: Context,
    tokens: UiStyle.Tokens,
    density: Float,
): Drawable? {
    val path = resolveSettingsDialogBackgroundImagePath(context)
    if (path.isBlank()) return null
    val metrics = context.resources.displayMetrics
    val targetWidth = (metrics.widthPixels - tokens.dialogInsetPx * 2).coerceAtLeast(1)
    val targetHeight = (metrics.heightPixels - tokens.dialogInsetPx * 2).coerceAtLeast(1)
    val bitmap = runCatching {
        HomeNativeGlassImageCache.decodeSampledBitmap(path, targetWidth, targetHeight)
    }.getOrNull()
    if (bitmap == null) {
        if (firstSettingsDialogBackgroundErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logD { "[SettingsMenuHook] settings dialog background unavailable: $path" }
        }
        return null
    }
    return SettingsDialogCustomBackgroundDrawable(
        bitmap = bitmap,
        tokens = tokens,
        overlayColor = settingsDialogOverlayColor(tokens),
        density = density,
        strokeEnabled = ConfigManager.isHomeNativeGlassStrokeEnabled,
        shadowEnabled = ConfigManager.isHomeNativeGlassShadowEnabled,
    )
}

private fun resolveSettingsDialogBackgroundImagePath(context: Context): String {
    val prefs = ConfigManager.getPrefs(context)
    if (!prefs.getBoolean(ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS, false)) return ""
    val sourcePath = prefs.getString(
        ConfigManager.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
    )?.trim().orEmpty()
    if (sourcePath.isBlank()) return ""
    val blurCachePath = prefs.getString(
        ConfigManager.KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
    )?.trim().orEmpty()
    if (
        blurCachePath.isNotBlank() &&
        runCatching { File(blurCachePath).isFile }.getOrDefault(false)
    ) {
        return blurCachePath
    }
    return if (runCatching { File(sourcePath).isFile }.getOrDefault(false)) sourcePath else ""
}

private fun settingsDialogOverlayColor(tokens: UiStyle.Tokens): Int {
    val tintColor = HomeNativeGlassDynamicTintCache.resolveAccentColor()
    val surface = tokens.surface
    val baseColor = if (tintColor == null) {
        surface
    } else {
        blendRgb(surface, tintColor, 0.35f)
    }
    val overlayAlpha = if (tokens.night) 126 else 142
    return Color.argb(
        overlayAlpha,
        Color.red(baseColor),
        Color.green(baseColor),
        Color.blue(baseColor),
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
    private val bitmap: Bitmap,
    private val tokens: UiStyle.Tokens,
    private val overlayColor: Int,
    density: Float,
    private val strokeEnabled: Boolean,
    private val shadowEnabled: Boolean,
) : Drawable() {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val strokeWidth = max(1f, density)
    private val src = Rect()
    private val dst = Rect()
    private val rect = RectF()
    private val insetRect = RectF()
    private val clipPath = Path()
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        dst.set(bounds)
        val width = dst.width()
        val height = dst.height()
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        if (width <= 0 || height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return

        rect.set(dst)
        clipPath.reset()
        clipPath.addRoundRect(rect, tokens.cardCornerPx, tokens.cardCornerPx, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)

        if (bitmapWidth.toLong() * height > width.toLong() * bitmapHeight) {
            val srcWidth = (bitmapHeight.toLong() * width / height).toInt().coerceIn(1, bitmapWidth)
            val left = (bitmapWidth - srcWidth) / 2
            src.set(left, 0, left + srcWidth, bitmapHeight)
        } else {
            val srcHeight = (bitmapWidth.toLong() * height / width).toInt().coerceIn(1, bitmapHeight)
            val top = (bitmapHeight - srcHeight) / 2
            src.set(0, top, bitmapWidth, top + srcHeight)
        }

        bitmapPaint.alpha = drawableAlpha
        canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
        overlayPaint.color = scaleAlpha(overlayColor, drawableAlpha)
        canvas.drawRect(rect, overlayPaint)
        if (shadowEnabled) {
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
        val alpha = (24 * drawableAlpha / 255f).toInt().coerceIn(0, 255)
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
        canvas.drawRoundRect(rect, tokens.cardCornerPx, tokens.cardCornerPx, shadowPaint)
        shadowPaint.shader = null
    }

    private fun drawStroke(canvas: Canvas) {
        insetRect.set(rect)
        val inset = strokeWidth * 0.5f
        insetRect.inset(inset, inset)
        val radius = (tokens.cardCornerPx - inset).coerceAtLeast(0f)
        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = scaleAlpha(settingsDialogStrokeColor(tokens), drawableAlpha)
        canvas.drawRoundRect(insetRect, radius, radius, strokePaint)
    }
}

internal fun dialogThemeFor(context: Context): Int {
    return if (UiStyle.tokens(context).night) {
        android.R.style.Theme_DeviceDefault_Dialog_Alert
    } else {
        android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
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
