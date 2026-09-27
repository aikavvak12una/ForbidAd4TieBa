package com.forbidad4tieba.hook.symbol.contract

import android.view.ViewGroup
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.DefaultOriginalImageSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.OriginalImageScanSymbols
import com.forbidad4tieba.hook.symbol.scan.OriginalImageSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object OriginalImageContract : SymbolContract("OriginalImage") {
    internal override val candidateClasses = listOf(
        "com.baidu.tbadk.coreExtra.view.UrlDragImageView",
    )

    val origImagePagerAdapterClass = text("origImagePagerAdapterClass")
    val origImageUrlDragImageViewClass = text("origImageUrlDragImageViewClass")
    val origImageDataClass = text("origImageDataClass")
    val origImageSetPrimaryItemMethod = text("origImageSetPrimaryItemMethod")
    val origImageSetAssistUrlMethod = text("origImageSetAssistUrlMethod")
    val origImageAssistDataMethod = text("origImageAssistDataMethod")
    val origImageOriginTextMethod = text("origImageOriginTextMethod")
    val origImageShowButtonField = text("origImageShowButtonField")
    val origImageBlockedField = text("origImageBlockedField")
    val origImageOriginalProcessField = text("origImageOriginalProcessField")
    val origImageOriginalUrlField = text("origImageOriginalUrlField")
    val origImageSharedPrefHelperClass = text("origImageSharedPrefHelperClass")
    val origImageSharedPrefGetInstanceMethod = text("origImageSharedPrefGetInstanceMethod")
    val origImageSharedPrefPutBooleanMethod = text("origImageSharedPrefPutBooleanMethod")
    val origImageMd5Class = text("origImageMd5Class")
    val origImageMd5Method = text("origImageMd5Method")
    val origImagePrimaryReadyMethod = text("origImagePrimaryReadyMethod")
    val origImageTriggerMethod = text("origImageTriggerMethod")
    val origImageDirectStartMethod = text("origImageDirectStartMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {
        val originalImageScan = runScanStep(
            "DefaultOriginalImageHook",
            logger,
            scanErrors,
            OriginalImageScanSymbols(),
        ) {
            OriginalImageSymbolScanner.scan(context, cl, logger)
        }

        output[OriginalImageContract.origImagePagerAdapterClass] = originalImageScan.pagerAdapterClass
        output[OriginalImageContract.origImageUrlDragImageViewClass] = originalImageScan.urlDragImageViewClass
        output[OriginalImageContract.origImageDataClass] = originalImageScan.dataClass
        output[OriginalImageContract.origImageSetPrimaryItemMethod] = originalImageScan.setPrimaryItemMethod
        output[OriginalImageContract.origImageSetAssistUrlMethod] = originalImageScan.setAssistUrlMethod
        output[OriginalImageContract.origImageAssistDataMethod] = originalImageScan.assistDataMethod
        output[OriginalImageContract.origImageOriginTextMethod] = originalImageScan.originTextMethod
        output[OriginalImageContract.origImageShowButtonField] = originalImageScan.showButtonField
        output[OriginalImageContract.origImageBlockedField] = originalImageScan.blockedField
        output[OriginalImageContract.origImageOriginalProcessField] = originalImageScan.originalProcessField
        output[OriginalImageContract.origImageOriginalUrlField] = originalImageScan.originalUrlField
        output[OriginalImageContract.origImageSharedPrefHelperClass] = originalImageScan.sharedPrefHelperClass
        output[OriginalImageContract.origImageSharedPrefGetInstanceMethod] = originalImageScan.sharedPrefGetInstanceMethod
        output[OriginalImageContract.origImageSharedPrefPutBooleanMethod] = originalImageScan.sharedPrefPutBooleanMethod
        output[OriginalImageContract.origImageMd5Class] = originalImageScan.md5Class
        output[OriginalImageContract.origImageMd5Method] = originalImageScan.md5Method
        output[OriginalImageContract.origImagePrimaryReadyMethod] = originalImageScan.primaryReadyMethod
        output[OriginalImageContract.origImageTriggerMethod] = originalImageScan.triggerMethod
        output[OriginalImageContract.origImageDirectStartMethod] = originalImageScan.directStartMethod
    }

    fun resolveDefaultOriginalImageSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): DefaultOriginalImageSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[DefaultOriginalImageHook] disabled: scan symbols unavailable")
                return null
            }
            val missing = ArrayList<String>(8)
            fun requireSymbol(name: String, value: String?): String? {
                if (value.isNullOrBlank()) {
                    missing.add(name)
                    return null
                }
                return value
            }

            val urlDragClassName = requireSymbol(
                "origImageUrlDragImageViewClass",
                resolvedSymbols[OriginalImageContract.origImageUrlDragImageViewClass],
            )
            val dataClassName = requireSymbol("origImageDataClass", resolvedSymbols[OriginalImageContract.origImageDataClass])
            val assistDataMethod = requireSymbol("origImageAssistDataMethod", resolvedSymbols[OriginalImageContract.origImageAssistDataMethod])
            val showButtonField = requireSymbol("origImageShowButtonField", resolvedSymbols[OriginalImageContract.origImageShowButtonField])
            val blockedField = requireSymbol("origImageBlockedField", resolvedSymbols[OriginalImageContract.origImageBlockedField])
            val originalProcessField = requireSymbol(
                "origImageOriginalProcessField",
                resolvedSymbols[OriginalImageContract.origImageOriginalProcessField],
            )
            val originalUrlField = requireSymbol("origImageOriginalUrlField", resolvedSymbols[OriginalImageContract.origImageOriginalUrlField])
            val triggerMethod = requireSymbol("origImageTriggerMethod", resolvedSymbols[OriginalImageContract.origImageTriggerMethod])
            if (missing.isNotEmpty()) {
                Diagnostics.log("[DefaultOriginalImageHook] disabled: missing scan symbols ${missing.joinToString(",")}")
                return null
            }

            val urlDragClass = ScanReflection.safeFindClass(urlDragClassName!!, cl) ?: run {
                Diagnostics.log("[DefaultOriginalImageHook] class NOT FOUND: $urlDragClassName")
                return null
            }
            val dataClass = ScanReflection.safeFindClass(dataClassName!!, cl) ?: run {
                Diagnostics.log("[DefaultOriginalImageHook] class NOT FOUND: $dataClassName")
                return null
            }

            DefaultOriginalImageSymbols(
                pagerAdapterClass = resolvedSymbols[OriginalImageContract.origImagePagerAdapterClass]?.takeIf { it.isNotBlank() }
                    ?.let { ScanReflection.safeFindClass(it, cl) },
                urlDragImageViewClass = urlDragClass,
                dataClass = dataClass,
                setPrimaryItemMethod = resolvedSymbols[OriginalImageContract.origImageSetPrimaryItemMethod],
                setAssistUrlMethod = resolvedSymbols[OriginalImageContract.origImageSetAssistUrlMethod],
                assistDataMethod = assistDataMethod!!,
                originTextMethod = resolvedSymbols[OriginalImageContract.origImageOriginTextMethod],
                showButtonField = showButtonField!!,
                blockedField = blockedField!!,
                originalProcessField = originalProcessField!!,
                originalUrlField = originalUrlField!!,
                sharedPrefHelperClass = resolvedSymbols[OriginalImageContract.origImageSharedPrefHelperClass]?.takeIf { it.isNotBlank() }
                    ?.let { ScanReflection.safeFindClass(it, cl) },
                sharedPrefGetInstanceMethod = resolvedSymbols[OriginalImageContract.origImageSharedPrefGetInstanceMethod],
                sharedPrefPutBooleanMethod = resolvedSymbols[OriginalImageContract.origImageSharedPrefPutBooleanMethod],
                md5Class = resolvedSymbols[OriginalImageContract.origImageMd5Class]?.takeIf { it.isNotBlank() }?.let { ScanReflection.safeFindClass(it, cl) },
                md5Method = resolvedSymbols[OriginalImageContract.origImageMd5Method],
                triggerMethod = triggerMethod!!,
                directStartMethod = resolvedSymbols[OriginalImageContract.origImageDirectStartMethod],
            )
        } catch (t: Throwable) {
            Diagnostics.log("[DefaultOriginalImageHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val imageCritical = ArrayList<String>(8)
        val imageOptional = ArrayList<String>(10)
        if (symbols[OriginalImageContract.origImageUrlDragImageViewClass].isNullOrBlank()) imageCritical.add("origImageUrlDragImageViewClass")
        if (symbols[OriginalImageContract.origImageDataClass].isNullOrBlank()) imageCritical.add("origImageDataClass")
        if (symbols[OriginalImageContract.origImageTriggerMethod].isNullOrBlank()) imageCritical.add("origImageTriggerMethod")
        if (symbols[OriginalImageContract.origImageAssistDataMethod].isNullOrBlank()) imageCritical.add("origImageAssistDataMethod")
        if (symbols[OriginalImageContract.origImageShowButtonField].isNullOrBlank()) imageCritical.add("origImageShowButtonField")
        if (symbols[OriginalImageContract.origImageBlockedField].isNullOrBlank()) imageCritical.add("origImageBlockedField")
        if (symbols[OriginalImageContract.origImageOriginalProcessField].isNullOrBlank()) imageCritical.add("origImageOriginalProcessField")
        if (symbols[OriginalImageContract.origImageOriginalUrlField].isNullOrBlank()) imageCritical.add("origImageOriginalUrlField")
        if (symbols[OriginalImageContract.origImagePagerAdapterClass].isNullOrBlank()) imageOptional.add("origImagePagerAdapterClass")
        if (symbols[OriginalImageContract.origImageSetPrimaryItemMethod].isNullOrBlank()) imageOptional.add("origImageSetPrimaryItemMethod")
        if (symbols[OriginalImageContract.origImageSetAssistUrlMethod].isNullOrBlank()) imageOptional.add("origImageSetAssistUrlMethod")
        if (symbols[OriginalImageContract.origImagePrimaryReadyMethod].isNullOrBlank()) imageOptional.add("origImagePrimaryReadyMethod")
        if (symbols[OriginalImageContract.origImageDirectStartMethod].isNullOrBlank()) imageOptional.add("origImageDirectStartMethod")
        if (symbols[OriginalImageContract.origImageOriginTextMethod].isNullOrBlank()) imageOptional.add("origImageOriginTextMethod")
        if (symbols[OriginalImageContract.origImageSharedPrefHelperClass].isNullOrBlank()) imageOptional.add("origImageSharedPrefHelperClass")
        if (symbols[OriginalImageContract.origImageSharedPrefGetInstanceMethod].isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefGetInstanceMethod")
        }
        if (symbols[OriginalImageContract.origImageSharedPrefPutBooleanMethod].isNullOrBlank()) {
            imageOptional.add("origImageSharedPrefPutBooleanMethod")
        }
        if (symbols[OriginalImageContract.origImageMd5Class].isNullOrBlank()) imageOptional.add("origImageMd5Class")
        if (symbols[OriginalImageContract.origImageMd5Method].isNullOrBlank()) imageOptional.add("origImageMd5Method")
        out[HookFeatureKey.DEFAULT_ORIGINAL_IMAGE] = when {
            imageCritical.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = imageCritical,
                missingOptional = imageOptional,
            )
            imageOptional.isNotEmpty() -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = imageOptional,
            )
            else -> HookFeatureStatus(state = HookFeatureState.FULL)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "DefaultOriginalImageHook",
            "${symbols[OriginalImageContract.origImageUrlDragImageViewClass]}.${symbols[OriginalImageContract.origImageTriggerMethod]}",
            listOf(
                OriginalImageContract.origImageUrlDragImageViewClass.check(symbols),
                OriginalImageContract.origImageDataClass.check(symbols),
                OriginalImageContract.origImageTriggerMethod.check(symbols),
                OriginalImageContract.origImageAssistDataMethod.check(symbols),
                OriginalImageContract.origImageShowButtonField.check(symbols),
                OriginalImageContract.origImageBlockedField.check(symbols),
                OriginalImageContract.origImageOriginalProcessField.check(symbols),
                OriginalImageContract.origImageOriginalUrlField.check(symbols),
            ),
        )
        add(
            "DefaultOriginalImageHook.PrimaryItem",
            "${symbols[OriginalImageContract.origImagePagerAdapterClass]}.${symbols[OriginalImageContract.origImageSetPrimaryItemMethod]}",
            listOf(
                OriginalImageContract.origImagePagerAdapterClass.check(symbols),
                OriginalImageContract.origImageSetPrimaryItemMethod.check(symbols),
            ),
        )
        add(
            "DefaultOriginalImageHook.UrlDragExtra",
            "${symbols[OriginalImageContract.origImageUrlDragImageViewClass]}.{${symbols[OriginalImageContract.origImageSetAssistUrlMethod]},${symbols[OriginalImageContract.origImagePrimaryReadyMethod]},${symbols[OriginalImageContract.origImageDirectStartMethod]},${symbols[OriginalImageContract.origImageOriginTextMethod]}}",
            listOf(
                OriginalImageContract.origImageSetAssistUrlMethod.check(symbols),
                OriginalImageContract.origImagePrimaryReadyMethod.check(symbols),
                OriginalImageContract.origImageDirectStartMethod.check(symbols),
                OriginalImageContract.origImageOriginTextMethod.check(symbols),
            ),
        )
        add(
            "DefaultOriginalImageHook.SharedPrefs",
            "${symbols[OriginalImageContract.origImageSharedPrefHelperClass]}.{${symbols[OriginalImageContract.origImageSharedPrefGetInstanceMethod]},${symbols[OriginalImageContract.origImageSharedPrefPutBooleanMethod]}}",
            listOf(
                OriginalImageContract.origImageSharedPrefHelperClass.check(symbols),
                OriginalImageContract.origImageSharedPrefGetInstanceMethod.check(symbols),
                OriginalImageContract.origImageSharedPrefPutBooleanMethod.check(symbols),
            ),
        )
        add(
            "DefaultOriginalImageHook.Md5",
            "${symbols[OriginalImageContract.origImageMd5Class]}.${symbols[OriginalImageContract.origImageMd5Method]}",
            listOf(
                OriginalImageContract.origImageMd5Class.check(symbols),
                OriginalImageContract.origImageMd5Method.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("DefaultOriginalImageHook", true, listOf(HookFeatureKey.DEFAULT_ORIGINAL_IMAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasOriginalImageSymbols =
            symbols[OriginalImageContract.origImagePagerAdapterClass] != null ||
                symbols[OriginalImageContract.origImageUrlDragImageViewClass] != null ||
                symbols[OriginalImageContract.origImageDataClass] != null ||
                symbols[OriginalImageContract.origImageTriggerMethod] != null ||
                symbols[OriginalImageContract.origImageAssistDataMethod] != null
        if (hasOriginalImageSymbols && !isOriginalImageValid(symbols, cl)) return false
        return true
    }

    private fun isOriginalImageValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return try {
            val adapterClassName = symbols[OriginalImageContract.origImagePagerAdapterClass]
            val setPrimaryMethodName = symbols[OriginalImageContract.origImageSetPrimaryItemMethod]
            if (!adapterClassName.isNullOrBlank() || !setPrimaryMethodName.isNullOrBlank()) {
                if (adapterClassName.isNullOrBlank() || setPrimaryMethodName.isNullOrBlank()) return false
                val adapterClass = ScanReflection.safeFindClass(adapterClassName, cl) ?: return false
                val hasSetPrimary = adapterClass.declaredMethods.any { method ->
                    method.name == setPrimaryMethodName &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 3 &&
                        ViewGroup::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                        method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[2] == Any::class.java
                }
                if (!hasSetPrimary) return false
            }

            val urlDragClassName = symbols[OriginalImageContract.origImageUrlDragImageViewClass]
            val urlDragClass = if (!urlDragClassName.isNullOrBlank()) {
                ScanReflection.safeFindClass(urlDragClassName, cl) ?: return false
            } else {
                null
            }
            fun hasUrlDragMethod(methodName: String?, check: (Method) -> Boolean): Boolean {
                if (methodName.isNullOrBlank()) return true
                val cls = urlDragClass ?: return false
                return cls.declaredMethods.any { it.name == methodName && check(it) }
            }

            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImagePrimaryReadyMethod]) {
                    it.returnType == Void.TYPE && it.parameterTypes.isEmpty()
                }) return false
            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImageTriggerMethod]) {
                    it.returnType == Void.TYPE && it.parameterTypes.isEmpty()
                }) return false
            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImageDirectStartMethod]) {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
                }) return false
            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImageSetAssistUrlMethod]) {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == symbols[OriginalImageContract.origImageDataClass]
                }) return false

            val dataClassName = symbols[OriginalImageContract.origImageDataClass]
            val dataClass = if (!dataClassName.isNullOrBlank()) {
                ScanReflection.safeFindClass(dataClassName, cl) ?: return false
            } else {
                null
            }
            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImageAssistDataMethod]) {
                    it.parameterTypes.isEmpty() && it.returnType.name == dataClassName
                }) return false
            if (!hasUrlDragMethod(symbols[OriginalImageContract.origImageOriginTextMethod]) {
                    it.parameterTypes.isEmpty() && it.returnType == String::class.java
                }) return false

            val dataFields = dataClass?.let(ScanReflection::collectInstanceFields).orEmpty()
            fun hasDataField(fieldName: String?, check: (Class<*>) -> Boolean): Boolean {
                if (fieldName.isNullOrBlank()) return true
                if (dataClass == null) return false
                return dataFields.any { it.name == fieldName && check(it.type) }
            }
            if (!hasDataField(symbols[OriginalImageContract.origImageShowButtonField], ScanReflection::isBooleanType)) return false
            if (!hasDataField(symbols[OriginalImageContract.origImageBlockedField], ScanReflection::isBooleanType)) return false
            if (!hasDataField(symbols[OriginalImageContract.origImageOriginalProcessField], ScanReflection::isIntType)) return false
            if (!hasDataField(symbols[OriginalImageContract.origImageOriginalUrlField]) { it == String::class.java }) return false

            val sharedPrefClassName = symbols[OriginalImageContract.origImageSharedPrefHelperClass]
            val sharedPrefClass = if (!sharedPrefClassName.isNullOrBlank()) {
                ScanReflection.safeFindClass(sharedPrefClassName, cl) ?: return false
            } else {
                null
            }
            val getInstanceName = symbols[OriginalImageContract.origImageSharedPrefGetInstanceMethod]
            if (!getInstanceName.isNullOrBlank()) {
                val cls = sharedPrefClass ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == getInstanceName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType.name == sharedPrefClassName
                }
                if (!ok) return false
            }
            val putBooleanName = symbols[OriginalImageContract.origImageSharedPrefPutBooleanMethod]
            if (!putBooleanName.isNullOrBlank()) {
                val cls = sharedPrefClass ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == putBooleanName &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                if (!ok) return false
            }

            val md5ClassName = symbols[OriginalImageContract.origImageMd5Class]
            val md5Class = if (!md5ClassName.isNullOrBlank()) {
                ScanReflection.safeFindClass(md5ClassName, cl) ?: return false
            } else {
                null
            }
            val md5MethodName = symbols[OriginalImageContract.origImageMd5Method]
            if (!md5MethodName.isNullOrBlank()) {
                val cls = md5Class ?: return false
                val ok = cls.declaredMethods.any { method ->
                    method.name == md5MethodName &&
                        Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
                if (!ok) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
