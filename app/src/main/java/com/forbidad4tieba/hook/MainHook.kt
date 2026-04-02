package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.ui.BottomTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTopBarRightSlotHook
import com.forbidad4tieba.hook.feature.web.EnterForumWebHook
import com.forbidad4tieba.hook.ui.SettingsMenuHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    @Volatile private var sAppContext: Context? = null
    @Volatile private var sSymbolHooksInstalled = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // STRICT PROCESS ISOLATION: Only hook the main package and the main process
        if (lpparam.packageName != Constants.TARGET_PACKAGE || lpparam.processName != Constants.TARGET_PACKAGE) return

        try {
            XposedHelpers.findAndHookMethod(
                android.app.Application::class.java, "attach",
                Context::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (sAppContext == null) {
                            val app = param.thisObject as? android.app.Application ?: return
                            sAppContext = app
                            ConfigManager.init(app)

                            // Delayed auto sign-in to avoid startup slowdown
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                com.forbidad4tieba.hook.feature.signin.AutoSignInManager.tryAutoSignIn(app)
                            }, 5000)
                        }
                        val appContext = sAppContext ?: return
                        if (!markSymbolHooksInstalled()) return

                        try {
                            val symbols = HookSymbolResolver.loadCachedIfUsable(
                                context = appContext,
                                cl = lpparam.classLoader,
                            ) ?: HookSymbols(source = "unsupported")

                            SettingsMenuHook.hook(lpparam.classLoader, symbols)
                            HomeTabHook.hook(lpparam.classLoader, symbols)

                            // Auto refresh hook (requires resolved symbol for method name)
                            com.forbidad4tieba.hook.feature.ui.AutoRefreshHook.hook(
                                lpparam.classLoader,
                                symbols.personalizeRefreshMethod,
                            )
                            
                            // 自动加载更多 (无缝滑动)
                            com.forbidad4tieba.hook.feature.ui.AutoLoadMoreHook.hook(lpparam.classLoader)

                            if (symbols.source == "unsupported") {
                                SettingsMenuHook.ensureInitialScanDialogHook(lpparam.classLoader)
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log("${Constants.TAG}: symbol hook init failed: ${t.message}")
                        }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook Application.attach: ${t.message}")
        }

        XposedBridge.log("${Constants.TAG}: initialized. version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")

        val cl = lpparam.classLoader

        // 1. Strategy layer: fake VIP ad-free status & SDK switches
        StrategyAdHook.hook(cl)

        // 2. Feed page (RecyclerView): filter ad cards via data interception
        FeedAdHook.hook(cl)

        // 3. Post page (TypeAdapter/ListView): replace ad views with empty views
        PostAdHook.hook(cl)

        // 4. Bottom Tabs (Hide Small Shop)
        BottomTabHook.hook(cl)

        // 5. Hide post bottom enter bar
        com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook.hook(cl)

        // Hide top game icon and show search
        HomeTopBarRightSlotHook.hook(cl)

        // 6. Enter Forum Page DOM manipulation
        EnterForumWebHook.hook(cl)
    }

    private fun markSymbolHooksInstalled(): Boolean {
        synchronized(this) {
            if (sSymbolHooksInstalled) return false
            sSymbolHooksInstalled = true
            return true
        }
    }
}
