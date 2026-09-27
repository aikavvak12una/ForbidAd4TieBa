package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier

internal class HomeTabsLevel1Rule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesUsingStrings(logger, "key_youliao_tab_red_dot_show")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val shape = scanRuleClassShape("HomeTabs", cls, logger) ?: return null
        val list = shape.fields.singleOrNull {
            !Modifier.isStatic(it.modifiers) && List::class.java.isAssignableFrom(it.type)
        } ?: return null
        if (shape.methods.count(HomeTabItemSymbolScanner::isItemFactory) != 1) return null
        val methods = ScanDexQueries.methods(cls.name, logger)
        val parser = uniqueSemanticCandidate("HomeTabs.Parse", methods.filter {
            it.paramCount == 0 && "key_youliao_tab_red_dot_show" in it.usingStrings &&
                it.calls("org.json.JSONArray", "<init>")
        }, logger) ?: return null
        val rebuild = uniqueSemanticCandidate("HomeTabs.Rebuild", methods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.paramCount == 0 && method.returnTypeName == "void" &&
                method.invokes.any { it.descriptor == parser.descriptor } &&
                method.invokes.any {
                    it.methodName == "addAll" && it.declaredClassName in listOf("java.util.List", "java.util.ArrayList", "java.util.Collection")
                } && method.usingFields.any { it.usingType == FieldUsingType.Read && it.field.name == list.name && it.field.declaredClassName == cls.name }
        }, logger) ?: return null
        val reflected = rebuild.getMethodInstance(cl)
        if (reflected.declaringClass != cls || reflected.returnType != Void.TYPE || reflected.parameterCount != 0) return null
        return ScanMatch(cls.name, reflected.name, list.name, 140)
    }
}
