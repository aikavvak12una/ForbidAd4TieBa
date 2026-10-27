package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.VideoDisableScan
import com.forbidad4tieba.hook.symbol.model.VideoDisableCache
import com.forbidad4tieba.hook.symbol.model.VideoDisableSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.symbol.scan.VideoDisableSymbolScanner
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Playback and background producers form one required installation group. */
object VideoDisableContract : SymbolContract("VideoDisable") {
    val methods = nested("videoDisableMethods", VideoDisableCache(), VideoDisableCache::fromJson, VideoDisableCache::toJson)
    val pcdnStartMethod = text("videoDisablePcdnStart")
    val playDispatchMethod = text("videoDisablePlayDispatch")
    val emptyKernelConstructor = text("videoDisableEmptyKernelConstructor")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val found = runScanStep("VideoDisable", logger, scanErrors, VideoDisableScan(VideoDisableCache(), null, null, null)) {
            VideoDisableSymbolScanner.scan(context, cl, logger)
        }
        output[methods] = found.methods
        output[pcdnStartMethod] = found.pcdnStart
        output[playDispatchMethod] = found.playDispatch
        output[emptyKernelConstructor] = found.emptyKernelConstructor
    }

    fun resolve(cl: ClassLoader, symbols: HookSymbols?): VideoDisableSymbols? = symbols?.let {
        val adTargets = PerformanceContract.resolveRuntimeSymbols(cl, it)
        if (adTargets.keys != PerformanceRuntimeTarget.entries.toSet()) return@let null
        VideoDisableSymbolScanner.restore(cl, it[methods], it[pcdnStartMethod], it[playDispatchMethod], adTargets, it[emptyKernelConstructor])
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val adStatus = PerformanceContract.capabilities(symbols).getValue(HookFeatureKey.DISABLE_AD_SDK_COMPONENTS)
        val missing = points(symbols).filter { it.isUnavailable() }.map { it.name } +
            adStatus.missingCritical + adStatus.missingOptional
        return mapOf(HookFeatureKey.DISABLE_VIDEO_COMPONENTS to HookFeatureStatus(
            if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.DISABLED,
            missingCritical = missing,
        ))
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> {
        val cache = symbols[methods]
        val stable = VideoDisableTarget.entries.map { target ->
            val family = cache.families[target.name]
            val found = family?.hasValidMetadata(target) == true
            HookPointStatus("VideoDisable.${target.name}", if (found) HookPointState.FOUND else HookPointState.MISSING,
                if (found) emptyList() else listOf("methodOrConsumer"),
                family?.methods?.joinToString().takeUnless { it.isNullOrBlank() }
                    ?: "${target.className}.${target.methodName}(${target.parameterTypeNames.joinToString()})")
        }
        val pcdn = symbols[pcdnStartMethod]
        val result = stable + HookPointStatus("VideoDisable.PCDN_START", if (pcdn.isNullOrBlank()) HookPointState.MISSING else HookPointState.FOUND,
            if (pcdn.isNullOrBlank()) listOf("pcdnStart") else emptyList(), pcdn ?: "pcdn configuration service-start entry") +
            HookPointStatus("VideoDisable.PLAY_DISPATCH", if (symbols[playDispatchMethod].isNullOrBlank()) HookPointState.MISSING else HookPointState.FOUND,
                if (symbols[playDispatchMethod].isNullOrBlank()) listOf("playDispatch") else emptyList(), symbols[playDispatchMethod] ?: "video playback WakeLock dispatch") +
            HookPointStatus("VideoDisable.EMPTY_KERNEL_CONSTRUCTOR",
                if (symbols[emptyKernelConstructor] == VideoDisableSymbolScanner.EMPTY_KERNEL_CONSTRUCTOR) HookPointState.FOUND else HookPointState.MISSING,
                if (symbols[emptyKernelConstructor] == VideoDisableSymbolScanner.EMPTY_KERNEL_CONSTRUCTOR) emptyList() else listOf("emptyKernelConstructor"),
                VideoDisableSymbolScanner.EMPTY_KERNEL_CONSTRUCTOR)
        return if (cache.hasValidMetadata()) result else result +
            HookPointStatus("VideoDisable.TargetSet", HookPointState.ERROR, listOf("unknownOrDuplicateTargets"), "invalid cached video target set")
    }

    internal override val pointOwners = listOf(PointOwner("VideoDisable.", true, listOf(HookFeatureKey.DISABLE_VIDEO_COMPONENTS)))

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        VideoDisableSymbolScanner.isCacheValid(cl, symbols[methods], symbols[pcdnStartMethod], symbols[playDispatchMethod], symbols[emptyKernelConstructor])
}
