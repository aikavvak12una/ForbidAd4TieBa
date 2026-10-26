package com.forbidad4tieba.hook.feature.ui.nativeglass

import android.os.Bundle
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.NativeGlassContract
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import java.util.concurrent.atomic.AtomicBoolean

/** Optional settings integration owned by the retired background feature. */
internal object HomeNativeGlassSettingsBridge {
    private val homeNativeGlassHostDarkModeBridgeHookInstalled = AtomicBoolean(false)

    fun install(
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

}
