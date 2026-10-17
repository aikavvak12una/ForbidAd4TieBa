package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.SystemSplashContract
import com.forbidad4tieba.hook.symbol.contract.LaunchSplashContract
import com.forbidad4tieba.hook.symbol.contract.SplashBackground
import java.util.concurrent.atomic.AtomicBoolean

internal val SystemSplashFeature = FeatureDefinition.single(
    id = "SystemSplashHook",
    phase = FeaturePhase.STATIC,
    process = FeatureProcess.SYSTEM_UI,
) { cl, _ ->
    SystemSplashHook.install(cl)
}

internal val LaunchSplashFeature = FeatureDefinition.single(
    id = "LaunchSplashHook",
    phase = FeaturePhase.STATIC,
    process = FeatureProcess.MAIN,
) { cl, _ ->
    SystemSplashHook.installLaunchWindow(cl)
}

internal object SystemSplashPolicy {
    const val DARK_BACKGROUND: Int = -0xededee // #FF121212

    // Inline the supplier so unrelated apps skip system configuration reads without a lambda allocation.
    inline fun background(packageName: String, systemUiMode: () -> Int): Int? =
        if (packageName == Constants.TARGET_PACKAGE &&
            systemUiMode() and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        ) DARK_BACKGROUND else null
}

internal object SystemSplashHook {
    fun install(classLoader: ClassLoader): InstallOutcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return InstallOutcome(InstallState.SKIPPED, reason = "System SplashScreen requires Android 12")
        }
        val module = XposedCompat.module
            ?: return InstallOutcome(InstallState.FAILED, reason = "Module unavailable")
        val targets = SystemSplashContract.resolve(classLoader)
        val branding = SystemSplashArtwork.load(module.moduleApplicationInfo.sourceDir)
        val failureLogged = AtomicBoolean(false)
        RuntimeHooks.builder(module, targets.build, "SystemSplashHook", "build").intercept { chain ->
            try {
                val builder = chain.thisObject
                if (builder != null) {
                    val context = targets.context(builder)
                    // Read the system configuration, not a host-specific night-mode override.
                    val color = SystemSplashPolicy.background(
                        context.applicationInfo.packageName,
                    ) { Resources.getSystem().configuration.uiMode }
                    if (color != null) {
                        val original = targets.overlay(builder)
                        val replacement = original?.let {
                            checkNotNull(SplashBackground.recolor(it, color, context.resources, branding)) {
                                "Unsupported legacy splash background"
                            }
                        }
                        targets.setBackground(builder, color, replacement)
                    }
                }
            } catch (failure: Throwable) {
                if (failureLogged.compareAndSet(false, true)) {
                    XposedCompat.logW("[SystemSplashHook] instance skipped: ${failure.javaClass.name}: ${failure.message}")
                }
            }
            chain.proceed()
        }
        XposedCompat.log("[SystemSplashHook] system splash hook INSTALLED")
        return try {
            val colorOsBackground = SystemSplashContract.resolveColorOsBackground(classLoader)
                ?: return InstallOutcome(InstallState.INSTALLED, reason = "ColorOS background: SKIPPED (OEM class absent)")
            val colorOsFailureLogged = AtomicBoolean(false)
            RuntimeHooks.builder(module, colorOsBackground, "SystemSplashHook", "colorOsBackground").intercept { chain ->
                val replacementArgs = try {
                    val view = chain.args[0] as? View
                    val original = chain.args[1] as? Drawable
                    if (view != null && original != null) {
                        val context = view.context
                        val color = SystemSplashPolicy.background(
                            context.applicationInfo.packageName,
                        ) { Resources.getSystem().configuration.uiMode }
                        if (color != null) {
                            val replacement = checkNotNull(SplashBackground.recolor(original, color, context.resources, branding)) {
                                "Unsupported ColorOS splash background: ${original.javaClass.name}"
                            }
                            // Replace the argument before ColorOS captures it in the queued setBackground task.
                            arrayOf<Any?>(view, replacement)
                        } else null
                    } else null
                } catch (failure: Throwable) {
                    if (colorOsFailureLogged.compareAndSet(false, true)) {
                        XposedCompat.logW("[SystemSplashHook] ColorOS instance skipped: ${failure.javaClass.name}: ${failure.message}")
                    }
                    null
                }
                // Keep downstream failures outside the preparation catch; proceed exactly once.
                if (replacementArgs == null) chain.proceed() else chain.proceed(replacementArgs)
            }
            XposedCompat.log("[SystemSplashHook] ColorOS background hook INSTALLED")
            InstallOutcome(InstallState.INSTALLED, reason = "ColorOS background: INSTALLED")
        } catch (failure: Throwable) {
            // The framework hook above remains installed if the optional OEM contract changes.
            InstallOutcome(InstallState.PARTIAL, reason = "ColorOS background: ${failure.javaClass.name}: ${failure.message}")
        } // RuntimeHooks measures actual handles, including duplicate installation.
    }

    fun installLaunchWindow(classLoader: ClassLoader): InstallOutcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return InstallOutcome(InstallState.SKIPPED, reason = "System SplashScreen requires Android 12")
        }
        val module = XposedCompat.module
            ?: return InstallOutcome(InstallState.FAILED, reason = "Module unavailable")
        val onCreate = LaunchSplashContract.resolve(classLoader)
        val branding = SystemSplashArtwork.load(module.moduleApplicationInfo.sourceDir)
        val failureLogged = AtomicBoolean(false)
        RuntimeHooks.builder(module, onCreate, "LaunchSplashHook", "onCreate").intercept { chain ->
            val result = chain.proceed()
            try {
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    val color = SystemSplashPolicy.background(
                        activity.applicationInfo.packageName,
                    ) { Resources.getSystem().configuration.uiMode }
                    if (color != null) {
                        val window = activity.window
                        val original = window.decorView.background
                        val content = activity.findViewById<ViewGroup>(android.R.id.content)
                        val cover = if (original != null && content != null) {
                            LaunchSplashContract.placeholder(content, original)
                        } else null
                        if (cover != null) {
                            val coverBackground = checkNotNull(SplashBackground.recolor(cover.background, color, activity.resources, branding))
                            val windowBackground = checkNotNull(SplashBackground.recolor(original, color, activity.resources, branding))
                            cover.background = coverBackground
                            window.setBackgroundDrawable(windowBackground)
                        } else if (failureLogged.compareAndSet(false, true)) {
                            XposedCompat.logW("[LaunchSplashHook] launch cover missing or ambiguous; instance unchanged")
                        }
                    }
                }
            } catch (failure: Throwable) {
                if (failureLogged.compareAndSet(false, true)) {
                    XposedCompat.logW("[LaunchSplashHook] instance skipped: ${failure.javaClass.name}: ${failure.message}")
                }
            }
            result
        }
        XposedCompat.log("[LaunchSplashHook] launch window hook INSTALLED")
        return null
    }
}
