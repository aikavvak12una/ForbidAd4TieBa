package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.MainTabBottomScanSymbols
import com.forbidad4tieba.hook.symbol.model.MainTabBottomSymbols
import com.forbidad4tieba.hook.symbol.scan.MainTabBottomSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Field

/** Owns the cached descriptors and host rules for this capability. */
object MainTabsContract : SymbolContract("MainTabs") {
    val mainTabDataClass = text("mainTabDataClass")
    val mainTabAddMethod = text("mainTabAddMethod")
    val mainTabGetListMethod = text("mainTabGetListMethod")
    val mainTabDelegateGetStructureMethod = text("mainTabDelegateGetStructureMethod")
    val mainTabStructureTypeField = text("mainTabStructureTypeField")
    val mainTabStructureDynamicIconField = text("mainTabStructureDynamicIconField")
    val mainTabStructureFragmentField = text("mainTabStructureFragmentField")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val mainTabBottomScan = runScanStep(
            "MainTabBottomHook",
            logger,
            scanErrors,
            MainTabBottomScanSymbols(),
        ) {
            MainTabBottomSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        val mainTabDataClass: String? = mainTabBottomScan.dataClass

        val mainTabAddMethod: String? = mainTabBottomScan.addMethod

        val mainTabGetListMethod: String? = mainTabBottomScan.getListMethod

        val mainTabDelegateGetStructureMethod: String? = mainTabBottomScan.delegateGetStructureMethod

        val mainTabStructureTypeField: String? = mainTabBottomScan.structureTypeField

        val mainTabStructureDynamicIconField: String? = mainTabBottomScan.structureDynamicIconField

        val mainTabStructureFragmentField: String? = mainTabBottomScan.structureFragmentField

        output[MainTabsContract.mainTabDataClass] = mainTabDataClass
        output[MainTabsContract.mainTabAddMethod] = mainTabAddMethod
        output[MainTabsContract.mainTabGetListMethod] = mainTabGetListMethod
        output[MainTabsContract.mainTabDelegateGetStructureMethod] = mainTabDelegateGetStructureMethod
        output[MainTabsContract.mainTabStructureTypeField] = mainTabStructureTypeField
        output[MainTabsContract.mainTabStructureDynamicIconField] = mainTabStructureDynamicIconField
        output[MainTabsContract.mainTabStructureFragmentField] = mainTabStructureFragmentField
    }

    fun resolveMainTabBottomSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): MainTabBottomSymbols? {
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
                Diagnostics.log("[MainTabBottomHook] skipped: scan symbols unavailable")
                return null
            }
            val dataClassName = resolvedSymbols[MainTabsContract.mainTabDataClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: missing mainTabDataClass")
                return null
            }
            val addMethodName = resolvedSymbols[MainTabsContract.mainTabAddMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: missing mainTabAddMethod")
                return null
            }
            val getListMethodName = resolvedSymbols[MainTabsContract.mainTabGetListMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: missing mainTabGetListMethod")
                return null
            }
            val structureMethodName =
                resolvedSymbols[MainTabsContract.mainTabDelegateGetStructureMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[MainTabBottomHook] skipped: missing mainTabDelegateGetStructureMethod")
                    return null
                }
            val typeFieldName = resolvedSymbols[MainTabsContract.mainTabStructureTypeField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: missing mainTabStructureTypeField")
                return null
            }

            val dataClass = ScanReflection.safeFindClass(dataClassName, cl) ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: class not found: $dataClassName")
                return null
            }
            val addMethod = ScanReflection.collectInstanceMethods(dataClass).singleOrNull { candidate ->
                candidate.name == addMethodName &&
                    candidate.returnType == Void.TYPE &&
                    candidate.parameterTypes.size == 1
            } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: method mismatch: $dataClassName.$addMethodName(*)")
                return null
            }
            val getListMethod = ScanReflection.collectInstanceMethods(dataClass).singleOrNull { candidate ->
                candidate.name == getListMethodName &&
                    candidate.parameterTypes.isEmpty() &&
                    List::class.java.isAssignableFrom(candidate.returnType)
            } ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: method mismatch: $dataClassName.$getListMethodName()")
                return null
            }
            val delegateClass = addMethod.parameterTypes.firstOrNull() ?: run {
                Diagnostics.log("[MainTabBottomHook] skipped: add method delegate parameter missing")
                return null
            }
            val structureMethod = ScanReflection.collectInstanceMethods(delegateClass).singleOrNull { candidate ->
                candidate.name == structureMethodName &&
                    candidate.parameterTypes.isEmpty() &&
                    !candidate.returnType.isPrimitive
            } ?: run {
                Diagnostics.log(
                    "[MainTabBottomHook] skipped: method mismatch: " +
                        "${delegateClass.name}.$structureMethodName()",
                )
                return null
            }
            val structureClass = structureMethod.returnType
            val typeField = namedFieldInHierarchy(structureClass, typeFieldName)
                ?.takeIf { it.type == Int::class.javaPrimitiveType }
                ?.apply { isAccessible = true }
                ?: run {
                    Diagnostics.log(
                        "[MainTabBottomHook] skipped: field mismatch: " +
                            "${structureClass.name}.$typeFieldName",
                    )
                    return null
                }
            val dynamicIconField = resolvedSymbols[MainTabsContract.mainTabStructureDynamicIconField]
                ?.takeIf { it.isNotBlank() }
                ?.let { fieldName ->
                    namedFieldInHierarchy(structureClass, fieldName)
                        ?.apply { isAccessible = true }
                        ?: run {
                            Diagnostics.logD {
                                "[MainTabBottomHook] optional field missing: ${structureClass.name}.$fieldName"
                            }
                            null
                        }
                }
            val fragmentField = resolvedSymbols[MainTabsContract.mainTabStructureFragmentField]
                ?.takeIf { it.isNotBlank() }
                ?.let { fieldName ->
                    namedFieldInHierarchy(structureClass, fieldName)
                        ?.apply { isAccessible = true }
                        ?: run {
                            Diagnostics.logD {
                                "[MainTabBottomHook] optional field missing: ${structureClass.name}.$fieldName"
                            }
                            null
                        }
                }

            addMethod.isAccessible = true
            getListMethod.isAccessible = true
            structureMethod.isAccessible = true
            MainTabBottomSymbols(
                dataClass = dataClass,
                addMethod = addMethod,
                getListMethod = getListMethod,
                structureMethod = structureMethod,
                structureTypeField = typeField,
                structureDynamicIconField = dynamicIconField,
                structureFragmentField = fragmentField,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[MainTabBottomHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val bottomCritical = ArrayList<String>(5)
        val bottomOptional = ArrayList<String>(2)
        if (symbols[MainTabsContract.mainTabDataClass].isNullOrBlank()) bottomCritical.add("mainTabDataClass")
        if (symbols[MainTabsContract.mainTabAddMethod].isNullOrBlank()) bottomCritical.add("mainTabAddMethod")
        if (symbols[MainTabsContract.mainTabGetListMethod].isNullOrBlank()) bottomCritical.add("mainTabGetListMethod")
        if (symbols[MainTabsContract.mainTabDelegateGetStructureMethod].isNullOrBlank()) {
            bottomCritical.add("mainTabDelegateGetStructureMethod")
        }
        if (symbols[MainTabsContract.mainTabStructureTypeField].isNullOrBlank()) bottomCritical.add("mainTabStructureTypeField")
        if (symbols[MainTabsContract.mainTabStructureDynamicIconField].isNullOrBlank()) {
            bottomOptional.add("mainTabStructureDynamicIconField")
        }
        if (symbols[MainTabsContract.mainTabStructureFragmentField].isNullOrBlank()) {
            bottomOptional.add("mainTabStructureFragmentField")
        }
        out[HookFeatureKey.SIMPLIFY_BOTTOM_TABS] = when {
            bottomCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = bottomCritical,
                missingOptional = bottomOptional,
            )
            bottomOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = bottomOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "MainTabBottomHook",
            "${symbols[MainTabsContract.mainTabDataClass]}.${symbols[MainTabsContract.mainTabAddMethod]}/${symbols[MainTabsContract.mainTabGetListMethod]}",
            listOf(
                MainTabsContract.mainTabDataClass.check(symbols),
                MainTabsContract.mainTabAddMethod.check(symbols),
                MainTabsContract.mainTabGetListMethod.check(symbols),
                MainTabsContract.mainTabDelegateGetStructureMethod.check(symbols),
                MainTabsContract.mainTabStructureTypeField.check(symbols),
            ),
        )
        add(
            "MainTabBottomHook.StructureFields",
            "${symbols[MainTabsContract.mainTabDelegateGetStructureMethod]}[${symbols[MainTabsContract.mainTabStructureDynamicIconField]},${symbols[MainTabsContract.mainTabStructureFragmentField]}]",
            listOf(
                MainTabsContract.mainTabStructureDynamicIconField.check(symbols),
                MainTabsContract.mainTabStructureFragmentField.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("MainTabBottomHook", true, listOf(HookFeatureKey.SIMPLIFY_BOTTOM_TABS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasMainTabBottomSymbols =
            symbols[MainTabsContract.mainTabDataClass] != null ||
                symbols[MainTabsContract.mainTabAddMethod] != null ||
                symbols[MainTabsContract.mainTabGetListMethod] != null ||
                symbols[MainTabsContract.mainTabDelegateGetStructureMethod] != null ||
                symbols[MainTabsContract.mainTabStructureTypeField] != null ||
                symbols[MainTabsContract.mainTabStructureDynamicIconField] != null ||
                symbols[MainTabsContract.mainTabStructureFragmentField] != null
        if (hasMainTabBottomSymbols && !isMainTabBottomValid(symbols, cl)) return false
        return true
    }

    private fun isMainTabBottomValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val dataClassName = symbols[MainTabsContract.mainTabDataClass] ?: return false
        val addMethodName = symbols[MainTabsContract.mainTabAddMethod] ?: return false
        val getListMethodName = symbols[MainTabsContract.mainTabGetListMethod] ?: return false
        val structureMethodName = symbols[MainTabsContract.mainTabDelegateGetStructureMethod] ?: return false
        val typeFieldName = symbols[MainTabsContract.mainTabStructureTypeField] ?: return false
        return try {
            val dataClass = ScanReflection.safeFindClass(dataClassName, cl) ?: return false
            val addMethod = dataClass.declaredMethods.firstOrNull { method ->
                method.name == addMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1
            } ?: return false
            dataClass.declaredMethods.firstOrNull { method ->
                method.name == getListMethodName &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isListType(method.returnType)
            } ?: return false
            val delegateClass = addMethod.parameterTypes.firstOrNull() ?: return false
            val structureMethod = delegateClass.declaredMethods.firstOrNull { method ->
                method.name == structureMethodName &&
                    method.parameterTypes.isEmpty() &&
                    !method.returnType.isPrimitive
            } ?: return false
            val structureClass = structureMethod.returnType
            val hasTypeField = ScanReflection.collectInstanceFields(structureClass).any { field ->
                field.name == typeFieldName && field.type == Int::class.javaPrimitiveType
            }
            if (!hasTypeField) return false
            val dynamicName = symbols[MainTabsContract.mainTabStructureDynamicIconField]
            if (!dynamicName.isNullOrBlank()) {
                val hasDynamic = ScanReflection.collectInstanceFields(structureClass).any { it.name == dynamicName }
                if (!hasDynamic) return false
            }
            val fragmentName = symbols[MainTabsContract.mainTabStructureFragmentField]
            if (!fragmentName.isNullOrBlank()) {
                val hasFragment = ScanReflection.collectInstanceFields(structureClass).any { it.name == fragmentName }
                if (!hasFragment) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
