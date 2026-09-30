package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.os.Build
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

/** Keeps all AGSL objects and installation state behind the Android 13 feature boundary. */
object BottomTabLiquidGlassHook {
    fun hook(cl: ClassLoader) {
        if (XposedCompat.module == null) return
        if (!ConfigManager.snapshot().isBottomTabLiquidGlassEnabled) {
            XposedCompat.logD("[BottomTabLiquidGlassHook] disabled by config")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            XposedCompat.log(
                "[BottomTabLiquidGlassHook] unavailable: AGSL lens requires Android 13 (API 33), " +
                    "device is API ${Build.VERSION.SDK_INT}",
            )
            return
        }
        BottomTabLiquidGlassRuntime.hook(cl)
    }

    fun isRuntimeActive(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        BottomTabLiquidGlassRuntime.isRuntimeActive()

    fun ownsBottomBar(view: View): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        BottomTabLiquidGlassRuntime.ownsBottomBar(view)
}
