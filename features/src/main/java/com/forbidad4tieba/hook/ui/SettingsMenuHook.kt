package com.forbidad4tieba.hook.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHostDarkModeBridge
import com.forbidad4tieba.hook.symbol.contract.NativeGlassContract
import com.forbidad4tieba.hook.symbol.contract.SettingsContract
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.ui.settings.SettingsMenuController.showModuleSettingsDialog
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

internal object SettingsMenuHook {
    private val sSettingsFieldCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Class<*>, Field>())

    private val homeNativeGlassHostDarkModeBridgeHookInstalled = AtomicBoolean(false)

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        installHomeNativeGlassHostDarkModeBridgeHook(cl, symbols)
        val className = symbols[SettingsContract.settingsClass]
        val methodName = symbols[SettingsContract.settingsInitMethod]
        val containerField = symbols[SettingsContract.settingsContainerField]
        if (className == null || methodName == null || containerField == null) {
            XposedCompat.log("[SettingsMenuHook] SKIP - missing symbols: class=$className, method=$methodName, field=$containerField")
            return
        }
        try {
            val navClass = MemberAccess.findClassOrNull(StableTiebaHookPoints.NAVIGATION_BAR_CLASS, cl)
            if (navClass == null) {
                XposedCompat.log("[SettingsMenuHook] NavigationBar class NOT FOUND")
                return
            }
            val settingsClass = MemberAccess.findClassOrNull(className, cl)
            if (settingsClass == null) {
                XposedCompat.log("[SettingsMenuHook] class NOT FOUND: $className")
                return
            }
            precacheSettingsContainerField(settingsClass, containerField)
            // Try the older Context/NavigationBar signature before the View signature.
            val method = MemberAccess.findMethodOrNull(settingsClass, methodName, Context::class.java, navClass)
                ?: MemberAccess.findMethodOrNull(settingsClass, methodName, View::class.java)
            if (method == null) {
                XposedCompat.log("[SettingsMenuHook] method NOT FOUND: $className.$methodName")
                return
            }
            RuntimeHooks.builder(mod, method, "SettingsMenuHook", "hook:method").intercept { chain ->
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
        val targets = NativeGlassContract.resolveHomeNativeGlassHostDarkModeSwitchSymbols(
            cl,
            symbols,
        ) ?: return
        HomeNativeGlassHostDarkModeBridge.configure(targets)
        if (!homeNativeGlassHostDarkModeBridgeHookInstalled.compareAndSet(false, true)) return
        try {
            val onCreateMethod = MemberAccess.findMethodOrNull(
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
            RuntimeHooks.builder(mod, onCreateMethod, "SettingsMenuHook", "installHomeNativeGlassHostDarkModeBridgeHook:onCreateMethod").intercept { chain ->
                val result = chain.proceed()
                HomeNativeGlassHostDarkModeBridge.cacheFromActivity(chain.thisObject)
                result
            }
            RuntimeHooks.builder(mod, targets.switchCallbackMethod, "SettingsMenuHook", "installHomeNativeGlassHostDarkModeBridgeHook:targets.switchCallbackMethod").intercept { chain ->
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
}
