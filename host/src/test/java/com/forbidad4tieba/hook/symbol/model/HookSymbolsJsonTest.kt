package com.forbidad4tieba.hook.symbol.model

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigSymbols
import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigTarget
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class HookSymbolsJsonTest {
    @Test
    fun removedMessageContainerFieldsDoNotReenterTheConsumerContract() {
        val old = JSONObject(buildHookSymbols { this[MessageTabContract.msgTabLocateToTabMethod] = "locateTab" }.toJson())
            .put("msgTabContainerSelectMethod", "unusedSelect")
            .put("msgTabContainerExtDataField", "unusedExtData")
        val parsed = requireNotNull(HookSymbols.fromJson(old.toString()))
        assertEquals("locateTab", parsed[MessageTabContract.msgTabLocateToTabMethod])
        val emitted = JSONObject(parsed.toJson())
        assertFalse(emitted.has("msgTabContainerSelectMethod"))
        assertFalse(emitted.has("msgTabContainerExtDataField"))
    }

    @Test
    fun jsonRoundTripPreservesResourceHookPointAndScanMetaFields() {
        val symbols = buildHookSymbols {
            this[AutoRefreshContract.autoRefreshTriggerMethod] = "com.tieba.Feed#triggerRefresh"
            this[FeedContract.feedCardBindMethod] = "com.tieba.FeedCard#bind"
            this[InputMemeBarContract.inputMemeBarControllerClass] = "com.tieba.SpriteMemePanController"
            this[InputMemeBarContract.inputMemeBarEnableMethod] = "enabled"
            this[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId] = 12345
            this[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds] = listOf(11, 22)
            scanSupportState = ScanSupportState.SUPPORTED
            scanErrors = listOf("sample scan error")
        }

        val parsed = HookSymbols.fromJson(symbols.toJson())

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("com.tieba.Feed#triggerRefresh", parsed[AutoRefreshContract.autoRefreshTriggerMethod])
        assertEquals("com.tieba.FeedCard#bind", parsed[FeedContract.feedCardBindMethod])
        assertEquals("com.tieba.SpriteMemePanController", parsed[InputMemeBarContract.inputMemeBarControllerClass])
        assertEquals("enabled", parsed[InputMemeBarContract.inputMemeBarEnableMethod])
        assertEquals(12345, parsed[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId])
        assertEquals(listOf(11, 22), parsed[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds])
        assertEquals(ScanSupportState.SUPPORTED, parsed.scanSupportState)
        assertEquals(listOf("sample scan error"), parsed.scanErrors)
        assertFalse(symbols.toJson().contains("\"featureStatusMap\""))
    }

    @Test
    fun everyBuilderFieldSurvivesJsonRoundTrip() {
        val builder = HookSymbolsBuilder()
        val fields = SymbolContracts.all.flatMap { contract ->
            contract.javaClass.declaredMethods.filter { it.returnType == SymbolField::class.java }
                .map { contract to it }
        }
        fields.forEachIndexed { index, (contract, getter) ->
            @Suppress("UNCHECKED_CAST")
            val field = getter.invoke(contract) as SymbolField<Any?>
            val valueType = (getter.genericReturnType as ParameterizedType).actualTypeArguments.single()
            val rawType = (if (valueType is ParameterizedType) valueType.rawType else valueType) as Class<*>
            builder[field] = sampleValue(rawType, valueType, field.cacheKey, index)
        }

        val expected = builder.build()
        val actual = HookSymbols.fromJson(expected.toJson())

        assertTrue(fields.size > 300)
        assertEquals(SymbolContracts.all.sumOf { it.fields.size }, fields.size)
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun roundTripPreservesHomeBottomEasterEggParserSymbols() {
        val symbols = buildHookSymbols {
            this[HomeBottomEasterEggContract.homeBottomEasterEggParserClass] = "com.tieba.EasterEggParser"
            this[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod] = "parseJson"
        }

        val parsed = HookSymbols.fromJson(symbols.toJson())

        assertEquals("com.tieba.EasterEggParser", parsed?.get(HomeBottomEasterEggContract.homeBottomEasterEggParserClass))
        assertEquals("parseJson", parsed?.get(HomeBottomEasterEggContract.homeBottomEasterEggParserMethod))
    }

    private fun sampleValue(
        type: Class<*>,
        genericType: java.lang.reflect.Type,
        fieldName: String,
        index: Int,
    ): Any {
        return when (type) {
            LowEndConfigSymbols::class.java -> LowEndConfigSymbols(
                LowEndConfigTarget.entries.associateWith { "method_${it.name}" },
            )
            PbAutoReplyFlowSymbols::class.java -> PbAutoReplyFlowSymbols(
                sendMethodSpec = "host.Editor#submit",
                writeModelField = "writeModel",
                writeDataField = "request",
                uploadMethod = "upload",
                callbackMethodSpec = "host.Callback#callback",
                transientMethod = "showNewPost",
                transientParamsClass = "host.TransientPostParams",
            )
            DefaultPopupSymbols::class.java -> DefaultPopupSymbols(
                firstLikeResponseClass = "host.LikeResponse",
                firstLikeToastMethod = "parseToast",
                notificationGuideClass = "host.PushGuide",
                notificationGuideMethod = "tryShow",
            )
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
