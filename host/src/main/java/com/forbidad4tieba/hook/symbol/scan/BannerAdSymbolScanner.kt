package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.BannerAdSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object BannerAdSymbolScanner {
    private const val LAYOUT = "tbclient.LayoutFactory"
    private const val RESPONSE = "tbclient.Personalized.DataRes"
    private const val PAGE = "tbclient.Personalized.PageData"
    private val parameters = listOf(LAYOUT, "java.util.Map", "boolean")
    private val parameterAnchors = setOf("layoutFactory", "commonLogParams")
    private val consumerAnchors = setOf("parseData. isUpdate:", ",fromCache:")

    internal data class Evidence(
        val hasParameterAnchors: Boolean,
        val readsLayout: Boolean,
        val looksUpFactory: Boolean,
        val dispatchesConversion: Boolean,
        val composesLayoutLogParams: Boolean,
        val ownerHasResponse: Boolean,
        val responseConsumerCount: Int,
        val localCallersOnly: Boolean,
    )

    internal data class Candidate(val method: Method, val evidence: Evidence)

    fun scan(cl: ClassLoader, logger: ScanLogger?): String? {
        val candidates = ScanDexQueries.query("BannerAdHook.Conversion", logger) { bridge ->
            bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba").matcher(
                MethodMatcher.create().paramTypes(parameters)
                    .usingStrings(parameterAnchors.toList(), StringMatchType.Equals),
            )).map { method ->
                val conversion = method.getMethodInstance(cl)
                val callers = method.callers.distinctBy { it.descriptor }
                val dispatch = method.invokes.singleOrNull {
                    !Modifier.isStatic(it.modifiers) &&
                        it.paramTypeNames == listOf("java.lang.Object", "java.lang.Object") &&
                        it.returnTypeName == method.returnTypeName
                }?.getMethodInstance(cl)
                Candidate(conversion, Evidence(
                    hasParameterAnchors = method.usingStrings.containsAll(parameterAnchors),
                    readsLayout = method.reads(LAYOUT, "layout", "java.lang.String"),
                    looksUpFactory = method.invokes.any {
                        it.declaredClassName in setOf("java.util.Map", "java.util.HashMap") &&
                            it.methodName == "get" && it.paramTypeNames == listOf("java.lang.Object") &&
                            it.returnTypeName == "java.lang.Object"
                    },
                    dispatchesConversion = dispatch?.let { isFactoryDispatch(it, conversion.returnType) } == true,
                    composesLayoutLogParams = method.invokes.any {
                        !Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(LAYOUT, "java.util.Map") &&
                            it.returnTypeName == "java.util.Map"
                    },
                    ownerHasResponse = bridge.getClassData(method.declaredClassName)?.fields.orEmpty().any {
                        !Modifier.isStatic(it.modifiers) && it.typeName == RESPONSE
                    },
                    responseConsumerCount = callers.count {
                        !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                            it.paramTypeNames == listOf("com.squareup.wire.Message", "boolean", "boolean") &&
                            it.usingStrings.containsAll(consumerAnchors) &&
                            it.reads(RESPONSE, "page_data", PAGE) && it.reads(PAGE, "feed_list", "java.util.List") &&
                            it.invokes.any { call -> call.descriptor == method.descriptor }
                    },
                    localCallersOnly = callers.all { it.declaredClassName == method.declaredClassName },
                ))
            }
        }.orEmpty()
        val method = select(candidates, logger) ?: return null
        protocolFields(cl)
        return "${method.declaringClass.name}#${method.name}"
    }

    internal fun accepts(evidence: Evidence): Boolean =
        evidence.hasParameterAnchors && evidence.readsLayout && evidence.looksUpFactory &&
            evidence.dispatchesConversion && evidence.composesLayoutLogParams && evidence.ownerHasResponse &&
            evidence.responseConsumerCount == 1 &&
            evidence.localCallersOnly

    internal fun isFactoryDispatch(method: Method, result: Class<*>): Boolean =
        method.declaringClass.isInterface && !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.toList() == listOf(Any::class.java, Any::class.java) && method.returnType == result

    internal fun select(candidates: List<Candidate>, logger: ScanLogger?): Method? =
        uniqueSemanticCandidate("BannerAdHook.Conversion", candidates.filter {
            accepts(it.evidence) && valid(it.method)
        }.map { it.method }, logger)

    internal fun valid(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers) || method.isBridge ||
            method.parameterTypes.map { it.name } != parameters || !method.returnType.isInterface ||
            method.declaringClass.declaredFields.none { !Modifier.isStatic(it.modifiers) && it.type.name == RESPONSE }
        ) return false
        val accessors = method.returnType.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty()
        }
        return accessors.count { it.returnType == String::class.java } == 1 &&
            accessors.count { it.returnType == Any::class.java } == 1
    }

    fun restore(cl: ClassLoader, descriptor: String?): BannerAdSymbols? {
        if (descriptor.isNullOrBlank()) return null
        return scanSubStep("BannerAdHook.Conversion.Restore", null, null) {
            val parts = descriptor.split('#')
            check(parts.size == 2 && parts.all { it.isNotBlank() }) { "Invalid banner converter descriptor" }
            val fields = protocolFields(cl)
            val method = Class.forName(parts[0], false, cl).getDeclaredMethod(
                parts[1], fields.layoutField.declaringClass, Map::class.java, java.lang.Boolean.TYPE,
            )
            check(valid(method)) { "Invalid banner converter signature or response owner" }
            BannerAdSymbols(
                conversionMethod = method.apply { isAccessible = true },
                layoutField = fields.layoutField,
                bannerCardField = fields.bannerCardField,
                componentsField = fields.componentsField,
                componentField = fields.componentField,
                feedBannerField = fields.feedBannerField,
                logParamField = fields.logParamField,
                logKeyField = fields.logKeyField,
                logValueField = fields.logValueField,
            )
        }
    }

    private data class ProtocolFields(
        val layoutField: Field,
        val bannerCardField: Field,
        val componentsField: Field,
        val componentField: Field,
        val feedBannerField: Field,
        val logParamField: Field,
        val logKeyField: Field,
        val logValueField: Field,
    )

    private fun protocolFields(cl: ClassLoader): ProtocolFields {
        val layout = Class.forName(LAYOUT, false, cl)
        val banner = Class.forName("tbclient.BannerCardLayout", false, cl)
        val component = Class.forName("tbclient.ComponentFactory", false, cl)
        val feedBanner = Class.forName("tbclient.FeedBannerComponent", false, cl)
        val log = Class.forName("tbclient.FeedKV", false, cl)
        val response = Class.forName(RESPONSE, false, cl)
        val page = Class.forName(PAGE, false, cl)
        field(response, "page_data", page)
        field(page, "feed_list", List::class.java)
        return ProtocolFields(
            layoutField = field(layout, "layout", String::class.java),
            bannerCardField = field(layout, "banner_card", banner),
            componentsField = field(banner, "components", List::class.java),
            componentField = field(component, "component", String::class.java),
            feedBannerField = field(component, "feed_banner", feedBanner),
            logParamField = field(banner, "log_param", List::class.java),
            logKeyField = field(log, "key", String::class.java),
            logValueField = field(log, "value", String::class.java),
        )
    }

    internal fun field(owner: Class<*>, name: String, type: Class<*>): Field = owner.getDeclaredField(name).apply {
        check(!Modifier.isStatic(modifiers) && this.type == type) { "Invalid banner protocol field: ${owner.name}.$name" }
        isAccessible = true
    }

    private fun MethodData.reads(owner: String, name: String, type: String): Boolean = usingFields.any {
        it.usingType.isRead() && it.field.declaredClassName == owner && it.field.fieldName == name &&
            it.field.typeName == type && !Modifier.isStatic(it.field.modifiers)
    }
}
