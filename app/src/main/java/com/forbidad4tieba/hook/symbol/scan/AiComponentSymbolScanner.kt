package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.AiComponentScanSymbols
import com.forbidad4tieba.hook.symbol.model.DexAiComponentInitMatch
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object AiComponentSymbolScanner {
    private const val AI_SPRITE_MEME_PAN_CONTROLLER_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePanController"
    private const val AI_SPRITE_MEME_ENABLE_METHOD = "s"
    private const val AI_SPRITE_MEME_PAN_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan"
    private const val AI_PB_NEW_INPUT_CONTAINER_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewInputContainer"
    private const val AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"
    private const val AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout"
    private const val AI_IMAGE_VIEWER_BOTTOM_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageViewerBottomLayout"
    private const val AI_IMAGE_VIEWER_ABS_FLOOR_IMAGE_TEXT_VIEW_CLASS =
        "com.baidu.tbadk.coreExtra.view.AbsFloorImageTextView"
    private const val AI_IMAGE_VIEWER_FACE_GROUP_DOWNLOAD_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.FaceGroupDownloadLayout"
    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"
    private const val AI_SPRITE_MEME_INFO_CLASS = "tbclient.SpriteMemeInfo"

    fun scan(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val imageViewerJumpButtonScan = scanSubStep(
            "AiComponentDisableHook.ImageViewerJumpButton",
            logger,
            AiComponentScanSymbols(),
        ) {
            scanImageViewerJumpButtonSymbols(context, cl, candidateClassNames, logger)
        }
        val emojiCreationScan = scanSubStep(
            "AiComponentDisableHook.PbAiEmojiCreation",
            logger,
            AiComponentScanSymbols(),
        ) {
            scanPbAiEmojiCreationSymbols(context, cl, candidateClassNames, logger)
        }
        val optionalScan = imageViewerJumpButtonScan.copy(
            pbAiEmojiCreationViewBindMethod = emojiCreationScan.pbAiEmojiCreationViewBindMethod,
            pbPageBrowserAiEmojiCreationViewClass = emojiCreationScan.pbPageBrowserAiEmojiCreationViewClass,
            pbPageBrowserAiEmojiCreationBindMethod = emojiCreationScan.pbPageBrowserAiEmojiCreationBindMethod,
        )
        return scanSubStep("AiComponentDisableHook.Main", logger, optionalScan) {
            val controllerClass = safeFindClass(AI_SPRITE_MEME_PAN_CONTROLLER_CLASS, cl) ?: run {
                log(logger, "aiComponent: class not found: $AI_SPRITE_MEME_PAN_CONTROLLER_CLASS")
                return@scanSubStep optionalScan
            }
            val inputContainerClass = safeFindClass(AI_PB_NEW_INPUT_CONTAINER_CLASS, cl) ?: run {
                log(logger, "aiComponent: class not found: $AI_PB_NEW_INPUT_CONTAINER_CLASS")
                return@scanSubStep optionalScan.copy(
                    spriteMemePanControllerClass = controllerClass.name,
                    spriteMemeEnableMethod = resolveAiSpriteMemeEnableMethod(controllerClass, cl, logger)?.name,
                )
            }
            val spriteMemePanClass = safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)
            val enableMethod = resolveAiSpriteMemeEnableMethod(controllerClass, cl, logger)
            val initSpriteMemeMethod = scanSpriteMemeInitMethodFromDex(context, inputContainerClass, logger)
            val initAiWriteMethod = scanAiWriteInitMethodFromDex(context, inputContainerClass, logger)

            if (!isAiPbNewInputContainerClassValidForScan(inputContainerClass, spriteMemePanClass, logger)) {
                log(logger, "aiComponent: PbNewInputContainer structure mismatch")
                return@scanSubStep optionalScan.copy(
                    spriteMemePanControllerClass = controllerClass.name,
                    spriteMemeEnableMethod = enableMethod?.name,
                    pbNewInputContainerClass = inputContainerClass.name,
                    pbInitSpriteMemeMethod = initSpriteMemeMethod?.name,
                    pbInitAiWriteMethod = initAiWriteMethod?.name,
                )
            }

            log(
                logger,
                "aiComponent matched: " +
                    "${controllerClass.name}.${enableMethod?.name} / " +
                    "${inputContainerClass.name}.{${initSpriteMemeMethod?.name},${initAiWriteMethod?.name}}",
            )
            optionalScan.copy(
                spriteMemePanControllerClass = controllerClass.name,
                spriteMemeEnableMethod = enableMethod?.name,
                pbNewInputContainerClass = inputContainerClass.name,
                pbInitSpriteMemeMethod = initSpriteMemeMethod?.name,
                pbInitAiWriteMethod = initAiWriteMethod?.name,
            )
        }
    }

    fun scoreImageViewerJumpButtonOwnerClass(clazz: Class<*>, layoutClass: Class<*>): Int {
        if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) return 0
        if (!clazz.name.startsWith("com.baidu.tieba.")) return 0
        val fields = ScanReflection.collectInstanceFields(clazz)
        if (fields.none { layoutClass.isAssignableFrom(it.type) }) return 0

        val constructors = declaredConstructorsForScan("ImageViewerJumpButton.${clazz.name}.Validate", clazz, null)
            ?: return 0
        val methods = declaredMethodsForScan("ImageViewerJumpButton.${clazz.name}.Validate", clazz, null)
            ?: return 0
        return scoreImageViewerJumpButtonOwnerShape(clazz, layoutClass, fields, constructors, methods)
    }

    private fun scoreImageViewerJumpButtonOwnerClassForScan(
        clazz: Class<*>,
        layoutClass: Class<*>,
        logger: ScanLogger?,
    ): Int {
        if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) return 0
        if (!clazz.name.startsWith("com.baidu.tieba.")) return 0
        val fields = scanSubStep(
            "AiComponentDisableHook.ImageViewerJumpButton.${clazz.name}.InstanceFields",
            logger,
            null,
        ) {
            ScanReflection.collectInstanceFields(clazz)
        } ?: return 0
        if (fields.none { layoutClass.isAssignableFrom(it.type) }) return 0

        val constructors = declaredConstructorsForScan("ImageViewerJumpButton.${clazz.name}", clazz, logger)
            ?: return 0
        val methods = declaredMethodsForScan("ImageViewerJumpButton.${clazz.name}", clazz, logger)
            ?: return 0
        return scoreImageViewerJumpButtonOwnerShape(clazz, layoutClass, fields, constructors, methods)
    }

    private fun scoreImageViewerJumpButtonOwnerShape(
        clazz: Class<*>,
        layoutClass: Class<*>,
        fields: List<Field>,
        constructors: List<Constructor<*>>,
        methods: List<Method>,
    ): Int {
        val hasContextRelativeCtor = constructors.any { ctor ->
            val params = ctor.parameterTypes
            params.size == 2 &&
                Context::class.java.isAssignableFrom(params[0]) &&
                RelativeLayout::class.java.isAssignableFrom(params[1])
        }
        if (!hasContextRelativeCtor) return 0

        var score = 150
        if (clazz.interfaces.any { it.name == "com.baidu.tieba.np6" }) score += 18
        if (fields.any { it.type == Context::class.java }) score += 10
        if (fields.any { it.type == LinearLayout::class.java }) score += 10
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_BOTTOM_LAYOUT_CLASS }) score += 14
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_ABS_FLOOR_IMAGE_TEXT_VIEW_CLASS }) score += 10
        if (fields.any { it.type.name == AI_IMAGE_VIEWER_FACE_GROUP_DOWNLOAD_LAYOUT_CLASS }) score += 10
        if (methods.any { method -> method.parameterTypes.isEmpty() && layoutClass.isAssignableFrom(method.returnType) }) {
            score += 12
        }
        score -= fields.size / 3
        score -= methods.size / 6
        score -= clazz.simpleName.length
        return if (score >= 145) score else 0
    }

    fun isImageViewerJumpButtonInitMethodName(ownerClass: Class<*>, methodName: String): Boolean {
        return declaredMethodsForScan("ImageViewerJumpButton.${ownerClass.name}.ValidateInit", ownerClass, null)
            ?.any { method ->
            method.name == methodName && isImageViewerJumpButtonInitMethod(method)
            }
            ?: false
    }

    private fun isImageViewerJumpButtonInitMethodName(methods: List<Method>, methodName: String): Boolean {
        return methods.any { method ->
            method.name == methodName && isImageViewerJumpButtonInitMethod(method)
        }
    }

    fun isImageViewerJumpButtonInitMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.isEmpty()
    }

    fun isAiPbEmojiCreationViewBindMethod(method: Method, cl: ClassLoader): Boolean {
        if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) return false
        val spriteMemeInfoClass = ScanReflection.safeFindClass(AI_SPRITE_MEME_INFO_CLASS, cl) ?: return false
        val params = method.parameterTypes
        return params.size == 3 &&
            spriteMemeInfoClass.isAssignableFrom(params[0]) &&
            ScanReflection.isIntType(params[1]) &&
            Map::class.java.isAssignableFrom(params[2])
    }

    fun isPbPageBrowserAiEmojiCreationBindMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1
    }

    fun isAiSpriteMemeEnableMethod(method: Method, inputShowTypeClass: Class<*>?): Boolean {
        val params = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 2 &&
            Context::class.java.isAssignableFrom(params[0]) &&
            (inputShowTypeClass?.let { params[1] == it } ?: (params[1].name == AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS))
    }

    fun isPbNewInputContextInitMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            params.size == 1 &&
            Context::class.java.isAssignableFrom(params[0])
    }

    fun isPbNewInputContextInitMethodName(inputContainerClass: Class<*>, methodName: String): Boolean {
        return declaredMethodsForScan("PbNewInputContainer.$methodName.Validate", inputContainerClass, null)
            ?.any { method ->
            method.name == methodName && isPbNewInputContextInitMethod(method)
            }
            ?: false
    }

    private fun isPbNewInputContextInitMethodName(methods: List<Method>, methodName: String): Boolean {
        return methods.any { method ->
            method.name == methodName && isPbNewInputContextInitMethod(method)
        }
    }

    fun isAiPbNewInputContainerClassValid(
        inputContainerClass: Class<*>,
        spriteMemePanClass: Class<*>?,
    ): Boolean {
        if (!LinearLayout::class.java.isAssignableFrom(inputContainerClass)) return false
        val fields = ScanReflection.collectInstanceFields(inputContainerClass)
        val hasAiWriteFrame = fields.any { field -> FrameLayout::class.java.isAssignableFrom(field.type) }
        if (!hasAiWriteFrame) return false
        if (spriteMemePanClass == null) return true
        return fields.any { field -> spriteMemePanClass.isAssignableFrom(field.type) }
    }

    private fun isAiPbNewInputContainerClassValidForScan(
        inputContainerClass: Class<*>,
        spriteMemePanClass: Class<*>?,
        logger: ScanLogger?,
    ): Boolean {
        if (!LinearLayout::class.java.isAssignableFrom(inputContainerClass)) return false
        val fields = scanSubStep(
            "AiComponentDisableHook.PbNewInputContainer.Structure.InstanceFields",
            logger,
            null,
        ) {
            ScanReflection.collectInstanceFields(inputContainerClass)
        } ?: return false
        val hasAiWriteFrame = fields.any { field -> FrameLayout::class.java.isAssignableFrom(field.type) }
        if (!hasAiWriteFrame) return false
        if (spriteMemePanClass == null) return true
        return fields.any { field -> spriteMemePanClass.isAssignableFrom(field.type) }
    }

    private fun scanPbAiEmojiCreationSymbols(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val viewBindMethod = scanPbAiEmojiCreationViewBindMethod(cl, logger)
        val pageBrowserPath = scanPbPageBrowserAiEmojiCreationBindMethod(context, cl, candidateClassNames, logger)
        log(
            logger,
            "aiComponent.pbAiEmojiCreation matched: " +
                "bind=${viewBindMethod?.name ?: "-"}, " +
                "pageBrowser=${pageBrowserPath?.viewClass?.name ?: "-"}.${pageBrowserPath?.bindMethod?.name ?: "-"}",
        )
        return AiComponentScanSymbols(
            pbAiEmojiCreationViewBindMethod = viewBindMethod?.name,
            pbPageBrowserAiEmojiCreationViewClass = pageBrowserPath?.viewClass?.name,
            pbPageBrowserAiEmojiCreationBindMethod = pageBrowserPath?.bindMethod?.name,
        )
    }

    private fun scanPbAiEmojiCreationViewBindMethod(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Method? {
        return try {
            val viewClass = safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: view class not found")
                return null
            }
            val methods = declaredMethodsForScan("PbAiEmojiCreation.View", viewClass, logger) ?: return null
            methods.singleOrNull { method ->
                isAiPbEmojiCreationViewBindMethod(method, cl)
            } ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: view bind method not found")
                null
            }
        } catch (t: Throwable) {
            log(logger, "aiComponent.pbAiEmojiCreation: view bind error: ${formatScanException(t)}")
            null
        }
    }

    private fun scanPbPageBrowserAiEmojiCreationBindMethod(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): PageBrowserAiEmojiCreationPath? {
        return try {
            val matches = ArrayList<PageBrowserAiEmojiCreationCandidate>()
            var skippedByReflection = 0
            var firstReflectionError: String? = null
            for (className in pageBrowserAiEmojiCreationViewCandidateNames(candidateClassNames)) {
                try {
                    val viewClass = safeFindClass(className, cl) ?: continue
                    scorePageBrowserAiEmojiCreationView(viewClass, logger)?.let(matches::add)
                } catch (t: Throwable) {
                    skippedByReflection++
                    if (firstReflectionError == null) {
                        firstReflectionError = sanitizeScanStatusText(formatScanException(t))
                    }
                }
            }
            if (skippedByReflection > 0) {
                log(
                    logger,
                    "aiComponent.pbAiEmojiCreation: page browser skipped classes by reflection=$skippedByReflection" +
                        (firstReflectionError?.let { ", firstException=$it" } ?: ""),
                )
            }

            val ranked = matches.sortedWith(
                compareByDescending<PageBrowserAiEmojiCreationCandidate> { it.score }
                    .thenByDescending { if (it.viewClass.name == AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS) 1 else 0 }
                    .thenBy { it.viewClass.name },
            )
            val best = ranked.firstOrNull() ?: run {
                log(logger, "aiComponent.pbAiEmojiCreation: page browser view/bind method not found")
                return scanPbPageBrowserAiEmojiCreationBindMethodFromDex(context, cl, logger)
            }
            val second = ranked.getOrNull(1)
            if (
                second != null &&
                best.viewClass.name != AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS &&
                best.score - second.score < 24
            ) {
                log(
                    logger,
                    "aiComponent.pbAiEmojiCreation: page browser view ambiguous: " +
                        "best=${best.viewClass.name}.${best.bindMethod.name}:${best.score}, " +
                        "second=${second.viewClass.name}.${second.bindMethod.name}:${second.score}",
                )
                return scanPbPageBrowserAiEmojiCreationBindMethodFromDex(context, cl, logger)
            }
            log(
                logger,
                "aiComponent.pbAiEmojiCreation: page browser matched " +
                    "${best.viewClass.name}.${best.bindMethod.name} score=${best.score}",
            )
            PageBrowserAiEmojiCreationPath(best.viewClass, best.bindMethod)
        } catch (t: Throwable) {
            log(logger, "aiComponent.pbAiEmojiCreation: page browser bind error: ${formatScanException(t)}")
            null
        }
    }

    private fun scanPbPageBrowserAiEmojiCreationBindMethodFromDex(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PageBrowserAiEmojiCreationPath? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.pbAiEmojiCreationDex: no app source paths")
            return null
        }
        val match = DexKitSemanticScanner.scanPbPageBrowserAiEmojiCreation(sourcePaths, logger) ?: return null
        val viewClass = safeFindClass(match.viewClassName, cl) ?: run {
            log(logger, "aiComponent.pbAiEmojiCreationDex: page browser view not loadable: ${match.viewClassName}")
            return null
        }
        val method = declaredMethodsForScan("PbAiEmojiCreation.PageBrowserDex.${viewClass.name}", viewClass, logger)
            ?.singleOrNull { method ->
                method.name == match.bindMethodName &&
                    isPbPageBrowserAiEmojiCreationBindMethod(method)
            } ?: run {
            log(
                logger,
                "aiComponent.pbAiEmojiCreationDex: page browser bind reflection mismatch: " +
                    "${match.viewClassName}.${match.bindMethodName}",
            )
            return null
        }
        log(
            logger,
            "aiComponent.pbAiEmojiCreationDex verified: ${viewClass.name}.${method.name}",
        )
        return PageBrowserAiEmojiCreationPath(viewClass, method)
    }

    private fun pageBrowserAiEmojiCreationViewCandidateNames(candidateClassNames: List<String>): List<String> {
        return (listOf(AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS) + candidateClassNames)
            .asSequence()
            .distinct()
            .filter { name ->
                name == AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS ||
                    (
                        name.startsWith("com.baidu.tieba.pb.pagebrowser.") &&
                            (
                                name.contains(".meme.") ||
                                    name.contains("AiEmoji") ||
                                    name.contains("EmojiCreation") ||
                                    name.contains("CommentFloor") ||
                                    name.endsWith("View")
                                )
                        )
            }
            .toList()
    }

    private fun scorePageBrowserAiEmojiCreationView(
        viewClass: Class<*>,
        logger: ScanLogger?,
    ): PageBrowserAiEmojiCreationCandidate? {
        if (viewClass.isInterface || Modifier.isAbstract(viewClass.modifiers)) return null
        if (!FrameLayout::class.java.isAssignableFrom(viewClass)) return null

        val methods = declaredMethodsForScan("PbAiEmojiCreation.PageBrowserView.${viewClass.name}", viewClass, logger)
            ?: return null
        val bindCandidates = methods.mapNotNull { method ->
            scorePageBrowserAiEmojiCreationBindMethod(viewClass, method)?.let { score -> method to score }
        }.sortedWith(
            compareByDescending<Pair<Method, Int>> { it.second }
                .thenBy { it.first.name },
        )
        val bind = bindCandidates.firstOrNull() ?: return null
        if (bindCandidates.getOrNull(1)?.let { bind.second - it.second < 20 } == true) return null

        val fields = declaredFieldsForScan("PbAiEmojiCreation.PageBrowserView.${viewClass.name}", viewClass, logger)
            .orEmpty()
        val constructors =
            declaredConstructorsForScan("PbAiEmojiCreation.PageBrowserView.${viewClass.name}", viewClass, logger)
                .orEmpty()

        var score = 120 + bind.second
        if (viewClass.name == AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS) score += 90
        if (viewClass.name.contains("CommentFloorAiEmojiCreationView")) score += 70
        if (viewClass.name.contains(".meme.")) score += 20
        if (hasKotlinMetadataSignal(viewClass, "CommentFloorAiEmojiCreationView")) score += 50
        if (hasKotlinMetadataSignal(viewClass, "AiEmojiCreation")) score += 42
        if (hasKotlinMetadataSignal(viewClass, "bindData")) score += 36
        if (hasKotlinMetadataSignal(viewClass, "CommentFloorAiEmojiCreationUIState")) score += 32
        if (fields.any { field ->
                field.type.name.contains("CommentFloorAiEmojiCreationItemBinding") ||
                    field.type.name.contains("PbAiEmojiCreationView")
            }
        ) {
            score += 42
        }
        if (constructors.any { ctor ->
                val params = ctor.parameterTypes
                params.isNotEmpty() && Context::class.java.isAssignableFrom(params[0])
            }
        ) {
            score += 16
        }
        score -= methods.size / 8
        score -= fields.size / 4
        return if (score >= 220) {
            PageBrowserAiEmojiCreationCandidate(viewClass, bind.first, score)
        } else {
            null
        }
    }

    private fun scorePageBrowserAiEmojiCreationBindMethod(viewClass: Class<*>, method: Method): Int? {
        if (!isPbPageBrowserAiEmojiCreationBindMethod(method)) return null
        val param = method.parameterTypes.firstOrNull() ?: return null
        var score = 0
        if (method.name == "bindData") score += 90
        if (param.name.contains("CommentFloorAiEmojiCreationUIState")) score += 80
        if (param.name.contains("AiEmojiCreation")) score += 40
        if (hasKotlinMetadataSignal(viewClass, "bindData")) score += 28
        if (hasKotlinMetadataSignal(viewClass, "uiState")) score += 16
        return score.takeIf { it >= 40 }
    }

    private fun scanImageViewerJumpButtonSymbols(
        context: Context,
        cl: ClassLoader,
        candidateClassNames: List<String>,
        logger: ScanLogger?,
    ): AiComponentScanSymbols {
        val layoutClass = safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl) ?: run {
            log(logger, "aiComponent.imageViewerJumpButton: class not found: $AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS")
            return AiComponentScanSymbols()
        }
        val ownerClass = resolveImageViewerJumpButtonOwnerClass(candidateClassNames, cl, layoutClass, logger)
            ?: return AiComponentScanSymbols()
        val initMethod = scanImageViewerJumpButtonInitMethodFromDex(context, ownerClass, logger)
        if (initMethod == null) {
            log(logger, "aiComponent.imageViewerJumpButton: init method missing: ${ownerClass.name}")
            return AiComponentScanSymbols(imageViewerJumpButtonOwnerClass = ownerClass.name)
        }
        log(logger, "aiComponent.imageViewerJumpButton matched: ${ownerClass.name}.${initMethod.name}")
        return AiComponentScanSymbols(
            imageViewerJumpButtonOwnerClass = ownerClass.name,
            imageViewerJumpButtonInitMethod = initMethod.name,
        )
    }

    private fun resolveImageViewerJumpButtonOwnerClass(
        candidateClassNames: List<String>,
        cl: ClassLoader,
        layoutClass: Class<*>,
        logger: ScanLogger?,
    ): Class<*>? {
        var skippedByReflection = 0
        var firstReflectionError: String? = null
        val matches = candidateClassNames
            .distinct()
            .mapNotNull { className ->
                try {
                    val clazz = safeFindClass(className, cl) ?: return@mapNotNull null
                    val score = scoreImageViewerJumpButtonOwnerClassForScan(clazz, layoutClass, logger)
                    if (score > 0) clazz to score else null
                } catch (t: Throwable) {
                    skippedByReflection++
                    if (firstReflectionError == null) {
                        firstReflectionError = sanitizeScanStatusText(formatScanException(t))
                    }
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<Class<*>, Int>> { it.second }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.name },
            )

        if (skippedByReflection > 0) {
            log(
                logger,
                "aiComponent.imageViewerJumpButton: skipped classes by reflection=$skippedByReflection" +
                    (firstReflectionError?.let { ", firstException=$it" } ?: ""),
            )
        }
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.imageViewerJumpButton: owner class not found")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.second - second.second < 20) {
            log(
                logger,
                "aiComponent.imageViewerJumpButton: owner ambiguous: " +
                    "best=${best.first.name}:${best.second}, second=${second.first.name}:${second.second}",
            )
            return null
        }
        return best.first
    }

    private fun scanImageViewerJumpButtonInitMethodFromDex(
        context: Context,
        ownerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.imageViewerJumpButtonDex: apk source path unavailable")
            return null
        }
        val ownerMethods = declaredMethodsForScan("ImageViewerJumpButton.${ownerClass.name}.Init", ownerClass, logger)
            ?: return null
        val matches = DexKitSemanticScanner.scanImageViewerJumpButtonInit(
            sourcePaths = sourcePaths,
            ownerClassName = ownerClass.name,
            logger = logger,
        )
            .filter { isImageViewerJumpButtonInitMethodName(ownerMethods, it.ownerMethodName) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        return pickImageViewerJumpButtonInitDexMatch(ownerClass, ownerMethods, matches, logger)
    }

    private fun pickImageViewerJumpButtonInitDexMatch(
        ownerClass: Class<*>,
        ownerMethods: List<Method>,
        matches: List<DexAiComponentInitMatch>,
        logger: ScanLogger?,
    ): Method? {
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.imageViewerJumpButtonDex: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 20) {
            log(
                logger,
                "aiComponent.imageViewerJumpButtonDex ambiguous: " +
                    "best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }
        val method = ownerMethods.singleOrNull { method ->
            method.name == best.ownerMethodName && isImageViewerJumpButtonInitMethod(method)
        } ?: run {
            log(logger, "aiComponent.imageViewerJumpButtonDex: reflection mismatch: ${ownerClass.name}.${best.ownerMethodName}")
            return null
        }
        log(
            logger,
            "aiComponent.imageViewerJumpButtonDex matched: ${ownerClass.name}.${method.name} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return method
    }

    private fun scanAiWriteInitMethodFromDex(
        context: Context,
        inputContainerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.aiWriteDex: apk source path unavailable")
            return null
        }
        val inputContainerMethods = declaredMethodsForScan("PbNewInputContainer.AiWriteInit", inputContainerClass, logger)
            ?: return null
        val dexMatches = DexKitSemanticScanner.scanAiWriteInit(
            sourcePaths = sourcePaths,
            ownerClassName = inputContainerClass.name,
            logger = logger,
        )
            .filter { isPbNewInputContextInitMethodName(inputContainerMethods, it.ownerMethodName) }
        val matches = dexMatches
            .filter { it.strong }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        if (matches.isEmpty()) {
            logAiComponentInitDexDiagnostics(logger, "aiWriteDex", dexMatches)
        }
        return pickAiComponentInitDexMatch(
            ownerClass = inputContainerClass,
            ownerMethods = inputContainerMethods,
            matches = matches,
            logger = logger,
            tag = "aiWriteDex",
        )
    }

    private fun scanSpriteMemeInitMethodFromDex(
        context: Context,
        inputContainerClass: Class<*>,
        logger: ScanLogger?,
    ): Method? {
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "aiComponent.spriteInitDex: apk source path unavailable")
            return null
        }
        val inputContainerMethods = declaredMethodsForScan("PbNewInputContainer.SpriteMemeInit", inputContainerClass, logger)
            ?: return null
        val matches = DexKitSemanticScanner.scanSpriteMemeInit(
            sourcePaths = sourcePaths,
            ownerClassName = inputContainerClass.name,
            logger = logger,
        )
            .filter { isPbNewInputContextInitMethodName(inputContainerMethods, it.ownerMethodName) }
            .groupBy { it.ownerMethodName }
            .mapNotNull { (_, methodMatches) -> methodMatches.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
        return pickAiComponentInitDexMatch(
            ownerClass = inputContainerClass,
            ownerMethods = inputContainerMethods,
            matches = matches,
            logger = logger,
            tag = "spriteInitDex",
        )
    }

    private fun logAiComponentInitDexDiagnostics(
        logger: ScanLogger?,
        tag: String,
        matches: List<DexAiComponentInitMatch>,
    ) {
        if (matches.isEmpty()) return
        val summary = matches
            .sortedWith(
                compareByDescending<DexAiComponentInitMatch> { it.score }
                    .thenBy { it.ownerMethodName.length }
                    .thenBy { it.ownerMethodName },
            )
            .take(3)
            .joinToString(";") { match ->
                "${match.ownerMethodName}:${match.score}[${match.evidence}]"
            }
        log(logger, "aiComponent.$tag partial candidates: $summary")
    }

    private fun pickAiComponentInitDexMatch(
        ownerClass: Class<*>,
        ownerMethods: List<Method>,
        matches: List<DexAiComponentInitMatch>,
        logger: ScanLogger?,
        tag: String,
    ): Method? {
        val best = matches.firstOrNull() ?: run {
            log(logger, "aiComponent.$tag: no semantic match")
            return null
        }
        val second = matches.getOrNull(1)
        if (second != null && best.score - second.score < 20) {
            log(
                logger,
                "aiComponent.$tag ambiguous: best=${best.ownerMethodName}:${best.score}[${best.evidence}], " +
                    "second=${second.ownerMethodName}:${second.score}[${second.evidence}]",
            )
            return null
        }
        val method = ownerMethods.singleOrNull { method ->
            method.name == best.ownerMethodName && isPbNewInputContextInitMethod(method)
        } ?: run {
            log(logger, "aiComponent.$tag: reflection mismatch: ${ownerClass.name}.${best.ownerMethodName}")
            return null
        }
        log(
            logger,
            "aiComponent.$tag matched: ${ownerClass.name}.${method.name} " +
                "score=${best.score} evidence=${best.evidence}",
        )
        return method
    }

    private fun resolveAiSpriteMemeEnableMethod(
        controllerClass: Class<*>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Method? {
        val inputShowTypeClass = safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
        val methods = declaredMethodsForScan("SpriteMemePanController.Enable", controllerClass, logger)
            ?: return null
        val candidates = methods.filter { method ->
            isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
        }
        if (candidates.size == 1) return candidates.first()
        val preferred = candidates.singleOrNull { it.name == AI_SPRITE_MEME_ENABLE_METHOD }
        if (preferred != null) return preferred
        log(
            logger,
            "aiComponent: SpriteMeme enable method " +
                "candidates=${candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" }}",
        )
        return null
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun hasKotlinMetadataSignal(clazz: Class<*>, signal: String): Boolean {
        val metadata = clazz.getAnnotation(Metadata::class.java) ?: return false
        return metadata.data1.any { it.contains(signal) } || metadata.data2.any { it.contains(signal) }
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

    private fun declaredMethodsForScan(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("AiComponentDisableHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun declaredFieldsForScan(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Field>? {
        return scanSubStep("AiComponentDisableHook.$label.Fields", logger, null) {
            clazz.declaredFields.toList()
        }
    }

    private fun declaredConstructorsForScan(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Constructor<*>>? {
        return scanSubStep("AiComponentDisableHook.$label.Constructors", logger, null) {
            clazz.declaredConstructors.toList()
        }
    }

    private fun formatScanException(t: Throwable): String =
        HookSymbolScanDiagnostics.formatScanException(t)

    private fun sanitizeScanStatusText(raw: String): String =
        HookSymbolScanDiagnostics.sanitizeScanStatusText(raw)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }

    private data class PageBrowserAiEmojiCreationPath(
        val viewClass: Class<*>,
        val bindMethod: Method,
    )

    private data class PageBrowserAiEmojiCreationCandidate(
        val viewClass: Class<*>,
        val bindMethod: Method,
        val score: Int,
    )
}
