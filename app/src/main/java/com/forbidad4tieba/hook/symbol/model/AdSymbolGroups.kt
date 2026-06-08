package com.forbidad4tieba.hook.symbol.model

data class AdSymbols(
    val feedTemplate: FeedTemplateSymbolsGroup = FeedTemplateSymbolsGroup(),
    val splash: SplashAdSymbolsGroup = SplashAdSymbolsGroup(),
    val closeAd: CloseAdSymbolsGroup = CloseAdSymbolsGroup(),
    val zga: ZgaSymbolsGroup = ZgaSymbolsGroup(),
    val typeAdapter: TypeAdapterSymbolsGroup = TypeAdapterSymbolsGroup(),
    val feedCard: FeedCardSymbolsGroup = FeedCardSymbolsGroup(),
)

data class FeedTemplateSymbolsGroup(
    val feedTemplateKeyMethod: String? = null,
    val feedTemplatePayloadMethod: String? = null,
    val feedTemplateLoadMoreMethod: String? = null,
)

data class SplashAdSymbolsGroup(
    val splashAdHelperClass: String? = null,
    val splashAdHelperMethod: String? = null,
)

data class CloseAdSymbolsGroup(
    val closeAdDataClass: String? = null,
    val closeAdDataMethodG1: String? = null,
    val closeAdDataMethodJ1: String? = null,
)

data class ZgaSymbolsGroup(
    val zgaClass: String? = null,
    val zgaMethods: List<String>? = null,
)

data class TypeAdapterSymbolsGroup(
    val typeAdapterSetDataMethod: String? = null,
    val typeAdapterDataItemClass: String? = null,
    val typeAdapterDataGetTypeMethod: String? = null,
)

data class FeedCardSymbolsGroup(
    val feedCardBindMethod: String? = null,
    val feedCardDataListField: String? = null,
    val feedHeadParamsField: String? = null,
    val feedRecommendCardNestedDataMethod: String? = null,
    val feedRecommendCardNestedDataListField: String? = null,
)
