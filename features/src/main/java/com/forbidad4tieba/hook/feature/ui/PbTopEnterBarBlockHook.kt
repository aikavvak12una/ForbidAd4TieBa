package com.forbidad4tieba.hook.feature.ui

import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarSymbols
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean

internal object PbTopEnterBarBlockHook {
    fun hook(targets: PbTopEnterBarSymbols) {
        val module = XposedCompat.module ?: return
        install(module, targets) { ConfigManager.snapshot().isPbTopEnterBarHidden }
    }

    internal fun install(
        api: XposedInterface,
        targets: PbTopEnterBarSymbols,
        enabled: () -> Boolean,
    ): XposedInterface.HookHandle {
        val errorLogged = AtomicBoolean(false)
        return RuntimeHooks.builder(api, targets.initializeMethod, "PbTopEnterBarBlockHook", "initialize").intercept { chain ->
            val result = chain.proceed()
            if (enabled()) {
                try {
                    val owner = chain.thisObject
                    val container = targets.containerField.get(owner) as? LinearLayout
                    val navigation = targets.navigationBarField.get(owner) as? ViewGroup
                    if ((container == null || navigation == null || !detachContainer(container, navigation)) &&
                        errorLogged.compareAndSet(false, true)
                    ) {
                        XposedCompat.log("[PbTopEnterBarBlockHook] skipped: container/navigation structure changed")
                    }
                } catch (failure: Throwable) {
                    if (errorLogged.compareAndSet(false, true)) {
                        XposedCompat.log("[PbTopEnterBarBlockHook] hide FAILED: ${failure.message}")
                    }
                }
            }
            result
        }
    }

    internal fun detachContainer(container: LinearLayout, navigation: ViewGroup): Boolean {
        val parent = container.parent as? ViewGroup ?: return true
        var ancestor: ViewParent? = parent
        while (ancestor != null && ancestor !== navigation) ancestor = ancestor.parent
        if (ancestor !== navigation) return false
        // Keep the initialized fields alive for host refreshes. A later setVisibility cannot reattach this view.
        parent.removeView(container)
        return true
    }
}
