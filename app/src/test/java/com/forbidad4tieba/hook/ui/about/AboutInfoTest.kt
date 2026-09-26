package com.forbidad4tieba.hook.ui.about

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class AboutInfoTest {
    private val environment = RuntimeEnvironment(
        "22.12.1.0", "apk", 35, "102", "LSPosed", "test", "102", "1",
        listOf("PROP_CAP_SYSTEM"), 0, "lsposed", "none",
    )

    @Test fun earlyControlsAndStartupWorkerShareParsedCacheIncludingMisses() {
        val cache = AboutStartupCache()
        var reads = 0
        repeat(2) { assertNull(cache.getOrRead { reads++; null }) }
        assertEquals(1, reads)
        val published = document()
        cache.publish(published)
        assertSame(published, cache.getOrRead { error("Published payload must be reused") })

        val existing = AboutStartupCache()
        repeat(2) { assertSame(published, existing.getOrRead { reads++; published }) }
        assertEquals(2, reads)
    }

    @Test fun boundedReaderCountsUtf8BytesAndRejectsOversizedStreams() {
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

    @Test fun payloadValidationKeepsItemOrderAndRejectsInvalidSchemaOrItems() {
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
        for (raw in listOf("broken", """{"schema":2,"items":[]}""",
            """{"schema":1,"items":[{"title":"","description":"x"}]}""")) {
            assertNull(AboutPayloadParser.parse(raw, warnings::add))
        }
        assertEquals(3, warnings.size)
    }

    @Test fun defaultAndExplicitEnvironmentControlsRetainTheirMeaning() {
        val defaults = RemoteControlPolicy.parse(JSONObject())
        assertFalse(defaults.forLevel(0).showWarningDialog)
        assertTrue(defaults.forLevel(1).showWarningDialog)
        assertFalse(defaults.forLevel(2).lockHiddenFeatures)
        val controls = RemoteControlPolicy.parse(JSONObject("""{"controls":{"environmentLevels":{
            "0":{"showWarningDialog":"yes","lockHiddenFeatures":1},
            "1":{"showWarningDialog":"off"}
        }}}"""))
        assertTrue(controls.forLevel(0).lockHiddenFeatures)
        assertTrue(controls.forLevel(0).showWarningDialog)
        assertFalse(controls.forLevel(1).showWarningDialog)
        assertTrue(controls.forLevel(2).showWarningDialog)
    }

    @Test fun unavailableAccountDoesNotMatchAccountComparison() {
        val condition = """{"field":"account_id","op":"neq","value":"123"}"""
        assertFalse(evaluate(condition, null).lockHiddenFeatures)
        assertFalse(evaluate(condition, "123").lockHiddenFeatures)
        assertTrue(evaluate(condition, "456").lockHiddenFeatures)
        val controls = RemoteControls(emptyMap(), listOf(rule(condition)))
        assertTrue(RemoteControlPolicy.dependsOnAccountId(controls))
    }

    @Test fun unknownConditionsStayIgnoredInAllAnyAndNot() {
        val unknown = """{"field":"future_field","op":"eq","value":1}"""
        val current = """{"field":"module_version_code","op":"gte","value":45}"""
        assertFalse(evaluate(unknown).lockHiddenFeatures)
        assertFalse(evaluate("""{"not":$unknown}""").lockHiddenFeatures)
        assertTrue(evaluate("""{"all":[$unknown,$current]}""").lockHiddenFeatures)
        assertTrue(evaluate("""{"any":[$unknown,$current]}""").lockHiddenFeatures)
        assertFalse(evaluate("""{"all":[$unknown]}""").lockHiddenFeatures)
    }

    @Test fun numericListAndRegexRulesPreserveMatchingAndDisabledRulesDoNothing() {
        assertTrue(evaluate("""{"field":"account_id","op":"in","value":[7,"8"]}""", "007").lockHiddenFeatures)
        assertTrue(evaluate("""{"field":"runtime_kind","op":"matches","value":"^lsposed$"}""").lockHiddenFeatures)
        assertFalse(evaluate("""{"field":"environment_level","op":"gt","value":0}""").lockHiddenFeatures)
        val disabled = rule("""{"field":"module_version_code","op":"eq","value":45}""").copy(enabled = false)
        val result = RemoteControlPolicy.evaluate(listOf(disabled), context(null))
        assertFalse(result.lockHiddenFeatures)
        assertEquals(0, result.matchedRuleCount)
    }

    @Test fun customDialogRevisionAndTelemetryConfigurationSurviveParsing() {
        val root = JSONObject("""{"controls":{"rules":[{"id":"notice","when":{
            "field":"module_version_code","op":"eq","value":45},"actions":[
            {"type":"customDialog","id":"n","revision":2,"title":"Title","message":"Message"}
        ]}]},"telemetry":[{"name":"a","endpoint":"https://example.invalid/report",
          "method":"GET","headers":{"X-Sample":"value"},"schedule":false,
          "request":{"timeoutMs":1234},"body":{"v":"sample"}},
          {"enabled":false,"endpoint":"ignored"}]}""")
        val controls = RemoteControlPolicy.parse(root)
        val result = RemoteControlPolicy.evaluate(controls.rules, context(null))
        assertEquals("n:2", result.customDialogs.single().ackKey)
        val config = AboutTelemetry.parseConfig(root).single()
        assertEquals("GET", config.method)
        assertFalse(config.successOncePerDay)
        assertEquals(1234, config.connectTimeoutMs)
        assertEquals(1234, config.readTimeoutMs)
        assertEquals("value", config.headers["X-Sample"])
        assertEquals("sample", (config.body as JSONObject).getString("v"))
    }

    private fun context(account: String?) = RemoteControlPolicy.RemoteConditionContext(environment, account, 45)
    private fun rule(condition: String) = RemoteRule("test", true, JSONObject(condition), listOf(RemoteAction.LockHiddenFeatures))
    private fun evaluate(condition: String, account: String? = null) =
        RemoteControlPolicy.evaluate(listOf(rule(condition)), context(account))
    private fun document() = AboutDocument(
        AboutPayload(listOf(AboutItem("a", "b", null)), emptyList(), RemoteControls.DEFAULT),
        "cache", "cache", 10, 1, 1,
    )
}
