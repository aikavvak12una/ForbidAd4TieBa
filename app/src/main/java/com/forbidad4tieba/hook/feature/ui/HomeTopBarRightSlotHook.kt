package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.NavBarSearchButton

object HomeTopBarRightSlotHook {
    private data class RuntimeTargets(
        val slotClass: String,
        val stateMethods: Set<String>,
    )

    @Volatile
    private var hooked = false

    @Volatile
    private var hooking = false

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        if (!tryBeginHook()) return

        var success = false
        try {
            val mod = XposedCompat.module ?: return
            val targets = resolveTargets(symbols)
            if (targets == null) {
                XposedCompat.log(
                    "[HomeTopBarRightSlotHook] skipped: scan symbols missing " +
                        "(homeRightSlotClass/homeRightSlotStateMethods)",
                )
                return
            }

            val rightSlotClass = XposedCompat.findClassOrNull(targets.slotClass, cl)
            if (rightSlotClass == null) {
                XposedCompat.log("[HomeTopBarRightSlotHook] class NOT FOUND: ${targets.slotClass}")
                return
            }

            val methods = targets.stateMethods.map { methodName ->
                XposedCompat.findMethodOrNull(rightSlotClass, methodName)
                    ?: run {
                        XposedCompat.log(
                            "[HomeTopBarRightSlotHook] method NOT FOUND: " +
                                "${targets.slotClass}.$methodName()",
                        )
                        return
                    }
            }
            if (methods.isEmpty()) return

            for (method in methods.distinctBy { it.name }) {
                if (method.returnType != Void.TYPE || method.parameterTypes.isNotEmpty()) {
                    XposedCompat.log(
                        "[HomeTopBarRightSlotHook] rejected unexpected method: " +
                            "${method.declaringClass.name}.${method.name}",
                    )
                    return
                }
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (ConfigManager.isAdBlockEnabled) {
                        applySlotUiState(chain.thisObject)
                    }
                    result
                }
            }
            success = true
            XposedCompat.log(
                "[HomeTopBarRightSlotHook] hook INSTALLED: " +
                    "${targets.slotClass} methods=${targets.stateMethods.joinToString(",")}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTopBarRightSlotHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val slotClass = scanSymbols.homeRightSlotClass?.takeIf { it.isNotBlank() } ?: return null
        val stateMethods = scanSymbols.homeRightSlotStateMethods.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (stateMethods.isEmpty()) return null

        return RuntimeTargets(
            slotClass = slotClass,
            stateMethods = stateMethods,
        )
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

    private fun applySlotUiState(slotObj: Any?) {
        val slot = slotObj ?: return
        try {
            val searchIcon = safeGetView(slot, "getSearchIconView")
            val gameIcon = safeGetView(slot, "getGameIconView")
            val topBarTip = safeGetView(slot, "getTopBarTip")
            val redDotView = safeGetView(slot, "getRedDotView")

            searchIcon?.visibility = View.VISIBLE
            searchIcon?.alpha = 1.0f
            searchIcon?.isEnabled = true
            searchIcon?.isClickable = true

            // 缓存搜索图标 drawable，给 HistorySearchHook 和 CollectionSearchHook 复用。
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

    private fun safeGetView(owner: Any, methodName: String): View? {
        return try {
            XposedCompat.callMethod(owner, methodName) as? View
        } catch (_: Throwable) {
            null
        }
    }
}
