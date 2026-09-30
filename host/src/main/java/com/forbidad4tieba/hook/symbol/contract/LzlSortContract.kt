package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.LzlSortSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

object LzlSortContract : SymbolContract("LzlSort") {
    val defaultSortSetter = text("lzlDefaultSortSetter")
    private val required = SymbolDependencies(defaultSortSetter)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[defaultSortSetter] = scan.runScanStep(
            "DefaultLzlEarliestHook", scan.logger, scan.scanErrors, null as String?,
        ) { LzlSortSymbolScanner.scan(scan.cl, scan.logger) }
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols): Method? {
        val name = symbols[defaultSortSetter] ?: return null
        return try {
            restore(Class.forName(LzlSortSymbolScanner.MODEL, false, cl), name)
        } catch (failure: Throwable) {
            Diagnostics.log("[DefaultLzlEarliestHook] cached setter invalid: ${failure.message}")
            null
        }
    }

    internal fun restore(owner: Class<*>, name: String): Method =
        owner.getDeclaredMethod(name, String::class.java).apply {
            check(LzlSortSymbolScanner.isSetter(this)) { "Invalid default sort setter" }
            isAccessible = true
        }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolve(cl, symbols) != null

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.DEFAULT_LZL_EARLIEST to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("DefaultLzlEarliestHook", "${LzlSortSymbolScanner.MODEL}.${symbols[defaultSortSetter]}(String)",
            required.checks(symbols))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("DefaultLzlEarliestHook", false, listOf(HookFeatureKey.DEFAULT_LZL_EARLIEST)),
    )
}
