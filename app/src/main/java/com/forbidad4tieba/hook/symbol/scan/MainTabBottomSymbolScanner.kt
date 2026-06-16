package com.forbidad4tieba.hook.symbol.scan

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
        preferredName = fieldParts.getOrNull(1),
    ) { field ->
        field.type.name.contains("DynamicIconData")
    }
    val fragmentFieldName = resolveMainTabBottomOptionalFieldName(
        clazz = structureClass,
        preferredName = fieldParts.getOrNull(2),
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

private fun resolveMainTabBottomAddMethod(clazz: Class<*>, preferredName: String?): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(clazz).filter { method ->
        method.returnType == Void.TYPE && method.parameterTypes.size == 1
    }
    return pickMethod(candidates, preferredName)
}

private fun resolveMainTabBottomGetListMethod(clazz: Class<*>, preferredName: String?): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(clazz).filter { method ->
        method.parameterTypes.isEmpty() && isListType(method.returnType)
    }
    return pickMethod(candidates, preferredName)
}

private fun resolveMainTabBottomStructureGetter(
    delegateClass: Class<*>,
    preferredName: String?,
): java.lang.reflect.Method? {
    val candidates = collectInstanceMethods(delegateClass).filter { method ->
        method.parameterTypes.isEmpty() &&
            !method.returnType.isPrimitive &&
            resolveMainTabBottomIntFieldName(method.returnType, preferredName = "type") != null
    }
    if (candidates.isEmpty()) return null

    if (!preferredName.isNullOrBlank()) {
        val preferred = candidates.firstOrNull { it.name == preferredName }
        if (preferred != null && !java.lang.reflect.Modifier.isAbstract(preferred.modifiers)) {
            return preferred
        }
    }

    candidates.firstOrNull {
        !java.lang.reflect.Modifier.isAbstract(it.modifiers) && it.name.startsWith("get")
    }?.let { return it }

    candidates.firstOrNull { !java.lang.reflect.Modifier.isAbstract(it.modifiers) }?.let { return it }

    return pickMethod(candidates, preferredName)
}

private fun resolveMainTabBottomIntFieldName(clazz: Class<*>, preferredName: String?): String? {
    val fields = collectInstanceFields(clazz).filter { field ->
        field.type == Int::class.javaPrimitiveType
    }
    if (fields.isEmpty()) return null

    fields.firstOrNull { it.name == "type" }?.let { return it.name }
    fields.firstOrNull { it.name.contains("type", ignoreCase = true) }?.let { return it.name }

    if (!preferredName.isNullOrBlank()) {
        fields.firstOrNull { it.name == preferredName }?.let { field ->
            val suspicious = field.name.contains("Res", ignoreCase = true) ||
                field.name.contains("Drawable", ignoreCase = true) ||
                field.name.contains("Icon", ignoreCase = true)
            if (!suspicious) return field.name
        }
    }

    return fields.minWithOrNull(
        compareBy<java.lang.reflect.Field>(
            { field ->
                when {
                    field.name.length <= 2 -> 0
                    else -> 1
                }
            },
            { field -> if (field.name.contains("Res", ignoreCase = true)) 1 else 0 },
            { field -> if (field.name.contains("Drawable", ignoreCase = true)) 1 else 0 },
            { field -> if (field.name.contains("Icon", ignoreCase = true)) 1 else 0 },
            { it.name.length },
            { it.name },
        ),
    )?.name
}

private fun resolveMainTabBottomOptionalFieldName(
    clazz: Class<*>,
    preferredName: String?,
    predicate: (java.lang.reflect.Field) -> Boolean,
): String? {
    val fields = collectInstanceFields(clazz).filter(predicate)
    if (fields.isEmpty()) return null
    return pickFieldName(fields, preferredName)
}


    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        ScanReflection.collectInstanceMethods(clazz)

    private fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    private fun pickMethod(methods: List<java.lang.reflect.Method>, preferredName: String?): java.lang.reflect.Method? =
        ScanReflection.pickMethod(methods, preferredName)

    private fun pickFieldName(fields: List<java.lang.reflect.Field>, preferredName: String?): String? =
        ScanReflection.pickFieldName(fields, preferredName)

    private fun isFragmentLikeType(type: Class<*>): Boolean = ScanReflection.isFragmentLikeType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
