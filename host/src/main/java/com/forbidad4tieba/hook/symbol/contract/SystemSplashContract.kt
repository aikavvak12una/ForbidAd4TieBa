package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.graphics.drawable.Drawable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Framework and optional OEM targets, resolved once in SystemUI without host DexKit or persisted caches. */
object SystemSplashContract {
    fun resolve(classLoader: ClassLoader): SystemSplashTargets = validate(
        Class.forName("android.window.SplashScreenView\$Builder", false, classLoader),
        Class.forName("android.window.SplashScreenView", false, classLoader),
    )

    /** ColorOS queues a second background assignment after the framework Builder has finished. */
    fun resolveColorOsBackground(classLoader: ClassLoader): Method? {
        val manager = try {
            Class.forName("com.android.wm.shell.startingsurface.OplusShellStartingWindowManager", false, classLoader)
        } catch (_: ClassNotFoundException) {
            return null
        }
        val view = Class.forName("android.window.SplashScreenView", false, classLoader)
        return manager.getDeclaredMethod("setContentViewBackground", view, Drawable::class.java).apply {
            check(!Modifier.isStatic(modifiers) && returnType == Void.TYPE) {
                "Invalid ColorOS splash background method"
            }
            isAccessible = true
        }
    }

    internal fun validate(builder: Class<*>, view: Class<*>): SystemSplashTargets {
        fun field(name: String, type: Class<*>): Field = builder.getDeclaredField(name).apply {
            check(!Modifier.isStatic(modifiers) && this.type == type) { "Invalid splash field: $name" }
            isAccessible = true
        }
        fun method(name: String, result: Class<*>, vararg parameters: Class<*>): Method =
            builder.getDeclaredMethod(name, *parameters).apply {
                check(!Modifier.isStatic(modifiers) && returnType == result) { "Invalid splash method: $name" }
                isAccessible = true
            }
        return SystemSplashTargets(
            build = method("build", view),
            context = field("mContext", Context::class.java),
            overlay = field("mOverlayDrawable", Drawable::class.java).apply {
                check(!Modifier.isFinal(modifiers)) { "Splash overlay is not writable" }
            },
            background = method("setBackgroundColor", builder, Int::class.javaPrimitiveType!!),
        )
    }
}

class SystemSplashTargets internal constructor(
    val build: Method,
    private val context: Field,
    private val overlay: Field,
    private val background: Method,
) {
    fun context(builder: Any): Context = context.get(builder) as Context
    fun overlay(builder: Any): Drawable? = overlay.get(builder) as? Drawable
    fun hasOverlay(builder: Any): Boolean = overlay.get(builder) != null
    fun setBackground(builder: Any, color: Int, replacement: Drawable?) {
        // Builder.build gives the legacy overlay precedence over mBackgroundColor.
        // Keep modern/legacy semantics: never add an overlay or remove an existing one.
        check(hasOverlay(builder) == (replacement != null)) { "Splash overlay presence changed" }
        background.invoke(builder, color)
        if (replacement != null) overlay.set(builder, replacement)
    }
}
