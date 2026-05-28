package com.forbidad4tieba.hook.feature.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import com.forbidad4tieba.hook.symbol.model.ImageViewerNativeShareSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.HashMap
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 图片查看器分享弹窗里的系统原生图片分享入口。
 *
 * 平台官方要求：
 * - ACTION_SEND 加 EXTRA_STREAM，也就是 content://
 * - FLAG_GRANT_READ_URI_PERMISSION
 * - ClipData 使用同一个 stream uri
 */
object ImageViewerNativeShareHook {

    private const val CUSTOM_LABEL_MARKER_RES_ID = -19042601
    private const val REQUEST_MARK_KEY = "tbhook_image_viewer_native_share_added"
    private const val IMAGE_FETCH_CONNECT_TIMEOUT_MS = 10000
    private const val IMAGE_FETCH_READ_TIMEOUT_MS = 15000
    private const val SHARE_STAGING_DIR = "com_qq_e_download/tbhook_share"
    private const val PB_SHARE_ICON_INSET_RATIO = 0.14f

    private val entryInstalled = AtomicBoolean(false)
    private val itemNameHookInstalled = AtomicBoolean(false)
    private val injectedConfigCache = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    internal fun hook(targets: ImageViewerNativeShareSymbols) {
        if (!entryInstalled.compareAndSet(false, true)) return
        try {
            val mod = XposedCompat.module ?: run {
                entryInstalled.set(false)
                XposedCompat.log("[ImageViewerNativeShareHook] skipped: module unavailable")
                return
            }
            val runtime = targets.toRuntime()

            mod.hook(runtime.sendMessageMethod).intercept { chain ->
                tryInjectNativeShareItem(chain.args.firstOrNull(), runtime)
                chain.proceed()
            }
            ensureItemNameHookInstalled(runtime)

            XposedCompat.log(
                "[ImageViewerNativeShareHook] hook INSTALLED: " +
                    "${runtime.sendMessageMethod.declaringClass.name}.${runtime.sendMessageMethod.name}(Message)",
            )
        } catch (t: Throwable) {
            entryInstalled.set(false)
            XposedCompat.log("[ImageViewerNativeShareHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private data class Runtime(
        val customMessageClass: Class<*>,
        val shareDialogConfigClass: Class<*>,
        val sendMessageMethod: Method,
        val customMessageGetDataMethod: Method,
        val isImageViewerDialogField: Field,
        val shareItemField: Field,
        val addOutsideTextViewMethod: Method,
        val getRequestDataMethod: Method,
        val setRequestDataMethod: Method,
        val getContextMethod: Method,
        val shareItemTitleField: Field?,
        val shareItemContentField: Field?,
        val shareItemLinkUrlField: Field?,
        val shareItemImageUriField: Field?,
        val shareItemImageUrlField: Field?,
        val shareItemLocalFileField: Field?,
        val itemNameByResMethod: Method,
        val itemNameByTextMethod: Method,
        val fileProviderGetUriMethod: Method,
        val shareIconResId: Int,
    )

    private fun ImageViewerNativeShareSymbols.toRuntime(): Runtime {
        return Runtime(
            customMessageClass = customMessageClass,
            shareDialogConfigClass = shareDialogConfigClass,
            sendMessageMethod = sendMessageMethod,
            customMessageGetDataMethod = customMessageGetDataMethod,
            isImageViewerDialogField = isImageViewerDialogField,
            shareItemField = shareItemField,
            addOutsideTextViewMethod = addOutsideTextViewMethod,
            getRequestDataMethod = getRequestDataMethod,
            setRequestDataMethod = setRequestDataMethod,
            getContextMethod = getContextMethod,
            shareItemTitleField = shareItemTitleField,
            shareItemContentField = shareItemContentField,
            shareItemLinkUrlField = shareItemLinkUrlField,
            shareItemImageUriField = shareItemImageUriField,
            shareItemImageUrlField = shareItemImageUrlField,
            shareItemLocalFileField = shareItemLocalFileField,
            itemNameByResMethod = itemNameByResMethod,
            itemNameByTextMethod = itemNameByTextMethod,
            fileProviderGetUriMethod = fileProviderGetUriMethod,
            shareIconResId = shareIconResId,
        )
    }

    private data class SharePayload(
        val title: String,
        val shareText: String?,
        val imageContentUri: Uri?,
        val remoteImageUrl: String?,
    )

    private fun ensureItemNameHookInstalled(runtime: Runtime) {
        if (!itemNameHookInstalled.compareAndSet(false, true)) return
        val mod = XposedCompat.module ?: run {
            itemNameHookInstalled.set(false)
            return
        }
        runCatching {
            mod.hook(runtime.itemNameByResMethod).intercept { chain ->
                val resId = chain.args.firstOrNull() as? Int ?: return@intercept chain.proceed()
                if (resId != CUSTOM_LABEL_MARKER_RES_ID) return@intercept chain.proceed()
                runCatching {
                    runtime.itemNameByTextMethod.invoke(
                        chain.thisObject,
                        UiText.ImageViewerNativeShare.BUTTON_LABEL,
                    )
                    applyPbShareIconInset(chain.thisObject)
                }.getOrElse { t ->
                    XposedCompat.logW("[ImageViewerNativeShareHook] setItemName override failed: ${t.message}")
                    chain.proceed()
                }
                null
            }
        }.onFailure { t ->
            itemNameHookInstalled.set(false)
            XposedCompat.logW("[ImageViewerNativeShareHook] install label override hook failed: ${t.message}")
        }
    }

    private fun applyPbShareIconInset(itemViewObj: Any?) {
        val root = itemViewObj as? ViewGroup ?: return
        root.post {
            val icon = root.getChildAt(0) as? ImageView ?: return@post
            val iconSize = icon.layoutParams?.width?.takeIf { it > 0 } ?: return@post
            val inset = (iconSize * PB_SHARE_ICON_INSET_RATIO).toInt()
            icon.setPadding(inset, inset, inset, inset)
        }
    }

    private fun tryInjectNativeShareItem(messageObj: Any?, runtime: Runtime) {
        if (messageObj == null || !runtime.customMessageClass.isInstance(messageObj)) return

        val configObj = runCatching { runtime.customMessageGetDataMethod.invoke(messageObj) }.getOrNull() ?: return
        if (!runtime.shareDialogConfigClass.isInstance(configObj)) return
        val isImageViewerDialog = runCatching { runtime.isImageViewerDialogField.getBoolean(configObj) }
            .getOrDefault(false)
        if (!isImageViewerDialog) return
        if (isAlreadyInjected(configObj, runtime)) return

        val listener = View.OnClickListener { view ->
            startNativeShare(configObj, view, runtime)
        }
        runCatching {
            runtime.addOutsideTextViewMethod.invoke(configObj, CUSTOM_LABEL_MARKER_RES_ID, runtime.shareIconResId, listener)
            markInjected(configObj, runtime)
        }.onFailure { t ->
            XposedCompat.logW("[ImageViewerNativeShareHook] inject outside item failed: ${t.message}")
        }
    }

    private fun isAlreadyInjected(configObj: Any, runtime: Runtime): Boolean {
        if (injectedConfigCache.containsKey(configObj)) return true
        val requestMap = runCatching {
            @Suppress("UNCHECKED_CAST")
            runtime.getRequestDataMethod.invoke(configObj) as? Map<String, Any?>
        }.getOrNull()
        return requestMap?.get(REQUEST_MARK_KEY) == true
    }

    private fun markInjected(configObj: Any, runtime: Runtime) {
        injectedConfigCache[configObj] = true
        val existing = runCatching {
            @Suppress("UNCHECKED_CAST")
            runtime.getRequestDataMethod.invoke(configObj) as? Map<String, Any?>
        }.getOrNull()
        val updated = HashMap<String, Any?>()
        if (existing != null) updated.putAll(existing)
        updated[REQUEST_MARK_KEY] = true
        runCatching { runtime.setRequestDataMethod.invoke(configObj, updated) }
            .onFailure { t ->
                XposedCompat.logW("[ImageViewerNativeShareHook] mark injected failed: ${t.message}")
            }
    }

    private fun getConfigContext(configObj: Any, runtime: Runtime): Context? {
        return runCatching { runtime.getContextMethod.invoke(configObj) as? Context }.getOrNull()
    }

    private fun startNativeShare(configObj: Any, clickView: View?, runtime: Runtime) {
        val context = clickView?.context ?: getConfigContext(configObj, runtime)
        if (context == null) {
            XposedCompat.logW("[ImageViewerNativeShareHook] share skipped: context unavailable")
            return
        }
        val payload = buildSharePayload(context, configObj, runtime) ?: run {
            XposedCompat.logW("[ImageViewerNativeShareHook] share aborted: no image uri available in payload")
            showShareFailedToast(context)
            return
        }
        val localUri = payload.imageContentUri
        if (localUri != null) {
            launchNativeShare(context, payload.title, payload.shareText, localUri)
            return
        }
        val remoteUrl = payload.remoteImageUrl
        if (remoteUrl.isNullOrBlank()) {
            XposedCompat.logW("[ImageViewerNativeShareHook] share aborted: remote url empty while local uri missing")
            showShareFailedToast(context)
            return
        }

        val appContext = context.applicationContext ?: context
        thread(name = "tbhook-native-share-fetch", isDaemon = true) {
            val downloadedUri = downloadRemoteImageToContentUri(appContext, remoteUrl, runtime)
            Handler(Looper.getMainLooper()).post {
                if (downloadedUri != null) {
                    launchNativeShare(context, payload.title, payload.shareText, downloadedUri)
                } else {
                    XposedCompat.logW("[ImageViewerNativeShareHook] share aborted: remote image cannot be converted to content uri")
                    showShareFailedToast(context)
                }
            }
        }
    }

    private fun launchNativeShare(
        context: Context,
        title: String,
        shareText: String?,
        imageContentUri: Uri,
    ) {
        val shareIntent = buildNativeShareIntent(context, title, shareText, imageContentUri)
        val chooser = Intent.createChooser(shareIntent, UiText.ImageViewerNativeShare.CHOOSER_TITLE)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { t ->
                XposedCompat.logW("[ImageViewerNativeShareHook] startActivity failed: ${t.message}")
                showShareFailedToast(context)
            }
    }

    private fun buildSharePayload(context: Context, configObj: Any, runtime: Runtime): SharePayload? {
        val shareItem = runCatching { runtime.shareItemField.get(configObj) }.getOrNull() ?: return null
        val title = runCatching { runtime.shareItemTitleField?.get(shareItem) as? String }.getOrNull().orEmpty().trim()
        val content = runCatching { runtime.shareItemContentField?.get(shareItem) as? String }.getOrNull().orEmpty().trim()
        val linkUrl = runCatching { runtime.shareItemLinkUrlField?.get(shareItem) as? String }.getOrNull().orEmpty().trim()
        val imageUri = runCatching { runtime.shareItemImageUriField?.get(shareItem) as? Uri }.getOrNull()
        val imageUrl = runCatching { runtime.shareItemImageUrlField?.get(shareItem) as? String }
            .getOrNull()
            .orEmpty()
            .trim()
        val localFile = runCatching { runtime.shareItemLocalFileField?.get(shareItem) as? String }
            .getOrNull()
            .orEmpty()
            .trim()
        val localContentUri = resolveLocalContentUri(context, imageUri, localFile, runtime)
        val remoteImageUrl = imageUri?.toString()?.takeIf { localContentUri == null && it.isHttpUrl() }
            ?: imageUrl.takeIf { localContentUri == null && it.isHttpUrl() }
        if (localContentUri == null && remoteImageUrl == null) return null

        return SharePayload(
            title = title,
            shareText = composeShareText(linkUrl, content),
            imageContentUri = localContentUri,
            remoteImageUrl = remoteImageUrl,
        )
    }

    private fun buildNativeShareIntent(
        context: Context,
        title: String,
        shareText: String?,
        imageContentUri: Uri,
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = resolveImageMimeType(context, imageContentUri)
            putExtra(Intent.EXTRA_STREAM, imageContentUri)
            clipData = ClipData.newUri(context.contentResolver, "tbhook_share_image", imageContentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
            if (!shareText.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, shareText)
        }
    }

    private fun resolveLocalContentUri(context: Context, imageUri: Uri?, localFile: String, runtime: Runtime): Uri? {
        if (localFile.isNotBlank()) {
            val file = File(localFile)
            if (file.exists() && file.length() > 0L) {
                val uri = stageLocalFileToContentUri(context, file, runtime)
                if (uri != null) return uri
            }
        }
        if (imageUri == null) return null
        return when (imageUri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> if (canOpenUri(context, imageUri)) imageUri else null
            "file" -> {
                val path = imageUri.path.orEmpty()
                if (path.isBlank()) null else {
                    val file = File(path)
                    if (file.exists() && file.length() > 0L) {
                        stageLocalFileToContentUri(context, file, runtime)
                    } else {
                        null
                    }
                }
            }
            else -> null
        }
    }

    private fun downloadRemoteImageToContentUri(context: Context, imageUrl: String, runtime: Runtime): Uri? {
        if (imageUrl.isBlank()) return null
        val targetFile = createShareStagingFile(context, imageUrl) ?: return null

        val ok = runCatching {
            val connection = URL(imageUrl).openConnection()
            try {
                if (connection is HttpURLConnection) {
                    connection.connectTimeout = IMAGE_FETCH_CONNECT_TIMEOUT_MS
                    connection.readTimeout = IMAGE_FETCH_READ_TIMEOUT_MS
                    connection.instanceFollowRedirects = true
                    connection.requestMethod = "GET"
                }
                connection.getInputStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, 16 * 1024)
                    }
                }
                targetFile.length() > 0L
            } finally {
                if (connection is HttpURLConnection) {
                    connection.disconnect()
                }
            }
        }.getOrElse { t ->
            XposedCompat.logW("[ImageViewerNativeShareHook] remote image download failed: ${t.message}")
            false
        }

        if (!ok) return null
        return fileToContentUri(context, targetFile, runtime)
    }

    private fun createShareStagingFile(context: Context, nameHint: String): File? {
        val dir = File(context.cacheDir, SHARE_STAGING_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null
        val ext = guessImageExt(nameHint)
        return runCatching { File.createTempFile("share_", ext, dir) }.getOrNull()
    }

    private fun stageLocalFileToContentUri(context: Context, sourceFile: File, runtime: Runtime): Uri? {
        val targetFile = createShareStagingFile(context, sourceFile.name) ?: return null
        val copied = runCatching {
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, 16 * 1024)
                }
            }
            targetFile.length() > 0L
        }.getOrElse { t ->
            XposedCompat.logW("[ImageViewerNativeShareHook] stage local file failed: ${t.message}")
            false
        }
        if (!copied) return null
        return fileToContentUri(context, targetFile, runtime)
    }

    private fun guessImageExt(imageUrl: String): String {
        val cleanUrl = imageUrl.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            cleanUrl.endsWith(".png") -> ".png"
            cleanUrl.endsWith(".gif") -> ".gif"
            cleanUrl.endsWith(".webp") -> ".webp"
            cleanUrl.endsWith(".jpeg") -> ".jpeg"
            cleanUrl.endsWith(".jpg") -> ".jpg"
            else -> ".jpg"
        }
    }

    private fun fileToContentUri(context: Context, file: File, runtime: Runtime): Uri? {
        val authority = "${context.packageName}.fileprovider"
        val uri = getUriByFileProvider(context, authority, file, runtime) ?: return null
        return if (canOpenUri(context, uri)) uri else null
    }

    private fun getUriByFileProvider(context: Context, authority: String, file: File, runtime: Runtime): Uri? {
        return runCatching {
            runtime.fileProviderGetUriMethod.invoke(null, context, authority, file) as? Uri
        }.onFailure { t ->
            val root = t.rootCause()
            XposedCompat.logW(
                "[ImageViewerNativeShareHook] FileProvider getUriForFile failed: " +
                    "authority=$authority file=${file.absolutePath} " +
                    "errType=${root.javaClass.name} err=${root.message}",
            )
        }.getOrNull()
    }

    private fun canOpenUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { }
            true
        }.getOrDefault(false)
    }

    private fun resolveImageMimeType(context: Context, uri: Uri): String {
        val resolverType = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.takeIf { it.startsWith("image/") }
        if (!resolverType.isNullOrBlank()) return resolverType

        val lower = uri.toString().lowercase(Locale.ROOT).substringBefore('?')
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpeg") || lower.endsWith(".jpg") -> "image/jpeg"
            else -> "image/*"
        }
    }

    private fun composeShareText(linkUrl: String, content: String): String? {
        if (linkUrl.isBlank() && content.isBlank()) return null
        if (linkUrl.isBlank()) return content
        if (content.isBlank()) return linkUrl
        return if (content.contains(linkUrl)) content else "$content\n$linkUrl"
    }

    private fun showShareFailedToast(context: Context) {
        runCatching {
            Toast.makeText(
                context.applicationContext ?: context,
                UiText.ImageViewerNativeShare.START_FAILED,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun String.isHttpUrl(): Boolean {
        return startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
    }

    private fun Throwable.rootCause(): Throwable {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }
}
