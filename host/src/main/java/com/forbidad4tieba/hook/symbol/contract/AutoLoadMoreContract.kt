package com.forbidad4tieba.hook.symbol.contract

import android.widget.AbsListView
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.TAG
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreConfigScanSymbols
import com.forbidad4tieba.hook.symbol.model.AutoLoadMoreSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PbCommentAutoLoadSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentBottomListSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentBottomRecyclerSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentInteractionScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbScrollCoalesceSymbols
import com.forbidad4tieba.hook.symbol.scan.AutoRefreshSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PbCommentInteractionSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointState
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object AutoLoadMoreContract : SymbolContract("AutoLoadMore") {
    internal override val candidateClasses = listOf(
        StableTiebaHookPoints.BD_LIST_VIEW_CLASS,
        StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS,
    )

    val autoLoadMoreConfigClass = text("autoLoadMoreConfigClass")
    val autoLoadMoreConfigMethod = text("autoLoadMoreConfigMethod")
    val pbCommentScrollListenerClass = text("pbCommentScrollListenerClass")
    val pbCommentScrollMethod = text("pbCommentScrollMethod")
    val pbCommentScrollFragmentField = text("pbCommentScrollFragmentField")
    val pbCommentScrollBottomListenerField = text("pbCommentScrollBottomListenerField")
    val pbCommentScrollBottomMethod = text("pbCommentScrollBottomMethod")
    val pbCommentBottomListScrollClass = text("pbCommentBottomListScrollClass")
    val pbCommentBottomListScrollMethod = text("pbCommentBottomListScrollMethod")
    val pbCommentBottomListOwnerField = text("pbCommentBottomListOwnerField")
    val pbCommentBottomRecyclerScrollClass = text("pbCommentBottomRecyclerScrollClass")
    val pbCommentBottomRecyclerScrollMethod = text("pbCommentBottomRecyclerScrollMethod")
    val pbCommentBottomRecyclerOwnerField = text("pbCommentBottomRecyclerOwnerField")

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

        val pbCommentBottomListScrollClass: String? = pbCommentInteractionScan.bottomListScrollClass

        val pbCommentBottomListScrollMethod: String? = pbCommentInteractionScan.bottomListScrollMethod

        val pbCommentBottomListOwnerField: String? = pbCommentInteractionScan.bottomListOwnerField

        val pbCommentBottomRecyclerScrollClass: String? = pbCommentInteractionScan.bottomRecyclerScrollClass

        val pbCommentBottomRecyclerScrollMethod: String? = pbCommentInteractionScan.bottomRecyclerScrollMethod

        val pbCommentBottomRecyclerOwnerField: String? = pbCommentInteractionScan.bottomRecyclerOwnerField

        output[AutoLoadMoreContract.autoLoadMoreConfigClass] = autoLoadMoreConfigClass
        output[AutoLoadMoreContract.autoLoadMoreConfigMethod] = autoLoadMoreConfigMethod
        output[AutoLoadMoreContract.pbCommentScrollListenerClass] = pbCommentScrollListenerClass
        output[AutoLoadMoreContract.pbCommentScrollMethod] = pbCommentScrollMethod
        output[AutoLoadMoreContract.pbCommentScrollFragmentField] = pbCommentScrollFragmentField
        output[AutoLoadMoreContract.pbCommentScrollBottomListenerField] = pbCommentScrollBottomListenerField
        output[AutoLoadMoreContract.pbCommentScrollBottomMethod] = pbCommentScrollBottomMethod
        output[AutoLoadMoreContract.pbCommentBottomListScrollClass] = pbCommentBottomListScrollClass
        output[AutoLoadMoreContract.pbCommentBottomListScrollMethod] = pbCommentBottomListScrollMethod
        output[AutoLoadMoreContract.pbCommentBottomListOwnerField] = pbCommentBottomListOwnerField
        output[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass] = pbCommentBottomRecyclerScrollClass
        output[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod] = pbCommentBottomRecyclerScrollMethod
        output[AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField] = pbCommentBottomRecyclerOwnerField
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

    fun resolvePbCommentAutoLoadSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbCommentAutoLoadSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PbCommentAutoLoadHook] skipped: scan symbols unavailable")
                return null
            }
            val listTargets = resolvePbCommentBottomListTargets(cl, resolvedSymbols)
            val recyclerTargets = resolvePbCommentBottomRecyclerTargets(cl, resolvedSymbols)
            if (listTargets == null && recyclerTargets == null) {
                Diagnostics.log("[PbCommentAutoLoadHook] skipped: bottom mechanism symbol missing")
                return null
            }
            PbCommentAutoLoadSymbols(listTargets = listTargets, recyclerTargets = recyclerTargets)
        } catch (t: Throwable) {
            Diagnostics.log("[PbCommentAutoLoadHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbCommentBottomListTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): PbCommentBottomListSymbols? {
        val scrollClassName = symbols[AutoLoadMoreContract.pbCommentBottomListScrollClass]?.takeIf { it.isNotBlank() } ?: return null
        val scrollMethodName = symbols[AutoLoadMoreContract.pbCommentBottomListScrollMethod]?.takeIf { it.isNotBlank() } ?: return null
        val ownerFieldName = symbols[AutoLoadMoreContract.pbCommentBottomListOwnerField]?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val scrollClass = ScanReflection.safeFindClass(scrollClassName, cl) ?: return null
            val listClass = ScanReflection.safeFindClass(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl) ?: return null
            val ownerField = MemberAccess.findField(scrollClass, ownerFieldName)
            if (ownerField.type != listClass) return null
            val bottomListenerField = MemberAccess.findField(listClass, PB_COMMENT_BOTTOM_LISTENER_FIELD)
            val bottomMethod = resolvePbCommentBottomMethod(bottomListenerField.type) ?: return null
            val scrollMethod = resolvePbCommentListScrollMethod(scrollClass, scrollMethodName) ?: return null
            ownerField.isAccessible = true
            bottomListenerField.isAccessible = true
            PbCommentBottomListSymbols(
                listClass = listClass,
                scrollMethod = scrollMethod,
                ownerField = ownerField,
                bottomListenerField = bottomListenerField,
                bottomMethod = bottomMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbCommentAutoLoadHook] BdListView target resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbCommentBottomRecyclerTargets(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): PbCommentBottomRecyclerSymbols? {
        val scrollClassName = symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass]?.takeIf { it.isNotBlank() } ?: return null
        val scrollMethodName = symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod]?.takeIf { it.isNotBlank() } ?: return null
        val ownerFieldName = symbols[AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField]?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val scrollClass = ScanReflection.safeFindClass(scrollClassName, cl) ?: return null
            val recyclerClass = ScanReflection.safeFindClass(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS, cl) ?: return null
            val recyclerViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl) ?: return null
            val ownerField = MemberAccess.findField(scrollClass, ownerFieldName)
            if (ownerField.type != recyclerClass) return null
            val bottomListenerField = MemberAccess.findField(recyclerClass, PB_COMMENT_BOTTOM_LISTENER_FIELD)
            val bottomMethod = resolvePbCommentBottomMethod(bottomListenerField.type) ?: return null
            val firstVisibleMethod = resolvePbCommentNoArgIntMethod(recyclerClass, "getFirstVisiblePosition") ?: return null
            val lastVisibleMethod = resolvePbCommentNoArgIntMethod(recyclerClass, "getLastVisiblePosition") ?: return null
            val getAdapterMethod = resolvePbCommentNoArgMethod(recyclerViewClass, "getAdapter") ?: return null
            val scrollMethod = resolvePbCommentRecyclerScrolledMethod(
                scrollClass = scrollClass,
                methodName = scrollMethodName,
                recyclerViewClass = recyclerViewClass,
            ) ?: return null
            ownerField.isAccessible = true
            bottomListenerField.isAccessible = true
            PbCommentBottomRecyclerSymbols(
                recyclerClass = recyclerClass,
                scrollMethod = scrollMethod,
                ownerField = ownerField,
                bottomListenerField = bottomListenerField,
                bottomMethod = bottomMethod,
                firstVisibleMethod = firstVisibleMethod,
                lastVisibleMethod = lastVisibleMethod,
                getAdapterMethod = getAdapterMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbCommentAutoLoadHook] BdRecyclerView target resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbCommentListScrollMethod(clazz: Class<*>, methodName: String): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == AbsListView::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun resolvePbCommentRecyclerScrolledMethod(
        scrollClass: Class<*>,
        methodName: String,
        recyclerViewClass: Class<*>,
    ): Method? {
        return scrollClass.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == recyclerViewClass &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun resolvePbCommentNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == methodName &&
                    it.parameterTypes.isEmpty()
            }
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }

    private fun resolvePbCommentNoArgIntMethod(clazz: Class<*>, methodName: String): Method? {
        return resolvePbCommentNoArgMethod(clazz, methodName)
            ?.takeIf { it.returnType == Int::class.javaPrimitiveType }
    }

    private fun resolvePbCommentBottomMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == PB_COMMENT_BOTTOM_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true }
    }

    private const val PB_COMMENT_BOTTOM_LISTENER_FIELD = "mOnScrollToBottomListener"

    private const val PB_COMMENT_BOTTOM_METHOD = "onScrollToBottom"

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val autoLoadMoreOptional = ArrayList<String>(8)
        if (symbols[AutoLoadMoreContract.autoLoadMoreConfigClass].isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigClass")
        }
        if (symbols[AutoLoadMoreContract.autoLoadMoreConfigMethod].isNullOrBlank()) {
            autoLoadMoreOptional.add("autoLoadMoreConfigMethod")
        }
        val hasListBottomMechanism =
            !symbols[AutoLoadMoreContract.pbCommentBottomListScrollClass].isNullOrBlank() &&
                !symbols[AutoLoadMoreContract.pbCommentBottomListScrollMethod].isNullOrBlank() &&
                !symbols[AutoLoadMoreContract.pbCommentBottomListOwnerField].isNullOrBlank()
        val hasRecyclerBottomMechanism =
            !symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass].isNullOrBlank() &&
                !symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod].isNullOrBlank() &&
                !symbols[AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField].isNullOrBlank()
        if (!hasListBottomMechanism && !hasRecyclerBottomMechanism) {
            autoLoadMoreOptional.add("pbCommentBottomMechanism")
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
        run {
            val listChecks = listOf(
                AutoLoadMoreContract.pbCommentBottomListScrollClass.check(symbols),
                AutoLoadMoreContract.pbCommentBottomListScrollMethod.check(symbols),
                AutoLoadMoreContract.pbCommentBottomListOwnerField.check(symbols),
            )
            val recyclerChecks = listOf(
                AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass.check(symbols),
                AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod.check(symbols),
                AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField.check(symbols),
            )
            val listFound = listChecks.all { it.second }
            val recyclerFound = recyclerChecks.all { it.second }
            val anyBottomSymbol = (listChecks + recyclerChecks).any { it.second }
            val missing = when {
                listFound || recyclerFound -> emptyList()
                anyBottomSymbol -> (listChecks + recyclerChecks).filter { !it.second }.map { it.first }
                else -> listOf("pbCommentBottomMechanism")
            }
            val state = when {
                listFound || recyclerFound -> HookPointState.FOUND
                anyBottomSymbol -> HookPointState.PARTIAL
                else -> HookPointState.MISSING
            }
            addStatus(
                HookPointStatus(
                    name = "PbCommentAutoLoadHook",
                    state = state,
                    missing = missing,
                    target =
                        "${symbols[AutoLoadMoreContract.pbCommentBottomListScrollClass]}.${symbols[AutoLoadMoreContract.pbCommentBottomListScrollMethod]} / " +
                            "${symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass]}.${symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod]}",
                ),
            )
        }
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
        val hasPbCommentBottomSymbols =
            symbols[AutoLoadMoreContract.pbCommentBottomListScrollClass] != null ||
                symbols[AutoLoadMoreContract.pbCommentBottomListScrollMethod] != null ||
                symbols[AutoLoadMoreContract.pbCommentBottomListOwnerField] != null ||
                symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass] != null ||
                symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod] != null ||
                symbols[AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField] != null
        if (hasPbCommentBottomSymbols && !isPbCommentBottomMechanismValid(symbols, cl)) return false
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

    private fun isPbCommentBottomMechanismValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val listOk = isListBottomScrollValid(
            symbols[AutoLoadMoreContract.pbCommentBottomListScrollClass],
            symbols[AutoLoadMoreContract.pbCommentBottomListScrollMethod],
            symbols[AutoLoadMoreContract.pbCommentBottomListOwnerField],
            StableTiebaHookPoints.BD_LIST_VIEW_CLASS,
            cl,
        ) { method ->
            method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == android.widget.AbsListView::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }
        val recyclerOk = isListBottomScrollValid(
            symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollClass],
            symbols[AutoLoadMoreContract.pbCommentBottomRecyclerScrollMethod],
            symbols[AutoLoadMoreContract.pbCommentBottomRecyclerOwnerField],
            StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS,
            cl,
        ) { method ->
            val recyclerViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl)
            method.parameterTypes.size == 3 &&
                recyclerViewClass != null &&
                method.parameterTypes[0] == recyclerViewClass &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        return listOk || recyclerOk
    }

    private fun isListBottomScrollValid(
        className: String?,
        methodName: String?,
        ownerFieldName: String?,
        ownerClassName: String,
        cl: ClassLoader,
        parameterCheck: (Method) -> Boolean,
    ): Boolean {
        if (className.isNullOrBlank() || methodName.isNullOrBlank() || ownerFieldName.isNullOrBlank()) {
            return false
        }
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl)
            val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl)
            if (targetClass == null || ownerClass == null) return false
            val ownerField = targetClass.declaredFields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.name == ownerFieldName &&
                    field.type == ownerClass
            } ?: return false
            ownerField.isAccessible = true
            targetClass.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    parameterCheck(method)
            }
        } catch (t: Throwable) {
            Diagnostics.logD("$TAG: pbCommentBottom validate failed: ${HookSymbolScanDiagnostics.sanitizeScanStatusText(HookSymbolScanDiagnostics.formatScanException(t))}")
            false
        }
    }
}
