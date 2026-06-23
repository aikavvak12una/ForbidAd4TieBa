package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import android.content.Context
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object AutoRefreshSymbolScanner {
    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): String? {
        val dexMatch = scanFromDex(context, cl, logger)
        if (dexMatch != null) return dexMatch

        logDiagnostics(context, cl, logger)
        return null
    }

    fun scanLoadMoreConfig(cl: ClassLoader, logger: ScanLogger?): AutoLoadMoreConfigScanSymbols {
        val match = ScanReflection.runRules(
            listOf(StableTiebaHookPoints.HOME_PRELOAD_CONFIG_COMPANION_CLASS),
            cl,
            listOf(AutoLoadMoreConfigRule(StableTiebaHookPoints.HOME_PRELOAD_CONFIG_PARSER_CLASS)),
            logger,
            "autoLoadMore",
        ) ?: return AutoLoadMoreConfigScanSymbols()
        return AutoLoadMoreConfigScanSymbols(
            configClass = match.className,
            configMethod = match.methodName,
        )
    }

    private fun scanFromDex(context: Context, cl: ClassLoader, logger: ScanLogger?): String? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "autoRefreshDex: apk source path unavailable")
            return null
        }

        val matches = DexKitSemanticScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isMethodNameValid(it.ownerMethodName, cl, logger) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAutoRefreshMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )

        val best = matches.firstOrNull() ?: run {
            log(logger, "autoRefreshDex: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 8) {
            log(
                logger,
                "autoRefreshDex ambiguous: best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }

        log(
            logger,
            "autoRefreshDex matched: ${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${best.ownerMethodName} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return best.ownerMethodName
    }

    private fun isMethodNameValid(methodName: String, cl: ClassLoader, logger: ScanLogger?): Boolean {
        val targetClass = safeFindClass(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: return false
        val methods = declaredMethods("HomePersonalizePageView.Validate", targetClass, logger) ?: return false
        return methods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
    }

    private fun logDiagnostics(context: Context, cl: ClassLoader, logger: ScanLogger?) {
        val targetClass = safeFindClass(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
            log(logger, "autoRefresh diag: class not found: ${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}")
            return
        }
        val methods = declaredMethods("HomePersonalizePageView.Diagnostics", targetClass, logger)
            .orEmpty()
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
            .sortedWith(compareBy<Method>({ it.name.length }, { it.name }))
            .take(40)
            .joinToString("; ") { describeMethodShape(it) }
        log(logger, "autoRefresh diag: voidNoArgMethods=$methods")

        val sourcePaths = appSourcePaths(context)
        val candidates = DexKitSemanticScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isMethodNameValid(it.ownerMethodName, cl, logger) }
            .sortedWith(compareByDescending<DexAutoRefreshMatch> { it.score }.thenBy { it.ownerMethodName })
            .take(8)
            .joinToString(" || ") { "${it.ownerMethodName}:${it.score}[${it.evidence}]" }
        if (candidates.isNotBlank()) {
            log(logger, "autoRefresh diag candidates: $candidates")
        }
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("AutoRefreshHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
