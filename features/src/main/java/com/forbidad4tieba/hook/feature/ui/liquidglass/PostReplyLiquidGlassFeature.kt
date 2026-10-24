package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
        val autoHideRequested = ConfigManager.snapshot().isHomeTabAutoHideEnabled
        val targets = PostReplyBarContract.resolve(cl, resolveScroll = autoHideRequested)
        var autoHideAvailable = false
        val app = checkNotNull(ConfigManager.getAppContext() as? Application)
        RuntimeHooks.builder(module, targets.onCreate, "PostReplyLiquidGlassHook", "onCreate").intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.snapshot().isFloatingBottomBarEnabled) {
                try {
                    val activity = chain.thisObject as? Activity
                    SystemBarCompatHook.applyIfNeeded(activity)
                    activity?.findViewById<ViewGroup>(android.R.id.content)?.let {
                        PostReplyGlassAttachment(it, targets, autoHideAvailable) {
                            SystemBarCompatHook.gestureNavigationInsetBottom(activity)
                        }.start()
                    }
                } catch (failure: Throwable) {
                    XposedCompat.logW("[PostReplyLiquidGlassHook] attach failed: ${failure.message}")
                }
            }
            result
        }
        targets.scrollDispatch?.let { scroll ->
            try {
                RuntimeHooks.builder(module, scroll, "PostReplyLiquidGlassHook", "scroll-collapse").intercept { chain ->
                    val result = chain.proceed()
                    val settings = ConfigManager.snapshot()
                    if (settings.isHomeTabAutoHideEnabled && settings.isFloatingBottomBarEnabled) {
                        val view = chain.thisObject as? View
                        val dy = (chain.args.getOrNull(1) as? Int) ?: 0
                        if (view != null && dy != 0) PostReplyScrollDispatcher.onScrolled(view, dy)
                    }
                    result
                }
                autoHideAvailable = true
                XposedCompat.log("[PostReplyLiquidGlassHook] scroll collapse hook INSTALLED")
            } catch (failure: Exception) {
                XposedCompat.logW("[PostReplyLiquidGlassHook] scroll collapse install failed: ${failure.message}")
            }
        }
        SystemBarCompatHook.registerPostReply(app, targets.onCreate.declaringClass)
        XposedCompat.log("[PostReplyLiquidGlassHook] public APIs FOUND; lifecycle hook installed")
        if (autoHideRequested && !autoHideAvailable) InstallOutcome(InstallState.PARTIAL,
            reason = "Scroll collapse unavailable; floating reply bar remains active")
        else null // The installation ledger counts the actual RuntimeHooks handles.
    }
}

/** A page owns its listeners; no process-wide map retains an Activity or its Views. */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
private class PostReplyGlassAttachment(
    private val content: ViewGroup,
    private val targets: PostReplyBarContract.Targets,
    private val autoHideAvailable: Boolean,
    private val navigationInsetBottom: () -> Int,
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
    private var session: PostReplyGlassSession? = null
    private var attempts = 0
    private var closed = false
    private var pendingHidden = false

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
                    navigationInsetBottom, ::reattach, ::close, autoHideAvailable, pendingHidden)
                session = current
                current.start()
                if (closed || session !== current) return
                pendingHidden = false
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
    private fun expandContentThroughNavigationBar() {
        LiquidGlassGeom.expandContentThroughNavigationBar(content, navigationInsetBottom())
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = close()

    /** The host replaces the loading input after post data arrives, before its next draw. */
    private fun reattach(hidden: Boolean) {
        if (closed) return
        pendingHidden = hidden
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
