package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.RestoredMembers.resolveMethodByCachedSpec
import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import com.forbidad4tieba.hook.symbol.model.FeedAdSymbols
import com.forbidad4tieba.hook.symbol.model.FeedCardScanSymbols
import com.forbidad4tieba.hook.symbol.model.FeedInfoLogSymbols
import com.forbidad4tieba.hook.symbol.model.FeedTemplateScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.RecommendCardNestedDataScanSymbols
import com.forbidad4tieba.hook.symbol.scan.FeedCardSchemaSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.FeedCardSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.FeedTemplateSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.RecommendCardSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object FeedContract : SymbolContract("Feed") {
    internal override val candidateClasses = listOf(
        "com.baidu.tieba.feed.list.FeedTemplateAdapter",
        "com.baidu.tieba.feed.card.FeedCardView",
        "com.baidu.tieba.feed.component.uistate.CardHeadUiState",
    )

    val feedTemplateKeyMethod = text("feedTemplateKeyMethod")
    val feedTemplatePayloadMethod = text("feedTemplatePayloadMethod")
    val feedTemplateLoadMoreMethod = text("feedTemplateLoadMoreMethod")
    val feedCardBindMethod = text("feedCardBindMethod")
    val feedCardBindMethodSpec = text("feedCardBindMethodSpec")
    val feedCardDataListField = text("feedCardDataListField")
    val feedCardSchemaGetterSpec = text("feedCardSchemaGetterSpec")
    val feedHeadParamsField = text("feedHeadParamsField")
    val feedRecommendCardNestedDataMethod = text("feedRecommendCardNestedDataMethod")
    val feedRecommendCardNestedDataListField = text("feedRecommendCardNestedDataListField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val feedTemplateScan = runScanStep(
            "FeedTemplateHook",
            logger,
            scanErrors,
            FeedTemplateScanSymbols(),
        ) {
            FeedTemplateSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        val feedKeyMethod: String? = feedTemplateScan.keyMethod

        val feedPayloadMethod: String? = feedTemplateScan.payloadMethod

        val feedLoadMoreMethod: String? = feedTemplateScan.loadMoreMethod

        val feedCardScan = runScanStep(
            "FeedInfoLogHook.Bind",
            logger,
            scanErrors,
            FeedCardScanSymbols(),
        ) {
            FeedCardSymbolScanner.scanBind(candidatesWithWhitelist, cl, logger)
        }

        val feedCardBindMethod: String? = feedCardScan.bindMethod

        val feedCardBindMethodSpec: String? = feedCardScan.bindMethodSpec

        val feedCardDataListField: String? = feedCardScan.dataListField

        val feedCardSchemaGetterSpec: String? = runScanStep("CustomPostCardBlockHook.TopicSchema", logger, scanErrors, null) {
            FeedCardSchemaSymbolScanner.scan(context, cl, feedCardBindMethodSpec, logger)
        }

        val feedHeadParamsScan = runScanStep(
            "CustomPostCardBlockHook.FeedHeadParams",
            logger,
            scanErrors,
            FeedCardScanSymbols(),
        ) {
            FeedCardSymbolScanner.scanHeadParams(candidatesWithWhitelist, cl, logger)
        }

        val feedHeadParamsField: String? = feedHeadParamsScan.feedHeadParamsField

        val recommendCardNestedDataScan = runScanStep(
            "CustomPostCardBlockHook.RecommendCard",
            logger,
            scanErrors,
            RecommendCardNestedDataScanSymbols(),
        ) {
            RecommendCardSymbolScanner.scanNestedData(cl, logger)
        }

        val feedRecommendCardNestedDataMethod: String? = recommendCardNestedDataScan.nestedDataMethod

        val feedRecommendCardNestedDataListField: String? = recommendCardNestedDataScan.nestedDataListField

        output[FeedContract.feedTemplateKeyMethod] = feedKeyMethod
        output[FeedContract.feedTemplatePayloadMethod] = feedPayloadMethod
        output[FeedContract.feedTemplateLoadMoreMethod] = feedLoadMoreMethod
        output[FeedContract.feedCardBindMethod] = feedCardBindMethod
        output[FeedContract.feedCardBindMethodSpec] = feedCardBindMethodSpec
        output[FeedContract.feedCardDataListField] = feedCardDataListField
        output[FeedContract.feedCardSchemaGetterSpec] = feedCardSchemaGetterSpec
        output[FeedContract.feedHeadParamsField] = feedHeadParamsField
        output[FeedContract.feedRecommendCardNestedDataMethod] = feedRecommendCardNestedDataMethod
        output[FeedContract.feedRecommendCardNestedDataListField] = feedRecommendCardNestedDataListField
    }

    fun resolveFeedAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
        includeCustomPostFilter: Boolean = false,
    ): FeedAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[FeedAdHook] skipped: scan symbols unavailable")
                return null
            }
            val templateKeyMethodName = resolvedSymbols[FeedContract.feedTemplateKeyMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FeedAdHook] skipped: feedTemplateKeyMethod missing")
                return null
            }
            val templateAdapterClass = ScanReflection.safeFindClass(StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS, cl) ?: run {
                Diagnostics.log("[FeedAdHook] class NOT FOUND: ${StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS}")
                return null
            }
            val setListMethod = findFeedListMethod(templateAdapterClass, StableTiebaHookPoints.METHOD_SET_LIST) ?: run {
                Diagnostics.log(
                    "[FeedAdHook] method NOT FOUND: " +
                        "${StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS}.${StableTiebaHookPoints.METHOD_SET_LIST}(List)",
                )
                return null
            }

            val loadMoreMethod = resolveFeedLoadMoreMethod(cl, resolvedSymbols)
            val customPostFilter = if (includeCustomPostFilter) {
                resolveCustomPostCardFilterSymbols(cl, resolvedSymbols) ?: return null
            } else {
                null
            }
            FeedAdSymbols(
                setListMethod = setListMethod,
                loadMoreMethod = loadMoreMethod,
                templateKeyMethodName = templateKeyMethodName,
                customPostFilter = customPostFilter,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[FeedAdHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolveFeedInfoLogSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): FeedInfoLogSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[FeedInfoLogHook] skipped: scan symbols unavailable")
                return null
            }
            val bindMethodName = resolvedSymbols[FeedContract.feedCardBindMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FeedInfoLogHook] skipped: feedCardBindMethod missing")
                return null
            }
            val bindMethodSpec = resolvedSymbols[FeedContract.feedCardBindMethodSpec]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FeedInfoLogHook] skipped: feedCardBindMethodSpec missing")
                return null
            }
            val feedCardViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[FeedInfoLogHook] skipped: class not found: " +
                        StableTiebaHookPoints.FEED_CARD_VIEW_CLASS,
                )
                return null
            }
            val bindMethod = resolveMethodByCachedSpec(feedCardViewClass, bindMethodSpec, bindMethodName) ?: run {
                Diagnostics.log(
                    "[FeedInfoLogHook] skipped: method not found: " +
                        "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.$bindMethodSpec",
                )
                return null
            }
            bindMethod.isAccessible = true
            FeedInfoLogSymbols(
                bindMethod = bindMethod,
                templateKeyMethodName = resolvedSymbols[FeedContract.feedTemplateKeyMethod]?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            Diagnostics.log("[FeedInfoLogHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolveFeedLoadMoreMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val methodName = symbols[FeedContract.feedTemplateLoadMoreMethod]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[FeedAdHook] FeedTemplateAdapter loadMore skipped: scan symbol missing")
            return null
        }
        val adapterClass = ScanReflection.safeFindClass(StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS, cl) ?: run {
            Diagnostics.log("[FeedAdHook] class NOT FOUND: ${StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS}")
            return null
        }
        return findFeedListMethod(adapterClass, methodName) ?: run {
            Diagnostics.log(
                "[FeedAdHook] method NOT FOUND: " +
                    "${StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS}.$methodName(List)",
            )
            null
        }
    }

    private fun findFeedListMethod(clazz: Class<*>, methodName: String): Method? {
        return try {
            clazz.getDeclaredMethod(methodName, List::class.java).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun resolveCustomPostCardFilterSymbols(cl: ClassLoader, symbols: HookSymbols): CustomPostCardFilterSymbols? {
        val templateKeyMethodName = symbols[FeedContract.feedTemplateKeyMethod]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[CustomPostCardBlockHook] SKIP: feedTemplateKeyMethod missing")
            return null
        }
        val templatePayloadMethodName = symbols[FeedContract.feedTemplatePayloadMethod]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[CustomPostCardBlockHook] SKIP: feedTemplatePayloadMethod missing")
            return null
        }
        val dataListFieldName = symbols[FeedContract.feedCardDataListField]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[CustomPostCardBlockHook] SKIP: feedCardDataListField missing")
            return null
        }
        return CustomPostCardFilterSymbols(
            dataListFieldName = dataListFieldName,
            templateKeyMethodName = templateKeyMethodName,
            templatePayloadMethodName = templatePayloadMethodName,
            headParamsFieldName = symbols[FeedContract.feedHeadParamsField]?.takeIf { it.isNotBlank() },
            recommendNestedDataMethodName = symbols[FeedContract.feedRecommendCardNestedDataMethod]?.takeIf { it.isNotBlank() },
            recommendNestedDataListFieldName = symbols[FeedContract.feedRecommendCardNestedDataListField]?.takeIf { it.isNotBlank() },
            schemaGetter = FeedCardSchemaSymbolScanner.restore(cl, symbols[FeedContract.feedCardBindMethodSpec], symbols[FeedContract.feedCardSchemaGetterSpec]),
        )
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val feedTemplateKeyMissing = symbols[FeedContract.feedTemplateKeyMethod].isNullOrBlank()
        val customPostCritical = ArrayList<String>(4)
        val customPostOptional = ArrayList<String>(1)
        if (feedTemplateKeyMissing) customPostCritical.add("feedTemplateKeyMethod")
        if (symbols[FeedContract.feedTemplatePayloadMethod].isNullOrBlank()) customPostCritical.add("feedTemplatePayloadMethod")
        if (symbols[FeedContract.feedTemplateLoadMoreMethod].isNullOrBlank()) customPostCritical.add("feedTemplateLoadMoreMethod")
        if (symbols[FeedContract.feedCardDataListField].isNullOrBlank()) customPostCritical.add("feedCardDataListField")
        if (symbols[FeedContract.feedHeadParamsField].isNullOrBlank()) customPostOptional.add("feedHeadParamsField")
        if (symbols[FeedContract.feedCardSchemaGetterSpec].isNullOrBlank()) customPostOptional.add("feedCardSchemaGetterSpec")
        if (symbols[FeedContract.feedRecommendCardNestedDataMethod].isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataMethod")
        }
        if (symbols[FeedContract.feedRecommendCardNestedDataListField].isNullOrBlank()) {
            customPostOptional.add("feedRecommendCardNestedDataListField")
        }
        out[HookFeatureKey.ENABLE_CUSTOM_POST_FILTER] = if (customPostCritical.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = customPostCritical,
                missingOptional = customPostOptional,
            )
        } else if (customPostOptional.isNotEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = customPostOptional,
            )
        } else {
            HookFeatureStatus(state = HookFeatureState.FULL)
        }
        val feedAdCritical = ArrayList<String>(1)
        val feedAdOptional = ArrayList<String>(1)
        if (symbols[FeedContract.feedTemplateKeyMethod].isNullOrBlank()) feedAdCritical.add("feedTemplateKeyMethod")
        if (symbols[FeedContract.feedTemplateLoadMoreMethod].isNullOrBlank()) feedAdOptional.add("feedTemplateLoadMoreMethod")
        out[HookFeatureKey.BLOCK_AD_FEED] = statusFromMissing(feedAdCritical, feedAdOptional)
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "FeedAdHook.TemplateKey",
            "Feed item.${symbols[FeedContract.feedTemplateKeyMethod]}()",
            listOf(FeedContract.feedTemplateKeyMethod.check(symbols)),
        )
        add(
            "CustomPostCardBlockHook.Payload",
            "Feed item.${symbols[FeedContract.feedTemplatePayloadMethod]}()",
            listOf(FeedContract.feedTemplatePayloadMethod.check(symbols)),
        )
        add(
            "FeedAdHook.LoadMore",
            "com.baidu.tieba.feed.list.FeedTemplateAdapter.${symbols[FeedContract.feedTemplateLoadMoreMethod]}(List)",
            listOf(FeedContract.feedTemplateLoadMoreMethod.check(symbols)),
        )
        add(
            "CustomPostCardBlockHook",
            "com.baidu.tieba.feed.list.TemplateAdapter.setList / FeedTemplateAdapter.${symbols[FeedContract.feedTemplateLoadMoreMethod]}",
            listOf(
                FeedContract.feedTemplateKeyMethod.check(symbols),
                FeedContract.feedTemplatePayloadMethod.check(symbols),
                FeedContract.feedTemplateLoadMoreMethod.check(symbols),
                FeedContract.feedCardDataListField.check(symbols),
            ),
        )
        add(
            "CustomPostCardBlockHook.HeadParams",
            "Feed head params.${symbols[FeedContract.feedHeadParamsField]}",
            listOf(FeedContract.feedHeadParamsField.check(symbols)),
        )
        add(
            "CustomPostCardBlockHook.TopicSchema",
            "Card schema getter ${symbols[FeedContract.feedCardSchemaGetterSpec]}",
            listOf(FeedContract.feedCardSchemaGetterSpec.check(symbols)),
        )
        add(
            "CustomPostCardBlockHook.RecommendCard",
            "RecommendCardUiState.${symbols[FeedContract.feedRecommendCardNestedDataMethod]}()[NestedData.${symbols[FeedContract.feedRecommendCardNestedDataListField]}]",
            listOf(
                FeedContract.feedRecommendCardNestedDataMethod.check(symbols),
                FeedContract.feedRecommendCardNestedDataListField.check(symbols),
            ),
        )
        add(
            "FeedInfoLogHook.Bind",
            "${StableTiebaHookPoints.FEED_CARD_VIEW_CLASS}.${symbols[FeedContract.feedCardBindMethodSpec]}",
            listOf(FeedContract.feedCardBindMethodSpec.check(symbols)),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("FeedInfoLogHook.Bind", false, listOf(HookFeatureKey.HOME_NATIVE_GLASS, HookFeatureKey.DETAILED_LOGGING)),
        PointOwner("FeedAdHook.TemplateKey", false, listOf(HookFeatureKey.BLOCK_AD_FEED, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)),
        PointOwner("FeedAdHook.LoadMore", false, listOf(HookFeatureKey.BLOCK_AD_FEED, HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)),
        PointOwner("CustomPostCardBlockHook", true, listOf(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!FeedCardSchemaSymbolScanner.isCacheValid(cl, symbols[FeedContract.feedCardBindMethodSpec], symbols[FeedContract.feedCardSchemaGetterSpec])) return false
        return true
    }
}
