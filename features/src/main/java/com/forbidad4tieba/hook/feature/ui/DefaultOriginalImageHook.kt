package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.utils.toLowerHex

import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.contracts.MemberAccess
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.symbol.model.DefaultOriginalImageSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 图片页成为当前页时，自动触发查看原图。
 *
 * 处理方式：hook ImagePagerAdapter.setPrimaryItem(ViewGroup, int, Object)。
 * 同时将 UrlDragImageView 构造函数作为备用入口。
 * 新页面成为当前页且查看原图按钮可用时，自动触发。
 *
 * 这个 hook 需要安装到所有贴吧进程，不只主进程。
 * 图片查看 Activity ImageViewerActivity 运行在子进程。
 */
object DefaultOriginalImageHook {
    private const val PREF_KEY_ORIGINAL_IMG_DOWN_TIP = "original_img_down_tip"

    private const val MAX_RETRY_TIMES = 5
    private const val RETRY_DELAY_MS = 200L
    private const val VERIFY_DELAY_MS = 300L

    private val hookInstalled = AtomicBoolean(false)
    private val tipBypassApplied = AtomicBoolean(false)

    // One-shot warning and log markers.
    private val firstPrimaryCallbackLogged = AtomicBoolean(false)
    private val firstCtorCallbackLogged = AtomicBoolean(false)
    private val warnedNoStartAfterRetry = AtomicBoolean(false)
    private val warnedDataUnavailable = AtomicBoolean(false)
    private val warnedAlreadyProcessing = AtomicBoolean(false)
    private val loggedSymbolSourceParameter = AtomicBoolean(false)

    private val setPrimaryDispatchDepth = ThreadLocal<Int>()

    // View tags avoid WeakHashMap work in high-frequency callbacks.
    private const val TAG_AUTO_TRIGGERED_DATA_ID = 0x7E000010
    private const val TAG_PENDING_TRIGGER_DATA_ID = 0x7E000011
    private const val TAG_DIRECT_START_DATA_ID = 0x7E000012

    // Public pager access can vary with the concrete parent View class.
    private val currentItemMethodCache = ConcurrentHashMap<Class<*>, Method>()
    private val currentItemMethodMissCache = ConcurrentHashMap.newKeySet<Class<*>>()
    @Volatile private var runtimeTargets: DefaultOriginalImageSymbols? = null

    internal fun hook(targets: DefaultOriginalImageSymbols) {
        runtimeTargets = targets
        if (loggedSymbolSourceParameter.compareAndSet(false, true)) {
            XposedCompat.log("[DefaultOriginalImageHook] scan symbols loaded from resolver targets")
        }
        if (!hookInstalled.compareAndSet(false, true)) return
        try {
            if (!hookInternal()) {
                hookInstalled.set(false)
            }
        } catch (t: Throwable) {
            hookInstalled.set(false)
            XposedCompat.log("[DefaultOriginalImageHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookInternal(): Boolean {
        val mod = XposedCompat.module ?: run {
            XposedCompat.log("[DefaultOriginalImageHook] FAILED: module is null")
            return false
        }
        val targets = runtimeTargets ?: run {
            XposedCompat.log("[DefaultOriginalImageHook] FAILED: scan symbols unavailable")
            return false
        }

        // Resolve classes from precomputed targets.
        val adapterClass = targets.pagerAdapterClass

        var hookCount = 0

        // Primary hook: ImagePagerAdapter.setPrimaryItem.
        if (adapterClass != null) {
            val setPrimaryMethod = targets.setPrimaryItemMethod
            if (setPrimaryMethod != null) {
                XposedCompat.log("[DefaultOriginalImageHook] resolved setPrimaryItem: ${adapterClass.name}.${setPrimaryMethod.name}")
                RuntimeHooks.builder(mod, setPrimaryMethod, "DefaultOriginalImageHook", "hookInternal:setPrimaryMethod").intercept { chain ->
                    enterSetPrimaryDispatch()
                    val result = try { chain.proceed() } finally { exitSetPrimaryDispatch() }

                    val enabled = ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]
                    if (firstPrimaryCallbackLogged.compareAndSet(false, true)) {
                        XposedCompat.log("[DefaultOriginalImageHook] setPrimaryItem callback observed, enabled=$enabled")
                    }
                    if (!enabled) return@intercept result

                    val position = chain.args[1] as? Int ?: return@intercept result
                    val item = chain.args[2] ?: return@intercept result

                    if (!isImageViewerScene(item)) return@intercept result
                    if (isAutoTriggeredForCurrentData(item)) return@intercept result

                    val view = item as? View ?: return@intercept result
                    view.postDelayed({
                        try {
                            tryAutoTriggerFromView(view, source = "setPrimaryItem@$position", requireCurrentItem = true)
                        } catch (t: Throwable) {
                            XposedCompat.logD { "[DefaultOriginalImageHook] auto-trigger setPrimaryItem failed: ${t.message}" }
                        }
                    }, RETRY_DELAY_MS)
                    result
                }
                hookCount++
                XposedCompat.log("[DefaultOriginalImageHook] hook INSTALLED: setPrimaryItem")
            } else {
                XposedCompat.log("[DefaultOriginalImageHook] warn: setPrimaryItem not found on ${targets.pagerAdapterClass}")
            }
        } else {
            XposedCompat.log("[DefaultOriginalImageHook] warn: primary adapter hook unavailable: ${targets.pagerAdapterClass}")
        }

        // Fallback entry: UrlDragImageView constructors.
        val urlDragClass = targets.urlDragImageViewClass
        val constructors = urlDragClass.declaredConstructors
        for (ctor in constructors) {
            runCatching { ctor.isAccessible = true }
            RuntimeHooks.builder(mod, ctor, "DefaultOriginalImageHook", "hookInternal:ctor").intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]) return@intercept result

                val view = chain.thisObject as? View ?: return@intercept result
                if (firstCtorCallbackLogged.compareAndSet(false, true)) {
                    XposedCompat.log("[DefaultOriginalImageHook] UrlDragImageView ctor observed")
                }

                // Trigger only outside setPrimaryItem dispatch.
                if (!isInSetPrimaryDispatch()) {
                    view.postDelayed({
                        try {
                            tryAutoTriggerFromView(view, source = "ctor", requireCurrentItem = true)
                        } catch (t: Throwable) {
                            XposedCompat.logD { "[DefaultOriginalImageHook] auto-trigger ctor failed: ${t.message}" }
                        }
                    }, 600L)
                }
                result
            }
            hookCount++
        }
        XposedCompat.log("[DefaultOriginalImageHook] hook INSTALLED: UrlDragImageView.<init> x${constructors.size}")

        val setAssistUrlMethod = targets.setAssistUrlMethod
        if (setAssistUrlMethod != null) {
            RuntimeHooks.builder(mod, setAssistUrlMethod, "DefaultOriginalImageHook", "hookInternal:setAssistUrlMethod").intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]) return@intercept result
                val view = chain.thisObject as? View ?: return@intercept result
                view.postDelayed({
                    try {
                        tryAutoTriggerFromView(view, source = "setAssistUrl", requireCurrentItem = true)
                    } catch (t: Throwable) {
                        XposedCompat.logD { "[DefaultOriginalImageHook] auto-trigger setAssistUrl failed: ${t.message}" }
                    }
                }, RETRY_DELAY_MS)
                result
            }
            hookCount++
            XposedCompat.log("[DefaultOriginalImageHook] hook INSTALLED: UrlDragImageView.${setAssistUrlMethod.name}")
        }

        if (hookCount == 0) {
            XposedCompat.log("[DefaultOriginalImageHook] FAILED: no hooks installed")
            return false
        }

        XposedCompat.log("[DefaultOriginalImageHook] ready ($hookCount hooks)")
        return true
    }

    // 触发入口。
    private fun tryAutoTriggerFromView(
        view: View,
        source: String,
        requireCurrentItem: Boolean,
    ) {
        if (!ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]) return
        val urlDragClass = runtimeTargets?.urlDragImageViewClass ?: return
        if (!urlDragClass.isInstance(view)) return
        if (!isImageViewerScene(view)) return
        if (requireCurrentItem && !isCurrentPagerItem(view)) return
        if (isAutoTriggeredForCurrentData(view)) return
        scheduleAutoTrigger(view, retryCount = 0, source = source, requireCurrentItem = requireCurrentItem)
    }

    // Callbacks consume members restored and validated by OriginalImageContract.
    private fun getOriginText(item: Any): String? {
        val method = runtimeTargets?.originTextMethod ?: return null
        return runCatching { method.invoke(item) as? String }.getOrNull()
    }

    private fun getAssistData(item: Any): Any? {
        val method = runtimeTargets?.assistDataMethod ?: return null
        return runCatching { method.invoke(item) }.getOrNull()
    }

    private fun hasOriginalCandidate(item: Any): Boolean {
        val originText = getOriginText(item)
        if (!originText.isNullOrBlank()) return true

        val targets = runtimeTargets ?: return false
        val data = getAssistData(item) ?: return false
        val showButton = (targets.showButtonField.get(data) as? Boolean) ?: false
        val blocked = (targets.blockedField.get(data) as? Boolean) ?: true
        val originalUrl = targets.originalUrlField.get(data) as? String
        return showButton && !blocked && !originalUrl.isNullOrBlank()
    }

    private fun isOriginalProcessingOrReady(item: Any): Boolean {
        val targets = runtimeTargets ?: return false
        val data = getAssistData(item) ?: return false
        val process = (targets.originalProcessField.get(data) as? Number)?.toInt() ?: -1
        return process >= 0
    }

    // 自动触发调度。
    private fun scheduleAutoTrigger(
        item: Any,
        retryCount: Int,
        source: String,
        requireCurrentItem: Boolean,
    ) {
        val view = item as? View ?: return
        if (requireCurrentItem && !isCurrentPagerItem(view)) {
            clearAutoTriggerPending(item)
            return
        }
        if (isAutoTriggeredForCurrentData(item)) {
            clearAutoTriggerPending(item)
            return
        }
        if (retryCount == 0 && !markAutoTriggerPending(item)) return
        if (!ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]) {
            clearAutoTriggerPending(item)
            return
        }
        if (isOriginalProcessingOrReady(item)) {
            clearAutoTriggerPending(item)
            if (warnedAlreadyProcessing.compareAndSet(false, true)) {
                XposedCompat.log("[DefaultOriginalImageHook] skip: already processing")
            }
            return
        }

        val triggerMethod = runtimeTargets?.triggerMethod
        if (triggerMethod != null && hasOriginalCandidate(item)) {
            ensureOriginalDownloadTipBypassed()
            runCatching { triggerMethod.invoke(item) }
                .onSuccess {
                    clearAutoTriggerPending(item)
                    markAutoTriggered(item)
                    XposedCompat.log("[DefaultOriginalImageHook] auto-triggered ($source)")
                    verifyAndRetryTrigger(item, source, triggerMethod)
                }
                .onFailure { t ->
                    clearAutoTriggerPending(item)
                    XposedCompat.logW("[DefaultOriginalImageHook] trigger failed: ${t.message}")
                }
            return
        }

        if (retryCount >= MAX_RETRY_TIMES) {
            clearAutoTriggerPending(item)
            if (warnedDataUnavailable.compareAndSet(false, true)) {
                XposedCompat.log("[DefaultOriginalImageHook] skip: data unavailable after retries")
            }
            return
        }
        view.postDelayed(
            { scheduleAutoTrigger(item, retryCount + 1, source, requireCurrentItem) },
            RETRY_DELAY_MS,
        )
    }

    private fun verifyAndRetryTrigger(
        item: Any,
        source: String,
        triggerMethod: Method,
    ) {
        val view = item as? View ?: return
        view.postDelayed({
            if (!ConfigManager.snapshot()[SimpleToggle.DEFAULT_ORIGINAL_IMAGE]) return@postDelayed
            if (isOriginalProcessingOrReady(item)) return@postDelayed

            XposedCompat.log("[DefaultOriginalImageHook] retry ($source)")
            ensureOriginalDownloadTipBypassed()
            runCatching { triggerMethod.invoke(item) }

            view.postDelayed({
                if (isOriginalProcessingOrReady(item)) return@postDelayed
                if (tryDirectStartOriginalDownload(item, source)) {
                    view.postDelayed({
                        if (!isOriginalProcessingOrReady(item)) {
                            clearAutoTriggered(item)
                            if (warnedNoStartAfterRetry.compareAndSet(false, true)) {
                                XposedCompat.log("[DefaultOriginalImageHook] failed after retries")
                            }
                        }
                    }, VERIFY_DELAY_MS)
                } else {
                    clearAutoTriggered(item)
                }
            }, VERIFY_DELAY_MS)
        }, VERIFY_DELAY_MS)
    }

    // 下载提示绕过和直接启动辅助。
    private fun ensureOriginalDownloadTipBypassed() {
        if (tipBypassApplied.get()) return
        val targets = runtimeTargets ?: return
        val getInstanceMethod = targets.sharedPrefGetInstanceMethod ?: return
        val putBooleanMethod = targets.sharedPrefPutBooleanMethod ?: return
        val helperInstance = runCatching { getInstanceMethod.invoke(null) }.getOrNull() ?: return
        runCatching { putBooleanMethod.invoke(helperInstance, PREF_KEY_ORIGINAL_IMG_DOWN_TIP, true) }
            .onSuccess { tipBypassApplied.set(true) }
    }

    private fun tryDirectStartOriginalDownload(item: Any, source: String): Boolean {
        val method = runtimeTargets?.directStartMethod ?: return false
        val downloadName = buildOriginalDownloadName(item) ?: return false
        if (isDirectStartAttemptedForCurrentData(item)) return false
        markDirectStartAttempted(item)
        ensureOriginalDownloadTipBypassed()
        return runCatching {
            method.invoke(item, downloadName)
            XposedCompat.log("[DefaultOriginalImageHook] direct-start ($source)")
            true
        }.getOrDefault(false)
    }

    private fun buildOriginalDownloadName(item: Any): String? {
        val targets = runtimeTargets ?: return null
        val data = getAssistData(item) ?: return null
        val originalUrl = (targets.originalUrlField.get(data) as? String).orEmpty()
        if (originalUrl.isBlank()) return null

        targets.md5Method?.let { method ->
            val fromHost = runCatching { method.invoke(null, originalUrl) as? String }.getOrNull()
            if (!fromHost.isNullOrBlank()) return fromHost
        }
        return runCatching { computeMd5(originalUrl) }.getOrNull()
    }

    private fun computeMd5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.toLowerHex()
    }

    // 场景和分发辅助。
    private fun isImageViewerScene(item: Any): Boolean {
        val view = item as? View ?: return false
        val activity = ReflectionUtils.findActivityFromContext(view.context) ?: return false
        return activity.javaClass.name == StableTiebaHookPoints.IMAGE_VIEWER_ACTIVITY_CLASS
    }

    private fun enterSetPrimaryDispatch() {
        setPrimaryDispatchDepth.set((setPrimaryDispatchDepth.get() ?: 0) + 1)
    }

    private fun exitSetPrimaryDispatch() {
        val d = setPrimaryDispatchDepth.get() ?: 0
        if (d <= 1) setPrimaryDispatchDepth.remove() else setPrimaryDispatchDepth.set(d - 1)
    }

    private fun isInSetPrimaryDispatch(): Boolean {
        return (setPrimaryDispatchDepth.get() ?: 0) > 0
    }

    private fun currentDataIdentity(item: Any): Int {
        return getAssistData(item)?.let { System.identityHashCode(it) } ?: 0
    }

    private fun isAutoTriggeredForCurrentData(item: Any): Boolean {
        val view = item as? View ?: return false
        val dataIdentity = currentDataIdentity(item)
        return dataIdentity != 0 && view.getTag(TAG_AUTO_TRIGGERED_DATA_ID) == dataIdentity
    }

    private fun markAutoTriggered(item: Any) {
        val view = item as? View ?: return
        val dataIdentity = currentDataIdentity(item)
        if (dataIdentity != 0) {
            view.setTag(TAG_AUTO_TRIGGERED_DATA_ID, dataIdentity)
        }
    }

    private fun clearAutoTriggered(item: Any) {
        val view = item as? View ?: return
        view.setTag(TAG_AUTO_TRIGGERED_DATA_ID, null)
    }

    private fun markAutoTriggerPending(item: Any): Boolean {
        val view = item as? View ?: return false
        val dataIdentity = currentDataIdentity(item)
        if (view.getTag(TAG_PENDING_TRIGGER_DATA_ID) == dataIdentity) return false
        view.setTag(TAG_PENDING_TRIGGER_DATA_ID, dataIdentity)
        return true
    }

    private fun clearAutoTriggerPending(item: Any) {
        val view = item as? View ?: return
        view.setTag(TAG_PENDING_TRIGGER_DATA_ID, null)
    }

    private fun isDirectStartAttemptedForCurrentData(item: Any): Boolean {
        val view = item as? View ?: return false
        val dataIdentity = currentDataIdentity(item)
        return dataIdentity != 0 && view.getTag(TAG_DIRECT_START_DATA_ID) == dataIdentity
    }

    private fun markDirectStartAttempted(item: Any) {
        val view = item as? View ?: return
        val dataIdentity = currentDataIdentity(item)
        if (dataIdentity != 0) {
            view.setTag(TAG_DIRECT_START_DATA_ID, dataIdentity)
        }
    }

    private fun isCurrentPagerItem(view: View): Boolean {
        val position = (view.tag as? String)?.toIntOrNull() ?: return false
        var parent = view.parent
        var depth = 0
        while (parent is View && depth < 6) {
            val currentItem = resolveCurrentItemMethod(parent.javaClass)
                ?.let { runCatching { it.invoke(parent) as? Int }.getOrNull() }
            if (currentItem != null) return currentItem == position
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun resolveCurrentItemMethod(clazz: Class<*>): Method? {
        currentItemMethodCache[clazz]?.let { return it }
        if (currentItemMethodMissCache.contains(clazz)) return null
        val method = MemberAccess.findMethodOrNull(clazz, "getCurrentItem")
            ?.takeIf { it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType }
            ?.apply { isAccessible = true }
        if (method == null) currentItemMethodMissCache.add(clazz) else currentItemMethodCache[clazz] = method
        return method
    }
}
