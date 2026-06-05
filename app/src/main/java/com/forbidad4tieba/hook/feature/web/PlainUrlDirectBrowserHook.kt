package com.forbidad4tieba.hook.feature.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.util.Patterns
import android.view.View
import android.widget.TextView
import com.forbidad4tieba.hook.symbol.model.MountCardLinkLayoutSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlClickableSpanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDispatchSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object PlainUrlDirectBrowserHook {
    private const val ORDINARY_URL_TYPE = 2
    private const val TIEBA_WEB_HOST = "tieba.baidu.com"
    private val installed = AtomicBoolean(false)
    private val installedMethodKeys = ConcurrentHashMap.newKeySet<String>()
    private val webUrlPattern = Pattern.compile(
        "(http://|ftp://|https://|www){1,1}[^\\u4E00-\\u9FA5\\s\"'<>\\[\\]{}]*",
        Pattern.CASE_INSENSITIVE,
    )

    internal data class RuntimeTargets(
        val spanTargets: PlainUrlClickableSpanSymbols?,
        val messageTarget: PlainUrlMessageDispatchSymbols?,
        val mountCardTargets: MountCardLinkLayoutSymbols?,
        val clickSpanMarkerField: Field?,
        val isClickMessageCmd: (Int) -> Boolean,
        val resolveMessageDataSymbols: (Any) -> PlainUrlMessageDataSymbols?,
    )

    internal fun hook(
        targets: RuntimeTargets,
    ) {
        val mod = XposedCompat.module ?: return
        if (!ConfigManager.isOpenWebLinkInSystemBrowserEnabled) {
            XposedCompat.logD("[PlainUrlDirectBrowserHook] skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[PlainUrlDirectBrowserHook] already installed, skip")
            return
        }

        try {
            val clickSpanField = targets.clickSpanMarkerField

            var installedCount = 0
            val spanTargets = targets.spanTargets
            if (spanTargets != null) {
                for (onClickMethod in spanTargets.onClickMethods) {
                    if (!installedMethodKeys.add(methodKey(onClickMethod))) continue
                    mod.hook(onClickMethod).intercept { chain ->
                        if (!ConfigManager.isOpenWebLinkInSystemBrowserEnabled) return@intercept chain.proceed()
                        val span = chain.thisObject ?: return@intercept chain.proceed()
                        val view = chain.args.firstOrNull() as? View
                        val normalizedUrl = resolveWebUrl(spanTargets.typeField, spanTargets.urlField, spanTargets.textField, span, view)
                            ?: return@intercept chain.proceed()

                        markClickSpan(clickSpanField)
                        openSystemBrowser(view, normalizedUrl)
                        null
                    }
                    installedCount++
                }
            }
            val messageTarget = targets.messageTarget
            if (messageTarget != null && installedMethodKeys.add(methodKey(messageTarget.dispatchMethod))) {
                mod.hook(messageTarget.dispatchMethod).intercept { chain ->
                    if (!ConfigManager.isOpenWebLinkInSystemBrowserEnabled) return@intercept chain.proceed()
                    val message = chain.args.firstOrNull() ?: return@intercept chain.proceed()
                    if (!messageTarget.customResponsedMessageClass.isInstance(message)) {
                        return@intercept chain.proceed()
                    }
                    val cmd = runCatching { messageTarget.getCmdMethod.invoke(message) as? Int }.getOrNull()
                        ?: return@intercept chain.proceed()
                    if (!targets.isClickMessageCmd(cmd)) return@intercept chain.proceed()
                    val data = runCatching { messageTarget.getDataMethod.invoke(message) }.getOrNull()
                        ?: return@intercept chain.proceed()
                    val dataSymbols = targets.resolveMessageDataSymbols(data)
                        ?: return@intercept chain.proceed()
                    val type = readIntField(dataSymbols.typeField, data)
                        ?: return@intercept chain.proceed()
                    val rawUrl = runCatching { dataSymbols.urlField.get(data) as? String }.getOrNull()
                    val rawText = runCatching { dataSymbols.textField.get(data) as? String }.getOrNull()
                    val normalizedUrl = chooseWebUrl(type, rawUrl, rawText, null)
                        ?: return@intercept chain.proceed()
                    val context = runCatching { messageTarget.getInstMethod.invoke(null) as? Context }.getOrNull()

                    markClickSpan(clickSpanField)
                    if (openSystemBrowser(context, normalizedUrl)) null else chain.proceed()
                }
                installedCount++
            }
            val mountCardTargets = targets.mountCardTargets
            if (mountCardTargets != null && installedMethodKeys.add(methodKey(mountCardTargets.onClickMethod))) {
                mod.hook(mountCardTargets.onClickMethod).intercept { chain ->
                    if (!ConfigManager.isOpenWebLinkInSystemBrowserEnabled) return@intercept chain.proceed()
                    val layout = chain.thisObject ?: return@intercept chain.proceed()
                    val view = chain.args.firstOrNull() as? View
                    val normalizedUrl = resolveMountCardWebUrl(mountCardTargets, layout)
                        ?: return@intercept chain.proceed()

                    openSystemBrowser(view ?: layout as? View, normalizedUrl)
                    null
                }
                installedCount++
            }
            if (installedCount <= 0) {
                installed.set(false)
                XposedCompat.logD("[PlainUrlDirectBrowserHook] no methods installed")
                return
            }

            XposedCompat.log(
                "[PlainUrlDirectBrowserHook] hook INSTALLED: count=$installedCount",
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[PlainUrlDirectBrowserHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun readIntField(field: Field, receiver: Any): Int? {
        return runCatching { field.get(receiver) as? Int }.getOrNull()
    }

    private fun methodKey(method: Method): String {
        return method.declaringClass.name + "#" + method.name + "(" +
            method.parameterTypes.joinToString(",") { it.name } + ")"
    }

    private fun markClickSpan(field: Field?) {
        runCatching { field?.setBoolean(null, true) }
    }

    private fun resolveWebUrl(
        typeField: Field,
        urlField: Field,
        textField: Field,
        span: Any,
        view: View?,
    ): String? {
        val type = readIntField(typeField, span)
        val rawUrl = runCatching { urlField.get(span) as? String }.getOrNull()
        val rawText = runCatching { textField.get(span) as? String }.getOrNull()
        val spanText = extractSpanText(view, span)

        return chooseWebUrl(type, rawUrl, rawText, spanText)
    }

    private fun chooseWebUrl(
        type: Int?,
        rawUrl: String?,
        rawText: String?,
        spanText: String?,
    ): String? {
        val targetUrl = normalizeWebUrl(rawUrl)
        val displayUrl = normalizeWebUrl(rawText)
        val clickedTextUrl = normalizeWebUrl(spanText)
        return if (type == ORDINARY_URL_TYPE) {
            targetUrl ?: displayUrl ?: clickedTextUrl
        } else {
            displayUrl ?: clickedTextUrl ?: targetUrl
        }
    }

    private fun resolveMountCardWebUrl(targets: MountCardLinkLayoutSymbols, layout: Any): String? {
        val data = runCatching { targets.dataField.get(layout) }.getOrNull() ?: return null
        val rawUrl = runCatching { targets.getUrlMethod.invoke(data) as? String }.getOrNull()
        return normalizeWebUrl(rawUrl)
    }

    private fun normalizeWebUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.let(::trimTrailingUrlPunctuation)?.takeIf { it.isNotEmpty() } ?: return null
        val candidates = decodedCandidates(trimmed, preferDecoded = !containsExplicitWebUrl(trimmed))
        val matched = candidates.firstNotNullOfOrNull(::matchExplicitWebUrl)
            ?: candidates.firstNotNullOfOrNull(::matchSdkWebUrl)
            ?: return null
        val lower = matched.lowercase(Locale.ROOT)
        val normalized = when {
            lower.startsWith("http://") -> matched
            lower.startsWith("https://") -> matched
            lower.startsWith("ftp://") -> matched
            lower.startsWith("rtsp://") -> matched
            else -> "http://$matched"
        }
        return normalized.takeUnless(::isTiebaWebUrl)
    }

    private fun isTiebaWebUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.ROOT) }.getOrNull()
        return host == TIEBA_WEB_HOST || host?.endsWith(".$TIEBA_WEB_HOST") == true
    }

    private fun decodedCandidates(value: String, preferDecoded: Boolean): List<String> {
        val decodedValues = ArrayList<String>(2)
        var current = value
        repeat(2) {
            val decoded = runCatching { Uri.decode(current) }.getOrNull()
            if (decoded.isNullOrEmpty() || decoded == current) return@repeat
            decodedValues.add(decoded)
            current = decoded
        }
        val values = LinkedHashSet<String>()
        if (!preferDecoded) values.add(value)
        values.addAll(decodedValues)
        values.add(value)
        return values.toList()
    }

    private fun containsExplicitWebUrl(text: String): Boolean {
        return webUrlPattern.matcher(text).find()
    }

    private fun matchExplicitWebUrl(text: String): String? {
        val appMatcher = webUrlPattern.matcher(text)
        if (appMatcher.find()) {
            return appMatcher.group()?.let(::trimTrailingUrlPunctuation)
        }
        return null
    }

    private fun matchSdkWebUrl(text: String): String? {
        val sdkMatcher = Patterns.WEB_URL.matcher(text)
        while (sdkMatcher.find()) {
            if (isPercentEncodedFragmentMatch(text, sdkMatcher.start())) continue
            return sdkMatcher.group()?.let(::trimTrailingUrlPunctuation)
        }
        return null
    }

    private fun isPercentEncodedFragmentMatch(text: String, start: Int): Boolean {
        return start > 0 && text[start - 1] == '%'
    }

    private fun extractSpanText(view: View?, span: Any): String? {
        val spanned = (view as? TextView)?.text as? Spanned ?: return null
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if (start < 0 || end <= start || end > spanned.length) return null
        return spanned.subSequence(start, end).toString()
    }

    private fun trimTrailingUrlPunctuation(value: String): String {
        return value.trim().trimEnd(
            '.', ',', ';', ')', ']', '}', '!', '?', '"', '\'',
            '\u3002', '\uFF0C', '\uFF1B', '\uFF09', '\uFF01', '\uFF1F', '\u201D', '\u2019',
        )
    }

    private fun openSystemBrowser(view: View?, url: String): Boolean {
        val context = view?.context ?: run {
            XposedCompat.logW("[PlainUrlDirectBrowserHook] browser launch skipped: view context unavailable")
            return false
        }
        return openSystemBrowser(context, url)
    }

    private fun openSystemBrowser(context: Context?, url: String): Boolean {
        if (context == null) {
            XposedCompat.logW("[PlainUrlDirectBrowserHook] browser launch skipped: context unavailable")
            return false
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: run {
            XposedCompat.logW("[PlainUrlDirectBrowserHook] browser launch skipped: invalid uri")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            XposedCompat.logD { "[PlainUrlDirectBrowserHook] open system browser: $url" }
            true
        } catch (_: ActivityNotFoundException) {
            XposedCompat.logW("[PlainUrlDirectBrowserHook] browser launch failed: no activity")
            false
        } catch (t: Throwable) {
            XposedCompat.logW("[PlainUrlDirectBrowserHook] browser launch failed: ${t.message}")
            false
        }
    }
}
