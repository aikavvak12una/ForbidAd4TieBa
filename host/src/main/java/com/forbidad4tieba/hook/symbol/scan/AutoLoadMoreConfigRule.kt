package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class AutoLoadMoreConfigRule(private val parserClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val parser = ScanReflection.safeFindClass(parserClassName, cl) ?: return emptyList()
        return parser.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type.enclosingClass == parser }
            .map { it.type.name }.distinct()
    }

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.enclosingClass?.name != parserClassName) return null
        val method = uniqueSemanticCandidate("AutoLoadMore.Config", cls.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType &&
                ScanDexQueries.method(it, logger)?.let { dex ->
                    "home_preload_not_see_thread_num_sp_key" in dex.usingStrings &&
                        dex.calls("com.baidu.tbadk.core.sharedPref.SharedPrefHelper", "getInt")
                } == true
        }, logger) ?: return null
        return ScanMatch(cls.name, method.name, "", 140)
    }
}
