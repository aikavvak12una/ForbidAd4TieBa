package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.CloudDialogSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the complete admission/result/data/time group; discovery failure is isolated to this feature. */
object CloudDialogContract : SymbolContract("CloudDialog") {
    internal val targetsDescriptor = text("cloudDialogTargets")
    private val required = SymbolDependencies(targetsDescriptor)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        output[targetsDescriptor] = runScanStep("CloudDialog", logger, scanErrors, null as String?) {
            CloudDialogSymbolScanner.scan(cl, logger)?.let(CloudDialogCodec::encode)
        }
    }

    fun resolveCloudDialogSymbols(cl: ClassLoader, symbols: HookSymbols?): CloudDialogSymbols? {
        val descriptor = symbols?.get(targetsDescriptor)
        if (descriptor == null) {
            Diagnostics.log("[CloudDialog] skipped: incomplete admission group")
            return null
        }
        return try {
            CloudDialogCodec.restore(descriptor, cl).targets
        } catch (failure: Throwable) {
            Diagnostics.log("[CloudDialog] invalid descriptors: ${failure.message}")
            Diagnostics.log(failure)
            null
        }
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val descriptor = symbols[targetsDescriptor] ?: return true // Complete negative discovery stays cached.
        return try {
            CloudDialogCodec.restore(descriptor, cl)
            true
        } catch (failure: Throwable) {
            Diagnostics.log("[CloudDialog] cache restoration failed: ${failure.message}")
            false
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add("CloudDialog.Admission", symbols[targetsDescriptor] ?: "-", required.checks(symbols))
    }.build()

    internal override val pointOwners = listOf(PointOwner("CloudDialog.", true, listOf(HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS)))
    internal override val candidateClasses = listOf(CloudDialogCodec.MANAGER, CloudDialogCodec.DATA, CloudDialogCodec.STRATEGY)
}
