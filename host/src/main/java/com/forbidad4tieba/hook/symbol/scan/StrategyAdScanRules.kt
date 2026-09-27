package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class SplashAdHelperRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.declaredFields.none { Modifier.isStatic(it.modifiers) && it.type.name == "kotlin.Lazy" }) return null
        val method = uniqueSemanticCandidate("SplashAd.Switch", cls.declaredMethods.filter {
            Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 0 &&
                ScanDexQueries.method(it, logger)?.usingStrings?.contains("disable_all_ad") == true
        }, logger) ?: return null
        return ScanMatch(cls.name, method.name, "", 140)
    }
}

internal class CloseAdDataRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) = listOf("com.baidu.tbadk.data.CloseAdData")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.data.CloseAdData" ||
            cls.superclass?.name != "com.baidu.adp.lib.OrmObject.toolsystem.orm.object.OrmObject") return null
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        if (fields.count { it.type == Int::class.javaPrimitiveType } < 3 ||
            fields.count { it.type == Boolean::class.javaPrimitiveType } != 1 || fields.count { it.type == String::class.java } != 1) return null
        val getters = cls.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Int::class.javaPrimitiveType && it.parameterCount == 0
        }
        if (getters.size !in 2..3) return null
        // All matching switches are hooked; order is only for reproducible descriptors.
        return ScanMatch(cls.name, getters.map { it.name }.sorted().joinToString(","), "", 140)
    }
}

internal class BiddingCodecRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesUsingStrings(logger, "biddingContext")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val methods = cls.declaredMethods.filter {
            Modifier.isStatic(it.modifiers) && it.returnType == String::class.java &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        fun anchored(key: String) = uniqueSemanticCandidate("BiddingCodec.$key", methods.filter {
            ScanDexQueries.method(it, logger)?.usingStrings?.contains(key) == true
        }, logger)
        val decode = anchored("biddingContext") ?: return null
        val encode = anchored("biddingStr") ?: return null
        if (decode == encode) return null
        return ScanMatch(cls.name, listOf(decode.name, encode.name).sorted().joinToString(","), "", 140)
    }
}
