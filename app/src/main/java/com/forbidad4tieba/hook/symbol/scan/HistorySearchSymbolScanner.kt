package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.view.View
import java.lang.reflect.Method

internal object HistorySearchSymbolScanner {
    private const val HISTORY_ACTIVITY_CLASS = "com.baidu.tieba.myCollection.history.PbHistoryActivity"
    private const val HISTORY_DATA_CLASS = "com.baidu.tieba.myCollection.baseHistory.PbHistoryData"

    fun scan(cl: ClassLoader, logger: ScanLogger?): HistorySearchScanSymbols {
        return scanSubStep("HistorySearchHook", logger, HistorySearchScanSymbols()) {
            val activityClass = safeFindClass(HISTORY_ACTIVITY_CLASS, cl)
            if (activityClass == null) {
                log(logger, "historyScan: history activity class not found")
                return@scanSubStep HistorySearchScanSymbols()
            }
            val historyDataMethods = safeFindClass(HISTORY_DATA_CLASS, cl)
                ?.let(::collectInstanceMethods)
                .orEmpty()

            val adapterCandidate = resolveHistoryAdapterCandidate(activityClass)
            val listField = collectInstanceFields(activityClass)
                .filter { isListType(it.type) }
                .minWithOrNull(
                    compareBy<java.lang.reflect.Field>(
                        { if (it.name == "h") 0 else 1 },
                        { it.name.length },
                        { it.name },
                    ),
                )?.name

            val activityListUpdateMethod = resolveActivityListUpdateMethod(activityClass)
            val out = HistorySearchScanSymbols(
                adapterField = adapterCandidate?.fieldName,
                adapterSetListMethod = adapterCandidate?.setterMethod,
                adapterSetListMethodSpec = adapterCandidate?.setterMethodSpec,
                listField = listField,
                activityListUpdateMethod = activityListUpdateMethod?.name,
                activityListUpdateMethodSpec = activityListUpdateMethod?.let(::encodeMethodSpec),
                activityNavBarField = resolveHistoryNavigationFieldName(activityClass),
                threadNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getThreadName"),
                forumNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getForumName"),
                userNameMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getUserName"),
                descriptionMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getDescription"),
                threadIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getThreadId"),
                postIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getPostID", "getPostId"),
                liveIdMethod = resolveHistoryDataStringGetterName(historyDataMethods, "getLiveId"),
            )
            log(
                logger,
                "historyScan: adapter=${out.adapterField}.${out.adapterSetListMethod}, " +
                    "listField=${out.listField}, update=${out.activityListUpdateMethod}, nav=${out.activityNavBarField}, " +
                    "getters={${out.threadNameMethod},${out.forumNameMethod},${out.userNameMethod}," +
                    "${out.descriptionMethod},${out.threadIdMethod},${out.postIdMethod},${out.liveIdMethod}}",
            )
            out
        }
    }

private fun resolveHistoryNavigationFieldName(activityClass: Class<*>): String? {
    return collectInstanceFields(activityClass)
        .filter { field -> hasNavigationAddCustomViewMethod(field.type) }
        .minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == "b") 0 else 1 },
                { if (it.name == "d") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
        ?.name
}

private fun resolveHistoryAdapterCandidate(activityClass: Class<*>): HistoryAdapterCandidate? {
    val candidates = collectInstanceFields(activityClass).mapNotNull { field ->
        val setter = resolveListSetterMethod(field.type, preferredName = "g") ?: return@mapNotNull null
        HistoryAdapterCandidate(
            fieldName = field.name,
            setterMethod = setter.name,
            setterMethodSpec = encodeMethodSpec(setter),
        )
    }
    return candidates.minWithOrNull(
        compareBy<HistoryAdapterCandidate>(
            { if (it.fieldName == "f") 0 else 1 },
            { if (it.setterMethod == "g") 0 else 1 },
            { it.setterMethod.length },
            { it.fieldName.length },
            { it.fieldName },
        ),
    )
}

private fun resolveActivityListUpdateMethod(activityClass: Class<*>): Method? {
    val candidates = collectInstanceMethods(activityClass).filter { method ->
        method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            isListType(method.parameterTypes[0])
    }
    return pickMethod(candidates, preferredName = "g")
}

private fun resolveListSetterMethod(clazz: Class<*>, preferredName: String): Method? {
    val candidates = collectInstanceMethods(clazz).filter { method ->
        method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            isListType(method.parameterTypes[0])
    }
    return pickMethod(candidates, preferredName)
}

private fun encodeMethodSpec(method: Method): String {
    return method.name + "|" +
        method.returnType.name + "|" +
        method.parameterTypes.joinToString(",") { it.name }
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

    private fun isListType(type: Class<*>): Boolean = ScanReflection.isListType(type)

    private fun pickMethod(methods: List<Method>, preferredName: String?): Method? =
        ScanReflection.pickMethod(methods, preferredName)

    private fun resolveHistoryDataStringGetterName(
        methods: List<java.lang.reflect.Method>,
        vararg candidateNames: String,
    ): String? = ScanReflection.resolveHistoryDataStringGetterName(methods, *candidateNames)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
