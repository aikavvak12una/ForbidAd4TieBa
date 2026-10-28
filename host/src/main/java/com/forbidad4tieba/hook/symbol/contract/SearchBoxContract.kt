package com.forbidad4tieba.hook.symbol.contract

import android.view.ViewGroup
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HomeHeaderScanSymbols
import com.forbidad4tieba.hook.symbol.model.HomeSearchBoxSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.SearchBoxTextAdSymbols
import com.forbidad4tieba.hook.symbol.scan.HomeHeaderSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the shared home-search target; hint blocking adds its own API dependency. */
object SearchBoxContract : SymbolContract("SearchBox") {
    internal override val candidateClasses = listOf(StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS)

    val searchBoxViewClass = text("searchBoxViewClass")
    val searchBoxSetHintMethod = text("searchBoxSetHintMethod")
    val homeSearchBoxOwnerClass = text("homeSearchBoxOwnerClass")
    val homeSearchBoxInitMethod = text("homeSearchBoxInitMethod")
    val homeSearchBoxGetterMethod = text("homeSearchBoxGetterMethod")
    private val home = SymbolDependencies(
        searchBoxViewClass, homeSearchBoxOwnerClass, homeSearchBoxInitMethod, homeSearchBoxGetterMethod,
    )
    private val hint = SymbolDependencies(searchBoxSetHintMethod)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val result = once(HomeHeaderSymbolScanner) {
            runScanStep("HomeHeaderHooks", logger, scanErrors, HomeHeaderScanSymbols()) {
                HomeHeaderSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
            }
        }
        output[searchBoxViewClass] = result.searchBoxViewClass
        output[searchBoxSetHintMethod] = result.searchBoxSetHintMethod
        output[homeSearchBoxOwnerClass] = result.homeSearchBoxOwnerClass
        output[homeSearchBoxInitMethod] = result.homeSearchBoxInitMethod
        output[homeSearchBoxGetterMethod] = result.homeSearchBoxGetterMethod
    }

    private const val HEADER_CONTAINER = "androidx.coordinatorlayout.widget.CoordinatorLayout"

    fun resolveHomeSearchBoxSymbols(cl: ClassLoader, symbols: HookSymbols): HomeSearchBoxSymbols? {
        if (home.missing(symbols).isNotEmpty()) return null
        return try {
            check(symbols[searchBoxViewClass] == StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS)
            check(symbols[homeSearchBoxOwnerClass] == StableTiebaHookPoints.HOME_SEARCH_BOX_OWNER_CLASS)
            val searchBox = Class.forName(requireNotNull(symbols[searchBoxViewClass]), false, cl)
            check(ViewGroup::class.java.isAssignableFrom(searchBox))
            restoreHome(
                Class.forName(requireNotNull(symbols[homeSearchBoxOwnerClass]), false, cl),
                searchBox,
                Class.forName(StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS, false, cl),
                Class.forName(HEADER_CONTAINER, false, cl),
                requireNotNull(symbols[homeSearchBoxInitMethod]),
                requireNotNull(symbols[homeSearchBoxGetterMethod]),
            )
        } catch (failure: Throwable) {
            Diagnostics.log("[SearchBox] cached home target invalid: ${failure.message}")
            null
        }
    }

    internal fun restoreHome(
        owner: Class<*>,
        searchBox: Class<*>,
        recycler: Class<*>,
        container: Class<*>,
        initName: String,
        getterName: String,
    ): HomeSearchBoxSymbols {
        val init = owner.getDeclaredMethod(initName, recycler, container)
        check(isInstanceMethod(init) && init.returnType == Void.TYPE) { "Invalid home search initializer" }
        val getter = owner.getDeclaredMethod(getterName)
        check(isInstanceMethod(getter) && getter.returnType == searchBox) { "Invalid home search getter" }
        init.isAccessible = true
        getter.isAccessible = true
        return HomeSearchBoxSymbols(init, getter)
    }

    private fun isInstanceMethod(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)

    fun resolveSearchBoxTextAdSymbols(cl: ClassLoader, symbols: HookSymbols?): SearchBoxTextAdSymbols? {
        val resolved = symbols ?: return null
        val target = resolveHomeSearchBoxSymbols(cl, resolved) ?: return null
        val name = resolved[searchBoxSetHintMethod] ?: return null
        return try {
            val method = target.ownerGetterMethod.returnType.getDeclaredMethod(
                name, List::class.java, Boolean::class.javaPrimitiveType,
            )
            check(isInstanceMethod(method) && method.returnType == Void.TYPE) { "Invalid search hint setter" }
            method.isAccessible = true
            SearchBoxTextAdSymbols(method, target.ownerInitMethod, target.ownerGetterMethod)
        } catch (failure: Throwable) {
            Diagnostics.log("[SearchBoxTextAdHook] cached hint target invalid: ${failure.message}")
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = mapOf(
        HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT to (home + hint).requiredStatus(symbols),
        HookFeatureKey.HIDE_RECOMMEND_SEARCH_BOX to home.requiredStatus(symbols),
    )

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("SearchBoxTextAdHook.Hint",
            "${symbols[searchBoxViewClass]}.${symbols[searchBoxSetHintMethod]}",
            SymbolDependencies(searchBoxViewClass, searchBoxSetHintMethod).checks(symbols))
        add("SearchBoxTextAdHook.Owner",
            "${symbols[homeSearchBoxOwnerClass]}.{${symbols[homeSearchBoxInitMethod]},${symbols[homeSearchBoxGetterMethod]}}",
            home.checks(symbols))
        add("RecommendSearchBoxHook",
            "${symbols[homeSearchBoxOwnerClass]}.${symbols[homeSearchBoxInitMethod]}",
            home.checks(symbols))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("SearchBoxTextAdHook.", true, listOf(HookFeatureKey.BLOCK_AD_SEARCH_BOX_TEXT)),
        PointOwner("RecommendSearchBoxHook", false, listOf(HookFeatureKey.HIDE_RECOMMEND_SEARCH_BOX)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        // Missing/partial discoveries are valid negative-cache entries. Complete groups must restore.
        (home.missing(symbols).isNotEmpty() || resolveHomeSearchBoxSymbols(cl, symbols) != null) &&
            ((home + hint).missing(symbols).isNotEmpty() || resolveSearchBoxTextAdSymbols(cl, symbols) != null)
}
