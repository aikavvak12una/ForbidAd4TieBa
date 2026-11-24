package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.BannerAdContract
import com.forbidad4tieba.hook.symbol.contract.ForumPageAdContract
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tbclient.LayoutFactory
import tbclient.FrsPage.FrsRainInfo
import tbclient.Personalized.DataRes

@Suppress("UNUSED_PARAMETER")
class ProtocolConversionRulesTest {
    private val loader = javaClass.classLoader!!
    private val bannerEvidence = BannerAdSymbolScanner.Evidence(true, true, true, true, true, true, 1, true)
    private val rainEvidence = ForumRainSymbolScanner.Evidence(
        setOf("resource", "popup_url", "popup_info_str", "business_id", "report_info"), true, true, 1, true, true)

    @Test fun bannerNeedsEverySemanticAnchorAndUniqueConversionEvidence() {
        assertTrue(BannerAdSymbolScanner.accepts(bannerEvidence))
        val invalid = listOf(bannerEvidence.copy(hasParameterAnchors = false), bannerEvidence.copy(readsLayout = false),
            bannerEvidence.copy(looksUpFactory = false), bannerEvidence.copy(dispatchesConversion = false),
            bannerEvidence.copy(composesLayoutLogParams = false), bannerEvidence.copy(ownerHasResponse = false),
            bannerEvidence.copy(responseConsumerCount = 0), bannerEvidence.copy(responseConsumerCount = 2),
            bannerEvidence.copy(localCallersOnly = false))
        invalid.forEach { assertFalse(it.toString(), BannerAdSymbolScanner.accepts(it)) }
        val target = banner("renamed")
        val distractor = banner("a")
        assertEquals(target, BannerAdSymbolScanner.select(listOf(
            BannerAdSymbolScanner.Candidate(distractor, bannerEvidence.copy(readsLayout = false)),
            BannerAdSymbolScanner.Candidate(target, bannerEvidence)), null))
        assertNull(BannerAdSymbolScanner.select(listOf(
            BannerAdSymbolScanner.Candidate(distractor, bannerEvidence),
            BannerAdSymbolScanner.Candidate(target, bannerEvidence)), null))
        assertFalse(BannerAdSymbolScanner.valid(NotPersonalized::class.java.declaredMethods.single()))
    }

    @Test fun bannerCacheRetainsEveryProtocolFieldAndTheErasedFactoryInterface() {
        val symbols = buildHookSymbols {
            this[BannerAdContract.bannerAdConversionMethodSpec] = Banner::class.java.name + "#renamed"
        }
        val target = requireNotNull(BannerAdContract.resolveBannerAdSymbols(loader, symbols))
        assertEquals(banner("renamed"), target.conversionMethod)
        assertEquals(listOf("layout", "banner_card", "components", "component", "feed_banner", "log_param", "key", "value"),
            listOf(target.layoutField, target.bannerCardField, target.componentsField, target.componentField,
                target.feedBannerField, target.logParamField, target.logKeyField, target.logValueField).map { it.name })
        assertTrue(BannerAdSymbolScanner.isFactoryDispatch(Factory::class.java.declaredMethods.single(), Template::class.java))
        assertFalse(BannerAdSymbolScanner.isFactoryDispatch(TypedFactory::class.java.declaredMethods.single(), Template::class.java))
    }

    @Test fun rainNeedsEveryProtocolReadConstructionAndResponseLink() {
        assertTrue(ForumRainSymbolScanner.accepts(rainEvidence))
        for (field in rainEvidence.protocolReads) {
            assertFalse(field, ForumRainSymbolScanner.accepts(rainEvidence.copy(protocolReads = rainEvidence.protocolReads - field)))
        }
        listOf(rainEvidence.copy(constructsResult = false), rainEvidence.copy(createsJson = false),
            rainEvidence.copy(callerCount = 0), rainEvidence.copy(callerCount = 2),
            rainEvidence.copy(callerReadsProtocol = false), rainEvidence.copy(callerStoresResult = false))
            .forEach { assertFalse(it.toString(), ForumRainSymbolScanner.accepts(it)) }
        assertEquals(rain("convert"), ForumRainSymbolScanner.select(listOf(rain("convert")), null))
        assertNull(ForumRainSymbolScanner.select(listOf(rain("convert"), rain("a")), null))
        assertFalse(ForumRainSymbolScanner.valid(rain("wrongConstructor")))
    }

    @Test fun invalidRainResultRetainsTheIndependentDialogTarget() {
        fun symbols(result: String, dialog: Boolean) = buildHookSymbols {
            this[ForumPageAdContract.forumRainMapperClass] = RainMapper::class.java.name
            this[ForumPageAdContract.forumRainMapperMethod] = "convert"
            this[ForumPageAdContract.forumRainDataClass] = result
            if (dialog) {
                this[ForumPageAdContract.forumDialogControllerClass] = Dialog::class.java.name
                this[ForumPageAdContract.forumBusinessPromotShowMethod] = "show"
            }
        }
        val good = requireNotNull(ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, symbols(Rain::class.java.name, false)))
        assertEquals(rain("convert"), good.rainMapperMethod)
        val invalid = symbols(Any::class.java.name, true)
        assertFalse(ForumPageAdContract.isCacheValid(invalid, loader))
        val retained = requireNotNull(ForumPageAdContract.resolveForumPageAdBlockSymbols(loader, invalid))
        assertNull(retained.rainMapperMethod)
        assertEquals("show", retained.businessPromotShowMethod?.name)
    }

    private fun banner(name: String) = Banner::class.java.getDeclaredMethod(name, LayoutFactory::class.java, Map::class.java, java.lang.Boolean.TYPE)
    private fun rain(name: String) = RainMapper::class.java.getDeclaredMethod(name, FrsRainInfo::class.java)

    class Banner {
        @JvmField var response: DataRes? = null
        fun renamed(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): Template? = null
        fun a(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): Template? = null
    }
    class NotPersonalized { fun convert(layout: LayoutFactory, log: Map<Any, Any>, cached: Boolean): Template? = null }
    interface Template { fun key(): String; fun payload(): Any? }
    interface Factory { fun convert(origin: Any?, log: Any?): Template? }
    interface TypedFactory { fun convert(origin: LayoutFactory, log: Map<Any, Any>): Template? }
    class RainMapper {
        companion object {
            @JvmStatic fun convert(protocol: FrsRainInfo): Rain? = null
            @JvmStatic fun a(protocol: FrsRainInfo): Rain? = null
            @JvmStatic fun wrongConstructor(protocol: FrsRainInfo): WrongRain? = null
        }
    }
    class Rain(resource: String, popupUrl: String, popupInfo: JSONObject, businessId: String, reportInfo: String)
    class WrongRain(resource: String)
    class Dialog { fun show(forum: String, promotion: Any) = true }
}
