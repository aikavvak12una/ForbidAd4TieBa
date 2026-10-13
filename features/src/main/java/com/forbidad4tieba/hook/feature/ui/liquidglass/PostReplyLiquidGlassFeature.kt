package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ui.SystemBarCompatHook
import com.forbidad4tieba.hook.symbol.contract.PostReplyBarContract

internal val PostReplyLiquidGlassFeature = FeatureDefinition.single(
    id = "PostReplyLiquidGlassHook",
    phase = FeaturePhase.POST_ATTACH,
    process = FeatureProcess.MAIN,
    enabled = { it.isFloatingBottomBarEnabled },
) { cl, _ ->
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        InstallOutcome(InstallState.SKIPPED, reason = "Post reply glass requires Android 13")
    } else {
        val module = checkNotNull(XposedCompat.module)
        val targets = PostReplyBarContract.resolve(cl)
        val app = checkNotNull(ConfigManager.getAppContext() as? Application)
        RuntimeHooks.builder(module, targets.onCreate, "PostReplyLiquidGlassHook", "onCreate").intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.snapshot().isFloatingBottomBarEnabled) {
                try {
                    val activity = chain.thisObject as? Activity
                    SystemBarCompatHook.applyIfNeeded(activity)
                    activity?.findViewById<ViewGroup>(android.R.id.content)?.let {
                        PostReplyGlassAttachment(it, targets,
                            SystemBarCompatHook.gestureNavigationInsetBottom(activity)).start()
                    }
                } catch (failure: Throwable) {
                    XposedCompat.logW("[PostReplyLiquidGlassHook] attach failed: ${failure.message}")
                }
            }
            result
        }
        SystemBarCompatHook.registerPostReply(app, targets.onCreate.declaringClass)
        XposedCompat.log("[PostReplyLiquidGlassHook] public APIs FOUND; lifecycle hook installed")
        null // The installation ledger counts the actual RuntimeHooks handle.
    }
}

/** A page owns its listeners; no process-wide map retains an Activity or its Views. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
private class PostReplyGlassAttachment(
    private val content: ViewGroup,
    private val targets: PostReplyBarContract.Targets,
    private val navigationInsetBottom: Int,
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
    private var session: PostReplyGlassSession? = null
    private var attempts = 0
    private var closed = false

    fun start() {
        content.addOnAttachStateChangeListener(this)
        content.viewTreeObserver.addOnGlobalLayoutListener(this)
        attachIfReady() // onCreate already inflated the bar; prepare it before the first layout.
    }

    override fun onGlobalLayout() {
        if (closed || !content.isAttachedToWindow) return
        attachIfReady()
    }

    private fun attachIfReady() {
        try {
            val settings = ConfigManager.snapshot()
            if (!settings.isFloatingBottomBarEnabled) { close(); return }
            expandContentThroughNavigationBar()
            if (session != null) return
            val page = targets.findPage(content)
            if (page != null) {
                val current = PostReplyGlassSession(page, targets, settings.bottomTabLiquidGlass,
                    navigationInsetBottom, ::reattach, ::close)
                session = current
                current.start()
                if (closed || session !== current) return
                XposedCompat.logD("[PostReplyLiquidGlassHook] unique reply bar attached")
            } else {
                if (++attempts >= 12) {
                    XposedCompat.logW("[PostReplyLiquidGlassHook] page skipped: missing or ambiguous reply layout")
                    close()
                }
            }
        } catch (failure: Throwable) {
            XposedCompat.logW("[PostReplyLiquidGlassHook] page skipped: ${failure.message}")
            close()
        }
    }

    /** The native keyboard helper can retain a fixed height measured before edge-to-edge. */
    @Suppress("DEPRECATION")
    private fun expandContentThroughNavigationBar() {
        if (content.rootView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION == 0) return
        val insets = content.rootWindowInsets ?: return
        // The host can consume navigation Insets after caching the pre-edge-to-edge height.
        val navigationBottom = maxOf(navigationInsetBottom,
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom)
        if (navigationBottom <= 0 || insets.isVisible(WindowInsets.Type.ime())) return
        val child = content.getChildAt(0) ?: return
        val layout = child.layoutParams as? FrameLayout.LayoutParams ?: return
        val available = content.height - content.paddingTop - content.paddingBottom -
            layout.topMargin - layout.bottomMargin
        // Repair only the navigation-sized reservation; preserve keyboard and other host sizing.
        if (layout.height > 0 && layout.height == available - navigationBottom) {
            layout.height = ViewGroup.LayoutParams.MATCH_PARENT
            child.layoutParams = layout
        }
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = close()

    /** The host replaces the loading input after post data arrives, before its next draw. */
    private fun reattach() {
        if (closed) return
        session = null // The old session has already restored and removed its panel/listeners.
        attachIfReady() // Revalidate the complete reply shape in the same traversal.
    }

    private fun close() {
        if (closed) return
        closed = true
        content.removeOnAttachStateChangeListener(this)
        if (content.viewTreeObserver.isAlive) content.viewTreeObserver.removeOnGlobalLayoutListener(this)
        session?.dispose()
        session = null
    }
}
