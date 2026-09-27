package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HomeHeaderScanSymbols
import com.forbidad4tieba.hook.symbol.model.HomeTopBarRightSlotSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.HomeHeaderSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object HomeRightSlotContract : SymbolContract("HomeRightSlot") {
    internal override val candidateClasses = listOf(
        StableTiebaHookPoints.HOME_TAB_BAR_RIGHT_SLOT_CLASS,
    )

    val homeRightSlotClass = text("homeRightSlotClass")
    val homeRightSlotStateMethods = texts("homeRightSlotStateMethods", preserveEmpty = false)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val homeHeaderScan = once(HomeHeaderSymbolScanner) {
runScanStep(
            "HomeHeaderHooks",
            logger,
            scanErrors,
            HomeHeaderScanSymbols(),
        ) {
            HomeHeaderSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }
        }

        output[HomeRightSlotContract.homeRightSlotClass] = homeHeaderScan.homeRightSlotClass
        output[HomeRightSlotContract.homeRightSlotStateMethods] = homeHeaderScan.homeRightSlotStateMethods
    }

    fun resolveHomeTopBarRightSlotSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): HomeTopBarRightSlotSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[HomeTopBarRightSlotHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[HomeRightSlotContract.homeRightSlotClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[HomeTopBarRightSlotHook] skipped: missing homeRightSlotClass")
                return null
            }
            val methodNames = resolvedSymbols[HomeRightSlotContract.homeRightSlotStateMethods].orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (methodNames.isEmpty()) {
                Diagnostics.log("[HomeTopBarRightSlotHook] skipped: missing homeRightSlotStateMethods")
                return null
            }

            val slotClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[HomeTopBarRightSlotHook] skipped: class not found: $className")
                return null
            }
            if (!ViewGroup::class.java.isAssignableFrom(slotClass)) {
                Diagnostics.log("[HomeTopBarRightSlotHook] skipped: class is not a ViewGroup: $className")
                return null
            }
            val methods = ScanReflection.collectInstanceMethods(slotClass)
            val stateMethods = ArrayList<Method>(methodNames.size)
            for (methodName in methodNames) {
                val method = methods.singleOrNull { candidate ->
                    candidate.name == methodName &&
                        candidate.returnType == Void.TYPE &&
                        candidate.parameterTypes.isEmpty()
                } ?: run {
                    Diagnostics.log("[HomeTopBarRightSlotHook] skipped: method mismatch: $className.$methodName()")
                    return null
                }
                stateMethods += method
            }

            val searchIconGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getSearchIconView",
                returnTypeMatch = { ImageView::class.java.isAssignableFrom(it) },
            ) ?: return null
            val gameIconGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getGameIconView",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null
            val topBarTipGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getTopBarTip",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null
            val redDotGetter = resolveHomeRightSlotGetter(
                methods = methods,
                name = "getRedDotView",
                returnTypeMatch = { View::class.java.isAssignableFrom(it) },
            ) ?: return null

            listOf(
                searchIconGetter,
                gameIconGetter,
                topBarTipGetter,
                redDotGetter,
            ).plus(stateMethods).forEach { it.isAccessible = true }

            HomeTopBarRightSlotSymbols(
                slotClass = slotClass,
                stateMethods = stateMethods,
                searchIconViewMethod = searchIconGetter,
                gameIconViewMethod = gameIconGetter,
                topBarTipMethod = topBarTipGetter,
                redDotViewMethod = redDotGetter,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[HomeTopBarRightSlotHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolveHomeRightSlotGetter(
        methods: List<Method>,
        name: String,
        returnTypeMatch: (Class<*>) -> Boolean,
    ): Method? {
        return methods.singleOrNull { method ->
            method.name == name &&
                method.parameterTypes.isEmpty() &&
                returnTypeMatch(method.returnType)
        } ?: run {
            Diagnostics.log("[HomeTopBarRightSlotHook] skipped: getter mismatch: $name()")
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.BLOCK_AD_HOME_TOP_BAR] = statusFromMissing(
            listOfNotNull(
                "homePersonalizeAnchorClasses".takeIf { symbols[HomeAnchorsContract.homePersonalizeAnchorClasses].isNullOrEmpty() },
                "homeRightSlotClass".takeIf { symbols[HomeRightSlotContract.homeRightSlotClass].isNullOrBlank() },
                "homeRightSlotStateMethods".takeIf { symbols[HomeRightSlotContract.homeRightSlotStateMethods].isNullOrEmpty() },
            ),
        )
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "HomeTopBarRightSlotHook",
            "${symbols[HomeRightSlotContract.homeRightSlotClass]}.{${listTarget(symbols[HomeRightSlotContract.homeRightSlotStateMethods])}}",
            listOf(
                HomeRightSlotContract.homeRightSlotClass.check(symbols),
                HomeRightSlotContract.homeRightSlotStateMethods.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("HomeTopBarRightSlotHook", false, listOf(HookFeatureKey.BLOCK_AD_HOME_TOP_BAR)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasHomeRightSlotSymbols =
            symbols[HomeRightSlotContract.homeRightSlotClass] != null ||
                symbols[HomeRightSlotContract.homeRightSlotStateMethods] != null
        if (hasHomeRightSlotSymbols && !isHomeRightSlotValid(symbols, cl)) return false
        return true
    }

    private fun isHomeRightSlotValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[HomeRightSlotContract.homeRightSlotClass] ?: return false
        val stateMethods = symbols[HomeRightSlotContract.homeRightSlotStateMethods].orEmpty()
        if (stateMethods.isEmpty()) return false
        return try {
            val slotClass = ScanReflection.safeFindClass(className, cl) ?: return false
            stateMethods.all { methodName ->
                slotClass.declaredMethods.any { method ->
                    method.name == methodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.isEmpty()
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}
