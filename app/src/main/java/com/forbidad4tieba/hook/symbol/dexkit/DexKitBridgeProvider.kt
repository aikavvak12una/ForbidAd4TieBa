package com.forbidad4tieba.hook.symbol.dexkit

import android.app.Application
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanSession
import org.luckypray.dexkit.DexKitBridge
import java.io.File

internal class DexKitScanBridge(
    val sourcePath: String,
    val bridge: DexKitBridge,
) : AutoCloseable {
    override fun close() {
        bridge.close()
    }
}

internal object DexKitBridgeProvider {
    private const val TAG = "DexKitBridge"
    private const val MODULE_PACKAGE_NAME = "com.forbidad4tieba.hook"
    private const val DEXKIT_LIBRARY_NAME = "dexkit"
    private const val DEXKIT_LIBRARY_FILE_NAME = "libdexkit.so"

    @Volatile
    private var nativeLibraryLoaded = false

    @Volatile
    private var nativeLibraryLoadError: String? = null

    fun openFirstAvailable(
        sourcePaths: List<String>,
        logger: ScanLogger?,
    ): DexKitScanBridge? {
        ensureNativeLibraryLoaded(logger)?.let { detail ->
            recordBridgeIssue(logger, detail)
            return null
        }

        var failed = 0
        var firstError: String? = null
        for (sourcePath in sourcePaths.distinct()) {
            if (!File(sourcePath).isFile) continue
            try {
                return DexKitScanBridge(
                    sourcePath = sourcePath,
                    bridge = DexKitBridge.create(sourcePath),
                )
            } catch (t: Throwable) {
                failed++
                if (firstError == null) {
                    firstError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                        HookSymbolScanDiagnostics.formatScanException(t),
                    )
                }
            }
        }
        if (failed > 0) {
            val detail = "open failed count=$failed" +
                (firstError?.let { ", firstException=$it" } ?: "")
            recordBridgeIssue(logger, detail)
        }
        return null
    }

    private fun ensureNativeLibraryLoaded(logger: ScanLogger?): String? {
        if (nativeLibraryLoaded) return null
        nativeLibraryLoadError?.let { return it }
        return synchronized(this) {
            if (nativeLibraryLoaded) return@synchronized null
            nativeLibraryLoadError?.let { return@synchronized it }

            val loadError = tryLoadNativeLibrary(logger)
            if (loadError == null) {
                nativeLibraryLoaded = true
                null
            } else {
                nativeLibraryLoadError = loadError
                loadError
            }
        }
    }

    private fun tryLoadNativeLibrary(logger: ScanLogger?): String? {
        try {
            System.loadLibrary(DEXKIT_LIBRARY_NAME)
            HookSymbolScanDiagnostics.log(logger, "$TAG: loaded by System.loadLibrary")
            return null
        } catch (t: Throwable) {
            val loadLibraryError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                HookSymbolScanDiagnostics.formatScanException(t),
            )
            val moduleLibrary = resolveModuleNativeLibrary()
                ?: return "native library path not found, loadLibraryException=$loadLibraryError"
            if (!moduleLibrary.isFile) {
                return "native library file missing: ${moduleLibrary.absolutePath}, " +
                    "loadLibraryException=$loadLibraryError"
            }
            return try {
                System.load(moduleLibrary.absolutePath)
                HookSymbolScanDiagnostics.log(
                    logger,
                    "$TAG: loaded native library from ${moduleLibrary.parent}",
                )
                null
            } catch (loadFileError: Throwable) {
                val loadError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                    HookSymbolScanDiagnostics.formatScanException(loadFileError),
                )
                "native library load failed: ${moduleLibrary.absolutePath}, " +
                    "loadLibraryException=$loadLibraryError, loadFileException=$loadError"
            }
        }
    }

    private fun resolveModuleNativeLibrary(): File? {
        val app = recoverCurrentApplication() ?: return null
        val appInfo = try {
            @Suppress("DEPRECATION")
            app.packageManager.getApplicationInfo(MODULE_PACKAGE_NAME, 0)
        } catch (_: Throwable) {
            null
        } ?: return null
        val nativeLibraryDir = appInfo.nativeLibraryDir?.takeIf { it.isNotBlank() } ?: return null
        return File(nativeLibraryDir, DEXKIT_LIBRARY_FILE_NAME)
    }

    private fun recoverCurrentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThread.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }
            currentApplication.invoke(null) as? Application
        } catch (_: Throwable) {
            null
        }
    }

    private fun recordBridgeIssue(logger: ScanLogger?, detail: String) {
        val errors = HookSymbolScanSession.get()?.scanErrors
        if (errors != null) {
            HookSymbolScanDiagnostics.recordScanIssue(
                logger = logger,
                tag = TAG,
                errors = errors,
                detail = detail,
            )
        } else {
            HookSymbolScanDiagnostics.log(logger, "$TAG: $detail")
        }
    }
}
