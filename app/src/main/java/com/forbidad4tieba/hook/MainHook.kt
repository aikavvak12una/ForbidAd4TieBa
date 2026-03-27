package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.feature.ad.FeedAdHook
import com.forbidad4tieba.hook.feature.ad.PostAdHook
import com.forbidad4tieba.hook.feature.ad.StrategyAdHook
import com.forbidad4tieba.hook.feature.ui.BottomTabHook
import com.forbidad4tieba.hook.feature.ui.HomeTabHook
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
                            // STRICT APPLICATION CHECK to avoid Context leaks
                            val app = param.thisObject as? android.app.Application ?: return
                            sAppContext = app
                            // Initialize our fast, in-memory ConfigManager
                            ConfigManager.init(app)
                            
                            // 后台静默执行贴吧全量签到 (延时避免影响启动速度)
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

                            if (symbols.source == "unsupported" && lpparam.processName == Constants.TARGET_PACKAGE) {
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

        // 2. Resolve ad classes once, shared by feed & post hooks
        val adClasses = resolveAdClasses(cl)

        // 3. Feed page (RecyclerView): hide ad views
        FeedAdHook.hook(cl, adClasses)

        // 4. Post page (TypeAdapter/ListView): replace ad views with empty views
        PostAdHook.hook(cl, adClasses)

        // 5. Bottom Tabs (Hide Small Shop)
        BottomTabHook.hook(cl)

        // 6. Hide post bottom enter bar
        com.forbidad4tieba.hook.feature.ui.PbBottomEnterBarHook.hook(cl)

        // 7. Enter Forum Page DOM manipulation
        EnterForumWebHook.hook()
    }

    private fun resolveAdClasses(cl: ClassLoader): Array<Class<*>> {
        val list = ArrayList<Class<*>>(Constants.AD_CLASS_NAMES.size)
        for (name in Constants.AD_CLASS_NAMES) {
            val c = XposedHelpers.findClassIfExists(name, cl)
            if (c != null) list.add(c)
        }
        return list.toTypedArray()
    }

    private fun markSymbolHooksInstalled(): Boolean {
        synchronized(this) {
            if (sSymbolHooksInstalled) return false
            sSymbolHooksInstalled = true
            return true
        }
    }
}
