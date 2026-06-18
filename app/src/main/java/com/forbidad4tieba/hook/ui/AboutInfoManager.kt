package com.forbidad4tieba.hook.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.forbidad4tieba.hook.BuildConfig
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.concurrent.thread

object AboutInfoManager {
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val CACHE_DIR_NAME = "tbhook"
    private const val CACHE_FILE_NAME = "about_info_cache.json"
    private const val KEY_TELEMETRY_LAST_SUCCESS_DATE = "about_telemetry_last_success_date"
    private const val KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX = "remote_custom_dialog_ack:"
    private const val TELEMETRY_ACCOUNT_ID_SALT = "forbidad4tieba.telemetry.account_id.v1"
    private const val TELEMETRY_ACCOUNT_ID_FIRST_DELAY_MS = 5000L
    private const val TELEMETRY_ACCOUNT_ID_RETRY_COUNT = 2
    private const val TELEMETRY_ACCOUNT_ID_RETRY_DELAY_MS = 5000L
    private const val PATCH_CONFIG_ASSET_PATH = "assets/npatch/config.json"
    private const val PATCH_MANIFEST_META_KEY = "npatch"
    private const val PATCH_EMBEDDED_MODULE_PREFIX = "assets/npatch/modules/"
    private val TELEMETRY_VARIABLE_PATTERN = Regex("""\$\{([A-Za-z0-9_]+)\}""")
    private val ENVIRONMENT_RATING_LEVELS = intArrayOf(0, 1, 2)
    private val DEFAULT_ENVIRONMENT_CONTROLS = mapOf(
        0 to EnvironmentLevelControls(showWarningDialog = false, lockHiddenFeatures = false),
        1 to EnvironmentLevelControls(showWarningDialog = true, lockHiddenFeatures = false),
        2 to EnvironmentLevelControls(showWarningDialog = true, lockHiddenFeatures = false),
    )

    private val ABOUT_SOURCE_URLS = listOf(
        "https://raw.giteeusercontent.com/ratsoluos/detectupdate/raw/master/about.json",
        "https://raw.githubusercontent.com/aikavvak12una/ForbidAd4TieBa/refs/heads/main/about.json",
        "https://github.com/aikavvak12una/ForbidAd4TieBa/raw/refs/heads/main/about.json",
    )

    @Volatile
    private var cachedRemoteItems: List<AboutItem> = emptyList()
    @Volatile
    private var startupFetchTriggered = false
    private val remoteCustomDialogLock = Any()
    private val pendingRemoteCustomDialogs = ArrayDeque<RemoteCustomDialog>()
    private val seenRemoteCustomDialogKeys = LinkedHashSet<String>()
    private val telemetryAccountRetryRunning = AtomicBoolean(false)
    @Volatile private var pendingTelemetryConfigs: List<TelemetryConfig> = emptyList()
    @Volatile private var runtimeEnvironmentCache: RuntimeEnvironment? = null
    @Volatile private var startupFetchThread: Thread? = null
    @Volatile private var telemetryRetryThread: Thread? = null

    data class AboutItem(
        val title: String,
        val description: String,
        val url: String?,
    )

    private data class JsonAboutItem(
        val title: String,
        val description: String,
        val hasLink: Boolean,
        val url: String?,
    )

    private data class JsonPayload(
        val items: List<JsonAboutItem>,
        val telemetry: List<TelemetryConfig>,
        val controls: RemoteControls,
    )

    private data class RemoteControls(
        val environmentLevels: Map<Int, EnvironmentLevelControls>,
        val rules: List<RemoteRule> = emptyList(),
    ) {
        fun forLevel(level: Int): EnvironmentLevelControls {
            return environmentLevels[level] ?: DEFAULT_ENVIRONMENT_CONTROLS[level] ?: EnvironmentLevelControls()
        }
    }

    private data class EnvironmentLevelControls(
        val showWarningDialog: Boolean = false,
        val lockHiddenFeatures: Boolean = false,
    )

    private data class RemoteRule(
        val id: String,
        val enabled: Boolean,
        val condition: JSONObject?,
        val actions: List<RemoteAction>,
    )

    private sealed class RemoteAction {
        object ShowWarningDialog : RemoteAction()
        object LockHiddenFeatures : RemoteAction()
        data class CustomDialog(val dialog: RemoteCustomDialog) : RemoteAction()
    }

    data class RemoteCustomDialog(
        val id: String,
        val revision: Int,
        val title: String,
        val message: String,
        val urlButton: RemoteCustomDialogUrlButton?,
    ) {
        val ackKey: String
            get() = "$id:$revision"
    }

    data class RemoteCustomDialogUrlButton(
        val text: String,
        val url: String,
    )

    private data class EvaluatedRemoteControls(
        val showWarningDialog: Boolean,
        val lockHiddenFeatures: Boolean,
        val customDialogs: List<RemoteCustomDialog>,
        val matchedRuleCount: Int,
    )

    private data class RemoteConditionContext(
        val environment: RuntimeEnvironment,
        val accountId: String?,
    )

    private data class RemoteConditionValue(
        val text: String,
        val number: Long?,
    )

    private data class RemoteFieldLookup(
        val known: Boolean,
        val value: RemoteConditionValue?,
    )

    private enum class RemoteConditionResult {
        MATCH,
        NO_MATCH,
        IGNORED,
    }

    private data class FetchedPayload(
        val raw: String,
        val url: String,
        val elapsedMs: Long,
        val bytes: Int,
    )

    private data class CachedPayload(
        val raw: String,
        val elapsedMs: Long,
        val bytes: Int,
        val cacheAgeMs: Long,
    )

    private data class TelemetryConfig(
        val name: String,
        val endpoint: String,
        val method: String,
        val headers: Map<String, String>,
        val body: Any?,
        val successOncePerDay: Boolean,
        val connectTimeoutMs: Int,
        val readTimeoutMs: Int,
    )

    private data class TelemetryVariables(
        val uuid: String,
        val moduleVersion: String,
        val environment: RuntimeEnvironment,
    ) {
        fun stringValue(name: String): String? {
            return when (name) {
                "uuid" -> uuid
                "moduleVersion" -> moduleVersion
                "tiebaVersionName" -> environment.tiebaVersionName
                "runtimeEnvironment", "runtimeEnvironmentJson" -> environment.toJson().toString()
                "hostSourceKind" -> environment.hostSourceKind
                "androidSdk" -> environment.androidSdk.toString()
                "xposedApiVersion" -> environment.xposedApiVersion
                "xposedFrameworkName" -> environment.xposedFrameworkName
                "xposedFrameworkVersion" -> environment.xposedFrameworkVersion
                "xposedFrameworkVersionCode" -> environment.xposedFrameworkVersionCode
                "xposedFrameworkProperties" -> environment.xposedFrameworkProperties
                "xposedFrameworkCapabilities" -> environment.xposedFrameworkCapabilities.joinToString(",")
                "environmentRatingLevel" -> environment.environmentRatingLevel.toString()
                "runtimeKind" -> environment.runtimeKind
                "patchMode" -> environment.patchMode
                else -> null
            }
        }

        fun objectValue(token: String): JSONObject? {
            return when (token) {
                "\${runtimeEnvironment}", "\${runtimeEnvironmentJson}" -> environment.toJson()
                else -> null
            }
        }
    }

    private data class RuntimeEnvironment(
        val tiebaVersionName: String,
        val hostSourceKind: String,
        val androidSdk: Int,
        val xposedApiVersion: String,
        val xposedFrameworkName: String,
        val xposedFrameworkVersion: String,
        val xposedFrameworkVersionCode: String,
        val xposedFrameworkProperties: String,
        val xposedFrameworkCapabilities: List<String>,
        val environmentRatingLevel: Int,
        val runtimeKind: String,
        val patchMode: String,
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put("tiebaVersionName", tiebaVersionName)
                .put("hostSourceKind", hostSourceKind)
                .put("androidSdk", androidSdk)
                .put("xposedApiVersion", xposedApiVersion)
                .put("xposedFrameworkName", xposedFrameworkName)
                .put("xposedFrameworkVersion", xposedFrameworkVersion)
                .put("xposedFrameworkVersionCode", xposedFrameworkVersionCode)
                .put("xposedFrameworkProperties", xposedFrameworkProperties)
                .put("xposedFrameworkCapabilities", JSONArray(xposedFrameworkCapabilities))
                .put("environmentRatingLevel", environmentRatingLevel)
                .put("runtimeKind", runtimeKind)
                .put("patchMode", patchMode)
        }
    }

    private data class PatchModeDetection(
        val configFound: Boolean,
        val embeddedModulesFound: Boolean,
        val sourceChecked: Boolean,
        val sourceLooksPatched: Boolean,
    )

    private fun defaultRemoteControls(): RemoteControls {
        return RemoteControls(DEFAULT_ENVIRONMENT_CONTROLS)
    }

    internal fun hasPendingRemoteCustomDialogs(): Boolean {
        return synchronized(remoteCustomDialogLock) {
            pendingRemoteCustomDialogs.isNotEmpty()
        }
    }

    internal fun pollPendingRemoteCustomDialog(): RemoteCustomDialog? {
        return synchronized(remoteCustomDialogLock) {
            pendingRemoteCustomDialogs.pollFirst()
        }
    }

    internal fun markRemoteCustomDialogAcknowledged(context: Context, dialog: RemoteCustomDialog) {
        ConfigManager.getModuleStatePrefs(context).edit()
            .putBoolean("$KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX${dialog.ackKey}", true)
            .apply()
    }

    fun loadCachedItemsForSettings(): List<AboutItem> {
        return cachedRemoteItems
    }

    fun hotReloadBusyReason(): String? {
        if (startupFetchThread?.isAlive == true) return "about startup fetch active"
        if (telemetryRetryThread?.isAlive == true) return "about telemetry retry active"
        return null
    }

    fun prepareForHotReload() {
        cachedRemoteItems = emptyList()
        startupFetchTriggered = false
        pendingTelemetryConfigs = emptyList()
        runtimeEnvironmentCache = null
        telemetryAccountRetryRunning.set(false)
        startupFetchThread = null
        telemetryRetryThread = null
        synchronized(remoteCustomDialogLock) {
            pendingRemoteCustomDialogs.clear()
            seenRemoteCustomDialogKeys.clear()
        }
    }

    fun runtimeEnvironmentJsonForSettings(context: Context): String {
        return collectRuntimeEnvironment(context)
            .toJson()
            .toString(2)
    }

    fun environmentRatingLevelForSettings(context: Context): Int {
        return collectRuntimeEnvironment(context).environmentRatingLevel
    }

    fun applyCachedRuntimeControlsIfNeeded(context: Context) {
        val appContext = context.applicationContext ?: context
        val cachedPayload = readCachedPayload(appContext)
        if (cachedPayload == null) {
            applyRemoteControls(appContext, defaultRemoteControls())
            return
        }

        val parsedPayload = parseAndValidatePayload(cachedPayload.raw)
        if (parsedPayload == null) {
            applyRemoteControls(appContext, defaultRemoteControls())
            return
        }
        applyRemoteControls(appContext, parsedPayload.controls)
    }

    fun fetchAtStartupIfNeeded(context: Context) {
        val appContext = context.applicationContext ?: context
        synchronized(this) {
            if (startupFetchTriggered) {
                XposedCompat.log("[AboutInfo] startup fetch already triggered in this process, skip")
                return
            }
            startupFetchTriggered = true
        }

        val worker = thread(start = false, name = "tbhook-about-startup-fetch", isDaemon = true) {
            try {
                runCatching {
                    val cachedPayload = readCachedPayload(appContext)
                    if (cachedPayload == null) {
                        XposedCompat.logW("[AboutInfo] startup cache unavailable, skip active payload")
                    } else {
                        val parsedPayload = parseAndValidatePayload(cachedPayload.raw)
                        if (parsedPayload == null) {
                            XposedCompat.logW(
                                "[AboutInfo] payload rejected: source=cache url=cache " +
                                    "bytes=${cachedPayload.bytes} elapsedMs=${cachedPayload.elapsedMs} " +
                                    "cacheAgeMs=${cachedPayload.cacheAgeMs}"
                            )
                        } else {
                            applyParsedPayload(
                                context = appContext,
                                rawPayload = cachedPayload.raw,
                                parsedPayload = parsedPayload,
                                elapsedMs = cachedPayload.elapsedMs,
                                cacheAgeMs = cachedPayload.cacheAgeMs,
                            )
                        }
                    }

                    val remotePayload = fetchFromSources()
                    if (remotePayload == null) {
                        XposedCompat.logW("[AboutInfo] remote cache update skipped: no valid source")
                    } else {
                        writeCachedPayload(
                            context = appContext,
                            payload = remotePayload.raw,
                            url = remotePayload.url,
                            bytes = remotePayload.bytes,
                            elapsedMs = remotePayload.elapsedMs,
                        )
                        parseAndValidatePayload(remotePayload.raw)?.let { parsedRemote ->
                            applyParsedPayload(
                                context = appContext,
                                rawPayload = remotePayload.raw,
                                parsedPayload = parsedRemote,
                                source = "remote",
                                url = remotePayload.url,
                                elapsedMs = remotePayload.elapsedMs,
                                cacheAgeMs = -1L,
                            )
                        }
                    }
                }.onFailure { t ->
                    XposedCompat.log("[AboutInfo] startup fetch crashed: ${t.message}")
                    XposedCompat.log(t)
                }
            } finally {
                if (startupFetchThread === Thread.currentThread()) {
                    startupFetchThread = null
                }
            }
        }
        startupFetchThread = worker
        worker.start()
    }

    private fun fetchFromSources(): FetchedPayload? {
        for (url in ABOUT_SOURCE_URLS) {
            val fetched = fetchFromSource(url) ?: continue
            if (parseAndValidatePayload(fetched.raw) == null) {
                XposedCompat.logW(
                    "[AboutInfo] payload rejected: source=remote url=$url " +
                        "bytes=${fetched.bytes} elapsedMs=${fetched.elapsedMs} cacheAgeMs=-"
                )
                continue
            }
            return fetched
        }
        return null
    }

    private fun fetchFromSource(url: String): FetchedPayload? {
        val startMs = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TBHook/${BuildConfig.VERSION_NAME}")
            }

            val code = connection.responseCode
            val elapsed = System.currentTimeMillis() - startMs
            if (code in 200..299) {
                val payload = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }.trim()

                val bytes = payload.toByteArray(Charsets.UTF_8).size
                if (payload.isNotEmpty()) {
                    XposedCompat.log("[AboutInfo] fetch success: source=remote url=$url bytes=$bytes elapsedMs=$elapsed cacheAgeMs=-")
                    return FetchedPayload(
                        raw = payload,
                        url = url,
                        elapsedMs = elapsed,
                        bytes = bytes,
                    )
                }
                XposedCompat.logW("[AboutInfo] fetch got empty payload: source=remote url=$url bytes=$bytes elapsedMs=$elapsed cacheAgeMs=-")
            } else {
                XposedCompat.logW("[AboutInfo] fetch failed: source=remote url=$url code=$code elapsedMs=$elapsed cacheAgeMs=-")
            }
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - startMs
            XposedCompat.logW("[AboutInfo] fetch exception: source=remote url=$url elapsedMs=$elapsed cacheAgeMs=- msg=${t.message}")
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun applyParsedPayload(
        context: Context,
        rawPayload: String,
        parsedPayload: JsonPayload,
        source: String = "cache",
        url: String = "cache",
        elapsedMs: Long,
        cacheAgeMs: Long,
    ) {
        val items = parsedPayload.items.map {
            AboutItem(
                title = it.title,
                description = it.description,
                url = if (it.hasLink) it.url else null,
            )
        }
        cachedRemoteItems = items
        val bytes = rawPayload.toByteArray(Charsets.UTF_8).size
        val cacheAgeText = cacheAgeMs.takeIf { it >= 0L }?.toString() ?: "-"
        XposedCompat.log(
            "[AboutInfo] payload accepted: source=$source url=$url " +
                "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=$cacheAgeText itemCount=${items.size}"
        )
        applyRemoteControls(context, parsedPayload.controls)
        reportTelemetryIfNeeded(context, parsedPayload.telemetry)
    }

    private fun readCachedPayload(context: Context): CachedPayload? {
        val startMs = System.currentTimeMillis()
        val file = cacheFile(context)
        if (!file.isFile) {
            val elapsed = System.currentTimeMillis() - startMs
            XposedCompat.logD("[AboutInfo] cache miss: source=cache url=cache bytes=0 elapsedMs=$elapsed cacheAgeMs=-")
            return null
        }
        return try {
            val payload = file.readText(Charsets.UTF_8).trim()
            val elapsed = System.currentTimeMillis() - startMs
            val bytes = payload.toByteArray(Charsets.UTF_8).size
            val cacheAgeMs = (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0L)
            if (payload.isEmpty()) {
                XposedCompat.logW("[AboutInfo] cache empty: source=cache url=cache bytes=$bytes elapsedMs=$elapsed cacheAgeMs=$cacheAgeMs")
                null
            } else {
                XposedCompat.log("[AboutInfo] cache loaded: source=cache url=cache bytes=$bytes elapsedMs=$elapsed cacheAgeMs=$cacheAgeMs")
                CachedPayload(
                    raw = payload,
                    elapsedMs = elapsed,
                    bytes = bytes,
                    cacheAgeMs = cacheAgeMs,
                )
            }
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - startMs
            XposedCompat.logW("[AboutInfo] cache read exception: source=cache url=cache elapsedMs=$elapsed cacheAgeMs=- msg=${t.message}")
            null
        }
    }

    private fun writeCachedPayload(
        context: Context,
        payload: String,
        url: String,
        bytes: Int,
        elapsedMs: Long,
    ) {
        val file = cacheFile(context)
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            XposedCompat.logW(
                "[AboutInfo] cache update failed: source=remote url=$url " +
                    "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=- msg=mkdirs"
            )
            return
        }

        val tmpFile = File(parent, "$CACHE_FILE_NAME.tmp")
        try {
            tmpFile.writeText(payload, Charsets.UTF_8)
            if (tmpFile.renameTo(file)) {
                XposedCompat.log(
                    "[AboutInfo] cache updated for next startup: source=remote url=$url " +
                        "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=0"
                )
            } else {
                tmpFile.delete()
                XposedCompat.logW(
                    "[AboutInfo] cache update failed: source=remote url=$url " +
                        "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=- msg=rename"
                )
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            XposedCompat.logW(
                "[AboutInfo] cache update exception: source=remote url=$url " +
                    "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=- msg=${t.message}"
            )
        }
    }

    private fun cacheFile(context: Context): File {
        return File(File(context.filesDir, CACHE_DIR_NAME), CACHE_FILE_NAME)
    }

    private fun parseAndValidatePayload(payload: String): JsonPayload? {
        try {
            val root = JSONObject(payload)
            val schema = root.optInt("schema", -1)
            if (schema != 1) {
                XposedCompat.logW("[AboutInfo] parse failed: schema=$schema")
                return null
            }

            val itemsArray = root.optJSONArray("items")
            if (itemsArray == null || itemsArray.length() == 0) {
                XposedCompat.logW("[AboutInfo] parse failed: empty items")
                return null
            }

            val parsed = ArrayList<JsonAboutItem>(itemsArray.length())
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i)
                if (item == null) {
                    XposedCompat.logW("[AboutInfo] parse failed: items[$i] is not object")
                    return null
                }

                val title = item.optString("title", "").trim()
                val description = item.optString("description", "").trim()
                if (title.isEmpty() || description.isEmpty()) {
                    XposedCompat.logW("[AboutInfo] parse failed: items[$i] title/description empty")
                    return null
                }

                val hasLink = item.optBoolean("hasLink", false)
                val itemUrl = item.optString("url", "").trim().ifEmpty { null }
                if (hasLink && (itemUrl == null || !isHttpOrHttpsUrl(itemUrl))) {
                    XposedCompat.logW("[AboutInfo] parse failed: items[$i] invalid url")
                    return null
                }

                parsed.add(
                    JsonAboutItem(
                        title = title,
                        description = description,
                        hasLink = hasLink,
                        url = if (hasLink) itemUrl else null,
                    )
                )
            }
            return JsonPayload(
                items = parsed,
                telemetry = parseTelemetryConfig(root),
                controls = parseRemoteControls(root),
            )
        } catch (t: Throwable) {
            XposedCompat.logW("[AboutInfo] parse exception: ${t.message}")
            return null
        }
    }

    private fun parseRemoteControls(root: JSONObject): RemoteControls {
        val defaultControls = defaultRemoteControls()
        val controls = root.optJSONObject("controls") ?: return defaultControls
        val rules = parseRemoteRules(controls)
        val levels = controls.optJSONObject("environmentLevels")
            ?: return defaultControls.copy(rules = rules)

        val parsedLevels = LinkedHashMap<Int, EnvironmentLevelControls>()
        for (level in ENVIRONMENT_RATING_LEVELS) {
            val defaultLevelControls = defaultControls.forLevel(level)
            val levelControls = levels.optJSONObject(level.toString())
            parsedLevels[level] = if (levelControls == null) {
                defaultLevelControls
            } else {
                EnvironmentLevelControls(
                    showWarningDialog = optRemoteBoolean(
                        source = levelControls,
                        key = "showWarningDialog",
                        defaultValue = defaultLevelControls.showWarningDialog,
                    ),
                    lockHiddenFeatures = optRemoteBoolean(
                        source = levelControls,
                        key = "lockHiddenFeatures",
                        defaultValue = defaultLevelControls.lockHiddenFeatures,
                    ),
                )
            }
        }
        return RemoteControls(parsedLevels, rules)
    }

    private fun parseRemoteRules(controls: JSONObject): List<RemoteRule> {
        val rules = controls.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<RemoteRule>(rules.length())
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i)
            if (rule == null) {
                XposedCompat.logD("[AboutInfo] controls.rules[$i] ignored: not object")
                continue
            }
            parseRemoteRule(rule, i)?.let(out::add)
        }
        return out
    }

    private fun parseRemoteRule(rule: JSONObject, index: Int): RemoteRule? {
        return try {
            val enabled = optRemoteBoolean(rule, "enabled", true)
            val id = rule.optString("id", "rule_${index + 1}").trim().ifEmpty { "rule_${index + 1}" }
            val condition = rule.optJSONObject("when")
            val actions = parseRemoteActions(rule.optJSONArray("actions"), id)
            if (actions.isEmpty()) {
                XposedCompat.logD("[AboutInfo] controls.rules[$index] ignored: actions empty id=$id")
                return null
            }
            RemoteRule(
                id = id,
                enabled = enabled,
                condition = condition,
                actions = actions,
            )
        } catch (t: Throwable) {
            XposedCompat.logW("[AboutInfo] controls.rules[$index] ignored: ${t.message}")
            null
        }
    }

    private fun parseRemoteActions(actions: JSONArray?, ruleId: String): List<RemoteAction> {
        if (actions == null) return emptyList()
        val out = ArrayList<RemoteAction>(actions.length())
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i)
            if (action == null) {
                XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].actions[$i] ignored: not object")
                continue
            }
            when (action.optString("type", "").trim()) {
                "showWarningDialog" -> out.add(RemoteAction.ShowWarningDialog)
                "lockHiddenFeatures" -> out.add(RemoteAction.LockHiddenFeatures)
                "customDialog" -> parseRemoteCustomDialog(action, ruleId)?.let {
                    out.add(RemoteAction.CustomDialog(it))
                }
                else -> {
                    XposedCompat.logD(
                        "[AboutInfo] controls.rules[$ruleId].actions[$i] ignored: unknown type"
                    )
                }
            }
        }
        return out
    }

    private fun parseRemoteCustomDialog(action: JSONObject, ruleId: String): RemoteCustomDialog? {
        val id = action.optString("id", "").trim()
        val revision = action.optInt("revision", 0)
        val title = action.optString("title", "").trim()
        val message = action.optString("message", "").trim()
        if (id.isEmpty() || revision <= 0 || title.isEmpty() || message.isEmpty()) {
            XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].customDialog ignored: required field missing")
            return null
        }

        val urlButton = action.optJSONObject("urlButton")?.let { button ->
            val text = button.optString("text", "").trim()
            val url = button.optString("url", "").trim()
            if (text.isNotEmpty() && isHttpOrHttpsUrl(url)) {
                RemoteCustomDialogUrlButton(text = text, url = url)
            } else {
                XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].customDialog urlButton ignored")
                null
            }
        }

        return RemoteCustomDialog(
            id = id,
            revision = revision,
            title = title,
            message = message,
            urlButton = urlButton,
        )
    }

    private fun optRemoteBoolean(
        source: JSONObject,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val value = if (source.has(key)) source.opt(key) else null
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase(Locale.ROOT)) {
                "1", "true", "yes", "on", "enabled" -> true
                "0", "false", "no", "off", "disabled" -> false
                else -> defaultValue
            }
            else -> defaultValue
        }
    }

    private fun applyRemoteControls(context: Context, controls: RemoteControls) {
        val environment = collectRuntimeEnvironment(context)
        val level = environment.environmentRatingLevel
        val levelControls = controls.forLevel(level)
        val evaluatedRules = evaluateRemoteRules(
            context = context,
            rules = controls.rules,
            conditionContext = RemoteConditionContext(
                environment = environment,
                accountId = TiebaAccountIdentity.currentAccountId(context),
            ),
        )
        ConfigManager.applyRemoteEnvironmentControls(
            context = context,
            showWarningDialog = levelControls.showWarningDialog || evaluatedRules.showWarningDialog,
            lockHiddenFeatures = levelControls.lockHiddenFeatures || evaluatedRules.lockHiddenFeatures,
        )
        enqueueRemoteCustomDialogs(context, evaluatedRules.customDialogs)
        XposedCompat.logD(
            "[AboutInfo] remote controls applied: " +
                "environmentRatingLevel=$level " +
                "showWarningDialog=${levelControls.showWarningDialog || evaluatedRules.showWarningDialog} " +
                "lockHiddenFeatures=${levelControls.lockHiddenFeatures || evaluatedRules.lockHiddenFeatures} " +
                "matchedRules=${evaluatedRules.matchedRuleCount} " +
                "customDialogs=${evaluatedRules.customDialogs.size}"
        )
    }

    private fun evaluateRemoteRules(
        context: Context,
        rules: List<RemoteRule>,
        conditionContext: RemoteConditionContext,
    ): EvaluatedRemoteControls {
        var showWarningDialog = false
        var lockHiddenFeatures = false
        val customDialogs = ArrayList<RemoteCustomDialog>()
        var matchedRuleCount = 0

        for (rule in rules) {
            if (!rule.enabled) continue
            if (evaluateRemoteCondition(rule.condition, conditionContext) != RemoteConditionResult.MATCH) {
                continue
            }
            matchedRuleCount += 1
            for (action in rule.actions) {
                when (action) {
                    RemoteAction.ShowWarningDialog -> showWarningDialog = true
                    RemoteAction.LockHiddenFeatures -> lockHiddenFeatures = true
                    is RemoteAction.CustomDialog -> {
                        if (!isRemoteCustomDialogAcknowledged(context, action.dialog)) {
                            customDialogs.add(action.dialog)
                        }
                    }
                }
            }
        }

        return EvaluatedRemoteControls(
            showWarningDialog = showWarningDialog,
            lockHiddenFeatures = lockHiddenFeatures,
            customDialogs = customDialogs,
            matchedRuleCount = matchedRuleCount,
        )
    }

    private fun evaluateRemoteCondition(
        condition: JSONObject?,
        context: RemoteConditionContext,
    ): RemoteConditionResult {
        if (condition == null) return RemoteConditionResult.NO_MATCH

        condition.optJSONArray("all")?.let { conditions ->
            var hasRecognizedCondition = false
            for (i in 0 until conditions.length()) {
                when (evaluateRemoteCondition(conditions.optJSONObject(i), context)) {
                    RemoteConditionResult.NO_MATCH -> return RemoteConditionResult.NO_MATCH
                    RemoteConditionResult.MATCH -> hasRecognizedCondition = true
                    RemoteConditionResult.IGNORED -> Unit
                }
            }
            return if (hasRecognizedCondition) {
                RemoteConditionResult.MATCH
            } else {
                RemoteConditionResult.IGNORED
            }
        }

        condition.optJSONArray("any")?.let { conditions ->
            var hasRecognizedCondition = false
            for (i in 0 until conditions.length()) {
                when (evaluateRemoteCondition(conditions.optJSONObject(i), context)) {
                    RemoteConditionResult.MATCH -> return RemoteConditionResult.MATCH
                    RemoteConditionResult.NO_MATCH -> hasRecognizedCondition = true
                    RemoteConditionResult.IGNORED -> Unit
                }
            }
            return if (hasRecognizedCondition) {
                RemoteConditionResult.NO_MATCH
            } else {
                RemoteConditionResult.IGNORED
            }
        }

        condition.optJSONObject("not")?.let { nested ->
            return when (evaluateRemoteCondition(nested, context)) {
                RemoteConditionResult.MATCH -> RemoteConditionResult.NO_MATCH
                RemoteConditionResult.NO_MATCH -> RemoteConditionResult.MATCH
                RemoteConditionResult.IGNORED -> RemoteConditionResult.IGNORED
            }
        }

        return evaluateRemoteConditionLeaf(condition, context)
    }

    private fun evaluateRemoteConditionLeaf(
        condition: JSONObject,
        context: RemoteConditionContext,
    ): RemoteConditionResult {
        val field = condition.optString("field", "").trim()
        val op = condition.optString("op", "").trim()
        if (field.isEmpty() || op.isEmpty() || !condition.has("value")) {
            return RemoteConditionResult.IGNORED
        }

        val lookup = remoteFieldValue(field, context)
        if (!lookup.known) return RemoteConditionResult.IGNORED
        val actual = lookup.value ?: return RemoteConditionResult.NO_MATCH
        val expected = condition.opt("value")

        val matched = when (op) {
            "eq" -> remoteValueEquals(actual, expected)
            "neq" -> !remoteValueEquals(actual, expected)
            "in" -> remoteValueList(expected).any { remoteValueEquals(actual, it) }
            "not_in" -> remoteValueList(expected).none { remoteValueEquals(actual, it) }
            "lt" -> compareRemoteNumber(actual, expected) { a, b -> a < b }
            "lte" -> compareRemoteNumber(actual, expected) { a, b -> a <= b }
            "gt" -> compareRemoteNumber(actual, expected) { a, b -> a > b }
            "gte" -> compareRemoteNumber(actual, expected) { a, b -> a >= b }
            "matches" -> remoteValueMatches(actual, expected)
            else -> return RemoteConditionResult.IGNORED
        }
        return if (matched) RemoteConditionResult.MATCH else RemoteConditionResult.NO_MATCH
    }

    private fun remoteFieldValue(
        field: String,
        context: RemoteConditionContext,
    ): RemoteFieldLookup {
        val environment = context.environment
        return when (field) {
            "module_version_code" -> RemoteFieldLookup(
                known = true,
                value = remoteNumberValue(BuildConfig.VERSION_CODE.toLong()),
            )
            "account_id" -> RemoteFieldLookup(
                known = true,
                value = context.accountId?.let(::remoteStringValue),
            )
            "environment_level" -> RemoteFieldLookup(
                known = true,
                value = remoteNumberValue(environment.environmentRatingLevel.toLong()),
            )
            "xposed_framework_name" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.xposedFrameworkName),
            )
            "patch_mode" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.patchMode),
            )
            "runtime_kind" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.runtimeKind),
            )
            "xposed_framework_version_code" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.xposedFrameworkVersionCode),
            )
            else -> RemoteFieldLookup(known = false, value = null)
        }
    }

    private fun remoteStringValue(value: String): RemoteConditionValue {
        return RemoteConditionValue(
            text = value,
            number = value.toLongOrNull(),
        )
    }

    private fun remoteNumberValue(value: Long): RemoteConditionValue {
        return RemoteConditionValue(
            text = value.toString(),
            number = value,
        )
    }

    private fun remoteValueEquals(actual: RemoteConditionValue, expected: Any?): Boolean {
        val expectedNumber = expected?.remoteLongOrNull()
        if (actual.number != null && expectedNumber != null) {
            return actual.number == expectedNumber
        }
        return actual.text == expected?.toString().orEmpty()
    }

    private fun remoteValueList(value: Any?): List<Any?> {
        if (value == null || value == JSONObject.NULL) return emptyList()
        if (value !is JSONArray) return listOf(value)
        val out = ArrayList<Any?>(value.length())
        for (i in 0 until value.length()) {
            out.add(value.opt(i))
        }
        return out
    }

    private fun compareRemoteNumber(
        actual: RemoteConditionValue,
        expected: Any?,
        predicate: (Long, Long) -> Boolean,
    ): Boolean {
        val actualNumber = actual.number ?: return false
        val expectedNumber = expected.remoteLongOrNull() ?: return false
        return predicate(actualNumber, expectedNumber)
    }

    private fun remoteValueMatches(actual: RemoteConditionValue, expected: Any?): Boolean {
        val pattern = expected?.toString()?.takeIf { it.isNotBlank() } ?: return false
        return try {
            Regex(pattern).containsMatchIn(actual.text)
        } catch (t: Throwable) {
            XposedCompat.logD("[AboutInfo] remote condition regex ignored: ${t.message}")
            false
        }
    }

    private fun Any?.remoteLongOrNull(): Long? {
        return when (this) {
            is Number -> toLong()
            is String -> trim().toLongOrNull()
            else -> null
        }
    }

    private fun isRemoteCustomDialogAcknowledged(
        context: Context,
        dialog: RemoteCustomDialog,
    ): Boolean {
        return ConfigManager.getModuleStatePrefs(context)
            .getBoolean("$KEY_REMOTE_CUSTOM_DIALOG_ACK_PREFIX${dialog.ackKey}", false)
    }

    private fun enqueueRemoteCustomDialogs(context: Context, dialogs: List<RemoteCustomDialog>) {
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

    private fun parseTelemetryConfig(root: JSONObject): List<TelemetryConfig> {
        val telemetry = root.opt("telemetry")
        return when (telemetry) {
            null, JSONObject.NULL -> emptyList()
            is JSONObject -> listOfNotNull(parseTelemetryRequest(telemetry, "default"))
            is JSONArray -> {
                val out = ArrayList<TelemetryConfig>(telemetry.length())
                for (i in 0 until telemetry.length()) {
                    val item = telemetry.optJSONObject(i)
                    if (item == null) {
                        XposedCompat.logD("[AboutInfo] telemetry[$i] ignored: not object")
                        continue
                    }
                    parseTelemetryRequest(item, "request_${i + 1}")?.let(out::add)
                }
                out
            }
            else -> {
                XposedCompat.logD("[AboutInfo] telemetry ignored: unsupported type")
                emptyList()
            }
        }
    }

    private fun parseTelemetryRequest(telemetry: JSONObject, fallbackName: String): TelemetryConfig? {
        return try {
            val enabled = telemetry.optBoolean("enabled", true)
            if (!enabled) {
                XposedCompat.logD("[AboutInfo] telemetry disabled by remote config")
                return null
            }

            val request = telemetry.optJSONObject("request")
            val name = telemetry.optString("name", fallbackName).trim().ifEmpty { fallbackName }
            val endpoint = telemetry.optString("endpoint", "").trim()
            if (endpoint.isEmpty()) {
                XposedCompat.logD("[AboutInfo] telemetry[$name] ignored: empty endpoint")
                return null
            }
            val method = telemetry.optString(
                "method",
                request?.optString("method", "POST") ?: "POST",
            ).trim().ifEmpty { "POST" }
            val headers = parseTelemetryHeaders(
                telemetry.optJSONObject("headers") ?: request?.optJSONObject("headers")
            )
            val successOncePerDay = parseTelemetrySuccessOncePerDay(telemetry)
            val body = when {
                telemetry.has("body") -> telemetry.opt("body")
                request?.has("body") == true -> request.opt("body")
                else -> null
            }
            val connectTimeoutMs = parseTelemetryTimeoutMs(
                telemetry = telemetry,
                request = request,
                primaryKey = "connectTimeoutMs",
                defaultValue = CONNECT_TIMEOUT_MS,
            )
            val readTimeoutMs = parseTelemetryTimeoutMs(
                telemetry = telemetry,
                request = request,
                primaryKey = "readTimeoutMs",
                defaultValue = READ_TIMEOUT_MS,
            )

            TelemetryConfig(
                name = name,
                endpoint = endpoint,
                method = method,
                headers = headers,
                body = body,
                successOncePerDay = successOncePerDay,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
            )
        } catch (t: Throwable) {
            XposedCompat.logD("[AboutInfo] telemetry parse ignored: ${t.message}")
            null
        }
    }

    private fun parseTelemetrySuccessOncePerDay(telemetry: JSONObject): Boolean {
        return when (val schedule = telemetry.opt("schedule")) {
            is Boolean -> schedule
            is JSONObject -> schedule.optBoolean("successOncePerDay", true)
            else -> telemetry.optBoolean("successOncePerDay", true)
        }
    }

    private fun parseTelemetryTimeoutMs(
        telemetry: JSONObject,
        request: JSONObject?,
        primaryKey: String,
        defaultValue: Int,
    ): Int {
        val value = when {
            telemetry.has(primaryKey) -> telemetry.optInt(primaryKey, defaultValue)
            request?.has(primaryKey) == true -> request.optInt(primaryKey, defaultValue)
            telemetry.has("timeoutMs") -> telemetry.optInt("timeoutMs", defaultValue)
            request?.has("timeoutMs") == true -> request.optInt("timeoutMs", defaultValue)
            else -> defaultValue
        }
        return if (value > 0) value else defaultValue
    }

    private fun parseTelemetryHeaders(headers: JSONObject?): Map<String, String> {
        if (headers == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val keys = headers.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.isEmpty()) continue
            val value = headers.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            out[key] = value.toString()
        }
        return out
    }

    private fun reportTelemetryIfNeeded(context: Context, configs: List<TelemetryConfig>) {
        pendingTelemetryConfigs = configs.toList()
        if (configs.isEmpty()) {
            XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
            return
        }
        scheduleTelemetryAccountRetry(context)
    }

    private fun uploadTelemetryIfAccountReady(
        context: Context,
        configs: List<TelemetryConfig>,
    ): Boolean {
        if (configs.isEmpty()) {
            XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
            return true
        }

        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        val today = todayDateString()
        val uuid = telemetryUuidForAccount(context) ?: return false
        val variables = TelemetryVariables(
            uuid = uuid,
            moduleVersion = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
            environment = collectRuntimeEnvironment(context),
        )

        for (config in configs) {
            val successDateKey = telemetrySuccessDateKey(config.name)
            val successSignature = telemetrySuccessSignature(today, variables.moduleVersion, variables.uuid)
            if (config.successOncePerDay && statePrefs.getString(successDateKey, null) == successSignature) {
                XposedCompat.logD(
                    "[AboutInfo] telemetry[${config.name}] skipped: already uploaded " +
                        "today=$today moduleVersion=${variables.moduleVersion}"
                )
                continue
            }

            if (uploadTelemetry(config, variables)) {
                if (config.successOncePerDay) {
                    val saved = statePrefs.edit()
                        .putString(successDateKey, successSignature)
                        .commit()
                    XposedCompat.logD(
                        "[AboutInfo] telemetry[${config.name}] success: " +
                            "date=$today moduleVersion=${variables.moduleVersion} saved=$saved"
                    )
                } else {
                    XposedCompat.logD("[AboutInfo] telemetry[${config.name}] success")
                }
            } else {
                XposedCompat.logD("[AboutInfo] telemetry[${config.name}] failed")
            }
        }
        return true
    }

    private fun scheduleTelemetryAccountRetry(context: Context) {
        if (!telemetryAccountRetryRunning.compareAndSet(false, true)) {
            XposedCompat.logD("[AboutInfo] telemetry account retry already scheduled")
            return
        }
        val appContext = context.applicationContext ?: context
        val worker = thread(start = false, name = "tbhook-telemetry-account-retry", isDaemon = true) {
            try {
                Thread.sleep(TELEMETRY_ACCOUNT_ID_FIRST_DELAY_MS)
                for (attempt in 0..TELEMETRY_ACCOUNT_ID_RETRY_COUNT) {
                    val uploadConfigs = pendingTelemetryConfigs
                    if (uploadConfigs.isEmpty()) {
                        XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
                        return@thread
                    }
                    if (uploadTelemetryIfAccountReady(appContext, uploadConfigs)) {
                        XposedCompat.logD("[AboutInfo] telemetry account id ready at attempt=${attempt + 1}")
                        return@thread
                    }
                    if (attempt < TELEMETRY_ACCOUNT_ID_RETRY_COUNT) {
                        Thread.sleep(TELEMETRY_ACCOUNT_ID_RETRY_DELAY_MS)
                    }
                }
                XposedCompat.logD("[AboutInfo] telemetry skipped: account id unavailable after retry")
            } catch (t: Throwable) {
                XposedCompat.logD("[AboutInfo] telemetry account retry stopped: ${t.message}")
            } finally {
                telemetryAccountRetryRunning.set(false)
                if (telemetryRetryThread === Thread.currentThread()) {
                    telemetryRetryThread = null
                }
            }
        }
        telemetryRetryThread = worker
        worker.start()
    }

    private fun telemetrySuccessDateKey(name: String): String {
        return "$KEY_TELEMETRY_LAST_SUCCESS_DATE:$name"
    }

    private fun telemetrySuccessSignature(date: String, moduleVersion: String, uuid: String): String {
        return "$date|$moduleVersion|$uuid"
    }

    private fun telemetryUuidForAccount(context: Context): String? {
        val accountId = TiebaAccountIdentity.currentAccountId(context) ?: return null
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$TELEMETRY_ACCOUNT_ID_SALT:$accountId".toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        return uuidStringFromHash(hash)
    }

    private fun uuidStringFromHash(hash: ByteArray): String {
        val hex = buildString(32) {
            for (i in 0 until 16) {
                append(((hash[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
            }
        }
        return hex.substring(0, 8) +
            "-" + hex.substring(8, 12) +
            "-" + hex.substring(12, 16) +
            "-" + hex.substring(16, 20) +
            "-" + hex.substring(20, 32)
    }

    private fun uploadTelemetry(config: TelemetryConfig, variables: TelemetryVariables): Boolean {
        val startMs = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            val method = replaceTelemetryVariables(config.method, variables).trim()
                .ifEmpty { "POST" }
                .uppercase(Locale.ROOT)
            val endpoint = replaceTelemetryVariables(config.endpoint, variables)
            val bodyBytes = buildTelemetryBody(config.body, variables)
                ?.takeUnless { method == "GET" || method == "HEAD" }

            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = config.connectTimeoutMs
                readTimeout = config.readTimeoutMs
                useCaches = false
                for ((key, value) in config.headers) {
                    setRequestProperty(
                        replaceTelemetryVariables(key, variables),
                        replaceTelemetryVariables(value, variables),
                    )
                }
                if (bodyBytes != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(bodyBytes.size)
                }
            }
            if (bodyBytes != null) {
                connection.outputStream.use { output ->
                    output.write(bodyBytes)
                }
            }

            val code = connection.responseCode
            val elapsed = System.currentTimeMillis() - startMs
            if (code in 200..299) {
                XposedCompat.logD("[AboutInfo] telemetry[${config.name}] upload success: method=$method code=$code elapsedMs=$elapsed")
                true
            } else {
                XposedCompat.logD("[AboutInfo] telemetry[${config.name}] upload failed: method=$method code=$code elapsedMs=$elapsed")
                false
            }
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - startMs
            XposedCompat.logD("[AboutInfo] telemetry[${config.name}] upload exception: elapsedMs=$elapsed msg=${t.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildTelemetryBody(body: Any?, variables: TelemetryVariables): ByteArray? {
        if (body == null || body == JSONObject.NULL) return null
        val replaced = replaceTelemetryValue(body, variables)
        val text = when (replaced) {
            null, JSONObject.NULL -> return null
            is JSONObject -> replaced.toString()
            is JSONArray -> replaced.toString()
            else -> replaced.toString()
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun replaceTelemetryValue(value: Any?, variables: TelemetryVariables): Any? {
        return when (value) {
            null, JSONObject.NULL -> JSONObject.NULL
            is String -> variables.objectValue(value) ?: replaceTelemetryVariables(value, variables)
            is JSONObject -> {
                val out = JSONObject()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    out.put(key, replaceTelemetryValue(value.opt(key), variables))
                }
                out
            }
            is JSONArray -> {
                val out = JSONArray()
                for (i in 0 until value.length()) {
                    out.put(replaceTelemetryValue(value.opt(i), variables))
                }
                out
            }
            else -> value
        }
    }

    private fun replaceTelemetryVariables(value: String, variables: TelemetryVariables): String {
        return TELEMETRY_VARIABLE_PATTERN.replace(value) { match ->
            variables.stringValue(match.groupValues[1]) ?: match.value
        }
    }

    private fun collectRuntimeEnvironment(context: Context): RuntimeEnvironment {
        runtimeEnvironmentCache?.let { return it }
        return synchronized(this) {
            runtimeEnvironmentCache ?: buildRuntimeEnvironment(context)
                .also { runtimeEnvironmentCache = it }
        }
    }

    private fun buildRuntimeEnvironment(context: Context): RuntimeEnvironment {
        val module = XposedCompat.module
        val frameworkProperties = runCatching { module?.frameworkProperties }.getOrNull()
        val frameworkName = runCatching { module?.frameworkName }.getOrNull().orUnknown()
        val patchMode = collectPatchMode(context)

        return RuntimeEnvironment(
            tiebaVersionName = collectTiebaVersionName(context),
            hostSourceKind = classifyHostSource(context.applicationInfo?.sourceDir),
            androidSdk = Build.VERSION.SDK_INT,
            xposedApiVersion = runCatching { module?.apiVersion }.getOrNull()?.toString().orUnknown(),
            xposedFrameworkName = frameworkName,
            xposedFrameworkVersion = runCatching { module?.frameworkVersion }.getOrNull().orUnknown(),
            xposedFrameworkVersionCode = runCatching { module?.frameworkVersionCode }.getOrNull()?.toString().orUnknown(),
            xposedFrameworkProperties = frameworkProperties?.toString().orUnknown(),
            xposedFrameworkCapabilities = formatFrameworkCapabilities(frameworkProperties),
            environmentRatingLevel = classifyEnvironmentRatingLevel(frameworkProperties, patchMode),
            runtimeKind = classifyRuntimeKind(frameworkName),
            patchMode = patchMode,
        )
    }

    private fun collectTiebaVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orUnknown()
    }

    private fun classifyRuntimeKind(frameworkName: String): String {
        val lowerName = frameworkName.lowercase(Locale.ROOT)
        return when {
            lowerName.contains("lsposed") -> "lsposed"
            lowerName.contains("edxposed") -> "edxposed"
            lowerName.contains("xposed") -> "xposed"
            lowerName.contains("vector") -> "vector"
            frameworkName == UNKNOWN_VALUE -> "unknown"
            else -> "xposed-compatible"
        }
    }

    private fun formatFrameworkCapabilities(properties: Long?): List<String> {
        if (properties == null) return emptyList()
        val out = ArrayList<String>(3)
        if ((properties and XposedInterface.PROP_CAP_SYSTEM) != 0L) {
            out.add("PROP_CAP_SYSTEM")
        }
        if ((properties and XposedInterface.PROP_CAP_REMOTE) != 0L) {
            out.add("PROP_CAP_REMOTE")
        }
        if ((properties and XposedInterface.PROP_RT_API_PROTECTION) != 0L) {
            out.add("PROP_RT_API_PROTECTION")
        }
        return out
    }

    private fun classifyEnvironmentRatingLevel(properties: Long?, patchMode: String): Int {
        return when {
            properties != null && (properties and XposedInterface.PROP_CAP_SYSTEM) != 0L -> 0
            patchMode == "integrated" -> 2
            else -> 1
        }
    }

    private fun classifyHostSource(sourceDir: String?): String {
        val value = sourceDir?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.isBlank() -> "unknown"
            value.contains("/cache/npatch/origin/") -> "npatch-origin"
            value.contains("/cache/lspatch/origin/") -> "lspatch-origin"
            value.endsWith(".apk") -> "apk"
            else -> "other"
        }
    }

    private fun collectPatchMode(context: Context): String {
        val detection = detectPatchMode(context)
        return when {
            detection.embeddedModulesFound -> "integrated"
            detection.configFound && detection.sourceChecked -> "local"
            detection.configFound || detection.sourceLooksPatched -> "unknown"
            else -> "none"
        }
    }

    private fun detectPatchMode(context: Context): PatchModeDetection {
        var configFound = findPatchConfigFromManifest(context) != null
        var embeddedModulesFound = false
        var sourceChecked = false
        var sourceLooksPatched = false

        for (sourcePath in collectPackageSourcePaths(context)) {
            val normalizedPath = sourcePath.replace('\\', '/').lowercase(Locale.ROOT)
            if (normalizedPath.contains("/npatch/") || normalizedPath.contains("-npatched.apk")) {
                sourceLooksPatched = true
            }
            val result = inspectPatchZipSource(sourcePath) ?: continue
            sourceChecked = true
            configFound = configFound || result.configFound
            embeddedModulesFound = embeddedModulesFound || result.embeddedModulesFound
            sourceLooksPatched = sourceLooksPatched || result.sourceLooksPatched
        }

        val assetResult = inspectPatchAssets(context)
        if (assetResult != null) {
            sourceChecked = true
            configFound = configFound || assetResult.configFound
            embeddedModulesFound = embeddedModulesFound || assetResult.embeddedModulesFound
            sourceLooksPatched = sourceLooksPatched || assetResult.sourceLooksPatched
        }

        return PatchModeDetection(
            configFound = configFound,
            embeddedModulesFound = embeddedModulesFound,
            sourceChecked = sourceChecked,
            sourceLooksPatched = sourceLooksPatched,
        )
    }

    private fun collectPackageSourcePaths(context: Context): List<String> {
        val paths = LinkedHashSet<String>()
        fun addPath(path: String?) {
            if (!path.isNullOrBlank()) paths.add(path)
        }

        addPath(context.applicationInfo?.sourceDir)
        addPath(context.applicationInfo?.publicSourceDir)
        addPath(context.packageResourcePath)
        getApplicationInfoCompat(context)?.let { appInfo ->
            addPath(appInfo.sourceDir)
            addPath(appInfo.publicSourceDir)
        }
        return paths.toList()
    }

    private fun getApplicationInfoCompat(context: Context): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            }
        }.getOrNull()
    }

    private fun findPatchConfigFromManifest(context: Context): JSONObject? {
        val appInfos = listOfNotNull(
            context.applicationInfo,
            getApplicationInfoCompat(context),
        )
        for (appInfo in appInfos) {
            val encoded = appInfo.metaData?.getString(PATCH_MANIFEST_META_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: continue
            decodePatchConfig(encoded)?.let { return it }
        }
        return null
    }

    private fun decodePatchConfig(encoded: String): JSONObject? {
        return runCatching {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            JSONObject(String(decoded, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun inspectPatchZipSource(sourcePath: String): PatchModeDetection? {
        return runCatching {
            ZipFile(sourcePath).use { zip ->
                var embeddedModulesFound = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (
                        name.startsWith(PATCH_EMBEDDED_MODULE_PREFIX) &&
                        name.endsWith(".apk", ignoreCase = true)
                    ) {
                        embeddedModulesFound = true
                        break
                    }
                }
                val configFound = zip.getEntry(PATCH_CONFIG_ASSET_PATH) != null
                PatchModeDetection(
                    configFound = configFound,
                    embeddedModulesFound = embeddedModulesFound,
                    sourceChecked = true,
                    sourceLooksPatched = configFound || embeddedModulesFound,
                )
            }
        }.getOrNull()
    }

    private fun inspectPatchAssets(context: Context): PatchModeDetection? {
        return runCatching {
            val configFound = runCatching {
                context.assets.open("npatch/config.json").close()
                true
            }.getOrDefault(false)
            val embeddedModulesFound = runCatching {
                context.assets.list("npatch/modules")
                    ?.any { it.endsWith(".apk", ignoreCase = true) }
            }.getOrNull() == true
            if (!configFound && !embeddedModulesFound) {
                null
            } else {
                PatchModeDetection(
                    configFound = configFound,
                    embeddedModulesFound = embeddedModulesFound,
                    sourceChecked = true,
                    sourceLooksPatched = configFound || embeddedModulesFound,
                )
            }
        }.getOrNull()
    }

    private fun String?.orUnknown(): String {
        return this?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
    }

    private fun todayDateString(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun isHttpOrHttpsUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Throwable) {
            false
        }
    }

    private const val UNKNOWN_VALUE = "unknown"
}
