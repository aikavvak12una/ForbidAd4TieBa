package com.forbidad4tieba.hook.symbol.scan

import android.view.ViewGroup
import android.widget.LinearLayout
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier

internal object PbTopEnterBarSymbolScanner {
    private const val NAVIGATION_BAR = "com.baidu.tbadk.core.view.NavigationBar"
    private const val CONTROL_ALIGN = "$NAVIGATION_BAR\$ControlAlign"
    private const val FORUM_ICON = "com.baidu.tbadk.core.view.PbBarImageView"
    private const val LINEAR_LAYOUT = "android.widget.LinearLayout"

    fun scan(cl: ClassLoader, logger: ScanLogger?): PbTopEnterBarScanSymbols {
        val candidates = ScanDexQueries.classesWithFieldType(FORUM_ICON, logger).flatMap { owner ->
            val methods = ScanDexQueries.methods(owner, logger)
            methods.filter { method ->
                !Modifier.isStatic(method.modifiers) && !method.isConstructor &&
                    method.returnTypeName == "void" && method.paramTypeNames.isEmpty() &&
                    method.invokes.any {
                        it.declaredClassName == NAVIGATION_BAR && it.methodName == "addCustomView" &&
                            it.returnTypeName == "android.view.View" &&
                            it.paramTypeNames == listOf(CONTROL_ALIGN, "int", "android.view.View\$OnClickListener")
                    } && method.usingFields.any {
                        it.usingType == FieldUsingType.Read &&
                            it.field.declaredClassName == CONTROL_ALIGN && it.field.name == "HORIZONTAL_LEFT"
                    } && method.instanceFields(owner, FORUM_ICON, FieldUsingType.Write).size == 1
            }.flatMap { initialize ->
                val createdContainers = initialize.instanceFields(owner, LINEAR_LAYOUT, FieldUsingType.Write)
                    .map { it.name }.toSet()
                // The forum container is both created here and controlled by the host's visibility setter.
                // The inner padding layout and the separate breadcrumb container do not share this path.
                val containers = methods.filter { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnTypeName == "void" &&
                        method.paramTypeNames == listOf("boolean") && method.calls(LINEAR_LAYOUT, "setVisibility")
                }.flatMap { it.instanceFields(owner, LINEAR_LAYOUT, FieldUsingType.Read) }
                    .map { it.name }.distinct().filter { it in createdContainers }
                val navigationFields = initialize.instanceFields(owner, NAVIGATION_BAR, FieldUsingType.Read)
                containers.flatMap { container ->
                    navigationFields.map { navigation ->
                        PbTopEnterBarScanSymbols(owner, initialize.methodName, container, navigation.name)
                    }
                }
            }
        }.distinct()
        return selectValidatedCandidate(candidates, cl, logger)
    }

    internal fun selectValidatedCandidate(
        candidates: List<PbTopEnterBarScanSymbols>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbTopEnterBarScanSymbols {
        val valid = candidates.filter { candidate ->
            scanSubStep("PbTopEnterBarBlockHook.Validate", logger, false) {
                restore(candidate, cl) != null
            }
        }
        return uniqueSemanticCandidate("pbTopEnterBar", valid, logger) ?: PbTopEnterBarScanSymbols()
    }

    internal fun restore(candidate: PbTopEnterBarScanSymbols, cl: ClassLoader): PbTopEnterBarSymbols? {
        val ownerName = candidate.controllerClass ?: return null
        val methodName = candidate.initializeMethod ?: return null
        val containerName = candidate.containerField ?: return null
        val navigationName = candidate.navigationBarField ?: return null
        val owner = Class.forName(ownerName, false, cl)
        if (owner.isInterface || Modifier.isAbstract(owner.modifiers)) return null
        val initialize = owner.getDeclaredMethod(methodName)
        val container = owner.getDeclaredField(containerName)
        val navigation = owner.getDeclaredField(navigationName)
        if (Modifier.isStatic(initialize.modifiers) || Modifier.isAbstract(initialize.modifiers) ||
            Modifier.isNative(initialize.modifiers) || initialize.returnType != Void.TYPE ||
            Modifier.isStatic(container.modifiers) || container.type != LinearLayout::class.java ||
            Modifier.isStatic(navigation.modifiers) || navigation.type.name != NAVIGATION_BAR ||
            !ViewGroup::class.java.isAssignableFrom(navigation.type) ||
            owner.declaredFields.count { !Modifier.isStatic(it.modifiers) && it.type.name == FORUM_ICON } != 1
        ) return null
        initialize.isAccessible = true
        container.isAccessible = true
        navigation.isAccessible = true
        return PbTopEnterBarSymbols(initialize, container, navigation)
    }
}
