package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreConfigScanSymbols
import com.forbidad4tieba.hook.symbol.model.DexHomeCacheRestoreMatch
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal object AutoRefreshSymbolScanner {
    fun scanLoadMoreConfig(cl: ClassLoader, logger: ScanLogger?): AutoLoadMoreConfigScanSymbols {
        val match = ScanReflection.runRules(
            listOf(StableTiebaHookPoints.HOME_PRELOAD_CONFIG_PARSER_CLASS), cl,
            listOf(AutoLoadMoreConfigRule(StableTiebaHookPoints.HOME_PRELOAD_CONFIG_PARSER_CLASS)), logger, "autoLoadMore",
        ) ?: return AutoLoadMoreConfigScanSymbols()
        return AutoLoadMoreConfigScanSymbols(configClass = match.className, configMethod = match.methodName)
    }

    fun scanHomeCacheRestore(context: Context, cl: ClassLoader, logger: ScanLogger?): DexHomeCacheRestoreMatch? {
        val sourcePaths = buildList {
            context.applicationInfo?.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path -> if (path.isNotBlank()) add(path) }
        }.distinct()
        if (sourcePaths.isEmpty()) {
            HookSymbolScanDiagnostics.log(logger, "cacheRestoreDex: apk source path unavailable")
            return null
        }
        val match = DexKitSemanticScanner.scanHomeCacheRestoreMethod(sourcePaths, logger = logger) ?: return null
        val owner = ScanReflection.safeFindClass(StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS, cl) ?: return null
        if (owner.declaredMethods.none {
                !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) && it.name == match.ownerMethodName &&
                    it.returnType == java.lang.Boolean.TYPE && it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }) {
            HookSymbolScanDiagnostics.log(logger, "cacheRestoreDex: exact instance signature mismatch")
            return null
        }
        HookSymbolScanDiagnostics.log(logger, "cacheRestoreDex matched: ${StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS}.${match.ownerMethodName} evidence=${match.evidence}")
        return match
    }
}
