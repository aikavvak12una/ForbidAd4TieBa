package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    private val sKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sInstalledMethodKeys = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var sTemplateKeyMethodName: String? = null

    private val NO_METHOD = Any()

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val templateKeyMethodName = symbols.feedTemplateKeyMethod?.takeIf { it.isNotBlank() } ?: run {
            XposedCompat.log("[FeedAdHook] skipped: feedTemplateKeyMethod missing")
            return
        }
        if (sTemplateKeyMethodName != templateKeyMethodName) {
            sKeyMethodCache.clear()
            sTemplateKeyMethodName = templateKeyMethodName
        }
        hookTemplateAdapterSetList(
            cl = cl,
            symbols = symbols,
            templateKeyMethodName = templateKeyMethodName,
            customPostFilter = CustomPostCardBlockHook.createRuntimeFilter(symbols),
        )
    }

    private fun hookTemplateAdapterSetList(
        cl: ClassLoader,
        symbols: HookSymbols,
        templateKeyMethodName: String,
        customPostFilter: CustomPostCardBlockHook.RuntimeFilter?,
    ) {
        val mod = XposedCompat.module ?: return

        fun hookListMethod(className: String, methodName: String) {
            val method = XposedCompat.findMethodOrNull(className, cl, methodName, List::class.java)
            if (method == null) {
                XposedCompat.log("[FeedAdHook] method NOT FOUND: $className.$methodName(List)")
                return
            }
            val methodKey = method.toGenericString()
            if (!sInstalledMethodKeys.add(methodKey)) return

            mod.hook(method).intercept { chain ->
                val list = chain.args.firstOrNull() as? List<*>
                if (list != null) {
                    var current = list
                    var changed = false
                    if (ConfigManager.isAdBlockEnabled) {
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
                    if (customPostFilter != null) {
                        val filtered = CustomPostCardBlockHook.filterList(
                            list = current,
                            runtimeFilter = customPostFilter,
                            methodName = methodName,
                        )
                        if (filtered !== current) {
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
            XposedCompat.log("[FeedAdHook] hook INSTALLED: $className.$methodName")
        }

        hookListMethod(StableTiebaHookPoints.TEMPLATE_ADAPTER_CLASS, StableTiebaHookPoints.METHOD_SET_LIST)

        val loadMoreMethod = symbols.feedTemplateLoadMoreMethod
            ?.takeIf { it.isNotBlank() }
        if (loadMoreMethod != null) {
            hookListMethod(StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS, loadMoreMethod)
        } else {
            XposedCompat.log("[FeedAdHook] FeedTemplateAdapter loadMore skipped: scan symbol missing")
        }
    }

    private fun filterItems(
        list: List<*>,
        templateKeyMethodName: String,
    ): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null

        val noKey = Any()
        val templateKeyCache = IdentityHashMap<Any, Any?>(size.coerceAtLeast(8))

        fun getTemplateKeyCached(target: Any?): String? {
            if (target == null) return null
            val cached = templateKeyCache[target]
            if (cached != null || templateKeyCache.containsKey(target)) {
                return if (cached === noKey) null else cached as String
            }
            val resolved = getTemplateKey(target, templateKeyMethodName)
            templateKeyCache[target] = resolved ?: noKey
            return resolved
        }

        for (i in 0 until size) {
            val item = list[i]
            var block = false
            var blockReason: String? = null

            if (item != null) {
                val key = getTemplateKeyCached(item)
                if (key != null && shouldBlock(key)) {
                    block = true
                    blockReason = "template_key:$key"
                }
            }

            if (block) {
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
        return out ?: list
    }

    private fun shouldBlock(key: String): Boolean = isAdKey(key) || isAdContainerKey(key)

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
