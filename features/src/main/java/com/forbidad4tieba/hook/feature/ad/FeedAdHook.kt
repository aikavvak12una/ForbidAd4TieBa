package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.FeedAdSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.core.OwnedHookSet
import io.github.libxposed.api.XposedInterface
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    private val sKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }
    @Volatile private var sTemplateKeyMethodName: String? = null

    private val NO_METHOD = Any()

    internal fun hook(targets: FeedAdSymbols): InstallOutcome {
        val templateKeyMethodName = targets.templateKeyMethodName
        if (sTemplateKeyMethodName != templateKeyMethodName) {
            sKeyMethodCache.clear()
            sTemplateKeyMethodName = templateKeyMethodName
        }
        return hookTemplateAdapterSetList(
            targets = targets,
            templateKeyMethodName = templateKeyMethodName,
            customPostFilter = targets.customPostFilter?.let(CustomPostCardBlockHook::createRuntimeFilter),
        )
    }

    private fun hookTemplateAdapterSetList(
        targets: FeedAdSymbols,
        templateKeyMethodName: String,
        customPostFilter: CustomPostCardBlockHook.RuntimeFilter?,
    ): InstallOutcome {
        val mod = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val methods = listOfNotNull(targets.setListMethod, targets.loadMoreMethod).distinct()
        val before = installedMethods.size()
        val failures = ArrayList<String>()
        for (method in methods) {
            try {
                installedMethods.install(method) {
                    RuntimeHooks.builder(mod, method, "FeedAdHook", "hookTemplateAdapterSetList:method").intercept { chain ->
                        val list = chain.args.firstOrNull() as? List<*>
                        if (list != null) {
                            val filtered = filterList(list, templateKeyMethodName, customPostFilter, method.name)
                            if (filtered !== list) return@intercept chain.proceed(arrayOf<Any?>(filtered))
                        }
                        chain.proceed()
                    }
                }
            } catch (failure: Throwable) {
                failures += "${method.declaringClass.name}.${method.name}: ${failure.message}"
            }
        }
        val count = methods.count(installedMethods::contains)
        val state = when {
            count == 0 -> InstallState.FAILED
            failures.isNotEmpty() -> InstallState.PARTIAL
            installedMethods.size() == before -> InstallState.ALREADY_INSTALLED
            else -> InstallState.INSTALLED
        }
        return InstallOutcome(state, count, failures.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    internal fun filterList(
        list: List<*>,
        templateKeyMethodName: String,
        customPostFilter: CustomPostCardBlockHook.RuntimeFilter?,
        methodName: String,
    ): List<*> {
        val settings = ConfigManager.snapshot()
        val blockFeedAds = settings.isFeedAdBlockEnabled
        val blockRecommendBanner = settings.isStrategyAdBlockEnabled
        val rules = settings.customPostRules
        if (customPostFilter != null && rules != null) {
            return CustomPostCardBlockHook.filterList(
                list = list,
                runtimeFilter = customPostFilter,
                methodName = methodName,
                rules = rules,
                templateKeyBlockReason = if (blockFeedAds || blockRecommendBanner) {
                    { key -> adBlockReason(key, blockFeedAds, blockRecommendBanner) }
                } else {
                    null
                },
            )
        }
        if (!blockFeedAds && !blockRecommendBanner) return list
        val filtered = filterItems(list, templateKeyMethodName, blockFeedAds, blockRecommendBanner)
        if (filtered !== list) {
            XposedCompat.logD {
                "[FeedAdHook] > $methodName filtered: ${list.size} -> ${filtered.size}"
            }
        }
        return filtered
    }

    private fun filterItems(
        list: List<*>,
        templateKeyMethodName: String,
        blockFeedAds: Boolean,
        blockRecommendBanner: Boolean,
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
                if (key != null && shouldBlock(key, blockFeedAds, blockRecommendBanner)) {
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

    private fun shouldBlock(key: String, blockFeedAds: Boolean, blockRecommendBanner: Boolean): Boolean {
        return (blockRecommendBanner && key == "recommend_banner") || (blockFeedAds && isAdKey(key))
    }

    private fun adBlockReason(key: String?, blockFeedAds: Boolean, blockRecommendBanner: Boolean): String? {
        return if (key != null && shouldBlock(key, blockFeedAds, blockRecommendBanner)) {
            "ad:template_key:$key"
        } else {
            null
        }
    }

    private fun isAdKey(key: String): Boolean {
        return key.startsWith("ad_card_") ||
            key.startsWith("fun_ad_card_") ||
            key in AD_KEYS
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

}
