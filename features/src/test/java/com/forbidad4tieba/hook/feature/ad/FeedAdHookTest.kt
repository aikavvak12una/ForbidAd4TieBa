package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class FeedAdHookTest {
    @Test
    fun strategyAndFeedSwitchesControlTheirOwnCardsInBothFilteringPaths() {
        val post = Card("feed_card")
        val banner = Card("recommend_banner")
        val feedAd = Card("ad_card_video")
        val otherBanner = Card("banner")
        val input = listOf(post, banner, feedAd, otherBanner)
        val cases = listOf(
            Triple(SettingsSnapshot.bootstrap(), input, false),
            Triple(SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true), listOf(post, feedAd, otherBanner), true),
            Triple(SettingsSnapshot.bootstrap().copy(isFeedAdBlockEnabled = true), listOf(post, banner), true),
            Triple(
                SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true, isFeedAdBlockEnabled = true),
                listOf(post),
                true,
            ),
        )

        for ((settings, expected, changed) in cases) {
            withSettings(settings) {
                for (runtimeFilter in listOf(null, customFilter)) {
                    val output = filter(input, runtimeFilter)
                    assertEquals(expected, output)
                    if (changed) assertNotSame(input, output) else assertSame(input, output)
                    assertEquals(listOf(post, banner, feedAd, otherBanner), input)
                }
            }
        }
    }

    @Test
    fun strategyRemovesOnlyExactBannerKeyAndPreservesUnknownItems() {
        withSettings(SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true)) {
            val input = listOf(Card("recommend_banner_extra"), Card("recommend"), Card(null), null, Any())
            for (runtimeFilter in listOf(null, customFilter)) {
                assertSame(input, filter(input, runtimeFilter))
            }
        }
    }

    @Test
    fun disabledFiltersDoNotReadTemplateKeys() {
        withSettings(SettingsSnapshot.bootstrap()) {
            val banner = Card("recommend_banner")
            val input = listOf(banner)
            for (runtimeFilter in listOf(null, customFilter)) {
                assertSame(input, filter(input, runtimeFilter))
            }
            assertEquals(0, banner.keyReads)
        }
    }

    @Test
    fun strategyAndCustomPostFilteringShareOnePassAndKeepFeedAds() {
        withSettings(
            SettingsSnapshot.bootstrap().copy(
                isStrategyAdBlockEnabled = true,
                isCustomPostFilterEnabled = true,
                isPostHelpFilterEnabled = true,
            ),
        ) {
            val post = Card("feed_card")
            val banner = Card("recommend_banner")
            val help = Card(
                "feed_card",
                CardData(
                    listOf(
                        Component(
                            "feed_head",
                            mapOf("card_type" to "question_good", "thread_type" to "0", "is_special_thread" to "1"),
                        ),
                    ),
                ),
            )
            val feedAd = Card("ad_card_video")
            val input = listOf(post, banner, help, feedAd)

            assertEquals(listOf(post, feedAd), filter(input, customFilter))
            assertEquals(listOf(post, banner, help, feedAd), input)
            input.forEach { assertEquals(1, it.keyReads) }
        }
    }

    private fun filter(input: List<*>, runtimeFilter: CustomPostCardBlockHook.RuntimeFilter?): List<*> {
        return FeedAdHook.filterList(input, "templateKey", runtimeFilter, "feed-ad-regression")
    }

    private fun withSettings(settings: SettingsSnapshot, block: () -> Unit) {
        val field = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val original = field.get(ConfigManager)
        try {
            field.set(ConfigManager, settings)
            block()
        } finally {
            field.set(ConfigManager, original)
        }
    }

    private val customFilter = CustomPostCardBlockHook.RuntimeFilter(
        dataListFieldName = "dataList",
        templateKeyMethodName = "templateKey",
        templatePayloadMethodName = "payload",
        headParamsFieldName = "params",
        recommendNestedDataMethodName = null,
        recommendNestedDataListFieldName = null,
    )

    class Card(private val key: String?, private val data: CardData = CardData(emptyList())) {
        var keyReads = 0
        fun templateKey(): String? {
            keyReads += 1
            return key
        }
        fun payload(): CardData = data
    }

    class CardData(@JvmField val dataList: List<Component>)

    class Component(private val key: String, @JvmField val params: Map<String, String>) {
        fun templateKey(): String = key
    }
}
