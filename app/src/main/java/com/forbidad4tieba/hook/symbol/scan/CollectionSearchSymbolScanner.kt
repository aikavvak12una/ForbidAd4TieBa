package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.view.View

internal object CollectionSearchSymbolScanner {
    private const val COLLECTION_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.CollectTabActivity"
    private const val COLLECTION_THREAD_FRAGMENT_CLASS = "com.baidu.tieba.myCollection.ThreadFragment"

    fun scan(cl: ClassLoader, logger: ScanLogger?): CollectionSearchScanSymbols {
        return scanSubStep("CollectionSearchHook", logger, CollectionSearchScanSymbols()) {
            val fragmentClass = safeFindClass(COLLECTION_THREAD_FRAGMENT_CLASS, cl)
            if (fragmentClass == null) {
                log(logger, "collectionScan: thread fragment class not found")
                return@scanSubStep CollectionSearchScanSymbols()
            }

            val presenterCandidate = resolveCollectionPresenterCandidate(fragmentClass)
            val modelCandidate = resolveCollectionModelCandidate(fragmentClass)
            val adapterSymbols = presenterCandidate?.let { resolveCollectionAdapterSymbols(it.presenterClass) }
                ?: CollectionAdapterSymbols()
            val fragmentDisplayListField = resolveCollectionFragmentDisplayListField(
                fragmentClass = fragmentClass,
                modelFieldName = modelCandidate?.fieldName,
            )
            val collectionActivityClass = safeFindClass(COLLECTION_ACTIVITY_CLASS, cl)
            val navigationSymbols = collectionActivityClass?.let(::resolveCollectionNavigationSymbols)
                ?: CollectionNavigationSymbols()
            val editModeMethod = collectionActivityClass?.let(::resolveCollectionEditModeMethod)

            val out = CollectionSearchScanSymbols(
                presenterField = presenterCandidate?.fieldName,
                presenterListSetterMethod = presenterCandidate?.setterMethod,
                presenterAdapterField = adapterSymbols.presenterAdapterField,
                modelField = modelCandidate?.fieldName,
                modelListGetterMethod = modelCandidate?.getterMethod,
                modelParseMethod = modelCandidate?.parseMethod,
                modelListField = modelCandidate?.listFieldName,
                fragmentDisplayListField = fragmentDisplayListField,
                activityNavControllerField = navigationSymbols.activityNavControllerField,
                navBarField = navigationSymbols.navBarField,
                adapterShowFooterMethod = adapterSymbols.showFooterMethod,
                adapterLoadingMethod = adapterSymbols.loadingMethod,
                adapterHasMoreMethod = adapterSymbols.hasMoreMethod,
                editModeMethod = editModeMethod,
            )

            log(
                logger,
                "collectionScan: presenter=${out.presenterField}.${out.presenterListSetterMethod}, " +
                    "model=${out.modelField}.${out.modelListGetterMethod}/${out.modelParseMethod}[${out.modelListField}], " +
                    "nav={${out.activityNavControllerField},${out.navBarField}}, " +
                    "adapter=${out.presenterAdapterField}.{${out.adapterShowFooterMethod},${out.adapterLoadingMethod},${out.adapterHasMoreMethod}}, " +
                    "fragmentList=${out.fragmentDisplayListField}, editMode=${out.editModeMethod}",
            )
            out
        }
    }

private fun resolveCollectionPresenterCandidate(fragmentClass: Class<*>): CollectionPresenterCandidate? {
    val candidates = collectInstanceFields(fragmentClass).mapNotNull { field ->
        val setter = resolveListSetterMethodName(field.type, preferredName = "w") ?: return@mapNotNull null
        CollectionPresenterCandidate(fieldName = field.name, setterMethod = setter, presenterClass = field.type)
    }
    return candidates.minWithOrNull(
        compareBy<CollectionPresenterCandidate>(
            { if (it.fieldName == "d") 0 else 1 },
            { if (it.setterMethod == "w") 0 else 1 },
            { it.setterMethod.length },
            { it.fieldName.length },
            { it.fieldName },
        ),
    )
}

private fun resolveCollectionModelCandidate(fragmentClass: Class<*>): CollectionModelCandidate? {
    val candidates = collectInstanceFields(fragmentClass).mapNotNull { field ->
        val getter = resolveListGetterMethodName(field.type, preferredName = "n") ?: return@mapNotNull null
        val parse = resolveParseMethodName(field.type, preferredName = "t") ?: return@mapNotNull null
        val listField = resolveListFieldName(field.type, preferredName = "d")
        CollectionModelCandidate(
            fieldName = field.name,
            getterMethod = getter,
            parseMethod = parse,
            listFieldName = listField,
        )
    }
    return candidates.minWithOrNull(
        compareBy<CollectionModelCandidate>(
            { if (it.fieldName == "c") 0 else 1 },
            { if (it.getterMethod == "n") 0 else 1 },
            { if (it.parseMethod == "t") 0 else 1 },
            { if (it.listFieldName == "d") 0 else 1 },
            { if (it.listFieldName.isNullOrBlank()) 1 else 0 },
            { it.fieldName.length },
            { it.fieldName },
        ),
    )
}

private fun resolveCollectionAdapterSymbols(presenterClass: Class<*>): CollectionAdapterSymbols {
    val adapterField = collectInstanceFields(presenterClass)
        .filter { field -> isAdapterLike(field.type) }
        .minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == "g") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
        ?: return CollectionAdapterSymbols()

    val footerCandidates = collectInstanceMethods(adapterField.type).filter { method ->
        method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            (method.parameterTypes[0] == Boolean::class.javaPrimitiveType || method.parameterTypes[0] == Boolean::class.java)
    }

    val showFooterMethod = pickMethodName(footerCandidates, preferredName = "m")
    val loadingMethod = pickMethodName(
        footerCandidates.filter { it.name != showFooterMethod },
        preferredName = "p",
    )
    val hasMoreMethod = pickMethodName(
        footerCandidates.filter { it.name != showFooterMethod && it.name != loadingMethod },
        preferredName = "n",
    )

    return CollectionAdapterSymbols(
        presenterAdapterField = adapterField.name,
        showFooterMethod = showFooterMethod,
        loadingMethod = loadingMethod,
        hasMoreMethod = hasMoreMethod,
    )
}

private fun resolveCollectionFragmentDisplayListField(fragmentClass: Class<*>, modelFieldName: String?): String? {
    val listFields = collectInstanceFields(fragmentClass).filter { isListType(it.type) }
    if (listFields.isEmpty()) return null
    listFields.firstOrNull { it.name == "f" }?.let { return it.name }
    listFields.firstOrNull { it.name != modelFieldName }?.let { return it.name }
    return listFields.minByOrNull { it.name.length }?.name
}

private fun resolveCollectionEditModeMethod(activityClass: Class<*>): String? {
    val methods = collectInstanceMethods(activityClass).filter { method ->
        method.parameterTypes.isEmpty() &&
            (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
    }
    if (methods.isEmpty()) return null
    methods.firstOrNull { it.name == "g1" }?.let { return it.name }
    methods.firstOrNull { it.name == "isEditMode" }?.let { return it.name }
    methods.firstOrNull { it.name.contains("edit", ignoreCase = true) }?.let { return it.name }
    return methods.minByOrNull { it.name.length }?.name
}

private fun resolveCollectionNavigationSymbols(activityClass: Class<*>): CollectionNavigationSymbols {
    val activityFields = collectInstanceFields(activityClass)
    val directNavField = activityFields
        .filter { field -> hasNavigationAddCustomViewMethod(field.type) }
        .minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == "b") 0 else 1 },
                { if (it.name == "d") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    if (directNavField != null) {
        return CollectionNavigationSymbols(
            activityNavControllerField = null,
            navBarField = directNavField.name,
        )
    }

    val nestedCandidates = ArrayList<Triple<String, String, Class<*>>>()
    for (controllerField in activityFields) {
        val navField = collectInstanceFields(controllerField.type)
            .firstOrNull { field -> hasNavigationAddCustomViewMethod(field.type) }
            ?: continue
        nestedCandidates.add(Triple(controllerField.name, navField.name, navField.type))
    }
    if (nestedCandidates.isEmpty()) return CollectionNavigationSymbols()

    val best = nestedCandidates.minWithOrNull(
        compareBy<Triple<String, String, Class<*>>>(
            { if (it.first == "a") 0 else 1 },
            { if (it.second == "d") 0 else 1 },
            { it.first.length },
            { it.second.length },
            { it.first },
            { it.second },
        ),
    ) ?: return CollectionNavigationSymbols()
    return CollectionNavigationSymbols(
        activityNavControllerField = best.first,
        navBarField = best.second,
    )
}

private fun hasNavigationAddCustomViewMethod(clazz: Class<*>): Boolean {
    return collectInstanceMethods(clazz).any { method ->
        method.name == "addCustomView" &&
            method.parameterTypes.size == 3 &&
            View::class.java.isAssignableFrom(method.parameterTypes[1])
    }
}


    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        ScanReflection.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<java.lang.reflect.Method> =
        ScanReflection.collectInstanceMethods(clazz)

    private fun resolveListSetterMethodName(clazz: Class<*>, preferredName: String): String? =
        ScanReflection.resolveListSetterMethodName(clazz, preferredName)

    private fun resolveListGetterMethodName(clazz: Class<*>, preferredName: String): String? =
        ScanReflection.resolveListGetterMethodName(clazz, preferredName)

    private fun resolveParseMethodName(clazz: Class<*>, preferredName: String): String? =
        ScanReflection.resolveParseMethodName(clazz, preferredName)

    private fun resolveListFieldName(clazz: Class<*>, preferredName: String): String? =
        ScanReflection.resolveListFieldName(clazz, preferredName)

    private fun isAdapterLike(type: Class<*>): Boolean = ScanReflection.isAdapterLike(type)

    private fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    private fun pickMethodName(methods: List<java.lang.reflect.Method>, preferredName: String): String? =
        ScanReflection.pickMethodName(methods, preferredName)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
