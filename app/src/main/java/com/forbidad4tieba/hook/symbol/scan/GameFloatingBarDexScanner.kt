package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexGameFloatingBarMatch
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

internal object GameFloatingBarDexScanner {
    private const val BAR_CLASS = "com.baidu.tieba.feed.component.view.TbFloatingBar"

    fun match(method: MethodData): DexGameFloatingBarMatch? {
        if (Modifier.isStatic(method.modifiers) || method.methodName == "<init>" ||
            method.returnTypeName != "void" || method.paramCount != 0) return null
        val calls = method.invokes
        // The entry binds game data after checking installation; animation-only methods are different targets.
        val bindsGame = calls.any {
            it.declaredClassName == BAR_CLASS && it.returnTypeName == "void" && it.paramCount == 1
        }
        val checksInstalledGame = calls.any {
            it.declaredClassName == "com.baidu.tbadk.core.util.UtilHelper" && it.methodName == "isInstalledPackage"
        }
        if (!bindsGame || !checksInstalledGame) return null
        val fields = method.usingFields.filter {
            it.usingType.isRead() && it.field.declaredClassName == method.declaredClassName &&
                it.field.typeName == BAR_CLASS
        }.map { it.field.fieldName }.distinct()
        val field = fields.singleOrNull() ?: return null
        return DexGameFloatingBarMatch(method.declaredClassName, method.methodName, field, 300,
            "TbFloatingBar.bind,UtilHelper.isInstalledPackage")
    }
}
