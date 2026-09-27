package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.findWebMethodInHierarchy
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isGetInnerWebViewMethod
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isGetUrlMethod
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.isStringLoadUrlMethod
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.MineTabWebBlockScanSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.scan.WebAdBlockSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object MineTabWebContract : SymbolContract("MineTabWeb") {
    val mineTabWebViewClass = text("mineTabWebViewClass")
    val mineTabWebLoadUrlMethod = text("mineTabWebLoadUrlMethod")
    val mineTabWebGetUrlMethod = text("mineTabWebGetUrlMethod")
    val mineTabWebGetInnerWebViewMethod = text("mineTabWebGetInnerWebViewMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val mineTabWebBlockScan = runScanStep(
            "MineTabWebBlockHook",
            logger,
            scanErrors,
            MineTabWebBlockScanSymbols(),
        ) {
            WebAdBlockSymbolScanner.scanMineTab(cl, logger)
        }

        val mineTabWebViewClass: String? = mineTabWebBlockScan.webViewClass

        val mineTabWebLoadUrlMethod: String? = mineTabWebBlockScan.loadUrlMethod

        val mineTabWebGetUrlMethod: String? = mineTabWebBlockScan.getUrlMethod

        val mineTabWebGetInnerWebViewMethod: String? = mineTabWebBlockScan.getInnerWebViewMethod

        output[MineTabWebContract.mineTabWebViewClass] = mineTabWebViewClass
        output[MineTabWebContract.mineTabWebLoadUrlMethod] = mineTabWebLoadUrlMethod
        output[MineTabWebContract.mineTabWebGetUrlMethod] = mineTabWebGetUrlMethod
        output[MineTabWebContract.mineTabWebGetInnerWebViewMethod] = mineTabWebGetInnerWebViewMethod
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val mineTabWebCritical = ArrayList<String>(4)
        if (symbols[MineTabWebContract.mineTabWebViewClass].isNullOrBlank()) mineTabWebCritical.add("mineTabWebViewClass")
        if (symbols[MineTabWebContract.mineTabWebLoadUrlMethod].isNullOrBlank()) mineTabWebCritical.add("mineTabWebLoadUrlMethod")
        if (symbols[MineTabWebContract.mineTabWebGetUrlMethod].isNullOrBlank()) mineTabWebCritical.add("mineTabWebGetUrlMethod")
        if (symbols[MineTabWebContract.mineTabWebGetInnerWebViewMethod].isNullOrBlank()) {
            mineTabWebCritical.add("mineTabWebGetInnerWebViewMethod")
        }
        out[HookFeatureKey.BLOCK_AD_MINE_TAB_WEB] = statusFromMissing(mineTabWebCritical)
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "MineTabWebBlockHook",
            "${symbols[MineTabWebContract.mineTabWebViewClass]}.${symbols[MineTabWebContract.mineTabWebLoadUrlMethod]}(String) " +
                "getUrl=${symbols[MineTabWebContract.mineTabWebGetUrlMethod]} " +
                "inner=${symbols[MineTabWebContract.mineTabWebGetInnerWebViewMethod]}",
            listOf(
                MineTabWebContract.mineTabWebViewClass.check(symbols),
                MineTabWebContract.mineTabWebLoadUrlMethod.check(symbols),
                MineTabWebContract.mineTabWebGetUrlMethod.check(symbols),
                MineTabWebContract.mineTabWebGetInnerWebViewMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("MineTabWebBlockHook", false, listOf(HookFeatureKey.BLOCK_AD_MINE_TAB_WEB)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasMineTabWebBlockSymbols =
            symbols[MineTabWebContract.mineTabWebViewClass] != null ||
                symbols[MineTabWebContract.mineTabWebLoadUrlMethod] != null ||
                symbols[MineTabWebContract.mineTabWebGetUrlMethod] != null ||
                symbols[MineTabWebContract.mineTabWebGetInnerWebViewMethod] != null
        if (hasMineTabWebBlockSymbols && !isMineTabWebBlockValid(symbols, cl)) return false
        return true
    }

    private fun isMineTabWebBlockValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[MineTabWebContract.mineTabWebViewClass] ?: return false
        val loadUrlMethodName = symbols[MineTabWebContract.mineTabWebLoadUrlMethod] ?: return false
        val getUrlMethodName = symbols[MineTabWebContract.mineTabWebGetUrlMethod] ?: return false
        val getInnerWebViewMethodName = symbols[MineTabWebContract.mineTabWebGetInnerWebViewMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            findWebMethodInHierarchy(targetClass, loadUrlMethodName, ::isStringLoadUrlMethod) != null &&
                findWebMethodInHierarchy(targetClass, getUrlMethodName, ::isGetUrlMethod) != null &&
                findWebMethodInHierarchy(targetClass, getInnerWebViewMethodName, ::isGetInnerWebViewMethod) != null
        } catch (_: Throwable) {
            false
        }
    }
}
