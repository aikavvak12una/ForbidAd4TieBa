package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigSymbols
import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigTarget
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

internal object LowEndConfigSymbolScanner {
    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): LowEndConfigSymbols {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
            context.applicationInfo?.splitSourceDirs.orEmpty()
        return HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
            val scanned = scan(source.bridge, logger)
            val restored = scanned.restore(cl)
            LowEndConfigSymbols(scanned.methods.filterKeys { it in restored })
        } ?: LowEndConfigSymbols()
    }

    fun scan(bridge: DexKitBridge, logger: ScanLogger?): LowEndConfigSymbols = LowEndConfigSymbols(
        LowEndConfigTarget.entries.mapNotNull { target ->
            scanSubStep("HostPerformanceConfigHook.${target.name}", logger, null) {
                val candidates = bridge.getClassData(target.owner)?.methods.orEmpty().filter { method ->
                    Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers) &&
                        method.returnTypeName == target.returnType && method.paramTypeNames == target.parameters &&
                        method.invokes.any { it.declaredClassName == target.callOwner && it.methodName == target.callName }
                }.distinctBy { it.descriptor }
                val selected = selectUniqueScoredCandidate(
                    "HostPerformanceConfigHook.${target.name}", candidates, 1, logger,
                    { 1 }, { it.descriptor },
                ) ?: return@scanSubStep null
                target to selected.methodName
            }
        }.toMap(),
    )
}
