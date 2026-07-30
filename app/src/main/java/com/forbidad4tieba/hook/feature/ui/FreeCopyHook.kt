package com.forbidad4tieba.hook.feature.ui

import android.content.ClipData
import android.graphics.Color
import android.os.Looper
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.FreeCopyNativeSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyPopupSymbols
import com.forbidad4tieba.hook.ui.ModuleForegroundActivityTracker
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object FreeCopyHook {
    private const val MAX_PATCH_TRACE_LOG = 2
    private const val MAX_RUNTIME_FAILURE_LOG = 3
    private const val MAX_RUNTIME_DIAGNOSTIC_LOG = 8
    private const val MAX_RICH_TEXT_PARENT_DEPTH = 2
    private const val FORCED_HIGHLIGHT_COLOR = 0x6633B5E5

    private data class PostMetadata(
        val title: String?,
        val floor: Int,
    )

    private data class ClipboardCapture(
        var write: CapturedClipboardWrite? = null,
    )

    private data class CapturedClipboardWrite(
        val method: Method,
        val receiver: Any?,
        val arguments: Array<Any?>,
        val text: String,
    ) {
        fun replay() {
            method.invoke(receiver, *arguments)
        }
    }

    private data class LongPressInvocation(
        var handled: Boolean = false,
    )

    private data class PostDataLookup(
        val postData: Any?,
        val candidateCount: Int,
        val resolvedFloorCount: Int,
        val firstFloorCount: Int,
    )

    private val popupInstalled = AtomicBoolean(false)
    private val nativeInstalled = AtomicBoolean(false)
    private val popupPatchCount = AtomicInteger(0)
    private val runtimeFailureLogCount = AtomicInteger(0)
    private val runtimeDiagnosticLogCount = AtomicInteger(0)
    private val runtimeDiagnosticKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val metadataByPostData = Collections.synchronizedMap(WeakHashMap<Any, PostMetadata>())
    private val clipboardCapture = ThreadLocal<ClipboardCapture?>()
    private val longPressInvocation = ThreadLocal<LongPressInvocation?>()

    internal fun hookCommentInjection(popupSymbols: FreeCopyPopupSymbols) {
        if (!popupInstalled.compareAndSet(false, true)) return
        if (!hookPopupMenuText(popupSymbols)) {
            popupInstalled.set(false)
        }
    }

    internal fun hookNative(symbols: FreeCopyNativeSymbols) {
        if (!nativeInstalled.compareAndSet(false, true)) return
        val clipboardInstalled = hookClipboardWrites(symbols.clipboardWriteMethods)
        val metadataInstalled = hookMetadataParsers(symbols)
        val copyInstalled = if (clipboardInstalled && metadataInstalled) {
            hookPostDataCopy(symbols)
        } else {
            false
        }
        val longPressReady =
            copyInstalled &&
            ConfigManager.isFreeCopyPostLongPressEnabled &&
            symbols.postFloorMethod != null
        val bodyLongPressInstalled = if (
            longPressReady && symbols.richTextViewClass != null
        ) {
            hookPostBodyLongPress(symbols)
        } else {
            0
        }
        val titleLongPressInstalled = if (longPressReady) {
            hookPostTitleLongPress(symbols)
        } else {
            0
        }
        XposedCompat.log(
            "[FreeCopyHook] native install: clipboard=$clipboardInstalled, " +
                "metadata=$metadataInstalled, floor=${symbols.postFloorMethod != null}, " +
                "copy=$copyInstalled, " +
                "longPress={body=$bodyLongPressInstalled,title=$titleLongPressInstalled}",
        )
    }

    private fun hookPopupMenuText(popupSymbols: FreeCopyPopupSymbols): Boolean {
        val mod = XposedCompat.module ?: return false
        try {
            mod.hook(popupSymbols.contentViewMethod).intercept { chain ->
                val result = chain.proceed()
                if (
                    !ConfigManager.isFreeCopyEnabled ||
                    !ConfigManager.isFreeCopyCommentInjectionEnabled
                ) {
                    return@intercept result
                }
                val menu = chain.thisObject ?: return@intercept result
                val textView = try {
                    popupSymbols.textField.get(menu) as? TextView
                } catch (t: Throwable) {
                    logRuntimeFailure("popup text field", t)
                    null
                } ?: return@intercept result
                applyCommentPopupCopy(textView, "popup-menu-text")
                result
            }
            XposedCompat.log(
                "[FreeCopyHook] hook INSTALLED: popup menu text " +
                    "${popupSymbols.menuClass.name}.${popupSymbols.contentViewMethod.name}",
            )
            return true
        } catch (t: Throwable) {
            XposedCompat.log("[FreeCopyHook] hook popup menu text FAILED: ${t.message}")
            XposedCompat.log(t)
            return false
        }
    }

    private fun hookClipboardWrites(methods: List<Method>): Boolean {
        val mod = XposedCompat.module ?: return false
        var installed = 0
        for (method in methods) {
            try {
                mod.hook(method).intercept { chain ->
                    val capture = clipboardCapture.get() ?: return@intercept chain.proceed()
                    val text = extractClipboardText(method, chain.args.firstOrNull())
                        ?: return@intercept chain.proceed()
                    capture.write = CapturedClipboardWrite(
                        method = method,
                        receiver = chain.thisObject,
                        arguments = chain.args.toTypedArray(),
                        text = text,
                    )
                    null
                }
                installed++
            } catch (t: Throwable) {
                XposedCompat.logW(
                    "[FreeCopyHook] clipboard hook failed: " +
                        "${method.declaringClass.name}.${method.name}: ${t.message}",
                )
            }
        }
        return installed > 0
    }

    private fun extractClipboardText(method: Method, argument: Any?): String? {
        return when (method.name) {
            "setText" -> (argument as? CharSequence)?.toString()
            "setPrimaryClip" -> {
                val clip = argument as? ClipData ?: return null
                if (clip.itemCount <= 0) return null
                clip.getItemAt(0).text?.toString()
            }
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun hookMetadataParsers(symbols: FreeCopyNativeSymbols): Boolean {
        var installed = 0
        if (
            hookMetadataParser(
                method = symbols.postParseMethod,
                titleField = symbols.postTitleField,
                floorField = symbols.postFloorField,
                source = "Post",
            )
        ) {
            installed++
        }
        if (
            hookMetadataParser(
                method = symbols.subPostParseMethod,
                titleField = symbols.subPostTitleField,
                floorField = symbols.subPostFloorField,
                source = "SubPostList",
            )
        ) {
            installed++
        }
        return installed > 0
    }

    private fun hookMetadataParser(
        method: Method?,
        titleField: Field?,
        floorField: Field?,
        source: String,
    ): Boolean {
        if (method == null || titleField == null || floorField == null) return false
        val mod = XposedCompat.module ?: return false
        return try {
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                val postData = chain.thisObject ?: return@intercept result
                val protocolData = chain.args.firstOrNull() ?: return@intercept result
                try {
                    val floor = (floorField.get(protocolData) as? Number)?.toInt()
                        ?: return@intercept result
                    val title = titleField.get(protocolData) as? String
                    metadataByPostData[postData] = PostMetadata(title = title, floor = floor)
                } catch (t: Throwable) {
                    logRuntimeFailure("metadata $source", t)
                }
                result
            }
            true
        } catch (t: Throwable) {
            XposedCompat.logW(
                "[FreeCopyHook] metadata hook failed: " +
                    "${method.declaringClass.name}.${method.name}: ${t.message}",
            )
            false
        }
    }

    private fun hookPostDataCopy(symbols: FreeCopyNativeSymbols): Boolean {
        val mod = XposedCompat.module ?: return false
        return try {
            mod.hook(symbols.copyMethod).intercept { chain ->
                if (!ConfigManager.isFreeCopyEnabled) return@intercept chain.proceed()
                val postData = chain.thisObject ?: return@intercept chain.proceed()
                val longPress = longPressInvocation.get()
                val metadata = resolvePostMetadata(postData, symbols) ?: run {
                    logRuntimeDiagnostic(
                        key = "copy:floor_missing",
                        reason = "floor_missing",
                        details = "source=copy,longPress=${longPress != null}",
                    )
                    return@intercept chain.proceed()
                }
                val isPostBody = metadata.floor == 1
                val shouldOpen = when {
                    isPostBody && longPress != null -> ConfigManager.isFreeCopyPostLongPressEnabled
                    isPostBody -> ConfigManager.isFreeCopyPostBodyEnabled
                    longPress != null -> false
                    else -> ConfigManager.isFreeCopyCommentDialogEnabled
                }
                if (!shouldOpen || clipboardCapture.get() != null) {
                    return@intercept chain.proceed()
                }

                val capture = ClipboardCapture()
                clipboardCapture.set(capture)
                val result = try {
                    chain.proceed()
                } catch (t: Throwable) {
                    if (longPress == null) replayCaptured(capture.write)
                    throw t
                } finally {
                    clipboardCapture.remove()
                }
                val write = capture.write ?: run {
                    logRuntimeDiagnostic(
                        key = "copy:clipboard_capture_missing:${longPress != null}",
                        reason = "clipboard_capture_missing",
                        details = "longPress=${longPress != null},floor=${metadata.floor}",
                    )
                    return@intercept result
                }
                val shown = showFreeCopyDialog(
                    title = metadata.title.takeIf { isPostBody },
                    body = write.text,
                    fallback = write.takeIf { longPress == null },
                )
                if (shown) {
                    longPress?.handled = true
                } else if (longPress == null) {
                    replayCaptured(write)
                } else {
                    logRuntimeDiagnostic(
                        key = "longPress:dialog_not_shown",
                        reason = "dialog_not_shown",
                        details = "floor=${metadata.floor}",
                    )
                }
                result
            }
            true
        } catch (t: Throwable) {
            XposedCompat.log("[FreeCopyHook] PostData copy hook FAILED: ${t.message}")
            XposedCompat.log(t)
            false
        }
    }

    private fun hookPostBodyLongPress(symbols: FreeCopyNativeSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val richTextViewClass = symbols.richTextViewClass ?: return 0
        var installed = 0
        for (method in symbols.longPressMethods) {
            try {
                mod.hook(method).intercept { chain ->
                    if (
                        !ConfigManager.isFreeCopyEnabled ||
                        !ConfigManager.isFreeCopyPostLongPressEnabled
                    ) {
                        return@intercept chain.proceed()
                    }
                    val view = chain.args.firstOrNull() as? View
                        ?: return@intercept chain.proceed()
                    if (
                        (view !is ViewGroup && hasNonNullReferencePayload(method, chain.args)) ||
                        isRichTextChildView(view, richTextViewClass)
                    ) {
                        return@intercept chain.proceed()
                    }
                    val lookup = findPostBodyData(view, richTextViewClass, symbols)
                    val postData = lookup.postData ?: run {
                        val reason = when {
                            lookup.candidateCount == 0 -> "postData_missing"
                            lookup.resolvedFloorCount == 0 -> "floor_missing"
                            lookup.firstFloorCount > 1 -> "postData_ambiguous"
                            else -> "not_first_floor"
                        }
                        logRuntimeDiagnostic(
                            key = "longPress:$reason",
                            reason = reason,
                            details = "view=${view.javaClass.name}," +
                                "candidates=${lookup.candidateCount}," +
                                "resolvedFloor=${lookup.resolvedFloorCount}," +
                                "firstFloor=${lookup.firstFloorCount}",
                        )
                        return@intercept chain.proceed()
                    }

                    if (invokeLongPressCopy(postData, symbols, "body", view)) {
                        true
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            } catch (t: Throwable) {
                XposedCompat.logW(
                    "[FreeCopyHook] long press hook failed: " +
                        "${method.declaringClass.name}.${method.name}: ${t.message}",
                )
            }
        }
        return installed
    }

    private fun hasNonNullReferencePayload(method: Method, arguments: List<Any?>): Boolean {
        return method.parameterTypes.indices.drop(1).any { index ->
            !method.parameterTypes[index].isPrimitive && arguments.getOrNull(index) != null
        }
    }

    private fun isRichTextChildView(view: View, richTextViewClass: Class<*>): Boolean {
        if (richTextViewClass.isInstance(view)) return false
        var parent = view.parent
        var depth = 0
        while (depth < MAX_RICH_TEXT_PARENT_DEPTH) {
            val parentView = parent as? View ?: return false
            if (richTextViewClass.isInstance(parentView)) return true
            parent = parentView.parent
            depth++
        }
        return false
    }

    private fun hookPostTitleLongPress(symbols: FreeCopyNativeSymbols): Int {
        val mod = XposedCompat.module ?: return 0
        val titleContainerField = symbols.titleContainerField ?: return 0
        val titleTextField = symbols.titleTextField ?: return 0
        val titlePostDataMethod = symbols.titlePostDataMethod ?: return 0
        if (symbols.titleBindMethods.isEmpty()) return 0
        var installed = 0
        for (method in symbols.titleBindMethods) {
            try {
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (
                        !ConfigManager.isFreeCopyEnabled ||
                        !ConfigManager.isFreeCopyPostLongPressEnabled
                    ) {
                        return@intercept result
                    }
                    val controller = chain.thisObject ?: return@intercept result
                    val titleContainer = try {
                        titleContainerField.get(controller) as? ViewGroup
                    } catch (t: Throwable) {
                        logRuntimeFailure("title container", t)
                        null
                    } ?: return@intercept result
                    val titleView = try {
                        titleTextField.get(controller) as? TextView
                    } catch (t: Throwable) {
                        logRuntimeFailure("title TextView", t)
                        null
                    } ?: return@intercept result
                    val pageData = chain.args.firstOrNull()
                        ?.takeIf { titlePostDataMethod.declaringClass.isInstance(it) }
                        ?: return@intercept result
                    val postData = try {
                        titlePostDataMethod.invoke(pageData)
                    } catch (t: Throwable) {
                        logRuntimeFailure("title PostData", t)
                        null
                    }?.takeIf(symbols.postDataClass::isInstance) ?: run {
                        logRuntimeDiagnostic(
                            key = "titleLongPress:postData_missing",
                            reason = "postData_missing",
                            details = "view=${titleView.javaClass.name}",
                        )
                        return@intercept result
                    }
                    if (titleView.parent !== titleContainer) {
                        logRuntimeDiagnostic(
                            key = "titleLongPress:container_mismatch",
                            reason = "container_mismatch",
                            details = "container=${titleContainer.javaClass.name}," +
                                "parent=${(titleView.parent as? View)?.javaClass?.name}",
                        )
                        return@intercept result
                    }
                    titleContainer.setOnLongClickListener { view ->
                        if (
                            !ConfigManager.isFreeCopyEnabled ||
                            !ConfigManager.isFreeCopyPostLongPressEnabled
                        ) {
                            false
                        } else {
                            invokeLongPressCopy(postData, symbols, "title", view)
                        }
                    }
                    result
                }
                installed++
            } catch (t: Throwable) {
                XposedCompat.logW(
                    "[FreeCopyHook] title long press hook failed: " +
                        "${method.declaringClass.name}.${method.name}: ${t.message}",
                )
            }
        }
        return installed
    }

    private fun invokeLongPressCopy(
        postData: Any,
        symbols: FreeCopyNativeSymbols,
        source: String,
        view: View,
    ): Boolean {
        val metadata = resolvePostMetadata(postData, symbols)
        if (metadata == null || metadata.floor != 1) {
            logRuntimeDiagnostic(
                key = "${source}LongPress:first_floor_missing",
                reason = if (metadata == null) "floor_missing" else "not_first_floor",
                details = "view=${view.javaClass.name},floor=${metadata?.floor}",
            )
            return false
        }
        val invocation = LongPressInvocation()
        longPressInvocation.set(invocation)
        try {
            symbols.copyMethod.invoke(postData)
        } catch (t: Throwable) {
            logRuntimeFailure("$source long press copy", t)
        } finally {
            longPressInvocation.remove()
        }
        if (!invocation.handled) {
            logRuntimeDiagnostic(
                key = "${source}LongPress:not_handled",
                reason = "long_press_not_handled",
                details = "view=${view.javaClass.name}",
            )
        }
        return invocation.handled
    }

    private fun findPostBodyData(
        view: View,
        richTextViewClass: Class<*>,
        symbols: FreeCopyNativeSymbols,
    ): PostDataLookup {
        val candidates = ArrayList<Any>(2)
        collectPostDataCandidates(view.tag, symbols.postDataClass, candidates)

        var parent = view.parent
        var depth = 0
        while (depth < MAX_RICH_TEXT_PARENT_DEPTH) {
            val parentView = parent as? View ?: break
            if (richTextViewClass.isInstance(parentView)) {
                collectPostDataCandidates(parentView.tag, symbols.postDataClass, candidates)
            }
            parent = parentView.parent
            depth++
        }

        var match: Any? = null
        var resolvedFloorCount = 0
        var firstFloorCount = 0
        for (candidate in candidates) {
            val metadata = resolvePostMetadata(candidate, symbols) ?: continue
            resolvedFloorCount++
            if (metadata.floor != 1) continue
            firstFloorCount++
            if (match == null) match = candidate
        }
        return PostDataLookup(
            postData = match.takeIf { firstFloorCount == 1 },
            candidateCount = candidates.size,
            resolvedFloorCount = resolvedFloorCount,
            firstFloorCount = firstFloorCount,
        )
    }

    private fun collectPostDataCandidates(
        tag: Any?,
        postDataClass: Class<*>,
        out: MutableList<Any>,
    ) {
        val sparseArray = tag as? SparseArray<*> ?: return
        for (index in 0 until sparseArray.size()) {
            val value = sparseArray.valueAt(index)
            if (!postDataClass.isInstance(value)) continue
            if (out.none { existing -> existing === value }) {
                out.add(value)
            }
        }
    }

    private fun resolvePostMetadata(
        postData: Any,
        symbols: FreeCopyNativeSymbols,
    ): PostMetadata? {
        metadataByPostData[postData]?.let { return it }
        val floorMethod = symbols.postFloorMethod ?: return null
        val floor = try {
            (floorMethod.invoke(postData) as? Number)?.toInt()
        } catch (t: Throwable) {
            logRuntimeFailure("post floor", t)
            null
        } ?: return null
        return PostMetadata(title = null, floor = floor).also { metadata ->
            metadataByPostData[postData] = metadata
        }
    }

    private fun showFreeCopyDialog(
        title: String?,
        body: String,
        fallback: CapturedClipboardWrite?,
    ): Boolean {
        val activity = ModuleForegroundActivityTracker.currentActivity() ?: return false
        if (fallback == null && Looper.myLooper() != Looper.getMainLooper()) return false
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            FreeCopyDialog.show(activity, title, body)
        } else {
            try {
                activity.runOnUiThread {
                    if (!FreeCopyDialog.show(activity, title, body)) {
                        replayCaptured(fallback)
                    }
                }
                true
            } catch (t: Throwable) {
                logRuntimeFailure("show dialog", t)
                false
            }
        }
    }

    private fun replayCaptured(write: CapturedClipboardWrite?) {
        if (write == null) return
        try {
            write.replay()
        } catch (t: Throwable) {
            logRuntimeFailure("clipboard replay", t)
        }
    }

    private fun applyCommentPopupCopy(textView: TextView, source: String) {
        applySelectableCommon(
            textView = textView,
            source = source,
            expandLineLimit = true,
        )
    }

    private fun applySelectableCommon(
        textView: TextView,
        source: String,
        expandLineLimit: Boolean,
    ) {
        try {
            val needsSelectablePatch = needsSelectablePatch(textView)
            val needsMultilinePatch = needsMultilinePatch(textView, expandLineLimit)
            if (!needsSelectablePatch && !needsMultilinePatch) return

            if (needsSelectablePatch) {
                textView.setTextIsSelectable(true)
                textView.setLongClickable(true)
                textView.setFocusable(true)
                textView.setFocusableInTouchMode(true)
                textView.setClickable(true)
                if (Color.alpha(textView.highlightColor) == 0) {
                    textView.highlightColor = FORCED_HIGHLIGHT_COLOR
                }
            }
            if (needsMultilinePatch) {
                textView.setSingleLine(false)
                textView.maxLines = Int.MAX_VALUE
                textView.ellipsize = null
                textView.setHorizontallyScrolling(false)
            }
            if (!textView.isTextSelectable) {
                XposedCompat.logW(
                    "[FreeCopyHook] patch verify failed: " +
                        "source=$source, selectable=false, view=${textView.javaClass.name}",
                )
                return
            }
            val count = popupPatchCount.incrementAndGet()
            if (count <= MAX_PATCH_TRACE_LOG) {
                XposedCompat.logD(
                    "[FreeCopyHook] patched by $source " +
                        "(count=$count, view=${textView.javaClass.name}, multiline=$expandLineLimit)",
                )
            }
        } catch (t: Throwable) {
            logRuntimeFailure("selectable patch $source", t)
        }
    }

    private fun needsSelectablePatch(textView: TextView): Boolean {
        return !textView.isTextSelectable ||
            !textView.isLongClickable ||
            !textView.isFocusable ||
            !textView.isFocusableInTouchMode ||
            !textView.isClickable ||
            Color.alpha(textView.highlightColor) == 0
    }

    private fun needsMultilinePatch(textView: TextView, expandLineLimit: Boolean): Boolean {
        return expandLineLimit && (textView.maxLines != Int.MAX_VALUE || textView.ellipsize != null)
    }

    private fun logRuntimeFailure(source: String, throwable: Throwable) {
        val count = runtimeFailureLogCount.incrementAndGet()
        if (count <= MAX_RUNTIME_FAILURE_LOG) {
            XposedCompat.logW(
                "[FreeCopyHook] runtime failure: source=$source, err=${throwable.message}",
            )
        }
    }

    private fun logRuntimeDiagnostic(
        key: String,
        reason: String,
        details: String,
    ) {
        if (!runtimeDiagnosticKeys.add(key)) return
        val count = runtimeDiagnosticLogCount.incrementAndGet()
        if (count <= MAX_RUNTIME_DIAGNOSTIC_LOG) {
            XposedCompat.logW(
                "[FreeCopyHook] runtime diagnostic: reason=$reason,$details",
            )
        }
    }
}
