package com.forbidad4tieba.hook.feature.ad

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.utils.ViewExt
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

object PostAdHook {
    private val adapterCache = WeakHashMap<Any, SparseArray<String>>()

    fun hook(cl: ClassLoader, adClasses: Array<Class<*>>) {
        try {
            val typeAdapterClass = XposedHelpers.findClassIfExists("com.baidu.adp.widget.ListView.TypeAdapter", cl) ?: return
            XposedBridge.hookAllMethods(
                typeAdapterClass,
                "getView",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val args = param.args
                        if (!ConfigManager.isAdBlockEnabled || args == null || args.size < 3) return

                        val cache = getOrCreateAdTypeCache(param.thisObject) ?: return
                        val position = args[0] as? Int ?: -1
                        if (position < 0) return

                        val viewType = safeGetItemViewType(param.thisObject, position)
                        if (viewType < 0) return
                        val cachedClassName = cache.get(viewType) ?: return

                        // Only fast-path when the reusable view class matches a previously confirmed ad root class.
                        val convertView = args[1] as? View ?: return
                        if (convertView.javaClass.name != cachedClassName) return
                        param.result = ViewExt.obtainEmptyView(convertView.context, convertView)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!ConfigManager.isAdBlockEnabled) return
                        val result = param.result as? View ?: return
                        if (ViewExt.isOurEmptyView(result)) return

                        val args = param.args
                        val position = args[0] as? Int ?: -1
                        val viewType = if (position >= 0) safeGetItemViewType(param.thisObject, position) else -1
                        val cache = getOrCreateAdTypeCache(param.thisObject)
                        val cachedClassName = if (cache != null && viewType >= 0) cache.get(viewType) else null

                        if (cachedClassName != null && result.javaClass.name == cachedClassName) {
                            val convertView = if (args.size > 1 && args[1] is View) args[1] as View else null
                            param.result = ViewExt.obtainEmptyView(result.context, convertView)
                            return
                        }

                        val isDirectAd = isAdView(result, adClasses)
                        var isAd = isDirectAd
                        if (!isAd && result is ViewGroup) {
                            isAd = containsAdChild(result, adClasses)
                        }

                        if (isAd) {
                            if (cache != null && viewType >= 0 && isDirectAd) {
                                cache.put(viewType, result.javaClass.name)
                            }
                            val convertView = if (args.size > 1 && args[1] is View) args[1] as View else null
                            param.result = ViewExt.obtainEmptyView(result.context, convertView)
                        }
                    }
                })

            val pbClass = XposedHelpers.findClassIfExists("com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter", cl)
            if (pbClass != null) {
                XposedBridge.hookAllMethods(
                    pbClass,
                    "onCreateViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!ConfigManager.isAdBlockEnabled) return
                            val holder = param.result ?: return
                            try {
                                val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View
                                if (itemView != null) ViewExt.squashView(itemView)
                            } catch (t: Throwable) {
                                XposedBridge.log("${Constants.TAG}: Failed to squash pb viewHolder itemView: ${t.message}")
                            }
                        }
                    })
            }

            XposedBridge.log("${Constants.TAG}: Post page ad view interception installed")
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook post page ads: ${t.message}")
        }
    }

    private fun getOrCreateAdTypeCache(adapter: Any?): SparseArray<String>? {
        if (adapter == null) return null
        synchronized(adapterCache) {
            var cache = adapterCache[adapter]
            if (cache == null) {
                cache = SparseArray()
                adapterCache[adapter] = cache
            }
            return cache
        }
    }

    private fun safeGetItemViewType(adapter: Any, position: Int): Int {
        return try {
            val vt = XposedHelpers.callMethod(adapter, "getItemViewType", position)
            if (vt is Int) vt else -1
        } catch (_: Throwable) {
            -1
        }
    }

    private fun isAdView(view: View, adClasses: Array<Class<*>>): Boolean {
        for (cls in adClasses) {
            if (cls.isInstance(view)) return true
        }
        return false
    }

    private fun containsAdChild(parent: ViewGroup, adClasses: Array<Class<*>>): Boolean {
        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            for (cls in adClasses) {
                if (cls.isInstance(child)) return true
            }
        }
        return false
    }
}
