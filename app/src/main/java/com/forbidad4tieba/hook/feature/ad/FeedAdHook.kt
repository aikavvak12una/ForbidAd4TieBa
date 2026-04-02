package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.utils.ViewExt
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    // Sentinel object to cache "no method found" result
    private val NO_METHOD = Any()
    // Cache: class -> reflective Method for templateKey access (or NO_METHOD)
    private val sKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    // Cache: class -> reflective Method for inner list access (or NO_METHOD)
    private val sInnerListMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    // Cache: class -> Pair<Method?, Method?> for zj9 wrapper (keyMethod, payloadMethod)
    private val sWrapperMethodCache = ConcurrentHashMap<Class<*>, Any>(32)

    fun hook(cl: ClassLoader) {
        hookTemplateAdapterSetList(cl)
        hookLiveBlockFallback(cl)
    }

    // ── TemplateAdapter data-level filtering ──────────────────────────────

    private fun hookTemplateAdapterSetList(cl: ClassLoader) {
        val filterHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val list = param.args?.firstOrNull() as? List<*> ?: return
                val blockAd = ConfigManager.isAdBlockEnabled
                val blockLive = ConfigManager.isLiveBlockEnabled
                val blockVideo = ConfigManager.isFeedVideoBlockEnabled
                val blockMyForum = ConfigManager.isMyForumBlockEnabled
                if (!blockAd && !blockLive && !blockVideo && !blockMyForum) return

                val filtered = filterItems(list, blockAd, blockLive, blockVideo, blockMyForum)
                if (filtered !== list) param.args[0] = filtered
            }
        }

        // Initial load / refresh
        hookMethodSafe(cl, "com.baidu.tieba.feed.list.TemplateAdapter", "setList", filterHook, List::class.java)
        
        // Load more / append (using dynamic symbol or fallback to J)
        val currentSymbols = com.forbidad4tieba.hook.HookSymbolResolver.getMemorySymbols()
        val loadMoreMethod = currentSymbols?.feedTemplateLoadMoreMethod ?: "J"
        hookMethodSafe(cl, "com.baidu.tieba.feed.list.FeedTemplateAdapter", loadMoreMethod, filterHook, List::class.java)
    }

    // ── Live block fallback (component-level) ─────────────────────────────

    private fun hookLiveBlockFallback(cl: ClassLoader) {
        val cardLiveViewClass = XposedHelpers.findClassIfExists(
            "com.baidu.tieba.feed.component.CardLiveView", cl
        ) ?: return

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ViewExt.squashAncestorFeedCard(v) }
            override fun onViewDetachedFromWindow(v: View) {}
        }

        XposedBridge.hookAllConstructors(cardLiveViewClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isLiveBlockEnabled) return
                val view = param.thisObject as? View ?: return
                if (view.getTag(ViewExt.TAG_EMPTY_VIEW) == true) return
                view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                view.addOnAttachStateChangeListener(listener)
                if (view.isAttachedToWindow) ViewExt.squashAncestorFeedCard(view)
            }
        })
    }

    // ── Core filter logic ─────────────────────────────────────────────────

    private fun filterItems(list: List<*>, blockAd: Boolean, blockLive: Boolean, blockVideo: Boolean, blockMyForum: Boolean): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null

        for (i in 0 until size) {
            val item = list[i]
            var block = false
            
            if (item != null) {
                val key = getTemplateKey(item)
                if (key != null && shouldBlock(key, blockAd, blockLive, blockVideo, blockMyForum)) {
                    block = true
                } else {
                    val innerList = getInnerItems(item)
                    if (innerList != null) {
                        for (innerItem in innerList) {
                            val innerKey = getTemplateKey(innerItem)
                            if (innerKey != null && shouldBlock(innerKey, blockAd, blockLive, blockVideo, blockMyForum)) {
                                block = true
                                break
                            }
                        }
                    }
                }
            }
            
            if (block) {
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

    private fun shouldBlock(key: String, blockAd: Boolean, blockLive: Boolean, blockVideo: Boolean, blockMyForum: Boolean): Boolean {
        if (blockAd && isAdKey(key)) return true
        if (blockLive && key == "live_card") return true
        if (blockVideo && isVideoKey(key)) return true
        if (blockMyForum && key == "card_feed_my_forum") return true
        return false
    }

    private fun isAdKey(key: String): Boolean {
        return key.startsWith("ad_card_") ||
               key.startsWith("fun_ad_card_") ||
               key in AD_KEYS
    }

    private fun isVideoKey(key: String): Boolean {
        return key == "video" ||
               key == "video_card" ||
               key == "staggered_video" ||
               key == "thread_ext_show"
    }

    // ── Template key extraction via reflection ────────────────────────────

    private fun resolveWrapperMethods(cls: Class<*>): Pair<Method?, Method?> {
        val cached = sWrapperMethodCache[cls]
        if (cached != null) {
            if (cached === NO_METHOD) return Pair(null, null)
            @Suppress("UNCHECKED_CAST")
            return cached as Pair<Method?, Method?>
        }

        var currentClass: Class<*>? = cls
        while (currentClass != null && currentClass != Any::class.java) {
            for (iface in currentClass.interfaces) {
                try {
                    val methods = iface.declaredMethods
                    if (methods.size == 2) {
                        val stringMethod = methods.find { it.returnType == String::class.java && it.parameterTypes.isEmpty() }
                        val objectMethod = methods.find { it.returnType == Any::class.java && it.parameterTypes.isEmpty() }

                        if (stringMethod != null && objectMethod != null) {
                            val keyMethod = cls.getMethod(stringMethod.name)
                            keyMethod.isAccessible = true

                            val payloadMethod = cls.getMethod(objectMethod.name)
                            payloadMethod.isAccessible = true

                            val pair = Pair(keyMethod, payloadMethod)
                            sWrapperMethodCache[cls] = pair
                            return pair
                        }
                    }
                } catch (_: Throwable) {}
            }
            currentClass = currentClass.superclass
        }

        sWrapperMethodCache[cls] = NO_METHOD
        return Pair(null, null)
    }

    private fun getInnerItems(item: Any): List<*>? {
        var payload = item
        // item is a zj9 wrapper. We need to call d() to get the actual payload (e.g. e99)
        val (_, payloadMethod) = resolveWrapperMethods(item.javaClass)
        if (payloadMethod != null) {
            try {
                val inner = payloadMethod.invoke(item)
                if (inner != null) {
                    payload = inner
                }
            } catch (_: Throwable) {}
        }

        val cls = payload.javaClass
        val cached = sInnerListMethodCache[cls]
        if (cached != null) {
            if (cached === NO_METHOD) return null
            return try {
                val res = (cached as Method).invoke(payload)
                if (res is List<*>) res else null
            } catch (_: Throwable) { null }
        }
        
        return try {
            val m = cls.getMethod("j")
            m.isAccessible = true
            sInnerListMethodCache[cls] = m
            val res = m.invoke(payload)
            if (res is List<*>) res else null
        } catch (_: Throwable) {
            // Fallback: look for a method returning List
            try {
                for (m in cls.methods) {
                    if (m.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(m.returnType)) {
                        if (m.name.length <= 3) {
                            m.isAccessible = true
                            sInnerListMethodCache[cls] = m
                            val res = m.invoke(payload)
                            return if (res is List<*>) res else null
                        }
                    }
                }
            } catch (_: Throwable) {}
            
            sInnerListMethodCache[cls] = NO_METHOD
            null
        }
    }

    private fun getTemplateKey(item: Any?): String? {
        if (item == null) return null
        val cls = item.javaClass
        val method = getKeyMethod(cls) ?: return null
        return try {
            val result = method.invoke(item)
            if (result is String) result else null
        } catch (_: Throwable) { null }
    }

    private fun getKeyMethod(cls: Class<*>): Method? {
        val (keyMethod, _) = resolveWrapperMethods(cls)
        if (keyMethod != null) return keyMethod

        sKeyMethodCache[cls]?.let { cached ->
            return if (cached === NO_METHOD) null else cached as Method
        }
        
        val currentSymbols = com.forbidad4tieba.hook.HookSymbolResolver.getMemorySymbols()
        val methodName = currentSymbols?.feedTemplateKeyMethod ?: "b"
        
        return try {
            val m = cls.getMethod(methodName)
            m.isAccessible = true
            sKeyMethodCache[cls] = m
            m
        } catch (_: Throwable) {
            // Fallback strategy if symbols haven't been updated yet
            if (methodName != "b") {
                 try {
                     val fallback = cls.getMethod("b")
                     fallback.isAccessible = true
                     sKeyMethodCache[cls] = fallback
                     return fallback
                 } catch (_: Throwable) {}
            }
            // Scan for no-arg methods returning String as a last resort fallback
            try {
                for (m in cls.methods) {
                    if (m.parameterTypes.isEmpty() && m.returnType == String::class.java) {
                        m.isAccessible = true
                        // Basic heuristic: check if it returns a known ad key, but we can't invoke it safely here
                        // We just take the first one that has a short name
                        if (m.name.length <= 3) {
                            sKeyMethodCache[cls] = m
                            return m
                        }
                    }
                }
            } catch (_: Throwable) {}
            
            sKeyMethodCache[cls] = NO_METHOD
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun hookMethodSafe(cl: ClassLoader, className: String, methodName: String, hook: XC_MethodHook, vararg params: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, *params, hook)
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook $className.$methodName: ${t.message}")
        }
    }

    private val AD_KEYS = setOf(
        "video_ad", "feed_ad_video", "frs_empty_advert",
        "ad_card_head", "ad_card_title", "ad_card_single_pic",
        "ad_card_multi_pic", "ad_card_video", "ad_card_amount_download",
        "ad_card_interact", "recommend_card_forum_attention",
        "recommend_card_person_attention", "recommend_card_list",
        "commerce", "banner"
    )
}
