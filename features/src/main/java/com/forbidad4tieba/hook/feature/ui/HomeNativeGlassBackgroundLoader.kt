package com.forbidad4tieba.hook.feature.ui

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Owns background checks, decode requests and their lifetime, not page styling. */
internal class HomeNativeGlassBackgroundLoader(
    private val store: HomeNativeGlassBackgroundStore,
    private val isCurrent: (BackgroundRequest) -> Boolean,
    private val onChanged: () -> Unit,
    private val onError: (Throwable) -> Unit,
) : ComponentCallbacks2 {
    private class Pending(val generation: Long) {
        val waiters = WeakHashMap<View, (View) -> Unit>()
    }

    private val pending = HashMap<String, Pending>()
    private val executor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "tbhook-glass-bg-decode").apply { isDaemon = true }
        }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var application: Application? = null

    fun register(app: Application) {
        if (application != null) return
        app.registerComponentCallbacks(this)
        application = app
    }

    fun unregister() {
        application?.unregisterComponentCallbacks(this)
        application = null
        clear()
    }

    fun find(request: BackgroundRequest): CachedBackgroundBitmap? {
        val cached = store.find(request)
        if (cached != null && store.needsMetadataCheck(cached)) {
            // Validate the reusable entry at its original size, not a smaller card size.
            schedule(BackgroundRequest(cached.path, cached.blurCachePath, cached.blurPercent,
                cached.targetWidth, cached.targetHeight))
        }
        return cached
    }

    fun schedule(request: BackgroundRequest, anchor: View? = null, onReady: ((View) -> Unit)? = null) {
        if (!isCurrent(request)) return
        val job: Pending
        synchronized(pending) {
            val existing = pending[request.cacheKey]
            if (existing != null) {
                if (anchor != null && onReady != null) existing.waiters[anchor] = onReady
                return
            }
            job = Pending(store.generation())
            if (anchor != null && onReady != null) job.waiters[anchor] = onReady
            pending[request.cacheKey] = job
        }
        try {
            executor.execute {
                val result = try {
                    store.refreshOnWorker(request, job.generation) { isCurrent(request) }
                } catch (t: Throwable) {
                    onError(t)
                    null
                }
                val waiters = synchronized(pending) {
                    if (pending[request.cacheKey] !== job) return@execute
                    pending.remove(request.cacheKey)
                    job.waiters.map { (view, callback) -> WeakReference(view) to callback }
                }
                if (result == null) return@execute
                if (result.changed && result.entry.bitmap == null) {
                    onError(IllegalStateException("background image unavailable: ${request.path}"))
                }
                mainHandler.post {
                    if (job.generation != store.generation() || !isCurrent(request)) return@post
                    // Capturing result keeps even an oversized entry alive until callbacks consume it.
                    if (result.changed) runCatching { onChanged() }.onFailure(onError)
                    waiters.forEach { (ref, callback) ->
                        ref.get()?.takeIf { it.isAttachedToWindow }?.let { view ->
                            // Posted work runs outside libxposed's protective callback boundary.
                            runCatching { callback(view) }.onFailure(onError)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            synchronized(pending) { if (pending[request.cacheKey] === job) pending.remove(request.cacheKey) }
            onError(t)
        }
    }

    private fun clear() {
        synchronized(pending) {
            pending.clear()
            store.clear()
        }
    }

    @Suppress("DEPRECATION") // Older supported Android versions still dispatch running-memory levels.
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) clear()
    }

    @Suppress("OVERRIDE_DEPRECATION") // Keep the callback for the supported pre-API-34 devices.
    override fun onLowMemory() = clear()
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
