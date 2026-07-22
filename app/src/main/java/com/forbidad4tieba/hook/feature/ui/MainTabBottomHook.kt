package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.symbol.model.MainTabBottomSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object MainTabBottomHook {
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

    private val resolveErrorLogCount = AtomicInteger(0)
    private val unknownTabLogCount = AtomicInteger(0)

    internal fun hook(symbols: MainTabBottomSymbols) {
        val mod = XposedCompat.module ?: return
        try {
            hookAddFragment(mod, symbols)
            hookGetList(mod, symbols)
            XposedCompat.log(
                "[MainTabBottomHook] hook INSTALLED: " +
                    "${symbols.dataClass.name}.${symbols.addMethod.name}/${symbols.getListMethod.name}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[MainTabBottomHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun hookAddFragment(
        mod: io.github.libxposed.api.XposedModule,
        symbols: MainTabBottomSymbols,
    ) {
        mod.hook(symbols.addMethod).intercept { chain ->
            val currentSelection = currentSelectionOrNull()
            val delegate = chain.args.firstOrNull()
            if (delegate != null && currentSelection != null) {
                val identity = resolveTabIdentity(delegate, symbols)
                if (shouldFilterOut(identity.targetTab, currentSelection)) {
                    XposedCompat.logD {
                        "[MainTabBottomHook] blocked addFragment: tab=${identity.targetTab}, " +
                            "type=${identity.type}, dynamic=${identity.hasDynamicIcon}, " +
                            "fragment=${identity.fragmentClassName}"
                    }
                    return@intercept null
                }
            }
            chain.proceed()
        }
    }

    private fun hookGetList(
        mod: io.github.libxposed.api.XposedModule,
        symbols: MainTabBottomSymbols,
    ) {
        mod.hook(symbols.getListMethod).intercept { chain ->
            val result = chain.proceed()
            val currentSelection = currentSelectionOrNull() ?: return@intercept result
            @Suppress("UNCHECKED_CAST")
            val list = result as? MutableList<Any?>
            if (list != null) {
                val it = list.iterator()
                while (it.hasNext()) {
                    val delegate = it.next() ?: continue
                    val identity = resolveTabIdentity(delegate, symbols)
                    if (shouldFilterOut(identity.targetTab, currentSelection)) {
                        it.remove()
                        XposedCompat.logD {
                            "[MainTabBottomHook] removed from getList: tab=${identity.targetTab}, " +
                                "type=${identity.type}, dynamic=${identity.hasDynamicIcon}, " +
                                "fragment=${identity.fragmentClassName}"
                        }
                    }
                }
            }
            result
        }
    }

    private fun currentSelectionOrNull(): ConfigManager.BottomTabSelection? {
        val settings = ConfigManager.snapshot()
        if (!settings.isBottomTabsCustomEnabled) return null
        return ConfigManager.normalizeBottomTabSelection(
            ConfigManager.BottomTabSelection(
                homeEnabled = settings.isBottomTabHomeEnabled,
                enterForumEnabled = settings.isBottomTabEnterForumEnabled,
                retailStoreEnabled = settings.isBottomTabRetailStoreEnabled,
                messageEnabled = settings.isBottomTabMessageEnabled,
                mineEnabled = settings.isBottomTabMineEnabled,
            ),
        )
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
        symbols: MainTabBottomSymbols,
    ): TabIdentity {
        val structure = try {
            symbols.structureMethod.invoke(delegate)
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
        val type = resolveStructureType(structure, symbols.structureTypeField)
        val hasDynamic = resolveDynamicIconPresent(structure, symbols.structureDynamicIconField)
        val fragmentClassName = resolveFragmentClassName(structure, symbols.structureFragmentField)
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

    private fun resolveStructureType(structure: Any, typeField: Field): Int {
        return try {
            typeField.getInt(structure)
        } catch (t: Throwable) {
            logResolveError("resolve type failed: ${t.message}")
            -1
        }
    }

    private fun resolveDynamicIconPresent(structure: Any, field: Field?): Boolean? {
        field ?: return null
        return try {
            field.get(structure) != null
        } catch (t: Throwable) {
            logResolveError("resolve dynamic icon failed: ${t.message}")
            null
        }
    }

    private fun resolveFragmentClassName(structure: Any, field: Field?): String? {
        field ?: return null
        return try {
            field.get(structure)?.javaClass?.name
        } catch (t: Throwable) {
            logResolveError("resolve fragment failed: ${t.message}")
            null
        }
    }

    private fun logResolveError(message: String) {
        val count = resolveErrorLogCount.incrementAndGet()
        if (count <= 3) {
            XposedCompat.logD { "[MainTabBottomHook] $message" }
        }
    }
}
