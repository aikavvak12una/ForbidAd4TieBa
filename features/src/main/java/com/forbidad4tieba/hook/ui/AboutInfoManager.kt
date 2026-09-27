package com.forbidad4tieba.hook.ui

import android.content.Context
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.about.AboutDocument
import com.forbidad4tieba.hook.ui.about.AboutInfoRepository
import com.forbidad4tieba.hook.ui.about.AboutItem
import com.forbidad4tieba.hook.ui.about.AboutTelemetry
import com.forbidad4tieba.hook.ui.about.RemoteControls
import com.forbidad4tieba.hook.ui.about.RemoteControlsController
import com.forbidad4tieba.hook.ui.about.RemoteCustomDialog
import com.forbidad4tieba.hook.ui.about.RemoteCustomDialogs
import com.forbidad4tieba.hook.ui.about.RuntimeEnvironmentProvider
import kotlin.concurrent.thread

/** Coordinates startup policy and About UI; storage, decisions and telemetry have their own owners. */
object AboutInfoManager {
    @Volatile private var cachedRemoteItems: List<AboutItem> = emptyList()
    private var startupFetchTriggered = false

    internal fun hasPendingRemoteCustomDialogs(): Boolean = RemoteCustomDialogs.hasPending()
    internal fun pollPendingRemoteCustomDialog(): RemoteCustomDialog? = RemoteCustomDialogs.poll()
    internal fun markRemoteCustomDialogAcknowledged(context: Context, dialog: RemoteCustomDialog) =
        RemoteCustomDialogs.acknowledge(context, dialog)

    fun loadCachedItemsForSettings(): List<AboutItem> = cachedRemoteItems

    fun runtimeEnvironmentJsonForSettings(context: Context): String =
        RuntimeEnvironmentProvider.get(context).toJson().toString(2)

    fun environmentRatingLevelForSettings(context: Context): Int =
        RuntimeEnvironmentProvider.get(context).environmentRatingLevel

    fun applyCachedRuntimeControlsIfNeeded(context: Context) {
        val app = context.applicationContext ?: context
        val controls = AboutInfoRepository.cached(app)?.payload?.controls ?: RemoteControls.DEFAULT
        RemoteControlsController.apply(app, controls, RuntimeEnvironmentProvider.get(app))
    }

    fun fetchAtStartupIfNeeded(context: Context) {
        val app = context.applicationContext ?: context
        synchronized(this) {
            if (startupFetchTriggered) return
            startupFetchTriggered = true
        }
        thread(name = "tbhook-about-startup-fetch", isDaemon = true) {
            try {
                val cached = AboutInfoRepository.cached(app)
                if (cached == null) {
                    XposedCompat.logW("[AboutInfo] startup cache unavailable, skip active payload")
                } else {
                    applyPayload(app, cached)
                }
                val remote = AboutInfoRepository.fetchAndCache(app)
                if (remote == null) {
                    XposedCompat.logW("[AboutInfo] remote cache update skipped: no valid source")
                } else {
                    // Keep the current behavior: valid remote controls apply in this process.
                    applyPayload(app, remote)
                }
            } catch (failure: Throwable) {
                XposedCompat.logW("[AboutInfo] startup fetch crashed: ${failure.message}")
                XposedCompat.log(failure)
            }
        }
    }

    private fun applyPayload(context: Context, document: AboutDocument) {
        val payload = document.payload
        cachedRemoteItems = payload.items
        XposedCompat.logD {
            "[AboutInfo] payload accepted: source=${document.source} url=${document.url} " +
                "bytes=${document.bytes} elapsedMs=${document.elapsedMs} " +
                "cacheAgeMs=${document.cacheAgeMs} itemCount=${payload.items.size}"
        }
        val environment = RuntimeEnvironmentProvider.get(context)
        RemoteControlsController.apply(context, payload.controls, environment)
        AboutTelemetry.reportIfNeeded(context, payload.telemetry, environment)
    }
}
