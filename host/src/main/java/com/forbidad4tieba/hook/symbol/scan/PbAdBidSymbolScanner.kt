package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.contracts.Diagnostics
import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PbAdBidScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** AdBid encoders identify the request type; starts must consume that same lazy request. */
internal object PbAdBidSymbolScanner {
    private const val TAG = "PbAdRequestBlockHook.AdBid"
    private const val COMMON = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
    private const val BROWSER = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
    private const val ENDPOINT = "c/b/ad/adBid?cmd=309757&format=protobuf"
    private const val DATA = "tbclient.AdBid.DataReq"
    private const val REQUEST = "tbclient.AdBid.AdBidReqIdl"
    private const val CONTINUATION = "kotlin.coroutines.Continuation"
    private const val CANCELLABLE = "kotlinx.coroutines.CancellableContinuationImpl"
    private const val FUNCTION = "kotlin.jvm.functions.Function0"
    private const val LAZY = "kotlin.Lazy"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    private data class Model(val encode: MethodData, val contract: MethodData)

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): PbAdBidScanSymbols =
        scanSubStep(TAG, logger, PbAdBidScanSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { scan(it.bridge, cl, logger) }
                ?: PbAdBidScanSymbols().also { issue("Bridge", "DexKit bridge unavailable", logger) }
        }

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): PbAdBidScanSymbols {
        val endpoints = bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba")
            .matcher(MethodMatcher.create().addEqString(ENDPOINT))).toList()
        val common = role("CommonModel", logger) { model(bridge, endpoints, COMMON, cl, logger) }
        val browser = role("PageBrowserModel", logger) { model(bridge, endpoints, BROWSER, cl, logger) }
        val commonGetter = common?.let { role("CommonRequest", logger) { requestGetter(bridge, it, logger) } }
        val browserGetter = browser?.let { role("PageBrowserRequest", logger) { requestGetter(bridge, it, logger) } }
        val notify = role("CommonNotify", logger) {
            unique("CommonNotify", bridge.getClassData(COMMON)?.methods.orEmpty().filter {
                instance(it, "void", "int") &&
                    it.calls("java.util.concurrent.CountDownLatch", "getCount", "long") &&
                    it.calls("java.util.concurrent.CountDownLatch", "countDown", "void") &&
                    it.calls("android.os.Handler", "removeCallbacks", "void", "java.lang.Runnable") &&
                    it.calls("com.baidu.adp.lib.safe.UiUtils", "runOnUiThreadImmediately", "void", "java.lang.Runnable") &&
                    it.usingFields.any { use -> use.usingType.isWrite() && use.field.declaredClassName == COMMON &&
                        use.field.typeName == "int" && !Modifier.isStatic(use.field.modifiers) }
            }, logger)?.getMethodInstance(cl)?.name
        }
        val starts = if (common != null && commonGetter != null) {
            val methods = role("CommonEntries", logger) {
                bridge.getClassData(COMMON)?.methods.orEmpty().filter { instance(it, "void") }
            }.orEmpty()
            listOfNotNull(
                role("CommonAsync", logger) {
                    unique("CommonAsync", methods.filter {
                        it.usingStrings.contains("开始异步请求: model=") && asyncRequest(it, commonGetter, cl)
                    }, logger)?.let { inheritedName(it, common, cl) }
                },
                role("CommonThread", logger) {
                    unique("CommonThread", methods.filter { threadedRequest(bridge, it, commonGetter, cl) }, logger)
                        ?.let { inheritedName(it, common, cl) }
                },
            ).distinct().sorted()
        } else emptyList()
        val browserStart = if (browser != null && browserGetter != null) role("PageBrowserStart", logger) {
            unique("PageBrowserStart", bridge.getClassData(BROWSER)?.methods.orEmpty().filter {
                instance(it, "java.lang.Object", CONTINUATION) && asyncRequest(it, browserGetter, cl) &&
                    it.calls(CANCELLABLE, "<init>", "void", CONTINUATION, "int") &&
                    it.calls(CANCELLABLE, "initCancellability", "void") &&
                    it.calls(CANCELLABLE, "getResult", "java.lang.Object")
            }, logger)?.let { inheritedName(it, browser, cl) }
        } else null
        return PbAdBidScanSymbols(common?.encode?.declaredClassName, starts, notify,
            browser?.encode?.declaredClassName, browserStart)
            .also { HookSymbolScanDiagnostics.log(logger, "$TAG $it") }
    }

    private fun model(
        bridge: DexKitBridge,
        endpoints: List<MethodData>,
        baseName: String,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Model? {
        val contracts = bridge.getClassData(baseName)?.methods.orEmpty()
        val matches = endpoints.mapNotNull { encode ->
            if (!instance(encode, encode.returnTypeName)) return@mapNotNull null
            if (!derivesFrom(bridge, encode.declaredClassName, baseName) || !adBidProtocol(encode)) return@mapNotNull null
            // Once Dex establishes the group, a reflection failure invalidates that group's uniqueness proof.
            val base = Class.forName(baseName, false, cl)
            val type = Class.forName(encode.declaredClassName, false, cl)
            if (type == base || type.isInterface || Modifier.isAbstract(type.modifiers) || !base.isAssignableFrom(type)) return@mapNotNull null
            if (!Class.forName(encode.returnTypeName, false, cl).isInterface) return@mapNotNull null
            val contract = contracts.singleOrNull {
                Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
                    it.methodName == encode.methodName && it.paramCount == 0 && it.returnTypeName == encode.returnTypeName
            } ?: return@mapNotNull null
            encode.getMethodInstance(cl)
            Model(encode, contract)
        }
        val role = if (baseName == COMMON) "CommonModel" else "PageBrowserModel"
        val selected = unique(role, matches.map { it.encode }, logger) ?: return null
        return matches.single { it.encode.descriptor == selected.descriptor }
    }

    private fun derivesFrom(bridge: DexKitBridge, name: String, base: String): Boolean {
        var parent = bridge.getClassData(name)?.superClass
        val visited = HashSet<String>()
        while (parent != null && visited.add(parent.name)) {
            if (parent.name == base) return true
            parent = parent.superClass
        }
        return false
    }

    private fun adBidProtocol(method: MethodData): Boolean {
        if (!method.calls("$DATA\$Builder", "build", DATA, "boolean") ||
            !method.calls("$REQUEST\$Builder", "build", REQUEST, "boolean") ||
            !method.calls("java.util.Map", "put", "java.lang.Object", "java.lang.Object", "java.lang.Object")) return false
        fun writes(owner: String, name: String, type: String) = method.usingFields.any {
            it.usingType.isWrite() && it.field.declaredClassName == owner &&
                it.field.fieldName == name && it.field.typeName == type && !Modifier.isStatic(it.field.modifiers)
        }
        if (!writes("$DATA\$Builder", "call_from", "java.lang.String") ||
            !writes("$REQUEST\$Builder", "data", DATA)) return false
        // The endpoint encoder must return a built request, not merely mention the URL.
        val ops = method.ops()
        val sites = method.callSites() ?: return false
        return method.invokes.withIndex().any { (index, call) ->
            val at = sites[index]
            instance(call, method.returnTypeName) && call.declaredClassName != method.declaredClassName &&
                ops.drop(at).take(3) == listOf("invoke-virtual", "move-result-object", "return-object") &&
                method.invokes.any { it.declaredClassName == call.declaredClassName &&
                    instance(it, call.declaredClassName, "java.lang.String") } &&
                method.invokes.any { it.declaredClassName == call.declaredClassName &&
                    instance(it, call.declaredClassName, "java.util.Map") }
        }
    }

    private fun requestGetter(bridge: DexKitBridge, model: Model, logger: ScanLogger?): MethodData? {
        val base = model.contract.declaredClassName
        val methods = bridge.getClassData(base)?.methods.orEmpty()
        val getters = methods.filter { getter ->
            if (!instance(getter, model.encode.returnTypeName) ||
                directBody(getter) != listOf("iget-object", "invoke-interface", "move-result-object", "check-cast", "return-object") ||
                !getter.calls(LAZY, "getValue", "java.lang.Object")) return@filter false
            val field = getter.ownFields().singleOrNull()?.takeIf { it.typeName == LAZY && it.declaredClassName == base }
                ?: return@filter false
            methods.filter { it.isConstructor }.any { lazyInitializer(bridge, it, field, model.contract) }
        }
        return unique(if (base == COMMON) "CommonRequest" else "PageBrowserRequest", getters, logger)
    }

    /** A constructor -> lazy factory -> field write window, without guessing field order. */
    private fun lazyInitializer(bridge: DexKitBridge, ctor: MethodData, field: FieldData, contract: MethodData): Boolean {
        val ops = ctor.ops()
        val sites = ctor.callSites() ?: return false
        val fieldSites = ops.indices.filter { ops[it].startsWith("iget") || ops[it].startsWith("iput") ||
            ops[it].startsWith("sget") || ops[it].startsWith("sput") }
        val fields = ctor.usingFields
        if (fieldSites.size != fields.size) return false
        return ctor.invokes.withIndex().any { (index, construct) ->
            val at = sites[index]
            if (!construct.isConstructor || construct.paramTypeNames != listOf(contract.declaredClassName) ||
                ops.drop(at).take(4) != listOf("invoke-direct", "invoke-static", "move-result-object", "iput-object")) return@any false
            val factory = ctor.invokes.getOrNull(index + 1) ?: return@any false
            if (factory.declaredClassName != "kotlin.LazyKt__LazyJVMKt" || factory.methodName != "lazy" ||
                factory.returnTypeName != LAZY || factory.paramTypeNames != listOf(FUNCTION)) return@any false
            val write = fields.getOrNull(fieldSites.indexOf(at + 3)) ?: return@any false
            if (!write.usingType.isWrite() || write.field.descriptor != field.descriptor) return@any false
            bridge.getClassData(construct.declaredClassName)?.methods.orEmpty().any { invoke ->
                invoke.methodName == "invoke" && instance(invoke, contract.returnTypeName) &&
                    directBody(invoke) == listOf("iget-object", "invoke-virtual", "move-result-object", "return-object") &&
                    invoke.invokes.filterNot { it.declaredClassName == INTERCEPTABLE }.singleOrNull()?.descriptor == contract.descriptor &&
                    invoke.ownFields().singleOrNull()?.let { it.typeName == contract.declaredClassName &&
                        it.declaredClassName == construct.declaredClassName } == true
            }
        }
    }

    private fun asyncRequest(method: MethodData, getter: MethodData, cl: ClassLoader): Boolean {
        val sites = method.callSites() ?: return false
        val ops = method.ops()
        return method.invokes.withIndex().any { (index, access) ->
            val direct = access.descriptor == getter.descriptor
            val wrapper = Modifier.isStatic(access.modifiers) && access.returnTypeName == getter.returnTypeName &&
                access.paramTypeNames == listOf(getter.declaredClassName) && access.declaredClassName == getter.declaredClassName &&
                directBody(access) == listOf("invoke-virtual", "move-result-object", "return-object") &&
                access.invokes.singleOrNull()?.descriptor == getter.descriptor
            if (!direct && !wrapper) return@any false
            val at = sites[index]
            if (ops.drop(at).take(5) != listOf(if (direct) "invoke-virtual" else "invoke-static",
                    "move-result-object", "new-instance", "invoke-direct", "invoke-interface")) return@any false
            val ctor = method.invokes.getOrNull(index + 1) ?: return@any false
            val send = method.invokes.getOrNull(index + 2) ?: return@any false
            if (!ctor.isConstructor || ctor.paramTypeNames != listOf(getter.declaredClassName) ||
                Modifier.isStatic(send.modifiers) || send.declaredClassName != getter.returnTypeName ||
                send.returnTypeName != "void" || send.paramCount != 1) return@any false
            val callback = Class.forName(send.paramTypeNames.single(), false, cl)
            val implementation = Class.forName(ctor.declaredClassName, false, cl)
            callback.isInterface && callback.isAssignableFrom(implementation)
        }
    }

    private fun threadedRequest(bridge: DexKitBridge, start: MethodData, getter: MethodData, cl: ClassLoader): Boolean {
        val sites = start.callSites() ?: return false
        val ops = start.ops()
        return start.invokes.withIndex().any { (index, ctor) ->
            if (!ctor.isConstructor || ctor.paramTypeNames != listOf(COMMON)) return@any false
            if (ops.drop(sites[index]).take(4) != listOf("invoke-direct", "invoke-direct", "invoke-virtual", "return-void")) return@any false
            val thread = start.invokes.getOrNull(index + 1) ?: return@any false
            val launch = start.invokes.getOrNull(index + 2) ?: return@any false
            if (thread.declaredClassName != "java.lang.Thread" || !thread.isConstructor ||
                thread.paramTypeNames != listOf("java.lang.Runnable") ||
                launch.declaredClassName != "java.lang.Thread" || launch.methodName != "start" ||
                !instance(launch, "void")) return@any false
            val type = Class.forName(ctor.declaredClassName, false, cl)
            if (!Runnable::class.java.isAssignableFrom(type)) return@any false
            bridge.getClassData(type.name)?.methods.orEmpty().any runner@{ run ->
                if (run.methodName != "run" || !instance(run, "void") ||
                    directBody(run) != listOf("iget-object", "invoke-static", "return-void") ||
                    run.ownFields().singleOrNull()?.let { it.declaredClassName == type.name && it.typeName == COMMON } != true) return@runner false
                val worker = run.invokes.filterNot { it.declaredClassName == INTERCEPTABLE }.singleOrNull() ?: return@runner false
                Modifier.isStatic(worker.modifiers) && worker.declaredClassName == COMMON &&
                    worker.returnTypeName == "void" && worker.paramTypeNames == listOf(COMMON) &&
                    worker.usingStrings.contains("开始高优请求，model=") &&
                    worker.calls("android.os.Process", "setThreadPriority", "void", "int") &&
                    worker.invokes.any { it.descriptor == getter.descriptor } &&
                    worker.invokes.any { it.declaredClassName == getter.returnTypeName &&
                        !Modifier.isStatic(it.modifiers) && it.paramCount == 0 && it.returnTypeName != "void" }
            }
        }
    }

    /** Only small direct wrappers use this shape check; it is not general control/data-flow analysis. */
    private fun directBody(method: MethodData): List<String>? {
        val ops = method.ops()
        if (ops.take(2) != listOf("sget-object", "if-nez")) {
            return ops.takeIf { method.invokes.none { call -> call.declaredClassName == INTERCEPTABLE } }
        }
        val end = ops.indexOfFirst { it == "return-object" || it == "return-void" }
        if (end < 2) return null
        val guard = method.usingFields.firstOrNull() ?: return null
        if (!guard.usingType.isRead() || !Modifier.isStatic(guard.field.modifiers) ||
            guard.field.declaredClassName != method.declaredClassName || guard.field.typeName != INTERCEPTABLE) return null
        val dispatch = method.invokes.singleOrNull { it.declaredClassName == INTERCEPTABLE } ?: return null
        if (dispatch.methodName != "invokeV" || dispatch.returnTypeName != INTERCEPT_RESULT ||
            dispatch.paramTypeNames != listOf("int", "java.lang.Object")) return null
        val prefix = listOf("move-object", "const", "invoke-interface", "move-result-object", "if-eqz")
        val tail = ops.drop(end + 1)
        val result = if (ops[end] == "return-void") listOf("return-void")
            else listOf("iget-object", "check-cast", "return-object")
        if (tail != prefix + result) return null
        return ops.subList(2, end + 1)
    }

    private fun inheritedName(method: MethodData, model: Model, cl: ClassLoader): String {
        val restored = method.getMethodInstance(cl)
        val target = Class.forName(model.encode.declaredClassName, false, cl)
        check(target.getMethod(restored.name, *restored.parameterTypes) == restored) { "request entry overridden in " + target.name }
        return restored.name
    }

    private fun MethodData.ownFields(): List<FieldData> = usingFields.filterNot {
        Modifier.isStatic(it.field.modifiers) && it.field.typeName == INTERCEPTABLE ||
            it.field.declaredClassName == INTERCEPT_RESULT
    }.map { it.field }.filterNot { Modifier.isStatic(it.modifiers) }

    private fun MethodData.ops() = opNames.map { it.substringBefore('/') }
    private fun MethodData.callSites(): List<Int>? {
        val ops = ops()
        return ops.indices.filter { ops[it].startsWith("invoke-") }.takeIf { it.size == invokes.size }
    }
    private fun MethodData.calls(owner: String, name: String, returns: String, vararg params: String) = invokes.any {
        it.declaredClassName == owner && it.methodName == name && it.returnTypeName == returns && it.paramTypeNames == params.toList()
    }
    private fun instance(method: MethodData, returns: String, vararg params: String) =
        !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) && !method.isConstructor &&
            method.returnTypeName == returns && method.paramTypeNames == params.toList()

    private fun unique(role: String, methods: List<MethodData>, logger: ScanLogger?): MethodData? {
        val candidates = methods.distinctBy { it.descriptor }
        if (candidates.size == 1) return candidates.single()
        issue(role, "semantic candidates=" + candidates.joinToString(",") { it.descriptor }.ifEmpty { "-" }, logger)
        return null
    }
    private inline fun <T> role(name: String, logger: ScanLogger?, block: () -> T?): T? =
        scanSubStep("$TAG.$name", logger, null, block)
    private fun issue(role: String, detail: String, logger: ScanLogger?) {
        HookSymbolScanSession.get()?.scanErrors?.let {
            HookSymbolScanDiagnostics.recordScanIssue(logger, "$TAG.$role", it, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$TAG.$role $detail")
    }
}
