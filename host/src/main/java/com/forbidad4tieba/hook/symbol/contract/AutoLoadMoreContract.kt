package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreConfigScanSymbols
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
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
    val pbCommentScrollFragmentField = text("pbCommentScrollFragmentField")
    val pbCommentScrollBottomListenerField = text("pbCommentScrollBottomListenerField")
    val pbCommentScrollBottomMethod = text("pbCommentScrollBottomMethod")
    val pbCommentPreloadConfigMethodSpec = text("pbCommentPreloadConfigMethodSpec")
    val pbCommentBatchSpec = text("pbCommentBatchSpec")

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

            val configClassName = symbols?.get(AutoLoadMoreContract.autoLoadMoreConfigClass)
                ?.takeIf { it.isNotBlank() }
            val configMethodName = symbols?.get(AutoLoadMoreContract.autoLoadMoreConfigMethod)
                ?.takeIf { it.isNotBlank() }
            val configMethod = if (configClassName == null || configMethodName == null) {
                null
            } else {
                MemberAccess.findMethodOrNull(configClassName, cl, configMethodName)
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
            val listenerClassName = resolvedSymbols[AutoLoadMoreContract.pbCommentScrollListenerClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: missing pbCommentScrollListenerClass")
                return null
            }
            val methodName = resolvedSymbols[AutoLoadMoreContract.pbCommentScrollMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PbScrollCoalesceHook] skipped: missing pbCommentScrollMethod")
                return null
            }
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

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val autoLoadMoreOptional = ArrayList<String>(8)
        if (symbols[AutoLoadMoreContract.autoLoadMoreConfigClass].isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigClass")
        }
        if (symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod].isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigMethod")
        }
        if (symbols[pbCommentPreloadConfigMethodSpec].isNullOrBlank()) {
            autoLoadMoreOptional.add(pbCommentPreloadConfigMethodSpec.cacheKey)
        }
        if (symbols[pbCommentBatchSpec].isNullOrBlank()) {
            autoLoadMoreOptional.add(pbCommentBatchSpec.cacheKey)
        }
        out[HookFeatureKey.AUTO_LOAD_MORE] = if (autoLoadMoreOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = autoLoadMoreOptional,
            )
        }
        val pbScrollCoalesceCritical = ArrayList<String>(2)
        if (symbols[AutoLoadMoreContract.pbCommentScrollListenerClass].isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollListenerClass")
        }
        if (symbols[AutoLoadMoreContract.pbCommentScrollMethod].isNullOrBlank()) {
            pbScrollCoalesceCritical.add("pbCommentScrollMethod")
        }
        val pbScrollCoalesceOptional = ArrayList<String>(3)
        if (symbols[AutoLoadMoreContract.pbCommentScrollFragmentField].isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollFragmentField")
        }
        if (symbols[AutoLoadMoreContract.pbCommentScrollBottomListenerField].isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollBottomListenerField")
        }
        if (symbols[AutoLoadMoreContract.pbCommentScrollBottomMethod].isNullOrBlank()) {
            pbScrollCoalesceOptional.add("pbCommentScrollBottomMethod")
        }
        out[HookFeatureKey.ENABLE_PB_SCROLL_COALESCE] = when {
            pbScrollCoalesceCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = pbScrollCoalesceCritical,
                missingOptional = pbScrollCoalesceOptional,
            )
            pbScrollCoalesceOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = pbScrollCoalesceOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AutoLoadMoreHook.Config",
            "${symbols[AutoLoadMoreContract.autoLoadMoreConfigClass]}.${symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod]}",
            listOf(
                AutoLoadMoreContract.autoLoadMoreConfigClass.check(symbols),
                AutoLoadMoreContract.autoLoadMoreConfigMethod.check(symbols),
            ),
        )
        add(
            "PbScrollCoalesceHook",
            "${symbols[AutoLoadMoreContract.pbCommentScrollListenerClass]}.${symbols[AutoLoadMoreContract.pbCommentScrollMethod]}",
            listOf(
                AutoLoadMoreContract.pbCommentScrollListenerClass.check(symbols),
                AutoLoadMoreContract.pbCommentScrollMethod.check(symbols),
            ),
        )
        addOptional(
            "PbScrollCoalesceHook.BottomGuard",
            "${symbols[AutoLoadMoreContract.pbCommentScrollListenerClass]}[${symbols[AutoLoadMoreContract.pbCommentScrollFragmentField]}]." +
                "${symbols[AutoLoadMoreContract.pbCommentScrollBottomListenerField]}.${symbols[AutoLoadMoreContract.pbCommentScrollBottomMethod]}",
            listOf(
                AutoLoadMoreContract.pbCommentScrollFragmentField.check(symbols),
                AutoLoadMoreContract.pbCommentScrollBottomListenerField.check(symbols),
                AutoLoadMoreContract.pbCommentScrollBottomMethod.check(symbols),
            ),
        )
        add(
            "PbCommentAutoLoadHook",
            symbols[pbCommentPreloadConfigMethodSpec].orEmpty(),
            listOf(pbCommentPreloadConfigMethodSpec.check(symbols)),
        )
        addOptional("PbCommentAutoLoadHook.Batch", symbols[pbCommentBatchSpec].orEmpty(),
            listOf(pbCommentPreloadConfigMethodSpec.check(symbols), pbCommentBatchSpec.check(symbols)))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AutoLoadMoreHook.Config", false, listOf(HookFeatureKey.AUTO_LOAD_MORE)),
        PointOwner("PbCommentAutoLoadHook", false, listOf(HookFeatureKey.AUTO_LOAD_MORE)),
        PointOwner("PbScrollCoalesceHook", true, listOf(HookFeatureKey.ENABLE_PB_SCROLL_COALESCE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasAutoLoadMoreSymbols =
            symbols[AutoLoadMoreContract.autoLoadMoreConfigClass] != null ||
                symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod] != null
        if (hasAutoLoadMoreSymbols && !isAutoLoadMoreConfigValid(symbols, cl)) return false
        val hasPbCommentScrollSymbols =
            symbols[AutoLoadMoreContract.pbCommentScrollListenerClass] != null ||
                symbols[AutoLoadMoreContract.pbCommentScrollMethod] != null ||
                symbols[AutoLoadMoreContract.pbCommentScrollFragmentField] != null ||
                symbols[AutoLoadMoreContract.pbCommentScrollBottomListenerField] != null ||
                symbols[AutoLoadMoreContract.pbCommentScrollBottomMethod] != null
        if (hasPbCommentScrollSymbols && !isPbCommentScrollValid(symbols, cl)) return false
        val commentConfig = symbols[pbCommentPreloadConfigMethodSpec]
        if (commentConfig != null && PbCommentPreloadSymbolScanner.restore(cl, commentConfig) == null) return false
        val commentBatch = symbols[pbCommentBatchSpec]
        if (commentBatch != null && PbCommentBatchSymbolScanner.restore(cl, commentBatch) == null) return false
        return true
    }

    private const val PB_FRAGMENT_CLASS = StableTiebaHookPoints.PB_FRAGMENT_CLASS

    private fun isAutoLoadMoreConfigValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[AutoLoadMoreContract.autoLoadMoreConfigClass] ?: return false
        val methodName = symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod] ?: return false
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
        val listenerClassName = symbols[AutoLoadMoreContract.pbCommentScrollListenerClass] ?: return false
        val scrollMethodName = symbols[AutoLoadMoreContract.pbCommentScrollMethod] ?: return false
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
            if (
                fragmentFieldName.isNullOrBlank() ||
                bottomListenerFieldName.isNullOrBlank() ||
                bottomMethodName.isNullOrBlank()
            ) {
                return true
            }

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
