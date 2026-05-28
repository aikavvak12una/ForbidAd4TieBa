package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

internal object PostAdDataFilterSymbolScanner {
    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"

    fun scan(cl: ClassLoader, logger: ScanLogger?): TypeAdapterDataFilterScanSymbols {
        val typeAdapterClass = safeFindClass(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl) ?: run {
            log(logger, "typeAdapterDataFilter: class not found: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}")
            return TypeAdapterDataFilterScanSymbols()
        }
        val bdUniqueIdClass = safeFindClass(BD_UNIQUE_ID_CLASS, cl) ?: run {
            log(logger, "typeAdapterDataFilter: class not found: $BD_UNIQUE_ID_CLASS")
            return TypeAdapterDataFilterScanSymbols()
        }

        val setDataMethod = resolveTypeAdapterSetDataMethod(typeAdapterClass, logger)
        val dataItemClass = resolveTypeAdapterDataItemClass(typeAdapterClass, setDataMethod, logger)
        val getTypeMethod = dataItemClass?.let {
            resolveTypeAdapterDataGetTypeMethod(it, bdUniqueIdClass, logger)
        }

        return TypeAdapterDataFilterScanSymbols(
            setDataMethod = setDataMethod?.name,
            dataItemClass = dataItemClass?.name,
            dataGetTypeMethod = getTypeMethod?.name,
        )
    }

    private fun resolveTypeAdapterSetDataMethod(
        typeAdapterClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = typeAdapterClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isListType(method.parameterTypes[0])
        }
        val genericCandidates = candidates.filter { method ->
            extractListGenericClass(method.genericParameterTypes.firstOrNull()) != null
        }
        val resolved = genericCandidates.singleOrNull() ?: candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "typeAdapterDataFilter: setData method mismatch candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun resolveTypeAdapterDataItemClass(
        typeAdapterClass: Class<*>,
        setDataMethod: Method?,
        logger: ScanLogger?,
    ): Class<*>? {
        setDataMethod
            ?.genericParameterTypes
            ?.firstOrNull()
            ?.let(::extractListGenericClass)
            ?.let { return it }

        val listFieldItemClasses = collectInstanceFields(typeAdapterClass)
            .asSequence()
            .filter { field -> isListType(field.type) }
            .mapNotNull { field -> extractListGenericClass(field.genericType) }
            .distinctBy { it.name }
            .toList()
        if (listFieldItemClasses.size == 1) return listFieldItemClasses.first()

        val interfaceItemClasses = typeAdapterClass.genericInterfaces
            .asSequence()
            .mapNotNull(::extractSingleGenericClass)
            .distinctBy { it.name }
            .toList()
        if (interfaceItemClasses.size == 1) return interfaceItemClasses.first()

        log(
            logger,
            "typeAdapterDataFilter: data item class mismatch " +
                "fields=${listFieldItemClasses.joinToString(",") { it.name }.ifBlank { "-" }} " +
                "interfaces=${interfaceItemClasses.joinToString(",") { it.name }.ifBlank { "-" }}",
        )
        return null
    }

    private fun resolveTypeAdapterDataGetTypeMethod(
        dataItemClass: Class<*>,
        bdUniqueIdClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = dataItemClass.methods.filter { method ->
            method.parameterTypes.isEmpty() &&
                bdUniqueIdClass.isAssignableFrom(method.returnType)
        }
        val resolved = candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "typeAdapterDataFilter: getType method mismatch class=${dataItemClass.name} candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun extractListGenericClass(type: Type?): Class<*>? {
        val parameterized = type as? ParameterizedType ?: return null
        val rawClass = parameterized.rawType as? Class<*> ?: return null
        if (!isListType(rawClass)) return null
        val arg = parameterized.actualTypeArguments.singleOrNull() ?: return null
        return extractGenericClass(arg)
    }

    private fun extractSingleGenericClass(type: Type?): Class<*>? {
        val parameterized = type as? ParameterizedType ?: return null
        val args = parameterized.actualTypeArguments
        if (args.size != 1) return null
        return extractGenericClass(args[0])
    }

    private fun extractGenericClass(type: Type?): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            is WildcardType -> type.upperBounds.firstOrNull()?.let(::extractGenericClass)
            else -> null
        }
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun isListType(type: Class<*>): Boolean =
        HookSymbolResolver.isListType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
