package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.core.Constants

internal object HookProcess {
    private const val IMAGE_VIEWER_REMOTE_SUFFIX = ":remote"

    fun isMain(processName: String): Boolean {
        return processName == Constants.TARGET_PACKAGE
    }

    fun isImageViewerRemote(processName: String): Boolean {
        return processName == Constants.TARGET_PACKAGE + IMAGE_VIEWER_REMOTE_SUFFIX
    }

    fun isImageViewerProcess(processName: String): Boolean {
        return isMain(processName) || isImageViewerRemote(processName)
    }

    fun isTargetTiebaProcess(processName: String): Boolean {
        return isMain(processName) || processName.startsWith("${Constants.TARGET_PACKAGE}:")
    }
}
