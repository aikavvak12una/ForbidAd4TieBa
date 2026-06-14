package com.forbidad4tieba.hook.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface

internal object RemoteCustomDialogInstaller {
    @Volatile private var hookInstalled = false
    @Volatile private var dialogSessionActive = false
    @Volatile private var resumeUnhooks: Array<XposedInterface.HookHandle>? = null

    fun ensureInstalled() {
        if (tryStart(ModuleForegroundActivityTracker.currentActivity())) return
        if (dialogSessionActive || hookInstalled) return
        val mod = XposedCompat.module ?: return
        synchronized(this) {
            if (dialogSessionActive || hookInstalled) return
            hookInstalled = true
        }
        try {
            val method = Activity::class.java.getDeclaredMethod("onResume").apply {
                isAccessible = true
            }
            val handle = mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                tryStart(activity)
                result
            }
            resumeUnhooks = arrayOf(handle)
            tryStart(ModuleForegroundActivityTracker.currentActivity())
        } catch (t: Throwable) {
            synchronized(this) { hookInstalled = false }
            XposedCompat.log("[RemoteCustomDialogInstaller] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryStart(activity: Activity?): Boolean {
        if (
            activity == null ||
            activity.packageName != Constants.TARGET_PACKAGE ||
            activity.isFinishing ||
            activity.isDestroyed ||
            !AboutInfoManager.hasPendingRemoteCustomDialogs()
        ) {
            return false
        }
        val shouldStart = synchronized(this) {
            if (dialogSessionActive) {
                false
            } else {
                dialogSessionActive = true
                true
            }
        }
        if (!shouldStart) return false
        unhookAll()
        ModuleDialogQueue.enqueue {
            showNext(activity)
        }
        return true
    }

    private fun showNext(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            finishSession()
            ModuleDialogQueue.finishCurrent()
            if (AboutInfoManager.hasPendingRemoteCustomDialogs()) ensureInstalled()
            return
        }
        val dialogConfig = AboutInfoManager.pollPendingRemoteCustomDialog()
        if (dialogConfig == null) {
            finishSession()
            ModuleDialogQueue.finishCurrent()
            return
        }

        try {
            val tokens = UiStyle.tokens(activity)
            val density = activity.resources.displayMetrics.density
            val padding = settingsDialogPadding(density)
            val messageView = TextView(activity).apply {
                text = dialogConfig.message
                applySettingsMessageStyle(tokens, density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, padding)
            }
            val scroll = ScrollView(activity).apply {
                addView(
                    messageView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                )
            }

            val builder = AlertDialog.Builder(activity, dialogThemeFor(activity))
                .setSettingsTitle(activity, dialogConfig.title)
                .setView(scroll)
                .setPositiveButton(UiText.Settings.BUTTON_OK, null)
                .setNegativeButton(UiText.Settings.REMOTE_DIALOG_NEVER_SHOW_AGAIN) { _, _ ->
                    AboutInfoManager.markRemoteCustomDialogAcknowledged(activity, dialogConfig)
                }

            dialogConfig.urlButton?.let { button ->
                builder.setNeutralButton(button.text) { _, _ ->
                    openUrl(activity, button.url)
                }
            }

            val dialog = builder.create()
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    applyUnifiedDialogCardStyle(window, density)
                    UiStyle.animateDialogEntry(window.decorView, density)
                }
            }
            dialog.setOnDismissListener {
                if (AboutInfoManager.hasPendingRemoteCustomDialogs() && !activity.isFinishing && !activity.isDestroyed) {
                    showNext(activity)
                } else {
                    finishSession()
                    ModuleDialogQueue.finishCurrent()
                    if (AboutInfoManager.hasPendingRemoteCustomDialogs()) ensureInstalled()
                }
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[RemoteCustomDialogInstaller] show failed: ${t.message}")
            finishSession()
            ModuleDialogQueue.finishCurrent()
            if (AboutInfoManager.hasPendingRemoteCustomDialogs()) ensureInstalled()
        }
    }

    private fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (t: Throwable) {
            XposedCompat.logW("[RemoteCustomDialogInstaller] open url failed: ${t.message}")
        }
    }

    private fun finishSession() {
        synchronized(this) {
            dialogSessionActive = false
        }
    }

    private fun unhookAll() {
        val handles = resumeUnhooks ?: return
        resumeUnhooks = null
        synchronized(this) {
            hookInstalled = false
        }
        for (handle in handles) {
            try {
                handle.unhook()
            } catch (t: Throwable) {
                XposedCompat.logD { "RemoteCustomDialogInstaller: ${t.message}" }
            }
        }
    }
}
