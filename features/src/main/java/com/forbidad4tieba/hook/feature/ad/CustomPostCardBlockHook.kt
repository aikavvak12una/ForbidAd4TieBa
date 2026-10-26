package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.config.CustomPostFilterRules
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

object CustomPostCardBlockHook {
    private const val FEED_HEAD_TEMPLATE_KEY = "feed_head"
    private val NO_VALUE = Any()

    internal fun filterList(
        list: List<*>,
        runtimeFilter: CustomPostCardFilterSymbols,
        methodName: String,
        templateKeyBlockReason: ((String?) -> String?)? = null,
        rules: CustomPostFilterRules? = CustomPostFilterMatcher.runtimeRules(),
        recordModelScores: (Map<String, Double>) -> Unit = { CustomPostModelScoreStats.record(it) },
    ): List<*> {
        if (rules == null && templateKeyBlockReason == null) return list
        val filtered = filterItems(
            list = list,
            targets = runtimeFilter,
            rules = rules,
            templateKeyBlockReason = templateKeyBlockReason,
            recordModelScores = recordModelScores,
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
        targets: CustomPostCardFilterSymbols,
        rules: CustomPostFilterRules?,
        templateKeyBlockReason: ((String?) -> String?)?,
        recordModelScores: (Map<String, Double>) -> Unit,
    ): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null
        var payloadCache: IdentityHashMap<Any, Any>? = null
        val keyCache = IdentityHashMap<Any, Any?>(size.coerceAtLeast(8))
        var adBlockedCount = 0
        var customPostBlockedCount = 0

        fun getPayloadCached(target: Any?): Any? {
            if (target == null) return null
            val cache = payloadCache ?: IdentityHashMap<Any, Any>(size.coerceAtLeast(8)).also {
                payloadCache = it
            }
            val cached = cache[target]
            if (cached != null) {
                return if (cached === NO_VALUE) null else cached
            }
            val resolved = invoke(targets.templatePayloadMethod, target)
            cache[target] = resolved ?: NO_VALUE
            return resolved
        }

        fun getKeyCached(target: Any?): String? {
            if (target == null) return null
            val cached = keyCache[target]
            if (cached != null) {
                return if (cached === NO_VALUE) null else cached as String
            }
            val resolved = invoke(targets.templateKeyMethod, target) as? String
            keyCache[target] = resolved ?: NO_VALUE
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
                CustomPostFilterMatcher.KEEP
            }
            val decision = if (templateKeyDecision.blocked) {
                templateKeyDecision
            } else if (rules != null) {
                val cardData = getPayloadCached(item)
                if (cardData != null) {
                    decideBlock(
                        cardData = cardData,
                        targets = targets,
                        rules = rules,
                    )
                } else {
                    CustomPostFilterMatcher.KEEP
                }
            } else {
                CustomPostFilterMatcher.KEEP
            }

            decision.modelScores?.let(recordModelScores)
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
        targets: CustomPostCardFilterSymbols,
        rules: CustomPostFilterRules,
    ): CustomPostFilterMatcher.Decision {
        val list = read(targets.dataListField, cardData) as? List<*>
            ?: if (rules.recommendForum) {
                val nested = targets.recommendNestedDataMethod?.let { invoke(it, cardData) }
                nested?.let { read(targets.recommendNestedDataListField, it) as? List<*> }
            } else null
        if (list == null) return CustomPostFilterMatcher.KEEP
        if (list.isEmpty()) return CustomPostFilterMatcher.KEEP

        val headRuleEnabled = rules.needsFeedHeadParamsCheck
        var headParams: Map<*, *>? = null
        val keyCache = IdentityHashMap<Any, Any?>(list.size.coerceAtLeast(8))
        for (item in list) {
            if (item == null) continue
            val cached = keyCache[item]
            val key = if (cached != null) {
                if (cached === NO_VALUE) null else cached as String
            } else {
                val resolved = invoke(targets.templateKeyMethod, item) as? String
                keyCache[item] = resolved ?: NO_VALUE
                resolved
            }
            if (
                headRuleEnabled &&
                headParams == null &&
                targets.headParamsField != null &&
                key == FEED_HEAD_TEMPLATE_KEY
            ) {
                val params = read(targets.headParamsField, item) as? Map<*, *>
                headParams = params?.takeIf { looksLikeFeedHeadParams(it, rules) }
            }
            val decision = CustomPostFilterMatcher.decideByTemplateKey(key, rules)
            if (decision.blocked) return decision
        }
        val headDecision = if (headRuleEnabled) {
            CustomPostFilterMatcher.decideByFeedHeadParams(headParams, rules)
        } else {
            CustomPostFilterMatcher.KEEP
        }
        if (headDecision.blocked) return headDecision
        val schemaGetter = targets.schemaGetter
        if (rules.hot && schemaGetter != null && schemaGetter.declaringClass.isInstance(cardData)) {
            val schema = runCatching { schemaGetter.invoke(cardData) as? String }.getOrNull()
            val topicDecision = CustomPostFilterMatcher.decideByCardSchema(schema, rules)
            if (topicDecision.blocked) {
                return if (headDecision.modelScores == null) topicDecision
                else topicDecision.copy(modelScores = headDecision.modelScores)
            }
        }
        return headDecision
    }

    private fun invoke(method: Method, receiver: Any): Any? =
        if (method.declaringClass.isInstance(receiver)) runCatching { method.invoke(receiver) }.getOrNull() else null

    private fun read(field: Field?, receiver: Any): Any? =
        if (field != null && field.declaringClass.isInstance(receiver)) runCatching { field.get(receiver) }.getOrNull() else null

    private fun looksLikeFeedHeadParams(
        map: Map<*, *>,
        rules: CustomPostFilterRules,
    ): Boolean {
        // This was a positive-only score checked against zero; presence is the actual rule.
        if ((rules.reply || rules.score || rules.lottery) &&
            (map.containsKey("card_type") || map.containsKey("thread_type"))) return true
        if (rules.help && (map.stringValue("thread_type") != null || map.stringValue("card_type") != null ||
                map.stringValue("is_special_thread") != null)) return true
        if (rules.gameBooking && (map.containsKey("button_name") || map.containsKey("game_ext"))) return true
        if (rules.lottery && (map.containsKey("title") || map.containsKey("abstract"))) return true
        if (rules.live && map.containsKey("recom_type")) return true
        if (rules.unfollowedForum && (map.containsKey("forum_is_liked") || map.containsKey("page_from"))) return true
        if (rules.forumKeyword && map.containsKey("forum_name")) return true
        if (rules.userId && map.containsKey("user_id")) return true
        return rules.modelScore && map.containsKey("extra")
    }

    private fun Map<*, *>.stringValue(key: String): String? {
        val value = this[key] ?: return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }

}
