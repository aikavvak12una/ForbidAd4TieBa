package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.FeedCardScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object FeedCardSymbolScanner {
    fun scanBind(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FeedCardScanSymbols {
        val match = ScanReflection.runRules(candidates, cl, listOf(FeedCardBindRule()), logger, "feedCardBind")
            ?: return FeedCardScanSymbols()
        return FeedCardScanSymbols(
            bindMethod = match.methodName,
            dataListField = resolveDataListField(cl, match.methodName, logger),
        )
    }

    fun scanHeadParams(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FeedCardScanSymbols {
        val match = ScanReflection.runRules(candidates, cl, listOf(FeedHeadParamsFieldRule()), logger, "feedHeadParams")
            ?: return FeedCardScanSymbols()
        return FeedCardScanSymbols(feedHeadParamsField = match.fieldName)
    }

    private fun resolveDataListField(cl: ClassLoader, bindMethodName: String, logger: ScanLogger?): String? {
        val feedCardClass = ScanReflection.safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl)
        val feedCardMethods = feedCardClass
            ?.let { declaredMethods("FeedCardView", it, logger) }
            ?: return null
        val bindMethod = feedCardMethods.firstOrNull { method ->
            method.name == bindMethodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive
        }
        val cardDataClass = bindMethod?.parameterTypes?.firstOrNull()
        return cardDataClass
            ?.let { instanceFields("CardData", it, logger) }
            ?.firstOrNull { ScanReflection.isListType(it.type) }
            ?.name
    }

    private fun declaredMethods(label: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? {
        return scanSubStep("FeedCardHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun instanceFields(label: String, clazz: Class<*>, logger: ScanLogger?): List<Field>? {
        return scanSubStep("FeedCardHook.$label.InstanceFields", logger, null) {
            ScanReflection.collectInstanceFields(clazz)
        }
    }
}
