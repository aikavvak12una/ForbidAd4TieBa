package com.forbidad4tieba.hook.feature.perf

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

object ComponentDisableHook {
    private const val TAG = "[ComponentDisableHook]"
    private const val PREF_DISABLED_PREFIX = "component_disable_applied_"

    private data class ComponentGroup(
        val key: String,
        val logName: String,
        val enabled: Boolean,
        val exactNames: Set<String> = emptySet(),
        val namePrefixes: List<String> = emptyList(),
    )

    private data class ApplyStats(
        var matched: Int = 0,
        var changed: Int = 0,
        var unchanged: Int = 0,
        var restored: Int = 0,
        var failed: Int = 0,
    )

    fun apply(context: Context) {
        val appContext = context.applicationContext ?: context
        val groups = listOf(
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_LOCATION_COMPONENTS,
                logName = "location-report",
                enabled = ConfigManager.isLocationComponentsDisabled,
                exactNames = LOCATION_COMPONENTS,
            ),
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_AD_SDK_COMPONENTS,
                logName = "ad-sdk",
                enabled = ConfigManager.isAdSdkComponentsDisabled,
                namePrefixes = AD_COMPONENT_PREFIXES,
            ),
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_HEAVY_FEATURE_COMPONENTS,
                logName = "heavy-feature",
                enabled = ConfigManager.isHeavyFeatureComponentsDisabled,
                namePrefixes = HEAVY_FEATURE_COMPONENT_PREFIXES,
            ),
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_VIDEO_COMPONENTS,
                logName = "video-playback",
                enabled = ConfigManager.isVideoComponentsDisabled,
                exactNames = VIDEO_COMPONENTS,
                namePrefixes = VIDEO_COMPONENT_PREFIXES,
            ),
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_MONITOR_SYNC_COMPONENTS,
                logName = "monitor-sync",
                enabled = ConfigManager.isMonitorSyncComponentsDisabled,
                exactNames = MONITOR_SYNC_COMPONENTS,
            ),
            ComponentGroup(
                key = ConfigManager.KEY_DISABLE_UPDATE_DOWNLOAD_COMPONENTS,
                logName = "update-download",
                enabled = ConfigManager.isUpdateDownloadComponentsDisabled,
                exactNames = UPDATE_DOWNLOAD_COMPONENTS,
                namePrefixes = UPDATE_DOWNLOAD_COMPONENT_PREFIXES,
            ),
        )

        val prefs = ConfigManager.getModuleStatePrefs(appContext)
        val hasEnabledGroup = groups.any { it.enabled }
        val hasTrackedDisabledComponents = groups.any { group ->
            prefs.getStringSet(PREF_DISABLED_PREFIX + group.key, null).orEmpty().isNotEmpty()
        }
        if (!hasEnabledGroup && !hasTrackedDisabledComponents) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }

        val packageComponents = runCatching { listPackageComponentNames(appContext) }
            .onFailure { XposedCompat.log("$TAG failed to list components: ${it.message}") }
            .getOrDefault(emptySet())
        if (packageComponents.isEmpty()) {
            XposedCompat.log("$TAG skipped: no package components found")
            return
        }

        val enabledCount = groups.count { it.enabled }
        XposedCompat.log("$TAG apply started: groups=${groups.size}, enabled=$enabledCount")
        for (group in groups) {
            applyGroup(appContext, packageComponents, group)
        }
        XposedCompat.log("$TAG apply completed: groups=${groups.size}, enabled=$enabledCount")
    }

    private fun applyGroup(
        context: Context,
        packageComponents: Set<String>,
        group: ComponentGroup,
    ) {
        val prefs = ConfigManager.getModuleStatePrefs(context)
        val storeKey = PREF_DISABLED_PREFIX + group.key
        val previouslyDisabled = prefs.getStringSet(storeKey, null).orEmpty()
        val matched = packageComponents
            .asSequence()
            .filter { componentName ->
                componentName in group.exactNames ||
                    group.namePrefixes.any { prefix -> componentName.startsWith(prefix) }
            }
            .toSortedSet()

        if (group.enabled) {
            val stats = ApplyStats(matched = matched.size)
            val applied = LinkedHashSet(previouslyDisabled)
            for (className in matched) {
                if (setComponentState(context, className, disable = true, stats = stats)) {
                    applied.add(className)
                }
            }
            prefs.edit().putStringSet(storeKey, applied).apply()
            XposedCompat.log(
                "$TAG disabled ${group.logName}: matched=${stats.matched}, " +
                    "changed=${stats.changed}, unchanged=${stats.unchanged}, failed=${stats.failed}",
            )
            return
        }

        if (previouslyDisabled.isEmpty()) {
            XposedCompat.log("$TAG restored ${group.logName}: tracked=0")
            return
        }

        val stats = ApplyStats(matched = previouslyDisabled.size)
        val stillDisabled = LinkedHashSet<String>()
        for (className in previouslyDisabled.sorted()) {
            if (!setComponentState(context, className, disable = false, stats = stats)) {
                stillDisabled.add(className)
            }
        }
        prefs.edit().putStringSet(storeKey, stillDisabled).apply()
        XposedCompat.log(
            "$TAG restored ${group.logName}: tracked=${stats.matched}, " +
                "restored=${stats.restored}, unchanged=${stats.unchanged}, failed=${stats.failed}",
        )
    }

    private fun setComponentState(
        context: Context,
        className: String,
        disable: Boolean,
        stats: ApplyStats,
    ): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, className)
        val targetState = if (disable) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        return try {
            val currentState = pm.getComponentEnabledSetting(component)
            if (currentState == targetState) {
                stats.unchanged++
                true
            } else {
                pm.setComponentEnabledSetting(
                    component,
                    targetState,
                    PackageManager.DONT_KILL_APP,
                )
                if (disable) {
                    stats.changed++
                } else {
                    stats.restored++
                }
                true
            }
        } catch (t: Throwable) {
            stats.failed++
            if (ConfigManager.shouldOutputDetailedLogs()) {
                XposedCompat.logD("$TAG ${if (disable) "disable" else "restore"} failed: $className, ${t.message}")
            }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun listPackageComponentNames(context: Context): Set<String> {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.MATCH_DISABLED_COMPONENTS
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val out = LinkedHashSet<String>()
        fun addAll(items: Array<out ComponentInfo>?) {
            items?.forEach { info ->
                val name = info.name
                if (!name.isNullOrBlank()) out.add(name)
            }
        }
        addAll(packageInfo.activities)
        addAll(packageInfo.services)
        addAll(packageInfo.receivers)
        addAll(packageInfo.providers)
        return out
    }

    private val LOCATION_COMPONENTS = setOf(
        "com.baidu.location.f",
        "com.baidu.tbadk.coreExtra.service.LocationReportService",
    )

    private val AD_COMPONENT_PREFIXES = listOf(
        "com.baidu.mobads.",
        "com.baidu.nadcore.",
        "com.baidu.tieba.adnotify.",
        "com.bytedance.sdk.openadsdk.",
        "com.kwad.sdk.",
        "com.qq.e.",
        "com.ss.android.downloadlib.",
        "com.ss.android.socialbase.",
        "com.ubix.ssp.",
    )

    private val HEAVY_FEATURE_COMPONENT_PREFIXES = listOf(
        "com.baidu.live.",
        "com.baidu.searchbox.yy.gameassist.",
        "com.baidu.swan.",
        "com.baidu.tieba.ala.",
        "com.baidu.tieba.gameassist.",
        "com.baidu.tieba.livesdk.",
        "com.baidu.wallet.",
        "com.bd.vtlive.",
        "com.bdgame.assist.",
        "com.byted.live.",
        "com.bytedance.android.openliveplugin.",
        "com.dxmpay.wallet.",
        "com.thunder.livesdk.",
        "com.yy.mobile.livebasebiz.",
        "com.yy.mobile.plugin.dreamerchannel.",
        "com.yy.mobile.plugin.pluginmobilelive.",
        "tv.athena.revenue.payui.activity.PayCommonWebActivity_gameassist",
    )

    private val VIDEO_COMPONENTS = setOf(
        "com.baidu.tbadk.core.voice.service.MediaService",
        "com.baidu.tieba.play.cyberPlayer.CyberRemotePlayerService",
        "com.baidu.tieba.video.composite.VideoCompositeService",
        "com.baidu.tieba.video.convert.VideoConvertService",
        "com.baidu.searchbox.player.remote.BDRemotePlayerService",
        "com.quvideo.xiaoying.VideoPlayerActivity",
    )

    private val VIDEO_COMPONENT_PREFIXES = listOf(
        "cn.jingling.motu.",
        "com.yy.render.",
    )

    private val MONITOR_SYNC_COMPONENTS = setOf(
        "com.baidu.searchbox.logsystem.basic.LokiService",
        "com.baidu.tieba.service.PerformMonitorService",
        "com.baidu.tieba.service.SyncLoginService",
        "com.baidu.tieba.service.TiebaActiveService",
        "com.baidu.tieba.service.TiebaSyncService",
        "com.bytedance.embedapplog.collector.Collector",
    )

    private val UPDATE_DOWNLOAD_COMPONENTS = setOf(
        "com.baidu.adp.titan.TitanDownloadService",
        "com.baidu.adp.titan.TitanLocalService",
        "com.baidu.searchbox.DownloadInstallReceiver",
        "com.baidu.searchbox.OpenDownloadReceiver",
        "com.baidu.tbadk.download.DownloadReceiver",
        "com.baidu.tieba.service.AsInstallService",
        "com.baidu.tieba.service.TiebaUpdateService",
        "com.baidu.tieba.service.UpdateInfoService",
        "com.baidu.titan.sdk.sandbox.WorkerService",
    )

    private val UPDATE_DOWNLOAD_COMPONENT_PREFIXES = listOf(
        "com.baidu.searchbox.download.component.",
    )
}
