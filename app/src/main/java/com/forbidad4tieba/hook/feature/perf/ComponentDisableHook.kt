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
    private const val PREF_LEGACY_RESTORE_PREFIX = "component_disable_legacy_restore_applied_"

    private data class LegacyComponentGroup(
        val key: String,
        val logName: String,
        val exactNames: Set<String> = emptySet(),
        val namePrefixes: List<String> = emptyList(),
    )

    private data class RestoreStats(
        var matched: Int = 0,
        var restored: Int = 0,
        var unchanged: Int = 0,
        var failed: Int = 0,
    )

    fun apply(context: Context) {
        val appContext = context.applicationContext ?: context
        val groups = LEGACY_COMPONENT_GROUPS
        val prefs = ConfigManager.getModuleStatePrefs(appContext)
        val needsLegacyRestore = groups.any { group ->
            !prefs.getBoolean(PREF_LEGACY_RESTORE_PREFIX + group.key, false) ||
                prefs.getStringSet(PREF_DISABLED_PREFIX + group.key, null).orEmpty().isNotEmpty()
        }
        if (!needsLegacyRestore) {
            XposedCompat.logD("$TAG skipped: legacy restore already applied")
            return
        }

        val packageComponents = runCatching { listPackageComponentNames(appContext) }
            .onFailure { XposedCompat.log("$TAG failed to list components: ${it.message}") }
            .getOrDefault(emptySet())
        if (packageComponents.isEmpty()) {
            XposedCompat.log("$TAG skipped: no package components found")
            return
        }

        XposedCompat.log("$TAG legacy restore started: groups=${groups.size}")
        for (group in groups) {
            restoreLegacyGroupIfNeeded(appContext, packageComponents, group)
        }
        XposedCompat.log("$TAG legacy restore completed: groups=${groups.size}")
    }

    private fun restoreLegacyGroupIfNeeded(
        context: Context,
        packageComponents: Set<String>,
        group: LegacyComponentGroup,
    ) {
        val prefs = ConfigManager.getModuleStatePrefs(context)
        val storeKey = PREF_DISABLED_PREFIX + group.key
        val restoreKey = PREF_LEGACY_RESTORE_PREFIX + group.key
        val tracked = prefs.getStringSet(storeKey, null).orEmpty()
        val restoreApplied = prefs.getBoolean(restoreKey, false)
        if (restoreApplied && tracked.isEmpty()) return

        val matched = packageComponents
            .asSequence()
            .filter { componentName ->
                componentName in group.exactNames ||
                    group.namePrefixes.any { prefix -> componentName.startsWith(prefix) }
            }
            .toSortedSet()
        val restoreTargets = (tracked + matched).toSortedSet()
        val stats = RestoreStats(matched = restoreTargets.size)
        val stillDisabled = LinkedHashSet<String>()
        for (className in restoreTargets) {
            if (!restoreComponentState(context, className, stats)) {
                stillDisabled.add(className)
            }
        }

        val editor = prefs.edit().putBoolean(restoreKey, true)
        if (stillDisabled.isEmpty()) {
            editor.remove(storeKey)
        } else {
            editor.putStringSet(storeKey, stillDisabled)
        }
        editor.apply()
        XposedCompat.log(
            "$TAG restored legacy ${group.logName}: tracked=${tracked.size}, matched=${matched.size}, " +
                "restored=${stats.restored}, unchanged=${stats.unchanged}, failed=${stats.failed}",
        )
    }

    private fun restoreComponentState(
        context: Context,
        className: String,
        stats: RestoreStats,
    ): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, className)
        return try {
            val currentState = pm.getComponentEnabledSetting(component)
            if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                stats.unchanged++
                true
            } else {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP,
                )
                stats.restored++
                true
            }
        } catch (t: Throwable) {
            stats.failed++
            if (ConfigManager.shouldOutputDetailedLogs()) {
                XposedCompat.logD("$TAG restore failed: $className, ${t.message}")
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

    private val LEGACY_COMPONENT_GROUPS = listOf(
        LegacyComponentGroup(
            key = "disable_location_components",
            logName = "location-report",
            exactNames = setOf(
                "com.baidu.location.f",
                "com.baidu.tbadk.coreExtra.service.LocationReportService",
            ),
        ),
        LegacyComponentGroup(
            key = "disable_ad_sdk_components",
            logName = "ad-sdk",
            namePrefixes = listOf(
                "com.baidu.mobads.",
                "com.baidu.nadcore.",
                "com.baidu.tieba.adnotify.",
                "com.bytedance.sdk.openadsdk.",
                "com.kwad.sdk.",
                "com.qq.e.",
                "com.ss.android.downloadlib.",
                "com.ss.android.socialbase.",
                "com.ubix.ssp.",
            ),
        ),
        LegacyComponentGroup(
            key = "disable_heavy_feature_components",
            logName = "heavy-feature",
            namePrefixes = listOf(
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
            ),
        ),
        LegacyComponentGroup(
            key = "disable_video_components",
            logName = "video-playback",
            exactNames = setOf(
                "com.baidu.tbadk.core.voice.service.MediaService",
                "com.baidu.tieba.play.cyberPlayer.CyberRemotePlayerService",
                "com.baidu.tieba.video.composite.VideoCompositeService",
                "com.baidu.tieba.video.convert.VideoConvertService",
                "com.baidu.searchbox.player.remote.BDRemotePlayerService",
                "com.quvideo.xiaoying.VideoPlayerActivity",
            ),
            namePrefixes = listOf(
                "cn.jingling.motu.",
                "com.yy.render.",
            ),
        ),
        LegacyComponentGroup(
            key = "disable_monitor_sync_components",
            logName = "monitor-sync",
            exactNames = setOf(
                "com.baidu.searchbox.logsystem.basic.LokiService",
                "com.baidu.tieba.service.PerformMonitorService",
                "com.baidu.tieba.service.SyncLoginService",
                "com.baidu.tieba.service.TiebaActiveService",
                "com.baidu.tieba.service.TiebaSyncService",
                "com.bytedance.embedapplog.collector.Collector",
            ),
        ),
        LegacyComponentGroup(
            key = "disable_update_download_components",
            logName = "update-download",
            exactNames = setOf(
                "com.baidu.adp.titan.TitanDownloadService",
                "com.baidu.adp.titan.TitanLocalService",
                "com.baidu.searchbox.DownloadInstallReceiver",
                "com.baidu.searchbox.OpenDownloadReceiver",
                "com.baidu.tbadk.download.DownloadReceiver",
                "com.baidu.tieba.service.AsInstallService",
                "com.baidu.tieba.service.TiebaUpdateService",
                "com.baidu.tieba.service.UpdateInfoService",
                "com.baidu.titan.sdk.sandbox.WorkerService",
            ),
            namePrefixes = listOf(
                "com.baidu.searchbox.download.component.",
            ),
        ),
    )
}
