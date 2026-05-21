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
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.utils.ReflectionUtils
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

    private const val FILE_PROVIDER_CLASS = "androidx.core.content.FileProvider"

    private const val CUSTOM_LABEL_MARKER_RES_ID = -19042601
    private const val REQUEST_MARK_KEY = "tbhook_image_viewer_native_share_added"
    private const val IMAGE_FETCH_CONNECT_TIMEOUT_MS = 10000
    private const val IMAGE_FETCH_READ_TIMEOUT_MS = 15000
    private const val SHARE_STAGING_DIR = "com_qq_e_download/tbhook_share"
    private const val PB_SHARE_ICON_INSET_RATIO = 0.14f

    private val entryInstalled = AtomicBoolean(false)
    private val itemNameHookInstalled = AtomicBoolean(false)
    private val warnedSymbolUnavailable = AtomicBoolean(false)
    private val injectedConfigCache = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    private var fileProviderGetUriMethod: Method? = null
    @Volatile
    private var fileProviderMethodClassLoader: ClassLoader? = null
    @Volatile
    private var targetAppClassLoader: ClassLoader? = null
    @Volatile
    private var resolvedRuntime: Runtime? = null
    @Volatile
    private var resolvedTargets: SymbolTargets? = null

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        if (!entryInstalled.compareAndSet(false, true)) return
        try {
            targetAppClassLoader = cl
            val targets = toSymbolTargets(symbols)
            if (targets == null) {
                entryInstalled.set(false)
                if (warnedSymbolUnavailable.compareAndSet(false, true)) {
                    XposedCompat.log(
                        "[ImageViewerNativeShareHook] skipped: missing symbol fields " +
                            "(imageViewerShareConfigClass/itemClass/itemViewClass/iconResId)",
                    )
                }
                return
            }
            resolvedTargets = targets
            val baseRuntime = resolveBaseRuntime(cl)
            if (baseRuntime == null) {
                entryInstalled.set(false)
                XposedCompat.log("[ImageViewerNativeShareHook] skipped: base runtime unavailable")
                return
            }
            val mod = XposedCompat.module ?: run {
                entryInstalled.set(false)
                XposedCompat.log("[ImageViewerNativeShareHook] skipped: module unavailable")
                return
            }

            mod.hook(baseRuntime.sendMessageMethod).intercept { chain ->
                val runtime = ensureRuntime(cl, baseRuntime)
                if (runtime != null) {
                    tryInjectNativeShareItem(chain.args.firstOrNull(), runtime)
                }
                chain.proceed()
            }
            ensureRuntime(cl, baseRuntime)

            XposedCompat.log(
                "[ImageViewerNativeShareHook] hook INSTALLED: " +
                    "${baseRuntime.sendMessageMethod.declaringClass.name}.${baseRuntime.sendMessageMethod.name}(Message)",
            )
        } catch (t: Throwable) {
            entryInstalled.set(false)
            XposedCompat.log("[ImageViewerNativeShareHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private data class BaseRuntime(
        val customMessageClass: Class<*>,
        val sendMessageMethod: Method,
        val customMessageGetDataMethod: Method,
    )

    private data class SymbolTargets(
        val shareDialogConfigClass: String,
        val isImageViewerDialogField: String,
        val shareItemField: String,
        val addOutsideTextViewMethod: String,
        val getRequestDataMethod: String,
        val setRequestDataMethod: String,
        val getContextMethod: String,
        val shareItemClass: String,
        val shareItemTitleField: String?,
        val shareItemContentField: String?,
        val shareItemLinkUrlField: String?,
        val shareItemImageUriField: String,
        val shareItemImageUrlField: String?,
        val shareItemLocalFileField: String?,
        val shareDialogItemViewClass: String,
        val itemNameByResMethod: String,
        val itemNameByTextMethod: String,
        val shareIconResId: Int,
    )

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
        val shareIconResId: Int,
    )

    private data class SharePayload(
        val title: String,
        val shareText: String?,
        val imageContentUri: Uri?,
        val remoteImageUrl: String?,
    )

    private fun resolveBaseRuntime(cl: ClassLoader): BaseRuntime? {
        val messageManagerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.MESSAGE_MANAGER_CLASS, cl)
            ?: return null
        val messageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.MESSAGE_CLASS, cl) ?: return null
        val customMessageClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.CUSTOM_MESSAGE_CLASS, cl)
            ?: return null

        val sendMessageMethod = XposedCompat.findMethodOrNull(
            messageManagerClass,
            StableTiebaHookPoints.METHOD_SEND_MESSAGE,
            messageClass,
        ) ?: return null
        val customMessageGetDataMethod =
            ReflectionUtils.findMethodInHierarchy(customMessageClass, StableTiebaHookPoints.METHOD_GET_DATA)
                ?: return null
        return BaseRuntime(
            customMessageClass = customMessageClass,
            sendMessageMethod = sendMessageMethod,
            customMessageGetDataMethod = customMessageGetDataMethod,
        )
    }

    private fun ensureRuntime(cl: ClassLoader, baseRuntime: BaseRuntime): Runtime? {
        resolvedRuntime?.let { return it }
        val targets = resolvedTargets ?: return null
        synchronized(this) {
            resolvedRuntime?.let { return it }
            val runtime = resolveRuntime(cl, baseRuntime, targets) ?: return null
            resolvedRuntime = runtime
            ensureItemNameHookInstalled(runtime)
            return runtime
        }
    }

    private fun resolveRuntime(cl: ClassLoader, baseRuntime: BaseRuntime, targets: SymbolTargets): Runtime? {
        val shareDialogConfigClass = XposedCompat.findClassOrNull(targets.shareDialogConfigClass, cl) ?: return null
        val shareItemClass = XposedCompat.findClassOrNull(targets.shareItemClass, cl) ?: return null
        val shareDialogItemViewClass = XposedCompat.findClassOrNull(targets.shareDialogItemViewClass, cl) ?: return null

        val isImageViewerDialogField = findFieldOrNull(shareDialogConfigClass, targets.isImageViewerDialogField) ?: return null
        val shareItemField = findFieldOrNull(shareDialogConfigClass, targets.shareItemField) ?: return null
        val addOutsideTextViewMethod = XposedCompat.findMethodOrNull(
            shareDialogConfigClass,
            targets.addOutsideTextViewMethod,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            View.OnClickListener::class.java,
        ) ?: return null
        val getRequestDataMethod = ReflectionUtils.findMethodInHierarchy(
            shareDialogConfigClass,
            targets.getRequestDataMethod,
        )?.takeIf { method ->
            method.parameterTypes.isEmpty() && Map::class.java.isAssignableFrom(method.returnType)
        } ?: return null
        val setRequestDataMethod = XposedCompat.findMethodOrNull(
            shareDialogConfigClass,
            targets.setRequestDataMethod,
            Map::class.java,
        ) ?: return null
        val getContextMethod = ReflectionUtils.findMethodInHierarchy(
            shareDialogConfigClass,
            targets.getContextMethod,
        )?.takeIf { method ->
            method.parameterTypes.isEmpty() && Context::class.java.isAssignableFrom(method.returnType)
        } ?: return null

        val shareItemTitleField = targets.shareItemTitleField?.let { findFieldOrNull(shareItemClass, it) }
        val shareItemContentField = targets.shareItemContentField?.let { findFieldOrNull(shareItemClass, it) }
        val shareItemLinkUrlField = targets.shareItemLinkUrlField?.let { findFieldOrNull(shareItemClass, it) }
        val shareItemImageUriField = findFieldOrNull(shareItemClass, targets.shareItemImageUriField)
        val shareItemImageUrlField = targets.shareItemImageUrlField?.let { findFieldOrNull(shareItemClass, it) }
        val shareItemLocalFileField = targets.shareItemLocalFileField?.let { findFieldOrNull(shareItemClass, it) }

        val itemNameByResMethod = XposedCompat.findMethodOrNull(
            shareDialogItemViewClass,
            targets.itemNameByResMethod,
            Int::class.javaPrimitiveType!!,
        ) ?: return null
        val itemNameByTextMethod = XposedCompat.findMethodOrNull(
            shareDialogItemViewClass,
            targets.itemNameByTextMethod,
            String::class.java,
        ) ?: return null

        return Runtime(
            customMessageClass = baseRuntime.customMessageClass,
            shareDialogConfigClass = shareDialogConfigClass,
            sendMessageMethod = baseRuntime.sendMessageMethod,
            customMessageGetDataMethod = baseRuntime.customMessageGetDataMethod,
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
            shareIconResId = targets.shareIconResId,
        )
    }

    private fun toSymbolTargets(symbols: HookSymbols): SymbolTargets? {
        val configClass = symbols.imageViewerShareConfigClass?.takeIf { it.isNotBlank() } ?: return null
        val isDialogField = symbols.imageViewerShareIsDialogField?.takeIf { it.isNotBlank() } ?: return null
        val shareItemField = symbols.imageViewerShareItemField?.takeIf { it.isNotBlank() } ?: return null
        val addOutsideMethod = symbols.imageViewerShareAddOutsideMethod?.takeIf { it.isNotBlank() } ?: return null
        val getRequestDataMethod = symbols.imageViewerShareGetRequestDataMethod?.takeIf { it.isNotBlank() } ?: return null
        val setRequestDataMethod = symbols.imageViewerShareSetRequestDataMethod?.takeIf { it.isNotBlank() } ?: return null
        val getContextMethod = symbols.imageViewerShareGetContextMethod?.takeIf { it.isNotBlank() } ?: return null
        val shareItemClass = symbols.imageViewerShareItemClass?.takeIf { it.isNotBlank() } ?: return null
        val imageUriField = symbols.imageViewerShareItemImageUriField?.takeIf { it.isNotBlank() } ?: return null
        val itemViewClass = symbols.imageViewerShareItemViewClass?.takeIf { it.isNotBlank() } ?: return null
        val itemNameByResMethod = symbols.imageViewerShareItemNameByResMethod?.takeIf { it.isNotBlank() } ?: return null
        val itemNameByTextMethod = symbols.imageViewerShareItemNameByTextMethod?.takeIf { it.isNotBlank() } ?: return null
        val shareIconResId = symbols.imageViewerShareIconResId?.takeIf { it != 0 } ?: return null
        return SymbolTargets(
            shareDialogConfigClass = configClass,
            isImageViewerDialogField = isDialogField,
            shareItemField = shareItemField,
            addOutsideTextViewMethod = addOutsideMethod,
            getRequestDataMethod = getRequestDataMethod,
            setRequestDataMethod = setRequestDataMethod,
            getContextMethod = getContextMethod,
            shareItemClass = shareItemClass,
            shareItemTitleField = symbols.imageViewerShareItemTitleField,
            shareItemContentField = symbols.imageViewerShareItemContentField,
            shareItemLinkUrlField = symbols.imageViewerShareItemLinkUrlField,
            shareItemImageUriField = imageUriField,
            shareItemImageUrlField = symbols.imageViewerShareItemImageUrlField,
            shareItemLocalFileField = symbols.imageViewerShareItemLocalFileField,
            shareDialogItemViewClass = itemViewClass,
            itemNameByResMethod = itemNameByResMethod,
            itemNameByTextMethod = itemNameByTextMethod,
            shareIconResId = shareIconResId,
        )
    }

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
            val downloadedUri = downloadRemoteImageToContentUri(appContext, remoteUrl)
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
        val localContentUri = resolveLocalContentUri(context, imageUri, localFile)
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

    private fun resolveLocalContentUri(context: Context, imageUri: Uri?, localFile: String): Uri? {
        if (localFile.isNotBlank()) {
            val file = File(localFile)
            if (file.exists() && file.length() > 0L) {
                val uri = stageLocalFileToContentUri(context, file)
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
                        stageLocalFileToContentUri(context, file)
                    } else {
                        null
                    }
                }
            }
            else -> null
        }
    }

    private fun downloadRemoteImageToContentUri(context: Context, imageUrl: String): Uri? {
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
        return fileToContentUri(context, targetFile)
    }

    private fun createShareStagingFile(context: Context, nameHint: String): File? {
        val dir = File(context.cacheDir, SHARE_STAGING_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null
        val ext = guessImageExt(nameHint)
        return runCatching { File.createTempFile("share_", ext, dir) }.getOrNull()
    }

    private fun stageLocalFileToContentUri(context: Context, sourceFile: File): Uri? {
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
        return fileToContentUri(context, targetFile)
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

    private fun fileToContentUri(context: Context, file: File): Uri? {
        val authority = "${context.packageName}.fileprovider"
        val uri = getUriByFileProvider(context, authority, file) ?: return null
        return if (canOpenUri(context, uri)) uri else null
    }

    private fun getUriByFileProvider(context: Context, authority: String, file: File): Uri? {
        return runCatching {
            val method = resolveFileProviderGetUriMethod()
            method.invoke(null, context, authority, file) as? Uri
        }.onFailure { t ->
            val root = t.rootCause()
            XposedCompat.logW(
                "[ImageViewerNativeShareHook] FileProvider getUriForFile failed: " +
                    "authority=$authority file=${file.absolutePath} " +
                    "errType=${root.javaClass.name} err=${root.message}",
            )
        }.getOrNull()
    }

    private fun resolveFileProviderGetUriMethod(): Method {
        val targetClassLoader = targetAppClassLoader
            ?: throw ClassNotFoundException("target app classloader unavailable")
        val cached = fileProviderGetUriMethod
        if (cached != null && fileProviderMethodClassLoader === targetClassLoader) return cached
        val method = Class.forName(FILE_PROVIDER_CLASS, false, targetClassLoader).getDeclaredMethod(
            "getUriForFile",
            Context::class.java,
            String::class.java,
            File::class.java,
        )
        method.isAccessible = true
        fileProviderGetUriMethod = method
        fileProviderMethodClassLoader = targetClassLoader
        return method
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

    private fun findFieldOrNull(clazz: Class<*>, fieldName: String): Field? {
        return runCatching {
            clazz.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull()
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
