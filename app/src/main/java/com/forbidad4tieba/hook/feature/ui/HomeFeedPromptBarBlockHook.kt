package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks the home feed browse-mode prompt and personalize bars.
 *
 * `PersonalizePageView` builds both the same way - a bottom-gravity FrameLayout whose margin is
 * anchored to the tab bar height, handed to a controller - and differs only in which controller it
 * constructs. Both controller classes have readable package names confirmed in inspected Tieba
 * versions. The hook avoids obfuscated methods and fields: it validates the stable constructor and
 * collapses the ViewGroup container passed by PersonalizePageView.
 *
 * Collapsing the container's height (rather than only hiding it) is what makes the block stick: the
 * controllers re-show their container later, and their own layout pass rewrites the bottom margin
 * but never the height.
 */
object HomeFeedPromptBarBlockHook {
    private val CONTROLLER_CLASSES = arrayOf(
        StableTiebaHookPoints.HOME_FEED_PROMPT_BAR_CONTROLLER_CLASS,
        StableTiebaHookPoints.HOME_FEED_PERSONALIZE_BAR_CONTROLLER_CLASS,
    )

    private val installed = AtomicBoolean(false)
    private val guardedContainers =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())

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

        var hooked = 0
        for (className in CONTROLLER_CLASSES) {
            hooked += hookController(mod, cl, className)
        }
        if (hooked == 0) {
            installed.set(false)
            XposedCompat.log("[HomeFeedPromptBarBlockHook] no controller hooked")
        }
    }

    private fun hookController(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
        className: String,
    ): Int {
        return try {
            val controllerClass = cl.loadClass(className)
            val constructor = controllerClass.getDeclaredConstructor(ViewGroup::class.java)
            constructor.isAccessible = true

            mod.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                (chain.args.firstOrNull() as? ViewGroup)?.let { container ->
                    installContainerGuard(container)
                    collapseContainer(container)
                }
                result
            }

            XposedCompat.log(
                "[HomeFeedPromptBarBlockHook] hook INSTALLED: " +
                    "${controllerClass.name}.<init>(ViewGroup) -> collapse container",
            )
            1
        } catch (e: ClassNotFoundException) {
            XposedCompat.log("[HomeFeedPromptBarBlockHook] class NOT FOUND: $className")
            0
        } catch (e: NoSuchMethodException) {
            XposedCompat.log(
                "[HomeFeedPromptBarBlockHook] constructor NOT FOUND: $className.<init>(ViewGroup)",
            )
            0
        } catch (t: Throwable) {
            XposedCompat.log("[HomeFeedPromptBarBlockHook] FAILED on $className: ${t.message}")
            XposedCompat.log(t)
            0
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

    /**
     * The controllers intentionally toggle their container after construction (for example when a
     * feed gesture settles). A one-shot constructor collapse therefore leaks the bar for a frame.
     * Keep the guard attached to this specific container and repair it before every tree draw; no
     * app-wide visibility hook is needed.
     */
    private fun installContainerGuard(container: ViewGroup) {
        synchronized(guardedContainers) {
            if (guardedContainers.put(container, true) != null) return
        }

        val targetRef = WeakReference(container)
        lateinit var preDraw: ViewTreeObserver.OnPreDrawListener
        preDraw = ViewTreeObserver.OnPreDrawListener {
            targetRef.get()?.let(::collapseContainer)
            true
        }
        val layout = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            (view as? ViewGroup)?.let(::collapseContainer)
        }
        var observedTree: ViewTreeObserver? = null

        fun attachPreDraw(view: View) {
            val observer = view.viewTreeObserver
            if (!observer.isAlive || observer === observedTree) return
            observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
            observer.addOnPreDrawListener(preDraw)
            observedTree = observer
        }

        fun detachPreDraw() {
            observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
            observedTree = null
        }

        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                attachPreDraw(view)
                (view as? ViewGroup)?.let(::collapseContainer)
            }

            override fun onViewDetachedFromWindow(view: View) {
                detachPreDraw()
            }
        }

        try {
            container.addOnLayoutChangeListener(layout)
            container.addOnAttachStateChangeListener(attach)
            // A detached view owns a floating observer which Android merges into the root observer
            // during attach. Registering on both sides of that merge duplicates the callback.
            if (container.isAttachedToWindow) {
                attachPreDraw(container)
            }
        } catch (t: Throwable) {
            detachPreDraw()
            guardedContainers.remove(container)
            runCatching { container.removeOnLayoutChangeListener(layout) }
            runCatching { container.removeOnAttachStateChangeListener(attach) }
            XposedCompat.log("[HomeFeedPromptBarBlockHook] guard install FAILED: ${t.message}")
        }
    }
}
