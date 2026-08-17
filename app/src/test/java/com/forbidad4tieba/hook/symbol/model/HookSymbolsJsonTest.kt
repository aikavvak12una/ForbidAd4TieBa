package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSymbolsJsonTest {
    @Test
    fun jsonRoundTripPreservesResourceHookPointAndScanMetaFields() {
        val symbols = buildHookSymbols {
            autoRefreshTriggerMethod = "com.tieba.Feed#triggerRefresh"
            feedCardBindMethod = "com.tieba.FeedCard#bind"
            inputMemeBarControllerClass = "com.tieba.SpriteMemePanController"
            inputMemeBarEnableMethod = "enabled"
            homeNativeGlassSubPbNextPageMoreViewId = 12345
            homeNativeGlassDynamicBackgroundColorIds = listOf(11, 22)
            scanSupportState = ScanSupportState.SUPPORTED
            scanErrors = listOf("sample scan error")
        }

        val parsed = HookSymbols.fromJson(symbols.toJson())

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("com.tieba.Feed#triggerRefresh", parsed.autoRefreshTriggerMethod)
        assertEquals("com.tieba.FeedCard#bind", parsed.feedCardBindMethod)
        assertEquals("com.tieba.SpriteMemePanController", parsed.inputMemeBarControllerClass)
        assertEquals("enabled", parsed.inputMemeBarEnableMethod)
        assertEquals(12345, parsed.homeNativeGlassSubPbNextPageMoreViewId)
        assertEquals(listOf(11, 22), parsed.homeNativeGlassDynamicBackgroundColorIds)
        assertEquals(ScanSupportState.SUPPORTED, parsed.scanSupportState)
        assertEquals(listOf("sample scan error"), parsed.scanErrors)
        assertFalse(symbols.toJson().contains("\"featureStatusMap\""))
    }

    @Test
    fun everyBuilderFieldSurvivesJsonRoundTrip() {
        val builder = HookSymbolsBuilder()
        val fields = HookSymbolsBuilder::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .sortedBy { it.name }

        fields.forEachIndexed { index, field ->
            field.isAccessible = true
            field.set(builder, sampleValue(field.type, field.genericType, field.name, index))
        }

        val expected = builder.build()
        val actual = HookSymbols.fromJson(expected.toJson())

        assertTrue(fields.size > 300)
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun roundTripPreservesHomeBottomEasterEggParserSymbols() {
        val symbols = buildHookSymbols {
            homeBottomEasterEggParserClass = "com.tieba.EasterEggParser"
            homeBottomEasterEggParserMethod = "parseJson"
        }

        val parsed = HookSymbols.fromJson(symbols.toJson())

        assertEquals("com.tieba.EasterEggParser", parsed?.homeBottomEasterEggParserClass)
        assertEquals("parseJson", parsed?.homeBottomEasterEggParserMethod)
    }

    private fun sampleValue(
        type: Class<*>,
        genericType: java.lang.reflect.Type,
        fieldName: String,
        index: Int,
    ): Any {
        return when (type) {
            String::class.java -> "value_$fieldName"
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 10_000 + index
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 100_000L + index
            List::class.java -> {
                val elementType = (genericType as ParameterizedType).actualTypeArguments.single()
                if (elementType == Int::class.javaObjectType) {
                    listOf(20_000 + index, 30_000 + index)
                } else {
                    listOf("${fieldName}_first", "${fieldName}_second")
                }
            }
            else -> error("Unsupported HookSymbolsBuilder field: $fieldName ($type)")
        }
    }
}
