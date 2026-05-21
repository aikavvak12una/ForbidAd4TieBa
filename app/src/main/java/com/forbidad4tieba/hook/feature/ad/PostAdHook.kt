package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PostAdHook {
    private const val ADVERT_APP_INFO_CLASS = "com.baidu.tbadk.core.data.AdvertAppInfo"

    private val ADVERT_APP_TYPE_FIELDS = arrayOf(
        "TYPE_FRS_ADVERT_APP_EMPTY",
        "TYPE_FRS_ADVERT_APP_SINGLE_PIC",
        "TYPE_FRS_ADVERT_APP_MULTI_PIC",
        "TYPE_FRS_ADVERT_APP_VIDEO",
        "TYPE_FRS_ADVERT_APP_VR_VIDEO",
        "TYPE_PB_ADVERT_APP_EMPTY",
        "TYPE_RECOMMEND_ADVERT_APP_EMPTY",
        "TYPE_RECOMMEND_ADVERT_APP_SINGLE_PIC",
        "TYPE_RECOMMEND_ADVERT_APP_VIDEO",
        "TYPE_ADVERT_LEGO_APP",
        "TYPE_ADVERT_LEGO_APP_SINGLE",
        "TYPE_ADVERT_LEGO_APP_MULTI",
        "TYPE_ADVERT_LEGO_APP_VIDEO",
        "TYPE_ADVERT_LEGO_APP_SMALL_PIC",
        "TYPE_ADVERT_LEGO_APP_SMALL_VIDEO_PIC",
        "TYPE_ADVERT_FUN_AD_TEMPLETE",
        "TYPE_ADVERT_FUN_AD_EMPTY",
        "TYPE_ADVERT_FUN_AD_PLACEHOLDER",
        "TYPE_ADVERT_FUN_AD_COMMENT_PLACEHOLDER",
    )

    private data class RuntimeFilter(
        val itemClass: Class<*>,
        val getTypeMethod: Method,
        val blockedTypes: Set<Any>,
    )

    @Volatile private var hooked = false
    private val filterErrorLogged = AtomicBoolean(false)

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val setDataMethodName = symbols.typeAdapterSetDataMethod?.takeIf { it.isNotBlank() } ?: run {
                resetHooked()
                XposedCompat.log("[PostAdHook] skipped: typeAdapterSetDataMethod missing")
                return
            }
            val setDataMethod = resolveTypeAdapterSetDataMethod(cl, setDataMethodName) ?: run {
                resetHooked()
                XposedCompat.log("[PostAdHook] method NOT FOUND: TypeAdapter.$setDataMethodName(List)")
                return
            }
            val runtimeFilter = resolveRuntimeFilter(cl, symbols) ?: run {
                resetHooked()
                XposedCompat.log("[PostAdHook] skipped: runtime filter incomplete")
                return
            }

            mod.hook(setDataMethod).intercept { chain ->
                if (!ConfigManager.isAdBlockEnabled) {
                    return@intercept chain.proceed()
                }
                val list = chain.args.firstOrNull() as? List<*> ?: return@intercept chain.proceed()
                val filtered = filterItems(list, runtimeFilter)
                if (filtered === list) {
                    chain.proceed()
                } else {
                    XposedCompat.logD {
                        "[PostAdHook] > TypeAdapter.$setDataMethodName filtered: ${list.size} -> ${filtered.size}"
                    }
                    chain.proceed(arrayOf<Any?>(filtered))
                }
            }
            XposedCompat.log("[PostAdHook] TypeAdapter data filter hook INSTALLED")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PostAdHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveTypeAdapterSetDataMethod(cl: ClassLoader, methodName: String): Method? {
        val adapterClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl) ?: return null
        return try {
            adapterClass.getDeclaredMethod(methodName, List::class.java).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun resolveRuntimeFilter(cl: ClassLoader, symbols: HookSymbols): RuntimeFilter? {
        val itemClassName = symbols.typeAdapterDataItemClass?.takeIf { it.isNotBlank() } ?: return null
        val getTypeMethodName = symbols.typeAdapterDataGetTypeMethod?.takeIf { it.isNotBlank() } ?: return null
        val itemClass = XposedCompat.findClassOrNull(itemClassName, cl) ?: return null
        val getTypeMethod = try {
            itemClass.getMethod(getTypeMethodName).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            return null
        }

        val blockedTypes = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        addAdvertAppTypes(cl, blockedTypes)
        if (blockedTypes.isEmpty()) return null

        return RuntimeFilter(
            itemClass = itemClass,
            getTypeMethod = getTypeMethod,
            blockedTypes = blockedTypes,
        )
    }

    private fun addAdvertAppTypes(cl: ClassLoader, out: MutableSet<Any>) {
        val advertAppInfoClass = XposedCompat.findClassOrNull(ADVERT_APP_INFO_CLASS, cl) ?: return
        for (fieldName in ADVERT_APP_TYPE_FIELDS) {
            val value = try {
                XposedCompat.findField(advertAppInfoClass, fieldName).get(null)
            } catch (_: Throwable) {
                null
            } ?: continue
            out.add(value)
        }
    }

    private fun filterItems(list: List<*>, runtimeFilter: RuntimeFilter): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null

        for (i in 0 until size) {
            val item = list[i]
            val block = isBlockedItem(item, runtimeFilter)
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

    private fun isBlockedItem(item: Any?, runtimeFilter: RuntimeFilter): Boolean {
        if (item == null || !runtimeFilter.itemClass.isInstance(item)) return false
        val type = try {
            runtimeFilter.getTypeMethod.invoke(item)
        } catch (t: Throwable) {
            logFilterFailureOnce(t)
            null
        } ?: return false
        return runtimeFilter.blockedTypes.contains(type)
    }

    private fun logFilterFailureOnce(t: Throwable) {
        if (filterErrorLogged.compareAndSet(false, true)) {
            XposedCompat.log("[PostAdHook] item type resolve FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
