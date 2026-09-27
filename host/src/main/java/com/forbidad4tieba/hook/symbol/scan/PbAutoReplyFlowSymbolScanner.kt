package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.symbol.model.PbAutoReplyFlowSymbols
import com.forbidad4tieba.hook.symbol.model.PbAutoReplyFlowTargets
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Follow send -> write model and success -> transient post; never match obfuscated sample names. */
internal object PbAutoReplyFlowSymbolScanner {
    private const val TAG = "PbLikeAutoReplyHook.Flow"
    private const val WRITE_DATA = "com.baidu.tbadk.coreExtra.data.WriteData"
    private const val WRITE_MODEL = "com.baidu.tieba.tbadkcore.writeModel.NewWriteModel"
    private const val CALLBACK_DATA = "com.baidu.tieba.tbadkcore.writeModel.PostWriteCallBackData"
    private const val ANTI_DATA = "com.baidu.tbadk.core.data.AntiData"
    private const val FRAGMENT = "com.baidu.tieba.pb.pagebrowser.ui.PageBrowserFragment"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): PbAutoReplyFlowSymbols =
        scanSubStep(TAG, logger, PbAutoReplyFlowSymbols()) {
            val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
                context.applicationInfo?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { scan(it.bridge, cl, logger) }
                ?: PbAutoReplyFlowSymbols()
        }

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): PbAutoReplyFlowSymbols {
        fun anchored(text: String): List<MethodData> = bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().addEqString(text)),
        ).toList()
        val send = method("Send", anchored("reportSendClickEmotion fail: ").filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("java.lang.String", WRITE_DATA) &&
                it.invokes.any { call -> call.declaredClassName == WRITE_MODEL && call.methodName == "setWriteData" }
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val modelField = field("WriteModel", send.usingFields.map { it.field }, send.declaredClassName, WRITE_MODEL, logger)
            ?: return PbAutoReplyFlowSymbols()
        val setter = method("WriteDataSetter", bridge.getClassData(WRITE_MODEL)?.methods.orEmpty().filter {
            it.methodName == "setWriteData" && it.paramTypeNames == listOf(WRITE_DATA) && it.returnTypeName == "void"
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val dataField = field("WriteData", setter.usingFields.map { it.field }, WRITE_MODEL, WRITE_DATA, logger)
            ?: return PbAutoReplyFlowSymbols()
        val upload = method("Upload", send.invokes.filter {
            it.declaredClassName == WRITE_MODEL && it.paramTypeNames.isEmpty() &&
                it.returnTypeName == "boolean" && "original_img_up_tip" in it.usingStrings &&
                it.usingFields.any { use -> use.field.descriptor == dataField.descriptor }
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val success = method("SuccessHandler", anchored("data!!.postId").filter {
            it.paramTypeNames == listOf(CALLBACK_DATA) && it.returnTypeName == "void" &&
                "PbPostManager" in it.usingStrings
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val callback = method("SuccessCallback", success.callers.filter {
            it.methodName == "callback" && it.returnTypeName == "void" && callbackParams(it.paramTypeNames)
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val params = method("TransientPostParams", anchored("TransientPostParams(postId=").filter {
            it.methodName == "toString" && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.lang.String"
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val transient = method("TransientPost", bridge.getClassData(FRAGMENT)?.methods.orEmpty().filter {
            !Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(params.declaredClassName) &&
                it.returnTypeName == "void" && "transientPostParams" in it.usingStrings &&
                it.invokes.any { call -> call.methodName == "post" && call.paramTypeNames == listOf("java.lang.Runnable") }
        }, logger) ?: return PbAutoReplyFlowSymbols()
        val symbols = PbAutoReplyFlowSymbols(
            spec(send), modelField.fieldName, dataField.fieldName, upload.methodName,
            spec(callback), transient.methodName, params.declaredClassName,
        )
        check(restore(cl, symbols, logger) != null) { "native reply flow failed reflection validation" }
        return symbols
    }

    private fun method(label: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate("$TAG.$label", candidates.distinctBy { it.descriptor }, logger) { it.descriptor }

    private fun field(label: String, candidates: List<FieldData>, owner: String, type: String, logger: ScanLogger?): FieldData? =
        selectUniqueScanCandidate("$TAG.$label", candidates.filter {
            it.declaredClassName == owner && it.typeName == type && !Modifier.isStatic(it.modifiers)
        }.distinctBy { it.descriptor }, logger) { it.descriptor }

    private fun spec(method: MethodData): String = "${method.declaredClassName}#${method.methodName}"

    private fun callbackParams(params: List<String>): Boolean = params.size == 5 &&
        params[0] == "boolean" && params[1] == CALLBACK_DATA && params[3] == WRITE_DATA && params[4] == ANTI_DATA

    fun restore(cl: ClassLoader, symbols: PbAutoReplyFlowSymbols, logger: ScanLogger? = null): PbAutoReplyFlowTargets? {
        if (symbols.missing().isNotEmpty()) return null
        return scanSubStep("$TAG.Restore", logger, null as PbAutoReplyFlowTargets?) {
            val data = Class.forName(WRITE_DATA, false, cl)
            val model = Class.forName(WRITE_MODEL, false, cl)
            fun owner(spec: String): Pair<Class<*>, String> {
                val parts = spec.split('#')
                check(parts.size == 2 && parts.all { it.isNotBlank() }) { "invalid reply method spec: $spec" }
                return Class.forName(parts[0], false, cl) to parts[1]
            }
            val (sendOwner, sendName) = owner(requireNotNull(symbols.sendMethodSpec))
            val send = sendOwner.getDeclaredMethod(sendName, String::class.java, data).checked(Void.TYPE)
            val writeModel = sendOwner.getDeclaredField(requireNotNull(symbols.writeModelField)).checked(model)
            val writeData = model.getDeclaredField(requireNotNull(symbols.writeDataField)).checked(data)
            val upload = model.getDeclaredMethod(requireNotNull(symbols.uploadMethod)).checked(Boolean::class.javaPrimitiveType!!)
            val (callbackOwner, callbackName) = owner(requireNotNull(symbols.callbackMethodSpec))
            check(callbackName == "callback") { "unexpected native reply callback" }
            val callback = callbackOwner.declaredMethods.single { it.name == callbackName && callbackParams(it.parameterTypes.map { type -> type.name }) }
                .checked(Void.TYPE)
            check(!callback.parameterTypes[2].isPrimitive) { "invalid native captcha parameter" }
            check(callbackOwner.interfaces.any { api -> api.methods.any { it.name == callbackName && it.parameterTypes.contentEquals(callback.parameterTypes) } }) {
                "reply callback does not implement the native callback contract"
            }
            val transient = Class.forName(FRAGMENT, false, cl).getDeclaredMethod(
                requireNotNull(symbols.transientMethod), Class.forName(requireNotNull(symbols.transientParamsClass), false, cl),
            ).checked(Void.TYPE)
            // WeakHashMap must identify this request by object identity, never by content equality.
            check(data.getMethod("equals", Any::class.java).declaringClass == Any::class.java &&
                data.getMethod("hashCode").declaringClass == Any::class.java) { "WriteData overrides identity equality" }
            PbAutoReplyFlowTargets(
                send, writeModel, writeData, upload, callback, transient,
                data.getMethod("getType").checked(Int::class.javaPrimitiveType!!),
                data.getMethod("getFloor").checked(String::class.java),
                data.getMethod("getReSubPostId").checked(String::class.java),
                model.getDeclaredMethod("setWriteData", data).checked(Void.TYPE),
                callback.parameterTypes[1].getMethod("getErrorCode").checked(Int::class.javaPrimitiveType!!),
            )
        }
    }

    private fun Method.checked(result: Class<*>): Method = apply {
        check(Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) &&
            !Modifier.isAbstract(modifiers) && returnType == result) { "invalid native reply method: $this" }
        isAccessible = true
    }

    private fun Field.checked(expected: Class<*>): Field = apply {
        check(!Modifier.isStatic(modifiers) && type == expected) { "invalid native reply field: $this" }
        isAccessible = true
    }
}
