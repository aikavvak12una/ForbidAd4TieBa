package com.forbidad4tieba.hook.ui.settings.glass

import com.forbidad4tieba.hook.config.HomeGlassPreferences
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHostDarkModeBridge
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassImageCache
import com.forbidad4tieba.hook.ui.HomeNativeGlassDialogPreviewStyle
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageAnalysis
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageFiles
import com.forbidad4tieba.hook.ui.HomeNativeGlassImagePickerBridge
import com.forbidad4tieba.hook.ui.HomeNativeGlassImageSelectionState
import com.forbidad4tieba.hook.ui.MaxHeightScrollView
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.applyUnifiedDialogCardStyle
import com.forbidad4tieba.hook.ui.showSettingsDialog
import com.forbidad4tieba.hook.ui.createDivider
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.copyHomeNativeGlassImageState
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.createHomeNativeGlassModeConfigState
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.homeNativeGlassPreviewBitmapKey
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.homeNativeGlassPreviewStyleFromModeState
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.homeNativeGlassStyleFromModeState
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormState.putHomeNativeGlassStyle
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.addHomeNativeGlassSettingRow
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.createHomeNativeGlassImagePickerRow
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.createHomeNativeGlassModeSelectorRow
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.createHomeNativeGlassSwitchRow
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.createSeekBarRow
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassFormViews.refreshHomeNativeGlassStyledViews
import com.forbidad4tieba.hook.ui.settings.glass.HomeNativeGlassTintPalette.createHomeNativeGlassTintColorRow
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.utils.ReflectionUtils
import kotlin.concurrent.thread

internal object HomeNativeGlassForm {
    private val blurCacheLock = Any()

    fun showHomeNativeGlassDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            ReflectionUtils.findActivityFromContext(context)?.let { activity ->
                HomeNativeGlassHostDarkModeBridge.cacheFromActivity(activity)
            }
            val dialogRoot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val modeSelectorContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, 0, padding, padding)
            }
            val scrollMaxHeight = (context.resources.displayMetrics.heightPixels * 0.62f)
                .toInt()
                .coerceAtLeast((220 * density).toInt())

            val lightModeState = createHomeNativeGlassModeConfigState(
                HomeGlassPreferences.readHomeNativeGlassStyle(
                    prefs,
                    HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
                )
            )
            val darkModeState = createHomeNativeGlassModeConfigState(
                HomeGlassPreferences.readHomeNativeGlassStyle(
                    prefs,
                    HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS,
                )
            )
            var selectedDarkMode = HomeNativeGlassHostDarkModeBridge.isDarkModeEnabled() == true
            fun currentModeState(): HomeNativeGlassModeConfigState {
                return if (selectedDarkMode) darkModeState else lightModeState
            }
            var loadingVisibleModeState = false
            var refreshVisibleModePreview: (() -> Unit)? = null
            var persistVisibleModeChanges: (() -> Unit)? = null
            fun commitVisibleModeChanges() {
                if (!loadingVisibleModeState) persistVisibleModeChanges?.invoke()
            }
            var refreshModeSelector: ((Boolean) -> Unit)? = null
            var homeNativeGlassDialog: AlertDialog? = null
            val mainHandler = Handler(Looper.getMainLooper())
            var previewRequestSerial = 0
            var visiblePreviewBitmap: Bitmap? = null
            var visiblePreviewBitmapKey: HomeNativeGlassPreviewBitmapKey? = null
            fun clearVisiblePreviewBitmap() {
                visiblePreviewBitmap?.let { bitmap ->
                    runCatching { bitmap.recycle() }
                }
                visiblePreviewBitmap = null
                visiblePreviewBitmapKey = null
            }
            fun currentDialogPreviewStyle(
                keepPreviousPreviewForSameSource: Boolean = false,
            ): HomeNativeGlassDialogPreviewStyle {
                val style = homeNativeGlassPreviewStyleFromModeState(currentModeState())
                val previewKey = homeNativeGlassPreviewBitmapKey(style)
                val previewBitmap = visiblePreviewBitmap.takeIf {
                    visiblePreviewBitmapKey == previewKey ||
                        (keepPreviousPreviewForSameSource &&
                            visiblePreviewBitmapKey?.sourcePath == previewKey.sourcePath)
                }
                return HomeNativeGlassDialogPreviewStyle(
                    style = style,
                    darkMode = selectedDarkMode,
                    previewBitmap = previewBitmap,
                )
            }
            fun applyHomeNativeGlassDialogPreview(previewStyle: HomeNativeGlassDialogPreviewStyle) {
                refreshHomeNativeGlassStyledViews(
                    dialogRoot,
                    UiStyle.homeNativeGlassPreviewTokens(
                        context,
                        previewStyle.style,
                        previewStyle.darkMode,
                    ),
                    density,
                )
                val dialogWindow = homeNativeGlassDialog?.window ?: return
                applyUnifiedDialogCardStyle(
                    window = dialogWindow,
                    density = density,
                    homeNativeGlassPreviewStyle = previewStyle,
                )
            }
            fun scheduleVisibleModeBlurPreview(previewStyle: HomeNativeGlassDialogPreviewStyle) {
                val style = previewStyle.style
                val requestKey = homeNativeGlassPreviewBitmapKey(style)
                if (requestKey.sourcePath.isBlank()) {
                    previewRequestSerial++
                    clearVisiblePreviewBitmap()
                    return
                }
                if (visiblePreviewBitmapKey?.sourcePath != requestKey.sourcePath) {
                    clearVisiblePreviewBitmap()
                }
                if (visiblePreviewBitmapKey == requestKey && visiblePreviewBitmap != null) return

                val requestSerial = ++previewRequestSerial
                thread(name = "tbhook-home-native-glass-preview-blur", isDaemon = true) {
                    val previewBitmap = runCatching {
                        HomeNativeGlassImageCache.createBlurPreviewBitmap(
                            sourcePath = requestKey.sourcePath,
                            blurPercent = requestKey.blurPercent,
                            tintOffset = requestKey.tintAlphaPercent,
                            appleMaterial = true,
                        )
                    }.getOrNull()
                    mainHandler.post {
                        val dialog = homeNativeGlassDialog
                        val currentKey = homeNativeGlassPreviewBitmapKey(
                            homeNativeGlassPreviewStyleFromModeState(currentModeState())
                        )
                        val isStaleResult =
                            requestSerial != previewRequestSerial ||
                                dialog?.isShowing != true ||
                                requestKey != currentKey
                        if (isStaleResult) {
                            runCatching { previewBitmap?.recycle() }
                            return@post
                        }
                        if (previewBitmap == null) {
                            clearVisiblePreviewBitmap()
                            applyHomeNativeGlassDialogPreview(currentDialogPreviewStyle())
                            return@post
                        }
                        val previousBitmap = visiblePreviewBitmap
                        visiblePreviewBitmap = previewBitmap
                        visiblePreviewBitmapKey = requestKey
                        applyHomeNativeGlassDialogPreview(currentDialogPreviewStyle())
                        if (previousBitmap !== previewBitmap) {
                            runCatching { previousBitmap?.recycle() }
                        }
                    }
                }
            }
            fun requestVisibleModePreviewRefresh() {
                if (!loadingVisibleModeState) {
                    refreshVisibleModePreview?.invoke()
                }
            }
            fun refreshHomeNativeGlassDialogPreview() {
                val previewStyle = currentDialogPreviewStyle(
                    keepPreviousPreviewForSameSource = true,
                )
                applyHomeNativeGlassDialogPreview(previewStyle)
                scheduleVisibleModeBlurPreview(previewStyle)
            }

            val visibleImageState = HomeNativeGlassImageSelectionState(
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
            )
            copyHomeNativeGlassImageState(visibleImageState, currentModeState().imageState)
            val tintColorRowAndRefresh = createHomeNativeGlassTintColorRow(
                context = context,
                state = visibleImageState,
                density = density,
                onTintChanged = {
                    requestVisibleModePreviewRefresh()
                    commitVisibleModeChanges()
                },
            )
            val tintColorRefresh = tintColorRowAndRefresh.second
            fun refreshTintColorAndPreview() {
                tintColorRefresh()
                requestVisibleModePreviewRefresh()
                commitVisibleModeChanges()
            }

            val tintAlphaRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_DESC,
                minValue = HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                maxValue = HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                value = currentModeState().tintAlphaPercent,
                suffix = "",
                density = density,
                onValueCommitted = {
                    requestVisibleModePreviewRefresh()
                    commitVisibleModeChanges()
                },
            )
            val tintAlphaSeekBar = tintAlphaRowAndSeekBar.second
            val backgroundImageImportCallback: (HomeNativeGlassImageAnalysis) -> Unit = { analysis ->
                tintAlphaSeekBar.progress = analysis.tintAlphaPercent.coerceIn(
                    HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ) - HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
            }
            val backgroundImageRowAndDisplay = createHomeNativeGlassImagePickerRow(
                context = context,
                state = visibleImageState,
                density = density,
                darkModeProvider = { selectedDarkMode },
                refreshPalette = ::refreshTintColorAndPreview,
                onImportedAnalysis = backgroundImageImportCallback,
            )
            val backgroundImageDisplay = backgroundImageRowAndDisplay.second

            val blurRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_CARD_BLUR_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_CARD_BLUR_DESC,
                minValue = HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                maxValue = HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                value = currentModeState().cardBlurPercent,
                suffix = "%",
                density = density,
                onValueCommitted = {
                    requestVisibleModePreviewRefresh()
                    commitVisibleModeChanges()
                },
            )
            val blurSeekBar = blurRowAndSeekBar.second

            val radiusRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_DESC,
                minValue = HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                maxValue = HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                value = currentModeState().cardRadiusDp,
                suffix = "dp",
                density = density,
                onValueChanged = { requestVisibleModePreviewRefresh() },
                onValueCommitted = { commitVisibleModeChanges() },
            )
            val radiusSeekBar = radiusRowAndSeekBar.second

            val strokeSwitch = createHomeNativeGlassSwitchRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_STROKE_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_STROKE_DESC,
                checked = currentModeState().strokeEnabled,
                density = density,
            )

            val shadowRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_SHADOW_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_SHADOW_DESC,
                minValue = HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                maxValue = HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                value = currentModeState().shadowStrengthPercent,
                suffix = "%",
                density = density,
                onValueChanged = { requestVisibleModePreviewRefresh() },
                onValueCommitted = { commitVisibleModeChanges() },
            )
            val shadowSeekBar = shadowRowAndSeekBar.second

            fun captureVisibleModeState() {
                val state = currentModeState()
                val previousImagePath = state.imageState.path.trim()
                val previousTintAlphaPercent = state.tintAlphaPercent
                val previousCardBlurPercent = state.cardBlurPercent
                copyHomeNativeGlassImageState(state.imageState, visibleImageState)
                state.tintAlphaPercent = (
                    tintAlphaSeekBar.progress + HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    ).coerceIn(
                    HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                )
                state.cardBlurPercent = (
                    blurSeekBar.progress + HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    ).coerceIn(
                    HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                    HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                )
                state.cardRadiusDp = (
                    radiusSeekBar.progress + HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    ).coerceIn(
                    HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                    HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                )
                state.strokeEnabled = strokeSwitch.second.isChecked
                state.shadowStrengthPercent = (
                    shadowSeekBar.progress + HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    ).coerceIn(
                    HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                    HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                )
                if (
                    state.imageState.path.trim() != previousImagePath ||
                    state.tintAlphaPercent != previousTintAlphaPercent ||
                    state.cardBlurPercent != previousCardBlurPercent
                ) {
                    state.blurCacheImagePath = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
                }
            }

            persistVisibleModeChanges = persist@{
                captureVisibleModeState()
                val lightStyle = homeNativeGlassPreviewStyleFromModeState(lightModeState)
                val darkStyle = homeNativeGlassPreviewStyleFromModeState(darkModeState)
                if (lightStyle == HomeGlassPreferences.readHomeNativeGlassStyle(prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS) &&
                    darkStyle == HomeGlassPreferences.readHomeNativeGlassStyle(prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS)
                ) return@persist
                val editor = prefs.edit()
                putHomeNativeGlassStyle(editor, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS, lightStyle)
                putHomeNativeGlassStyle(editor, HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS, darkStyle)
                editor
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_COLOR)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_PALETTE)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_STROKE_ENABLED)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED)
                    .remove(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT)
                    .remove(HomeGlassPreferences.KEY_ENABLE_HOME_TAB_DYNAMIC_TINT)
                    .putBoolean(HomeGlassPreferences.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED, true)
                    .apply()
                fun updateCache(state: HomeNativeGlassModeConfigState, style: HomeGlassPreferences.HomeNativeGlassStyleConfig, path: String) {
                    if (homeNativeGlassPreviewBitmapKey(homeNativeGlassPreviewStyleFromModeState(state)) == homeNativeGlassPreviewBitmapKey(style)) {
                        state.blurCacheImagePath = path
                    }
                }
                ensureSavedBlurCache(context, prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS, lightStyle, "light") {
                    updateCache(lightModeState, lightStyle, it)
                }
                ensureSavedBlurCache(context, prefs, HomeGlassPreferences.HOME_NATIVE_GLASS_DARK_STYLE_KEYS, darkStyle, "dark") {
                    updateCache(darkModeState, darkStyle, it)
                }
            }

            fun loadVisibleModeState(darkMode: Boolean) {
                val state = if (darkMode) darkModeState else lightModeState
                loadingVisibleModeState = true
                try {
                    copyHomeNativeGlassImageState(visibleImageState, state.imageState)
                    backgroundImageDisplay.text = HomeNativeGlassImageFiles.displayText(visibleImageState.path)
                    tintColorRefresh()
                    tintAlphaSeekBar.progress = state.tintAlphaPercent.coerceIn(
                        HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                        HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    ) - HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    blurSeekBar.progress = state.cardBlurPercent.coerceIn(
                        HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                        HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                    ) - HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    radiusSeekBar.progress = state.cardRadiusDp.coerceIn(
                        HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                        HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                    ) - HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    strokeSwitch.second.isChecked = state.strokeEnabled
                    shadowSeekBar.progress = state.shadowStrengthPercent.coerceIn(
                        HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                        HomeGlassPreferences.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                    ) - HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                } finally {
                    loadingVisibleModeState = false
                }
            }

            refreshVisibleModePreview = {
                captureVisibleModeState()
                refreshModeSelector?.invoke(selectedDarkMode)
                refreshHomeNativeGlassDialogPreview()
            }
            strokeSwitch.second.setOnCheckedChangeListener { _, _ ->
                requestVisibleModePreviewRefresh()
                commitVisibleModeChanges()
            }

            fun selectMode(darkMode: Boolean) {
                if (selectedDarkMode == darkMode) return
                HomeNativeGlassImagePickerBridge.clearIfState(visibleImageState)
                captureVisibleModeState()
                selectedDarkMode = darkMode
                loadVisibleModeState(darkMode)
                refreshModeSelector?.invoke(darkMode)
                refreshHomeNativeGlassDialogPreview()
            }
            val modeSelectorRowAndRefresh = createHomeNativeGlassModeSelectorRow(
                context = context,
                density = density,
                selectedDarkMode = selectedDarkMode,
                lightModeState = lightModeState,
                darkModeState = darkModeState,
                onSelected = ::selectMode,
            )
            refreshModeSelector = modeSelectorRowAndRefresh.second

            addHomeNativeGlassSettingRow(modeSelectorContainer, modeSelectorRowAndRefresh.first, density, topMarginDp = 0)
            modeSelectorContainer.addView(createDivider(context, padding))
            addHomeNativeGlassSettingRow(root, backgroundImageRowAndDisplay.first, density)
            addHomeNativeGlassSettingRow(root, tintColorRowAndRefresh.first, density)
            addHomeNativeGlassSettingRow(root, tintAlphaRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, blurRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, radiusRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, shadowRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, strokeSwitch.first, density)
            dialogRoot.addView(modeSelectorContainer)
            dialogRoot.addView(
                MaxHeightScrollView(context, scrollMaxHeight).apply {
                    isFillViewport = false
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    clipToPadding = false
                    addView(
                        root,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            val initialPreviewStyle = currentDialogPreviewStyle()
            refreshHomeNativeGlassStyledViews(
                dialogRoot,
                UiStyle.homeNativeGlassPreviewTokens(context, initialPreviewStyle.style, initialPreviewStyle.darkMode),
                density,
            )
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_NATIVE_GLASS_DIALOG_TITLE)
                .setView(dialogRoot)
                .setNeutralButton(UiText.Settings.HOME_NATIVE_GLASS_RESTORE_DEFAULTS, null)
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            homeNativeGlassDialog = dialog
            dialog.setOnShowListener {
                scheduleVisibleModeBlurPreview(initialPreviewStyle)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    loadingVisibleModeState = true
                    try {
                        tintAlphaSeekBar.progress = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT -
                            HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                        blurSeekBar.progress = HomeGlassPreferences.APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT -
                            HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                        radiusSeekBar.progress = HomeGlassPreferences.APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP -
                            HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                        visibleImageState.path = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH
                        visibleImageState.tintColor = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                        visibleImageState.paletteColors = emptyList()
                        visibleImageState.defaultTintColor = null
                        backgroundImageDisplay.text = HomeNativeGlassImageFiles.displayText(visibleImageState.path)
                        tintColorRefresh()
                        strokeSwitch.second.isChecked = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED
                        shadowSeekBar.progress = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT -
                            HomeGlassPreferences.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } finally {
                        loadingVisibleModeState = false
                    }
                    requestVisibleModePreviewRefresh()
                    commitVisibleModeChanges()
                }
            }
            dialog.setOnDismissListener {
                HomeNativeGlassImagePickerBridge.clearIfState(visibleImageState)
                previewRequestSerial++
                clearVisiblePreviewBitmap()
                homeNativeGlassDialog = null
            }
            dialog.showSettingsDialog(density, homeNativeGlassPreviewStyle = initialPreviewStyle)
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeNativeGlassDialog failed: ${t.message}")
        }
    }

    private fun ensureSavedBlurCache(
        context: Context,
        prefs: android.content.SharedPreferences,
        keys: HomeGlassPreferences.HomeNativeGlassStyleKeys,
        style: HomeGlassPreferences.HomeNativeGlassStyleConfig,
        modeName: String,
        onReady: (String) -> Unit,
    ) {
        if (style.backgroundImagePath.isBlank() || style.blurCacheImagePath.isNotBlank()) return
        val requestKey = homeNativeGlassPreviewBitmapKey(style)
        val appContext = context.applicationContext ?: context
        fun isCurrentRequest(): Boolean = homeNativeGlassPreviewBitmapKey(
            HomeGlassPreferences.readHomeNativeGlassStyle(prefs, keys),
        ) == requestKey
        thread(name = "tbhook-home-native-glass-blur-cache", isDaemon = true) {
            // The cache writer removes older files, so generations must never run concurrently.
            val path = synchronized(blurCacheLock) {
                if (!isCurrentRequest()) return@synchronized ""
                runCatching {
                    HomeNativeGlassImageCache.ensureBlurCache(
                        context = appContext,
                        sourcePath = style.backgroundImagePath,
                        blurPercent = style.cardBlurPercent,
                        tintOffset = style.tintAlphaPercent,
                        appleMaterial = true,
                        cacheNamespace = modeName,
                    )
                }.onFailure {
                    XposedCompat.logW("[SettingsMenuHook] home native blur cache failed: $modeName: ${it.message}")
                }.getOrDefault("")
            }
            if (path.isBlank()) return@thread
            Handler(Looper.getMainLooper()).post {
                if (!isCurrentRequest()) return@post
                if (keys.blurCacheImagePath.read(prefs) != path) {
                    prefs.edit().putString(keys.blurCacheImagePath.key, path).apply()
                }
                onReady(path)
            }
        }
    }

}
