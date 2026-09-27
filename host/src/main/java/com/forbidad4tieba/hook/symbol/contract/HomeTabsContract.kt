package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.symbol.model.HomeTabItemScanSymbols
import com.forbidad4tieba.hook.symbol.model.HomeTabResolvedSymbols
import com.forbidad4tieba.hook.symbol.model.HomeTabScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.HomeTabItemSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.HomeTabSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Field

/** Owns the cached descriptors and host rules for this capability. */
object HomeTabsContract : SymbolContract("HomeTabs") {
    val homeTabClass = text("homeTabClass")
    val homeTabRebuildMethod = text("homeTabRebuildMethod")
    val homeTabListField = text("homeTabListField")
    val homeTabItemTypeField = text("homeTabItemTypeField")
    val homeTabItemCodeField = text("homeTabItemCodeField")
    val homeTabItemNameField = text("homeTabItemNameField")
    val homeTabItemUrlField = text("homeTabItemUrlField")
    val homeTabItemMainSetterMethod = text("homeTabItemMainSetterMethod")
    val homeTabItemMainIntField = text("homeTabItemMainIntField")
    val homeTabItemMainBooleanField = text("homeTabItemMainBooleanField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val homeTabScan = runScanStep(
            "HomeTabHook",
            logger,
            scanErrors,
            HomeTabScanSymbols(),
        ) {
            HomeTabSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        // Dynamic obfuscated symbols used by ad and UI scans.

        val homeItemScan = runScanStep(
            "HomeTabHook.Item",
            logger,
            scanErrors,
            HomeTabItemScanSymbols(),
        ) {
            if (homeTabScan.tabClass != null) {
                HomeTabItemSymbolScanner.scan(homeTabScan, cl, logger)
            } else {
                HomeTabItemScanSymbols()
            }
        }

        val homeTabItemTypeField: String? = homeItemScan.typeField

        val homeTabItemCodeField: String? = homeItemScan.codeField

        val homeTabItemNameField: String? = homeItemScan.nameField

        val homeTabItemUrlField: String? = homeItemScan.urlField

        val homeTabItemMainSetterMethod: String? = homeItemScan.mainSetterMethod

        val homeTabItemMainIntField: String? = homeItemScan.mainIntField

        val homeTabItemMainBooleanField: String? = homeItemScan.mainBooleanField

        output[HomeTabsContract.homeTabClass] = homeTabScan.tabClass
        output[HomeTabsContract.homeTabRebuildMethod] = homeTabScan.rebuildMethod
        output[HomeTabsContract.homeTabListField] = homeTabScan.listField
        output[HomeTabsContract.homeTabItemTypeField] = homeTabItemTypeField
        output[HomeTabsContract.homeTabItemCodeField] = homeTabItemCodeField
        output[HomeTabsContract.homeTabItemNameField] = homeTabItemNameField
        output[HomeTabsContract.homeTabItemUrlField] = homeTabItemUrlField
        output[HomeTabsContract.homeTabItemMainSetterMethod] = homeTabItemMainSetterMethod
        output[HomeTabsContract.homeTabItemMainIntField] = homeTabItemMainIntField
        output[HomeTabsContract.homeTabItemMainBooleanField] = homeTabItemMainBooleanField
    }

    fun resolveHomeTabSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): HomeTabResolvedSymbols? {
        fun requireSymbol(name: String, value: String?): String {
            return value?.takeIf { it.isNotBlank() } ?: error("missing $name")
        }

        fun namedFieldInHierarchy(clazz: Class<*>, fieldName: String): Field? {
            var current: Class<*>? = clazz
            while (current != null && current != Any::class.java) {
                try {
                    return current.getDeclaredField(fieldName)
                } catch (_: NoSuchFieldException) {
                    current = current.superclass
                }
            }
            return null
        }

        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[HomeTabHook] skipped: scan symbols unavailable")
                return null
            }
            val hostClassName = requireSymbol("homeTabClass", resolvedSymbols[HomeTabsContract.homeTabClass])
            val rebuildMethodName = requireSymbol("homeTabRebuildMethod", resolvedSymbols[HomeTabsContract.homeTabRebuildMethod])
            val listFieldName = requireSymbol("homeTabListField", resolvedSymbols[HomeTabsContract.homeTabListField])
            val hostClass = ScanReflection.safeFindClass(hostClassName, cl) ?: run {
                Diagnostics.log("[HomeTabHook] skipped: class not found: $hostClassName")
                return null
            }
            val rebuildMethod = MemberAccess.findMethodOrNull(hostClass, rebuildMethodName) ?: run {
                Diagnostics.log("[HomeTabHook] skipped: method not found: $hostClassName.$rebuildMethodName()")
                return null
            }
            val listField = namedFieldInHierarchy(hostClass, listFieldName)
                ?.takeIf { List::class.java.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }
                ?: run {
                    Diagnostics.log("[HomeTabHook] skipped: field mismatch: $hostClassName.$listFieldName")
                    return null
                }
            rebuildMethod.isAccessible = true
            HomeTabResolvedSymbols(
                hostClass = hostClass,
                rebuildMethod = rebuildMethod,
                listField = listField,
                itemTypeField = requireSymbol("homeTabItemTypeField", resolvedSymbols[HomeTabsContract.homeTabItemTypeField]),
                itemCodeField = requireSymbol("homeTabItemCodeField", resolvedSymbols[HomeTabsContract.homeTabItemCodeField]),
                itemNameField = requireSymbol("homeTabItemNameField", resolvedSymbols[HomeTabsContract.homeTabItemNameField]),
                itemUrlField = requireSymbol("homeTabItemUrlField", resolvedSymbols[HomeTabsContract.homeTabItemUrlField]),
                itemMainSetterMethod = resolvedSymbols[HomeTabsContract.homeTabItemMainSetterMethod]?.takeIf { it.isNotBlank() },
                itemMainIntField = resolvedSymbols[HomeTabsContract.homeTabItemMainIntField]?.takeIf { it.isNotBlank() },
                itemMainBooleanField = resolvedSymbols[HomeTabsContract.homeTabItemMainBooleanField]?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            Diagnostics.log("[HomeTabHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val homeCritical = ArrayList<String>(7)
        val homeOptional = ArrayList<String>(3)
        if (symbols[HomeTabsContract.homeTabClass].isNullOrBlank()) homeCritical.add("homeTabClass")
        if (symbols[HomeTabsContract.homeTabRebuildMethod].isNullOrBlank()) homeCritical.add("homeTabRebuildMethod")
        if (symbols[HomeTabsContract.homeTabListField].isNullOrBlank()) homeCritical.add("homeTabListField")
        if (symbols[HomeTabsContract.homeTabItemTypeField].isNullOrBlank()) homeCritical.add("homeTabItemTypeField")
        if (symbols[HomeTabsContract.homeTabItemCodeField].isNullOrBlank()) homeCritical.add("homeTabItemCodeField")
        if (symbols[HomeTabsContract.homeTabItemNameField].isNullOrBlank()) homeCritical.add("homeTabItemNameField")
        if (symbols[HomeTabsContract.homeTabItemUrlField].isNullOrBlank()) homeCritical.add("homeTabItemUrlField")
        if (symbols[HomeTabsContract.homeTabItemMainSetterMethod].isNullOrBlank()) {
            homeOptional.add("homeTabItemMainSetterMethod")
        }
        if (symbols[HomeTabsContract.homeTabItemMainIntField].isNullOrBlank()) {
            homeOptional.add("homeTabItemMainIntField")
        }
        if (symbols[HomeTabsContract.homeTabItemMainBooleanField].isNullOrBlank()) {
            homeOptional.add("homeTabItemMainBooleanField")
        }
        out[HookFeatureKey.SIMPLIFY_HOME_TOP_TABS] = if (homeCritical.isEmpty() && homeOptional.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else if (homeCritical.isEmpty()) {
            HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = homeOptional,
            )
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeCritical,
                missingOptional = homeOptional,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "HomeTabHook",
            "${symbols[HomeTabsContract.homeTabClass]}.${symbols[HomeTabsContract.homeTabRebuildMethod]}[${symbols[HomeTabsContract.homeTabListField]}]",
            listOf(
                HomeTabsContract.homeTabClass.check(symbols),
                HomeTabsContract.homeTabRebuildMethod.check(symbols),
                HomeTabsContract.homeTabListField.check(symbols),
                HomeTabsContract.homeTabItemTypeField.check(symbols),
                HomeTabsContract.homeTabItemCodeField.check(symbols),
                HomeTabsContract.homeTabItemNameField.check(symbols),
                HomeTabsContract.homeTabItemUrlField.check(symbols),
                HomeTabsContract.homeTabItemMainSetterMethod.check(symbols),
                HomeTabsContract.homeTabItemMainIntField.check(symbols),
                HomeTabsContract.homeTabItemMainBooleanField.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("HomeTabHook", false, listOf(HookFeatureKey.SIMPLIFY_HOME_TOP_TABS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasHomeSymbols =
            symbols[HomeTabsContract.homeTabClass] != null ||
                symbols[HomeTabsContract.homeTabRebuildMethod] != null ||
                symbols[HomeTabsContract.homeTabListField] != null
        if (hasHomeSymbols && !isHomeValid(symbols, cl)) return false
        return true
    }

    private fun isHomeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val homeTabClass = symbols[HomeTabsContract.homeTabClass] ?: return false
        val homeTabRebuildMethod = symbols[HomeTabsContract.homeTabRebuildMethod] ?: return false
        val homeTabListField = symbols[HomeTabsContract.homeTabListField] ?: return false
        return try {
            val homeClass = ScanReflection.safeFindClass(homeTabClass, cl) ?: return false
            val methodOk = homeClass.declaredMethods.any {
                it.name == homeTabRebuildMethod &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Void.TYPE
            }
            if (!methodOk) return false
            val listFieldOk = homeClass.declaredFields.any { it.name == homeTabListField }
            if (!listFieldOk) return false

            val hasHomeItemSymbols =
                !symbols[HomeTabsContract.homeTabItemTypeField].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemCodeField].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemNameField].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemUrlField].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemMainSetterMethod].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemMainIntField].isNullOrBlank() ||
                    !symbols[HomeTabsContract.homeTabItemMainBooleanField].isNullOrBlank()
            if (!hasHomeItemSymbols) return true

            val itemClass = HomeTabItemSymbolScanner.resolveHomeTabItemClass(homeClass, homeTabListField) ?: return false
            val fields = ScanReflection.collectInstanceFields(itemClass)
            val methods = ScanReflection.collectInstanceMethods(itemClass)

            if (!symbols[HomeTabsContract.homeTabItemTypeField].isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols[HomeTabsContract.homeTabItemTypeField] &&
                        (it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType)
                }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemCodeField].isNullOrBlank()) {
                val ok = fields.any { it.name == symbols[HomeTabsContract.homeTabItemCodeField] && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemNameField].isNullOrBlank()) {
                val ok = fields.any { it.name == symbols[HomeTabsContract.homeTabItemNameField] && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemUrlField].isNullOrBlank()) {
                val ok = fields.any { it.name == symbols[HomeTabsContract.homeTabItemUrlField] && it.type == String::class.java }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemMainSetterMethod].isNullOrBlank()) {
                val ok = methods.any { method ->
                    method.name == symbols[HomeTabsContract.homeTabItemMainSetterMethod] &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        (method.parameterTypes[0] == Boolean::class.javaPrimitiveType || method.parameterTypes[0] == Boolean::class.java)
                }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemMainIntField].isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols[HomeTabsContract.homeTabItemMainIntField] &&
                        (it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType)
                }
                if (!ok) return false
            }
            if (!symbols[HomeTabsContract.homeTabItemMainBooleanField].isNullOrBlank()) {
                val ok = fields.any {
                    it.name == symbols[HomeTabsContract.homeTabItemMainBooleanField] &&
                        (it.type == Boolean::class.javaPrimitiveType || it.type == Boolean::class.java)
                }
                if (!ok) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
