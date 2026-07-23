package com.forbidad4tieba.hook.feature.diagnostic

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.forbidad4tieba.hook.core.DetailedLogFileFormatter
import com.forbidad4tieba.hook.core.DetailedLogSession
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal sealed interface DetailedLogExportStartResult {
    data object Started : DetailedLogExportStartResult
    data object AlreadySaving : DetailedLogExportStartResult
    data object NoSession : DetailedLogExportStartResult
    data object Empty : DetailedLogExportStartResult
    data class Failure(val reason: String) : DetailedLogExportStartResult
}

internal sealed interface DetailedLogExportResult {
    data class Success(val fileName: String) : DetailedLogExportResult
    data class Failure(val reason: String) : DetailedLogExportResult
}

internal object DetailedLogExporter {
    private const val MIME_TYPE = "text/x-log"
    private val saving = AtomicBoolean(false)

    fun start(
        context: Context,
        onComplete: (DetailedLogExportResult) -> Unit,
    ): DetailedLogExportStartResult {
        if (!saving.compareAndSet(false, true)) {
            return DetailedLogExportStartResult.AlreadySaving
        }

        val snapshot = DetailedLogSession.snapshot()
        if (snapshot == null) {
            saving.set(false)
            return DetailedLogExportStartResult.NoSession
        }
        if (snapshot.entries.isEmpty()) {
            saving.set(false)
            return DetailedLogExportStartResult.Empty
        }

        val savedAtMillis = System.currentTimeMillis()
        val fileName = DetailedLogFileFormatter.fileName(savedAtMillis)
        val appContext = context.applicationContext ?: context
        return try {
            thread(name = "tbhook-detailed-log-export", isDaemon = true) {
                val result = try {
                    val content = DetailedLogFileFormatter.format(snapshot, savedAtMillis)
                    writeToDownloads(appContext, fileName, content)
                    DetailedLogExportResult.Success(fileName)
                } catch (t: Throwable) {
                    DetailedLogExportResult.Failure(failureReason(t))
                } finally {
                    saving.set(false)
                }
                Handler(Looper.getMainLooper()).post {
                    onComplete(result)
                }
            }
            DetailedLogExportStartResult.Started
        } catch (t: Throwable) {
            saving.set(false)
            DetailedLogExportStartResult.Failure(failureReason(t))
        }
    }

    private fun writeToDownloads(
        context: Context,
        fileName: String,
        content: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, fileName, content)
        } else {
            writeToLegacyDownloads(fileName, content)
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(
        context: Context,
        fileName: String,
        content: String,
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("downloads insert returned null")
        try {
            val outputStream = resolver.openOutputStream(uri, "w")
                ?: error("downloads output stream returned null")
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                writer.write(content)
            }
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(uri, published, null, null) <= 0) {
                error("downloads publish failed")
            }
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun writeToLegacyDownloads(
        fileName: String,
        content: String,
    ) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs()) {
            error("downloads directory creation failed")
        }
        if (!downloads.isDirectory) {
            error("downloads path is not a directory")
        }
        val outputFile = File(downloads, fileName)
        OutputStreamWriter(FileOutputStream(outputFile, false), Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    private fun failureReason(t: Throwable): String {
        return t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
    }
}
