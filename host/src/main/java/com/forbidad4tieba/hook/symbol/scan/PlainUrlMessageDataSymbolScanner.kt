package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataScanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Owns payload discovery during the scan and descriptor restoration before callbacks run. */
internal object PlainUrlMessageDataSymbolScanner {
    private const val TAG = "PlainUrlMessagePayload"
    private const val COMMAND = 2001332
    private const val CUSTOM_MESSAGE = "com.baidu.adp.framework.message.CustomResponsedMessage"
    private const val MESSAGE_MANAGER = "com.baidu.adp.framework.MessageManager"
    private const val PAGE_CONTEXT = "com.baidu.tbadk.TbPageContext"
    private const val CONTEXT = "android.content.Context"
    private const val STRING = "java.lang.String"
    private val constructorPrefix = listOf("int", STRING, STRING, "int", STRING)

    internal data class Roles(val typeField: String, val urlField: String, val textField: String)

    fun scan(cl: ClassLoader, logger: ScanLogger?): PlainUrlMessageDataScanSymbols =
        ScanDexQueries.query(TAG, logger) { bridge ->
            val session = HookSymbolScanSession.get() ?: return@query PlainUrlMessageDataScanSymbols()
            val source = session.dexKitBridge(session.sourcePaths, logger) ?: return@query PlainUrlMessageDataScanSymbols()
            if (source.bridge !== bridge) return@query PlainUrlMessageDataScanSymbols()
            PlainUrlDexOperands(source.sourcePath).use { operands -> scan(bridge, operands, cl, logger) }
        } ?: PlainUrlMessageDataScanSymbols()

    private fun scan(bridge: DexKitBridge, operands: PlainUrlDexOperands, cl: ClassLoader, logger: ScanLogger?): PlainUrlMessageDataScanSymbols {
        val empty = PlainUrlMessageDataScanSymbols()
        val producers = bridge.findMethod(FindMethod.create().searchPackages("com.baidu")
            .matcher(MethodMatcher.create().name("onClick").usingNumbers(COMMAND))).filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("android.view.View") && it.invokes.any { call ->
                    call.declaredClassName == MESSAGE_MANAGER && call.methodName == "dispatchResponsedMessage" &&
                        call.paramTypeNames == listOf("com.baidu.adp.framework.message.ResponsedMessage")
                } && it.invokes.any { call -> call.declaredClassName == CUSTOM_MESSAGE && call.isConstructor &&
                    call.paramTypeNames == listOf("int", "java.lang.Object") }
        }
        val constructors = producers.flatMap { it.invokes }.filter { it.isConstructor && isPayloadConstructor(it.paramTypeNames) }
            .distinctBy { it.descriptor }
        val navigation = producers.filter { "rich_text_click" in it.usingStrings && "obj_url" in it.usingStrings }
            .map { it.declaredClassName }.distinct().flatMap { owner ->
                bridge.getClassData(owner)?.methods.orEmpty().mapNotNull { method ->
                    navigationArguments(method, owner, operands)?.let { method.descriptor to it }
                }
            }.toMap()
        if (constructors.isEmpty() || navigation.isEmpty()) return empty
        val candidates = constructors.groupBy { it.declaredClassName }.mapNotNull { (owner, producerConstructors) ->
            val fields = bridge.getClassData(owner)?.fields.orEmpty().filter {
                it.declaredClassName == owner && !Modifier.isStatic(it.modifiers)
            }.associateBy { it.descriptor }
            val initialized = producerConstructors.flatMap { initializedFields(it) }.toSet()
            val consumers = fields.values.flatMap { it.readers }.distinctBy { it.descriptor }.filter {
                it.methodName == "onMessage" && !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                    it.paramTypeNames == listOf(CUSTOM_MESSAGE) && it.calls(CUSTOM_MESSAGE, "getData")
            }
            val roles = consumers.flatMap { consumer ->
                if (consumer.invokes.none { it.descriptor in navigation }) emptyList() else {
                    operands.read(consumer)?.let { code ->
                        PlainUrlNavigationOperands.consumer(code, navigation, owner, initialized)
                    }.orEmpty()
                }
            }.distinct()
            if (roles.isEmpty()) return@mapNotNull null
            // Conflicting consumers invalidate this payload instead of silently selecting one.
            val selected = selectRoles(roles, logger) ?: return empty
            owner to selected
        }
        val (owner, roles) = uniqueSemanticCandidate("$TAG.Payload", candidates, logger) ?: return empty
        val fields = bridge.getClassData(owner)?.fields.orEmpty().associateBy { it.descriptor }
        val type = fields.getValue(roles.typeField)
        val url = fields.getValue(roles.urlField)
        val text = fields.getValue(roles.textField)
        if (restore(cl, owner, type.fieldName, url.fieldName, text.fieldName) == null) return empty
        HookSymbolScanDiagnostics.log(logger, "$TAG matched: $owner fields=${type.fieldName}/${url.fieldName}/${text.fieldName} " +
            "evidence=command-producer/constructor-write/navigation-registers")
        return PlainUrlMessageDataScanSymbols(owner, type.fieldName, url.fieldName, text.fieldName)
    }

    private fun navigationArguments(method: MethodData, owner: String, operands: PlainUrlDexOperands): PlainUrlNavigationOperands.Arguments? {
        if (!staticVoid(method, listOf(PAGE_CONTEXT, "int", STRING, STRING))) return null
        val chains = method.invokes.filter { it.declaredClassName == owner &&
            staticVoid(it, listOf(PAGE_CONTEXT, "int", STRING, STRING, STRING)) }.distinctBy { it.descriptor }.flatMap { delegate ->
            delegate.invokes.filter { route -> route.declaredClassName == owner && Modifier.isStatic(route.modifiers) &&
                route.returnTypeName == "void" && route.paramCount == 6 && route.paramTypeNames.first() == CONTEXT &&
                route.paramTypeNames.drop(2) == listOf("int", STRING, STRING, STRING)
            }.distinctBy { it.descriptor }.map { delegate to it }
        }
        val (delegate, route) = chains.singleOrNull() ?: return null
        val c = operands.read(method) ?: return null
        val d = operands.read(delegate) ?: return null
        val b = operands.read(route) ?: return null
        return PlainUrlNavigationOperands.chain(c, d, b, delegate.descriptor, route.descriptor)
    }

    private fun staticVoid(method: MethodData, parameters: List<String>): Boolean =
        Modifier.isStatic(method.modifiers) && method.returnTypeName == "void" && method.paramTypeNames == parameters

    private fun initializedFields(constructor: MethodData, visited: MutableSet<String> = HashSet()): Set<String> {
        if (!visited.add(constructor.descriptor)) return emptySet()
        val owner = constructor.declaredClassName
        return constructor.usingFields.filter { it.usingType.isWrite() && it.field.declaredClassName == owner &&
            !Modifier.isStatic(it.field.modifiers) }.mapTo(HashSet()) { it.field.descriptor } +
            constructor.invokes.filter { it.isConstructor && it.declaredClassName == owner && isPayloadConstructor(it.paramTypeNames) }
                .flatMap { initializedFields(it, visited) }
    }

    internal fun selectRoles(candidates: List<Roles>, logger: ScanLogger? = null): Roles? =
        uniqueSemanticCandidate("$TAG.Roles", candidates.distinct(), logger)

    internal fun isPayloadConstructor(parameters: List<String>): Boolean =
        parameters == constructorPrefix ||
            parameters.size == constructorPrefix.size + 1 && parameters.take(constructorPrefix.size) == constructorPrefix &&
            parameters.last() !in setOf("boolean", "byte", "char", "short", "int", "long", "float", "double")

    fun restore(
        cl: ClassLoader,
        className: String?,
        typeFieldName: String?,
        urlFieldName: String?,
        textFieldName: String?,
    ): PlainUrlMessageDataSymbols? {
        if (listOf(className, typeFieldName, urlFieldName, textFieldName).any { it.isNullOrBlank() }) return null
        return runCatching {
            val dataClass = Class.forName(requireNotNull(className), false, cl)
            val typeField = dataClass.getDeclaredField(requireNotNull(typeFieldName))
            val urlField = dataClass.getDeclaredField(requireNotNull(urlFieldName))
            val textField = dataClass.getDeclaredField(requireNotNull(textFieldName))
            if (!isStructureValid(dataClass, typeField, urlField, textField)) return null
            listOf(typeField, urlField, textField).forEach { it.isAccessible = true }
            PlainUrlMessageDataSymbols(dataClass, typeField, urlField, textField)
        }.getOrNull()
    }

    internal fun isStructureValid(dataClass: Class<*>, typeField: Field, urlField: Field, textField: Field): Boolean =
        !dataClass.isInterface && !Modifier.isAbstract(dataClass.modifiers) &&
            dataClass.declaredConstructors.any { isPayloadConstructor(it.parameterTypes.map { parameter -> parameter.name }) } &&
            listOf(typeField, urlField, textField).all { it.declaringClass == dataClass && !Modifier.isStatic(it.modifiers) } &&
            typeField.type == Int::class.javaPrimitiveType &&
            urlField.type == String::class.java && textField.type == String::class.java && urlField != textField
}
