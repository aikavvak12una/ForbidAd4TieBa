package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.dexkit.DexKitBridgeProvider
import com.forbidad4tieba.hook.symbol.model.PbPreloadSymbols
import com.forbidad4tieba.hook.symbol.model.PbPreloadTargets
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolve the provider consumed by the current PB request manager and the native clicked-card cache. */
internal object PbForcePreloadSymbolScanner {
    private const val DATA_RES = "tbclient.PbPage.DataRes"
    private const val FLOOR_MODEL = "com.baidu.tieba.pb.pb.main.model.PbFloorModel"
    private const val CARD_UTILS = "com.baidu.tbadk.core.util.ThreadCardUtils"
    private const val THREAD_DATA = StableTiebaHookPoints.THREAD_DATA_CLASS

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): PbPreloadSymbols =
        scanSubStep("PbForcePreloadHook", logger, PbPreloadSymbols()) {
            val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
                context.applicationInfo?.splitSourceDirs.orEmpty()
            val cached = HookSymbolScanSession.get()?.dexKitBridge(paths, logger)
            val opened = cached ?: DexKitBridgeProvider.openFirstAvailable(paths, logger)
                ?: return@scanSubStep PbPreloadSymbols()
            if (cached != null) scan(opened.bridge, cl, logger)
            else opened.use { scan(it.bridge, cl, logger) }
        }

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): PbPreloadSymbols {
        val publisher = unique("ResultPublisher", bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().addEqString("PbRequestModelManager notifyNewResult mState:")),
        ).filter {
            !Modifier.isStatic(it.modifiers) && it.paramTypeNames.isEmpty() && it.returnTypeName == "void"
        }, logger) ?: return PbPreloadSymbols()
        val request = unique("InitialRequest", bridge.getClassData(publisher.declaredClassName)?.methods.orEmpty().filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" &&
                it.paramTypeNames.size == 2 && it.paramTypeNames.first() == "android.content.Intent" &&
                "cdnCallback" in it.usingStrings && it.invokes.any { call ->
                    call.declaredClassName == FLOOR_MODEL && call.methodName == "<init>"
                } && it.invokes.any { call -> call.descriptor == publisher.descriptor }
        }, logger) ?: return PbPreloadSymbols()
        val dispatcherCompanion = Class.forName("com.baidu.tieba.pb.preload.PbPreloadDispatcher", false, cl)
            .getField("Companion").type.name
        val provider = unique("Provider", request.invokes.filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == DATA_RES &&
                it.paramTypeNames == listOf("java.lang.String") && "tid" in it.usingStrings &&
                it.invokes.any { call -> call.declaredClassName == dispatcherCompanion &&
                    call.paramTypeNames.isEmpty() && call.returnTypeName == "java.util.Map" }
        }, logger) ?: return PbPreloadSymbols()
        val eligibility = unique("CardEligibility", bridge.getClassData(CARD_UTILS)?.methods.orEmpty()
            .filter { it.methodName == "isPreloadType" && it.paramTypeNames == listOf(THREAD_DATA) }
            .flatMap { it.callers }.filter {
                Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" && it.paramTypeNames.isEmpty() &&
                    it.invokes.any { call ->
                        call.declaredClassName == StableTiebaHookPoints.TB_SINGLETON_CLASS &&
                            call.methodName == "isPbPreloadSwitchOn"
                    }
            }, logger) ?: return PbPreloadSymbols()
        val card = unique("CardGetter", eligibility.invokes.filter {
            it.declaredClassName == eligibility.declaredClassName && Modifier.isStatic(it.modifiers) &&
                it.paramTypeNames.isEmpty() && it.returnTypeName == THREAD_DATA
        }, logger) ?: return PbPreloadSymbols()
        val pageState = PbPreloadPageStateResolver.scan(bridge, logger) ?: return PbPreloadSymbols()
        val symbols = PbPreloadSymbols(spec(provider), spec(card), pageState.first, pageState.second)
        check(restore(cl, symbols.providerMethodSpec, symbols.cardGetterMethodSpec, symbols.pageStateMutableField, symbols.pageStateFlowField) != null) {
            "preload provider/card protocol failed reflection validation"
        }
        return symbols
    }

    private fun unique(tag: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate("PbForcePreloadHook.$tag", candidates.distinctBy { it.descriptor }, logger) { it.descriptor }

    private fun spec(method: MethodData): String = "${method.declaredClassName}#${method.methodName}"

    fun restore(cl: ClassLoader, providerSpec: String?, cardGetterSpec: String?, mutableField: String?, flowField: String?): PbPreloadTargets? {
        if (providerSpec.isNullOrBlank() || cardGetterSpec.isNullOrBlank() || mutableField.isNullOrBlank() || flowField.isNullOrBlank()) return null
        return scanSubStep("PbForcePreloadHook.Restore", null, null as PbPreloadTargets?) {
            val singleton = Class.forName(StableTiebaHookPoints.TB_SINGLETON_CLASS, false, cl)
            val switch = singleton.getDeclaredMethod("isPbPreloadSwitchOn")
            validate(switch, Boolean::class.javaPrimitiveType!!, false)
            PbPreloadTargets(
                switch,
                restoreSpec(cl, providerSpec, DATA_RES, false, String::class.java),
                restoreSpec(cl, cardGetterSpec, THREAD_DATA, true),
                PbPreloadProtocolResolver.resolve(cl),
                PbPreloadPageStateResolver.restore(cl, mutableField, flowField),
            )
        }
    }

    fun isCacheValid(cl: ClassLoader, providerSpec: String?, cardGetterSpec: String?, mutableField: String?, flowField: String?): Boolean =
        (providerSpec == null && cardGetterSpec == null && mutableField == null && flowField == null) ||
            restore(cl, providerSpec, cardGetterSpec, mutableField, flowField) != null

    private fun restoreSpec(cl: ClassLoader, spec: String, result: String, static: Boolean, vararg params: Class<*>): Method {
        val parts = spec.split('#')
        check(parts.size == 2 && parts.all { it.isNotBlank() }) { "invalid preload method spec" }
        val method = Class.forName(parts[0], false, cl).getDeclaredMethod(parts[1], *params)
        validate(method, Class.forName(result, false, cl), static)
        return method
    }

    private fun validate(method: Method, result: Class<*>, static: Boolean) {
        check(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers) == static &&
            !Modifier.isAbstract(method.modifiers) && method.returnType == result) { "invalid preload signature: $method" }
        method.isAccessible = true
    }
}
