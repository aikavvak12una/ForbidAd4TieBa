package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.RemoteEnvironmentState
import android.content.Context
import android.widget.Toast
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.contracts.Diagnostics
import kotlin.concurrent.thread

/** Owns user-triggered scan effects. Host scanning itself only returns data. */
internal object ScanController {
    fun manualRescanAsync(context: Context, classLoader: ClassLoader?, clearUserData: Boolean) {
        val appCtx = context.applicationContext ?: context
        val cl = classLoader ?: appCtx.classLoader
        if (cl == null) {
            toastOnMain(appCtx, UiText.SymbolResolverToast.CLASSLOADER_UNAVAILABLE)
            return
        }
        toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_START)

        thread(name = "tbhook-manual-rescan", isDaemon = true) {
            if (clearUserData) {
                val clearResult = ModuleUserDataCleaner.clearBeforeManualScan(appCtx)
                if (!clearResult.success) {
                    toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_CLEAR_FAILED)
                    return@thread
                }
            }
            val symbols = HookSymbolResolver.resolve(
                context = appCtx,
                cl = cl,
                forceRescan = true,
            )
            if (symbols.source != "unsupported") {
                RemoteEnvironmentState.markPostScanEnvironmentWarningPending(appCtx)
            }
            val versionWarning = ScanMessages.versionWarning(symbols)
            when {
                versionWarning != null -> toastOnMain(appCtx, versionWarning)
                HookSymbolResolver.hasScanErrors(symbols) -> toastOnMain(appCtx, ScanMessages.featureWarning())
                else -> toastOnMain(appCtx, UiText.SymbolResolverToast.MANUAL_SCAN_DONE)
            }
        }
    }

    private fun toastOnMain(context: Context, text: String) {
        try {
            val appCtx = context.applicationContext ?: context
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                try {
                    Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) { Diagnostics.logD("HookSymbolResolver: ${t.message}") }
            }
        } catch (t: Throwable) { Diagnostics.logD("HookSymbolResolver: ${t.message}") }
    }
}
