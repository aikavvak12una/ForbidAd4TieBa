package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Forum roles whose signatures alone cannot distinguish their business purpose. */
internal object ForumPageAdSemanticScanner {
    private const val TAG = "ForumPageAdBlockHook"
    private const val BOOK_SCENE = "com.baidu.tieba.game.IBookButtonService\$BookScene"
    private const val BOOK_SUB_SCENE = "com.baidu.tieba.game.IBookButtonService\$BookSubScene"
    private const val CALLABLE_REFERENCE = "kotlin.jvm.internal.CallableReference"
    private const val FUNCTION_REFERENCE = "kotlin.jvm.internal.FunctionReferenceImpl"
    private const val INTRINSICS = "kotlin.jvm.internal.Intrinsics"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"
    private const val JSON = "org.json.JSONObject"
    private val GAME_FIELDS = setOf("title", "desc", "sub_desc", "button", "schema", "action_type", "log_param")
    private val CALLBACK_OPS = setOf(
        "sget-object", "iget-object", "const", "const-string", "move-object", "move-result-object",
        "check-cast", "invoke-static", "invoke-interface", "if-eqz", "if-nez", "return-void", "nop",
    )

    fun bottomGameBar(
        bridge: DexKitBridge,
        mapperClass: String,
        dataResClass: String,
        logger: ScanLogger?,
    ): MethodData? {
        val dataRes = bridge.getClassData(dataResClass) ?: return missing("bottomGameBar", logger, "DataRes missing")
        val gameField = dataRes.fields.singleOrNull { it.fieldName == "bottom_game_bar" && !Modifier.isStatic(it.modifiers) }
            ?: return missing("bottomGameBar", logger, "DataRes.bottom_game_bar missing or ambiguous")
        val mapper = bridge.getClassData(mapperClass) ?: return missing("bottomGameBar", logger, "mapper missing")
        val matches = mapper.methods.filter { method ->
            Modifier.isStatic(method.modifiers) && method.paramTypeNames == listOf(dataResClass) &&
                method.returnTypeName != "void" && method.usingFields.any {
                    it.usingType.isRead() && it.field.descriptor == gameField.descriptor
                } && GAME_FIELDS.all { name ->
                    method.reads(gameField.typeName, name)
                } && method.reads(BOOK_SCENE, "BOOK_SCENE_FRS") &&
                method.reads(BOOK_SUB_SCENE, "BOOK_SUB_SCENE_FRS_BOTTOM_BAR") &&
                method.invokes.any { it.declaredClassName == method.returnTypeName && it.methodName == "<init>" } &&
                listOf(BOOK_SCENE, BOOK_SUB_SCENE).all { scene ->
                    method.invokes.any {
                        it.declaredClassName == method.returnTypeName && instanceMethod(it, "void", scene)
                    }
                }
        }
        return unique("bottomGameBar", matches, logger)
    }

    fun businessPromotJump(
        bridge: DexKitBridge,
        bizClass: String,
        businessPromotClass: String,
        logger: ScanLogger?,
    ): MethodData? {
        val biz = bridge.getClassData(bizClass) ?: return missing("businessPromotBiz", logger, "business class missing")
        val registration = unique("businessPromotBiz.registration", biz.methods.filter {
            instanceMethod(it, "com.baidu.tieba.forum.hybrid.manager.WrapListener[]") &&
                "frsPage.businessPromotJump" in it.usingStrings && it.invokes.any { call ->
                    call.declaredClassName == "kotlin.jvm.functions.Function2" && call.methodName == "invoke" &&
                        call.paramTypeNames == listOf("java.lang.Object", "java.lang.Object")
                }
        }, logger) ?: return null
        val constructors = registration.invokes.filter { call ->
            call.methodName == "<init>" && bridge.getClassData(call.declaredClassName)?.superClass?.name == FUNCTION_REFERENCE &&
                "businessPromotJump" in call.usingStrings
        }.distinctBy { it.descriptor }
        val constructor = unique("businessPromotBiz.callback", constructors, logger) ?: return null
        val callbackClass = bridge.getClassData(constructor.declaredClassName)
            ?: return missing("businessPromotBiz", logger, "callback class missing")
        val callback = unique("businessPromotBiz.invoke", callbackClass.methods.filter {
            it.methodName == "invoke" && instanceMethod(it, "void", "java.lang.String") &&
                it.reads(CALLABLE_REFERENCE, "receiver")
        }, logger) ?: return null
        val forwarder = unique("businessPromotBiz.forwarder", callback.invokes.filter {
            it.declaredClassName == bizClass && Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf(bizClass, "java.lang.String")
        }.distinctBy { it.descriptor }, logger) ?: return null
        // The callback only unwraps CallableReference.receiver, checks null, and forwards its String.
        // This deliberately accepts a bounded compiler shape, not arbitrary register data flow.
        if (callback.opNames.any { it.substringBefore('/') !in CALLBACK_OPS } ||
            callback.usingStrings.any { it != "p0" } || callback.usingFields.any {
                it.usingType.isWrite() || !(it.field.declaredClassName == CALLABLE_REFERENCE && it.field.fieldName == "receiver" ||
                    it.field.typeName == INTERCEPTABLE || it.field.declaredClassName == INTERCEPT_RESULT)
            } || callback.invokes.any {
                it.descriptor != forwarder.descriptor && !isTitanDispatch(it) &&
                    !(it.declaredClassName == INTRINSICS && it.methodName == "checkNotNullParameter")
            }) return missing("businessPromotBiz", logger, "callback is not a direct reference forwarder")
        val jump = forwarder.invokes.singleOrNull()
            ?: return missing("businessPromotBiz", logger, "forwarder does not call exactly one target")
        if (forwarder.opNames.map { it.substringBefore('/') } != listOf("invoke-virtual", "return-void") ||
            jump.declaredClassName != bizClass || !instanceMethod(jump, "void", "java.lang.String")) {
            return missing("businessPromotBiz", logger, "forwarder shape mismatch")
        }
        if (jump.invokes.none { it.declaredClassName == JSON && it.methodName == "<init>" && it.paramTypeNames == listOf("java.lang.String") } ||
            jump.invokes.none { Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(JSON) && it.returnTypeName == businessPromotClass }) {
            return missing("businessPromotBiz", logger, "jump does not parse the BusinessPromot protocol")
        }
        return jump
    }

    private fun MethodData.reads(owner: String, name: String): Boolean = usingFields.any {
        it.usingType.isRead() && it.field.declaredClassName == owner && it.field.fieldName == name
    }

    private fun instanceMethod(method: MethodData, result: String, vararg params: String): Boolean =
        !Modifier.isStatic(method.modifiers) && method.returnTypeName == result && method.paramTypeNames == params.toList()

    private fun isTitanDispatch(method: MethodData): Boolean =
        method.declaredClassName == INTERCEPTABLE && method.returnTypeName == INTERCEPT_RESULT && method.methodName.startsWith("invoke")

    private fun unique(role: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        candidates.singleOrNull() ?: missing(role, logger, "candidates=" + candidates.joinToString { it.descriptor }.ifEmpty { "-" })

    private fun missing(role: String, logger: ScanLogger?, detail: String): Nothing? {
        HookSymbolScanDiagnostics.recordScanIssue(logger, "$TAG.$role", HookSymbolScanSession.get()?.scanErrors ?: mutableListOf(), detail)
        return null
    }
}
