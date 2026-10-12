package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreConfigScanSymbols
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbCommentAutoLoadSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentInteractionScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbScrollCoalesceSymbols
import com.forbidad4tieba.hook.symbol.scan.PbCommentPreloadSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbCommentBatchSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.AutoRefreshSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbCommentInteractionSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object AutoLoadMoreContract : SymbolContract("AutoLoadMore") {
    val autoLoadMoreConfigClass = text("autoLoadMoreConfigClass")
    val autoLoadMoreConfigMethod = text("autoLoadMoreConfigMethod")
    val pbCommentScrollListenerClass = text("pbCommentScrollListenerClass")
    val pbCommentScrollMethod = text("pbCommentScrollMethod")
    // Discovery and full-cache structural evidence; the runtime hook only consumes the scroll method.
    val pbCommentScrollFragmentField = text("pbCommentScrollFragmentField")
    val pbCommentScrollBottomListenerField = text("pbCommentScrollBottomListenerField")
    val pbCommentScrollBottomMethod = text("pbCommentScrollBottomMethod")
    val pbCommentPreloadConfigMethodSpec = text("pbCommentPreloadConfigMethodSpec")
    val pbCommentBatchSpec = text("pbCommentBatchSpec")

    private val feedConfig = SymbolDependencies(autoLoadMoreConfigClass, autoLoadMoreConfigMethod)
    private val scrollTarget = SymbolDependencies(pbCommentScrollListenerClass, pbCommentScrollMethod)
    private val scrollEvidence = SymbolDependencies(
        pbCommentScrollFragmentField, pbCommentScrollBottomListenerField, pbCommentScrollBottomMethod,
    )
    private val scrollCache = scrollTarget + scrollEvidence
    private val commentConfig = SymbolDependencies(pbCommentPreloadConfigMethodSpec)
    private val commentBatch = commentConfig + SymbolDependencies(pbCommentBatchSpec)
    private val autoLoadMoreOptional = feedConfig + commentBatch

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val autoLoadMoreScan = runScanStep(
            "AutoLoadMoreHook.Config",
            logger,
            scanErrors,
            AutoLoadMoreConfigScanSymbols(),
        ) {
            AutoRefreshSymbolScanner.scanLoadMoreConfig(cl, logger)
        }

        val autoLoadMoreConfigClass: String? = autoLoadMoreScan.configClass

        val autoLoadMoreConfigMethod: String? = autoLoadMoreScan.configMethod

        val pbCommentInteractionScan = once(PbCommentInteractionSymbolScanner) {
runScanStep(
            "PbCommentInteractionHooks",
            logger,
            scanErrors,
            PbCommentInteractionScanSymbols(),
        ) {
            PbCommentInteractionSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        }

        val pbCommentScrollListenerClass: String? = pbCommentInteractionScan.scrollListenerClass

        val pbCommentScrollMethod: String? = pbCommentInteractionScan.scrollMethod

        val pbCommentScrollFragmentField: String? = pbCommentInteractionScan.scrollFragmentField

        val pbCommentScrollBottomListenerField: String? = pbCommentInteractionScan.scrollBottomListenerField

        val pbCommentScrollBottomMethod: String? = pbCommentInteractionScan.scrollBottomMethod

        val commentConfig = PbCommentPreloadSymbolScanner.scan(cl, logger)
        output[pbCommentPreloadConfigMethodSpec] = commentConfig
        output[pbCommentBatchSpec] = PbCommentBatchSymbolScanner.scan(cl, commentConfig, logger)

        output[AutoLoadMoreContract.autoLoadMoreConfigClass] = autoLoadMoreConfigClass
        output[AutoLoadMoreContract.autoLoadMoreConfigMethod] = autoLoadMoreConfigMethod
        output[AutoLoadMoreContract.pbCommentScrollListenerClass] = pbCommentScrollListenerClass
        output[AutoLoadMoreContract.pbCommentScrollMethod] = pbCommentScrollMethod
        output[AutoLoadMoreContract.pbCommentScrollFragmentField] = pbCommentScrollFragmentField
        output[AutoLoadMoreContract.pbCommentScrollBottomListenerField] = pbCommentScrollBottomListenerField
        output[AutoLoadMoreContract.pbCommentScrollBottomMethod] = pbCommentScrollBottomMethod
    }

    fun resolveAutoLoadMoreSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AutoLoadMoreSymbols? {
        return try {
            val ubsMethod = MemberAccess.findMethodOrNull(
                StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS,
                cl,
                StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && ScanReflection.isBooleanType(method.returnType)
            }?.apply { isAccessible = true }

            val configClassName = symbols?.get(autoLoadMoreConfigClass)
            val configMethodName = symbols?.get(autoLoadMoreConfigMethod)
            val configMethod = if (symbols == null || feedConfig.missing(symbols).isNotEmpty()) {
                null
            } else {
                MemberAccess.findMethodOrNull(configClassName!!, cl, configMethodName!!)
                    ?.takeIf { method ->
                        method.parameterTypes.isEmpty() && ScanReflection.isIntType(method.returnType)
                    }
                    ?.apply { isAccessible = true }
            }

            if (ubsMethod == null && configMethod == null) {
                Diagnostics.log(
                    "[AutoLoadMoreHook] skipped: missing install targets " +
                        "ubs=${StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS}." +
                        "${StableTiebaHookPoints.METHOD_IS_HOME_PRE_LOAD_MORE_OPT}, " +
                        "config=$configClassName.$configMethodName",
                )
                return null
            }
            AutoLoadMoreSymbols(
                ubsMethod = ubsMethod,
                configMethod = configMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[AutoLoadMoreHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolvePbScrollCoalesceSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbScrollCoalesceSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: scan symbols unavailable")
                return null
            }
            val missing = scrollTarget.missing(resolvedSymbols)
            if (missing.isNotEmpty()) {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: missing ${missing.joinToString()}")
                return null
            }
            val listenerClassName = resolvedSymbols[pbCommentScrollListenerClass]!!
            val methodName = resolvedSymbols[pbCommentScrollMethod]!!
            val listenerClass = ScanReflection.safeFindClass(listenerClassName, cl) ?: run {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: class not found: $listenerClassName")
                return null
            }
            val method = ScanReflection.collectInstanceMethods(listenerClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 4 &&
                    candidate.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    candidate.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: run {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: method mismatch: $listenerClassName.$methodName")
                return null
            }
            method.isAccessible = true
            PbScrollCoalesceSymbols(scrollMethod = method)
        } catch (t: Throwable) {
            Diagnostics.log("[PbScrollCoalesceHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolvePbCommentAutoLoadSymbols(cl: ClassLoader, symbols: HookSymbols?): PbCommentAutoLoadSymbols? =
        PbCommentPreloadSymbolScanner.restore(cl, symbols?.get(pbCommentPreloadConfigMethodSpec))
            ?.let { PbCommentAutoLoadSymbols(it, PbCommentBatchSymbolScanner.restore(cl, symbols?.get(pbCommentBatchSpec))) }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = linkedMapOf(
        HookFeatureKey.AUTO_LOAD_MORE to CapabilityPolicy.statusFromMissing(
            emptyList(), autoLoadMoreOptional.missing(symbols),
        ),
        HookFeatureKey.ENABLE_PB_SCROLL_COALESCE to scrollTarget.requiredStatus(symbols),
    )

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AutoLoadMoreHook.Config",
            "${symbols[AutoLoadMoreContract.autoLoadMoreConfigClass]}.${symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod]}",
            feedConfig.checks(symbols),
        )
        add(
            "PbScrollCoalesceHook",
            "${symbols[AutoLoadMoreContract.pbCommentScrollListenerClass]}.${symbols[AutoLoadMoreContract.pbCommentScrollMethod]}",
            scrollTarget.checks(symbols),
        )
        add(
            "PbCommentAutoLoadHook",
            symbols[pbCommentPreloadConfigMethodSpec].orEmpty(),
            commentConfig.checks(symbols),
        )
        addOptional("PbCommentAutoLoadHook.Batch", symbols[pbCommentBatchSpec].orEmpty(),
            commentBatch.checks(symbols))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AutoLoadMoreHook.Config", false, listOf(HookFeatureKey.AUTO_LOAD_MORE)),
        PointOwner("PbCommentAutoLoadHook", false, listOf(HookFeatureKey.AUTO_LOAD_MORE)),
        PointOwner("PbScrollCoalesceHook", true, listOf(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (feedConfig.anyPresent(symbols) && !isAutoLoadMoreConfigValid(symbols, cl)) return false
        if (scrollCache.anyPresent(symbols) && !isPbCommentScrollValid(symbols, cl)) return false
        val configSpec = symbols[pbCommentPreloadConfigMethodSpec]
        if (configSpec != null && PbCommentPreloadSymbolScanner.restore(cl, configSpec) == null) return false
        val batchSpec = symbols[pbCommentBatchSpec]
        if (batchSpec != null && PbCommentBatchSymbolScanner.restore(cl, batchSpec) == null) return false
        return true
    }

    private const val PB_FRAGMENT_CLASS = StableTiebaHookPoints.PB_FRAGMENT_CLASS

    private fun isAutoLoadMoreConfigValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (feedConfig.missing(symbols).isNotEmpty()) return false
        val className = symbols[autoLoadMoreConfigClass]!!
        val methodName = symbols[autoLoadMoreConfigMethod]!!
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Int::class.javaPrimitiveType
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPbCommentScrollValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (scrollTarget.missing(symbols).isNotEmpty()) return false
        val listenerClassName = symbols[pbCommentScrollListenerClass]!!
        val scrollMethodName = symbols[pbCommentScrollMethod]!!
        return try {
            val listenerClass = ScanReflection.safeFindClass(listenerClassName, cl) ?: return false
            val hasScrollMethod = listenerClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == scrollMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Int::class.javaPrimitiveType
            }
            if (!hasScrollMethod) return false

            val fragmentFieldName = symbols[AutoLoadMoreContract.pbCommentScrollFragmentField]
            val bottomListenerFieldName = symbols[AutoLoadMoreContract.pbCommentScrollBottomListenerField]
            val bottomMethodName = symbols[AutoLoadMoreContract.pbCommentScrollBottomMethod]
            if (scrollEvidence.missing(symbols).isNotEmpty()) return true

            val fragmentField = listenerClass.declaredFields.firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.name == fragmentFieldName &&
                    field.type.name == PB_FRAGMENT_CLASS
            } ?: return false
            val bottomListenerField = fragmentField.type.declaredFields.firstOrNull { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    field.name == bottomListenerFieldName
            } ?: return false
            bottomListenerField.type.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == bottomMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }

}
