package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager

import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object HomeTabHook {
    private val sListFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val sTypeFieldCache = ConcurrentHashMap<Class<*>, Field>()

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val mod = XposedCompat.module ?: return
        val className = symbols.homeTabClass
        val methodName = symbols.homeTabRebuildMethod
        val listFieldName = symbols.homeTabListField
        if (className == null || methodName == null || listFieldName == null) {
            XposedCompat.log("[HomeTabHook] SKIP - missing symbols: class=$className, method=$methodName, field=$listFieldName")
            return
        }
        try {
            val method = XposedCompat.findMethodOrNull(className, cl, methodName)
            if (method == null) {
                XposedCompat.log("[HomeTabHook] method NOT FOUND: $className.$methodName")
                return
            }
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.isHomeTabSimplifyEnabled) {
                    @Suppress("UNCHECKED_CAST")
                    val list = resolveMutableListField(chain.thisObject, listFieldName) as? MutableList<Any?>
                    if (list != null) {
                        val sizeBefore = list.size
                        var typeField: Field? = null
                        var lastClass: Class<*>? = null
                        val it = list.iterator()
                        while (it.hasNext()) {
                            val pm6 = it.next()
                            if (pm6 != null) {
                                val cls = pm6.javaClass
                                if (cls != lastClass) {
                                    typeField = resolveTypeField(cls)
                                    lastClass = cls
                                }
                                val type = if (typeField != null) {
                                    try { typeField.getInt(pm6) } catch (_: Throwable) { -1 }
                                } else -1
                                if (type != 0 && type != 1) it.remove()
                            }
                        }
                        XposedCompat.logD("[HomeTabHook] > tabs filtered: $sizeBefore -> ${list.size}")
                    }
                }
                result
            }
            XposedCompat.log("[HomeTabHook] hook INSTALLED: $className.$methodName")
        } catch (t: Throwable) {
            XposedCompat.log("[HomeTabHook] FAILED ($className.$methodName): ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveMutableListField(owner: Any?, preferredFieldName: String): Any? {
        if (owner == null) return null
        val cls = owner.javaClass
        val cached = sListFieldCache[cls]
        if (cached != null) {
            return try { cached.get(owner) } catch (_: Throwable) { null }
        }
        try {
            val field = cls.getDeclaredField(preferredFieldName)
            field.isAccessible = true
            sListFieldCache[cls] = field
            return field.get(owner)
        } catch (t: Throwable) { XposedCompat.logD("HomeTabHook: ${t.message}") }

        val found = cls.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        return try {
            found.isAccessible = true
            sListFieldCache[cls] = found
            found.get(owner)
        } catch (_: Throwable) { null }
    }

    private fun resolveTypeField(cls: Class<*>): Field? {
        val cached = sTypeFieldCache[cls]
        if (cached != null) return cached
        try {
            val field = cls.getDeclaredField("a")
            field.isAccessible = true
            sTypeFieldCache[cls] = field
            return field
        } catch (t: Throwable) { XposedCompat.logD("HomeTabHook: ${t.message}") }

        val found = cls.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType } ?: return null
        found.isAccessible = true
        sTypeFieldCache[cls] = found
        return found
    }
}
