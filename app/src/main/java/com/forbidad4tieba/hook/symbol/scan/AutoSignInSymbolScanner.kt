package com.forbidad4tieba.hook.symbol.scan

import android.webkit.WebView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.AutoSignInScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.HashMap

internal object AutoSignInSymbolScanner {
    private const val HYBRID_JS_BRIDGE_PROXY_CLASS =
        "com.baidu.tieba.h5power.HybridJsBridgePlugin_Proxy"
    private const val NETWORK_CONSTRUCTOR_SPEC = "java.lang.String"
    private const val HYBRID_PROXY_CONSTRUCTOR_SPEC = "jsBridge"
    private const val HYBRID_TASK_CONSTRUCTOR_SPEC =
        "java.lang.String,java.lang.String,int,int,long,java.util.HashMap,android.webkit.WebView"

    fun scan(cl: ClassLoader, logger: ScanLogger?): AutoSignInScanSymbols {
        val native = scanNativeNetwork(cl, logger)
        val hybrid = scanHybridNativeProxy(cl, logger)
        return AutoSignInScanSymbols(
            networkClass = native.networkClass,
            networkConstructorSpec = native.networkConstructorSpec,
            addPostDataMethod = native.addPostDataMethod,
            postNetDataMethod = native.postNetDataMethod,
            setNeedTbsMethod = native.setNeedTbsMethod,
            setNeedSigMethod = native.setNeedSigMethod,
            tbConfigClass = native.tbConfigClass,
            serverAddressField = native.serverAddressField,
            coreApplicationClass = native.coreApplicationClass,
            currentAccountMethod = native.currentAccountMethod,
            hybridProxyClass = hybrid.hybridProxyClass,
            hybridProxyConstructorSpec = hybrid.hybridProxyConstructorSpec,
            hybridJsBridgeClass = hybrid.hybridJsBridgeClass,
            hybridNativeNetworkProxyMethod = hybrid.hybridNativeNetworkProxyMethod,
            hybridTaskClass = hybrid.hybridTaskClass,
            hybridTaskConstructorSpec = hybrid.hybridTaskConstructorSpec,
            hybridTaskDoInBackgroundMethod = hybrid.hybridTaskDoInBackgroundMethod,
        )
    }

    private fun scanNativeNetwork(cl: ClassLoader, logger: ScanLogger?): AutoSignInScanSymbols {
        val networkClass = ScanReflection.safeFindClass(StableTiebaHookPoints.NETWORK_CLASS, cl) ?: run {
            log(logger, "autoSignInNative: class not found ${StableTiebaHookPoints.NETWORK_CLASS}")
            return AutoSignInScanSymbols()
        }
        val tbConfigClass = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_CONFIG_CLASS, cl)
        val coreApplicationClass =
            ScanReflection.safeFindClass(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, cl)

        val networkConstructor = selectUniqueScanCandidate(
            "AutoSignIn.Native.NetworkConstructor",
            scanDeclaredConstructors("AutoSignIn.Native.Network", networkClass, logger)
                .orEmpty()
                .filter(::isNetworkConstructor),
            logger,
            ::describeConstructor,
        )
        val addPostDataMethod = exactDeclaredMethod(
            networkClass,
            StableTiebaHookPoints.METHOD_ADD_POST_DATA,
            logger,
        ) { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
        }
        val postNetDataMethod = exactDeclaredMethod(
            networkClass,
            StableTiebaHookPoints.METHOD_POST_NET_DATA,
            logger,
        ) { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty()
        }
        val setNeedTbsMethod = exactDeclaredMethod(
            networkClass,
            StableTiebaHookPoints.METHOD_SET_NEED_TBS,
            logger,
        ) { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        }
        val setNeedSigMethod = exactDeclaredMethod(
            networkClass,
            StableTiebaHookPoints.METHOD_SET_NEED_SIG,
            logger,
        ) { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        }
        val serverAddressField = tbConfigClass?.let { clazz ->
            selectUniqueScanCandidate(
                "AutoSignIn.Native.ServerAddressField",
                scanDeclaredFields("AutoSignIn.Native.TbConfig", clazz, logger)
                    .orEmpty()
                    .filter { field ->
                    field.name == StableTiebaHookPoints.FIELD_SERVER_ADDRESS &&
                        Modifier.isStatic(field.modifiers) &&
                        field.type == String::class.java
                    },
                logger,
            ) { field -> "${field.name}:${field.type.name.substringAfterLast('.')}" }
        }
        val currentAccountMethod = coreApplicationClass?.let { clazz ->
            selectUniqueScanCandidate(
                "AutoSignIn.Native.CurrentAccountMethod",
                scanDeclaredMethods("AutoSignIn.Native.CoreApplication", clazz, logger)
                    .orEmpty()
                    .filter { method ->
                    method.name == StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == String::class.java
                    },
                logger,
                ::describeMethod,
            )
        }

        log(
            logger,
            "autoSignInNative matched: network=${networkClass.name} " +
                "ctor=${networkConstructor != null} tbConfig=${tbConfigClass?.name ?: "-"} " +
                "core=${coreApplicationClass?.name ?: "-"}",
        )
        return AutoSignInScanSymbols(
            networkClass = networkClass.name,
            networkConstructorSpec = NETWORK_CONSTRUCTOR_SPEC.takeIf { networkConstructor != null },
            addPostDataMethod = addPostDataMethod?.name,
            postNetDataMethod = postNetDataMethod?.name,
            setNeedTbsMethod = setNeedTbsMethod?.name,
            setNeedSigMethod = setNeedSigMethod?.name,
            tbConfigClass = tbConfigClass?.name,
            serverAddressField = serverAddressField?.name,
            coreApplicationClass = coreApplicationClass?.name,
            currentAccountMethod = currentAccountMethod?.name,
        )
    }

    private fun scanHybridNativeProxy(cl: ClassLoader, logger: ScanLogger?): AutoSignInScanSymbols {
        val proxyClass = ScanReflection.safeFindClass(HYBRID_JS_BRIDGE_PROXY_CLASS, cl) ?: run {
            log(logger, "autoSignInHybrid: class not found $HYBRID_JS_BRIDGE_PROXY_CLASS")
            return AutoSignInScanSymbols()
        }
        val proxyConstructor = selectUniqueScanCandidate(
            "AutoSignIn.Hybrid.ProxyConstructor",
            scanDeclaredConstructors("AutoSignIn.Hybrid.Proxy", proxyClass, logger)
                .orEmpty()
                .filter(::isProxyConstructor),
            logger,
            ::describeConstructor,
        ) ?: run {
            log(logger, "autoSignInHybrid: proxy constructor missing")
            return AutoSignInScanSymbols(hybridProxyClass = proxyClass.name)
        }
        val jsBridgeClass = proxyConstructor.parameterTypes[0]
        val jsBridgeMethods = scanDeclaredMethods("AutoSignIn.Hybrid.NativeNetworkProxy", jsBridgeClass, logger)
            .orEmpty()
        val nativeNetworkProxyMethod = selectUniqueMethod(
            "AutoSignIn.Hybrid.NativeNetworkProxy",
            jsBridgeMethods.asSequence(),
            logger,
            ::isNativeNetworkProxyMethod,
        ) ?: return AutoSignInScanSymbols(
            hybridProxyClass = proxyClass.name,
            hybridProxyConstructorSpec = HYBRID_PROXY_CONSTRUCTOR_SPEC,
            hybridJsBridgeClass = jsBridgeClass.name,
        )
        val taskConstructor = findHybridNativeProxyTaskConstructor(jsBridgeClass, cl, logger) ?: run {
            log(logger, "autoSignInHybrid: task constructor missing")
            return AutoSignInScanSymbols(
                hybridProxyClass = proxyClass.name,
                hybridProxyConstructorSpec = HYBRID_PROXY_CONSTRUCTOR_SPEC,
                hybridJsBridgeClass = jsBridgeClass.name,
                hybridNativeNetworkProxyMethod = nativeNetworkProxyMethod.name,
            )
        }
        val taskClass = taskConstructor.declaringClass
        val doInBackgroundMethod = selectTaskDoInBackgroundMethod(taskClass, logger) ?: run {
            log(logger, "autoSignInHybrid: task doInBackground missing ${taskClass.name}")
            return AutoSignInScanSymbols(
                hybridProxyClass = proxyClass.name,
                hybridProxyConstructorSpec = HYBRID_PROXY_CONSTRUCTOR_SPEC,
                hybridJsBridgeClass = jsBridgeClass.name,
                hybridNativeNetworkProxyMethod = nativeNetworkProxyMethod.name,
                hybridTaskClass = taskClass.name,
                hybridTaskConstructorSpec = HYBRID_TASK_CONSTRUCTOR_SPEC,
            )
        }

        log(
            logger,
            "autoSignInHybrid matched: bridge=${jsBridgeClass.name}.${nativeNetworkProxyMethod.name} " +
                "task=${taskClass.name}.${doInBackgroundMethod.name}",
        )
        return AutoSignInScanSymbols(
            hybridProxyClass = proxyClass.name,
            hybridProxyConstructorSpec = HYBRID_PROXY_CONSTRUCTOR_SPEC,
            hybridJsBridgeClass = jsBridgeClass.name,
            hybridNativeNetworkProxyMethod = nativeNetworkProxyMethod.name,
            hybridTaskClass = taskClass.name,
            hybridTaskConstructorSpec = HYBRID_TASK_CONSTRUCTOR_SPEC,
            hybridTaskDoInBackgroundMethod = doInBackgroundMethod.name,
        )
    }

    private fun exactDeclaredMethod(
        clazz: Class<*>,
        name: String,
        logger: ScanLogger?,
        predicate: (Method) -> Boolean,
    ): Method? {
        val declaredMethods = scanDeclaredMethods("AutoSignIn.Native.${clazz.name}.$name", clazz, logger).orEmpty()
        return selectUniqueMethod(
            "AutoSignIn.Native.${clazz.name}.$name",
            declaredMethods.asSequence().filter { it.name == name },
            logger,
            predicate,
        )
    }

    private fun selectUniqueMethod(
        tag: String,
        methods: Sequence<Method>,
        logger: ScanLogger?,
        predicate: (Method) -> Boolean,
    ): Method? {
        return selectUniqueScanCandidate(tag, methods.filter(predicate).toList(), logger, ::describeMethod)
    }

    private fun findHybridNativeProxyTaskConstructor(
        jsBridgeClass: Class<*>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Constructor<*>? {
        val candidates = LinkedHashSet<Class<*>>()
        scanSubStep("AutoSignIn.Hybrid.DeclaredTaskClasses.Classes", logger, emptyList()) {
            jsBridgeClass.declaredClasses.toList()
        }.forEach { candidates.add(it) }
        for (suffix in 'a'..'z') {
            ScanReflection.safeFindClass("${jsBridgeClass.name}\$$suffix", cl)?.let { candidates.add(it) }
        }
        val constructors = scanSubStep("AutoSignIn.Hybrid.TaskConstructors.Constructors", logger, emptyList()) {
            candidates.flatMap { it.declaredConstructors.asIterable() }
        }
        val matches = constructors.filter(::isHybridTaskConstructor)
        if (matches.size == 1) return matches.single()
        log(
            logger,
            "AutoSignIn.Hybrid.TaskConstructors candidates=" +
                matches.joinToString(",") { it.declaringClass.name }.ifBlank { "-" },
        )
        return null
    }

    private fun selectTaskDoInBackgroundMethod(taskClass: Class<*>, logger: ScanLogger?): Method? {
        val candidates = scanDeclaredMethods("AutoSignIn.Hybrid.TaskDoInBackground", taskClass, logger)
            .orEmpty()
            .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].isArray &&
                        method.parameterTypes[0].componentType == Any::class.java &&
                        (java.util.Map::class.java.isAssignableFrom(method.returnType) ||
                            method.returnType == Any::class.java)
            }
        if (candidates.isEmpty()) return null
        val ranked = candidates.groupBy { method ->
            if (java.util.Map::class.java.isAssignableFrom(method.returnType)) 2 else 1
        }
        val bestScore = ranked.keys.maxOrNull() ?: return null
        val best = ranked.getValue(bestScore)
        if (best.size == 1) return best.single()
        log(
            logger,
            "AutoSignIn.Hybrid.TaskDoInBackground ambiguous=" +
                best.joinToString(",") { describeMethod(it) },
        )
        return null
    }

    private fun isNetworkConstructor(constructor: Constructor<*>): Boolean {
        return constructor.parameterTypes.contentEquals(arrayOf(String::class.java))
    }

    private fun isProxyConstructor(constructor: Constructor<*>): Boolean {
        return constructor.parameterTypes.size == 1 &&
            !constructor.parameterTypes[0].isPrimitive
    }

    private fun isNativeNetworkProxyMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.size == 7 &&
            method.parameterTypes[0] == WebView::class.java &&
            method.parameterTypes[1] == String::class.java &&
            method.parameterTypes[2] == String::class.java &&
            method.parameterTypes[3] == String::class.java &&
            method.parameterTypes[4] == JSONObject::class.java &&
            isIntType(method.parameterTypes[5]) &&
            isIntType(method.parameterTypes[6]) &&
            method.returnType != Void.TYPE
    }

    private fun isHybridTaskConstructor(constructor: Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 7 &&
            types[0] == String::class.java &&
            types[1] == String::class.java &&
            isIntType(types[2]) &&
            isIntType(types[3]) &&
            types[4] == Long::class.javaPrimitiveType &&
            HashMap::class.java.isAssignableFrom(types[5]) &&
            types[6] == WebView::class.java
    }

    private fun isIntType(type: Class<*>): Boolean {
        return type == Integer.TYPE || type == Integer::class.java
    }

    private fun describeMethod(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        return "${method.name}($params):${method.returnType.name.substringAfterLast('.')}"
    }

    private fun describeConstructor(constructor: Constructor<*>): String {
        val params = constructor.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        return "${constructor.declaringClass.name.substringAfterLast('.')}($params)"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
