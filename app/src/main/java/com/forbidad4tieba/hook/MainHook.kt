package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreStats
import com.forbidad4tieba.hook.feature.perf.ComponentDisableHook
import com.forbidad4tieba.hook.feature.perf.TitanPatchBlockHook
import com.forbidad4tieba.hook.feature.web.MineTabWebBlockHook
import com.forbidad4tieba.hook.ui.AboutInfoManager
import com.forbidad4tieba.hook.ui.ModuleForegroundActivityTracker
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import io.github.libxposed.api.XposedModule
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
                        XposedCompat.log("[MainHook] > ConfigManager initialized, app=${app.packageName}")
                        val isMainProcess = HookProcess.isMain(processName)
                        if (isMainProcess) {
                            ModuleForegroundActivityTracker.register(app)
                            val startupSettings = ConfigManager.snapshot()
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
                            if (startupSettings.isAutoSignInEnabled) {
                                runStartupTask("schedule auto sign in") {
                                    // Delay auto sign-in to keep startup hooks light.
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        runStartupTask("auto sign in") {
                                            com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(app)
                                        }
                                    }, 5000)
                                }
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
                    return@intercept result
                }

                val symbolPlan = HookInstallPlanner.symbolPlan(
                    processName = processName,
                    symbols = symbols,
                    settings = ConfigManager.snapshot(),
                )
                if (!isMainProcess) {
                    HookInstaller.install(symbolPlan, cl)
                    XposedCompat.log("[MainHook] > Skip remaining symbol-dependent hooks in non-main process: $processName")
                    return@intercept result
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

                result
            }
            XposedCompat.log("[MainHook] Application.attach hook INSTALLED")
        } catch (t: Throwable) {
            synchronized(this) { sAttachHookInstalled = false }
            XposedCompat.log("[MainHook] FAILED to hook Application.attach: ${t.message}")
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
