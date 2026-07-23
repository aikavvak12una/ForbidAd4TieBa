package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

internal object PostAdDataFilterSymbolScanner {
    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"

    fun scan(cl: ClassLoader, logger: ScanLogger?): TypeAdapterDataFilterScanSymbols {
        val typeAdapterClass = safeFindClass(StableTiebaHookPoints.TYPE_ADAPTER_CLASS, cl)
        if (typeAdapterClass == null) {
            log(logger, "postAdDataFilter: class not found: ${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}")
        }
        val recyclerViewTypeAdapterClass =
            safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS, cl)
        if (recyclerViewTypeAdapterClass == null) {
            log(
                logger,
                "postAdDataFilter: class not found: " +
                    StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS,
            )
        }
        val bdUniqueIdClass = safeFindClass(BD_UNIQUE_ID_CLASS, cl) ?: run {
            log(logger, "postAdDataFilter: class not found: $BD_UNIQUE_ID_CLASS")
            return TypeAdapterDataFilterScanSymbols()
        }
        return scanResolvedClasses(
            typeAdapterClass = typeAdapterClass,
            recyclerViewTypeAdapterClass = recyclerViewTypeAdapterClass,
            bdUniqueIdClass = bdUniqueIdClass,
            logger = logger,
        )
    }

    internal fun scanResolvedClasses(
        typeAdapterClass: Class<*>?,
        recyclerViewTypeAdapterClass: Class<*>?,
        bdUniqueIdClass: Class<*>,
        logger: ScanLogger?,
    ): TypeAdapterDataFilterScanSymbols {
        val typeAdapterSetDataMethod = typeAdapterClass?.let { adapterClass ->
            resolveSetDataMethod("TypeAdapter", adapterClass, logger)
        }
        val recyclerViewTypeAdapterSetDataMethod = recyclerViewTypeAdapterClass?.let { adapterClass ->
            resolveSetDataMethod("RecyclerViewTypeAdapter", adapterClass, logger)
        }
        val typeAdapterDataItemClass = typeAdapterClass?.let { adapterClass ->
            resolveDataItemClass(
                label = "TypeAdapter",
                adapterClass = adapterClass,
                setDataMethod = typeAdapterSetDataMethod,
                logger = logger,
            )
        }
        val recyclerViewDataItemClass = recyclerViewTypeAdapterClass?.let { adapterClass ->
            resolveDataItemClass(
                label = "RecyclerViewTypeAdapter",
                adapterClass = adapterClass,
                setDataMethod = recyclerViewTypeAdapterSetDataMethod,
                logger = logger,
            )
        }
        val dataItemClass = resolveSharedDataItemClass(
            typeAdapterDataItemClass,
            recyclerViewDataItemClass,
            logger,
        )
        val getTypeMethod = dataItemClass?.let {
            resolveDataGetTypeMethod(it, bdUniqueIdClass, logger)
        }

        return TypeAdapterDataFilterScanSymbols(
            typeAdapterSetDataMethod = typeAdapterSetDataMethod?.name,
            recyclerViewTypeAdapterSetDataMethod = recyclerViewTypeAdapterSetDataMethod?.name,
            dataItemClass = dataItemClass?.name,
            dataGetTypeMethod = getTypeMethod?.name,
        )
    }

    private fun resolveSetDataMethod(
        label: String,
        adapterClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = declaredMethods(label, adapterClass, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                isListType(method.parameterTypes[0])
        } ?: return null
        val genericCandidates = candidates.filter { method ->
            extractListGenericClass(method.genericParameterTypes.firstOrNull()) != null
        }
        val resolved = genericCandidates.singleOrNull() ?: candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "postAdDataFilter: $label setData method mismatch candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
        }
        return resolved
    }

    private fun resolveDataItemClass(
        label: String,
        adapterClass: Class<*>,
        setDataMethod: Method?,
        logger: ScanLogger?,
    ): Class<*>? {
        setDataMethod
            ?.genericParameterTypes
            ?.firstOrNull()
            ?.let(::extractListGenericClass)
            ?.let { return it }

        val listFieldItemClasses = (instanceFields(label, adapterClass, logger) ?: return null)
            .asSequence()
            .filter { field -> isListType(field.type) }
            .mapNotNull { field -> extractListGenericClass(field.genericType) }
            .distinctBy { it.name }
            .toList()
        if (listFieldItemClasses.size == 1) return listFieldItemClasses.first()

        val interfaceItemClasses = adapterClass.genericInterfaces
            .asSequence()
            .mapNotNull(::extractSingleGenericClass)
            .distinctBy { it.name }
            .toList()
        if (interfaceItemClasses.size == 1) return interfaceItemClasses.first()

        log(
            logger,
            "postAdDataFilter: $label data item class mismatch " +
                "fields=${listFieldItemClasses.joinToString(",") { it.name }.ifBlank { "-" }} " +
                "interfaces=${interfaceItemClasses.joinToString(",") { it.name }.ifBlank { "-" }}",
        )
        return null
    }

    private fun resolveSharedDataItemClass(
        typeAdapterDataItemClass: Class<*>?,
        recyclerViewDataItemClass: Class<*>?,
        logger: ScanLogger?,
    ): Class<*>? {
        val candidates = listOfNotNull(typeAdapterDataItemClass, recyclerViewDataItemClass)
            .distinctBy { it.name }
        if (candidates.size == 1) return candidates.single()
        log(
            logger,
            "postAdDataFilter: shared data item class mismatch candidates=" +
                candidates.joinToString(",") { it.name }.ifBlank { "-" },
        )
        return null
    }

    private fun resolveDataGetTypeMethod(
        dataItemClass: Class<*>,
        bdUniqueIdClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val candidates = methods("DataItem", dataItemClass, logger)?.filter { method ->
            method.parameterTypes.isEmpty() &&
                bdUniqueIdClass.isAssignableFrom(method.returnType)
        } ?: return null
        val resolved = candidates.singleOrNull()
        if (resolved == null) {
            log(
                logger,
                "postAdDataFilter: getType method mismatch class=${dataItemClass.name} candidates=" +
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
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("TypeAdapterDataFilterHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun instanceFields(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Field>? {
        return scanSubStep("TypeAdapterDataFilterHook.$label.InstanceFields", logger, null) {
            collectInstanceFields(clazz)
        }
    }

    private fun methods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("TypeAdapterDataFilterHook.$label.Methods", logger, null) {
            clazz.methods.toList()
        }
    }

    private fun isListType(type: Class<*>): Boolean =
        ScanReflection.isListType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
