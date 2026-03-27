package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object HomeTabHook {
    private val sListFieldCache = ConcurrentHashMap<Class<*>, Field>()
    private val sTypeFieldCache = ConcurrentHashMap<Class<*>, Field>()

    internal fun hook(cl: ClassLoader, symbols: HookSymbols) {
        val className = symbols.homeTabClass ?: return
        val methodName = symbols.homeTabRebuildMethod ?: return
        val listFieldName = symbols.homeTabListField ?: return
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHomeTabSimplifyEnabled) return
                    @Suppress("UNCHECKED_CAST")
                    val list = resolveMutableListField(param.thisObject, listFieldName) as? MutableList<Any?> ?: return
                    val it = list.iterator()
                    while (it.hasNext()) {
                        val pm6 = it.next()
                        if (pm6 != null) {
                            val type = readItemType(pm6)
                            if (type != 0 && type != 1) it.remove()
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook home tabs($className.$methodName): ${t.message}")
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
        } catch (_: Throwable) {}
        
        val found = cls.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        return try {
            found.isAccessible = true
            sListFieldCache[cls] = found
            found.get(owner)
        } catch (_: Throwable) { null }
    }

    private fun readItemType(item: Any): Int {
        val cls = item.javaClass
        val cached = sTypeFieldCache[cls]
        if (cached != null) {
            return try { cached.getInt(item) } catch (_: Throwable) { -1 }
        }
        try {
            val field = cls.getDeclaredField("a")
            field.isAccessible = true
            sTypeFieldCache[cls] = field
            return field.getInt(item)
        } catch (_: Throwable) {}
        
        val found = cls.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType } ?: return -1
        return try {
            found.isAccessible = true
            sTypeFieldCache[cls] = found
            found.getInt(item)
        } catch (_: Throwable) { -1 }
    }
}
