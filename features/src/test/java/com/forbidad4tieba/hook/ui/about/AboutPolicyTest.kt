package com.forbidad4tieba.hook.ui.about

import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32

class AboutPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    private val environment = RuntimeEnvironment(
        "22.12.1.0", "apk", 35, "102", "LSPosed", "test", "102", "1",
        listOf("PROP_CAP_SYSTEM"), 0, "lsposed", "none",
    )

    @Test
    fun boundedReaderCountsUtf8AndLeavesAnOversizedTailUnread() {
        val bytes = "中文abc".toByteArray(Charsets.UTF_8)
        val read = AboutPayloadReader.read(ByteArrayInputStream(bytes), bytes.size)
        assertEquals("中文abc", read.value)
        assertEquals(9, read.bytes)

        val stream = ByteArrayInputStream(ByteArray(100))
        try {
            AboutPayloadReader.read(stream, 10)
            fail("Oversized payload should be rejected")
        } catch (_: IOException) {
            assertEquals(89, stream.available())
        }
    }

    @Test
    fun payloadParserPreservesOrderAndRejectsSchemaOrItemConflicts() {
        val warnings = mutableListOf<String>()
        val parsed = AboutPayloadParser.parse("""{"schema":1,"items":[
            {"title":" A ","description":" first "},
            {"title":"B","description":"second","hasLink":false,"url":"ignored"}
        ]}""", warnings::add)!!
        assertEquals(listOf("A", "B"), parsed.items.map { it.title })
        assertEquals("first", parsed.items[0].description)
        assertNull(parsed.items[1].url)
        assertTrue(parsed.telemetry.isEmpty())
        assertTrue(warnings.isEmpty())

        for (raw in listOf(
            "broken",
            """{"schema":2,"items":[]}""",
            """{"schema":1,"items":[{"title":"","description":"x"}]}""",
        )) {
            assertNull(AboutPayloadParser.parse(raw, warnings::add))
        }
        assertEquals(3, warnings.size)
    }

    @Test
    fun enabledUnknownAndDisabledRemoteRulesStayIndependent() {
        val unknown = """{"field":"future_field","op":"eq","value":1}"""
        val current = """{"field":"module_version_code","op":"gte","value":45}"""
        assertTrue(evaluate(current).lockHiddenFeatures)
        assertFalse(evaluate(unknown).lockHiddenFeatures)
        assertFalse(evaluate("""{"not":$unknown}""").lockHiddenFeatures)
        assertTrue(evaluate("""{"all":[$unknown,$current]}""").lockHiddenFeatures)
        assertTrue(evaluate("""{"any":[$unknown,$current]}""").lockHiddenFeatures)

        val disabled = rule(current).copy(enabled = false)
        val result = RemoteControlPolicy.evaluate(listOf(disabled), context(null))
        assertFalse(result.lockHiddenFeatures)
        assertEquals(0, result.matchedRuleCount)
    }

    @Test
    fun conflictingOrMalformedManagerEvidenceCannotUpgradeTheRating() {
        val cache = temporary.newFolder("host-cache")
        val apk = cachedModule(cache)
        val valid = EnvironmentRatingEvidence(MODULE_PACKAGE, apk, cache).apply {
            observeConfig(JSONObject("""{"useManager":false}"""))
        }
        assertEquals(2, valid.level(XposedInterface.PROP_CAP_REMOTE))

        for (configs in listOf(listOf(true, false), listOf(false, true))) {
            val evidence = EnvironmentRatingEvidence(MODULE_PACKAGE, apk, cache)
            configs.forEach { evidence.observeConfig(JSONObject().put("useManager", it)) }
            assertEquals(1, evidence.level(XposedInterface.PROP_CAP_REMOTE))
        }
        val malformed = EnvironmentRatingEvidence(MODULE_PACKAGE, apk, cache).apply {
            observeConfig(JSONObject("""{"useManager":"false"}"""))
        }
        assertEquals(1, malformed.level(XposedInterface.PROP_CAP_REMOTE))
    }

    private fun context(account: String?) =
        RemoteControlPolicy.RemoteConditionContext(environment, account, 45)

    private fun rule(condition: String) =
        RemoteRule("test", true, JSONObject(condition), listOf(RemoteAction.LockHiddenFeatures))

    private fun evaluate(condition: String) =
        RemoteControlPolicy.evaluate(listOf(rule(condition)), context(null))

    private fun cachedModule(cache: File): File {
        val bytes = "current loaded module fixture".toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        return File(cache, "code_cache/mods/$MODULE_PACKAGE/$crc.apk").apply {
            checkNotNull(parentFile).mkdirs()
            writeBytes(bytes)
        }
    }

    private companion object {
        const val MODULE_PACKAGE = "example.module"
    }
}
