package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.contract.AiImageCreationCodec
import com.forbidad4tieba.hook.symbol.model.AiImageCreationScanSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageField
import com.forbidad4tieba.hook.symbol.model.AiImageMethod
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

/** Dedicated controls plus the two nullable/refusal routing boundaries; shared media work is untouched. */
internal object AiImageCreationSymbolScanner {
    private const val TAG = "AiImageCreation"
    private const val CONTEXT = "android.content.Context"
    private const val STRING = "java.lang.String"
    private const val JSON = "org.json.JSONObject"
    private const val BROWSER = "com.baidu.tbadk.browser.BrowserHelper"
    private const val PB_BINDING = "com.baidu.tieba.pb.core.databinding.PbAiEmojiCreationViewBinding"
    private const val AI_ROUTE = "hybrid-main-chatgroup/ai-create-pic"

    fun scan(cl: ClassLoader, logger: ScanLogger?): AiImageCreationScanSymbols =
        ScanDexQueries.query(TAG, logger) { bridge ->
            fun methods(owner: String): List<MethodData> = bridge.getClassData(owner)?.methods?.toList().orEmpty()
            fun one(label: String, values: List<MethodData>): MethodData = requireNotNull(
                selectUniqueScanCandidate("$TAG.$label", values.distinctBy { it.descriptor }, logger) { it.descriptor },
            ) { "Missing or ambiguous $label" }
            fun oneField(label: String, values: List<FieldData>): FieldData = requireNotNull(
                selectUniqueScanCandidate("$TAG.$label", values.distinctBy { it.descriptor }, logger) { it.descriptor },
            ) { "Missing or ambiguous $label" }
            val resolvedMethods = linkedMapOf<AiImageMethod, Method>()
            val resolvedFields = linkedMapOf<AiImageField, Field>()
            fun save(role: AiImageMethod, discover: () -> MethodData): MethodData? {
                return scanSubStep("$TAG.$role", logger, null as MethodData?) {
                    discover().also { value ->
                        val restored = value.getMethodInstance(cl)
                        AiImageCreationCodec.validate(role, restored)
                        restored.isAccessible = true
                        resolvedMethods[role] = restored
                        HookSymbolScanDiagnostics.log(logger, "$TAG.$role: ${value.descriptor}")
                    }
                }
            }

            val imageMethods = methods(AiImageCreationCodec.IMAGE_DATA)
            save(AiImageMethod.IMAGE_JUMP_DATA) {
                one("IMAGE_JUMP_DATA", imageMethods.filter {
                    it.methodName == "getImageJumpIconData" && instance(it, AiImageCreationCodec.IMAGE_JUMP) &&
                        it.usingFields.any { use -> use.usingType.isRead() &&
                            use.field.declaredClassName == AiImageCreationCodec.IMAGE_DATA &&
                            use.field.typeName == AiImageCreationCodec.IMAGE_JUMP }
                })
            }
            save(AiImageMethod.IMAGE_JUMP_SCHEME) {
                one("IMAGE_JUMP_SCHEME", methods(AiImageCreationCodec.IMAGE_JUMP).filter {
                    it.methodName == "getScheme" && instance(it, STRING) &&
                        it.usingFields.any { use -> use.usingType.isRead() &&
                            use.field.declaredClassName == AiImageCreationCodec.IMAGE_JUMP && use.field.typeName == STRING }
                })
            }

            save(AiImageMethod.ROUTE_DISPATCH) {
                // A surviving DTO/class is insufficient if the host renames the H5 page. Require
                // the current AI route in a JSON -> encoded h5/openWebView schema producer.
                val routeBuilders = bridge.findMethod(FindMethod.create()
                    .searchPackages("com.baidu.tieba", "com.baidu.tbadk")
                    .matcher(MethodMatcher.create().usingStrings(listOf(AI_ROUTE), StringMatchType.Contains)))
                    .filter { it.returnTypeName == STRING && "h5/openWebView" in it.usingStrings &&
                        "pageParams" in it.usingStrings && it.calls(JSON, "put") &&
                        it.calls("java.net.URLEncoder", "encode") }
                require(routeBuilders.isNotEmpty()) { "Current AI image H5 route producer missing" }
                HookSymbolScanDiagnostics.log(logger, "$TAG route producers=${routeBuilders.joinToString { it.descriptor }}")
                one("ROUTE_DISPATCH", methods(AiImageCreationCodec.URL_MANAGER).filter {
                    it.methodName == "dealOneLinkWithDialog" && instance(it, "boolean",
                        AiImageCreationCodec.PAGE_CONTEXT, STRING, "$STRING[]", "boolean",
                        AiImageCreationCodec.URL_CANCEL, "boolean", "android.os.Bundle") &&
                        it.calls("com.baidu.tbadk.core.util.schemeaction.SchemeActionManager", "doSchemeAction")
                })
            }

            // BrowserHelper overloads all feed the same builder; rejecting its nullable config
            // preserves caller null checks and avoids a Hook for every overload.
            val builderOwner = scanSubStep("$TAG.WebBuilder", logger, null as String?) {
                val factory = one("WebBuilderFactory", methods(BROWSER).filter { it.methodName == "startWebActivity" }
                    .flatMap { it.invokes }.filter {
                        Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(CONTEXT, STRING) &&
                            it.returnTypeName == it.declaredClassName && it.returnTypeName != "void" &&
                            it.invokes.any { call -> call.methodName == "<init>" &&
                                call.declaredClassName == it.declaredClassName && call.paramTypeNames == listOf(CONTEXT, STRING) }
                    })
                factory.returnTypeName
            }
            val webBuild = save(AiImageMethod.WEB_CONFIG_BUILD) {
                val owner = requireNotNull(builderOwner) { "WebView builder unavailable" }
                one("WEB_CONFIG_BUILD", methods(owner).filter {
                    instance(it, AiImageCreationCodec.WEB_CONFIG, CONTEXT) && it.calls(BROWSER, "getActivityConfig") &&
                        it.calls("com.baidu.android.common.others.url.UrlUtils", "isBaiduDomain") &&
                        it.calls(BROWSER, "appendCuidParam") && "need_add_extra_param" in it.usingStrings
                })
            }
            scanSubStep("$TAG.${AiImageField.WEB_URL}", logger, Unit) {
                val build = requireNotNull(webBuild) { "WebView config builder unavailable" }
                val owner = build.declaredClassName
                val url = oneField("WEB_URL", build.usingFields.map { it.field }.filter {
                    it.declaredClassName == owner && it.typeName == STRING && !Modifier.isStatic(it.modifiers) &&
                        build.reads(it) && build.writes(it)
                })
                require(methods(owner).any { it.methodName == "<init>" &&
                    it.paramTypeNames == listOf(CONTEXT, STRING) && it.writes(url) }) { "URL field is not initialized by the route builder" }
                val restored = url.getFieldInstance(cl)
                AiImageCreationCodec.validate(AiImageField.WEB_URL, restored)
                restored.isAccessible = true
                resolvedFields[AiImageField.WEB_URL] = restored
                HookSymbolScanDiagnostics.log(logger, "$TAG.${AiImageField.WEB_URL}: ${url.descriptor}")
            }

            val inputParser = scanSubStep("$TAG.InputProtocol", logger, null as MethodData?) {
                one("InputProtocol", bridge.findMethod(FindMethod.create()
                    .searchPackages("com.baidu.tieba", "com.baidu.tbadk")
                    .matcher(MethodMatcher.create().usingStrings(listOf("edit_ai_meme_schema", "is_show_ai_meme"), StringMatchType.Equals)))
                    .filter { !Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(JSON) &&
                        it.returnTypeName != "void" && it.calls(JSON, "optString") && it.calls(JSON, "optInt") })
            }
            fun inputGetter(result: String, parameter: String, label: String): MethodData {
                val parser = requireNotNull(inputParser) { "AI input protocol parser unavailable" }
                val owner = parser.returnTypeName
                val setter = one("$label.Setter", parser.invokes.filter {
                    it.declaredClassName == owner && instance(it, "void", parameter)
                })
                val field = oneField("$label.Field", setter.usingFields.filter {
                    it.usingType.isWrite() && it.field.declaredClassName == owner &&
                        it.field.typeName == result && !Modifier.isStatic(it.field.modifiers)
                }.map { it.field })
                return one(label, methods(owner).filter {
                    instance(it, result) && it.reads(field) && !it.writes(field) &&
                        it.usingFields.none { use -> use.field.declaredClassName == owner &&
                            !Modifier.isStatic(use.field.modifiers) && use.field.descriptor != field.descriptor }
                })
            }
            save(AiImageMethod.INPUT_AI_VISIBLE) { inputGetter("int", "int", "INPUT_AI_VISIBLE") }
            save(AiImageMethod.INPUT_AI_SCHEMA) { inputGetter(STRING, STRING, "INPUT_AI_SCHEMA") }

            val guideMethods = methods(AiImageCreationCodec.PB_GUIDE)
            save(AiImageMethod.PB_GUIDE_BIND) {
                one("PB_GUIDE_BIND", guideMethods.filter {
                    instance(it, "void", "tbclient.SpriteMemeInfo", "int", "java.util.Map") &&
                        it.usingFields.any { use -> use.field.declaredClassName == "tbclient.SpriteMemeInfo" &&
                            use.field.fieldName == "use_same_guide" && use.usingType.isRead() } &&
                        it.invokes.any { call -> call.declaredClassName == AiImageCreationCodec.PB_GUIDE &&
                            "aigc_recreate_entry" in call.usingStrings }
                })
            }
            save(AiImageMethod.PB_GUIDE_CAPSULE) {
                one("PB_GUIDE_CAPSULE", guideMethods.filter {
                    it.methodName == "getCapsuleView" && instance(it, "android.view.View") &&
                        it.invokes.any { call -> call.declaredClassName == AiImageCreationCodec.PB_GUIDE &&
                            instance(call, PB_BINDING) } &&
                        it.usingFields.any { use -> use.usingType.isRead() && use.field.declaredClassName == PB_BINDING &&
                            use.field.typeName == "android.widget.LinearLayout" }
                })
            }
            save(AiImageMethod.IMAGE_BOTTOM_TIP_BIND) {
                one("IMAGE_BOTTOM_TIP_BIND", methods(AiImageCreationCodec.TIP).filter {
                    instance(it, "void", STRING, STRING) &&
                        it.calls("com.baidu.adp.lib.util.StringUtils", "isNull") &&
                        it.invokes.any { call -> call.declaredClassName == AiImageCreationCodec.TIP &&
                            instance(call, "void") && "lottie_sprite_reply_thread" in call.usingStrings } &&
                        it.usingFields.any { use -> use.usingType.isWrite() &&
                            use.field.declaredClassName == AiImageCreationCodec.TIP && use.field.typeName == STRING }
                })
            }
            AiImageCreationCodec.validateRelations(resolvedMethods, resolvedFields)
            AiImageCreationScanSymbols(resolvedMethods.toMap(), resolvedFields.toMap())
        } ?: AiImageCreationScanSymbols()

    private fun instance(method: MethodData, result: String, vararg params: String): Boolean =
        !Modifier.isStatic(method.modifiers) && !method.isConstructor &&
            method.returnTypeName == result && method.paramTypeNames == params.toList()

    private fun MethodData.reads(field: FieldData): Boolean = usingFields.any {
        it.usingType.isRead() && it.field.descriptor == field.descriptor
    }

    private fun MethodData.writes(field: FieldData): Boolean = usingFields.any {
        it.usingType.isWrite() && it.field.descriptor == field.descriptor
    }
}
