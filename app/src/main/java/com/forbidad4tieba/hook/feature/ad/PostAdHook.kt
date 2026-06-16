package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.PostAdDataFilterSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PostAdHook {
    private data class RuntimeFilter(
        val itemClass: Class<*>,
        val getTypeMethod: Method,
        val blockedTypes: Set<Any>,
        val blockedItemClasses: Array<Class<*>>,
        val classDecisions: ConcurrentHashMap<Class<*>, ClassDecision> = ConcurrentHashMap(32),
    )

    private enum class ClassDecision {
        BLOCKED,
        CHECK_TYPE,
        ALLOW,
    }

    @Volatile private var hooked = false
    private val filterErrorLogged = AtomicBoolean(false)

    internal fun hook(targets: PostAdDataFilterSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val setDataMethod = targets.setDataMethod
            val runtimeFilter = RuntimeFilter(
                itemClass = targets.itemClass,
                getTypeMethod = targets.getTypeMethod,
                blockedTypes = targets.blockedTypes,
                blockedItemClasses = targets.blockedItemClasses,
            )

            mod.hook(setDataMethod).intercept { chain ->
                if (!ConfigManager.isPostAdBlockEnabled) {
                    return@intercept chain.proceed()
                }
                val list = chain.args.firstOrNull() as? List<*> ?: return@intercept chain.proceed()
                val filtered = filterItems(list, runtimeFilter)
                if (filtered === list) {
                    chain.proceed()
                } else {
                    XposedCompat.logD {
                        "[PostAdHook] > TypeAdapter.${setDataMethod.name} filtered: ${list.size} -> ${filtered.size}"
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

    private fun filterItems(list: List<*>, runtimeFilter: RuntimeFilter): List<*> {
        val size = list.size
        var out: ArrayList<Any?>? = null
        var blockedCount = 0

        for (i in 0 until size) {
            val item = list[i]
            val block = isBlockedItem(item, runtimeFilter)
            if (block) {
                blockedCount += 1
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

    private fun isBlockedItem(item: Any?, runtimeFilter: RuntimeFilter): Boolean {
        if (item == null) return false
        when (classDecisionFor(item.javaClass, runtimeFilter)) {
            ClassDecision.BLOCKED -> return true
            ClassDecision.ALLOW -> return false
            ClassDecision.CHECK_TYPE -> Unit
        }
        val type = try {
            runtimeFilter.getTypeMethod.invoke(item)
        } catch (t: Throwable) {
            logFilterFailureOnce(t)
            null
        } ?: return false
        return runtimeFilter.blockedTypes.contains(type)
    }

    private fun classDecisionFor(clazz: Class<*>, runtimeFilter: RuntimeFilter): ClassDecision {
        runtimeFilter.classDecisions[clazz]?.let { return it }
        val resolved = when {
            runtimeFilter.blockedItemClasses.any { it.isAssignableFrom(clazz) } -> ClassDecision.BLOCKED
            runtimeFilter.itemClass.isAssignableFrom(clazz) -> ClassDecision.CHECK_TYPE
            else -> ClassDecision.ALLOW
        }
        return runtimeFilter.classDecisions.putIfAbsent(clazz, resolved) ?: resolved
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
