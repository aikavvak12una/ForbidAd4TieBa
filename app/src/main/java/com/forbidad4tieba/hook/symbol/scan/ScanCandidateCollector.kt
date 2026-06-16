package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanCandidateSet
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexFile

internal object ScanCandidateCollector {
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

    @Suppress("DEPRECATION")
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

        try {
            var currentCl: ClassLoader? = cl
            var missingDexFileField = 0
            var firstDexFileFieldError: String? = null
            while (currentCl != null) {
                if (currentCl is BaseDexClassLoader) {
                    val pathListField = XposedCompat.findField(currentCl::class.java, "pathList")
                    val pathList = pathListField.get(currentCl)!!
                    val dexElementsField = XposedCompat.findField(pathList::class.java, "dexElements")
                    val dexElements = dexElementsField.get(pathList) as Array<*>

                    for (element in dexElements) {
                        if (element == null) continue
                        val dexFileField = try {
                            XposedCompat.findField(element::class.java, "dexFile")
                        } catch (t: Throwable) {
                            missingDexFileField++
                            if (firstDexFileFieldError == null) {
                                firstDexFileFieldError = HookSymbolScanDiagnostics.formatScanException(t)
                            }
                            null
                        } ?: continue
                        val dexFile = dexFileField.get(element) as? DexFile ?: continue
                        val entries = dexFile.entries()
                        while (entries.hasMoreElements()) {
                            collectClassName(entries.nextElement())
                        }
                    }
                }
                currentCl = currentCl.parent
            }
            if (missingDexFileField > 0) {
                log(
                    logger,
                    "list classes skipped dex elements without dexFile field=$missingDexFileField" +
                        (firstDexFileFieldError?.let { ", firstException=$it" } ?: ""),
                )
            }
        } catch (t: Throwable) {
            log(logger, "list classes from dex path list failed: ${t.message}")
            XposedCompat.log(t)
        }

        if (obfuscated.isNotEmpty() || expanded.isNotEmpty()) {
            return buildResult(obfuscated, expanded, logger)
        }

        val sourceDir = context.applicationInfo?.sourceDir ?: return ScanCandidateSet(emptyList(), emptyList())
        var dexFile: DexFile? = null
        try {
            dexFile = DexFile(sourceDir)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                collectClassName(entries.nextElement())
            }
        } catch (t: Throwable) {
            XposedCompat.log("[ScanCandidateCollector] list classes fallback failed: ${t.message}")
            log(logger, "list classes fallback failed: ${t.message}")
            XposedCompat.log(t)
        } finally {
            try {
                dexFile?.close()
            } catch (t: Throwable) {
                XposedCompat.logD("ScanCandidateCollector: ${t.message}")
            }
        }
        return buildResult(obfuscated, expanded, logger)
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
