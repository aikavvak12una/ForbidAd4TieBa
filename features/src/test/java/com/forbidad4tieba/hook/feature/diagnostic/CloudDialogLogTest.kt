package com.forbidad4tieba.hook.feature.diagnostic

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudDialogLogTest {
    private val lines = mutableListOf<String>()
    private val logger = CloudDialogLog { lines += it }
    private val attempt = "4a215f82-89ce-4f2d-822b-92a062d2c761"

    @Test fun configKeepsIdentityAndStrategyWhileDiscardingCredentialValues() {
        val input = JSONArray().put(config("commercial_push_sign_pop",
            "https://user:secret-user-info@tieba.baidu.com/mo/q/signTaskDialog?token=secret-token&user_identify=secret-user&%74bs=secret-tbs#secret-fragment")
            .put("extra", "secret-extra"))
        logger.record(SYNC + input, 0)
        val row = json().first()
        assertEquals("commercial_push_sign_pop", row.getString("dialogName"))
        assertEquals("NA_COMMERCIAL_PUSH_SIGN_POP_STRATEGY", row.getJSONArray("strategies").getString(0))
        assertEquals("sync_finish", row.getJSONArray("triggers").getString(0))
        val url = row.getJSONObject("url")
        assertEquals("tieba.baidu.com", url.getString("host"))
        assertEquals("/mo/q/signTaskDialog", url.getString("path"))
        assertEquals(listOf("token", "user_identify", "tbs"), strings(url.getJSONArray("queryKeys")))
        assertFalse(lines.joinToString().contains("secret-"))
    }

    @Test fun nativeAndOpaqueUrlsDoNotExposeTheirPayloads() {
        val input = JSONArray().put(config("native", "dialoginternal://commonAlertDialog"))
            .put(config("opaque", "data:text/html,secret-page-payload"))
            .put(config("invalid", "https://invalid host/?token=secret-invalid"))
        logger.record(SYNC + input, 0)
        val rows = json()
        assertEquals("commonAlertDialog", rows[0].getJSONObject("url").getString("host"))
        assertTrue(rows[1].getJSONObject("url").getBoolean("opaque"))
        assertFalse(rows[2].getJSONObject("url").getBoolean("valid"))
        assertFalse(lines.joinToString().contains("secret-"))
    }

    @Test fun cacheAndSyncSnapshotsKeepAllFiftySevenEntriesInOneSecond() {
        val input = JSONArray()
        repeat(57) { input.put(config("dialog_$it")) }
        logger.record(CACHE + input, 0)
        logger.record(SYNC + input, 10)
        assertEquals(116, lines.size)
        val summaries = json().filter { it.getString("phase") == "config_summary" }
        assertEquals(listOf(1L, 2L), summaries.map { it.getLong("batch") })
        assertEquals(listOf("cache", "sync"), summaries.map { it.getString("source") })
        summaries.forEach { assertEquals(57, it.getInt("recorded")); assertEquals(0, it.getInt("omitted")) }
    }

    @Test fun stagesKeepOnlyHostSuppliedAttemptIds() {
        logger.record("云弹窗时机触发:sync_finish  $attempt", 0)
        logger.record("云弹窗 sync_finish 时机到达，准备展示的弹窗：[\"test\"] ", 0)
        logger.record("弹窗准备渲染test", 0)
        logger.record("BaseWebDialogProcessor test 准备展示", 0)
        logger.record("云弹窗 test 弹出显示，展示链接：https://tieba.baidu.com/path?token=secret-value", 0)
        logger.record("弹窗渲染成功test   $attempt", 0)
        logger.record("unMarkShowingDialogName:test", 0)
        val rows = json()
        assertEquals(listOf("trigger", "candidates", "render_requested", "web_prepare",
            "render_dispatched", "render_success", "released"), rows.map { it.getString("phase") })
        assertEquals(attempt, rows.first().getString("attemptId"))
        assertEquals(attempt, rows[5].getString("attemptId"))
        rows.filterIndexed { i, _ -> i != 0 && i != 5 }.forEach { assertFalse(it.has("attemptId")) }
        assertFalse(lines.joinToString().contains("secret-value"))
    }

    @Test fun unknownMessagesStayOutsideTheStructuredBudget() {
        repeat(1_000) {
            logger.record("strategyMap: unrelated event $it", 0)
            logger.record("BaseWebDialogProcessor test closeWebDialog, reason: backClick", 0)
        }
        logger.record("弹窗准备渲染test", 0)
        logger.flushSuppressed()
        assertEquals(1, lines.size)
        assertEquals("render_requested", json().single().getString("phase"))
    }

    @Test fun finalSuppressedBurstsAreReportedOnceWithoutAnotherEvent() {
        repeat(19) { logger.record("弹窗准备渲染test", 0) }
        repeat(3) { logger.record(SYNC + "[]", 0) }
        logger.flushSuppressed()
        val losses = json().filter { it.getString("phase") == "loss" }
        assertEquals(mapOf("event_rate" to 3L, "config_rate" to 1L),
            losses.associate { it.getString("reason") to it.getLong("count") })
        val size = lines.size
        logger.flushSuppressed()
        assertEquals(size, lines.size)
    }

    @Test fun malformedAndOversizeInputsOnlyProduceCounts() {
        logger.record(SYNC + "[{secret-invalid-json", 0)
        logger.record(SYNC + "x".repeat(128 * 1024) + "secret-tail", 0)
        logger.record("弹窗渲染成功test   secret-not-an-attempt-id", 0)
        logger.flushSuppressed()
        assertEquals(mapOf("malformed_input" to 2L, "oversize_input" to 1L),
            json().associate { it.getString("reason") to it.getLong("count") })
        assertFalse(lines.joinToString().contains("secret-"))
    }

    @Test fun absentConfigurationIsAnExplicitEmptySnapshot() {
        logger.record(SYNC + "null", 0)
        logger.flushSuppressed()
        val summary = json().single()
        assertEquals("config_summary", summary.getString("phase"))
        assertEquals(0, summary.getInt("total"))
    }

    @Test fun itemLimitsAreExplicitAndNeverTurnRecordsIntoTruncatedJson() {
        val input = JSONArray()
        repeat(130) { input.put(config("dialog_$it")) }
        logger.record(SYNC + input, 0)
        val summary = json().last()
        assertEquals(130, summary.getInt("total"))
        assertEquals(128, summary.getInt("recorded"))
        assertEquals(2, summary.getInt("omitted"))
        assertTrue(lines.all { it.length < 3_500 })
    }

    @Test fun fieldAndSnapshotCharacterLimitsRemainBoundedWithLargeMetadata() {
        val input = JSONArray()
        repeat(30) {
            input.put(config("n".repeat(128), "https://${"h".repeat(120)}.com/${"p".repeat(255)}?" +
                (0 until 12).joinToString("&") { i -> "k${"k".repeat(30)}$i=secret" })
                .put("dialogTime", JSONArray(List(20) { "t".repeat(64) }))
                .put("dialogStrategy", JSONArray(List(20) { JSONObject().put("type", "s".repeat(64)) })))
        }
        logger.record(SYNC + input, 0)
        val rows = json()
        assertTrue(rows.isNotEmpty())
        assertTrue(lines.all { it.length < 3_500 })
        assertEquals(12, rows.first().getJSONArray("triggers").length())
        assertEquals(20, rows.first().getInt("triggerCount"))
        assertTrue(rows.last().getInt("omitted") > 0)
        assertTrue(lines.sumOf { it.length } < 66 * 1024)
        assertFalse(lines.joinToString().contains("secret"))
    }

    private fun config(name: String, url: String = "https://tieba.baidu.com/dialog") = JSONObject()
        .put("dialogName", name).put("dialogUrl", url)
        .put("dialogTime", JSONArray(listOf("sync_finish")))
        .put("dialogStrategy", JSONArray().put(JSONObject()
            .put("type", "NA_COMMERCIAL_PUSH_SIGN_POP_STRATEGY").put("data", JSONObject().put("token", "secret-strategy"))))

    private fun json(): List<JSONObject> = lines.map(::JSONObject)
    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map(array::getString)

    private companion object {
        const val SYNC = "sync更新本地云弹窗配置，配置为："
        const val CACHE = "加载本地云弹窗配置: "
    }
}
