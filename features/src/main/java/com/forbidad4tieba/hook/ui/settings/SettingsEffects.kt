package com.forbidad4tieba.hook.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.forbidad4tieba.hook.config.ModuleUserDataCleaner
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.diagnostic.DetailedLogExportResult
import com.forbidad4tieba.hook.feature.diagnostic.DetailedLogExportStartResult
import com.forbidad4tieba.hook.feature.diagnostic.DetailedLogExporter
import com.forbidad4tieba.hook.ui.UiText
import kotlin.concurrent.thread
import kotlin.system.exitProcess

internal object SettingsEffects {
    fun saveDetailedLog(context: Context) {
        val startResult = DetailedLogExporter.start(context) { result ->
            when (result) {
                is DetailedLogExportResult.Success -> {
                    Toast.makeText(
                        context,
                        UiText.Settings.detailedLogSaved(result.fileName),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is DetailedLogExportResult.Failure -> {
                    XposedCompat.logW("[SettingsMenuHook] detailed log export failed: ${result.reason}")
                    Toast.makeText(
                        context,
                        UiText.Settings.DETAILED_LOG_SAVE_FAILED,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        val message = when (startResult) {
            DetailedLogExportStartResult.Started -> UiText.Settings.DETAILED_LOG_SAVE_STARTED
            DetailedLogExportStartResult.AlreadySaving -> {
                UiText.Settings.DETAILED_LOG_SAVE_ALREADY_RUNNING
            }
            DetailedLogExportStartResult.NoSession -> UiText.Settings.DETAILED_LOG_SAVE_NO_SESSION
            DetailedLogExportStartResult.Empty -> UiText.Settings.DETAILED_LOG_SAVE_EMPTY
            is DetailedLogExportStartResult.Failure -> {
                XposedCompat.logW(
                    "[SettingsMenuHook] detailed log export start failed: ${startResult.reason}",
                )
                UiText.Settings.DETAILED_LOG_SAVE_FAILED
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun clearModuleDataAndRestart(activity: Activity) {
        Toast.makeText(activity, UiText.Settings.CLEAR_MODULE_DATA_STARTED, Toast.LENGTH_SHORT).show()
        thread(name = "tbhook-clear-module-data", isDaemon = true) {
            try {
                val result = ModuleUserDataCleaner.clearAllModuleData(activity, resetRuntime = false)
                Handler(Looper.getMainLooper()).post {
                    val message = if (result.success) {
                        UiText.Settings.CLEAR_MODULE_DATA_RESTARTING
                    } else {
                        UiText.Settings.clearModuleDataPartialFailed(result.failedTargets.size)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    restartHostApp(activity)
                }
            } catch (t: Throwable) {
                XposedCompat.log("[SettingsMenuHook] clear module data failed: ${t.message}")
                XposedCompat.log(t)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        activity,
                        UiText.Settings.scanException(t.message ?: UiText.Settings.UNKNOWN),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun restartHostApp(context: Context) {
        try {
            val restartIntent = Intent().apply {
                setClassName(Constants.TARGET_PACKAGE, StableTiebaHookPoints.MAIN_TAB_ACTIVITY_CLASS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(restartIntent)
        } catch (t: Throwable) {
            XposedCompat.log("[SettingsMenuHook] restart launch failed: ${t.message}")
            XposedCompat.log(t)
            return
        }

        try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
        try { exitProcess(0) } catch (t: Throwable) { XposedCompat.logD("SettingsMenuHook: ${t.message}") }
    }
}
