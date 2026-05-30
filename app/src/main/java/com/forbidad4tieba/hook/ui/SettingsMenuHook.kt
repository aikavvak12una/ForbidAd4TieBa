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
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.feature.signin.AutoSignInManager
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreCatalog
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassDynamicTintCache
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassImageCache
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassReadableTextPalette
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
    private const val SCAN_RUNNING_PULSE_INITIAL_DELAY_MS = 900L
    private const val SCAN_RUNNING_PULSE_INTERVAL_MS = 1800L
    private const val REQUEST_HOME_NATIVE_GLASS_IMAGE = 0x4E47
    private const val HOME_NATIVE_GLASS_SOURCE_DIR_NAME = "home_native_glass"
    private const val HOME_NATIVE_GLASS_SOURCE_FILE_PREFIX = "source_"
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS = 6
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE = 96
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_PIXEL_ALPHA = 32
    private const val HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE = 34
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_SAMPLE_EDGE = 48
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_MIN_LUMA = 24
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_MAX_LUMA = 232
    private const val HOME_NATIVE_GLASS_DEFAULT_TINT_CHROMA_BIAS = 32

    private class HomeNativeGlassImageSelectionState(
        var path: String,
        var tintColor: Int,
    ) {
        var paletteColors: List<Int> = emptyList()
        var defaultTintColor: Int? = null
    }

    private data class PendingHomeNativeGlassImagePick(
        val contextRef: WeakReference<Context>,
        val displayRef: WeakReference<TextView>,
        val state: HomeNativeGlassImageSelectionState,
        val refreshPalette: (() -> Unit)?,
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
    private val firstSettingsDialogBackgroundErrorLogged = AtomicBoolean(false)
    private val homeNativeGlassImagePickerActivityResultHooks =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Boolean>())
    @Volatile private var pendingHomeNativeGlassImagePick: PendingHomeNativeGlassImagePick? = null

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
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
                XposedCompat.logD("[SettingsMenuHook] > settings init intercepted")
                val context = chain.args.firstOrNull { it is Context } as? Context
                    ?: (chain.args.firstOrNull { it is View } as? View)?.context
                try {
                    val settingsContainer = resolveSettingsContainer(chain.thisObject, containerField)
                    if (settingsContainer != null && context != null && ViewExt.markSettingsLongPressBound(settingsContainer)) {
                        settingsContainer.setOnLongClickListener {
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
        refreshPalette: (() -> Unit)? = null,
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
            refreshPalette = refreshPalette,
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
                copyHomeNativeGlassImageToPrivateFile(context, uri)
            }.onFailure {
                XposedCompat.logW("[SettingsMenuHook] import home native image failed: ${it.message}")
            }.getOrNull()
            val paletteColors = if (copiedPath.isNullOrBlank()) {
                emptyList()
            } else {
                extractHomeNativeGlassTintPalette(copiedPath)
            }
            val defaultTintColor = if (copiedPath.isNullOrBlank()) {
                null
            } else {
                extractHomeNativeGlassDefaultTintColor(copiedPath)
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
                    pending.state.paletteColors = paletteColors
                    pending.state.defaultTintColor = defaultTintColor
                    pending.displayRef.get()?.text = homeNativeGlassImageDisplayText(copiedPath)
                    pending.refreshPalette?.invoke()
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

    private fun extractHomeNativeGlassDefaultTintColor(path: String): Int? {
        val file = File(path.trim())
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sampleSize = 1
        while (max(width, height) / sampleSize > HOME_NATIVE_GLASS_DEFAULT_TINT_SAMPLE_EDGE) {
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
            extractHomeNativeGlassDefaultTintColor(bitmap)
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

    private fun copyHomeNativeGlassImageToPrivateFile(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext ?: context
        val sourceDir = File(appContext.filesDir, HOME_NATIVE_GLASS_SOURCE_DIR_NAME)
        if (!sourceDir.exists() && !sourceDir.mkdirs()) return null
        val fileName = "$HOME_NATIVE_GLASS_SOURCE_FILE_PREFIX${System.currentTimeMillis()}.img"
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
        cleanupOldHomeNativeGlassSourceImages(sourceDir, targetFile.name)
        return targetFile.absolutePath
    }

    private fun cleanupOldHomeNativeGlassSourceImages(sourceDir: File, keepName: String) {
        runCatching {
            sourceDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.name.startsWith(HOME_NATIVE_GLASS_SOURCE_FILE_PREFIX) &&
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

    private fun extractHomeNativeGlassTintPalette(path: String): List<Int> {
        val file = File(path.trim())
        if (!file.isFile || file.length() <= 0L) return emptyList()
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return emptyList()
        var sampleSize = 1
        while (max(width, height) / sampleSize > HOME_NATIVE_GLASS_TINT_PALETTE_SAMPLE_EDGE) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return emptyList()
        return try {
            extractHomeNativeGlassTintPalette(bitmap)
        } finally {
            bitmap.recycle()
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
        val selected = ArrayList<Int>(HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS)
        val ranked = buckets.values.asSequence()
            .filter { it.weight > 0L }
            .map { bucket ->
                val red = (bucket.red / bucket.weight).toInt().coerceIn(0, 255)
                val green = (bucket.green / bucket.weight).toInt().coerceIn(0, 255)
                val blue = (bucket.blue / bucket.weight).toInt().coerceIn(0, 255)
                val maxChannel = max(red, max(green, blue))
                val minChannel = red.coerceAtMost(green).coerceAtMost(blue)
                HomeNativeGlassPaletteColor(
                    color = Color.rgb(red, green, blue),
                    score = bucket.weight * (maxChannel - minChannel + 64),
                )
            }
            .sortedByDescending { it.score }
            .toList()
        val minDistanceSquared = HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE *
            HOME_NATIVE_GLASS_TINT_PALETTE_MIN_DISTANCE
        for (candidate in ranked) {
            if (selected.any { homeNativeGlassTintColorDistanceSquared(it, candidate.color) < minDistanceSquared }) {
                continue
            }
            selected.add(candidate.color)
            if (selected.size >= HOME_NATIVE_GLASS_TINT_PALETTE_MAX_COLORS) break
        }
        return selected
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
        val score: Long,
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
            val padding = (16 * density).toInt()
            val messageView = TextView(activity).apply {
                text = UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_MESSAGE
                textSize = 14f
                setTextColor(tokens.textPrimary)
                setPadding(padding, padding, padding, padding)
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
                .setTitle(UiText.Settings.INITIAL_SCAN_ENVIRONMENT_WARNING_TITLE)
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
            val padding = (20 * density).toInt()
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
                setPadding(padding, padding / 2, padding, padding / 2)
            }

            val restrictedFeaturesUnlocked = ConfigManager.isRestrictedFeaturesUnlocked(context)
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
                        false
                    )
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
                            SwitchItem(UiText.Settings.DISABLE_LOCATION_COMPONENTS_LABEL, UiText.Settings.DISABLE_LOCATION_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_LOCATION_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_AI_COMPONENTS_LABEL, UiText.Settings.DISABLE_AI_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_AI_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_HEAVY_FEATURE_COMPONENTS_LABEL, UiText.Settings.DISABLE_HEAVY_FEATURE_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_HEAVY_FEATURE_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_VIDEO_COMPONENTS_LABEL, UiText.Settings.DISABLE_VIDEO_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_VIDEO_COMPONENTS, true, true),
                            SwitchItem(UiText.Settings.DISABLE_UPDATE_DOWNLOAD_COMPONENTS_LABEL, UiText.Settings.DISABLE_UPDATE_DOWNLOAD_COMPONENTS_DESC, ConfigManager.KEY_DISABLE_UPDATE_DOWNLOAD_COMPONENTS, true, true),
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
                        showHomeNativeGlassDialog(context, prefs)
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
                    },
                    SwitchItem(UiText.Settings.FILTER_ENTER_FORUM_WEB_LABEL, UiText.Settings.FILTER_ENTER_FORUM_WEB_DESC, ConfigManager.KEY_FILTER_ENTER_FORUM_WEB, true, false)
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
                    gap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
                    root.addView(gap)
                }

                val tokens = UiStyle.tokens(context)
                val headerLabel = TextView(context).apply {
                    text = group.name
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.7f).toInt(), 0, (padding * 0.35f).toInt())
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
            defaultEnabledGap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
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
                textSize = 12.5f
                letterSpacing = 0.04f
                setTextColor(tokensForDefault.accent)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
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
                gap.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (12 * density).toInt())
                addView(gap)

                addView(TextView(context).apply {
                    text = UiText.Settings.ABOUT
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokensForDefault.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.7f).toInt(), 0, (padding * 0.35f).toInt())
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
                setPadding(padding, padding, padding, padding / 2)

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
                    textSize = 11.5f
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
            builder.setPositiveButton(UiText.Settings.SAVE) { _, _ ->
                Toast.makeText(context, UiText.Settings.SETTINGS_SAVED, Toast.LENGTH_SHORT).show()
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
            val padding = (16 * density).toInt()
            val messageView = TextView(context).apply {
                text = UiText.Settings.RESTRICTED_FEATURE_WARNING_MESSAGE
                textSize = 14f
                setTextColor(tokens.textPrimary)
                setPadding(padding, padding, padding, padding)
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
                .setTitle(UiText.Settings.RESTRICTED_FEATURE_WARNING_TITLE)
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
            val padding = (16 * density).toInt()
            val contentView = TextView(context).apply {
                text = AboutInfoManager.runtimeEnvironmentJsonForSettings(context)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(tokens.textPrimary)
                includeFontPadding = false
                setTextIsSelectable(true)
                setPadding(padding, padding, padding, padding)
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
                .setTitle(UiText.Settings.RUNTIME_ENVIRONMENT)
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
        val padding = (20 * density).toInt()
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
            setPadding(padding, padding, padding, padding)
        }

        // Title area.
        val titleContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.DIALOG_SCAN_TITLE
            textSize = 20f
            letterSpacing = 0.02f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        })
        titleContainer.addView(TextView(activity).apply {
            text = UiText.Settings.BRAND_TAG
            textSize = 11f
            letterSpacing = 0.06f
            typeface = Typeface.MONOSPACE
            setTextColor(tokens.textMuted)
            setPadding(0, (2 * density).toInt(), 0, 0)
        }.also { UiStyle.animateBrandTagShimmer(it) })
        root.addView(titleContainer)

        // Progress bar.
        val progressBar = UiStyle.ThinProgressBar(activity, tokens).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (5 * density).toInt(),
            )
        }
        root.addView(progressBar)

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
                        displayedProgress < 0.18f -> 0.025f
                        displayedProgress < 0.60f -> 0.015f
                        else -> 0.006f
                    }
                    val nextProgress = (displayedProgress + drift).coerceAtMost(0.88f)
                    if (nextProgress > displayedProgress + 0.0005f) {
                        displayedProgress = nextProgress
                        progressBar.setProgress(nextProgress)
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
            textSize = 13f
            setTextColor(tokens.textSecondary)
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        root.addView(statusView)

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
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val resultVersionView = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(tokens.textMuted)
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        resultCard.addView(resultStatusView)
        resultCard.addView(resultVersionView)
        root.addView(resultCard)

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
                        textSize = 11f
                        typeface = Typeface.MONOSPACE
                        setTextColor(tokens.textMuted)
                        setPadding(0, (6 * density).toInt(), 0, 0)
                        setTextIsSelectable(true)
                    }
                    resultCard.addView(runtimeEnvironmentView)

                    if (resultWarning != null) {
                        val warningView = TextView(activity).apply {
                            text = resultWarning
                            textSize = 12f
                            setTextColor(summaryColor)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(warningView)
                    }

                    val exceptionLine = scanExceptionLine
                    if (exceptionLine != null) {
                        val exceptionView = TextView(activity).apply {
                            text = exceptionLine
                            textSize = 11f
                            typeface = Typeface.MONOSPACE
                            setTextColor(tokens.danger)
                            setPadding(0, (6 * density).toInt(), 0, 0)
                        }
                        resultCard.addView(exceptionView)
                    }

                    // Show failed or missing hook points.
                    if (failedLines.isNotEmpty()) {
                        val failedView = TextView(activity).apply {
                            text = failedLines.joinToString("\n")
                            textSize = 11f
                            typeface = Typeface.MONOSPACE
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
        val dialogTheme = if (tokens.night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, dialogTheme)
            .setTitle(UiText.Settings.DIALOG_SCAN_ACTION_TITLE)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> startSymbolScanWithDialog(activity, classLoader ?: activity.classLoader, clearUserData = false)
                    1 -> clearModuleDataAndRestart(activity)
                }
            }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                applyUnifiedDialogCardStyle(window, density)
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

    private fun showPerformanceOptimizationDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
        groups: List<SettingGroup>,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val tokens = UiStyle.tokens(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
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
                    textSize = 12.5f
                    letterSpacing = 0.04f
                    setTextColor(tokens.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(0, (padding * 0.35f).toInt(), 0, (padding * 0.25f).toInt())
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
                .setTitle(UiText.Settings.PERFORMANCE_OPTIMIZATION_DIALOG_TITLE)
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
            val padding = (16 * density).toInt()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
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
                .setTitle(UiText.Settings.CUSTOM_POST_FILTER_DIALOG_TITLE)
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

    private fun showHomeNativeGlassDialog(
        context: Context,
        prefs: android.content.SharedPreferences,
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }

            val backgroundImageState = HomeNativeGlassImageSelectionState(
                prefs.getString(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                ) ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                ConfigManager.normalizeHomeNativeGlassTintColor(
                    prefs.getInt(
                        ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_COLOR,
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
                    )
                ),
            ).apply {
                path = path.trim()
                paletteColors = parseHomeNativeGlassTintPalette(
                    prefs.getString(
                        ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_PALETTE,
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE,
                    )
                )
                defaultTintColor = homeNativeGlassCachedAutoTintColorOrNull(
                    prefs.getInt(
                        ConfigManager.KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
                    )
                )
                if (path.isBlank()) {
                    tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                } else if (
                    tintColor != ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR &&
                    paletteColors.none { it == tintColor }
                ) {
                    paletteColors = paletteColors + tintColor
                }
            }
            val tintColorRowAndRefresh = createHomeNativeGlassTintColorRow(
                context = context,
                state = backgroundImageState,
                density = density,
            )
            val tintColorRefresh = tintColorRowAndRefresh.second
            createHomeNativeGlassImagePickerRow(
                context = context,
                state = backgroundImageState,
                density = density,
                refreshPalette = tintColorRefresh,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density, topMarginDp = 0) }
            addHomeNativeGlassSettingRow(root, tintColorRowAndRefresh.first, density)

            val tintAlphaRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_TINT_ALPHA_DESC,
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                value = prefs.getInt(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                ),
                suffix = "",
                density = density,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }
            val tintAlphaSeekBar = tintAlphaRowAndSeekBar.second

            val blurRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_CARD_BLUR_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_CARD_BLUR_DESC,
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                value = prefs.getInt(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                ),
                suffix = "%",
                density = density,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }
            val blurSeekBar = blurRowAndSeekBar.second

            val radiusRowAndSeekBar = createSeekBarRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_CARD_RADIUS_DESC,
                minValue = ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                maxValue = ConfigManager.MAX_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                value = prefs.getInt(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                ),
                suffix = "dp",
                density = density,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }
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
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }.second

            val strokeSwitch = createHomeNativeGlassSwitchRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_STROKE_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_STROKE_DESC,
                checked = prefs.getBoolean(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_STROKE_ENABLED,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
                ),
                density = density,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }.second

            val shadowSwitch = createHomeNativeGlassSwitchRow(
                context = context,
                label = UiText.Settings.HOME_NATIVE_GLASS_SHADOW_LABEL,
                description = UiText.Settings.HOME_NATIVE_GLASS_SHADOW_DESC,
                checked = prefs.getBoolean(
                    ConfigManager.KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED,
                    ConfigManager.DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED,
                ),
                density = density,
            ).also { addHomeNativeGlassSettingRow(root, it.first, density) }.second

            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setTitle(UiText.Settings.HOME_NATIVE_GLASS_DIALOG_TITLE)
                .setView(createDialogScrollContainer(context, root))
                .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
                .setNeutralButton(UiText.Settings.HOME_NATIVE_GLASS_RESTORE_DEFAULTS, null)
                .setPositiveButton(UiText.Settings.SAVE, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    applyUnifiedDialogCardStyle(window, density)
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    tintAlphaSeekBar.progress = ConfigManager.APPLE_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT -
                        ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    blurSeekBar.progress = ConfigManager.APPLE_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT -
                        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    radiusSeekBar.progress = ConfigManager.APPLE_HOME_NATIVE_GLASS_CARD_RADIUS_DP -
                        ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    backgroundImageState.tintColor = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                    tintColorRefresh()
                    tabDynamicTintSwitch.isChecked = ConfigManager.DEFAULT_HOME_TAB_DYNAMIC_TINT_ENABLED
                    strokeSwitch.isChecked = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED
                    shadowSwitch.isChecked = ConfigManager.DEFAULT_HOME_NATIVE_GLASS_SHADOW_ENABLED
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { buttonView ->
                    val positiveButton = buttonView as? Button
                    positiveButton?.isEnabled = false
                    val backgroundImagePath = backgroundImageState.path.trim()
                    val tintColor = if (backgroundImagePath.isBlank()) {
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR
                    } else {
                        ConfigManager.normalizeHomeNativeGlassTintColor(backgroundImageState.tintColor)
                    }
                    val autoTintColor = if (backgroundImagePath.isBlank()) {
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
                    } else {
                        ConfigManager.normalizeHomeNativeGlassTintColor(
                            backgroundImageState.defaultTintColor
                                ?: ConfigManager.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR
                        )
                    }
                    val tintPalette = if (backgroundImagePath.isBlank()) {
                        ConfigManager.DEFAULT_HOME_NATIVE_GLASS_TINT_PALETTE
                    } else {
                        serializeHomeNativeGlassTintPalette(backgroundImageState.paletteColors)
                    }
                    val blurPercent = blurSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT
                    val tintAlphaPercent = tintAlphaSeekBar.progress +
                        ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT
                    val tabDynamicTintEnabled = tabDynamicTintSwitch.isChecked
                    val radiusDp = radiusSeekBar.progress + ConfigManager.MIN_HOME_NATIVE_GLASS_CARD_RADIUS_DP
                    val strokeEnabled = strokeSwitch.isChecked
                    val shadowEnabled = shadowSwitch.isChecked
                    thread(name = "tbhook-home-native-glass-blur-cache", isDaemon = true) {
                        val blurCacheImagePath = runCatching {
                            HomeNativeGlassImageCache.ensureBlurCache(
                                context = context,
                                sourcePath = backgroundImagePath,
                                blurPercent = blurPercent,
                                appleMaterial = true,
                            )
                        }.onFailure {
                            XposedCompat.logW("[SettingsMenuHook] home native blur cache failed: ${it.message}")
                        }.getOrDefault("")
                        val textPalettes = runCatching {
                            HomeNativeGlassReadableTextPalette.computeSerialized(
                                context = context,
                                blurCachePath = blurCacheImagePath,
                                sourcePath = backgroundImagePath,
                                tintAlphaPercent = tintAlphaPercent,
                            )
                        }.onFailure {
                            XposedCompat.logW("[SettingsMenuHook] home native text palette failed: ${it.message}")
                        }.getOrDefault(
                            HomeNativeGlassReadableTextPalette.Serialized(light = "", dark = "")
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (!dialog.isShowing) return@post
                            prefs.edit()
                                .putString(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
                                    backgroundImagePath,
                                )
                                .putString(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
                                    blurCacheImagePath,
                                )
                                .putString(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_LIGHT,
                                    textPalettes.light,
                                )
                                .putString(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TEXT_PALETTE_DARK,
                                    textPalettes.dark,
                                )
                                .putInt(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_COLOR,
                                    tintColor,
                                )
                                .putInt(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
                                    autoTintColor,
                                )
                                .putString(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_PALETTE,
                                    tintPalette,
                                )
                                .putInt(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
                                    tintAlphaPercent,
                                )
                                .putBoolean(
                                    ConfigManager.KEY_ENABLE_HOME_TAB_DYNAMIC_TINT,
                                    tabDynamicTintEnabled,
                                )
                                .putInt(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
                                    blurPercent,
                                )
                                .putInt(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
                                    radiusDp,
                                )
                                .putBoolean(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_STROKE_ENABLED,
                                    strokeEnabled,
                                )
                                .putBoolean(
                                    ConfigManager.KEY_HOME_NATIVE_GLASS_SHADOW_ENABLED,
                                    shadowEnabled,
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
                if (pendingHomeNativeGlassImagePick?.state === backgroundImageState) {
                    pendingHomeNativeGlassImagePick = null
                }
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
        refreshPalette: (() -> Unit)? = null,
    ): Pair<View, TextView> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_LABEL
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_DESC
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
        })

        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val display = TextView(context).apply {
            text = homeNativeGlassImageDisplayText(state.path)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(tokens.textPrimary)
            includeFontPadding = false
            background = createModelScoreThresholdInputBackground(context, density)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            setOnClickListener {
                launchHomeNativeGlassImagePicker(context, state, this, refreshPalette)
            }
        }
        controlRow.addView(
            display,
            LinearLayout.LayoutParams(0, (40 * density).toInt(), 1.0f),
        )

        val chooseButton = Button(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_CHOOSE
            UiStyle.paintScanActionButton(this, density, tokens.accent)
            setOnClickListener {
                launchHomeNativeGlassImagePicker(context, state, display, refreshPalette)
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
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_LABEL
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(context).apply {
            text = UiText.Settings.HOME_NATIVE_GLASS_TINT_COLOR_DESC
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
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
            textSize = 12f
            setTextColor(tokens.textMuted)
            setPadding(0, (6 * density).toInt(), 0, 0)
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
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        textContainer.addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        textContainer.addView(TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (2 * density).toInt(), (12 * density).toInt(), 0)
        })
        root.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val switch = Switch(context).apply {
            isChecked = checked
            applyHomeNativeGlassSwitchTint(this, tokens)
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
        topMarginDp: Int = 8,
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
    ): Pair<View, android.widget.SeekBar> {
        val tokens = UiStyle.tokens(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
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
            textSize = 15f
            setTextColor(tokens.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        textContainer.addView(TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, (2 * density).toInt(), (12 * density).toInt(), 0)
        })
        header.addView(
            textContainer,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f),
        )
        val valueText = TextView(context).apply {
            textSize = 13f
            setTextColor(homeNativeGlassSliderAccent(tokens))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            includeFontPadding = false
            setPadding((8 * density).toInt(), 0, 0, 0)
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
        }
        fun updateValueText(progress: Int) {
            valueText.text = "${progress + minValue}$suffix"
        }
        updateValueText(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                updateValueText(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
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
            val padding = (16 * density).toInt()
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
                setPadding(padding, padding, padding, 0)
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
                    textSize = 15f
                    setTextColor(tokens.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
            statsLimitTextContainer.addView(
                TextView(context).apply {
                    text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_LIMIT_DESC
                    textSize = 12f
                    setTextColor(tokens.textSecondary)
                    setPadding(0, (2 * density).toInt(), (12 * density).toInt(), 0)
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
                textSize = 13f
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
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
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
                    setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                }
                val textContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textContainer.addView(
                    TextView(context).apply {
                        text = item.label
                        textSize = 15f
                        setTextColor(tokens.textPrimary)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                )
                textContainer.addView(
                    TextView(context).apply {
                        text = item.description
                        textSize = 12f
                        setTextColor(tokens.textSecondary)
                        setPadding(0, (2 * density).toInt(), (12 * density).toInt(), 0)
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
                    textSize = 13f
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
                .setTitle(UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_DIALOG_TITLE)
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
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createModelScoreExpandedStatsBackground(context, density)
            setPadding(padding, padding, padding, padding)

            if (summary.sampleCount <= 0) {
                addView(
                    TextView(context).apply {
                        text = UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_STATS_EMPTY
                        textSize = 14f
                        setTextColor(UiStyle.tokens(context).textSecondary)
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
                textSize = 12f
                setTextColor(if (active) tokens.accent else tokens.textSecondary)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(if (active) tokens.accent else tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
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
        val percentiles = ConfigManager.SUPPORTED_MODEL_SCORE_AUTO_PERCENTILES
        val labels = percentiles.map { UiText.Settings.modelScoreAutoPercentileLabel(it) }.toTypedArray()
        val checkedIndex = percentiles.indexOf(currentPercentile).coerceAtLeast(0)
        val dialogTheme = if (tokens.night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        AlertDialog.Builder(context, dialogTheme)
            .setTitle(UiText.Settings.CUSTOM_POST_FILTER_MODEL_SCORE_AUTO_PERCENTILE_DIALOG_TITLE)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val percentile = percentiles[which]
                val value = summary.percentileValue(percentile)
                if (value != null) {
                    onSelected(percentile, value, summary.sampleCount)
                }
                dialog.dismiss()
            }
            .setNegativeButton(UiText.Settings.BUTTON_CANCEL, null)
            .create()
            .apply {
                setOnShowListener {
                    window?.let { applyUnifiedDialogCardStyle(it, context.resources.displayMetrics.density) }
                }
            }
            .show()
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
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            val counterView = TextView(context).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.accent)
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
                textSize = 15f
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
                val padding = (16 * density).toInt()
                setPadding(padding, padding, padding, 0)
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
                .setTitle(UiText.Settings.CUSTOM_POST_FILTER_KEYWORD_DIALOG_TITLE)
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
            val padding = (16 * density).toInt()

            val label = TextView(context).apply {
                text = UiText.Settings.PB_LIKE_AUTO_REPLY_CONTENT_LABEL
                textSize = 12.5f
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
                textSize = 15f
                includeFontPadding = false
                background = UiStyle.createPlainInputUnderlineBackground(tokens, density)
                setPadding(0, (2 * density).toInt(), 0, (8 * density).toInt())
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
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
                .setTitle(UiText.Settings.PB_LIKE_AUTO_REPLY_DIALOG_TITLE)
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

    private fun showHomeTopTabDialog(context: Context, prefs: android.content.SharedPreferences) {
        try {
            val density = context.resources.displayMetrics.density
            val padding = (16 * density).toInt()

            val persistedSelection = ConfigManager.HomeTopTabSelection(
                materialEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_MATERIAL, true),
                recommendEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_RECOMMEND, true),
                liveEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_LIVE, true),
                followedEnabled = prefs.getBoolean(ConfigManager.KEY_HOME_TOP_TAB_FOLLOWED, true),
            )
            val initialSelection = persistedSelection

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
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
                .setTitle(UiText.Settings.HOME_TOP_TAB_DIALOG_TITLE)
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
            val padding = (16 * density).toInt()

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
                setPadding(padding, padding, padding, 0)
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
                .setTitle(UiText.Settings.BOTTOM_TAB_DIALOG_TITLE)
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



    private fun restartHostApp(activity: Activity) {
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                activity.startActivity(launchIntent)
            }
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] restart launch failed: ${t.message}")
            XposedCompat.log(t)
        }

        try { activity.finishAffinity() } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }

        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
                try { exitProcess(0) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
            }, 200)
        } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
    }

    private class ModelScoreDistributionView(
        context: Context,
        private val summary: CustomPostModelScoreStats.Summary,
    ) : View(context) {
        private val tokens = UiStyle.tokens(context)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.chartBg
            style = Paint.Style.FILL
        }
        private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.chartAxis
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.chartGrid
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.chartBar
            style = Paint.Style.FILL
        }
        private val highlightBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.chartBarHighlight
            style = Paint.Style.FILL
        }
        private val cumulativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.accent
            strokeWidth = 2.2f
            style = Paint.Style.STROKE
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.textSecondary
            textSize = 10.5f * context.resources.displayMetrics.density * context.resources.configuration.fontScale
        }
        private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.accent
            textSize = 11f * context.resources.displayMetrics.density * context.resources.configuration.fontScale
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val cardRect = RectF()
        private val plotRect = RectF()
        private val barRect = RectF()
        private val barRects = ArrayList<RectF>()
        private val cumulativePath = Path()
        private var selectedBucketIndex = -1

        init {
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            cardRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(cardRect, 10f * density, 10f * density, bgPaint)

            val leftInset = 38f * density
            val rightInset = 10f * density
            val topInset = 34f * density
            val bottomInset = 28f * density
            plotRect.set(leftInset, topInset, width - rightInset, height - bottomInset)
            if (plotRect.width() <= 0f || plotRect.height() <= 0f) return
            canvas.drawLine(plotRect.left, plotRect.top, plotRect.left, plotRect.bottom, axisPaint)
            canvas.drawLine(plotRect.left, plotRect.bottom, plotRect.right, plotRect.bottom, axisPaint)

            val buckets = summary.buckets
            if (buckets.isEmpty()) return
            if (selectedBucketIndex >= buckets.size) selectedBucketIndex = -1
            val maxCount = max(1, buckets.maxOf { it.count })
            drawSelectedBucketInfo(canvas, density, buckets)
            drawAxisLabels(canvas, density, maxCount)
            val gap = (if (buckets.size > 32) 1f else 2f) * density
            val barWidth = ((plotRect.width() - gap * (buckets.size - 1)) / buckets.size).coerceAtLeast(1f)
            barRects.clear()
            buckets.forEachIndexed { index, bucket ->
                val left = plotRect.left + index * (barWidth + gap)
                val top = plotRect.bottom - plotRect.height() * (bucket.count.toFloat() / maxCount)
                barRect.set(left, top, left + barWidth, plotRect.bottom)
                barRects.add(RectF(barRect))
                canvas.drawRoundRect(
                    barRect,
                    2f * density,
                    2f * density,
                    if (index == selectedBucketIndex) highlightBarPaint else barPaint
                )
            }
            drawCumulativeCurve(canvas, buckets)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    val index = findBucketIndex(event.x, event.y)
                    if (index >= 0 && selectedBucketIndex != index) {
                        selectedBucketIndex = index
                        invalidate()
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun drawAxisLabels(canvas: Canvas, density: Float, maxCount: Int) {
            val halfY = plotRect.top + plotRect.height() / 2f
            canvas.drawLine(plotRect.left, halfY, plotRect.right, halfY, gridPaint)

            labelPaint.textAlign = Paint.Align.RIGHT
            val yLabelX = plotRect.left - 6f * density
            canvas.drawText(maxCount.toString(), yLabelX, plotRect.top + 4f * density, labelPaint)
            canvas.drawText((maxCount / 2).toString(), yLabelX, halfY + 4f * density, labelPaint)
            canvas.drawText("0", yLabelX, plotRect.bottom, labelPaint)

            val min = summary.displayMin ?: return
            val maxValue = summary.displayMax ?: return
            val xLabelY = plotRect.bottom + 16f * density
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(formatAxisValue(min), plotRect.left, xLabelY, labelPaint)
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAxisValue(maxValue), plotRect.right, xLabelY, labelPaint)
        }

        private fun drawSelectedBucketInfo(
            canvas: Canvas,
            density: Float,
            buckets: List<CustomPostModelScoreStats.Bucket>,
        ) {
            val selected = buckets.getOrNull(selectedBucketIndex) ?: return
            val text = UiText.Settings.modelScoreBucketInfo(
                formatAxisValue(selected.start),
                formatAxisValue(selected.end),
                selected.count,
                selected.cumulativeCount,
                formatPercent(selected.cumulativeRatio),
            )
            canvas.drawText(text, width / 2f, 20f * density, selectedLabelPaint)
        }

        private fun drawCumulativeCurve(
            canvas: Canvas,
            buckets: List<CustomPostModelScoreStats.Bucket>,
        ) {
            if (buckets.isEmpty() || barRects.size != buckets.size) return
            cumulativePath.reset()
            buckets.forEachIndexed { index, bucket ->
                val rect = barRects[index]
                val x = rect.centerX()
                val y = plotRect.bottom - plotRect.height() * bucket.cumulativeRatio.coerceIn(0.0, 1.0).toFloat()
                if (index == 0) {
                    cumulativePath.moveTo(x, y)
                } else {
                    cumulativePath.lineTo(x, y)
                }
            }
            canvas.drawPath(cumulativePath, cumulativePaint)
        }

        private fun findBucketIndex(x: Float, y: Float): Int {
            if (y < plotRect.top || y > plotRect.bottom) return -1
            if (barRects.isEmpty() || x < plotRect.left || x > plotRect.right) return -1
            val ratio = ((x - plotRect.left) / plotRect.width()).coerceIn(0f, 0.999999f)
            return (ratio * barRects.size).toInt().coerceIn(0, barRects.lastIndex)
        }

        private fun formatAxisValue(value: Double): String {
            val text = String.format(Locale.US, "%.4f", value)
            return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }

        private fun formatPercent(value: Double): String {
            return String.format(Locale.US, "%.1f%%", value.coerceIn(0.0, 1.0) * 100.0)
        }
    }

    private fun applyUnifiedDialogCardStyle(
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
        val tintAlpha = ConfigManager.homeNativeGlassTintAlphaPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
        )
        val overlayAlpha = if (tokens.night) {
            (126 + tintAlpha).coerceIn(126, 226)
        } else {
            (142 + tintAlpha).coerceIn(142, 242)
        }
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

    private fun dialogThemeFor(context: Context): Int {
        return if (UiStyle.tokens(context).night) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
    }

    private fun createDialogScrollContainer(context: Context, content: View): ScrollView {
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

    private fun Button.updateButtonEnabledState(enabled: Boolean) {
        UiStyle.setButtonEnabledState(this, enabled)
    }

    private fun findSwitchView(root: View): Switch? {
        if (root is Switch) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val found = findSwitchView(root.getChildAt(index))
            if (found != null) return found
        }
        return null
    }

    private fun createSwitchRow(
        context: Context, prefs: android.content.SharedPreferences,
        label: String, description: String?, prefKey: String?, padding: Int, enabled: Boolean = true,
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
            setPadding(0, (padding * 0.55f).toInt(), 0, (padding * 0.55f).toInt())
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvLabel = TextView(context).apply {
            text = label
            textSize = 14.5f
            setTextColor(if (enabled) tokens.textPrimary else tokens.textMuted)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setLineSpacing(1.5f * density, 1f)
        }
        textContainer.addView(tvLabel)

        if (description != null) {
            val tvDesc = TextView(context).apply {
                text = description
                textSize = 11.5f
                setTextColor(if (enabled) tokens.textSecondary else tokens.textMuted)
                setPadding(0, (3 * density).toInt(), (14 * density).toInt(), 0)
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
            thumbTintList = ColorStateList(states, intArrayOf(
                tokens.accent,
                tokens.accentThumbOff,
            ))
            trackTintList = ColorStateList(states, intArrayOf(
                tokens.accentTrackOn,
                tokens.accentTrackOff,
            ))

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
        prefs: android.content.SharedPreferences,
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

    private fun createAboutItem(
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

    private fun createDivider(context: Context, padding: Int): View {
        val tokens = UiStyle.tokens(context)
        val density = context.resources.displayMetrics.density
        val divider = View(context)
        divider.setBackgroundColor(tokens.divider)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (0.8f * density).toInt().coerceAtLeast(1))
        lp.setMargins(0, (padding * 0.4f).toInt(), 0, (padding * 0.4f).toInt())
        divider.layoutParams = lp
        return divider
    }
}
