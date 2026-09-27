package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.model.HomeHeaderScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.SearchBoxTextAdSymbols
import com.forbidad4tieba.hook.symbol.scan.HomeHeaderSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object SearchBoxContract : SymbolContract("SearchBox") {
    internal override val candidateClasses = listOf(
        StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS,
    )

    val searchBoxViewClass = text("searchBoxViewClass")
    val searchBoxSetHintMethod = text("searchBoxSetHintMethod")
    val homeSearchBoxOwnerClass = text("homeSearchBoxOwnerClass")
    val homeSearchBoxInitMethod = text("homeSearchBoxInitMethod")
    val homeSearchBoxGetterMethod = text("homeSearchBoxGetterMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val homeHeaderScan = once(HomeHeaderSymbolScanner) {
runScanStep(
            "HomeHeaderHooks",
            logger,
            scanErrors,
            HomeHeaderScanSymbols(),
        ) {
            HomeHeaderSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        }

        output[SearchBoxContract.searchBoxViewClass] = homeHeaderScan.searchBoxViewClass
        output[SearchBoxContract.searchBoxSetHintMethod] = homeHeaderScan.searchBoxSetHintMethod
        output[SearchBoxContract.homeSearchBoxOwnerClass] = homeHeaderScan.homeSearchBoxOwnerClass
        output[SearchBoxContract.homeSearchBoxInitMethod] = homeHeaderScan.homeSearchBoxInitMethod
        output[SearchBoxContract.homeSearchBoxGetterMethod] = homeHeaderScan.homeSearchBoxGetterMethod
    }

    private const val SEARCH_BOX_HEADER_CONTAINER_CLASS = "androidx.coordinatorlayout.widget.CoordinatorLayout"

    fun resolveSearchBoxTextAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): SearchBoxTextAdSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: scan symbols unavailable")
                return null
            }
            val searchBoxClassName = resolvedSymbols[SearchBoxContract.searchBoxViewClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: missing searchBoxViewClass")
                return null
            }
            val setHintMethodName = resolvedSymbols[SearchBoxContract.searchBoxSetHintMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: missing searchBoxSetHintMethod")
                return null
            }
            val ownerClassName = resolvedSymbols[SearchBoxContract.homeSearchBoxOwnerClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxOwnerClass")
                return null
            }
            val ownerInitMethodName = resolvedSymbols[SearchBoxContract.homeSearchBoxInitMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxInitMethod")
                return null
            }
            val ownerGetterMethodName = resolvedSymbols[SearchBoxContract.homeSearchBoxGetterMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: missing homeSearchBoxGetterMethod")
                return null
            }

            val searchBoxClass = ScanReflection.safeFindClass(searchBoxClassName, cl) ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: class not found: $searchBoxClassName")
                return null
            }
            val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: class not found: $ownerClassName")
                return null
            }
            val recyclerClass = ScanReflection.safeFindClass(StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[SearchBoxTextAdHook] skipped: class not found: " +
                        StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS,
                )
                return null
            }
            val containerClass = ScanReflection.safeFindClass(SEARCH_BOX_HEADER_CONTAINER_CLASS, cl) ?: run {
                Diagnostics.log("[SearchBoxTextAdHook] skipped: class not found: $SEARCH_BOX_HEADER_CONTAINER_CLASS")
                return null
            }

            val setHintMethod = searchBoxClass.declaredMethods.singleOrNull { method ->
                method.name == setHintMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType
            } ?: run {
                Diagnostics.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$searchBoxClassName.$setHintMethodName(List,boolean)",
                )
                return null
            }

            val ownerMethods = ScanReflection.collectInstanceMethods(ownerClass)
            val ownerInitMethod = ownerMethods.singleOrNull { method ->
                method.name == ownerInitMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    recyclerClass.isAssignableFrom(method.parameterTypes[0]) &&
                    containerClass.isAssignableFrom(method.parameterTypes[1])
            } ?: run {
                Diagnostics.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$ownerClassName.$ownerInitMethodName(${recyclerClass.name},${containerClass.name})",
                )
                return null
            }
            val ownerGetterMethod = ownerMethods.singleOrNull { method ->
                method.name == ownerGetterMethodName &&
                    method.parameterTypes.isEmpty() &&
                    searchBoxClass.isAssignableFrom(method.returnType)
            } ?: run {
                Diagnostics.log(
                    "[SearchBoxTextAdHook] skipped: method mismatch: " +
                        "$ownerClassName.$ownerGetterMethodName()",
                )
                return null
            }

            setHintMethod.isAccessible = true
            ownerInitMethod.isAccessible = true
            ownerGetterMethod.isAccessible = true
            SearchBoxTextAdSymbols(
                setHintMethod = setHintMethod,
                ownerInitMethod = ownerInitMethod,
                ownerGetterMethod = ownerGetterMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[SearchBoxTextAdHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT] = statusFromMissing(
            listOfNotNull(
                "searchBoxViewClass".takeIf { symbols[SearchBoxContract.searchBoxViewClass].isNullOrBlank() },
                "searchBoxSetHintMethod".takeIf { symbols[SearchBoxContract.searchBoxSetHintMethod].isNullOrBlank() },
                "homeSearchBoxOwnerClass".takeIf { symbols[SearchBoxContract.homeSearchBoxOwnerClass].isNullOrBlank() },
                "homeSearchBoxInitMethod".takeIf { symbols[SearchBoxContract.homeSearchBoxInitMethod].isNullOrBlank() },
                "homeSearchBoxGetterMethod".takeIf { symbols[SearchBoxContract.homeSearchBoxGetterMethod].isNullOrBlank() },
            ),
        )
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "SearchBoxTextAdHook.Hint",
            "${symbols[SearchBoxContract.searchBoxViewClass]}.${symbols[SearchBoxContract.searchBoxSetHintMethod]}",
            listOf(
                SearchBoxContract.searchBoxViewClass.check(symbols),
                SearchBoxContract.searchBoxSetHintMethod.check(symbols),
            ),
        )
        add(
            "SearchBoxTextAdHook.Owner",
            "${symbols[SearchBoxContract.homeSearchBoxOwnerClass]}.{${symbols[SearchBoxContract.homeSearchBoxInitMethod]},${symbols[SearchBoxContract.homeSearchBoxGetterMethod]}}",
            listOf(
                SearchBoxContract.homeSearchBoxOwnerClass.check(symbols),
                SearchBoxContract.homeSearchBoxInitMethod.check(symbols),
                SearchBoxContract.homeSearchBoxGetterMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("SearchBoxTextAdHook.", true, listOf(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true
}
