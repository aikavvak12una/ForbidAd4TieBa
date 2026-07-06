package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks the home feed browse-mode prompt bar.
 *
 * FeedPromptBarController is a readable host class confirmed in inspected Tieba versions. The hook
 * avoids obfuscated methods and fields: it validates the stable constructor and collapses the
 * ViewGroup container passed by PersonalizePageView.
 */
object HomeFeedPromptBarBlockHook {
    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[HomeFeedPromptBarBlockHook] already installed, skip")
            return
        }
        val mod = XposedCompat.module
        if (mod == null) {
            installed.set(false)
            return
        }

        try {
            val controllerClass = cl.loadClass(StableTiebaHookPoints.HOME_FEED_PROMPT_BAR_CONTROLLER_CLASS)
            val constructor = controllerClass.getDeclaredConstructor(ViewGroup::class.java)
            constructor.isAccessible = true

            mod.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val container = chain.args.firstOrNull() as? ViewGroup
                if (container != null) {
                    collapseContainer(container)
                }
                result
            }

            XposedCompat.log(
                "[HomeFeedPromptBarBlockHook] hook INSTALLED: " +
                    "${controllerClass.name}.<init>(ViewGroup) -> collapse container",
            )
        } catch (e: ClassNotFoundException) {
            installed.set(false)
            XposedCompat.log(
                "[HomeFeedPromptBarBlockHook] class NOT FOUND: " +
                    StableTiebaHookPoints.HOME_FEED_PROMPT_BAR_CONTROLLER_CLASS,
            )
        } catch (e: NoSuchMethodException) {
            installed.set(false)
            XposedCompat.log("[HomeFeedPromptBarBlockHook] constructor NOT FOUND: <init>(ViewGroup)")
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[HomeFeedPromptBarBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun collapseContainer(container: ViewGroup) {
        if (container.visibility != View.GONE) {
            container.visibility = View.GONE
        }
        val params = container.layoutParams
        if (params != null && params.height != 0) {
            params.height = 0
            container.layoutParams = params
        }
        if (container.minimumHeight != 0) {
            container.minimumHeight = 0
        }
        if (container.isClickable) {
            container.isClickable = false
        }
        if (container.isFocusable) {
            container.isFocusable = false
        }
    }
}
