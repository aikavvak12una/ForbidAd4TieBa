package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.FeedAdSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    private val sKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sInstalledMethodKeys = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var sTemplateKeyMethodName: String? = null

    private val NO_METHOD = Any()

    internal fun hook(targets: FeedAdSymbols) {
        val templateKeyMethodName = targets.templateKeyMethodName
        if (sTemplateKeyMethodName != templateKeyMethodName) {
            sKeyMethodCache.clear()
            sTemplateKeyMethodName = templateKeyMethodName
        }
        hookTemplateAdapterSetList(
            targets = targets,
            templateKeyMethodName = templateKeyMethodName,
            customPostFilter = targets.customPostFilter?.let(CustomPostCardBlockHook::createRuntimeFilter),
        )
    }

    private fun hookTemplateAdapterSetList(
        targets: FeedAdSymbols,
        templateKeyMethodName: String,
        customPostFilter: CustomPostCardBlockHook.RuntimeFilter?,
    ) {
        val mod = XposedCompat.module ?: return

        fun hookListMethod(method: Method) {
            val methodKey = method.toGenericString()
            if (!sInstalledMethodKeys.add(methodKey)) return
            val methodName = method.name

            mod.hook(method).intercept { chain ->
                val list = chain.args.firstOrNull() as? List<*>
                if (list != null) {
                    var current = list
                    var changed = false
                    if (customPostFilter != null) {
                        val filtered = CustomPostCardBlockHook.filterList(
                            list = current,
                            runtimeFilter = customPostFilter,
                            methodName = methodName,
                            templateKeyBlockReason = if (ConfigManager.isFeedAdBlockEnabled) {
                                ::adBlockReason
                            } else {
                                null
                            },
                        )
                        if (filtered !== current) {
                            current = filtered
                            changed = true
                        }
                    } else if (ConfigManager.isFeedAdBlockEnabled) {
                        val filtered = filterItems(
                            list = current,
                            templateKeyMethodName = templateKeyMethodName,
                        )
                        if (filtered !== current) {
                            XposedCompat.logD {
                                "[FeedAdHook] > $methodName filtered: ${current.size} -> ${filtered.size}"
                            }
                            current = filtered
                            changed = true
                        }
                    }
                    if (changed) {
                        return@intercept chain.proceed(arrayOf<Any?>(current))
                    }
                }
                chain.proceed()
            }
            XposedCompat.log("[FeedAdHook] hook INSTALLED: ${method.declaringClass.name}.$methodName")
        }

        hookListMethod(targets.setListMethod)
        targets.loadMoreMethod?.let(::hookListMethod)
    }

    private fun filterItems(
        list: List<*>,
        templateKeyMethodName: String,
    ): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null
        var blockedCount = 0

        for (i in 0 until size) {
            val item = list[i]
            var block = false
            var blockReason: String? = null

            if (item != null) {
                val key = getTemplateKey(item, templateKeyMethodName)
                if (key != null && shouldBlock(key)) {
                    block = true
                    blockReason = "template_key:$key"
                }
            }

            if (block) {
                blockedCount += 1
                if (blockReason != null) {
                    XposedCompat.logD { "[FeedAdHook] > blocked[$i] reason=$blockReason" }
                }
                if (out == null) {
                    out = ArrayList(size - 1)
                    for (j in 0 until i) out.add(list[j])
                }
            } else {
                out?.add(item)
            }
        }
        if (blockedCount > 0) BlockCountStats.recordAd(blockedCount)
        return out ?: list
    }

    private fun shouldBlock(key: String): Boolean = isAdKey(key) || isAdContainerKey(key)

    private fun adBlockReason(key: String?): String? {
        return key?.takeIf(::shouldBlock)?.let { "ad:template_key:$it" }
    }

    private fun isAdKey(key: String): Boolean {
        return key.startsWith("ad_card_") ||
            key.startsWith("fun_ad_card_") ||
            key in AD_KEYS
    }

    private fun isAdContainerKey(key: String): Boolean {
        return key in AD_CONTAINER_KEYS
    }

    private fun getTemplateKey(item: Any?, methodName: String): String? {
        if (item == null) return null
        val method = getKeyMethod(item.javaClass, methodName) ?: return null
        return try {
            method.invoke(item) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun getKeyMethod(cls: Class<*>, methodName: String): Method? {
        sKeyMethodCache[cls]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }

        return try {
            cls.getMethod(methodName).apply {
                if (parameterTypes.isNotEmpty() || returnType != String::class.java) {
                    sKeyMethodCache[cls] = NO_METHOD
                    return null
                }
                isAccessible = true
                sKeyMethodCache[cls] = this
            }
        } catch (t: Throwable) {
            XposedCompat.logD("FeedAdHook: ${t.message}")
            sKeyMethodCache[cls] = NO_METHOD
            null
        }
    }

    private val AD_KEYS = setOf(
        "video_ad",
        "feed_ad_video",
        "frs_empty_advert",
        "ad_card_head",
        "ad_card_title",
        "ad_card_single_pic",
        "ad_card_multi_pic",
        "ad_card_video",
        "ad_card_amount_download",
        "ad_card_interact",
        "commerce",
        "banner",
        "game",
    )

    private val AD_CONTAINER_KEYS = setOf(
        "sideway_list",
    )
}
