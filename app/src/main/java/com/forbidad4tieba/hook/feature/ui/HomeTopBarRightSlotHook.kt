package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.symbol.model.HomeTopBarRightSlotSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.NavBarSearchButton
import java.lang.reflect.Method

object HomeTopBarRightSlotHook {
    @Volatile
    private var hooked = false

    @Volatile
    private var hooking = false

    internal fun hook(targets: HomeTopBarRightSlotSymbols) {
        if (!tryBeginHook()) return

        var success = false
        try {
            val mod = XposedCompat.module ?: return
            val methods = targets.stateMethods
            if (methods.isEmpty()) return

            for (method in methods.distinctBy { it.name }) {
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (ConfigManager.shouldStabilizeHomeChrome()) {
                        applySlotUiState(chain.thisObject, targets)
                    }
                    result
                }
            }
            success = true
            XposedCompat.log(
                "[HomeTopBarRightSlotHook] hook INSTALLED: " +
                    "${targets.slotClass.name} methods=${methods.joinToString(",") { it.name }}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTopBarRightSlotHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun tryBeginHook(): Boolean {
        synchronized(this) {
            if (hooked || hooking) return false
            hooking = true
            return true
        }
    }

    private fun finishHook(success: Boolean) {
        synchronized(this) {
            hooking = false
            if (success) hooked = true
        }
    }

    private fun applySlotUiState(slotObj: Any?, targets: HomeTopBarRightSlotSymbols) {
        val slot = slotObj ?: return
        try {
            val searchIcon = safeGetView(slot, targets.searchIconViewMethod)
            val gameIcon = safeGetView(slot, targets.gameIconViewMethod)
            val topBarTip = safeGetView(slot, targets.topBarTipMethod)
            val redDotView = safeGetView(slot, targets.redDotViewMethod)

            searchIcon?.visibility = View.VISIBLE
            searchIcon?.alpha = 1.0f
            searchIcon?.isEnabled = true
            searchIcon?.isClickable = true
            // Cache the search icon drawable for history and collection hooks.
            (searchIcon as? android.widget.ImageView)?.drawable?.let {
                NavBarSearchButton.cacheSearchIconDrawable(it)
            }

            gameIcon?.visibility = View.GONE
            gameIcon?.alpha = 0.0f

            topBarTip?.visibility = View.GONE
            topBarTip?.alpha = 0.0f

            redDotView?.visibility = View.GONE
            redDotView?.alpha = 0.0f
        } catch (t: Throwable) {
            XposedCompat.logD("[HomeTopBarRightSlotHook] apply state ignored: ${t.message}")
        }
    }

    private fun safeGetView(owner: Any, method: Method): View? {
        return try {
            method.invoke(owner) as? View
        } catch (_: Throwable) {
            null
        }
    }
}
