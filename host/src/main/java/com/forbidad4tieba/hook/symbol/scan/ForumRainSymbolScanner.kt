package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object ForumRainSymbolScanner {
    private const val PROTOCOL = "tbclient.FrsPage.FrsRainInfo"
    private const val RESPONSE = "tbclient.FrsPage.DataRes"
    private const val RESPONSE_FIELD = "frs_rain_info"
    private val fields = setOf("resource", "popup_url", "popup_info_str", "business_id", "report_info")
    private val constructorParams = listOf(
        "java.lang.String", "java.lang.String", "org.json.JSONObject", "java.lang.String", "java.lang.String",
    )

    internal data class Evidence(
        val protocolReads: Set<String>,
        val constructsResult: Boolean,
        val createsJson: Boolean,
        val callerCount: Int,
        val callerReadsProtocol: Boolean,
        val callerStoresResult: Boolean,
    )

    internal data class ScanPath(val converter: Method, val headerMapperClass: Class<*>)

    fun scan(cl: ClassLoader, logger: ScanLogger?): ScanPath? {
        val candidates = ScanDexQueries.query("ForumPageAdBlockHook.Rain", logger) { bridge ->
            bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba").matcher(
                MethodMatcher.create().paramTypes(PROTOCOL),
            )).mapNotNull { method ->
                val callers = method.callers.distinctBy { it.descriptor }
                val caller = callers.singleOrNull()
                val header = caller?.takeIf {
                    Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(RESPONSE) &&
                        it.returnTypeName != "void" && it.invokes.any { call -> call.descriptor == method.descriptor }
                }
                if (!accepts(Evidence(
                    protocolReads = method.usingFields.filter {
                        it.usingType.isRead() && it.field.declaredClassName == PROTOCOL
                    }.mapTo(HashSet()) { it.field.fieldName },
                    constructsResult = method.invokes.any {
                        it.declaredClassName == method.returnTypeName && it.methodName == "<init>" &&
                            it.paramTypeNames == constructorParams
                    },
                    createsJson = method.invokes.any {
                        it.declaredClassName == "org.json.JSONObject" && it.methodName == "<init>" &&
                            it.paramTypeNames.isEmpty()
                    },
                    callerCount = callers.size,
                    callerReadsProtocol = header?.usingFields.orEmpty().any {
                        it.usingType.isRead() && it.field.declaredClassName == RESPONSE &&
                            it.field.fieldName == RESPONSE_FIELD && it.field.typeName == PROTOCOL
                    },
                    callerStoresResult = header?.invokes.orEmpty().any {
                        !Modifier.isStatic(it.modifiers) && it.declaredClassName == header?.returnTypeName &&
                            it.returnTypeName == "void" && it.paramTypeNames == listOf(method.returnTypeName)
                    },
                ))) return@mapNotNull null
                ScanPath(method.getMethodInstance(cl), Class.forName(checkNotNull(header).declaredClassName, false, cl))
            }
        }.orEmpty()
        val selected = select(candidates.map { it.converter }, logger) ?: return null
        return candidates.single { it.converter == selected }
    }

    internal fun accepts(evidence: Evidence): Boolean =
        evidence.protocolReads.containsAll(fields) && evidence.constructsResult && evidence.createsJson &&
            evidence.callerCount == 1 && evidence.callerReadsProtocol && evidence.callerStoresResult

    internal fun select(candidates: List<Method>, logger: ScanLogger?): Method? =
        uniqueSemanticCandidate("ForumPageAdBlockHook.Rain", candidates.filter(::valid), logger)

    internal fun valid(method: Method): Boolean =
        Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) && !method.isBridge &&
            method.parameterTypes.map { it.name } == listOf(PROTOCOL) && !method.returnType.isPrimitive &&
            fields.all { name -> method.parameterTypes[0].declaredFields.any {
                it.name == name && !Modifier.isStatic(it.modifiers) && it.type == String::class.java
            } } && method.returnType.declaredConstructors.any {
                it.parameterTypes.toList() == listOf(
                    String::class.java, String::class.java, JSONObject::class.java, String::class.java, String::class.java,
                )
            }

    fun restore(cl: ClassLoader, owner: String?, name: String?, result: String?): Method? {
        if (owner.isNullOrBlank() || name.isNullOrBlank() || result.isNullOrBlank()) return null
        return scanSubStep("ForumPageAdBlockHook.Rain.Restore", null, null as Method?) {
            val protocol = Class.forName(PROTOCOL, false, cl)
            val responseField = Class.forName(RESPONSE, false, cl).getDeclaredField(RESPONSE_FIELD)
            check(!Modifier.isStatic(responseField.modifiers) && responseField.type == protocol)
            Class.forName(owner, false, cl).getDeclaredMethod(name, protocol).apply {
                check(returnType.name == result && valid(this)) { "Invalid forum rain converter" }
                isAccessible = true
            }
        }
    }
}
