package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.PbPreloadBodySymbols
import com.forbidad4tieba.hook.symbol.model.PbPreloadBodyTargets
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolve the accepted page result and its native/hybrid body decision as one cooperating group. */
internal object PbPreloadBodyResolver {
    private const val REPOSITORY = "com.baidu.tieba.pb.pagebrowser.repository.PageBrowserRepository"
    private const val HEADER = "com.baidu.tieba.pb.legacy.header.LegacyHeaderBusinessKt"
    private const val DATA = "tbclient.PbPage.DataRes"
    private const val RESPONSE = "tbclient.PbPage.PbPageResIdl"
    private const val STAGE = "com.baidu.tieba.tbadkcore.data.PbDataType"
    private const val WEB = "tbclient.WebviewData"
    private const val THREAD = StableTiebaHookPoints.THREAD_DATA_CLASS

    fun scan(bridge: DexKitBridge, logger: ScanLogger?): PbPreloadBodySymbols? {
        val result = unique("Result", bridge.findMethod(FindMethod.create().searchPackages("com.baidu")
            .matcher(MethodMatcher.create().addEqString("PageBrowserData(success="))).filter {
            it.methodName == "toString" && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.lang.String"
        }, logger) ?: return null
        val parse = unique("BodyParser", bridge.findMethod(FindMethod.create().searchPackages("com.baidu")
            .matcher(MethodMatcher.create().returnType("void").paramTypes(DATA))).filter {
            !Modifier.isStatic(it.modifiers) && it.usingFields.any { use ->
                use.field.declaredClassName == DATA && use.field.fieldName == "webview_data"
            } && it.invokes.any { call -> call.declaredClassName == THREAD &&
                call.methodName == "parserProtobuf" && call.paramTypeNames == listOf("tbclient.ThreadInfo") }
        }, logger) ?: return null
        val modelType = parse.declaredClassName
        val commit = unique("PageCommit", bridge.getClassData(REPOSITORY)?.methods.orEmpty().filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf(result.declaredClassName) && it.invokes.any { call ->
                    call.declaredClassName == result.declaredClassName && call.returnTypeName == modelType
                } && it.usingFields.any { use -> use.field.declaredClassName == REPOSITORY && use.field.typeName == modelType }
        }, logger) ?: return null
        val hybrid = unique("HybridBody", bridge.getClassData(HEADER)?.methods.orEmpty().flatMap { it.invokes }.filter {
            Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" && it.paramTypeNames == listOf(modelType) &&
                it.invokes.any { call -> call.declaredClassName == modelType && call.returnTypeName == "tbclient.ForumBotInfo" } &&
                it.invokes.any { call -> call.declaredClassName == modelType && call.returnTypeName == THREAD }
        }, logger) ?: return null
        val resultFields = bridge.getClassData(result.declaredClassName)?.fields.orEmpty()
        val parsedFields = parse.usingFields.map { it.field }.filter { it.declaredClassName == modelType }
        return PbPreloadBodySymbols(
            commit = spec(commit),
            hybrid = spec(hybrid),
            success = field("ResultSuccess", resultFields, "boolean", true, logger) ?: return null,
            model = field("ResultModel", resultFields, modelType, true, logger) ?: return null,
            stage = field("ResultStage", resultFields, STAGE, true, logger) ?: return null,
            response = field("ResultResponse", resultFields, RESPONSE, true, logger) ?: return null,
            thread = field("BodyThread", parsedFields, THREAD, false, logger) ?: return null,
            webView = field("BodyWebView", parsedFields, WEB, false, logger) ?: return null,
        )
    }

    private fun unique(label: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate("PbForcePreloadHook.$label", candidates.distinctBy { it.descriptor }, logger) { it.descriptor }

    private fun spec(method: MethodData): String = "${method.declaredClassName}#${method.methodName}"

    private fun field(label: String, fields: List<FieldData>, type: String, final: Boolean, logger: ScanLogger?): String? =
        selectUniqueScanCandidate("PbForcePreloadHook.$label", fields.filter {
            !Modifier.isStatic(it.modifiers) && it.typeName == type && Modifier.isFinal(it.modifiers) == final
        }.distinctBy { it.descriptor }, logger) { it.descriptor }?.let { "${it.declaredClassName}#${it.fieldName}" }

    fun restore(cl: ClassLoader, symbols: PbPreloadBodySymbols): PbPreloadBodyTargets {
        fun type(name: String) = Class.forName(name, false, cl)
        val success = restoreField(cl, symbols.success, Boolean::class.javaPrimitiveType!!, true)
        val model = restoreField(cl, symbols.model, null, true)
        val stage = restoreField(cl, symbols.stage, type(STAGE), true)
        val response = restoreField(cl, symbols.response, type(RESPONSE), true)
        val thread = restoreField(cl, symbols.thread, type(THREAD), false)
        val webView = restoreField(cl, symbols.webView, type(WEB), false)
        check(listOf(model, stage, response).all { it.declaringClass == success.declaringClass }) { "mixed page result owners" }
        check(thread.declaringClass == model.type && webView.declaringClass == model.type) { "mixed page body owners" }
        val commit = restoreMethod(cl, symbols.commit, Void.TYPE, false, success.declaringClass)
        check(commit.declaringClass.name == REPOSITORY) { "unexpected page result owner" }
        val hybrid = restoreMethod(cl, symbols.hybrid, Boolean::class.javaPrimitiveType!!, true, model.type)
        val data = type(DATA)
        val post = type("tbclient.Post")
        check(stage.type.isEnum) { "page stage is not an enum" }
        return PbPreloadBodyTargets(
            commit, hybrid, success, model, stage, response, thread, webView,
            stage.type.getField("PB_DATA_TYPE_PRELOAD").get(null)!!,
            protocolField(response.type, "data", data),
            protocolField(data, "thread", type("tbclient.ThreadInfo")),
            protocolField(data, "first_floor_post", post),
            protocolField(post, "id", Long::class.javaObjectType),
            protocolField(post, "floor", Int::class.javaObjectType),
            protocolField(post, "content", List::class.java),
            protocolField(data, "forum_bot_info", type("tbclient.ForumBotInfo")),
            protocolField(data, "shop_goods_info", type("tbclient.ForumShopGoodsInfo")),
        )
    }

    private fun parts(spec: String?): List<String> = requireNotNull(spec).split('#').also {
        check(it.size == 2 && it.all(String::isNotBlank)) { "invalid preload body descriptor" }
    }

    private fun restoreField(cl: ClassLoader, spec: String?, type: Class<*>?, final: Boolean): Field {
        val (owner, name) = parts(spec)
        return Class.forName(owner, false, cl).getDeclaredField(name).apply {
            check(!Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) == final &&
                (type == null || this.type == type)) { "invalid preload body field: $this" }
            isAccessible = true
        }
    }

    private fun restoreMethod(cl: ClassLoader, spec: String?, result: Class<*>, static: Boolean, vararg params: Class<*>): Method {
        val (owner, name) = parts(spec)
        return Class.forName(owner, false, cl).getDeclaredMethod(name, *params).apply {
            check(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) == static &&
                !Modifier.isAbstract(modifiers) && returnType == result) { "invalid preload body method: $this" }
            isAccessible = true
        }
    }

    private fun protocolField(owner: Class<*>, name: String, type: Class<*>): Field = owner.getField(name).apply {
        check(!Modifier.isStatic(modifiers) && this.type == type) { "invalid preload body protocol: $this" }
    }
}
