package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.resolveCachedParameterClass
import com.forbidad4tieba.hook.symbol.contract.RestoredMembers.resolveMethodByCachedSpec
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.hasList
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.FreeCopyLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyNativeScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyNativeSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyPopupScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyPopupSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyTitleLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyWebViewLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.FreeCopyNativeSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.FreeCopyPopupSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object FreeCopyContract : SymbolContract("FreeCopy") {
    val freeCopyPopupMenuClass = text("freeCopyPopupMenuClass")
    val freeCopyPopupContentViewMethod = text("freeCopyPopupContentViewMethod")
    val freeCopyPopupTextField = text("freeCopyPopupTextField")
    val freeCopyPostDataClass = text("freeCopyPostDataClass")
    val freeCopyPostCopyMethodSpec = text("freeCopyPostCopyMethodSpec")
    val freeCopyPostParseMethodSpec = text("freeCopyPostParseMethodSpec")
    val freeCopySubPostParseMethodSpec = text("freeCopySubPostParseMethodSpec")
    val freeCopyPostFloorMethodSpec = text("freeCopyPostFloorMethodSpec")
    val freeCopyRichTextViewClass = text("freeCopyRichTextViewClass")
    val freeCopyPostLongPressMethodSpecs = texts("freeCopyPostLongPressMethodSpecs", preserveEmpty = false)
    val freeCopyTitleBindMethodSpecs = texts("freeCopyTitleBindMethodSpecs", preserveEmpty = false)
    val freeCopyTitleContainerField = text("freeCopyTitleContainerField")
    val freeCopyTitleTextField = text("freeCopyTitleTextField")
    val freeCopyTitlePostDataMethodSpec = text("freeCopyTitlePostDataMethodSpec")
    val freeCopyWebViewBindMethodSpec = text("freeCopyWebViewBindMethodSpec")
    val freeCopyWebViewGetterMethodSpec = text("freeCopyWebViewGetterMethodSpec")
    val freeCopyInnerWebViewGetterMethodSpec = text("freeCopyInnerWebViewGetterMethodSpec")
    val freeCopyWebViewPageDataGetterMethodSpec = text("freeCopyWebViewPageDataGetterMethodSpec")
    val freeCopyWebViewFirstFloorPostGetterMethodSpec = text("freeCopyWebViewFirstFloorPostGetterMethodSpec")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        var freeCopyPostLongPressMethodSpecs: List<String>? = null

        var freeCopyTitleBindMethodSpecs: List<String>? = null

        var freeCopyInnerWebViewGetterMethodSpec: String? = null

        var freeCopyWebViewPageDataGetterMethodSpec: String? = null

        var freeCopyWebViewFirstFloorPostGetterMethodSpec: String? = null

        val freeCopyPopupScan = runScanStep(
            "FreeCopyHook.Popup",
            logger,
            scanErrors,
            FreeCopyPopupScanSymbols(),
        ) {
            FreeCopyPopupSymbolScanner.scan(candidatesWithWhitelist, cl, logger)
        }

        val freeCopyPopupMenuClass: String? = freeCopyPopupScan.menuClass

        val freeCopyPopupContentViewMethod: String? = freeCopyPopupScan.contentViewMethod

        val freeCopyPopupTextField: String? = freeCopyPopupScan.textField

        val freeCopyNativeScan = runScanStep(
            "FreeCopyHook.Native",
            logger,
            scanErrors,
            FreeCopyNativeScanSymbols(),
        ) {
            FreeCopyNativeSymbolScanner.scanNative(context, cl, logger)
        }

        val freeCopyPostDataClass: String? = freeCopyNativeScan.postDataClass

        val freeCopyPostCopyMethodSpec: String? = freeCopyNativeScan.copyMethodSpec

        val freeCopyPostParseMethodSpec: String? = freeCopyNativeScan.postParseMethodSpec

        val freeCopySubPostParseMethodSpec: String? = freeCopyNativeScan.subPostParseMethodSpec

        val freeCopyLongPressScan = runScanStep(
            "FreeCopyHook.LongPress",
            logger,
            scanErrors,
            FreeCopyLongPressScanSymbols(),
        ) {
            FreeCopyNativeSymbolScanner.scanLongPress(context, cl, logger)
        }

        val freeCopyRichTextViewClass: String? = freeCopyLongPressScan.richTextViewClass

        freeCopyPostLongPressMethodSpecs =
            freeCopyLongPressScan.methodSpecs.takeIf { it.isNotEmpty() }

        val freeCopyPostFloorMethodSpec: String? = freeCopyLongPressScan.postFloorMethodSpec

        val freeCopyTitleLongPressScan = runScanStep(
            "FreeCopyHook.TitleLongPress",
            logger,
            scanErrors,
            FreeCopyTitleLongPressScanSymbols(),
        ) {
            FreeCopyNativeSymbolScanner.scanTitleLongPress(
                context = context,
                cl = cl,
                postFloorMethodSpec = freeCopyPostFloorMethodSpec,
                logger = logger,
            )
        }

        freeCopyTitleBindMethodSpecs =
            freeCopyTitleLongPressScan.bindMethodSpecs.takeIf { it.isNotEmpty() }

        val freeCopyTitleContainerField: String? = freeCopyTitleLongPressScan.containerField

        val freeCopyTitleTextField: String? = freeCopyTitleLongPressScan.textField

        val freeCopyTitlePostDataMethodSpec: String? = freeCopyTitleLongPressScan.postDataMethodSpec

        val freeCopyWebViewLongPressScan = runScanStep(
            "FreeCopyHook.WebViewLongPress",
            logger,
            scanErrors,
            FreeCopyWebViewLongPressScanSymbols(),
        ) {
            FreeCopyNativeSymbolScanner.scanWebViewLongPress(
                context = context,
                cl = cl,
                postFloorMethodSpec = freeCopyPostFloorMethodSpec,
                logger = logger,
            )
        }

        val freeCopyWebViewBindMethodSpec: String? = freeCopyWebViewLongPressScan.bindMethodSpec

        val freeCopyWebViewGetterMethodSpec: String? = freeCopyWebViewLongPressScan.webViewGetterMethodSpec

        freeCopyInnerWebViewGetterMethodSpec =
            freeCopyWebViewLongPressScan.innerWebViewGetterMethodSpec

        freeCopyWebViewPageDataGetterMethodSpec =
            freeCopyWebViewLongPressScan.pageDataGetterMethodSpec

        freeCopyWebViewFirstFloorPostGetterMethodSpec =
            freeCopyWebViewLongPressScan.firstFloorPostGetterMethodSpec

        output[FreeCopyContract.freeCopyPopupMenuClass] = freeCopyPopupMenuClass
        output[FreeCopyContract.freeCopyPopupContentViewMethod] = freeCopyPopupContentViewMethod
        output[FreeCopyContract.freeCopyPopupTextField] = freeCopyPopupTextField
        output[FreeCopyContract.freeCopyPostDataClass] = freeCopyPostDataClass
        output[FreeCopyContract.freeCopyPostCopyMethodSpec] = freeCopyPostCopyMethodSpec
        output[FreeCopyContract.freeCopyPostParseMethodSpec] = freeCopyPostParseMethodSpec
        output[FreeCopyContract.freeCopySubPostParseMethodSpec] = freeCopySubPostParseMethodSpec
        output[FreeCopyContract.freeCopyPostFloorMethodSpec] = freeCopyPostFloorMethodSpec
        output[FreeCopyContract.freeCopyRichTextViewClass] = freeCopyRichTextViewClass
        output[FreeCopyContract.freeCopyPostLongPressMethodSpecs] = freeCopyPostLongPressMethodSpecs
        output[FreeCopyContract.freeCopyTitleBindMethodSpecs] = freeCopyTitleBindMethodSpecs
        output[FreeCopyContract.freeCopyTitleContainerField] = freeCopyTitleContainerField
        output[FreeCopyContract.freeCopyTitleTextField] = freeCopyTitleTextField
        output[FreeCopyContract.freeCopyTitlePostDataMethodSpec] = freeCopyTitlePostDataMethodSpec
        output[FreeCopyContract.freeCopyWebViewBindMethodSpec] = freeCopyWebViewBindMethodSpec
        output[FreeCopyContract.freeCopyWebViewGetterMethodSpec] = freeCopyWebViewGetterMethodSpec
        output[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec] = freeCopyInnerWebViewGetterMethodSpec
        output[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec] = freeCopyWebViewPageDataGetterMethodSpec
        output[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec] = freeCopyWebViewFirstFloorPostGetterMethodSpec
    }

    private data class FreeCopyTitleResolvedSymbols(
        val bindMethods: List<Method> = emptyList(),
        val containerField: Field? = null,
        val textField: Field? = null,
        val postDataMethod: Method? = null,
    )

    private data class FreeCopyWebViewResolvedSymbols(
        val bindMethod: Method? = null,
        val webViewGetterMethod: Method? = null,
        val innerWebViewGetterMethod: Method? = null,
        val pageDataGetterMethod: Method? = null,
        val firstFloorPostGetterMethod: Method? = null,
    )

    fun resolveFreeCopyPopupSymbols(cl: ClassLoader, symbols: HookSymbols?): FreeCopyPopupSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: scan symbols unavailable")
                return null
            }
            val menuClassName = resolvedSymbols[FreeCopyContract.freeCopyPopupMenuClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: missing freeCopyPopupMenuClass")
                return null
            }
            val contentViewMethodName = resolvedSymbols[FreeCopyContract.freeCopyPopupContentViewMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: missing freeCopyPopupContentViewMethod")
                return null
            }
            val textFieldName = resolvedSymbols[FreeCopyContract.freeCopyPopupTextField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: missing freeCopyPopupTextField")
                return null
            }

            val menuClass = ScanReflection.safeFindClass(menuClassName, cl) ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: class not found: $menuClassName")
                return null
            }
            val roundLayoutClass = ScanReflection.safeFindClass(FREE_COPY_POPUP_ROUND_LAYOUT_CLASS, cl) ?: return null
            val emTextViewClass = ScanReflection.safeFindClass(FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS, cl) ?: return null

            val hasRoundLayoutField = menuClass.declaredFields.any {
                roundLayoutClass.isAssignableFrom(it.type)
            }
            if (!hasRoundLayoutField) {
                Diagnostics.log("[FreeCopyHook] popup skipped: $menuClassName structure mismatch")
                return null
            }

            val textField = menuClass.declaredFields.singleOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == textFieldName &&
                    (emTextViewClass.isAssignableFrom(it.type) || TextView::class.java.isAssignableFrom(it.type))
            } ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: text field mismatch: $textFieldName")
                return null
            }

            val contentViewMethod = menuClass.declaredMethods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == contentViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    View::class.java.isAssignableFrom(method.returnType)
            } ?: run {
                Diagnostics.log("[FreeCopyHook] popup skipped: content view method mismatch: $contentViewMethodName")
                return null
            }

            textField.isAccessible = true
            contentViewMethod.isAccessible = true
            FreeCopyPopupSymbols(
                menuClass = menuClass,
                contentViewMethod = contentViewMethod,
                textField = textField,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[FreeCopyHook] popup symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolveFreeCopyNativeSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): FreeCopyNativeSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[FreeCopyHook] native skipped: scan symbols unavailable")
                return null
            }
            val postDataClassName = resolvedSymbols[FreeCopyContract.freeCopyPostDataClass]
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log("[FreeCopyHook] native skipped: missing freeCopyPostDataClass")
                    return null
                }
            val copyMethodSpec = resolvedSymbols[FreeCopyContract.freeCopyPostCopyMethodSpec]
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    Diagnostics.log("[FreeCopyHook] native skipped: missing freeCopyPostCopyMethodSpec")
                    return null
                }
            val postDataClass = ScanReflection.safeFindClass(postDataClassName, cl) ?: run {
                Diagnostics.log("[FreeCopyHook] native skipped: class not found: $postDataClassName")
                return null
            }
            val copyMethod = resolveMethodByCachedSpec(
                postDataClass,
                copyMethodSpec,
                copyMethodSpec.substringBefore('|'),
            ) ?: run {
                Diagnostics.log("[FreeCopyHook] native skipped: copy method mismatch: $copyMethodSpec")
                return null
            }

            fun resolveOptionalPostDataMethod(spec: String?): Method? {
                val value = spec?.takeIf { it.isNotBlank() } ?: return null
                return resolveMethodByCachedSpec(
                    postDataClass,
                    value,
                    value.substringBefore('|'),
                )
            }

            fun resolveMetadataFields(method: Method?): Pair<Field?, Field?> {
                val protocolClass = method?.parameterTypes?.firstOrNull() ?: return null to null
                val titleField = protocolClass.getDeclaredField("title").takeIf {
                    it.type == String::class.java
                } ?: return null to null
                val floorField = protocolClass.getDeclaredField("floor").takeIf {
                    Number::class.java.isAssignableFrom(it.type)
                } ?: return null to null
                titleField.isAccessible = true
                floorField.isAccessible = true
                return titleField to floorField
            }

            val rawPostParseMethod = resolveOptionalPostDataMethod(
                resolvedSymbols[FreeCopyContract.freeCopyPostParseMethodSpec],
            )
            val rawSubPostParseMethod = resolveOptionalPostDataMethod(
                resolvedSymbols[FreeCopyContract.freeCopySubPostParseMethodSpec],
            )
            val postFloorMethod = resolveOptionalPostDataMethod(
                resolvedSymbols[FreeCopyContract.freeCopyPostFloorMethodSpec],
            )?.takeIf { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterTypes.isEmpty()
            }
            if (
                !resolvedSymbols[FreeCopyContract.freeCopyPostFloorMethodSpec].isNullOrBlank() &&
                postFloorMethod == null
            ) {
                Diagnostics.log(
                    "[FreeCopyHook] floor method mismatch: " +
                        resolvedSymbols[FreeCopyContract.freeCopyPostFloorMethodSpec],
                )
            }
            val postFields = resolveMetadataFields(rawPostParseMethod)
            val subPostFields = resolveMetadataFields(rawSubPostParseMethod)
            val postParseMethod = rawPostParseMethod.takeIf {
                postFields.first != null && postFields.second != null
            }
            val subPostParseMethod = rawSubPostParseMethod.takeIf {
                subPostFields.first != null && subPostFields.second != null
            }
            if (postParseMethod == null && subPostParseMethod == null) {
                Diagnostics.log("[FreeCopyHook] native skipped: no validated metadata parser")
                return null
            }

            val longPressMethods = resolvedSymbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs].orEmpty()
                .mapNotNull { fullSpec ->
                    val separator = fullSpec.indexOf('#')
                    if (separator <= 0 || separator >= fullSpec.lastIndex) {
                        Diagnostics.log("[FreeCopyHook] long press spec invalid: $fullSpec")
                        return@mapNotNull null
                    }
                    val ownerClassName = fullSpec.substring(0, separator)
                    val methodSpec = fullSpec.substring(separator + 1)
                    val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: run {
                        Diagnostics.log("[FreeCopyHook] long press owner missing: $ownerClassName")
                        return@mapNotNull null
                    }
                    resolveMethodByCachedSpec(
                        ownerClass,
                        methodSpec,
                        methodSpec.substringBefore('|'),
                    )?.takeIf { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Boolean::class.javaPrimitiveType &&
                            method.parameterTypes.firstOrNull()?.let { parameterType ->
                                View::class.java.isAssignableFrom(parameterType)
                            } == true
                    } ?: run {
                        Diagnostics.log("[FreeCopyHook] long press method mismatch: $fullSpec")
                        null
                    }
                }
            val richTextViewClass = resolvedSymbols[FreeCopyContract.freeCopyRichTextViewClass]
                ?.takeIf { it.isNotBlank() }
                ?.let { ScanReflection.safeFindClass(it, cl) }

            val titleSymbols = run title@ {
                val bindSpecs = resolvedSymbols[FreeCopyContract.freeCopyTitleBindMethodSpecs].orEmpty()
                val containerFieldName = resolvedSymbols[FreeCopyContract.freeCopyTitleContainerField]
                    ?.takeIf { it.isNotBlank() }
                val textFieldName = resolvedSymbols[FreeCopyContract.freeCopyTitleTextField]
                    ?.takeIf { it.isNotBlank() }
                val postDataMethodSpec = resolvedSymbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec]
                    ?.takeIf { it.isNotBlank() }
                val hasAny = bindSpecs.isNotEmpty() ||
                    containerFieldName != null ||
                    textFieldName != null ||
                    postDataMethodSpec != null
                if (!hasAny) {
                    return@title FreeCopyTitleResolvedSymbols()
                }
                if (
                    bindSpecs.isEmpty() ||
                    containerFieldName == null ||
                    textFieldName == null ||
                    postDataMethodSpec == null
                ) {
                    Diagnostics.log("[FreeCopyHook] title long press skipped: incomplete symbols")
                    return@title FreeCopyTitleResolvedSymbols()
                }
                val bindMethods = bindSpecs.mapNotNull { fullSpec ->
                    val separator = fullSpec.indexOf('#')
                    if (separator <= 0 || separator >= fullSpec.lastIndex) {
                        Diagnostics.log("[FreeCopyHook] title bind spec invalid: $fullSpec")
                        return@mapNotNull null
                    }
                    val ownerClassName = fullSpec.substring(0, separator)
                    val methodSpec = fullSpec.substring(separator + 1)
                    val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: run {
                        Diagnostics.log("[FreeCopyHook] title bind owner missing: $ownerClassName")
                        return@mapNotNull null
                    }
                    resolveMethodByCachedSpec(
                        ownerClass,
                        methodSpec,
                        methodSpec.substringBefore('|'),
                    )?.takeIf { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Void.TYPE &&
                            method.parameterTypes.size == 1
                    } ?: run {
                        Diagnostics.log("[FreeCopyHook] title bind method mismatch: $fullSpec")
                        null
                    }
                }.distinct()
                if (bindMethods.size != bindSpecs.distinct().size || bindMethods.isEmpty()) {
                    return@title FreeCopyTitleResolvedSymbols()
                }
                val ownerClass = bindMethods.first().declaringClass
                val pageDataClass = bindMethods.first().parameterTypes.single()
                if (bindMethods.any { method ->
                        method.declaringClass != ownerClass ||
                            method.parameterTypes.single() != pageDataClass
                    }
                ) {
                    Diagnostics.log("[FreeCopyHook] title bind methods do not share one owner/data type")
                    return@title FreeCopyTitleResolvedSymbols()
                }
                val containerField = try {
                    ownerClass.getDeclaredField(containerFieldName).takeIf { field ->
                        ViewGroup::class.java.isAssignableFrom(field.type)
                    }
                } catch (_: NoSuchFieldException) {
                    null
                } ?: run {
                    Diagnostics.log(
                        "[FreeCopyHook] title container field mismatch: $containerFieldName",
                    )
                    return@title FreeCopyTitleResolvedSymbols()
                }
                val textField = try {
                    ownerClass.getDeclaredField(textFieldName).takeIf { field ->
                        TextView::class.java.isAssignableFrom(field.type)
                    }
                } catch (_: NoSuchFieldException) {
                    null
                } ?: run {
                    Diagnostics.log("[FreeCopyHook] title TextView field mismatch: $textFieldName")
                    return@title FreeCopyTitleResolvedSymbols()
                }
                val postDataMethod = resolveMethodByCachedSpec(
                    pageDataClass,
                    postDataMethodSpec,
                    postDataMethodSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == postDataClass &&
                        method.parameterTypes.isEmpty()
                } ?: run {
                    Diagnostics.log(
                        "[FreeCopyHook] title PostData method mismatch: $postDataMethodSpec",
                    )
                    return@title FreeCopyTitleResolvedSymbols()
                }
                FreeCopyTitleResolvedSymbols(
                    bindMethods = bindMethods,
                    containerField = containerField,
                    textField = textField,
                    postDataMethod = postDataMethod,
                )
            }

            val webViewSymbols = run webView@ {
                val bindSpec = resolvedSymbols[FreeCopyContract.freeCopyWebViewBindMethodSpec]
                    ?.takeIf { it.isNotBlank() }
                val webViewGetterSpec = resolvedSymbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec]
                    ?.takeIf { it.isNotBlank() }
                val innerWebViewGetterSpec =
                    resolvedSymbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec]
                        ?.takeIf { it.isNotBlank() }
                val pageDataGetterSpec =
                    resolvedSymbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec]
                        ?.takeIf { it.isNotBlank() }
                val firstFloorPostGetterSpec =
                    resolvedSymbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec]
                        ?.takeIf { it.isNotBlank() }
                val hasAny = bindSpec != null || webViewGetterSpec != null ||
                    innerWebViewGetterSpec != null ||
                    pageDataGetterSpec != null || firstFloorPostGetterSpec != null
                if (!hasAny) return@webView FreeCopyWebViewResolvedSymbols()
                if (
                    bindSpec == null || webViewGetterSpec == null ||
                    innerWebViewGetterSpec == null ||
                    pageDataGetterSpec == null || firstFloorPostGetterSpec == null
                ) {
                    Diagnostics.log(
                        "[FreeCopyHook] WebView long press skipped: incomplete symbols",
                    )
                    return@webView FreeCopyWebViewResolvedSymbols()
                }
                val ownerClass = ScanReflection.safeFindClass(
                    StableTiebaHookPoints.PB_COMMON_WEB_VIEW_CLASS,
                    cl,
                ) ?: return@webView FreeCopyWebViewResolvedSymbols()
                val bindMethod = resolveMethodByCachedSpec(
                    ownerClass,
                    bindSpec,
                    bindSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1
                } ?: return@webView FreeCopyWebViewResolvedSymbols()
                val webViewGetterMethod = resolveMethodByCachedSpec(
                    ownerClass,
                    webViewGetterSpec,
                    webViewGetterSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType != Void.TYPE
                } ?: return@webView FreeCopyWebViewResolvedSymbols()
                val innerWebViewGetterMethod = resolveMethodByCachedSpec(
                    webViewGetterMethod.returnType,
                    innerWebViewGetterSpec,
                    innerWebViewGetterSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        WebView::class.java.isAssignableFrom(method.returnType)
                } ?: return@webView FreeCopyWebViewResolvedSymbols()
                val pageDataClass = bindMethod.parameterTypes.single()
                val pageDataGetterMethod = resolveMethodByCachedSpec(
                    pageDataClass,
                    pageDataGetterSpec,
                    pageDataGetterSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType != Void.TYPE
                } ?: return@webView FreeCopyWebViewResolvedSymbols()
                val firstFloorPostGetterMethod = resolveMethodByCachedSpec(
                    pageDataGetterMethod.returnType,
                    firstFloorPostGetterSpec,
                    firstFloorPostGetterSpec.substringBefore('|'),
                )?.takeIf { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == postDataClass
                } ?: return@webView FreeCopyWebViewResolvedSymbols()
                FreeCopyWebViewResolvedSymbols(
                    bindMethod = bindMethod,
                    webViewGetterMethod = webViewGetterMethod,
                    innerWebViewGetterMethod = innerWebViewGetterMethod,
                    pageDataGetterMethod = pageDataGetterMethod,
                    firstFloorPostGetterMethod = firstFloorPostGetterMethod,
                )
            }

            fun frameworkMethodOrNull(
                clazz: Class<*>,
                name: String,
                vararg parameterTypes: Class<*>,
            ): Method? {
                return try {
                    clazz.getMethod(name, *parameterTypes)
                } catch (_: NoSuchMethodException) {
                    null
                }
            }

            val clipboardWriteMethods = listOfNotNull(
                frameworkMethodOrNull(
                    android.content.ClipboardManager::class.java,
                    "setText",
                    CharSequence::class.java,
                ),
                frameworkMethodOrNull(
                    android.content.ClipboardManager::class.java,
                    "setPrimaryClip",
                    android.content.ClipData::class.java,
                ),
            ).distinct()
            if (clipboardWriteMethods.isEmpty()) {
                Diagnostics.log("[FreeCopyHook] native skipped: clipboard write methods unavailable")
                return null
            }

            copyMethod.isAccessible = true
            postParseMethod?.isAccessible = true
            subPostParseMethod?.isAccessible = true
            postFloorMethod?.isAccessible = true
            longPressMethods.forEach { it.isAccessible = true }
            titleSymbols.bindMethods.forEach { it.isAccessible = true }
            titleSymbols.containerField?.isAccessible = true
            titleSymbols.textField?.isAccessible = true
            titleSymbols.postDataMethod?.isAccessible = true
            webViewSymbols.bindMethod?.isAccessible = true
            webViewSymbols.webViewGetterMethod?.isAccessible = true
            webViewSymbols.innerWebViewGetterMethod?.isAccessible = true
            webViewSymbols.pageDataGetterMethod?.isAccessible = true
            webViewSymbols.firstFloorPostGetterMethod?.isAccessible = true
            clipboardWriteMethods.forEach { it.isAccessible = true }
            FreeCopyNativeSymbols(
                postDataClass = postDataClass,
                copyMethod = copyMethod,
                postParseMethod = postParseMethod,
                subPostParseMethod = subPostParseMethod,
                postTitleField = postFields.first.takeIf { postParseMethod != null },
                postFloorField = postFields.second.takeIf { postParseMethod != null },
                subPostTitleField = subPostFields.first.takeIf { subPostParseMethod != null },
                subPostFloorField = subPostFields.second.takeIf { subPostParseMethod != null },
                postFloorMethod = postFloorMethod,
                richTextViewClass = richTextViewClass,
                longPressMethods = longPressMethods,
                titleBindMethods = titleSymbols.bindMethods,
                titleContainerField = titleSymbols.containerField,
                titleTextField = titleSymbols.textField,
                titlePostDataMethod = titleSymbols.postDataMethod,
                webViewBindMethod = webViewSymbols.bindMethod,
                webViewGetterMethod = webViewSymbols.webViewGetterMethod,
                innerWebViewGetterMethod = webViewSymbols.innerWebViewGetterMethod,
                webViewPageDataGetterMethod = webViewSymbols.pageDataGetterMethod,
                webViewFirstFloorPostGetterMethod =
                    webViewSymbols.firstFloorPostGetterMethod,
                clipboardWriteMethods = clipboardWriteMethods,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[FreeCopyHook] native symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private const val FREE_COPY_POPUP_ROUND_LAYOUT_CLASS = "com.baidu.tbadk.core.dialog.RoundLinearLayout"

    private const val FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS =
        "com.baidu.tbadk.core.elementsMaven.view.EMTextView"

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val freeCopyPopupCritical = ArrayList<String>(3)
        if (symbols[FreeCopyContract.freeCopyPopupMenuClass].isNullOrBlank()) {
            freeCopyPopupCritical.add("freeCopyPopupMenuClass")
        }
        if (symbols[FreeCopyContract.freeCopyPopupContentViewMethod].isNullOrBlank()) {
            freeCopyPopupCritical.add("freeCopyPopupContentViewMethod")
        }
        if (symbols[FreeCopyContract.freeCopyPopupTextField].isNullOrBlank()) {
            freeCopyPopupCritical.add("freeCopyPopupTextField")
        }
        val commentInjectionStatus = if (freeCopyPopupCritical.isEmpty()) {
            HookFeatureStatus(state = HookFeatureState.FULL)
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = freeCopyPopupCritical,
            )
        }
        out[HookFeatureKey.FREE_COPY_COMMENT_INJECTION] = commentInjectionStatus
        fun nativeFreeCopyStatus(requireLongPress: Boolean): HookFeatureStatus {
            val critical = ArrayList<String>(4)
            val optional = ArrayList<String>(2)
            if (symbols[FreeCopyContract.freeCopyPostDataClass].isNullOrBlank()) {
                critical.add("freeCopyPostDataClass")
            }
            if (symbols[FreeCopyContract.freeCopyPostCopyMethodSpec].isNullOrBlank()) {
                critical.add("freeCopyPostCopyMethodSpec")
            }
            val hasPostParser = !symbols[FreeCopyContract.freeCopyPostParseMethodSpec].isNullOrBlank()
            val hasSubPostParser = !symbols[FreeCopyContract.freeCopySubPostParseMethodSpec].isNullOrBlank()
            if (!hasPostParser && !hasSubPostParser) {
                critical.add("freeCopyPostMetadataParser")
            } else {
                if (!hasPostParser) optional.add("freeCopyPostParseMethodSpec")
                if (!hasSubPostParser) optional.add("freeCopySubPostParseMethodSpec")
            }
            if (requireLongPress && symbols[FreeCopyContract.freeCopyPostFloorMethodSpec].isNullOrBlank()) {
                critical.add("freeCopyPostFloorMethodSpec")
            }
            if (requireLongPress) {
                val nativeLongPressMissing = buildList {
                    if (symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs].isNullOrEmpty()) {
                        add("freeCopyPostLongPressMethodSpecs")
                    }
                    if (symbols[FreeCopyContract.freeCopyRichTextViewClass].isNullOrBlank()) {
                        add("freeCopyRichTextViewClass")
                    }
                }
                val webViewLongPressMissing = buildList {
                    if (symbols[FreeCopyContract.freeCopyWebViewBindMethodSpec].isNullOrBlank()) {
                        add("freeCopyWebViewBindMethodSpec")
                    }
                    if (symbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec].isNullOrBlank()) {
                        add("freeCopyWebViewGetterMethodSpec")
                    }
                    if (symbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec].isNullOrBlank()) {
                        add("freeCopyInnerWebViewGetterMethodSpec")
                    }
                    if (symbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec].isNullOrBlank()) {
                        add("freeCopyWebViewPageDataGetterMethodSpec")
                    }
                    if (symbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec].isNullOrBlank()) {
                        add("freeCopyWebViewFirstFloorPostGetterMethodSpec")
                    }
                }
                if (nativeLongPressMissing.isNotEmpty() && webViewLongPressMissing.isNotEmpty()) {
                    critical += nativeLongPressMissing
                    critical += webViewLongPressMissing
                } else {
                    optional += nativeLongPressMissing
                    optional += webViewLongPressMissing
                }
                if (symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs].isNullOrEmpty()) {
                    optional.add("freeCopyTitleBindMethodSpecs")
                }
                if (symbols[FreeCopyContract.freeCopyTitleContainerField].isNullOrBlank()) {
                    optional.add("freeCopyTitleContainerField")
                }
                if (symbols[FreeCopyContract.freeCopyTitleTextField].isNullOrBlank()) {
                    optional.add("freeCopyTitleTextField")
                }
                if (symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec].isNullOrBlank()) {
                    optional.add("freeCopyTitlePostDataMethodSpec")
                }
            }
            return when {
                critical.isNotEmpty() -> HookFeatureStatus(
                    state = HookFeatureState.DISABLED,
                    missingCritical = critical,
                    missingOptional = optional,
                )
                optional.isNotEmpty() -> HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = optional,
                )
                else -> HookFeatureStatus(state = HookFeatureState.FULL)
            }
        }
        val postBodyStatus = nativeFreeCopyStatus(requireLongPress = false)
        val postLongPressStatus = nativeFreeCopyStatus(requireLongPress = true)
        val commentDialogStatus = nativeFreeCopyStatus(requireLongPress = false)
        out[HookFeatureKey.FREE_COPY_POST_BODY] = postBodyStatus
        out[HookFeatureKey.FREE_COPY_POST_LONG_PRESS] = postLongPressStatus
        out[HookFeatureKey.FREE_COPY_COMMENT_DIALOG] = commentDialogStatus
        val freeCopyChildStatuses = listOf(
            HookFeatureKey.FREE_COPY_POST_BODY to postBodyStatus,
            HookFeatureKey.FREE_COPY_POST_LONG_PRESS to postLongPressStatus,
            HookFeatureKey.FREE_COPY_COMMENT_INJECTION to commentInjectionStatus,
            HookFeatureKey.FREE_COPY_COMMENT_DIALOG to commentDialogStatus,
        )
        val supportedFreeCopyChildren = freeCopyChildStatuses.filter { it.second.isSupported() }
        out[HookFeatureKey.FREE_COPY] = when {
            supportedFreeCopyChildren.isEmpty() -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = freeCopyChildStatuses.map { it.first },
            )
            supportedFreeCopyChildren.all { it.second.state == HookFeatureState.FULL } &&
                supportedFreeCopyChildren.size == freeCopyChildStatuses.size -> {
                HookFeatureStatus(state = HookFeatureState.FULL)
            }
            else -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = freeCopyChildStatuses
                    .filter { it.second.state != HookFeatureState.FULL }
                    .map { it.first },
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "FreeCopyHook.Popup",
            "${symbols[FreeCopyContract.freeCopyPopupMenuClass]}.${symbols[FreeCopyContract.freeCopyPopupContentViewMethod]}[${symbols[FreeCopyContract.freeCopyPopupTextField]}]",
            listOf(
                FreeCopyContract.freeCopyPopupMenuClass.check(symbols),
                FreeCopyContract.freeCopyPopupContentViewMethod.check(symbols),
                FreeCopyContract.freeCopyPopupTextField.check(symbols),
            ),
        )
        add(
            "FreeCopyHook.Native",
            "${symbols[FreeCopyContract.freeCopyPostDataClass]}.${symbols[FreeCopyContract.freeCopyPostCopyMethodSpec]}",
            listOf(
                FreeCopyContract.freeCopyPostDataClass.check(symbols),
                FreeCopyContract.freeCopyPostCopyMethodSpec.check(symbols),
                "freeCopyPostMetadataParser" to (
                    has(symbols[FreeCopyContract.freeCopyPostParseMethodSpec]) ||
                        has(symbols[FreeCopyContract.freeCopySubPostParseMethodSpec])
                    ),
            ),
        )
        addOptional(
            "FreeCopyHook.Native.PostParser",
            symbols[FreeCopyContract.freeCopyPostParseMethodSpec] ?: "-",
            listOf(FreeCopyContract.freeCopyPostParseMethodSpec.check(symbols)),
        )
        addOptional(
            "FreeCopyHook.Native.SubPostParser",
            symbols[FreeCopyContract.freeCopySubPostParseMethodSpec] ?: "-",
            listOf(
                FreeCopyContract.freeCopySubPostParseMethodSpec.check(symbols),
            ),
        )
        add(
            "FreeCopyHook.LongPress",
            "${listTarget(symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs])} " +
                "floor=${symbols[FreeCopyContract.freeCopyPostFloorMethodSpec]}",
            listOf(
                FreeCopyContract.freeCopyRichTextViewClass.check(symbols),
                "freeCopyPostLongPressMethodSpecs" to
                    hasList(symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs]),
                FreeCopyContract.freeCopyPostFloorMethodSpec.check(symbols),
            ),
        )
        addOptional(
            "FreeCopyHook.TitleLongPress",
            "${listTarget(symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs])} " +
                "[${symbols[FreeCopyContract.freeCopyTitleContainerField]}:" +
                "${symbols[FreeCopyContract.freeCopyTitleTextField]}->${symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec]}]",
            listOf(
                "freeCopyTitleBindMethodSpecs" to
                    hasList(symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs]),
                FreeCopyContract.freeCopyTitleContainerField.check(symbols),
                FreeCopyContract.freeCopyTitleTextField.check(symbols),
                "freeCopyTitlePostDataMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec]),
            ),
        )
        addOptional(
            "FreeCopyHook.WebViewLongPress",
            "${symbols[FreeCopyContract.freeCopyWebViewBindMethodSpec]} " +
                "webView=${symbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec]}->" +
                "${symbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec]} " +
                "data=${symbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec]}->" +
                symbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec],
            listOf(
                "freeCopyWebViewBindMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyWebViewBindMethodSpec]),
                "freeCopyWebViewGetterMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec]),
                "freeCopyInnerWebViewGetterMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec]),
                "freeCopyWebViewPageDataGetterMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec]),
                "freeCopyWebViewFirstFloorPostGetterMethodSpec" to
                    has(symbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec]),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("FreeCopyHook.Popup", false, listOf(HookFeatureKey.FREE_COPY, HookFeatureKey.FREE_COPY_COMMENT_INJECTION)),
        PointOwner("FreeCopyHook.Native", true, listOf(HookFeatureKey.FREE_COPY, HookFeatureKey.FREE_COPY_POST_BODY, HookFeatureKey.FREE_COPY_COMMENT_DIALOG)),
        PointOwner("FreeCopyHook.LongPress", true, listOf(HookFeatureKey.FREE_COPY, HookFeatureKey.FREE_COPY_POST_LONG_PRESS)),
        PointOwner("FreeCopyHook.WebViewLongPress", true, listOf(HookFeatureKey.FREE_COPY, HookFeatureKey.FREE_COPY_POST_LONG_PRESS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasFreeCopyPopupSymbols =
            symbols[FreeCopyContract.freeCopyPopupMenuClass] != null ||
                symbols[FreeCopyContract.freeCopyPopupContentViewMethod] != null ||
                symbols[FreeCopyContract.freeCopyPopupTextField] != null
        if (hasFreeCopyPopupSymbols && !isFreeCopyPopupValid(symbols, cl)) return false
        val hasFreeCopyNativeSymbols =
            symbols[FreeCopyContract.freeCopyPostDataClass] != null ||
                symbols[FreeCopyContract.freeCopyPostCopyMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyPostParseMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopySubPostParseMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyPostFloorMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyRichTextViewClass] != null ||
                !symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs].isNullOrEmpty() ||
                !symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs].isNullOrEmpty() ||
                symbols[FreeCopyContract.freeCopyTitleContainerField] != null ||
                symbols[FreeCopyContract.freeCopyTitleTextField] != null ||
                symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyWebViewBindMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec] != null ||
                symbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec] != null
        if (hasFreeCopyNativeSymbols && !isFreeCopyNativeValid(symbols, cl)) return false
        return true
    }

    private fun isFreeCopyPopupValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val menuClassName = symbols[FreeCopyContract.freeCopyPopupMenuClass] ?: return false
        val contentViewMethodName = symbols[FreeCopyContract.freeCopyPopupContentViewMethod] ?: return false
        val textFieldName = symbols[FreeCopyContract.freeCopyPopupTextField] ?: return false
        return try {
            val menuClass = ScanReflection.safeFindClass(menuClassName, cl) ?: return false
            val hasContentViewMethod = menuClass.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == contentViewMethodName &&
                    method.parameterTypes.isEmpty() &&
                    View::class.java.isAssignableFrom(method.returnType)
            }
            if (!hasContentViewMethod) return false
            val emTextViewClass = ScanReflection.safeFindClass(FREE_COPY_POPUP_EM_TEXT_VIEW_CLASS, cl)
            menuClass.declaredFields.any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.name == textFieldName &&
                    (
                        TextView::class.java.isAssignableFrom(field.type) ||
                            emTextViewClass?.isAssignableFrom(field.type) == true
                    )
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isFreeCopyNativeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return try {
            val postDataClass = symbols[FreeCopyContract.freeCopyPostDataClass]?.let { ScanReflection.safeFindClass(it, cl) }
            if (symbols[FreeCopyContract.freeCopyPostDataClass] != null && postDataClass == null) return false
            val richTextViewClassName = symbols[FreeCopyContract.freeCopyRichTextViewClass]
            if (
                richTextViewClassName != null &&
                ScanReflection.safeFindClass(richTextViewClassName, cl) == null
            ) {
                return false
            }

            fun resolvePostDataMethod(spec: String?): Method? {
                val value = spec ?: return null
                val owner = postDataClass ?: return null
                return resolveFullMethodSpec(owner, value, cl)
            }

            symbols[FreeCopyContract.freeCopyPostCopyMethodSpec]?.let { spec ->
                val method = resolvePostDataMethod(spec) ?: return false
                if (
                    Modifier.isStatic(method.modifiers) ||
                    method.returnType != Void.TYPE ||
                    method.parameterTypes.isNotEmpty()
                ) {
                    return false
                }
            }

            symbols[FreeCopyContract.freeCopyPostFloorMethodSpec]?.let { spec ->
                val method = resolvePostDataMethod(spec) ?: return false
                if (
                    Modifier.isStatic(method.modifiers) ||
                    method.returnType != Int::class.javaPrimitiveType ||
                    method.parameterTypes.isNotEmpty()
                ) {
                    return false
                }
            }

            listOf(
                symbols[FreeCopyContract.freeCopyPostParseMethodSpec],
                symbols[FreeCopyContract.freeCopySubPostParseMethodSpec],
            ).filterNotNull().forEach { spec ->
                val method = resolvePostDataMethod(spec) ?: return false
                if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) return false
                val protocolClass = method.parameterTypes.firstOrNull() ?: return false
                val titleField = protocolClass.getDeclaredField("title")
                val floorField = protocolClass.getDeclaredField("floor")
                if (
                    titleField.type != String::class.java ||
                    !Number::class.java.isAssignableFrom(floorField.type)
                ) {
                    return false
                }
            }

            if (!symbols[FreeCopyContract.freeCopyPostLongPressMethodSpecs].orEmpty().all { fullSpec ->
                val separator = fullSpec.indexOf('#')
                if (separator <= 0 || separator >= fullSpec.lastIndex) return false
                val owner = ScanReflection.safeFindClass(fullSpec.substring(0, separator), cl) ?: return false
                val method = resolveFullMethodSpec(owner, fullSpec.substring(separator + 1), cl)
                    ?: return false
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.firstOrNull()?.let { parameterType ->
                        View::class.java.isAssignableFrom(parameterType)
                    } == true
            }) {
                return false
            }

            val titleBindSpecs = symbols[FreeCopyContract.freeCopyTitleBindMethodSpecs].orEmpty()
            val titleContainerFieldName = symbols[FreeCopyContract.freeCopyTitleContainerField]
            val titleTextFieldName = symbols[FreeCopyContract.freeCopyTitleTextField]
            val titlePostDataMethodSpec = symbols[FreeCopyContract.freeCopyTitlePostDataMethodSpec]
            val hasAnyTitleSymbol = titleBindSpecs.isNotEmpty() ||
                titleContainerFieldName != null ||
                titleTextFieldName != null ||
                titlePostDataMethodSpec != null
            if (hasAnyTitleSymbol) {
                if (
                    titleBindSpecs.isEmpty() ||
                    titleContainerFieldName.isNullOrBlank() ||
                    titleTextFieldName.isNullOrBlank() ||
                    titlePostDataMethodSpec.isNullOrBlank() ||
                    postDataClass == null
                ) {
                    return false
                }
                val titleBindMethods = titleBindSpecs.map { fullSpec ->
                    val separator = fullSpec.indexOf('#')
                    if (separator <= 0 || separator >= fullSpec.lastIndex) return false
                    val owner = ScanReflection.safeFindClass(fullSpec.substring(0, separator), cl) ?: return false
                    resolveFullMethodSpec(owner, fullSpec.substring(separator + 1), cl)
                        ?: return false
                }
                val titleOwner = titleBindMethods.first().declaringClass
                val pageDataClass = titleBindMethods.first().parameterTypes.singleOrNull()
                    ?: return false
                if (titleBindMethods.any { method ->
                        Modifier.isStatic(method.modifiers) ||
                            method.returnType != Void.TYPE ||
                            method.declaringClass != titleOwner ||
                            method.parameterTypes.singleOrNull() != pageDataClass
                    }
                ) {
                    return false
                }
                val titleContainerField = titleOwner.getDeclaredField(titleContainerFieldName)
                if (!ViewGroup::class.java.isAssignableFrom(titleContainerField.type)) return false
                val titleTextField = titleOwner.getDeclaredField(titleTextFieldName)
                if (!TextView::class.java.isAssignableFrom(titleTextField.type)) return false
                val titlePostDataMethod = resolveFullMethodSpec(
                    pageDataClass,
                    titlePostDataMethodSpec,
                    cl,
                ) ?: return false
                if (
                    Modifier.isStatic(titlePostDataMethod.modifiers) ||
                    titlePostDataMethod.returnType != postDataClass ||
                    titlePostDataMethod.parameterTypes.isNotEmpty()
                ) {
                    return false
                }
            }

            val webViewBindSpec = symbols[FreeCopyContract.freeCopyWebViewBindMethodSpec]
            val webViewGetterSpec = symbols[FreeCopyContract.freeCopyWebViewGetterMethodSpec]
            val innerWebViewGetterSpec = symbols[FreeCopyContract.freeCopyInnerWebViewGetterMethodSpec]
            val webViewPageDataGetterSpec = symbols[FreeCopyContract.freeCopyWebViewPageDataGetterMethodSpec]
            val webViewFirstFloorPostGetterSpec =
                symbols[FreeCopyContract.freeCopyWebViewFirstFloorPostGetterMethodSpec]
            val hasAnyWebViewSymbol = webViewBindSpec != null || webViewGetterSpec != null ||
                innerWebViewGetterSpec != null ||
                webViewPageDataGetterSpec != null || webViewFirstFloorPostGetterSpec != null
            if (!hasAnyWebViewSymbol) return true
            if (
                webViewBindSpec.isNullOrBlank() || webViewGetterSpec.isNullOrBlank() ||
                innerWebViewGetterSpec.isNullOrBlank() ||
                webViewPageDataGetterSpec.isNullOrBlank() ||
                webViewFirstFloorPostGetterSpec.isNullOrBlank() || postDataClass == null
            ) {
                return false
            }
            val webViewOwner = ScanReflection.safeFindClass(
                StableTiebaHookPoints.PB_COMMON_WEB_VIEW_CLASS,
                cl,
            ) ?: return false
            val webViewBindMethod = resolveFullMethodSpec(webViewOwner, webViewBindSpec, cl)
                ?: return false
            if (
                Modifier.isStatic(webViewBindMethod.modifiers) ||
                webViewBindMethod.returnType != Void.TYPE ||
                webViewBindMethod.parameterTypes.size != 1
            ) {
                return false
            }
            val webViewGetterMethod = resolveFullMethodSpec(webViewOwner, webViewGetterSpec, cl)
                ?: return false
            if (
                Modifier.isStatic(webViewGetterMethod.modifiers) ||
                webViewGetterMethod.parameterTypes.isNotEmpty() ||
                webViewGetterMethod.returnType == Void.TYPE
            ) {
                return false
            }
            val innerWebViewGetterMethod = resolveFullMethodSpec(
                webViewGetterMethod.returnType,
                innerWebViewGetterSpec,
                cl,
            ) ?: return false
            if (
                Modifier.isStatic(innerWebViewGetterMethod.modifiers) ||
                innerWebViewGetterMethod.parameterTypes.isNotEmpty() ||
                !WebView::class.java.isAssignableFrom(innerWebViewGetterMethod.returnType)
            ) {
                return false
            }
            val webViewPageDataGetterMethod = resolveFullMethodSpec(
                webViewBindMethod.parameterTypes.single(),
                webViewPageDataGetterSpec,
                cl,
            ) ?: return false
            if (
                Modifier.isStatic(webViewPageDataGetterMethod.modifiers) ||
                webViewPageDataGetterMethod.parameterTypes.isNotEmpty() ||
                webViewPageDataGetterMethod.returnType == Void.TYPE
            ) {
                return false
            }
            val firstFloorPostGetterMethod = resolveFullMethodSpec(
                webViewPageDataGetterMethod.returnType,
                webViewFirstFloorPostGetterSpec,
                cl,
            ) ?: return false
            !Modifier.isStatic(firstFloorPostGetterMethod.modifiers) &&
                firstFloorPostGetterMethod.parameterTypes.isEmpty() &&
                firstFloorPostGetterMethod.returnType == postDataClass
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveFullMethodSpec(owner: Class<*>, raw: String, cl: ClassLoader): Method? {
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3) return null
        val name = parts[0].takeIf { it.isNotBlank() } ?: return null
        val returnTypeName = parts[1].takeIf { it.isNotBlank() } ?: return null
        val parameterTypes = parts[2].split(',')
            .filter { it.isNotBlank() }
            .map { typeName -> resolveCachedParameterClass(typeName, cl) ?: return null }
            .toTypedArray()
        val method = owner.declaredMethods.singleOrNull { candidate ->
            candidate.name == name && candidate.parameterTypes.contentEquals(parameterTypes)
        } ?: return null
        return method.takeIf { it.returnType.name == returnTypeName }
    }
}
