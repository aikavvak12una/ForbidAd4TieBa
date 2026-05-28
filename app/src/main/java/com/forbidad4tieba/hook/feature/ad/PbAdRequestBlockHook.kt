package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.PbAdRequestBlockSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdRequestFieldPatchSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object PbAdRequestBlockHook {
    @Volatile private var hooked = false
    private val pbPageClearWarned = AtomicBoolean(false)
    private val commonNotifyWarned = AtomicBoolean(false)

    internal fun hook(targets: PbAdRequestBlockSymbols) {
        if (!ConfigManager.isAdBlockEnabled) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!tryMarkHooked()) return

        try {
            var installed = 0
            installed += installPbPageRequestMessageHook(targets)
            installed += installPageBrowserRequestMessageHook(targets)
            installed += installCommonAdBidShortCircuit(targets)
            installed += installPageBrowserAdBidShortCircuit(targets)

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbAdRequestBlockHook] no hooks installed")
                return
            }
            XposedCompat.log("[PbAdRequestBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbAdRequestBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installPbPageRequestMessageHook(targets: PbAdRequestBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val encodeMethod = targets.pbPageEncodeMethod ?: return 0
        val patches = targets.pbPageFieldPatches
        if (patches.isEmpty()) {
            return 0
        }

        mod.hook(encodeMethod).intercept { chain ->
            if (ConfigManager.isAdBlockEnabled) {
                clearPbPageAdRequestFields(chain.thisObject, patches)
            }
            chain.proceed()
        }
        return 1
    }

    private fun installPageBrowserRequestMessageHook(targets: PbAdRequestBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val addAdMethod = targets.pageBrowserAddAdMethod ?: return 0

        mod.hook(addAdMethod).intercept { chain ->
            if (ConfigManager.isAdBlockEnabled) {
                return@intercept null
            }
            chain.proceed()
        }
        return 1
    }

    private fun installCommonAdBidShortCircuit(targets: PbAdRequestBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val targetModelClass = targets.commonAdBidTargetClass ?: return 0
        val notifyMethod = targets.commonAdBidNotifyMethod ?: return 0

        var installed = 0
        for (startMethod in targets.commonAdBidStartMethods.distinctBy { it.name }) {
            mod.hook(startMethod).intercept { chain ->
                val model = chain.thisObject
                if (!ConfigManager.isAdBlockEnabled || model == null || !targetModelClass.isInstance(model)) {
                    return@intercept chain.proceed()
                }
                notifyCommonAdBidFailure(model, notifyMethod)
                null
            }
            installed++
        }
        return installed
    }

    private fun installPageBrowserAdBidShortCircuit(targets: PbAdRequestBlockSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val targetModelClass = targets.pageBrowserAdBidTargetClass ?: return 0
        val requestDataMethod = targets.pageBrowserAdBidRequestDataMethod ?: return 0

        mod.hook(requestDataMethod).intercept { chain ->
            val model = chain.thisObject
            if (!ConfigManager.isAdBlockEnabled || model == null || !targetModelClass.isInstance(model)) {
                return@intercept chain.proceed()
            }
            null
        }
        return 1
    }

    private fun clearPbPageAdRequestFields(message: Any?, patches: List<PbAdRequestFieldPatchSymbols>) {
        if (message == null) return
        try {
            for (patch in patches) {
                patch.field.set(message, patch.value)
            }
        } catch (t: Throwable) {
            if (pbPageClearWarned.compareAndSet(false, true)) {
                XposedCompat.log("[PbAdRequestBlockHook] clear PbPageRequestMessage ad fields FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        }
    }

    private fun notifyCommonAdBidFailure(model: Any, notifyMethod: Method) {
        try {
            notifyMethod.invoke(model, -2)
        } catch (t: Throwable) {
            if (commonNotifyWarned.compareAndSet(false, true)) {
                XposedCompat.log("[PbAdRequestBlockHook] notify common AdBid failure FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
