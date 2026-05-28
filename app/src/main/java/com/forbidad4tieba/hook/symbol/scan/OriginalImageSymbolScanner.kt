package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.view.ViewGroup
import java.lang.reflect.Modifier

internal object OriginalImageSymbolScanner {
    private const val IMAGE_PAGER_ADAPTER_CLASS = "com.baidu.tbadk.coreExtra.view.ImagePagerAdapter"
    private const val URL_DRAG_IMAGE_VIEW_CLASS = "com.baidu.tbadk.coreExtra.view.UrlDragImageView"
    private const val IMAGE_URL_DATA_CLASS = "com.baidu.tbadk.coreExtra.view.ImageUrlData"
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val TB_MD5_CLASS = "com.baidu.tbadk.core.util.TbMd5"

    fun scan(cl: ClassLoader, logger: ScanLogger?): OriginalImageScanSymbols {
    val adapterClass = safeFindClass(IMAGE_PAGER_ADAPTER_CLASS, cl)
    val urlDragClass = safeFindClass(URL_DRAG_IMAGE_VIEW_CLASS, cl)
    val dataClass = safeFindClass(IMAGE_URL_DATA_CLASS, cl)
    val sharedPrefClass = safeFindClass(SHARED_PREF_HELPER_CLASS, cl)
    val md5Class = safeFindClass(TB_MD5_CLASS, cl)

    if (adapterClass == null) log(logger, "origImage: class not found: $IMAGE_PAGER_ADAPTER_CLASS")
    if (urlDragClass == null) log(logger, "origImage: class not found: $URL_DRAG_IMAGE_VIEW_CLASS")
    if (dataClass == null) log(logger, "origImage: class not found: $IMAGE_URL_DATA_CLASS")

    val setPrimaryItemMethod = adapterClass?.declaredMethods?.firstOrNull { method ->
        method.name == "setPrimaryItem" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 3 &&
            ViewGroup::class.java.isAssignableFrom(method.parameterTypes[0]) &&
            method.parameterTypes[1] == Int::class.javaPrimitiveType &&
            method.parameterTypes[2] == Any::class.java
    }?.name

    val originalMethods = urlDragClass
        ?.let { runCatching { OriginalImageMethodsRule().match(it, cl) }.getOrNull() }
        ?.methodName
        ?.split(",")
        ?.map { it.trim().ifEmpty { null } }
        .orEmpty()
    fun originalMethodPart(index: Int): String? = originalMethods.getOrNull(index)

    val setAssistUrlMethod = if (urlDragClass != null && dataClass != null) {
        val candidates = urlDragClass.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == dataClass &&
                !Modifier.isStatic(method.modifiers)
        }
        candidates.singleOrNull { it.name == "setAssistUrl" }?.name
            ?: candidates.singleOrNull()?.name
    } else {
        null
    }
    val assistDataCandidates = urlDragClass?.declaredMethods?.filter { method ->
        method.parameterTypes.isEmpty() &&
            method.returnType == dataClass &&
            !Modifier.isStatic(method.modifiers)
    }.orEmpty()
    val assistDataMethod = assistDataCandidates.firstOrNull { it.name == "getmAssistUrlData" }?.name
        ?: assistDataCandidates.singleOrNull()?.name
    val originTextMethod = urlDragClass?.declaredMethods?.firstOrNull { method ->
        method.parameterTypes.isEmpty() &&
            method.returnType == String::class.java &&
            !Modifier.isStatic(method.modifiers) &&
            method.name == "getmCheckOriginPicText"
    }?.name

    val dataFields = dataClass?.let(::collectInstanceFields).orEmpty()
    val showButtonField = dataFields.firstOrNull {
        it.name == "mIsShowOrigonButton" && isBooleanType(it.type)
    }?.name
    val blockedField = dataFields.firstOrNull {
        it.name == "isBlockedPic" && isBooleanType(it.type)
    }?.name
    val originalProcessField = dataFields.firstOrNull {
        it.name == "originalProcess" && isIntType(it.type)
    }?.name
    val originalUrlField = dataFields.firstOrNull {
        it.name == "originalUrl" && it.type == String::class.java
    }?.name

    val sharedPrefGetInstanceMethod = sharedPrefClass?.declaredMethods?.firstOrNull { method ->
        method.name == "getInstance" &&
            Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType.name == SHARED_PREF_HELPER_CLASS
    }?.name
    val sharedPrefPutBooleanMethod = sharedPrefClass?.declaredMethods?.firstOrNull { method ->
        method.name == "putBoolean" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == String::class.java &&
            method.parameterTypes[1] == Boolean::class.javaPrimitiveType
    }?.name
    val md5Method = md5Class?.declaredMethods?.firstOrNull { method ->
        method.name == "getNameMd5FromUrl" &&
            Modifier.isStatic(method.modifiers) &&
            method.returnType == String::class.java &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == String::class.java
    }?.name

    val result = OriginalImageScanSymbols(
        pagerAdapterClass = adapterClass?.name,
        urlDragImageViewClass = urlDragClass?.name,
        dataClass = dataClass?.name,
        setPrimaryItemMethod = setPrimaryItemMethod,
        setAssistUrlMethod = setAssistUrlMethod,
        assistDataMethod = assistDataMethod,
        originTextMethod = originTextMethod,
        showButtonField = showButtonField,
        blockedField = blockedField,
        originalProcessField = originalProcessField,
        originalUrlField = originalUrlField,
        sharedPrefHelperClass = sharedPrefClass?.name,
        sharedPrefGetInstanceMethod = sharedPrefGetInstanceMethod,
        sharedPrefPutBooleanMethod = sharedPrefPutBooleanMethod,
        md5Class = md5Class?.name,
        md5Method = md5Method,
        primaryReadyMethod = originalMethodPart(0),
        triggerMethod = originalMethodPart(1),
        directStartMethod = originalMethodPart(2),
    )
    log(
        logger,
        "origImage symbols: view=${result.urlDragImageViewClass}.${result.triggerMethod}, " +
            "setAssist=${result.setAssistUrlMethod}, " +
            "data={${result.showButtonField},${result.blockedField},${result.originalProcessField},${result.originalUrlField}}",
    )
    return result
}


    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun isBooleanType(type: Class<*>): Boolean = HookSymbolResolver.isBooleanType(type)

    private fun isIntType(type: Class<*>): Boolean = HookSymbolResolver.isIntType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
