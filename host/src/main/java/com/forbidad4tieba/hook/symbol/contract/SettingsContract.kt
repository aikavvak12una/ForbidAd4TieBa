package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.widget.RelativeLayout
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.SettingsScanSymbols
import com.forbidad4tieba.hook.symbol.model.SettingsMenuSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.scan.SettingsSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

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
        return runCatching { restoreSettings(symbols, cl) }.isSuccess
    }

    private const val NAV_CLASS = StableTiebaHookPoints.NAVIGATION_BAR_CLASS

    fun resolveSettingsMenuSymbols(cl: ClassLoader, symbols: HookSymbols): SettingsMenuSymbols? =
        try { restoreSettings(symbols, cl) } catch (failure: Throwable) {
            Diagnostics.log("[SettingsMenuHook] target restoration failed: ${failure.message}")
            null
        }

    private fun restoreSettings(symbols: HookSymbols, cl: ClassLoader): SettingsMenuSymbols {
        val ownerName = requireNotNull(symbols[settingsClass]) { "settings class missing" }
        val methodName = requireNotNull(symbols[settingsInitMethod]) { "settings init missing" }
        val fieldName = requireNotNull(symbols[settingsContainerField]) { "settings container missing" }
        checkNotNull(ScanReflection.safeFindClass(NAV_CLASS, cl)) { "navigation bar class missing" }
        val owner = checkNotNull(ScanReflection.safeFindClass(ownerName, cl)) { "settings class unavailable" }
        return restoreEntry(owner, methodName, fieldName)
    }

    /** The scanner's semantic rule selects exactly an instance void(View) initializer. */
    internal fun restoreEntry(owner: Class<*>, methodName: String, fieldName: String): SettingsMenuSymbols {
        val method = owner.getDeclaredMethod(methodName, View::class.java)
        check(!Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE) { "invalid settings init" }
        val field = owner.getDeclaredField(fieldName)
        check(!Modifier.isStatic(field.modifiers) && field.type == RelativeLayout::class.java) { "invalid settings container" }
        method.isAccessible = true
        field.isAccessible = true
        return SettingsMenuSymbols(method, field)
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
