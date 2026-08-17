package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.HomeBottomEasterEggAdScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import java.lang.reflect.Modifier

internal object HomeBottomEasterEggAdSymbolScanner {
    fun scan(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): HomeBottomEasterEggAdScanSymbols {
        val dexResult = DexKitSemanticScanner.scanHomeBottomEasterEggParser(
            appSourcePaths(context),
            logger,
        )
        val className = dexResult.parserClass ?: return HomeBottomEasterEggAdScanSymbols()
        val methodName = dexResult.parserMethod ?: return HomeBottomEasterEggAdScanSymbols()
        val clazz = ScanReflection.safeFindClass(className, cl) ?: run {
            log(logger, "parser class unavailable: $className")
            return HomeBottomEasterEggAdScanSymbols()
        }
        val matches = scanSubStep(
            "HomeBottomEasterEggAdHook.ParserReflection",
            logger,
            emptyList(),
        ) {
            clazz.declaredMethods.filter { method ->
                method.name == methodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(JSONObject::class.java))
            }
        }
        if (matches.size != 1) {
            log(logger, "parser reflection validation failed: $className#$methodName matches=${matches.size}")
            return HomeBottomEasterEggAdScanSymbols()
        }
        return dexResult
    }

    private fun appSourcePaths(context: Context): List<String> = buildList {
        context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
        context.applicationInfo?.splitSourceDirs?.forEach { path ->
            if (!path.isNullOrBlank()) add(path)
        }
    }.distinct()

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, "HomeBottomEasterEggAdHook: $line")
    }
}
