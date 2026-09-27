package com.forbidad4tieba.hook.ui

import android.content.Context
import android.net.Uri
import java.io.File

internal object HomeNativeGlassImageFiles {
    private const val SOURCE_DIR_NAME = "home_native_glass"
    private const val SOURCE_FILE_PREFIX = "source_"

    fun copyToPrivateFile(
        context: Context,
        uri: Uri,
        darkMode: Boolean,
    ): String? {
        val appContext = context.applicationContext ?: context
        val sourceDir = File(appContext.filesDir, SOURCE_DIR_NAME)
        if (!sourceDir.exists() && !sourceDir.mkdirs()) return null
        val modePrefix = SOURCE_FILE_PREFIX + if (darkMode) "dark_" else "light_"
        val fileName = "$modePrefix${System.currentTimeMillis()}.img"
        val targetFile = File(sourceDir, fileName)
        val tempFile = File(sourceDir, "$fileName.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        if (tempFile.length() <= 0L) {
            runCatching { tempFile.delete() }
            return null
        }
        if (!tempFile.renameTo(targetFile)) {
            runCatching {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }.getOrElse {
                runCatching { tempFile.delete() }
                return null
            }
        }
        if (!targetFile.isFile || targetFile.length() <= 0L) return null
        cleanupOldSourceImages(sourceDir, modePrefix, targetFile.name)
        return targetFile.absolutePath
    }

    fun displayText(path: String): String {
        val name = path.trim().takeIf { it.isNotEmpty() }?.let { File(it).name }.orEmpty()
        return if (name.isBlank()) {
            UiText.Settings.HOME_NATIVE_GLASS_BACKGROUND_IMAGE_NONE
        } else {
            UiText.Settings.homeNativeGlassBackgroundImageSelected(name)
        }
    }

    private fun cleanupOldSourceImages(
        sourceDir: File,
        modePrefix: String,
        keepName: String,
    ) {
        runCatching {
            sourceDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    file.name.startsWith(modePrefix) &&
                    file.name != keepName
                ) {
                    file.delete()
                }
            }
        }
    }
}
