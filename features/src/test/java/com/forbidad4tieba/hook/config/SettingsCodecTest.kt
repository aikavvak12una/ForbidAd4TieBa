package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.config.TabPreferences.FixedHomeTopTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SettingsCodecTest {
    @Test
    fun modelScoreCodecsRetainHistoricalSyntaxAndRejectInvalidValues() {
        val thresholds = ModelScoreSettings.parseModelScoreThresholds(
            "alpha=0.25\nbeta:1.5; alpha = 0.1234565; empty=; bad=-1; nan=NaN; infinite=Infinity",
        )
        assertEquals(listOf("alpha", "beta"), thresholds.map { it.key })
        assertEquals(0.1234565, thresholds[0].threshold, 0.0)
        assertEquals(1.5, thresholds[1].threshold, 0.0)
        assertEquals(
            "alpha=0.123457\nbeta=1.5",
            ModelScoreSettings.serializeModelScoreThresholds(thresholds),
        )
        assertEquals("10000000", ModelScoreSettings.formatModelScoreThresholdValue(10000000.0))
        assertTrue(ModelScoreSettings.parseModelScoreThresholds(" ").isEmpty())

        val percentiles = ModelScoreSettings.parseModelScoreAutoPercentiles(
            "alpha=P5; beta:p10\nalpha=20; rejected=7; =15; bad=text",
        )
        assertEquals(linkedMapOf("alpha" to 20, "beta" to 10), percentiles)
        assertEquals("alpha=20\nbeta=10", ModelScoreSettings.serializeModelScoreAutoPercentiles(percentiles))
        assertTrue(ModelScoreSettings.serializeModelScoreAutoPercentiles(mapOf("" to 5, "bad" to 7)).isEmpty())
    }

    @Test
    fun verifiedPostUserCodecUsesTheInternalUidAndDropsConflicts() {
        val user = BlockedPostUser("9007199254740993", "123456", "昵称😀")
        val conflictingProfile = BlockedPostUser("789", user.profileId, "another")
        val unrelated = BlockedPostUser("456", "456789", "safe")
        val raw = PostUserFilterSettings.encode(listOf(user, conflictingProfile, unrelated))
        assertEquals(listOf(unrelated), PostUserFilterSettings.decode(raw))
        assertEquals(setOf("456"), PostFilterPreferences.parseUserIdList(raw))
        assertEquals(listOf(user), PostUserFilterSettings.decode(PostUserFilterSettings.encode(listOf(user, user))))

        for (invalid in listOf(
            "{bad,123,456",
            "[]",
            "{\"version\":2,\"users\":[{\"userId\":\"123\"}]}",
            "{\"version\":1,\"users\":[{\"userId\":\"123\",\"profileId\":\"456\"}]}",
        )) {
            assertTrue(invalid, PostUserFilterSettings.decode(invalid).isEmpty())
        }
        assertEquals("123", PostFilterPreferences.normalizeUserId(" 00123 "))
        assertEquals("9007199254740993", PostFilterPreferences.normalizeUserId("9007199254740993"))
        for (invalid in listOf(null, "", "0", "-123", "+123", "123.0", "1e3", "123 456", "#123#")) {
            assertNull("raw=$invalid", PostFilterPreferences.normalizeUserId(invalid))
        }
    }

    @Test
    fun fixedTabCodecCanonicalizesAliasesAndKeepsUrlIdentityStable() {
        val saved = listOf(
            FixedHomeTopTab(" TYPE:1 ", "旧标题"),
            FixedHomeTopTab("code:recommend", "另一个标题"),
            FixedHomeTopTab("code:myfollowed", "ignored"),
            FixedHomeTopTab("code:custom", "推荐"),
        )
        val decoded = TabPreferences.parseFixedHomeTopTabs(
            TabPreferences.serializeFixedHomeTopTabs(saved),
        )
        assertEquals(
            listOf(TabPreferences.HOME_TOP_TAB_KEY_RECOMMEND, TabPreferences.HOME_TOP_TAB_KEY_FOLLOWED, "code:custom"),
            decoded.map { it.key },
        )
        assertEquals("旧标题", decoded.first().label)

        for (url in listOf(
            "https://example.org/path",
            "https://example.org/中文?q=1",
            " HTTPS://EXAMPLE.ORG/PATH ",
        )) {
            val normalized = url.trim().lowercase(java.util.Locale.ROOT)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
                .take(16)
            assertEquals("url:$digest", TabPreferences.homeTopTabKeyFor(null, null, url))
        }
        assertTrue(TabPreferences.parseFixedHomeTopTabs("[{\"key\":\"garbage\"}]").isEmpty())
    }

    @Test
    fun typedBoundsNormalizeBothReadAndSaveWithoutTouchingUnrelatedValues() {
        val values = mutableMapOf<String, Any>(
            BottomTabLiquidGlassPreferences.HOME_WIDTH_ADJUSTMENT to Int.MIN_VALUE,
            BottomTabLiquidGlassPreferences.REPLY_WIDTH_ADJUSTMENT to Int.MAX_VALUE,
            BottomTabLiquidGlassPreferences.HEIGHT_ADJUSTMENT to Int.MAX_VALUE,
            BottomTabLiquidGlassPreferences.POSITION_ADJUSTMENT to Int.MIN_VALUE,
            BottomTabLiquidGlassPreferences.BLUR_PERCENT to Int.MAX_VALUE,
            "unrelated" to 7,
        )
        val store = TestPreferences(
            values,
            readMethods = setOf("getInt", "getBoolean"),
            editorMethods = setOf("putInt", "putBoolean", "apply"),
        )
        assertEquals(
            BottomTabLiquidGlassConfig(-100, 100, -100, blurPercent = 100, replyWidthAdjustment = 100),
            BottomTabLiquidGlassPreferences.read(store.prefs),
        )
        BottomTabLiquidGlassPreferences.save(
            store.prefs,
            BottomTabLiquidGlassConfig(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, blurPercent = -1,
                replyWidthAdjustment = Int.MIN_VALUE),
        )
        assertEquals(
            BottomTabLiquidGlassConfig(100, -100, 100, blurPercent = 0, replyWidthAdjustment = -100),
            BottomTabLiquidGlassPreferences.read(store.prefs),
        )
        assertEquals(7, values["unrelated"])
    }
}
