package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Applies host configuration overrides. Obfuscated targets are restored by the symbol domain. */
object HostPerformanceConfigHook {
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val INIT_FLUTTER_NPS_PLUGIN_TASK_CLASS =
        "com.baidu.searchbox.task.sync.appcreate.InitFlutterNpsPluginTask"

    private const val PREF_FUN_AD_SDK_ENABLE = "pref_key_fun_ad_sdk_enable"
    private const val PREF_SPLASH_PLG_ENABLE = "key_splash_new_policy_plg_enable"
    private const val PREF_SPLASH_PLG_CPC_ENABLE = "key_splash_new_policy_plg_cpc_enable"
    private const val PREF_SPLASH_SHAKE_AD_OPEN = "key_splash_shake_ad_open"
    private const val PREF_LOW_SCORE_THRESHOLD = "sp_mid_score_device_config"
    private const val FORCED_LOW_DEVICE_SCORE = -1.0
    private const val FORCED_LOW_SCORE_THRESHOLD = 100.0f

    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }

    @Synchronized
    internal fun hook(cl: ClassLoader, lowEndTargets: Map<LowEndConfigTarget, Method>): InstallOutcome {
        val settings = ConfigManager.snapshot()
        if (!isAnyConfigOverrideEnabled(settings)) {
            return InstallOutcome.skipped("config disabled")
        }
        val mod = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val before = installedMethods.size()
        var count = 0
        var missing = 0
        val failures = ArrayList<String>()

        // Each override is useful independently. Keep successes and retry only missing/failed points.
        fun install(
            label: String,
            resolve: () -> Method?,
            create: (Method) -> XposedInterface.HookHandle,
        ) {
            try {
                val method = resolve()
                if (method == null) {
                    missing++
                    failures += "$label missing"
                    return
                }
                installedMethods.install(method) {
                    method.isAccessible = true
                    create(method)
                }
                count++
            } catch (failure: Throwable) {
                failures += "$label: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }

        if (settings.isAdSdkComponentsDisabled) {
            install("ad.getInt", {
                MemberAccess.findMethodOrNull(
                    SHARED_PREF_HELPER_CLASS, cl, "getInt", String::class.java, Int::class.javaPrimitiveType!!,
                )
            }) { method ->
                RuntimeHooks.builder(mod, method, "HostPerformanceConfigHook", "install:method").intercept { chain ->
                    if (!ConfigManager.snapshot().isAdSdkComponentsDisabled) return@intercept chain.proceed()
                    val key = chain.args.firstOrNull() as? String
                    when (key) {
                        PREF_FUN_AD_SDK_ENABLE, PREF_SPLASH_PLG_ENABLE, PREF_SPLASH_PLG_CPC_ENABLE -> 0
                        else -> chain.proceed()
                    }
                }
            }
            install("ad.getBoolean", {
                MemberAccess.findMethodOrNull(
                    SHARED_PREF_HELPER_CLASS, cl, "getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!,
                )
            }) { method ->
                RuntimeHooks.builder(mod, method, "HostPerformanceConfigHook", "install:method:2").intercept { chain ->
                    if (!ConfigManager.snapshot().isAdSdkComponentsDisabled) return@intercept chain.proceed()
                    val key = chain.args.firstOrNull() as? String
                    if (key == PREF_SPLASH_SHAKE_AD_OPEN) return@intercept false
                    chain.proceed()
                }
            }
        }

        if (settings.isLowEndDeviceConfigForced) {
            for (target in LowEndConfigTarget.entries) {
                install(target.name, { lowEndTargets[target] }) { method ->
                    // Created once for the string callback, only after checking for an existing handle.
                    val policies = if (target == LowEndConfigTarget.STRING_CONFIG) LowEndConfigPolicyCache() else null
                    RuntimeHooks.builder(mod, method, "HostPerformanceConfigHook", "install:method:3").intercept { chain ->
                        val current = ConfigManager.snapshot()
                        if (!current.isLowEndDeviceConfigForced) return@intercept chain.proceed()
                        when (target) {
                            LowEndConfigTarget.THRESHOLD ->
                                if (chain.args[0] == PREF_LOW_SCORE_THRESHOLD) FORCED_LOW_SCORE_THRESHOLD else chain.proceed()
                            LowEndConfigTarget.DEVICE_SCORE -> FORCED_LOW_DEVICE_SCORE
                            LowEndConfigTarget.STRING_CONFIG -> {
                                val key = chain.args[0] as? String
                                if (key == LowEndConfigPolicy.BLOCK_LIST || key == LowEndConfigPolicy.KV_CONFIG) {
                                    val original = chain.proceed() as? String ?: ""
                                    policies!!.apply(key, original, current.isPbPreloadForced)
                                } else chain.proceed()
                            }
                        }
                    }
                }
            }
        }

        if (settings.isFlutterPreinitDisabled) {
            for (methodName in arrayOf("execute", "initFlutterPlugin")) {
                install("flutter.$methodName", {
                    MemberAccess.findMethodOrNull(INIT_FLUTTER_NPS_PLUGIN_TASK_CLASS, cl, methodName)
                }) { method ->
                    RuntimeHooks.builder(mod, method, "HostPerformanceConfigHook", "install:method:4").intercept { chain ->
                        if (ConfigManager.snapshot().isFlutterPreinitDisabled) return@intercept null
                        chain.proceed()
                    }
                }
            }
        }

        val state = when {
            count == 0 && missing == failures.size -> InstallState.SKIPPED
            count == 0 -> InstallState.FAILED
            failures.isNotEmpty() -> InstallState.PARTIAL
            installedMethods.size() == before -> InstallState.ALREADY_INSTALLED
            else -> InstallState.INSTALLED
        }
        return InstallOutcome(state, count, failures.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    private fun isAnyConfigOverrideEnabled(settings: SettingsSnapshot): Boolean {
        return settings.isAdSdkComponentsDisabled ||
            settings.isFlutterPreinitDisabled ||
            settings.isLowEndDeviceConfigForced
    }

}
