package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbAdBidScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdRequestBlockSymbols
import com.forbidad4tieba.hook.symbol.resolve.PbAdRequestTargetResolver
import com.forbidad4tieba.hook.symbol.scan.PbAdBidSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PbAdRequestContract : SymbolContract("PbAdRequest") {
    val pbAdBidCommonRequestModelClass = text("pbAdBidCommonRequestModelClass")
    val pbAdBidCommonRequestStartMethods = texts("pbAdBidCommonRequestStartMethods", preserveEmpty = false)
    val pbAdBidCommonRequestNotifyMethod = text("pbAdBidCommonRequestNotifyMethod")
    val pbAdBidPageBrowserRequestModelClass = text("pbAdBidPageBrowserRequestModelClass")
    val pbAdBidPageBrowserRequestDataMethod = text("pbAdBidPageBrowserRequestDataMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val pbAdBidScan = runScanStep(
            "PbAdRequestBlockHook.AdBid",
            logger,
            scanErrors,
            PbAdBidScanSymbols(),
        ) {
            PbAdBidSymbolScanner.scan(context, cl, logger)
        }

        val pbAdBidCommonRequestModelClass: String? = pbAdBidScan.commonRequestModelClass

        val pbAdBidCommonRequestStartMethods: List<String>? = pbAdBidScan.commonRequestStartMethods.takeIf { it.isNotEmpty() }

        val pbAdBidCommonRequestNotifyMethod: String? = pbAdBidScan.commonRequestNotifyMethod

        val pbAdBidPageBrowserRequestModelClass: String? = pbAdBidScan.pageBrowserRequestModelClass

        val pbAdBidPageBrowserRequestDataMethod: String? = pbAdBidScan.pageBrowserRequestDataMethod

        output[PbAdRequestContract.pbAdBidCommonRequestModelClass] = pbAdBidCommonRequestModelClass
        output[PbAdRequestContract.pbAdBidCommonRequestStartMethods] = pbAdBidCommonRequestStartMethods
        output[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod] = pbAdBidCommonRequestNotifyMethod
        output[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass] = pbAdBidPageBrowserRequestModelClass
        output[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod] = pbAdBidPageBrowserRequestDataMethod
    }

    fun resolvePbAdRequestBlockSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbAdRequestBlockSymbols? = PbAdRequestTargetResolver.resolve(cl, symbols)

    internal fun pathStatus(symbols: HookSymbols): HookFeatureStatus {
        val hasPbAdBidCommonPath =
            !symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass].isNullOrBlank() &&
                !symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods].isNullOrEmpty() &&
                !symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod].isNullOrBlank()
        val hasPbAdBidPageBrowserPath =
            !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass].isNullOrBlank() &&
                !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod].isNullOrBlank()
        val pbAdRequestOptional = ArrayList<String>(5)
        if (!hasPbAdBidCommonPath && !hasPbAdBidPageBrowserPath) {
            if (symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass].isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestModelClass")
            }
            if (symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods].isNullOrEmpty()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestStartMethods")
            }
            if (symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod].isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidCommonRequestNotifyMethod")
            }
            if (symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass].isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidPageBrowserRequestModelClass")
            }
            if (symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod].isNullOrBlank()) {
                pbAdRequestOptional.add("pbAdBidPageBrowserRequestDataMethod")
            }
        }
        val pbRequestStatus = HookFeatureStatus(
            state = if (pbAdRequestOptional.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL,
            missingOptional = pbAdRequestOptional,
        )
        return pbRequestStatus
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PbAdRequestBlockHook.AdBid.Common",
            "common=${symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass]}.{${listTarget(symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods])}}#${symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod]}",
            listOf(
                PbAdRequestContract.pbAdBidCommonRequestModelClass.check(symbols),
                PbAdRequestContract.pbAdBidCommonRequestStartMethods.check(symbols),
                PbAdRequestContract.pbAdBidCommonRequestNotifyMethod.check(symbols),
            ),
        )
        addOptional(
            "PbAdRequestBlockHook.AdBid.PageBrowser",
            "pageBrowser=" + if (
                has(symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass]) &&
                has(symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod])
            ) {
                "${symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass]}.${symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod]}"
            } else {
                "optional-absent"
            },
            listOf(
                PbAdRequestContract.pbAdBidPageBrowserRequestModelClass.check(symbols),
                PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PbAdRequestBlockHook.", true, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPbAdBidSymbols =
            symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass] != null ||
                symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods] != null ||
                symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod] != null ||
                symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass] != null ||
                symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod] != null
        if (hasPbAdBidSymbols && !isPbAdBidValid(symbols, cl)) return false
        return true
    }

    private const val PB_COMMON_REQUEST_MODEL_CLASS =
        "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"

    private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"

    private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"

    private fun isPbAdBidValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val commonStartMethods = symbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods].orEmpty()
        val hasCompleteCommonSymbols =
            !symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass].isNullOrBlank() &&
                commonStartMethods.isNotEmpty() &&
                !symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod].isNullOrBlank()
        val hasCompletePageBrowserSymbols =
            !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass].isNullOrBlank() &&
                !symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod].isNullOrBlank()
        return try {
            if (hasCompleteCommonSymbols) {
                val commonBaseClass = ScanReflection.safeFindClass(PB_COMMON_REQUEST_MODEL_CLASS, cl) ?: return false
                val commonModelClass = ScanReflection.safeFindClass(symbols[PbAdRequestContract.pbAdBidCommonRequestModelClass]!!, cl) ?: return false
                if (!commonBaseClass.isAssignableFrom(commonModelClass)) return false

                val hasAllStartMethods = commonStartMethods.all { methodName ->
                    commonBaseClass.declaredMethods.any { method ->
                        method.name == methodName &&
                            !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Void.TYPE &&
                            method.parameterTypes.isEmpty()
                    }
                }
                if (!hasAllStartMethods) return false

                val hasNotifyMethod = commonBaseClass.declaredMethods.any { method ->
                    method.name == symbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod] &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        ScanReflection.isIntType(method.parameterTypes[0])
                }
                if (!hasNotifyMethod) return false
            }

            if (hasCompletePageBrowserSymbols) {
                val pageBrowserBaseClass = ScanReflection.safeFindClass(PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, cl) ?: return false
                val pageBrowserModelClass = ScanReflection.safeFindClass(symbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass]!!, cl)
                    ?: return false
                if (!pageBrowserBaseClass.isAssignableFrom(pageBrowserModelClass)) return false
                val continuationClass = ScanReflection.safeFindClass(KOTLIN_CONTINUATION_CLASS, cl) ?: return false
                val method = pageBrowserBaseClass.getDeclaredMethod(
                    symbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod]!!,
                    continuationClass,
                )
                if (Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers) ||
                    method.returnType != Any::class.java ||
                    pageBrowserModelClass.getMethod(method.name, continuationClass) != method) return false
            }

            true
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
