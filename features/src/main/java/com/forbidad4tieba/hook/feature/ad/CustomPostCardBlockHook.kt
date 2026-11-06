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
        if (list.isEmpty() || (rules == null && templateKeyBlockReason == null)) return list
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
        var decisionCache: IdentityHashMap<Any, CustomPostFilterMatcher.Decision>? = null
        val keyCache = TemplateKeyCache(targets.templateKeyMethod, size.coerceAtLeast(8))
        val templateKeyRuleEnabled = rules?.needsTemplateKeyCheck == true
        val outerKeyNeeded = templateKeyRuleEnabled || templateKeyBlockReason != null
        var adBlockedCount = 0
        var customPostBlockedCount = 0

        fun getDecisionCached(target: Any?): CustomPostFilterMatcher.Decision {
            if (target == null || rules == null || !targets.templatePayloadMethod.declaringClass.isInstance(target)) {
                return CustomPostFilterMatcher.KEEP
            }
            val cache = decisionCache ?: IdentityHashMap<Any, CustomPostFilterMatcher.Decision>(size.coerceAtLeast(8)).also {
                decisionCache = it
            }
            cache[target]?.let { return it }
            val cardData = invoke(targets.templatePayloadMethod, target)
            val decision = if (cardData == null) CustomPostFilterMatcher.KEEP else decideBlock(
                cardData = cardData,
                targets = targets,
                rules = rules,
                keyCache = keyCache,
            )
            cache[target] = decision
            return decision
        }

        for (i in 0 until size) {
            val item = list[i]
            val key = if (outerKeyNeeded) keyCache.read(item) else null
            val directBlockReason = templateKeyBlockReason?.invoke(key)
            val templateKeyDecision = if (directBlockReason != null) {
                CustomPostFilterMatcher.Decision(
                    blocked = true,
                    reason = directBlockReason,
                )
            } else if (templateKeyRuleEnabled && rules != null) {
                CustomPostFilterMatcher.decideByTemplateKey(key, rules)
            } else {
                CustomPostFilterMatcher.KEEP
            }
            val decision = if (templateKeyDecision.blocked) {
                templateKeyDecision
            } else if (rules != null) {
                getDecisionCached(item)
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
        keyCache: TemplateKeyCache,
    ): CustomPostFilterMatcher.Decision {
        val list = read(targets.dataListField, cardData) as? List<*>
            ?: if (rules.recommendForum) {
                val nested = targets.recommendNestedDataMethod?.let { invoke(it, cardData) }
                nested?.let { read(targets.recommendNestedDataListField, it) as? List<*> }
            } else null
        if (list == null) return CustomPostFilterMatcher.KEEP
        if (list.isEmpty()) return CustomPostFilterMatcher.KEEP

        val headRuleEnabled = rules.needsFeedHeadParamsCheck
        val templateKeyRuleEnabled = rules.needsTemplateKeyCheck
        val headParamsField = targets.headParamsField
        var headParams: Map<*, *>? = null
        for (item in list) {
            if (item == null) continue
            val headCandidate = headRuleEnabled && headParams == null && headParamsField != null &&
                headParamsField.declaringClass.isInstance(item)
            if (!templateKeyRuleEnabled && !headCandidate) continue
            val key = keyCache.read(item)
            if (headCandidate && key == FEED_HEAD_TEMPLATE_KEY) {
                val params = read(headParamsField, item) as? Map<*, *>
                headParams = params?.takeIf { looksLikeFeedHeadParams(it, rules) }
            }
            if (templateKeyRuleEnabled) {
                val decision = CustomPostFilterMatcher.decideByTemplateKey(key, rules)
                if (decision.blocked) return decision
            }
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

    private class TemplateKeyCache(private val method: Method, private val initialSize: Int) {
        private var values: IdentityHashMap<Any, Any?>? = null

        fun read(target: Any?): String? {
            if (target == null || !method.declaringClass.isInstance(target)) return null
            val cache = values ?: IdentityHashMap<Any, Any?>(initialSize).also { values = it }
            val cached = cache[target]
            if (cached != null) return if (cached === NO_VALUE) null else cached as String
            val resolved = invoke(method, target) as? String
            cache[target] = resolved ?: NO_VALUE
            return resolved
        }
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
