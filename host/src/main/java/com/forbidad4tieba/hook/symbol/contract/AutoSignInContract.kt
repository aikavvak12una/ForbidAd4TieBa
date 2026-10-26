package com.forbidad4tieba.hook.symbol.contract

import android.webkit.WebView
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.AutoSignInHybridNativeProxySymbols
import com.forbidad4tieba.hook.symbol.model.AutoSignInNetworkSymbols
import com.forbidad4tieba.hook.symbol.model.AutoSignInScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.AutoSignInSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONObject

/** Owns the cached descriptors and host rules for this capability. */
object AutoSignInContract : SymbolContract("AutoSignIn") {
    val autoSignInNetworkClass = text("autoSignInNetworkClass")
    val autoSignInNetworkConstructorSpec = text("autoSignInNetworkConstructorSpec")
    val autoSignInNetworkAddPostDataMethod = text("autoSignInNetworkAddPostDataMethod")
    val autoSignInNetworkPostNetDataMethod = text("autoSignInNetworkPostNetDataMethod")
    val autoSignInNetworkSetNeedTbsMethod = text("autoSignInNetworkSetNeedTbsMethod")
    val autoSignInNetworkSetNeedSigMethod = text("autoSignInNetworkSetNeedSigMethod")
    val autoSignInTbConfigClass = text("autoSignInTbConfigClass")
    val autoSignInServerAddressField = text("autoSignInServerAddressField")
    val autoSignInCoreApplicationClass = text("autoSignInCoreApplicationClass")
    val autoSignInCurrentAccountMethod = text("autoSignInCurrentAccountMethod")
    val autoSignInHybridProxyClass = text("autoSignInHybridProxyClass")
    val autoSignInHybridProxyConstructorSpec = text("autoSignInHybridProxyConstructorSpec")
    val autoSignInHybridJsBridgeClass = text("autoSignInHybridJsBridgeClass")
    val autoSignInHybridNativeNetworkProxyMethod = text("autoSignInHybridNativeNetworkProxyMethod")
    val autoSignInHybridTaskClass = text("autoSignInHybridTaskClass")
    val autoSignInHybridTaskConstructorSpec = text("autoSignInHybridTaskConstructorSpec")
    val autoSignInHybridTaskDoInBackgroundMethod = text("autoSignInHybridTaskDoInBackgroundMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val autoSignInScan = runScanStep(
            "AutoSignInManager",
            logger,
            scanErrors,
            AutoSignInScanSymbols(),
        ) {
            AutoSignInSymbolScanner.scan(cl, logger)
        }

        val autoSignInNetworkClass: String? = autoSignInScan.networkClass

        val autoSignInNetworkConstructorSpec: String? = autoSignInScan.networkConstructorSpec

        val autoSignInNetworkAddPostDataMethod: String? = autoSignInScan.addPostDataMethod

        val autoSignInNetworkPostNetDataMethod: String? = autoSignInScan.postNetDataMethod

        val autoSignInNetworkSetNeedTbsMethod: String? = autoSignInScan.setNeedTbsMethod

        val autoSignInNetworkSetNeedSigMethod: String? = autoSignInScan.setNeedSigMethod

        val autoSignInTbConfigClass: String? = autoSignInScan.tbConfigClass

        val autoSignInServerAddressField: String? = autoSignInScan.serverAddressField

        val autoSignInCoreApplicationClass: String? = autoSignInScan.coreApplicationClass

        val autoSignInCurrentAccountMethod: String? = autoSignInScan.currentAccountMethod

        val autoSignInHybridProxyClass: String? = autoSignInScan.hybridProxyClass

        val autoSignInHybridProxyConstructorSpec: String? = autoSignInScan.hybridProxyConstructorSpec

        val autoSignInHybridJsBridgeClass: String? = autoSignInScan.hybridJsBridgeClass

        val autoSignInHybridNativeNetworkProxyMethod: String? = autoSignInScan.hybridNativeNetworkProxyMethod

        val autoSignInHybridTaskClass: String? = autoSignInScan.hybridTaskClass

        val autoSignInHybridTaskConstructorSpec: String? = autoSignInScan.hybridTaskConstructorSpec

        val autoSignInHybridTaskDoInBackgroundMethod: String? = autoSignInScan.hybridTaskDoInBackgroundMethod

        output[AutoSignInContract.autoSignInNetworkClass] = autoSignInNetworkClass
        output[AutoSignInContract.autoSignInNetworkConstructorSpec] = autoSignInNetworkConstructorSpec
        output[AutoSignInContract.autoSignInNetworkAddPostDataMethod] = autoSignInNetworkAddPostDataMethod
        output[AutoSignInContract.autoSignInNetworkPostNetDataMethod] = autoSignInNetworkPostNetDataMethod
        output[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod] = autoSignInNetworkSetNeedTbsMethod
        output[AutoSignInContract.autoSignInNetworkSetNeedSigMethod] = autoSignInNetworkSetNeedSigMethod
        output[AutoSignInContract.autoSignInTbConfigClass] = autoSignInTbConfigClass
        output[AutoSignInContract.autoSignInServerAddressField] = autoSignInServerAddressField
        output[AutoSignInContract.autoSignInCoreApplicationClass] = autoSignInCoreApplicationClass
        output[AutoSignInContract.autoSignInCurrentAccountMethod] = autoSignInCurrentAccountMethod
        output[AutoSignInContract.autoSignInHybridProxyClass] = autoSignInHybridProxyClass
        output[AutoSignInContract.autoSignInHybridProxyConstructorSpec] = autoSignInHybridProxyConstructorSpec
        output[AutoSignInContract.autoSignInHybridJsBridgeClass] = autoSignInHybridJsBridgeClass
        output[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod] = autoSignInHybridNativeNetworkProxyMethod
        output[AutoSignInContract.autoSignInHybridTaskClass] = autoSignInHybridTaskClass
        output[AutoSignInContract.autoSignInHybridTaskConstructorSpec] = autoSignInHybridTaskConstructorSpec
        output[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod] = autoSignInHybridTaskDoInBackgroundMethod
    }

    fun resolveAutoSignInNetworkSymbols(cl: ClassLoader, symbols: HookSymbols): AutoSignInNetworkSymbols =
        restoreNativeNetwork(symbols, cl, includeHybrid = true)

    private fun restoreNativeNetwork(symbols: HookSymbols, cl: ClassLoader, includeHybrid: Boolean): AutoSignInNetworkSymbols {
        fun required(field: SymbolField<String?>): String =
            requireNotNull(symbols[field]?.takeIf { it.isNotBlank() }) { "Missing ${field.cacheKey}" }
        fun owner(field: SymbolField<String?>): Class<*> =
            Class.forName(required(field), false, cl)
        fun method(owner: Class<*>, name: SymbolField<String?>, result: Class<*>?, static: Boolean,
                   vararg params: Class<*>): Method = owner.getDeclaredMethod(required(name), *params).apply {
            check(Modifier.isStatic(modifiers) == static && (result == null || returnType == result)) {
                "Invalid ${name.cacheKey}"
            }
            isAccessible = true
        }
        check(required(autoSignInNetworkConstructorSpec) == "java.lang.String") { "Invalid native network constructor" }
        val network = owner(autoSignInNetworkClass)
        val config = owner(autoSignInTbConfigClass)
        val application = owner(autoSignInCoreApplicationClass)
        val address = config.getDeclaredField(required(autoSignInServerAddressField)).apply {
            check(Modifier.isStatic(modifiers) && type == String::class.java) { "Invalid server address field" }
            isAccessible = true
        }
        return AutoSignInNetworkSymbols(
            network.getDeclaredConstructor(String::class.java).apply { isAccessible = true },
            method(network, autoSignInNetworkAddPostDataMethod, Void.TYPE, false, String::class.java, String::class.java),
            method(network, autoSignInNetworkPostNetDataMethod, null, false),
            method(network, autoSignInNetworkSetNeedTbsMethod, Void.TYPE, false, Boolean::class.javaPrimitiveType!!),
            method(network, autoSignInNetworkSetNeedSigMethod, Void.TYPE, false, Boolean::class.javaPrimitiveType!!),
            address,
            method(application, autoSignInCurrentAccountMethod, String::class.java, true),
            if (includeHybrid) resolveAutoSignInHybridNativeProxySymbols(cl, symbols) else null,
        )
    }

    fun resolveAutoSignInHybridNativeProxySymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AutoSignInHybridNativeProxySymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[AutoSignIn] hybrid native proxy skipped: scan symbols unavailable")
                return null
            }
            fun required(name: String, value: String?): String? {
                return value?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[AutoSignIn] hybrid native proxy skipped: missing $name")
                    null
                }
            }

            val proxyClassName = required(
                "autoSignInHybridProxyClass",
                resolvedSymbols[AutoSignInContract.autoSignInHybridProxyClass],
            ) ?: return null
            val proxyConstructorSpec = required(
                "autoSignInHybridProxyConstructorSpec",
                resolvedSymbols[AutoSignInContract.autoSignInHybridProxyConstructorSpec],
            ) ?: return null
            val jsBridgeClassName = required(
                "autoSignInHybridJsBridgeClass",
                resolvedSymbols[AutoSignInContract.autoSignInHybridJsBridgeClass],
            ) ?: return null
            val nativeNetworkProxyMethodName = required(
                "autoSignInHybridNativeNetworkProxyMethod",
                resolvedSymbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod],
            ) ?: return null
            val taskClassName = required(
                "autoSignInHybridTaskClass",
                resolvedSymbols[AutoSignInContract.autoSignInHybridTaskClass],
            ) ?: return null
            val taskConstructorSpec = required(
                "autoSignInHybridTaskConstructorSpec",
                resolvedSymbols[AutoSignInContract.autoSignInHybridTaskConstructorSpec],
            ) ?: return null
            val doInBackgroundMethodName = required(
                "autoSignInHybridTaskDoInBackgroundMethod",
                resolvedSymbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod],
            ) ?: return null
            val proxyClass = ScanReflection.safeFindClass(proxyClassName, cl) ?: error("proxy class not found: $proxyClassName")
            val jsBridgeClass =
                ScanReflection.safeFindClass(jsBridgeClassName, cl) ?: error("js bridge class not found: $jsBridgeClassName")
            val proxyConstructor = proxyClass.declaredConstructors.singleOrNull { constructor ->
                proxyConstructorSpec == "jsBridge" &&
                constructor.parameterTypes.size == 1 &&
                    constructor.parameterTypes[0] == jsBridgeClass
            } ?: error("proxy constructor mismatch: $proxyClassName($jsBridgeClassName)")
            val nativeNetworkProxyMethod = jsBridgeClass.declaredMethods.singleOrNull { method ->
                method.name == nativeNetworkProxyMethodName &&
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 7 &&
                    method.parameterTypes[0] == android.webkit.WebView::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == String::class.java &&
                    method.parameterTypes[4] == JSONObject::class.java &&
                    ScanReflection.isIntType(method.parameterTypes[5]) &&
                    ScanReflection.isIntType(method.parameterTypes[6]) &&
                    method.returnType != Void.TYPE
            } ?: error("bridge method mismatch: $jsBridgeClassName.$nativeNetworkProxyMethodName")
            val taskClass = ScanReflection.safeFindClass(taskClassName, cl) ?: error("task class not found: $taskClassName")
            val taskConstructor = taskClass.declaredConstructors.singleOrNull { constructor ->
                taskConstructorSpec ==
                    "java.lang.String,java.lang.String,int,int,long,java.util.HashMap,android.webkit.WebView" &&
                    isHybridNativeProxyTaskConstructor(constructor)
            } ?: error("task constructor mismatch: $taskClassName")
            val doInBackgroundMethod = taskClass.declaredMethods.singleOrNull { method ->
                method.name == doInBackgroundMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isArray &&
                    method.parameterTypes[0].componentType == Any::class.java &&
                    (java.util.Map::class.java.isAssignableFrom(method.returnType) ||
                        method.returnType == Any::class.java)
            } ?: error("task doInBackground mismatch: $taskClassName.$doInBackgroundMethodName")

            nativeNetworkProxyMethod.isAccessible = true
            taskConstructor.isAccessible = true
            doInBackgroundMethod.isAccessible = true
            Diagnostics.log(
                "[AutoSignIn] hybrid native proxy resolved: " +
                    "${jsBridgeClass.name}.${nativeNetworkProxyMethod.name} task=${taskClass.name}",
            )
            AutoSignInHybridNativeProxySymbols(
                jsBridgeClass = jsBridgeClass,
                nativeNetworkProxyMethod = nativeNetworkProxyMethod,
                taskConstructor = taskConstructor,
                doInBackgroundMethod = doInBackgroundMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[AutoSignIn] hybrid native proxy resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun isHybridNativeProxyTaskConstructor(constructor: Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 7 &&
            types[0] == String::class.java &&
            types[1] == String::class.java &&
            ScanReflection.isIntType(types[2]) &&
            ScanReflection.isIntType(types[3]) &&
            types[4] == Long::class.javaPrimitiveType &&
            java.util.HashMap::class.java.isAssignableFrom(types[5]) &&
            types[6] == android.webkit.WebView::class.java
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val autoSignInCritical = ArrayList<String>(10)
        if (symbols[AutoSignInContract.autoSignInNetworkClass].isNullOrBlank()) autoSignInCritical.add("autoSignInNetworkClass")
        if (symbols[AutoSignInContract.autoSignInNetworkConstructorSpec].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkConstructorSpec")
        }
        if (symbols[AutoSignInContract.autoSignInNetworkAddPostDataMethod].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkAddPostDataMethod")
        }
        if (symbols[AutoSignInContract.autoSignInNetworkPostNetDataMethod].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkPostNetDataMethod")
        }
        if (symbols[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkSetNeedTbsMethod")
        }
        if (symbols[AutoSignInContract.autoSignInNetworkSetNeedSigMethod].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInNetworkSetNeedSigMethod")
        }
        if (symbols[AutoSignInContract.autoSignInTbConfigClass].isNullOrBlank()) autoSignInCritical.add("autoSignInTbConfigClass")
        if (symbols[AutoSignInContract.autoSignInServerAddressField].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInServerAddressField")
        }
        if (symbols[AutoSignInContract.autoSignInCoreApplicationClass].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInCoreApplicationClass")
        }
        if (symbols[AutoSignInContract.autoSignInCurrentAccountMethod].isNullOrBlank()) {
            autoSignInCritical.add("autoSignInCurrentAccountMethod")
        }
        val autoSignInOptional = ArrayList<String>(7)
        if (symbols[AutoSignInContract.autoSignInHybridProxyClass].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridProxyClass")
        }
        if (symbols[AutoSignInContract.autoSignInHybridProxyConstructorSpec].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridProxyConstructorSpec")
        }
        if (symbols[AutoSignInContract.autoSignInHybridJsBridgeClass].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridJsBridgeClass")
        }
        if (symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridNativeNetworkProxyMethod")
        }
        if (symbols[AutoSignInContract.autoSignInHybridTaskClass].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskClass")
        }
        if (symbols[AutoSignInContract.autoSignInHybridTaskConstructorSpec].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskConstructorSpec")
        }
        if (symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod].isNullOrBlank()) {
            autoSignInOptional.add("autoSignInHybridTaskDoInBackgroundMethod")
        }
        out[HookFeatureKey.AUTO_SIGN_IN] = when {
            autoSignInCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = autoSignInCritical,
                missingOptional = autoSignInOptional,
            )
            autoSignInOptional.isEmpty() -> HookFeatureStatus(state = HookFeatureState.FULL)
            else -> HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = autoSignInOptional)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AutoSignInManager.NativeNetworkBridge",
            "${symbols[AutoSignInContract.autoSignInNetworkClass]}.${symbols[AutoSignInContract.autoSignInNetworkAddPostDataMethod]}/" +
                "${symbols[AutoSignInContract.autoSignInNetworkPostNetDataMethod]} " +
                "config=${symbols[AutoSignInContract.autoSignInTbConfigClass]}[${symbols[AutoSignInContract.autoSignInServerAddressField]}] " +
                "account=${symbols[AutoSignInContract.autoSignInCoreApplicationClass]}.${symbols[AutoSignInContract.autoSignInCurrentAccountMethod]}",
            listOf(
                AutoSignInContract.autoSignInNetworkClass.check(symbols),
                AutoSignInContract.autoSignInNetworkConstructorSpec.check(symbols),
                AutoSignInContract.autoSignInNetworkAddPostDataMethod.check(symbols),
                AutoSignInContract.autoSignInNetworkPostNetDataMethod.check(symbols),
                AutoSignInContract.autoSignInNetworkSetNeedTbsMethod.check(symbols),
                AutoSignInContract.autoSignInNetworkSetNeedSigMethod.check(symbols),
                AutoSignInContract.autoSignInTbConfigClass.check(symbols),
                AutoSignInContract.autoSignInServerAddressField.check(symbols),
                AutoSignInContract.autoSignInCoreApplicationClass.check(symbols),
                AutoSignInContract.autoSignInCurrentAccountMethod.check(symbols),
            ),
        )
        addOptional(
            "AutoSignInManager.HybridNativeProxy",
            "${symbols[AutoSignInContract.autoSignInHybridJsBridgeClass]}." +
                "${symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod]} " +
                "task=${symbols[AutoSignInContract.autoSignInHybridTaskClass]}." +
                symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod],
            listOf(
                AutoSignInContract.autoSignInHybridProxyClass.check(symbols),
                AutoSignInContract.autoSignInHybridProxyConstructorSpec.check(symbols),
                AutoSignInContract.autoSignInHybridJsBridgeClass.check(symbols),
                "autoSignInHybridNativeNetworkProxyMethod" to
                    has(symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod]),
                AutoSignInContract.autoSignInHybridTaskClass.check(symbols),
                AutoSignInContract.autoSignInHybridTaskConstructorSpec.check(symbols),
                "autoSignInHybridTaskDoInBackgroundMethod" to
                    has(symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod]),
            ),
        )
    }.build()

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasAutoSignInNativeNetworkSymbols =
            symbols[AutoSignInContract.autoSignInNetworkClass] != null ||
                symbols[AutoSignInContract.autoSignInNetworkConstructorSpec] != null ||
                symbols[AutoSignInContract.autoSignInNetworkAddPostDataMethod] != null ||
                symbols[AutoSignInContract.autoSignInNetworkPostNetDataMethod] != null ||
                symbols[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod] != null ||
                symbols[AutoSignInContract.autoSignInNetworkSetNeedSigMethod] != null ||
                symbols[AutoSignInContract.autoSignInTbConfigClass] != null ||
                symbols[AutoSignInContract.autoSignInServerAddressField] != null ||
                symbols[AutoSignInContract.autoSignInCoreApplicationClass] != null ||
                symbols[AutoSignInContract.autoSignInCurrentAccountMethod] != null
        if (hasAutoSignInNativeNetworkSymbols && !isAutoSignInNativeNetworkValid(symbols, cl)) return false
        val hasAutoSignInHybridNativeProxySymbols =
            symbols[AutoSignInContract.autoSignInHybridProxyClass] != null ||
                symbols[AutoSignInContract.autoSignInHybridProxyConstructorSpec] != null ||
                symbols[AutoSignInContract.autoSignInHybridJsBridgeClass] != null ||
                symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod] != null ||
                symbols[AutoSignInContract.autoSignInHybridTaskClass] != null ||
                symbols[AutoSignInContract.autoSignInHybridTaskConstructorSpec] != null ||
                symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod] != null
        if (hasAutoSignInHybridNativeProxySymbols && !isAutoSignInHybridNativeProxyValid(symbols, cl)) {
            return false
        }
        return true
    }

    private fun isAutoSignInNativeNetworkValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        runCatching { restoreNativeNetwork(symbols, cl, includeHybrid = false) }.isSuccess

    private fun isAutoSignInHybridNativeProxyValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val proxyClassName = symbols[AutoSignInContract.autoSignInHybridProxyClass] ?: return false
        val jsBridgeClassName = symbols[AutoSignInContract.autoSignInHybridJsBridgeClass] ?: return false
        val nativeNetworkProxyMethodName = symbols[AutoSignInContract.autoSignInHybridNativeNetworkProxyMethod] ?: return false
        val taskClassName = symbols[AutoSignInContract.autoSignInHybridTaskClass] ?: return false
        val doInBackgroundMethodName = symbols[AutoSignInContract.autoSignInHybridTaskDoInBackgroundMethod] ?: return false
        if (symbols[AutoSignInContract.autoSignInHybridProxyConstructorSpec] != "jsBridge") return false
        if (
            symbols[AutoSignInContract.autoSignInHybridTaskConstructorSpec] !=
            "java.lang.String,java.lang.String,int,int,long,java.util.HashMap,android.webkit.WebView"
        ) {
            return false
        }
        return try {
            val proxyClass = ScanReflection.safeFindClass(proxyClassName, cl) ?: return false
            val jsBridgeClass = ScanReflection.safeFindClass(jsBridgeClassName, cl) ?: return false
            val taskClass = ScanReflection.safeFindClass(taskClassName, cl) ?: return false
            proxyClass.declaredConstructors.any { constructor ->
                constructor.parameterTypes.size == 1 &&
                    constructor.parameterTypes[0] == jsBridgeClass
            } &&
                jsBridgeClass.declaredMethods.any { method ->
                    method.name == nativeNetworkProxyMethodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 7 &&
                        method.parameterTypes[0] == WebView::class.java &&
                        method.parameterTypes[1] == String::class.java &&
                        method.parameterTypes[2] == String::class.java &&
                        method.parameterTypes[3] == String::class.java &&
                        method.parameterTypes[4] == JSONObject::class.java &&
                        ScanReflection.isIntType(method.parameterTypes[5]) &&
                        ScanReflection.isIntType(method.parameterTypes[6]) &&
                        method.returnType != Void.TYPE
                } &&
                taskClass.declaredConstructors.any(::isAutoSignInHybridTaskConstructor) &&
                taskClass.declaredMethods.any { method ->
                    method.name == doInBackgroundMethodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].isArray &&
                        method.parameterTypes[0].componentType == Any::class.java &&
                        (java.util.Map::class.java.isAssignableFrom(method.returnType) ||
                            method.returnType == Any::class.java)
                }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAutoSignInHybridTaskConstructor(constructor: java.lang.reflect.Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 7 &&
            types[0] == String::class.java &&
            types[1] == String::class.java &&
            ScanReflection.isIntType(types[2]) &&
            ScanReflection.isIntType(types[3]) &&
            types[4] == Long::class.javaPrimitiveType &&
            java.util.HashMap::class.java.isAssignableFrom(types[5]) &&
            types[6] == WebView::class.java
    }
}
