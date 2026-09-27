package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.ViewGroup
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class PbFallingViewRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) = listOf(StableTiebaHookPoints.PB_FALLING_VIEW_CLASS)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != StableTiebaHookPoints.PB_FALLING_VIEW_CLASS || !ViewGroup::class.java.isAssignableFrom(cls)) return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
        val init = uniqueSemanticCandidate("PbFalling.Init", methods.filter {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                ScanDexQueries.method(it, logger)?.invokes?.any { call -> call.methodName == "addView" } == true
        }, logger) ?: return null
        val show = uniqueSemanticCandidate("PbFalling.Show", methods.filter {
            it.returnType == Void.TYPE && it.parameterTypes.map { type -> type.name } == listOf(
                "com.baidu.tbadk.data.AdverSegmentData", "com.baidu.tbadk.TbPageContext", "int", "boolean",
            )
        }, logger) ?: return null
        val clear = uniqueSemanticCandidate("PbFalling.Clear", methods.filter {
            it.returnType == Void.TYPE && it.parameterCount == 0 && ScanDexQueries.method(it, logger)?.let { dex ->
                dex.calls("android.animation.AnimatorSet", "cancel") && dex.calls("android.animation.AnimatorSet", "removeAllListeners")
            } == true
        }, logger) ?: return null
        return ScanMatch(cls.name, "${init.name},${show.name},${clear.name}", "", 140)
    }
}
