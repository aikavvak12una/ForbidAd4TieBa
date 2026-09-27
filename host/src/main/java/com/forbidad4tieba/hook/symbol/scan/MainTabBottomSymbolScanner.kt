package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
internal object MainTabBottomSymbolScanner {
    fun scan(
    candidates: List<String>,
    cl: ClassLoader,
    logger: ScanLogger?,
): MainTabBottomScanSymbols {
    val match = ScanReflection.runRules(candidates, cl, listOf(MainTabBottomLevel1Rule()), logger, "mainTabBottom")
    if (match == null) {
        log(logger, "mainTabBottom: no scan match")
        return MainTabBottomScanSymbols()
    }

    val targetClass = safeFindClass(match.className, cl)
    if (targetClass == null) {
        log(logger, "mainTabBottom: target class not found ${match.className}")
        return MainTabBottomScanSymbols()
    }

    val methodParts = match.methodName.split(",")
    val addMethod = resolveMainTabBottomAddMethod(targetClass, methodParts.getOrNull(0))
    val getListMethod = resolveMainTabBottomGetListMethod(targetClass, methodParts.getOrNull(1))
    if (addMethod == null || getListMethod == null) {
        log(
            logger,
            "mainTabBottom: unresolved methods add=${addMethod?.name}, getList=${getListMethod?.name}",
        )
        return MainTabBottomScanSymbols()
    }

    val delegateClass = addMethod.parameterTypes.firstOrNull()
    if (delegateClass == null) {
        log(logger, "mainTabBottom: add method has no delegate parameter")
        return MainTabBottomScanSymbols()
    }
    val structureGetter = resolveMainTabBottomStructureGetter(delegateClass, methodParts.getOrNull(2))
    val structureClass = structureGetter?.returnType
    if (structureGetter == null || structureClass == null) {
        log(logger, "mainTabBottom: delegate structure getter unresolved")
        return MainTabBottomScanSymbols()
    }

    val fieldParts = match.fieldName.split(",")
    val typeFieldName = resolveMainTabBottomIntFieldName(structureClass, fieldParts.getOrNull(0))
    if (typeFieldName == null) {
        log(logger, "mainTabBottom: structure type field unresolved")
        return MainTabBottomScanSymbols()
    }
    val dynamicFieldName = resolveMainTabBottomOptionalFieldName(
        clazz = structureClass,
        resolvedName = fieldParts.getOrNull(1),
    ) { field ->
        field.type == safeFindClass(MAIN_TAB_DYNAMIC_ICON_DATA_CLASS, cl)
    }
    val fragmentFieldName = resolveMainTabBottomOptionalFieldName(
        clazz = structureClass,
        resolvedName = fieldParts.getOrNull(2),
    ) { field ->
        isFragmentLikeType(field.type)
    }

    val out = MainTabBottomScanSymbols(
        dataClass = targetClass.name,
        addMethod = addMethod.name,
        getListMethod = getListMethod.name,
        delegateGetStructureMethod = structureGetter.name,
        structureTypeField = typeFieldName,
        structureDynamicIconField = dynamicFieldName,
        structureFragmentField = fragmentFieldName,
    )
    log(
        logger,
        "mainTabBottom: ${out.dataClass}.${out.addMethod}/${out.getListMethod}, " +
            "delegate=${delegateClass.name}.${out.delegateGetStructureMethod}, " +
            "structure=${structureClass.name}[type=${out.structureTypeField}, " +
            "dynamic=${out.structureDynamicIconField}, fragment=${out.structureFragmentField}]",
    )
    return out
}

private fun resolveMainTabBottomAddMethod(clazz: Class<*>, resolvedName: String?): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(clazz).filter { method ->
        method.returnType == Void.TYPE && method.parameterTypes.size == 1
    }
    return pickMethod(candidates, resolvedName)
}

private fun resolveMainTabBottomGetListMethod(clazz: Class<*>, resolvedName: String?): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(clazz).filter { method ->
        method.parameterTypes.isEmpty() && isListType(method.returnType)
    }
    return pickMethod(candidates, resolvedName)
}

private fun resolveMainTabBottomStructureGetter(
    delegateClass: Class<*>,
    resolvedName: String?,
): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(delegateClass).filter { method ->
        method.parameterTypes.isEmpty() &&
            !method.returnType.isPrimitive &&
            resolveMainTabBottomIntFieldName(method.returnType, resolvedName = "type") != null
    }
    return candidates.singleOrNull { !resolvedName.isNullOrBlank() && it.name == resolvedName }
}

private fun resolveMainTabBottomIntFieldName(clazz: Class<*>, resolvedName: String?): String? =
    collectInstanceFields(clazz).singleOrNull {
        it.type == Int::class.javaPrimitiveType && !resolvedName.isNullOrBlank() && it.name == resolvedName
    }?.name

private fun resolveMainTabBottomOptionalFieldName(
    clazz: Class<*>,
    resolvedName: String?,
    predicate: (java.lang.reflect.Field) -> Boolean,
): String? {
    val fields = collectInstanceFields(clazz).filter(predicate)
    if (fields.isEmpty()) return null
    return pickFieldName(fields, resolvedName)
}


    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        ScanReflection.collectInstanceMethods(clazz)

    private fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    private fun pickMethod(methods: List<java.lang.reflect.Method>, resolvedName: String?): java.lang.reflect.Method? =
        ScanReflection.pickMethod(methods, resolvedName)

    private fun pickFieldName(fields: List<java.lang.reflect.Field>, resolvedName: String?): String? =
        ScanReflection.pickFieldName(fields, resolvedName)

    private fun isFragmentLikeType(type: Class<*>): Boolean = ScanReflection.isFragmentLikeType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
