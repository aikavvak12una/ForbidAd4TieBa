package com.forbidad4tieba.hook.feature.ad

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.utils.ViewExt
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

object FeedAdHook {
    fun hook(cl: ClassLoader, adClasses: Array<Class<*>>) {
        hookFeedAppAdCard(cl)
        hookFeedAdViewHolder(cl)
        hookFeedLiveCards(cl)
        hookFeedVideoCards(cl)
        hookFeedMyForumCards(cl)

        val squashOnAdBlock = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (ConfigManager.isAdBlockEnabled && param.thisObject is View) {
                    ViewExt.squashView(param.thisObject as View)
                }
            }
        }

        val forceGone = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isAdBlockEnabled) return
                val args = param.args
                if (args != null && args.isNotEmpty() && args[0] is Int) {
                    args[0] = View.GONE
                }
            }
        }

        for (clazz in adClasses) {
            try {
                XposedBridge.hookAllConstructors(clazz, squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashOnAdBlock)
                XposedBridge.hookAllMethods(clazz, "setVisibility", forceGone)
            } catch (t: Throwable) {
                XposedBridge.log("${Constants.TAG}: Failed to hook ad view class ${clazz.name}: ${t.message}")
            }
        }
    }

    private fun hookFeedAppAdCard(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.recapp.lego.view.AdCardBaseView", cl, "x",
                "com.baidu.tieba.recapp.lego.model.AdCard",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled || param.args[0] == null) return
                        try {
                            val appInfo = XposedHelpers.callMethod(param.args[0], "getAdvertAppInfo")
                            if (appInfo != null && XposedHelpers.callMethod(appInfo, "isAppAdvert") as Boolean) {
                                ViewExt.squashView(param.thisObject as View)
                            }
                        } catch (_: Throwable) {}
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook AdCardBaseView.x: ${t.message}")
        }
    }

    private fun hookFeedAdViewHolder(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists("com.baidu.tieba.funad.adapter.FeedAdViewHolder", cl) ?: return
            XposedBridge.hookAllConstructors(
                clazz,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        try {
                            val itemView = XposedHelpers.getObjectField(param.thisObject, "itemView") as? View
                            if (itemView != null) ViewExt.squashView(itemView)
                        } catch (_: Throwable) {}
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook FeedAdViewHolder constructors: ${t.message}")
        }
    }

    private fun hookFeedLiveCards(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.list.TemplateAdapter", cl, "setList", List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (args == null || args.isEmpty()) return
                        val arg0 = args[0] as? List<*> ?: return

                        val blockLive = ConfigManager.isLiveBlockEnabled
                        val blockForum = ConfigManager.isMyForumBlockEnabled
                        if (!blockLive && !blockForum) return

                        val keysToRemove = if (blockLive && blockForum) {
                            setOf("live_card", "card_feed_my_forum")
                        } else if (blockLive) {
                            setOf("live_card")
                        } else {
                            setOf("card_feed_my_forum")
                        }

                        val filtered = filterOutTemplateKeys(arg0, keysToRemove)
                        if (filtered !== arg0) args[0] = filtered
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook TemplateAdapter.setList: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.baidu.tieba.feed.card.FeedCardView", cl, "w", "com.baidu.tieba.y89",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isLiveBlockEnabled && !ConfigManager.isFeedVideoBlockEnabled) return
                        if (param.thisObject is View) {
                            ViewExt.restoreViewIfSquashed(param.thisObject as View)
                        }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook FeedCardView.w restore path: ${t.message}")
        }

        try {
            val cardLiveViewClass = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardLiveView", cl
            ) ?: return

            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    ViewExt.squashAncestorFeedCard(v)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            }

            XposedBridge.hookAllConstructors(cardLiveViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isLiveBlockEnabled || param.thisObject !is View) return
                    val liveView = param.thisObject as View

                    if (liveView.getTag(ViewExt.TAG_EMPTY_VIEW) != true) {
                        liveView.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                        liveView.addOnAttachStateChangeListener(attachListener)
                        if (liveView.isAttachedToWindow) ViewExt.squashAncestorFeedCard(liveView)
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook CardLiveView constructors: ${t.message}")
        }
    }

    private fun hookFeedVideoCards(cl: ClassLoader) {
        try {
            val cardVideoViewClass = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardVideoView", cl
            ) ?: return

            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    ViewExt.squashAncestorFeedCard(v)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            }

            XposedBridge.hookAllConstructors(cardVideoViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isFeedVideoBlockEnabled || param.thisObject !is View) return
                    val videoView = param.thisObject as View

                    if (videoView.getTag(ViewExt.TAG_EMPTY_VIEW) != true) {
                        videoView.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                        videoView.addOnAttachStateChangeListener(attachListener)
                        if (videoView.isAttachedToWindow) ViewExt.squashAncestorFeedCard(videoView)
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook CardVideoView constructors: ${t.message}")
        }
    }

    private fun hookFeedMyForumCards(cl: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "com.baidu.tieba.feed.component.CardFeedMyForumView", cl
            ) ?: return

            val squashHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (ConfigManager.isMyForumBlockEnabled && param.thisObject is View) {
                        ViewExt.squashView(param.thisObject as View)
                    }
                }
            }

            XposedBridge.hookAllConstructors(clazz, squashHook)
            XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", squashHook)

            XposedBridge.hookAllMethods(clazz, "onMeasure", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isMyForumBlockEnabled) return
                    if (param.thisObject is View) ViewExt.squashView(param.thisObject as View)
                    param.result = null
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed hook CardFeedMyForumView: ${t.message}")
        }
    }

    private val NULL_METHOD = Any()
    private val sTemplateKeyMethodCache = ConcurrentHashMap<Class<*>, Any>()

    private fun filterOutTemplateKeys(list: List<*>?, keysToRemove: Set<String>): List<*>? {
        if (list == null || keysToRemove.isEmpty()) return list
        val size = list.size
        if (size == 0) return list

        var filtered: ArrayList<Any?>? = null
        for (i in 0 until size) {
            val item: Any? = try { list[i] } catch (_: Throwable) { null }
            val key = getTemplateKey(item)
            if (key != null && key in keysToRemove) {
                if (filtered == null) {
                    filtered = ArrayList(size - 1)
                    for (j in 0 until i) {
                        try { filtered.add(list[j]) } catch (_: Throwable) {}
                    }
                }
            } else if (filtered != null) {
                filtered.add(item)
            }
        }
        return filtered ?: list
    }

    private fun getTemplateKey(item: Any?): String? {
        if (item == null) return null
        val cls = item.javaClass
        val cached = sTemplateKeyMethodCache[cls]
        if (cached != null) {
            if (cached === NULL_METHOD) return null
            return try {
                val key = (cached as java.lang.reflect.Method).invoke(item)
                if (key is String) key else null
            } catch (_: Throwable) {
                null
            }
        }
        return try {
            val method = cls.getDeclaredMethod("b")
            method.isAccessible = true
            sTemplateKeyMethodCache[cls] = method
            val key = method.invoke(item)
            if (key is String) key else null
        } catch (_: Throwable) {
            sTemplateKeyMethodCache[cls] = NULL_METHOD
            null
        }
    }
}
