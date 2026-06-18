package com.forbidad4tieba.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Api102HookRegistry
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.BlockCountStats
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.feature.perf.ComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.TitanPatchBlockHook
import com.forbidad4tieba.hook.feature.signin.AutoSignInManager
import com.forbidad4tieba.hook.feature.ui.AutoRefreshHook
import com.forbidad4tieba.hook.feature.ui.CollectionSearchHook
import com.forbidad4tieba.hook.feature.ui.HomeNativeGlassHook
import com.forbidad4tieba.hook.feature.ui.SystemBarCompatHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.ui.AboutInfoManager
import com.forbidad4tieba.hook.ui.ModuleForegroundActivityTracker
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class MainHook : XposedModule() {
    private data class SymbolLoadResult(
        val symbols: HookSymbols,
        val pendingScan: Boolean,
    )

    @Volatile private var sAppContext: Context? = null
    @Volatile private var sAttachHookInstalled = false
    @Volatile private var sStaticHooksInstalled = false
    @Volatile private var sPostAttachStaticHooksInstalled = false
    @Volatile private var sSymbolHooksInstalled = false
    @Volatile private var sTitanStartupLogged = false
    @Volatile private var pendingAutoSignInHandler: Handler? = null
    @Volatile private var pendingAutoSignInRunnable: Runnable? = null
    @Volatile private var restoreAutoSignInAfterHotReload = false
    private var processName: String = ""

    private val hotReloadManagedThreadNames = setOf(
        "tbhook-about-startup-fetch",
        "tbhook-telemetry-account-retry",
        "tbhook-auto-signin",
        "tbhook-model-score-stats",
        "tbhook-block-count-stats",
        "tbhook-collect-cache-io",
        "tbhook-collect-search-net",
        "tbhook-glass-bg-decode",
    )

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedCompat.attachModule(this)
        processName = param.processName
        XposedCompat.log("[MainHook] onModuleLoaded: process=${param.processName}")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val app = sAppContext as? Application
        val state = Bundle().apply {
            putString("processName", processName)
            putInt("registeredHookCount", Api102HookRegistry.registeredCount())
            putBoolean("attachHookInstalled", sAttachHookInstalled)
            putBoolean("staticHooksInstalled", sStaticHooksInstalled)
            putBoolean("postAttachStaticHooksInstalled", sPostAttachStaticHooksInstalled)
            putBoolean("symbolHooksInstalled", sSymbolHooksInstalled)
            putBoolean("pendingAutoSignIn", pendingAutoSignInRunnable != null)
        }
        param.setSavedInstanceState(state)

        if (app == null) {
            XposedCompat.log("[MainHook] onHotReloading: reject, app context unavailable")
            return false
        }
        val busyReasons = hotReloadBusyReasons()
        if (busyReasons.isNotEmpty()) {
            XposedCompat.log("[MainHook] onHotReloading: reject, busy=${busyReasons.joinToString("; ")}")
            return false
        }

        val modelStatsReady = CustomPostModelScoreStats.prepareForHotReload()
        val blockStatsReady = BlockCountStats.prepareForHotReload()
        val collectionSearchReady = CollectionSearchHook.prepareForHotReload()
        val homeNativeGlassReady = HomeNativeGlassHook.prepareForHotReload()
        if (!modelStatsReady || !blockStatsReady || !collectionSearchReady || !homeNativeGlassReady) {
            XposedCompat.log(
                "[MainHook] onHotReloading: reject, executor shutdown timeout " +
                    "modelStats=$modelStatsReady blockStats=$blockStatsReady " +
                    "collectionSearch=$collectionSearchReady homeNativeGlass=$homeNativeGlassReady"
            )
            return false
        }
        cancelPendingAutoSignIn()
        ModuleForegroundActivityTracker.prepareForHotReload()
        AutoRefreshHook.prepareForHotReload()
        SystemBarCompatHook.prepareForHotReload()
        AboutInfoManager.prepareForHotReload()
        ConfigManager.prepareForHotReload()

        XposedCompat.log(
            "[MainHook] onHotReloading: accept, registeredHooks=${Api102HookRegistry.registeredCount()}"
        )
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        XposedCompat.attachModule(this)
        processName = param.processName
        val savedState = param.savedInstanceState as? Bundle
        restoreAutoSignInAfterHotReload = savedState?.getBoolean("pendingAutoSignIn", false) == true
        Api102HookRegistry.beginHotReloadReplacement(param.oldHookHandles)
        try {
            val app = recoverCurrentApplication()
            val cl = recoverTargetClassLoader(app, param.oldHookHandles)
            if (app == null || cl == null || app.packageName != Constants.TARGET_PACKAGE) {
                XposedCompat.log(
                    "[MainHook] onHotReloaded: unable to recover target context, " +
                        "app=${app?.packageName} cl=$cl oldHandles=${param.oldHookHandles.size}"
                )
                return
            }

            XposedCompat.log(
                "[MainHook] onHotReloaded: reinstall process=$processName " +
                    "oldHandles=${param.oldHookHandles.size}"
            )
            installStaticHooks(cl)
            installApplicationAttachHook(cl)
            handleApplicationReady(app, cl, fromHotReload = true)
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] onHotReloaded reinstall FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            val summary = Api102HookRegistry.finishHotReloadReplacement()
            restoreAutoSignInAfterHotReload = false
            XposedCompat.log(
                "[MainHook] onHotReloaded: replacement summary " +
                    "oldTotal=${summary.oldTotal} oldWithId=${summary.oldWithId} " +
                    "replaced=${summary.replaced} unhookedStale=${summary.unhookedStale} " +
                    "registered=${Api102HookRegistry.registeredCount()} errors=${summary.errors.size}"
            )
            summary.errors.take(5).forEach { error ->
                XposedCompat.log("[MainHook] onHotReloaded replacement issue: $error")
            }
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        XposedCompat.log("[MainHook] onPackageLoaded: pkg=${param.packageName}, cl=${param.defaultClassLoader}")

        if (param.packageName != Constants.TARGET_PACKAGE) return
        // Titan patch loading only runs in the main process.
        if (!HookProcess.isMain(processName)) return
        // Install before Application.attach so Titan patch loading can be blocked early.
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
            detach()
            return
        }
        if (!shouldHandleProcess(processName)) {
            XposedCompat.logD("[MainHook] onPackageReady: SKIP - process not in hook whitelist ($processName)")
            detach()
            return
        }
        val cl = param.classLoader
        XposedCompat.log("[MainHook] onPackageReady: using app classloader=$cl")

        handleLoadPackage(param.packageName, cl)
    }

    private fun handleLoadPackage(packageName: String, cl: ClassLoader) {
        XposedCompat.log("[MainHook] handleLoadPackage: pkg=$packageName, cl=$cl")
        installStaticHooks(cl)

        if (!shouldInstallAttachHook(processName)) {
            XposedCompat.logD("[MainHook] Application.attach hook skipped by process whitelist: $processName")
            return
        }

        installApplicationAttachHook(cl)
    }

    private fun installStaticHooks(cl: ClassLoader) {
        val staticPlan = HookInstallPlanner.staticPlan(processName)
        if (staticPlan.isEmpty()) {
            XposedCompat.logD("[MainHook] static hook plan empty for process=$processName, skip")
        } else if (markStaticHooksInstalled()) {
            try {
                XposedCompat.log("[MainHook] initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
                HookInstaller.install(staticPlan, cl)
                XposedCompat.log("[MainHook] All static hooks dispatched.")
            } catch (t: Throwable) {
                synchronized(this) { sStaticHooksInstalled = false }
                XposedCompat.log("[MainHook] static hook install FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        } else {
            XposedCompat.log("[MainHook] static hooks already installed, skip duplicate install")
        }
    }

    private fun installApplicationAttachHook(cl: ClassLoader) {
        if (!markAttachHookInstalled()) {
            XposedCompat.log("[MainHook] Application.attach hook already installed, skip")
            return
        }

        try {
            val attachMethod = android.app.Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
            attachMethod.isAccessible = true
            XposedCompat.log("[MainHook] Application.attach method found, installing hook...")

            XposedCompat.interceptHook("MainHook.ApplicationAttach", attachMethod) { chain ->
                XposedCompat.log("[MainHook] > Application.attach INTERCEPTED, thisObj=${chain.thisObject?.javaClass?.name}")
                val result = chain.proceed()
                XposedCompat.log("[MainHook] > Application.attach proceed() returned")

                val app = chain.thisObject as? Application
                if (app == null) {
                    XposedCompat.log("[MainHook] > Symbol hooks skipped: app=false")
                } else {
                    handleApplicationReady(app, cl, fromHotReload = false)
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

    private fun handleApplicationReady(
        app: Application,
        cl: ClassLoader,
        fromHotReload: Boolean,
    ) {
        if (sAppContext == null) {
            sAppContext = app
            ConfigManager.init(app)
            XposedCompat.log("[MainHook] > ConfigManager initialized, app=${app.packageName}")
            val isMainProcess = HookProcess.isMain(processName)
            if (isMainProcess) {
                ModuleForegroundActivityTracker.register(app)
                val startupSettings = ConfigManager.snapshot()
                if (startupSettings.isAutoRefreshDisabled) {
                    AutoRefreshHook.registerForegroundCallbacks(app)
                }
                if (startupSettings.isMineTabWebAdBlockEnabled) {
                    MineTabWebBlockHook.onAppContextReady(app)
                }
                runStartupTask("apply cached runtime controls") {
                    AboutInfoManager.applyCachedRuntimeControlsIfNeeded(app)
                }
                runStartupTask("restore legacy component state") {
                    ComponentDisableHook.apply(app)
                }
                if (startupSettings.isTitanPatchBlockEnabled) {
                    runStartupTask("delete Titan patch files") {
                        TitanPatchBlockHook.deletePatchFiles(app)
                    }
                }
                if (startupSettings.isTitanPatchBlockEnabled || startupSettings.isDetailedLoggingEnabled) {
                    runStartupTask("log Titan startup") {
                        logTitanStartupIfNeeded(app, cl)
                    }
                }
                if (startupSettings.isAutoSignInEnabled && (!fromHotReload || restoreAutoSignInAfterHotReload)) {
                    runStartupTask("schedule auto sign in") {
                        scheduleAutoSignIn(app)
                    }
                }
            } else {
                XposedCompat.log("[MainHook] > Skip auto sign in non-main process: $processName")
            }
        }

        val appContext = sAppContext
        if (appContext == null) {
            XposedCompat.log("[MainHook] > Symbol hooks skipped: appContext=false")
            return
        }

        val symbolLoadResult = try {
            loadCachedSymbolsOrUnsupported(appContext, cl)
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] > Symbol cache load FAILED, pending scan: ${t.message}")
            XposedCompat.log(t)
            SymbolLoadResult(
                symbols = HookSymbols.unsupported(
                    featureStatusMap = HookSymbolResolver.featureStatusMap(null),
                ),
                pendingScan = true,
            )
        }
        val symbols = symbolLoadResult.symbols
        if (!symbolLoadResult.pendingScan) {
            ConfigManager.applyScanAvailability(
                appContext,
                HookSymbolResolver.featureStatusMap(symbols),
                refreshRuntime = true,
            )
        }
        val isMainProcess = HookProcess.isMain(processName)
        val settingsSnapshot = ConfigManager.snapshot()
        if (isMainProcess) {
            ConfigManager.formatPerformanceStatusLines(settingsSnapshot).forEach { line ->
                XposedCompat.log("[MainHook] > $line")
            }
        }
        if (isMainProcess && shouldMaintainCustomPostModelScoreStats(settingsSnapshot)) {
            runStartupTask("trim model score stats") {
                CustomPostModelScoreStats.trimToPostLimitAsync(
                    settingsSnapshot.postModelScoreStatsPostLimit,
                )
            }
            runStartupTask("apply auto percentile thresholds") {
                CustomPostModelScoreStats.applyAutoPercentileThresholdsAsync()
            }
        }
        if (markPostAttachStaticHooksInstalled()) {
            HookInstaller.install(
                HookInstallPlanner.postAttachPlan(
                    processName = processName,
                    symbols = symbols,
                    settings = settingsSnapshot,
                ),
                cl,
            )
        }

        if (!markSymbolHooksInstalled()) {
            XposedCompat.log("[MainHook] > Symbol hooks skipped: alreadyInstalled=$sSymbolHooksInstalled")
            return
        }

        val symbolPlan = HookInstallPlanner.symbolPlan(
            processName = processName,
            symbols = symbols,
            settings = ConfigManager.snapshot(),
        )
        if (!isMainProcess) {
            HookInstaller.install(symbolPlan, cl)
            XposedCompat.log("[MainHook] > Skip remaining symbol-dependent hooks in non-main process: $processName")
            return
        }

        XposedCompat.log("[MainHook] > Installing symbol-dependent hooks...")
        val needsInitialScanDialog = symbolLoadResult.pendingScan || symbols.source == "unsupported"
        if (needsInitialScanDialog) {
            XposedCompat.log("[MainHook] > Symbols unavailable, installing initial scan dialog hook")
            SettingsMenuHook.ensureInitialScanDialogHook(cl)
        } else if (ConfigManager.hasPendingPostScanEnvironmentWarning(appContext)) {
            XposedCompat.log("[MainHook] > Installing post-scan environment warning hook")
            SettingsMenuHook.ensurePostScanEnvironmentWarningHook()
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

            HookInstaller.install(symbolPlan, cl)

        } catch (t: Throwable) {
            synchronized(this@MainHook) { sSymbolHooksInstalled = false }
            if (needsInitialScanDialog) {
                XposedCompat.log("[MainHook] > Reinstall initial scan dialog hook after symbol init failure")
                SettingsMenuHook.ensureInitialScanDialogHook(cl)
            }
            XposedCompat.log("[MainHook] > symbol hook init FAILED: ${t.message}")
            XposedCompat.log(t)
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
            symbols = HookSymbols.unsupported(
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

    private fun shouldMaintainCustomPostModelScoreStats(settings: SettingsSnapshot): Boolean {
        return settings.isCustomPostFilterEnabled && settings.isPostModelScoreFilterEnabled
    }

    private fun runStartupTask(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] startup task FAILED: $name, ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun scheduleAutoSignIn(app: Application) {
        cancelPendingAutoSignIn()
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            pendingAutoSignInHandler = null
            pendingAutoSignInRunnable = null
            runStartupTask("auto sign in") {
                AutoSignInManager.tryAutoSignIn(app)
            }
        }
        pendingAutoSignInHandler = handler
        pendingAutoSignInRunnable = runnable
        if (!handler.postDelayed(runnable, 5000L)) {
            pendingAutoSignInHandler = null
            pendingAutoSignInRunnable = null
            XposedCompat.logW("[MainHook] auto sign in schedule failed")
        }
    }

    private fun cancelPendingAutoSignIn() {
        val handler = pendingAutoSignInHandler
        val runnable = pendingAutoSignInRunnable
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable)
        }
        pendingAutoSignInHandler = null
        pendingAutoSignInRunnable = null
    }

    private fun hotReloadBusyReasons(): List<String> {
        val reasons = ArrayList<String>()
        AboutInfoManager.hotReloadBusyReason()?.let { reasons += it }
        CustomPostModelScoreStats.hotReloadBusyReason()?.let { reasons += it }
        BlockCountStats.hotReloadBusyReason()?.let { reasons += it }
        CollectionSearchHook.hotReloadBusyReason()?.let { reasons += it }
        HomeNativeGlassHook.hotReloadBusyReason()?.let { reasons += it }
        AutoSignInManager.hotReloadBusyReason()?.let { reasons += it }
        reasons += unmanagedModuleThreadBusyReasons()
        return reasons
    }

    private fun unmanagedModuleThreadBusyReasons(): List<String> {
        return Thread.getAllStackTraces().keys
            .asSequence()
            .filter { thread -> thread !== Thread.currentThread() }
            .filter { thread -> thread.isAlive }
            .map { thread -> thread.name.orEmpty() }
            .filter { name -> name.startsWith("tbhook-") }
            .filter { name -> name !in hotReloadManagedThreadNames }
            .distinct()
            .sorted()
            .map { name -> "module thread active: $name" }
            .toList()
    }

    private fun recoverCurrentApplication(): Application? {
        (sAppContext as? Application)?.let { return it }
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThread.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }
            currentApplication.invoke(null) as? Application
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] recover current application failed: ${t.message}")
            null
        }
    }

    private fun recoverTargetClassLoader(
        app: Application?,
        oldHandles: List<io.github.libxposed.api.XposedInterface.HookHandle>,
    ): ClassLoader? {
        if (app?.packageName == Constants.TARGET_PACKAGE) {
            app.classLoader?.let { return it }
        }
        val moduleClassLoader = MainHook::class.java.classLoader
        for (handle in oldHandles) {
            val loader = runCatching {
                handle.executable.declaringClass.classLoader
            }.getOrNull() ?: continue
            if (loader !== moduleClassLoader) return loader
        }
        return null
    }

    private fun isTargetProcess(name: String): Boolean {
        return HookProcess.isTargetTiebaProcess(name)
    }

    private fun shouldHandleProcess(name: String): Boolean {
        return HookInstallPlanner.shouldHandleProcess(name)
    }

    private fun shouldInstallAttachHook(name: String): Boolean {
        // Only the main process and image-viewer remote process need attach hooks.
        return HookInstallPlanner.shouldInstallAttachHook(name)
    }

}
