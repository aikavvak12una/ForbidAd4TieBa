package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal const val MAIN_TAB_DYNAMIC_ICON_DATA_CLASS = "com.baidu.tbadk.mainTab.dynamicIcon.DynamicIconData"

internal class MainTabBottomLevel1Rule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tbadk.mainTab.MaintabAddResponedData")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.mainTab.MaintabAddResponedData") return null
        if (cls.declaredFields.none { !Modifier.isStatic(it.modifiers) && Context::class.java.isAssignableFrom(it.type) }) return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
        val add = methods.singleOrNull { it.name == "addFragment" && it.returnType == Void.TYPE && it.parameterCount == 1 } ?: return null
        val get = methods.singleOrNull { it.name == "getList" && it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) } ?: return null
        val structure = add.parameterTypes.single().declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && it.name == "getFragmentTabStructure" && it.parameterCount == 0 && !it.returnType.isPrimitive
        } ?: return null
        val fields = structure.returnType.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        val type = fields.singleOrNull { it.name == "type" && it.type == Int::class.javaPrimitiveType } ?: return null
        val fragment = fields.singleOrNull { ScanReflection.isFragmentLikeType(it.type) }
        val icon = fields.singleOrNull { it.type == ScanReflection.safeFindClass(MAIN_TAB_DYNAMIC_ICON_DATA_CLASS, cl) }
        return ScanMatch(
            cls.name, listOf(add.name, get.name, structure.name).joinToString(","),
            listOf(type.name, icon?.name.orEmpty(), fragment?.name.orEmpty()).joinToString(","), 140,
        )
    }
}
