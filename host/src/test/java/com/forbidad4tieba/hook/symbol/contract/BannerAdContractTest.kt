package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.scan.BannerConversionFixture
import com.forbidad4tieba.hook.symbol.scan.NonPersonalizedConverterFixture
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tbclient.LayoutFactory

class BannerAdContractTest {
    private val descriptor = BannerConversionFixture::class.java.name + "#renamedConverter"

    @Test fun descriptorRoundtripRestoresTheExactMethodAndEveryProtocolField() {
        val symbols = fixture(descriptor)
        val restored = requireNotNull(HookSymbols.fromJson(symbols.toJson()))
        assertEquals(symbols, restored)
        assertTrue(JSONObject(restored.toJson()).has("bannerAdConversionMethodSpec"))
        val targets = requireNotNull(BannerAdContract.resolveBannerAdSymbols(javaClass.classLoader!!, restored))
        assertEquals(BannerConversionFixture::class.java.getDeclaredMethod(
            "renamedConverter", LayoutFactory::class.java, Map::class.java, java.lang.Boolean.TYPE,
        ), targets.conversionMethod)
        assertEquals(listOf("layout", "banner_card", "components", "component", "feed_banner", "log_param", "key", "value"),
            listOf(targets.layoutField, targets.bannerCardField, targets.componentsField, targets.componentField,
                targets.feedBannerField, targets.logParamField, targets.logKeyField, targets.logValueField).map { it.name })
        assertTrue(BannerAdContract.isCacheValid(restored, javaClass.classLoader!!))
        assertEquals(HookPointState.FOUND, BannerAdContract.points(restored).single().state)
        assertEquals(listOf(HookFeatureKey.BLOCK_AD_FEED), SymbolContracts.featuresForPoint("BannerAdHook.Conversion"))
    }

    @Test fun missingOrInvalidDescriptorCannotRestoreAnotherConverter() {
        val loader = javaClass.classLoader!!
        assertNull(BannerAdContract.resolveBannerAdSymbols(loader, fixture(null)))
        assertTrue(BannerAdContract.isCacheValid(fixture(null), loader))
        assertEquals(HookPointState.MISSING, BannerAdContract.points(fixture(null)).single().state)
        val owner = BannerConversionFixture::class.java.name
        for (invalid in listOf("missing", "wrongParameters", "boxedBoolean", "wrongResult", "missingPayload", "staticConverter")) {
            val symbols = fixture("$owner#$invalid")
            assertNull(invalid, BannerAdContract.resolveBannerAdSymbols(loader, symbols))
            assertFalse(invalid, BannerAdContract.isCacheValid(symbols, loader))
        }
        for (invalid in listOf("broken", "$owner#", "$owner#renamedConverter#extra",
            NonPersonalizedConverterFixture::class.java.name + "#convert")) {
            assertNull(invalid, BannerAdContract.resolveBannerAdSymbols(loader, fixture(invalid)))
            assertFalse(invalid, BannerAdContract.isCacheValid(fixture(invalid), loader))
        }
    }

    @Test fun missingProtocolClassesDisableOnlyBannerRestoration() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == "tbclient.ComponentFactory") throw LinkageError("fixture missing component protocol")
                return super.loadClass(name, resolve)
            }
        }
        assertNull(BannerAdContract.resolveBannerAdSymbols(loader, fixture(descriptor)))
        assertFalse(BannerAdContract.isCacheValid(fixture(descriptor), loader))
        assertTrue(BannerAdContract.isCacheValid(fixture(null), loader))
        assertTrue(BannerAdContract.capabilities(fixture(descriptor)).isEmpty())
    }

    private fun fixture(value: String?) = buildHookSymbols {
        this[BannerAdContract.bannerAdConversionMethodSpec] = value
    }
}
