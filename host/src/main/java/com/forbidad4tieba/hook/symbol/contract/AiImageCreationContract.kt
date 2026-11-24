package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.AiImageCreationScanSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageCreationSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageField
import com.forbidad4tieba.hook.symbol.model.AiImageMethod
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.AiImageCreationSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns every AI-image target, its exact cache descriptor and the independent capability. */
object AiImageCreationContract : SymbolContract("AiImageCreation") {
    internal val methodDescriptors = AiImageMethod.entries.associateWith { text("aiImageCreation.${it.name}") }
    internal val fieldDescriptors = AiImageField.entries.associateWith { text("aiImageCreation.${it.name}") }
    private val required = SymbolDependencies(*(methodDescriptors.values + fieldDescriptors.values).toTypedArray())

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val result = runScanStep("AiImageCreation", logger, scanErrors, AiImageCreationScanSymbols()) {
            AiImageCreationSymbolScanner.scan(cl, logger)
        }
        methodDescriptors.forEach { (role, key) -> output[key] = result.methods[role]?.let(AiImageCreationCodec::encode) }
        fieldDescriptors.forEach { (role, key) -> output[key] = result.fields[role]?.let(AiImageCreationCodec::encode) }
    }

    fun resolveAiImageCreationSymbols(cl: ClassLoader, symbols: HookSymbols?): AiImageCreationSymbols? {
        if (symbols == null || required.missing(symbols).isNotEmpty()) {
            Diagnostics.log("[AiImageCreation] skipped: incomplete AI image descriptors")
            return null
        }
        return try {
            val methods = methodDescriptors.mapValues { (role, key) ->
                AiImageCreationCodec.restore(role, requireNotNull(symbols[key]), cl)
            }
            val fields = fieldDescriptors.mapValues { (role, key) ->
                AiImageCreationCodec.restore(role, requireNotNull(symbols[key]), cl)
            }
            AiImageCreationCodec.validateRelations(methods, fields)
            AiImageCreationSymbols(methods, fields)
        } catch (failure: Throwable) {
            Diagnostics.log("[AiImageCreation] invalid descriptors: ${failure.message}")
            Diagnostics.log(failure)
            null
        }
    }

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = try {
        // Partial negative discovery is valid; present descriptors still undergo exact restoration.
        val methods = methodDescriptors.mapNotNull { (role, key) ->
            symbols[key]?.let { role to AiImageCreationCodec.restore(role, it, cl) }
        }.toMap()
        val fields = fieldDescriptors.mapNotNull { (role, key) ->
            symbols[key]?.let { role to AiImageCreationCodec.restore(role, it, cl) }
        }.toMap()
        AiImageCreationCodec.validateRelations(methods, fields)
        true
    } catch (failure: Throwable) {
        Diagnostics.log("[AiImageCreation] cache restoration failed: ${failure.message}")
        false
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.DISABLE_AI_IMAGE_CREATION to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        methodDescriptors.forEach { (role, key) -> add("AiImageCreation.$role", symbols[key] ?: "-", listOf(key.check(symbols))) }
        fieldDescriptors.forEach { (role, key) -> add("AiImageCreation.$role", symbols[key] ?: "-", listOf(key.check(symbols))) }
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AiImageCreation.", true, listOf(HookFeatureKey.DISABLE_AI_IMAGE_CREATION)),
    )
}
