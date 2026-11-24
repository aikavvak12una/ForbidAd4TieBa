package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedAdHookTest {
    @Test
    fun missingCustomTargetsDoNotDisableTheIndependentAdFilter() {
        withSettings(SettingsSnapshot.bootstrap().copy(
            isFeedAdBlockEnabled = true,
            isCustomPostFilterEnabled = true,
            isPostHelpFilterEnabled = true,
        )) {
            val post = Card("feed_card")
            val feedAd = Card("ad_card_video")
            val input = listOf(post, feedAd)

            assertEquals(listOf(post), filter(input, null))
            assertEquals(listOf(post, feedAd), input)
        }
    }

    @Test
    fun strategyAndCustomRulesShareOnePassWithoutCrossEnabling() {
        withSettings(SettingsSnapshot.bootstrap().copy(
            isStrategyAdBlockEnabled = true,
            isCustomPostFilterEnabled = true,
            isPostHelpFilterEnabled = true,
        )) {
            val post = Card("feed_card")
            val banner = Card("recommend_banner")
            val help = Card(
                "feed_card",
                CardData(listOf(Component(
                    "feed_head",
                    mapOf(
                        "card_type" to "question_good",
                        "thread_type" to "0",
                        "is_special_thread" to "1",
                    ),
                ))),
            )
            val feedAd = Card("ad_card_video")
            val input = listOf(post, banner, help, feedAd)

            assertEquals(listOf(post, feedAd), filter(input, customFilter))
            assertEquals(listOf(post, banner, help, feedAd), input)
            input.forEach { assertEquals(1, it.keyReads) }

            val disabled = Card("recommend_banner")
            withSettings(SettingsSnapshot.bootstrap()) {
                val disabledInput = listOf(disabled)
                assertSame(disabledInput, filter(disabledInput, customFilter))
                assertEquals(0, disabled.keyReads)
            }
        }
    }

    private fun filter(input: List<*>, custom: CustomPostCardFilterSymbols?): List<*> =
        FeedAdHook.filterList(
            input,
            FeedTemplateFixture::class.java.getMethod("templateKey"),
            custom,
            "feed-ad",
        )

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

    private val customFilter = feedFilterFixture(CardData::class.java, Component::class.java)

    class Card(
        private val key: String?,
        private val data: CardData = CardData(emptyList()),
    ) : FeedTemplateFixture {
        var keyReads = 0
        override fun templateKey(): String? {
            keyReads++
            return key
        }

        override fun payload() = data
    }

    class CardData(@JvmField val dataList: List<Component>)
    class Component(
        private val key: String,
        @JvmField val params: Map<String, String>,
    ) : FeedTemplateFixture {
        override fun templateKey() = key
    }
}
