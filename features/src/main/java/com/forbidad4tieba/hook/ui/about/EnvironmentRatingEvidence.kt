package com.forbidad4tieba.hook.ui.about

import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Client-only evidence. The existing uploaded fields and 0/1/2 policy remain unchanged. */
internal class EnvironmentRatingEvidence(
    private val modulePackageName: String?,
    private val moduleApk: File?,
    private val hostCacheDir: File?,
) {
    private var integratedConfigFound = false
    private var incompatibleConfigFound = false
    private var embeddedModuleMismatch = false

    fun observeConfig(config: JSONObject?) {
        when (config?.opt("useManager")) {
            false -> integratedConfigFound = true
            else -> incompatibleConfigFound = true
        }
    }

    fun observeConfigStream(input: InputStream) {
        observeConfig(runCatching {
            JSONObject(AboutPayloadReader.read(input, MAX_CONFIG_BYTES).value)
        }.getOrNull())
    }

    /** Only call for an archive positively identified as patched, never for the origin APK. */
    fun observePatchedArchive(zip: ZipFile) {
        val expectedCrc = moduleCacheCrc ?: return
        val entry = zip.getEntry("assets/npatch/modules/$modulePackageName.apk")
        if (entry == null || entry.isDirectory || entry.crc != expectedCrc || entry.size != moduleApk?.length()) {
            embeddedModuleMismatch = true
        }
    }

    fun observePatchedAssetModules(names: Array<String>) {
        if ("$modulePackageName.apk" !in names) embeddedModuleMismatch = true
    }

    fun level(properties: Long?): Int {
        if (properties == null) return 1
        if ((properties and XposedInterface.PROP_CAP_SYSTEM) != 0L) return 0
        if (!integratedConfigFound || incompatibleConfigFound || embeddedModuleMismatch) return 1
        return if (verifyModuleCache()) 2 else 1
    }

    // NPatch IntegrApplicationService extracts this module to
    // <host cache>/code_cache/mods/<module package>/<entry CRC>.apk and publishes that location
    // through libxposed. A /data/app module or another module's cache is not this provenance.
    private val moduleCacheCrc: Long? by lazy {
        runCatching {
            val packageName = modulePackageName?.takeIf { it.isNotBlank() } ?: return@runCatching null
            if ('/' in packageName || '\\' in packageName || packageName == "." || packageName == "..") {
                return@runCatching null
            }
            val cache = hostCacheDir?.canonicalFile ?: return@runCatching null
            val apk = moduleApk?.canonicalFile ?: return@runCatching null
            val expectedParent = File(cache, "code_cache/mods/$packageName")
            if (apk.parentFile != expectedParent || !apk.isFile || apk.length() !in 1..MAX_MODULE_BYTES) {
                return@runCatching null
            }
            val crc = apk.name.removeSuffix(".apk").toLongOrNull() ?: return@runCatching null
            crc.takeIf { it in 0..0xffffffffL && apk.name == "$it.apk" }
        }.getOrNull()
    }

    private fun verifyModuleCache(): Boolean {
        val expectedCrc = moduleCacheCrc ?: return false
        return runCatching {
            // This also works when signature bypass exposes only the origin APK. The explicit
            // integrated config and the current module's verified extraction cache are required.
            val crc = CRC32()
            var total = 0L
            checkNotNull(moduleApk).inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_MODULE_BYTES) return false
                    crc.update(buffer, 0, count)
                }
            }
            total > 0 && crc.value == expectedCrc
        }.getOrDefault(false)
    }

    companion object {
        const val MAX_CONFIG_BYTES = 64 * 1024
        private const val MAX_MODULE_BYTES = 64L * 1024 * 1024
    }
}
