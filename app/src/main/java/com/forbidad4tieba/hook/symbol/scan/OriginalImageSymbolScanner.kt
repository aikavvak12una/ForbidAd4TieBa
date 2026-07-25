package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.ViewGroup
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object OriginalImageSymbolScanner {
    private const val IMAGE_PAGER_ADAPTER_CLASS = "com.baidu.tbadk.coreExtra.view.ImagePagerAdapter"
    private const val URL_DRAG_IMAGE_VIEW_CLASS = "com.baidu.tbadk.coreExtra.view.UrlDragImageView"
    private const val IMAGE_URL_DATA_CLASS = "com.baidu.tbadk.coreExtra.view.ImageUrlData"
    private const val SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val TB_MD5_CLASS = "com.baidu.tbadk.core.util.TbMd5"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): OriginalImageScanSymbols {
    val adapterClass = safeFindClass(IMAGE_PAGER_ADAPTER_CLASS, cl)
    val urlDragClass = safeFindClass(URL_DRAG_IMAGE_VIEW_CLASS, cl)
    val dataClass = safeFindClass(IMAGE_URL_DATA_CLASS, cl)
    val sharedPrefClass = safeFindClass(SHARED_PREF_HELPER_CLASS, cl)
    val md5Class = safeFindClass(TB_MD5_CLASS, cl)

    if (adapterClass == null) log(logger, "origImage: class not found: $IMAGE_PAGER_ADAPTER_CLASS")
    if (urlDragClass == null) log(logger, "origImage: class not found: $URL_DRAG_IMAGE_VIEW_CLASS")
    if (dataClass == null) log(logger, "origImage: class not found: $IMAGE_URL_DATA_CLASS")

    val adapterMethods = adapterClass?.let { declaredMethods("ImagePagerAdapter", it, logger) }
    val urlDragMethods = urlDragClass?.let { declaredMethods("UrlDragImageView", it, logger) }
    val sharedPrefMethods = sharedPrefClass?.let { declaredMethods("SharedPrefHelper", it, logger) }
    val md5Methods = md5Class?.let { declaredMethods("TbMd5", it, logger) }

    val setPrimaryItemMethod = adapterMethods?.firstOrNull { method ->
        method.name == "setPrimaryItem" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 3 &&
            ViewGroup::class.java.isAssignableFrom(method.parameterTypes[0]) &&
            method.parameterTypes[1] == Int::class.javaPrimitiveType &&
            method.parameterTypes[2] == Any::class.java
    }?.name

    val originalMethods = urlDragClass?.let { clazz ->
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "origImageMethodsDex: apk source path unavailable")
            null
        } else {
            val dexMatch = DexKitSemanticScanner.scanOriginalImageMethods(
                sourcePaths = sourcePaths,
                ownerClassName = clazz.name,
                logger = logger,
            )
            validateDexMethodMatch(clazz, dexMatch, logger)
        }
    }

    val setAssistUrlMethod = if (urlDragClass != null && dataClass != null) {
        val candidates = urlDragMethods.orEmpty().filter { method ->
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
    val assistDataCandidates = urlDragMethods?.filter { method ->
        method.parameterTypes.isEmpty() &&
            method.returnType == dataClass &&
            !Modifier.isStatic(method.modifiers)
    }.orEmpty()
    val assistDataMethod = assistDataCandidates.firstOrNull { it.name == "getmAssistUrlData" }?.name
        ?: assistDataCandidates.singleOrNull()?.name
    val originTextMethod = urlDragMethods?.firstOrNull { method ->
        method.parameterTypes.isEmpty() &&
            method.returnType == String::class.java &&
            !Modifier.isStatic(method.modifiers) &&
            method.name == "getmCheckOriginPicText"
    }?.name

    val dataFields = dataClass?.let { instanceFields("ImageUrlData", it, logger) }.orEmpty()
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

    val sharedPrefGetInstanceMethod = sharedPrefMethods?.firstOrNull { method ->
        method.name == "getInstance" &&
            Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType.name == SHARED_PREF_HELPER_CLASS
    }?.name
    val sharedPrefPutBooleanMethod = sharedPrefMethods?.firstOrNull { method ->
        method.name == "putBoolean" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == String::class.java &&
            method.parameterTypes[1] == Boolean::class.javaPrimitiveType
    }?.name
    val md5Method = md5Methods?.firstOrNull { method ->
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
        primaryReadyMethod = originalMethods?.primaryReadyMethod,
        triggerMethod = originalMethods?.triggerMethod,
        directStartMethod = originalMethods?.directStartMethod,
    )
    log(
        logger,
        "origImage symbols: view=${result.urlDragImageViewClass}.${result.triggerMethod}, " +
            "setAssist=${result.setAssistUrlMethod}, " +
            "data={${result.showButtonField},${result.blockedField},${result.originalProcessField},${result.originalUrlField}}",
    )
    return result
}

    internal fun validateDexMethodMatch(
        urlDragClass: Class<*>,
        match: DexOriginalImageMethodsMatch?,
        logger: ScanLogger?,
    ): DexOriginalImageMethodsMatch? {
        if (match == null) return null
        val methods = declaredMethods("UrlDragImageView.ValidateDex", urlDragClass, logger) ?: return null
        val triggerMethod = methods.singleOrNull { method ->
            method.name == match.triggerMethod &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty() &&
                !Modifier.isStatic(method.modifiers)
        }
        if (triggerMethod == null) {
            log(logger, "origImageMethodsDex: trigger reflection mismatch: ${match.triggerMethod}")
            return null
        }

        val primaryReadyMethod = match.primaryReadyMethod?.let { methodName ->
            methods.singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty() &&
                    !Modifier.isStatic(method.modifiers)
            }?.name.also { resolved ->
                if (resolved == null) {
                    log(logger, "origImageMethodsDex: primaryReady reflection mismatch: $methodName")
                }
            }
        }
        val directStartMethod = match.directStartMethod?.let { methodName ->
            methods.singleOrNull { method ->
                method.name == methodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                    !Modifier.isStatic(method.modifiers)
            }?.name.also { resolved ->
                if (resolved == null) {
                    log(logger, "origImageMethodsDex: directStart reflection mismatch: $methodName")
                }
            }
        }
        val validated = match.copy(
            primaryReadyMethod = primaryReadyMethod,
            triggerMethod = triggerMethod.name,
            directStartMethod = directStartMethod,
        )
        log(
            logger,
            "origImageMethodsDex matched: ${urlDragClass.name}." +
                "{${validated.primaryReadyMethod},${validated.triggerMethod},${validated.directStartMethod}} " +
                "evidence=${validated.evidence}",
        )
        return validated
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
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
        return scanSubStep("DefaultOriginalImageHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun instanceFields(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Field>? {
        return scanSubStep("DefaultOriginalImageHook.$label.InstanceFields", logger, null) {
            collectInstanceFields(clazz)
        }
    }

    private fun isBooleanType(type: Class<*>): Boolean = ScanReflection.isBooleanType(type)

    private fun isIntType(type: Class<*>): Boolean = ScanReflection.isIntType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
