package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Overrides stable host performance configuration reads.
 *
 * This hook only touches stable helper/config classes. More complex policy is precomputed into
 * boolean values in ConfigManager.
 *
 * 低端机精简（isLowEndDeviceConfigForced）适配 22.9.1.0 的实际读取点：
 * - ScheduleStrategy.getDeviceScore() -> -1.0（设备分压到低端）
 * - MultiSharedPrefHelper.b("sp_mid_score_device_config") -> 100.0f（低端阈值，sjc.a.b() 恒 true）
 * - MultiSharedPrefHelper.e("low_score_block_list") -> 注入 JSONArray 禁用列表
 *   （disable_preload_feed_image / disable_webview_proxy，被 LowScoreScheduler.C() 消费）
 * - MultiSharedPrefHelper.e("low_score_kv_config") -> 注入 {"disable_re_run_idle":"true"}
 *   （被 LowScoreScheduler.k() 消费）
 * 旧实现 hook 的 sp_low_score_device_config / low_dev_forbid_list / a(Float) / c(String,String)
 * 在 22.9.1.0 无消费点，已修正。
 */
object HostPerformanceConfigHook {
    private const val TAG = "[HostPerformanceConfigHook]"
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    // 注意：宿主实际类是 com.baidu.adp.baes.sharedperf.MultiSharedPrefHelper（sjc/LowScoreScheduler 均引用它），
    // 不是 com.baidu.tbadk.core.sharedPref.MultiSharedPrefHelper（旧常量导致 findClassOrNull 永远失败）
    private const val MULTI_SHARED_PREF_HELPER_CLASS = "com.baidu.adp.baes.sharedperf.MultiSharedPrefHelper"
    private const val INIT_FLUTTER_NPS_PLUGIN_TASK_CLASS =
        "com.baidu.searchbox.task.sync.appcreate.InitFlutterNpsPluginTask"
    private const val SCHEDULE_STRATEGY_CLASS = "com.baidu.searchbox.launch.ScheduleStrategy"

    private const val PREF_FUN_AD_SDK_ENABLE = "pref_key_fun_ad_sdk_enable"
    private const val PREF_SPLASH_PLG_ENABLE = "key_splash_new_policy_plg_enable"
    private const val PREF_SPLASH_PLG_CPC_ENABLE = "key_splash_new_policy_plg_cpc_enable"
    private const val PREF_SPLASH_SHAKE_AD_OPEN = "key_splash_shake_ad_open"
    private const val PREF_LOW_SCORE_THRESHOLD = "sp_mid_score_device_config"
    private const val PREF_LOW_DEV_BLOCK_LIST = "low_score_block_list"
    private const val PREF_LOW_DEV_KV_CONFIG = "low_score_kv_config"
    private const val FORCED_LOW_DEVICE_SCORE = -1.0
    private const val FORCED_LOW_SCORE_THRESHOLD = 100.0f

    // 22.9.1.0 中被 LowScoreScheduler 实际消费的 key
    private const val LOW_DEV_DISABLE_RE_RUN_IDLE = "disable_re_run_idle"
    private const val LOW_DEV_DISABLE_PRELOAD_FEED_IMAGE = "disable_preload_feed_image"
    private const val LOW_DEV_DISABLE_WEBVIEW_PROXY = "disable_webview_proxy"
    private const val LOW_DEV_DEFER_VIDEO_AUTOPLAY_MS = "defer_video_autoplay_ms"
    private const val DEFER_VIDEO_AUTOPLAY_MS = "5000"

    private val installed = AtomicBoolean(false)

    fun hook(cl: ClassLoader) {
        val settings = ConfigManager.snapshot()
        if (!isAnyConfigOverrideEnabled(settings)) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        if (settings.isAdSdkComponentsDisabled) {
            totalInstalled += hookSharedPrefHelper(mod, cl)
        }
        if (settings.isLowEndDeviceConfigForced) {
            totalInstalled += hookMultiSharedPrefHelper(mod, cl)
            totalInstalled += hookScheduleStrategy(mod, cl)
        }
        if (settings.isFlutterPreinitDisabled) {
            totalInstalled += hookFlutterPreinitTask(mod, cl)
        }

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
                        key == PREF_FUN_AD_SDK_ENABLE && ConfigManager.isAdSdkComponentsDisabled -> 0
                        // splash 新策略 plg 开关走 getInt（ad7 读 getInt(key,0)==1），非 getBoolean
                        (key == PREF_SPLASH_PLG_ENABLE || key == PREF_SPLASH_PLG_CPC_ENABLE) &&
                            ConfigManager.isAdSdkComponentsDisabled -> 0
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
                        key == PREF_SPLASH_SHAKE_AD_OPEN &&
                        ConfigManager.isAdSdkComponentsDisabled
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

        // 低端阈值：sjc.a.a() 读 MultiSharedPrefHelper.b("sp_mid_score_device_config", 0.8f)
        XposedCompat.findMethodOrNull(
            clazz,
            "b",
            String::class.java,
            Float::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (key == PREF_LOW_SCORE_THRESHOLD && ConfigManager.isLowEndDeviceConfigForced) {
                        return@intercept FORCED_LOW_SCORE_THRESHOLD
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG low score threshold skipped: ${it.message}" } }
        }

        // 低端禁用列表：LowScoreScheduler.u() 读 MultiSharedPrefHelper.e("low_score_block_list", "")
        // 合并策略：保留服务端已下发的列表，再追加模块项（避免覆盖宿主自身的低端优化）
        XposedCompat.findMethodOrNull(
            clazz,
            "e",
            String::class.java,
            String::class.java,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (key == PREF_LOW_DEV_BLOCK_LIST && ConfigManager.isLowEndDeviceConfigForced) {
                        val original = chain.proceed() as? String ?: ""
                        return@intercept mergeLowDevBlockList(original)
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG low score block list skipped: ${it.message}" } }
        }

        // 低端 KV 配置：LowScoreScheduler.w() 读 MultiSharedPrefHelper.e("low_score_kv_config", "")
        // 合并策略：保留服务端 KV，再写入模块项（disable_re_run_idle 恒 true）
        // （JSON object，供 k("disable_re_run_idle", true) 等读取）
        XposedCompat.findMethodOrNull(
            clazz,
            "e",
            String::class.java,
            String::class.java,
        )?.let { method ->
            runCatching {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (key == PREF_LOW_DEV_KV_CONFIG && ConfigManager.isLowEndDeviceConfigForced) {
                        val original = chain.proceed() as? String ?: ""
                        return@intercept mergeLowDevKvConfig(original)
                    }
                    chain.proceed()
                }
                installedCount++
            }.onFailure { XposedCompat.logD { "$TAG low score kv config skipped: ${it.message}" } }
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

    private fun isAnyConfigOverrideEnabled(settings: SettingsSnapshot): Boolean {
        return settings.isAdSdkComponentsDisabled ||
            settings.isFlutterPreinitDisabled ||
            settings.isLowEndDeviceConfigForced
    }

    private fun moduleLowDevBlockItems(): List<String> {
        val items = ArrayList<String>(2)
        items.add(LOW_DEV_DISABLE_PRELOAD_FEED_IMAGE)
        // disable_webview_proxy 会关闭 WebViewDiskLoader（hybrid 帖子 webview 资源磁盘缓存）。
        // 强制帖子预加载开启时保留该代理，避免 hybrid 帖子页资源回退到纯网络。
        if (!ConfigManager.isPbPreloadForced) {
            items.add(LOW_DEV_DISABLE_WEBVIEW_PROXY)
        }
        return items
    }

    private fun mergeLowDevBlockList(original: String): String {
        val json = try {
            JSONArray(original)
        } catch (t: Throwable) {
            JSONArray()
        }
        val existing = HashSet<String>()
        for (i in 0 until json.length()) existing.add(json.optString(i))
        for (item in moduleLowDevBlockItems()) {
            if (existing.add(item)) json.put(item)
        }
        return json.toString()
    }

    private fun mergeLowDevKvConfig(original: String): String {
        val json = try {
            JSONObject(original)
        } catch (t: Throwable) {
            JSONObject()
        }
        // 覆盖为 true：保证 disable_re_run_idle 恒生效，不受服务端 KV 影响
        json.put(LOW_DEV_DISABLE_RE_RUN_IDLE, "true")
        // 视频自动播放延迟（毫秒）：v1e 经 LowScoreScheduler.m("defer_video_autoplay_ms", 0)
        // 读取，>0 时延迟自动播放，降低低端机滑动时视频解码压力
        json.put(LOW_DEV_DEFER_VIDEO_AUTOPLAY_MS, DEFER_VIDEO_AUTOPLAY_MS)
        return json.toString()
    }
}
