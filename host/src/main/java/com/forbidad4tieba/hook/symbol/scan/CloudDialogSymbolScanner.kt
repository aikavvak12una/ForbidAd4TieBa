package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation
import com.forbidad4tieba.hook.symbol.contract.CloudDialogCodec
import com.forbidad4tieba.hook.symbol.model.CloudDialogScanSymbols
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

/** Follows single-candidate admission through both data callbacks to the renderer and result consumer. */
internal object CloudDialogSymbolScanner {
    private const val TAG = "CloudDialog"
    private const val STRING = "java.lang.String"
    private const val LIST = "java.util.List"
    private const val JSON = "org.json.JSONObject"
    private const val CONTEXT = "android.content.Context"

    fun scan(cl: ClassLoader, logger: ScanLogger?): CloudDialogScanSymbols? =
        ScanDexQueries.query(TAG, logger) { bridge ->
            fun methods(owner: String): List<MethodData> = requireNotNull(bridge.getClassData(owner)) { "Missing $owner" }.methods.toList()
            fun <T> one(label: String, values: List<T>): T = requireNotNull(
                uniqueSemanticCandidate("$TAG.$label", values.distinct(), logger),
            ) { "Missing or ambiguous $label" }
            fun instance(value: MethodData, result: String, vararg params: String): Boolean =
                !value.isConstructor && !Modifier.isStatic(value.modifiers) && !Modifier.isAbstract(value.modifiers) &&
                    value.returnTypeName == result && value.paramTypeNames == params.toList()
            fun reads(value: MethodData, field: FieldData): Boolean = value.usingFields.any {
                it.usingType.isRead() && it.field.descriptor == field.descriptor
            }
            fun writes(value: MethodData, field: FieldData): Boolean = value.usingFields.any {
                it.usingType.isWrite() && it.field.descriptor == field.descriptor
            }
            fun fieldFor(getter: MethodData, fieldName: String, type: String): FieldData = one(fieldName,
                getter.usingFields.filter { it.usingType.isRead() }.map { it.field }.filter {
                    it.declaredClassName == CloudDialogCodec.DATA && it.fieldName == fieldName &&
                        it.typeName == type && !Modifier.isStatic(it.modifiers)
                }.distinctBy { it.descriptor })
            val dataMethods = methods(CloudDialogCodec.DATA)
            fun getter(name: String, type: String): MethodData = one(name, dataMethods.filter {
                it.methodName == name && instance(it, type)
            })
            val nameGetter = getter("getDialogName", STRING)
            val urlGetter = getter("getDialogUrl", STRING)
            val strategiesGetter = getter("getDialogStrategy", LIST)
            val nameField = fieldFor(nameGetter, "dialogName", STRING)
            val urlField = fieldFor(urlGetter, "dialogUrl", STRING)
            val strategiesField = fieldFor(strategiesGetter, "dialogStrategy", LIST)

            val admission = one("Admission", methods(CloudDialogCodec.MANAGER).filter { candidate ->
                !candidate.isConstructor && !Modifier.isStatic(candidate.modifiers) && !Modifier.isAbstract(candidate.modifiers) &&
                    candidate.paramTypeNames.size == 3 && candidate.paramTypeNames[1] == CloudDialogCodec.DATA &&
                    candidate.paramTypeNames[2] == JSON && candidate.returnTypeName !in setOf("void", "boolean", STRING) &&
                    candidate.usingStrings.containsAll(listOf("currentShowingDialogList", "alreadyShownDialogs", "currentPageName", "isNeedSkipCheck")) &&
                    listOf(nameGetter, urlGetter, strategiesGetter).all { getter -> candidate.invokes.any { it.descriptor == getter.descriptor } } &&
                    candidate.invokes.any { dispatch ->
                        dispatch.declaredClassName == CloudDialogCodec.MANAGER &&
                            instance(dispatch, "void", candidate.paramTypeNames[0], STRING, LIST, STRING) &&
                            dispatch.calls("java.util.Set", "add") && dispatch.invokes.any { renderer ->
                                Modifier.isStatic(renderer.modifiers) && renderer.returnTypeName == "void" &&
                                    renderer.paramTypeNames == listOf(candidate.paramTypeNames[0], STRING, STRING, LIST) &&
                                    "yun_dialogClass" in renderer.usingStrings && renderer.calls("java.lang.Class", "getConstructor")
                            }
                    }
            })
            val callerType = admission.paramTypeNames[0]
            val resultType = admission.returnTypeName
            val constructor = one("ResultConstructor", methods(resultType).filter {
                it.isConstructor && it.paramTypeNames == listOf("boolean", STRING, STRING) &&
                    it.usingStrings.containsAll(listOf("errCode", "detail"))
            })
            val allowedField = one("ResultAllowedField", constructor.usingFields.filter { it.usingType.isWrite() }
                .map { it.field }.filter { it.declaredClassName == resultType && it.typeName == "boolean" &&
                    !Modifier.isStatic(it.modifiers) }.distinctBy { it.descriptor })
            val allowed = one("ResultAllowed", methods(resultType).filter {
                instance(it, "boolean") && reads(it, allowedField) && !writes(it, allowedField)
            })
            val consumers = admission.callers.flatMap { caller ->
                if (caller.declaredClassName == CloudDialogCodec.MANAGER && Modifier.isStatic(caller.modifiers) &&
                    caller.returnTypeName == resultType && caller.invokes.any { it.descriptor == admission.descriptor }) {
                    caller.callers.toList()
                } else listOf(caller)
            }.distinctBy { it.descriptor }.filter { consumer ->
                instance(consumer, "void", LIST) && consumer.invokes.any { it.descriptor == allowed.descriptor } &&
                    consumer.invokes.any { it.descriptor == nameGetter.descriptor }
            }
            one("PageAndPreloadConsumer", consumers.filter { "client_preload" in it.usingStrings })
            one("NamedConsumer", consumers.filter { "default" in it.usingStrings && it.invokes.any { call ->
                call.isConstructor && call.declaredClassName == callerType
            } })

            val contextTime = one("ContextTime", admission.invokes.filter { getter ->
                getter.declaredClassName == callerType && !Modifier.isStatic(getter.modifiers) && getter.paramTypeNames.isEmpty() &&
                    getter.returnTypeName !in setOf("void", "boolean", STRING, JSON, CONTEXT) &&
                    methods(getter.returnTypeName).any { it.isConstructor && it.paramTypeNames == listOf(STRING, "boolean") }
            }.distinctBy { it.descriptor })
            val timeType = contextTime.returnTypeName
            val timeConstructor = one("TimeConstructor", methods(timeType).filter {
                it.isConstructor && it.paramTypeNames == listOf(STRING, "boolean")
            })
            val timeField = one("TimeNameField", timeConstructor.usingFields.filter { it.usingType.isWrite() }.map { it.field }
                .filter { it.declaredClassName == timeType && it.typeName == STRING && !Modifier.isStatic(it.modifiers) }
                .distinctBy { it.descriptor })
            val timeName = one("TimeName", methods(timeType).filter { instance(it, STRING) && reads(it, timeField) && !writes(it, timeField) })
            val contextField = one("CallerTimeField", contextTime.usingFields.filter { it.usingType.isRead() }.map { it.field }
                .filter { it.declaredClassName == callerType && it.typeName == timeType && !Modifier.isStatic(it.modifiers) }
                .distinctBy { it.descriptor })
            require(methods(callerType).any { it.isConstructor && it.paramTypeNames.take(2) == listOf(CONTEXT, timeType) &&
                JSON in it.paramTypeNames && "dialogTime" in it.usingStrings && writes(it, contextField) }) {
                "Caller time is not owned by the cloud dialog caller context"
            }

            val strategyType = one("StrategyType", methods(CloudDialogCodec.STRATEGY).filter { getter ->
                getter.methodName == "getType" && instance(getter, STRING) && getter.usingFields.any { use ->
                    use.usingType.isRead() && use.field.declaredClassName == CloudDialogCodec.STRATEGY &&
                        use.field.typeName == STRING && !Modifier.isStatic(use.field.modifiers)
                }
            })
            require(methods(CloudDialogCodec.STRATEGY).any { parser ->
                Modifier.isStatic(parser.modifiers) && parser.paramTypeNames == listOf(JSON) && parser.returnTypeName == CloudDialogCodec.STRATEGY &&
                    parser.usingStrings.containsAll(listOf("type", "data")) && parser.calls(JSON, "optJSONObject")
            }) { "Missing cloud dialog strategy protocol parser" }

            val resultClass = requireNotNull(CacheTargetValidation.resolveCachedParameterClass(resultType, cl))
            val group = CloudDialogScanSymbols(
                CloudDialogSymbols(
                    admission.getMethodInstance(cl).apply { isAccessible = true },
                    nameField.getFieldInstance(cl).apply { isAccessible = true },
                    urlField.getFieldInstance(cl).apply { isAccessible = true },
                    strategiesField.getFieldInstance(cl).apply { isAccessible = true },
                    strategyType.getMethodInstance(cl).apply { isAccessible = true },
                    resultClass.getDeclaredConstructor(Boolean::class.javaPrimitiveType, String::class.java, String::class.java)
                        .apply { isAccessible = true },
                    contextTime.getMethodInstance(cl).apply { isAccessible = true },
                    timeName.getMethodInstance(cl).apply { isAccessible = true },
                ),
                allowed.getMethodInstance(cl).apply { isAccessible = true },
            )
            CloudDialogCodec.validate(group)
            HookSymbolScanDiagnostics.log(logger, "$TAG admission=${admission.descriptor} result=${constructor.descriptor} " +
                "time=${contextTime.descriptor}/${timeName.descriptor} consumers=${consumers.joinToString { it.descriptor }}")
            group
        }
}
