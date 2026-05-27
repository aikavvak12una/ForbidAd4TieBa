package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.FeedInfoLogHook
import com.forbidad4tieba.hook.feature.ad.PbAdRequestBlockHook
import com.forbidad4tieba.hook.feature.ad.PbEarlyAdBlockHook
import com.forbidad4tieba.hook.feature.ad.PbFallingAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.SearchBoxTextAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.feature.im.PrivateReadReceiptBlockHook
import com.forbidad4tieba.hook.feature.perf.ComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.AiComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.ForceFeedUiOptHook
import com.forbidad4tieba.hook.feature.perf.HostPerformanceConfigHook
import com.forbidad4tieba.hook.feature.perf.PbPerformanceModeHook
import com.forbidad4tieba.hook.feature.perf.AdSdkInitBlockHook
import com.forbidad4tieba.hook.feature.perf.ColdStartOptHook
import com.forbidad4tieba.hook.feature.perf.TitanPatchBlockHook
import com.forbidad4tieba.hook.feature.perf.TrackingBlockHook
import com.forbidad4tieba.hook.feature.perf.VideoPreloadBlockHook
import com.forbidad4tieba.hook.feature.share.ImageViewerNativeShareHook
import com.forbidad4tieba.hook.feature.share.ShareTrackingParamCleanerHook
import com.forbidad4tieba.hook.feature.ui.HomeBottomTabAutoHideHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTopTabAutoHideHook
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotHook
import com.forbidad4tieba.hook.feature.ui.ImageViewerSwipeEnterForumBlockHook
import com.forbidad4tieba.hook.feature.ui.MainTabBottomHook
import com.forbidad4tieba.hook.feature.ui.MsgTabDefaultNotifyHook
import com.forbidad4tieba.hook.feature.ui.FreeCopyHook
import com.forbidad4tieba.hook.feature.ui.DefaultOriginalImageHook
import com.forbidad4tieba.hook.feature.ui.CollectionSearchHook
import com.forbidad4tieba.hook.feature.ui.HistorySearchHook
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHook
import com.forbidad4tieba.hook.feature.ui.HomeSideBarSettingsEntryHook
import com.forbidad4tieba.hook.feature.ui.ForumNativeTopShiftBlockHook
import com.forbidad4tieba.hook.feature.ui.PbDisableGestureFontScaleHook
import com.forbidad4tieba.hook.feature.ui.PbLikeAutoReplyHook
import com.forbidad4tieba.hook.feature.ui.PbCommentAutoLoadHook
import com.forbidad4tieba.hook.feature.ui.PbScrollCoalesceHook
import com.forbidad4tieba.hook.feature.ui.UpgradePopWindowBlockHook
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.feature.web.FollowedTabWebHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.feature.web.PlainUrlDirectBrowserHook
import com.forbidad4tieba.hook.ui.AboutInfoManager
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class MainHook : XposedModule() {
    private data class SymbolLoadResult(
        val symbols: HookSymbols,
        val pendingScan: Boolean,
    )

    private data class StaticHookPlan(
        val adHooks: Boolean,
        val topAndBottomBarHooks: Boolean,
        val webHooks: Boolean,
        val copyAndImageHooks: Boolean,
        val imageViewerNativeShareHook: Boolean,
        val defaultOriginalImageHook: Boolean,
        val imageViewerSwipeEnterForumBlockHook: Boolean,
        val pbPerformanceModeHook: Boolean,
        val homeTabAutoHideHooks: Boolean,
        val upgradeDialogHook: Boolean,
    ) {
        fun isEmpty(): Boolean {
            return !adHooks &&
                !topAndBottomBarHooks &&
                !webHooks &&
                !copyAndImageHooks &&
                !imageViewerNativeShareHook &&
                !defaultOriginalImageHook &&
                !imageViewerSwipeEnterForumBlockHook &&
                !pbPerformanceModeHook &&
                !homeTabAutoHideHooks &&
                !upgradeDialogHook
        }
    }

    @Volatile private var sAppContext: Context? = null
    @Volatile private var sAttachHookInstalled = false
    @Volatile private var sStaticHooksInstalled = false
    @Volatile private var sPostAttachStaticHooksInstalled = false
    @Volatile private var sSymbolHooksInstalled = false
    @Volatile private var sTitanStartupLogged = false
    private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedCompat.module = this
        processName = param.processName
        XposedCompat.log("[MainHook] onModuleLoaded: process=${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        XposedCompat.log("[MainHook] onPackageLoaded: pkg=${param.packageName}, cl=${param.defaultClassLoader}")

        if (param.packageName != Constants.TARGET_PACKAGE) return
        // 只在主进程安装，Titan 只在主进程加载补丁。
        if (processName != Constants.TARGET_PACKAGE) return

        // 尽早安装 TitanPatchBlockHook，在 Application.attach 之前。
        // 目标应用会在 TiebaBaseApplication.attachBaseContext() 里调用 LoaderManager.load()，
        // 所以要在 Application 生命周期开始前完成 hook。
        TitanPatchBlockHook.hook(param.defaultClassLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        XposedCompat.log("[MainHook] onPackageReady: pkg=${param.packageName}, process=$processName")

        if (param.packageName != Constants.TARGET_PACKAGE) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target package (${param.packageName})")
            return
        }
        if (!isTargetProcess(processName)) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - non-target process ($processName)")
            return
        }
        if (!shouldHandleProcess(processName)) {
            XposedCompat.logD("[MainHook] onPackageReady: SKIP - process not in hook whitelist ($processName)")
            return
        }
        val cl = param.classLoader
        XposedCompat.log("[MainHook] onPackageReady: using app classloader=$cl")

        handleLoadPackage(param.packageName, cl)
    }

    private fun handleLoadPackage(packageName: String, cl: ClassLoader) {
        XposedCompat.log("[MainHook] handleLoadPackage: pkg=$packageName, cl=$cl")
        val staticPlan = resolveStaticHookPlan(processName)
        if (staticPlan.isEmpty()) {
            XposedCompat.logD("[MainHook] static hook plan empty for process=$processName, skip")
        } else if (markStaticHooksInstalled()) {
            try {
                installStaticHooks(cl, staticPlan)
            } catch (t: Throwable) {
                synchronized(this) { sStaticHooksInstalled = false }
                XposedCompat.log("[MainHook] static hook install FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        } else {
            XposedCompat.log("[MainHook] static hooks already installed, skip duplicate install")
        }

        if (!shouldInstallAttachHook(processName)) {
            XposedCompat.logD("[MainHook] Application.attach hook skipped by process whitelist: $processName")
            return
        }

        if (!markAttachHookInstalled()) {
            XposedCompat.log("[MainHook] Application.attach hook already installed, skip")
            return
        }

        try {
            val attachMethod = android.app.Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
            attachMethod.isAccessible = true
            XposedCompat.log("[MainHook] Application.attach method found, installing hook...")

            hook(attachMethod).intercept { chain ->
                XposedCompat.log("[MainHook] > Application.attach INTERCEPTED, thisObj=${chain.thisObject?.javaClass?.name}")
                val result = chain.proceed()
                XposedCompat.log("[MainHook] > Application.attach proceed() returned")

                if (sAppContext == null) {
                    val app = chain.thisObject as? android.app.Application
                    if (app != null) {
                        sAppContext = app
                        ConfigManager.init(app)
                        MineTabWebBlockHook.onAppContextReady(app)
                        XposedCompat.log("[MainHook] > ConfigManager initialized, app=${app.packageName}")
                        val isMainProcess = processName == Constants.TARGET_PACKAGE
                        if (isMainProcess) {
                            runStartupTask("apply cached runtime controls") {
                                AboutInfoManager.applyCachedRuntimeControlsIfNeeded(app)
                            }
                            runStartupTask("trim model score stats") {
                                CustomPostModelScoreStats.trimToPostLimitAsync()
                            }
                            runStartupTask("apply auto percentile thresholds") {
                                CustomPostModelScoreStats.applyAutoPercentileThresholdsAsync()
                            }
                            runStartupTask("apply component disable") {
                                ComponentDisableHook.apply(app)
                            }
                            runStartupTask("delete Titan patch files") {
                                TitanPatchBlockHook.deletePatchFiles(app)
                            }
                            runStartupTask("log Titan startup") {
                                logTitanStartupIfNeeded(app, cl)
                            }
                            runStartupTask("schedule auto sign in") {
                                // 延后自动签到，减少启动阶段卡顿。
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    runStartupTask("auto sign in") {
                                        com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(app)
                                    }
                                }, 5000)
                            }
                        } else {
                            XposedCompat.log("[MainHook] > Skip auto sign in non-main process: $processName")
                        }
                    }
                }

                val appContext = sAppContext
                if (appContext == null) {
                    XposedCompat.log("[MainHook] > Symbol hooks skipped: appContext=false")
                    return@intercept result
                }

                val symbolLoadResult = try {
                    loadCachedSymbolsOrUnsupported(appContext, cl)
                } catch (t: Throwable) {
                    XposedCompat.log("[MainHook] > Symbol cache load FAILED, pending scan: ${t.message}")
                    XposedCompat.log(t)
                    SymbolLoadResult(
                        symbols = HookSymbols(
                            source = "unsupported",
                            featureStatusMap = HookSymbolResolver.featureStatusMap(null),
                        ),
                        pendingScan = true,
                    )
                }
                val symbols = symbolLoadResult.symbols
                if (!symbolLoadResult.pendingScan) {
                    ConfigManager.applyScanAvailability(appContext, HookSymbolResolver.featureStatusMap(symbols))
                }
                if (markPostAttachStaticHooksInstalled()) {
                    installPostAttachStaticHooks(cl, resolveStaticHookPlan(processName), symbols)
                }

                if (!markSymbolHooksInstalled()) {
                    XposedCompat.log("[MainHook] > Symbol hooks skipped: alreadyInstalled=$sSymbolHooksInstalled")
                    return@intercept result
                }

                val isMainProcess = processName == Constants.TARGET_PACKAGE
                if (!isMainProcess) {
                    if (processName == "${Constants.TARGET_PACKAGE}:remote") {
                        AiComponentDisableHook.hookImageViewerJumpButton(cl, symbols)
                    }
                    XposedCompat.log("[MainHook] > Skip remaining symbol-dependent hooks in non-main process: $processName")
                    return@intercept result
                }

                XposedCompat.log("[MainHook] > Installing symbol-dependent hooks...")
                val needsInitialScanDialog = symbolLoadResult.pendingScan || symbols.source == "unsupported"
                if (needsInitialScanDialog) {
                    XposedCompat.log("[MainHook] > Symbols unavailable, installing initial scan dialog hook")
                    SettingsMenuHook.ensureInitialScanDialogHook(cl)
                }
                try {
                    AboutInfoManager.fetchAtStartupIfNeeded(appContext)

                    XposedCompat.log("[MainHook] > Symbols loaded: source=${symbols.source}, settings=${symbols.settingsClass}, home=${symbols.homeTabClass}")
                    if (symbolLoadResult.pendingScan) {
                        XposedCompat.log("[MainHook] > Scan availability skipped: no cached symbols yet")
                    }
                    HookSymbolResolver.formatFeatureStatusLines(symbols).forEach { line ->
                        XposedCompat.log("[MainHook] > $line")
                    }
                    HookSymbolResolver.formatHookPointStatusLines(symbols).forEach { line ->
                        XposedCompat.log("[MainHook] > $line")
                    }

                    SettingsMenuHook.hook(cl, symbols)
                    HomeSideBarSettingsEntryHook.hook(cl)
                    FeedAdHook.hook(cl, symbols)
                    PostAdHook.hook(cl, symbols)
                    HomeTabHook.hook(cl, symbols)
                    XposedCompat.log(
                        "[MainHook] Installing MainTabBottomHook via symbols: " +
                            "data=${symbols.mainTabDataClass}, add=${symbols.mainTabAddMethod}, " +
                            "getList=${symbols.mainTabGetListMethod}, " +
                            "delegate=${symbols.mainTabDelegateGetStructureMethod}, " +
                            "typeField=${symbols.mainTabStructureTypeField}"
                    )
                    MainTabBottomHook.hook(cl, symbols)
                    StrategyAdHook.hookWithSymbols(cl, symbols)
                    SearchBoxTextAdHook.hook(cl, symbols)
                    HomeTopBarRightSlotHook.hook(cl, symbols)
                    PbEarlyAdBlockHook.hook(cl, symbols)
                    PbAdRequestBlockHook.hook(cl, symbols)
                    PbFallingAdHook.hook(cl, symbols)
                    EnterForumWebHook.hook(cl, symbols)
                    PlainUrlDirectBrowserHook.hook(cl, symbols)
                    ForumNativeTopShiftBlockHook.hook(cl, symbols)
                    HomeNativeGlassHook.hook(cl, symbols)

                    com.forbidad4tieba.hook.feature.ui.AutoRefreshHook.hook(cl, symbols)

                    com.forbidad4tieba.hook.feature.ui.AutoLoadMoreHook.hook(cl, symbols)
                    PbCommentAutoLoadHook.hook(cl, symbols)
                    PbScrollCoalesceHook.hook(cl, symbols)
                    PbDisableGestureFontScaleHook.hook(cl, symbols)
                    PbLikeAutoReplyHook.hook(cl, symbols)
                    AiComponentDisableHook.hook(cl, symbols)

                    MsgTabDefaultNotifyHook.hook(cl, symbols)
                    PrivateReadReceiptBlockHook.hook(cl, symbols)
                    CollectionSearchHook.hook(cl, symbols)
                    HistorySearchHook.hook(cl, symbols)
                    ShareTrackingParamCleanerHook.hook(cl, symbols)

                    if (ConfigManager.shouldOutputDetailedLogs()) {
                        FeedInfoLogHook.hook(cl, symbols)
                    }

                } catch (t: Throwable) {
                    synchronized(this@MainHook) { sSymbolHooksInstalled = false }
                    if (needsInitialScanDialog) {
                        XposedCompat.log("[MainHook] > Reinstall initial scan dialog hook after symbol init failure")
                        SettingsMenuHook.ensureInitialScanDialogHook(cl)
                    }
                    XposedCompat.log("[MainHook] > symbol hook init FAILED: ${t.message}")
                    XposedCompat.log(t)
                }

                result
            }
            XposedCompat.log("[MainHook] Application.attach hook INSTALLED")
        } catch (t: Throwable) {
            synchronized(this) { sAttachHookInstalled = false }
            XposedCompat.log("[MainHook] FAILED to hook Application.attach: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installStaticHooks(cl: ClassLoader, plan: StaticHookPlan) {
        XposedCompat.log("[MainHook] initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")

        if (plan.adHooks) {
            XposedCompat.log("[MainHook] Installing StrategyAdHook (static)...")
            StrategyAdHook.hookStatic(cl)
        }

        if (plan.topAndBottomBarHooks) {
            XposedCompat.log("[MainHook] Installing PbBottomEnterBarHook...")
            com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook.hook(cl)
        }

        if (plan.webHooks) {
            XposedCompat.log("[MainHook] Installing FollowedTabWebHook...")
            FollowedTabWebHook.hook(cl)

            XposedCompat.log("[MainHook] Installing MineTabWebBlockHook...")
            MineTabWebBlockHook.hook(cl)
        }

        if (plan.upgradeDialogHook) {
            XposedCompat.log("[MainHook] Installing UpgradePopWindowBlockHook...")
            UpgradePopWindowBlockHook.hook(cl)
        }

        XposedCompat.log("[MainHook] All static hooks dispatched.")
    }

    private fun installPostAttachStaticHooks(cl: ClassLoader, plan: StaticHookPlan, symbols: HookSymbols) {
        if (plan.copyAndImageHooks) {
            XposedCompat.log("[MainHook] Installing FreeCopyHook...")
            FreeCopyHook.hook(cl, symbols)
        }

        if (plan.homeTabAutoHideHooks && ConfigManager.isHomeTabAutoHideEnabled) {
            XposedCompat.log("[MainHook] Installing HomeTopTabAutoHideHook...")
            HomeTopTabAutoHideHook.hook(cl)

            XposedCompat.log("[MainHook] Installing HomeBottomTabAutoHideHook...")
            HomeBottomTabAutoHideHook.hook(cl)
        }

        if (plan.pbPerformanceModeHook) {
            XposedCompat.log("[MainHook] Installing PbPerformanceModeHook...")
            PbPerformanceModeHook.hook(cl)

            XposedCompat.log("[MainHook] Installing ForceFeedUiOptHook...")
            ForceFeedUiOptHook.hook(cl)

            XposedCompat.log("[MainHook] Installing AdSdkInitBlockHook...")
            AdSdkInitBlockHook.hook(cl)

            XposedCompat.log("[MainHook] Installing TrackingBlockHook...")
            TrackingBlockHook.hook(cl)

            XposedCompat.log("[MainHook] Installing VideoPreloadBlockHook...")
            VideoPreloadBlockHook.hook(cl)

            XposedCompat.log("[MainHook] Installing ColdStartOptHook...")
            ColdStartOptHook.hook(cl)

            XposedCompat.log("[MainHook] Installing HostPerformanceConfigHook...")
            HostPerformanceConfigHook.hook(cl)
        }

        if (plan.imageViewerNativeShareHook) {
            XposedCompat.log("[MainHook] Installing ImageViewerNativeShareHook...")
            ImageViewerNativeShareHook.hook(cl, symbols)
        }

        if (plan.defaultOriginalImageHook) {
            XposedCompat.log("[MainHook] Installing DefaultOriginalImageHook...")
            DefaultOriginalImageHook.hook(cl, symbols)
        }

        if (plan.imageViewerSwipeEnterForumBlockHook) {
            XposedCompat.log("[MainHook] Installing ImageViewerSwipeEnterForumBlockHook...")
            ImageViewerSwipeEnterForumBlockHook.hook(cl)
        }
    }

    private fun loadCachedSymbolsOrUnsupported(context: Context, cl: ClassLoader): SymbolLoadResult {
        val cached = HookSymbolResolver.loadCachedIfUsable(
            context = context,
            cl = cl,
            verifyFull = false,
        )
        if (cached != null) return SymbolLoadResult(cached, pendingScan = false)

        return SymbolLoadResult(
            symbols = HookSymbols(
                source = "unsupported",
                featureStatusMap = HookSymbolResolver.featureStatusMap(null),
            ),
            pendingScan = true,
        )
    }

    private fun markAttachHookInstalled(): Boolean {
        synchronized(this) {
            if (sAttachHookInstalled) return false
            sAttachHookInstalled = true
            return true
        }
    }

    private fun markStaticHooksInstalled(): Boolean {
        synchronized(this) {
            if (sStaticHooksInstalled) return false
            sStaticHooksInstalled = true
            return true
        }
    }

    private fun markPostAttachStaticHooksInstalled(): Boolean {
        synchronized(this) {
            if (sPostAttachStaticHooksInstalled) return false
            sPostAttachStaticHooksInstalled = true
            return true
        }
    }

    private fun markSymbolHooksInstalled(): Boolean {
        synchronized(this) {
            if (sSymbolHooksInstalled) return false
            sSymbolHooksInstalled = true
            return true
        }
    }

    private fun logTitanStartupIfNeeded(context: Context, cl: ClassLoader) {
        synchronized(this) {
            if (sTitanStartupLogged) return
            sTitanStartupLogged = true
        }
        TitanRuntimeState.logStartup(context, cl)
    }

    private fun runStartupTask(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] startup task FAILED: $name, ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isTargetProcess(name: String): Boolean {
        return name == Constants.TARGET_PACKAGE || name.startsWith("${Constants.TARGET_PACKAGE}:")
    }

    private fun shouldHandleProcess(name: String): Boolean {
        return shouldInstallAttachHook(name) || !resolveStaticHookPlan(name).isEmpty()
    }

    private fun shouldInstallAttachHook(name: String): Boolean {
        // 图片查看器 Activity 声明在 :remote，其他服务或辅助进程
        // 不需要初始化 ConfigManager、加载符号缓存或安装图片查看器 hook。
        return name == Constants.TARGET_PACKAGE || isImageViewerRemoteProcess(name)
    }

    private fun resolveStaticHookPlan(name: String): StaticHookPlan {
        val isMain = name == Constants.TARGET_PACKAGE
        val isImageViewerProcess = isMain || isImageViewerRemoteProcess(name)
        return StaticHookPlan(
            adHooks = isMain,
            topAndBottomBarHooks = isMain,
            webHooks = isMain,
            copyAndImageHooks = isMain,
            imageViewerNativeShareHook = isImageViewerProcess,
            defaultOriginalImageHook = isImageViewerProcess,
            imageViewerSwipeEnterForumBlockHook = isImageViewerProcess,
            pbPerformanceModeHook = isMain,
            homeTabAutoHideHooks = isMain,
            upgradeDialogHook = isMain,
        )
    }

    private fun isImageViewerRemoteProcess(name: String): Boolean {
        return name == "${Constants.TARGET_PACKAGE}:remote"
    }
}
