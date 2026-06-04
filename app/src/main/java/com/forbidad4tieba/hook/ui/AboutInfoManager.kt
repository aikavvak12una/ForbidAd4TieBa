package com.forbidad4tieba.hook.ui

import android.content.Context
import android.content.SharedPreferences
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
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.concurrent.thread

object AboutInfoManager {
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val CACHE_DIR_NAME = "tbhook"
    private const val CACHE_FILE_NAME = "about_info_cache.json"
    private const val KEY_TELEMETRY_UUID = "about_telemetry_uuid"
    private const val KEY_TELEMETRY_LAST_SUCCESS_DATE = "about_telemetry_last_success_date"
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
    ) {
        fun forLevel(level: Int): EnvironmentLevelControls {
            return environmentLevels[level] ?: DEFAULT_ENVIRONMENT_CONTROLS[level] ?: EnvironmentLevelControls()
        }
    }

    private data class EnvironmentLevelControls(
        val showWarningDialog: Boolean = false,
        val lockHiddenFeatures: Boolean = false,
    )

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

    fun loadCachedItemsForSettings(): List<AboutItem> {
        return cachedRemoteItems
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

        thread(name = "tbhook-about-startup-fetch", isDaemon = true) {
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
                        applyRemoteControls(appContext, parsedRemote.controls)
                    }
                }
            }.onFailure { t ->
                XposedCompat.log("[AboutInfo] startup fetch crashed: ${t.message}")
                XposedCompat.log(t)
            }
        }
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
        XposedCompat.log(
            "[AboutInfo] payload accepted: source=cache url=cache " +
                "bytes=$bytes elapsedMs=$elapsedMs cacheAgeMs=$cacheAgeMs itemCount=${items.size}"
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
        val levels = root.optJSONObject("controls")
            ?.optJSONObject("environmentLevels")
            ?: return defaultControls

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
        return RemoteControls(parsedLevels)
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
        val level = collectRuntimeEnvironment(context).environmentRatingLevel
        val levelControls = controls.forLevel(level)
        ConfigManager.applyRemoteEnvironmentControls(
            context = context,
            showWarningDialog = levelControls.showWarningDialog,
            lockHiddenFeatures = levelControls.lockHiddenFeatures,
        )
        XposedCompat.logD(
            "[AboutInfo] remote controls applied: " +
                "environmentRatingLevel=$level " +
                "showWarningDialog=${levelControls.showWarningDialog} " +
                "lockHiddenFeatures=${levelControls.lockHiddenFeatures}"
        )
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
        if (configs.isEmpty()) {
            XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
            return
        }

        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        val userPrefs = ConfigManager.getPrefs(context)
        val today = todayDateString()
        val uuid = getOrCreateTelemetryUuid(userPrefs) ?: return
        val variables = TelemetryVariables(
            uuid = uuid,
            moduleVersion = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
            environment = collectRuntimeEnvironment(context),
        )

        for (config in configs) {
            val successDateKey = telemetrySuccessDateKey(config.name)
            val successSignature = telemetrySuccessSignature(today, variables.moduleVersion)
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
    }

    private fun telemetrySuccessDateKey(name: String): String {
        return "$KEY_TELEMETRY_LAST_SUCCESS_DATE:$name"
    }

    private fun telemetrySuccessSignature(date: String, moduleVersion: String): String {
        return "$date|$moduleVersion"
    }

    private fun getOrCreateTelemetryUuid(prefs: SharedPreferences): String? {
        val existing = prefs.getString(KEY_TELEMETRY_UUID, null)?.trim()
        if (!existing.isNullOrEmpty() && isValidUuid(existing)) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        val saved = prefs.edit()
            .putString(KEY_TELEMETRY_UUID, generated)
            .commit()
        if (!saved) {
            XposedCompat.logD("[AboutInfo] telemetry skipped: uuid persist failed")
            return null
        }
        return generated
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
        val out = ArrayList<String>(4)
        if ((properties and XposedInterface.PROP_CAP_SYSTEM) != 0L) {
            out.add("PROP_CAP_SYSTEM")
        }
        if ((properties and XposedInterface.PROP_CAP_REMOTE) != 0L) {
            out.add("PROP_CAP_REMOTE")
        }
        if ((properties and XposedInterface.PROP_RT_API_PROTECTION) != 0L) {
            out.add("PROP_RT_API_PROTECTION")
        }
        if ((properties and XposedInterface.PROP_RT_HOT_RELOAD) != 0L) {
            out.add("PROP_RT_HOT_RELOAD")
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

    private fun isValidUuid(value: String): Boolean {
        return try {
            UUID.fromString(value).toString().equals(value, ignoreCase = true)
        } catch (_: Throwable) {
            false
        }
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
