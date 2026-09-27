package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class SettingsLevel1Rule(private val navClass: Class<*>) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesUsingStrings(logger, "settingContainer")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val shape = scanRuleClassShape("Settings", cls, logger) ?: return null
        if (shape.constructors.none { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }) return null
        if (shape.fields.none { it.type == ImageView::class.java }) return null
        val container = shape.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == RelativeLayout::class.java }
            ?: return null
        val init = uniqueSemanticCandidate("Settings.Init", shape.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(View::class.java)) &&
                ScanDexQueries.method(method, logger)?.let { dex ->
                    "settingContainer" in dex.usingStrings && dex.calls(navClass.name, "addCustomView") &&
                        dex.usingFields.any { it.field.declaredClassName == cls.name && it.field.name == container.name }
                } == true
        }, logger) ?: return null
        return ScanMatch(cls.name, init.name, container.name, 140)
    }
}
