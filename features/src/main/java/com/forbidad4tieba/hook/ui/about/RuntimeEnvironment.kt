package com.forbidad4tieba.hook.ui.about

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.zip.ZipFile

internal data class RuntimeEnvironment(
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

internal object RuntimeEnvironmentProvider {
    private const val PATCH_CONFIG_ASSET_PATH = "assets/npatch/config.json"
    private const val PATCH_MANIFEST_META_KEY = "npatch"
    private const val PATCH_EMBEDDED_MODULE_PREFIX = "assets/npatch/modules/"
    private const val UNKNOWN_VALUE = "unknown"
    @Volatile private var runtimeEnvironmentCache: RuntimeEnvironment? = null

    private data class PatchModeDetection(
        val configFound: Boolean,
        val embeddedModulesFound: Boolean,
        val sourceChecked: Boolean,
        val sourceLooksPatched: Boolean,
    )

    fun get(context: Context): RuntimeEnvironment {
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
}
