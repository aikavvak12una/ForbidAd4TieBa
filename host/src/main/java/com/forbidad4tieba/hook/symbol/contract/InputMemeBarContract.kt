package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.InputMemeBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.InputMemeBarSymbols
import com.forbidad4tieba.hook.symbol.scan.InputMemeBarSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object InputMemeBarContract : SymbolContract("InputMemeBar") {
    val inputMemeBarControllerClass = text("inputMemeBarControllerClass")
    val inputMemeBarEnableMethod = text("inputMemeBarEnableMethod")

    internal val required = SymbolDependencies(inputMemeBarControllerClass, inputMemeBarEnableMethod)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val inputMemeBarScan = runScanStep(
            "InputMemeBarBlockHook",
            logger,
            scanErrors,
            InputMemeBarScanSymbols(),
        ) {
            InputMemeBarSymbolScanner.scan(cl, logger)
        }

        output[InputMemeBarContract.inputMemeBarControllerClass] = inputMemeBarScan.controllerClass
        output[InputMemeBarContract.inputMemeBarEnableMethod] = inputMemeBarScan.enableMethod
    }

    fun resolveInputMemeBarSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): InputMemeBarSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[InputMemeBarBlockHook] skipped: scan symbols unavailable")
                return null
            }
            val controllerClassName =
                resolvedSymbols[InputMemeBarContract.inputMemeBarControllerClass]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[InputMemeBarBlockHook] skipped: missing inputMemeBarControllerClass")
                    return null
                }
            val enableMethodName =
                resolvedSymbols[InputMemeBarContract.inputMemeBarEnableMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[InputMemeBarBlockHook] skipped: missing inputMemeBarEnableMethod")
                    return null
                }
            val controllerClass = ScanReflection.safeFindClass(controllerClassName, cl) ?: run {
                Diagnostics.log("[InputMemeBarBlockHook] skipped: class not found: $controllerClassName")
                return null
            }
            val enableMethod = controllerClass.declaredMethods.singleOrNull { method ->
                method.name == enableMethodName &&
                    InputMemeBarSymbolScanner.isInputMemeBarEnableMethod(method)
            } ?: run {
                Diagnostics.log(
                    "[InputMemeBarBlockHook] skipped: method mismatch: " +
                        "$controllerClassName.$enableMethodName(Context,InputShowType,boolean)",
                )
                return null
            }
            enableMethod.isAccessible = true
            InputMemeBarSymbols(enableMethod = enableMethod)
        } catch (t: Throwable) {
            Diagnostics.log("[InputMemeBarBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.HIDE_INPUT_MEME_BAR to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "InputMemeBarBlockHook",
            "${symbols[InputMemeBarContract.inputMemeBarControllerClass]}.${symbols[InputMemeBarContract.inputMemeBarEnableMethod]}" +
                "(Context,InputShowType,boolean)",
            required.checks(symbols),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("InputMemeBarBlockHook", false, listOf(HookFeatureKey.HIDE_INPUT_MEME_BAR)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        !required.anyPresent(symbols) || resolveInputMemeBarSymbols(cl, symbols) != null

}
