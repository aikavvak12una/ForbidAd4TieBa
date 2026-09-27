package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.findWebMethodInHierarchy
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isGetInnerWebViewMethod
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isGetUrlMethod
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isStringLoadUrlMethod
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HomeSideBarWebBlockScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.scan.WebAdBlockSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object HomeSidebarContract : SymbolContract("HomeSidebar") {
    val homeSideBarWebViewClass = text("homeSideBarWebViewClass")
    val homeSideBarTbWebViewClass = text("homeSideBarTbWebViewClass")
    val homeSideBarWebGetWebViewMethod = text("homeSideBarWebGetWebViewMethod")
    val homeSideBarWebGetUrlMethod = text("homeSideBarWebGetUrlMethod")
    val homeSideBarWebGetInnerWebViewMethod = text("homeSideBarWebGetInnerWebViewMethod")
    val homeSideBarWebLoadUrlMethods = texts("homeSideBarWebLoadUrlMethods", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val homeSideBarWebBlockScan = runScanStep(
            "HomeSideBarWebBlockHook",
            logger,
            scanErrors,
            HomeSideBarWebBlockScanSymbols(),
        ) {
            WebAdBlockSymbolScanner.scanHomeSideBar(cl, logger)
        }

        val homeSideBarWebViewClass: String? = homeSideBarWebBlockScan.sideBarWebViewClass

        val homeSideBarTbWebViewClass: String? = homeSideBarWebBlockScan.tbWebViewClass

        val homeSideBarWebGetWebViewMethod: String? = homeSideBarWebBlockScan.getWebViewMethod

        val homeSideBarWebGetUrlMethod: String? = homeSideBarWebBlockScan.getUrlMethod

        val homeSideBarWebGetInnerWebViewMethod: String? = homeSideBarWebBlockScan.getInnerWebViewMethod

        val homeSideBarWebLoadUrlMethods: List<String>? = homeSideBarWebBlockScan.loadUrlMethods.takeIf { it.isNotEmpty() }

        output[HomeSidebarContract.homeSideBarWebViewClass] = homeSideBarWebViewClass
        output[HomeSidebarContract.homeSideBarTbWebViewClass] = homeSideBarTbWebViewClass
        output[HomeSidebarContract.homeSideBarWebGetWebViewMethod] = homeSideBarWebGetWebViewMethod
        output[HomeSidebarContract.homeSideBarWebGetUrlMethod] = homeSideBarWebGetUrlMethod
        output[HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod] = homeSideBarWebGetInnerWebViewMethod
        output[HomeSidebarContract.homeSideBarWebLoadUrlMethods] = homeSideBarWebLoadUrlMethods
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB] = statusFromMissing(
            listOfNotNull(
                "homeSideBarWebViewClass".takeIf { symbols[HomeSidebarContract.homeSideBarWebViewClass].isNullOrBlank() },
                "homeSideBarTbWebViewClass".takeIf { symbols[HomeSidebarContract.homeSideBarTbWebViewClass].isNullOrBlank() },
                "homeSideBarWebGetWebViewMethod".takeIf {
                    symbols[HomeSidebarContract.homeSideBarWebGetWebViewMethod].isNullOrBlank()
                },
                "homeSideBarWebGetUrlMethod".takeIf { symbols[HomeSidebarContract.homeSideBarWebGetUrlMethod].isNullOrBlank() },
                "homeSideBarWebGetInnerWebViewMethod".takeIf {
                    symbols[HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod].isNullOrBlank()
                },
                "homeSideBarWebLoadUrlMethods".takeIf { symbols[HomeSidebarContract.homeSideBarWebLoadUrlMethods].isNullOrEmpty() },
            ),
        )
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "HomeSideBarWebBlockHook",
            "${symbols[HomeSidebarContract.homeSideBarWebViewClass]}.{${listTarget(symbols[HomeSidebarContract.homeSideBarWebLoadUrlMethods])}} " +
                "getWebView=${symbols[HomeSidebarContract.homeSideBarWebGetWebViewMethod]} " +
                "tb=${symbols[HomeSidebarContract.homeSideBarTbWebViewClass]}.${symbols[HomeSidebarContract.homeSideBarWebGetUrlMethod]}/" +
                symbols[HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod],
            listOf(
                HomeSidebarContract.homeSideBarWebViewClass.check(symbols),
                HomeSidebarContract.homeSideBarTbWebViewClass.check(symbols),
                HomeSidebarContract.homeSideBarWebGetWebViewMethod.check(symbols),
                HomeSidebarContract.homeSideBarWebGetUrlMethod.check(symbols),
                HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod.check(symbols),
                HomeSidebarContract.homeSideBarWebLoadUrlMethods.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("HomeSideBarWebBlockHook", false, listOf(HookFeatureKey.BLOCK_AD_HOME_SIDE_BAR_WEB)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasHomeSideBarWebBlockSymbols =
            symbols[HomeSidebarContract.homeSideBarWebViewClass] != null ||
                symbols[HomeSidebarContract.homeSideBarTbWebViewClass] != null ||
                symbols[HomeSidebarContract.homeSideBarWebGetWebViewMethod] != null ||
                symbols[HomeSidebarContract.homeSideBarWebGetUrlMethod] != null ||
                symbols[HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod] != null ||
                !symbols[HomeSidebarContract.homeSideBarWebLoadUrlMethods].isNullOrEmpty()
        if (hasHomeSideBarWebBlockSymbols && !isHomeSideBarWebBlockValid(symbols, cl)) return false
        return true
    }

    private fun isHomeSideBarWebBlockValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val sideBarClassName = symbols[HomeSidebarContract.homeSideBarWebViewClass] ?: return false
        val tbWebViewClassName = symbols[HomeSidebarContract.homeSideBarTbWebViewClass] ?: return false
        val getWebViewMethodName = symbols[HomeSidebarContract.homeSideBarWebGetWebViewMethod] ?: return false
        val getUrlMethodName = symbols[HomeSidebarContract.homeSideBarWebGetUrlMethod] ?: return false
        val getInnerWebViewMethodName = symbols[HomeSidebarContract.homeSideBarWebGetInnerWebViewMethod] ?: return false
        val loadUrlMethodNames = symbols[HomeSidebarContract.homeSideBarWebLoadUrlMethods].orEmpty().filter { it.isNotBlank() }
        if (loadUrlMethodNames.isEmpty()) return false
        return try {
            val sideBarClass = ScanReflection.safeFindClass(sideBarClassName, cl) ?: return false
            val tbWebViewClass = ScanReflection.safeFindClass(tbWebViewClassName, cl) ?: return false
            val hasGetWebViewMethod = findWebMethodInHierarchy(sideBarClass, getWebViewMethodName) { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    tbWebViewClass.isAssignableFrom(method.returnType)
            } != null
            if (!hasGetWebViewMethod) return false
            if (findWebMethodInHierarchy(tbWebViewClass, getUrlMethodName, ::isGetUrlMethod) == null) return false
            if (
                findWebMethodInHierarchy(
                    tbWebViewClass,
                    getInnerWebViewMethodName,
                    ::isGetInnerWebViewMethod,
                ) == null
            ) {
                return false
            }
            loadUrlMethodNames.all { methodName ->
                sideBarClass.declaredMethods.any { method ->
                    method.name == methodName && isStringLoadUrlMethod(method)
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}
