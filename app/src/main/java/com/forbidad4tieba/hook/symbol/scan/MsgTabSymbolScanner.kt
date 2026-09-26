package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.MsgTabScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Only the ViewModel delegate is consumed by the default-notification hook. */
internal object MsgTabSymbolScanner {
    private const val TAG = "MsgTabDefaultNotifyHook"
    private const val VIEW_MODEL = StableTiebaHookPoints.MSG_CENTER_CONTAINER_VIEW_MODEL_CLASS
    private const val PREFERENCES = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val MONITOR = "com.baidu.tieba.immessagecenter.msgtab.obs.NewsRemindMsgMonitor"
    private const val MESSAGE = "com.baidu.tbadk.coreExtra.messageCenter.NewsRemindMessage"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): MsgTabScanSymbols =
        scanSubStep(TAG, logger, MsgTabScanSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { handle ->
                val viewModel = handle.bridge.getClassData(VIEW_MODEL)
                if (viewModel == null) {
                    issue("ViewModel", "class missing: $VIEW_MODEL", logger)
                    return@withDexKitBridge MsgTabScanSymbols()
                }
                val candidates = viewModel.methods.filter { method ->
                    instance(method, "long", "long", "java.lang.String") &&
                        directDelegate(method)?.let(::isTabPolicy) == true
                }
                if (candidates.size != 1) {
                    issue("LocateToTab", "direct tab-policy delegates=" +
                        candidates.joinToString(",") { it.descriptor }.ifEmpty { "-" }, logger)
                    return@withDexKitBridge MsgTabScanSymbols()
                }
                MsgTabScanSymbols(candidates.single().getMethodInstance(cl).name)
            } ?: MsgTabScanSymbols().also { issue("Bridge", "DexKit bridge unavailable", logger) }
        }.also { HookSymbolScanDiagnostics.log(logger, "$TAG $it") }

    /** Restricted direct return shape, including the current Titan dispatch; not general data flow. */
    private fun directDelegate(method: MethodData): MethodData? {
        val ops = method.opNames.map { it.substringBefore('/') }
        val hasDispatch = ops.take(2) == listOf("sget-object", "if-nez")
        var body = if (hasDispatch) ops.drop(2) else ops
        val end = body.indexOf("return-wide")
        if (end < 0) return null
        if (hasDispatch) {
            val guard = method.usingFields.firstOrNull() ?: return null
            if (!guard.usingType.isRead() || guard.field.declaredClassName != VIEW_MODEL ||
                guard.field.typeName != INTERCEPTABLE || !Modifier.isStatic(guard.field.modifiers)) return null
            if (body.drop(end + 1) != listOf("move-object", "const", "invoke-interface", "move-result-object",
                    "if-eqz", "iget-wide", "return-wide")) return null
            val dispatch = method.invokes.singleOrNull { it.declaredClassName == INTERCEPTABLE } ?: return null
            if (dispatch.methodName != "invokeJL" || dispatch.returnTypeName != INTERCEPT_RESULT ||
                dispatch.paramTypeNames != listOf("int", "java.lang.Object", "long", "java.lang.Object")) return null
        } else if (end != body.lastIndex || method.invokes.any { it.declaredClassName == INTERCEPTABLE }) {
            return null
        }
        body = body.take(end + 1)
        var calls = method.invokes.filterNot { it.declaredClassName == INTERCEPTABLE }
        if (body.take(2) == listOf("const-string", "invoke-static")) {
            val nullCheck = calls.firstOrNull() ?: return null
            if (nullCheck.declaredClassName != "kotlin.jvm.internal.Intrinsics" ||
                nullCheck.methodName != "checkNotNullParameter" || nullCheck.returnTypeName != "void" ||
                nullCheck.paramTypeNames != listOf("java.lang.Object", "java.lang.String")) return null
            body = body.drop(2)
            calls = calls.drop(1)
        }
        if (body != listOf("iget-object", "invoke-virtual", "move-result-wide", "return-wide")) return null
        val delegate = calls.singleOrNull() ?: return null
        if (!instance(delegate, "long", "long", "java.lang.String")) return null
        val field = method.usingFields.filterNot {
            Modifier.isStatic(it.field.modifiers) && it.field.typeName == INTERCEPTABLE ||
                it.field.declaredClassName == INTERCEPT_RESULT
        }.singleOrNull() ?: return null
        if (!field.usingType.isRead() || Modifier.isStatic(field.field.modifiers) ||
            field.field.declaredClassName != VIEW_MODEL || field.field.typeName != delegate.declaredClassName) return null
        return delegate
    }

    private fun isTabPolicy(method: MethodData): Boolean {
        if (!method.usingStrings.containsAll(listOf("chat", "notice", "|", "key_has_notification_msg", "key_has_chat_msg"))) return false
        if (!method.calls("android.text.TextUtils", "isEmpty", "boolean", "java.lang.CharSequence") ||
            !method.calls(PREFERENCES, "getInstance", PREFERENCES) ||
            !method.calls(PREFERENCES, "getBoolean", "boolean", "java.lang.String", "boolean") ||
            !method.calls("java.lang.String", "equals", "boolean", "java.lang.Object")) return false
        if (method.invokes.none { it.declaredClassName == MONITOR && instance(it, MESSAGE) }) return false
        val unread = method.invokes.filter { it.declaredClassName == method.declaredClassName && instance(it, "boolean", MESSAGE) }
            .distinctBy { it.descriptor }.singleOrNull() ?: return false
        return listOf("getNotificationCount", "getMsgCount", "getChatCount", "getBroadcastCount").all {
            unread.calls(MESSAGE, it, "int")
        }
    }

    private fun MethodData.calls(owner: String, name: String, returns: String, vararg params: String) = invokes.any {
        it.declaredClassName == owner && it.methodName == name && it.returnTypeName == returns && it.paramTypeNames == params.toList()
    }

    private fun instance(method: MethodData, returns: String, vararg params: String) =
        !Modifier.isStatic(method.modifiers) && !method.isConstructor &&
            method.returnTypeName == returns && method.paramTypeNames == params.toList()

    private fun issue(role: String, detail: String, logger: ScanLogger?) {
        HookSymbolScanSession.get()?.scanErrors?.let {
            HookSymbolScanDiagnostics.recordScanIssue(logger, "$TAG.$role", it, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$TAG.$role $detail")
    }
}
