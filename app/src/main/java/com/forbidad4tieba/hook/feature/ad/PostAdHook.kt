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

import java.util.concurrent.ConcurrentHashMap

object PostAdHook {
    private var sAdClasses: Array<Class<*>>? = null
    private val sTypeCache = ConcurrentHashMap<Int, String>()
    
    fun hook(cl: ClassLoader) {
        val adClasses = resolveAdClasses(cl)
        if (adClasses.isEmpty()) return
        sAdClasses = adClasses

        hookTypeAdapterGetView(cl, adClasses)
        hookPbRecommendAdapter(cl)
    }

    // ── TypeAdapter.getView interception (post page ListView) ─────────────

    private fun hookTypeAdapterGetView(cl: ClassLoader, adClasses: Array<Class<*>>) {
        val adapterClass = XposedHelpers.findClassIfExists(
            "com.baidu.adp.widget.ListView.TypeAdapter", cl
        ) ?: return

        XposedBridge.hookAllMethods(adapterClass, "getView", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isAdBlockEnabled) return
                val position = param.args?.getOrNull(0) as? Int ?: return
                if (position < 0) return

                val viewType = getItemViewType(param.thisObject, position)
                if (viewType < 0) return

                val cachedName = sTypeCache[viewType] ?: return
                val convertView = param.args?.getOrNull(1) as? View ?: return

                if (ViewExt.isOurEmptyView(convertView)) {
                    param.result = ViewExt.obtainEmptyView(convertView.context, convertView)
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isAdBlockEnabled) return
                val result = param.result as? View ?: return
                if (ViewExt.isOurEmptyView(result)) return

                val position = param.args?.getOrNull(0) as? Int ?: -1
                val viewType = if (position >= 0) getItemViewType(param.thisObject, position) else -1
                val cachedName = if (viewType >= 0) sTypeCache[viewType] else null

                // Fast path: known ad type from cache
                if (cachedName != null && result.javaClass.name == cachedName) {
                    replaceWithEmpty(param, result)
                    return
                }

                val directAd = isAdView(result, adClasses)
                val isAd = directAd || (result is ViewGroup && containsAdChild(result, adClasses))

                if (isAd) {
                    if (directAd && viewType >= 0) {
                        sTypeCache[viewType] = result.javaClass.name
                    }
                    replaceWithEmpty(param, result)
                }
            }
        })

        XposedBridge.log("${Constants.TAG}: Post page ad view interception installed")
    }

    // ── PbFirstFloorRecommendAdapter interception ─────────────────────────

    private fun hookPbRecommendAdapter(cl: ClassLoader) {
        val pbClass = XposedHelpers.findClassIfExists(
            "com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter", cl
        ) ?: return

        XposedBridge.hookAllMethods(pbClass, "onCreateViewHolder", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!ConfigManager.isAdBlockEnabled) return
                val holder = param.result ?: return
                try {
                    val itemView = XposedHelpers.getObjectField(holder, "itemView") as? View
                    if (itemView != null) ViewExt.squashView(itemView)
                } catch (_: Throwable) {}
            }
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun replaceWithEmpty(param: XC_MethodHook.MethodHookParam, original: View) {
        val convertView = param.args?.getOrNull(1) as? View
        param.result = ViewExt.obtainEmptyView(original.context, convertView)
    }

    private fun getItemViewType(adapter: Any, position: Int): Int {
        return try {
            val vt = XposedHelpers.callMethod(adapter, "getItemViewType", position)
            if (vt is Int) vt else -1
        } catch (_: Throwable) { -1 }
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
            val child = parent.getChildAt(i) ?: continue
            if (isAdView(child, adClasses)) return true
            // Only recurse one level deep to avoid deep tree traversal
            if (child is ViewGroup && child.childCount in 1..5) {
                if (containsAdChild(child, adClasses)) return true
            }
        }
        return false
    }

    private fun resolveAdClasses(cl: ClassLoader): Array<Class<*>> {
        val list = ArrayList<Class<*>>(Constants.AD_CLASS_NAMES.size)
        for (name in Constants.AD_CLASS_NAMES) {
            XposedHelpers.findClassIfExists(name, cl)?.let { list.add(it) }
        }
        return list.toTypedArray()
    }
}
