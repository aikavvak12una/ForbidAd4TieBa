package com.forbidad4tieba.hook.feature.ad

import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PostAdHook {
    private var sAdClasses: Array<Class<*>>? = null
    private val sTypeCache = ConcurrentHashMap<Int, String>()
    
    // Performance optimization: cache the getItemViewType method
    private val sItemViewTypeMethods = ConcurrentHashMap<Class<*>, Method>()

    fun hook(cl: ClassLoader) {
        val adClasses = resolveAdClasses(cl)
        XposedCompat.log("[PostAdHook] resolved ${adClasses.size} ad classes")
        if (adClasses.isEmpty()) return
        sAdClasses = adClasses

        hookTypeAdapterGetView(cl, adClasses)
        hookPbRecommendAdapter(cl)
    }

    // ── TypeAdapter.getView interception (post page ListView) ─────────────

    private fun hookTypeAdapterGetView(cl: ClassLoader, adClasses: Array<Class<*>>) {
        val mod = XposedCompat.module ?: return
        val adapterClass = XposedCompat.findClassOrNull(
            "com.baidu.adp.widget.ListView.TypeAdapter", cl
        ) ?: return

        for (method in adapterClass.declaredMethods) {
            if (method.name != "getView") continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (!ConfigManager.isAdBlockEnabled) {
                    return@intercept chain.proceed()
                }

                val args = chain.args
                val position = args.getOrNull(0) as? Int ?: -1
                val convertView = args.getOrNull(1) as? View

                // Before: check if we know this is an ad type and can short-circuit
                if (position >= 0) {
                    val viewType = getItemViewType(chain.thisObject, position)
                    val cachedName = if (viewType >= 0) sTypeCache[viewType] else null
                    if (cachedName != null && convertView != null && ViewExt.isOurEmptyView(convertView)) {
                        XposedCompat.logD("[PostAdHook] > getView pos=$position FAST-PATH cached ad type")
                        return@intercept ViewExt.obtainEmptyView(convertView.context, convertView)
                    }
                }

                // Proceed with original
                val result = chain.proceed()
                val resultView = result as? View ?: return@intercept result
                if (ViewExt.isOurEmptyView(resultView)) return@intercept result

                val viewType = if (position >= 0) getItemViewType(chain.thisObject, position) else -1
                val cachedName = if (viewType >= 0) sTypeCache[viewType] else null

                // Fast path: known ad type from cache
                if (cachedName != null && resultView.javaClass.name == cachedName) {
                    XposedCompat.logD("[PostAdHook] > getView pos=$position BLOCKED cached ad")
                    return@intercept ViewExt.obtainEmptyView(resultView.context, convertView)
                }

                val directAd = isAdView(resultView, adClasses)
                val isAd = directAd || (resultView is ViewGroup && containsAdChild(resultView, adClasses))

                if (isAd) {
                    XposedCompat.logD("[PostAdHook] > getView pos=$position BLOCKED ad (direct=$directAd)")
                    if (directAd && viewType >= 0) {
                        sTypeCache[viewType] = resultView.javaClass.name
                    }
                    return@intercept ViewExt.obtainEmptyView(resultView.context, convertView)
                }

                result
            }
        }

        XposedCompat.log("[PostAdHook] TypeAdapter.getView hook INSTALLED")
    }

    // ── PbFirstFloorRecommendAdapter interception ─────────────────────────

    private fun hookPbRecommendAdapter(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val pbClass = XposedCompat.findClassOrNull(
            "com.baidu.tieba.pb.widget.adapter.PbFirstFloorRecommendAdapter", cl
        ) ?: return

        for (method in pbClass.declaredMethods) {
            if (method.name != "onCreateViewHolder") continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.isAdBlockEnabled && result != null) {
                    try {
                        val itemView = XposedCompat.getObjectField(result, "itemView") as? View
                        if (itemView != null) ViewExt.squashView(itemView)
                    } catch (t: Throwable) {
                        XposedCompat.logD("hookPbRecommendAdapter failed: ${t.message}")
                    }
                }
                result
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun getItemViewType(adapter: Any?, position: Int): Int {
        if (adapter == null) return -1
        val cls = adapter.javaClass
        var method = sItemViewTypeMethods[cls]
        if (method == null) {
            method = XposedCompat.findMethodOrNull(cls, "getItemViewType", Int::class.javaPrimitiveType!!)
            if (method != null) {
                sItemViewTypeMethods[cls] = method
            } else {
                return -1
            }
        }
        return try {
            val vt = method.invoke(adapter, position)
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
            if (child is ViewGroup && child.childCount in 1..5) {
                if (containsAdChild(child, adClasses)) return true
            }
        }
        return false
    }

    private fun resolveAdClasses(cl: ClassLoader): Array<Class<*>> {
        val list = ArrayList<Class<*>>(Constants.AD_CLASS_NAMES.size)
        for (name in Constants.AD_CLASS_NAMES) {
            XposedCompat.findClassOrNull(name, cl)?.let { list.add(it) }
        }
        return list.toTypedArray()
    }
}
