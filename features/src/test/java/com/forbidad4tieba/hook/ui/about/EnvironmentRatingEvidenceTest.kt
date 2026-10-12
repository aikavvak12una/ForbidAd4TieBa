package com.forbidad4tieba.hook.ui.about

import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EnvironmentRatingEvidenceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val modulePackage = "example.module"
    private val moduleBytes = "current loaded module fixture".toByteArray()
    private val system = XposedInterface.PROP_CAP_SYSTEM
    private val remote = XposedInterface.PROP_CAP_REMOTE
    private val cache by lazy { temporary.newFolder("host-cache") }

    @Test fun systemCapabilityWinsEvenWhenTheHostIsPatched() {
        val evidence = evidence().apply {
            observeConfig(JSONObject("""{"useManager":false}"""))
            observeConfig(JSONObject("""{"useManager":true}"""))
            observePatchedAssetModules(arrayOf("another.module.apk"))
        }
        for (properties in listOf(system, system or remote, system or remote or 4L, system or (1L shl 40))) {
            assertEquals(0, evidence.level(properties))
        }
    }

    @Test fun absentCapabilitiesDoNotBecomeSystemOrIntegrated() {
        val evidence = integratedEvidence()
        assertEquals(1, evidence.level(null))
        assertEquals(2, evidence.level(0L))
        assertEquals(2, evidence.level(remote))
        assertEquals(1, evidence().level(remote))
    }

    @Test fun remoteCapabilityDoesNotDistinguishLocalFromIntegrated() {
        val local = evidence().apply { observeConfig(JSONObject("""{"useManager":true}""")) }
        assertEquals(1, local.level(remote))
        assertEquals(2, integratedEvidence().level(remote))
    }

    @Test fun explicitIntegratedConfigAndVerifiedOwnCacheSurviveOriginApkVisibility() {
        // No patch assets are observable: signature bypass exposes the original host APK.
        assertEquals(2, integratedEvidence().level(remote))
    }

    @Test fun installedModuleCannotBeClassifiedByHostEmbeddedAssets() {
        val installed = temporary.newFile("installed-module.apk").apply { writeBytes(moduleBytes) }
        val evidence = evidence(installed).apply {
            observeConfig(JSONObject("""{"useManager":false}"""))
            observePatchedAssetModules(arrayOf("$modulePackage.apk"))
        }
        assertEquals(1, evidence.level(remote))
    }

    @Test fun conflictingConfigsCannotUpgradeToIntegratedRegardlessOfOrder() {
        for (configs in listOf(listOf(true, false), listOf(false, true))) {
            val evidence = evidence()
            configs.forEach { evidence.observeConfig(JSONObject().put("useManager", it)) }
            assertEquals(1, evidence.level(remote))
        }
    }

    @Test fun missingMalformedOrCoercedConfigFieldsCannotUpgradeToIntegrated() {
        for (raw in listOf("{}", """{"useManager":"false"}""", """{"useManager":0}""", """{"useManager":null}""")) {
            val evidence = integratedEvidence().apply { observeConfig(JSONObject(raw)) }
            assertEquals(raw, 1, evidence.level(remote))
        }
        val invalid = integratedEvidence().apply { observeConfig(null) }
        assertEquals(1, invalid.level(remote))
    }

    @Test fun configStreamsAreBoundedAndInvalidJsonIsNotAnAbsentConfig() {
        for (raw in listOf("broken", " ".repeat(EnvironmentRatingEvidence.MAX_CONFIG_BYTES + 1))) {
            val evidence = integratedEvidence()
            ByteArrayInputStream(raw.toByteArray()).use(evidence::observeConfigStream)
            assertEquals(1, evidence.level(remote))
        }
        val evidence = evidence()
        ByteArrayInputStream("""{"useManager":false}""".toByteArray()).use(evidence::observeConfigStream)
        assertEquals(2, evidence.level(remote))
    }

    @Test fun patchedArchiveMustContainTheCurrentModuleAndMatchingExtractionMetadata() {
        val variants = listOf(
            mapOf("$modulePackage.apk" to moduleBytes) to 2,
            mapOf("other.module.apk" to moduleBytes) to 1,
            mapOf("$modulePackage.apk" to "different module version".toByteArray()) to 1,
            emptyMap<String, ByteArray>() to 1,
        )
        for ((entries, expected) in variants) {
            val evidence = integratedEvidence()
            ZipFile(patchedArchive(entries)).use(evidence::observePatchedArchive)
            assertEquals(expected, evidence.level(remote))
        }
    }

    @Test fun observablePatchedAssetsForAnotherModuleCannotUpgradeThisModule() {
        for (names in listOf(emptyArray(), arrayOf("other.module.apk"))) {
            val evidence = integratedEvidence().apply { observePatchedAssetModules(names) }
            assertEquals(1, evidence.level(remote))
        }
    }

    @Test fun staleOrForgedCacheFileNamesCannotUpgradeToIntegrated() {
        val original = cachedModule()
        val changed = evidence(original).apply { observeConfig(JSONObject("""{"useManager":false}""")) }
        original.writeText("changed after extraction")
        assertEquals(1, changed.level(remote))
        for (name in listOf("base.apk", "-1.apk", "4294967296.apk", "00.apk", "1.zip")) {
            val apk = File(original.parentFile, name).apply { writeBytes(moduleBytes) }
            assertEquals(1, integratedEvidence(apk).level(remote))
        }
    }

    @Test fun moduleCacheMustBelongToThisHostAndThisModule() {
        val apk = cachedModule()
        val config = JSONObject("""{"useManager":false}""")
        val variants = listOf(
            EnvironmentRatingEvidence("other.module", apk, cache),
            EnvironmentRatingEvidence(modulePackage, apk, temporary.newFolder("other-host-cache")),
            EnvironmentRatingEvidence(null, apk, cache),
            EnvironmentRatingEvidence(modulePackage, null, cache),
            EnvironmentRatingEvidence(modulePackage, apk, null),
        )
        for (evidence in variants) {
            evidence.observeConfig(config)
            assertEquals(1, evidence.level(remote))
        }
    }

    @Test fun uploadedEnvironmentSchemaAndExistingFieldTypesStayUnchanged() {
        val original = RuntimeEnvironment(
            "22.12.1.0", "apk", 35, "102", "NPatch", "test", "102", "2",
            listOf("PROP_CAP_REMOTE"), 1, "xposed-compatible", "integrated",
        )
        val before = original.toJson()
        val after = original.copy(environmentRatingLevel = integratedEvidence().level(remote)).toJson()
        assertEquals(setOf(
            "tiebaVersionName", "hostSourceKind", "androidSdk", "xposedApiVersion",
            "xposedFrameworkName", "xposedFrameworkVersion", "xposedFrameworkVersionCode",
            "xposedFrameworkProperties", "xposedFrameworkCapabilities", "environmentRatingLevel",
            "runtimeKind", "patchMode",
        ), after.keys().asSequence().toSet())
        assertEquals(2, after.get("environmentRatingLevel"))
        for (key in before.keys().asSequence().toSet() - "environmentRatingLevel") {
            assertEquals(key, before.get(key).javaClass, after.get(key).javaClass)
            assertEquals(key, before.get(key).toString(), after.get(key).toString())
        }
    }

    private fun evidence(apk: File = cachedModule()) = EnvironmentRatingEvidence(modulePackage, apk, cache)

    private fun integratedEvidence(apk: File = cachedModule()) = evidence(apk).apply {
        observeConfig(JSONObject("""{"useManager":false}"""))
    }

    private fun cachedModule(): File {
        val crc = CRC32().apply { update(moduleBytes) }.value
        return File(cache, "code_cache/mods/$modulePackage/$crc.apk").apply {
            checkNotNull(parentFile).mkdirs()
            writeBytes(moduleBytes)
        }
    }

    private fun patchedArchive(modules: Map<String, ByteArray>): File = temporary.newFile().apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/npatch/config.json"))
            zip.write("""{"useManager":false}""".toByteArray())
            zip.closeEntry()
            for ((name, bytes) in modules) {
                zip.putNextEntry(ZipEntry("assets/npatch/modules/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
