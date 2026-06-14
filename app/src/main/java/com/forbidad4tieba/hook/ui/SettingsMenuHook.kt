package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.feature.signin.AutoSignInManager
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreCatalog
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassDynamicTintCache
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHostDarkModeBridge
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassImageCache
import com.forbidad4tieba.hook.utils.ReflectionUtils
import com.forbidad4tieba.hook.utils.ViewExt
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.File
import java.lang.reflect.Field
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.system.exitProcess

object SettingsMenuHook {
    private const val RESTRICTED_FEATURE_UNLOCK_TAP_COUNT = 7
    private const val RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS = 5
    private const val INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS = 10
    private const val SCAN_RUNNING_PULSE_INITIAL_DELAY_MS = 1000L
    private const val SCAN_RUNNING_PULSE_INTERVAL_MS = 1500L
    private const val REQUEST_HOME_NATIVE_GLASS_IMAGE = 0x4E47
    private const val HOME_NATIVE_GLASS_SOURCE_DIR_NAME = "home_native_glass"
    private const val HOME_NATIVE_GLASS_SOURCE_FILE_PREFIX = "source_"
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS = 6
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE = 96
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_PIXEL_ALPHA = 32
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE = 34
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_LUMA = 16
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MAX_LUMA = 240
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_TARGET_LUMA = 144
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_TARGET_LUMA_RANGE = 128
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_HUE_DISTANCE = 20.0
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_HUE_SATURATION_FLOOR = 0.12
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_SAMPLE_EDGE = 48
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_MIN_LUMA = 24
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_MAX_LUMA = 232
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_CHROMA_BIAS = 32
    private const val HOME_NATIVE_GLASS_AUTO_TINT_MID_LUMA = 128
    private const val HOME_NATIVE_GLASS_AUTO_TINT_TRIGGER_DISTANCE = 72
    private const val HOME_NATIVE_GLASS_AUTO_TINT_MAX_ABS_PERCENT = 22
    private const val HOME_NATIVE_GLASS_MODE_SELECTOR_MIN_FILL_ALPHA = 36
    private const val HOME_NATIVE_GLASS_MODE_SELECTOR_MAX_FILL_ALPHA = 128

    private class HomeNativeGlassImageSelectionState(
        var path: String,
        var tintColor: Int,
    ) {
        var paletteColors: List<Int> = emptyList()
        var defaultTintColor: Int? = null
    }

    private data class HomeNativeGlassImageAnalysis(
        val paletteColors: List<Int>,
        val defaultTintColor: Int?,
        val tintAlphaPercent: Int,
    )

    private class HomeNativeGlassModeConfigState(
        val imageState: HomeNativeGlassImageSelectionState,
        var blurCacheImagePath: String,
        var tintAlphaPercent: Int,
        var cardBlurPercent: Int,
        var cardRadiusDp: Int,
        var strokeEnabled: Boolean,
        var shadowStrengthPercent: Int,
    )

    private enum class HomeNativeGlassStyleRole {
        ROW_TITLE,
        ROW_DESCRIPTION,
        MUTED_TEXT,
        ACCENT_TEXT,
        BUTTON_ACCENT,
        BUTTON_SECONDARY,
        INPUT_TEXT,
        SEEK_BAR,
        SWITCH,
    }

    private data class HomeNativeGlassPreviewBitmapKey(
        val sourcePath: String,
        val blurPercent: Int,
        val tintAlphaPercent: Int,
    )

    private data class PendingHomeNativeGlassImagePick(
        val contextRef: WeakReference<Context>,
        val displayRef: WeakReference<TextView>,
        val state: HomeNativeGlassImageSelectionState,
        val darkMode: Boolean,
        val refreshPalette: (() -> Unit)?,
        val onImportedAnalysis: ((HomeNativeGlassImageAnalysis) -> Unit)?,
    )

    private data class SwitchItem(
        val label: String,
        val description: String,
        val prefKey: String,
        val supported: Boolean,
        val defaultValue: Boolean = false,
        val actionIcon: String? = null,
        val linkedPrefKeys: List<String> = emptyList(),
        val onActionClick: (() -> Unit)? = null,
    )

    private data class SettingGroup(
        val name: String,
        val items: List<SwitchItem>
    )

    private data class SwitchRuntimeSupport(
        val supported: Boolean,
        val partial: Boolean,
        val note: String?,
    )

    private data class ModelScoreUiItem(
        val key: String,
        val label: String,
        val description: String,
    )

    private data class VersionDisplayInfo(
        val tiebaVersion: String,
        val tiebaBuildType: String,
        val moduleVersion: String,
        val moduleBuildType: String,
    )

    private val modelScoreUiItems = listOf(
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.MSD_SCORE,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_SCORE_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_SCORE_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.MSD_DURATION_SCORE,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.DNN_PB_DUR_CTR_0,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_1,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_2,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_3,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CDNN_LTR,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CDNN_LTR_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CDNN_LTR_DESC,
        ),
    )

    private val sSettingsFieldCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Field>())
    private val homeNativeGlassImagePickerResultHookInstalled = AtomicBoolean(false)
    private val homeNativeGlassHostDarkModeBridgeHookInstalled = AtomicBoolean(false)
    private val homeNativeGlassImagePickerActivityResultHooks =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Boolean>())
    @Volatile private var pendingHomeNativeGlassImagePick: PendingHomeNativeGlassImagePick? = null

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        installHomeNativeGlassHostDarkModeBridgeHook(cl, symbols)
        val className = symbols.settingsClass
        val methodName = symbols.settingsInitMethod
        val containerField = symbols.settingsContainerField
        if (className == null || methodName == null || containerField == null) {
            XposedCompat.log("[SettingsMenuHook] SKIP - missing symbols: class=$className, method=$methodName, field=$containerField")
            return
        }
        try {
            val navClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.NAVIGATION_BAR_CLASS, cl)
            if (navClass == null) {
                XposedCompat.log("[SettingsMenuHook] NavigationBar class NOT FOUND")
                return
            }
            val settingsClass = XposedCompat.findClassOrNull(className, cl)
            if (settingsClass == null) {
                XposedCompat.log("[SettingsMenuHook] class NOT FOUND: $className")
                return
            }
            precacheSettingsContainerField(settingsClass, containerField)
            // Try the older Context/NavigationBar signature before the View signature.
            val method = XposedCompat.findMethodOrNull(settingsClass, methodName, Context::class.java, navClass)
                ?: XposedCompat.findMethodOrNull(settingsClass, methodName, View::class.java)
            if (method == null) {
                XposedCompat.log("[SettingsMenuHook] method NOT FOUND: $className.$methodName")
                return
            }
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val settingsOwner = chain.thisObject
                HomeNativeGlassHostDarkModeBridge.cacheFromController(settingsOwner)
                XposedCompat.logD("[SettingsMenuHook] > settings init intercepted")
                val context = chain.args.firstOrNull { it is Context } as? Context
                    ?: (chain.args.firstOrNull { it is View } as? View)?.context
                try {
                    val settingsContainer = resolveSettingsContainer(chain.thisObject, containerField)
                    if (settingsContainer != null && context != null && ViewExt.markSettingsLongPressBound(settingsContainer)) {
                        settingsContainer.setOnLongClickListener {
                            HomeNativeGlassHostDarkModeBridge.cacheFromController(settingsOwner)
                            showModuleSettingsDialog(settingsContainer.context ?: context, cl)
                            true
                        }
                        XposedCompat.logD("[SettingsMenuHook] > long-press listener bound")
                    }
                } catch (t: Throwable) { XposedCompat.logD { "SettingsMenuHook: ${t.message}" } }
                result
            }
            XposedCompat.log("[SettingsMenuHook] hook INSTALLED: $className.$methodName")
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED ($className.$methodName): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installHomeNativeGlassHostDarkModeBridgeHook(
        cl: ClassLoader,
        symbols: HookSymbols,
    ) {
        val mod = XposedCompat.module ?: return
        val targets = HookSymbolResolver.resolveHomeNativeGlassHostDarkModeSwitchSymbols(
            cl,
            symbols,
        ) ?: return
        HomeNativeGlassHostDarkModeBridge.configure(targets)
        if (!homeNativeGlassHostDarkModeBridgeHookInstalled.compareAndSet(false, true)) return
        try {
            val onCreateMethod = XposedCompat.findMethodOrNull(
                targets.moreActivityClass,
                "onCreate",
                Bundle::class.java,
            ) ?: run {
                homeNativeGlassHostDarkModeBridgeHookInstalled.set(false)
                XposedCompat.logW(
                    "[SettingsMenuHook] host dark mode bridge skipped: " +
                        "${targets.moreActivityClass.name}.onCreate(Bundle) missing",
                )
                return
            }
            mod.hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                HomeNativeGlassHostDarkModeBridge.cacheFromActivity(chain.thisObject)
                result
            }
            mod.hook(targets.switchCallbackMethod).intercept { chain ->
                val result = chain.proceed()
                HomeNativeGlassHostDarkModeBridge.cacheFromHostCallback(
                    activity = chain.thisObject,
                    switchView = chain.args.getOrNull(0),
                    state = chain.args.getOrNull(1),
                )
                result
            }
            XposedCompat.log(
                "[SettingsMenuHook] host dark mode bridge hook INSTALLED: " +
                    "${targets.moreActivityClass.name}.onCreate/" +
                    targets.switchCallbackMethod.name,
            )
        } catch (t: Throwable) {
            homeNativeGlassHostDarkModeBridgeHookInstalled.set(false)
            XposedCompat.logW("[SettingsMenuHook] host dark mode bridge install failed: ${t.message}")
        }
    }

    private fun installHomeNativeGlassImagePickerResultHook() {
        val mod = XposedCompat.module ?: return
        if (!homeNativeGlassImagePickerResultHookInstalled.compareAndSet(false, true)) return
        try {
            val method = Activity::class.java.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java,
            ).apply { isAccessible = true }
            mod.hook(method).intercept { chain ->
                val requestCode = chain.args.getOrNull(0) as? Int
                val resultCode = chain.args.getOrNull(1) as? Int
                val data = chain.args.getOrNull(2) as? Intent
                if (shouldHandleHomeNativeGlassImagePickerResult(
                        activity = chain.thisObject as? Activity,
                        requestCode = requestCode,
                        resultCode = resultCode,
                        data = data,
                    )
                ) {
                    return@intercept null
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            homeNativeGlassImagePickerResultHookInstalled.set(false)
            XposedCompat.logW("[SettingsMenuHook] install home native image picker result hook failed: ${t.message}")
        }
    }

    private fun installHomeNativeGlassImagePickerResultHook(activity: Activity) {
        val mod = XposedCompat.module ?: return
        val method = ReflectionUtils.findMethodInHierarchy(
            activity.javaClass,
            "onActivityResult",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Intent::class.java,
        ) ?: return
        if (method.declaringClass == Activity::class.java) return
        synchronized(homeNativeGlassImagePickerActivityResultHooks) {
            if (homeNativeGlassImagePickerActivityResultHooks.containsKey(method.declaringClass)) return
            homeNativeGlassImagePickerActivityResultHooks[method.declaringClass] = true
        }
        try {
            mod.hook(method).intercept { chain ->
                val requestCode = chain.args.getOrNull(0) as? Int
                val resultCode = chain.args.getOrNull(1) as? Int
                val data = chain.args.getOrNull(2) as? Intent
                if (shouldHandleHomeNativeGlassImagePickerResult(
                        activity = chain.thisObject as? Activity,
                        requestCode = requestCode,
                        resultCode = resultCode,
                        data = data,
                    )
                ) {
                    return@intercept null
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            homeNativeGlassImagePickerActivityResultHooks.remove(method.declaringClass)
            XposedCompat.logW(
                "[SettingsMenuHook] install activity image picker result hook failed: " +
                    "${method.declaringClass.name}: ${t.message}"
            )
        }
    }

    private fun shouldHandleHomeNativeGlassImagePickerResult(
        activity: Activity?,
        requestCode: Int?,
        resultCode: Int?,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_HOME_NATIVE_GLASS_IMAGE || pendingHomeNativeGlassImagePick == null) {
            return false
        }
        handleHomeNativeGlassImagePickerResult(
            activity = activity,
            resultCode = resultCode,
            data = data,
        )
        return true
    }

    private fun launchHomeNativeGlassImagePicker(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        display: TextView,
        darkMode: Boolean,
        refreshPalette: (() -> Unit)? = null,
        onImportedAnalysis: ((HomeNativeGlassImageAnalysis) -> Unit)? = null,
    ) {
        val activity = ReflectionUtils.findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, UiText.Settings.CONTEXT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            return
        }
        installHomeNativeGlassImagePickerResultHook()
        installHomeNativeGlassImagePickerResultHook(activity)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }.apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pendingHomeNativeGlassImagePick = PendingHomeNativeGlassImagePick(
            contextRef = WeakReference(activity),
            displayRef = WeakReference(display),
            state = state,
            darkMode = darkMode,
            refreshPalette = refreshPalette,
            onImportedAnalysis = onImportedAnalysis,
        )
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_HOME_NATIVE_GLASS_IMAGE)
        } catch (t: Throwable) {
            pendingHomeNativeGlassImagePick = null
            XposedCompat.logW("[SettingsMenuHook] launch home native image picker failed: ${t.message}")
            Toast.makeText(
                activity,
                UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PICKER_UNAVAILABLE,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun handleHomeNativeGlassImagePickerResult(
        activity: Activity?,
        resultCode: Int?,
        data: Intent?,
    ) {
        val pending = pendingHomeNativeGlassImagePick ?: return
        if (resultCode != Activity.RESULT_OK) {
            pendingHomeNativeGlassImagePick = null
            return
        }
        val uri = data?.data
        val context = pending.contextRef.get() ?: activity
        if (uri == null || context == null) {
            pendingHomeNativeGlassImagePick = null
            return
        }
        thread(name = "tbhook-home-native-glass-image-import", isDaemon = true) {
            val copiedPath = runCatching {
                copyHomeNativeGlassImageToPrivateFile(context, uri, pending.darkMode)
            }.onFailure {
                XposedCompat.logW("[SettingsMenuHook] import home native image failed: ${it.message}")
            }.getOrNull()
            val imageAnalysis = if (copiedPath.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    analyzeHomeNativeGlassImage(copiedPath, pending.darkMode)
                }.onFailure {
                    XposedCompat.logW("[SettingsMenuHook] analyze home native image failed: ${it.message}")
                }.getOrNull()
            }
            Handler(Looper.getMainLooper()).post {
                if (pendingHomeNativeGlassImagePick !== pending) return@post
                if (copiedPath.isNullOrBlank()) {
                    Toast.makeText(
                        context,
                        UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_IMPORT_FAILED,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pending.state.path = copiedPath
                    pending.state.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                    pending.state.paletteColors = imageAnalysis?.paletteColors.orEmpty()
                    pending.state.defaultTintColor = imageAnalysis?.defaultTintColor
                    pending.displayRef.get()?.text = homeNativeGlassImageDisplayText(copiedPath)
                    pending.refreshPalette?.invoke()
                    imageAnalysis?.let { pending.onImportedAnalysis?.invoke(it) }
                    Toast.makeText(
                        context,
                        UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_IMPORTED,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                pendingHomeNativeGlassImagePick = null
            }
        }
    }

    private fun analyzeHomeNativeGlassImage(path: String, darkMode: Boolean): HomeNativeGlassImageAnalysis? {
        val file = File(path.trim())
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val sampleEdge = max(
            HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE,
            HOME_NATIVE_GLASS_DEFAULT_TINT_SAMPLE_EDGE,
        )
        var sampleSize = 1
        while (max(width, height) / sampleSize > sampleEdge) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        return try {
            val averageLuma = computeHomeNativeGlassAverageLuma(bitmap)
            HomeNativeGlassImageAnalysis(
                paletteColors = extractHomeNativeGlassTintPalette(bitmap),
                defaultTintColor = extractHomeNativeGlassDefaultTintColor(bitmap),
                tintAlphaPercent = averageLuma?.let {
                    homeNativeGlassAutoTintAlphaPercent(it, darkMode)
                }
                    ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun extractHomeNativeGlassDefaultTintColor(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val step = (max(width, height) / HOME_NATIVE_GLASS_DEFAULT_TINT_SAMPLE_EDGE).coerceAtLeast(1)
        var weightedRed = 0L
        var weightedGreen = 0L
        var weightedBlue = 0L
        var weightSum = 0L
        var fallbackRed = 0L
        var fallbackGreen = 0L
        var fallbackBlue = 0L
        var fallbackCount = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= HOME_NATIVE_GLASS_TINT_PALETTE_MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    fallbackRed += red.toLong()
                    fallbackGreen += green.toLong()
                    fallbackBlue += blue.toLong()
                    fallbackCount++
                    val maxChannel = max(red, max(green, blue))
                    val minChannel = red.coerceAtMost(green).coerceAtMost(blue)
                    val chroma = maxChannel - minChannel
                    val luma = (red * 299 + green * 587 + blue * 114) / 1000
                    if (luma in HOME_NATIVE_GLASS_DEFAULT_TINT_MIN_LUMA..HOME_NATIVE_GLASS_DEFAULT_TINT_MAX_LUMA) {
                        val weight = (alpha * (chroma + HOME_NATIVE_GLASS_DEFAULT_TINT_CHROMA_BIAS)).toLong()
                        weightedRed += red.toLong() * weight
                        weightedGreen += green.toLong() * weight
                        weightedBlue += blue.toLong() * weight
                        weightSum += weight
                    }
                }
                x += step
            }
            y += step
        }
        if (weightSum > 0L) {
            return Color.rgb(
                (weightedRed / weightSum).toInt().coerceIn(0, 255),
                (weightedGreen / weightSum).toInt().coerceIn(0, 255),
                (weightedBlue / weightSum).toInt().coerceIn(0, 255),
            )
        }
        if (fallbackCount <= 0L) return null
        return Color.rgb(
            (fallbackRed / fallbackCount).toInt().coerceIn(0, 255),
            (fallbackGreen / fallbackCount).toInt().coerceIn(0, 255),
            (fallbackBlue / fallbackCount).toInt().coerceIn(0, 255),
        )
    }

    private fun computeHomeNativeGlassAverageLuma(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val step = (max(width, height) / HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE).coerceAtLeast(1)
        var lumaSum = 0L
        var alphaSum = 0L
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= HOME_NATIVE_GLASS_TINT_PALETTE_MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    val luma = (red * 299 + green * 587 + blue * 114) / 1000
                    lumaSum += luma.toLong() * alpha
                    alphaSum += alpha.toLong()
                }
                x += step
            }
            y += step
        }
        if (alphaSum <= 0L) return null
        return (lumaSum / alphaSum).toInt().coerceIn(0, 255)
    }

    private fun homeNativeGlassAutoTintAlphaPercent(
        averageLuma: Int,
        darkMode: Boolean,
    ): Int {
        val luma = averageLuma.coerceIn(0, 255)
        val distanceFromMid = if (luma >= HOME_NATIVE_GLASS_AUTO_TINT_MID_LUMA) {
            luma - HOME_NATIVE_GLASS_AUTO_TINT_MID_LUMA
        } else {
            HOME_NATIVE_GLASS_AUTO_TINT_MID_LUMA - luma
        }
        if (distanceFromMid >= HOME_NATIVE_GLASS_AUTO_TINT_TRIGGER_DISTANCE) {
            return ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
        }
        val strength = (
            (HOME_NATIVE_GLASS_AUTO_TINT_TRIGGER_DISTANCE - distanceFromMid) *
                HOME_NATIVE_GLASS_AUTO_TINT_MAX_ABS_PERCENT +
                HOME_NATIVE_GLASS_AUTO_TINT_TRIGGER_DISTANCE / 2
            ) / HOME_NATIVE_GLASS_AUTO_TINT_TRIGGER_DISTANCE
        val signedStrength = if (darkMode) -strength else strength
        return signedStrength.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
    }

    private fun copyHomeNativeGlassImageToPrivateFile(
        context: Context,
        uri: Uri,
        darkMode: Boolean,
    ): String? {
        val appContext = context.applicationContext ?: context
        val sourceDir = File(appContext.filesDir, HOME_NATIVE_GLASS_SOURCE_DIR_NAME)
        if (!sourceDir.exists() && !sourceDir.mkdirs()) return null
        val modePrefix = HOME_NATIVE_GLASS_SOURCE_FILE_PREFIX + if (darkMode) "dark_" else "light_"
        val fileName = "$modePrefix${System.currentTimeMillis()}.img"
        val targetFile = File(sourceDir, fileName)
        val tempFile = File(sourceDir, "$fileName.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        if (tempFile.length() <= 0L) {
            runCatching { tempFile.delete() }
            return null
        }
        if (!tempFile.renameTo(targetFile)) {
            runCatching {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }.getOrElse {
                runCatching { tempFile.delete() }
                return null
            }
        }
        if (!targetFile.isFile || targetFile.length() <= 0L) return null
        cleanupOldHomeNativeGlassSourceImages(sourceDir, modePrefix, targetFile.name)
        return targetFile.absolutePath
    }

    private fun cleanupOldHomeNativeGlassSourceImages(
        sourceDir: File,
        modePrefix: String,
        keepName: String,
    ) {
        runCatching {
            sourceDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.name.startsWith(modePrefix) &&
                    file.name != keepName
                ) {
                    file.delete()
                }
            }
        }
    }

    private fun homeNativeGlassImageDisplayText(path: String): String {
        val name = path.trim().takeIf { it.isNotEmpty() }?.let { File(it).name }.orEmpty()
        return if (name.isBlank()) {
            UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_NONE
        } else {
            UiText.Settings.homeNativeGlassBackgroundImageSelected(name)
        }
    }

    private fun extractHomeNativeGlassTintPalette(bitmap: Bitmap): List<Int> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()
        val step = (max(width, height) / HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE).coerceAtLeast(1)
        val buckets = HashMap<Int, HomeNativeGlassColorBucket>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha >= HOME_NATIVE_GLASS_TINT_PALETTE_MIN_PIXEL_ALPHA) {
                    val red = color shr 16 and 0xFF
                    val green = color shr 8 and 0xFF
                    val blue = color and 0xFF
                    val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
                    val bucket = buckets.getOrPut(key) { HomeNativeGlassColorBucket() }
                    bucket.weight += alpha.toLong()
                    bucket.red += red.toLong() * alpha
                    bucket.green += green.toLong() * alpha
                    bucket.blue += blue.toLong() * alpha
                }
                x += step
            }
            y += step
        }
        if (buckets.isEmpty()) return emptyList()
        var maxWeight = 0L
        buckets.values.forEach { bucket ->
            if (bucket.weight > maxWeight) maxWeight = bucket.weight
        }
        if (maxWeight <= 0L) return emptyList()
        val candidates = buckets.values.asSequence()
            .filter { it.weight > 0L }
            .map { bucket ->
                val red = (bucket.red / bucket.weight).toInt().coerceIn(0, 255)
                val green = (bucket.green / bucket.weight).toInt().coerceIn(0, 255)
                val blue = (bucket.blue / bucket.weight).toInt().coerceIn(0, 255)
                val maxChannel = max(red, max(green, blue))
                val minChannel = red.coerceAtMost(green).coerceAtMost(blue)
                val chroma = maxChannel - minChannel
                val luma = (red * 299 + green * 587 + blue * 114) / 1000
                val saturation = homeNativeGlassTintPaletteSaturation(maxChannel, minChannel, chroma)
                val populationScore = Math.sqrt(bucket.weight.toDouble() / maxWeight.toDouble())
                val saturationScore = saturation.coerceIn(0.0, 1.0)
                val toneScore = (
                    1.0 - Math.abs(luma - HOME_NATIVE_GLASS_TINT_PALETTE_TARGET_LUMA).toDouble() /
                        HOME_NATIVE_GLASS_TINT_PALETTE_TARGET_LUMA_RANGE
                    ).coerceIn(0.0, 1.0)
                val neutralPenalty = if (chroma < 8) 0.65 else 1.0
                HomeNativeGlassPaletteColor(
                    color = Color.rgb(red, green, blue),
                    score = (
                        populationScore * 0.52 +
                            saturationScore * 0.28 +
                            toneScore * 0.20
                        ) * neutralPenalty,
                    luma = luma,
                    saturation = saturation,
                    hue = homeNativeGlassTintPaletteHue(red, green, blue, maxChannel, chroma),
                )
            }
            .toList()
        val ranked = (
            candidates
                .filter {
                    it.luma in HOME_NATIVE_GLASS_TINT_PALETTE_MIN_LUMA..HOME_NATIVE_GLASS_TINT_PALETTE_MAX_LUMA
                }
                .takeIf { it.isNotEmpty() }
                ?: candidates
            )
            .sortedByDescending { it.score }
        val minDistanceSquared = HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE *
            HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE
        val selected = ArrayList<HomeNativeGlassPaletteColor>(HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS)
        val postponed = ArrayList<HomeNativeGlassPaletteColor>()
        for (candidate in ranked) {
            if (selected.any { homeNativeGlassTintColorDistanceSquared(it.color, candidate.color) < minDistanceSquared }) {
                continue
            }
            if (selected.any { homeNativeGlassTintPaletteHueDistanceTooSmall(it, candidate) }) {
                postponed.add(candidate)
                continue
            }
            selected.add(candidate)
            if (selected.size >= HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS) break
        }
        if (selected.size < HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS) {
            for (candidate in postponed) {
                if (selected.any { homeNativeGlassTintColorDistanceSquared(it.color, candidate.color) < minDistanceSquared }) {
                    continue
                }
                selected.add(candidate)
                if (selected.size >= HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS) break
            }
        }
        return selected.map { it.color }
    }

    private fun homeNativeGlassTintPaletteSaturation(
        maxChannel: Int,
        minChannel: Int,
        chroma: Int,
    ): Double {
        if (chroma <= 0) return 0.0
        val denominator = 255 - Math.abs(maxChannel + minChannel - 255)
        if (denominator <= 0) return 0.0
        return (chroma.toDouble() / denominator.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun homeNativeGlassTintPaletteHue(
        red: Int,
        green: Int,
        blue: Int,
        maxChannel: Int,
        chroma: Int,
    ): Double {
        if (chroma <= 0) return 0.0
        val rawHue = when (maxChannel) {
            red -> ((green - blue).toDouble() / chroma.toDouble()) % 6.0
            green -> ((blue - red).toDouble() / chroma.toDouble()) + 2.0
            else -> ((red - green).toDouble() / chroma.toDouble()) + 4.0
        } * 60.0
        return if (rawHue < 0.0) rawHue + 360.0 else rawHue
    }

    private fun homeNativeGlassTintPaletteHueDistanceTooSmall(
        a: HomeNativeGlassPaletteColor,
        b: HomeNativeGlassPaletteColor,
    ): Boolean {
        if (
            a.saturation < HOME_NATIVE_GLASS_TINT_PALETTE_HUE_SATURATION_FLOOR ||
            b.saturation < HOME_NATIVE_GLASS_TINT_PALETTE_HUE_SATURATION_FLOOR
        ) {
            return false
        }
        val distance = Math.abs(a.hue - b.hue)
        val circularDistance = distance.coerceAtMost(360.0 - distance)
        return circularDistance < HOME_NATIVE_GLASS_TINT_PALETTE_MIN_HUE_DISTANCE
    }

    private fun homeNativeGlassTintColorDistanceSquared(a: Int, b: Int): Int {
        val red = Color.red(a) - Color.red(b)
        val green = Color.green(a) - Color.green(b)
        val blue = Color.blue(a) - Color.blue(b)
        return red * red + green * green + blue * blue
    }

    private class HomeNativeGlassColorBucket {
        var weight: Long = 0L
        var red: Long = 0L
        var green: Long = 0L
        var blue: Long = 0L
    }

    private data class HomeNativeGlassPaletteColor(
        val color: Int,
        val score: Double,
        val luma: Int,
        val saturation: Double,
        val hue: Double,
    )

    fun ensureInitialScanDialogHook(classLoader: ClassLoader) {
        InitialScanDialogInstaller.ensureInstalled(classLoader) { activity, cl ->
            startSymbolScanWithDialog(activity, cl, clearUserData = false)
        }
    }

    fun ensurePostScanEnvironmentWarningHook() {
        PostScanEnvironmentWarningInstaller.ensureInstalled { activity ->
            if (ConfigManager.consumePendingPostScanEnvironmentWarning(activity)) {
                showEnvironmentWarningDialog(activity) {}
            }
        }
    }

    private fun showEnvironmentWarningDialog(
        activity: Activity,
        onConfirmed: () -> Unit,
    ) {
        try {
            if (!ConfigManager.shouldShowEnvironmentWarningDialog(activity)) {
                onConfirmed()
                return
            }

            val tokens = UiStyle.tokens(activity)
            val density = activity.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val messageView = TextView(activity).apply {
                text = UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_MESSAGE
                applySettingsMessageStyle(tokens, density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(activity).apply {
                addView(
                    messageView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }

            val handler = Handler(Looper.getMainLooper())
            var countdownRunnable: Runnable? = null
            val dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
                .setSettingsTitle(activity, UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_TITLE)
                .setView(scroll)
                .setPositiveButton(
                    UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(
                        INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS
                    ),
                    null,
                )
                .create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    applyUnifiedDialogCardStyle(window, density)
                    UiStyle.animateDialogEntry(window.decorView, density)
                }
                val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                confirmButton.setTextColor(tokens.danger)
                var secondsLeft = INITIAL_SCAN_ENVIRONMENT_WARNING_DELAY_SECONDS
                confirmButton.text = UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(secondsLeft)
                confirmButton.updateButtonEnabledState(false)

                countdownRunnable = object : Runnable {
                    override fun run() {
                        secondsLeft -= 1
                        if (secondsLeft <= 0) {
                            confirmButton.text = UiText.Settings.BUTTON_OK
                            confirmButton.updateButtonEnabledState(true)
                        } else {
                            confirmButton.text =
                                UiText.Settings.initialScanEnvironmentWarningConfirmWaiting(secondsLeft)
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
                handler.postDelayed(countdownRunnable!!, 1000L)

                confirmButton.setOnClickListener {
                    dialog.dismiss()
                    onConfirmed()
                }
            }
            dialog.setOnDismissListener {
                countdownRunnable?.let { handler.removeCallbacks(it) }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showEnvironmentWarningDialog failed: ${t.message}")
            onConfirmed()
        }
    }

    private fun precacheSettingsContainerField(cls: Class<*>, preferredFieldName: String) {
        val field = runCatching { cls.getDeclaredField(preferredFieldName) }.getOrNull()
        if (field != null && View::class.java.isAssignableFrom(field.type)) {
            runCatching {
                field.isAccessible = true
                sSettingsFieldCache[cls] = field
            }
        }
    }

    private fun resolveSettingsContainer(owner: Any?, preferredFieldName: String): View? {
        if (owner == null) return null
        val cls = owner.javaClass
        val cached = sSettingsFieldCache[cls]
        if (cached != null) {
            return try { cached.get(owner) as? View } catch (_: Throwable) { null }
        }
        try {
            val field = cls.getDeclaredField(preferredFieldName)
            if (View::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                sSettingsFieldCache[cls] = field
                return field.get(owner) as? View
            }
        } catch (t: Throwable) { XposedCompat.logD { "SettingsMenuHook: ${t.message}" } }
        return null
    }

    private fun buildVersionDisplayInfo(context: Context, symbols: HookSymbols? = null): VersionDisplayInfo {
        val tiebaVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: UiText.Settings.UNKNOWN
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        val versionType = symbols?.scanTargetVersionType ?: HookSymbolResolver.readTargetVersionType(context)
        val tiebaBuildType = if (HookSymbolResolver.isOfficialTiebaVersionType(versionType)) {
            UiText.Settings.TIEBA_OFFICIAL_VERSION
        } else {
            UiText.Settings.TIEBA_TEST_VERSION
        }
        val moduleBuildType = if (com.forbidad4tieba.hook.BuildConfig.DEBUG) {
            UiText.Settings.MODULE_DEBUG_VERSION
        } else {
            UiText.Settings.MODULE_RELEASE_VERSION
        }
        return VersionDisplayInfo(
            tiebaVersion = tiebaVersion,
            tiebaBuildType = tiebaBuildType,
            moduleVersion = HookSymbolResolver.runtimeModuleVersionName(),
            moduleBuildType = moduleBuildType,
        )
    }

    internal fun showModuleSettingsDialog(context: Context, classLoader: ClassLoader?) {
        try {
            val prefs = ConfigManager.getPrefs(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val scanSymbols = HookSymbolResolver.loadCachedIfUsable(
                context = context,
                cl = classLoader ?: context.classLoader,
            )
            val featureStatusMap = HookSymbolResolver.featureStatusMap(scanSymbols)
            if (scanSymbols != null) {
                ConfigManager.applyScanAvailability(context, featureStatusMap, refreshRuntime = false)
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val verticalPadding = settingsRootContentVerticalPadding(padding)
                setPadding(padding, verticalPadding, padding, verticalPadding)
            }

            val restrictedFeaturesUnlocked = ConfigManager.isRestrictedFeaturesUnlocked(context)
            val adBlockItems = listOf(
                SwitchItem(
                    UiText.Settings.BLOCK_AD_FEED_LABEL,
                    UiText.Settings.BLOCK_AD_FEED_DESC,
                    ConfigManager.KEY_BLOCK_AD_FEED,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_POST_PAGE_LABEL,
                    UiText.Settings.BLOCK_AD_POST_PAGE_DESC,
                    ConfigManager.KEY_BLOCK_AD_POST_PAGE,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_FORUM_PAGE_LABEL,
                    UiText.Settings.BLOCK_AD_FORUM_PAGE_DESC,
                    ConfigManager.KEY_BLOCK_AD_FORUM_PAGE,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_STRATEGY_LABEL,
                    UiText.Settings.BLOCK_AD_STRATEGY_DESC,
                    ConfigManager.KEY_BLOCK_AD_STRATEGY,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_LABEL,
                    UiText.Settings.BLOCK_AD_SEARCH_BOX_TEXT_DESC,
                    ConfigManager.KEY_BLOCK_AD_SEARCH_BOX_TEXT,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_HOME_TOP_BAR_LABEL,
                    UiText.Settings.BLOCK_AD_HOME_TOP_BAR_DESC,
                    ConfigManager.KEY_BLOCK_AD_HOME_TOP_BAR,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_MINE_TAB_WEB_LABEL,
                    UiText.Settings.BLOCK_AD_MINE_TAB_WEB_DESC,
                    ConfigManager.KEY_BLOCK_AD_MINE_TAB_WEB,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_LABEL,
                    UiText.Settings.BLOCK_AD_HOME_SIDE_BAR_WEB_DESC,
                    ConfigManager.KEY_BLOCK_AD_HOME_SIDE_BAR_WEB,
                    true,
                    true,
                ),
                SwitchItem(
                    UiText.Settings.FILTER_ENTER_FORUM_WEB_LABEL,
                    UiText.Settings.FILTER_ENTER_FORUM_WEB_DESC,
                    ConfigManager.KEY_FILTER_ENTER_FORUM_WEB,
                    true,
                    false,
                ),
            )
            val customPostFilterItems = mutableListOf(
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_VOTE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VOTE_DESC, ConfigManager.KEY_FILTER_POST_VOTE, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_VIDEO_LABEL, UiText.Settings.CUSTOM_POST_FILTER_VIDEO_DESC, ConfigManager.KEY_FILTER_POST_VIDEO, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_LIVE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_LIVE_DESC, ConfigManager.KEY_FILTER_POST_LIVE, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_REPLY_LABEL, UiText.Settings.CUSTOM_POST_FILTER_REPLY_DESC, ConfigManager.KEY_FILTER_POST_REPLY, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_HOT_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HOT_DESC, ConfigManager.KEY_FILTER_POST_HOT, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_GOODS_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GOODS_DESC, ConfigManager.KEY_FILTER_POST_GOODS, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_LABEL, UiText.Settings.CUSTOM_POST_FILTER_GAME_BOOKING_DESC, ConfigManager.KEY_FILTER_POST_GAME_BOOKING, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_HELP_LABEL, UiText.Settings.CUSTOM_POST_FILTER_HELP_DESC, ConfigManager.KEY_FILTER_POST_HELP, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_SCORE_LABEL, UiText.Settings.CUSTOM_POST_FILTER_SCORE_DESC, ConfigManager.KEY_FILTER_POST_SCORE, true, false),
                SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_RECOMMEND_FORUM_DESC, ConfigManager.KEY_FILTER_POST_RECOMMEND_FORUM, true, false),
            )
            if (restrictedFeaturesUnlocked) {
                customPostFilterItems.add(
                    SwitchItem(
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_LABEL,
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DESC,
                        ConfigManager.KEY_FILTER_POST_MODEL_SCORE,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS
                    ) {
                        showCustomPostModelScoreDialog(context, prefs)
                    }
                )
            }
            customPostFilterItems.add(SwitchItem(UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_LABEL, UiText.Settings.CUSTOM_POST_FILTER_UNFOLLOWED_FORUM_DESC, ConfigManager.KEY_FILTER_POST_UNFOLLOWED_FORUM, true, false))
            customPostFilterItems.add(
                SwitchItem(
                    UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_LABEL,
                    UiText.Settings.CUSTOM_POST_FILTER_FORUM_KEYWORD_DESC,
                    ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS
                ) {
                    showCustomPostFilterKeywordDialog(context, prefs)
                }
            )

            val contentBlockItems = mutableListOf<SwitchItem>()
            if (restrictedFeaturesUnlocked) {
                contentBlockItems.add(
                    SwitchItem(
                        UiText.Settings.BLOCK_AD_LABEL,
                        UiText.Settings.BLOCK_AD_DESC,
                        ConfigManager.KEY_BLOCK_AD,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                    ) {
                        showAdBlockDialog(context, prefs, adBlockItems)
                    }
                )
            }
            contentBlockItems.add(
                SwitchItem(
                    UiText.Settings.CUSTOM_POST_FILTER_LABEL,
                    UiText.Settings.CUSTOM_POST_FILTER_DESC,
                    ConfigManager.KEY_ENABLE_CUSTOM_POST_FILTER,
                    true,
                    false,
                    UiText.Settings.ACTION_ICON_SETTINGS
                ) {
                    showCustomPostFilterDialog(context, prefs, customPostFilterItems)
                }
            )

            val extensionItems = mutableListOf(
                SwitchItem(UiText.Settings.AUTO_LOAD_MORE_LABEL, UiText.Settings.AUTO_LOAD_MORE_DESC, ConfigManager.KEY_ENABLE_AUTO_LOAD_MORE, true, false),
                SwitchItem(UiText.Settings.DISABLE_AUTO_REFRESH_LABEL, UiText.Settings.DISABLE_AUTO_REFRESH_DESC, ConfigManager.KEY_DISABLE_AUTO_REFRESH, true, false),
                SwitchItem(UiText.Settings.DEFAULT_ORIGINAL_IMAGE_LABEL, UiText.Settings.DEFAULT_ORIGINAL_IMAGE_DESC, ConfigManager.KEY_ENABLE_DEFAULT_ORIGINAL_IMAGE, true, false),
                SwitchItem(UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_LABEL, UiText.Settings.OPEN_WEB_LINK_IN_SYSTEM_BROWSER_DESC, ConfigManager.KEY_OPEN_WEB_LINK_IN_SYSTEM_BROWSER, true, false),
            )
            if (restrictedFeaturesUnlocked) {
                extensionItems.add(
                    1,
                    SwitchItem(
                        UiText.Settings.PB_LIKE_AUTO_REPLY_LABEL,
                        UiText.Settings.PB_LIKE_AUTO_REPLY_DESC,
                        ConfigManager.KEY_ENABLE_PB_LIKE_AUTO_REPLY,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS
                    ) {
                        showPbLikeAutoReplyDialog(context, prefs)
                    }
                )
                val performanceGroups = listOf(
                    SettingGroup(
                        UiText.Settings.PERFORMANCE_GROUP_HOST_RUNTIME,
                        listOf(
                            SwitchItem(UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_LABEL, UiText.Settings.FORCE_HOST_PERFORMANCE_FLAGS_DESC, ConfigManager.KEY_FORCE_HOST_PERFORMANCE_FLAGS, true, true),
                            SwitchItem(UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_LABEL, UiText.Settings.FORCE_LOW_END_DEVICE_CONFIG_DESC, ConfigManager.KEY_FORCE_LOW_END_DEVICE_CONFIG, true, true),
                            SwitchItem(UiText.Settings.DISABLE_APSARAS_SCHEDULE_LABEL, UiText.Settings.DISABLE_APSARAS_SCHEDULE_DESC, ConfigManager.KEY_DISABLE_APSARAS_SCHEDULE, true, true),
                            SwitchItem(UiText.Settings.PB_PERFORMANCE_MODE_LABEL, UiText.Settings.PB_PERFORMANCE_MODE_DESC, ConfigManager.KEY_ENABLE_PB_PERFORMANCE_MODE, true, true),
                            SwitchItem(UiText.Settings.PB_SCROLL_COALESCE_LABEL, UiText.Settings.PB_SCROLL_COALESCE_DESC, ConfigManager.KEY_ENABLE_PB_SCROLL_COALESCE, true, true),
                        ),
                    ),
                    SettingGroup(
                        UiText.Settings.PERFORMANCE_GROUP_STARTUP,
                        listOf(
                            SwitchItem(UiText.Settings.DISABLE_AD_SDK_COMPONENTS_LABEL, UiText.Settings.DISABLE_AD_SDK_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_AD_SDK_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_FLUTTER_PREINIT_LABEL, UiText.Settings.DISABLE_FLUTTER_PREINIT_DESC, ConfigManager.KEY_DISABLE_FLUTTER_PREINIT, true, true),
                            SwitchItem(UiText.Settings.BLOCK_TITAN_PATCH_LABEL, UiText.Settings.BLOCK_TITAN_PATCH_DESC, ConfigManager.KEY_BLOCK_TITAN_PATCH, true, false),
                        ),
                    ),
                    SettingGroup(
                        UiText.Settings.PERFORMANCE_GROUP_COMPONENT,
                        listOf(
                            SwitchItem(UiText.Settings.DISABLE_AI_COMPONENTS_LABEL, UiText.Settings.DISABLE_AI_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_AI_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_VIDEO_COMPONENTS_LABEL, UiText.Settings.DISABLE_VIDEO_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_VIDEO_COMPONENTS, true, true),
                        ),
                    ),
                )
                extensionItems.add(
                    SwitchItem(
                        UiText.Settings.GROUP_PERFORMANCE,
                        UiText.Settings.PERFORMANCE_OPTIMIZATION_DESC,
                        ConfigManager.KEY_ENABLE_PERFORMANCE_OPTIMIZATION,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                    ) {
                        showPerformanceOptimizationDialog(context, prefs, performanceGroups)
                    }
                )
                extensionItems.add(
                    SwitchItem(
                        UiText.Settings.AUTO_SIGN_IN_LABEL,
                        UiText.Settings.AUTO_SIGN_IN_DESC,
                        ConfigManager.KEY_ENABLE_AUTO_SIGN_IN,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_PLAY
                    ) {
                        AutoSignInManager.tryAutoSignIn(context, force = true)
                    }
                )
                extensionItems.add(
                    SwitchItem(
                        UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_LABEL,
                        UiText.Settings.PRIVATE_READ_RECEIPT_INVISIBLE_DESC,
                        ConfigManager.KEY_PRIVATE_READ_RECEIPT_INVISIBLE,
                        true,
                        false,
                    )
                )
                extensionItems.add(
                    SwitchItem(
                        UiText.Settings.REPLY_VISIBILITY_PROBE_LABEL,
                        UiText.Settings.REPLY_VISIBILITY_PROBE_DESC,
                        ConfigManager.KEY_VERIFY_REPLY_AFTER_POST,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS,
                    ) {
                        showReplyVisibilityProbeDialog(context, prefs)
                    }
                )
                 extensionItems.add(
                    SwitchItem(UiText.Settings.DETAILED_LOGGING_LABEL, UiText.Settings.DETAILED_LOGGING_DESC, ConfigManager.KEY_ENABLE_DETAILED_LOGGING, true, false),
                )
            }

            val groups = mutableListOf(
                SettingGroup(UiText.Settings.GROUP_CONTENT_BLOCK, contentBlockItems),

                SettingGroup(UiText.Settings.GROUP_UI_OPTIMIZE, listOf(
                    SwitchItem(
                        UiText.Settings.SIMPLIFY_HOME_TAB_LABEL,
                        UiText.Settings.SIMPLIFY_HOME_TAB_DESC,
                        ConfigManager.KEY_CUSTOM_HOME_TOP_TABS,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS
                    ) {
                        showHomeTopTabDialog(context, prefs)
                    },
                    SwitchItem(UiText.Settings.AUTO_HIDE_HOME_TAB_LABEL, UiText.Settings.AUTO_HIDE_HOME_TAB_DESC, ConfigManager.KEY_AUTO_HIDE_HOME_TAB, true, false),
                    SwitchItem(
                        UiText.Settings.HOME_NATIVE_GLASS_LABEL,
                        UiText.Settings.HOME_NATIVE_GLASS_DESC,
                        ConfigManager.KEY_ENABLE_HOME_NATIVE_GLASS,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS
                    ) {
                        showHomeNativeGlassDialog(
                            context,
                            prefs,
                        )
                    },
                    SwitchItem(
                        UiText.Settings.FORCE_FEED_UI_OPT_LABEL,
                        UiText.Settings.FORCE_FEED_UI_OPT_DESC,
                        ConfigManager.KEY_FORCE_FEED_UI_OPT,
                        true,
                        false,
                        linkedPrefKeys = listOf(ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS),
                    ),
                    SwitchItem(
                        UiText.Settings.SIMPLIFY_BOTTOM_TAB_LABEL,
                        UiText.Settings.SIMPLIFY_BOTTOM_TAB_DESC,
                        ConfigManager.KEY_CUSTOM_BOTTOM_TABS,
                        true,
                        false,
                        UiText.Settings.ACTION_ICON_SETTINGS
                    ) {
                        showBottomTabDialog(context, prefs)
                    }
                )),
            )
            groups.add(SettingGroup(UiText.Settings.GROUP_EXTENSION, extensionItems))

            val runtimeSupportByKey = mutableMapOf<String, SwitchRuntimeSupport>()
            fun supportOf(item: SwitchItem): SwitchRuntimeSupport {
                return runtimeSupportByKey.getOrPut(item.prefKey) {
                    resolveSwitchRuntimeSupport(item, featureStatusMap)
                }
            }

            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    val gap = View(context)
                    gap.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (SETTINGS_ROOT_GROUP_GAP_DP * density).toInt(),
                    )
                    root.addView(gap)
                }

                val tokens = UiStyle.tokens(context)
                val headerLabel = TextView(context).apply {
                    text = group.name
                    applySettingsSectionTitleStyle(tokens, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                }
                root.addView(headerLabel)

                group.items.forEach { item ->
                    val support = supportOf(item)
                    val finalLabel = when {
                        !support.supported -> UiText.Settings.withUnsupportedSuffix(item.label)
                        support.partial -> UiText.Settings.withPartialSuffix(item.label)
                        else -> item.label
                    }
                    val finalDesc = mergeDescription(item.description, support.note)

                    val rowView = createSwitchRow(
                        context,
                        prefs,
                        finalLabel,
                        finalDesc,
                        item.prefKey,
                        padding,
                        support.supported,
                        if (support.supported) item.defaultValue else false,
                        item.actionIcon,
                        item.onActionClick,
                        item.linkedPrefKeys,
                    )
                    root.addView(rowView)
                }
            }

            val defaultEnabledGap = View(context)
            defaultEnabledGap.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (SETTINGS_ROOT_GROUP_GAP_DP * density).toInt(),
            )
            root.addView(defaultEnabledGap)
            val tokensForDefault = UiStyle.tokens(context)

            val defaultEnabledContent = TextView(context).apply {
                text = UiText.Settings.DEFAULT_ENABLED_FEATURES
                textSize = 11.5f
                setTextColor(tokensForDefault.textSecondary)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                includeFontPadding = false
                setLineSpacing(2f * density, 1f)
                setPadding(0, (padding * 0.3f).toInt(), 0, (padding * 0.3f).toInt())
                visibility = View.GONE
            }

            var defaultEnabledExpanded = false
            val defaultEnabledRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (padding * 0.5f).toInt(), 0, (padding * 0.3f).toInt())
            }
            val arrowSize = (20 * density).toInt()
            val defaultEnabledArrow = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_ICON
                textSize = 13f
                setTextColor(tokensForDefault.accent)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(arrowSize, arrowSize).apply {
                    leftMargin = (6 * density).toInt()
                }
            }
            val defaultEnabledLabel = TextView(context).apply {
                text = UiText.Settings.DEFAULT_ENABLED_DESC
                applySettingsSectionTitleStyle(tokensForDefault, density)
            }
            defaultEnabledRow.addView(defaultEnabledLabel)
            defaultEnabledRow.addView(defaultEnabledArrow)
            defaultEnabledRow.setOnClickListener {
                defaultEnabledExpanded = !defaultEnabledExpanded
                UiStyle.animateExpandArrow(defaultEnabledArrow, defaultEnabledExpanded)
                if (defaultEnabledExpanded) {
                    UiStyle.animateCardExpand(defaultEnabledContent)
                } else {
                    UiStyle.animateCardCollapse(defaultEnabledContent)
                }
            }

            root.addView(defaultEnabledRow)
            root.addView(defaultEnabledContent)

            root.addView(createDivider(context, padding))
            val aboutContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, padding)

                val gap = View(context)
                gap.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (SETTINGS_ROOT_GROUP_GAP_DP * density).toInt(),
                )
                addView(gap)

                addView(TextView(context).apply {
                    text = UiText.Settings.ABOUT
                    applySettingsSectionTitleStyle(tokensForDefault, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_ROOT_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_ROOT_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                })
            }

            val versionInfo = buildVersionDisplayInfo(context, scanSymbols)

            val defaultAboutItems = listOf(
                AboutInfoManager.AboutItem(
                    UiText.Settings.VERSION,
                    UiText.Settings.aboutVersionSummary(
                        tiebaBuildType = versionInfo.tiebaBuildType,
                        tiebaVersion = versionInfo.tiebaVersion,
                        moduleBuildType = versionInfo.moduleBuildType,
                        moduleVersion = versionInfo.moduleVersion,
                    ),
                    null,
                ),
                AboutInfoManager.AboutItem(UiText.Settings.AUTHOR, UiText.Settings.AUTHOR_NAME, "https://github.com/aikavvak12una/ForbidAd4TieBa"),
            )
            val aboutItemsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            aboutContainer.addView(aboutItemsContainer)

            var settingsDialog: AlertDialog? = null
            var versionTapCount = 0

            fun renderAboutItems(remoteAboutItems: List<AboutInfoManager.AboutItem>) {
                aboutItemsContainer.removeAllViews()
                val aboutItems = defaultAboutItems + remoteAboutItems
                for (aboutItem in aboutItems) {
                    val onAboutClick = if (aboutItem.title == UiText.Settings.VERSION) {
                        if (restrictedFeaturesUnlocked) {
                            { showRuntimeEnvironmentDialog(context) }
                        } else {
                            versionClick@{
                                if (ConfigManager.isRestrictedFeatureUnlockBlocked(context)) {
                                    Toast.makeText(
                                        context,
                                        UiText.Settings.RESTRICTED_FEATURE_UNSUPPORTED_ENVIRONMENT,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@versionClick
                                }
                                versionTapCount += 1
                                if (versionTapCount >= RESTRICTED_FEATURE_UNLOCK_TAP_COUNT) {
                                    versionTapCount = 0
                                    showRestrictedFeatureWarningDialog(context) {
                                        settingsDialog?.dismiss()
                                        Handler(Looper.getMainLooper()).post {
                                            showModuleSettingsDialog(context, classLoader)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    }
                    aboutItemsContainer.addView(
                        createAboutItem(
                            context = context,
                            density = density,
                            padding = padding,
                            title = aboutItem.title,
                            content = aboutItem.description,
                            url = aboutItem.url,
                            onClick = onAboutClick,
                        )
                    )
                }
            }

            renderAboutItems(AboutInfoManager.loadCachedItemsForSettings())

            root.addView(aboutContainer)

            val scrollContainer = ScrollView(context).apply {
                addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val tokensTitle = UiStyle.tokens(context)
            val titleView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, settingsDialogTitleBottomPadding(padding))

                val titleRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(TextView(context).apply {
                        text = UiText.Settings.MODULE_SETTINGS
                        textSize = 22f
                        letterSpacing = 0.02f
                        setTextColor(tokensTitle.textPrimary)
                        typeface = Typeface.DEFAULT_BOLD
                    })

                    addView(android.widget.ImageView(context).apply {
                        setImageResource(android.R.drawable.ic_popup_sync)
                        setColorFilter(tokensTitle.accent)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                        val lp = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
                        lp.leftMargin = (8 * density).toInt()
                        layoutParams = lp
                        setOnClickListener { v ->
                            UiStyle.animateIconTap(v)
                            showSymbolScanActionDialog(context, classLoader ?: context.classLoader)
                        }
                    })
                }
                addView(titleRow)

                val brandTag = TextView(context).apply {
                    text = UiText.Settings.BRAND_TAG
                    textSize = SETTINGS_BRAND_TAG_SP
                    letterSpacing = 0.06f
                    typeface = Typeface.MONOSPACE
                    setTextColor(tokensTitle.textMuted)
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }
                addView(brandTag)
                UiStyle.animateBrandTagShimmer(brandTag)
            }

            val dialogTheme = if (tokensTitle.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val builder = AlertDialog.Builder(context, dialogTheme)
            builder.setCustomTitle(titleView)
            builder.setView(scrollContainer)
            builder.setPositiveButton(UiText.Settings.SAVE_AND_RESTART) { _, _ ->
                Toast.makeText(context, UiText.Settings.SETTINGS_SAVED_RESTARTING, Toast.LENGTH_SHORT).show()
                restartHostApp(context)
            }

            val dialog = builder.create()
            settingsDialog = dialog
            dialog.show()
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] FAILED to show settings dialog: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun showRestrictedFeatureWarningDialog(
        context: Context,
        onConfirmed: () -> Unit,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val messageView = TextView(context).apply {
                text = UiText.Settings.RESTRICTED_FEATURE_WARNING_MESSAGE
                applySettingsMessageStyle(tokens, density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(context).apply {
                addView(
                    messageView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }

            val handler = Handler(Looper.getMainLooper())
            var countdownRunnable: Runnable? = null
            val dialogTheme = if (tokens.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val dialog = AlertDialog.Builder(context, dialogTheme)
                .setSettingsTitle(context, UiText.Settings.RESTRICTED_FEATURE_WARNING_TITLE)
                .setView(scroll)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(
                    UiText.Settings.restrictedFeatureConfirmWaiting(RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS),
                    null
                )
                .create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    applyUnifiedDialogCardStyle(window, density)
                    UiStyle.animateDialogEntry(window.decorView, density)
                }
                val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                confirmButton.setTextColor(tokens.danger)
                var secondsLeft = RESTRICTED_FEATURE_CONFIRM_DELAY_SECONDS
                confirmButton.text = UiText.Settings.restrictedFeatureConfirmWaiting(secondsLeft)
                confirmButton.updateButtonEnabledState(false)

                countdownRunnable = object : Runnable {
                    override fun run() {
                        secondsLeft -= 1
                        if (secondsLeft <= 0) {
                            confirmButton.text = UiText.Settings.RESTRICTED_FEATURE_CONFIRM
                            confirmButton.updateButtonEnabledState(true)
                        } else {
                            confirmButton.text = UiText.Settings.restrictedFeatureConfirmWaiting(secondsLeft)
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
                handler.postDelayed(countdownRunnable!!, 1000L)

                confirmButton.setOnClickListener {
                    ConfigManager.setRestrictedFeaturesUnlocked(context, true)
                    Toast.makeText(context, UiText.Settings.RESTRICTED_FEATURE_UNLOCKED, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onConfirmed()
                }
            }
            dialog.setOnDismissListener {
                countdownRunnable?.let { handler.removeCallbacks(it) }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showRestrictedFeatureWarningDialog failed: ${t.message}")
        }
    }

    private fun showRuntimeEnvironmentDialog(context: Context) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val contentView = TextView(context).apply {
                text = AboutInfoManager.runtimeEnvironmentJsonForSettings(context)
                applySettingsCodeTextStyle(tokens, density)
                setTextIsSelectable(true)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(context).apply {
                addView(
                    contentView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }
            val dialogTheme = if (tokens.night) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            val dialog = AlertDialog.Builder(context, dialogTheme)
                .setSettingsTitle(context, UiText.Settings.RUNTIME_ENVIRONMENT)
                .setView(scroll)
                .setPositiveButton(UiText.Settings.BUTTON_OK, null)
                .create()
            dialog.show()
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showRuntimeEnvironmentDialog failed: ${t.message}")
        }
    }

    private fun startSymbolScanWithDialog(context: Context, classLoader: ClassLoader?, clearUserData: Boolean) {
        val activity = ReflectionUtils.findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, UiText.Settings.CONTEXT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            HookSymbolResolver.manualRescanAsync(
                context,
                classLoader ?: context.classLoader,
                clearUserData = clearUserData,
            )
            return
        }
        val cl = classLoader ?: activity.classLoader
        if (cl == null) {
            Toast.makeText(activity, UiText.Settings.CLASSLOADER_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            return
        }

        val tokens = UiStyle.tokens(activity)
        val density = activity.resources.displayMetrics.density
        val padding = settingsDialogPadding(density)
        val ui = Handler(Looper.getMainLooper())
        var finished = false
        var progressSteps = 0
        var displayedProgress = 0f
        val scanLogLines = Collections.synchronizedList(mutableListOf<String>())
        var scanExceptionLine: String? = null
        var scanPulseRunnable: Runnable? = null

        fun appendScanLog(line: String) {
            scanLogLines.add(line)
        }

        fun snapshotScanLog(): String {
            return synchronized(scanLogLines) {
                scanLogLines.joinToString("\n")
            }
        }

        fun scanLogContains(prefix: String): Boolean {
            return synchronized(scanLogLines) {
                scanLogLines.any { it.startsWith(prefix) }
            }
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
        }
        val scanContentMaxHeight = (activity.resources.displayMetrics.heightPixels * 0.62f)
            .toInt()
            .coerceAtLeast((220 * density).toInt())
        val scanContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title area.
        val titleContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.DIALOG_SCAN_TITLE
            applySettingsDialogTitleStyle(tokens, density)
        })
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.BRAND_TAG
            textSize = SETTINGS_BRAND_TAG_SP
            letterSpacing = 0.06f
            typeface = Typeface.MONOSPACE
            setTextColor(tokens.textMuted)
            setPadding(0, (2 * density).toInt(), 0, 0)
        }.also { UiStyle.animateBrandTagShimmer(it) })
        root.addView(titleContainer)

        val scanScroll = MaxHeightScrollView(activity, scanContentMaxHeight).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(
                scanContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }
        root.addView(
            scanScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )

        // Progress bar.
        val progressBar = UiStyle.ThinProgressBar(activity, tokens).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (5 * density).toInt(),
            )
        }
        scanContent.addView(progressBar)

        fun setScanProgress(target: Float, animated: Boolean = true) {
            val clamped = target.coerceIn(0f, 1f)
            ui.post {
                if (clamped <= displayedProgress + 0.0005f) return@post
                displayedProgress = clamped
                progressBar.setProgress(clamped, animated)
            }
        }

        fun stopScanPulse() {
            scanPulseRunnable?.let { ui.removeCallbacks(it) }
            scanPulseRunnable = null
            progressBar.animate().cancel()
            progressBar.alpha = 1f
        }

        fun startScanPulse() {
            if (scanPulseRunnable != null) return
            scanPulseRunnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val drift = when {
                        displayedProgress < 0.18f -> 0.018f
                        displayedProgress < 0.60f -> 0.010f
                        else -> 0.004f
                    }
                    val nextProgress = (displayedProgress + drift).coerceAtMost(0.88f)
                    if (nextProgress > displayedProgress + 0.0005f) {
                        displayedProgress = nextProgress
                        progressBar.setProgress(nextProgress, animated = true)
                    }
                    UiStyle.animateProgressRunningPulse(progressBar)
                    ui.postDelayed(this, SCAN_RUNNING_PULSE_INTERVAL_MS)
                }
            }
            ui.postDelayed(scanPulseRunnable!!, SCAN_RUNNING_PULSE_INITIAL_DELAY_MS)
        }

        // Status text.
        val statusView = TextView(activity).apply {
            text = UiText.Settings.SCAN_PREPARING
            textSize = SETTINGS_VALUE_TEXT_SP
            setTextColor(tokens.textSecondary)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        scanContent.addView(statusView)

        // Result card is hidden until scanning finishes.
        val resultCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.createResultCardBackground(tokens, density)
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (16 * density).toInt()
            layoutParams = lp
        }
        val resultStatusView = TextView(activity).apply {
            applySettingsRowTitleStyle(tokens, density)
        }
        val resultVersionView = TextView(activity).apply {
            applySettingsCodeTextStyle(tokens, density, muted = true)
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        resultCard.addView(resultStatusView)
        resultCard.addView(resultVersionView)
        scanContent.addView(resultCard)

        // Restart button.
        val restartBtn = Button(activity).apply {
            text = UiText.Settings.BUTTON_RESTART
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            UiStyle.setButtonEnabledState(this, false)
        }
        val copyLogBtn = Button(activity).apply {
            text = UiText.Settings.BUTTON_COPY_SCAN_LOG
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            UiStyle.setButtonEnabledState(this, false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = (8 * density).toInt()
            }
        }
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (16 * density).toInt(), 0, 0)
            visibility = View.GONE
        }
        buttonRow.addView(copyLogBtn)
        buttonRow.addView(restartBtn)
        root.addView(buttonRow)

        val dialogTheme = if (tokens.night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, dialogTheme)
            .setView(root)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (finished) {
                    dialog.dismiss()
                    true
                } else {
                    true // Consume Back while scanning.
                }
            } else false
        }
        dialog.setOnDismissListener {
            stopScanPulse()
        }
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                window.attributes = window.attributes.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()

        restartBtn.setOnClickListener {
            if (!finished) return@setOnClickListener
            restartHostApp(activity)
        }
        copyLogBtn.setOnClickListener {
            if (!finished) return@setOnClickListener
            val text = snapshotScanLog()
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("tbhook_symbol_scan", text))
            Toast.makeText(activity, UiText.Settings.SCAN_LOG_COPIED, Toast.LENGTH_SHORT).show()
        }

        // Map scan log hints to a rough progress ratio.
        fun advanceProgress(hint: String) {
            progressSteps++
            val ratio = when {
                hint.contains("fingerprint") -> 0.05f
                hint.contains("disk cache") -> 0.10f
                hint.contains("scan begin") -> 0.15f
                hint.contains("nav class") -> 0.25f
                hint.contains("settings match") -> 0.35f
                hint.contains("plainUrl") -> 0.55f
                hint.contains("pbEarlyAd") -> 0.65f
                hint.contains("collection") -> 0.75f
                hint.contains("history") -> 0.80f
                hint.contains("scan done") -> 0.95f
                hint.contains("cache updated") -> 0.98f
                hint.contains("durationMs") -> 1.0f
                else -> (0.15f + progressSteps * 0.008f).coerceAtMost(0.92f)
            }
            setScanProgress(ratio)
        }

        fun updateStatus(text: String) {
            ui.post { statusView.text = text }
        }

        updateStatus(UiText.Settings.SCAN_PREPARING)
        displayedProgress = 0.02f
        progressBar.setProgress(0.02f, animated = false)
        startScanPulse()

        thread(name = "tbhook-symbol-scan", isDaemon = true) {
            var source = "unsupported"
            var scanSymbols: HookSymbols? = null
            var runtimeEnvironmentJson: String? = null
            try {
                if (clearUserData) {
                    updateStatus(UiText.Settings.SCAN_CLEARING)
                    val clearResult = ModuleUserDataCleaner.clearBeforeManualScan(activity)
                    if (!clearResult.success) {
                        source = "cleanup_failed"
                        appendScanLog(UiText.Settings.scanUserDataClearFailed(clearResult.failedTargets.size))
                        return@thread
                    }
                    appendScanLog(UiText.Settings.scanUserDataCleared(clearResult.deletedTargets.size))
                }
                updateStatus(UiText.Settings.SCAN_RUNNING)
                val symbols = HookSymbolResolver.resolve(
                    context = activity,
                    cl = cl,
                    forceRescan = true,
                    showToast = false,
                    logger = ScanLogger { line ->
                        appendScanLog(line)
                        advanceProgress(line)
                    },
                )
                ConfigManager.applyScanAvailability(
                    activity,
                    HookSymbolResolver.featureStatusMap(symbols),
                    refreshRuntime = false,
                )
                if (symbols.source != "unsupported") {
                    ConfigManager.markPostScanEnvironmentWarningPending(activity)
                }
                source = symbols.source
                scanSymbols = symbols
            } catch (t: Throwable) {
                val exceptionText = HookSymbolResolver.formatScanException(t)
                scanExceptionLine = UiText.Settings.scanException(exceptionText)
                appendScanLog(scanExceptionLine ?: exceptionText)
                XposedCompat.log("[SettingsMenuHook] scan exception: $exceptionText")
                XposedCompat.log(t)
            } finally {
                if (!scanLogContains("Feature[")) {
                    HookSymbolResolver.formatFeatureStatusLines(scanSymbols).forEach(::appendScanLog)
                }
                if (!scanLogContains("HookPoint[")) {
                    HookSymbolResolver.formatHookPointStatusLines(scanSymbols).forEach(::appendScanLog)
                }
                runtimeEnvironmentJson = runCatching {
                    AboutInfoManager.runtimeEnvironmentJsonForSettings(activity)
                }.getOrElse { t ->
                    UiText.Settings.scanException(t.message ?: UiText.Settings.UNKNOWN)
                }
                appendScanLog("${UiText.Settings.RUNTIME_ENVIRONMENT}:\n$runtimeEnvironmentJson")
                ui.post {
                    finished = true
                    stopScanPulse()
                    displayedProgress = 1f
                    progressBar.setProgress(1f)
                    UiStyle.animateProgressComplete(progressBar)
                    dialog.setCancelable(true)
                    buttonRow.visibility = View.VISIBLE
                    UiStyle.setButtonEnabledState(restartBtn, true)
                    UiStyle.setButtonEnabledState(copyLogBtn, true)
                    UiStyle.animateButtonEnable(restartBtn)
                    UiStyle.animateButtonEnable(copyLogBtn)

                    val versionInfo = buildVersionDisplayInfo(activity, scanSymbols)

                    // Failed hook points decide the result severity color.
                    val failedLines = HookSymbolResolver.formatHookPointStatusLines(scanSymbols)
                        .filter { it.contains("MISSING") || it.contains("ERROR") }
                    val hasScanErrors = HookSymbolResolver.hasScanErrors(scanSymbols, failedLines)
                    val versionWarning = HookSymbolResolver.formatScanVersionWarning(scanSymbols)
                    val resultWarning = versionWarning ?: if (hasScanErrors) {
                        HookSymbolResolver.formatScanFeatureWarning()
                    } else {
                        null
                    }

                    val summaryText = when {
                        !hasScanErrors -> UiText.Settings.SCAN_COMPLETED
                        source == "scan" || source == "partial" -> UiText.Settings.SCAN_PARTIALLY_COMPLETED
                        else -> UiText.Settings.SCAN_FAILED
                    }
                    val summaryColor = when {
                        !hasScanErrors && versionWarning == null -> tokens.success
                        !hasScanErrors -> tokens.warning
                        else -> tokens.danger
                    }

                    // Result card replaces the inline status row after completion.
                    statusView.visibility = View.GONE

                    resultStatusView.text = "${UiText.Settings.SCAN_RESULT_LABEL}  $summaryText"
                    resultStatusView.setTextColor(summaryColor)
                    resultVersionView.text = UiText.Settings.scanVersionSummary(
                        tiebaBuildType = versionInfo.tiebaBuildType,
                        tiebaVersion = versionInfo.tiebaVersion,
                        moduleBuildType = versionInfo.moduleBuildType,
                        moduleVersion = versionInfo.moduleVersion,
                    )

                    val runtimeEnvironmentView = TextView(activity).apply {
                        text = "${UiText.Settings.RUNTIME_ENVIRONMENT}\n$runtimeEnvironmentJson"
                        applySettingsCodeTextStyle(tokens, density, muted = true)
                        setPadding(0, (6 * density).toInt(), 0, 0)
                        setTextIsSelectable(true)
                    }
                    resultCard.addView(runtimeEnvironmentView)

                    if (resultWarning != null) {
                        val warningView = TextView(activity).apply {
                            text = resultWarning
                            textSize = SETTINGS_ROW_DESC_SP
                            setTextColor(summaryColor)
                            includeFontPadding = false
                            setLineSpacing(1f * density, 1f)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(warningView)
                    }

                    val exceptionLine = scanExceptionLine
                    if (exceptionLine != null) {
                        val exceptionView = TextView(activity).apply {
                            text = exceptionLine
                            applySettingsCodeTextStyle(tokens, density)
                            setTextColor(tokens.danger)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(exceptionView)
                    }

                    // Show failed or missing hook points.
                    if (failedLines.isNotEmpty()) {
                        val failedView = TextView(activity).apply {
                            text = failedLines.joinToString("\n")
                            applySettingsCodeTextStyle(tokens, density)
                            setTextColor(tokens.danger)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(failedView)
                    }

                    resultCard.visibility = View.VISIBLE
                    UiStyle.animateResultReveal(resultCard)
                }
            }
        }
    }

    private fun showSymbolScanActionDialog(context: Context, classLoader: ClassLoader?) {
        val activity = ReflectionUtils.findActivityFromContext(context)
        if (activity == null) {
            Toast.makeText(context, UiText.Settings.CONTEXT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            startSymbolScanWithDialog(context, classLoader ?: context.classLoader, clearUserData = false)
            return
        }

        val tokens = UiStyle.tokens(activity)
        val density = activity.resources.displayMetrics.density
        val actions = arrayOf(
            UiText.Settings.SCAN_ACTION_RESCAN_ONLY,
            UiText.Settings.SCAN_ACTION_CLEAR_DATA_RESTART,
        )
        val padding = settingsDialogPadding(density)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
        }
        lateinit var dialog: AlertDialog
        actions.forEachIndexed { index, action ->
            root.addView(
                TextView(activity).apply {
                    text = action
                    applySettingsRowTitleStyle(tokens, density)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(0, settingsRowVerticalPadding(density), 0, settingsRowVerticalPadding(density))
                    setOnClickListener {
                        UiStyle.animateActionPress(this)
                        dialog.dismiss()
                        when (index) {
                            0 -> startSymbolScanWithDialog(activity, classLoader ?: activity.classLoader, clearUserData = false)
                            1 -> clearModuleDataAndRestart(activity)
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        dialog = AlertDialog.Builder(activity, dialogThemeFor(activity))
            .setSettingsTitle(activity, UiText.Settings.DIALOG_SCAN_ACTION_TITLE)
            .setView(createDialogScrollContainer(activity, root))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()
    }

    private fun clearModuleDataAndRestart(activity: Activity) {
        Toast.makeText(activity, UiText.Settings.CLEAR_MODULE_DATA_STARTED, Toast.LENGTH_SHORT).show()
        thread(name = "tbhook-clear-module-data", isDaemon = true) {
            try {
                val result = ModuleUserDataCleaner.clearAllModuleData(activity, resetRuntime = false)
                Handler(Looper.getMainLooper()).post {
                    val message = if (result.success) {
                        UiText.Settings.CLEAR_MODULE_DATA_RESTARTING
                    } else {
                        UiText.Settings.clearModuleDataPartialFailed(result.failedTargets.size)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    restartHostApp(activity)
                }
            } catch (t: Throwable) {
                XposedCompat.log("[SettingsMenuHook] clear module data failed: ${t.message}")
                XposedCompat.log(t)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        activity,
                        UiText.Settings.scanException(t.message ?: UiText.Settings.UNKNOWN),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }




    private fun resolveSwitchRuntimeSupport(
        item: SwitchItem,
        featureStatusMap: Map<String, HookFeatureStatus>,
    ): SwitchRuntimeSupport {
        val status = featureStatusMap[item.prefKey]
        if (status == null) {
            return SwitchRuntimeSupport(
                supported = item.supported,
                partial = false,
                note = null,
            )
        }
        return when (status.state) {
            HookFeatureState.DISABLED -> {
                val critical = if (status.missingCritical.isEmpty()) "-" else status.missingCritical.joinToString(", ")
                SwitchRuntimeSupport(
                    supported = false,
                    partial = false,
                    note = UiText.Settings.SCAN_DISABLED_PREFIX + critical,
                )
            }
            HookFeatureState.PARTIAL -> {
                val miss = status.missingCritical + status.missingOptional
                val detail = if (miss.isEmpty()) "-" else miss.joinToString(", ")
                SwitchRuntimeSupport(
                    supported = item.supported,
                    partial = true,
                    note = UiText.Settings.SCAN_PARTIAL_PREFIX + detail,
                )
            }
            else -> {
                SwitchRuntimeSupport(
                    supported = item.supported,
                    partial = false,
                    note = null,
                )
            }
        }
    }

    private fun mergeDescription(base: String?, note: String?): String? {
        if (base.isNullOrBlank()) return note
        if (note.isNullOrBlank()) return base
        return "$base\n$note"
    }

    private fun resolveAdBlockDialogItemSupported(item: SwitchItem): Boolean {
        val featureKey = when (item.prefKey) {
            ConfigManager.KEY_FILTER_ENTER_FORUM_WEB -> item.prefKey
            else -> ConfigManager.KEY_BLOCK_AD
        }
        return item.supported && ConfigManager.isScanFeatureAvailable(featureKey)
    }

    private fun showAdBlockDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        items: List<SwitchItem>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(items.size)
            for (item in items) {
                val supported = resolveAdBlockDialogItemSupported(item)
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = item.label,
                    description = item.description,
                    prefKey = null,
                    padding = padding,
                    enabled = supported,
                    defaultValue = if (supported) {
                        prefs.getBoolean(item.prefKey, item.defaultValue)
                    } else {
                        false
                    },
                    actionIcon = item.actionIcon,
                    onActionClick = item.onActionClick,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showAdBlockDialog failed: switch view missing for ${item.prefKey}")
                    return
                }
                views.add(item to switchView)
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BLOCK_AD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val editor = prefs.edit()
                    for ((item, switchView) in views) {
                        if (switchView.isEnabled) {
                            editor.putBoolean(item.prefKey, switchView.isChecked)
                        }
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.BLOCK_AD_SAVED),
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showAdBlockDialog failed: ${t.message}")
        }
    }

    private fun showPerformanceOptimizationDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        groups: List<SettingGroup>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val tokens = UiStyle.tokens(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(groups.sumOf { it.items.size })
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    root.addView(createDivider(context, padding))
                }
                root.addView(TextView(context).apply {
                    text = group.name
                    applySettingsSectionTitleStyle(tokens, density)
                    setPadding(
                        0,
                        (padding * SETTINGS_SUBMENU_SECTION_TOP_PADDING_RATIO).toInt(),
                        0,
                        (padding * SETTINGS_SUBMENU_SECTION_BOTTOM_PADDING_RATIO).toInt(),
                    )
                })
                for (item in group.items) {
                    val supported = ConfigManager.isScanFeatureAvailable(item.prefKey)
                    val row = createSwitchRow(
                        context = context,
                        prefs = prefs,
                        label = item.label,
                        description = item.description,
                        prefKey = null,
                        padding = padding,
                        enabled = supported,
                        defaultValue = if (supported) resolvePerformanceItemChecked(prefs, item) else false,
                        actionIcon = item.actionIcon,
                        onActionClick = item.onActionClick,
                    )
                    val switchView = findSwitchView(row)
                    if (switchView == null) {
                        XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizationDialog failed: switch view missing for ${item.prefKey}")
                        return
                    }
                    views.add(item to switchView)
                    root.addView(row)
                }
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.PERFORMANCE_OPTIMIZATION_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val editor = prefs.edit()
                    for ((item, switchView) in views) {
                        if (switchView.isEnabled) {
                            editor.putBoolean(item.prefKey, switchView.isChecked)
                        }
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PERFORMANCE_OPTIMIZATION_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showPerformanceOptimizationDialog failed: ${t.message}")
        }
    }

    private fun resolvePerformanceItemChecked(
        prefs: android.content.SharedPreferences,
        item: SwitchItem,
    ): Boolean {
        if (prefs.contains(item.prefKey)) {
            return prefs.getBoolean(item.prefKey, item.defaultValue)
        }
        return item.defaultValue
    }

    private fun showCustomPostFilterDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        items: List<SwitchItem>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val views = ArrayList<Pair<SwitchItem, Switch>>(items.size)
            var modelScoreStatsStartToastShown = false
            for (item in items) {
                val actionClick = if (item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE) {
                    { showCustomPostModelScoreDialog(context, prefs) }
                } else {
                    item.onActionClick
                }
                val row = createSwitchRow(
                    context = context,
                    prefs = prefs,
                    label = item.label,
                    description = null,
                    prefKey = null,
                    padding = padding,
                    enabled = true,
                    defaultValue = prefs.getBoolean(item.prefKey, item.defaultValue),
                    actionIcon = item.actionIcon,
                    onActionClick = actionClick,
                )
                val switchView = findSwitchView(row)
                if (switchView == null) {
                    XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterDialog failed: switch view missing for ${item.prefKey}")
                    return
                }
                if (item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE) {
                    switchView.setOnCheckedChangeListener { _, isChecked ->
                        if (
                            isChecked &&
                            !modelScoreStatsStartToastShown
                        ) {
                            modelScoreStatsStartToastShown = true
                            Toast.makeText(
                                context,
                                UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_STARTED,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                views.add(item to switchView)
                root.addView(row)
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val editor = prefs.edit()
                    var modelScoreStatsStarted = false
                    for ((item, switchView) in views) {
                        if (
                            item.prefKey == ConfigManager.KEY_FILTER_POST_MODEL_SCORE &&
                            !prefs.getBoolean(item.prefKey, item.defaultValue) &&
                            switchView.isChecked &&
                            !modelScoreStatsStartToastShown
                        ) {
                            modelScoreStatsStarted = true
                        }
                        editor.putBoolean(item.prefKey, switchView.isChecked)
                    }
                    editor.apply()
                    Toast.makeText(
                        context,
                        if (modelScoreStatsStarted) {
                            UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_STARTED
                        } else {
                            UiText.Settings.withRestartHint(UiText.Settings.CUSTOM_POST_FILTER_SAVED)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterDialog failed: ${t.message}")
        }
    }

    private fun createHomeNativeGlassModeConfigState(
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassModeConfigState {
        val imageState = HomeNativeGlassImageSelectionState(
            style.backgroundImagePath.trim(),
            ConfigManager.normalizeHomeNativeGlassTintColor(style.tintColor),
        ).apply {
            paletteColors = parseHomeNativeGlassTintPalette(style.tintPalette)
            defaultTintColor = homeNativeGlassCachedAutoTintColorOrNull(style.autoTintColor)
            if (path.isBlank()) {
                tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            } else if (
                tintColor != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR &&
                paletteColors.none { it == tintColor }
            ) {
                paletteColors = paletteColors + tintColor
            }
        }
        return HomeNativeGlassModeConfigState(
            imageState = imageState,
            blurCacheImagePath = style.blurCacheImagePath.trim(),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = style.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = style.cardRadiusDp.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = style.strokeEnabled,
            shadowStrengthPercent = style.shadowStrengthPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    private fun copyHomeNativeGlassImageState(
        target: HomeNativeGlassImageSelectionState,
        source: HomeNativeGlassImageSelectionState,
    ) {
        target.path = source.path.trim()
        target.tintColor = ConfigManager.normalizeHomeNativeGlassTintColor(source.tintColor)
        target.paletteColors = source.paletteColors
        target.defaultTintColor = source.defaultTintColor
    }

    private fun homeNativeGlassStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
        blurCacheImagePath: String,
    ): ConfigManager.HomeNativeGlassStyleConfig {
        val imageState = state.imageState
        val backgroundImagePath = imageState.path.trim()
        val hasBackgroundImage = backgroundImagePath.isNotBlank()
        return ConfigManager.HomeNativeGlassStyleConfig(
            backgroundImagePath = backgroundImagePath,
            blurCacheImagePath = if (hasBackgroundImage) {
                blurCacheImagePath.trim()
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
            },
            tintColor = if (hasBackgroundImage) {
                ConfigManager.normalizeHomeNativeGlassTintColor(imageState.tintColor)
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            },
            autoTintColor = if (hasBackgroundImage) {
                ConfigManager.normalizeHomeNativeGlassTintColor(
                    imageState.defaultTintColor ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
                )
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
            },
            tintPalette = if (hasBackgroundImage) {
                serializeHomeNativeGlassTintPalette(imageState.paletteColors)
            } else {
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE
            },
            tintAlphaPercent = state.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
            cardBlurPercent = state.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            cardRadiusDp = state.cardRadiusDp.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            ),
            strokeEnabled = state.strokeEnabled,
            shadowStrengthPercent = state.shadowStrengthPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ),
        )
    }

    private fun homeNativeGlassPreviewStyleFromModeState(
        state: HomeNativeGlassModeConfigState,
    ): ConfigManager.HomeNativeGlassStyleConfig {
        return homeNativeGlassStyleFromModeState(
            state,
            blurCacheImagePath = state.blurCacheImagePath,
        )
    }

    private fun homeNativeGlassDialogTokens(
        context: Context,
        state: HomeNativeGlassModeConfigState,
        darkMode: Boolean,
    ): UiStyle.Tokens {
        return UiStyle.homeNativeGlassPreviewTokens(
            context,
            homeNativeGlassPreviewStyleFromModeState(state),
            darkMode,
        )
    }

    private fun homeNativeGlassPreviewBitmapKey(
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): HomeNativeGlassPreviewBitmapKey {
        return HomeNativeGlassPreviewBitmapKey(
            sourcePath = style.backgroundImagePath.trim(),
            blurPercent = style.cardBlurPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            ),
            tintAlphaPercent = style.tintAlphaPercent.coerceIn(
                ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ),
        )
    }

    private fun refreshHomeNativeGlassStyledViews(
        view: View,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        applyHomeNativeGlassStyleRole(view, tokens, density)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                refreshHomeNativeGlassStyledViews(view.getChildAt(index), tokens, density)
            }
        }
    }

    private fun applyHomeNativeGlassStyleRole(
        view: View,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        when (view.tag as? HomeNativeGlassStyleRole) {
            HomeNativeGlassStyleRole.ROW_TITLE -> (view as? TextView)
                ?.setTextColor(tokens.textPrimary)
            HomeNativeGlassStyleRole.ROW_DESCRIPTION -> (view as? TextView)
                ?.setTextColor(tokens.textSecondary)
            HomeNativeGlassStyleRole.MUTED_TEXT -> (view as? TextView)
                ?.setTextColor(tokens.textMuted)
            HomeNativeGlassStyleRole.ACCENT_TEXT -> (view as? TextView)
                ?.setTextColor(homeNativeGlassSliderAccent(tokens))
            HomeNativeGlassStyleRole.BUTTON_ACCENT -> (view as? Button)
                ?.let { UiStyle.paintScanActionButton(it, density, tokens.accent) }
            HomeNativeGlassStyleRole.BUTTON_SECONDARY -> (view as? Button)
                ?.let { UiStyle.paintScanActionButton(it, density, tokens.textSecondary) }
            HomeNativeGlassStyleRole.INPUT_TEXT -> (view as? TextView)?.let { textView ->
                textView.setTextColor(tokens.textPrimary)
                textView.background = createModelScoreThresholdInputBackground(tokens, density)
            }
            HomeNativeGlassStyleRole.SEEK_BAR -> (view as? android.widget.SeekBar)
                ?.let { applyHomeNativeGlassSeekBarTint(it, tokens) }
            HomeNativeGlassStyleRole.SWITCH -> (view as? Switch)
                ?.let { applyHomeNativeGlassSwitchTint(it, tokens) }
            null -> Unit
        }
    }

    private fun putHomeNativeGlassStyle(
        editor: android.content.SharedPreferences.Editor,
        keys: ConfigManager.HomeNativeGlassStyleKeys,
        style: ConfigManager.HomeNativeGlassStyleConfig,
    ): android.content.SharedPreferences.Editor {
        return editor
            .putString(keys.backgroundImagePath, style.backgroundImagePath)
            .putString(keys.blurCacheImagePath, style.blurCacheImagePath)
            .putInt(keys.tintColor, style.tintColor)
            .putInt(keys.autoTintColor, style.autoTintColor)
            .putString(keys.tintPalette, style.tintPalette)
            .putInt(keys.tintAlphaPercent, style.tintAlphaPercent)
            .putInt(keys.cardBlurPercent, style.cardBlurPercent)
            .putInt(keys.cardRadiusDp, style.cardRadiusDp)
            .putBoolean(keys.strokeEnabled, style.strokeEnabled)
            .putInt(keys.shadowStrengthPercent, style.shadowStrengthPercent)
            .remove(keys.legacyShadowEnabled)
    }

    private fun createHomeNativeGlassModeSelectorRow(
        context: Context,
        density: Float,
        selectedDarkMode: Boolean,
        lightModeState: HomeNativeGlassModeConfigState,
        darkModeState: HomeNativeGlassModeConfigState,
        onSelected: (Boolean) -> Unit,
    ): Pair<View, (Boolean) -> Unit> {
        val initialTokens = homeNativeGlassDialogTokens(
            context,
            if (selectedDarkMode) darkModeState else lightModeState,
            selectedDarkMode,
        )
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        val segment = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createHomeNativeGlassModeSegmentBackground(initialTokens, density)
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
        }
        val lightOption = TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_MODE_LIGHT_LABEL
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { onSelected(false) }
        }
        val darkOption = TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_MODE_DARK_LABEL
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { onSelected(true) }
        }
        segment.addView(
            lightOption,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        segment.addView(
            darkOption,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        root.addView(
            segment,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * density).toInt()),
        )

        fun update(selectedDark: Boolean) {
            val tokens = homeNativeGlassDialogTokens(
                context,
                if (selectedDark) darkModeState else lightModeState,
                selectedDark,
            )
            segment.background = createHomeNativeGlassModeSegmentBackground(tokens, density)
            applyHomeNativeGlassModeOptionStyle(
                lightOption,
                selected = !selectedDark,
                fillColor = homeNativeGlassModeTintFillColor(lightModeState, darkMode = false),
                tokens,
                density,
            )
            applyHomeNativeGlassModeOptionStyle(
                darkOption,
                selected = selectedDark,
                fillColor = homeNativeGlassModeTintFillColor(darkModeState, darkMode = true),
                tokens,
                density,
            )
        }
        update(selectedDarkMode)
        return root to ::update
    }

    private fun homeNativeGlassModeTintFillColor(
        state: HomeNativeGlassModeConfigState,
        darkMode: Boolean,
    ): Int {
        val offset = state.tintAlphaPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        val alpha = (kotlin.math.abs(offset) * 255 / 100)
            .coerceAtLeast(HOME_NATIVE_GLASS_MODE_SELECTOR_MIN_FILL_ALPHA)
            .coerceAtMost(HOME_NATIVE_GLASS_MODE_SELECTOR_MAX_FILL_ALPHA)
        val overlay = when {
            offset < 0 -> Color.BLACK
            offset > 0 -> Color.WHITE
            darkMode -> Color.BLACK
            else -> Color.WHITE
        }
        return Color.argb(
            alpha,
            Color.red(overlay),
            Color.green(overlay),
            Color.blue(overlay),
        )
    }

    private fun createHomeNativeGlassModeSegmentBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.cardCornerPx
            setColor(Color.TRANSPARENT)
            setStroke((1f * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    private fun applyHomeNativeGlassModeOptionStyle(
        option: TextView,
        selected: Boolean,
        fillColor: Int,
        tokens: UiStyle.Tokens,
        density: Float,
    ) {
        option.textSize = SETTINGS_ROW_TITLE_SP
        option.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        option.setTextColor(if (selected) tokens.accent else tokens.textSecondary)
        option.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.cardCornerPx
            setColor(if (selected) fillColor else Color.TRANSPARENT)
        }
    }

    private fun showHomeNativeGlassDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            ReflectionUtils.findActivityFromContext(context)?.let { activity ->
                HomeNativeGlassHostDarkModeBridge.cacheFromActivity(activity)
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }

            val lightModeState = createHomeNativeGlassModeConfigState(
                ConfigManager.readHomeNativeGlassStyle(
                    prefs,
                    ConfigManager.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
                )
            )
            val darkModeState = createHomeNativeGlassModeConfigState(
                ConfigManager.readHomeNativeGlassStyle(
                    prefs,
                    ConfigManager.HOME_NATIVE_GLASS_DARK_STYLE_KEYS,
                )
            )
            var selectedDarkMode = HomeNativeGlassHostDarkModeBridge.isDarkModeEnabled() == true
            fun currentModeState(): HomeNativeGlassModeConfigState {
                return if (selectedDarkMode) darkModeState else lightModeState
            }
            var loadingVisibleModeState = false
            var refreshVisibleModePreview: (() -> Unit)? = null
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
                    root,
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
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
            )
            copyHomeNativeGlassImageState(visibleImageState, currentModeState().imageState)
            val tintColorRowAndRefresh = createHomeNativeGlassTintColorRow(
                context = context,
                state = visibleImageState,
                density = density,
            )
            val tintColorRefresh = tintColorRowAndRefresh.second
            fun refreshTintColorAndPreview() {
                tintColorRefresh()
                requestVisibleModePreviewRefresh()
            }

            val tintAlphaRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_DESC,
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                value = currentModeState().tintAlphaPercent,
                suffix = "",
                density = density,
                onStopTrackingTouch = { requestVisibleModePreviewRefresh() },
            )
            val tintAlphaSeekBar = tintAlphaRowAndSeekBar.second
            val backgroundImageImportCallback: (HomeNativeGlassImageAnalysis) -> Unit = { analysis ->
                tintAlphaSeekBar.progress = analysis.tintAlphaPercent.coerceIn(
                    ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ) - ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                requestVisibleModePreviewRefresh()
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
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                value = currentModeState().cardBlurPercent,
                suffix = "%",
                density = density,
                onStopTrackingTouch = { requestVisibleModePreviewRefresh() },
            )
            val blurSeekBar = blurRowAndSeekBar.second

            val radiusRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_DESC,
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                value = currentModeState().cardRadiusDp,
                suffix = "dp",
                density = density,
                onValueChanged = { requestVisibleModePreviewRefresh() },
            )
            val radiusSeekBar = radiusRowAndSeekBar.second

            val tabDynamicTintSwitch = createHomeNativeGlassSwitchRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_TAB_DYNAMIC_TINT_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_TAB_DYNAMIC_TINT_DESC,
                checked = prefs.getBoolean(
                    ConfigManager.KEY_ENABLE_HOME_TAB_DYNAMIC_TINT,
                    ConfigManager.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED,
                ),
                density = density,
            )

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
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                value = currentModeState().shadowStrengthPercent,
                suffix = "%",
                density = density,
                onValueChanged = { requestVisibleModePreviewRefresh() },
            )
            val shadowSeekBar = shadowRowAndSeekBar.second

            fun captureVisibleModeState() {
                val state = currentModeState()
                val previousImagePath = state.imageState.path.trim()
                val previousTintAlphaPercent = state.tintAlphaPercent
                val previousCardBlurPercent = state.cardBlurPercent
                copyHomeNativeGlassImageState(state.imageState, visibleImageState)
                state.tintAlphaPercent = (
                    tintAlphaSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    ).coerceIn(
                    ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                )
                state.cardBlurPercent = (
                    blurSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    ).coerceIn(
                    ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                    ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                )
                state.cardRadiusDp = (
                    radiusSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    ).coerceIn(
                    ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                    ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                )
                state.strokeEnabled = strokeSwitch.second.isChecked
                state.shadowStrengthPercent = (
                    shadowSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    ).coerceIn(
                    ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                    ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                )
                if (
                    state.imageState.path.trim() != previousImagePath ||
                    state.tintAlphaPercent != previousTintAlphaPercent ||
                    state.cardBlurPercent != previousCardBlurPercent
                ) {
                    state.blurCacheImagePath = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH
                }
            }

            fun loadVisibleModeState(darkMode: Boolean) {
                val state = if (darkMode) darkModeState else lightModeState
                loadingVisibleModeState = true
                try {
                    copyHomeNativeGlassImageState(visibleImageState, state.imageState)
                    backgroundImageDisplay.text = homeNativeGlassImageDisplayText(visibleImageState.path)
                    tintColorRefresh()
                    tintAlphaSeekBar.progress = state.tintAlphaPercent.coerceIn(
                        ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                        ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    ) - ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    blurSeekBar.progress = state.cardBlurPercent.coerceIn(
                        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                        ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                    ) - ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    radiusSeekBar.progress = state.cardRadiusDp.coerceIn(
                        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                        ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                    ) - ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    strokeSwitch.second.isChecked = state.strokeEnabled
                    shadowSeekBar.progress = state.shadowStrengthPercent.coerceIn(
                        ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                        ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
                    ) - ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
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
            }

            fun selectMode(darkMode: Boolean) {
                if (selectedDarkMode == darkMode) return
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

            addHomeNativeGlassSettingRow(root, modeSelectorRowAndRefresh.first, density, topMarginDp = 0)
            addHomeNativeGlassSettingRow(root, backgroundImageRowAndDisplay.first, density)
            addHomeNativeGlassSettingRow(root, tintColorRowAndRefresh.first, density)
            addHomeNativeGlassSettingRow(root, tintAlphaRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, blurRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, radiusRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, shadowRowAndSeekBar.first, density)
            addHomeNativeGlassSettingRow(root, tabDynamicTintSwitch.first, density)
            addHomeNativeGlassSettingRow(root, strokeSwitch.first, density)

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_NATIVE_GLASS_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setNeutralButton(UiText.Settings.HOME_NATIVE_GLASS_RESTORE_DEFAULTS, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            homeNativeGlassDialog = dialog
            dialog.setOnShowListener {
                refreshHomeNativeGlassDialogPreview()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    loadingVisibleModeState = true
                    try {
                        tintAlphaSeekBar.progress = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT -
                            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                        blurSeekBar.progress = ConfigManager.APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT -
                            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                        radiusSeekBar.progress = ConfigManager.APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP -
                            ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                        visibleImageState.path = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH
                        visibleImageState.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                        visibleImageState.paletteColors = emptyList()
                        visibleImageState.defaultTintColor = null
                        backgroundImageDisplay.text = homeNativeGlassImageDisplayText(visibleImageState.path)
                        tintColorRefresh()
                        tabDynamicTintSwitch.second.isChecked = ConfigManager.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED
                        strokeSwitch.second.isChecked = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED
                        shadowSeekBar.progress = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT -
                            ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT
                    } finally {
                        loadingVisibleModeState = false
                    }
                    captureVisibleModeState()
                    refreshModeSelector?.invoke(selectedDarkMode)
                    refreshHomeNativeGlassDialogPreview()
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { buttonView ->
                    val positiveButton = buttonView as? Button
                    positiveButton?.isEnabled = false
                    captureVisibleModeState()
                    val lightStyle = homeNativeGlassStyleFromModeState(lightModeState, blurCacheImagePath = "")
                    val darkStyle = homeNativeGlassStyleFromModeState(darkModeState, blurCacheImagePath = "")
                    val tabDynamicTintEnabled = tabDynamicTintSwitch.second.isChecked
                    thread(name = "tbhook-home-native-glass-blur-cache", isDaemon = true) {
                        fun ensureBlurCache(
                            modeName: String,
                            style: ConfigManager.HomeNativeGlassStyleConfig,
                        ): String {
                            if (style.backgroundImagePath.isBlank()) return ""
                            return runCatching {
                                HomeNativeGlassImageCache.ensureBlurCache(
                                    context = context,
                                    sourcePath = style.backgroundImagePath,
                                    blurPercent = style.cardBlurPercent,
                                    tintOffset = style.tintAlphaPercent,
                                    appleMaterial = true,
                                    cacheNamespace = modeName,
                                )
                            }.onFailure {
                                XposedCompat.logW(
                                    "[SettingsMenuHook] home native blur cache failed: " +
                                        "$modeName: ${it.message}"
                                )
                            }.getOrDefault("")
                        }
                        val savedLightStyle = lightStyle.copy(
                            blurCacheImagePath = ensureBlurCache("light", lightStyle)
                        )
                        val savedDarkStyle = darkStyle.copy(
                            blurCacheImagePath = ensureBlurCache("dark", darkStyle)
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (!dialog.isShowing) return@post
                            val editor = prefs.edit()
                            putHomeNativeGlassStyle(
                                editor,
                                ConfigManager.HOME_NATIVE_GLASS_LIGHT_STYLE_KEYS,
                                savedLightStyle,
                            )
                            putHomeNativeGlassStyle(
                                editor,
                                ConfigManager.HOME_NATIVE_GLASS_DARK_STYLE_KEYS,
                                savedDarkStyle,
                            )
                            editor
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_COLOR)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_PALETTE)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_STROKE_ENABLED)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED)
                                .remove(ConfigManager.KEY_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT)
                                .putBoolean(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_OFFSET_MIGRATED,
                                    true,
                                )
                                .putBoolean(
                                    ConfigManager.KEY_ENABLE_HOME_TAB_DYNAMIC_TINT,
                                    tabDynamicTintEnabled,
                                )
                                .apply()
                            Toast.makeText(
                                context,
                                UiText.Settings.HOME_NATIVE_GLASS_SAVED,
                                Toast.LENGTH_SHORT,
                            ).show()
                            dialog.dismiss()
                        }
                    }
                }
            }
            dialog.setOnDismissListener {
                if (pendingHomeNativeGlassImagePick?.state === visibleImageState) {
                    pendingHomeNativeGlassImagePick = null
                }
                previewRequestSerial++
                clearVisiblePreviewBitmap()
                homeNativeGlassDialog = null
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeNativeGlassDialog failed: ${t.message}")
        }
    }

    private fun createHomeNativeGlassImagePickerRow(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
        darkModeProvider: () -> Boolean = { false },
        refreshPalette: (() -> Unit)? = null,
        onImportedAnalysis: ((HomeNativeGlassImageAnalysis) -> Unit)? = null,
    ): Pair<View, TextView> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_LABEL
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_DESC
            applySettingsRowDescriptionStyle(tokens, density, rightPaddingDp = 0f, bottomPaddingDp = 8f)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val display = TextView(context).apply {
            text = homeNativeGlassImageDisplayText(state.path)
            textSize = SETTINGS_VALUE_TEXT_SP
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            background = createModelScoreThresholdInputBackground(context, density)
            tag = HomeNativeGlassStyleRole.INPUT_TEXT
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                launchHomeNativeGlassImagePicker(
                    context,
                    state,
                    this,
                    darkModeProvider(),
                    refreshPalette,
                    onImportedAnalysis,
                )
            }
        }
        controlRow.addView(
            display,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1.0f),
        )

        val chooseButton = Button(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CHOOSE
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            tag = HomeNativeGlassStyleRole.BUTTON_ACCENT
            setOnClickListener {
                launchHomeNativeGlassImagePicker(
                    context,
                    state,
                    display,
                    darkModeProvider(),
                    refreshPalette,
                    onImportedAnalysis,
                )
            }
        }
        controlRow.addView(
            chooseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (8 * density).toInt()
            },
        )

        val clearButton = Button(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CLEAR
            UiStyle.paintScanActionButton(this, density, tokens.textSecondary)
            tag = HomeNativeGlassStyleRole.BUTTON_SECONDARY
            setOnClickListener {
                state.path = ""
                state.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                state.paletteColors = emptyList()
                state.defaultTintColor = null
                display.text = homeNativeGlassImageDisplayText(state.path)
                refreshPalette?.invoke()
                if (pendingHomeNativeGlassImagePick?.state === state) {
                    pendingHomeNativeGlassImagePick = null
                }
            }
        }
        controlRow.addView(
            clearButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (40 * density).toInt(),
            ).apply {
                leftMargin = (4 * density).toInt()
            },
        )
        root.addView(controlRow)
        return root to display
    }

    private fun parseHomeNativeGlassTintPalette(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';', '\n')
            .asSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .map { ConfigManager.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .toList()
    }

    private fun serializeHomeNativeGlassTintPalette(colors: List<Int>): String {
        return colors.asSequence()
            .map { ConfigManager.normalizeHomeNativeGlassTintColor(it) }
            .filter { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR }
            .distinct()
            .joinToString(",") { it.toString() }
    }

    private fun parseHomeNativeGlassUserTintColor(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val hex = when {
            value.startsWith("#") -> value.substring(1)
            value.startsWith("0x", ignoreCase = true) -> value.substring(2)
            else -> value
        }
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { digit ->
                digit !in '0'..'9' &&
                    digit !in 'a'..'f' &&
                    digit !in 'A'..'F'
            }
        ) {
            return null
        }
        val rgb = (hex.toLongOrNull(16) ?: return null) and 0xFFFFFFL
        return ConfigManager.normalizeHomeNativeGlassTintColor(
            Color.rgb(
                ((rgb ushr 16) and 0xFFL).toInt(),
                ((rgb ushr 8) and 0xFFL).toInt(),
                (rgb and 0xFFL).toInt(),
            )
        )
    }

    private fun homeNativeGlassCachedAutoTintColorOrNull(color: Int): Int? {
        val normalized = ConfigManager.normalizeHomeNativeGlassTintColor(color)
        return normalized.takeIf { it != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR }
    }

    private fun createHomeNativeGlassTintColorRow(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
    ): Pair<View, () -> Unit> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_LABEL
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DESC
            applySettingsRowDescriptionStyle(tokens, density, rightPaddingDp = 0f, bottomPaddingDp = 8f)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })

        val swatchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                swatchRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val emptyText = TextView(context).apply {
            textSize = SETTINGS_ROW_DESC_SP
            setTextColor(tokens.textMuted)
            includeFontPadding = false
            setLineSpacing(1f * density, 1f)
            setPadding(0, (6 * density).toInt(), 0, 0)
            tag = HomeNativeGlassStyleRole.MUTED_TEXT
        }
        root.addView(emptyText)

        lateinit var refresh: () -> Unit
        refresh = {
            swatchRow.removeAllViews()
            val selectedAuto = state.tintColor == ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
            swatchRow.addView(
                createHomeNativeGlassTintDefaultSwatch(
                    context,
                    tokens,
                    density,
                    state.defaultTintColor,
                    selectedAuto,
                ) {
                    state.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                    refresh()
                },
                LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                    rightMargin = (8 * density).toInt()
                },
            )
            val paletteColors = displayedHomeNativeGlassPaletteColors(state)
            paletteColors.forEachIndexed { index, color ->
                swatchRow.addView(
                    createHomeNativeGlassTintSwatch(
                        context = context,
                        color = color,
                        selected = state.tintColor == color,
                        density = density,
                        tokens = tokens,
                        index = index,
                    ) {
                        state.tintColor = color
                        refresh()
                    },
                    LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                        rightMargin = (8 * density).toInt()
                    },
                )
            }
            if (state.path.isNotBlank()) {
                swatchRow.addView(
                    createHomeNativeGlassTintAddSwatch(
                        context = context,
                        tokens = tokens,
                        density = density,
                    ) {
                        showHomeNativeGlassAddTintColorDialog(
                            context = context,
                            state = state,
                            density = density,
                            refreshPalette = refresh,
                        )
                    },
                    LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                        rightMargin = (8 * density).toInt()
                    },
                )
            }
            emptyText.text = if (state.path.isBlank()) {
                UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_EMPTY
            } else {
                UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_UNAVAILABLE
            }
            emptyText.visibility = if (paletteColors.isEmpty()) View.VISIBLE else View.GONE
        }
        refresh()
        return root to refresh
    }

    private fun displayedHomeNativeGlassPaletteColors(
        state: HomeNativeGlassImageSelectionState,
    ): List<Int> {
        val defaultColor = state.defaultTintColor ?: return state.paletteColors
        val minDistanceSquared = HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE *
            HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE
        return state.paletteColors.filter { color ->
            state.tintColor == color ||
                homeNativeGlassTintColorDistanceSquared(color, defaultColor) >= minDistanceSquared
        }
    }

    private fun showHomeNativeGlassAddTintColorDialog(
        context: Context,
        state: HomeNativeGlassImageSelectionState,
        density: Float,
        refreshPalette: () -> Unit,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val padding = settingsDialogPadding(density)
            val input = android.widget.EditText(context).apply {
                setSingleLine(true)
                hint = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_HINT
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(
                    0,
                    (2 * density).toInt(),
                    0,
                    (8 * density).toInt(),
                )
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val color = parseHomeNativeGlassUserTintColor(input.text?.toString().orEmpty())
                    if (color == null) {
                        input.error = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_INVALID
                        return@setOnClickListener
                    }
                    val existed = state.paletteColors.any { it == color }
                    if (!existed) {
                        state.paletteColors = state.paletteColors + color
                    }
                    state.tintColor = color
                    refreshPalette()
                    Toast.makeText(
                        context,
                        if (existed) {
                            UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DUPLICATED
                        } else {
                            UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADDED
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
                input.post {
                    input.requestFocus()
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeNativeGlassAddTintColorDialog failed: ${t.message}")
        }
    }

    private fun createHomeNativeGlassTintDefaultSwatch(
        context: Context,
        tokens: UiStyle.Tokens,
        density: Float,
        previewColor: Int?,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        return View(context).apply {
            background = createHomeNativeGlassTintDefaultSwatchBackground(
                previewColor,
                selected,
                tokens,
                density,
            )
            contentDescription = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DEFAULT
            isSelected = selected
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintDefaultSwatchBackground(
        previewColor: Int?,
        selected: Boolean,
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(previewColor ?: tokens.accentSoft)
            cornerRadius = 999f * density
            setStroke(
                ((if (selected) 3f else 1f) * density).toInt().coerceAtLeast(1),
                if (selected) tokens.accent else tokens.divider,
            )
        }
    }

    private fun createHomeNativeGlassTintAddSwatch(
        context: Context,
        tokens: UiStyle.Tokens,
        density: Float,
        onClick: () -> Unit,
    ): View {
        return TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.accent)
            tag = HomeNativeGlassStyleRole.ACCENT_TEXT
            background = createHomeNativeGlassTintAddSwatchBackground(tokens, density)
            contentDescription = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_ADD_DESC
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintAddSwatchBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(tokens.surfaceAlt)
            cornerRadius = 999f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    private fun createHomeNativeGlassTintSwatch(
        context: Context,
        color: Int,
        selected: Boolean,
        density: Float,
        tokens: UiStyle.Tokens,
        index: Int,
        onClick: () -> Unit,
    ): View {
        return View(context).apply {
            background = createHomeNativeGlassTintSwatchBackground(color, selected, tokens, density)
            contentDescription = UiText.Settings.homeNativeGlassTintColorSwatch(index + 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeNativeGlassTintSwatchBackground(
        color: Int,
        selected: Boolean,
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 999f * density
            setStroke(
                ((if (selected) 3f else 1f) * density).toInt().coerceAtLeast(1),
                if (selected) tokens.accent else tokens.divider,
            )
        }
    }

    private fun createHomeNativeGlassSwitchRow(
        context: Context,
        label: String,
        description: String,
        checked: Boolean,
        density: Float,
    ): Pair<View, Switch> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = label
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        textContainer.addView(TextView(context).apply {
            text = description
            applySettingsRowDescriptionStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })
        root.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val switch = Switch(context).apply {
            isChecked = checked
            applyHomeNativeGlassSwitchTint(this, tokens)
            tag = HomeNativeGlassStyleRole.SWITCH
        }
        root.addView(
            switch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.setOnClickListener {
            switch.isChecked = !switch.isChecked
        }
        return root to switch
    }

    private fun applyHomeNativeGlassSwitchTint(
        switch: Switch,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        switch.thumbTintList = ColorStateList(states, intArrayOf(tokens.accent, tokens.accentThumbOff))
        switch.trackTintList = ColorStateList(states, intArrayOf(tokens.accentTrackOn, tokens.accentTrackOff))
    }

    private fun addHomeNativeGlassSettingRow(
        root: LinearLayout,
        row: View,
        density: Float,
        topMarginDp: Int = 0,
    ) {
        applyHomeNativeGlassSettingRowStyle(row, density)
        root.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (topMarginDp * density).toInt()
            },
        )
    }

    private fun applyHomeNativeGlassSettingRowStyle(row: View, density: Float) {
        row.setPadding(
            0,
            row.paddingTop,
            0,
            row.paddingBottom,
        )
        row.background = null
    }

    private fun createSeekBarRow(
        context: Context,
        label: String,
        description: String,
        minValue: Int,
        maxValue: Int,
        value: Int,
        suffix: String,
        density: Float,
        onValueChanged: ((Int) -> Unit)? = null,
        onStopTrackingTouch: ((Int) -> Unit)? = null,
    ): Pair<View, android.widget.SeekBar> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = settingsRowVerticalPadding(density)
            setPadding(0, verticalPadding, 0, verticalPadding)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = label
            applySettingsRowTitleStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_TITLE
        })
        textContainer.addView(TextView(context).apply {
            text = description
            applySettingsRowDescriptionStyle(tokens, density)
            tag = HomeNativeGlassStyleRole.ROW_DESCRIPTION
        })
        header.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val valueText = TextView(context).apply {
            textSize = SETTINGS_VALUE_TEXT_SP
            setTextColor(homeNativeGlassSliderAccent(tokens))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            includeFontPadding = false
            setPadding((8 * density).toInt(), 0, 0, 0)
            tag = HomeNativeGlassStyleRole.ACCENT_TEXT
        }
        header.addView(
            valueText,
            LinearLayout.LayoutParams((72 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(header)

        val safeValue = value.coerceIn(minValue, maxValue)
        val seekBar = android.widget.SeekBar(context).apply {
            max = maxValue - minValue
            progress = safeValue - minValue
            splitTrack = false
            applyHomeNativeGlassSeekBarTint(this, tokens)
            tag = HomeNativeGlassStyleRole.SEEK_BAR
        }
        fun updateValueText(progress: Int) {
            valueText.text = "${progress + minValue}$suffix"
        }
        updateValueText(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                updateValueText(progress)
                onValueChanged?.invoke(progress + minValue)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progress = seekBar?.progress ?: return
                onStopTrackingTouch?.invoke(progress + minValue)
            }
        })
        root.addView(
            seekBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return root to seekBar
    }

    private fun applyHomeNativeGlassSeekBarTint(
        seekBar: android.widget.SeekBar,
        tokens: UiStyle.Tokens,
    ) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_enabled),
        )
        val accent = homeNativeGlassSliderAccent(tokens)
        val trackColor = homeNativeGlassSliderTrackColor(tokens)
        seekBar.progressTintList = ColorStateList(states, intArrayOf(tokens.textMuted, accent))
        seekBar.thumbTintList = ColorStateList(states, intArrayOf(tokens.textMuted, accent))
        seekBar.progressBackgroundTintList = ColorStateList(states, intArrayOf(tokens.divider, trackColor))
        seekBar.secondaryProgressTintList = ColorStateList(states, intArrayOf(tokens.divider, trackColor))
    }

    private fun homeNativeGlassSliderAccent(tokens: UiStyle.Tokens): Int {
        return tokens.accent
    }

    private fun homeNativeGlassSliderTrackColor(tokens: UiStyle.Tokens): Int {
        return tokens.inputStroke
    }

    private fun showCustomPostModelScoreDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val thresholdByKey = ConfigManager.parseModelScoreThresholds(
                prefs.getString(ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "")
            ).associate { it.key to it.threshold }.toMutableMap()
            val autoPercentiles = ConfigManager.parseModelScoreAutoPercentiles(
                prefs.getString(ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES, "")
            ).toMutableMap()
            val inputRows = ArrayList<Pair<ModelScoreUiItem, android.widget.EditText>>(modelScoreUiItems.size)
            val statsRefreshers = ArrayList<() -> Unit>(modelScoreUiItems.size)
            val initialStatsPostLimit = prefs.getInt(
                ConfigManager.KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
                ConfigManager.DEFAULT_MODEL_SCORE_STATS_POST_LIMIT
            ).coerceAtLeast(ConfigManager.MIN_MODEL_SCORE_STATS_POST_LIMIT)

            fun persistModelScoreAutoThreshold(
                modelKey: String,
                percentile: Int,
                value: Double,
                applyThreshold: Boolean,
            ) {
                val roundedValue = ConfigManager.roundModelScoreThreshold(value)
                autoPercentiles[modelKey] = ConfigManager.normalizeModelScoreAutoPercentile(percentile)
                val merged = LinkedHashMap<String, Double>()
                for (threshold in ConfigManager.parseModelScoreThresholds(
                    prefs.getString(ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "")
                )) {
                    merged[threshold.key] = threshold.threshold
                }
                if (applyThreshold) {
                    merged[modelKey] = roundedValue
                } else {
                    merged.remove(modelKey)
                }
                val thresholds = merged.map { (key, threshold) ->
                    ConfigManager.ModelScoreThreshold(key, threshold)
                }
                prefs.edit()
                    .putString(
                        ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                        ConfigManager.serializeModelScoreThresholds(thresholds)
                    )
                    .putString(
                        ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                        ConfigManager.serializeModelScoreAutoPercentiles(autoPercentiles)
                    )
                    .apply()
            }

            fun persistModelScoreAutoDisabled(modelKey: String) {
                val thresholds = ConfigManager.parseModelScoreThresholds(
                    prefs.getString(ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS, "")
                ).filter { it.key != modelKey }
                prefs.edit()
                    .putString(
                        ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                        ConfigManager.serializeModelScoreThresholds(thresholds)
                    )
                    .putString(
                        ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                        ConfigManager.serializeModelScoreAutoPercentiles(autoPercentiles)
                    )
                    .apply()
            }

            val tokens = UiStyle.tokens(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }

            val statsLimitRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (10 * density).toInt())
            }
            val statsLimitTextContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            statsLimitTextContainer.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_LABEL
                    applySettingsRowTitleStyle(tokens, density)
                }
            )
            statsLimitTextContainer.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_DESC
                    applySettingsRowDescriptionStyle(tokens, density)
                }
            )
            statsLimitRow.addView(
                statsLimitTextContainer,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            )
            val clearStatsButton = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_ICON
                contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEAR_DESC
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(tokens.danger)
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener {
                    CustomPostModelScoreStats.clear()
                    statsRefreshers.forEach { refresh -> refresh() }
                    Toast.makeText(
                        context,
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_CLEARED,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            statsLimitRow.addView(
                clearStatsButton,
                LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt()).apply {
                    rightMargin = (6 * density).toInt()
                }
            )
            val statsLimitInput = android.widget.EditText(context).apply {
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                hint = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_HINT
                setText(initialStatsPostLimit.toString())
                textSize = SETTINGS_VALUE_TEXT_SP
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                includeFontPadding = false
                background = createModelScoreThresholdInputBackground(context, density)
                setPadding(
                    (4 * density).toInt(),
                    0,
                    (4 * density).toInt(),
                    0,
                )
                prepareModelScoreInputForKeyboard(this)
            }
            statsLimitRow.addView(
                statsLimitInput,
                LinearLayout.LayoutParams((50 * density).toInt(), (34 * density).toInt())
            )
            root.addView(statsLimitRow)
            root.addView(createDivider(context, padding))

            root.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_GUIDE
                    textSize = SETTINGS_ROW_DESC_SP
                    setTextColor(tokens.textSecondary)
                    includeFontPadding = false
                    setLineSpacing(1f * density, 1f)
                    setPadding(0, 0, 0, (8 * density).toInt())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            for (item in modelScoreUiItems) {
                val itemContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val verticalPadding = settingsRowVerticalPadding(density)
                    setPadding(0, verticalPadding, 0, verticalPadding)
                }
                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textContainer.addView(
                    TextView(context).apply {
                        text = item.label
                        applySettingsRowTitleStyle(tokens, density)
                    }
                )
                textContainer.addView(
                    TextView(context).apply {
                        text = item.description
                        applySettingsRowDescriptionStyle(tokens, density)
                    }
                )
                row.addView(
                    textContainer,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                )

                val statsContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                lateinit var input: android.widget.EditText
                var expanded = false
                lateinit var refreshStatsContent: () -> Unit

                fun enableAutoPercentile(percentile: Int, value: Double, sampleCount: Int) {
                    val roundedValue = ConfigManager.roundModelScoreThreshold(value)
                    val valueText = formatModelScoreThreshold(roundedValue)
                    val applyThreshold = sampleCount > ConfigManager.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT
                    if (applyThreshold) {
                        input.setText(valueText)
                        input.setSelection(input.text?.length ?: 0)
                        thresholdByKey[item.key] = roundedValue
                    } else {
                        input.setText("")
                        thresholdByKey.remove(item.key)
                    }
                    persistModelScoreAutoThreshold(item.key, percentile, roundedValue, applyThreshold)
                    Toast.makeText(
                        context,
                        if (applyThreshold) {
                            UiText.Settings.modelScoreAutoPercentileEnabled(percentile, valueText)
                        } else {
                            UiText.Settings.modelScoreAutoPercentilePending(
                                percentile,
                                sampleCount,
                                ConfigManager.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT,
                            )
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                    if (expanded) refreshStatsContent()
                }

                fun disableAutoPercentile(percentile: Int) {
                    autoPercentiles.remove(item.key)
                    thresholdByKey.remove(item.key)
                    input.setText("")
                    persistModelScoreAutoDisabled(item.key)
                    Toast.makeText(
                        context,
                        UiText.Settings.modelScoreAutoPercentileDisabled(percentile),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (expanded) refreshStatsContent()
                }

                refreshStatsContent = {
                    statsContainer.removeAllViews()
                    statsContainer.addView(
                        createModelScoreStatsContent(
                            context,
                            density,
                            item,
                            autoPercentiles[item.key],
                            onAutoPercentileClick = { percentile, value, sampleCount, enabled ->
                                if (enabled) {
                                    disableAutoPercentile(percentile)
                                } else {
                                    enableAutoPercentile(percentile, value, sampleCount)
                                }
                            },
                            onAutoPercentileSelected = { percentile, value, sampleCount ->
                                enableAutoPercentile(percentile, value, sampleCount)
                            },
                        )
                    )
                }
                statsRefreshers.add {
                    if (expanded) refreshStatsContent()
                }
                val expandButton = TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_ICON
                    contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_DESC
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        expanded = !expanded
                        UiStyle.animateExpandArrow(this, expanded)
                        if (expanded) {
                            contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_COLLAPSE_DESC
                            refreshStatsContent()
                            UiStyle.animateCardExpand(statsContainer)
                        } else {
                            contentDescription = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EXPAND_DESC
                            UiStyle.animateCardCollapse(statsContainer)
                        }
                    }
                }
                row.addView(
                    expandButton,
                    LinearLayout.LayoutParams((34 * density).toInt(), (40 * density).toInt()).apply {
                        rightMargin = (6 * density).toInt()
                    }
                )

                input = android.widget.EditText(context).apply {
                    setSingleLine(true)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    hint = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_HINT
                    setText(formatModelScoreThreshold(thresholdByKey[item.key]))
                    textSize = SETTINGS_VALUE_TEXT_SP
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setHintTextColor(tokens.textMuted)
                    includeFontPadding = false
                    background = createModelScoreThresholdInputBackground(context, density)
                    setPadding(
                        (4 * density).toInt(),
                        0,
                        (4 * density).toInt(),
                        0,
                    )
                    prepareModelScoreInputForKeyboard(this)
                }
                inputRows.add(item to input)
                row.addView(
                    input,
                    LinearLayout.LayoutParams((50 * density).toInt(), (34 * density).toInt())
                )
                itemContainer.addView(row)
                itemContainer.addView(
                    statsContainer,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }
                )
                root.addView(itemContainer)
            }

            val scroll = ScrollView(context).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DIALOG_TITLE)
                .setView(scroll)
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    prepareModelScoreDialogWindowForInput(window)
                    applyUnifiedDialogCardStyle(window, density)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val statsPostLimit = statsLimitInput.text?.toString().orEmpty().trim().toIntOrNull()
                    if (
                        statsPostLimit == null ||
                        statsPostLimit < ConfigManager.MIN_MODEL_SCORE_STATS_POST_LIMIT
                    ) {
                        Toast.makeText(
                            context,
                            UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_INVALID,
                            Toast.LENGTH_SHORT
                        ).show()
                        statsLimitInput.requestFocus()
                        showKeyboardForModelScoreInput(statsLimitInput)
                        return@setOnClickListener
                    }
                    val nextAutoPercentiles = autoPercentiles.toMutableMap()
                    val thresholds = ArrayList<ConfigManager.ModelScoreThreshold>(inputRows.size)
                    for ((item, input) in inputRows) {
                        val raw = input.text?.toString().orEmpty().trim()
                        if (raw.isEmpty()) {
                            if (nextAutoPercentiles.containsKey(item.key) && thresholdByKey[item.key] == null) {
                                continue
                            }
                            nextAutoPercentiles.remove(item.key)
                            continue
                        }
                        val threshold = raw.toDoubleOrNull()
                        if (
                            threshold == null ||
                            threshold < 0.0 ||
                            threshold.isNaN() ||
                            threshold.isInfinite()
                        ) {
                            Toast.makeText(
                                context,
                                UiText.Settings.modelScoreThresholdInvalid(item.label),
                                Toast.LENGTH_SHORT
                            ).show()
                            input.requestFocus()
                            showKeyboardForModelScoreInput(input)
                            return@setOnClickListener
                        }
                        if (
                            nextAutoPercentiles.containsKey(item.key) &&
                            CustomPostModelScoreStats.summary(item.key).sampleCount <=
                            ConfigManager.MIN_MODEL_SCORE_AUTO_PERCENTILE_SAMPLE_COUNT
                        ) {
                            thresholdByKey.remove(item.key)
                            continue
                        }
                        thresholds.add(ConfigManager.ModelScoreThreshold(item.key, threshold))
                    }
                    prefs.edit()
                        .putInt(
                            ConfigManager.KEY_FILTER_POST_MODEL_SCORE_STATS_POST_LIMIT,
                            statsPostLimit
                        )
                        .putString(
                            ConfigManager.KEY_FILTER_POST_MODEL_SCORE_THRESHOLDS,
                            ConfigManager.serializeModelScoreThresholds(thresholds)
                        )
                        .putString(
                            ConfigManager.KEY_FILTER_POST_MODEL_SCORE_AUTO_PERCENTILES,
                            ConfigManager.serializeModelScoreAutoPercentiles(nextAutoPercentiles)
                        )
                        .apply()
                    CustomPostModelScoreStats.trimToPostLimitAsync(statsPostLimit)
                    val toastText = if (thresholds.isEmpty()) {
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_EMPTY
                    } else {
                        UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_SAVED
                    }
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(toastText),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostModelScoreDialog failed: ${t.message}")
        }
    }

    private fun createModelScoreThresholdInputBackground(context: Context, density: Float): Drawable {
        val tokens = UiStyle.tokens(context)
        return createModelScoreThresholdInputBackground(tokens, density)
    }

    private fun createModelScoreThresholdInputBackground(
        tokens: UiStyle.Tokens,
        density: Float,
    ): Drawable {
        return UiStyle.createModelScoreInputBackground(tokens, density)
    }

    @Suppress("DEPRECATION")
    private fun prepareModelScoreDialogWindowForInput(window: android.view.Window) {
        window.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        )
    }

    private fun prepareModelScoreInputForKeyboard(input: android.widget.EditText) {
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.setOnClickListener {
            showKeyboardForModelScoreInput(input)
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showKeyboardForModelScoreInput(input)
        }
    }

    private fun showKeyboardForModelScoreInput(input: android.widget.EditText) {
        input.post {
            input.requestFocus()
            val imm = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun createModelScoreStatsContent(
        context: Context,
        density: Float,
        item: ModelScoreUiItem,
        autoPercentile: Int?,
        onAutoPercentileClick: (Int, Double, Int, Boolean) -> Unit,
        onAutoPercentileSelected: (Int, Double, Int) -> Unit,
    ): View {
        val padding = (12 * density).toInt()
        val summary = CustomPostModelScoreStats.summary(item.key)
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createModelScoreExpandedStatsBackground(context, density)
            setPadding(padding, padding, padding, padding)

            if (summary.sampleCount <= 0) {
                addView(
                    TextView(context).apply {
                        text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_EMPTY
                        applySettingsMessageStyle(tokens, density, primary = false)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            } else {
                addView(
                    createModelScoreStatsMetricRow(
                        context,
                        density,
                        summary,
                        autoPercentile,
                        onAutoPercentileClick,
                        onAutoPercentileSelected,
                    )
                )
                addView(
                    ModelScoreDistributionView(context, summary),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (150 * density).toInt(),
                    ).apply {
                        topMargin = (10 * density).toInt()
                    }
                )
            }
        }
    }

    private fun createModelScoreStatsMetricRow(
        context: Context,
        density: Float,
        summary: CustomPostModelScoreStats.Summary,
        autoPercentile: Int?,
        onAutoPercentileClick: (Int, Double, Int, Boolean) -> Unit,
        onAutoPercentileSelected: (Int, Double, Int) -> Unit,
    ): View {
        val active = autoPercentile != null
        val displayPercentile = ConfigManager.normalizeModelScoreAutoPercentile(
            autoPercentile ?: ConfigManager.DEFAULT_MODEL_SCORE_AUTO_PERCENTILE
        )
        val percentileValue = summary.percentileValue(displayPercentile)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBaselineAligned(false)
            gravity = Gravity.CENTER_VERTICAL
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_SAMPLE_COUNT_LABEL,
                    summary.sampleCount.toString(),
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.modelScoreAutoPercentileLabel(displayPercentile),
                    formatModelScoreValue(percentileValue),
                    active = active,
                    onClick = if (percentileValue != null) {
                        { onAutoPercentileClick(displayPercentile, percentileValue, summary.sampleCount, active) }
                    } else {
                        null
                    },
                    onLongClick = {
                        showModelScoreAutoPercentileDialog(
                            context,
                            summary,
                            displayPercentile,
                            onAutoPercentileSelected,
                        )
                    },
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (8 * density).toInt()
                }
            )
            addView(
                createModelScoreStatsMetric(
                    context,
                    UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_AVERAGE_LABEL,
                    formatModelScoreValue(summary.average),
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (8 * density).toInt()
                }
            )
        }
    }

    private fun createModelScoreStatsMetric(
        context: Context,
        label: String,
        value: String,
        active: Boolean = false,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            val density = context.resources.displayMetrics.density
            val interactive = onClick != null || onLongClick != null
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                if (interactive) (8 * density).toInt() else 0,
                (6 * density).toInt(),
                if (interactive) (8 * density).toInt() else 0,
                (6 * density).toInt(),
            )
            if (interactive) {
                background = UiStyle.createMetricCellBackground(tokens, density, active)
                isClickable = true
                isFocusable = true
                if (onClick != null) setOnClickListener { onClick() }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
            }
            addView(TextView(context).apply {
                text = label
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(if (active) tokens.accent else tokens.textSecondary)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = value
                textSize = SETTINGS_ROW_TITLE_SP
                setTextColor(if (active) tokens.accent else tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, (4 * context.resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
    }

    private fun createModelScoreAutoPercentileMetricBackground(density: Float, active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (active) 0xFFEAF2FF.toInt() else 0xFFF5F7FB.toInt())
            cornerRadius = 9f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), if (active) 0x664C87F7 else 0x244C87F7)
        }
    }

    private fun formatModelScoreValue(value: Double?): String {
        value ?: return "-"
        val text = String.format(Locale.US, "%.6f", value)
        return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun showModelScoreAutoPercentileDialog(
        context: Context,
        summary: CustomPostModelScoreStats.Summary,
        currentPercentile: Int,
        onSelected: (Int, Double, Int) -> Unit,
    ) {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val padding = settingsDialogPadding(density)
        val percentiles = ConfigManager.SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES
        val checkedIndex = percentiles.indexOf(currentPercentile).coerceAtLeast(0)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
        }
        lateinit var dialog: AlertDialog
        percentiles.forEachIndexed { index, percentile ->
            val active = index == checkedIndex
            root.addView(
                TextView(context).apply {
                    text = UiText.Settings.modelScoreAutoPercentileLabel(percentile)
                    applySettingsRowTitleStyle(tokens, density)
                    if (active) {
                        setTextColor(tokens.accent)
                    }
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(0, settingsRowVerticalPadding(density), 0, settingsRowVerticalPadding(density))
                    setOnClickListener {
                        val value = summary.percentileValue(percentile)
                        if (value != null) {
                            onSelected(percentile, value, summary.sampleCount)
                        }
                        dialog.dismiss()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        dialog = AlertDialog.Builder(context, dialogThemeFor(context))
            .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_AUTO_PERCENTILE_DIALOG_TITLE)
            .setView(createDialogScrollContainer(context, root))
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
                UiStyle.animateDialogEntry(window.decorView, density)
            }
        }
        dialog.show()
    }

    private fun createModelScoreExpandedStatsBackground(context: Context, density: Float): GradientDrawable {
        val tokens = UiStyle.tokens(context)
        return GradientDrawable().apply {
            setColor(tokens.surfaceAlt)
            cornerRadius = 10f * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    private fun formatModelScoreThreshold(value: Double?): String {
        return ConfigManager.formatModelScoreThresholdValue(value)
    }

    private fun showCustomPostFilterKeywordDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val initialRaw = prefs.getString(ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST, "").orEmpty()

            fun keywordCount(raw: String): Int {
                if (raw.isBlank()) return 0
                return raw.split('\n', ',', '\uff0c', ';', '\uff1b')
                    .asSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .count()
            }

            val guideView = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_GUIDE
                textSize = SETTINGS_ROW_DESC_SP
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setLineSpacing(1f * density, 1f)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val counterView = TextView(context).apply {
                textSize = SETTINGS_SECTION_TITLE_SP
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.accent)
                includeFontPadding = false
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            fun refreshCounter(raw: String) {
                counterView.text = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_COUNT_PREFIX + keywordCount(raw)
            }

            val input = android.widget.EditText(context).apply {
                setSingleLine(false)
                maxLines = 6
                minLines = 4
                gravity = Gravity.TOP or Gravity.START
                hint = UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_HINT
                setText(initialRaw)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(
                    0,
                    (2 * density).toInt(),
                    0,
                    (8 * density).toInt()
                )
            }
            refreshCounter(initialRaw)
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    refreshCounter(s?.toString().orEmpty())
                }
            })

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = settingsDialogPadding(density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(
                    guideView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    counterView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val raw = input.text?.toString().orEmpty().trim()
                    prefs.edit()
                        .putString(ConfigManager.KEY_FILTER_POST_FORUM_KEYWORD_LIST, raw)
                        .apply()
                    val toastText = if (raw.isEmpty()) {
                        UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_EMPTY
                    } else {
                        UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_SAVED
                    }
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterKeywordDialog failed: ${t.message}")
        }
    }

    private fun showPbLikeAutoReplyDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            val label = TextView(context).apply {
                text = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_LABEL
                textSize = SETTINGS_SECTION_TITLE_SP
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
                includeFontPadding = false
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val input = android.widget.EditText(context).apply {
                setSingleLine(false)
                maxLines = 6
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                hint = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_HINT
                setText(prefs.getString(ConfigManager.KEY_PB_LIKE_AUTO_REPLY_TEXT, "").orEmpty())
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textMuted)
                textSize = SETTINGS_INPUT_TEXT_SP
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(label)
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.PB_LIKE_AUTO_REPLY_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, container))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val content = input.text?.toString()?.trim().orEmpty()
                    if (content.isBlank()) {
                        Toast.makeText(context, UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_EMPTY, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    prefs.edit()
                        .putString(ConfigManager.KEY_PB_LIKE_AUTO_REPLY_TEXT, content)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartHint(UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
            input.post {
                input.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showPbLikeAutoReplyDialog failed: ${t.message}")
        }
    }

    private fun showReplyVisibilityProbeDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            fun createNumberInputRow(
                label: String,
                description: String,
                value: Int,
                unit: String? = null,
            ): Pair<View, android.widget.EditText> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val verticalPadding = settingsRowVerticalPadding(density)
                    setPadding(0, verticalPadding, 0, verticalPadding)
                }
                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textContainer.addView(TextView(context).apply {
                    text = label
                    applySettingsRowTitleStyle(tokens, density)
                })
                textContainer.addView(TextView(context).apply {
                    text = description
                    applySettingsRowDescriptionStyle(tokens, density)
                })
                row.addView(
                    textContainer,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
                )

                val input = android.widget.EditText(context).apply {
                    setSingleLine(true)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    setText(value.toString())
                    textSize = SETTINGS_VALUE_TEXT_SP
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    setHintTextColor(tokens.textMuted)
                    includeFontPadding = false
                    background = createModelScoreThresholdInputBackground(context, density)
                    setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    prepareModelScoreInputForKeyboard(this)
                }
                row.addView(
                    input,
                    LinearLayout.LayoutParams((64 * density).toInt(), (34 * density).toInt()).apply {
                        leftMargin = (10 * density).toInt()
                    },
                )
                if (unit != null) {
                    row.addView(
                        TextView(context).apply {
                            text = unit
                            textSize = SETTINGS_VALUE_TEXT_SP
                            setTextColor(tokens.textSecondary)
                            includeFontPadding = false
                            setPadding((6 * density).toInt(), 0, 0, 0)
                        },
                    )
                }
                return row to input
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
            }
            root.addView(TextView(context).apply {
                text = UiText.Settings.REPLY_VISIBILITY_PROBE_GUIDE
                applySettingsMessageStyle(tokens, density)
                setPadding(0, 0, 0, (8 * density).toInt())
            })

            val maxAttemptsRow = createNumberInputRow(
                UiText.Settings.REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS_LABEL,
                UiText.Settings.replyVisibilityProbeMaxAttemptsDesc(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
                prefs.getInt(
                    ConfigManager.KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ).coerceIn(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                ),
            )
            val intervalRow = createNumberInputRow(
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_LABEL,
                UiText.Settings.replyVisibilityProbeIntervalDesc(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                prefs.getInt(
                    ConfigManager.KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.DEFAULT_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ).coerceIn(
                    ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                ),
                UiText.Settings.REPLY_VISIBILITY_PROBE_INTERVAL_UNIT,
            )
            root.addView(maxAttemptsRow.first)
            root.addView(createDivider(context, padding))
            root.addView(intervalRow.first)

            fun parseNumber(input: android.widget.EditText, min: Int, max: Int): Int? {
                val value = input.text?.toString()?.trim()?.toIntOrNull() ?: return null
                return value.takeIf { it in min..max }
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.REPLY_VISIBILITY_PROBE_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val maxAttempts = parseNumber(
                        maxAttemptsRow.second,
                        ConfigManager.MIN_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                        ConfigManager.MAX_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS,
                    )
                    val intervalMs = parseNumber(
                        intervalRow.second,
                        ConfigManager.MIN_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                        ConfigManager.MAX_REPLY_VISIBILITY_PROBE_INTERVAL_MS,
                    )
                    if (maxAttempts == null || intervalMs == null) {
                        Toast.makeText(
                            context,
                            UiText.Settings.REPLY_VISIBILITY_PROBE_CONFIG_INVALID,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@setOnClickListener
                    }
                    prefs.edit()
                        .putInt(ConfigManager.KEY_REPLY_VISIBILITY_PROBE_MAX_ATTEMPTS, maxAttempts)
                        .putInt(ConfigManager.KEY_REPLY_VISIBILITY_PROBE_INTERVAL_MS, intervalMs)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.REPLY_VISIBILITY_PROBE_CONFIG_SAVED,
                        Toast.LENGTH_SHORT,
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showReplyVisibilityProbeDialog failed: ${t.message}")
        }
    }

    private fun showHomeTopTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            val persistedSelection = ConfigManager.HomeTopTabSelection(
                materialEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_MATERIAL, true),
                recommendEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND, true),
                liveEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_LIVE, true),
                followedEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED, true),
            )
            val initialSelection = persistedSelection

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val materialRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.HOME_TOP_TAB_MATERIAL_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.materialEnabled,
            )
            val recommendRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.HOME_TOP_TAB_RECOMMEND_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.recommendEnabled,
            )
            val liveRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.HOME_TOP_TAB_LIVE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.liveEnabled,
            )
            val followedRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.HOME_TOP_TAB_FOLLOWED_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.followedEnabled,
            )

            root.addView(materialRow)
            root.addView(recommendRow)
            root.addView(liveRow)
            root.addView(followedRow)

            val materialSwitch = findSwitchView(materialRow)
            val recommendSwitch = findSwitchView(recommendRow)
            val liveSwitch = findSwitchView(liveRow)
            val followedSwitch = findSwitchView(followedRow)
            if (materialSwitch == null || recommendSwitch == null || liveSwitch == null || followedSwitch == null) {
                XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.HOME_TOP_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val rawSelection = ConfigManager.HomeTopTabSelection(
                        materialEnabled = materialSwitch.isChecked,
                        recommendEnabled = recommendSwitch.isChecked,
                        liveEnabled = liveSwitch.isChecked,
                        followedEnabled = followedSwitch.isChecked,
                    )
                    if (!rawSelection.hasEnabledTab()) {
                        Toast.makeText(context, UiText.Settings.HOME_TOP_TAB_AT_LEAST_ONE, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val normalized = ConfigManager.normalizeHomeTopTabSelection(rawSelection)

                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_HOME_TOP_TAB_MATERIAL, normalized.materialEnabled)
                        .putBoolean(ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND, normalized.recommendEnabled)
                        .putBoolean(ConfigManager.KEY_HOME_TOP_TAB_LIVE, normalized.liveEnabled)
                        .putBoolean(ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED, normalized.followedEnabled)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.HOME_TOP_TAB_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showHomeTopTabDialog failed: ${t.message}")
        }
    }

    private fun showBottomTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)

            val persistedSelection = ConfigManager.BottomTabSelection(
                homeEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_HOME, true),
                enterForumEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM, true),
                retailStoreEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE, true),
                messageEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_MESSAGE, true),
                mineEnabled = prefs.getBoolean(ConfigManager.KEY_BOTTOM_TAB_MINE, true),
            )
            val initialSelection = persistedSelection

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val homeRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_HOME_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.homeEnabled,
            )
            val enterForumRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_ENTER_FORUM_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.enterForumEnabled,
            )
            val retailStoreRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_RETAIL_STORE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.retailStoreEnabled,
            )
            val messageRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_MESSAGE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.messageEnabled,
            )
            val mineRow = createSwitchRow(
                context = context,
                prefs = prefs,
                label = UiText.Settings.BOTTOM_TAB_MINE_LABEL,
                description = null,
                prefKey = null,
                padding = padding,
                enabled = true,
                defaultValue = initialSelection.mineEnabled,
            )

            root.addView(homeRow)
            root.addView(enterForumRow)
            root.addView(retailStoreRow)
            root.addView(messageRow)
            root.addView(mineRow)

            val homeSwitch = findSwitchView(homeRow)
            val enterForumSwitch = findSwitchView(enterForumRow)
            val retailStoreSwitch = findSwitchView(retailStoreRow)
            val messageSwitch = findSwitchView(messageRow)
            val mineSwitch = findSwitchView(mineRow)
            if (
                homeSwitch == null ||
                enterForumSwitch == null ||
                retailStoreSwitch == null ||
                messageSwitch == null ||
                mineSwitch == null
            ) {
                XposedCompat.logW("[SettingsMenuHook] showBottomTabDialog failed: switch view missing")
                return
            }

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.BOTTOM_TAB_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> applyUnifiedDialogCardStyle(window, density) }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val rawSelection = ConfigManager.BottomTabSelection(
                        homeEnabled = homeSwitch.isChecked,
                        enterForumEnabled = enterForumSwitch.isChecked,
                        retailStoreEnabled = retailStoreSwitch.isChecked,
                        messageEnabled = messageSwitch.isChecked,
                        mineEnabled = mineSwitch.isChecked,
                    )
                    if (!rawSelection.hasEnabledTab()) {
                        Toast.makeText(context, UiText.Settings.BOTTOM_TAB_AT_LEAST_ONE, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val normalized = ConfigManager.normalizeBottomTabSelection(rawSelection)

                    prefs.edit()
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_HOME, normalized.homeEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_ENTER_FORUM, normalized.enterForumEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_RETAIL_STORE, normalized.retailStoreEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_MESSAGE, normalized.messageEnabled)
                        .putBoolean(ConfigManager.KEY_BOTTOM_TAB_MINE, normalized.mineEnabled)
                        .apply()
                    Toast.makeText(
                        context,
                        UiText.Settings.withRestartTiebaHint(UiText.Settings.BOTTOM_TAB_SAVED),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[SettingsMenuHook] showBottomTabDialog failed: ${t.message}")
        }
    }



    private fun restartHostApp(context: Context) {
        try {
            val restartIntent = Intent().apply {
                setClassName(Constants.TARGET_PACKAGE, StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(restartIntent)
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] restart launch failed: ${t.message}")
            XposedCompat.log(t)
            return
        }

        try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
        try { exitProcess(0) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
    }

}
