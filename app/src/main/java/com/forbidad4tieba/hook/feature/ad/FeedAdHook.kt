package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    private val NO_METHOD = Any()
    private val sKeyMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sInnerListMethodCache = ConcurrentHashMap<Class<*>, Any>(32)
    private val sWrapperMethodCache = ConcurrentHashMap<Class<*>, Any>(32)

    fun hook(cl: ClassLoader) {
        hookTemplateAdapterSetList(cl)
        hookLiveBlockFallback(cl)
    }

    // ── TemplateAdapter data-level filtering ──────────────────────────────

    private fun hookTemplateAdapterSetList(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return

        // Helper to hook a single setList/loadMore method
        fun hookListMethod(className: String, methodName: String) {
            val method = XposedCompat.findMethodOrNull(className, cl, methodName, List::class.java)
            if (method == null) {
                XposedCompat.log("[FeedAdHook] method NOT FOUND: $className.$methodName(List)")
                return
            }
            mod.hook(method).intercept { chain ->
                val args = chain.args
                val list = args.firstOrNull() as? List<*>
                if (list != null) {
                    val blockAd = ConfigManager.isAdBlockEnabled
                    val blockLive = ConfigManager.isLiveBlockEnabled
                    val blockVideo = ConfigManager.isFeedVideoBlockEnabled
                    val blockMyForum = ConfigManager.isMyForumBlockEnabled
                    if (blockAd || blockLive || blockVideo || blockMyForum) {
                        val filtered = filterItems(list, blockAd, blockLive, blockVideo, blockMyForum)
                        if (filtered !== list) {
                            XposedCompat.logD("[FeedAdHook] > $methodName filtered: ${list.size} -> ${filtered.size}")
                            // API 101: chain.args is a COPY — must pass modified args explicitly
                            return@intercept chain.proceed(arrayOf<Any?>(filtered))
                        }
                    }
                }
                chain.proceed()
            }
            XposedCompat.log("[FeedAdHook] hook INSTALLED: $className.$methodName")
        }

        // Initial load / refresh
        hookListMethod("com.baidu.tieba.feed.list.TemplateAdapter", "setList")

        // Load more / append (using dynamic symbol or fallback to J)
        val currentSymbols = com.forbidad4tieba.hook.HookSymbolResolver.getMemorySymbols()
        val loadMoreMethod = currentSymbols?.feedTemplateLoadMoreMethod ?: "J"
        hookListMethod("com.baidu.tieba.feed.list.FeedTemplateAdapter", loadMoreMethod)
    }

    // ── Live block fallback (component-level) ─────────────────────────────

    private fun hookLiveBlockFallback(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val cardLiveViewClass = XposedCompat.findClassOrNull(
            "com.baidu.tieba.feed.component.CardLiveView", cl
        ) ?: return

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ViewExt.squashAncestorFeedCard(v) }
            override fun onViewDetachedFromWindow(v: View) {}
        }

        for (ctor in cardLiveViewClass.declaredConstructors) {
            ctor.isAccessible = true
            mod.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                XposedCompat.logD("[FeedAdHook] > CardLiveView.<init> intercepted, liveBlock=${ConfigManager.isLiveBlockEnabled}")
                if (ConfigManager.isLiveBlockEnabled) {
                    val view = chain.thisObject as? View
                    if (view != null && view.getTag(ViewExt.TAG_EMPTY_VIEW) != true) {
                        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                        view.addOnAttachStateChangeListener(listener)
                        if (view.isAttachedToWindow) ViewExt.squashAncestorFeedCard(view)
                    }
                }
                result
            }
        }
        XposedCompat.log("[FeedAdHook] CardLiveView constructor hooks INSTALLED")
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
                    val innerLists = getInnerItems(item)
                    for (innerList in innerLists) {
                        for (innerItem in innerList) {
                            val innerKey = getTemplateKey(innerItem)
                            if (innerKey != null && shouldBlock(innerKey, blockAd, blockLive, blockVideo, blockMyForum)) {
                                block = true
                                break
                            }
                        }
                        if (block) break
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
                } catch (t: Throwable) { XposedCompat.logD("FeedAdHook: ${t.message}") }
            }
            currentClass = currentClass.superclass
        }

        sWrapperMethodCache[cls] = NO_METHOD
        return Pair(null, null)
    }

    private fun getInnerItems(item: Any): List<List<*>> {
        var payload = item
        val (_, payloadMethod) = resolveWrapperMethods(item.javaClass)
        if (payloadMethod != null) {
            try {
                val inner = payloadMethod.invoke(item)
                if (inner != null) payload = inner
            } catch (t: Throwable) { XposedCompat.logD("FeedAdHook: ${t.message}") }
        }

        val cls = payload.javaClass
        val cached = sInnerListMethodCache[cls]
        if (cached != null) {
            if (cached === NO_METHOD) return emptyList()
            @Suppress("UNCHECKED_CAST")
            val methods = cached as List<Method>
            val results = ArrayList<List<*>>(methods.size)
            for (m in methods) {
                try {
                    val res = m.invoke(payload)
                    if (res is List<*>) results.add(res)
                } catch (_: Throwable) { }
            }
            return results
        }

        val foundMethods = ArrayList<Method>()
        try {
            val m = cls.getMethod("j")
            m.isAccessible = true
            foundMethods.add(m)
        } catch (_: Throwable) { }

        try {
            for (m in cls.methods) {
                if (m.name == "j") continue
                if (m.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(m.returnType)) {
                    if (m.name.length <= 3) {
                        m.isAccessible = true
                        foundMethods.add(m)
                    }
                }
            }
        } catch (t: Throwable) { XposedCompat.logD("FeedAdHook: ${t.message}") }

        if (foundMethods.isEmpty()) {
            sInnerListMethodCache[cls] = NO_METHOD
            return emptyList()
        }

        sInnerListMethodCache[cls] = foundMethods
        val results = ArrayList<List<*>>(foundMethods.size)
        for (m in foundMethods) {
            try {
                val res = m.invoke(payload)
                if (res is List<*>) results.add(res)
            } catch (_: Throwable) { }
        }
        return results
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
            if (methodName != "b") {
                 try {
                     val fallback = cls.getMethod("b")
                     fallback.isAccessible = true
                     sKeyMethodCache[cls] = fallback
                     return fallback
                 } catch (t: Throwable) { XposedCompat.logD("FeedAdHook: ${t.message}") }
            }
            try {
                for (m in cls.methods) {
                    if (m.parameterTypes.isEmpty() && m.returnType == String::class.java) {
                        m.isAccessible = true
                        if (m.name.length <= 3) {
                            sKeyMethodCache[cls] = m
                            return m
                        }
                    }
                }
            } catch (t: Throwable) { XposedCompat.logD("FeedAdHook: ${t.message}") }
            sKeyMethodCache[cls] = NO_METHOD
            null
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
