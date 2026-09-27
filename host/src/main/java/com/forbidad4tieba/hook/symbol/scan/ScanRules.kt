package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class ScanMatch(
    val className: String,
    val methodName: String,
    val fieldName: String,
    val score: Int,
)

internal abstract class ScanRule {
    open val minScore: Int = 80
    open val minScoreGap: Int = 10
    open fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): List<String> = candidates
    abstract fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch?
}

internal data class ScanRuleClassShape(
    val methods: Array<Method>,
    val fields: Array<Field>,
    val constructors: Array<Constructor<*>>,
)

internal fun scanRuleClassShape(tag: String, cls: Class<*>, logger: ScanLogger?): ScanRuleClassShape? {
    return scanSubStep("ScanRules.$tag.${cls.name}.ClassShape", logger, null) {
        ScanRuleClassShape(
            methods = cls.declaredMethods,
            fields = cls.declaredFields,
            constructors = cls.declaredConstructors,
        )
    }
}

internal fun kotlinMetadataStrings(cls: Class<*>, logger: ScanLogger? = null): List<String> {
    val metadata = cls.getAnnotation(kotlin.Metadata::class.java) ?: return emptyList()
    val out = ArrayList<String>(8)
    for (methodName in arrayOf("d1", "d2")) {
        val values = scanSubStep("ScanRules.${cls.name}.Metadata.$methodName", logger, null) {
            kotlin.Metadata::class.java.getMethod(methodName).invoke(metadata) as? Array<*>
        } ?: continue
        for (value in values) {
            if (value is String && value.isNotEmpty()) out.add(value)
        }
    }
    return out
}
