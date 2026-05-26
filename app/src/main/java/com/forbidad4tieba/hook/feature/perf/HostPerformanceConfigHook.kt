package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 覆盖目标应用稳定性能配置的读取结果。
 *
 * 这个 hook 不处理混淆业务类，只碰稳定的辅助类和配置类。
 * 复杂策略提前算成 ConfigManager 里的布尔值。
 */
object HostPerformanceConfigHook {
    private const val TAG = "[HostPerformanceConfigHook]"
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val MULTI_SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.MultiSharedPrefHelper"
    private const val INIT_FLUTTER_NPS_PLUGIN_TASK_CLASS =
        "com.baidu.searchbox.task.sync.appcreate.InitFlutterNpsPluginTask"
    private const val SCHEDULE_STRATEGY_CLASS = "com.baidu.searchbox.launch.ScheduleStrategy"

    private const val PREF_FUN_AD_SDK_ENABLE = "pref_key_fun_ad_sdk_enable"
    private const val PREF_SPLASH_PLG_ENABLE = "key_splash_new_policy_plg_enable"
    private const val PREF_SPLASH_PLG_CPC_ENABLE = "key_splash_new_policy_plg_cpc_enable"
    private const val PREF_SPLASH_SHAKE_AD_OPEN = "key_splash_shake_ad_open"
    private const val PREF_LOW_SCORE_THRESHOLD = "sp_low_score_device_config"
    private const val PREF_LOW_DEV_FORBID_LIST = "low_dev_forbid_list"
    private const val FORCED_LOW_DEVICE_SCORE = -1.0
    private const val FORCED_LOW_SCORE_THRESHOLD = 100.0f
    private const val LOW_DEV_DISABLE_RE_RUN_IDLE = "disable_re_run_idle"
    private const val LOW_DEV_DISABLE_DU_MEDIA_PRE_INSTALL = "disable_du_media_pre_install"
    private const val LOW_DEV_DISABLE_PRELOAD_FEED_IMAGE = "disable_preload_feed_image"
    private const val LOW_DEV_DISABLE_HOME_ANIMATION = "disable_home_animation"
    private const val LOW_DEV_DISABLE_PRELOAD_HOME_DATA = "disable_preload_home_data"
    private const val LOW_DEV_DISABLE_WEBVIEW_PROXY = "disable_webview_proxy"
    private const val LOW_DEV_MI_13_FORCE_DISABLE = "mi_13_force_disable"

    private val lowDevDefaultForbidListJson = buildLowDevForbidListJson(
        includeHomeData = true,
    )
    private val lowDevAutoLoadDefaultForbidListJson = buildLowDevForbidListJson(
        includeHomeData = false,
    )

    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        if (!isAnyConfigOverrideEnabled()) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        totalInstalled += hookSharedPrefHelper(mod, cl)
        totalInstalled += hookMultiSharedPrefHelper(mod, cl)
        totalInstalled += hookScheduleStrategy(mod, cl)
        totalInstalled += hookFlutterPreinitTask(mod, cl)

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            installed.set(false)
            XposedCompat.logD("$TAG no host config methods found")
        }
    }

    private fun hookSharedPrefHelper(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(SHARED_PREF_HELPER_CLASS, cl) ?: return 0
        var installedCount = 0

        XposedCompat.findMethodOrNull(
            clazz,
            "getInt",
            String::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    when {
                        ConfigManager.isAdSdkComponentsDisabled && key == PREF_FUN_AD_SDK_ENABLE -> 0
                        else -> chain.proceed()
                    }
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG getInt skipped: ${it.message}" } }
        }

        XposedCompat.findMethodOrNull(
            clazz,
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (
                        ConfigManager.isAdSdkComponentsDisabled &&
                        (key == PREF_SPLASH_PLG_ENABLE ||
                            key == PREF_SPLASH_PLG_CPC_ENABLE ||
                            key == PREF_SPLASH_SHAKE_AD_OPEN)
                    ) {
                        return@intercept false
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG getBoolean skipped: ${it.message}" } }
        }

        return installedCount
    }

    private fun hookMultiSharedPrefHelper(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(MULTI_SHARED_PREF_HELPER_CLASS, cl) ?: return 0
        var installedCount = 0

        XposedCompat.findMethodOrNull(
            clazz,
            "a",
            String::class.java,
            Float::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (ConfigManager.isLowEndDeviceConfigForced && key == PREF_LOW_SCORE_THRESHOLD) {
                        return@intercept FORCED_LOW_SCORE_THRESHOLD
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG low score threshold skipped: ${it.message}" } }
        }

        XposedCompat.findMethodOrNull(
            clazz,
            "c",
            String::class.java,
            String::class.java,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (ConfigManager.isLowEndDeviceConfigForced && key == PREF_LOW_DEV_FORBID_LIST) {
                        return@intercept resolveLowDevForbidListJson()
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG low device list skipped: ${it.message}" } }
        }

        return installedCount
    }

    private fun hookScheduleStrategy(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(SCHEDULE_STRATEGY_CLASS, cl) ?: return 0
        val method = XposedCompat.findMethodOrNull(clazz, "getDeviceScore") ?: return 0
        return runCatching {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (ConfigManager.isLowEndDeviceConfigForced) return@intercept FORCED_LOW_DEVICE_SCORE
                chain.proceed()
            }
            1
        }.onFailure { XposedCompat.logD { "$TAG getDeviceScore skipped: ${it.message}" } }
            .getOrDefault(0)
    }

    private fun hookFlutterPreinitTask(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(INIT_FLUTTER_NPS_PLUGIN_TASK_CLASS, cl) ?: return 0
        var installedCount = 0
        for (methodName in arrayOf("execute", "initFlutterPlugin")) {
            val method = XposedCompat.findMethodOrNull(clazz, methodName) ?: continue
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isFlutterPreinitDisabled) return@intercept null
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG $methodName skipped: ${it.message}" } }
        }
        return installedCount
    }

    private fun isAnyConfigOverrideEnabled(): Boolean {
        return ConfigManager.isAdSdkComponentsDisabled ||
            ConfigManager.isFlutterPreinitDisabled ||
            ConfigManager.isLowEndDeviceConfigForced
    }

    private fun resolveLowDevForbidListJson(): String {
        val keepHomePreloadMore = ConfigManager.isAutoLoadMoreEnabled
        return if (keepHomePreloadMore) {
            lowDevAutoLoadDefaultForbidListJson
        } else {
            lowDevDefaultForbidListJson
        }
    }

    private fun buildLowDevForbidListJson(includeHomeData: Boolean): String {
        val items = ArrayList<String>(9)
        items.add(LOW_DEV_DISABLE_RE_RUN_IDLE)
        items.add(LOW_DEV_DISABLE_DU_MEDIA_PRE_INSTALL)
        items.add(LOW_DEV_DISABLE_PRELOAD_FEED_IMAGE)
        items.add(LOW_DEV_DISABLE_HOME_ANIMATION)
        if (includeHomeData) {
            items.add(LOW_DEV_DISABLE_PRELOAD_HOME_DATA)
        }
        items.add(LOW_DEV_DISABLE_WEBVIEW_PROXY)
        items.add(LOW_DEV_MI_13_FORCE_DISABLE)
        return JSONArray(items).toString()
    }
}
