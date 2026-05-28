package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object AutoRefreshSymbolScanner {
    private const val PERSONALIZE_PAGE_VIEW_CLASS =
        "com.baidu.tieba.homepage.personalize.PersonalizePageView"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): String? {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
            log(logger, "autoRefresh: class not found: $PERSONALIZE_PAGE_VIEW_CLASS")
            return null
        }
        val dexMatch = scanFromDex(context, cl, logger)
        if (dexMatch != null) return dexMatch

        if (appSourcePaths(context).isNotEmpty()) {
            logDiagnostics(context, cl, logger)
            return null
        }

        val ruleMatch = runCatching { AutoRefreshTriggerRule().match(targetClass, cl) }.getOrNull()
        if (ruleMatch != null) {
            log(
                logger,
                "autoRefresh matched by AutoRefreshTriggerRule fallback: " +
                    "${ruleMatch.className}.${ruleMatch.methodName} score=${ruleMatch.score}",
            )
            return ruleMatch.methodName
        }
        logDiagnostics(context, cl, logger)
        return null
    }

    private fun scanFromDex(context: Context, cl: ClassLoader, logger: ScanLogger?): String? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "autoRefreshDex: apk source path unavailable")
            return null
        }

        val matches = DexShareIconScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isMethodNameValid(it.ownerMethodName, cl) }
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
            "autoRefreshDex matched: $PERSONALIZE_PAGE_VIEW_CLASS.${best.ownerMethodName} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return best.ownerMethodName
    }

    private fun isMethodNameValid(methodName: String, cl: ClassLoader): Boolean {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: return false
        return targetClass.declaredMethods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }
    }

    private fun logDiagnostics(context: Context, cl: ClassLoader, logger: ScanLogger?) {
        val targetClass = safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
            log(logger, "autoRefresh diag: class not found: $PERSONALIZE_PAGE_VIEW_CLASS")
            return
        }
        val methods = targetClass.declaredMethods
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
        val candidates = DexShareIconScanner.scanAutoRefresh(
            sourcePaths = sourcePaths,
            ownerClassName = PERSONALIZE_PAGE_VIEW_CLASS,
            logger = logger,
        )
            .filter { isMethodNameValid(it.ownerMethodName, cl) }
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
        HookSymbolResolver.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
