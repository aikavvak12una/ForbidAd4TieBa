package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.symbol.dexkit.DexKitBridgeProvider
import com.forbidad4tieba.hook.symbol.model.DefaultPopupSymbols
import com.forbidad4tieba.hook.symbol.model.FirstLikePopupTargets
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DefaultPopupSymbolScanner {
    private const val JSON_RESPONSE = "com.baidu.tbadk.message.http.JsonHttpResponsedMessage"
    private const val AGREE_RESPONSE = "com.baidu.tieba.pb.data.PbFloorAgreeResponseMessage"
    private const val TOAST_DATA = "com.baidu.tbadk.core.data.BdToastData"
    private const val TOAST_SERVICE = "com.baidu.tbadk.service.TbConvergedToastService"
    private const val GUIDE_PROVIDER = "com.baidu.tieba.push.guide.DialogParamProvider"
    private const val REPLY_GUIDE = "com.baidu.tieba.pb.pb.main.push.PbReplyPushGuide"
    private const val PAGE_CONTEXT = "com.baidu.tbadk.TbPageContext"
    private const val FUNCTION = "kotlin.jvm.functions.Function0"
    private const val PUSH_OPEN_UTIL = "com.baidu.tbadk.coreExtra.util.PushOpenUtil"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): DefaultPopupSymbols {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
            context.applicationInfo?.splitSourceDirs.orEmpty()
        val opened = DexKitBridgeProvider.openFirstAvailable(paths, logger) ?: return DefaultPopupSymbols()
        return opened.use { source ->
            val firstLike = scanSubStep("FirstLikePopupBlockHook", logger, null as String?) {
                scanFirstLike(source.bridge, cl, logger)
            }
            val notification = scanSubStep("NotificationGuideBlockHook", logger, null as Pair<String, String>?) {
                scanNotification(source.bridge, cl, logger)
            }
            DefaultPopupSymbols(
                firstLikeResponseClass = if (firstLike != null) AGREE_RESPONSE else null,
                firstLikeToastMethod = firstLike,
                notificationGuideClass = notification?.first,
                notificationGuideMethod = notification?.second,
            )
        }
    }

    private fun scanFirstLike(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): String? {
        val decoders = bridge.getClassData(AGREE_RESPONSE)?.methods.orEmpty().filter {
            it.methodName == "decodeLogicInBackGround" &&
                it.paramTypeNames == listOf("int", "org.json.JSONObject") &&
                it.returnTypeName == "void" && !Modifier.isStatic(it.modifiers) &&
                it.usingStrings.containsAll(listOf("agree", "score", "is_first_agree"))
        }
        if (unique("FirstLikePopupBlockHook.Response", decoders, logger) == null) return null
        val candidates = bridge.getClassData(JSON_RESPONSE)?.methods.orEmpty().filter { method ->
            Modifier.isPrivate(method.modifiers) && !Modifier.isStatic(method.modifiers) &&
                method.returnTypeName == "void" && method.paramTypeNames == listOf("java.lang.String") &&
                method.invokes.any {
                    it.declaredClassName == TOAST_DATA && it.methodName == "parserJson" &&
                        it.paramTypeNames == listOf("java.lang.String")
                } && method.invokes.any {
                    it.declaredClassName == TOAST_SERVICE && it.methodName == "showToast" &&
                        it.paramTypeNames == listOf("java.lang.Object")
                } && method.callers.any {
                    it.declaredClassName == JSON_RESPONSE && it.methodName == "parseServerResponsedData" &&
                        it.paramTypeNames == listOf("java.lang.String") && it.returnTypeName == "org.json.JSONObject"
                }
        }
        val target = unique("FirstLikePopupBlockHook.ToastParser", candidates, logger) ?: return null
        check(restoreFirstLike(cl, DefaultPopupSymbols(AGREE_RESPONSE, target.methodName)) != null) {
            "first-like toast parser failed reflection validation"
        }
        return target.methodName
    }

    private fun scanNotification(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): Pair<String, String>? {
        val provider = Class.forName(GUIDE_PROVIDER, false, cl)
        val h5Candidates = bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().addEqString("push_guide_h5_dialog")),
        ).filter { method ->
            !Modifier.isStatic(method.modifiers) && method.returnTypeName == "boolean" &&
                method.paramTypeNames.isEmpty() &&
                provider.isAssignableFrom(Class.forName(method.declaredClassName, false, cl)) &&
                method.invokes.any {
                    it.declaredClassName == "com.baidu.tbadk.dispatcher.OpenWebViewDispatcher" &&
                        it.methodName == "assembleH5DialogSchemaUrl"
                }
        }
        val h5 = unique("NotificationGuideBlockHook.H5", h5Candidates, logger) ?: return null
        // Returning false from only the H5 helper would open the native fallback.
        val candidates = h5.callers.filter { method ->
            method.declaredClassName == h5.declaredClassName && !Modifier.isStatic(method.modifiers) &&
                Modifier.isPublic(method.modifiers) && method.returnTypeName == "boolean" &&
                method.paramTypeNames == listOf(PAGE_CONTEXT, FUNCTION) &&
                method.invokes.any {
                    it.declaredClassName == PUSH_OPEN_UTIL && it.methodName == "showPushPermissionDialogV2" &&
                        it.paramTypeNames.take(2) == listOf(PAGE_CONTEXT, "java.lang.String") &&
                        it.paramTypeNames.size == 3 && it.returnTypeName == "void"
                }
        }.distinctBy { it.descriptor }
        val target = unique("NotificationGuideBlockHook.TryShow", candidates, logger) ?: return null
        val symbols = DefaultPopupSymbols(
            notificationGuideClass = target.declaredClassName,
            notificationGuideMethod = target.methodName,
        )
        check(restoreNotification(cl, symbols) != null) { "notification guide failed reflection validation" }
        return target.declaredClassName to target.methodName
    }

    fun restoreFirstLike(cl: ClassLoader, symbols: DefaultPopupSymbols): FirstLikePopupTargets? {
        val responseName = symbols.firstLikeResponseClass ?: return null
        val methodName = symbols.firstLikeToastMethod ?: return null
        return scanSubStep("FirstLikePopupBlockHook.Restore", null, null) {
            check(responseName == AGREE_RESPONSE) { "unexpected first-like response owner" }
            val parent = Class.forName(JSON_RESPONSE, false, cl)
            val response = Class.forName(responseName, false, cl)
            check(parent.isAssignableFrom(response)) { "first-like response is not a JSON response" }
            val method = parent.getDeclaredMethod(methodName, String::class.java)
            check(Modifier.isPrivate(method.modifiers) && !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE) { "invalid first-like toast parser signature" }
            FirstLikePopupTargets(response, method.apply { isAccessible = true })
        }
    }

    fun restoreNotification(cl: ClassLoader, symbols: DefaultPopupSymbols): Method? {
        val ownerName = symbols.notificationGuideClass ?: return null
        val methodName = symbols.notificationGuideMethod ?: return null
        return scanSubStep("NotificationGuideBlockHook.Restore", null, null) {
            val owner = Class.forName(ownerName, false, cl)
            val provider = Class.forName(GUIDE_PROVIDER, false, cl)
            check(provider.isAssignableFrom(owner) && Modifier.isAbstract(owner.modifiers)) {
                "notification guide owner is not the shared parameter provider"
            }
            // Read parameter Classes from the host method. R8 can rewrite Class.forName(FUNCTION)
            // to the module's obfuscated Kotlin type, which the host classloader cannot load.
            val method = owner.declaredMethods.singleOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.parameterTypes.map { it.name } == listOf(PAGE_CONTEXT, FUNCTION)
            } ?: error("notification tryShow missing or ambiguous: $ownerName.$methodName")
            check(Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) && method.returnType == Boolean::class.javaPrimitiveType) {
                "invalid notification tryShow signature"
            }
            val params = method.parameterTypes
            check(Class.forName(REPLY_GUIDE, false, cl).getMethod(methodName, *params).declaringClass == owner) {
                "reply guide bypasses the shared tryShow entry"
            }
            method.apply { isAccessible = true }
        }
    }

    fun isCacheValid(cl: ClassLoader, symbols: DefaultPopupSymbols): Boolean {
        val hasFirstLike = symbols.firstLikeResponseClass != null || symbols.firstLikeToastMethod != null
        val hasNotification = symbols.notificationGuideClass != null || symbols.notificationGuideMethod != null
        return (!hasFirstLike || restoreFirstLike(cl, symbols) != null) &&
            (!hasNotification || restoreNotification(cl, symbols) != null)
    }

    private fun unique(tag: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate(tag, candidates, logger) { it.descriptor }
}
