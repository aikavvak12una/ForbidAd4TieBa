package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.CollectionSearchScanSymbols
import com.forbidad4tieba.hook.symbol.model.CollectionSearchSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.CollectionSearchSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object CollectionContract : SymbolContract("Collection") {
    val collectionPresenterField = text("collectionPresenterField")
    val collectionPresenterListSetterMethod = text("collectionPresenterListSetterMethod")
    val collectionPresenterListSetterMethodSpec = text("collectionPresenterListSetterMethodSpec")
    val collectionPresenterAdapterField = text("collectionPresenterAdapterField")
    val collectionModelField = text("collectionModelField")
    val collectionModelListGetterMethod = text("collectionModelListGetterMethod")
    val collectionModelListGetterMethodSpec = text("collectionModelListGetterMethodSpec")
    val collectionModelParseMethod = text("collectionModelParseMethod")
    val collectionModelParseMethodSpec = text("collectionModelParseMethodSpec")
    val collectionModelListField = text("collectionModelListField")
    val collectionFragmentDisplayListField = text("collectionFragmentDisplayListField")
    val collectionActivityNavControllerField = text("collectionActivityNavControllerField")
    val collectionNavBarField = text("collectionNavBarField")
    val collectionAdapterShowFooterMethod = text("collectionAdapterShowFooterMethod")
    val collectionAdapterLoadingMethod = text("collectionAdapterLoadingMethod")
    val collectionAdapterHasMoreMethod = text("collectionAdapterHasMoreMethod")
    val collectionEditModeMethod = text("collectionEditModeMethod")

    fun isSearchComplete(symbols: HookSymbols): Boolean = listOf<Any?>(
        symbols[collectionPresenterField],
        symbols[collectionPresenterListSetterMethod],
        symbols[collectionPresenterListSetterMethodSpec],
        symbols[collectionModelField],
        symbols[collectionModelListGetterMethod],
        symbols[collectionModelListGetterMethodSpec],
        symbols[collectionModelParseMethod],
        symbols[collectionModelParseMethodSpec],
        symbols[collectionModelListField],
        symbols[collectionFragmentDisplayListField],
        symbols[collectionNavBarField],
    ).all { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Int -> value != 0
            else -> false
        }
    }

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val collectionScan = runScanStep(
            "CollectionSearchHook",
            logger,
            scanErrors,
            CollectionSearchScanSymbols(),
        ) {
            CollectionSearchSymbolScanner.scan(context, cl, logger)
        }

        val collectionPresenterField: String? = collectionScan.presenterField

        val collectionPresenterListSetterMethod: String? = collectionScan.presenterListSetterMethod

        val collectionPresenterListSetterMethodSpec: String? = collectionScan.presenterListSetterMethodSpec

        val collectionPresenterAdapterField: String? = collectionScan.presenterAdapterField

        val collectionModelField: String? = collectionScan.modelField

        val collectionModelListGetterMethod: String? = collectionScan.modelListGetterMethod

        val collectionModelListGetterMethodSpec: String? = collectionScan.modelListGetterMethodSpec

        val collectionModelParseMethod: String? = collectionScan.modelParseMethod

        val collectionModelParseMethodSpec: String? = collectionScan.modelParseMethodSpec

        val collectionModelListField: String? = collectionScan.modelListField

        val collectionFragmentDisplayListField: String? = collectionScan.fragmentDisplayListField

        val collectionActivityNavControllerField: String? = collectionScan.activityNavControllerField

        val collectionNavBarField: String? = collectionScan.navBarField

        val collectionAdapterShowFooterMethod: String? = collectionScan.adapterShowFooterMethod

        val collectionAdapterLoadingMethod: String? = collectionScan.adapterLoadingMethod

        val collectionAdapterHasMoreMethod: String? = collectionScan.adapterHasMoreMethod

        val collectionEditModeMethod: String? = collectionScan.editModeMethod

        output[CollectionContract.collectionPresenterField] = collectionPresenterField
        output[CollectionContract.collectionPresenterListSetterMethod] = collectionPresenterListSetterMethod
        output[CollectionContract.collectionPresenterListSetterMethodSpec] = collectionPresenterListSetterMethodSpec
        output[CollectionContract.collectionPresenterAdapterField] = collectionPresenterAdapterField
        output[CollectionContract.collectionModelField] = collectionModelField
        output[CollectionContract.collectionModelListGetterMethod] = collectionModelListGetterMethod
        output[CollectionContract.collectionModelListGetterMethodSpec] = collectionModelListGetterMethodSpec
        output[CollectionContract.collectionModelParseMethod] = collectionModelParseMethod
        output[CollectionContract.collectionModelParseMethodSpec] = collectionModelParseMethodSpec
        output[CollectionContract.collectionModelListField] = collectionModelListField
        output[CollectionContract.collectionFragmentDisplayListField] = collectionFragmentDisplayListField
        output[CollectionContract.collectionActivityNavControllerField] = collectionActivityNavControllerField
        output[CollectionContract.collectionNavBarField] = collectionNavBarField
        output[CollectionContract.collectionAdapterShowFooterMethod] = collectionAdapterShowFooterMethod
        output[CollectionContract.collectionAdapterLoadingMethod] = collectionAdapterLoadingMethod
        output[CollectionContract.collectionAdapterHasMoreMethod] = collectionAdapterHasMoreMethod
        output[CollectionContract.collectionEditModeMethod] = collectionEditModeMethod
    }

    fun resolveCollectionSearchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): CollectionSearchSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[CollectionSearchHook] skipped: scan symbols unavailable")
                return null
            }
            val activityClass = ScanReflection.safeFindClass(StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[CollectionSearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS,
                )
                return null
            }
            val fragmentClass = ScanReflection.safeFindClass(StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[CollectionSearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS,
                )
                return null
            }
            CollectionSearchSymbols(
                activityClass = activityClass,
                fragmentClass = fragmentClass,
                presenterField = requireSymbol("collectionPresenterField", resolvedSymbols[CollectionContract.collectionPresenterField]),
                presenterListSetterMethod = requireSymbol(
                    "collectionPresenterListSetterMethod",
                    resolvedSymbols[CollectionContract.collectionPresenterListSetterMethod],
                ),
                presenterListSetterMethodSpec = requireSymbol(
                    "collectionPresenterListSetterMethodSpec",
                    resolvedSymbols[CollectionContract.collectionPresenterListSetterMethodSpec],
                ),
                modelField = requireSymbol("collectionModelField", resolvedSymbols[CollectionContract.collectionModelField]),
                modelListGetterMethod = requireSymbol(
                    "collectionModelListGetterMethod",
                    resolvedSymbols[CollectionContract.collectionModelListGetterMethod],
                ),
                modelListGetterMethodSpec = requireSymbol(
                    "collectionModelListGetterMethodSpec",
                    resolvedSymbols[CollectionContract.collectionModelListGetterMethodSpec],
                ),
                modelParseMethod = requireSymbol("collectionModelParseMethod", resolvedSymbols[CollectionContract.collectionModelParseMethod]),
                modelParseMethodSpec = requireSymbol(
                    "collectionModelParseMethodSpec",
                    resolvedSymbols[CollectionContract.collectionModelParseMethodSpec],
                ),
                modelListField = requireSymbol("collectionModelListField", resolvedSymbols[CollectionContract.collectionModelListField]),
                fragmentDisplayListField = requireSymbol(
                    "collectionFragmentDisplayListField",
                    resolvedSymbols[CollectionContract.collectionFragmentDisplayListField],
                ),
                activityNavControllerField = resolvedSymbols[CollectionContract.collectionActivityNavControllerField]
                    ?.takeIf { it.isNotBlank() },
                navBarField = requireSymbol("collectionNavBarField", resolvedSymbols[CollectionContract.collectionNavBarField]),
                presenterAdapterField = resolvedSymbols[CollectionContract.collectionPresenterAdapterField]?.takeIf { it.isNotBlank() },
                adapterShowFooterMethod = resolvedSymbols[CollectionContract.collectionAdapterShowFooterMethod]?.takeIf { it.isNotBlank() },
                adapterLoadingMethod = resolvedSymbols[CollectionContract.collectionAdapterLoadingMethod]?.takeIf { it.isNotBlank() },
                adapterHasMoreMethod = resolvedSymbols[CollectionContract.collectionAdapterHasMoreMethod]?.takeIf { it.isNotBlank() },
                editModeMethod = resolvedSymbols[CollectionContract.collectionEditModeMethod]?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            Diagnostics.log("[CollectionSearchHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "CollectionSearchHook.Core",
            "${symbols[CollectionContract.collectionPresenterField]}.${symbols[CollectionContract.collectionPresenterListSetterMethod]}",
            listOf(
                CollectionContract.collectionPresenterField.check(symbols),
                CollectionContract.collectionPresenterListSetterMethod.check(symbols),
                "collectionPresenterListSetterMethodSpec" to has(
                    symbols[CollectionContract.collectionPresenterListSetterMethodSpec],
                ),
                CollectionContract.collectionPresenterAdapterField.check(symbols),
                CollectionContract.collectionModelField.check(symbols),
                CollectionContract.collectionModelListGetterMethod.check(symbols),
                CollectionContract.collectionModelListGetterMethodSpec.check(symbols),
                CollectionContract.collectionModelParseMethod.check(symbols),
                CollectionContract.collectionModelParseMethodSpec.check(symbols),
            ),
        )
        add(
            "CollectionSearchHook.List",
            "${symbols[CollectionContract.collectionModelListField]}/${symbols[CollectionContract.collectionFragmentDisplayListField]}",
            listOf(
                CollectionContract.collectionModelListField.check(symbols),
                CollectionContract.collectionFragmentDisplayListField.check(symbols),
                CollectionContract.collectionActivityNavControllerField.check(symbols),
                CollectionContract.collectionNavBarField.check(symbols),
            ),
        )
        add(
            "CollectionSearchHook.Adapter",
            "${symbols[CollectionContract.collectionPresenterAdapterField]}.{${symbols[CollectionContract.collectionAdapterShowFooterMethod]},${symbols[CollectionContract.collectionAdapterLoadingMethod]},${symbols[CollectionContract.collectionAdapterHasMoreMethod]}}",
            listOf(
                CollectionContract.collectionAdapterShowFooterMethod.check(symbols),
                CollectionContract.collectionAdapterLoadingMethod.check(symbols),
                CollectionContract.collectionAdapterHasMoreMethod.check(symbols),
                CollectionContract.collectionEditModeMethod.check(symbols),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasCollectionSearchSymbols =
            symbols[CollectionContract.collectionPresenterField] != null ||
                symbols[CollectionContract.collectionPresenterListSetterMethod] != null ||
                symbols[CollectionContract.collectionPresenterListSetterMethodSpec] != null ||
                symbols[CollectionContract.collectionModelField] != null ||
                symbols[CollectionContract.collectionModelListGetterMethod] != null ||
                symbols[CollectionContract.collectionModelListGetterMethodSpec] != null ||
                symbols[CollectionContract.collectionModelParseMethod] != null ||
                symbols[CollectionContract.collectionModelParseMethodSpec] != null ||
                symbols[CollectionContract.collectionModelListField] != null ||
                symbols[CollectionContract.collectionFragmentDisplayListField] != null
        if (hasCollectionSearchSymbols && symbols[CollectionContract.collectionNavBarField].isNullOrBlank()) return false
        return true
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
