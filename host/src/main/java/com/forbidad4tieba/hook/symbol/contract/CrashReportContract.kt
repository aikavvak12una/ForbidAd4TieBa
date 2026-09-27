package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.CrashReportSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

/** The optional obfuscated SDK report path complements the stable crash initialization hooks. */
object CrashReportContract : SymbolContract("CrashReport") {
    val crashReportExceptionMethod = text("crashReportExceptionMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) {
        output[crashReportExceptionMethod] = scan.runScanStep(
            "CrashReportBlockHook.Exception", scan.logger, scan.scanErrors, null as String?,
        ) { CrashReportSymbolScanner.scan(scan.cl, scan.logger) }
    }

    fun resolveExceptionReportMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val name = symbols[crashReportExceptionMethod] ?: return null
        return try {
            val owner = ScanReflection.safeFindClass(CrashReportSymbolScanner.HANDLER_CLASS, cl) ?: return null
            if (!Thread.UncaughtExceptionHandler::class.java.isAssignableFrom(owner)) return null
            owner.declaredMethods.singleOrNull { it.name == name && CrashReportSymbolScanner.isReportMethod(it) }
                ?.apply { isAccessible = true }
        } catch (failure: Throwable) {
            Diagnostics.log("[CrashReportBlockHook] cached exception report method invalid: " + failure.message)
            null
        }
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        symbols[crashReportExceptionMethod] == null || resolveExceptionReportMethod(cl, symbols) != null

    // This always-on SDK guard has no user configuration key.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        addOptional(
            "CrashReportBlockHook.Exception",
            CrashReportSymbolScanner.HANDLER_CLASS + "." + symbols[crashReportExceptionMethod],
            listOf(crashReportExceptionMethod.check(symbols)),
        )
    }.build()
}
