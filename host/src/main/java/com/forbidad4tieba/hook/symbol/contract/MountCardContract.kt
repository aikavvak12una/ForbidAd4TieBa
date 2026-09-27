package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.MountCardLinkLayoutScanSymbols
import com.forbidad4tieba.hook.symbol.model.MountCardLinkLayoutSymbols
import com.forbidad4tieba.hook.symbol.scan.MountCardLinkSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object MountCardContract : SymbolContract("MountCard") {
    val mountCardLinkLayoutClass = text("mountCardLinkLayoutClass")
    val mountCardLinkLayoutOnClickMethod = text("mountCardLinkLayoutOnClickMethod")
    val mountCardLinkLayoutDataField = text("mountCardLinkLayoutDataField")
    val mountCardLinkInfoDataClass = text("mountCardLinkInfoDataClass")
    val mountCardLinkInfoGetUrlMethod = text("mountCardLinkInfoGetUrlMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val mountCardLinkScan = runScanStep(
            "PlainUrlDirectBrowserHook.MountCard",
            logger,
            scanErrors,
            MountCardLinkLayoutScanSymbols(),
        ) {
            MountCardLinkSymbolScanner.scan(cl, logger)
        }

        val mountCardLinkLayoutClass: String? = mountCardLinkScan.layoutClass

        val mountCardLinkLayoutOnClickMethod: String? = mountCardLinkScan.onClickMethod

        val mountCardLinkLayoutDataField: String? = mountCardLinkScan.dataField

        val mountCardLinkInfoDataClass: String? = mountCardLinkScan.dataClass

        val mountCardLinkInfoGetUrlMethod: String? = mountCardLinkScan.getUrlMethod

        output[MountCardContract.mountCardLinkLayoutClass] = mountCardLinkLayoutClass
        output[MountCardContract.mountCardLinkLayoutOnClickMethod] = mountCardLinkLayoutOnClickMethod
        output[MountCardContract.mountCardLinkLayoutDataField] = mountCardLinkLayoutDataField
        output[MountCardContract.mountCardLinkInfoDataClass] = mountCardLinkInfoDataClass
        output[MountCardContract.mountCardLinkInfoGetUrlMethod] = mountCardLinkInfoGetUrlMethod
    }

    fun resolveMountCardLinkLayoutSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): MountCardLinkLayoutSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: scan symbols unavailable")
                return null
            }
            val layoutClassName = resolvedSymbols[MountCardContract.mountCardLinkLayoutClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: missing layout class")
                return null
            }
            val onClickMethodName = resolvedSymbols[MountCardContract.mountCardLinkLayoutOnClickMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: missing onClick method")
                return null
            }
            val dataFieldName = resolvedSymbols[MountCardContract.mountCardLinkLayoutDataField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: missing data field")
                return null
            }
            val dataClassName = resolvedSymbols[MountCardContract.mountCardLinkInfoDataClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: missing data class")
                return null
            }
            val getUrlMethodName = resolvedSymbols[MountCardContract.mountCardLinkInfoGetUrlMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: missing getUrl method")
                return null
            }

            val layoutClass = ScanReflection.safeFindClass(layoutClassName, cl) ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: layout class not found: $layoutClassName")
                return null
            }
            val dataClass = ScanReflection.safeFindClass(dataClassName, cl) ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: data class not found: $dataClassName")
                return null
            }
            val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
                ScanReflection.isMountCardLinkLayoutOnClickMethod(method, onClickMethodName)
            } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: onClick mismatch: $layoutClassName.$onClickMethodName")
                return null
            }
            val dataField = ScanReflection.resolveMountCardLinkLayoutDataField(layoutClass, dataClass)
                ?.takeIf { it.name == dataFieldName } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: data field mismatch: $layoutClassName.$dataFieldName")
                return null
            }
            val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
                method.name == getUrlMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: getUrl mismatch: $dataClassName.$getUrlMethodName")
                return null
            }
            if (!ScanReflection.isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)) {
                Diagnostics.log("[PlainUrlDirectBrowserHook] mount card skipped: structure mismatch: $layoutClassName")
                return null
            }

            onClickMethod.isAccessible = true
            dataField.isAccessible = true
            getUrlMethod.isAccessible = true
            MountCardLinkLayoutSymbols(
                layoutClass = layoutClass,
                onClickMethod = onClickMethod,
                dataField = dataField,
                getUrlMethod = getUrlMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PlainUrlDirectBrowserHook] mount card symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PlainUrlDirectBrowserHook.MountCard",
            "${symbols[MountCardContract.mountCardLinkLayoutClass]}.${symbols[MountCardContract.mountCardLinkLayoutOnClickMethod]}(View)[${symbols[MountCardContract.mountCardLinkLayoutDataField]}->${symbols[MountCardContract.mountCardLinkInfoDataClass]}.${symbols[MountCardContract.mountCardLinkInfoGetUrlMethod]}]",
            listOf(
                MountCardContract.mountCardLinkLayoutClass.check(symbols),
                MountCardContract.mountCardLinkLayoutOnClickMethod.check(symbols),
                MountCardContract.mountCardLinkLayoutDataField.check(symbols),
                MountCardContract.mountCardLinkInfoDataClass.check(symbols),
                MountCardContract.mountCardLinkInfoGetUrlMethod.check(symbols),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasMountCardLinkSymbols =
            symbols[MountCardContract.mountCardLinkLayoutClass] != null ||
                symbols[MountCardContract.mountCardLinkLayoutOnClickMethod] != null ||
                symbols[MountCardContract.mountCardLinkLayoutDataField] != null ||
                symbols[MountCardContract.mountCardLinkInfoDataClass] != null ||
                symbols[MountCardContract.mountCardLinkInfoGetUrlMethod] != null
        if (hasMountCardLinkSymbols && !isMountCardLinkLayoutValid(symbols, cl)) return false
        return true
    }

    private fun isMountCardLinkLayoutValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val layoutClassName = symbols[MountCardContract.mountCardLinkLayoutClass] ?: return false
        val onClickMethodName = symbols[MountCardContract.mountCardLinkLayoutOnClickMethod] ?: return false
        val dataFieldName = symbols[MountCardContract.mountCardLinkLayoutDataField] ?: return false
        val dataClassName = symbols[MountCardContract.mountCardLinkInfoDataClass] ?: return false
        val getUrlMethodName = symbols[MountCardContract.mountCardLinkInfoGetUrlMethod] ?: return false
        return try {
            val layoutClass = ScanReflection.safeFindClass(layoutClassName, cl) ?: return false
            val dataClass = ScanReflection.safeFindClass(dataClassName, cl) ?: return false
            val onClickMethod = layoutClass.declaredMethods.singleOrNull { method ->
                ScanReflection.isMountCardLinkLayoutOnClickMethod(method, onClickMethodName)
            } ?: return false
            val dataField = ScanReflection.resolveMountCardLinkLayoutDataField(layoutClass, dataClass)
                ?.takeIf { it.name == dataFieldName } ?: return false
            val getUrlMethod = dataClass.declaredMethods.singleOrNull { method ->
                method.name == getUrlMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            } ?: return false
            ScanReflection.isMountCardLinkLayoutStructureValid(layoutClass, onClickMethod, dataClass, dataField, getUrlMethod)
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
