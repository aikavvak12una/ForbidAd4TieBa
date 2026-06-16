package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal object HomeNativeGlassImagePickerBridge {
    private const val REQUEST_HOME_NATIVE_GLASS_IMAGE = 0x4E47

    private val resultHookInstalled = AtomicBoolean(false)
    private val activityResultHooks =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Boolean>())

    @Volatile private var pendingImagePick: PendingHomeNativeGlassImagePick? = null

    fun launch(
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
        installResultHook()
        installResultHook(activity)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }.apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pendingImagePick = PendingHomeNativeGlassImagePick(
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
            pendingImagePick = null
            XposedCompat.logW("[SettingsMenuHook] launch home native image picker failed: ${t.message}")
            Toast.makeText(
                activity,
                UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PICKER_UNAVAILABLE,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun clearIfState(state: HomeNativeGlassImageSelectionState) {
        if (pendingImagePick?.state === state) {
            pendingImagePick = null
        }
    }

    private fun installResultHook() {
        val mod = XposedCompat.module ?: return
        if (!resultHookInstalled.compareAndSet(false, true)) return
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
                if (shouldHandleResult(
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
            resultHookInstalled.set(false)
            XposedCompat.logW("[SettingsMenuHook] install home native image picker result hook failed: ${t.message}")
        }
    }

    private fun installResultHook(activity: Activity) {
        val mod = XposedCompat.module ?: return
        val method = ReflectionUtils.findMethodInHierarchy(
            activity.javaClass,
            "onActivityResult",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Intent::class.java,
        ) ?: return
        if (method.declaringClass == Activity::class.java) return
        synchronized(activityResultHooks) {
            if (activityResultHooks.containsKey(method.declaringClass)) return
            activityResultHooks[method.declaringClass] = true
        }
        try {
            mod.hook(method).intercept { chain ->
                val requestCode = chain.args.getOrNull(0) as? Int
                val resultCode = chain.args.getOrNull(1) as? Int
                val data = chain.args.getOrNull(2) as? Intent
                if (shouldHandleResult(
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
            activityResultHooks.remove(method.declaringClass)
            XposedCompat.logW(
                "[SettingsMenuHook] install activity image picker result hook failed: " +
                    "${method.declaringClass.name}: ${t.message}"
            )
        }
    }

    private fun shouldHandleResult(
        activity: Activity?,
        requestCode: Int?,
        resultCode: Int?,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_HOME_NATIVE_GLASS_IMAGE || pendingImagePick == null) {
            return false
        }
        handleResult(
            activity = activity,
            resultCode = resultCode,
            data = data,
        )
        return true
    }

    private fun handleResult(
        activity: Activity?,
        resultCode: Int?,
        data: Intent?,
    ) {
        val pending = pendingImagePick ?: return
        if (resultCode != Activity.RESULT_OK) {
            pendingImagePick = null
            return
        }
        val uri = data?.data
        val context = pending.contextRef.get() ?: activity
        if (uri == null || context == null) {
            pendingImagePick = null
            return
        }
        thread(name = "tbhook-home-native-glass-image-import", isDaemon = true) {
            val copiedPath = runCatching {
                HomeNativeGlassImageFiles.copyToPrivateFile(context, uri, pending.darkMode)
            }.onFailure {
                XposedCompat.logW("[SettingsMenuHook] import home native image failed: ${it.message}")
            }.getOrNull()
            val imageAnalysis = if (copiedPath.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    HomeNativeGlassImageAnalyzer.analyze(copiedPath, pending.darkMode)
                }.onFailure {
                    XposedCompat.logW("[SettingsMenuHook] analyze home native image failed: ${it.message}")
                }.getOrNull()
            }
            Handler(Looper.getMainLooper()).post {
                if (pendingImagePick !== pending) return@post
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
                    pending.displayRef.get()?.text = HomeNativeGlassImageFiles.displayText(copiedPath)
                    pending.refreshPalette?.invoke()
                    imageAnalysis?.let { pending.onImportedAnalysis?.invoke(it) }
                    Toast.makeText(
                        context,
                        UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_IMPORTED,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                pendingImagePick = null
            }
        }
    }
}
