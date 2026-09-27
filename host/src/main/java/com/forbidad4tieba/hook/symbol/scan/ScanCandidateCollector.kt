package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.ClassMatcher

/** DexKit is the only dex inventory. Class spelling never determines eligibility. */
internal object ScanCandidateCollector {
    private val packagePrefixes = listOf(
        "com.baidu.tieba", "com.baidu.tbadk", "com.baidu.adp.widget.ListView",
        "com.baidu.searchbox.task.view.mainactivity",
    )

    fun collect(context: Context, logger: ScanLogger?): List<String> {
        val names = HookSymbolScanSession.withDexKitBridge(ScanDexQueries.sourcePaths(context), logger) { scan ->
            packagePrefixes.flatMap { prefix ->
                scanSubStep("ScanCandidateCollector.$prefix", logger, emptyList()) {
                    scan.bridge.findClass(FindClass.create().searchPackages(prefix).matcher(ClassMatcher.create()))
                        .map { it.name }.filter(::isCandidateClassName)
                }
            }.distinct()
        }.orEmpty()
        HookSymbolScanDiagnostics.log(logger, "DexKit class inventory=${names.size}")
        return names
    }

    internal fun isCandidateClassName(name: String): Boolean {
        val packageName = name.substringBeforeLast('.', missingDelimiterValue = "")
        if (packagePrefixes.none { packageName == it || packageName.startsWith("$it.") }) return false
        val simple = name.substringAfterLast('.')
        return simple != "R" && !simple.startsWith("R\$") && simple != "BuildConfig"
    }
}
