package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.BannerAdSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object BannerAdHook {
    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }

    @Synchronized
    fun hook(targets: BannerAdSymbols): InstallOutcome {
        if (!ConfigManager.snapshot().isFeedAdBlockEnabled) return InstallOutcome.skipped("config disabled")
        val module = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val method = targets.conversionMethod
        val alreadyInstalled = installedMethods.contains(method)
        return try {
            installedMethods.install(method) {
                val errorLogged = AtomicBoolean(false)
                RuntimeHooks.builder(module, method, "BannerAdHook", "sourceConversion").intercept { chain ->
                    if (!ConfigManager.snapshot().isFeedAdBlockEnabled) return@intercept chain.proceed()
                    val blocked = try {
                        shouldBlock(chain.args.firstOrNull(), targets)
                    } catch (failure: Throwable) {
                        if (errorLogged.compareAndSet(false, true)) {
                            XposedCompat.log("[BannerAdHook] source inspection failed: ${failure.javaClass.simpleName}")
                        }
                        false
                    }
                    if (blocked) {
                        BlockCountStats.recordAd()
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
            InstallOutcome(if (alreadyInstalled) InstallState.ALREADY_INSTALLED else InstallState.INSTALLED, 1)
        } catch (failure: Throwable) {
            InstallOutcome(InstallState.FAILED, 0, "source conversion: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }

    internal fun shouldBlock(layout: Any?, targets: BannerAdSymbols): Boolean {
        if (layout == null || !targets.layoutField.declaringClass.isInstance(layout)) return false
        if (targets.layoutField.get(layout) != "banner_card") return false
        val banner = targets.bannerCardField.get(layout) ?: return false
        val components = targets.componentsField.get(banner) as? List<*> ?: return false
        if (components.isEmpty()) return false
        for (component in components) {
            if (component == null || !targets.componentField.declaringClass.isInstance(component)) return false
            if (targets.componentField.get(component) != "feed_banner" || targets.feedBannerField.get(component) == null) return false
        }
        val parameters = targets.logParamField.get(banner) as? List<*> ?: return false
        var homeBanner = false
        for (parameter in parameters) {
            if (parameter == null || !targets.logKeyField.declaringClass.isInstance(parameter)) return false
            if (targets.logKeyField.get(parameter) != "card_type") continue
            if (targets.logValueField.get(parameter) != "homeBanner") return false
            homeBanner = true
        }
        return homeBanner
    }
}
