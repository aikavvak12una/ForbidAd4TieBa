package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.ImageViewerNativeShareSymbols
import com.forbidad4tieba.hook.symbol.model.ImageViewerShareScanSymbols
import com.forbidad4tieba.hook.symbol.model.ShareTrackingParamCleanerSymbols
import com.forbidad4tieba.hook.symbol.scan.ImageViewerShareSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object ImageSharingContract : SymbolContract("ImageSharing") {
    internal override val candidateClasses = listOf(
        "com.baidu.tbadk.core.atomData.ShareDialogConfig",
        "com.baidu.tbadk.coreExtra.share.ShareItem",
        "com.baidu.tieba.sharesdk.view.ShareDialogItemView",
    )

    val shareTrackBuilderClass = text("shareTrackBuilderClass")
    val shareTrackBuildUrlMethod = text("shareTrackBuildUrlMethod")
    val shareTrackAppendQueryMethod = text("shareTrackAppendQueryMethod")
    val imageViewerShareConfigClass = text("imageViewerShareConfigClass")
    val imageViewerShareIsDialogField = text("imageViewerShareIsDialogField")
    val imageViewerShareItemField = text("imageViewerShareItemField")
    val imageViewerShareAddOutsideMethod = text("imageViewerShareAddOutsideMethod")
    val imageViewerShareGetRequestDataMethod = text("imageViewerShareGetRequestDataMethod")
    val imageViewerShareSetRequestDataMethod = text("imageViewerShareSetRequestDataMethod")
    val imageViewerShareGetContextMethod = text("imageViewerShareGetContextMethod")
    val imageViewerShareItemClass = text("imageViewerShareItemClass")
    val imageViewerShareItemTitleField = text("imageViewerShareItemTitleField")
    val imageViewerShareItemContentField = text("imageViewerShareItemContentField")
    val imageViewerShareItemLinkUrlField = text("imageViewerShareItemLinkUrlField")
    val imageViewerShareItemImageUriField = text("imageViewerShareItemImageUriField")
    val imageViewerShareItemImageUrlField = text("imageViewerShareItemImageUrlField")
    val imageViewerShareItemLocalFileField = text("imageViewerShareItemLocalFileField")
    val imageViewerShareItemViewClass = text("imageViewerShareItemViewClass")
    val imageViewerShareItemNameByResMethod = text("imageViewerShareItemNameByResMethod")
    val imageViewerShareItemNameByTextMethod = text("imageViewerShareItemNameByTextMethod")
    val imageViewerShareIconResId = number("imageViewerShareIconResId")

    fun isNativeShareReady(symbols: HookSymbols): Boolean = listOf<Any?>(
        symbols[imageViewerShareConfigClass],
        symbols[imageViewerShareIsDialogField],
        symbols[imageViewerShareItemField],
        symbols[imageViewerShareAddOutsideMethod],
        symbols[imageViewerShareGetRequestDataMethod],
        symbols[imageViewerShareSetRequestDataMethod],
        symbols[imageViewerShareGetContextMethod],
        symbols[imageViewerShareItemClass],
        symbols[imageViewerShareItemImageUriField],
        symbols[imageViewerShareItemViewClass],
        symbols[imageViewerShareItemNameByResMethod],
        symbols[imageViewerShareItemNameByTextMethod],
        symbols[imageViewerShareIconResId],
    ).all { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Int -> value != 0
            else -> false
        }
    }

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val imageViewerShareScan = runScanStep(
            "ImageViewerShareHooks",
            logger,
            scanErrors,
            ImageViewerShareScanSymbols(),
        ) {
            ImageViewerShareSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
        }

        val shareTrackBuilderClass: String? = imageViewerShareScan.shareTrackBuilderClass

        val shareTrackBuildUrlMethod: String? = imageViewerShareScan.shareTrackBuildUrlMethod

        val shareTrackAppendQueryMethod: String? = imageViewerShareScan.shareTrackAppendQueryMethod

        val imageViewerShareItemClass: String? = imageViewerShareScan.itemClass

        val imageViewerShareItemTitleField: String? = imageViewerShareScan.itemTitleField

        val imageViewerShareItemContentField: String? = imageViewerShareScan.itemContentField

        val imageViewerShareItemLinkUrlField: String? = imageViewerShareScan.itemLinkUrlField

        val imageViewerShareItemImageUriField: String? = imageViewerShareScan.itemImageUriField

        val imageViewerShareItemImageUrlField: String? = imageViewerShareScan.itemImageUrlField

        val imageViewerShareItemLocalFileField: String? = imageViewerShareScan.itemLocalFileField

        val imageViewerShareConfigClass: String? = imageViewerShareScan.configClass

        val imageViewerShareAddOutsideMethod: String? = imageViewerShareScan.addOutsideMethod

        val imageViewerShareGetRequestDataMethod: String? = imageViewerShareScan.getRequestDataMethod

        val imageViewerShareSetRequestDataMethod: String? = imageViewerShareScan.setRequestDataMethod

        val imageViewerShareGetContextMethod: String? = imageViewerShareScan.getContextMethod

        val imageViewerShareIsDialogField: String? = imageViewerShareScan.isDialogField

        val imageViewerShareItemField: String? = imageViewerShareScan.itemField

        val imageViewerShareItemViewClass: String? = imageViewerShareScan.itemViewClass

        val imageViewerShareItemNameByResMethod: String? = imageViewerShareScan.itemNameByResMethod

        val imageViewerShareItemNameByTextMethod: String? = imageViewerShareScan.itemNameByTextMethod

        val imageViewerShareIconResId: Int? = imageViewerShareScan.iconResId

        output[ImageSharingContract.shareTrackBuilderClass] = shareTrackBuilderClass
        output[ImageSharingContract.shareTrackBuildUrlMethod] = shareTrackBuildUrlMethod
        output[ImageSharingContract.shareTrackAppendQueryMethod] = shareTrackAppendQueryMethod
        output[ImageSharingContract.imageViewerShareConfigClass] = imageViewerShareConfigClass
        output[ImageSharingContract.imageViewerShareIsDialogField] = imageViewerShareIsDialogField
        output[ImageSharingContract.imageViewerShareItemField] = imageViewerShareItemField
        output[ImageSharingContract.imageViewerShareAddOutsideMethod] = imageViewerShareAddOutsideMethod
        output[ImageSharingContract.imageViewerShareGetRequestDataMethod] = imageViewerShareGetRequestDataMethod
        output[ImageSharingContract.imageViewerShareSetRequestDataMethod] = imageViewerShareSetRequestDataMethod
        output[ImageSharingContract.imageViewerShareGetContextMethod] = imageViewerShareGetContextMethod
        output[ImageSharingContract.imageViewerShareItemClass] = imageViewerShareItemClass
        output[ImageSharingContract.imageViewerShareItemTitleField] = imageViewerShareItemTitleField
        output[ImageSharingContract.imageViewerShareItemContentField] = imageViewerShareItemContentField
        output[ImageSharingContract.imageViewerShareItemLinkUrlField] = imageViewerShareItemLinkUrlField
        output[ImageSharingContract.imageViewerShareItemImageUriField] = imageViewerShareItemImageUriField
        output[ImageSharingContract.imageViewerShareItemImageUrlField] = imageViewerShareItemImageUrlField
        output[ImageSharingContract.imageViewerShareItemLocalFileField] = imageViewerShareItemLocalFileField
        output[ImageSharingContract.imageViewerShareItemViewClass] = imageViewerShareItemViewClass
        output[ImageSharingContract.imageViewerShareItemNameByResMethod] = imageViewerShareItemNameByResMethod
        output[ImageSharingContract.imageViewerShareItemNameByTextMethod] = imageViewerShareItemNameByTextMethod
        output[ImageSharingContract.imageViewerShareIconResId] = imageViewerShareIconResId
    }

    fun resolveShareTrackingParamCleanerSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ShareTrackingParamCleanerSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ShareTrackingParamCleanerHook] skipped: scan symbols unavailable")
                return null
            }
            val builderClassName = resolvedSymbols[ImageSharingContract.shareTrackBuilderClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[ShareTrackingParamCleanerHook] skipped: missing shareTrackBuilderClass")
                return null
            }
            val buildUrlMethodName = resolvedSymbols[ImageSharingContract.shareTrackBuildUrlMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[ShareTrackingParamCleanerHook] skipped: missing shareTrackBuildUrlMethod")
                return null
            }
            val builderClass = ScanReflection.safeFindClass(builderClassName, cl) ?: run {
                Diagnostics.log("[ShareTrackingParamCleanerHook] skipped: class not found: $builderClassName")
                return null
            }
            val buildUrlMethod = builderClass.declaredMethods.singleOrNull { method ->
                method.name == buildUrlMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            } ?: run {
                Diagnostics.log(
                    "[ShareTrackingParamCleanerHook] skipped: method mismatch: " +
                        "$builderClassName.$buildUrlMethodName(String,String,String,boolean)",
                )
                return null
            }
            buildUrlMethod.isAccessible = true
            ShareTrackingParamCleanerSymbols(buildUrlMethod = buildUrlMethod)
        } catch (t: Throwable) {
            Diagnostics.log("[ShareTrackingParamCleanerHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolveImageViewerNativeShareSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): ImageViewerNativeShareSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[ImageViewerNativeShareHook] skipped: scan symbols unavailable")
                return null
            }
            val configClassName = requiredImageViewerShareSymbol(
                "imageViewerShareConfigClass",
                resolvedSymbols[ImageSharingContract.imageViewerShareConfigClass],
            ) ?: return null
            val isDialogFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareIsDialogField",
                resolvedSymbols[ImageSharingContract.imageViewerShareIsDialogField],
            ) ?: return null
            val shareItemFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareItemField",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemField],
            ) ?: return null
            val addOutsideMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareAddOutsideMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareAddOutsideMethod],
            ) ?: return null
            val getRequestDataMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareGetRequestDataMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareGetRequestDataMethod],
            ) ?: return null
            val setRequestDataMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareSetRequestDataMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareSetRequestDataMethod],
            ) ?: return null
            val getContextMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareGetContextMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareGetContextMethod],
            ) ?: return null
            val shareItemClassName = requiredImageViewerShareSymbol(
                "imageViewerShareItemClass",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemClass],
            ) ?: return null
            val imageUriFieldName = requiredImageViewerShareSymbol(
                "imageViewerShareItemImageUriField",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemImageUriField],
            ) ?: return null
            val itemViewClassName = requiredImageViewerShareSymbol(
                "imageViewerShareItemViewClass",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemViewClass],
            ) ?: return null
            val itemNameByResMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareItemNameByResMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemNameByResMethod],
            ) ?: return null
            val itemNameByTextMethodName = requiredImageViewerShareSymbol(
                "imageViewerShareItemNameByTextMethod",
                resolvedSymbols[ImageSharingContract.imageViewerShareItemNameByTextMethod],
            ) ?: return null
            val shareIconResId = resolvedSymbols[ImageSharingContract.imageViewerShareIconResId]?.takeIf { it != 0 } ?: run {
                Diagnostics.log("[ImageViewerNativeShareHook] skipped: missing imageViewerShareIconResId")
                return null
            }

            val messageManagerClass = ScanReflection.safeFindClass(StableTiebaHookPoints.MESSAGE_MANAGER_CLASS, cl) ?: return null
            val messageClass = ScanReflection.safeFindClass(StableTiebaHookPoints.MESSAGE_CLASS, cl) ?: return null
            val customMessageClass = ScanReflection.safeFindClass(StableTiebaHookPoints.CUSTOM_MESSAGE_CLASS, cl) ?: return null
            val shareDialogConfigClass = ScanReflection.safeFindClass(configClassName, cl) ?: return null
            val shareItemClass = ScanReflection.safeFindClass(shareItemClassName, cl) ?: return null
            val shareDialogItemViewClass = ScanReflection.safeFindClass(itemViewClassName, cl) ?: return null
            val fileProviderClass = ScanReflection.safeFindClass(IMAGE_VIEWER_NATIVE_SHARE_FILE_PROVIDER_CLASS, cl) ?: return null

            val sendMessageMethod = MemberAccess.findMethodOrNull(
                messageManagerClass,
                StableTiebaHookPoints.METHOD_SEND_MESSAGE,
                messageClass,
            ) ?: return null
            val customMessageGetDataMethod =
                ReflectionUtils.findMethodInHierarchy(customMessageClass, StableTiebaHookPoints.METHOD_GET_DATA)
                    ?: return null
            val isImageViewerDialogField =
                findImageViewerShareField(shareDialogConfigClass, isDialogFieldName) ?: return null
            val shareItemField = findImageViewerShareField(shareDialogConfigClass, shareItemFieldName) ?: return null
            val addOutsideTextViewMethod = MemberAccess.findMethodOrNull(
                shareDialogConfigClass,
                addOutsideMethodName,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                View.OnClickListener::class.java,
            ) ?: return null
            val getRequestDataMethod = ReflectionUtils.findMethodInHierarchy(
                shareDialogConfigClass,
                getRequestDataMethodName,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && Map::class.java.isAssignableFrom(method.returnType)
            } ?: return null
            val setRequestDataMethod = MemberAccess.findMethodOrNull(
                shareDialogConfigClass,
                setRequestDataMethodName,
                Map::class.java,
            ) ?: return null
            val getContextMethod = ReflectionUtils.findMethodInHierarchy(
                shareDialogConfigClass,
                getContextMethodName,
            )?.takeIf { method ->
                method.parameterTypes.isEmpty() && Context::class.java.isAssignableFrom(method.returnType)
            } ?: return null
            val itemNameByResMethod = MemberAccess.findMethodOrNull(
                shareDialogItemViewClass,
                itemNameByResMethodName,
                Int::class.javaPrimitiveType!!,
            ) ?: return null
            val itemNameByTextMethod = MemberAccess.findMethodOrNull(
                shareDialogItemViewClass,
                itemNameByTextMethodName,
                String::class.java,
            ) ?: return null
            val fileProviderGetUriMethod = fileProviderClass.getDeclaredMethod(
                "getUriForFile",
                Context::class.java,
                String::class.java,
                File::class.java,
            )

            listOf(
                sendMessageMethod,
                customMessageGetDataMethod,
                addOutsideTextViewMethod,
                getRequestDataMethod,
                setRequestDataMethod,
                getContextMethod,
                itemNameByResMethod,
                itemNameByTextMethod,
                fileProviderGetUriMethod,
            ).forEach { it.isAccessible = true }

            ImageViewerNativeShareSymbols(
                customMessageClass = customMessageClass,
                shareDialogConfigClass = shareDialogConfigClass,
                sendMessageMethod = sendMessageMethod,
                customMessageGetDataMethod = customMessageGetDataMethod,
                isImageViewerDialogField = isImageViewerDialogField,
                shareItemField = shareItemField,
                addOutsideTextViewMethod = addOutsideTextViewMethod,
                getRequestDataMethod = getRequestDataMethod,
                setRequestDataMethod = setRequestDataMethod,
                getContextMethod = getContextMethod,
                shareItemTitleField = resolvedSymbols[ImageSharingContract.imageViewerShareItemTitleField]
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemContentField = resolvedSymbols[ImageSharingContract.imageViewerShareItemContentField]
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemLinkUrlField = resolvedSymbols[ImageSharingContract.imageViewerShareItemLinkUrlField]
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemImageUriField = findImageViewerShareField(shareItemClass, imageUriFieldName),
                shareItemImageUrlField = resolvedSymbols[ImageSharingContract.imageViewerShareItemImageUrlField]
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                shareItemLocalFileField = resolvedSymbols[ImageSharingContract.imageViewerShareItemLocalFileField]
                    ?.let { findImageViewerShareField(shareItemClass, it) },
                itemNameByResMethod = itemNameByResMethod,
                itemNameByTextMethod = itemNameByTextMethod,
                fileProviderGetUriMethod = fileProviderGetUriMethod,
                shareIconResId = shareIconResId,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[ImageViewerNativeShareHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun requiredImageViewerShareSymbol(name: String, value: String?): String? {
        return value?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[ImageViewerNativeShareHook] skipped: missing $name")
            null
        }
    }

    private fun findImageViewerShareField(clazz: Class<*>, fieldName: String): Field? {
        return runCatching {
            clazz.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull()
    }

    private const val IMAGE_VIEWER_NATIVE_SHARE_FILE_PROVIDER_CLASS = "androidx.core.content.FileProvider"

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val shareTrackingCritical = ArrayList<String>(2)
        if (symbols[ImageSharingContract.shareTrackBuilderClass].isNullOrBlank()) shareTrackingCritical.add("shareTrackBuilderClass")
        if (symbols[ImageSharingContract.shareTrackBuildUrlMethod].isNullOrBlank()) shareTrackingCritical.add("shareTrackBuildUrlMethod")
        out[HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS] = if (shareTrackingCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(state = HookFeatureState.DISABLED, missingCritical = shareTrackingCritical)
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "ShareTrackingParamCleanerHook",
            "${symbols[ImageSharingContract.shareTrackBuilderClass]}.${symbols[ImageSharingContract.shareTrackBuildUrlMethod]}",
            listOf(
                ImageSharingContract.shareTrackBuilderClass.check(symbols),
                ImageSharingContract.shareTrackBuildUrlMethod.check(symbols),
            ),
        )
        add(
            "ShareTrackingParamCleanerHook.AppendQuery",
            "${symbols[ImageSharingContract.shareTrackBuilderClass]}.${symbols[ImageSharingContract.shareTrackAppendQueryMethod]}",
            listOf(ImageSharingContract.shareTrackAppendQueryMethod.check(symbols)),
        )
        add(
            "ImageViewerNativeShareHook",
            "${symbols[ImageSharingContract.imageViewerShareConfigClass]}.${symbols[ImageSharingContract.imageViewerShareAddOutsideMethod]}",
            listOf(
                ImageSharingContract.imageViewerShareConfigClass.check(symbols),
                ImageSharingContract.imageViewerShareIsDialogField.check(symbols),
                ImageSharingContract.imageViewerShareItemField.check(symbols),
                ImageSharingContract.imageViewerShareAddOutsideMethod.check(symbols),
                ImageSharingContract.imageViewerShareGetRequestDataMethod.check(symbols),
                ImageSharingContract.imageViewerShareSetRequestDataMethod.check(symbols),
                ImageSharingContract.imageViewerShareGetContextMethod.check(symbols),
                ImageSharingContract.imageViewerShareItemClass.check(symbols),
                ImageSharingContract.imageViewerShareItemImageUriField.check(symbols),
                ImageSharingContract.imageViewerShareItemViewClass.check(symbols),
                ImageSharingContract.imageViewerShareItemNameByResMethod.check(symbols),
                ImageSharingContract.imageViewerShareItemNameByTextMethod.check(symbols),
                ImageSharingContract.imageViewerShareIconResId.check(symbols),
            ),
        )
        add(
            "ImageViewerNativeShareHook.ItemTextFields",
            "${symbols[ImageSharingContract.imageViewerShareItemClass]}[${symbols[ImageSharingContract.imageViewerShareItemTitleField]},${symbols[ImageSharingContract.imageViewerShareItemContentField]},${symbols[ImageSharingContract.imageViewerShareItemLinkUrlField]},${symbols[ImageSharingContract.imageViewerShareItemImageUrlField]},${symbols[ImageSharingContract.imageViewerShareItemLocalFileField]}]",
            listOf(
                ImageSharingContract.imageViewerShareItemTitleField.check(symbols),
                ImageSharingContract.imageViewerShareItemContentField.check(symbols),
                ImageSharingContract.imageViewerShareItemLinkUrlField.check(symbols),
                ImageSharingContract.imageViewerShareItemImageUrlField.check(symbols),
                ImageSharingContract.imageViewerShareItemLocalFileField.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("ShareTrackingParamCleanerHook", true, listOf(HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasShareTrackSymbols =
            symbols[ImageSharingContract.shareTrackBuilderClass] != null ||
                symbols[ImageSharingContract.shareTrackBuildUrlMethod] != null ||
                symbols[ImageSharingContract.shareTrackAppendQueryMethod] != null
        if (hasShareTrackSymbols && !isShareTrackValid(symbols, cl)) return false
        val hasCompleteImageViewerShareSymbols =
            symbols[ImageSharingContract.imageViewerShareConfigClass] != null &&
                symbols[ImageSharingContract.imageViewerShareIsDialogField] != null &&
                symbols[ImageSharingContract.imageViewerShareItemField] != null &&
                symbols[ImageSharingContract.imageViewerShareAddOutsideMethod] != null &&
                symbols[ImageSharingContract.imageViewerShareGetRequestDataMethod] != null &&
                symbols[ImageSharingContract.imageViewerShareSetRequestDataMethod] != null &&
                symbols[ImageSharingContract.imageViewerShareGetContextMethod] != null &&
                symbols[ImageSharingContract.imageViewerShareItemClass] != null &&
                symbols[ImageSharingContract.imageViewerShareItemImageUriField] != null &&
                symbols[ImageSharingContract.imageViewerShareItemViewClass] != null &&
                symbols[ImageSharingContract.imageViewerShareItemNameByResMethod] != null &&
                symbols[ImageSharingContract.imageViewerShareItemNameByTextMethod] != null
        if (hasCompleteImageViewerShareSymbols && !isImageViewerShareValid(symbols, cl)) return false
        return true
    }

    private fun isShareTrackValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[ImageSharingContract.shareTrackBuilderClass] ?: return false
        val buildUrlMethodName = symbols[ImageSharingContract.shareTrackBuildUrlMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            val hasBuildUrl = targetClass.declaredMethods.any { method ->
                method.name == buildUrlMethodName &&
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            if (!hasBuildUrl) return false
            val appendMethodName = symbols[ImageSharingContract.shareTrackAppendQueryMethod]
            if (!appendMethodName.isNullOrBlank()) {
                val hasAppend = targetClass.declaredMethods.any { method ->
                    method.name == appendMethodName &&
                        java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == String::class.java
                }
                if (!hasAppend) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isImageViewerShareValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val configClassName = symbols[ImageSharingContract.imageViewerShareConfigClass] ?: return false
        val isDialogFieldName = symbols[ImageSharingContract.imageViewerShareIsDialogField] ?: return false
        val shareItemFieldName = symbols[ImageSharingContract.imageViewerShareItemField] ?: return false
        val addOutsideMethodName = symbols[ImageSharingContract.imageViewerShareAddOutsideMethod] ?: return false
        val getRequestDataMethodName = symbols[ImageSharingContract.imageViewerShareGetRequestDataMethod] ?: return false
        val setRequestDataMethodName = symbols[ImageSharingContract.imageViewerShareSetRequestDataMethod] ?: return false
        val getContextMethodName = symbols[ImageSharingContract.imageViewerShareGetContextMethod] ?: return false
        val shareItemClassName = symbols[ImageSharingContract.imageViewerShareItemClass] ?: return false
        val shareItemImageUriFieldName = symbols[ImageSharingContract.imageViewerShareItemImageUriField] ?: return false
        val itemViewClassName = symbols[ImageSharingContract.imageViewerShareItemViewClass] ?: return false
        val itemNameByResMethodName = symbols[ImageSharingContract.imageViewerShareItemNameByResMethod] ?: return false
        val itemNameByTextMethodName = symbols[ImageSharingContract.imageViewerShareItemNameByTextMethod] ?: return false
        return try {
            val configClass = ScanReflection.safeFindClass(configClassName, cl) ?: return false
            val shareItemClass = ScanReflection.safeFindClass(shareItemClassName, cl) ?: return false
            val itemViewClass = ScanReflection.safeFindClass(itemViewClassName, cl) ?: return false
            val iconResId = symbols[ImageSharingContract.imageViewerShareIconResId]
            if (iconResId != null) {
                if (!isDrawableResId(iconResId)) return false
            }

            val configFields = ScanReflection.collectInstanceFields(configClass)
            val hasDialogField = configFields.any { field ->
                field.name == isDialogFieldName && field.type == Boolean::class.javaPrimitiveType
            }
            if (!hasDialogField) return false
            val hasShareItemField = configFields.any { field ->
                field.name == shareItemFieldName && field.type.name == shareItemClassName
            }
            if (!hasShareItemField) return false
            val hasAddOutside = configClass.declaredMethods.any { method ->
                method.name == addOutsideMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == View.OnClickListener::class.java
            }
            if (!hasAddOutside) return false
            val hasGetRequestData = configClass.declaredMethods.any { method ->
                method.name == getRequestDataMethodName &&
                    method.parameterTypes.isEmpty() &&
                    Map::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasGetRequestData) return false
            val hasSetRequestData = configClass.declaredMethods.any { method ->
                method.name == setRequestDataMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    Map::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            if (!hasSetRequestData) return false
            val hasGetContext = (configClass.declaredMethods + configClass.methods).distinctBy { method ->
                "${method.name}#${method.parameterTypes.joinToString(",") { it.name }}#${method.returnType.name}"
            }.any { method ->
                method.name == getContextMethodName &&
                    method.parameterTypes.isEmpty() &&
                    Context::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasGetContext) return false

            val shareItemFields = ScanReflection.collectInstanceFields(shareItemClass)
            val hasImageUriField = shareItemFields.any { field ->
                field.name == shareItemImageUriFieldName && field.type == android.net.Uri::class.java
            }
            if (!hasImageUriField) return false

            val localFileFieldName = symbols[ImageSharingContract.imageViewerShareItemLocalFileField]
            if (!localFileFieldName.isNullOrBlank()) {
                val hasLocalFileField = shareItemFields.any { field ->
                    field.name == localFileFieldName && field.type == String::class.java
                }
                if (!hasLocalFileField) return false
            }
            val imageUrlFieldName = symbols[ImageSharingContract.imageViewerShareItemImageUrlField]
            if (!imageUrlFieldName.isNullOrBlank()) {
                val hasImageUrlField = shareItemFields.any { field ->
                    field.name == imageUrlFieldName && field.type == String::class.java
                }
                if (!hasImageUrlField) return false
            }
            val titleFieldName = symbols[ImageSharingContract.imageViewerShareItemTitleField]
            if (!titleFieldName.isNullOrBlank()) {
                val hasTitleField = shareItemFields.any { field ->
                    field.name == titleFieldName && field.type == String::class.java
                }
                if (!hasTitleField) return false
            }
            val contentFieldName = symbols[ImageSharingContract.imageViewerShareItemContentField]
            if (!contentFieldName.isNullOrBlank()) {
                val hasContentField = shareItemFields.any { field ->
                    field.name == contentFieldName && field.type == String::class.java
                }
                if (!hasContentField) return false
            }
            val linkFieldName = symbols[ImageSharingContract.imageViewerShareItemLinkUrlField]
            if (!linkFieldName.isNullOrBlank()) {
                val hasLinkField = shareItemFields.any { field ->
                    field.name == linkFieldName && field.type == String::class.java
                }
                if (!hasLinkField) return false
            }

            val hasItemNameByRes = itemViewClass.declaredMethods.any { method ->
                method.name == itemNameByResMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }
            if (!hasItemNameByRes) return false
            itemViewClass.declaredMethods.any { method ->
                method.name == itemNameByTextMethodName &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isDrawableResId(value: Int): Boolean {
        if ((value ushr 24) != 0x7F) return false
        return ((value ushr 16) and 0xFF) == 0x08
    }
}
