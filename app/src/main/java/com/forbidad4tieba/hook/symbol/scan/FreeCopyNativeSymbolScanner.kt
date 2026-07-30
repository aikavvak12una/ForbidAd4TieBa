package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.DexFreeCopyMethodMatch
import com.forbidad4tieba.hook.symbol.model.FreeCopyLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyNativeScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyTitleLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.FreeCopyWebViewLongPressScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object FreeCopyNativeSymbolScanner {
    private const val POST_PROTO_CLASS = "tbclient.Post"
    private const val SUB_POST_PROTO_CLASS = "tbclient.SubPostList"
    private const val THREAD_DATA_CLASS = "com.baidu.tbadk.core.data.ThreadData"

    private data class WebViewBindingCandidate(
        val bindMethod: Method,
        val pageDataGetter: Method,
        val postDataOwnerClass: Class<*>,
    )

    private data class WebViewGetterCandidate(
        val wrapperGetter: Method,
        val innerWebViewGetter: Method,
    )

    fun scanNative(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FreeCopyNativeScanSymbols {
        val postDataClass = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_POST_DATA_CLASS, cl)
            ?: run {
                log(logger, "freeCopyNative: PostData class missing")
                return FreeCopyNativeScanSymbols()
            }
        val methods = scanSubStep("FreeCopyHook.Native.PostDataMethods", logger, null) {
            postDataClass.declaredMethods.toList()
        } ?: return FreeCopyNativeScanSymbols(postDataClass = postDataClass.name)

        val postParser = selectParser(
            methods = methods,
            parameterTypeNames = listOf(POST_PROTO_CLASS, THREAD_DATA_CLASS),
            protocolClassName = POST_PROTO_CLASS,
            cl = cl,
            logger = logger,
            tag = "FreeCopyHook.Native.PostParser",
        )
        val subPostParser = selectParser(
            methods = methods,
            parameterTypeNames = listOf(
                SUB_POST_PROTO_CLASS,
                "boolean",
                THREAD_DATA_CLASS,
                "int",
            ),
            protocolClassName = SUB_POST_PROTO_CLASS,
            cl = cl,
            logger = logger,
            tag = "FreeCopyHook.Native.SubPostParser",
        )

        val sourcePaths = appSourcePaths(context)
        val copyMethod = if (sourcePaths.isEmpty()) {
            log(logger, "freeCopyNative: apk source path unavailable")
            null
        } else {
            val matches = DexKitSemanticScanner.scanFreeCopyPostDataCopy(
                sourcePaths = sourcePaths,
                ownerClassName = postDataClass.name,
                logger = logger,
            )
            selectDexMethod(
                ownerClass = postDataClass,
                matches = matches,
                logger = logger,
                tag = "FreeCopyHook.Native.CopyMethod",
            )
        }

        return FreeCopyNativeScanSymbols(
            postDataClass = postDataClass.name,
            copyMethodSpec = copyMethod?.let(::encodeMethodSpec),
            postParseMethodSpec = postParser?.let(::encodeMethodSpec),
            subPostParseMethodSpec = subPostParser?.let(::encodeMethodSpec),
        )
    }

    fun scanLongPress(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): FreeCopyLongPressScanSymbols {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "freeCopyLongPress: apk source path unavailable")
            return FreeCopyLongPressScanSymbols()
        }
        val matches = DexKitSemanticScanner.scanFreeCopyPostLongPress(sourcePaths, logger)
        if (matches.isEmpty()) {
            log(logger, "freeCopyLongPress: no structurally verified entry")
            return FreeCopyLongPressScanSymbols()
        }
        val richTextViewClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.TB_RICH_TEXT_VIEW_CLASS,
            cl,
        ) ?: run {
            log(logger, "freeCopyLongPress: rich text view class missing")
            return FreeCopyLongPressScanSymbols()
        }

        val methods = matches.mapNotNull { match ->
            val ownerClass = ScanReflection.safeFindClass(match.ownerClassName, cl) ?: run {
                log(logger, "freeCopyLongPress: owner missing ${match.ownerClassName}")
                return@mapNotNull null
            }
            selectDexMethod(
                ownerClass = ownerClass,
                matches = listOf(match),
                logger = logger,
                tag = "FreeCopyHook.LongPress.${ownerClass.name}",
            )
        }
        val postDataClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_POST_DATA_CLASS,
            cl,
        )
        val commonFloorSpecs = matches
            .map { it.postDataIntNoArgMethodSpecs.toSet() }
            .takeIf { it.isNotEmpty() }
            ?.reduce { common, specs -> common intersect specs }
            .orEmpty()
        val postFloorMethod = if (postDataClass == null) {
            log(logger, "freeCopyLongPress: PostData class missing for floor accessor")
            null
        } else {
            val postDataMethods = scanSubStep(
                "FreeCopyHook.LongPress.PostFloor.Methods",
                logger,
                emptyList(),
            ) {
                postDataClass.declaredMethods.toList()
            }
            val floorCandidates = postDataMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Int::class.javaPrimitiveType &&
                    method.parameterTypes.isEmpty() &&
                    encodeMethodSpec(method) in commonFloorSpecs
            }
            selectUniqueScanCandidate(
                "FreeCopyHook.LongPress.PostFloor",
                floorCandidates,
                logger,
                ::describeMethod,
            )
        }
        return FreeCopyLongPressScanSymbols(
            richTextViewClass = richTextViewClass.name,
            methodSpecs = methods.map(::encodeClassMethodSpec).distinct(),
            postFloorMethodSpec = postFloorMethod?.let(::encodeMethodSpec),
        )
    }

    fun scanTitleLongPress(
        context: Context,
        cl: ClassLoader,
        postFloorMethodSpec: String?,
        logger: ScanLogger?,
    ): FreeCopyTitleLongPressScanSymbols {
        val floorSpec = postFloorMethodSpec?.takeIf { it.isNotBlank() } ?: run {
            log(logger, "freeCopyTitleLongPress: floor accessor unavailable")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "freeCopyTitleLongPress: apk source path unavailable")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val compatibleMatches = DexKitSemanticScanner
            .scanFreeCopyPostTitleLongPress(sourcePaths, logger)
            .filter { match -> floorSpec in match.postDataIntNoArgMethodSpecs }
        val match = selectUniqueScanCandidate(
            "FreeCopyHook.TitleLongPress.Dex",
            compatibleMatches,
            logger,
        ) { candidate ->
            candidate.ownerClassName + "[" + candidate.textFieldName + "]" +
                "->" + candidate.pageDataClassName + "." + candidate.postDataMethodSpec
        } ?: return FreeCopyTitleLongPressScanSymbols()

        val ownerClass = ScanReflection.safeFindClass(match.ownerClassName, cl) ?: run {
            log(logger, "freeCopyTitleLongPress: owner missing ${match.ownerClassName}")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val pageDataClass = ScanReflection.safeFindClass(match.pageDataClassName, cl) ?: run {
            log(logger, "freeCopyTitleLongPress: page data missing ${match.pageDataClassName}")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val postDataClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_POST_DATA_CLASS,
            cl,
        ) ?: run {
            log(logger, "freeCopyTitleLongPress: PostData class missing")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val ownerMethods = scanSubStep(
            "FreeCopyHook.TitleLongPress.OwnerMethods",
            logger,
            emptyList(),
        ) {
            ownerClass.declaredMethods.toList()
        }
        val validBindMethods = ownerMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(pageDataClass))
        }
        val validBindMethodSpecs = validBindMethods.map(::encodeMethodSpec).toSet()
        if (
            validBindMethods.isEmpty() ||
            !validBindMethodSpecs.containsAll(match.bindMethodSpecs)
        ) {
            log(logger, "freeCopyTitleLongPress: bind method signature mismatch")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val titleContainerFields = scanSubStep(
            "FreeCopyHook.TitleLongPress.ContainerField",
            logger,
            emptyList(),
        ) {
            ownerClass.declaredFields.filter { field ->
                !Modifier.isStatic(field.modifiers) &&
                    ViewGroup::class.java.isAssignableFrom(field.type)
            }
        }
        if (titleContainerFields.size != 1) {
            log(
                logger,
                "freeCopyTitleLongPress: container fields=" +
                    titleContainerFields.map { field -> field.name },
            )
            return FreeCopyTitleLongPressScanSymbols()
        }
        val titleContainerField = titleContainerFields.single()
        val titleTextField = scanSubStep(
            "FreeCopyHook.TitleLongPress.TextField",
            logger,
            null,
        ) {
            ownerClass.getDeclaredField(match.textFieldName)
        }?.takeIf { field ->
            TextView::class.java.isAssignableFrom(field.type)
        } ?: run {
            log(logger, "freeCopyTitleLongPress: title TextView field mismatch")
            return FreeCopyTitleLongPressScanSymbols()
        }
        val postDataMethod = resolveMethodBySpec(pageDataClass, match.postDataMethodSpec)
            ?.takeIf { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == postDataClass &&
                    method.parameterTypes.isEmpty()
            } ?: run {
            log(logger, "freeCopyTitleLongPress: first-floor PostData method mismatch")
            return FreeCopyTitleLongPressScanSymbols()
        }
        log(
            logger,
            "freeCopyTitleLongPress: ${match.evidence}, methods=${validBindMethods.size}, " +
                "container=${titleContainerField.type.name}",
        )
        return FreeCopyTitleLongPressScanSymbols(
            bindMethodSpecs = validBindMethods.map(::encodeClassMethodSpec),
            containerField = titleContainerField.name,
            textField = titleTextField.name,
            postDataMethodSpec = encodeMethodSpec(postDataMethod),
        )
    }

    fun scanWebViewLongPress(
        context: Context,
        cl: ClassLoader,
        postFloorMethodSpec: String?,
        logger: ScanLogger?,
    ): FreeCopyWebViewLongPressScanSymbols {
        val floorSpec = postFloorMethodSpec?.takeIf { it.isNotBlank() } ?: run {
            log(logger, "freeCopyWebViewLongPress: floor accessor unavailable")
            return FreeCopyWebViewLongPressScanSymbols()
        }
        val postDataClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_POST_DATA_CLASS,
            cl,
        ) ?: run {
            log(logger, "freeCopyWebViewLongPress: PostData class missing")
            return FreeCopyWebViewLongPressScanSymbols()
        }
        val ownerClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_COMMON_WEB_VIEW_CLASS,
            cl,
        ) ?: run {
            log(logger, "freeCopyWebViewLongPress: PbCommonWebView class missing")
            return FreeCopyWebViewLongPressScanSymbols()
        }
        val ownerMethods = scanSubStep(
            "FreeCopyHook.WebViewLongPress.OwnerMethods",
            logger,
            emptyList(),
        ) {
            ownerClass.declaredMethods.toList()
        }
        val webViewGetter = selectUniqueScanCandidate(
            "FreeCopyHook.WebViewLongPress.WebViewGetter",
            ownerMethods.asSequence()
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType != Void.TYPE
                }
                .flatMap { wrapperGetter ->
                    wrapperGetter.returnType.declaredMethods.asSequence()
                        .filter { method ->
                            !Modifier.isStatic(method.modifiers) &&
                                method.parameterTypes.isEmpty() &&
                                WebView::class.java.isAssignableFrom(method.returnType)
                        }
                        .map { innerGetter ->
                            WebViewGetterCandidate(wrapperGetter, innerGetter)
                        }
                }
                .toList(),
            logger,
        ) { candidate ->
            describeMethod(candidate.wrapperGetter) + "->" +
                describeMethod(candidate.innerWebViewGetter)
        } ?: return FreeCopyWebViewLongPressScanSymbols()

        val bindingCandidates = ownerMethods.asSequence()
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    !Modifier.isFinal(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1
            }
            .flatMap { bindMethod ->
                val pageDataClass = bindMethod.parameterTypes.single()
                pageDataClass.declaredMethods.asSequence()
                    .filter { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType != Void.TYPE
                    }
                    .mapNotNull { pageDataGetter ->
                        val postDataOwnerClass = pageDataGetter.returnType
                        val hasPostDataGetter = postDataOwnerClass.declaredMethods.any { method ->
                            !Modifier.isStatic(method.modifiers) &&
                                method.parameterTypes.isEmpty() &&
                                method.returnType == postDataClass
                        }
                        if (hasPostDataGetter) {
                            WebViewBindingCandidate(
                                bindMethod = bindMethod,
                                pageDataGetter = pageDataGetter,
                                postDataOwnerClass = postDataOwnerClass,
                            )
                        } else {
                            null
                        }
                    }
            }
            .toList()
        val binding = selectUniqueScanCandidate(
            "FreeCopyHook.WebViewLongPress.Binding",
            bindingCandidates,
            logger,
        ) { candidate ->
            describeMethod(candidate.bindMethod) + "->" +
                describeMethod(candidate.pageDataGetter)
        } ?: return FreeCopyWebViewLongPressScanSymbols()

        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "freeCopyWebViewLongPress: apk source path unavailable")
            return FreeCopyWebViewLongPressScanSymbols()
        }
        val firstFloorPostGetter = selectDexMethod(
            ownerClass = binding.postDataOwnerClass,
            matches = DexKitSemanticScanner.scanFreeCopyFirstFloorPostGetter(
                sourcePaths = sourcePaths,
                ownerClassName = binding.postDataOwnerClass.name,
                postDataClassName = postDataClass.name,
                floorMethodSpec = floorSpec,
                logger = logger,
            ),
            logger = logger,
            tag = "FreeCopyHook.WebViewLongPress.FirstFloorPostGetter",
        ) ?: return FreeCopyWebViewLongPressScanSymbols()
        log(
            logger,
            "freeCopyWebViewLongPress: bind=${describeMethod(binding.bindMethod)}, " +
                "webView=${describeMethod(webViewGetter.wrapperGetter)}->" +
                "${describeMethod(webViewGetter.innerWebViewGetter)}, " +
                "post=${describeMethod(firstFloorPostGetter)}",
        )
        return FreeCopyWebViewLongPressScanSymbols(
            bindMethodSpec = encodeMethodSpec(binding.bindMethod),
            webViewGetterMethodSpec = encodeMethodSpec(webViewGetter.wrapperGetter),
            innerWebViewGetterMethodSpec =
                encodeMethodSpec(webViewGetter.innerWebViewGetter),
            pageDataGetterMethodSpec = encodeMethodSpec(binding.pageDataGetter),
            firstFloorPostGetterMethodSpec = encodeMethodSpec(firstFloorPostGetter),
        )
    }

    private fun selectParser(
        methods: List<Method>,
        parameterTypeNames: List<String>,
        protocolClassName: String,
        cl: ClassLoader,
        logger: ScanLogger?,
        tag: String,
    ): Method? {
        if (!hasMetadataFields(protocolClassName, cl, logger, tag)) return null
        val candidates = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.map { it.name } == parameterTypeNames
        }
        return selectUniqueScanCandidate(tag, candidates, logger, ::describeMethod)
    }

    private fun hasMetadataFields(
        protocolClassName: String,
        cl: ClassLoader,
        logger: ScanLogger?,
        tag: String,
    ): Boolean {
        val protocolClass = ScanReflection.safeFindClass(protocolClassName, cl) ?: run {
            log(logger, "$tag: protocol class missing $protocolClassName")
            return false
        }
        val titleField = scanSubStep("$tag.TitleField", logger, null) {
            protocolClass.getDeclaredField("title")
        } ?: return false
        val floorField = scanSubStep("$tag.FloorField", logger, null) {
            protocolClass.getDeclaredField("floor")
        } ?: return false
        val valid = titleField.type == String::class.java &&
            Number::class.java.isAssignableFrom(floorField.type)
        if (!valid) {
            log(
                logger,
                "$tag: metadata field mismatch title=${titleField.type.name}, floor=${floorField.type.name}",
            )
        }
        return valid
    }

    private fun selectDexMethod(
        ownerClass: Class<*>,
        matches: List<DexFreeCopyMethodMatch>,
        logger: ScanLogger?,
        tag: String,
    ): Method? {
        val declaredMethods = scanSubStep("$tag.Methods", logger, null) {
            ownerClass.declaredMethods.toList()
        } ?: return null
        val candidates = matches.flatMap { match ->
            declaredMethods.filter { method ->
                method.name == match.methodName &&
                    method.returnType.name == match.returnTypeName &&
                    method.parameterTypes.map { it.name } == match.parameterTypeNames
            }
        }.distinct()
        return selectUniqueScanCandidate(tag, candidates, logger, ::describeMethod)
    }

    private fun resolveMethodBySpec(ownerClass: Class<*>, spec: String): Method? {
        val parts = spec.split('|', limit = 3)
        if (parts.size != 3) return null
        val methodName = parts[0]
        val returnTypeName = parts[1]
        val parameterTypeNames = parts[2].split(',').filter { it.isNotBlank() }
        return ownerClass.declaredMethods.singleOrNull { method ->
            method.name == methodName &&
                method.returnType.name == returnTypeName &&
                method.parameterTypes.map { it.name } == parameterTypeNames
        }
    }

    private fun encodeMethodSpec(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.name}|${method.returnType.name}|$params"
    }

    private fun encodeClassMethodSpec(method: Method): String {
        return method.declaringClass.name + "#" + encodeMethodSpec(method)
    }

    private fun describeMethod(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        return "${method.declaringClass.name}.${method.name}($params):${method.returnType.name}"
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
