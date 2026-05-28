package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

internal class HookSymbolScanContext(private val classLoader: ClassLoader) {
    private val classCache = HashMap<String, Class<*>?>()
    private val instanceFieldsCache = IdentityHashMap<Class<*>, List<Field>>()
    private val instanceMethodsCache = IdentityHashMap<Class<*>, List<Method>>()
    var scanErrors: MutableList<String>? = null

    fun findClass(name: String, cl: ClassLoader): Class<*>? {
        if (cl !== classLoader) return HookSymbolResolver.safeFindClassUncached(name, cl)
        if (classCache.containsKey(name)) return classCache[name]
        val resolved = HookSymbolResolver.safeFindClassUncached(name, cl)
        classCache[name] = resolved
        return resolved
    }

    fun collectInstanceFields(clazz: Class<*>): List<Field> {
        instanceFieldsCache[clazz]?.let { return it }
        val resolved = HookSymbolResolver.collectInstanceFieldsUncached(clazz)
        instanceFieldsCache[clazz] = resolved
        return resolved
    }

    fun collectInstanceMethods(clazz: Class<*>): List<Method> {
        instanceMethodsCache[clazz]?.let { return it }
        val resolved = HookSymbolResolver.collectInstanceMethodsUncached(clazz)
        instanceMethodsCache[clazz] = resolved
        return resolved
    }
}

internal object HookSymbolScanSession {
    private val current = ThreadLocal<HookSymbolScanContext?>()

    fun get(): HookSymbolScanContext? = current.get()

    fun set(context: HookSymbolScanContext?) {
        current.set(context)
    }
}
