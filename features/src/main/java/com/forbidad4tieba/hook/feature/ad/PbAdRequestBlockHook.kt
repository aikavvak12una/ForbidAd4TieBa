package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.symbol.model.PbAdRequestBlockSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdRequestFieldPatchSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object PbAdRequestBlockHook {
    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }
    private val pbPageClearWarned = AtomicBoolean(false)
    private val commonNotifyWarned = AtomicBoolean(false)

    @Synchronized
    internal fun hook(targets: PbAdRequestBlockSymbols): InstallOutcome {
        if (!ConfigManager.snapshot().isPostPageAdBlockEnabled) {
            return InstallOutcome.skipped("config disabled")
        }
        val mod = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val before = installedMethods.size()
        var count = 0
        var missing = 0
        val failures = ArrayList<String>()

        fun skip(label: String) {
            missing++
            failures += "$label missing"
        }

        // These paths work independently. Retain successful handles and retry only absent points.
        fun install(label: String, method: Method?, create: (Method) -> XposedInterface.HookHandle) {
            if (method == null) {
                skip(label)
                return
            }
            try {
                installedMethods.install(method) { create(method) }
                count++
            } catch (failure: Throwable) {
                failures += "$label: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }

        val patches = targets.pbPageFieldPatches
        install("pb.encode", targets.pbPageEncodeMethod?.takeIf { patches.isNotEmpty() }) { method ->
            RuntimeHooks.builder(mod, method, "PbAdRequestBlockHook", "install:method").intercept { chain ->
                if (ConfigManager.snapshot().isPostPageAdBlockEnabled) {
                    clearPbPageAdRequestFields(chain.thisObject, patches)
                }
                chain.proceed()
            }
        }

        install("pageBrowser.addAd", targets.pageBrowserAddAdMethod) { method ->
            RuntimeHooks.builder(mod, method, "PbAdRequestBlockHook", "install:method:2").intercept { chain ->
                if (ConfigManager.snapshot().isPostPageAdBlockEnabled) return@intercept null
                chain.proceed()
            }
        }

        val commonModelClass = targets.commonAdBidTargetClass
        val notifyMethod = targets.commonAdBidNotifyMethod
        if (commonModelClass != null && notifyMethod != null && targets.commonAdBidStartMethods.isNotEmpty()) {
            for (startMethod in targets.commonAdBidStartMethods.distinct()) {
                install("commonAdBid.${startMethod.name}", startMethod) { method ->
                    RuntimeHooks.builder(mod, method, "PbAdRequestBlockHook", "install:method:3").intercept { chain ->
                        if (!ConfigManager.snapshot().isPostPageAdBlockEnabled) return@intercept chain.proceed()
                        val model = chain.thisObject
                        if (model == null || !commonModelClass.isInstance(model)) return@intercept chain.proceed()
                        notifyCommonAdBidFailure(model, notifyMethod)
                        null
                    }
                }
            }
        } else {
            skip("commonAdBid targets")
        }

        val pageBrowserModelClass = targets.pageBrowserAdBidTargetClass
        if (pageBrowserModelClass != null) {
            install("pageBrowser.adBid", targets.pageBrowserAdBidRequestDataMethod) { method ->
                RuntimeHooks.builder(mod, method, "PbAdRequestBlockHook", "install:method:4").intercept { chain ->
                    if (!ConfigManager.snapshot().isPostPageAdBlockEnabled) return@intercept chain.proceed()
                    val model = chain.thisObject
                    if (model == null || !pageBrowserModelClass.isInstance(model)) return@intercept chain.proceed()
                    null
                }
            }
        } else {
            skip("pageBrowser.adBid target")
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

}
