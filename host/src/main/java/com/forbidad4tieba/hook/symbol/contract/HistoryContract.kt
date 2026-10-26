package com.forbidad4tieba.hook.symbol.contract

import java.lang.reflect.Modifier
import android.os.Bundle
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.RestoredMembers.resolveMethodByCachedSpec
import com.forbidad4tieba.hook.symbol.model.HistorySearchScanSymbols
import com.forbidad4tieba.hook.symbol.model.HistorySearchSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.HistorySearchSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus

/** Owns the cached descriptors and host rules for this capability. */
object HistoryContract : SymbolContract("History") {
    val historyAdapterField = text("historyAdapterField")
    val historyAdapterSetListMethod = text("historyAdapterSetListMethod")
    val historyAdapterSetListMethodSpec = text("historyAdapterSetListMethodSpec")
    val historyListField = text("historyListField")
    val historyActivityListUpdateMethod = text("historyActivityListUpdateMethod")
    val historyActivityListUpdateMethodSpec = text("historyActivityListUpdateMethodSpec")
    val historyActivityNavBarField = text("historyActivityNavBarField")
    val historyThreadNameMethod = text("historyThreadNameMethod")
    val historyForumNameMethod = text("historyForumNameMethod")
    val historyUserNameMethod = text("historyUserNameMethod")
    val historyDescriptionMethod = text("historyDescriptionMethod")
    val historyThreadIdMethod = text("historyThreadIdMethod")
    val historyPostIdMethod = text("historyPostIdMethod")
    val historyLiveIdMethod = text("historyLiveIdMethod")

    fun isSearchComplete(symbols: HookSymbols): Boolean = listOf<Any?>(
        symbols[historyAdapterField],
        symbols[historyAdapterSetListMethod],
        symbols[historyAdapterSetListMethodSpec],
        symbols[historyListField],
        symbols[historyActivityNavBarField],
        symbols[historyThreadNameMethod],
        symbols[historyForumNameMethod],
        symbols[historyUserNameMethod],
        symbols[historyDescriptionMethod],
        symbols[historyThreadIdMethod],
        symbols[historyPostIdMethod],
        symbols[historyLiveIdMethod],
    ).all { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Int -> value != 0
            else -> false
        }
    }

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val historyScan = runScanStep(
            "HistorySearchHook",
            logger,
            scanErrors,
            HistorySearchScanSymbols(),
        ) {
            HistorySearchSymbolScanner.scan(context, cl, logger)
        }

        val historyAdapterField: String? = historyScan.adapterField

        val historyAdapterSetListMethod: String? = historyScan.adapterSetListMethod

        val historyAdapterSetListMethodSpec: String? = historyScan.adapterSetListMethodSpec

        val historyListField: String? = historyScan.listField

        val historyActivityListUpdateMethod: String? = historyScan.activityListUpdateMethod

        val historyActivityListUpdateMethodSpec: String? = historyScan.activityListUpdateMethodSpec

        val historyActivityNavBarField: String? = historyScan.activityNavBarField

        val historyThreadNameMethod: String? = historyScan.threadNameMethod

        val historyForumNameMethod: String? = historyScan.forumNameMethod

        val historyUserNameMethod: String? = historyScan.userNameMethod

        val historyDescriptionMethod: String? = historyScan.descriptionMethod

        val historyThreadIdMethod: String? = historyScan.threadIdMethod

        val historyPostIdMethod: String? = historyScan.postIdMethod

        val historyLiveIdMethod: String? = historyScan.liveIdMethod

        output[HistoryContract.historyAdapterField] = historyAdapterField
        output[HistoryContract.historyAdapterSetListMethod] = historyAdapterSetListMethod
        output[HistoryContract.historyAdapterSetListMethodSpec] = historyAdapterSetListMethodSpec
        output[HistoryContract.historyListField] = historyListField
        output[HistoryContract.historyActivityListUpdateMethod] = historyActivityListUpdateMethod
        output[HistoryContract.historyActivityListUpdateMethodSpec] = historyActivityListUpdateMethodSpec
        output[HistoryContract.historyActivityNavBarField] = historyActivityNavBarField
        output[HistoryContract.historyThreadNameMethod] = historyThreadNameMethod
        output[HistoryContract.historyForumNameMethod] = historyForumNameMethod
        output[HistoryContract.historyUserNameMethod] = historyUserNameMethod
        output[HistoryContract.historyDescriptionMethod] = historyDescriptionMethod
        output[HistoryContract.historyThreadIdMethod] = historyThreadIdMethod
        output[HistoryContract.historyPostIdMethod] = historyPostIdMethod
        output[HistoryContract.historyLiveIdMethod] = historyLiveIdMethod
    }

    fun resolveHistorySearchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): HistorySearchSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[HistorySearchHook] skipped: scan symbols unavailable")
                return null
            }
            val activityClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[HistorySearchHook] skipped: class not found: " +
                        StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS,
                )
                return null
            }
            val updateMethodName = resolvedSymbols[HistoryContract.historyActivityListUpdateMethod]?.takeIf { it.isNotBlank() }
            val updateMethodSpec = resolvedSymbols[HistoryContract.historyActivityListUpdateMethodSpec]?.takeIf { it.isNotBlank() }
            val updateMethod = if (updateMethodName != null && updateMethodSpec != null) {
                resolveMethodByCachedSpec(activityClass, updateMethodSpec, updateMethodName)?.apply {
                    isAccessible = true
                }
            } else {
                null
            }
            if (updateMethodName != null && updateMethod == null) {
                Diagnostics.logD {
                    "[HistorySearchHook] optional list update method missing: " +
                        "${activityClass.name}.$updateMethodName"
                }
            }

            val activityFields = ScanReflection.collectInstanceFields(activityClass)
            fun field(symbol: SymbolField<String?>) = activityFields.singleOrNull {
                it.name == requireSymbol(symbol.cacheKey, resolvedSymbols[symbol])
            }?.apply { isAccessible = true } ?: error("invalid ${symbol.cacheKey}")
            val adapter = field(historyAdapterField)
            val list = field(historyListField).also { check(List::class.java.isAssignableFrom(it.type)) }
            val setter = requireNotNull(resolveMethodByCachedSpec(adapter.type,
                requireSymbol("historyAdapterSetListMethodSpec", resolvedSymbols[historyAdapterSetListMethodSpec]),
                requireSymbol("historyAdapterSetListMethod", resolvedSymbols[historyAdapterSetListMethod]))) {
                "invalid history adapter setter"
            }.apply { isAccessible = true }
            check(!Modifier.isStatic(setter.modifiers) && setter.returnType == Void.TYPE)
            val data = ScanReflection.safeFindClass(HistorySearchSymbolScanner.DATA_CLASS, cl) ?: error("history data class missing")
            fun getter(symbol: SymbolField<String?>) = requireNotNull(MemberAccess.findMethodOrNull(data,
                requireSymbol(symbol.cacheKey, resolvedSymbols[symbol]))) { "missing ${symbol.cacheKey}" }.also {
                check(!Modifier.isStatic(it.modifiers) && it.returnType == String::class.java) { "invalid ${symbol.cacheKey}" }
                it.isAccessible = true
            }
            HistorySearchSymbols(
                activityClass = activityClass,
                onCreateMethod = MemberAccess.findMethodOrNull(activityClass, "onCreate", Bundle::class.java),
                onResumeMethod = MemberAccess.findMethodOrNull(activityClass, "onResume"),
                onDestroyMethod = MemberAccess.findMethodOrNull(activityClass, "onDestroy"),
                activityListUpdateMethod = updateMethod,
                adapterField = adapter,
                adapterSetListMethod = setter,
                listField = list,
                activityNavBarField = field(historyActivityNavBarField),
                threadNameMethod = getter(historyThreadNameMethod),
                forumNameMethod = getter(historyForumNameMethod),
                userNameMethod = getter(historyUserNameMethod),
                descriptionMethod = getter(historyDescriptionMethod),
                threadIdMethod = getter(historyThreadIdMethod),
                postIdMethod = getter(historyPostIdMethod),
                liveIdMethod = getter(historyLiveIdMethod),
            )
        } catch (t: Throwable) {
            Diagnostics.log("[HistorySearchHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "HistorySearchHook",
            "${symbols[HistoryContract.historyAdapterField]}.${symbols[HistoryContract.historyAdapterSetListMethod]}[${symbols[HistoryContract.historyListField]}]",
            listOf(
                HistoryContract.historyAdapterField.check(symbols),
                HistoryContract.historyAdapterSetListMethod.check(symbols),
                HistoryContract.historyAdapterSetListMethodSpec.check(symbols),
                HistoryContract.historyListField.check(symbols),
                HistoryContract.historyActivityListUpdateMethod.check(symbols),
                HistoryContract.historyActivityListUpdateMethodSpec.check(symbols),
                HistoryContract.historyActivityNavBarField.check(symbols),
                HistoryContract.historyThreadNameMethod.check(symbols),
                HistoryContract.historyForumNameMethod.check(symbols),
                HistoryContract.historyUserNameMethod.check(symbols),
                HistoryContract.historyDescriptionMethod.check(symbols),
                HistoryContract.historyThreadIdMethod.check(symbols),
                HistoryContract.historyPostIdMethod.check(symbols),
                HistoryContract.historyLiveIdMethod.check(symbols),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasHistorySearchSymbols =
            symbols[HistoryContract.historyAdapterField] != null ||
                symbols[HistoryContract.historyAdapterSetListMethod] != null ||
                symbols[HistoryContract.historyAdapterSetListMethodSpec] != null ||
                symbols[HistoryContract.historyListField] != null ||
                symbols[HistoryContract.historyActivityListUpdateMethod] != null ||
                symbols[HistoryContract.historyActivityListUpdateMethodSpec] != null
        if (hasHistorySearchSymbols && symbols[HistoryContract.historyActivityNavBarField].isNullOrBlank()) return false
        if (isSearchComplete(symbols) && resolveHistorySearchSymbols(cl, symbols) == null) return false
        return true
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
