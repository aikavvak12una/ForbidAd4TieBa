package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

object HomeNativeGlassHook {
    private const val TAG = "[HomeNativeGlassHook]"
    private val installed = AtomicBoolean(false)
    private val firstPageErrorLogged = AtomicBoolean(false)
    private val firstCardErrorLogged = AtomicBoolean(false)
    private val firstChromeErrorLogged = AtomicBoolean(false)
    private val firstPbCommentErrorLogged = AtomicBoolean(false)
    private val firstPbDynamicBackgroundColorErrorLogged = AtomicBoolean(false)
    private val firstPbSortSwitchDynamicTintErrorLogged = AtomicBoolean(false)
    private val firstPbEnterForumCapsuleDynamicTintErrorLogged = AtomicBoolean(false)
    private val firstSubPbInputBarDynamicTintErrorLogged = AtomicBoolean(false)
    private val firstSubPbNavigationBarTintErrorLogged = AtomicBoolean(false)
    private val firstBackgroundImageErrorLogged = AtomicBoolean(false)
    private val homeRecyclerViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val glassBackgroundViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val scrollInvalidationInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val frameInvalidationInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeRecyclerChildAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pageStyleReapplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbCommentActivityApplyScheduled = Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())
    private val pbCommentSurfaceAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbCommentSurfaceApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbCommentItemFrameAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbCommentItemFrameApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbActivityByContext = Collections.synchronizedMap(WeakHashMap<Context, WeakReference<Activity>>())
    private val pbActivityContentHosts = Collections.synchronizedMap(WeakHashMap<Activity, WeakReference<View>>())
    private val pbActivityTypes = Collections.synchronizedMap(WeakHashMap<Activity, PbActivityType>())
    private val pbCommentListItemApplyScheduled =
        Collections.synchronizedMap(WeakHashMap<View, PendingViewGroupApply>())
    private val pbCommentBackgroundHostStates = Collections.synchronizedMap(WeakHashMap<View, PbCommentBackgroundState>())
    private val pbCommentBackgroundWriteRoles =
        Collections.synchronizedMap(WeakHashMap<View, PbCommentBackgroundWriteRole>())
    private val pbSubPbLayoutAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbSubPbLayoutApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbSubPbLayoutCardStates = Collections.synchronizedMap(WeakHashMap<View, PbSubPbLayoutCardState>())
    private val pbSubPbLayoutPaddingStates = Collections.synchronizedMap(WeakHashMap<View, PbSubPbLayoutPaddingState>())
    private val subPbReplyItemApplyScheduled =
        Collections.synchronizedMap(WeakHashMap<View, PendingViewGroupApply>())
    private val subPbInputBarApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val subPbNavigationBarApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbReplyBarInputApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbReplyBarGestureBridgeViews = Collections.synchronizedMap(WeakHashMap<Activity, WeakReference<View>>())
    private val pbReplyTitleDynamicTintScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbDialogRoundLayoutDynamicTintScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val shareDialogDynamicTintTargets = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pbSortSwitchOriginalBackgroundColors = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val pbSortSwitchTintStates = Collections.synchronizedMap(WeakHashMap<View, PbSortSwitchTintState>())
    private val pbSortSwitchSelectedSlidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pbSortSwitchNativeSelectedSlideLayerPaint = Paint().apply { alpha = 0 }
    private val pbEnterForumCapsuleOriginalStates =
        Collections.synchronizedMap(WeakHashMap<View, EnterForumCapsuleOriginalState>())
    private val homeTopChromeAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeTopChromeRefreshScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeBottomTabAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeBottomTabRefreshScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val topChromeTabTypes = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val homeSearchBoxes = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeSearchBoxAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeSearchBoxBootstrapScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeCardComponentViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val cardComponentAttachRefreshInstalled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val cardComponentBootstrapScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeFeedCardGlassTargets = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeFeedCardStyleApplyScheduled = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val homeFeedCardStyleStates = Collections.synchronizedMap(WeakHashMap<View, HomeFeedCardStyleState>())
    private val chromeGlassOriginalStates = Collections.synchronizedMap(WeakHashMap<View, ChromeGlassOriginalState>())
    private val imageContainerRadiusStates = Collections.synchronizedMap(WeakHashMap<View, Float>())
    private val scrollInvalidateScheduled = AtomicBoolean(false)
    private val homeNativeGlassModeReapplyScheduled = AtomicBoolean(false)
    private val runtimeStyleStore = HomeNativeGlassRuntimeStyleStore(::scheduleHomeNativeGlassModeReapply)
    private val chromeDynamicTintBackgroundWriteDepth = object : ThreadLocal<Int>() {
        override fun initialValue(): Int = 0
    }
    private val emManagerFromViewFields = ConcurrentHashMap<Class<*>, Field>()
    private val emManagerFromViewMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val emManagerRealBackgroundColorMethods = ConcurrentHashMap<Class<*>, Method>()
    private val emManagerRealBackgroundColorMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val emManagerFromMethods = ConcurrentHashMap<Class<*>, Method>()
    private val emManagerFromMethodMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val chromeNoArgViewMethods = ConcurrentHashMap<RuntimeMethodKey, Method>()
    private val chromeNoArgViewMissingMethods =
        Collections.newSetFromMap(ConcurrentHashMap<RuntimeMethodKey, Boolean>())
    private val chromeBooleanMethods = ConcurrentHashMap<RuntimeMethodKey, Method>()
    private val chromeBooleanMissingMethods =
        Collections.newSetFromMap(ConcurrentHashMap<RuntimeMethodKey, Boolean>())

    @Volatile private var recyclerViewClass: Class<*>? = null
    private val backgroundStore = HomeNativeGlassBackgroundStore(
        maxCachedBytes = MAX_CACHED_BACKGROUND_BITMAP_BYTES,
        metadataCheckIntervalMs = BACKGROUND_CACHE_METADATA_CHECK_INTERVAL_MS,
    )
    private val backgroundDecodeKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val backgroundDecodeExecutorLock = Any()
    private val activeBackgroundDecodeTasks = AtomicInteger(0)
    private var backgroundDecodeExecutor: ExecutorService? = null
    @Volatile private var runtimeTargets: RuntimeTargets? = null
    @Volatile private var pbCommentDynamicTintState: PbCommentDynamicTintState? = null
    @Volatile private var pbSortSwitchBackgroundPaintField: Field? = null
    @Volatile private var pbSortSwitchSlidePathField: Field? = null
    @Volatile private var pbEnterForumCapsuleViewField: Field? = null
    @Volatile private var pbEnterForumCapsuleTitleField: Field? = null

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !ConfigManager.hasAnyHomeNativeGlassBackgroundImage) return
        val mod = XposedCompat.module ?: return
        val feedPlan = resolveHomeNativeGlassFeedInstallPlan(symbols)
        if (!installed.compareAndSet(false, true)) return
        HomeNativeGlassHostDarkModeBridge.addDarkModeChangeListener(::onHostDarkModeChanged)
        refreshHomeNativeGlassRuntimeStyle(forceHostRead = true, scheduleReapply = false)

        try {
            recyclerViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
            runtimeTargets = resolveHomeNativeGlassRuntimeTargets(symbols)
            prewarmBackgroundCacheIfNeeded()
            if (!feedPlan.hasHomeFeedTargets) {
                XposedCompat.logD { feedPlan.formatSkipLog(TAG) }
            }
            val pageConstructors = if (feedPlan.hasHomeFeedTargets) installPageConstructors(cl) else 0
            val hostSkinTypeHooks = installHostSkinTypeChangeHooks(cl)
            val bindHooks = if (
                feedPlan.hasHomeFeedTargets &&
                feedPlan.bindMethodName != null &&
                feedPlan.bindMethodSpec != null
            ) {
                installFeedCardBind(cl, feedPlan.bindMethodName, feedPlan.bindMethodSpec)
            } else {
                if (feedPlan.shouldLogMissingBindMethod) {
                    XposedCompat.log("$TAG recommend card SKIP: feedCardBindMethodSpec not resolved")
                }
                0
            }
            val touchHooks = if (bindHooks > 0) installFeedCardTouchHooks(cl) else 0
            val componentHooks = if (bindHooks > 0) installCardComponentHooks(cl) else 0
            val pbCommentHooks = installPbCommentActivityHooks(cl) +
                installPbCommentSurfaceHooks(cl) +
                installPbCommentListItemGetViewHook(cl) +
                installSubPbReplyAdapterHooks(cl) +
                installSubPbInputBarHooks(cl) +
                installSubPbNextPageGlassHook(cl) +
                installSubPbNavigationBarHooks(cl) +
                installPbCommentItemFrameHooks(cl) +
                installPbSubPbLayoutHooks(cl) +
                installPbReplyTitleViewHolderHooks(cl) +
                installPbCommonLayoutPreloaderHook(cl)
            val dynamicColorHooks = installPbDynamicBackgroundColorHooks(cl)
            val sortSwitchHooks = installPbSortSwitchButtonDynamicTintHooks(cl)
            val enterForumCapsuleHooks = installPbEnterForumCapsuleDynamicTintHooks(cl)
            val tabDynamicTintEnabled = feedPlan.hasHomeFeedTargets && ConfigManager.isHomeTabDynamicTintEnabled
            val topTabObservers = if (tabDynamicTintEnabled) installHomeTopTabObservers(cl) else 0
            val bottomTabDynamicTintHooks = if (tabDynamicTintEnabled) {
                installHomeBottomTabDynamicTintHooks(cl)
            } else {
                0
            }
            val chromeDirectBackgroundBlock = if (tabDynamicTintEnabled) {
                installHomeChromeDirectBackgroundBlock()
            } else {
                0
            }
            val searchBoxHooks = if (tabDynamicTintEnabled) installHomeSearchBoxHooks(cl) else 0
            val installReport = HomeNativeGlassInstallReport(
                pageConstructors = pageConstructors,
                bindHooks = bindHooks,
                touchHooks = touchHooks,
                componentHooks = componentHooks,
                pbCommentHooks = pbCommentHooks,
                dynamicColorHooks = dynamicColorHooks,
                sortSwitchHooks = sortSwitchHooks,
                enterForumCapsuleHooks = enterForumCapsuleHooks,
                hostSkinTypeHooks = hostSkinTypeHooks,
                topTabObservers = topTabObservers,
                bottomTabDynamicTintHooks = bottomTabDynamicTintHooks,
                chromeDirectBackgroundBlock = chromeDirectBackgroundBlock,
                searchBoxHooks = searchBoxHooks,
                bindMethodName = feedPlan.bindMethodName,
            )
            if (!installReport.hasRequiredHooks) {
                installed.set(false)
                XposedCompat.log(installReport.formatNoHooksLog(TAG))
                return
            }
            (ConfigManager.getAppContext() as? android.app.Application)?.let { app ->
                SystemBarCompatHook.register(app)
            }
            XposedCompat.log(installReport.formatInstalledLog(TAG))
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    fun hotReloadBusyReason(): String? {
        if (activeBackgroundDecodeTasks.get() > 0) return "home native glass background decode active"
        return null
    }

    fun prepareForHotReload(): Boolean {
        HomeNativeGlassHostDarkModeBridge.removeDarkModeChangeListener(::onHostDarkModeChanged)
        clearRuntimeViewStateForHotReload()
        val executor = synchronized(backgroundDecodeExecutorLock) {
            backgroundDecodeExecutor.also { backgroundDecodeExecutor = null }
        } ?: return true
        executor.shutdownNow()
        return try {
            executor.awaitTermination(300L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun clearRuntimeViewStateForHotReload() {
        homeRecyclerViews.clear()
        glassBackgroundViews.clear()
        scrollInvalidationInstalled.clear()
        frameInvalidationInstalled.clear()
        homeRecyclerChildAttachRefreshInstalled.clear()
        pageStyleReapplyScheduled.clear()
        pbCommentActivityApplyScheduled.clear()
        pbCommentSurfaceAttachRefreshInstalled.clear()
        pbCommentSurfaceApplyScheduled.clear()
        pbCommentItemFrameAttachRefreshInstalled.clear()
        pbCommentItemFrameApplyScheduled.clear()
        pbActivityByContext.clear()
        pbActivityContentHosts.clear()
        pbActivityTypes.clear()
        pbCommentListItemApplyScheduled.clear()
        pbCommentBackgroundHostStates.clear()
        pbCommentBackgroundWriteRoles.clear()
        pbSubPbLayoutAttachRefreshInstalled.clear()
        pbSubPbLayoutApplyScheduled.clear()
        pbSubPbLayoutCardStates.clear()
        pbSubPbLayoutPaddingStates.clear()
        subPbReplyItemApplyScheduled.clear()
        subPbInputBarApplyScheduled.clear()
        subPbNavigationBarApplyScheduled.clear()
        pbReplyBarInputApplyScheduled.clear()
        pbReplyBarGestureBridgeViews.clear()
        pbReplyTitleDynamicTintScheduled.clear()
        pbDialogRoundLayoutDynamicTintScheduled.clear()
        shareDialogDynamicTintTargets.clear()
        pbSortSwitchOriginalBackgroundColors.clear()
        pbSortSwitchTintStates.clear()
        pbEnterForumCapsuleOriginalStates.clear()
        homeTopChromeAttachRefreshInstalled.clear()
        homeTopChromeRefreshScheduled.clear()
        homeBottomTabAttachRefreshInstalled.clear()
        homeBottomTabRefreshScheduled.clear()
        topChromeTabTypes.clear()
        homeSearchBoxes.clear()
        homeSearchBoxAttachRefreshInstalled.clear()
        homeSearchBoxBootstrapScheduled.clear()
        homeCardComponentViews.clear()
        cardComponentAttachRefreshInstalled.clear()
        cardComponentBootstrapScheduled.clear()
        homeFeedCardGlassTargets.clear()
        homeFeedCardStyleApplyScheduled.clear()
        homeFeedCardStyleStates.clear()
        chromeGlassOriginalStates.clear()
        imageContainerRadiusStates.clear()
        chromeNoArgViewMethods.clear()
        chromeNoArgViewMissingMethods.clear()
        chromeBooleanMethods.clear()
        chromeBooleanMissingMethods.clear()
        backgroundDecodeKeys.clear()
        recyclerViewClass = null
        runtimeTargets = null
        pbCommentDynamicTintState = null
        pbSortSwitchBackgroundPaintField = null
        pbSortSwitchSlidePathField = null
        pbEnterForumCapsuleViewField = null
        pbEnterForumCapsuleTitleField = null
    }

    private fun installHostSkinTypeChangeHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val appClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, cl)
            ?: return 0
        var installedCount = 0
        for (methodName in HOST_SKIN_TYPE_CHANGE_METHODS) {
            val method = XposedCompat.findMethodOrNull(appClass, methodName, java.lang.Integer.TYPE)
                ?: continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                HomeNativeGlassHostDarkModeBridge.onHostSkinTypeChanged(
                    (chain.args.getOrNull(0) as? Number)?.toInt()
                )
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPageConstructors(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val pageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.log("$TAG class NOT FOUND: ${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}")
                return 0
            }
        var installedCount = 0
        for (ctor in pageClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                val page = chain.thisObject as? View
                if (page != null) {
                    applyPageStyleSafely(page)
                    page.post { applyPageStyleSafely(page) }
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installFeedCardBind(cl: ClassLoader, bindMethodName: String, bindMethodSpec: String): Int {
        val mod = XposedCompat.module ?: return 0
        val feedCardViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.log("$TAG class NOT FOUND: ${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}")
                return 0
            }
        val bindMethod = HookSymbolResolver.resolveMethodByCachedSpec(
            feedCardViewClass,
            bindMethodSpec,
            bindMethodName,
        ) ?: run {
            XposedCompat.log("$TAG method NOT FOUND: ${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodSpec")
            return 0
        }
        bindMethod.isAccessible = true
        mod.hook(bindMethod).intercept { chain ->
            val card = chain.thisObject as? View
            if (
                card != null &&
                ConfigManager.isHomeNativeGlassEnabled &&
                hasPageBackgroundOverride() &&
                isInsideHomeNativePage(card)
            ) {
                rememberHomeFeedCardGlassTargets(card)
            }
            val result = chain.proceed()
            if (card != null) {
                applyCardStyleSafely(card, force = true)
                scheduleHomeFeedCardStyleReapply(card)
            }
            result
        }
        return 1
    }

    private fun installFeedCardTouchHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val feedCardViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl)
            ?: return 0
        var installedCount = 0
        for (methodName in FEED_CARD_TOUCH_METHODS) {
            val method = XposedCompat.findMethodOrNull(feedCardViewClass, methodName, MotionEvent::class.java)
                ?: continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val event = chain.args.getOrNull(0) as? MotionEvent
                val result = chain.proceed()
                val card = chain.thisObject as? View
                if (card != null) {
                    handleCardTouchVisualSafely(card, event)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installCardComponentHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        for (className in CARD_COMPONENT_GLASS_CLASSES) {
            val componentClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            for (ctor in componentClass.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { componentView ->
                        rememberCardComponentView(componentView)
                    }
                    result
                }
                installedCount++
            }
        }
        return installedCount
    }

    private fun installPbCommentActivityHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val activityClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS}" }
                return 0
            }
        val methods = listOfNotNull(
            ReflectionUtils.findMethodInHierarchy(activityClass, "onCreate", Bundle::class.java)
                ?.takeIf { it.returnType == Void.TYPE },
            ReflectionUtils.findMethodInHierarchy(activityClass, "onChangeSkinType", java.lang.Integer.TYPE)
                ?.takeIf { it.returnType == Void.TYPE },
        )
        var installedCount = 0
        for (method in methods) {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Activity)?.let { activity ->
                    schedulePbCommentActivityBackgroundRefresh(activity)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPbCommentSurfaceHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        for (className in PB_COMMENT_SURFACE_CLASSES) {
            val surfaceClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            for (ctor in surfaceClass.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { surface ->
                        rememberPbCommentSurfaceView(surface)
                    }
                    result
                }
                installedCount++
            }
            ReflectionUtils.findMethodInHierarchy(surfaceClass, "onAttachedToWindow")
                ?.takeIf { it.returnType == Void.TYPE }
                ?.let { method ->
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { surface ->
                            rememberPbCommentSurfaceView(surface)
                            schedulePbCommentSurfaceRefresh(surface)
                        }
                        result
                    }
                    installedCount++
                }
        }
        return installedCount
    }

    private fun installPbCommentListItemGetViewHook(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val adapterClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}" }
                return 0
            }
        val method = XposedCompat.findMethodOrNull(
            adapterClass,
            StableTiebaHookPoints.METHOD_GET_VIEW,
            java.lang.Integer.TYPE,
            View::class.java,
            ViewGroup::class.java,
        ) ?: run {
            XposedCompat.logD { "$TAG method NOT FOUND: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.getView" }
            return 0
        }
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            val itemView = result as? View ?: return@intercept result
            val parent = chain.args.getOrNull(2) as? ViewGroup
            rememberPbCommentBackgroundWriteRole(itemView, PbCommentBackgroundWriteRole.CLEAR)
            schedulePbCommentListItemBackground(itemView, parent)
            result
        }
        return 1
    }

    private fun installSubPbReplyAdapterHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val adapterClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SUB_PB_REPLY_ADAPTER_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.SUB_PB_REPLY_ADAPTER_CLASS}" }
                return 0
            }
        val viewHolderClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TYPE_ADAPTER_VIEW_HOLDER_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.TYPE_ADAPTER_VIEW_HOLDER_CLASS}" }
                return 0
            }
        var installedCount = 0

        XposedCompat.findMethodOrNull(
            adapterClass,
            StableTiebaHookPoints.METHOD_ON_FILL_VIEW_HOLDER,
            java.lang.Integer.TYPE,
            View::class.java,
            ViewGroup::class.java,
            Any::class.java,
            viewHolderClass,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val itemView = result as? View ?: chain.args.getOrNull(1) as? View
                val parent = chain.args.getOrNull(2) as? ViewGroup
                if (itemView != null) {
                    scheduleSubPbReplyItemGlass(itemView, parent)
                }
                result
            }
            installedCount++
        }

        return installedCount
    }

    private fun installSubPbInputBarHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val subPbViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SUB_PB_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.SUB_PB_VIEW_CLASS}" }
                return 0
            }
        var installedCount = 0
        for (ctor in subPbViewClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { subPbView ->
                    scheduleSubPbInputBarDynamicTint(subPbView)
                }
                result
            }
            installedCount++
        }
        ReflectionUtils.findMethodInHierarchy(subPbViewClass, "onAttachedToWindow")
            ?.takeIf { it.returnType == Void.TYPE }
            ?.let { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { subPbView ->
                        scheduleSubPbInputBarDynamicTint(subPbView)
                    }
                    result
                }
                installedCount++
            }
        return installedCount
    }

    private fun installSubPbNextPageGlassHook(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val target = runtimeTargets?.subPbSetNextPageTarget ?: run {
            XposedCompat.logD { "$TAG sub pb next page SKIP: setNextPage method not resolved" }
            return 0
        }
        val listViewClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}" }
                return 0
            }
        val parameterType = resolveCachedParameterClass(target.parameterTypeName, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG sub pb next page parameter type unavailable: ${target.parameterTypeName}"
                }
                return 0
            }
        val setNextPageMethod = XposedCompat.findMethodOrNull(
            listViewClass,
            target.methodName,
            parameterType,
        )
        if (
            setNextPageMethod == null ||
            setNextPageMethod.returnType != Void.TYPE ||
            Modifier.isStatic(setNextPageMethod.modifiers)
        ) {
            XposedCompat.logD {
                "$TAG sub pb next page method unavailable: " +
                    "${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}.${target.methodName}(${target.parameterTypeName})"
            }
            return 0
        }
        setNextPageMethod.isAccessible = true
        mod.hook(setNextPageMethod).intercept { chain ->
            val result = chain.proceed()
            val listView = chain.thisObject as? ViewGroup
            val nextPage = chain.args.getOrNull(0)
            if (listView != null && nextPage != null) {
                scheduleSubPbNextPageGlass(listView)
            }
            result
        }
        return 1
    }

    private fun resolveCachedParameterClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "boolean" -> java.lang.Boolean.TYPE
            "byte" -> java.lang.Byte.TYPE
            "char" -> java.lang.Character.TYPE
            "short" -> java.lang.Short.TYPE
            "int" -> Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "float" -> java.lang.Float.TYPE
            "double" -> java.lang.Double.TYPE
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
    }

    private fun installSubPbNavigationBarHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val navigationBarClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.NAVIGATION_BAR_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.NAVIGATION_BAR_CLASS}" }
                return 0
            }
        var installedCount = 0
        for (ctor in navigationBarClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { navigationBar ->
                    scheduleSubPbNavigationBarTint(navigationBar)
                }
                result
            }
            installedCount++
        }
        resolveCachedParameterClass(BD_PAGE_CONTEXT_CLASS, cl)?.let { pageContextClass ->
            XposedCompat.findMethodOrNull(
                navigationBarClass,
                "onChangeSkinType",
                pageContextClass,
                java.lang.Integer.TYPE,
            )
        }?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { navigationBar ->
                    scheduleSubPbNavigationBarTint(navigationBar)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPbCommentItemFrameHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val itemFrameClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_ITEM_FRAME_VIEW_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.PB_ITEM_FRAME_VIEW_CLASS}" }
                return 0
            }
        var installedCount = 0
        for (ctor in itemFrameClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { itemFrame ->
                    rememberPbCommentItemFrame(itemFrame)
                }
                result
            }
            installedCount++
        }
        XposedCompat.findMethodOrNull(
            itemFrameClass,
            "setPageStartFrom",
            java.lang.Integer.TYPE,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { itemFrame ->
                    rememberPbCommentItemFrame(itemFrame)
                    schedulePbCommentItemFrameRefresh(itemFrame)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPbSubPbLayoutHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val subPbLayoutClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_SUB_PB_LAYOUT_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.PB_SUB_PB_LAYOUT_CLASS}" }
                return 0
            }
        var installedCount = 0
        for (ctor in subPbLayoutClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { subPbLayout ->
                    rememberPbSubPbLayout(subPbLayout)
                }
                result
            }
            installedCount++
        }
        val postDataClass = resolveCachedParameterClass(PB_POST_DATA_CLASS, cl)
        if (postDataClass != null) {
            XposedCompat.findMethodOrNull(
                subPbLayoutClass,
                "setData",
                postDataClass,
                View::class.java,
            )
        } else {
            null
        }?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { subPbLayout ->
                    rememberPbSubPbLayout(subPbLayout)
                    schedulePbSubPbLayoutRefresh(subPbLayout)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPbReplyTitleViewHolderHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val holderClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_REPLY_TITLE_VIEW_HOLDER_CLASS, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG class NOT FOUND: ${StableTiebaHookPoints.PB_REPLY_TITLE_VIEW_HOLDER_CLASS}"
                }
                return 0
            }
        var installedCount = 0
        for (ctor in holderClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                val root = chain.args.firstOrNull { it is View } as? View
                if (root != null) {
                    rememberPbReplyTitleViewHolderRoot(root)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun installPbCommonLayoutPreloaderHook(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val methodName = runtimeTargets?.pbCommonLayoutPreloaderGetOrDefaultMethod ?: run {
            XposedCompat.logD { "$TAG common layout preloader SKIP: method not resolved" }
            return 0
        }
        val preloaderClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG class NOT FOUND: ${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}"
                }
                return 0
            }
        val method = XposedCompat.findMethodOrNull(
            preloaderClass,
            methodName,
            Context::class.java,
            java.lang.Boolean.TYPE,
            Integer.TYPE,
            Class::class.java,
        )
        if (method == null || !View::class.java.isAssignableFrom(method.returnType)) {
            XposedCompat.logD {
                "$TAG common layout preloader method unavailable: " +
                    "${StableTiebaHookPoints.PB_COMMON_LAYOUT_PRELOADER_CLASS}.$methodName"
            }
            return 0
        }
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            val result = chain.proceed()
            (result as? View)?.let { view ->
                applyPbCommonPreloadedLayoutTintSafely(view)
                view.post { applyPbCommonPreloadedLayoutTintSafely(view) }
            }
            result
        }
        return 1
    }

    private fun installPbDynamicBackgroundColorHooks(cl: ClassLoader): Int {
        val targets = runtimeTargets
        val hasDynamicBackgroundTargets = targets?.dynamicBackgroundColorIds?.isNotEmpty() == true
        if (!hasDynamicBackgroundTargets) {
            XposedCompat.logD {
                "$TAG dynamic background color resources not resolved; sub pb nav bar block remains active"
            }
        }
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0

        val skinManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SKIN_MANAGER_CLASS, cl)
        if (skinManagerClass == null) {
            XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.SKIN_MANAGER_CLASS}" }
        } else {
            XposedCompat.findMethodOrNull(
                skinManagerClass,
                "setBackgroundColor",
                View::class.java,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
            )?.let { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0) as? View
                        ?: return@intercept chain.proceed()
                    if (applySubPbNavigationBarTintForBackgroundWrite(view)) {
                        return@intercept null
                    }
                    val colorResId = (chain.args.getOrNull(1) as? Number)?.toInt()
                        ?: return@intercept chain.proceed()
                    val skinType = (chain.args.getOrNull(2) as? Number)?.toInt()
                    if (interceptPbCommentNativeBackgroundWrite(view)) {
                        afterSkinManagerPbBackgroundWrite(view)
                        return@intercept null
                    }
                    val color = resolvePbDynamicBackgroundReplacement(view, colorResId, skinType)
                    if (color == null) {
                        val result = chain.proceed()
                        afterSkinManagerPbBackgroundWrite(view)
                        return@intercept result
                    }
                    if (applySubPbNextPageMoreViewTransparencyIfNeeded(view)) {
                        return@intercept null
                    }
                    if (!applySubPbInputBarFrameDynamicTintIfNeeded(view)) {
                        setBackgroundColorPreservingPadding(view, color)
                    }
                    afterSkinManagerPbBackgroundWrite(view)
                    null
                }
                installedCount++
            }

            XposedCompat.findMethodOrNull(
                skinManagerClass,
                StableTiebaHookPoints.METHOD_SET_BACKGROUND_RESOURCE,
                View::class.java,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
            )?.let { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0) as? View
                        ?: return@intercept chain.proceed()
                    if (applySubPbNavigationBarTintForBackgroundWrite(view)) {
                        return@intercept null
                    }
                    val colorResId = (chain.args.getOrNull(1) as? Number)?.toInt()
                        ?: return@intercept chain.proceed()
                    val skinType = (chain.args.getOrNull(2) as? Number)?.toInt()
                    if (interceptPbCommentNativeBackgroundWrite(view)) {
                        afterSkinManagerPbBackgroundWrite(view)
                        return@intercept null
                    }
                    val color = resolvePbDynamicBackgroundReplacement(view, colorResId, skinType)
                    if (color == null) {
                        val result = chain.proceed()
                        afterSkinManagerPbBackgroundWrite(view)
                        return@intercept result
                    }
                    if (applySubPbNextPageMoreViewTransparencyIfNeeded(view)) {
                        return@intercept null
                    }
                    if (!applySubPbInputBarFrameDynamicTintIfNeeded(view)) {
                        setBackgroundColorPreservingPadding(view, color)
                    }
                    afterSkinManagerPbBackgroundWrite(view)
                    null
                }
                installedCount++
            }
            installedCount += installSkinManagerShapeBackgroundBlock(skinManagerClass)
        }

        val emManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.EM_MANAGER_CLASS, cl)
        if (emManagerClass == null) {
            XposedCompat.logD { "$TAG class NOT FOUND: ${StableTiebaHookPoints.EM_MANAGER_CLASS}" }
        } else {
            XposedCompat.findMethodOrNull(
                emManagerClass,
                "setBackGroundColor",
                java.lang.Integer.TYPE,
            )?.let { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val manager = chain.thisObject ?: return@intercept chain.proceed()
                    val colorResId = (chain.args.getOrNull(0) as? Number)?.toInt()
                        ?: return@intercept chain.proceed()
                    val view = readEmManagerView(manager) ?: return@intercept chain.proceed()
                    if (interceptHomeFeedCardNativeBackgroundWrite(view)) {
                        return@intercept null
                    }
                    if (interceptPbCommentNativeBackgroundWrite(view)) {
                        afterEmManagerPbBackgroundWrite(view)
                        return@intercept null
                    }
                    val shareDialogColor = resolveShareDialogDynamicTintColor(view, colorResId)
                    if (shareDialogColor != null) {
                        if (!applyEmManagerRealBackgroundColor(manager, shareDialogColor)) {
                            setBackgroundColorPreservingPadding(view, shareDialogColor)
                        }
                        afterEmManagerPbBackgroundWrite(view)
                        return@intercept null
                    }
                    val color = resolvePbDynamicBackgroundReplacement(view, colorResId, null)
                        ?: run {
                            val result = chain.proceed()
                            afterEmManagerPbBackgroundWrite(view)
                            return@intercept result
                        }
                    if (applyEmManagerRealBackgroundColor(manager, color)) {
                        afterEmManagerPbBackgroundWrite(view)
                        null
                    } else {
                        val result = chain.proceed()
                        afterEmManagerPbBackgroundWrite(view)
                        result
                    }
                }
                installedCount++
            }
            installedCount += installHomeFeedCardEmManagerBackgroundBlock(emManagerClass)
        }

        return installedCount
    }

    private fun resolveShareDialogDynamicTintColor(view: View, colorResId: Int): Int? {
        val targets = runtimeTargets ?: return null
        if (colorResId == 0 || colorResId !in targets.dynamicBackgroundColorIds) return null
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return null
        if (!isShareDialogDynamicTintTarget(view)) return null
        return resolveCachedPbCommentDynamicTintColorOrNull()
    }

    private fun isShareDialogDynamicTintTarget(view: View): Boolean {
        synchronized(shareDialogDynamicTintTargets) {
            if (shareDialogDynamicTintTargets.containsKey(view)) return true
        }
        val group = view as? ViewGroup ?: return false
        if (!hasShareDialogMarker(group, SHARE_DIALOG_MARKER_SCAN_DEPTH)) return false
        synchronized(shareDialogDynamicTintTargets) {
            shareDialogDynamicTintTargets[view] = true
        }
        return true
    }

    private fun hasShareDialogMarker(view: View, depth: Int): Boolean {
        if (depth < 0) return false
        if (view.javaClass.name in SHARE_DIALOG_MARKER_CLASSES) return true
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (hasShareDialogMarker(child, depth - 1)) return true
        }
        return false
    }

    private fun installHomeFeedCardEmManagerBackgroundBlock(emManagerClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        XposedCompat.findMethodOrNull(
            emManagerClass,
            "setBackGroundSelectorColor",
            java.lang.Integer.TYPE,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val manager = chain.thisObject ?: return@intercept chain.proceed()
                val view = readEmManagerView(manager) ?: return@intercept chain.proceed()
                if (interceptHomeFeedCardNativeBackgroundWrite(view)) {
                    return@intercept null
                }
                chain.proceed()
            }
            installedCount++
        }
        XposedCompat.findMethodOrNull(
            emManagerClass,
            "setBackGroundSelectorColor",
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val manager = chain.thisObject ?: return@intercept chain.proceed()
                val view = readEmManagerView(manager) ?: return@intercept chain.proceed()
                if (interceptHomeFeedCardNativeBackgroundWrite(view)) {
                    return@intercept null
                }
                chain.proceed()
            }
            installedCount++
        }
        return installedCount
    }

    private fun installSkinManagerShapeBackgroundBlock(skinManagerClass: Class<*>): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        for (parameterTypes in SKIN_MANAGER_SHAPE_BACKGROUND_SIGNATURES) {
            val method = XposedCompat.findMethodOrNull(
                skinManagerClass,
                "setBackgroundShapeDrawable",
                *parameterTypes,
            ) ?: continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                    ?: return@intercept chain.proceed()
                if (applySubPbNavigationBarTintForBackgroundWrite(view)) {
                    return@intercept null
                }
                if (interceptPbCommentNativeBackgroundWrite(view)) {
                    afterSkinManagerPbBackgroundWrite(view)
                    return@intercept null
                }
                chain.proceed()
            }
            installedCount++
        }
        return installedCount
    }

    private fun afterSkinManagerPbBackgroundWrite(view: View) {
        applySubPbNextPageMoreViewTransparencyIfNeeded(view)
        schedulePbReplyBarInputCapsuleDynamicTint(view)
        applySubPbInputBarFrameDynamicTintIfNeeded(view)
        scheduleSubPbInputBarDynamicTint(view, reapplyAfterDelay = false)
    }

    private fun afterEmManagerPbBackgroundWrite(view: View) {
        schedulePbDialogRoundLayoutDynamicTint(view)
        schedulePbReplyBarInputCapsuleDynamicTint(view)
        scheduleSubPbInputBarDynamicTint(view, reapplyAfterDelay = false)
    }

    private fun installPbSortSwitchButtonDynamicTintHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val targets = runtimeTargets ?: return 0
        val sortSwitchClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG class NOT FOUND: ${StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS}"
                }
                return 0
            }

        var installedCount = 0
        val backgroundFieldName = targets.sortSwitchBackgroundPaintField
        if (backgroundFieldName == null) {
            XposedCompat.logD { "$TAG sort switch dynamic tint SKIP: background paint field not resolved" }
        } else {
            val paintField = try {
                sortSwitchClass.getDeclaredField(backgroundFieldName).apply {
                    isAccessible = true
                }
            } catch (t: Throwable) {
                XposedCompat.logD {
                    "$TAG sort switch dynamic tint field unavailable: $backgroundFieldName ${t.message}"
                }
                null
            }
            if (paintField != null && paintField.type.name == "android.graphics.Paint") {
                pbSortSwitchBackgroundPaintField = paintField
                for (ctor in sortSwitchClass.declaredConstructors) {
                    ctor.isAccessible = true
                    mod.hook(ctor).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { button ->
                            applyPbSortSwitchBackgroundDynamicTint(button, invalidateOnChange = true)
                        }
                        result
                    }
                    installedCount++
                }

                XposedCompat.findMethodOrNull(sortSwitchClass, "onDraw", Canvas::class.java)?.let { method ->
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        (chain.thisObject as? View)?.let { button ->
                            applyPbSortSwitchBackgroundDynamicTint(button, invalidateOnChange = false)
                        }
                        chain.proceed()
                    }
                    installedCount++
                } ?: XposedCompat.logD { "$TAG sort switch dynamic tint SKIP: onDraw(Canvas) not found" }
            } else if (paintField != null) {
                XposedCompat.logD {
                    "$TAG sort switch dynamic tint field rejected: " +
                        "$backgroundFieldName type=${paintField.type.name}"
                }
            }
        }

        val slideDrawMethodName = targets.sortSwitchSlideDrawMethod
        val slidePathFieldName = targets.sortSwitchSlidePathField
        if (slideDrawMethodName == null || slidePathFieldName == null) {
            XposedCompat.logD { "$TAG sort switch selected tint SKIP: slide draw symbols not resolved" }
        } else {
            val pathField = try {
                sortSwitchClass.getDeclaredField(slidePathFieldName).apply {
                    isAccessible = true
                }
            } catch (t: Throwable) {
                XposedCompat.logD {
                    "$TAG sort switch selected tint path field unavailable: " +
                        "$slidePathFieldName ${t.message}"
                }
                null
            }
            if (pathField != null && pathField.type.name == "android.graphics.Path") {
                pbSortSwitchSlidePathField = pathField
                XposedCompat.findMethodOrNull(sortSwitchClass, slideDrawMethodName, Canvas::class.java)
                    ?.let { method ->
                        method.isAccessible = true
                        mod.hook(method).intercept { chain ->
                            val button = chain.thisObject as? View
                            val canvas = chain.args.getOrNull(0) as? Canvas
                            val saveCount = if (
                                button != null &&
                                canvas != null &&
                                shouldReplacePbSortSwitchNativeSelectedSlide(button)
                            ) {
                                savePbSortSwitchNativeSelectedTransparentLayer(button, canvas)
                            } else {
                                null
                            }
                            val result = if (saveCount != null && canvas != null) {
                                try {
                                    chain.proceed()
                                } finally {
                                    canvas.restoreToCount(saveCount)
                                }
                            } else {
                                chain.proceed()
                            }
                            if (button != null && canvas != null) {
                                drawPbSortSwitchSelectedDynamicTint(button, canvas)
                            }
                            result
                        }
                        installedCount++
                    }
                    ?: XposedCompat.logD {
                        "$TAG sort switch selected tint SKIP: $slideDrawMethodName(Canvas) not found"
                    }
            } else if (pathField != null) {
                XposedCompat.logD {
                    "$TAG sort switch selected tint path field rejected: " +
                        "$slidePathFieldName type=${pathField.type.name}"
                }
            }
        }

        return installedCount
    }

    private fun installPbEnterForumCapsuleDynamicTintHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val targets = runtimeTargets ?: return 0
        val controllerClassName = targets.enterForumCapsuleControllerClass
        val initMethodName = targets.enterForumCapsuleInitMethod
        val refreshMethodName = targets.enterForumCapsuleRefreshMethod
        val viewFieldName = targets.enterForumCapsuleViewField
        val titleFieldName = targets.enterForumCapsuleTitleField
        if (
            controllerClassName == null ||
            initMethodName == null ||
            refreshMethodName == null ||
            viewFieldName == null ||
            titleFieldName == null
        ) {
            XposedCompat.logD { "$TAG enter forum capsule dynamic tint SKIP: symbols not resolved" }
            return 0
        }
        val controllerClass = XposedCompat.findClassOrNull(controllerClassName, cl)
            ?: run {
                XposedCompat.logD { "$TAG class NOT FOUND: $controllerClassName" }
                return 0
            }
        val viewField = try {
            controllerClass.getDeclaredField(viewFieldName).apply { isAccessible = true }
        } catch (t: Throwable) {
            XposedCompat.logD {
                "$TAG enter forum capsule view field unavailable: $viewFieldName ${t.message}"
            }
            return 0
        }
        if (!View::class.java.isAssignableFrom(viewField.type)) {
            XposedCompat.logD {
                "$TAG enter forum capsule view field rejected: $viewFieldName type=${viewField.type.name}"
            }
            return 0
        }
        val titleField = try {
            controllerClass.getDeclaredField(titleFieldName).apply { isAccessible = true }
        } catch (t: Throwable) {
            XposedCompat.logD {
                "$TAG enter forum capsule title field unavailable: $titleFieldName ${t.message}"
            }
            return 0
        }
        if (titleField.type != String::class.java) {
            XposedCompat.logD {
                "$TAG enter forum capsule title field rejected: $titleFieldName type=${titleField.type.name}"
            }
            return 0
        }
        pbEnterForumCapsuleViewField = viewField
        pbEnterForumCapsuleTitleField = titleField

        var installedCount = 0
        fun hookNoArgVoidMethod(methodName: String, label: String) {
            val method = XposedCompat.findMethodOrNull(controllerClass, methodName)
            if (method == null || method.returnType != Void.TYPE || Modifier.isStatic(method.modifiers)) {
                XposedCompat.logD { "$TAG enter forum capsule $label method unavailable: $methodName" }
                return
            }
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                chain.thisObject?.let { controller ->
                    applyPbEnterForumCapsuleBackgroundDynamicTint(
                        controller,
                        reapplyAfterLayout = true,
                    )
                }
                result
            }
            installedCount++
        }

        hookNoArgVoidMethod(initMethodName, "init")
        if (refreshMethodName != initMethodName) {
            hookNoArgVoidMethod(refreshMethodName, "refresh")
        }
        return installedCount
    }

    private fun installHomeTopTabObservers(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val tabSelectedTargets = runtimeTargets
            ?.topChromeTabSelectedMethods
            .orEmpty()
            .groupBy { it.className }
        var installedCount = 0
        for (className in HOME_TOP_CHROME_CLASSES) {
            val clazz = XposedCompat.findClassOrNull(className, cl)
            if (clazz == null) {
                XposedCompat.logD { "$TAG top chrome class NOT FOUND: $className" }
                continue
            }
            for (ctor in clazz.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { topChrome ->
                        scheduleHomeTopChromeRefresh(topChrome)
                    }
                    result
                }
                installedCount++
            }
            for (target in tabSelectedTargets[className].orEmpty()) {
                val method = XposedCompat.findMethodOrNull(
                    clazz,
                    target.methodName,
                    Integer.TYPE,
                    Integer.TYPE,
                )
                if (method == null || method.returnType != Void.TYPE || Modifier.isStatic(method.modifiers)) {
                    XposedCompat.logD {
                        "$TAG top chrome tab selected method unavailable: " +
                            "${target.className}#${target.methodName}"
                    }
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val topChrome = chain.thisObject as? View
                    if (topChrome != null) {
                        val itemType = (chain.args.getOrNull(1) as? Number)?.toInt()
                        if (itemType != null) {
                            topChromeTabTypes[topChrome] = itemType
                        }
                        scheduleHomeTopChromeRefresh(topChrome)
                    }
                    result
                }
                installedCount++
            }
        }
        installedCount += installHomeRecommendFragmentRefreshHooks(cl)
        return installedCount
    }

    private fun installHomeRecommendFragmentRefreshHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(HOME_RECOMMEND_CONTROL_FRAGMENT_CLASS, cl)
            ?: run {
                XposedCompat.logD { "$TAG recommend fragment class NOT FOUND: $HOME_RECOMMEND_CONTROL_FRAGMENT_CLASS" }
                return 0
            }
        var installedCount = 0
        XposedCompat.findMethodOrNull(clazz, "onResume")?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                scheduleHomeTopChromeRefreshFromFragment(chain.thisObject)
                result
            }
            installedCount++
        }
        XposedCompat.findMethodOrNull(clazz, "setPrimary", java.lang.Boolean.TYPE)?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (chain.args.getOrNull(0) == true) {
                    scheduleHomeTopChromeRefreshFromFragment(chain.thisObject)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun scheduleHomeTopChromeRefresh(topChrome: View) {
        rememberHomeTopChromeView(topChrome)
        synchronized(homeTopChromeRefreshScheduled) {
            if (homeTopChromeRefreshScheduled.containsKey(topChrome)) return
            homeTopChromeRefreshScheduled[topChrome] = true
        }
        applyHomeTopTabDynamicTintSafely(topChrome)
        topChrome.post {
            try {
                if (topChrome.isAttachedToWindow) {
                    applyHomeTopTabDynamicTintSafely(topChrome)
                }
            } finally {
                synchronized(homeTopChromeRefreshScheduled) {
                    homeTopChromeRefreshScheduled.remove(topChrome)
                }
            }
        }
    }

    private fun scheduleHomeTopChromeRefreshFromFragment(fragment: Any?) {
        if (fragment == null) return
        val view = runCatching {
            fragment.javaClass.getMethod("getView").invoke(fragment) as? View
        }.getOrNull() ?: return
        scheduleHomeTopChromeRefreshFromAnchor(view)
    }

    private fun scheduleHomeTopChromeRefreshFromAnchor(anchor: View) {
        anchor.post {
            val root = anchor.rootView ?: anchor
            applyChromeStyleForMatchingViews(root, HOME_TOP_CHROME_CLASSES) { topChrome ->
                scheduleHomeTopChromeRefresh(topChrome)
            }
        }
    }

    private fun rememberHomeTopChromeView(topChrome: View) {
        installHomeTopChromeAttachRefresh(topChrome)
    }

    private fun installHomeTopChromeAttachRefresh(topChrome: View) {
        synchronized(homeTopChromeAttachRefreshInstalled) {
            if (homeTopChromeAttachRefreshInstalled.containsKey(topChrome)) return
            homeTopChromeAttachRefreshInstalled[topChrome] = true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                scheduleHomeTopChromeRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        runCatching {
            topChrome.addOnAttachStateChangeListener(attachListener)
        }.onFailure {
            homeTopChromeAttachRefreshInstalled.remove(topChrome)
            runCatching { topChrome.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun installHomeBottomTabDynamicTintHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val hostClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG bottom tab host class NOT FOUND: ${StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS}"
                }
                return 0
            }
        val widgetClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS, cl)
            ?: run {
                XposedCompat.logD {
                    "$TAG bottom tab widget class NOT FOUND: ${StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS}"
                }
                return 0
            }
        val skinManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.SKIN_MANAGER_CLASS, cl)
        var installedCount = 0
        for (ctor in hostClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { tabHost ->
                    scheduleHomeBottomTabRefresh(tabHost)
                }
                result
            }
            installedCount++
        }
        for (ctor in widgetClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { tabWidget ->
                    scheduleHomeBottomTabRefresh(tabWidget)
                }
                result
            }
            installedCount++
        }
        val refreshMethods = arrayOf(
            "onPageSelected",
            "setCurrentTab",
            "setCurrentTabByIndex",
            "setCurrentTabByType",
            "setTabWidgetBackgroundColor",
        )
        for (methodName in refreshMethods) {
            XposedCompat.findMethodOrNull(hostClass, methodName, java.lang.Integer.TYPE)?.let { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { tabHost ->
                        scheduleHomeBottomTabRefresh(tabHost)
                    }
                    result
                }
                installedCount++
            }
        }
        XposedCompat.findMethodOrNull(widgetClass, "addView", View::class.java)?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { tabWidget ->
                    scheduleHomeBottomTabRefresh(tabWidget)
                }
                result
            }
            installedCount++
        }
        if (skinManagerClass != null) {
            installedCount += installHomeBottomTabSkinBackgroundBlock(skinManagerClass)
        } else {
            XposedCompat.logD {
                "$TAG bottom tab dynamic tint SKIP: skin manager class not found"
            }
        }
        return installedCount
    }

    private fun installHomeBottomTabSkinBackgroundBlock(
        skinManagerClass: Class<*>,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = 0
        XposedCompat.findMethodOrNull(
            skinManagerClass,
            "setBackgroundColor",
            View::class.java,
            java.lang.Integer.TYPE,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                if (
                    view != null &&
                    (
                        applyHomeBottomTabDynamicTintForBackgroundWrite(view) ||
                            applyHomeBottomTabBoundaryTintForSkinBackgroundWrite(view)
                        )
                ) {
                    return@intercept null
                }
                chain.proceed()
            }
            installedCount++
        }
        XposedCompat.findMethodOrNull(
            skinManagerClass,
            "setBackgroundColor",
            View::class.java,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
        )?.let { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                if (
                    view != null &&
                    (
                        applyHomeBottomTabDynamicTintForBackgroundWrite(view) ||
                            applyHomeBottomTabBoundaryTintForSkinBackgroundWrite(view)
                        )
                ) {
                    return@intercept null
                }
                chain.proceed()
            }
            installedCount++
        }
        return installedCount
    }

    private fun installHomeChromeDirectBackgroundBlock(): Int {
        val mod = XposedCompat.module ?: return 0
        val method = runCatching {
            View::class.java.getDeclaredMethod("setBackgroundColor", java.lang.Integer.TYPE)
        }.getOrNull() ?: return 0
        method.isAccessible = true
        mod.hook(method).intercept { chain ->
            if (isChromeDynamicTintBackgroundWriteInProgress()) {
                return@intercept chain.proceed()
            }
            if (!ConfigManager.isHomeNativeGlassEnabled || !ConfigManager.isHomeTabDynamicTintEnabled) {
                return@intercept chain.proceed()
            }
            val view = chain.thisObject as? View ?: return@intercept chain.proceed()
            if (!isHomeChromeDynamicTintTarget(view)) return@intercept chain.proceed()
            if (applyHomeChromeDynamicTintForBackgroundWrite(view)) {
                return@intercept null
            }
            chain.proceed()
        }
        return 1
    }

    private fun isHomeChromeDynamicTintTarget(view: View): Boolean {
        val className = view.javaClass.name
        return className == StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS ||
            className in HOME_TOP_CHROME_CLASSES
    }

    private fun applyHomeChromeDynamicTintForBackgroundWrite(view: View): Boolean {
        val className = view.javaClass.name
        return when {
            className == StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS ->
                applyHomeBottomTabDynamicTintForBackgroundWrite(view)
            className in HOME_TOP_CHROME_CLASSES ->
                applyHomeTopTabDynamicTintForBackgroundWrite(view)
            else -> false
        }
    }

    private fun applyHomeTopTabDynamicTintForBackgroundWrite(view: View): Boolean {
        if (
            !ConfigManager.isHomeNativeGlassEnabled ||
            !ConfigManager.isHomeTabDynamicTintEnabled ||
            !hasPageBackgroundOverride()
        ) {
            return false
        }
        return try {
            val color = resolveCachedHomeTabDynamicTintColor(view) ?: return false
            rememberChromeGlassOriginalState(view)
            if ((view.background as? ColorDrawable)?.color != color) {
                setChromeDynamicTintBackgroundColor(view, color)
            }
            applyHomeTopTabBoundaryDecoration(view, color)
            true
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home top tab direct background block failed: ${t.message}" }
            }
            false
        }
    }

    private fun applyHomeBottomTabDynamicTintForBackgroundWrite(view: View): Boolean {
        if (view.javaClass.name != StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS) return false
        if (
            !ConfigManager.isHomeNativeGlassEnabled ||
            !ConfigManager.isHomeTabDynamicTintEnabled ||
            !hasPageBackgroundOverride()
        ) {
            return false
        }
        return try {
            val color = resolveCachedHomeTabDynamicTintColor(view) ?: return false
            rememberChromeGlassOriginalState(view)
            if ((view.background as? ColorDrawable)?.color != color) {
                setChromeDynamicTintBackgroundColor(view, color)
            }
            invokeBooleanMethod(view, "setShouldDrawTopLine", false)
            true
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home bottom tab background block failed: ${t.message}" }
            }
            false
        }
    }

    private fun applyHomeBottomTabBoundaryTintForSkinBackgroundWrite(view: View): Boolean {
        if (view.javaClass.name != "android.view.View") return false
        if (
            !ConfigManager.isHomeNativeGlassEnabled ||
            !ConfigManager.isHomeTabDynamicTintEnabled ||
            !hasPageBackgroundOverride()
        ) {
            return false
        }
        if (!isHomeBottomTabBoundaryLineCandidate(view)) return false
        val tabHost = findHomeBottomTabHostAncestor(view) ?: return false
        return try {
            val color = resolveCachedHomeTabDynamicTintColor(tabHost) ?: return false
            rememberChromeGlassOriginalState(view)
            if ((view.background as? ColorDrawable)?.color != color) {
                setChromeDynamicTintBackgroundColor(view, color)
            }
            true
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home bottom tab boundary background block failed: ${t.message}" }
            }
            false
        }
    }

    private fun scheduleHomeBottomTabRefresh(tabHost: View) {
        installHomeBottomTabAttachRefresh(tabHost)
        synchronized(homeBottomTabRefreshScheduled) {
            if (homeBottomTabRefreshScheduled.containsKey(tabHost)) return
            homeBottomTabRefreshScheduled[tabHost] = true
        }
        applyHomeBottomTabDynamicTintSafely(tabHost)
        tabHost.post {
            try {
                if (tabHost.isAttachedToWindow) {
                    applyHomeBottomTabDynamicTintSafely(tabHost)
                }
            } finally {
                synchronized(homeBottomTabRefreshScheduled) {
                    homeBottomTabRefreshScheduled.remove(tabHost)
                }
            }
        }
    }

    private fun installHomeBottomTabAttachRefresh(tabHost: View) {
        synchronized(homeBottomTabAttachRefreshInstalled) {
            if (homeBottomTabAttachRefreshInstalled.containsKey(tabHost)) return
            homeBottomTabAttachRefreshInstalled[tabHost] = true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                scheduleHomeBottomTabRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        runCatching {
            tabHost.addOnAttachStateChangeListener(attachListener)
        }.onFailure {
            homeBottomTabAttachRefreshInstalled.remove(tabHost)
            runCatching { tabHost.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun installHomeSearchBoxHooks(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installedCount = installHomeSearchBoxViewConstructors(cl)
        val targets = runtimeTargets ?: return installedCount
        val ownerClassName = targets.homeSearchBoxOwnerClass ?: return installedCount
        val initMethodName = targets.homeSearchBoxInitMethod ?: return installedCount
        val getterMethodName = targets.homeSearchBoxGetterMethod ?: return installedCount
        val ownerClass = XposedCompat.findClassOrNull(ownerClassName, cl) ?: return installedCount
        val recyclerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS, cl)
            ?: return installedCount
        val containerClass = XposedCompat.findClassOrNull(HOME_SEARCH_BOX_HEADER_CONTAINER_CLASS, cl)
            ?: return installedCount
        val initMethod = XposedCompat.findMethodOrNull(ownerClass, initMethodName, recyclerClass, containerClass)
            ?: return installedCount
        val getterMethod = XposedCompat.findMethodOrNull(ownerClass, getterMethodName)
            ?: return installedCount

        initMethod.isAccessible = true
        getterMethod.isAccessible = true
        mod.hook(initMethod).intercept { chain ->
            val result = chain.proceed()
            val searchBox = runCatching { getterMethod.invoke(chain.thisObject) as? View }.getOrNull()
            if (searchBox != null) {
                rememberHomeSearchBox(searchBox)
            }
            result
        }
        installedCount++
        return installedCount
    }

    private fun installHomeSearchBoxViewConstructors(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val searchBoxClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS, cl)
            ?: return 0
        var installedCount = 0
        for (ctor in searchBoxClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { searchBox ->
                    rememberHomeSearchBox(searchBox)
                }
                result
            }
            installedCount++
        }
        return installedCount
    }

    private fun applyPageStyleSafely(page: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        val style = currentHomeNativeGlassRuntimeStyle()
        try {
            SystemBarCompatHook.applyIfNeeded(ReflectionUtils.findActivityFromContext(page.context))
            val backgroundDrawable = createBackgroundDrawable(
                page,
                style,
            )
            val hasPageBackground = backgroundDrawable != null
            if (!hasPageBackground && !hasPageBackgroundOverride()) {
                applyHomeChromeGlassSafely(page)
                return
            }

            markFeedListPath(page, clearBackgrounds = true)
            if (backgroundDrawable != null) {
                clearHomeNativeDefaultBackgrounds(page)
                page.background = backgroundDrawable
            } else {
                clearHomeNativeDefaultBackgrounds(page)
                applyHomeNativePageFallbackBackground(page)
            }
            applyHomeChromeGlassSafely(page)
        } catch (t: Throwable) {
            if (firstPageErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG page style failed: ${t.message}" }
            }
        }
    }

    private fun applyCardStyleSafely(card: View, force: Boolean = false) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        val hasBackgroundOverride = hasPageBackgroundOverride()
        if (!hasBackgroundOverride) return
        try {
            val page = findHomeNativePage(card)
            if (page == null && !isInsideHomeNativePage(card)) {
                return
            }
            val state = homeFeedCardStyleState(card, page)
            if (!force && homeFeedCardStyleStates[card] == state && isHomeFeedCardStyleApplied(card, page)) {
                return
            }
            applyCardGlass(card, page)
            applyCardComponentGlassToDescendants(card, page)
            applyHomeFeedImageContainerRadius(card)
            homeFeedCardStyleStates[card] = state
            if (page != null) {
                schedulePageStyleReapply(page)
            }
        } catch (t: Throwable) {
            if (firstCardErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG card style failed: ${t.message}" }
            }
        }
    }

    private fun scheduleHomeFeedCardStyleReapply(card: View) {
        synchronized(homeFeedCardStyleApplyScheduled) {
            if (homeFeedCardStyleApplyScheduled.containsKey(card)) return
            homeFeedCardStyleApplyScheduled[card] = true
        }
        card.post {
            homeFeedCardStyleApplyScheduled.remove(card)
            applyCardStyleSafely(card)
        }
    }

    private fun homeFeedCardStyleState(card: View, page: View?): HomeFeedCardStyleState {
        val style = currentHomeNativeGlassRuntimeStyle()
        val request = backgroundRequestForStyle(
            style,
            BACKGROUND_CACHE_SAMPLE_EDGE,
            BACKGROUND_CACHE_SAMPLE_EDGE,
        )
        val cached = request?.let { findCachedBackgroundEntry(it) }
        val group = card as? ViewGroup
        return HomeFeedCardStyleState(
            page = page,
            width = card.width,
            height = card.height,
            childCount = group?.childCount ?: 0,
            sourcePath = style.backgroundImagePath,
            blurCachePath = style.blurCacheImagePath,
            sourceLastModified = cached?.lastModified ?: 0L,
            sourceLength = cached?.length ?: 0L,
            blurCacheLastModified = cached?.blurCacheLastModified ?: 0L,
            blurCacheLength = cached?.blurCacheLength ?: 0L,
            tintAlphaPercent = style.tintAlphaPercent,
            cardBlurPercent = style.cardBlurPercent,
            cardRadiusDp = style.cardRadiusDp,
            strokeEnabled = style.strokeEnabled,
            shadowStrengthPercent = style.shadowStrengthPercent,
        )
    }

    private fun hasHomeFeedCardGlassState(card: View): Boolean {
        val group = card as? ViewGroup
        val backgroundHolder = group?.getChildAt(0)
        if (backgroundHolder != null) {
            if (backgroundHolder.background is CardGlassDrawable) return true
            val innerHolder = (backgroundHolder as? ViewGroup)?.let { holder ->
                firstNonRecyclerChild(holder)
            }
            return innerHolder?.background is CardGlassDrawable
        }
        return card.background is CardGlassDrawable
    }

    private fun isHomeFeedCardStyleApplied(card: View, page: View?): Boolean {
        if (hasHomeFeedCardGlassState(card)) return true
        if (page == null) return true
        val request = backgroundRequestForStyle(
            currentHomeNativeGlassRuntimeStyle(),
            BACKGROUND_CACHE_SAMPLE_EDGE,
            BACKGROUND_CACHE_SAMPLE_EDGE,
        )
        return request?.let { findCachedBackgroundEntry(it) }?.blurredBitmap == null
    }

    private fun clearHomeNativeDefaultBackgrounds(page: View) {
        clearForeground(page)
        clearElevation(page)
        val group = page as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearHomeNativeContainerBackground(group.getChildAt(index) ?: continue, depth = 0)
        }
    }

    private fun clearHomeNativeContainerBackground(view: View, depth: Int): Boolean {
        if (isRecyclerView(view)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            clearForeground(view)
            clearElevation(view)
            clearVisibleHomeRecyclerTopBackgrounds(view)
            return true
        }
        if (isFeedCardView(view)) return false

        val group = view as? ViewGroup
        if (group == null) {
            if (depth <= HOME_TOP_BACKGROUND_CLEAR_DEPTH) {
                clearPlainBackground(view)
            }
            return false
        }
        var containsHomeList = false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (clearHomeNativeContainerBackground(child, depth + 1)) {
                containsHomeList = true
            }
        }
        if (containsHomeList || depth <= 1 || isHomeNativeOverlayContainer(view)) {
            view.setBackgroundColor(Color.TRANSPARENT)
            clearForeground(view)
            clearElevation(view)
        }
        return containsHomeList
    }

    private fun clearVisibleHomeRecyclerTopBackgrounds(recycler: View) {
        if (!hasPageBackgroundOverride()) return
        if (recycler.canScrollVertically(-1)) return
        val group = recycler as? ViewGroup ?: return
        val topLimit = recycler.paddingTop + (recycler.resources.displayMetrics.density * 48f).toInt()
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (isFeedCardView(child)) continue
            if (child.top > topLimit && !isKnownHomeTopBackgroundView(child)) continue
            clearHomeTopRecyclerChildBackground(child, depth = 0)
        }
    }

    private fun clearHomeTopRecyclerChildBackground(view: View, depth: Int) {
        if (isFeedCardView(view)) return
        clearPlainBackground(view)
        val group = view as? ViewGroup ?: return
        if (depth >= HOME_TOP_RECYCLER_CHILD_CLEAR_DEPTH) return
        for (index in 0 until group.childCount) {
            clearHomeTopRecyclerChildBackground(group.getChildAt(index) ?: continue, depth + 1)
        }
    }

    private fun isKnownHomeTopBackgroundView(view: View): Boolean {
        val name = view.javaClass.name
        return name == "android.view.View" ||
            name == "android.widget.LinearLayout" ||
            name == "android.widget.FrameLayout" ||
            name.contains("HomePageRecentForum") ||
            name.contains("Header")
    }

    private fun clearPlainBackground(view: View) {
        view.setBackgroundColor(Color.TRANSPARENT)
        clearForeground(view)
        clearElevation(view)
    }

    private fun isHomeNativeOverlayContainer(view: View): Boolean {
        val name = view.javaClass.name
        return name == "android.widget.FrameLayout" ||
            name.contains("CoordinatorLayout") ||
            name.contains("SwipeRefreshLayout")
    }

    private fun schedulePageStyleReapply(page: View) {
        synchronized(pageStyleReapplyScheduled) {
            if (pageStyleReapplyScheduled.containsKey(page)) return
            pageStyleReapplyScheduled[page] = true
        }
        page.post {
            pageStyleReapplyScheduled.remove(page)
            applyPageStyleSafely(page)
        }
    }

    private fun markFeedListPath(view: View, clearBackgrounds: Boolean): Boolean {
        if (isRecyclerView(view)) {
            homeRecyclerViews[view] = true
            installScrollInvalidation(view)
            installHomeRecyclerChildAttachRefresh(view)
            if (clearBackgrounds) {
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            return true
        }
        val group = view as? ViewGroup ?: return false
        var containsHomeList = false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (markFeedListPath(child, clearBackgrounds)) {
                containsHomeList = true
            }
        }
        if (containsHomeList && clearBackgrounds) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        return containsHomeList
    }

    private fun findHomeNativePage(card: View): View? {
        var nearestRecycler: View? = null
        var current = card.parent
        while (current is View) {
            if (isRecyclerView(current)) {
                if (nearestRecycler == null) nearestRecycler = current
            }
            if (current.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS) {
                nearestRecycler?.let { recycler ->
                    homeRecyclerViews[recycler] = true
                    installScrollInvalidation(recycler)
                    installHomeRecyclerChildAttachRefresh(recycler)
                    if (hasPageBackgroundOverride()) {
                        recycler.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun isInsideHomeNativePage(card: View): Boolean {
        var nearestRecycler: View? = null
        var current = card.parent
        while (current is View) {
            if (isRecyclerView(current)) {
                if (homeRecyclerViews.containsKey(current)) return true
                if (nearestRecycler == null) nearestRecycler = current
            }
            if (current.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS) {
                nearestRecycler?.let { recycler ->
                    homeRecyclerViews[recycler] = true
                    installScrollInvalidation(recycler)
                    installHomeRecyclerChildAttachRefresh(recycler)
                    if (hasPageBackgroundOverride()) {
                        recycler.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun applyCardGlass(card: View, page: View?) {
        val group = card as? ViewGroup
        clearElevation(card)
        card.setBackgroundColor(Color.TRANSPARENT)
        clearCardNativePressVisuals(card)
        clearNestedRecyclerBackgrounds(card)
        rememberHomeFeedCardGlassTargets(card)

        val radius = card.resources.displayMetrics.density * effectiveCardRadiusDp()

        val backgroundHolder = group?.getChildAt(0)
        if (backgroundHolder != null) {
            setGlassBackground(backgroundHolder, page, radius)
            val innerHolder = (backgroundHolder as? ViewGroup)?.let { holder ->
                firstNonRecyclerChild(holder)
            }
            if (innerHolder != null) {
                setGlassBackground(innerHolder, page, radius)
            }
        } else {
            setGlassBackground(card, page, radius)
        }
        clearCardNativePressVisuals(card)
    }

    private fun rememberHomeFeedCardGlassTargets(card: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        val group = card as? ViewGroup
        val backgroundHolder = group?.getChildAt(0)
        if (backgroundHolder != null) {
            rememberHomeFeedCardGlassTarget(backgroundHolder)
            val innerHolder = (backgroundHolder as? ViewGroup)?.let { holder ->
                firstNonRecyclerChild(holder)
            }
            if (innerHolder != null) {
                rememberHomeFeedCardGlassTarget(innerHolder)
            }
        } else {
            rememberHomeFeedCardGlassTarget(card)
        }
    }

    private fun rememberHomeFeedCardGlassTarget(view: View) {
        homeFeedCardGlassTargets[view] = true
    }

    private fun interceptHomeFeedCardNativeBackgroundWrite(view: View): Boolean {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return false
        if (!homeFeedCardGlassTargets.containsKey(view) && view.background !is CardGlassDrawable) return false
        if (view.background !is CardGlassDrawable) {
            findHomeFeedCardAncestor(view)?.let { card ->
                scheduleHomeFeedCardStyleReapply(card)
            }
        }
        return true
    }

    private fun findHomeFeedCardAncestor(view: View): View? {
        if (isFeedCardView(view)) return view
        var current = view.parent
        var depth = 0
        while (current is View && depth < HOME_FEED_CARD_BACKGROUND_PARENT_SCAN_DEPTH) {
            if (isFeedCardView(current)) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun applyHomeNativePageFallbackBackground(page: View) {
        if (page.background is CenterCropBitmapDrawable) return
        val color = homeNativePageFallbackColor()
        if ((page.background as? ColorDrawable)?.color == color) return
        page.background = ColorDrawable(color)
    }

    private fun applyHomeFeedImageContainerRadius(card: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        val radius = effectiveImageContainerRadius(card)
        applyCardPicViewImageRadiusInTree(card, radius, depth = 0)
    }

    private fun applyCardPicViewImageRadiusInTree(view: View, radius: Float, depth: Int) {
        if (depth > HOME_IMAGE_CONTAINER_RADIUS_SCAN_DEPTH) return
        if (view.javaClass.name == StableTiebaHookPoints.FEED_CARD_PIC_VIEW_CLASS) {
            applyCardPicViewImageRadius(view, radius)
            return
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            applyCardPicViewImageRadiusInTree(group.getChildAt(index) ?: continue, radius, depth + 1)
        }
    }

    private fun applyCardPicViewImageRadius(cardPicView: View, radius: Float) {
        val group = cardPicView as? ViewGroup ?: return
        val imageStrip = firstDirectChildByClass(group, "android.widget.LinearLayout") as? ViewGroup ?: return
        applyRoundedImageContainerClip(imageStrip, radius)
        for (index in 0 until imageStrip.childCount) {
            val imageCell = imageStrip.getChildAt(index) ?: continue
            if (imageCell.visibility == View.GONE) continue
            applyRoundedImageContainerClip(imageCell, radius)
            applyImageChildRadius(imageCell, radius, depth = 0)
        }
    }

    private fun firstDirectChildByClass(group: ViewGroup, className: String): View? {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (child.javaClass.name == className) return child
        }
        return null
    }

    private fun effectiveImageContainerRadius(view: View): Float {
        return view.resources.displayMetrics.density *
            effectiveCardRadiusDp() *
            IMAGE_CONTAINER_RADIUS_SCALE
    }

    private fun applyImageChildRadius(view: View, radius: Float, depth: Int) {
        if (depth >= IMAGE_CONTAINER_RADIUS_CHILD_DEPTH) return
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (isImageViewLike(child) || child.javaClass.name == StableTiebaHookPoints.CONSTRAIN_IMAGE_LAYOUT_CLASS) {
                applyRoundedImageContainerClip(child, radius)
            }
            applyImageChildRadius(child, radius, depth + 1)
        }
    }

    private fun isImageViewLike(view: View): Boolean {
        val name = view.javaClass.name
        return name.contains("ImageView") ||
            name == StableTiebaHookPoints.TB_IMAGE_CLASS
    }

    private fun applyRoundedImageContainerClip(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val normalizedRadius = radius.coerceAtLeast(0f)
        if (
            imageContainerRadiusStates[view] == normalizedRadius &&
            view.clipToOutline
        ) {
            return
        }
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) return
                outline.setRoundRect(0, 0, view.width, view.height, normalizedRadius)
            }
        }
        view.clipToOutline = true
        if (view is ViewGroup) {
            view.clipChildren = true
            view.clipToPadding = true
        }
        imageContainerRadiusStates[view] = normalizedRadius
        view.invalidateOutline()
    }

    private fun handleCardTouchVisualSafely(card: View, event: MotionEvent?) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        try {
            if (!isInsideHomeNativePage(card)) return
            clearCardNativePressVisuals(card)
            if (event != null) {
                updateCardPressVisual(card, event)
            }
        } catch (t: Throwable) {
            if (firstCardErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG card touch visual failed: ${t.message}" }
            }
        }
    }

    private fun updateCardPressVisual(card: View, event: MotionEvent) {
        val pressed = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_MOVE -> isTouchInsideView(card, event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> false
            else -> return
        }
        setCardGlassPressed(card, pressed)
    }

    private fun setCardGlassPressed(card: View, pressed: Boolean) {
        setGlassPressed(card, pressed)
        val group = card as? ViewGroup ?: return
        val backgroundHolder = group.getChildAt(0)
        if (backgroundHolder != null) {
            setGlassPressed(backgroundHolder, pressed)
            val innerHolder = (backgroundHolder as? ViewGroup)?.let { holder ->
                firstNonRecyclerChild(holder)
            }
            if (innerHolder != null) {
                setGlassPressed(innerHolder, pressed)
            }
        }
    }

    private fun setGlassPressed(view: View, pressed: Boolean) {
        (view.background as? CardGlassDrawable)?.setPressedTintEnabled(pressed)
    }

    private fun rememberPbCommentItemFrame(itemFrame: View) {
        if (!isPbCommentItemFrame(itemFrame)) return
        rememberPbCommentBackgroundWriteRole(itemFrame, PbCommentBackgroundWriteRole.CLEAR)
        installPbCommentItemFrameAttachRefresh(itemFrame)
        schedulePbCommentItemFrameRefresh(itemFrame)
    }

    private fun schedulePbCommentActivityBackgroundRefresh(activity: Activity) {
        if (!isPbActivity(activity)) return
        synchronized(pbCommentActivityApplyScheduled) {
            if (pbCommentActivityApplyScheduled.containsKey(activity)) return
            pbCommentActivityApplyScheduled[activity] = true
        }
        val anchor = findPbActivityContentHost(activity) ?: activity.window?.decorView
        if (anchor == null) {
            pbCommentActivityApplyScheduled.remove(activity)
            return
        }
        anchor.post {
            pbCommentActivityApplyScheduled.remove(activity)
            applyPbCommentActivityBackgroundSafely(activity)
        }
    }

    private fun applyPbCommentActivityBackgroundSafely(activity: Activity) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        if (!isPbActivity(activity)) return
        try {
            SystemBarCompatHook.applyIfNeeded(activity)
            val host = findPbActivityContentHost(activity) ?: return
            applyPbCommentBackgroundHost(host)
            clearPbCommentHostBackgroundBlockers(host)
            schedulePbReplyBarInputCapsuleDynamicTint(host)
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb comment activity glass failed: ${t.message}" }
            }
        }
    }

    private fun rememberPbCommentSurfaceView(surface: View) {
        val role = if (isPbSubPbLayout(surface)) {
            PbCommentBackgroundWriteRole.SUB_PB_LAYOUT
        } else {
            PbCommentBackgroundWriteRole.CLEAR
        }
        rememberPbCommentBackgroundWriteRole(surface, role)
        installPbCommentSurfaceAttachRefresh(surface)
        schedulePbCommentSurfaceRefresh(surface)
    }

    private fun installPbCommentSurfaceAttachRefresh(surface: View) {
        synchronized(pbCommentSurfaceAttachRefreshInstalled) {
            if (pbCommentSurfaceAttachRefreshInstalled.containsKey(surface)) return
            pbCommentSurfaceAttachRefreshInstalled[surface] = true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                schedulePbCommentSurfaceRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        runCatching {
            surface.addOnAttachStateChangeListener(attachListener)
            if (surface.isAttachedToWindow) {
                schedulePbCommentSurfaceRefresh(surface)
            }
        }.onFailure {
            pbCommentSurfaceAttachRefreshInstalled.remove(surface)
            runCatching { surface.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun schedulePbCommentSurfaceRefresh(surface: View) {
        synchronized(pbCommentSurfaceApplyScheduled) {
            if (pbCommentSurfaceApplyScheduled.containsKey(surface)) return
            pbCommentSurfaceApplyScheduled[surface] = true
        }
        surface.post {
            pbCommentSurfaceApplyScheduled.remove(surface)
            applyPbCommentUnifiedBackgroundSafely(surface)
        }
    }

    private fun installPbCommentItemFrameAttachRefresh(itemFrame: View) {
        synchronized(pbCommentItemFrameAttachRefreshInstalled) {
            if (pbCommentItemFrameAttachRefreshInstalled.containsKey(itemFrame)) return
            pbCommentItemFrameAttachRefreshInstalled[itemFrame] = true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                schedulePbCommentItemFrameRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        runCatching {
            itemFrame.addOnAttachStateChangeListener(attachListener)
            if (itemFrame.isAttachedToWindow) {
                schedulePbCommentItemFrameRefresh(itemFrame)
            }
        }.onFailure {
            pbCommentItemFrameAttachRefreshInstalled.remove(itemFrame)
            runCatching { itemFrame.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun schedulePbCommentItemFrameRefresh(itemFrame: View) {
        synchronized(pbCommentItemFrameApplyScheduled) {
            if (pbCommentItemFrameApplyScheduled.containsKey(itemFrame)) return
            pbCommentItemFrameApplyScheduled[itemFrame] = true
        }
        itemFrame.post {
            pbCommentItemFrameApplyScheduled.remove(itemFrame)
            applyPbCommentItemGlassSafely(itemFrame)
        }
    }

    private fun applyPbCommentItemGlassSafely(itemFrame: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        if (!isPbCommentItemFrame(itemFrame)) return
        applyPbCommentUnifiedBackgroundSafely(itemFrame)
    }

    private fun applyPbCommonPreloadedLayoutTintSafely(view: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(view.context) ?: return
        if (!isPbActivity(activity)) return
        try {
            if (isPbItemRelativeView(view)) {
                setTransparentBackgroundIfNeeded(view)
                clearForeground(view)
                clearElevation(view)
                view.invalidate()
                return
            }
            if (keepPbSortSwitchComponentTransparent(view)) return
            applyPbCommentDynamicTintColor(view, resolveCachedPbCommentDynamicTintColor(view))
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb common preloaded layout tint failed: ${t.message}" }
            }
        }
    }

    private fun rememberPbSubPbLayout(subPbLayout: View) {
        if (!isPbSubPbLayout(subPbLayout)) return
        rememberPbCommentBackgroundWriteRole(subPbLayout, PbCommentBackgroundWriteRole.SUB_PB_LAYOUT)
        installPbSubPbLayoutAttachRefresh(subPbLayout)
        schedulePbSubPbLayoutRefresh(subPbLayout)
    }

    private fun installPbSubPbLayoutAttachRefresh(subPbLayout: View) {
        synchronized(pbSubPbLayoutAttachRefreshInstalled) {
            if (pbSubPbLayoutAttachRefreshInstalled.containsKey(subPbLayout)) return
            pbSubPbLayoutAttachRefreshInstalled[subPbLayout] = true
        }
        val frameState = PbSubPbLayoutFrameState()
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (
                subPbLayout.isAttachedToWindow &&
                subPbLayout.background is CardGlassDrawable &&
                updatePbSubPbLayoutFrameState(subPbLayout, frameState)
            ) {
                invalidatePbSubPbLayoutGlassFrame(subPbLayout)
            }
            true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                schedulePbSubPbLayoutRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                frameState.initialized = false
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    }
                }
            }
        }
        runCatching {
            subPbLayout.addOnAttachStateChangeListener(attachListener)
            if (subPbLayout.isAttachedToWindow) {
                subPbLayout.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                schedulePbSubPbLayoutRefresh(subPbLayout)
            }
        }.onFailure {
            pbSubPbLayoutAttachRefreshInstalled.remove(subPbLayout)
            runCatching { subPbLayout.removeOnAttachStateChangeListener(attachListener) }
            runCatching {
                if (subPbLayout.viewTreeObserver.isAlive) {
                    subPbLayout.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }
        }
    }

    private fun updatePbSubPbLayoutFrameState(
        subPbLayout: View,
        state: PbSubPbLayoutFrameState,
    ): Boolean {
        subPbLayout.getLocationInWindow(state.location)
        val x = state.location[0]
        val y = state.location[1]
        val width = subPbLayout.width
        val height = subPbLayout.height
        val changed = state.initialized && (
            state.x != x ||
                state.y != y ||
                state.width != width ||
                state.height != height
            )
        state.initialized = true
        state.x = x
        state.y = y
        state.width = width
        state.height = height
        return changed
    }

    private fun invalidatePbSubPbLayoutGlassFrame(subPbLayout: View) {
        subPbLayout.invalidate()
    }

    private fun schedulePbSubPbLayoutRefresh(subPbLayout: View) {
        synchronized(pbSubPbLayoutApplyScheduled) {
            if (pbSubPbLayoutApplyScheduled.containsKey(subPbLayout)) return
            pbSubPbLayoutApplyScheduled[subPbLayout] = true
        }
        subPbLayout.post {
            pbSubPbLayoutApplyScheduled.remove(subPbLayout)
            applyPbSubPbLayoutGlassSafely(subPbLayout)
        }
    }

    private fun applyPbSubPbLayoutGlassSafely(subPbLayout: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        if (!isPbSubPbLayout(subPbLayout)) return
        val activity = findCachedActivityFromContext(subPbLayout.context) ?: return
        if (!isPbActivity(activity)) return
        try {
            val host = findPbActivityContentHost(activity) ?: findPbCommentBackgroundHost(subPbLayout) ?: return
            applyPbCommentBackgroundHost(host)
            (subPbLayout.parent as? View)?.let { parent ->
                clearPbCommentBackgroundPath(parent, host)
            }
            applyPbSubPbLayoutCard(subPbLayout, host)
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb sub comment glass failed: ${t.message}" }
            }
        }
    }

    private fun applyPbCommentUnifiedBackgroundSafely(source: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(source.context) ?: return
        if (!isPbActivity(activity) && !isSubPbReplyHostActivity(activity)) return
        try {
            val host = findPbCommentBackgroundHost(source) ?: return
            applyPbCommentBackgroundHost(host)
            clearPbCommentBackgroundPath(source, host)
            clearPbCommentSurfaceBackground(source)
            if (isPbCommentRootSurface(source)) {
                clearPbCommentHostBackgroundBlockers(source)
            }
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb comment glass failed: ${t.message}" }
            }
        }
    }

    private fun schedulePbCommentListItemBackground(itemView: View, parent: ViewGroup?) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        var shouldPost = false
        synchronized(pbCommentListItemApplyScheduled) {
            val pending = pbCommentListItemApplyScheduled[itemView]
            if (pending == null) {
                pbCommentListItemApplyScheduled[itemView] = PendingViewGroupApply(parent)
                shouldPost = true
            } else {
                pending.parentRef = parent?.let(::WeakReference)
            }
        }
        if (!shouldPost) return
        itemView.postOnAnimation {
            val pending = synchronized(pbCommentListItemApplyScheduled) {
                pbCommentListItemApplyScheduled.remove(itemView)
            }
            applyPbCommentListItemBackgroundSafely(itemView, pending?.parentRef?.get())
        }
    }

    private fun applyPbCommentListItemBackgroundSafely(itemView: View, parent: ViewGroup?) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(itemView.context)
            ?: findCachedActivityFromContext(parent?.context)
            ?: return
        if (!isPbActivity(activity)) return
        try {
            val host = findPbActivityContentHost(activity) ?: findPbCommentBackgroundHost(itemView) ?: return
            applyPbCommentBackgroundHost(host)
            clearPbCommentBackgroundPath(itemView, host, keepSortSwitchComponent = false)
            removePbCommentReplyTitleDecorationsSafely(itemView, requireKnownDivider = true)
            clearPbCommentListItemBackgrounds(itemView, depth = 0, keepSortSwitchComponent = false)
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb comment list item glass failed: ${t.message}" }
            }
        }
    }

    private fun scheduleSubPbReplyItemGlass(itemView: View, parent: ViewGroup?) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        var shouldPost = false
        synchronized(subPbReplyItemApplyScheduled) {
            val pending = subPbReplyItemApplyScheduled[itemView]
            if (pending == null) {
                subPbReplyItemApplyScheduled[itemView] = PendingViewGroupApply(parent)
                shouldPost = true
            } else {
                pending.parentRef = parent?.let(::WeakReference)
            }
        }
        if (!shouldPost) return
        itemView.postOnAnimation {
            val pending = synchronized(subPbReplyItemApplyScheduled) {
                subPbReplyItemApplyScheduled.remove(itemView)
            }
            applySubPbReplyItemGlassSafely(itemView, pending?.parentRef?.get())
        }
    }

    private fun applySubPbReplyItemGlassSafely(itemView: View, parent: ViewGroup?) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(itemView.context)
            ?: findCachedActivityFromContext(parent?.context)
            ?: return
        if (!isSubPbReplyHostActivity(activity)) return
        try {
            val host = findPbActivityContentHost(activity) ?: findPbCommentBackgroundHost(itemView) ?: return
            applyPbCommentBackgroundHost(host)
            setTransparentBackgroundIfNeeded(itemView)
            clearForeground(itemView)
            clearNativePressVisual(itemView)
            clearSubPbReplyItemContentBackgrounds(itemView)
            itemView.invalidate()
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG sub pb reply item glass failed: ${t.message}" }
            }
        }
    }

    private fun scheduleSubPbNextPageGlass(listView: ViewGroup) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        listView.post {
            try {
                applySubPbNextPageGlassToList(listView)
            } catch (t: Throwable) {
                if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                    XposedCompat.logD { "$TAG sub pb next page glass failed: ${t.message}" }
                }
            }
        }
    }

    private fun applySubPbNextPageGlassToList(listView: ViewGroup): Boolean {
        val activity = findCachedActivityFromContext(listView.context)
            ?: return false
        if (!isSubPbReplyHostActivity(activity)) return false
        val target = findSubPbNextPageMoreView(listView) ?: return false
        val host = findPbActivityContentHost(activity) ?: findPbCommentBackgroundHost(listView) ?: return false
        applyPbCommentBackgroundHost(host)
        clearSubPbNextPageMoreViewBackground(target)
        listView.invalidate()
        return true
    }

    private fun clearSubPbNextPageMoreViewBackground(target: View) {
        setTransparentBackgroundIfNeeded(target)
        clearNativePressVisual(target)
        target.invalidate()
    }

    private fun applySubPbNextPageMoreViewTransparencyIfNeeded(anchor: View): Boolean {
        val target = findSubPbNextPageMoreViewForRuntimeTint(anchor) ?: return false
        clearSubPbNextPageMoreViewBackground(target)
        return target === anchor
    }

    private fun findSubPbNextPageMoreViewForRuntimeTint(anchor: View): View? {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return null
        val activity = findCachedActivityFromContext(anchor.context) ?: return null
        if (!isSubPbReplyHostActivity(activity)) return null
        val viewId = runtimeTargets?.subPbNextPageMoreViewId ?: return null
        if (viewId <= 0 || viewId == View.NO_ID) return null
        if (anchor.id == viewId) return anchor
        return (anchor as? ViewGroup)?.findViewById(viewId)
    }

    private fun findSubPbNextPageMoreView(root: View): View? {
        val viewId = runtimeTargets?.subPbNextPageMoreViewId ?: return null
        return root.findViewById(viewId)
    }

    private fun clearSubPbReplyItemContentBackgrounds(itemView: View) {
        val group = itemView as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearSubPbReplyItemContentBackground(group.getChildAt(index) ?: continue, depth = 0)
        }
    }

    private fun clearSubPbReplyItemContentBackground(view: View, depth: Int) {
        if (depth > SUB_PB_REPLY_ITEM_CONTENT_CLEAR_DEPTH) return
        setTransparentBackgroundIfNeeded(view)
        clearForeground(view)
        clearElevation(view)
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearSubPbReplyItemContentBackground(group.getChildAt(index) ?: continue, depth + 1)
        }
    }

    private fun findPbCommentBackgroundHost(source: View): View? {
        findPbCommentActivityContentHost(source)?.let { return it }
        var current = source.parent
        var firstSizedParent: View? = null
        while (current is View) {
            if (current is AbsListView || isRecyclerView(current) || isPbCommentRecyclerView(current)) {
                return current
            }
            if (firstSizedParent == null && current.width > 0 && current.height > 0) {
                firstSizedParent = current
            }
            current = current.parent
        }
        return firstSizedParent ?: source.rootView?.takeIf { it.width > 0 && it.height > 0 }
    }

    private fun findPbCommentActivityContentHost(source: View): View? {
        val activity = findCachedActivityFromContext(source.context) ?: return null
        if (!isPbActivity(activity)) return null
        return findPbActivityContentHost(activity)
    }

    private fun findPbActivityContentHost(activity: Activity): View? {
        synchronized(pbActivityContentHosts) {
            val cached = pbActivityContentHosts[activity]?.get()
            if (cached != null) return cached
            pbActivityContentHosts.remove(activity)
        }
        val host = runCatching { activity.findViewById<View>(android.R.id.content) }.getOrNull()
        if (host != null) {
            rememberPbCommentBackgroundWriteRole(host, PbCommentBackgroundWriteRole.HOST)
            pbActivityContentHosts[activity] = WeakReference(host)
        }
        return host
    }

    private fun findCachedActivityFromContext(context: Context?): Activity? {
        context ?: return null
        synchronized(pbActivityByContext) {
            val cached = pbActivityByContext[context]?.get()
            if (cached != null) return cached
            pbActivityByContext.remove(context)
        }
        val activity = ReflectionUtils.findActivityFromContext(context) ?: return null
        pbActivityByContext[context] = WeakReference(activity)
        return activity
    }

    private fun isPbActivity(activity: Activity): Boolean {
        return pbActivityTypeFor(activity).isPb
    }

    private fun isSubPbReplyHostActivity(activity: Activity): Boolean {
        return pbActivityTypeFor(activity).isSubPbReplyHost
    }

    private fun isForumActivity(activity: Activity): Boolean {
        return activity.javaClass.name == StableTiebaHookPoints.FORUM_ACTIVITY_CLASS
    }

    private fun pbActivityTypeFor(activity: Activity): PbActivityType {
        synchronized(pbActivityTypes) {
            pbActivityTypes[activity]?.let { return it }
        }
        val resolved = resolvePbActivityType(activity)
        pbActivityTypes[activity] = resolved
        return resolved
    }

    private fun resolvePbActivityType(activity: Activity): PbActivityType {
        if (activity.javaClass.name == StableTiebaHookPoints.PB_COMMENT_FLOAT_ACTIVITY_CLASS) {
            return PbActivityType(isPb = false, isSubPbReplyHost = false)
        }
        var isPb = false
        var isSubPbReplyHost = false
        var current: Class<*>? = activity.javaClass
        while (current != null && current != Any::class.java) {
            val name = current.name
            if (
                name == StableTiebaHookPoints.PB_ACTIVITY_CLASS ||
                name == StableTiebaHookPoints.PB_ABS_ACTIVITY_CLASS
            ) {
                isPb = true
            }
            if (
                name == StableTiebaHookPoints.NEW_SUB_PB_ACTIVITY_CLASS ||
                name == StableTiebaHookPoints.FOLD_COMMENT_ACTIVITY_CLASS
            ) {
                isSubPbReplyHost = true
            }
            if (isPb && isSubPbReplyHost) break
            current = current.superclass
        }
        return PbActivityType(isPb = isPb, isSubPbReplyHost = isSubPbReplyHost)
    }

    private fun applyPbCommentBackgroundHost(host: View) {
        val style = currentHomeNativeGlassRuntimeStyle()
        val state = PbCommentBackgroundState(
            blurCachePath = style.blurCacheImagePath,
            sourcePath = style.backgroundImagePath,
            tintAlphaPercent = style.tintAlphaPercent,
        )
        if (
            pbCommentBackgroundHostStates[host] == state &&
            host.background is PbCommentGlassBackgroundDrawable
        ) {
            return
        }
        val request = backgroundRequestForStyle(
            style,
            BACKGROUND_CACHE_SAMPLE_EDGE,
            BACKGROUND_CACHE_SAMPLE_EDGE,
        )
        val cachedEntry = request?.let { findCachedBackgroundEntry(it) }
        if (cachedEntry == null) {
            applyPbCommentFallbackBackgroundHost(host, state)
            request?.let {
                scheduleBackgroundDecode(it, host) { target -> applyPbCommentBackgroundHost(target) }
            }
            return
        }
        val bitmap = cachedEntry.blurredBitmap ?: run {
            applyPbCommentFallbackBackgroundHost(host, state)
            return
        }
        host.background = PbCommentGlassBackgroundDrawable(
            bitmap = bitmap,
            tintAlphaPercent = state.tintAlphaPercent,
        )
        if (host is AbsListView) {
            host.cacheColorHint = Color.TRANSPARENT
        }
        clearForeground(host)
        clearElevation(host)
        pbCommentBackgroundHostStates[host] = state
        host.invalidate()
    }

    private fun applyPbCommentFallbackBackgroundHost(host: View, state: PbCommentBackgroundState) {
        val color = resolveCachedPbCommentDynamicTintColor(host)
        if ((host.background as? ColorDrawable)?.color != color) {
            setBackgroundColorPreservingPadding(host, color)
        }
        if (host is AbsListView) {
            host.cacheColorHint = Color.TRANSPARENT
        }
        clearForeground(host)
        clearElevation(host)
        pbCommentBackgroundHostStates[host] = state
    }

    private fun applyPbSubPbLayoutCard(subPbLayout: View, host: View) {
        val style = currentHomeNativeGlassRuntimeStyle()
        val radiusDp = effectiveCardRadiusDp()
        val state = PbSubPbLayoutCardState(
            host = host,
            blurCachePath = style.blurCacheImagePath,
            sourcePath = style.backgroundImagePath,
            tintAlphaPercent = style.tintAlphaPercent,
            cardBlurPercent = style.cardBlurPercent,
            cardRadiusDp = radiusDp,
            strokeEnabled = style.strokeEnabled,
            shadowStrengthPercent = style.shadowStrengthPercent,
        )
        val shouldUpdateBackground = pbSubPbLayoutCardStates[subPbLayout] != state ||
            subPbLayout.background !is CardGlassDrawable
        val radius = subPbLayout.resources.displayMetrics.density * radiusDp
        if (shouldUpdateBackground) {
            setGlassBackground(subPbLayout, host, radius, childSurfaceEnabled = true)
            pbSubPbLayoutCardStates[subPbLayout] = state
            disablePbSubPbLayoutScrollCaches(subPbLayout)
            applyPbSubPbLayoutShadow(subPbLayout, radius)
            subPbLayout.invalidate()
        }
        clearForeground(subPbLayout)
        applyPbSubPbLayoutExtraHeight(subPbLayout)
        clearPbSubPbLayoutContentBackgrounds(subPbLayout)
    }

    private fun applyPbSubPbLayoutExtraHeight(subPbLayout: View) {
        val extraPadding = (subPbLayout.resources.displayMetrics.density * PB_SUB_PB_EXTRA_VERTICAL_PADDING_DP).toInt()
        if (extraPadding <= 0) return
        val basePadding = pbSubPbLayoutPaddingStates.getOrPut(subPbLayout) {
            PbSubPbLayoutPaddingState(
                left = subPbLayout.paddingLeft,
                top = subPbLayout.paddingTop,
                right = subPbLayout.paddingRight,
                bottom = subPbLayout.paddingBottom,
            )
        }
        val targetTop = basePadding.top + extraPadding
        val targetBottom = basePadding.bottom + extraPadding
        if (
            subPbLayout.paddingLeft == basePadding.left &&
            subPbLayout.paddingTop == targetTop &&
            subPbLayout.paddingRight == basePadding.right &&
            subPbLayout.paddingBottom == targetBottom
        ) {
            return
        }
        subPbLayout.setPadding(
            basePadding.left,
            targetTop,
            basePadding.right,
            targetBottom,
        )
    }

    @Suppress("DEPRECATION")
    private fun disablePbSubPbLayoutScrollCaches(subPbLayout: View) {
        subPbLayout.setWillNotCacheDrawing(true)
        subPbLayout.isDrawingCacheEnabled = false
        var current = subPbLayout.parent
        var depth = 0
        while (current is View && depth < PB_SUB_PB_SCROLL_CACHE_PARENT_DEPTH) {
            current.setWillNotCacheDrawing(true)
            current.isDrawingCacheEnabled = false
            if (current is AbsListView) {
                current.isScrollingCacheEnabled = false
                current.cacheColorHint = Color.TRANSPARENT
            }
            current = current.parent
            depth++
        }
    }

    private fun clearPbSubPbLayoutContentBackgrounds(subPbLayout: View) {
        val group = subPbLayout as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbSubPbLayoutContentBackground(
                group.getChildAt(index) ?: continue,
                depth = 0,
            )
        }
    }

    private fun clearPbSubPbLayoutContentBackground(
        view: View,
        depth: Int,
    ) {
        if (depth > PB_SUB_PB_CONTENT_CLEAR_DEPTH) return
        if (isPbSubPbLayout(view) || shouldKeepPbCommentBackgroundSubtree(view)) return
        if (isPbSubPbLayoutContentContainer(view)) {
            clearNativePressVisual(view)
            setTransparentBackgroundIfNeeded(view)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbSubPbLayoutContentBackground(
                group.getChildAt(index) ?: continue,
                depth + 1,
            )
        }
    }

    private fun isPbSubPbLayoutContentContainer(view: View): Boolean {
        val name = view.javaClass.name
        return name == "android.widget.FrameLayout" ||
            name == "android.widget.RelativeLayout" ||
            name == "android.widget.LinearLayout" ||
            name.contains("ConstraintLayout")
    }

    private fun applyPbSubPbLayoutShadow(subPbLayout: View, radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val density = subPbLayout.resources.displayMetrics.density
        val shadowStrength = currentHomeNativeGlassRuntimeStyle().shadowStrengthPercent.coerceIn(
            ConfigManager.MIN_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
            ConfigManager.MAX_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        )
        if (shadowStrength > 0) {
            allowPbSubPbLayoutShadowOverflow(subPbLayout)
        }
        subPbLayout.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) return
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        subPbLayout.clipToOutline = true
        subPbLayout.stateListAnimator = null
        if (shadowStrength > 0) {
            val shadowScale = shadowStrength / 100f
            subPbLayout.elevation = density * PB_SUB_PB_SHADOW_ELEVATION_DP * shadowScale
            subPbLayout.translationZ = density * PB_SUB_PB_SHADOW_TRANSLATION_Z_DP * shadowScale
        } else {
            subPbLayout.elevation = 0f
            subPbLayout.translationZ = 0f
        }
        subPbLayout.invalidateOutline()
    }

    private fun allowPbSubPbLayoutShadowOverflow(subPbLayout: View) {
        var current = subPbLayout.parent
        var depth = 0
        while (current is ViewGroup && depth < PB_SUB_PB_SHADOW_PARENT_DEPTH) {
            current.clipChildren = false
            current.clipToPadding = false
            current = current.parent
            depth++
        }
    }

    private fun interceptPbCommentNativeBackgroundWrite(view: View): Boolean {
        if (!ConfigManager.isHomeNativeGlassEnabled) return false
        if (!hasPageBackgroundOverride()) return false
        val activity = findCachedActivityFromContext(view.context) ?: return false
        if (!isPbActivity(activity) && !isSubPbReplyHostActivity(activity)) return false

        val host = findPbActivityContentHost(activity)
        if (host === view) {
            applyPbCommentBackgroundHost(view)
            return true
        }

        return when (cachedPbCommentBackgroundWriteRole(view)) {
            PbCommentBackgroundWriteRole.CLEAR -> {
                host?.let { applyPbCommentBackgroundHost(it) }
                clearPbCommentNativeBackgroundWriteTarget(view)
                true
            }
            PbCommentBackgroundWriteRole.SORT_SWITCH -> {
                keepPbSortSwitchComponentTransparent(view)
                true
            }
            PbCommentBackgroundWriteRole.SUB_PB_LAYOUT -> {
                applyPbSubPbLayoutGlassSafely(view)
                true
            }
            PbCommentBackgroundWriteRole.HOST -> {
                applyPbCommentBackgroundHost(view)
                true
            }
            PbCommentBackgroundWriteRole.KEEP,
            PbCommentBackgroundWriteRole.IGNORE -> false
        }
    }

    private fun clearPbCommentNativeBackgroundWriteTarget(view: View) {
        clearNativePressVisual(view)
        clearForeground(view)
        clearElevation(view)
        setTransparentBackgroundIfNeeded(view)
        if (view is AbsListView) {
            view.cacheColorHint = Color.TRANSPARENT
        }
        if (
            isPbCommentItemFrame(view) ||
            isPbItemRelativeView(view) ||
            view.javaClass.name in PB_COMMENT_ITEM_SURFACE_CLASSES
        ) {
            clearNestedRecyclerBackgrounds(view)
        }
        view.invalidate()
    }

    private fun rememberPbCommentBackgroundWriteRole(
        view: View,
        role: PbCommentBackgroundWriteRole,
    ) {
        if (role == PbCommentBackgroundWriteRole.IGNORE) return
        synchronized(pbCommentBackgroundWriteRoles) {
            val current = pbCommentBackgroundWriteRoles[view]
            if (current == null || pbCommentBackgroundWriteRolePriority(role) > pbCommentBackgroundWriteRolePriority(current)) {
                pbCommentBackgroundWriteRoles[view] = role
            }
        }
    }

    private fun cachedPbCommentBackgroundWriteRole(view: View): PbCommentBackgroundWriteRole {
        synchronized(pbCommentBackgroundWriteRoles) {
            pbCommentBackgroundWriteRoles[view]?.let { return it }
        }
        val role = resolvePbCommentBackgroundWriteRole(view)
        if (role != PbCommentBackgroundWriteRole.IGNORE) {
            rememberPbCommentBackgroundWriteRole(view, role)
        }
        return role
    }

    private fun resolvePbCommentBackgroundWriteRole(view: View): PbCommentBackgroundWriteRole {
        if (shouldKeepPbCommentBackground(view)) return PbCommentBackgroundWriteRole.KEEP
        if (isPbSubPbLayout(view)) return PbCommentBackgroundWriteRole.SUB_PB_LAYOUT
        if (isPbSortSwitchComponent(view)) return PbCommentBackgroundWriteRole.SORT_SWITCH
        if (
            isPbCommentItemFrame(view) ||
            isPbItemRelativeView(view) ||
            view.javaClass.name in PB_COMMENT_SURFACE_CLASSES ||
            view.javaClass.name in PB_COMMENT_ITEM_SURFACE_CLASSES ||
            view.javaClass.name.contains("PageBrowserRecyclerView") ||
            view.javaClass.name.contains("CommentRecyclerView") ||
            view.javaClass.name.contains("ParentRecyclerView") ||
            view.javaClass.name.contains("ChildRecyclerView") ||
            view is AbsListView
        ) {
            return PbCommentBackgroundWriteRole.CLEAR
        }
        if (
            (
                isRecyclerView(view) ||
                    view.javaClass.name == "android.widget.FrameLayout" ||
                    view.javaClass.name == "android.widget.RelativeLayout" ||
                    view.javaClass.name == "android.widget.LinearLayout"
                ) &&
            hasPbCommentBackgroundWriteAncestor(view)
        ) {
            return PbCommentBackgroundWriteRole.CLEAR
        }
        return PbCommentBackgroundWriteRole.IGNORE
    }

    private fun hasPbCommentBackgroundWriteAncestor(view: View): Boolean {
        var current = view.parent as? View
        var depth = 0
        while (current != null && depth < PB_COMMENT_BACKGROUND_WRITE_PARENT_SCAN_DEPTH) {
            synchronized(pbCommentBackgroundWriteRoles) {
                when (pbCommentBackgroundWriteRoles[current]) {
                    PbCommentBackgroundWriteRole.CLEAR,
                    PbCommentBackgroundWriteRole.SUB_PB_LAYOUT,
                    PbCommentBackgroundWriteRole.HOST -> return true
                    PbCommentBackgroundWriteRole.KEEP -> return false
                    else -> Unit
                }
            }
            if (
                isPbCommentItemFrame(current) ||
                isPbItemRelativeView(current) ||
                isPbSubPbLayout(current) ||
                current.javaClass.name in PB_COMMENT_SURFACE_CLASSES ||
                current.javaClass.name in PB_COMMENT_ITEM_SURFACE_CLASSES
            ) {
                return true
            }
            if (shouldKeepPbCommentBackground(current)) return false
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun pbCommentBackgroundWriteRolePriority(role: PbCommentBackgroundWriteRole): Int {
        return when (role) {
            PbCommentBackgroundWriteRole.IGNORE -> 0
            PbCommentBackgroundWriteRole.CLEAR -> 1
            PbCommentBackgroundWriteRole.SORT_SWITCH -> 2
            PbCommentBackgroundWriteRole.SUB_PB_LAYOUT -> 3
            PbCommentBackgroundWriteRole.HOST -> 4
            PbCommentBackgroundWriteRole.KEEP -> 5
        }
    }

    private fun clearPbCommentBackgroundPath(
        source: View,
        host: View,
        keepSortSwitchComponent: Boolean = true,
    ) {
        var current: View? = source
        var depth = 0
        while (current != null && current !== host && depth < PB_COMMENT_BACKGROUND_PATH_CLEAR_DEPTH) {
            if (keepPbSortSwitchComponentTransparent(current, keepSortSwitchComponent)) break
            clearPbCommentSurfaceBackground(current, keepSortSwitchComponent)
            current = current.parent as? View
            depth++
        }
    }

    private fun clearPbCommentHostBackgroundBlockers(host: View) {
        val group = host as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbCommentHostBackgroundBlocker(group.getChildAt(index) ?: continue, depth = 0)
        }
    }

    private fun clearPbCommentHostBackgroundBlocker(view: View, depth: Int): Boolean {
        if (isPbSubPbLayout(view)) {
            applyPbSubPbLayoutGlassSafely(view)
            return false
        }
        if (keepPbSortSwitchComponentTransparent(view)) return false
        if (shouldKeepPbCommentBackground(view)) return false
        val isSurface = isPbCommentBackgroundContainer(view) || view is AbsListView || isRecyclerView(view)
        if (isSurface || depth <= PB_COMMENT_TOP_CONTAINER_CLEAR_DEPTH) {
            clearPbCommentSurfaceBackground(view)
        }
        val group = view as? ViewGroup ?: return isSurface
        if (depth >= PB_COMMENT_HOST_CLEAR_DEPTH && !isSurface) return isSurface
        var containsSurface = isSurface
        for (index in 0 until group.childCount) {
            if (clearPbCommentHostBackgroundBlocker(group.getChildAt(index) ?: continue, depth + 1)) {
                containsSurface = true
            }
        }
        if (containsSurface) {
            clearPbCommentSurfaceBackground(view)
        }
        return containsSurface
    }

    private fun clearPbCommentSurfaceBackground(view: View, keepSortSwitchComponent: Boolean = true) {
        if (isPbSubPbLayout(view)) {
            applyPbSubPbLayoutGlassSafely(view)
            return
        }
        if (keepPbSortSwitchComponentTransparent(view, keepSortSwitchComponent)) return
        if (shouldKeepPbCommentBackground(view)) return
        if (
            isPbCommentBackgroundContainer(view) ||
            view is AbsListView ||
            isRecyclerView(view) ||
            isPbCommentItemFrame(view)
        ) {
            clearNativePressVisual(view)
            setTransparentBackgroundIfNeeded(view)
            if (view is AbsListView) {
                view.cacheColorHint = Color.TRANSPARENT
            }
            if (isPbCommentItemFrame(view) || view.javaClass.name in PB_COMMENT_ITEM_SURFACE_CLASSES) {
                clearNestedRecyclerBackgrounds(view)
            }
        }
    }

    private fun clearPbCommentListItemBackgrounds(
        view: View,
        depth: Int,
        keepSortSwitchComponent: Boolean,
    ) {
        if (depth > PB_COMMENT_LIST_ITEM_CLEAR_DEPTH) return
        if (isPbSubPbLayout(view)) {
            applyPbSubPbLayoutGlassSafely(view)
            return
        }
        if (shouldKeepPbCommentBackgroundSubtree(view, keepSortSwitchComponent)) return
        if (shouldKeepPbCommentBackground(view)) return
        if (
            isPbCommentBackgroundContainer(view) ||
            view is AbsListView ||
            isRecyclerView(view)
        ) {
            clearPbCommentSurfaceBackground(view, keepSortSwitchComponent)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbCommentListItemBackgrounds(
                group.getChildAt(index) ?: continue,
                depth + 1,
                keepSortSwitchComponent,
            )
        }
    }

    private fun removePbCommentReplyTitleDecorationsSafely(root: View, requireKnownDivider: Boolean) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(root.context) ?: return
        if (!isPbActivity(activity)) return
        try {
            val knownDecorations = findPbReplyTitleKnownDecorations(root)
            if (knownDecorations.isEmpty() && requireKnownDivider) return
            for (decoration in knownDecorations) {
                collapsePbCommentDecorativeView(decoration)
            }
            val maxHeightPx = max(
                PB_REPLY_TITLE_DECORATIVE_MIN_HEIGHT_PX,
                (root.resources.displayMetrics.density * PB_REPLY_TITLE_DECORATIVE_MAX_HEIGHT_DP).toInt(),
            )
            clearPbReplyTitleThinDecorativeViews(root, root, maxHeightPx, depth = 0)
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb reply title decoration removal failed: ${t.message}" }
            }
        }
    }

    private fun rememberPbReplyTitleViewHolderRoot(root: View) {
        refreshPbReplyTitleDynamicTintSafely(root)
        schedulePbReplyTitleDynamicTint(root)
    }

    private fun schedulePbReplyTitleDynamicTint(root: View) {
        var shouldPost = false
        synchronized(pbReplyTitleDynamicTintScheduled) {
            if (!pbReplyTitleDynamicTintScheduled.containsKey(root)) {
                pbReplyTitleDynamicTintScheduled[root] = true
                shouldPost = true
            }
        }
        if (!shouldPost) return
        root.post {
            synchronized(pbReplyTitleDynamicTintScheduled) {
                pbReplyTitleDynamicTintScheduled.remove(root)
            }
            refreshPbReplyTitleDynamicTintSafely(root)
        }
    }

    private fun refreshPbReplyTitleDynamicTintSafely(root: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(root.context) ?: return
        if (!isPbActivity(activity)) return
        try {
            removePbCommentReplyTitleDecorationsSafely(root, requireKnownDivider = false)
            val maxHeightPx = max(
                PB_REPLY_TITLE_DECORATIVE_MIN_HEIGHT_PX,
                (root.resources.displayMetrics.density * PB_REPLY_TITLE_DECORATIVE_MAX_HEIGHT_DP).toInt(),
            )
            clearPbReplyTitleBackgrounds(root, root, maxHeightPx, depth = 0)
            findPbSortSwitchButton(root, depth = 0)?.let { sortButton ->
                applyPbSortSwitchBackgroundDynamicTint(sortButton, invalidateOnChange = true)
            }
        } catch (t: Throwable) {
            if (firstPbCommentErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb reply title dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun clearPbReplyTitleBackgrounds(
        root: View,
        view: View,
        maxHeightPx: Int,
        depth: Int,
    ) {
        if (depth > PB_REPLY_TITLE_DYNAMIC_TINT_DEPTH) return
        if (view === root || isPbReplyTitleTransparentBackgroundSurface(view, maxHeightPx)) {
            clearNativePressVisual(view)
            setTransparentBackgroundIfNeeded(view)
        }
        if (isPbSortSwitchButton(view)) return
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbReplyTitleBackgrounds(
                root,
                group.getChildAt(index) ?: continue,
                maxHeightPx,
                depth + 1,
            )
        }
    }

    private fun isPbReplyTitleTransparentBackgroundSurface(view: View, maxHeightPx: Int): Boolean {
        if (isPbSortSwitchButton(view)) return false
        val name = view.javaClass.name
        if (name == "android.view.View") {
            return !view.isClickable &&
                !view.isLongClickable &&
                !view.isFocusable &&
                !view.hasOnClickListeners() &&
                !isPbReplyTitleDecorativeView(view, maxHeightPx)
        }
        if (!isPbReplyTitleSimpleContainer(view)) return false
        if (containsPbSortSwitchButton(view, depth = 0)) return true
        return view.background != null &&
            !view.isClickable &&
            !view.isLongClickable &&
            !view.isFocusable &&
            !view.hasOnClickListeners()
    }

    private fun isPbReplyTitleSimpleContainer(view: View): Boolean {
        val name = view.javaClass.name
        return name == "android.widget.LinearLayout" ||
            name == "android.widget.RelativeLayout" ||
            name == "android.widget.FrameLayout"
    }

    private fun findPbReplyTitleKnownDecorations(root: View): List<View> {
        val decorations = ArrayList<View>(1)
        findViewByResolvedId(root, resolvePbReplyTitleDividerViewId())?.let { divider ->
            if (!decorations.contains(divider)) {
                decorations.add(divider)
            }
        }
        return decorations
    }

    private fun resolvePbReplyTitleDividerViewId(): Int {
        return runtimeTargets?.pbReplyTitleDividerViewId ?: 0
    }

    private fun clearPbReplyTitleThinDecorativeViews(
        root: View,
        view: View,
        maxHeightPx: Int,
        depth: Int,
    ) {
        if (depth > PB_REPLY_TITLE_DECORATIVE_CLEAR_DEPTH) return
        if (view !== root && isPbReplyTitleDecorativeView(view, maxHeightPx)) {
            collapsePbCommentDecorativeView(view)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearPbReplyTitleThinDecorativeViews(root, group.getChildAt(index) ?: continue, maxHeightPx, depth + 1)
        }
    }

    private fun isPbReplyTitleDecorativeView(view: View, maxHeightPx: Int): Boolean {
        if (view.javaClass.name != "android.view.View") return false
        if (view.isClickable || view.isLongClickable || view.isFocusable || view.hasOnClickListeners()) {
            return false
        }
        val layoutHeight = view.layoutParams?.height ?: 0
        if (layoutHeight == ViewGroup.LayoutParams.MATCH_PARENT || layoutHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            return view.height in 1..maxHeightPx || view.measuredHeight in 1..maxHeightPx
        }
        return layoutHeight in 0..maxHeightPx ||
            view.height in 1..maxHeightPx ||
            view.measuredHeight in 1..maxHeightPx
    }

    private fun collapsePbCommentDecorativeView(view: View) {
        clearNativePressVisual(view)
        setTransparentBackgroundIfNeeded(view)
        view.minimumHeight = 0
        val layoutParams = view.layoutParams
        if (layoutParams != null && layoutParams.height != 0) {
            layoutParams.height = 0
            view.layoutParams = layoutParams
        }
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
        }
        view.requestLayout()
    }

    private fun keepPbSortSwitchComponentTransparent(
        view: View,
        keepSortSwitchComponent: Boolean = true,
    ): Boolean {
        if (!keepSortSwitchComponent) return false
        if (isPbSortSwitchButton(view)) {
            applyPbSortSwitchBackgroundDynamicTint(view, invalidateOnChange = false)
            return true
        }
        if (!isPbSortSwitchComponent(view)) return false
        val sortButton = findAnyPbSortSwitchButton(view, depth = 0)
        val pinnedReplyTitle = sortButton?.let { isPbSortSwitchPinnedReplyTitle(it) }
            ?: isPbSortSwitchPinnedReplyTitle(view)
        if (pinnedReplyTitle) {
            applyPbCommentDynamicTintColor(view, resolveCachedPbCommentDynamicTintColor(view))
        } else {
            setTransparentBackgroundPreservingPaddingIfNeeded(view)
            clearForeground(view)
            clearElevation(view)
        }
        sortButton?.let { sortButton ->
            applyPbSortSwitchBackgroundDynamicTint(sortButton, invalidateOnChange = true)
        }
        return true
    }

    private fun applyPbCommentDynamicTintColor(view: View, color: Int) {
        if ((view.background as? ColorDrawable)?.color != color) {
            setBackgroundColorPreservingPadding(view, color)
        }
        clearForeground(view)
        clearElevation(view)
    }

    private fun setChromeDynamicTintBackgroundColor(view: View, color: Int) {
        val depth = chromeDynamicTintBackgroundWriteDepth.get() ?: 0
        chromeDynamicTintBackgroundWriteDepth.set(depth + 1)
        try {
            setBackgroundColorPreservingPadding(view, color)
        } finally {
            if (depth <= 0) {
                chromeDynamicTintBackgroundWriteDepth.remove()
            } else {
                chromeDynamicTintBackgroundWriteDepth.set(depth)
            }
        }
    }

    private fun isChromeDynamicTintBackgroundWriteInProgress(): Boolean {
        return (chromeDynamicTintBackgroundWriteDepth.get() ?: 0) > 0
    }

    private fun scheduleSubPbNavigationBarTint(navigationBar: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        if (!isSubPbNavigationBar(navigationBar)) return
        val activity = findCachedActivityFromContext(navigationBar.context) ?: return
        if (!isSubPbReplyHostActivity(activity)) return

        var shouldPost = false
        synchronized(subPbNavigationBarApplyScheduled) {
            if (!subPbNavigationBarApplyScheduled.containsKey(navigationBar)) {
                subPbNavigationBarApplyScheduled[navigationBar] = true
                shouldPost = true
            }
        }
        if (!shouldPost) return

        navigationBar.post {
            synchronized(subPbNavigationBarApplyScheduled) {
                subPbNavigationBarApplyScheduled.remove(navigationBar)
            }
            applySubPbNavigationBarTintSafely(navigationBar)
        }
        navigationBar.postOnAnimation {
            applySubPbNavigationBarTintSafely(navigationBar)
        }
        navigationBar.postDelayed({
            applySubPbNavigationBarTintSafely(navigationBar)
        }, SUB_PB_NAVIGATION_BAR_REAPPLY_DELAY_MS)
    }

    private fun applySubPbNavigationBarTintForBackgroundWrite(view: View): Boolean {
        if (!isSubPbNavigationBar(view)) return false
        return applySubPbNavigationBarTintSafely(view)
    }

    private fun applySubPbNavigationBarTintSafely(navigationBar: View): Boolean {
        return try {
            applySubPbNavigationBarTint(navigationBar)
        } catch (t: Throwable) {
            if (firstSubPbNavigationBarTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG sub pb navigation bar dynamic tint failed: ${t.message}" }
            }
            false
        }
    }

    private fun applySubPbNavigationBarTint(navigationBar: View): Boolean {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return false
        if (!isSubPbNavigationBar(navigationBar)) return false
        val activity = findCachedActivityFromContext(navigationBar.context) ?: return false
        if (!isSubPbReplyHostActivity(activity)) return false

        findPbActivityContentHost(activity)?.let { host ->
            applyPbCommentBackgroundHost(host)
            clearPbCommentHostBackgroundBlockers(host)
        }
        applyPbCommentDynamicTintColor(navigationBar, resolveCachedPbCommentDynamicTintColor(navigationBar))
        clearSubPbNavigationBarChromeBackgrounds(navigationBar)
        navigationBar.invalidate()
        return true
    }

    private fun clearSubPbNavigationBarChromeBackgrounds(navigationBar: View) {
        val group = navigationBar as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearSubPbNavigationBarChromeBackground(group.getChildAt(index) ?: continue, depth = 0)
        }
    }

    private fun clearSubPbNavigationBarChromeBackground(view: View, depth: Int) {
        if (depth > SUB_PB_NAVIGATION_BAR_BACKGROUND_CLEAR_DEPTH) return
        setTransparentBackgroundPreservingPaddingIfNeeded(view)
        clearForeground(view)
        clearElevation(view)
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearSubPbNavigationBarChromeBackground(group.getChildAt(index) ?: continue, depth + 1)
        }
    }

    private fun scheduleSubPbInputBarDynamicTint(
        anchor: View,
        reapplyAfterDelay: Boolean = true,
    ) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(anchor.context) ?: return
        if (!isSubPbReplyHostActivity(activity)) return
        val root = findSubPbViewRoot(anchor) ?: return

        var shouldPost = false
        synchronized(subPbInputBarApplyScheduled) {
            if (!subPbInputBarApplyScheduled.containsKey(root)) {
                subPbInputBarApplyScheduled[root] = true
                shouldPost = true
            }
        }
        if (!shouldPost) return

        root.post {
            applySubPbInputBarDynamicTintSafely(root)
        }
        root.postOnAnimation {
            try {
                applySubPbInputBarDynamicTintSafely(root)
            } finally {
                synchronized(subPbInputBarApplyScheduled) {
                    subPbInputBarApplyScheduled.remove(root)
                }
            }
        }
        if (!reapplyAfterDelay) {
            return
        }
        root.postDelayed({
            applySubPbInputBarDynamicTintSafely(root)
        }, SUB_PB_INPUT_BAR_REAPPLY_DELAY_MS)
    }

    private fun applySubPbInputBarDynamicTintSafely(anchor: View) {
        try {
            applySubPbInputBarDynamicTint(anchor)
        } catch (t: Throwable) {
            if (firstSubPbInputBarDynamicTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG sub pb input bar dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun applySubPbInputBarDynamicTint(anchor: View) {
        val match = findSubPbInputBarMatch(anchor) ?: return
        val frameColor = resolvePbCachedDynamicTintColor(match.capsule) ?: return
        applySubPbInputFrameBackgrounds(match, frameColor)
        val capsuleColor = offsetSubPbInputCapsuleColor(frameColor)
        setSubPbInputCapsuleDynamicBackground(match.capsule, capsuleColor)
    }

    private fun applySubPbInputBarFrameDynamicTintIfNeeded(anchor: View): Boolean {
        val match = findSubPbInputBarMatch(anchor) ?: return false
        if (!isSubPbInputFrameAnchor(anchor, match)) return false
        val color = resolvePbCachedDynamicTintColor(match.capsule) ?: return false
        setSubPbInputBarFrameDynamicBackground(anchor, color)
        return true
    }

    private fun findSubPbInputBarMatch(anchor: View): SubPbInputBarMatch? {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return null
        val activity = findCachedActivityFromContext(anchor.context) ?: return null
        if (!isSubPbReplyHostActivity(activity)) return null
        val root = findSubPbViewRoot(anchor) ?: return null
        val capsule = findSubPbInputCapsule(root) ?: return null
        return SubPbInputBarMatch(
            root = root,
            capsule = capsule,
            container = findSubPbInputBarContainer(root, capsule),
        )
    }

    private fun findSubPbViewRoot(anchor: View): ViewGroup? {
        var current: View? = anchor
        var depth = 0
        while (current != null && depth <= SUB_PB_INPUT_ROOT_PARENT_SCAN_DEPTH) {
            if (current.javaClass.name == StableTiebaHookPoints.SUB_PB_VIEW_CLASS) {
                return current as? ViewGroup
            }
            current = current.parent as? View
            depth++
        }
        return null
    }

    private fun findSubPbInputCapsule(root: ViewGroup): View? {
        var best: View? = null
        var bestScore = 0

        fun scan(view: View, depth: Int) {
            if (depth > SUB_PB_INPUT_CAPSULE_SCAN_DEPTH) return
            if (isSubPbInputCapsuleCandidate(view)) {
                val score = scoreSubPbInputCapsule(root, view)
                if (score > bestScore) {
                    best = view
                    bestScore = score
                }
            }
            val group = view as? ViewGroup ?: return
            for (index in 0 until group.childCount) {
                scan(group.getChildAt(index) ?: continue, depth + 1)
            }
        }

        scan(root, depth = 0)
        return best?.takeIf { bestScore >= SUB_PB_INPUT_CAPSULE_MIN_SCORE }
    }

    private fun isSubPbInputCapsuleCandidate(view: View): Boolean {
        val layout = view as? LinearLayout ?: return false
        if (layout.orientation != LinearLayout.HORIZONTAL) return false
        var hasText = false
        var hasImage = false
        for (index in 0 until layout.childCount) {
            when (layout.getChildAt(index)) {
                is TextView -> hasText = true
                is ImageView -> hasImage = true
            }
        }
        return hasText && hasImage
    }

    private fun scoreSubPbInputCapsule(root: ViewGroup, capsule: View): Int {
        var score = 3
        if (capsule.background != null) score += 2
        if (hasNonBlankDirectTextChild(capsule)) score += 2
        if (hasBottomAlignedAncestor(capsule, root)) score += 3
        if (isNearRootBottom(root, capsule)) score += 3
        if (capsule.parent is RelativeLayout) score += 1
        if (isWideLayout(capsule)) score += 1
        return score
    }

    private fun hasNonBlankDirectTextChild(view: View): Boolean {
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            val textView = group.getChildAt(index) as? TextView ?: continue
            if (textView.text?.isNotBlank() == true) return true
        }
        return false
    }

    private fun findSubPbInputBarContainer(root: ViewGroup, capsule: View): View? {
        var best: View? = null
        var current = capsule.parent as? View
        var depth = 0
        while (current != null && current !== root && depth < SUB_PB_INPUT_FRAME_PARENT_CLEAR_DEPTH) {
            if (isSubPbInputFrameContainer(current, root, capsule)) {
                best = current
            }
            current = current.parent as? View
            depth++
        }
        return best?.takeIf { it !== root }
    }

    private fun applySubPbInputFrameBackgrounds(match: SubPbInputBarMatch, color: Int) {
        match.container?.let { container ->
            setSubPbInputBarFrameDynamicBackground(container, color)
        }
        var current = match.capsule.parent as? View
        var depth = 0
        while (current != null && current !== match.root && depth < SUB_PB_INPUT_FRAME_PARENT_CLEAR_DEPTH) {
            if (isSubPbInputFrameContainer(current, match.root, match.capsule)) {
                setSubPbInputBarFrameDynamicBackground(current, color)
            }
            current = current.parent as? View
            depth++
        }
    }

    private fun setSubPbInputBarFrameDynamicBackground(view: View, color: Int) {
        clearNativePressVisual(view)
        setBackgroundColorPreservingPadding(view, color)
        view.invalidate()
    }

    private fun isSubPbInputFrameAnchor(anchor: View, match: SubPbInputBarMatch): Boolean {
        if (anchor === match.root || anchor === match.capsule) return false
        match.container?.let { container ->
            if (anchor === container) return true
            if (anchor.parent === container && isPlainSubPbInputFrameView(anchor)) return true
        }
        return isSubPbInputFrameContainer(anchor, match.root, match.capsule)
    }

    private fun isPlainSubPbInputFrameView(view: View): Boolean {
        return view.javaClass.name == "android.view.View" &&
            !view.isClickable &&
            !view.isLongClickable &&
            !view.isFocusable &&
            !view.hasOnClickListeners()
    }

    private fun isSubPbInputFrameContainer(
        view: View,
        root: ViewGroup,
        capsule: View,
    ): Boolean {
        if (view === root || view === capsule) return false
        if (!isAncestorOf(view, capsule)) return false
        val name = view.javaClass.name
        if (
            name != "android.widget.RelativeLayout" &&
            name != "android.widget.FrameLayout" &&
            name != "android.widget.LinearLayout"
        ) {
            return false
        }
        return hasBottomAlignedAncestor(view, root) ||
            isCompactSubPbInputFrame(view) ||
            isNearRootBottom(root, view)
    }

    private fun hasBottomAlignedAncestor(view: View, root: ViewGroup): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && current !== root && depth < SUB_PB_INPUT_FRAME_PARENT_CLEAR_DEPTH) {
            val params = current.layoutParams
            if (params is RelativeLayout.LayoutParams) {
                val rules = params.getRules()
                if (rules.getOrNull(RelativeLayout.ALIGN_PARENT_BOTTOM) == RelativeLayout.TRUE) {
                    return true
                }
            }
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun isCompactSubPbInputFrame(view: View): Boolean {
        val maxHeight = (view.resources.displayMetrics.density * SUB_PB_INPUT_FRAME_MAX_HEIGHT_DP).toInt()
        val layoutHeight = view.layoutParams?.height ?: 0
        if (layoutHeight in 1..maxHeight) return true
        val actualHeight = max(view.height, view.measuredHeight)
        return actualHeight in 1..maxHeight
    }

    private fun isNearRootBottom(root: ViewGroup, view: View): Boolean {
        val rootHeight = root.height
        val viewHeight = max(view.height, view.measuredHeight)
        if (rootHeight <= 0 || viewHeight <= 0) return false
        val rootLocation = IntArray(2)
        val viewLocation = IntArray(2)
        return runCatching {
            root.getLocationOnScreen(rootLocation)
            view.getLocationOnScreen(viewLocation)
            val rootBottom = rootLocation[1] + rootHeight
            val viewBottom = viewLocation[1] + viewHeight
            val tolerance = (root.resources.displayMetrics.density * SUB_PB_INPUT_FRAME_BOTTOM_TOLERANCE_DP).toInt()
            viewBottom >= rootBottom - tolerance
        }.getOrDefault(false)
    }

    private fun isWideLayout(view: View): Boolean {
        val width = view.layoutParams?.width ?: return false
        return width == ViewGroup.LayoutParams.MATCH_PARENT || width == 0
    }

    private fun setSubPbInputCapsuleDynamicBackground(view: View, color: Int) {
        val radius = resolveSubPbInputCapsuleRadius(view)
        val currentBackground = view.background
        if (
            currentBackground is SubPbInputCapsuleTintDrawable &&
            currentBackground.appliedColor == color &&
            currentBackground.appliedRadius == radius
        ) {
            return
        }
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.background = SubPbInputCapsuleTintDrawable(color, radius).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        view.setPadding(left, top, right, bottom)
        view.invalidate()
    }

    private fun resolveSubPbInputCapsuleRadius(view: View): Float {
        val height = max(view.height, view.measuredHeight)
        if (height > 0) return height / 2f
        return view.resources.displayMetrics.density * SUB_PB_INPUT_CAPSULE_FALLBACK_RADIUS_DP
    }

    private fun resolvePbDynamicBackgroundReplacement(
        view: View,
        colorResId: Int,
        skinType: Int?,
    ): Int? {
        val targets = runtimeTargets ?: return null
        if (colorResId == 0 || colorResId !in targets.dynamicBackgroundColorIds) return null
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return null
        val activity = findCachedActivityFromContext(view.context) ?: return null
        if (!isPbActivity(activity) && !isSubPbReplyHostActivity(activity)) return null
        return resolveCachedPbCommentDynamicTintColorOrNull()
    }

    private fun applyPbSortSwitchBackgroundDynamicTint(
        view: View,
        invalidateOnChange: Boolean,
    ) {
        if (!isPbSortSwitchButton(view)) return
        val paintField = pbSortSwitchBackgroundPaintField ?: return
        val paint = try {
            paintField.get(view) as? Paint
        } catch (t: Throwable) {
            if (firstPbSortSwitchDynamicTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG sort switch background paint unavailable: ${t.message}" }
            }
            null
        } ?: return
        val color = resolvePbSortSwitchTintState(view)?.backgroundColor ?: run {
            restorePbSortSwitchOriginalBackgroundColor(view, paint, invalidateOnChange)
            return
        }
        rememberPbSortSwitchOriginalBackgroundColor(view, paint.color)
        if (paint.color != color) {
            paint.color = color
            if (invalidateOnChange) view.invalidate()
        }
    }

    private fun drawPbSortSwitchSelectedDynamicTint(view: View, canvas: Canvas) {
        if (!isPbSortSwitchButton(view)) return
        val pathField = pbSortSwitchSlidePathField ?: return
        val color = resolvePbSortSwitchTintState(view)?.selectedColor ?: return
        val slidePath = try {
            pathField.get(view) as? Path
        } catch (t: Throwable) {
            if (firstPbSortSwitchDynamicTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG sort switch selected slide path unavailable: ${t.message}" }
            }
            null
        } ?: return

        val paint = pbSortSwitchSelectedSlidePaint
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(slidePath, paint)
    }

    private fun resolvePbSortSwitchTintState(view: View): PbSortSwitchTintState? {
        val style = currentHomeNativeGlassRuntimeStyle()
        synchronized(pbSortSwitchTintStates) {
            pbSortSwitchTintStates[view]?.let { cached ->
                if (cached.matchesPbSortSwitchTintState(view)) return cached
            }
        }
        val sourcePath = style.backgroundImagePath
        val blurCachePath = style.blurCacheImagePath
        val blurPercent = style.cardBlurPercent
        val tintColor = style.tintColor
        val autoTintColor = style.autoTintColor
        val pinnedReplyTitle = isPbSortSwitchPinnedReplyTitle(view)
        val canApply = isPbSortSwitchDynamicTintHost(view)
        if (!canApply) {
            pbSortSwitchTintStates.remove(view)
            return null
        }
        val baseColor = resolveCachedPbCommentDynamicTintColorOrNull()
        val backgroundColor = when {
            pinnedReplyTitle -> baseColor
            else -> Color.TRANSPARENT
        }
        val selectedColor = if (baseColor != null) {
            if (pinnedReplyTitle) applyPbSortSwitchSelectedTintOverlay(baseColor) else baseColor
        } else {
            null
        }
        val state = PbSortSwitchTintState(
            sourcePath = sourcePath,
            blurCachePath = blurCachePath,
            blurPercent = blurPercent,
            tintColor = tintColor,
            autoTintColor = autoTintColor,
            pinnedReplyTitle = pinnedReplyTitle,
            backgroundColor = backgroundColor,
            selectedColor = selectedColor,
        )
        pbSortSwitchTintStates[view] = state
        return state
    }

    private fun PbSortSwitchTintState.matchesPbSortSwitchTintState(view: View): Boolean {
        val style = currentHomeNativeGlassRuntimeStyle()
        return sourcePath == style.backgroundImagePath &&
            blurCachePath == style.blurCacheImagePath &&
            blurPercent == style.cardBlurPercent &&
            tintColor == style.tintColor &&
            autoTintColor == style.autoTintColor &&
            pinnedReplyTitle == isPbSortSwitchPinnedReplyTitle(view)
    }

    private fun isPbSortSwitchDynamicTintHost(view: View): Boolean {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return false
        val activity = findCachedActivityFromContext(view.context) ?: return false
        return isPbActivity(activity) || isSubPbReplyHostActivity(activity)
    }

    private fun shouldReplacePbSortSwitchNativeSelectedSlide(view: View): Boolean {
        return resolvePbSortSwitchTintState(view)?.selectedColor != null
    }

    private fun savePbSortSwitchNativeSelectedTransparentLayer(view: View, canvas: Canvas): Int? {
        val width = max(max(view.width, view.measuredWidth), canvas.width)
        val height = max(max(view.height, view.measuredHeight), canvas.height)
        if (width <= 0 || height <= 0) return null
        return runCatching {
            canvas.saveLayer(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                pbSortSwitchNativeSelectedSlideLayerPaint,
            )
        }.getOrNull()
    }

    private fun resolvePbCachedDynamicTintColor(view: View): Int? {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return null
        val activity = findCachedActivityFromContext(view.context) ?: return null
        if (!isPbActivity(activity) && !isSubPbReplyHostActivity(activity)) return null
        return resolveCachedPbCommentDynamicTintColorOrNull()
    }

    private fun schedulePbReplyBarInputCapsuleDynamicTint(anchor: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        val activity = findCachedActivityFromContext(anchor.context) ?: return
        if (!isPbActivity(activity)) return
        val scheduleAnchor = findPbActivityContentHost(activity) ?: anchor

        var shouldPost = false
        synchronized(pbReplyBarInputApplyScheduled) {
            if (!pbReplyBarInputApplyScheduled.containsKey(scheduleAnchor)) {
                pbReplyBarInputApplyScheduled[scheduleAnchor] = true
                shouldPost = true
            }
        }
        if (!shouldPost) return

        scheduleAnchor.post {
            applyPbReplyBarInputCapsuleDynamicTintSafely(scheduleAnchor)
        }
        scheduleAnchor.postOnAnimation {
            try {
                applyPbReplyBarInputCapsuleDynamicTintSafely(scheduleAnchor)
            } finally {
                synchronized(pbReplyBarInputApplyScheduled) {
                    pbReplyBarInputApplyScheduled.remove(scheduleAnchor)
                }
            }
        }
    }

    private fun applyPbReplyBarInputCapsuleDynamicTintSafely(anchor: View) {
        try {
            applyPbReplyBarInputCapsuleDynamicTint(anchor)
        } catch (t: Throwable) {
            if (firstPbDynamicBackgroundColorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG pb reply bar input capsule dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun applyPbReplyBarInputCapsuleDynamicTint(anchor: View) {
        val activity = findCachedActivityFromContext(anchor.context) ?: return
        if (!isPbActivity(activity)) return
        val searchRoot = findPbReplyBarSearchRoot(anchor, activity) ?: return
        val capsule = findPbReplyBarInputCapsule(searchRoot) ?: return
        val frameColor = resolvePbCachedDynamicTintColor(capsule) ?: return
        val capsuleColor = offsetPbReplyBarInputCapsuleColor(frameColor)
        setPbReplyBarInputCapsuleDynamicBackground(capsule, capsuleColor)
        applyPbReplyBarGestureNavigationBridge(activity, capsule, frameColor)
    }

    private fun applyPbReplyBarGestureNavigationBridge(activity: Activity, anchor: View, color: Int) {
        if (!isNearWindowBottom(anchor)) return
        val insetBottom = SystemBarCompatHook.gestureNavigationInsetBottom(activity)
        if (insetBottom <= 0) return
        val content = findPbActivityContentHost(activity) as? FrameLayout ?: return
        val bridge = findOrCreatePbReplyBarGestureBridge(activity, content, insetBottom)
        val params = bridge.layoutParams as? FrameLayout.LayoutParams
        if (params?.height != insetBottom || params.gravity != android.view.Gravity.BOTTOM) {
            bridge.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                insetBottom,
                android.view.Gravity.BOTTOM,
            )
        }
        if ((bridge.background as? ColorDrawable)?.color != color) {
            bridge.background = ColorDrawable(color)
        }
    }

    private fun findOrCreatePbReplyBarGestureBridge(
        activity: Activity,
        content: FrameLayout,
        insetBottom: Int,
    ): View {
        synchronized(pbReplyBarGestureBridgeViews) {
            val cached = pbReplyBarGestureBridgeViews[activity]?.get()
            if (cached?.parent === content) return cached
            pbReplyBarGestureBridgeViews.remove(activity)
        }
        val bridge = View(content.context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(
            bridge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                insetBottom,
                android.view.Gravity.BOTTOM,
            ),
        )
        synchronized(pbReplyBarGestureBridgeViews) {
            pbReplyBarGestureBridgeViews[activity] = WeakReference(bridge)
        }
        return bridge
    }

    private fun findPbReplyBarSearchRoot(anchor: View, activity: Activity): View? {
        var current: View? = anchor
        var depth = 0
        while (current != null && depth < PB_REPLY_BAR_INPUT_PARENT_SCAN_DEPTH) {
            if (containsPbReplyBarInputCapsule(current, depth = 0)) return current
            current = current.parent as? View
            depth++
        }
        return findPbActivityContentHost(activity)
    }

    private fun containsPbReplyBarInputCapsule(view: View, depth: Int): Boolean {
        if (depth > PB_REPLY_BAR_INPUT_SEARCH_DEPTH) return false
        if (isPbReplyBarInputCapsuleCandidate(view)) return true
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            if (containsPbReplyBarInputCapsule(group.getChildAt(index) ?: continue, depth + 1)) return true
        }
        return false
    }

    private fun findPbReplyBarInputCapsule(root: View): View? {
        var best: View? = null
        var bestScore = 0

        fun scan(view: View, depth: Int) {
            if (depth > PB_REPLY_BAR_INPUT_SEARCH_DEPTH) return
            if (isPbReplyBarInputCapsuleCandidate(view)) {
                val score = scorePbReplyBarInputCapsule(view)
                if (score > bestScore) {
                    best = view
                    bestScore = score
                }
            }
            val group = view as? ViewGroup ?: return
            for (index in 0 until group.childCount) {
                scan(group.getChildAt(index) ?: continue, depth + 1)
            }
        }

        scan(root, depth = 0)
        return best?.takeIf { bestScore >= PB_REPLY_BAR_INPUT_CAPSULE_MIN_SCORE }
    }

    private fun isPbReplyBarInputCapsuleCandidate(view: View): Boolean {
        if (isInsideSubPbView(view)) return false
        if (view.javaClass.name == StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS) return false
        val layout = view as? LinearLayout ?: return false
        if (layout.orientation != LinearLayout.HORIZONTAL) return false
        if (layout.childCount < 2) return false
        if (!isWeightedPbReplyBarInput(layout)) return false
        var hasText = false
        for (index in 0 until layout.childCount) {
            val textView = layout.getChildAt(index) as? TextView ?: continue
            if (textView.maxLines == 1 || !textView.text.isNullOrBlank()) {
                hasText = true
                break
            }
        }
        return hasText
    }

    private fun scorePbReplyBarInputCapsule(view: View): Int {
        var score = 0
        if (view.background != null) score += 3
        if (hasNonBlankDirectTextChild(view)) score += 2
        if (isWeightedPbReplyBarInput(view)) score += 3
        if (isCompactPbReplyBarInput(view)) score += 2
        if (isNearWindowBottom(view)) score += 3
        return score
    }

    private fun isWeightedPbReplyBarInput(view: View): Boolean {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return false
        return params.width == 0 && params.weight > 0f
    }

    private fun isCompactPbReplyBarInput(view: View): Boolean {
        val maxHeight = (view.resources.displayMetrics.density * PB_REPLY_BAR_INPUT_CAPSULE_MAX_HEIGHT_DP).toInt()
        val height = max(view.height, view.measuredHeight)
        if (height in 1..maxHeight) return true
        val layoutHeight = view.layoutParams?.height ?: return false
        return layoutHeight == ViewGroup.LayoutParams.WRAP_CONTENT || layoutHeight in 1..maxHeight
    }

    private fun isNearWindowBottom(view: View): Boolean {
        val height = max(view.height, view.measuredHeight)
        if (height <= 0) return false
        val location = IntArray(2)
        return runCatching {
            view.getLocationOnScreen(location)
            val screenHeight = view.resources.displayMetrics.heightPixels
            val bottom = location[1] + height
            val tolerance = (view.resources.displayMetrics.density * PB_REPLY_BAR_INPUT_BOTTOM_TOLERANCE_DP).toInt()
            bottom >= screenHeight - tolerance
        }.getOrDefault(false)
    }

    private fun setPbReplyBarInputCapsuleDynamicBackground(view: View, color: Int) {
        val radius = resolvePbReplyBarInputCapsuleRadius(view)
        val currentBackground = view.background
        if (
            currentBackground is PbReplyBarInputCapsuleTintDrawable &&
            currentBackground.appliedColor == color &&
            currentBackground.appliedRadius == radius
        ) {
            return
        }
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.background = PbReplyBarInputCapsuleTintDrawable(color, radius).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        view.setPadding(left, top, right, bottom)
        view.invalidate()
    }

    private fun resolvePbReplyBarInputCapsuleRadius(view: View): Float {
        val height = max(view.height, view.measuredHeight)
        if (height > 0) return height / 2f
        return view.resources.displayMetrics.density * PB_REPLY_BAR_INPUT_CAPSULE_FALLBACK_RADIUS_DP
    }

    private fun isInsideSubPbView(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth <= SUB_PB_INPUT_ROOT_PARENT_SCAN_DEPTH) {
            if (current.javaClass.name == StableTiebaHookPoints.SUB_PB_VIEW_CLASS) return true
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun schedulePbDialogRoundLayoutDynamicTint(view: View) {
        if (view.javaClass.name != StableTiebaHookPoints.CORE_DIALOG_ROUND_LINEAR_LAYOUT_CLASS) return
        var shouldPost = false
        synchronized(pbDialogRoundLayoutDynamicTintScheduled) {
            if (!pbDialogRoundLayoutDynamicTintScheduled.containsKey(view)) {
                pbDialogRoundLayoutDynamicTintScheduled[view] = true
                shouldPost = true
            }
        }
        if (!shouldPost) return
        view.postOnAnimation {
            synchronized(pbDialogRoundLayoutDynamicTintScheduled) {
                pbDialogRoundLayoutDynamicTintScheduled.remove(view)
            }
            applyPbDialogRoundLayoutDynamicTintSafely(view)
        }
    }

    private fun applyPbDialogRoundLayoutDynamicTintSafely(view: View) {
        try {
            applyPbDialogRoundLayoutDynamicTint(view)
        } catch (t: Throwable) {
            if (firstPbDynamicBackgroundColorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG dialog round layout dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun applyPbDialogRoundLayoutDynamicTint(view: View) {
        if (view.javaClass.name != StableTiebaHookPoints.CORE_DIALOG_ROUND_LINEAR_LAYOUT_CLASS) return
        if (!isPbDialogActionMenuRoundLayout(view)) return
        val color = resolvePbCommentDynamicTintColor(view)
        val manager = createEmManagerForView(view) ?: run {
            setBackgroundColorPreservingPadding(view, color)
            return
        }
        if (!applyEmManagerRealBackgroundColor(manager, color)) {
            setBackgroundColorPreservingPadding(view, color)
        }
    }

    private fun isPbDialogActionMenuRoundLayout(view: View): Boolean {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return false
        val activity = findCachedActivityFromContext(view.context) ?: return false
        if (!isPbActivity(activity) && !isSubPbReplyHostActivity(activity)) return false
        val group = view as? ViewGroup ?: return false
        return hasDescendantClassName(group, "android.widget.HorizontalScrollView", maxDepth = 4) &&
            hasDescendantClassName(group, StableTiebaHookPoints.EM_TEXT_VIEW_CLASS, maxDepth = 6)
    }

    private fun hasDescendantClassName(view: View, className: String, maxDepth: Int): Boolean {
        if (maxDepth < 0) return false
        if (view.javaClass.name == className) return true
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (hasDescendantClassName(child, className, maxDepth - 1)) return true
        }
        return false
    }

    private fun rememberPbSortSwitchOriginalBackgroundColor(view: View, color: Int) {
        synchronized(pbSortSwitchOriginalBackgroundColors) {
            if (!pbSortSwitchOriginalBackgroundColors.containsKey(view)) {
                pbSortSwitchOriginalBackgroundColors[view] = color
            }
        }
    }

    private fun restorePbSortSwitchOriginalBackgroundColor(
        view: View,
        paint: Paint,
        invalidateOnChange: Boolean,
    ) {
        val originalColor = synchronized(pbSortSwitchOriginalBackgroundColors) {
            pbSortSwitchOriginalBackgroundColors.remove(view)
        } ?: return
        if (paint.color != originalColor) {
            paint.color = originalColor
            if (invalidateOnChange) view.invalidate()
        }
    }

    private fun applyPbEnterForumCapsuleBackgroundDynamicTint(
        controller: Any,
        reapplyAfterLayout: Boolean,
    ) {
        val view = readPbEnterForumCapsuleView(controller) ?: return
        if (!isPbEnterForumCapsuleActive(controller, view)) {
            clearPbEnterForumCapsuleTintIfNeeded(view)
            return
        }
        val color = resolvePbCachedDynamicTintColor(view)
        if (color == null) {
            clearPbEnterForumCapsuleTintIfNeeded(view)
            return
        }
        setPbEnterForumCapsuleDynamicBackground(view, color)
        if (reapplyAfterLayout && view.height <= 0) {
            view.post {
                applyPbEnterForumCapsuleBackgroundDynamicTint(
                    controller,
                    reapplyAfterLayout = false,
                )
            }
        }
    }

    private fun readPbEnterForumCapsuleView(controller: Any): View? {
        val field = pbEnterForumCapsuleViewField ?: return null
        return try {
            field.get(controller) as? View
        } catch (t: Throwable) {
            if (firstPbEnterForumCapsuleDynamicTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG enter forum capsule view unavailable: ${t.message}" }
            }
            null
        }
    }

    private fun isPbEnterForumCapsuleActive(controller: Any, view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val field = pbEnterForumCapsuleTitleField ?: return false
        val title = try {
            field.get(controller) as? String
        } catch (t: Throwable) {
            if (firstPbEnterForumCapsuleDynamicTintErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG enter forum capsule title unavailable: ${t.message}" }
            }
            null
        }
        return !title.isNullOrBlank()
    }

    private fun setPbEnterForumCapsuleDynamicBackground(view: View, color: Int) {
        val radius = resolvePbEnterForumCapsuleRadius(view)
        val currentBackground = view.background
        if (currentBackground is EnterForumCapsuleTintDrawable &&
            currentBackground.appliedColor == color &&
            currentBackground.appliedRadius == radius
        ) {
            return
        }
        if (currentBackground !is EnterForumCapsuleTintDrawable) {
            rememberPbEnterForumCapsuleOriginalState(view, currentBackground)
        }
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.background = EnterForumCapsuleTintDrawable(color, radius).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        view.setPadding(left, top, right, bottom)
        view.invalidate()
    }

    private fun resolvePbEnterForumCapsuleRadius(view: View): Float {
        val height = view.height
        if (height > 0) return height / 2f
        return view.resources.displayMetrics.density * PB_ENTER_FORUM_CAPSULE_FALLBACK_RADIUS_DP
    }

    private fun rememberPbEnterForumCapsuleOriginalState(view: View, background: Drawable?) {
        synchronized(pbEnterForumCapsuleOriginalStates) {
            pbEnterForumCapsuleOriginalStates[view] = EnterForumCapsuleOriginalState(
                background = background,
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
            )
        }
    }

    private fun clearPbEnterForumCapsuleTintIfNeeded(view: View) {
        if (view.background is EnterForumCapsuleTintDrawable) {
            restorePbEnterForumCapsuleOriginalState(view)
        } else {
            synchronized(pbEnterForumCapsuleOriginalStates) {
                pbEnterForumCapsuleOriginalStates.remove(view)
            }
        }
    }

    private fun restorePbEnterForumCapsuleOriginalState(view: View) {
        val state = synchronized(pbEnterForumCapsuleOriginalStates) {
            pbEnterForumCapsuleOriginalStates.remove(view)
        } ?: return
        view.background = state.background
        view.setPadding(state.left, state.top, state.right, state.bottom)
        view.invalidate()
    }

    private fun readEmManagerView(manager: Any): View? {
        return try {
            cachedFieldInHierarchy(
                manager.javaClass,
                "fromView",
                emManagerFromViewFields,
                emManagerFromViewMissingClasses,
            )?.get(manager) as? View
        } catch (t: Throwable) {
            if (firstPbDynamicBackgroundColorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG EMManager.fromView unavailable: ${t.message}" }
            }
            null
        }
    }

    private fun createEmManagerForView(view: View): Any? {
        return try {
            val classLoader = view.javaClass.classLoader ?: return null
            val managerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.EM_MANAGER_CLASS, classLoader)
                ?: return null
            val fromMethod = cachedMethodInHierarchy(
                managerClass,
                "from",
                emManagerFromMethods,
                emManagerFromMethodMissingClasses,
                View::class.java,
            ) ?: return null
            fromMethod.invoke(null, view)
        } catch (t: Throwable) {
            if (firstPbDynamicBackgroundColorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG EMManager.from(view) unavailable: ${t.message}" }
            }
            null
        }
    }

    private fun applyEmManagerRealBackgroundColor(manager: Any, color: Int): Boolean {
        return try {
            cachedMethodInHierarchy(
                manager.javaClass,
                "setBackGroundRealColor",
                emManagerRealBackgroundColorMethods,
                emManagerRealBackgroundColorMissingClasses,
                java.lang.Integer.TYPE,
            )
                ?.invoke(manager, color)
                ?: return false
            true
        } catch (t: Throwable) {
            if (firstPbDynamicBackgroundColorErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG EMManager dynamic background failed: ${t.message}" }
            }
            false
        }
    }

    private fun cachedFieldInHierarchy(
        clazz: Class<*>,
        name: String,
        cache: ConcurrentHashMap<Class<*>, Field>,
        missing: MutableSet<Class<*>>,
    ): Field? {
        cache[clazz]?.let { return it }
        if (missing.contains(clazz)) return null
        val field = findFieldInHierarchy(clazz, name)
        if (field != null) {
            cache[clazz] = field
        } else {
            missing.add(clazz)
        }
        return field
    }

    private fun cachedMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        cache: ConcurrentHashMap<Class<*>, Method>,
        missing: MutableSet<Class<*>>,
        vararg paramTypes: Class<*>,
    ): Method? {
        cache[clazz]?.let { return it }
        if (missing.contains(clazz)) return null
        val method = findMethodInHierarchy(clazz, name, *paramTypes)
        if (method != null) {
            cache[clazz] = method
        } else {
            missing.add(clazz)
        }
        return method
    }

    private fun resolvePbCommentDynamicTintColor(view: View): Int {
        return resolveCachedPbCommentDynamicTintColor(view)
    }

    private fun resolveCachedPbCommentDynamicTintColor(view: View): Int {
        resolveCachedPbCommentDynamicTintColorOrNull()?.let { return it }
        return Color.rgb(255, 255, 255)
    }

    private fun resolveCachedHomeTabDynamicTintColor(view: View): Int? {
        return resolveCachedPbCommentDynamicTintColorOrNull()
    }

    private fun resolveCachedPbCommentDynamicTintColorOrNull(): Int? {
        val style = currentHomeNativeGlassRuntimeStyle()
        val tintColor = style.tintColor
        val autoTintColor = style.autoTintColor
        val cached = pbCommentDynamicTintState
        if (
            cached != null &&
            cached.tintColor == tintColor &&
            cached.autoTintColor == autoTintColor
        ) {
            return cached.lightColor
        }
        val baseColor = style.configuredPbCommentTintColor() ?: return null
        val state = PbCommentDynamicTintState(
            tintColor = tintColor,
            autoTintColor = autoTintColor,
            lightColor = pbCommentBaseRgb(baseColor),
        )
        pbCommentDynamicTintState = state
        return state.lightColor
    }

    private fun isPbCommentItemFrame(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.PB_ITEM_FRAME_VIEW_CLASS
    }

    private fun isPbItemRelativeView(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.PB_ITEM_RELATIVE_VIEW_CLASS
    }

    private fun isPbSubPbLayout(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.PB_SUB_PB_LAYOUT_CLASS
    }

    private fun isSubPbNavigationBar(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.NAVIGATION_BAR_CLASS
    }

    private fun isPbSortSwitchButton(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS
    }

    private fun findPbSortSwitchButton(view: View, depth: Int): View? {
        if (isPbSortSwitchButton(view)) {
            return if (isPbSortSwitchExcludedFromTopDynamicTint(view)) null else view
        }
        if (depth >= PB_REPLY_TITLE_SORT_SEARCH_DEPTH) return null
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val button = findPbSortSwitchButton(group.getChildAt(index) ?: continue, depth + 1)
            if (button != null) return button
        }
        return null
    }

    private fun findAnyPbSortSwitchButton(view: View, depth: Int): View? {
        if (isPbSortSwitchButton(view)) return view
        if (depth >= PB_REPLY_TITLE_SORT_SEARCH_DEPTH) return null
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val button = findAnyPbSortSwitchButton(group.getChildAt(index) ?: continue, depth + 1)
            if (button != null) return button
        }
        return null
    }

    private fun isPbSortSwitchComponent(view: View): Boolean {
        val name = view.javaClass.name
        if (
            name != "android.widget.LinearLayout" &&
            name != "android.widget.RelativeLayout" &&
            name != "android.widget.FrameLayout"
        ) {
            return false
        }
        return containsPbSortSwitchButton(view, depth = 0)
    }

    private fun containsPbSortSwitchButton(view: View, depth: Int): Boolean {
        if (isPbSortSwitchButton(view)) return true
        if (depth >= PB_SORT_SWITCH_COMPONENT_CHECK_DEPTH) return false
        val group = view as? ViewGroup ?: return false
        for (index in 0 until group.childCount) {
            if (containsPbSortSwitchButton(group.getChildAt(index) ?: continue, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isPbSortSwitchPinnedReplyTitle(view: View): Boolean {
        val dividerId = resolvePbReplyTitleDividerViewId()
        if (dividerId <= 0) return false
        var current: View? = view
        var depth = 0
        while (current != null && depth < PB_SORT_SWITCH_PARENT_SCAN_DEPTH) {
            val group = current as? ViewGroup
            if (group != null && containsPbSortSwitchButton(group, depth = 0)) {
                findViewByResolvedId(group, dividerId)?.let { divider ->
                    return divider.visibility == View.VISIBLE &&
                        max(max(divider.height, divider.measuredHeight), divider.layoutParams?.height ?: 0) > 0
                }
            }
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun isPbSortSwitchExcludedFromTopDynamicTint(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth < PB_SORT_SWITCH_PARENT_SCAN_DEPTH) {
            if (current is AbsListView || isPbCommentItemFrame(current)) return true
            current = current.parent as? View
            depth++
        }
        return false
    }

    private fun isPbCommentRecyclerView(view: View): Boolean {
        return view.javaClass.name.contains("CommentRecyclerView")
    }

    private fun isPbCommentRootSurface(view: View): Boolean {
        return view.javaClass.name in PB_COMMENT_ROOT_SURFACE_CLASSES
    }

    private fun isPbCommentBackgroundContainer(view: View): Boolean {
        if (shouldKeepPbCommentBackground(view)) return false
        val name = view.javaClass.name
        return name == "android.widget.FrameLayout" ||
            name == "android.widget.RelativeLayout" ||
            name == "android.widget.LinearLayout" ||
            name.contains("LogParamConstraintLayout") ||
            name.contains("SwipeRefreshLayout") ||
            name.contains("SwipeBackLayout") ||
            name.contains("PageBrowserRecyclerView") ||
            name.contains("CommentRecyclerView") ||
            name.contains("ParentRecyclerView") ||
            name.contains("ChildRecyclerView") ||
            name.contains("DisableSwipeBackLinearLayout") ||
            name == StableTiebaHookPoints.PB_ITEM_RELATIVE_VIEW_CLASS ||
            name in PB_COMMENT_SURFACE_CLASSES ||
            name in PB_COMMENT_ITEM_SURFACE_CLASSES ||
            isPbCommentItemFrame(view)
    }

    private fun shouldKeepPbCommentBackground(view: View): Boolean {
        val name = view.javaClass.name
        return name == StableTiebaHookPoints.NAVIGATION_BAR_CLASS ||
            name == StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS ||
            isPbSortSwitchButton(view) ||
            name.contains("NavigationBar") ||
            name.contains("BottomEnterBar")
    }

    private fun shouldKeepPbCommentBackgroundSubtree(
        view: View,
        keepSortSwitchComponent: Boolean = true,
    ): Boolean {
        return keepSortSwitchComponent && (isPbSortSwitchButton(view) || isPbSortSwitchComponent(view))
    }

    private fun rememberHomeSearchBox(view: View) {
        homeSearchBoxes[view] = true
        installHomeSearchBoxAttachRefresh(view)
        scheduleHomeSearchBoxBootstrapRefresh(view)
    }

    private fun installHomeSearchBoxAttachRefresh(view: View) {
        synchronized(homeSearchBoxAttachRefreshInstalled) {
            if (homeSearchBoxAttachRefreshInstalled.containsKey(view)) return
            homeSearchBoxAttachRefreshInstalled[view] = true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                scheduleHomeSearchBoxBootstrapRefresh(v)
                v.post { applyHomeChromeGlassSafely(v) }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        runCatching {
            view.addOnAttachStateChangeListener(attachListener)
            if (view.isAttachedToWindow) {
                view.post { applyHomeChromeGlassSafely(view) }
            }
        }.onFailure {
            homeSearchBoxAttachRefreshInstalled.remove(view)
            runCatching { view.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun scheduleHomeSearchBoxBootstrapRefresh(view: View) {
        synchronized(homeSearchBoxBootstrapScheduled) {
            if (homeSearchBoxBootstrapScheduled.containsKey(view)) return
            homeSearchBoxBootstrapScheduled[view] = true
        }
        for (delayMs in HOME_SEARCH_BOX_BOOTSTRAP_REFRESH_DELAYS_MS) {
            if (delayMs <= 0L) {
                view.post { applyHomeChromeGlassSafely(view) }
            } else {
                view.postDelayed({ applyHomeChromeGlassSafely(view) }, delayMs)
            }
        }
    }

    private fun applyHomeChromeGlassSafely(anchor: View) {
        try {
            val root = anchor.rootView ?: anchor
            val page = findHomeNativePageForFeature(anchor, root)
            val hasNativeGlassBackground = ConfigManager.isHomeNativeGlassEnabled &&
                hasPageBackgroundOverride()
            val canApplyChrome = hasNativeGlassBackground &&
                page != null

            if (ConfigManager.isHomeTabDynamicTintEnabled) {
                applyHomeTopTabDynamicTintForMatchingViews(root, canApplyChrome)
                applyHomeBottomTabDynamicTintForMatchingViews(root, hasNativeGlassBackground)
            }
            applyHomeSearchBoxGlassForMatchingViews(root, page, canApplyChrome)
            applyTrackedHomeSearchBoxGlass(root, page, canApplyChrome)
            applyTrackedCardComponentGlass(root)
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home chrome glass failed: ${t.message}" }
            }
        }
    }

    private fun applyHomeBottomTabDynamicTintSafely(tabHost: View) {
        try {
            applyHomeBottomTabDynamicTint(
                tabHost,
                ConfigManager.isHomeNativeGlassEnabled && hasPageBackgroundOverride(),
            )
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home bottom tab dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun applyHomeTopTabDynamicTintSafely(topChrome: View) {
        try {
            val root = topChrome.rootView ?: topChrome
            val canApplyChrome = ConfigManager.isHomeNativeGlassEnabled &&
                hasPageBackgroundOverride() &&
                findHomeNativePageForFeature(topChrome, root) != null
            applyHomeTopTabDynamicTint(topChrome, canApplyChrome)
        } catch (t: Throwable) {
            if (firstChromeErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home top tab dynamic tint failed: ${t.message}" }
            }
        }
    }

    private fun applyHomeTopTabDynamicTintForMatchingViews(root: View, shouldApply: Boolean) {
        applyChromeStyleForMatchingViews(root, HOME_TOP_CHROME_CLASSES) { topChrome ->
            applyHomeTopTabDynamicTint(topChrome, shouldApply)
        }
    }

    private fun applyHomeBottomTabDynamicTintForMatchingViews(root: View, shouldApply: Boolean) {
        if (root.javaClass.name in HOME_BOTTOM_TAB_HOST_CLASSES) {
            applyHomeBottomTabDynamicTint(root, shouldApply)
            return
        }
        val group = root as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            applyHomeBottomTabDynamicTintForMatchingViews(group.getChildAt(index) ?: continue, shouldApply)
        }
    }

    private fun applyHomeTopTabDynamicTint(topChrome: View, shouldApply: Boolean) {
        val color = if (
            shouldApply &&
            ConfigManager.isHomeTabDynamicTintEnabled
        ) {
            resolveCachedHomeTabDynamicTintColor(topChrome)
        } else {
            null
        }
        if (color == null) {
            restoreChromeGlass(topChrome)
            applyHomeTopTabBoundaryDecoration(topChrome, null)
            return
        }
        rememberChromeGlassOriginalState(topChrome)
        if ((topChrome.background as? ColorDrawable)?.color != color) {
            setChromeDynamicTintBackgroundColor(topChrome, color)
        }
        applyHomeTopTabBoundaryDecoration(topChrome, color)
    }

    private fun applyHomeBottomTabDynamicTint(tabHost: View, shouldApply: Boolean) {
        val targets = resolveHomeBottomTabDynamicTintTargets(tabHost)
        val color = if (
            shouldApply &&
            ConfigManager.isHomeTabDynamicTintEnabled
        ) {
            resolveCachedHomeTabDynamicTintColor(tabHost)
        } else {
            null
        }
        if (targets.isEmpty()) {
            applyHomeBottomTabBoundaryDecorations(tabHost, color)
            return
        }
        for (target in targets) {
            if (color == null) {
                restoreChromeGlass(target)
            } else {
                rememberChromeGlassOriginalState(target)
                if ((target.background as? ColorDrawable)?.color != color) {
                    setChromeDynamicTintBackgroundColor(target, color)
                }
            }
        }
        applyHomeBottomTabBoundaryDecorations(tabHost, color)
    }

    private fun applyHomeTopTabBoundaryDecoration(topChrome: View, color: Int?) {
        val bottomLine = invokeNoArgView(topChrome, "getBottomLine") ?: return
        if (color == null) {
            restoreChromeGlass(bottomLine)
            return
        }
        rememberChromeGlassOriginalState(bottomLine)
        if ((bottomLine.background as? ColorDrawable)?.color != color) {
            setChromeDynamicTintBackgroundColor(bottomLine, color)
        }
    }

    private fun applyHomeBottomTabBoundaryDecorations(tabHostOrWidget: View, color: Int?) {
        if (tabHostOrWidget.javaClass.name == StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS) {
            if (color != null) {
                invokeBooleanMethod(tabHostOrWidget, "setShouldDrawTopLine", false)
            }
            return
        }
        if (tabHostOrWidget.javaClass.name != StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS) return
        if (color == null) {
            findHomeBottomTabBoundaryLineViews(tabHostOrWidget).forEach { restoreChromeGlass(it) }
            return
        }
        invokeBooleanMethod(tabHostOrWidget, "setShouldDrawTopLine", false)
        invokeBooleanMethod(tabHostOrWidget, "setTabContainerShadowShow", false)
        invokeNoArgView(tabHostOrWidget, "getFragmentTabWidget")?.let { widget ->
            invokeBooleanMethod(widget, "setShouldDrawTopLine", false)
        }
        findHomeBottomTabBoundaryLineViews(tabHostOrWidget).forEach { line ->
            rememberChromeGlassOriginalState(line)
            if ((line.background as? ColorDrawable)?.color != color) {
                setChromeDynamicTintBackgroundColor(line, color)
            }
        }
    }

    private fun findHomeBottomTabBoundaryLineViews(tabHost: View): List<View> {
        val lines = ArrayList<View>(2)
        collectHomeBottomTabBoundaryLineViews(tabHost, lines)
        invokeNoArgView(tabHost, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER)?.let { wrapper ->
            collectHomeBottomTabBoundaryLineViews(wrapper, lines)
        }
        return lines.distinct()
    }

    private fun collectHomeBottomTabBoundaryLineViews(container: View, out: MutableList<View>) {
        val group = container as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (isHomeBottomTabBoundaryLineCandidate(child)) {
                out.add(child)
            }
        }
    }

    private fun isHomeBottomTabBoundaryLineCandidate(view: View): Boolean {
        if (view.javaClass.name != "android.view.View") return false
        if (view.isClickable || view.isLongClickable || view.isFocusable || view.hasOnClickListeners()) {
            return false
        }
        val maxHeightPx = max(2, (view.resources.displayMetrics.density * 2f).toInt())
        val layoutHeight = view.layoutParams?.height ?: 0
        return layoutHeight in 1..maxHeightPx ||
            view.height in 1..maxHeightPx ||
            view.measuredHeight in 1..maxHeightPx
    }

    private fun findHomeBottomTabHostAncestor(view: View): View? {
        var depth = 0
        var current = view.parent
        while (current is View && depth < HOME_BOTTOM_TAB_BOUNDARY_PARENT_DEPTH) {
            if (current.javaClass.name == StableTiebaHookPoints.FRAGMENT_TAB_HOST_CLASS) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun resolveHomeBottomTabDynamicTintTargets(tabHost: View): List<View> {
        if (tabHost.javaClass.name == StableTiebaHookPoints.FRAGMENT_TAB_WIDGET_CLASS) {
            return listOf(tabHost)
        }
        val targets = ArrayList<View>(2)
        invokeNoArgView(tabHost, StableTiebaHookPoints.METHOD_GET_TAB_WRAPPER)?.let { targets.add(it) }
        invokeNoArgView(tabHost, "getFragmentTabWidget")?.let { targets.add(it) }
        return targets.distinct()
    }

    private fun invokeNoArgView(target: View, methodName: String): View? {
        return try {
            cachedRuntimeMethodInHierarchy(
                target.javaClass,
                methodName,
                "()View",
                chromeNoArgViewMethods,
                chromeNoArgViewMissingMethods,
                validate = { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        View::class.java.isAssignableFrom(method.returnType)
                },
            )?.invoke(target) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeBooleanMethod(target: View, methodName: String, value: Boolean): Boolean {
        return try {
            cachedRuntimeMethodInHierarchy(
                target.javaClass,
                methodName,
                "(boolean)",
                chromeBooleanMethods,
                chromeBooleanMissingMethods,
                validate = { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
                },
                java.lang.Boolean.TYPE,
            )
                ?.invoke(target, value)
                ?: return false
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun cachedRuntimeMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        signature: String,
        cache: ConcurrentHashMap<RuntimeMethodKey, Method>,
        missing: MutableSet<RuntimeMethodKey>,
        validate: (Method) -> Boolean,
        vararg paramTypes: Class<*>,
    ): Method? {
        val key = RuntimeMethodKey(clazz, name, signature)
        cache[key]?.let { return it }
        if (missing.contains(key)) return null
        val method = findMethodInHierarchy(clazz, name, *paramTypes)
        if (method != null && validate(method)) {
            cache[key] = method
            return method
        }
        missing.add(key)
        return null
    }

    private fun applyHomeSearchBoxGlassForMatchingViews(root: View, page: View?, shouldApply: Boolean) {
        val searchBoxClasses = searchBoxClassNames()
        if (searchBoxClasses.isEmpty()) return
        applyChromeStyleForMatchingViews(root, searchBoxClasses) { searchBox ->
            rememberHomeSearchBox(searchBox)
            val searchBoxPage = findHomeNativePageForSearchBox(searchBox, root, page)
            if (shouldApply && searchBoxPage != null) {
                applyChromeGlass(searchBox, searchBoxPage)
            } else {
                restoreChromeGlass(searchBox)
            }
        }
    }

    private fun applyTrackedHomeSearchBoxGlass(root: View, page: View?, shouldApply: Boolean) {
        val trackedViews = synchronized(homeSearchBoxes) {
            homeSearchBoxes.keys.toList()
        }
        for (view in trackedViews) {
            if (view.rootView !== root && view !== root) continue
            val searchBoxPage = findHomeNativePageForSearchBox(view, root, page)
            if (shouldApply && searchBoxPage != null) {
                applyChromeGlass(view, searchBoxPage)
            } else {
                restoreChromeGlass(view)
            }
        }
    }

    private fun applyChromeStyleForMatchingViews(
        view: View,
        classNames: Array<String>,
        action: (View) -> Unit,
    ) {
        if (view.javaClass.name in classNames) {
            action(view)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            applyChromeStyleForMatchingViews(group.getChildAt(index) ?: continue, classNames, action)
        }
    }

    private fun applyChromeGlass(view: View, page: View) {
        applyGlassToView(view, page)
    }

    private fun applyCardComponentGlassToDescendants(view: View, page: View?) {
        if (isCardComponentGlassView(view)) {
            rememberCardComponentView(view)
            val root = view.rootView ?: view
            if (page != null && isRecommendTabActive(view, root, page)) {
                applyCardComponentGlass(view, page)
            } else {
                ensureCardComponentGlassSafely(view)
            }
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            applyCardComponentGlassToDescendants(group.getChildAt(index) ?: continue, page)
        }
    }

    private fun ensureCardComponentGlassSafely(componentView: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) {
            restoreCardComponentGlass(componentView)
            return
        }
        try {
            val root = componentView.rootView ?: componentView
            val page = findHomeNativePage(componentView)
            if (page == null || !isRecommendTabActive(componentView, root, page)) {
                restoreCardComponentGlass(componentView)
                return
            }
            val targets = resolveCardComponentGlassTargets(componentView)
            if (targets.isNotEmpty() && targets.all { it.background is CardGlassDrawable }) return
            applyCardComponentGlass(componentView, page)
        } catch (t: Throwable) {
            if (firstCardErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG card component glass failed: ${t.message}" }
            }
        }
    }

    private fun applyCardComponentGlass(componentView: View, page: View) {
        for (target in resolveCardComponentGlassTargets(componentView)) {
            applyGlassToView(
                target,
                page,
                childSurfaceEnabled = true,
            )
        }
    }

    private fun applyTrackedCardComponentGlass(root: View) {
        val trackedViews = synchronized(homeCardComponentViews) {
            homeCardComponentViews.keys.toList()
        }
        for (view in trackedViews) {
            if (view.rootView !== root && view !== root) continue
            ensureCardComponentGlassSafely(view)
        }
    }

    private fun rememberCardComponentView(view: View) {
        homeCardComponentViews[view] = true
        installCardComponentAttachRefresh(view)
        scheduleCardComponentBootstrapRefresh(view)
    }

    private fun installCardComponentAttachRefresh(view: View) {
        synchronized(cardComponentAttachRefreshInstalled) {
            if (cardComponentAttachRefreshInstalled.containsKey(view)) return
            cardComponentAttachRefreshInstalled[view] = true
        }
        lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener
        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            runCatching {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
            }
            if (view.isAttachedToWindow) {
                ensureCardComponentGlassSafely(view)
            }
            true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                scheduleCardComponentBootstrapRefresh(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    }
                }
            }
        }
        runCatching {
            view.addOnAttachStateChangeListener(attachListener)
            if (view.isAttachedToWindow) {
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }.onFailure {
            cardComponentAttachRefreshInstalled.remove(view)
            runCatching { view.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun scheduleCardComponentBootstrapRefresh(view: View) {
        synchronized(cardComponentBootstrapScheduled) {
            if (cardComponentBootstrapScheduled.containsKey(view)) return
            cardComponentBootstrapScheduled[view] = true
        }
        for (delayMs in CARD_COMPONENT_BOOTSTRAP_REFRESH_DELAYS_MS) {
            if (delayMs <= 0L) {
                view.post { ensureCardComponentGlassSafely(view) }
            } else {
                view.postDelayed({ ensureCardComponentGlassSafely(view) }, delayMs)
            }
        }
    }

    private fun restoreCardComponentGlass(componentView: View) {
        for (target in resolveCardComponentGlassTargets(componentView)) {
            restoreChromeGlass(target)
        }
    }

    private fun resolveCardComponentGlassTargets(componentView: View): List<View> {
        if (!isCardComponentGlassView(componentView)) return listOf(componentView)
        val targets = ArrayList<View>(3)
        targets.add(componentView)
        if (componentView.javaClass.name == StableTiebaHookPoints.FEED_CARD_REPLY_VIEW_CLASS) {
            return targets
        }
        val group = componentView as? ViewGroup ?: return targets
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (child is ViewGroup && child.visibility != View.GONE) {
                targets.add(child)
            }
        }
        return targets.distinct()
    }

    private fun applyGlassToView(
        view: View,
        page: View,
        childSurfaceEnabled: Boolean = false,
    ) {
        rememberChromeGlassOriginalState(view)
        val radius = view.resources.displayMetrics.density *
            effectiveCardRadiusDp()
        setGlassBackground(view, page, radius, childSurfaceEnabled)
        clearNativePressVisual(view)
    }

    private fun effectiveCardRadiusDp(): Int {
        return currentHomeNativeGlassRuntimeStyle().cardRadiusDp
    }

    private fun rememberChromeGlassOriginalState(view: View) {
        synchronized(chromeGlassOriginalStates) {
            if (chromeGlassOriginalStates.containsKey(view)) return
            val stateListAnimator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.stateListAnimator
            } else {
                null
            }
            chromeGlassOriginalStates[view] = ChromeGlassOriginalState(
                background = view.background,
                foreground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) view.foreground else null,
                stateListAnimator = stateListAnimator,
                elevation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.elevation else 0f,
                translationZ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) view.translationZ else 0f,
            )
        }
    }

    private fun restoreChromeGlass(view: View) {
        val state = synchronized(chromeGlassOriginalStates) {
            chromeGlassOriginalStates.remove(view)
        } ?: return
        view.background = state.background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.foreground = state.foreground
        }
        glassBackgroundViews.remove(view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.stateListAnimator = state.stateListAnimator
            view.elevation = state.elevation
            view.translationZ = state.translationZ
        }
    }

    private fun searchBoxClassNames(): Array<String> {
        return listOfNotNull(
            runtimeTargets?.searchBoxClass,
            StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS,
        ).distinct().toTypedArray()
    }

    private fun isRecommendTabActive(anchor: View, root: View, page: View? = findHomeNativePageForFeature(anchor, root)): Boolean {
        if (page != null && isHomeNativePageAnchor(anchor, page)) return true
        findTopChromeView(anchor)?.let { topChrome ->
            topChromeTabTypes[topChrome]?.let { return it == HOME_TOP_TAB_RECOMMEND_TYPE }
            resolveCurrentTopTabItem(topChrome)?.let { item ->
                resolveRecommendTopTabItemState(item)?.let { return it }
            }
        }
        findFirstTopChromeView(root)?.let { topChrome ->
            topChromeTabTypes[topChrome]?.let { return it == HOME_TOP_TAB_RECOMMEND_TYPE }
            resolveCurrentTopTabItem(topChrome)?.let { item ->
                resolveRecommendTopTabItemState(item)?.let { return it }
            }
        }
        return findVisibleHomeNativePage(root) != null
    }

    private fun resolveCurrentTopTabItem(topChrome: View): Any? {
        return runCatching {
            XposedCompat.callMethod(topChrome, "getCurrentFragmentTabItem")
        }.getOrNull()
    }

    private fun resolveRecommendTopTabItemState(item: Any): Boolean? {
        val targets = runtimeTargets ?: return null
        val code = targets.homeTabItemCodeField?.let { fieldName ->
            runCatching { XposedCompat.getObjectField(item, fieldName) as? String }.getOrNull()
        }
        if (code == HOME_TOP_TAB_RECOMMEND_CODE) return true
        if (!code.isNullOrBlank()) return false
        val type = targets.homeTabItemTypeField?.let { fieldName ->
            runCatching { (XposedCompat.getObjectField(item, fieldName) as? Number)?.toInt() }.getOrNull()
        }
        return type?.let { it == HOME_TOP_TAB_RECOMMEND_TYPE }
    }

    private fun findTopChromeView(view: View): View? {
        var current: View? = view
        while (current != null) {
            if (isHomeTopChromeView(current)) return current
            current = current.parent as? View
        }
        return null
    }

    private fun findFirstTopChromeView(view: View): View? {
        if (isHomeTopChromeView(view)) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val found = findFirstTopChromeView(group.getChildAt(index) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun isHomeTopChromeView(view: View): Boolean {
        return view.javaClass.name in HOME_TOP_CHROME_CLASSES
    }

    private fun findVisibleHomeNativePage(view: View): View? {
        if (view.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS && view.isShown) {
            return view
        }
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            val found = findVisibleHomeNativePage(group.getChildAt(index) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun findHomeNativePageForFeature(anchor: View, root: View): View? {
        if (anchor.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS && anchor.isShown) {
            return anchor
        }
        return findHomeNativePageFromChild(anchor)?.takeIf { it.isShown } ?: findVisibleHomeNativePage(root)
    }

    private fun findHomeNativePageForSearchBox(searchBox: View, root: View, preferredPage: View?): View? {
        return findHomeNativePageFromChild(searchBox)?.takeIf { it.isShown }
            ?: preferredPage?.takeIf { it.isShown }
            ?: findVisibleHomeNativePage(root)
    }

    private fun installHomeRecyclerChildAttachRefresh(recycler: View) {
        synchronized(homeRecyclerChildAttachRefreshInstalled) {
            if (homeRecyclerChildAttachRefreshInstalled.containsKey(recycler)) return
            homeRecyclerChildAttachRefreshInstalled[recycler] = true
        }
        val recyclerClass = recyclerViewClass ?: recycler.javaClass.takeIf { isRecyclerView(recycler) }
        val listenerClass = runCatching {
            Class.forName(
                "${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}\$OnChildAttachStateChangeListener",
                false,
                recyclerClass?.classLoader ?: recycler.javaClass.classLoader,
            )
        }.getOrNull()
        val addListenerMethod = if (recyclerClass != null && listenerClass != null) {
            XposedCompat.findMethodOrNull(
                recyclerClass,
                "addOnChildAttachStateChangeListener",
                listenerClass,
            )
        } else {
            null
        }
        if (listenerClass == null || addListenerMethod == null) {
            homeRecyclerChildAttachRefreshInstalled.remove(recycler)
            return
        }
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { proxy, method, args ->
            when (method.name) {
                "onChildViewAttachedToWindow" -> {
                    (args?.getOrNull(0) as? View)?.let { child ->
                        handleHomeRecyclerChildAttached(child)
                    }
                    null
                }
                "onChildViewDetachedFromWindow" -> null
                "toString" -> "$TAG home recycler child text listener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> null
            }
        }
        runCatching {
            addListenerMethod.invoke(recycler, listener)
            refreshAttachedHomeRecyclerChildren(recycler)
        }.onFailure {
            homeRecyclerChildAttachRefreshInstalled.remove(recycler)
        }
    }

    private fun refreshAttachedHomeRecyclerChildren(recycler: View) {
        val group = recycler as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            handleHomeRecyclerChildAttached(group.getChildAt(index) ?: continue)
        }
    }

    private fun handleHomeRecyclerChildAttached(child: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        if (isFeedCardView(child)) {
            applyCardStyleSafely(child, force = true)
            scheduleHomeFeedCardStyleReapply(child)
        } else {
            scheduleHomeRecyclerChildBackgroundRefresh(child)
        }
    }

    private fun scheduleHomeRecyclerChildBackgroundRefresh(child: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        child.post {
            applyHomeRecyclerChildBackgroundSafely(child)
        }
    }

    private fun applyHomeRecyclerChildBackgroundSafely(child: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled || !hasPageBackgroundOverride()) return
        try {
            if (!isInsideHomeNativePage(child)) return
            markNestedHomeRecyclerViews(child)
        } catch (t: Throwable) {
            if (firstPageErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logD { "$TAG home recycler child background refresh failed: ${t.message}" }
            }
        }
    }

    private fun markNestedHomeRecyclerViews(view: View) {
        if (isRecyclerView(view)) {
            homeRecyclerViews[view] = true
            installScrollInvalidation(view)
            installHomeRecyclerChildAttachRefresh(view)
            view.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            markNestedHomeRecyclerViews(group.getChildAt(index) ?: continue)
        }
    }

    private fun isHomeNativePageAnchor(anchor: View, page: View): Boolean {
        if (anchor === page) return true
        return findHomeNativePageFromChild(anchor) === page
    }

    private fun clearNestedRecyclerBackgrounds(view: View) {
        if (isRecyclerView(view)) {
            setTransparentBackgroundIfNeeded(view)
            clearElevation(view)
            return
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            clearNestedRecyclerBackgrounds(group.getChildAt(index) ?: continue)
        }
    }

    private fun firstNonRecyclerChild(group: ViewGroup): View? {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (!isRecyclerView(child)) return child
        }
        return null
    }

    private fun setGlassBackground(
        view: View,
        page: View?,
        radius: Float,
        childSurfaceEnabled: Boolean = false,
    ) {
        val style = currentHomeNativeGlassRuntimeStyle()
        val request = backgroundRequestForStyle(
            style,
            BACKGROUND_CACHE_SAMPLE_EDGE,
            BACKGROUND_CACHE_SAMPLE_EDGE,
        )
        val cachedEntry = if (page != null && request != null) {
            findCachedBackgroundEntry(request)
        } else {
            null
        }
        if (page != null && cachedEntry == null && request != null) {
            val pageRef = WeakReference(page)
            scheduleBackgroundDecode(request, view) { target ->
                setGlassBackground(target, pageRef.get(), radius, childSurfaceEnabled)
            }
        }
        val blurredBitmap = cachedEntry?.blurredBitmap
        val tintAlphaPercent = style.tintAlphaPercent
        val darkMode = style.darkMode
        val blurPercent = style.cardBlurPercent
        val strokeEnabled = style.strokeEnabled
        val shadowStrengthPercent = style.shadowStrengthPercent
        val background = if (page != null && blurredBitmap != null) {
            val current = view.background as? CardGlassDrawable
            if (
                current != null &&
                current.matches(
                    page = page,
                    bitmap = blurredBitmap,
                    radius = radius,
                    tintAlphaPercent = tintAlphaPercent,
                    childSurfaceEnabled = childSurfaceEnabled,
                    darkMode = darkMode,
                    blurPercent = blurPercent,
                    strokeEnabled = strokeEnabled,
                    shadowStrengthPercent = shadowStrengthPercent,
                )
            ) {
                glassBackgroundViews[view] = true
                clearElevation(view)
                return
            }
            CardGlassDrawable(
                target = view,
                page = page,
                bitmap = blurredBitmap,
                radius = radius,
                tintAlphaPercent = tintAlphaPercent,
                childSurfaceEnabled = childSurfaceEnabled,
                darkMode = darkMode,
                blurPercent = blurPercent,
                strokeEnabled = strokeEnabled,
                shadowStrengthPercent = shadowStrengthPercent,
            )
        } else {
            GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = radius
            }
        }
        view.background = background
        if (background is CardGlassDrawable) {
            glassBackgroundViews[view] = true
        } else {
            glassBackgroundViews.remove(view)
        }
        clearElevation(view)
    }

    private fun clearCardNativePressVisuals(card: View) {
        clearNativePressVisual(card)
        val group = card as? ViewGroup ?: return
        val backgroundHolder = group.getChildAt(0)
        if (backgroundHolder != null) {
            clearNativePressVisual(backgroundHolder)
            val innerHolder = (backgroundHolder as? ViewGroup)?.let { holder ->
                firstNonRecyclerChild(holder)
            }
            if (innerHolder != null) {
                clearNativePressVisual(innerHolder)
            }
        }
    }

    private fun clearNativePressVisual(view: View) {
        var stateChanged = false
        if (view.isPressed) {
            view.isPressed = false
            stateChanged = true
        }
        if (view.isDuplicateParentStateEnabled) {
            view.isDuplicateParentStateEnabled = false
            stateChanged = true
        }
        if (stateChanged) {
            view.jumpDrawablesToCurrentState()
        }
        clearForeground(view)
        clearElevation(view)
    }

    private fun currentHomeNativeGlassRuntimeStyle(): HomeNativeGlassRuntimeStyle {
        return runtimeStyleStore.current()
    }

    private fun onHostDarkModeChanged(@Suppress("UNUSED_PARAMETER") darkMode: Boolean) {
        if (refreshHomeNativeGlassRuntimeStyle(scheduleReapply = false)) {
            scheduleHomeNativeGlassModeReapply()
        }
    }

    private fun refreshHomeNativeGlassRuntimeStyle(
        forceHostRead: Boolean = false,
        scheduleReapply: Boolean = true,
    ): Boolean {
        return runtimeStyleStore.refresh(forceHostRead, scheduleReapply)
    }

    private fun scheduleHomeNativeGlassModeReapply() {
        if (!homeNativeGlassModeReapplyScheduled.compareAndSet(false, true)) return
        val anchor = synchronized(glassBackgroundViews) {
            glassBackgroundViews.keys.firstOrNull { it.isAttachedToWindow }
        } ?: synchronized(homeRecyclerViews) {
            homeRecyclerViews.keys.firstOrNull { it.isAttachedToWindow }
        }
        val reapply = Runnable {
            homeNativeGlassModeReapplyScheduled.set(false)
            reapplyHomeNativeGlassForModeChange()
        }
        if (anchor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                anchor.postOnAnimation(reapply)
            } else {
                anchor.post(reapply)
            }
        } else {
            reapply.run()
        }
    }

    private fun reapplyHomeNativeGlassForModeChange() {
        val cards = ArrayList<View>()
        val pages = ArrayList<View>()
        fun rememberCard(card: View?) {
            if (card != null && !cards.contains(card)) {
                cards.add(card)
            }
        }
        fun rememberPage(page: View?) {
            if (page != null && !pages.contains(page)) {
                pages.add(page)
            }
        }
        synchronized(homeFeedCardStyleStates) {
            homeFeedCardStyleStates.keys.forEach(::rememberCard)
            homeFeedCardStyleStates.clear()
        }
        synchronized(homeFeedCardGlassTargets) {
            homeFeedCardGlassTargets.keys.forEach { view ->
                rememberCard(findHomeFeedCardAncestor(view))
            }
        }
        val recyclers = synchronized(homeRecyclerViews) {
            homeRecyclerViews.keys.toList()
        }
        val componentViews = synchronized(homeCardComponentViews) {
            homeCardComponentViews.keys.toList()
        }
        pbCommentBackgroundHostStates.clear()
        pbSubPbLayoutCardStates.clear()
        pbSortSwitchTintStates.clear()
        pbCommentDynamicTintState = null

        for (recycler in recyclers) {
            if (!recycler.isAttachedToWindow) continue
            refreshHomeNativeBackgroundLayers(recycler)
            rememberPage(findHomeNativePageFromChild(recycler))
        }
        for (page in pages) {
            if (page.isAttachedToWindow) {
                applyPageStyleSafely(page)
            }
        }
        for (card in cards) {
            if (card.isAttachedToWindow) {
                applyCardStyleSafely(card, force = true)
            }
        }
        for (componentView in componentViews) {
            if (componentView.isAttachedToWindow) {
                ensureCardComponentGlassSafely(componentView)
            }
        }
        invalidateGlassBackgroundViews()
    }

    private fun hasPageBackgroundOverride(): Boolean {
        return currentHomeNativeGlassRuntimeStyle().hasBackgroundImage
    }

    private fun createBackgroundDrawable(view: View, style: HomeNativeGlassRuntimeStyle): Drawable? {
        val metrics = view.resources.displayMetrics
        val rawTargetWidth = if (view.width > 0) view.width else metrics.widthPixels
        val rawTargetHeight = if (view.height > 0) view.height else metrics.heightPixels
        val targetSize = backgroundDecodeSizeForMaxEdge(
            rawTargetWidth.coerceAtLeast(1),
            rawTargetHeight.coerceAtLeast(1),
            PAGE_BACKGROUND_MAX_DECODE_EDGE,
        )
        val request = backgroundRequestForStyle(
            style,
            targetSize.first,
            targetSize.second,
        ) ?: return null
        val cachedEntry = findCachedBackgroundEntry(request)
        if (cachedEntry == null) {
            scheduleBackgroundDecode(request, view) { target ->
                applyPageStyleSafely(target)
            }
            return null
        }
        val bitmap = cachedEntry.bitmap ?: return null
        return CenterCropBitmapDrawable(bitmap)
    }

    private fun executeBackgroundDecode(block: () -> Unit) {
        activeBackgroundDecodeTasks.incrementAndGet()
        try {
            backgroundDecodeExecutor().execute {
                try {
                    block()
                } finally {
                    activeBackgroundDecodeTasks.decrementAndGet()
                }
            }
        } catch (t: Throwable) {
            activeBackgroundDecodeTasks.decrementAndGet()
            throw t
        }
    }

    private fun backgroundDecodeExecutor(): ExecutorService {
        return synchronized(backgroundDecodeExecutorLock) {
            val current = backgroundDecodeExecutor
            if (current != null && !current.isShutdown) return current
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "tbhook-glass-bg-decode").apply {
                    isDaemon = true
                }
            }.also { backgroundDecodeExecutor = it }
        }
    }

    private fun prewarmBackgroundCacheIfNeeded() {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        val style = currentHomeNativeGlassRuntimeStyle()
        if (!style.hasBackgroundImage) return
        val context = ConfigManager.getAppContext() ?: return
        val metrics = context.resources.displayMetrics
        val targetSize = backgroundDecodeSizeForMaxEdge(
            metrics.widthPixels.coerceAtLeast(1),
            metrics.heightPixels.coerceAtLeast(1),
            PAGE_BACKGROUND_MAX_DECODE_EDGE,
        )
        val request = backgroundRequestForStyle(style, targetSize.first, targetSize.second) ?: return
        if (findCachedBackgroundEntry(request) != null) return
        val key = "prewarm|" + request.cacheKey
        if (!backgroundDecodeKeys.add(key)) return
        executeBackgroundDecode {
            try {
                if (
                    isBackgroundDecodeRequestCurrent(request) &&
                    findCachedBackgroundEntry(request) == null
                ) {
                    decodeBackgroundEntry(request)
                }
            } catch (t: Throwable) {
                if (firstBackgroundImageErrorLogged.compareAndSet(false, true)) {
                    XposedCompat.logD { "$TAG background image prewarm failed: ${t.message}" }
                }
            } finally {
                backgroundDecodeKeys.remove(key)
            }
        }
    }

    private fun findCachedBackgroundEntry(request: BackgroundRequest): CachedBackgroundBitmap? {
        return backgroundStore.find(request)
    }

    private fun scheduleBackgroundDecode(
        request: BackgroundRequest,
        anchor: View,
        onReady: (View) -> Unit,
    ) {
        val key = request.cacheKey
        if (!backgroundDecodeKeys.add(key)) return
        val anchorRef = WeakReference(anchor)
        executeBackgroundDecode {
            try {
                if (
                    isBackgroundDecodeRequestCurrent(request) &&
                    findCachedBackgroundEntry(request) == null
                ) {
                    decodeBackgroundEntry(request)
                }
            } catch (t: Throwable) {
                if (firstBackgroundImageErrorLogged.compareAndSet(false, true)) {
                    XposedCompat.logD { "$TAG background image decode failed: ${t.message}" }
                }
            } finally {
                backgroundDecodeKeys.remove(key)
            }
            val target = anchorRef.get()
            if (target != null) {
                target.post {
                    if (!target.isAttachedToWindow) return@post
                    if (!isBackgroundDecodeRequestCurrent(request)) return@post
                    onReady(target)
                    invalidateGlassBackgroundViews()
                }
            }
        }
    }

    private fun isBackgroundDecodeRequestCurrent(request: BackgroundRequest): Boolean {
        val style = currentHomeNativeGlassRuntimeStyle()
        return ConfigManager.isHomeNativeGlassEnabled &&
            style.backgroundImagePath == request.path &&
            style.blurCacheImagePath == request.blurCachePath &&
            style.cardBlurPercent == request.blurPercent
    }

    private fun decodeBackgroundEntry(request: BackgroundRequest): CachedBackgroundBitmap? {
        val cachedEntry = backgroundStore.decode(request)
        if (cachedEntry.bitmap == null && firstBackgroundImageErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logD { "$TAG background image unavailable: ${request.path}" }
        }
        return cachedEntry
    }

    private fun installScrollInvalidation(recycler: View) {
        installFramePositionInvalidation(recycler)
        if (scrollInvalidationInstalled.containsKey(recycler)) return
        scrollInvalidationInstalled[recycler] = true
        val listener = ViewTreeObserver.OnScrollChangedListener {
            scheduleGlassBackgroundInvalidation(recycler)
        }
        runCatching {
            recycler.viewTreeObserver.addOnScrollChangedListener(listener)
        }.onFailure {
            scrollInvalidationInstalled.remove(recycler)
        }
    }

    private fun installFramePositionInvalidation(recycler: View) {
        if (frameInvalidationInstalled.containsKey(recycler)) return
        frameInvalidationInstalled[recycler] = true
        val state = HomeRecyclerFrameState()
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (
                recycler.isAttachedToWindow &&
                ConfigManager.isHomeNativeGlassEnabled &&
                hasPageBackgroundOverride() &&
                updateHomeRecyclerFrameState(recycler, state)
            ) {
                scheduleGlassBackgroundInvalidation(recycler)
            }
            true
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(listener)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(listener)
                    }
                }
            }
        }
        runCatching {
            recycler.addOnAttachStateChangeListener(attachListener)
            recycler.viewTreeObserver.addOnPreDrawListener(listener)
        }.onFailure {
            frameInvalidationInstalled.remove(recycler)
            runCatching { recycler.removeOnAttachStateChangeListener(attachListener) }
        }
    }

    private fun updateHomeRecyclerFrameState(recycler: View, state: HomeRecyclerFrameState): Boolean {
        recycler.getLocationInWindow(state.location)
        val recyclerX = state.location[0]
        val recyclerY = state.location[1]
        val trackedChild = findFirstVisibleRecyclerChild(recycler)
        if (trackedChild != null) {
            trackedChild.getLocationInWindow(state.location)
        }
        val firstChildX = if (trackedChild == null) Int.MIN_VALUE else state.location[0]
        val firstChildY = if (trackedChild == null) Int.MIN_VALUE else state.location[1]
        val canScrollUp = recycler.canScrollVertically(-1)
        val changed = state.initialized && (
            state.recyclerX != recyclerX ||
                state.recyclerY != recyclerY ||
                state.firstChildX != firstChildX ||
                state.firstChildY != firstChildY ||
                state.canScrollUp != canScrollUp
            )
        state.initialized = true
        state.recyclerX = recyclerX
        state.recyclerY = recyclerY
        state.firstChildX = firstChildX
        state.firstChildY = firstChildY
        state.canScrollUp = canScrollUp
        return changed
    }

    private fun findFirstVisibleRecyclerChild(recycler: View): View? {
        val group = recycler as? ViewGroup ?: return null
        var firstVisibleChild: View? = null
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index) ?: continue
            if (child.visibility != View.VISIBLE || child.width <= 0 || child.height <= 0) continue
            if (firstVisibleChild == null) firstVisibleChild = child
            if (isFeedCardView(child)) return child
        }
        return firstVisibleChild
    }

    private fun scheduleGlassBackgroundInvalidation(anchor: View) {
        if (!scrollInvalidateScheduled.compareAndSet(false, true)) return
        anchor.postOnAnimation {
            scrollInvalidateScheduled.set(false)
            refreshHomeNativeBackgroundLayers(anchor)
            invalidateGlassBackgroundViews(anchor)
        }
    }

    private fun refreshHomeNativeBackgroundLayers(recycler: View) {
        if (!ConfigManager.isHomeNativeGlassEnabled) return
        if (!hasPageBackgroundOverride()) return
        clearVisibleHomeRecyclerTopBackgrounds(recycler)
        if (!recycler.canScrollVertically(-1)) {
            findHomeNativePageFromChild(recycler)?.let { page ->
                clearHomeNativeDefaultBackgrounds(page)
            }
        }
    }

    private fun invalidateGlassBackgroundViews() {
        val views = synchronized(glassBackgroundViews) {
            glassBackgroundViews.keys.toList()
        }
        for (view in views) {
            if (view.isAttachedToWindow) {
                view.invalidate()
            }
        }
    }

    private fun invalidateGlassBackgroundViews(anchor: View) {
        val root = anchor.rootView ?: anchor
        val anchorPage = findHomeNativePageFromChild(anchor)
        val views = synchronized(glassBackgroundViews) {
            glassBackgroundViews.keys.toList()
        }
        for (view in views) {
            if (view.isAttachedToWindow && shouldInvalidateGlassBackgroundView(view, anchor, root, anchorPage)) {
                view.invalidate()
            }
        }
    }

    private fun shouldInvalidateGlassBackgroundView(
        view: View,
        anchor: View,
        root: View,
        anchorPage: View?,
    ): Boolean {
        if (isViewWithinAncestor(view, anchor)) return true
        if (anchorPage == null) return false
        if (view.rootView !== root && view !== root) return false
        if (!isTrackedHomeSearchBox(view)) return false
        return findHomeNativePageForSearchBox(view, root, anchorPage) === anchorPage
    }

    private fun isTrackedHomeSearchBox(view: View): Boolean {
        return synchronized(homeSearchBoxes) {
            homeSearchBoxes.containsKey(view)
        }
    }

    private fun isRecyclerView(view: View): Boolean {
        val cls = recyclerViewClass
        return cls?.isInstance(view) == true ||
            view.javaClass.name == StableTiebaHookPoints.RECYCLER_VIEW_CLASS
    }

    private fun isFeedCardView(view: View): Boolean {
        return view.javaClass.name == StableTiebaHookPoints.FEED_CARD_VIEW_CLASS
    }

    private fun isCardComponentGlassView(view: View): Boolean {
        return view.javaClass.name in CARD_COMPONENT_GLASS_CLASSES
    }

    private fun findHomeNativePageFromChild(view: View): View? {
        var current = view.parent
        while (current is View) {
            if (current.javaClass.name == StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private data class RuntimeMethodKey(
        val clazz: Class<*>,
        val name: String,
        val signature: String,
    )
}
