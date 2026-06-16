package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.SettingsScanSymbols

internal object SettingsSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): SettingsScanSymbols {
        val navClassName = StableTiebaHookPoints.NAVIGATION_BAR_CLASS
        val navClass = ScanReflection.safeFindClass(navClassName, cl)
        if (navClass == null) {
            recordScanIssue(logger, "SettingsMenuHook", "nav class not found: $navClassName")
            return SettingsScanSymbols()
        }

        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(SettingsLevel1Rule(navClass)),
            logger,
            "settings",
        ) ?: return SettingsScanSymbols()

        return SettingsScanSymbols(
            settingsClass = match.className,
            initMethod = match.methodName,
            containerField = match.fieldName,
        )
    }

    private fun recordScanIssue(logger: ScanLogger?, tag: String, detail: String) {
        HookSymbolScanSession.get()?.scanErrors?.let { errors ->
            HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$tag scan issue: $detail")
    }
}
