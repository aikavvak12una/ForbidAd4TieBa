package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.FeedTemplateScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object FeedTemplateSymbolScanner {
    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FeedTemplateScanSymbols {
        val feedKeyMatch = ScanReflection.runRules(
            candidates,
            cl,
            listOf(FeedTemplateKeyRule()),
            logger,
            "feedKey",
        )
        val feedLoadMoreMatch = ScanReflection.runRules(
            candidates,
            cl,
            listOf(FeedTemplateLoadMoreRule()),
            logger,
            "feedLoadMore",
        )
        if (feedLoadMoreMatch == null) {
            logFeedLoadMoreDiagnostics(cl, logger)
        }
        return FeedTemplateScanSymbols(
            keyMethod = feedKeyMatch?.methodName,
            payloadMethod = feedKeyMatch?.fieldName?.takeIf { it.isNotBlank() },
            loadMoreMethod = feedLoadMoreMatch?.methodName,
        )
    }

    private fun logFeedLoadMoreDiagnostics(cl: ClassLoader, logger: ScanLogger?) {
        val adapterClassName = StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS
        val adapterClass = ScanReflection.safeFindClass(adapterClassName, cl)
        if (adapterClass == null) {
            log(logger, "feedLoadMore diag: class not found: $adapterClassName")
            logTemplateAdapterDiagnostics(cl, logger)
            return
        }

        val metadata = kotlinMetadataStrings(adapterClass, logger)
        val metadataHints = metadata
            .flatMap { text ->
                listOf("loadMore", "refreshList", "setList", "List").filter { text.contains(it) }
            }
            .distinct()
            .joinToString(",")
            .ifBlank { "-" }
        val adapterMethods = declaredMethods("FeedTemplateAdapter", adapterClass, logger).orEmpty()
        val listMethods = adapterMethods
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.any { type ->
                        List::class.java.isAssignableFrom(type) ||
                            java.util.Collection::class.java.isAssignableFrom(type)
                    }
            }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(12)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }
        val voidMethods = adapterMethods
            .filter { it.returnType == Void.TYPE && it.parameterTypes.size <= 2 }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(16)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }

        log(
            logger,
            "feedLoadMore diag: class=$adapterClassName, super=${adapterClass.superclass?.name}, " +
                "metadataHints=$metadataHints, listMethods=$listMethods, voidMethods=$voidMethods",
        )
        logTemplateAdapterDiagnostics(cl, logger)
    }

    private fun logTemplateAdapterDiagnostics(cl: ClassLoader, logger: ScanLogger?) {
        val templateClassName = StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS
        val templateClass = ScanReflection.safeFindClass(templateClassName, cl) ?: run {
            log(logger, "feedLoadMore diag: class not found: $templateClassName")
            return
        }
        val listMethods = declaredMethods("TemplateAdapter", templateClass, logger)
            .orEmpty()
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.any { type ->
                        List::class.java.isAssignableFrom(type) ||
                            java.util.Collection::class.java.isAssignableFrom(type)
                    }
            }
            .sortedWith(compareBy<Method>({ it.parameterTypes.size }, { it.name }))
            .take(12)
            .joinToString("; ") { describeMethodShape(it) }
            .ifBlank { "-" }
        log(logger, "feedLoadMore diag: class=$templateClassName, listMethods=$listMethods")
    }

    private fun kotlinMetadataStrings(targetClass: Class<*>, logger: ScanLogger?): List<String> {
        val metadata = targetClass.getAnnotation(kotlin.Metadata::class.java) ?: return emptyList()
        return (
            readMetadataStringArray(targetClass, metadata, "d1", logger) +
                readMetadataStringArray(targetClass, metadata, "d2", logger) +
                readMetadataStringArray(targetClass, metadata, "data1", logger) +
                readMetadataStringArray(targetClass, metadata, "data2", logger)
            )
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun readMetadataStringArray(
        targetClass: Class<*>,
        metadata: Annotation,
        methodName: String,
        logger: ScanLogger?,
    ): List<String> {
        val value = scanSubStep("FeedTemplateHook.${targetClass.name}.Metadata.$methodName", logger, null) {
            metadata.javaClass.getMethod(methodName).invoke(metadata)
        } as? Array<*> ?: return emptyList()
        return value.mapNotNull { it as? String }
    }

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("FeedTemplateHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
