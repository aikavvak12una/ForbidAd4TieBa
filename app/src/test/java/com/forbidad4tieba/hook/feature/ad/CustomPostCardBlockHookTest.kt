package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CustomPostCardBlockHookTest {
    @Test
    fun helpFilterRemovesEntireQuestionGoodCardWithoutInputGuide() {
        withHelpFilter(master = true, help = true) {
            val first = card("normal", "0")
            val help = card("question_good", "1")
            val last = card("normal", "0")
            val input = listOf(first, help, last)

            val output = filter(input)

            assertEquals(listOf(first, last), output)
            assertNotSame(input, output)
            assertEquals(listOf(first, help, last), input)
        }
    }

    @Test
    fun disabledHelpSwitchKeepsOriginalList() {
        withHelpFilter(master = true, help = false) {
            val input = listOf(card("question_good", "1"))

            assertSame(input, filter(input))
        }
    }

    @Test
    fun disabledMasterSwitchKeepsOriginalListEvenWithHelpFlagSet() {
        withHelpFilter(master = false, help = true) {
            val input = listOf(card("question_good", "1"))

            assertSame(input, filter(input))
        }
    }

    @Test
    fun helpFilterKeepsUnmarkedAndUnknownCards() {
        withHelpFilter(master = true, help = true) {
            val input = listOf(card("normal", "0"), card("question_good", "0"), card("question_unknown", "1"))

            assertSame(input, filter(input))
        }
    }

    private fun filter(input: List<Card>): List<*> {
        return CustomPostCardBlockHook.filterList(
            input,
            CustomPostCardBlockHook.RuntimeFilter(
                dataListFieldName = "dataList",
                templateKeyMethodName = "templateKey",
                templatePayloadMethodName = "payload",
                headParamsFieldName = "params",
                recommendNestedDataMethodName = null,
                recommendNestedDataListFieldName = null,
            ),
            "help-filter-regression",
        )
    }

    private fun card(cardType: String, specialThread: String): Card {
        val params = mapOf(
            "card_type" to cardType,
            "thread_type" to "0",
            "is_special_thread" to specialThread,
            "page_from" to "recommend",
            "recom_type" to "1",
        )
        return Card(CardData(listOf(Component("feed_head", params), Component("feed_content", emptyMap()))))
    }

    private fun withHelpFilter(master: Boolean, help: Boolean, block: () -> Unit) {
        val field = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val original = field.get(ConfigManager)
        try {
            field.set(ConfigManager, SettingsSnapshot(isCustomPostFilterEnabled = master, isPostHelpFilterEnabled = help))
            block()
        } finally {
            field.set(ConfigManager, original)
        }
    }

    class Card(private val data: CardData) {
        fun templateKey(): String = "feed_card"
        fun payload(): CardData = data
    }

    class CardData(@JvmField val dataList: List<Component>)

    class Component(private val key: String, @JvmField val params: Map<String, String>) {
        fun templateKey(): String = key
    }
}
