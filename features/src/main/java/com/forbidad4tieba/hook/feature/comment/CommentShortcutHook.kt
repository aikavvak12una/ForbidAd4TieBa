package com.forbidad4tieba.hook.feature.comment

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.CommentShortcutTargets
import com.forbidad4tieba.hook.ui.UiText
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Executable
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object CommentShortcutHook {
    private val hooks = OwnedHookSet<Executable, XposedInterface.HookHandle> { it.unhook() }
    private var active: Session? = null

    @Synchronized fun hook(targets: CommentShortcutTargets, icons: List<Drawable.ConstantState>): InstallOutcome {
        if (active != null) return InstallOutcome(InstallState.ALREADY_INSTALLED, hooks.size())
        if (hooks.size() > 0 && hooks.rollback().isNotEmpty()) return InstallOutcome(InstallState.ROLLBACK_FAILED, hooks.size())
        val module = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val session = Session(targets, icons)
        try {
            listOf(targets.constructor, targets.bind, targets.destroy).forEach { member ->
                hooks.install(member) {
                    RuntimeHooks.builder(module, member, "CommentShortcut", member.toString()).intercept { chain ->
                        if (member == targets.destroy) CommentFilterOverrides.visits.remove(chain.thisObject)
                        val result = chain.proceed()
                        if (active === session && member != targets.destroy) session.bind(chain.thisObject as View)
                        result
                    }
                }
            }
            active = session
            CommentFilterOverrides.responseThreadId = targets::responseThreadId
            return InstallOutcome(InstallState.INSTALLED, hooks.size())
        } catch (failure: Throwable) {
            active = null
            val failures = hooks.rollback()
            return InstallOutcome(if (failures.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED,
                hooks.size(), failure.message)
        }
    }

    private class Session(val targets: CommentShortcutTargets, val icons: List<Drawable.ConstantState>) {
        private val buttons = WeakHashMap<View, WeakReference<ImageView>>()
        private var failed = false

        fun bind(bar: View) = guarded {
            if (!ConfigManager.snapshot().isCommentShortcutEnabled) {
                buttons[bar]?.get()?.visibility = View.GONE
                CommentFilterOverrides.visits.clear()
                return@guarded
            }
            val host = targets.hostTitle(bar)
            val parent = host.parent as? RelativeLayout ?: return@guarded
            if (host.id == View.NO_ID || host.id == 0) return@guarded
            var button = buttons[bar]?.get()
            if (button == null) {
                fun dp(value: Int) = (value * bar.resources.displayMetrics.density).roundToInt()
                button = ImageView(bar.context).apply {
                    id = View.generateViewId()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                    isFocusable = true
                    setOnClickListener {
                        if (active !== this@Session || failed) return@setOnClickListener
                        if (!CommentLevelFilterHook.isReady()) {
                            Toast.makeText(context, UiText.Settings.COMMENT_SHORTCUT_UNAVAILABLE, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        guarded {
                            val page = targets.page(context)
                            if (page == null || !targets.canRefresh(page)) {
                                Toast.makeText(context, UiText.Settings.COMMENT_SHORTCUT_BUSY, Toast.LENGTH_SHORT).show()
                                return@guarded
                            }
                            if (!ConfigManager.snapshot().isCommentShortcutEnabled) return@guarded
                            CommentFilterOverrides.visits.toggle(page.owner, page.tid, ConfigManager.snapshot().commentLevelFilter.enabled)
                            buttons.values.mapNotNull { it.get() }.forEach(::render)
                            targets.refreshComments(checkNotNull(page.comments))
                        }
                    }
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) { guarded { render(v as ImageView) } }
                        override fun onViewDetachedFromWindow(v: View) = Unit
                    })
                }
                parent.addView(button, RelativeLayout.LayoutParams(dp(44), dp(40)).apply {
                    addRule(RelativeLayout.RIGHT_OF, host.id)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                })
                buttons[bar] = WeakReference(button)
            }
            button.visibility = if (host.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            render(button)
        }

        private fun render(button: ImageView) {
            if (!ConfigManager.snapshot().isCommentShortcutEnabled) {
                button.visibility = View.GONE
                CommentFilterOverrides.visits.clear()
                return
            }
            val settings = ConfigManager.snapshot().commentLevelFilter
            val page = targets.page(button.context)
            val enabled = if (page == null) settings.enabled else
                CommentFilterOverrides.visits.enabled(page.owner, page.tid, settings.enabled)
            val level = settings.minimumLevel.coerceIn(1, icons.size)
            if (button.tag != level) {
                button.setImageDrawable(icons[level - 1].newDrawable(button.resources).mutate())
                button.tag = level
            }
            button.alpha = if (enabled) 1f else .5f
            button.isSelected = enabled
            button.contentDescription = UiText.Settings.commentShortcutDescription(level, enabled)
        }

        private inline fun guarded(action: () -> Unit) {
            if (failed || active !== this) return
            try { action() } catch (failure: Throwable) {
                failed = true
                CommentFilterOverrides.visits.clear()
                CommentFilterOverrides.responseThreadId = null
                buttons.values.mapNotNull { it.get() }.forEach { (it.parent as? ViewGroup)?.removeView(it) }
                buttons.clear()
                XposedCompat.logW("[CommentShortcut] disabled: ${failure.message}")
            }
        }
    }
}
