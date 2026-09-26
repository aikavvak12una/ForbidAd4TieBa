package com.forbidad4tieba.hook.ui.about

import android.content.Context
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.ui.RemoteCustomDialogInstaller
import java.util.ArrayDeque

internal object RemoteCustomDialogs {
    private const val KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX = "remote_custom_dialog_ack:"
    private val remoteCustomDialogLock = Any()
    private val pendingRemoteCustomDialogs = ArrayDeque<RemoteCustomDialog>()
    private val seenRemoteCustomDialogKeys = LinkedHashSet<String>()

    fun hasPending(): Boolean {
        return synchronized(remoteCustomDialogLock) {
            pendingRemoteCustomDialogs.isNotEmpty()
        }
    }

    fun poll(): RemoteCustomDialog? {
        return synchronized(remoteCustomDialogLock) {
            pendingRemoteCustomDialogs.pollFirst()
        }
    }

    fun acknowledge(context: Context, dialog: RemoteCustomDialog) {
        ConfigManager.getModuleStatePrefs(context).edit()
            .putBoolean("$KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX${dialog.ackKey}", true)
            .apply()
    }

    private fun isRemoteCustomDialogAcknowledged(
        context: Context,
        dialog: RemoteCustomDialog,
    ): Boolean {
        return ConfigManager.getModuleStatePrefs(context)
            .getBoolean("$KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX${dialog.ackKey}", false)
    }

    fun enqueue(context: Context, dialogs: List<RemoteCustomDialog>) {
        if (dialogs.isEmpty()) return
        var added = false
        synchronized(remoteCustomDialogLock) {
            for (dialog in dialogs) {
                if (isRemoteCustomDialogAcknowledged(context, dialog)) continue
                if (!seenRemoteCustomDialogKeys.add(dialog.ackKey)) continue
                pendingRemoteCustomDialogs.addLast(dialog)
                added = true
            }
        }
        if (added) {
            RemoteCustomDialogInstaller.ensureInstalled()
        }
    }
}
