package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object CustomPostCardBlockHook {
    private const val FEED_HEAD_TEMPLATE_KEY = "feed_head"
    private val NO_METHOD = Any()
    private val sTemplateKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(64)
    private val sTemplatePayloadMethodCache = ConcurrentHashMap<Class<*>, Any>(64)
    private val sDataListFieldCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Field?>())
    private val sHeadParamsFieldCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Field?>())
    private val sRecommendNestedDataMethodCache = ConcurrentHashMap<Class<*>, Any>(16)
    private val sRecommendNestedDataListFieldCache = Collections.synchronizedMap(WeakHashMap<Class<*>, Field?>())

    private data class FilterSignature(
        val templateKeyMethodName: String,
        val templatePayloadMethodName: String,
        val dataListFieldName: String,
        val headParamsFieldName: String?,
        val recommendNestedDataMethodName: String?,
        val recommendNestedDataListFieldName: String?,
    )

    internal data class RuntimeFilter(
        val dataListFieldName: String,
        val templateKeyMethodName: String,
        val templatePayloadMethodName: String,
        val headParamsFieldName: String?,
        val recommendNestedDataMethodName: String?,
        val recommendNestedDataListFieldName: String?,
    )

    @Volatile private var sFilterSignature: FilterSignature? = null

    internal fun createRuntimeFilter(symbols: CustomPostCardFilterSymbols): RuntimeFilter {
        val templateKeyMethodName = symbols.templateKeyMethodName
        val templatePayloadMethodName = symbols.templatePayloadMethodName
        val dataListFieldName = symbols.dataListFieldName
        val headParamsFieldName = symbols.headParamsFieldName
        val recommendNestedDataMethodName = symbols.recommendNestedDataMethodName
        val recommendNestedDataListFieldName = symbols.recommendNestedDataListFieldName
        val signature = FilterSignature(
            templateKeyMethodName = templateKeyMethodName,
            templatePayloadMethodName = templatePayloadMethodName,
            dataListFieldName = dataListFieldName,
            headParamsFieldName = headParamsFieldName,
            recommendNestedDataMethodName = recommendNestedDataMethodName,
            recommendNestedDataListFieldName = recommendNestedDataListFieldName,
        )
        if (sFilterSignature != signature) {
            sTemplateKeyMethodCache.clear()
            sTemplatePayloadMethodCache.clear()
            sDataListFieldCache.clear()
            sHeadParamsFieldCache.clear()
            sRecommendNestedDataMethodCache.clear()
            sRecommendNestedDataListFieldCache.clear()
            sFilterSignature = signature
            XposedCompat.log("[CustomPostCardBlockHook] runtime filter READY")
        }
        return RuntimeFilter(
            dataListFieldName = dataListFieldName,
            templateKeyMethodName = templateKeyMethodName,
            templatePayloadMethodName = templatePayloadMethodName,
            headParamsFieldName = headParamsFieldName,
            recommendNestedDataMethodName = recommendNestedDataMethodName,
            recommendNestedDataListFieldName = recommendNestedDataListFieldName,
        )
    }

    internal fun filterList(
        list: List<*>,
        runtimeFilter: RuntimeFilter,
        methodName: String,
        templateKeyBlockReason: ((String?) -> String?)? = null,
    ): List<*> {
        val rules = CustomPostFilterMatcher.runtimeRules()
        if (rules == null && templateKeyBlockReason == null) return list
        val filtered = filterItems(
            list = list,
            dataListFieldName = runtimeFilter.dataListFieldName,
            templateKeyMethodName = runtimeFilter.templateKeyMethodName,
            templatePayloadMethodName = runtimeFilter.templatePayloadMethodName,
            headParamsFieldName = runtimeFilter.headParamsFieldName,
            recommendNestedDataMethodName = runtimeFilter.recommendNestedDataMethodName,
            recommendNestedDataListFieldName = runtimeFilter.recommendNestedDataListFieldName,
            rules = rules,
            templateKeyBlockReason = templateKeyBlockReason,
        )
        if (filtered !== list) {
            XposedCompat.logD {
                "[CustomPostCardBlockHook] > $methodName filtered: ${list.size} -> ${filtered.size}"
            }
        }
        return filtered
    }

    private fun filterItems(
        list: List<*>,
        dataListFieldName: String,
        templateKeyMethodName: String,
        templatePayloadMethodName: String,
        headParamsFieldName: String?,
        recommendNestedDataMethodName: String?,
        recommendNestedDataListFieldName: String?,
        rules: CustomPostFilterMatcher.RuntimeRules?,
        templateKeyBlockReason: ((String?) -> String?)?,
    ): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null
        val noPayload = Any()
        val noKey = Any()
        val payloadCache = IdentityHashMap<Any, Any?>(size.coerceAtLeast(8))
        val keyCache = IdentityHashMap<Any, Any?>(size.coerceAtLeast(8))
        var adBlockedCount = 0
        var customPostBlockedCount = 0

        fun getPayloadCached(target: Any?): Any? {
            if (target == null) return null
            val cached = payloadCache[target]
            if (cached != null || payloadCache.containsKey(target)) {
                return if (cached === noPayload) null else cached
            }
            val resolved = getTemplatePayload(target, templatePayloadMethodName)
            payloadCache[target] = resolved ?: noPayload
            return resolved
        }

        fun getKeyCached(target: Any?): String? {
            if (target == null) return null
            val cached = keyCache[target]
            if (cached != null || keyCache.containsKey(target)) {
                return if (cached === noKey) null else cached as String
            }
            val resolved = getTemplateKey(target, templateKeyMethodName)
            keyCache[target] = resolved ?: noKey
            return resolved
        }

        for (i in 0 until size) {
            val item = list[i]
            val key = getKeyCached(item)
            val directBlockReason = templateKeyBlockReason?.invoke(key)
            val templateKeyDecision = if (directBlockReason != null) {
                CustomPostFilterMatcher.Decision(
                    blocked = true,
                    reason = directBlockReason,
                )
            } else if (rules != null) {
                CustomPostFilterMatcher.decideByTemplateKey(key, rules)
            } else {
                CustomPostFilterMatcher.Decision(false)
            }
            val recommendKeyDecision = if (
                !templateKeyDecision.blocked &&
                rules?.recommendForum == true
            ) {
                CustomPostFilterMatcher.decideByRecommendCardTemplateKey(key, rules)
            } else {
                CustomPostFilterMatcher.Decision(false)
            }
            val decision = if (templateKeyDecision.blocked) {
                templateKeyDecision
            } else if (recommendKeyDecision.blocked) {
                recommendKeyDecision
            } else if (rules != null) {
                val cardData = getPayloadCached(item)
                if (cardData != null) {
                    decideBlock(
                        cardData = cardData,
                        dataListFieldName = dataListFieldName,
                        templateKeyMethodName = templateKeyMethodName,
                        headParamsFieldName = headParamsFieldName,
                        recommendNestedDataMethodName = recommendNestedDataMethodName,
                        recommendNestedDataListFieldName = recommendNestedDataListFieldName,
                        rules = rules,
                    )
                } else {
                    CustomPostFilterMatcher.Decision(false)
                }
            } else {
                CustomPostFilterMatcher.Decision(false)
            }

            if (decision.blocked) {
                if (isAdBlockDecision(decision)) {
                    adBlockedCount += 1
                } else {
                    customPostBlockedCount += 1
                }
                XposedCompat.logD {
                    "[CustomPostCardBlockHook] > blocked[$i] reason=${decision.reason.orEmpty()}"
                }
                if (out == null) {
                    out = ArrayList(size - 1)
                    for (j in 0 until i) out.add(list[j])
                }
            } else {
                out?.add(item)
            }
        }
        if (adBlockedCount > 0) BlockCountStats.recordAd(adBlockedCount)
        if (customPostBlockedCount > 0) BlockCountStats.recordCustomPost(customPostBlockedCount)
        return out ?: list
    }

    private fun isAdBlockDecision(decision: CustomPostFilterMatcher.Decision): Boolean {
        return decision.reason?.startsWith("ad:") == true
    }

    private fun decideBlock(
        cardData: Any,
        dataListFieldName: String,
        templateKeyMethodName: String,
        headParamsFieldName: String?,
        recommendNestedDataMethodName: String?,
        recommendNestedDataListFieldName: String?,
        rules: CustomPostFilterMatcher.RuntimeRules,
    ): CustomPostFilterMatcher.Decision {
        val list = extractCardDataList(cardData, dataListFieldName)
            ?: extractRecommendNestedDataList(
                cardData = cardData,
                methodName = recommendNestedDataMethodName,
                fieldName = recommendNestedDataListFieldName,
                enabled = rules.recommendForum,
            )
            ?: return CustomPostFilterMatcher.Decision(false)
        if (list.isEmpty()) return CustomPostFilterMatcher.Decision(false)

        val headRuleEnabled = rules.needsFeedHeadParamsCheck
        var headParams: Map<*, *>? = null
        val keyCache = IdentityHashMap<Any, Any?>(list.size.coerceAtLeast(8))
        val noKey = Any()
        for (item in list) {
            if (item == null) continue
            val cached = keyCache[item]
            val key = if (cached != null || keyCache.containsKey(item)) {
                if (cached === noKey) null else cached as String
            } else {
                val resolved = getTemplateKey(item, templateKeyMethodName)
                keyCache[item] = resolved ?: noKey
                resolved
            }
            if (
                headRuleEnabled &&
                headParams == null &&
                headParamsFieldName != null &&
                key == FEED_HEAD_TEMPLATE_KEY
            ) {
                headParams = extractFeedHeadParams(item, headParamsFieldName, rules)
            }
            val decision = CustomPostFilterMatcher.decideByTemplateKey(key, rules)
            if (decision.blocked) return decision
        }
        if (headRuleEnabled) {
            val headDecision = CustomPostFilterMatcher.decideByFeedHeadParams(headParams, rules)
            if (headDecision.blocked) return headDecision
        }
        return CustomPostFilterMatcher.Decision(false)
    }

    private fun extractCardDataList(cardData: Any, dataListFieldName: String): List<Any?>? {
        val field = resolveDataListField(cardData.javaClass, dataListFieldName) ?: return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            field.get(cardData) as? List<Any?>
        }.getOrNull()
    }

    private fun resolveDataListField(clazz: Class<*>, fieldName: String): Field? {
        val cached = sDataListFieldCache[clazz]
        if (cached != null || sDataListFieldCache.containsKey(clazz)) {
            return cached
        }
        var current: Class<*>? = clazz
        var field: Field? = null
        while (current != null && current != Any::class.java && field == null) {
            field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            current = current.superclass
        }
        if (field != null) {
            field.isAccessible = true
            if (Modifier.isStatic(field.modifiers) || !List::class.java.isAssignableFrom(field.type)) {
                field = null
            }
        }
        sDataListFieldCache[clazz] = field
        return field
    }

    private fun extractRecommendNestedDataList(
        cardData: Any,
        methodName: String?,
        fieldName: String?,
        enabled: Boolean,
    ): List<Any?>? {
        if (!enabled) return null
        if (methodName.isNullOrBlank() || fieldName.isNullOrBlank()) return null
        val method = resolveRecommendNestedDataMethod(cardData.javaClass, methodName) ?: return null
        val nestedData = runCatching { method.invoke(cardData) }.getOrNull() ?: return null
        val field = resolveRecommendNestedDataListField(nestedData.javaClass, fieldName) ?: return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            field.get(nestedData) as? List<Any?>
        }.getOrNull()
    }

    private fun resolveRecommendNestedDataMethod(clazz: Class<*>, methodName: String): Method? {
        sRecommendNestedDataMethodCache[clazz]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }
        var current: Class<*>? = clazz
        var method: Method? = null
        while (current != null && current != Any::class.java && method == null) {
            method = runCatching { current.getDeclaredMethod(methodName) }.getOrNull()
            current = current.superclass
        }
        if (method != null) {
            if (
                Modifier.isStatic(method.modifiers) ||
                method.parameterTypes.isNotEmpty() ||
                method.returnType.isPrimitive ||
                method.returnType == Void.TYPE ||
                method.returnType == String::class.java
            ) {
                method = null
            } else {
                method.isAccessible = true
            }
        }
        sRecommendNestedDataMethodCache[clazz] = method ?: NO_METHOD
        return method
    }

    private fun resolveRecommendNestedDataListField(clazz: Class<*>, fieldName: String): Field? {
        val cached = sRecommendNestedDataListFieldCache[clazz]
        if (cached != null || sRecommendNestedDataListFieldCache.containsKey(clazz)) {
            return cached
        }
        var current: Class<*>? = clazz
        var field: Field? = null
        while (current != null && current != Any::class.java && field == null) {
            field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            current = current.superclass
        }
        if (field != null) {
            if (Modifier.isStatic(field.modifiers) || !List::class.java.isAssignableFrom(field.type)) {
                field = null
            } else {
                field.isAccessible = true
            }
        }
        sRecommendNestedDataListFieldCache[clazz] = field
        return field
    }

    private fun getTemplateKey(item: Any, methodName: String): String? {
        val method = resolveTemplateKeyMethod(item.javaClass, methodName) ?: return null
        return runCatching { method.invoke(item) as? String }.getOrNull()
    }

    private fun resolveTemplateKeyMethod(clazz: Class<*>, methodName: String): Method? {
        sTemplateKeyMethodCache[clazz]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }
        return try {
            clazz.getMethod(methodName).apply {
                if (parameterTypes.isNotEmpty() || returnType != String::class.java) {
                    sTemplateKeyMethodCache[clazz] = NO_METHOD
                    return null
                }
                isAccessible = true
                sTemplateKeyMethodCache[clazz] = this
            }
        } catch (_: Throwable) {
            sTemplateKeyMethodCache[clazz] = NO_METHOD
            null
        }
    }

    private fun getTemplatePayload(item: Any, methodName: String): Any? {
        val method = resolveTemplatePayloadMethod(item.javaClass, methodName) ?: return null
        return runCatching { method.invoke(item) }.getOrNull()
    }

    private fun resolveTemplatePayloadMethod(clazz: Class<*>, methodName: String): Method? {
        sTemplatePayloadMethodCache[clazz]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }
        return try {
            clazz.getMethod(methodName).apply {
                if (parameterTypes.isNotEmpty() || returnType.isPrimitive || returnType == String::class.java) {
                    sTemplatePayloadMethodCache[clazz] = NO_METHOD
                    return null
                }
                isAccessible = true
                sTemplatePayloadMethodCache[clazz] = this
            }
        } catch (_: Throwable) {
            sTemplatePayloadMethodCache[clazz] = NO_METHOD
            null
        }
    }

    private fun extractFeedHeadParams(
        item: Any,
        fieldName: String,
        rules: CustomPostFilterMatcher.RuntimeRules,
    ): Map<*, *>? {
        val field = resolveFeedHeadParamsField(item.javaClass, fieldName) ?: return null
        val value = runCatching { field.get(item) as? Map<*, *> }.getOrNull() ?: return null
        return value.takeIf { looksLikeFeedHeadParams(it, rules) }
    }

    private fun resolveFeedHeadParamsField(clazz: Class<*>, fieldName: String): Field? {
        val cached = sHeadParamsFieldCache[clazz]
        if (cached != null || sHeadParamsFieldCache.containsKey(clazz)) {
            return cached
        }
        var current: Class<*>? = clazz
        var field: Field? = null
        while (current != null && current != Any::class.java) {
            field = runCatching { current.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                break
            }
            current = current.superclass
        }
        if (field != null) {
            if (Modifier.isStatic(field.modifiers) || !Map::class.java.isAssignableFrom(field.type)) {
                field = null
            } else {
                field.isAccessible = true
            }
        }
        sHeadParamsFieldCache[clazz] = field
        return field
    }

    private fun looksLikeFeedHeadParams(
        map: Map<*, *>,
        rules: CustomPostFilterMatcher.RuntimeRules,
    ): Boolean {
        return feedHeadParamsScore(map, rules) > 0
    }

    private fun feedHeadParamsScore(
        map: Map<*, *>,
        rules: CustomPostFilterMatcher.RuntimeRules,
    ): Int {
        var score = 0
        if (rules.help) {
            val threadType = map.stringValue("thread_type")
            val cardType = map.stringValue("card_type")
            val specialThread = map.stringValue("is_special_thread")
            if (threadType == "0") score += 8 else if (threadType != null) score += 2
            if (cardType == "question" || cardType == "normal") score += 10 else if (cardType != null) score += 2
            if (specialThread == "1") score += 10 else if (specialThread != null) score += 2
        }
        if (rules.gameBooking) {
            if (map.containsKey("button_name")) score += 6
            if (map.containsKey("game_ext")) score += 3
        }
        if (rules.score) {
            if (map.containsKey("thread_type")) score += 3
            if (map.containsKey("card_type")) score += 4
        }
        if (rules.lottery) {
            if (map.containsKey("card_type")) score += 5
            if (map.containsKey("thread_type")) score += 4
            if (map.containsKey("title")) score += 2
            if (map.containsKey("abstract")) score += 2
        }
        if (rules.live && map.containsKey("recom_type")) score += 4
        if (rules.unfollowedForum) {
            if (map.containsKey("forum_is_liked")) score += 4
            if (map.containsKey("page_from")) score += 3
        }
        if (rules.forumKeyword && map.containsKey("forum_name")) score += 4
        if (rules.modelScore && map.containsKey("extra")) score += 2
        return score
    }

    private fun Map<*, *>.stringValue(key: String): String? {
        val value = this[key] ?: return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

}
