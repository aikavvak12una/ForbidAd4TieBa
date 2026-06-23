package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.dexkit.DexKitBridgeProvider
import com.forbidad4tieba.hook.symbol.dexkit.DexKitScanBridge
import com.forbidad4tieba.hook.symbol.model.*

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap

internal class HookSymbolScanContext(private val classLoader: ClassLoader) : AutoCloseable {
    private companion object {
        const val MAX_RECOVERABLE_PROBE_LOGS = 20
    }

    private val classCache = HashMap<String, Class<*>?>()
    private val instanceFieldsCache = IdentityHashMap<Class<*>, List<Field>>()
    private val instanceMethodsCache = IdentityHashMap<Class<*>, List<Method>>()
    private val dexKitBridgeCache = HashMap<String, DexKitScanBridge?>()
    private val recoverableProbeIssueTags = LinkedHashSet<String>()
    var scanErrors: MutableList<String>? = null

    fun findClass(name: String, cl: ClassLoader): Class<*>? {
        if (cl !== classLoader) return ScanReflection.safeFindClassUncached(name, cl)
        if (classCache.containsKey(name)) return classCache[name]
        val resolved = ScanReflection.safeFindClassUncached(name, cl)
        classCache[name] = resolved
        return resolved
    }

    fun collectInstanceFields(clazz: Class<*>): List<Field> {
        instanceFieldsCache[clazz]?.let { return it }
        val resolved = ScanReflection.collectInstanceFieldsUncached(clazz)
        instanceFieldsCache[clazz] = resolved
        return resolved
    }

    fun collectInstanceMethods(clazz: Class<*>): List<Method> {
        instanceMethodsCache[clazz]?.let { return it }
        val resolved = ScanReflection.collectInstanceMethodsUncached(clazz)
        instanceMethodsCache[clazz] = resolved
        return resolved
    }

    fun dexKitBridge(sourcePaths: List<String>, logger: ScanLogger?): DexKitScanBridge? {
        val key = sourcePaths.distinct().joinToString(separator = "\u0000")
        if (dexKitBridgeCache.containsKey(key)) return dexKitBridgeCache[key]
        val bridge = DexKitBridgeProvider.openFirstAvailable(sourcePaths, logger)
        dexKitBridgeCache[key] = bridge
        return bridge
    }

    fun shouldLogRecoverableProbeIssue(tag: String): Boolean {
        if (recoverableProbeIssueTags.contains(tag)) return false
        if (recoverableProbeIssueTags.size >= MAX_RECOVERABLE_PROBE_LOGS) return false
        recoverableProbeIssueTags.add(tag)
        return true
    }

    override fun close() {
        dexKitBridgeCache.values.filterNotNull().distinctBy { System.identityHashCode(it) }.forEach { bridge ->
            try {
                bridge.close()
            } catch (_: Throwable) {
            }
        }
        dexKitBridgeCache.clear()
    }
}

internal object HookSymbolScanSession {
    private val current = ThreadLocal<HookSymbolScanContext?>()

    fun get(): HookSymbolScanContext? = current.get()

    fun set(context: HookSymbolScanContext?) {
        current.set(context)
    }
}
