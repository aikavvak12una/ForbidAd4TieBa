package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotHook
import com.forbidad4tieba.hook.feature.ui.MainTabBottomHook
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class MainHook : XposedModule() {

    @Volatile private var sAppContext: Context? = null
    @Volatile private var sSymbolHooksInstalled = false
    private var processName: String = ""
    private var loadedParam: PackageLoadedParam? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedCompat.module = this
        processName = param.processName
        XposedCompat.log("[MainHook] onModuleLoaded: process=${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        loadedParam = param
        XposedCompat.log("[MainHook] onPackageLoaded: pkg=${param.packageName}, cl=${param.defaultClassLoader}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        XposedCompat.log("[MainHook] onPackageReady: pkg=${param.packageName}, process=$processName")

        val lp = loadedParam
        if (lp == null) {
            XposedCompat.log("[MainHook] onPackageReady: ABORT - loadedParam is null")
            return
        }
        if (lp.packageName != Constants.TARGET_PACKAGE || processName != Constants.TARGET_PACKAGE) {
            XposedCompat.log("[MainHook] onPackageReady: SKIP - not target (lp.pkg=${lp.packageName}, process=$processName)")
            return
        }

        handleLoadPackage(lp.packageName, lp.defaultClassLoader)
    }

    private fun handleLoadPackage(packageName: String, cl: ClassLoader) {
        XposedCompat.log("[MainHook] handleLoadPackage: pkg=$packageName, cl=$cl")
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

                        // Delayed auto sign-in to avoid startup slowdown
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(app)
                        }, 5000)
                    }
                }

                val appContext = sAppContext
                if (appContext != null && markSymbolHooksInstalled()) {
                    XposedCompat.log("[MainHook] > Installing symbol-dependent hooks...")
                    try {
                        val symbols = HookSymbolResolver.loadCachedIfUsable(
                            context = appContext,
                            cl = cl,
                        ) ?: HookSymbols(source = "unsupported")
                        XposedCompat.log("[MainHook] > Symbols loaded: source=${symbols.source}, settings=${symbols.settingsClass}, home=${symbols.homeTabClass}")

                        SettingsMenuHook.hook(cl, symbols)
                        HomeTabHook.hook(cl, symbols)
                        StrategyAdHook.hookWithSymbols(cl, symbols)

                        com.forbidad4tieba.hook.feature.ui.AutoRefreshHook.hook(cl)

                        com.forbidad4tieba.hook.feature.ui.AutoLoadMoreHook.hook(cl)

                        if (symbols.source == "unsupported") {
                            XposedCompat.log("[MainHook] > Symbols unsupported, installing initial scan dialog hook")
                            SettingsMenuHook.ensureInitialScanDialogHook(cl)
                        }
                    } catch (t: Throwable) {
                        XposedCompat.log("[MainHook] > symbol hook init FAILED: ${t.message}")
                        XposedCompat.log(t)
                    }
                } else {
                    XposedCompat.log("[MainHook] > Symbol hooks skipped: appContext=${appContext != null}, alreadyInstalled=$sSymbolHooksInstalled")
                }

                result
            }
            XposedCompat.log("[MainHook] Application.attach hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[MainHook] FAILED to hook Application.attach: ${t.message}")
            XposedCompat.log(t)
        }

        XposedCompat.log("[MainHook] initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")

        // 1. Strategy layer: fake VIP ad-free status & SDK switches
        XposedCompat.log("[MainHook] Installing StrategyAdHook (static)...")
        StrategyAdHook.hookStatic(cl)

        // 2. Feed page (RecyclerView): filter ad cards via data interception
        XposedCompat.log("[MainHook] Installing FeedAdHook...")
        FeedAdHook.hook(cl)

        // 3. Post page (TypeAdapter/ListView): replace ad views with empty views
        XposedCompat.log("[MainHook] Installing PostAdHook...")
        PostAdHook.hook(cl)

        // 5. Hide post bottom enter bar
        XposedCompat.log("[MainHook] Installing PbBottomEnterBarHook...")
        com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook.hook(cl)

        // Hide top game icon and show search
        XposedCompat.log("[MainHook] Installing HomeTopBarRightSlotHook...")
        HomeTopBarRightSlotHook.hook(cl)

        // Simplify bottom tab (Hide Store/Op tab)
        XposedCompat.log("[MainHook] Installing MainTabBottomHook...")
        MainTabBottomHook.hook(cl)

        // 6. Enter Forum Page DOM manipulation
        XposedCompat.log("[MainHook] Installing EnterForumWebHook...")
        EnterForumWebHook.hook(cl)

        XposedCompat.log("[MainHook] All static hooks dispatched.")
    }

    private fun markSymbolHooksInstalled(): Boolean {
        synchronized(this) {
            if (sSymbolHooksInstalled) return false
            sSymbolHooksInstalled = true
            return true
        }
    }
}
