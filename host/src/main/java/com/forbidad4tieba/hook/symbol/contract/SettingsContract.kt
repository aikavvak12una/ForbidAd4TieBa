package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.SettingsScanSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.scan.SettingsSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object SettingsContract : SymbolContract("Settings") {
    val settingsClass = text("settingsClass")
    val settingsInitMethod = text("settingsInitMethod")
    val settingsContainerField = text("settingsContainerField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val settingsScan = runScanStep(
            "SettingsMenuHook",
            logger,
            scanErrors,
            SettingsScanSymbols(),
        ) {
            SettingsSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        output[SettingsContract.settingsClass] = settingsScan.settingsClass
        output[SettingsContract.settingsInitMethod] = settingsScan.initMethod
        output[SettingsContract.settingsContainerField] = settingsScan.containerField
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "SettingsMenuHook",
            "${symbols[SettingsContract.settingsClass]}.${symbols[SettingsContract.settingsInitMethod]}[${symbols[SettingsContract.settingsContainerField]}]",
            listOf(
                SettingsContract.settingsClass.check(symbols),
                SettingsContract.settingsInitMethod.check(symbols),
                SettingsContract.settingsContainerField.check(symbols),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!isSettingsValid(symbols, cl)) return false
        return true
    }

    private const val NAV_CLASS = StableTiebaHookPoints.NAVIGATION_BAR_CLASS

    private fun isSettingsValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val settingsClassName = symbols[SettingsContract.settingsClass] ?: return false
        val settingsInitMethod = symbols[SettingsContract.settingsInitMethod] ?: return false
        val settingsContainerField = symbols[SettingsContract.settingsContainerField] ?: return false
        return try {
            val settingsClass = ScanReflection.safeFindClass(settingsClassName, cl) ?: return false
            val navClass = ScanReflection.safeFindClass(NAV_CLASS, cl) ?: return false
            val methodOk = settingsClass.declaredMethods.any { method ->
                if (method.name != settingsInitMethod) return@any false
                if (method.returnType != Void.TYPE) return@any false
                val p = method.parameterTypes
                (p.size == 2 && Context::class.java.isAssignableFrom(p[0]) && navClass.isAssignableFrom(p[1])) ||
                    (p.size == 1 && View::class.java.isAssignableFrom(p[0]))
            }
            if (!methodOk) return false
            settingsClass.declaredFields.any { it.name == settingsContainerField }
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
