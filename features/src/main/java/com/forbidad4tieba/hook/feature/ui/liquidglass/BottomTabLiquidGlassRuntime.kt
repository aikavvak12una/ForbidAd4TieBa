package com.forbidad4tieba.hook.feature.ui.liquidglass

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.contracts.MemberAccess
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.app.Application
import com.forbidad4tieba.hook.config.BottomTabLiquidGlassConfig
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ui.SystemBarCompatHook
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * iOS-style liquid glass surface for the host bottom tab bar.
 *
 * The host's own bar is kept intact - red dots, unread counts, dynamic icons, long-press menus and
 * skinning all keep running on the host's implementation. Only three things change:
 *
 *  1. the bar's own background/divider chrome is cleared through the host's public setters,
 *  2. a [LiquidGlassPanel] is inserted into `FragmentTabHost` directly beneath the bar wrapper, so
 *     it draws behind the tab icons and in front of the content pager,
 *  3. the content pager is made to fill the whole host so there is something real to refract.
 *
 * `FragmentTabHost` is a `RelativeLayout` whose content pager and bar wrapper are siblings, so no
 * reparenting or layout surgery is needed - the panel is just another sibling in draw order.
 *
 * Every anchor used here is a public, unobfuscated host API, so this feature needs no entry in
 * `HookSymbolResolver`. Anything missing fails closed: the panel is never attached and the host bar
 * is left exactly as it was.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
internal object BottomTabLiquidGlassRuntime {
    private const val TAG = "[BottomTabLiquidGlassHook]"

    private const val METHOD_GET_TAB_WRAPPER = StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER
    private const val METHOD_GET_FRAGMENT_TAB_WIDGET = "getFragmentTabWidget"
    private const val METHOD_GET_FRAGMENT_VIEW_PAGER = "getFragmentViewPager"
    private const val METHOD_GET_TAB_CONTAINER_BG_IMG = "getTabcontainerDynamicBgImg"
    private const val METHOD_SET_UI_TYPE = "setUIType"
    private const val METHOD_SET_CURRENT_TAB = "setCurrentTab"
    private const val METHOD_SET_TAB_WIDGET_BACKGROUND_COLOR = "setTabWidgetBackgroundColor"
    private const val METHOD_SET_SHOULD_DRAW_TOP_LINE = "setShouldDrawTopLine"
    private const val METHOD_SET_SHOULD_DRAW_DIVIDER_LINE = "setShouldDrawDividerLine"
    private const val METHOD_SET_SHOULD_DRAW_INDICATOR_LINE = "setShouldDrawIndicatorLine"
    private const val METHOD_SET_TAB_CONTAINER_SHADOW_SHOW = "setTabContainerShadowShow"
    private const val METHOD_GET_INST = "getInst"
    private const val METHOD_GET_SKIN_TYPE = "getSkinType"

    /** Host skin protocol values, mirrored from the bottom tab skin switch. */
    private const val LIGHT_SKIN_TYPE = 0
    private const val LEGACY_DARK_SKIN_TYPE = 1
    private const val DARK_SKIN_TYPE = 4

    /** Minimum content clearance when a larger host font needs more than the standard height. */
    private const val PILL_VERTICAL_PADDING_DP = 7f

    /**
     * How long the panel keeps redrawing after the backdrop last changed.
     *
     * Live refraction needs a redraw per content frame, but an unconditional per-frame invalidate
     * self-sustains a 60fps loop forever even on a completely static screen. Scroll and layout
     * callbacks open this window instead, which also covers the async work (image loads, settle
     * animations) that follows them without leaving the loop running while nothing moves.
     */
    private const val KEEP_ALIVE_MS = 600L

    /** Depth budget when looking for the scrolling container to un-clip inside a page. */
    private const val SCROLL_CONTAINER_SEARCH_DEPTH = 6

    private val installed = AtomicBoolean(false)
    private val states = Collections.synchronizedMap(WeakHashMap<View, GlassState>())
    private val registeredHosts = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val unclippedContainers = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val applyErrorLogged = AtomicBoolean(false)
    private val skinErrorLogged = AtomicBoolean(false)

    /** Scratch for the row offset lookup; every caller runs on the UI thread. */
    private val rowOffset = IntArray(2)

    /** Scratch for the row content box; every caller runs on the UI thread. */
    private val contentBox = IntArray(4)

    private val hostWindowPosition = IntArray(2)
    private val rootWindowPosition = IntArray(2)

    @Volatile private var hostApi: HostApi? = null

    private class HostApi(
        val getTabWrapper: Method,
        val getFragmentTabWidget: Method,
        val getFragmentViewPager: Method,
        val getTabContainerBgImg: Method?,
        val setShouldDrawTopLine: Method?,
        val setShouldDrawDividerLine: Method?,
        val setShouldDrawIndicatorLine: Method?,
        val setTabContainerShadowShow: Method?,
        val widgetSetShouldDrawTopLine: Method?,
        val widgetSetShouldDrawDividerLine: Method?,
        val widgetSetShouldDrawIndicatorLine: Method?,
        val getSkinType: Method?,
        val getCoreAppInst: Method?,
        val webContainerClass: Class<*>?,
    )

    private class GlassState(
        val host: ViewGroup,
        val wrapper: ViewGroup,
        val widget: ViewGroup,
        val pager: ViewGroup,
        val panel: LiquidGlassPanel,
        val density: Float,
        val config: BottomTabLiquidGlassConfig,
    ) {
        val wrapperOverlappingRendering = wrapper.getHasOverlappingRendering()
        @Volatile var keepAliveUntil = 0L
        /**
         * Cached hug result. A negative width means the attempt completed but the host did not
         * expose a usable fixed width, so the row is left at its own extent for this layout.
         */
        var hugWidth = 0
        var hugChildCount = -1
        var hugRowWidth = -1
        var hugSignature = Long.MIN_VALUE
        /** False while a host relayout is still applying the fixed column widths. */
        var geometryReady = false
        var rowTranslationY = 0f
        var droplet: DropletPanel? = null
        var controller: DropletController? = null
        var touchOverlay: LiquidGlassTouchOverlay? = null
        var dropletIndex = -1
        var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null
        var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
        var lastNight: Boolean? = null
    }

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) return

        try {
            val hostClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS,
                cl,
            )
            if (hostClass == null) {
                installed.set(false)
                XposedCompat.log("$TAG class NOT FOUND: ${StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS}")
                return
            }

            val getTabWrapper = MemberAccess.findMethodOrNull(hostClass, METHOD_GET_TAB_WRAPPER)
            val getWidget = MemberAccess.findMethodOrNull(hostClass, METHOD_GET_FRAGMENT_TAB_WIDGET)
            val getPager = MemberAccess.findMethodOrNull(hostClass, METHOD_GET_FRAGMENT_VIEW_PAGER)
            if (getTabWrapper == null || getWidget == null || getPager == null) {
                installed.set(false)
                XposedCompat.log(
                    "$TAG required host API missing: " +
                        "$METHOD_GET_TAB_WRAPPER=${getTabWrapper != null}, " +
                        "$METHOD_GET_FRAGMENT_TAB_WIDGET=${getWidget != null}, " +
                        "$METHOD_GET_FRAGMENT_VIEW_PAGER=${getPager != null}",
                )
                return
            }
            val app = ConfigManager.getAppContext() as? Application
            if (app == null) {
                installed.set(false)
                XposedCompat.log("$TAG application context unavailable")
                return
            }

            val coreAppClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS,
                cl,
            )
            val widgetClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS,
                cl,
            )
            val webContainerClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.TB_WEB_VIEW_CLASS,
                cl,
            )?.takeIf { ViewGroup::class.java.isAssignableFrom(it) }
            if (webContainerClass == null) {
                XposedCompat.log("$TAG web content inset support unavailable: TbWebView missing or invalid")
            }
            hostApi = HostApi(
                getTabWrapper = getTabWrapper,
                getFragmentTabWidget = getWidget,
                getFragmentViewPager = getPager,
                getTabContainerBgImg = MemberAccess.findMethodOrNull(
                    hostClass,
                    METHOD_GET_TAB_CONTAINER_BG_IMG,
                ),
                setShouldDrawTopLine = booleanSetter(hostClass, METHOD_SET_SHOULD_DRAW_TOP_LINE),
                setShouldDrawDividerLine = booleanSetter(hostClass, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE),
                setShouldDrawIndicatorLine = booleanSetter(hostClass, METHOD_SET_SHOULD_DRAW_INDICATOR_LINE),
                setTabContainerShadowShow = booleanSetter(hostClass, METHOD_SET_TAB_CONTAINER_SHADOW_SHOW),
                widgetSetShouldDrawTopLine = widgetClass?.let {
                    booleanSetter(it, METHOD_SET_SHOULD_DRAW_TOP_LINE)
                },
                widgetSetShouldDrawDividerLine = widgetClass?.let {
                    booleanSetter(it, METHOD_SET_SHOULD_DRAW_DIVIDER_LINE)
                },
                widgetSetShouldDrawIndicatorLine = widgetClass?.let {
                    booleanSetter(it, METHOD_SET_SHOULD_DRAW_INDICATOR_LINE)
                },
                getSkinType = coreAppClass?.let {
                    MemberAccess.findMethodOrNull(it, METHOD_GET_SKIN_TYPE)
                },
                getCoreAppInst = coreAppClass?.let {
                    MemberAccess.findMethodOrNull(it, METHOD_GET_INST)
                },
                webContainerClass = webContainerClass,
            )

            var hookCount = 0
            for (ctor in hostClass.declaredConstructors) {
                ctor.isAccessible = true
                RuntimeHooks.builder(mod, ctor, "BottomTabLiquidGlassRuntime", "hook:ctor").intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? ViewGroup)?.let { registerHost(it) }
                    result
                }
                hookCount++
            }
            hookCount += hookIntMethod(mod, hostClass, METHOD_SET_UI_TYPE)
            hookCount += hookIntMethod(mod, hostClass, METHOD_SET_CURRENT_TAB)
            hookCount += hookIntMethod(mod, hostClass, METHOD_SET_TAB_WIDGET_BACKGROUND_COLOR)

            if (hookCount == 0) {
                installed.set(false)
                hostApi = null
                XposedCompat.log("$TAG hook point missing on ${hostClass.name}")
                return
            }
            // Liquid glass owns a floating bottom bar even when auto-hide is off. Registration is
            // deliberately after host validation so an unavailable glass implementation cannot
            // leave the activity in an unrelated edge-to-edge mode.
            SystemBarCompatHook.register(app)
            XposedCompat.log("$TAG hook INSTALLED: ${hostClass.name} hooks=$hookCount")
        } catch (t: Throwable) {
            installed.set(false)
            hostApi = null
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    /** True while the feature's hooks are installed. */
    fun isRuntimeActive(): Boolean = installed.get() && hostApi != null

    /**
     * Returns true when [view] belongs to a host whose bottom chrome is currently owned by a glass
     * panel.
     *
     * Hook installation happens before the first [FragmentTabHost] instance is attached, so the
     * global runtime flag alone is intentionally not enough to arbitrate background writes. Walking
     * the view's ancestors makes the decision precise during that short bootstrap window and after
     * a host has been detached.
     */
    fun ownsBottomBar(view: View): Boolean {
        if (!isRuntimeActive()) return false
        var current: View? = view
        var depth = 0
        while (current != null && depth < 32) {
            if (states[current]?.panel?.parent != null) return true
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun booleanSetter(clazz: Class<*>, name: String): Method? =
        MemberAccess.findMethodOrNull(clazz, name, java.lang.Boolean.TYPE)

    private fun hookIntMethod(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodName: String,
    ): Int {
        val method = MemberAccess.findMethodOrNull(clazz, methodName, java.lang.Integer.TYPE) ?: run {
            XposedCompat.logD("$TAG method NOT FOUND: ${clazz.name}.$methodName(int)")
            return 0
        }
        RuntimeHooks.builder(mod, method, "BottomTabLiquidGlassRuntime", "hookIntMethod:method").intercept { chain ->
            val result = chain.proceed()
            val host = chain.thisObject as? ViewGroup
            if (host != null) {
                host.post { refresh(host) }
            }
            result
        }
        return 1
    }

    private fun registerHost(host: ViewGroup) {
        if (registeredHosts.put(host, true) != null) return
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post { apply(v as? ViewGroup ?: return@post) }
            }

            override fun onViewDetachedFromWindow(v: View) {
                detach(v)
            }
        })
        if (host.isAttachedToWindow) {
            host.post { apply(host) }
        }
    }

    private fun refresh(host: ViewGroup) {
        val state = states[host]
        if (state == null) {
            apply(host)
            return
        }
        // A tab switch swaps the page under the pill, so the previous page's un-clipping says
        // nothing about the new one.
        state.hugWidth = 0
        state.hugChildCount = -1
        state.hugRowWidth = -1
        state.hugSignature = Long.MIN_VALUE
        state.geometryReady = false
        ensurePanelOrder(state)
        SystemBarCompatHook.applyIfNeeded(findActivity(host.context))
        ensureBackdropFillsHost(state)
        clearHostChrome(state)
        applySkin(state)
        positionPanel(state)
        syncDropletSelection(state)
        markDirty(state)
    }

    /**
     * Keeps the panel between the content pager and the bar wrapper in draw order.
     *
     * The host re-adds its pager on some layout transitions, which appends it after the panel and
     * would paint the page straight over the glass.
     */
    private fun ensurePanelOrder(state: GlassState) {
        val host = state.host
        val panelIndex = host.indexOfChild(state.panel)
        val wrapperIndex = host.indexOfChild(state.wrapper)
        val pagerIndex = host.indexOfChild(state.pager)
        if (panelIndex < 0) return
        val ordered = (wrapperIndex < 0 || panelIndex < wrapperIndex) &&
            (pagerIndex < 0 || panelIndex > pagerIndex)
        // A re-added pager must not cover the selection or the glass touch surface either.
        state.droplet?.bringToFront()
        state.touchOverlay?.bringToFront()
        if (ordered) return
        host.removeView(state.panel)
        val target = when {
            wrapperIndex >= 0 -> host.indexOfChild(state.wrapper)
            else -> host.childCount
        }
        host.addView(state.panel, target.coerceAtLeast(0), state.panel.layoutParams)
        XposedCompat.logD { "$TAG panel re-ordered to index $target" }
    }

    private fun apply(host: ViewGroup) {
        if (!ConfigManager.snapshot().isBottomTabLiquidGlassEnabled) return
        val api = hostApi ?: return
        if (states.containsKey(host)) {
            refresh(host)
            return
        }
        if (!isMainTabActivityContext(host.context)) return

        try {
            val wrapper = api.getTabWrapper.invoke(host) as? ViewGroup
            val widget = api.getFragmentTabWidget.invoke(host) as? ViewGroup
            val pager = api.getFragmentViewPager.invoke(host) as? ViewGroup
            if (wrapper == null || widget == null || pager == null) {
                logApplyError(
                    "host views unavailable: wrapper=${wrapper != null}, " +
                        "widget=${widget != null}, pager=${pager != null}",
                )
                return
            }
            if (host !is RelativeLayout) {
                logApplyError("unexpected host type ${host.javaClass.name}, expected RelativeLayout")
                return
            }
            if (wrapper.parent !== host ||
                !LiquidGlassGeom.layoutOffsetInAncestor(widget, wrapper, rowOffset)
            ) {
                logApplyError("tab row is not inside the host's bottom-bar wrapper")
                return
            }
            if (host.height <= 0 || wrapper.height <= 0 || widget.height <= 0) {
                // Not laid out yet; the attach listener re-posts and the layout listener below
                // takes over once the first pass lands.
                host.post { apply(host) }
                return
            }
            // Only the bottom-bar layout modes are supported. When the host puts the tab row at the
            // top the content pager is anchored below it, and forcing it to fill would slide the
            // page under the bar. The wrapper is a direct child of the host, so its top is already
            // in host coordinates - the widget's is not.
            if (wrapper.top < host.height / 2) {
                XposedCompat.logD {
                    "$TAG skipped: tab row is not at the bottom " +
                        "(wrapperTop=${wrapper.top}, hostHeight=${host.height})"
                }
                return
            }

            val density = host.resources.displayMetrics.density
            val config = ConfigManager.snapshot().bottomTabLiquidGlass
            val panel = LiquidGlassPanel(host.context, pager, density, isNightSkin(), config)
            if (!panel.isSupported) {
                XposedCompat.log("$TAG unavailable: lens pipeline unsupported on this device")
                return
            }

            val state = GlassState(host, wrapper, widget, pager, panel, density, config)
            val index = host.indexOfChild(wrapper).takeIf { it >= 0 } ?: host.childCount
            // The first hug pass changes the host's child LayoutParams. Keep both overlays out of
            // the draw list until the following layout has committed those widths.
            panel.visibility = View.INVISIBLE
            host.addView(panel, index, RelativeLayout.LayoutParams(0, 0))
            states[host] = state
            // Activity lifecycle callbacks run before Tieba finishes rewriting its decor flags.
            // Re-assert edge-to-edge only after this host is really owned by the glass feature, just
            // as the auto-hide path does when it resolves the bottom bar for a scroll target.
            SystemBarCompatHook.applyIfNeeded(findActivity(host.context))

            // The lifted row overflows its inner FrameLayout, not just the outer tab wrapper.
            // Release clipping along the validated path from the row to the glass host.
            var tabParent: ViewGroup? = widget
            while (tabParent != null) {
                tabParent.clipChildren = false
                tabParent.clipToPadding = false
                if (tabParent === host) break
                tabParent = tabParent.parent as? ViewGroup
            }
            // An alpha fade otherwise allocates an offscreen layer bounded by the wrapper's
            // original slot, clipping the lifted icons even with clipChildren disabled. This
            // transparent container can pass alpha through to its tab contents instead.
            wrapper.forceHasOverlappingRendering(false)

            // The droplet goes on top of the tabs, not under them: it refracts a separately drawn,
            // enlarged copy of the tab row, and that copy is what should be visible inside it.
            val droplet = DropletPanel(host.context, panel, widget, density, isNightSkin(), config)
            if (droplet.isSupported) {
                droplet.visibility = View.INVISIBLE
                host.addView(droplet, RelativeLayout.LayoutParams(0, 0))
                droplet.setPill(panel)
                val controller = DropletController(droplet, widget, density, config)
                controller.setPill(panel)
                state.droplet = droplet
                state.controller = controller
                val touchOverlay = LiquidGlassTouchOverlay(panel, droplet, wrapper, widget, controller)
                state.touchOverlay = touchOverlay
                host.addView(touchOverlay, RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                })
            } else {
                XposedCompat.log("$TAG droplet unavailable: lens pipeline unsupported")
            }

            ensureBackdropFillsHost(state)
            clearHostChrome(state)
            applySkin(state)
            positionPanel(state)
            attachObservers(state)
            markDirty(state)
            XposedCompat.log(
                "$TAG applied: host=${host.javaClass.name}, widget=${widget.javaClass.name}, " +
                    "pager=${pager.javaClass.name}",
            )
        } catch (t: Throwable) {
            logApplyError("apply FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun detach(host: View) {
        val state = states.remove(host) ?: return
        state.wrapper.forceHasOverlappingRendering(state.wrapperOverlappingRendering)
        state.touchOverlay?.cancelInteraction()
        state.controller?.cancelInteraction()
        val observer = state.host.viewTreeObserver
        if (observer.isAlive) {
            state.scrollListener?.let { observer.removeOnScrollChangedListener(it) }
            state.layoutListener?.let { observer.removeOnGlobalLayoutListener(it) }
            state.preDrawListener?.let { observer.removeOnPreDrawListener(it) }
        }
        state.scrollListener = null
        state.layoutListener = null
        state.preDrawListener = null
        // Drop the panel too: a re-attach goes back through apply(), and leaving the old one in
        // place would stack a second panel on top of it.
        (state.panel.parent as? ViewGroup)?.removeView(state.panel)
        state.droplet?.let { (it.parent as? ViewGroup)?.removeView(it) }
        state.touchOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        state.touchOverlay = null
        state.droplet = null
        state.controller = null
    }

    private fun attachObservers(state: GlassState) {
        val observer = state.host.viewTreeObserver
        if (!observer.isAlive) return

        val scroll = ViewTreeObserver.OnScrollChangedListener {
            if (!state.panel.isCapturing) markDirty(state)
        }
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            if (state.panel.isCapturing) return@OnGlobalLayoutListener
            relaxScrollContainerClipping(state)
            // Only a real geometry change is a reason to redraw. Re-arming on every layout pass
            // keeps the redraw window permanently open and burns a few percent of a core on a
            // completely static screen.
            if (positionPanel(state)) markDirty(state)
        }
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            val panel = state.panel
            if (panel.isAttachedToWindow) {
                // A hug requests a layout, but a few host transitions can coalesce that pass. The
                // pre-draw retry is cheap (the target width is cached) and prevents one stale
                // full-width frame from leaking through if the global-layout callback was skipped.
                if (!state.geometryReady || hugInputSignature(state.widget) != state.hugSignature) {
                    if (positionPanel(state)) markDirty(state)
                }
                // This must run even while the panel is INVISIBLE: auto-hide sets the wrapper
                // invisible at the end of its animation, and on show the panel has to be made
                // visible again before this callback can observe its alpha/translation.
                followWrapperTransform(state)
                if (panel.visibility == View.VISIBLE) {
                    // Cheap per-frame guard: the host rewrites these on skin changes, which do not
                    // always produce a layout pass.
                    if (state.wrapper.background != null || state.widget.background != null) {
                        clearHostChrome(state)
                        applySkin(state)
                    }
                    if (SystemClock.uptimeMillis() <= state.keepAliveUntil) {
                        panel.refreshBackdrop()
                    }
                }
            }
            true
        }

        observer.addOnScrollChangedListener(scroll)
        observer.addOnGlobalLayoutListener(layout)
        observer.addOnPreDrawListener(preDraw)
        state.scrollListener = scroll
        state.layoutListener = layout
        state.preDrawListener = preDraw
    }

    private fun markDirty(state: GlassState) {
        state.keepAliveUntil = SystemClock.uptimeMillis() + KEEP_ALIVE_MS
        state.panel.refreshBackdrop()
    }

    /**
     * Makes the content pager span the whole host so the panel has real content to refract.
     *
     * In the host's bottom-bar layout modes the pager already carries no anchor tying it to the tab
     * row, so this is usually a no-op; it is kept because a mode that does anchor it would leave the
     * panel refracting the window background instead.
     */
    private fun ensureBackdropFillsHost(state: GlassState) {
        val lp = state.pager.layoutParams as? RelativeLayout.LayoutParams ?: return
        var changed = false
        if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            changed = true
        }
        if (lp.bottomMargin != 0) {
            lp.bottomMargin = 0
            changed = true
        }
        val rules = lp.rules
        if (rules.size > RelativeLayout.ABOVE && rules[RelativeLayout.ABOVE] != 0) {
            lp.removeRule(RelativeLayout.ABOVE)
            changed = true
        }
        if (rules.size > RelativeLayout.ALIGN_PARENT_BOTTOM &&
            rules[RelativeLayout.ALIGN_PARENT_BOTTOM] == 0
        ) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            changed = true
        }
        if (changed) {
            state.pager.layoutParams = lp
            XposedCompat.logD { "$TAG content pager extended behind the tab row" }
        }
        relaxScrollContainerClipping(state)
    }

    /**
     * Lets the current page draw through its own bottom padding.
     *
     * Tieba insets its feed lists by the bar height so the last row can clear the tab bar. With the
     * default `clipToPadding` that inset region paints only the list background, so the panel would
     * refract a flat colour instead of the feed. Turning clipping off keeps the inset - the last row
     * is still reachable - while letting the rows draw behind the pill.
     */
    private fun relaxScrollContainerClipping(state: GlassState) {
        if (!ConfigManager.snapshot().isBottomTabLiquidGlassEnabled || state.panel.parent == null) return
        val pager = state.pager
        for (i in 0 until pager.childCount) {
            val page = pager.getChildAt(i) as? ViewGroup ?: continue
            if (page.visibility != View.VISIBLE) continue
            unclipScrollContainer(state, page, 0)
        }
    }

    private fun unclipScrollContainer(state: GlassState, view: ViewGroup, depth: Int) {
        if (depth > SCROLL_CONTAINER_SEARCH_DEPTH) return
        if (hostApi?.webContainerClass?.isInstance(view) == true) {
            if (LiquidGlassWebContent.expandIntoTabReserve(view, state.pager, state.widget.height)) {
                XposedCompat.logD { "$TAG bottom-tab web content extended behind the tab row" }
                markDirty(state)
            }
            return
        }
        // Scroll capability rather than a class name: this has to match the host's RecyclerView,
        // ListView and NestedScrollView pages without naming any of them.
        if (view.paddingBottom > 0 &&
            (view.canScrollVertically(1) || view.canScrollVertically(-1))
        ) {
            val previouslyKnown = unclippedContainers.put(view, true) != null
            if (view.clipToPadding) {
                view.clipToPadding = false
                if (!previouslyKnown) {
                    XposedCompat.logD { "$TAG un-clipped ${view.javaClass.name}" }
                }
            }
            return
        }
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) as? ViewGroup ?: continue
            if (child.visibility != View.VISIBLE) continue
            // A ViewPager keeps neighbouring pages VISIBLE. Finishing the first native list must
            // not prevent a later web page from releasing its separate bottom-tab reserve.
            unclipScrollContainer(state, child, depth + 1)
        }
    }

    /** Mirrors the host wrapper's slide/fade so the pill and the icons move as one. */
    private fun followWrapperTransform(state: GlassState) {
        val panel = state.panel
        val wrapper = state.wrapper
        // The row carries the float as a translation because its own layout slot is fixed by the
        // host; the panel has the same offset baked into its margin instead, so it only has to
        // mirror the auto-hide slide.
        val lift = state.rowTranslationY
        if (state.widget.translationY != lift) {
            state.widget.translationY = lift
        }
        // Insets can move the native wrapper before the overlays' requested margins are laid out.
        // Bridge that pending layout with a translation so every layer uses this frame's origin.
        val panelTop = (panel.layoutParams as RelativeLayout.LayoutParams).topMargin
        val panelTranslationY = wrapper.translationY + panelTop - panel.top
        var moved = false
        if (panel.translationY != panelTranslationY) {
            panel.translationY = panelTranslationY
            moved = true
        }
        if (panel.setContainerAlpha(wrapper.alpha)) {
            moved = true
        }
        val wrapperVisible = wrapper.visibility == View.VISIBLE
        val visibility = if (wrapperVisible && state.geometryReady && state.dropletIndex >= 0) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        if (panel.visibility != visibility) {
            panel.visibility = visibility
            if (visibility != View.VISIBLE) state.controller?.cancelInteraction()
            moved = true
        }
        state.droplet?.let { droplet ->
            val dropletTop = (droplet.layoutParams as RelativeLayout.LayoutParams).topMargin
            val dropletTranslationY = wrapper.translationY + dropletTop - droplet.top
            if (droplet.translationY != dropletTranslationY) {
                droplet.translationY = dropletTranslationY
            }
            if (droplet.alpha != wrapper.alpha) droplet.alpha = wrapper.alpha
            if (droplet.visibility != visibility) droplet.visibility = visibility
        }
        state.touchOverlay?.setActive(visibility == View.VISIBLE && wrapper.alpha > 0f)
        // The auto-hide slide animates the wrapper directly: no layout pass, no scroll callback, so
        // nothing else re-opens the redraw window. Without this the capsule reappears still showing
        // the backdrop it captured from its hidden position, which reads as the glass dropping out
        // for a frame or two.
        if (moved) markDirty(state)
    }

    /** Clears the host's own bar chrome so only the glass pill shows. */
    private fun clearHostChrome(state: GlassState) {
        val api = hostApi ?: return
        setBoolean(api.setShouldDrawTopLine, state.host, false)
        setBoolean(api.setShouldDrawDividerLine, state.host, false)
        setBoolean(api.setShouldDrawIndicatorLine, state.host, false)
        setBoolean(api.setTabContainerShadowShow, state.host, false)
        // FragmentTabWidget.draw() gates its top line, divider and indicator on three separate
        // booleans, and the host's own top-line setter only forwards behind an A/B flag - so going
        // through the host alone leaves the hairline drawn. Set them on the widget directly.
        setBoolean(api.widgetSetShouldDrawTopLine, state.widget, false)
        setBoolean(api.widgetSetShouldDrawDividerLine, state.widget, false)
        setBoolean(api.widgetSetShouldDrawIndicatorLine, state.widget, false)
        if (state.wrapper.background != null) state.wrapper.background = null
        if (state.widget.background != null) state.widget.background = null
        try {
            (api.getTabContainerBgImg?.invoke(state.host) as? View)?.let { bg ->
                if (bg.visibility != View.GONE) bg.visibility = View.GONE
            }
        } catch (t: Throwable) {
            logApplyError("hide tab container background failed: ${t.message}")
        }
    }

    private fun setBoolean(method: Method?, target: Any, value: Boolean) {
        method ?: return
        try {
            method.invoke(target, value)
        } catch (t: Throwable) {
            logApplyError("${method.name} failed: ${t.message}")
        }
    }

    private fun applySkin(state: GlassState) {
        val night = isNightSkin()
        if (state.lastNight == night) return
        state.lastNight = night
        state.panel.setTheme(night)
        state.droplet?.setTheme(night)
    }

    private fun isNightSkin(): Boolean {
        val api = hostApi ?: return false
        val getInst = api.getCoreAppInst ?: return false
        val getSkinType = api.getSkinType ?: return false
        return try {
            val inst = getInst.invoke(null) ?: return false
            when (getSkinType.invoke(inst) as? Int) {
                LEGACY_DARK_SKIN_TYPE, DARK_SKIN_TYPE -> true
                LIGHT_SKIN_TYPE -> false
                else -> false
            }
        } catch (t: Throwable) {
            if (skinErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD("$TAG skin type unavailable: ${t.message}")
            }
            false
        }
    }

    /**
     * Keeps the pill locked onto the host tab row, sized to its content rather than to the bar.
     *
     * The host bar is full width and includes a navigation reserve. Hug the columns horizontally,
     * keep KernelSU's minimum height, and centre the capsule on the actual icon/label content.
     * Larger host fonts retain their existing content clearance. See [LiquidGlassBarMetrics].
     *
     * Returns true when the panel's geometry actually moved, so callers can tell a real change from
     * one of the many layout passes that leave the bar exactly where it was.
     */
    private fun positionPanel(state: GlassState): Boolean {
        val host = state.host
        val widget = state.widget
        val panel = state.panel
        val previousTabSignature = state.hugSignature
        if (widget.width <= 0 || widget.height <= 0) {
            state.geometryReady = false
            panel.visibility = View.INVISIBLE
            state.droplet?.visibility = View.INVISIBLE
            return false
        }
        if (!LiquidGlassGeom.layoutOffsetInAncestor(widget, host, rowOffset)) {
            logApplyError("tab row is not a descendant of the host")
            return false
        }

        hugWidthFor(state)
        // `applyHugWidth()` writes fixed LayoutParams immediately, while `tab.width` remains the
        // old weighted width until the next traversal. Measuring the row in that interval yields a
        // full-width pill (and parks the droplet under the wrong column). Keep the overlays hidden
        // until the actual child bounds agree with the requested width.
        if (!hugColumnsSettled(state)) {
            state.geometryReady = false
            panel.visibility = View.INVISIBLE
            state.droplet?.visibility = View.INVISIBLE
            return false
        }
        LiquidGlassBarMetrics.hideColumnHairlines(widget, state.density)
        val padX = (LiquidGlassBarMetrics.HORIZONTAL_PADDING_DP * state.density).roundToInt()
        val padY = (PILL_VERTICAL_PADDING_DP * state.density).roundToInt()
        val minHeight = (state.config.heightDp * state.density).roundToInt()

        // Derive the capsule from where the columns actually landed rather than from the row box.
        // The row stays full-screen width, so anchoring on it puts the capsule where the content is
        // not. Fail soft to the row's own extent when nothing measurable was found.
        //
        // The float is baked into the layout position rather than applied as a translation: the row
        // already sits flush with the bottom of the host, so a capsule that is taller than the row
        // gets its measured height clamped at the host's edge and loses its bottom rim - which is
        // what made the icons sit low in it.
        val width: Int
        val height: Int
        val left: Int
        val naturalTop: Int
        if (LiquidGlassBarMetrics.measureContent(widget, contentBox)) {
            width = (contentBox[2] - contentBox[0]) + padX * 2
            val contentHeight = contentBox[3] - contentBox[1]
            height = (contentHeight + padY * 2).coerceAtLeast(minHeight)
            left = rowOffset[0] + contentBox[0] - padX
            naturalTop = rowOffset[1] + contentBox[1] - (height - contentHeight) / 2
        } else {
            width = widget.width
            height = widget.height.coerceAtLeast(minHeight)
            left = rowOffset[0]
            naturalTop = rowOffset[1] - (height - widget.height) / 2
        }
        if (width <= 0 || height <= 0) {
            state.geometryReady = false
            if (panel.visibility != View.GONE) panel.visibility = View.GONE
            state.droplet?.visibility = View.INVISIBLE
            return false
        }

        val root = host.rootView
        host.getLocationInWindow(hostWindowPosition)
        root.getLocationInWindow(rootWindowPosition)
        val navigationBottom = host.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        val top = LiquidGlassBarMetrics.floatingTop(
            host.height, hostWindowPosition[1] + host.height,
            rootWindowPosition[1] + root.height, navigationBottom, height, state.density,
            state.config.bottomGapDp,
        )
        state.rowTranslationY = (top - naturalTop).toFloat()

        val lp = panel.layoutParams as? RelativeLayout.LayoutParams
            ?: RelativeLayout.LayoutParams(width, height)
        var changed = false
        if (lp.width != width) { lp.width = width; changed = true }
        if (lp.height != height) { lp.height = height; changed = true }
        if (lp.leftMargin != left) { lp.leftMargin = left; changed = true }
        if (lp.topMargin != top) { lp.topMargin = top; changed = true }
        val rules = lp.rules
        if (rules.size > RelativeLayout.ALIGN_PARENT_TOP &&
            rules[RelativeLayout.ALIGN_PARENT_TOP] == 0
        ) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            changed = true
        }
        if (changed) panel.layoutParams = lp
        val wasReady = state.geometryReady
        state.geometryReady = true
        positionDroplet(state, height, top)
        followWrapperTransform(state)
        // Hairline suppression and the host's post-layout pass can both mutate the tab tree. Keep
        // the cache anchored to the settled tree so the pre-draw guard does not remeasure forever.
        state.hugSignature = hugInputSignature(widget)
        if (changed || !wasReady || state.hugSignature != previousTabSignature) {
            // The resting droplet caches a drawn copy of the selected tab. Moving a View or
            // changing its source's layout does not necessarily invalidate that display list.
            state.droplet?.refresh()
        }
        if (changed) {
            XposedCompat.logD {
                "$TAG geometry: row=${widget.width}x${widget.height} at " +
                    "(${rowOffset[0]},${rowOffset[1]}) -> pill=${width}x$height at ($left,$top), " +
                    "content=[${contentBox[0]},${contentBox[1]},${contentBox[2]},${contentBox[3]}], " +
                    "tabs=${widget.childCount}"
            }
        }
        return changed || !wasReady
    }

    /**
     * Hug state for the row, applied once per tab set.
     *
     * [LiquidGlassBarMetrics.applyHugWidth] probes the live tab tree and restores its current
     * constraints. Cache that work until the inputs change. The host can mutate a badge, label or
     * icon in place, so a compact content/layout signature supplements child count and row width.
     */
    private fun hugWidthFor(state: GlassState) {
        val widget = state.widget
        val signature = hugInputSignature(widget)
        if (state.hugWidth != 0 &&
            state.hugChildCount == widget.childCount &&
            state.hugRowWidth == widget.width &&
            state.hugSignature == signature
        ) {
            return
        }
        val applied = LiquidGlassBarMetrics.applyHugWidth(widget, state.density, state.config.widthPercent)
        state.hugChildCount = widget.childCount
        state.hugRowWidth = widget.width
        state.hugSignature = hugInputSignature(widget)
        state.hugWidth = if (applied) {
            (0 until widget.childCount)
                .asSequence()
                .mapNotNull { widget.getChildAt(it)?.layoutParams?.width }
                .firstOrNull { it > 0 }
                ?: -1
        } else {
            -1
        }
    }

    /**
     * Returns false only during the one traversal where the host still reports its old weighted
     * child widths. A failed/unsupported hug has a negative cache value and deliberately falls back
     * to the host's own geometry instead of hiding the bar forever.
     */
    private fun hugColumnsSettled(state: GlassState): Boolean {
        val expected = state.hugWidth
        if (expected <= 0) return true
        var visibleColumns = 0
        val row = state.widget
        for (i in 0 until row.childCount) {
            val tab = row.getChildAt(i) ?: continue
            if (tab.visibility == View.GONE) continue
            visibleColumns++
            if (tab.layoutParams?.width != expected || tab.width != expected) return false
        }
        return visibleColumns > 0
    }

    /**
     * Captures the parts of the tab tree that affect intrinsic width or the content bounds used by
     * the pill. The host mutates badges and labels in place, so child count/row width alone cannot
     * tell an async rebuild from a stable layout.
     */
    private fun hugInputSignature(row: ViewGroup): Long {
        var hash = 17L

        fun add(value: Long) {
            hash = hash * 31L + value
        }

        fun visit(view: View, depth: Int) {
            if (depth > 12) return
            add(System.identityHashCode(view).toLong())
            add(view.visibility.toLong())
            add(view.width.toLong())
            add(view.height.toLong())
            add(view.measuredWidth.toLong())
            add(view.measuredHeight.toLong())
            add(view.left.toLong())
            add(view.top.toLong())
            add(view.right.toLong())
            add(view.bottom.toLong())
            add(view.paddingLeft.toLong())
            add(view.paddingTop.toLong())
            add(view.paddingRight.toLong())
            add(view.paddingBottom.toLong())
            add(if (view.isSelected) 1L else 0L)
            val lp = view.layoutParams
            if (lp == null) {
                add(0L)
            } else {
                add(lp.width.toLong())
                add(lp.height.toLong())
                add(lp.javaClass.name.hashCode().toLong())
                if (lp is ViewGroup.MarginLayoutParams) {
                    add(lp.leftMargin.toLong())
                    add(lp.topMargin.toLong())
                    add(lp.rightMargin.toLong())
                    add(lp.bottomMargin.toLong())
                }
                if (lp is LinearLayout.LayoutParams) {
                    add(java.lang.Float.floatToIntBits(lp.weight).toLong())
                }
            }
            when (view) {
                is TextView -> add(view.text?.hashCode()?.toLong() ?: 0L)
                is ImageView -> {
                    val drawable = view.drawable
                    add(drawable?.intrinsicWidth?.toLong() ?: 0L)
                    add(drawable?.intrinsicHeight?.toLong() ?: 0L)
                }
            }
            if (view is ViewGroup) {
                add(view.childCount.toLong())
                for (i in 0 until view.childCount) {
                    view.getChildAt(i)?.let { visit(it, depth + 1) }
                }
            }
        }

        visit(row, 0)
        return hash
    }

    /**
     * Sizes the droplet to one tab column and parks it on the selected one.
     *
     * It is laid out at the row's origin and moved by `translationX` from there, which is what lets
     * the controller slide it on the render thread without a layout pass per frame.
     */
    private fun positionDroplet(state: GlassState, pillHeight: Int, pillTop: Int) {
        val droplet = state.droplet ?: return
        val controller = state.controller ?: return
        val row = state.widget
        val first = row.getChildAt(0) ?: return
        if (first.width <= 0) return

        val width = first.width
        val inset = (LiquidGlassBarMetrics.DROPLET_INSET_DP * state.density).roundToInt()
        val height = (pillHeight - inset * 2).coerceAtLeast(1)
        val top = pillTop + inset
        val lp = droplet.layoutParams as? RelativeLayout.LayoutParams
            ?: RelativeLayout.LayoutParams(width, height)
        var changed = false
        if (lp.width != width) { lp.width = width; changed = true }
        if (lp.height != height) { lp.height = height; changed = true }
        if (lp.topMargin != top) { lp.topMargin = top; changed = true }
        if (lp.leftMargin != 0) { lp.leftMargin = 0; changed = true }
        val rules = lp.rules
        if (rules.size > RelativeLayout.ALIGN_PARENT_TOP &&
            rules[RelativeLayout.ALIGN_PARENT_TOP] == 0
        ) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            changed = true
        }
        if (changed) droplet.layoutParams = lp
        controller.setGeometry(
            rowOffset[0].toFloat(),
            first.width.toFloat(),
            width.toFloat(),
        )
        val selected = selectedIndex(row)
        if (selected < 0) {
            state.dropletIndex = -1
            droplet.setSelectedTabIndex(-1)
            droplet.visibility = View.INVISIBLE
            return
        }
        droplet.setSelectedTabIndex(selected)
        if (state.dropletIndex != selected) {
            val immediate = state.dropletIndex < 0
            state.dropletIndex = selected
            controller.animateToIndex(selected, immediate)
        }
        if (droplet.visibility != View.VISIBLE) droplet.visibility = View.VISIBLE
    }

    private fun selectedIndex(row: ViewGroup): Int {
        for (i in 0 until row.childCount) {
            if (row.getChildAt(i)?.isSelected == true) return i
        }
        return -1
    }

    /** Nudges the droplet after the host changed the selection itself. */
    private fun syncDropletSelection(state: GlassState) {
        val controller = state.controller ?: return
        val selected = selectedIndex(state.widget)
        if (selected < 0) {
            state.dropletIndex = -1
            state.droplet?.setSelectedTabIndex(-1)
            state.droplet?.visibility = View.INVISIBLE
            return
        }
        state.droplet?.setSelectedTabIndex(selected)
        if (selected == state.dropletIndex) return
        // The first selection to arrive is the one the app cold-started on; springing to it from
        // index 0 would show a slide the user never asked for.
        val immediate = state.dropletIndex < 0
        state.dropletIndex = selected
        controller.animateToIndex(selected, immediate)
    }

    private fun isMainTabActivityContext(context: Context?): Boolean =
        findActivity(context)?.javaClass?.name == StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS

    private fun findActivity(context: Context?): Activity? {
        var current = context
        var depth = 0
        while (current != null && depth < 8) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private fun logApplyError(message: String) {
        if (applyErrorLogged.compareAndSet(false, true)) {
            XposedCompat.log("$TAG $message")
        } else {
            XposedCompat.logD { "$TAG $message" }
        }
    }
}
