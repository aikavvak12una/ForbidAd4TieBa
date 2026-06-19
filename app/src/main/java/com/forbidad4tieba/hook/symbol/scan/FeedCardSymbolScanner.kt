package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.FeedCardScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
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
            bindMethodSpec = resolveBindMethodSpec(cl, match.methodName, logger),
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
        val bindMethod = resolveBindMethod(cl, bindMethodName, logger) ?: return null
        val cardDataClass = bindMethod?.parameterTypes?.firstOrNull()
        return cardDataClass
            ?.let { scanInstanceFields("FeedCardHook.CardData", it, logger) }
            ?.firstOrNull { ScanReflection.isListType(it.type) }
            ?.name
    }

    private fun resolveBindMethodSpec(cl: ClassLoader, bindMethodName: String, logger: ScanLogger?): String? {
        val bindMethod = resolveBindMethod(cl, bindMethodName, logger) ?: return null
        return encodeMethodSpec(bindMethod)
    }

    private fun resolveBindMethod(cl: ClassLoader, bindMethodName: String, logger: ScanLogger?): Method? {
        val feedCardClass = ScanReflection.safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl)
        val feedCardMethods = feedCardClass
            ?.let { scanDeclaredMethods("FeedCardHook.FeedCardView", it, logger) }
            ?: return null
        val candidates = feedCardMethods.filter { method ->
            method.name == bindMethodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                !method.parameterTypes[0].isPrimitive
        }
        return selectUniqueScanCandidate(
            "FeedCardHook.FeedCardView.Bind",
            candidates,
            logger,
            ::describeMethodShape,
        )
    }

    private fun encodeMethodSpec(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.name}|${method.returnType.name}|$params"
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }
}
