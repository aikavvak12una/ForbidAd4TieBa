package com.forbidad4tieba.hook.ui.about

import android.content.Context
import com.forbidad4tieba.hook.contracts.BuildIdentity
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.TiebaAccountIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal data class TelemetryConfig(
    val name: String,
    val endpoint: String,
    val method: String,
    val headers: Map<String, String>,
    val body: Any?,
    val successOncePerDay: Boolean,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
)

internal object AboutTelemetry {
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val KEY_TELEMETRY_LAST_SUCCESS_DATE = "about_telemetry_last_success_date"
    private const val TELEMETRY_ACCOUNT_ID_SALT = "forbidad4tieba.telemetry.account_id.v1"
    private const val TELEMETRY_ACCOUNT_ID_FIRST_DELAY_MS = 5000L
    private const val TELEMETRY_ACCOUNT_ID_RETRY_COUNT = 2
    private const val TELEMETRY_ACCOUNT_ID_RETRY_DELAY_MS = 5000L
    private val TELEMETRY_VARIABLE_PATTERN = Regex("""\$\{([A-Za-z0-9_]+)\}""")
    private val telemetryAccountRetryRunning = AtomicBoolean(false)
    private class PendingTelemetry(val configs: List<TelemetryConfig>, val environment: RuntimeEnvironment)
    @Volatile private var pendingTelemetry: PendingTelemetry? = null

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

    fun parseConfig(root: JSONObject): List<TelemetryConfig> {
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

    fun reportIfNeeded(context: Context, configs: List<TelemetryConfig>, environment: RuntimeEnvironment) {
        pendingTelemetry = PendingTelemetry(configs, environment)
        if (configs.isEmpty()) {
            XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
            return
        }
        scheduleTelemetryAccountRetry(context)
    }

    private fun uploadTelemetryIfAccountReady(
        context: Context,
        configs: List<TelemetryConfig>,
        environment: RuntimeEnvironment,
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
            moduleVersion = "${BuildIdentity.current.versionName}(${BuildIdentity.current.versionCode})",
            environment = environment,
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
        thread(name = "tbhook-telemetry-account-retry", isDaemon = true) {
            try {
                Thread.sleep(TELEMETRY_ACCOUNT_ID_FIRST_DELAY_MS)
                for (attempt in 0..TELEMETRY_ACCOUNT_ID_RETRY_COUNT) {
                    val pending = pendingTelemetry ?: return@thread
                    val uploadConfigs = pending.configs
                    if (uploadConfigs.isEmpty()) {
                        XposedCompat.logD("[AboutInfo] telemetry skipped: no config")
                        return@thread
                    }
                    if (uploadTelemetryIfAccountReady(appContext, uploadConfigs, pending.environment)) {
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
            }
        }
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
}
