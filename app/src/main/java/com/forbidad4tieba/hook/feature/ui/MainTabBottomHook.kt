package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object MainTabBottomHook {
    private data class RuntimeTargets(
        val dataClassName: String,
        val addMethodName: String,
        val getListMethodName: String,
        val delegateGetStructureMethodName: String,
        val structureTypeFieldName: String,
        val structureDynamicIconFieldName: String?,
        val structureFragmentFieldName: String?,
    )

    private data class TabIdentity(
        val targetTab: BottomTargetTab,
        val type: Int,
        val hasDynamicIcon: Boolean?,
        val fragmentClassName: String?,
    )

    private enum class BottomTargetTab {
        HOME,
        ENTER_FORUM,
        RETAIL_STORE,
        MESSAGE,
        MINE,
        OTHER,
    }

    private val typeFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val dynamicFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val fragmentFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val resolveErrorLogCount = AtomicInteger(0)
    private val unknownTabLogCount = AtomicInteger(0)
    private val invalidSelectionLogged = AtomicBoolean(false)

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val targets = resolveTargets(symbols)
        if (targets == null) {
            XposedCompat.log(
                "[MainTabBottomHook] skipped: scan symbols missing " +
                    "(mainTabDataClass/mainTabAddMethod/mainTabGetListMethod/" +
                    "mainTabDelegateGetStructureMethod/mainTabStructureTypeField)",
            )
            return
        }

        val mod = XposedCompat.module ?: return
        try {
            val dataClass = XposedCompat.findClassOrNull(targets.dataClassName, cl)
            if (dataClass == null) {
                XposedCompat.log("[MainTabBottomHook] class NOT FOUND: ${targets.dataClassName}")
                return
            }
            val addMethod = resolveAddMethod(dataClass, targets.addMethodName)
            val getListMethod = resolveGetListMethod(dataClass, targets.getListMethodName)
            if (addMethod == null || getListMethod == null) {
                XposedCompat.log(
                    "[MainTabBottomHook] hook point missing: add=${targets.addMethodName}, " +
                        "getList=${targets.getListMethodName}",
                )
                return
            }
            val delegateClass = addMethod.parameterTypes.firstOrNull()
            if (delegateClass == null) {
                XposedCompat.log("[MainTabBottomHook] add method delegate parameter missing")
                return
            }
            val structureMethod = resolveStructureMethod(delegateClass, targets.delegateGetStructureMethodName)
            if (structureMethod == null) {
                XposedCompat.log(
                    "[MainTabBottomHook] delegate structure method NOT FOUND: " +
                        "${delegateClass.name}.${targets.delegateGetStructureMethodName}",
                )
                return
            }
            prewarmStructureFields(structureMethod.returnType, targets)

            hookAddFragment(mod, addMethod, structureMethod, targets)
            hookGetList(mod, getListMethod, structureMethod, targets)
            XposedCompat.log(
                "[MainTabBottomHook] hook INSTALLED: ${dataClass.name}.${addMethod.name}/${getListMethod.name}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[MainTabBottomHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val dataClassName = scanSymbols.mainTabDataClass?.takeIf { it.isNotBlank() } ?: return null
        val addMethodName = scanSymbols.mainTabAddMethod?.takeIf { it.isNotBlank() } ?: return null
        val getListMethodName = scanSymbols.mainTabGetListMethod?.takeIf { it.isNotBlank() } ?: return null
        val delegateStructureMethodName =
            scanSymbols.mainTabDelegateGetStructureMethod?.takeIf { it.isNotBlank() } ?: return null
        val structureTypeFieldName = scanSymbols.mainTabStructureTypeField?.takeIf { it.isNotBlank() } ?: return null
        return RuntimeTargets(
            dataClassName = dataClassName,
            addMethodName = addMethodName,
            getListMethodName = getListMethodName,
            delegateGetStructureMethodName = delegateStructureMethodName,
            structureTypeFieldName = structureTypeFieldName,
            structureDynamicIconFieldName = scanSymbols.mainTabStructureDynamicIconField?.takeIf { it.isNotBlank() },
            structureFragmentFieldName = scanSymbols.mainTabStructureFragmentField?.takeIf { it.isNotBlank() },
        )
    }

    private fun resolveAddMethod(dataClass: Class<*>, methodName: String): Method? {
        val method = findMethodInHierarchy(dataClass) { candidate ->
            candidate.name == methodName &&
                candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 1
        } ?: return null
        method.isAccessible = true
        return method
    }

    private fun resolveGetListMethod(dataClass: Class<*>, methodName: String): Method? {
        val method = findMethodInHierarchy(dataClass) { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.isEmpty() &&
                List::class.java.isAssignableFrom(candidate.returnType)
        } ?: return null
        method.isAccessible = true
        return method
    }

    private fun resolveStructureMethod(delegateClass: Class<*>, methodName: String): Method? {
        val method = findMethodInHierarchy(delegateClass) { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.isEmpty() &&
                !candidate.returnType.isPrimitive
        } ?: return null
        method.isAccessible = true
        return method
    }

    private fun hookAddFragment(
        mod: io.github.libxposed.api.XposedModule,
        addMethod: Method,
        structureMethod: Method,
        targets: RuntimeTargets,
    ) {
        mod.hook(addMethod).intercept { chain ->
            if (ConfigManager.isBottomTabsCustomEnabled) {
                val selection = resolveCurrentSelection()
                val delegate = chain.args.firstOrNull()
                if (delegate != null) {
                    val identity = resolveTabIdentity(delegate, structureMethod, targets)
                    if (shouldFilterOut(identity.targetTab, selection)) {
                        XposedCompat.logD {
                            "[MainTabBottomHook] blocked addFragment: tab=${identity.targetTab}, " +
                                "type=${identity.type}, dynamic=${identity.hasDynamicIcon}, " +
                                "fragment=${identity.fragmentClassName}"
                        }
                        return@intercept null
                    }
                }
            }
            chain.proceed()
        }
    }

    private fun hookGetList(
        mod: io.github.libxposed.api.XposedModule,
        getListMethod: Method,
        structureMethod: Method,
        targets: RuntimeTargets,
    ) {
        mod.hook(getListMethod).intercept { chain ->
            val result = chain.proceed()
            if (ConfigManager.isBottomTabsCustomEnabled) {
                val selection = resolveCurrentSelection()
                @Suppress("UNCHECKED_CAST")
                val list = result as? MutableList<Any?>
                if (list != null) {
                    val it = list.iterator()
                    while (it.hasNext()) {
                        val delegate = it.next() ?: continue
                        val identity = resolveTabIdentity(delegate, structureMethod, targets)
                        if (shouldFilterOut(identity.targetTab, selection)) {
                            it.remove()
                            XposedCompat.logD {
                                "[MainTabBottomHook] removed from getList: tab=${identity.targetTab}, " +
                                    "type=${identity.type}, dynamic=${identity.hasDynamicIcon}, " +
                                    "fragment=${identity.fragmentClassName}"
                            }
                        }
                    }
                }
            }
            result
        }
    }

    private fun resolveCurrentSelection(): ConfigManager.BottomTabSelection {
        val raw = ConfigManager.BottomTabSelection(
            homeEnabled = ConfigManager.isBottomTabHomeEnabled,
            enterForumEnabled = ConfigManager.isBottomTabEnterForumEnabled,
            retailStoreEnabled = ConfigManager.isBottomTabRetailStoreEnabled,
            messageEnabled = ConfigManager.isBottomTabMessageEnabled,
            mineEnabled = ConfigManager.isBottomTabMineEnabled,
        )
        val normalized = ConfigManager.normalizeBottomTabSelection(raw)
        if (raw != normalized) {
            if (invalidSelectionLogged.compareAndSet(false, true)) {
                XposedCompat.log(
                    "[MainTabBottomHook] invalid bottom-tab selection detected; " +
                        "fallback to home-only",
                )
            }
        } else {
            invalidSelectionLogged.set(false)
        }
        return normalized
    }

    private fun shouldFilterOut(
        targetTab: BottomTargetTab,
        selection: ConfigManager.BottomTabSelection,
    ): Boolean {
        return when (targetTab) {
            BottomTargetTab.HOME -> !selection.homeEnabled
            BottomTargetTab.ENTER_FORUM -> !selection.enterForumEnabled
            BottomTargetTab.RETAIL_STORE -> !selection.retailStoreEnabled
            BottomTargetTab.MESSAGE -> !selection.messageEnabled
            BottomTargetTab.MINE -> !selection.mineEnabled
            BottomTargetTab.OTHER -> false
        }
    }

    private fun resolveTabIdentity(
        delegate: Any,
        structureMethod: Method,
        targets: RuntimeTargets,
    ): TabIdentity {
        val structure = try {
            structureMethod.invoke(delegate)
        } catch (t: Throwable) {
            logResolveError("resolve structure failed: ${t.message}")
            null
        }
        if (structure == null) {
            return TabIdentity(
                targetTab = BottomTargetTab.OTHER,
                type = -1,
                hasDynamicIcon = null,
                fragmentClassName = null,
            )
        }
        val type = resolveStructureType(structure, targets.structureTypeFieldName)
        val hasDynamic = resolveDynamicIconPresent(structure, targets.structureDynamicIconFieldName)
        val fragmentClassName = resolveFragmentClassName(structure, targets.structureFragmentFieldName)
        val targetTab = resolveTargetTab(type, hasDynamic, fragmentClassName)
        if (targetTab == BottomTargetTab.OTHER) {
            val count = unknownTabLogCount.incrementAndGet()
            if (count <= 8) {
                XposedCompat.logD {
                    "[MainTabBottomHook] unresolved tab identity: type=$type, " +
                        "dynamic=$hasDynamic, fragment=$fragmentClassName, structure=${structure.javaClass.name}"
                }
            }
        }
        return TabIdentity(
            targetTab = targetTab,
            type = type,
            hasDynamicIcon = hasDynamic,
            fragmentClassName = fragmentClassName,
        )
    }

    private fun resolveTargetTab(type: Int, hasDynamicIcon: Boolean?, fragmentClassName: String?): BottomTargetTab {
        val fragment = fragmentClassName?.lowercase(Locale.ROOT).orEmpty()
        if (fragment.contains("personcenter") || fragment.contains("personinfo")) return BottomTargetTab.MINE
        if (fragment.contains("enterforum")) return BottomTargetTab.ENTER_FORUM
        if (fragment.contains("recommend") || fragment.contains("homepage")) return BottomTargetTab.HOME
        if (fragment.contains("msgcenter") || fragment.contains("messagecenter")) return BottomTargetTab.MESSAGE
        if (fragment.contains("retailstore")) return BottomTargetTab.RETAIL_STORE

        if (type == 8) return BottomTargetTab.MINE
        val dynamicReady = hasDynamicIcon == true
        return when (type) {
            1 -> if (dynamicReady || fragment.contains("enterforum")) BottomTargetTab.ENTER_FORUM else BottomTargetTab.OTHER
            2 -> if (dynamicReady || fragment.contains("recommend") || fragment.contains("homepage")) BottomTargetTab.HOME else BottomTargetTab.OTHER
            3 -> if (dynamicReady || fragment.contains("message") || fragment.contains("msg")) BottomTargetTab.MESSAGE else BottomTargetTab.OTHER
            5 -> if (dynamicReady || fragment.contains("retail") || fragment.contains("store")) BottomTargetTab.RETAIL_STORE else BottomTargetTab.OTHER
            else -> BottomTargetTab.OTHER
        }
    }

    private fun resolveStructureType(structure: Any, typeFieldName: String): Int {
        val field = resolveTypeField(structure.javaClass, typeFieldName) ?: return -1
        return try {
            field.getInt(structure)
        } catch (t: Throwable) {
            logResolveError("resolve type failed: ${t.message}")
            -1
        }
    }

    private fun resolveDynamicIconPresent(structure: Any, fieldName: String?): Boolean? {
        if (fieldName.isNullOrBlank()) return null
        val field = resolveDynamicField(structure.javaClass, fieldName) ?: return null
        return try {
            field.get(structure) != null
        } catch (t: Throwable) {
            logResolveError("resolve dynamic icon failed: ${t.message}")
            null
        }
    }

    private fun resolveFragmentClassName(structure: Any, fieldName: String?): String? {
        if (fieldName.isNullOrBlank()) return null
        val field = resolveFragmentField(structure.javaClass, fieldName) ?: return null
        return try {
            (field.get(structure) as? Any)?.javaClass?.name
        } catch (t: Throwable) {
            logResolveError("resolve fragment failed: ${t.message}")
            null
        }
    }

    private fun resolveTypeField(structureClass: Class<*>, fieldName: String): Field? {
        val cached = typeFieldCache[structureClass]
        if (cached != null || typeFieldCache.containsKey(structureClass)) return cached
        val resolved = findFieldInHierarchy(structureClass, fieldName)?.takeIf {
            it.type == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
        typeFieldCache[structureClass] = resolved
        return resolved
    }

    private fun resolveDynamicField(structureClass: Class<*>, fieldName: String): Field? {
        val cached = dynamicFieldCache[structureClass]
        if (cached != null || dynamicFieldCache.containsKey(structureClass)) return cached
        val resolved = findFieldInHierarchy(structureClass, fieldName)?.apply { isAccessible = true }
        dynamicFieldCache[structureClass] = resolved
        return resolved
    }

    private fun resolveFragmentField(structureClass: Class<*>, fieldName: String): Field? {
        val cached = fragmentFieldCache[structureClass]
        if (cached != null || fragmentFieldCache.containsKey(structureClass)) return cached
        val resolved = findFieldInHierarchy(structureClass, fieldName)?.apply { isAccessible = true }
        fragmentFieldCache[structureClass] = resolved
        return resolved
    }

    private fun prewarmStructureFields(structureClass: Class<*>, targets: RuntimeTargets) {
        if (structureClass.isPrimitive || structureClass == Void.TYPE) return
        resolveTypeField(structureClass, targets.structureTypeFieldName)
        targets.structureDynamicIconFieldName?.let { resolveDynamicField(structureClass, it) }
        targets.structureFragmentFieldName?.let { resolveFragmentField(structureClass, it) }
    }

    private fun findMethodInHierarchy(clazz: Class<*>, match: (Method) -> Boolean): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull(match)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: Throwable) {
                current = current.superclass
            }
        }
        return null
    }

    private fun logResolveError(message: String) {
        val count = resolveErrorLogCount.incrementAndGet()
        if (count <= 3) {
            XposedCompat.logD { "[MainTabBottomHook] $message" }
        }
    }
}
