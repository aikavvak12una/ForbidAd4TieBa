package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class FreeCopyPopupRule(
    private val hasContentEvidence: (Method, ScanLogger?) -> Boolean = { method, logger ->
        ScanDexQueries.method(method, logger)?.let { dex ->
            dex.invokes.any {
                it.methodName == "setOnClickListener" && it.paramTypeNames == listOf("android.view.View\$OnClickListener")
            } && dex.instanceFields(method.declaringClass.name, "com.baidu.tbadk.core.dialog.RoundLinearLayout", FieldUsingType.Read).size == 1
        } == true
    },
) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesWithFieldType("com.baidu.tbadk.core.dialog.RoundLinearLayout", logger)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        if (fields.count { it.type.name == "com.baidu.tbadk.core.dialog.RoundLinearLayout" } != 1) return null
        if (fields.none { Context::class.java.isAssignableFrom(it.type) } || fields.none { List::class.java.isAssignableFrom(it.type) }) return null
        val text = fields.singleOrNull { it.type.name == "com.baidu.tbadk.core.elementsMaven.view.EMTextView" } ?: return null
        val method = uniqueSemanticCandidate("FreeCopy.ContentView", cls.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType) &&
                hasContentEvidence(it, logger)
        }, logger) ?: return null
        return ScanMatch(cls.name, method.name, text.name, 140)
    }
}
