package com.forbidad4tieba.hook.feature.ui

import android.graphics.Color
import android.widget.TextView
import com.forbidad4tieba.hook.symbol.model.FreeCopyPopupSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enables text selection only inside the native comment long-press popup.
 */
object FreeCopyHook {
    private const val MAX_PATCH_TRACE_LOG = 2
    private const val FORCED_HIGHLIGHT_COLOR = 0x6633B5E5

    private val entryInstalled = AtomicBoolean(false)
    private val popupPatchCount = AtomicInteger(0)

    internal fun hook(popupSymbols: FreeCopyPopupSymbols) {
        if (!entryInstalled.compareAndSet(false, true)) return
        if (!hookPopupMenuText(popupSymbols)) {
            entryInstalled.set(false)
        }
    }

    private fun hookPopupMenuText(popupSymbols: FreeCopyPopupSymbols): Boolean {
        val mod = XposedCompat.module ?: return false
        try {
            mod.hook(popupSymbols.contentViewMethod).intercept { chain ->
                val result = chain.proceed()
                val menu = chain.thisObject ?: return@intercept result
                val textView = runCatching { popupSymbols.textField.get(menu) as? TextView }.getOrNull()
                    ?: return@intercept result
                applyCommentPopupCopy(textView, "popup-menu-text")
                result
            }
            XposedCompat.log(
                "[FreeCopyHook] hook INSTALLED: popup menu text ${popupSymbols.menuClass.name}.${popupSymbols.contentViewMethod.name}",
            )
            return true
        } catch (t: Throwable) {
            XposedCompat.log("[FreeCopyHook] hook popup menu text FAILED: ${t.message}")
            XposedCompat.log(t)
            return false
        }
    }

    private fun applyCommentPopupCopy(textView: TextView, source: String) {
        applySelectableCommon(
            textView = textView,
            source = source,
            expandLineLimit = true,
        )
    }

    private fun applySelectableCommon(
        textView: TextView,
        source: String,
        expandLineLimit: Boolean,
    ) {
        try {
            val needsSelectablePatch = needsSelectablePatch(textView)
            val needsMultilinePatch = needsMultilinePatch(textView, expandLineLimit)

            if (!needsSelectablePatch && !needsMultilinePatch) return

            if (needsSelectablePatch) {
                textView.setTextIsSelectable(true)
                textView.setLongClickable(true)
                textView.setFocusable(true)
                textView.setFocusableInTouchMode(true)
                textView.setClickable(true)
                if (Color.alpha(textView.highlightColor) == 0) {
                    textView.highlightColor = FORCED_HIGHLIGHT_COLOR
                }
            }

            if (needsMultilinePatch) {
                textView.setSingleLine(false)
                textView.maxLines = Int.MAX_VALUE
                textView.ellipsize = null
                textView.setHorizontallyScrolling(false)
            }

            if (!textView.isTextSelectable) {
                XposedCompat.logW(
                    "[FreeCopyHook] patch verify failed: source=$source, selectable=false, view=${textView.javaClass.name}",
                )
                return
            }

            val count = popupPatchCount.incrementAndGet()
            if (count <= MAX_PATCH_TRACE_LOG) {
                XposedCompat.logD(
                    "[FreeCopyHook] patched by $source (count=$count, view=${textView.javaClass.name}, multiline=$expandLineLimit)",
                )
            }
        } catch (t: Throwable) {
            XposedCompat.logW("[FreeCopyHook] apply failed: source=$source, err=${t.message}")
        }
    }

    private fun needsSelectablePatch(textView: TextView): Boolean {
        return !textView.isTextSelectable ||
            !textView.isLongClickable ||
            !textView.isFocusable ||
            !textView.isFocusableInTouchMode ||
            !textView.isClickable ||
            Color.alpha(textView.highlightColor) == 0
    }

    private fun needsMultilinePatch(textView: TextView, expandLineLimit: Boolean): Boolean {
        return expandLineLimit && (textView.maxLines != Int.MAX_VALUE || textView.ellipsize != null)
    }
}
