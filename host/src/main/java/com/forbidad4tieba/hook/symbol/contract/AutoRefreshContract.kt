package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.AutoRefreshSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.AutoRefreshSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object AutoRefreshContract : SymbolContract("AutoRefresh") {
    val autoRefreshTriggerMethod = text("autoRefreshTriggerMethod")
    val autoRefreshNetRequestMethod = text("autoRefreshNetRequestMethod")
    val autoRefreshNetRequestMethodSpec = text("autoRefreshNetRequestMethodSpec")
    val autoRefreshCacheRestoreMethod = text("autoRefreshCacheRestoreMethod")
    val autoRefreshPullGestureMethod = text("autoRefreshPullGestureMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val autoRefreshTriggerMethod: String? = runScanStep(
            "AutoRefreshHook",
            logger,
            scanErrors,
            null as String?,
        ) {
            AutoRefreshSymbolScanner.scan(context, cl, logger)
        }

        val autoRefreshNetRequestScan = runScanStep(
            "AutoRefreshHook.RecRequest",
            logger,
            scanErrors,
            emptyList(),
        ) {
            AutoRefreshSymbolScanner.scanRecPersonalizeRequest(context, cl, logger)
        }

        val autoRefreshNetRequestMethod: String? = autoRefreshNetRequestScan
            .map { it.ownerMethodName }
            .distinct()
            .joinToString(",")
            .takeIf { it.isNotBlank() }
        // name|void|paramTypes|ownerClass — the owner class is part of the spec
        // because the host spreads refresh transports over several classes.

        val autoRefreshNetRequestMethodSpec: String? = autoRefreshNetRequestScan
            .map {
                it.ownerMethodName + "|void|" + it.paramTypes.joinToString(",") +
                    "|" + it.ownerClassName
            }
            .distinct()
            .joinToString(";")
            .takeIf { it.isNotBlank() }

        val autoRefreshCacheRestoreMethod: String? = runScanStep(
            "AutoRefreshHook.CacheRestore",
            logger,
            scanErrors,
            null as String?,
        ) {
            AutoRefreshSymbolScanner.scanHomeCacheRestore(context, cl, logger)?.ownerMethodName
        }

        output[AutoRefreshContract.autoRefreshTriggerMethod] = autoRefreshTriggerMethod
        output[AutoRefreshContract.autoRefreshNetRequestMethod] = autoRefreshNetRequestMethod
        output[AutoRefreshContract.autoRefreshNetRequestMethodSpec] = autoRefreshNetRequestMethodSpec
        output[AutoRefreshContract.autoRefreshCacheRestoreMethod] = autoRefreshCacheRestoreMethod
        output[AutoRefreshContract.autoRefreshPullGestureMethod] = runScanStep(
            "AutoRefreshHook.PullGesture", logger, scanErrors, null as String?,
        ) { AutoRefreshSymbolScanner.scanPullGesture(cl, logger) }
    }

    fun resolveAutoRefreshSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AutoRefreshSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[AutoRefreshHook] skipped: scan symbols unavailable")
                return null
            }
            val methodName = resolvedSymbols[AutoRefreshContract.autoRefreshTriggerMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[AutoRefreshHook] skipped: missing autoRefreshTriggerMethod")
                return null
            }
            val pageClass = ScanReflection.safeFindClass(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: run {
                Diagnostics.log(
                    "[AutoRefreshHook] skipped: class not found: " +
                        StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS,
                )
                return null
            }
            val method = ScanReflection.collectInstanceMethods(pageClass).singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.isEmpty()
            } ?: run {
                Diagnostics.log(
                    "[AutoRefreshHook] skipped: method mismatch: " +
                        "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.$methodName()",
                )
                return null
            }
            method.isAccessible = true

            val netRequestMethods = resolveAutoRefreshNetRequestMethods(cl, resolvedSymbols)
            if (netRequestMethods.isEmpty()) {
                Diagnostics.log(
                    "[AutoRefreshHook] skipped: net request methods unresolved: " +
                        "trigger=${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.$methodName() " +
                        "net=${resolvedSymbols[AutoRefreshContract.autoRefreshNetRequestMethod]}",
                )
                return null
            }

            val cacheRestoreMethod = resolveAutoRefreshCacheRestoreMethod(cl, resolvedSymbols)
            if (cacheRestoreMethod == null) {
                Diagnostics.log(
                    "[AutoRefreshHook] skipped: cache restore method unresolved: " +
                        "cache=${resolvedSymbols[AutoRefreshContract.autoRefreshCacheRestoreMethod]}",
                )
                return null
            }

            AutoRefreshSymbols(
                triggerMethod = method,
                netRequestMethods = netRequestMethods,
                cacheRestoreMethod = cacheRestoreMethod,
                pullGestureMethod = resolvePullGestureMethod(cl, resolvedSymbols),
            )

        } catch (t: Throwable) {
            Diagnostics.log("[AutoRefreshHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolveAutoRefreshNetRequestMethods(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): List<Method> {
        // Each spec entry is name|void|paramTypes|ownerClass. The owner class is
        // authoritative: refresh transports live on more than one host class, so
        // resolving every name against a single hardcoded class drops matches.
        val specs = symbols[AutoRefreshContract.autoRefreshNetRequestMethodSpec]
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                Diagnostics.log("[AutoRefreshHook] net request: missing autoRefreshNetRequestMethodSpec")
                return emptyList()
            }
        val resolved = ArrayList<Method>(specs.size)
        for (spec in specs) {
            val parts = spec.split("|")
            val methodName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[AutoRefreshHook] net request: malformed spec: $spec")
                return emptyList()
            }
            val paramCount = parts.getOrNull(2)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.size
            val ownerClassName = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                ?: StableTiebaHookPoints.REC_PERSONALIZE_MODEL_CLASS
            val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: run {
                Diagnostics.log("[AutoRefreshHook] net request: class not found: $ownerClassName")
                return emptyList()
            }
            val candidate = ScanReflection.collectInstanceMethods(ownerClass).singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    (paramCount == null || method.parameterTypes.size == paramCount)
            } ?: run {
                Diagnostics.log(
                    "[AutoRefreshHook] net request: method mismatch: $ownerClassName.$methodName()",
                )
                return emptyList()
            }
            candidate.isAccessible = true
            resolved.add(candidate)
        }
        if (resolved.isNotEmpty()) {
            Diagnostics.log(
                "[AutoRefreshHook] net request resolved: " +
                    resolved.joinToString(", ") { "${it.declaringClass.name}.${it.name}()" },
            )
        }
        return resolved
    }

    private fun resolveAutoRefreshCacheRestoreMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        val methodName = symbols[AutoRefreshContract.autoRefreshCacheRestoreMethod]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[AutoRefreshHook] cache restore: missing autoRefreshCacheRestoreMethod")
            return null
        }
        val schedulerClass = ScanReflection.safeFindClass(StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS, cl) ?: run {
            Diagnostics.log(
                "[AutoRefreshHook] cache restore: class not found: " +
                    StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS,
            )
            return null
        }
        val candidate = ScanReflection.collectInstanceMethods(schedulerClass).singleOrNull { method ->
            method.name == methodName &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        } ?: run {
            Diagnostics.log(
                "[AutoRefreshHook] cache restore: method mismatch: " +
                    "${StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS}.$methodName(String):boolean",
            )
            return null
        }
        candidate.isAccessible = true
        Diagnostics.log(
            "[AutoRefreshHook] cache restore resolved: " +
                "${StableTiebaHookPoints.LOW_SCORE_SCHEDULER_CLASS}.$methodName()",
        )
        return candidate
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val autoRefreshCritical = ArrayList<String>(3)
        if (symbols[AutoRefreshContract.autoRefreshTriggerMethod].isNullOrBlank()) {
            autoRefreshCritical.add("autoRefreshTriggerMethod")
        }
        if (symbols[AutoRefreshContract.autoRefreshNetRequestMethod].isNullOrBlank()) {
            autoRefreshCritical.add("autoRefreshNetRequestMethod")
        }
        if (symbols[AutoRefreshContract.autoRefreshCacheRestoreMethod].isNullOrBlank()) {
            autoRefreshCritical.add("autoRefreshCacheRestoreMethod")
        }
        out[HookFeatureKey.DISABLE_AUTO_REFRESH] = if (autoRefreshCritical.isEmpty()) {
            val missing = listOfNotNull(autoRefreshPullGestureMethod.cacheKey.takeIf { symbols[autoRefreshPullGestureMethod].isNullOrBlank() })
            HookFeatureStatus(state = if (missing.isEmpty()) HookFeatureState.FULL else HookFeatureState.PARTIAL, missingOptional = missing)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = autoRefreshCritical)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AutoRefreshHook",
            "${StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS}.${symbols[AutoRefreshContract.autoRefreshTriggerMethod]}",
            listOf(
                AutoRefreshContract.autoRefreshTriggerMethod.check(symbols),
                AutoRefreshContract.autoRefreshNetRequestMethod.check(symbols),
                AutoRefreshContract.autoRefreshCacheRestoreMethod.check(symbols),
            ),
        )
        addOptional(
            "AutoRefreshHook.PullGesture",
            "${StableTiebaHookPoints.HOME_SWIPE_REFRESH_LAYOUT_CLASS}.${symbols[autoRefreshPullGestureMethod]}",
            listOf(autoRefreshPullGestureMethod.check(symbols)),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AutoRefreshHook", false, listOf(HookFeatureKey.DISABLE_AUTO_REFRESH)),
        PointOwner("AutoRefreshHook.PullGesture", false, listOf(HookFeatureKey.DISABLE_AUTO_REFRESH)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasAutoRefreshSymbols = symbols[AutoRefreshContract.autoRefreshTriggerMethod] != null
        if (hasAutoRefreshSymbols && !isAutoRefreshValid(symbols, cl)) return false
        if (symbols[autoRefreshPullGestureMethod] != null && resolvePullGestureMethod(cl, symbols) == null) return false
        return true
    }

    private fun resolvePullGestureMethod(cl: ClassLoader, symbols: HookSymbols): Method? {
        val name = symbols[autoRefreshPullGestureMethod] ?: return null
        return try {
            val owner = ScanReflection.safeFindClass(StableTiebaHookPoints.HOME_SWIPE_REFRESH_LAYOUT_CLASS, cl) ?: return null
            owner.declaredMethods.singleOrNull { it.name == name && AutoRefreshSymbolScanner.isPullGestureMethod(it) }
                ?.apply { isAccessible = true }
        } catch (t: Throwable) {
            Diagnostics.log("[AutoRefreshHook] pull gesture restore failed: ${t.message}")
            null
        }
    }

    private const val PERSONALIZE_PAGE_VIEW_CLASS =
        "com.baidu.tieba.homepage.personalize.PersonalizePageView"

    private const val REC_PERSONALIZE_MODEL_CLASS =
        "com.baidu.tieba.homepage.personalize.model.RecPersonalizePageModel"

    private const val LOW_SCORE_SCHEDULER_CLASS = "com.baidu.tieba.parser.LowScoreScheduler"

    private fun isAutoRefreshValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val methodName = symbols[AutoRefreshContract.autoRefreshTriggerMethod] ?: return false
        val hasTrigger = try {
            val targetClass = ScanReflection.safeFindClass(PERSONALIZE_PAGE_VIEW_CLASS, cl) ?: return false
            targetClass.declaredMethods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
        if (!hasTrigger) return false

        val cacheRestoreName = symbols[AutoRefreshContract.autoRefreshCacheRestoreMethod]
        if (cacheRestoreName != null) {
            val cacheRestoreValid = try {
                val schedulerClass = ScanReflection.safeFindClass(LOW_SCORE_SCHEDULER_CLASS, cl)
                if (schedulerClass == null) {
                    false
                } else {
                    schedulerClass.declaredMethods.any { method ->
                        !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                            method.name == cacheRestoreName &&
                            method.returnType == Boolean::class.javaPrimitiveType &&
                            method.parameterTypes.size == 1 &&
                            method.parameterTypes[0] == String::class.java
                    }
                }
            } catch (_: Throwable) {
                false
            }
            if (!cacheRestoreValid) return false
        }

        val netRequestNames = symbols[AutoRefreshContract.autoRefreshNetRequestMethod]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: return true
        // Spec entries are name|void|paramTypes|ownerClass; validate each against
        // its own owner class, since refresh transports span several host classes.
        val specs = symbols[AutoRefreshContract.autoRefreshNetRequestMethodSpec]
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return try {
            netRequestNames.all { netRequestName ->
                val parts = specs
                    .firstOrNull { it.startsWith("$netRequestName|") }
                    ?.split("|")
                val paramCount = parts
                    ?.getOrNull(2)
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.size
                val ownerClassName = parts?.getOrNull(3)?.takeIf { it.isNotBlank() }
                    ?: REC_PERSONALIZE_MODEL_CLASS
                val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: return@all false
                ownerClass.declaredMethods.any { method ->
                    !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                        method.name == netRequestName &&
                        method.returnType == Void.TYPE &&
                        (paramCount == null || method.parameterTypes.size == paramCount)
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}
