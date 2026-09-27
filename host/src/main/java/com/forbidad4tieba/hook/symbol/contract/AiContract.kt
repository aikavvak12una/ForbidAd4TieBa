package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.AiComponentScanSymbols
import com.forbidad4tieba.hook.symbol.model.AiComponentSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageViewerJumpButtonSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.AiComponentSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method

/** Owns the cached descriptors and host rules for this capability. */
object AiContract : SymbolContract("Ai") {
    internal override val candidateClasses = listOf(
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePanController",
        "com.baidu.tbadk.editortools.pb.PbNewInputContainer",
    )

    private const val AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS =
        "com.baidu.tieba.pb.pagebrowser.comment.floor.meme.CommentFloorAiEmojiCreationView"

    val aiSpriteMemePanControllerClass = text("aiSpriteMemePanControllerClass")
    val aiSpriteMemeEnableMethod = text("aiSpriteMemeEnableMethod")
    val aiPbNewInputContainerClass = text("aiPbNewInputContainerClass")
    val aiPbNewInputContainerInitSpriteMemeMethod = text("aiPbNewInputContainerInitSpriteMemeMethod")
    val aiPbNewInputContainerInitAiWriteMethod = text("aiPbNewInputContainerInitAiWriteMethod")
    val aiPbAiEmojiCreationViewBindMethod = text("aiPbAiEmojiCreationViewBindMethod")
    val aiPbPageBrowserAiEmojiCreationViewClass = text("aiPbPageBrowserAiEmojiCreationViewClass")
    val aiPbPageBrowserAiEmojiCreationBindMethod = text("aiPbPageBrowserAiEmojiCreationBindMethod")
    val aiImageViewerJumpButtonOwnerClass = text("aiImageViewerJumpButtonOwnerClass")
    val aiImageViewerJumpButtonInitMethod = text("aiImageViewerJumpButtonInitMethod")

    fun isReady(symbols: HookSymbols): Boolean = listOf<Any?>(
        symbols[aiImageViewerJumpButtonOwnerClass],
        symbols[aiImageViewerJumpButtonInitMethod],
    ).all { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Int -> value != 0
            else -> false
        }
    }

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val aiComponentScan = runScanStep(
            "AiComponentDisableHook",
            logger,
            scanErrors,
            AiComponentScanSymbols(),
        ) {
            AiComponentSymbolScanner.scan(context, cl, candidatesWithWhitelist, logger)
        }

        val aiSpriteMemePanControllerClass: String? = aiComponentScan.spriteMemePanControllerClass

        val aiSpriteMemeEnableMethod: String? = aiComponentScan.spriteMemeEnableMethod

        val aiPbNewInputContainerClass: String? = aiComponentScan.pbNewInputContainerClass

        val aiPbNewInputContainerInitSpriteMemeMethod: String? = aiComponentScan.pbInitSpriteMemeMethod

        val aiPbNewInputContainerInitAiWriteMethod: String? = aiComponentScan.pbInitAiWriteMethod

        val aiPbAiEmojiCreationViewBindMethod: String? = aiComponentScan.pbAiEmojiCreationViewBindMethod

        val aiPbPageBrowserAiEmojiCreationViewClass: String? = aiComponentScan.pbPageBrowserAiEmojiCreationViewClass

        val aiPbPageBrowserAiEmojiCreationBindMethod: String? = aiComponentScan.pbPageBrowserAiEmojiCreationBindMethod

        val aiImageViewerJumpButtonOwnerClass: String? = aiComponentScan.imageViewerJumpButtonOwnerClass

        val aiImageViewerJumpButtonInitMethod: String? = aiComponentScan.imageViewerJumpButtonInitMethod

        output[AiContract.aiSpriteMemePanControllerClass] = aiSpriteMemePanControllerClass
        output[AiContract.aiSpriteMemeEnableMethod] = aiSpriteMemeEnableMethod
        output[AiContract.aiPbNewInputContainerClass] = aiPbNewInputContainerClass
        output[AiContract.aiPbNewInputContainerInitSpriteMemeMethod] = aiPbNewInputContainerInitSpriteMemeMethod
        output[AiContract.aiPbNewInputContainerInitAiWriteMethod] = aiPbNewInputContainerInitAiWriteMethod
        output[AiContract.aiPbAiEmojiCreationViewBindMethod] = aiPbAiEmojiCreationViewBindMethod
        output[AiContract.aiPbPageBrowserAiEmojiCreationViewClass] = aiPbPageBrowserAiEmojiCreationViewClass
        output[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod] = aiPbPageBrowserAiEmojiCreationBindMethod
        output[AiContract.aiImageViewerJumpButtonOwnerClass] = aiImageViewerJumpButtonOwnerClass
        output[AiContract.aiImageViewerJumpButtonInitMethod] = aiImageViewerJumpButtonInitMethod
    }

    private const val AI_SPRITE_MEME_PAN_CLASS =
        "com.baidu.tbadk.editortools.meme.pan.SpriteMemePan"

    private const val AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS =
        "com.baidu.tbadk.editortools.pb.PbNewEditorTool\$InputShowType"

    private const val AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS =
        "com.baidu.tbadk.coreExtra.view.ImageJumpButtonLayout"

    fun resolveAiComponentSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AiComponentSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[AiComponentDisableHook] skipped: scan symbols unavailable")
                return null
            }

            fun required(name: String, value: String?): String? {
                val normalized = value?.takeIf { it.isNotBlank() }
                if (normalized == null) {
                    Diagnostics.log("[AiComponentDisableHook] skipped: missing $name")
                }
                return normalized
            }

            val controllerClassName = required(
                "aiSpriteMemePanControllerClass",
                resolvedSymbols[AiContract.aiSpriteMemePanControllerClass],
            ) ?: return null
            val enableMethodName = required(
                "aiSpriteMemeEnableMethod",
                resolvedSymbols[AiContract.aiSpriteMemeEnableMethod],
            ) ?: return null
            val inputContainerClassName = required(
                "aiPbNewInputContainerClass",
                resolvedSymbols[AiContract.aiPbNewInputContainerClass],
            ) ?: return null
            val initSpriteMemeMethodName = required(
                "aiPbNewInputContainerInitSpriteMemeMethod",
                resolvedSymbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod],
            ) ?: return null
            val initAiWriteMethodName = required(
                "aiPbNewInputContainerInitAiWriteMethod",
                resolvedSymbols[AiContract.aiPbNewInputContainerInitAiWriteMethod],
            ) ?: return null
            val controllerClass = ScanReflection.safeFindClass(controllerClassName, cl) ?: run {
                Diagnostics.log("[AiComponentDisableHook] skipped: class not found: $controllerClassName")
                return null
            }
            val inputContainerClass = ScanReflection.safeFindClass(inputContainerClassName, cl) ?: run {
                Diagnostics.log("[AiComponentDisableHook] skipped: class not found: $inputContainerClassName")
                return null
            }
            val inputShowTypeClass = ScanReflection.safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
            val spriteMemePanClass = ScanReflection.safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)

            val enableMethod = controllerClass.declaredMethods.singleOrNull { method ->
                method.name == enableMethodName &&
                    AiComponentSymbolScanner.isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
            } ?: run {
                Diagnostics.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$controllerClassName.$enableMethodName",
                )
                return null
            }
            val initSpriteMemeMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initSpriteMemeMethodName &&
                    AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            } ?: run {
                Diagnostics.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initSpriteMemeMethodName",
                )
                return null
            }
            val initAiWriteMethod = inputContainerClass.declaredMethods.singleOrNull { method ->
                method.name == initAiWriteMethodName &&
                    AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            } ?: run {
                Diagnostics.log(
                    "[AiComponentDisableHook] skipped: method mismatch: " +
                        "$inputContainerClassName.$initAiWriteMethodName",
                )
                return null
            }
            if (!AiComponentSymbolScanner.isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) {
                Diagnostics.log("[AiComponentDisableHook] skipped: input container structure mismatch")
                return null
            }

            val pbAiEmojiCreationViewBindMethod = resolveAiPbEmojiCreationViewBindMethod(cl, resolvedSymbols)
            val pbPageBrowserAiEmojiCreationBindMethod =
                resolvePbPageBrowserAiEmojiCreationBindMethod(cl, resolvedSymbols)

            listOfNotNull(
                enableMethod,
                initSpriteMemeMethod,
                initAiWriteMethod,
                pbAiEmojiCreationViewBindMethod,
                pbPageBrowserAiEmojiCreationBindMethod,
            ).forEach { it.isAccessible = true }
            AiComponentSymbols(
                spriteMemeEnableMethod = enableMethod,
                pbInitSpriteMemeMethod = initSpriteMemeMethod,
                pbInitAiWriteMethod = initAiWriteMethod,
                pbAiEmojiCreationViewBindMethod = pbAiEmojiCreationViewBindMethod,
                pbPageBrowserAiEmojiCreationBindMethod = pbPageBrowserAiEmojiCreationBindMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[AiComponentDisableHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolveAiImageViewerJumpButtonSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): AiImageViewerJumpButtonSymbols? {
        val initMethod = resolveAiImageViewerJumpButtonInitMethod(cl, symbols) ?: return null
        return AiImageViewerJumpButtonSymbols(initMethod = initMethod)
    }

    private fun resolveAiPbEmojiCreationViewBindMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        return try {
            val methodName = symbols[AiContract.aiPbAiEmojiCreationViewBindMethod]?.takeIf { it.isNotBlank() } ?: return null
            val viewClass = ScanReflection.safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: return null
            viewClass.declaredMethods.singleOrNull { method ->
                method.name == methodName && AiComponentSymbolScanner.isAiPbEmojiCreationViewBindMethod(method, cl)
            }
        } catch (t: Throwable) {
            Diagnostics.log("[AiComponentDisableHook] AI emoji creation view bind resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePbPageBrowserAiEmojiCreationBindMethod(
        cl: ClassLoader,
        symbols: HookSymbols,
    ): Method? {
        return try {
            val methodName = symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod]?.takeIf { it.isNotBlank() } ?: return null
            val viewClassName = symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass]
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val viewClass = ScanReflection.safeFindClass(viewClassName, cl) ?: return null
            viewClass.declaredMethods.singleOrNull { method ->
                method.name == methodName && AiComponentSymbolScanner.isPbPageBrowserAiEmojiCreationBindMethod(method)
            }
        } catch (t: Throwable) {
            Diagnostics.log("[AiComponentDisableHook] page browser AI emoji creation bind resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolveAiImageViewerJumpButtonInitMethod(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): Method? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[AiComponentDisableHook] image viewer skipped: scan symbols unavailable")
                return null
            }
            val ownerClassName = resolvedSymbols[AiContract.aiImageViewerJumpButtonOwnerClass]?.takeIf { it.isNotBlank() }
            val initMethodName = resolvedSymbols[AiContract.aiImageViewerJumpButtonInitMethod]?.takeIf { it.isNotBlank() }
            if (ownerClassName == null || initMethodName == null) {
                Diagnostics.log("[AiComponentDisableHook] image viewer skipped: scan symbols missing")
                return null
            }

            val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl)
            val layoutClass = ScanReflection.safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl)
            if (ownerClass == null || layoutClass == null) {
                Diagnostics.log(
                    "[AiComponentDisableHook] image viewer skipped: class not found: " +
                        "$ownerClassName / $AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS",
                )
                return null
            }
            if (AiComponentSymbolScanner.scoreImageViewerJumpButtonOwnerClass(ownerClass, layoutClass) <= 0) {
                Diagnostics.log("[AiComponentDisableHook] image viewer skipped: owner structure mismatch")
                return null
            }
            val initMethod = ownerClass.declaredMethods.singleOrNull { method ->
                method.name == initMethodName && AiComponentSymbolScanner.isImageViewerJumpButtonInitMethod(method)
            } ?: run {
                Diagnostics.log(
                    "[AiComponentDisableHook] image viewer skipped: method mismatch: " +
                        "$ownerClassName.$initMethodName",
                )
                return null
            }
            initMethod.isAccessible = true
            initMethod
        } catch (t: Throwable) {
            Diagnostics.log("[AiComponentDisableHook] image viewer symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val aiComponentCritical = ArrayList<String>(5)
        if (symbols[AiContract.aiSpriteMemePanControllerClass].isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemePanControllerClass")
        }
        if (symbols[AiContract.aiSpriteMemeEnableMethod].isNullOrBlank()) {
            aiComponentCritical.add("aiSpriteMemeEnableMethod")
        }
        if (symbols[AiContract.aiPbNewInputContainerClass].isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerClass")
        }
        if (symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod].isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitSpriteMemeMethod")
        }
        if (symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod].isNullOrBlank()) {
            aiComponentCritical.add("aiPbNewInputContainerInitAiWriteMethod")
        }
        val aiComponentOptional = ArrayList<String>(4)
        if (symbols[AiContract.aiPbAiEmojiCreationViewBindMethod].isNullOrBlank()) {
            aiComponentOptional.add("aiPbAiEmojiCreationViewBindMethod")
        }
        if (symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass].isNullOrBlank()) {
            aiComponentOptional.add("aiPbPageBrowserAiEmojiCreationViewClass")
        }
        if (symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod].isNullOrBlank()) {
            aiComponentOptional.add("aiPbPageBrowserAiEmojiCreationBindMethod")
        }
        if (symbols[AiContract.aiImageViewerJumpButtonOwnerClass].isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonOwnerClass")
        }
        if (symbols[AiContract.aiImageViewerJumpButtonInitMethod].isNullOrBlank()) {
            aiComponentOptional.add("aiImageViewerJumpButtonInitMethod")
        }
        out[HookFeatureKey.DISABLE_AI_COMPONENTS] = if (aiComponentCritical.isEmpty()) {
            if (aiComponentOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(state = HookFeatureState.PARTIAL, missingOptional = aiComponentOptional)
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = aiComponentCritical,
                missingOptional = aiComponentOptional,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "AiComponentDisableHook.SpriteMeme",
            "${symbols[AiContract.aiSpriteMemePanControllerClass]}.${symbols[AiContract.aiSpriteMemeEnableMethod]}",
            listOf(
                AiContract.aiSpriteMemePanControllerClass.check(symbols),
                AiContract.aiSpriteMemeEnableMethod.check(symbols),
            ),
        )
        add(
            "AiComponentDisableHook.PbNewInputContainer",
            "${symbols[AiContract.aiPbNewInputContainerClass]}.{${symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod]},${symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod]}}",
            listOf(
                AiContract.aiPbNewInputContainerClass.check(symbols),
                AiContract.aiPbNewInputContainerInitSpriteMemeMethod.check(symbols),
                AiContract.aiPbNewInputContainerInitAiWriteMethod.check(symbols),
            ),
        )
        add(
            "AiComponentDisableHook.PbAiEmojiCreation",
            "${AI_PB_AI_EMOJI_CREATION_VIEW_CLASS}.${symbols[AiContract.aiPbAiEmojiCreationViewBindMethod]}",
            listOf(
                AiContract.aiPbAiEmojiCreationViewBindMethod.check(symbols),
            ),
        )
        addOptional(
            "AiComponentDisableHook.PbPageBrowserAiEmojiCreation",
            if (
                has(symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass]) &&
                has(symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod])
            ) {
                "${symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass] ?: AI_PB_AI_EMOJI_CREATION_PAGE_BROWSER_VIEW_CLASS}.${symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod]}"
            } else {
                "optional-absent"
            },
            listOf(
                "aiPbPageBrowserAiEmojiCreationViewClass" to
                    has(symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass]),
                "aiPbPageBrowserAiEmojiCreationBindMethod" to
                    has(symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod]),
            ),
        )
        add(
            "AiComponentDisableHook.ImageViewerJumpButton",
            "${symbols[AiContract.aiImageViewerJumpButtonOwnerClass]}.${symbols[AiContract.aiImageViewerJumpButtonInitMethod]}",
            listOf(
                AiContract.aiImageViewerJumpButtonOwnerClass.check(symbols),
                AiContract.aiImageViewerJumpButtonInitMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("AiComponentDisableHook.", true, listOf(HookFeatureKey.DISABLE_AI_COMPONENTS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasAiComponentSymbols =
            symbols[AiContract.aiSpriteMemePanControllerClass] != null ||
                symbols[AiContract.aiSpriteMemeEnableMethod] != null ||
                symbols[AiContract.aiPbNewInputContainerClass] != null ||
                symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod] != null ||
                symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod] != null ||
                symbols[AiContract.aiPbAiEmojiCreationViewBindMethod] != null ||
                symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass] != null ||
                symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod] != null ||
                symbols[AiContract.aiImageViewerJumpButtonOwnerClass] != null ||
                symbols[AiContract.aiImageViewerJumpButtonInitMethod] != null
        if (hasAiComponentSymbols && !isAiComponentValid(symbols, cl)) return false
        return true
    }

    private const val AI_PB_AI_EMOJI_CREATION_VIEW_CLASS =
        "com.baidu.tieba.pb.view.PbAiEmojiCreationView"

    private fun isAiComponentValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClassName = symbols[AiContract.aiSpriteMemePanControllerClass] ?: return false
        val enableMethodName = symbols[AiContract.aiSpriteMemeEnableMethod] ?: return false
        val inputContainerClassName = symbols[AiContract.aiPbNewInputContainerClass] ?: return false
        val initSpriteMemeMethodName = symbols[AiContract.aiPbNewInputContainerInitSpriteMemeMethod] ?: return false
        val initAiWriteMethodName = symbols[AiContract.aiPbNewInputContainerInitAiWriteMethod] ?: return false
        return try {
            val controllerClass = ScanReflection.safeFindClass(controllerClassName, cl) ?: return false
            val inputContainerClass = ScanReflection.safeFindClass(inputContainerClassName, cl) ?: return false
            val inputShowTypeClass = ScanReflection.safeFindClass(AI_PB_NEW_EDITOR_INPUT_SHOW_TYPE_CLASS, cl)
            val spriteMemePanClass = ScanReflection.safeFindClass(AI_SPRITE_MEME_PAN_CLASS, cl)
            if (!AiComponentSymbolScanner.isAiPbNewInputContainerClassValid(inputContainerClass, spriteMemePanClass)) return false
            val enableOk = controllerClass.declaredMethods.any { method ->
                method.name == enableMethodName && AiComponentSymbolScanner.isAiSpriteMemeEnableMethod(method, inputShowTypeClass)
            }
            if (!enableOk) return false
            val initSpriteOk = inputContainerClass.declaredMethods.any { method ->
                method.name == initSpriteMemeMethodName && AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            }
            if (!initSpriteOk) return false
            val initAiWriteOk = inputContainerClass.declaredMethods.any { method ->
                method.name == initAiWriteMethodName && AiComponentSymbolScanner.isPbNewInputContextInitMethod(method)
            }
            if (!initAiWriteOk) return false

            if (!isAiPbEmojiCreationValid(symbols, cl)) return false

            val imageViewerOwnerName = symbols[AiContract.aiImageViewerJumpButtonOwnerClass]
            val imageViewerInitName = symbols[AiContract.aiImageViewerJumpButtonInitMethod]
            if (!imageViewerOwnerName.isNullOrBlank() || !imageViewerInitName.isNullOrBlank()) {
                if (imageViewerOwnerName.isNullOrBlank() || imageViewerInitName.isNullOrBlank()) return false
                val layoutClass = ScanReflection.safeFindClass(AI_IMAGE_JUMP_BUTTON_LAYOUT_CLASS, cl) ?: return false
                val ownerClass = ScanReflection.safeFindClass(imageViewerOwnerName, cl) ?: return false
                if (AiComponentSymbolScanner.scoreImageViewerJumpButtonOwnerClass(ownerClass, layoutClass) <= 0) return false
                ownerClass.declaredMethods.any { method ->
                    method.name == imageViewerInitName && AiComponentSymbolScanner.isImageViewerJumpButtonInitMethod(method)
                }
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isAiPbEmojiCreationValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val viewBindName = symbols[AiContract.aiPbAiEmojiCreationViewBindMethod]
        if (!viewBindName.isNullOrBlank()) {
            val viewClass = ScanReflection.safeFindClass(AI_PB_AI_EMOJI_CREATION_VIEW_CLASS, cl) ?: return false
            val bindOk = viewClass.declaredMethods.any { method ->
                method.name == viewBindName && AiComponentSymbolScanner.isAiPbEmojiCreationViewBindMethod(method, cl)
            }
            if (!bindOk) return false
        }

        val pageBrowserViewName = symbols[AiContract.aiPbPageBrowserAiEmojiCreationViewClass]
        val pageBrowserBindName = symbols[AiContract.aiPbPageBrowserAiEmojiCreationBindMethod]
        if (!pageBrowserViewName.isNullOrBlank() || !pageBrowserBindName.isNullOrBlank()) {
            val viewClassName = pageBrowserViewName ?: return false
            if (pageBrowserBindName.isNullOrBlank()) return false
            val viewClass = ScanReflection.safeFindClass(viewClassName, cl) ?: return false
            val bindOk = viewClass.declaredMethods.any { method ->
                method.name == pageBrowserBindName && AiComponentSymbolScanner.isPbPageBrowserAiEmojiCreationBindMethod(method)
            }
            if (!bindOk) return false
        }
        return true
    }
}
