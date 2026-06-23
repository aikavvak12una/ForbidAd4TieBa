package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitBridgeProvider
import com.forbidad4tieba.hook.symbol.model.ScanCandidateSet
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher

internal object ScanCandidateCollector {
    private const val TAG = "ScanCandidateCollector"
    private val TARGET_SCAN_PACKAGE_PREFIXES = listOf(
        "com.baidu.tieba",
        "com.baidu.tbadk",
        "com.baidu.adp.widget.ListView",
        "com.baidu.searchbox.task.view.mainactivity",
    )

    private val EXPANDED_SCAN_PACKAGE_PREFIXES = listOf(
        "com.baidu.tieba.ad.",
        "com.baidu.tieba.browser.",
        "com.baidu.tieba.card.",
        "com.baidu.tieba.feed.",
        "com.baidu.tieba.feedlog.",
        "com.baidu.tieba.forum.",
        "com.baidu.tieba.funad.",
        "com.baidu.tieba.home.",
        "com.baidu.tieba.homepage.",
        "com.baidu.tieba.immessagecenter.",
        "com.baidu.tieba.myCollection.",
        "com.baidu.tieba.pb.",
        "com.baidu.tieba.sharesdk.",
        "com.baidu.tbadk.core.atomData.",
        "com.baidu.tbadk.core.sharedPref.",
        "com.baidu.tbadk.core.util.",
        "com.baidu.tbadk.core.view.",
        "com.baidu.tbadk.coreExtra.",
        "com.baidu.tbadk.data.",
        "com.baidu.tbadk.editortools.",
        "com.baidu.tbadk.mainTab.",
        "com.baidu.tbadk.widget.",
        "com.baidu.adp.widget.ListView.",
        "com.baidu.searchbox.task.view.mainactivity.",
    )

    private val EXPANDED_SCAN_CLASS_MARKERS = listOf(
        "Adapter",
        "Config",
        "Controller",
        "Data",
        "Delegate",
        "Helper",
        "Kt",
        "Manager",
        "Parser",
        "Presenter",
        "Share",
        "State",
        "Util",
        "View",
        "ViewModel",
    )

    fun collect(context: Context, cl: ClassLoader, logger: ScanLogger?): ScanCandidateSet {
        val obfuscated = ArrayList<String>(512)
        val expanded = ArrayList<String>(1024)

        fun collectClassName(name: String) {
            if (!isTargetAppClassName(name)) return
            val shortName = name.substringAfterLast('.')
            if (isLikelyObfuscatedShortName(shortName)) {
                obfuscated.add(name)
            }
            if (isExpandedScanClassName(name)) {
                expanded.add(name)
            }
        }

        val sourcePaths = appSourcePaths(context)
        val bridge = DexKitBridgeProvider.openFirstAvailable(sourcePaths, logger)
        if (bridge == null) {
            log(logger, "list classes by DexKit failed: apk source path unavailable or bridge open failed")
            return buildResult(obfuscated, expanded, logger)
        }
        bridge.use { scanBridge ->
            for (packagePrefix in TARGET_SCAN_PACKAGE_PREFIXES) {
                try {
                    val classes = scanBridge.bridge.findClass(
                        FindClass.create()
                            .searchPackages(packagePrefix)
                            .matcher(
                                ClassMatcher.create().className(
                                    packagePrefix,
                                    StringMatchType.StartsWith,
                                ),
                            ),
                    )
                    classes.forEach { classData ->
                        collectClassName(classData.name)
                    }
                } catch (t: Throwable) {
                    HookSymbolScanDiagnostics.recordScanIssue(
                        logger = logger,
                        tag = "$TAG.$packagePrefix",
                        errors = HookSymbolScanSession.get()?.scanErrors ?: mutableListOf(),
                        detail = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                            HookSymbolScanDiagnostics.formatScanException(t),
                        ),
                    )
                }
            }
        }
        return buildResult(obfuscated, expanded, logger)
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct().filter { it.isNotBlank() }
    }

    private fun buildResult(
        obfuscated: List<String>,
        expanded: List<String>,
        logger: ScanLogger?,
    ): ScanCandidateSet {
        val result = ScanCandidateSet(
            obfuscated = obfuscated.distinct(),
            expanded = expanded.distinct(),
        )
        log(logger, "obfuscated root classes listed: ${result.obfuscated.size}")
        log(logger, "expanded scan classes listed: ${result.expanded.size}")
        return result
    }

    private fun isTargetAppClassName(name: String): Boolean {
        return name.startsWith("com.baidu.tieba.") ||
            name.startsWith("com.baidu.tbadk.") ||
            name.startsWith("com.baidu.adp.widget.ListView.") ||
            name.startsWith("com.baidu.searchbox.task.view.mainactivity.")
    }

    private fun isExpandedScanClassName(name: String): Boolean {
        val shortName = name.substringAfterLast('.')
        if (shortName == "BuildConfig" || shortName == "R" || shortName.startsWith("R\$")) return false
        if (EXPANDED_SCAN_PACKAGE_PREFIXES.any { name.startsWith(it) }) return true
        return EXPANDED_SCAN_CLASS_MARKERS.any { shortName.contains(it) }
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
