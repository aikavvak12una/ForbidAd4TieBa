package com.forbidad4tieba.hook.core

import com.forbidad4tieba.hook.contracts.LogPolicy
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the API 102 module lifecycle object and shared Xposed helpers.
 *
 * Feature hooks install through the owned runtime boundary:
 * ```
 * XposedCompat.interceptHook("FeatureId", method) { chain ->
 *     // chain.thisObject, chain.args and chain.proceed(args) are available here.
 * }
 * ```
 *
 * This object centralizes:
 * - module reference management
 * - structured logging
 * - reflection helpers for fields, methods, and classes
 */
object XposedCompat {
    @Volatile private var logPolicy = LogPolicy { false }

    fun configureLogging(policy: LogPolicy) { logPolicy = policy }
    fun shouldOutputDetailedLogs(): Boolean = logPolicy.detailed()

    private const val MODULE_LOG_TAG = "TbHook"
    private const val HOST_LOG_TAG = "TiebaHost"
    private const val MAX_LOGCAT_MESSAGE_CHARACTERS = 3_800

    @Volatile
    var module: XposedModule? = null
        private set

    private val installInfoOnce = ConcurrentHashMap.newKeySet<String>()

    fun attachModule(xposedModule: XposedModule) {
        module = xposedModule
    }

    fun interceptHook(
        featureId: String,
        executable: Executable,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle? {
        val mod = module ?: run {
            log("[XposedCompat] hook skipped, module unavailable: feature=$featureId")
            return null
        }
        return RuntimeHooks.builder(mod, executable, featureId, "entry").intercept(hooker)
    }

    // Logging.

    fun log(msg: String) {
        if (isSuccessfulHookInstallLog(msg)) {
            if (!installInfoOnce.add(msg)) return
            val detailedLogging = shouldOutputDetailedLogs()
            val priority = if (detailedLogging) {
                android.util.Log.DEBUG
            } else {
                android.util.Log.INFO
            }
            if (detailedLogging) recordModuleLog(priority, msg)
            android.util.Log.println(priority, MODULE_LOG_TAG, msg)
            module?.log(priority, MODULE_LOG_TAG, msg)
            return
        }
        if (isStaticDispatchLog(msg)) {
            if (!shouldOutputDetailedLogs()) return
            if (!installInfoOnce.add(msg)) return
            recordModuleLog(android.util.Log.DEBUG, msg)
            android.util.Log.d(MODULE_LOG_TAG, msg)
            module?.log(android.util.Log.DEBUG, MODULE_LOG_TAG, msg)
            return
        }
        if (!shouldOutputDetailedLogs()) {
            if (!isReleaseKeyInfo(msg)) return
        } else {
            recordModuleLog(android.util.Log.INFO, msg)
        }
        android.util.Log.i(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.INFO, MODULE_LOG_TAG, msg)
    }

    fun logD(msg: String) {
        if (!shouldOutputDetailedLogs()) return
        recordModuleLog(android.util.Log.DEBUG, msg)
        android.util.Log.d(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.DEBUG, MODULE_LOG_TAG, msg)
    }

    inline fun logD(msg: () -> String) {
        if (!shouldOutputDetailedLogs()) return
        logD(msg())
    }

    fun logW(msg: String) {
        if (shouldOutputDetailedLogs()) {
            recordModuleLog(android.util.Log.WARN, msg)
        }
        android.util.Log.w(MODULE_LOG_TAG, msg)
        module?.log(android.util.Log.WARN, MODULE_LOG_TAG, msg)
    }

    fun log(t: Throwable) {
        if (!shouldOutputDetailedLogs()) {
            val summary = "${t.javaClass.name}: ${t.message.orEmpty()}"
            android.util.Log.e(MODULE_LOG_TAG, summary)
            module?.log(android.util.Log.ERROR, MODULE_LOG_TAG, summary)
            return
        }
        val stackTrace = android.util.Log.getStackTraceString(t)
        recordModuleLog(android.util.Log.ERROR, stackTrace)
        android.util.Log.e(MODULE_LOG_TAG, "Error", t)
        module?.log(android.util.Log.ERROR, MODULE_LOG_TAG, stackTrace)
    }

    fun emitTiebaHostLog(
        priority: Int,
        tag: String,
        message: String,
    ) {
        if (!shouldOutputDetailedLogs()) return
        val line = "[$tag] $message"
        val boundedLine = if (line.length > MAX_LOGCAT_MESSAGE_CHARACTERS) {
            line.take(MAX_LOGCAT_MESSAGE_CHARACTERS - LOG_TRUNCATED_SUFFIX.length) +
                LOG_TRUNCATED_SUFFIX
        } else {
            line
        }
        android.util.Log.println(priority, HOST_LOG_TAG, boundedLine)
        module?.log(priority, HOST_LOG_TAG, boundedLine)
    }

    private fun recordModuleLog(priority: Int, message: String) {
        DetailedLogSession.recordModule(
            level = priorityName(priority),
            tag = MODULE_LOG_TAG,
            message = message,
        )
    }

    private fun priorityName(priority: Int): String {
        return when (priority) {
            android.util.Log.VERBOSE -> "VERBOSE"
            android.util.Log.DEBUG -> "DEBUG"
            android.util.Log.WARN -> "WARN"
            android.util.Log.ERROR -> "ERROR"
            android.util.Log.ASSERT -> "ASSERT"
            else -> "INFO"
        }
    }

    private const val LOG_TRUNCATED_SUFFIX = "...[truncated]"

    private fun isSuccessfulHookInstallLog(msg: String): Boolean {
        if (msg.contains("FAILED", ignoreCase = true) || msg.contains("no hooks installed", ignoreCase = true)) {
            return false
        }
        return msg.contains("hook INSTALLED", ignoreCase = true) ||
            msg.contains("hooks INSTALLED", ignoreCase = true)
    }

    private fun isStaticDispatchLog(msg: String): Boolean {
        return msg.contains("All static hooks dispatched", ignoreCase = true)
    }

    private fun isReleaseKeyInfo(msg: String): Boolean {
        return msg.contains("[CustomPostModelScoreStats] auto percentile effective", ignoreCase = true) ||
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("error", ignoreCase = true) ||
            msg.contains("exception", ignoreCase = true) ||
            msg.contains("abort", ignoreCase = true) ||
            msg.contains("unsupported", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true)
    }

    // 绫昏В鏋?
}
