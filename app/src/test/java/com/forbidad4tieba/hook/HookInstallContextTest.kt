package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallContextTest {
    @Test
    fun requiredEvidenceAllowsInstallWhenOnlyOptionalSymbolsAreMissing() {
        val symbols = buildHookSymbols {
            homeTabClass = "com.tieba.HomeTabs"
            homeTabRebuildMethod = "rebuild"
            homeTabListField = "tabs"
            homeTabItemTypeField = "type"
            homeTabItemCodeField = "code"
            homeTabItemNameField = "name"
            homeTabItemUrlField = "url"

            mainTabDataClass = "com.tieba.MainTabs"
            mainTabAddMethod = "add"
            mainTabGetListMethod = "getTabs"
            mainTabDelegateGetStructureMethod = "getStructure"
            mainTabStructureTypeField = "type"

            origImageUrlDragImageViewClass = "com.tieba.UrlDragImageView"
            origImageDataClass = "com.tieba.ImageData"
            origImageAssistDataMethod = "getAssistData"
            origImageShowButtonField = "showButton"
            origImageBlockedField = "blocked"
            origImageOriginalProcessField = "originalProcess"
            origImageOriginalUrlField = "originalUrl"
            origImageTriggerMethod = "loadOriginal"

            aiSpriteMemePanControllerClass = "com.tieba.SpriteMemeController"
            aiSpriteMemeEnableMethod = "setEnabled"
            aiPbNewInputContainerClass = "com.tieba.PbInput"
            aiPbNewInputContainerInitSpriteMemeMethod = "initSpriteMeme"
            aiPbNewInputContainerInitAiWriteMethod = "initAiWrite"
        }
        val context = HookInstallContext(Constants.TARGET_PACKAGE, symbols)

        assertTrue(
            context.canInstallHomeTopTabs(
                SettingsSnapshot(isHomeTopTabsCustomEnabled = true),
            ),
        )
        assertTrue(
            context.canInstallBottomTabs(
                SettingsSnapshot(isBottomTabsCustomEnabled = true),
            ),
        )
        assertTrue(
            context.canInstallDefaultOriginalImage(
                SettingsSnapshot(isDefaultOriginalImageEnabled = true),
            ),
        )
        assertTrue(
            context.canInstallMainAiComponents(
                SettingsSnapshot(isAiComponentsDisabled = true),
            ),
        )
    }

    @Test
    fun freeCopyRequiresEveryRuntimeTarget() {
        val incomplete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                freeCopyPopupMenuClass = "com.tieba.Popup"
                freeCopyPopupContentViewMethod = "getContentView"
            },
        )
        val complete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                freeCopyPopupMenuClass = "com.tieba.Popup"
                freeCopyPopupContentViewMethod = "getContentView"
                freeCopyPopupTextField = "text"
            },
        )

        assertFalse(incomplete.canInstallFreeCopy())
        assertTrue(complete.canInstallFreeCopy())
    }

    @Test
    fun nativeShareRejectsEveryMissingRequiredTarget() {
        val requiredFields = listOf<HookSymbolsBuilder.() -> Unit>(
            { imageViewerShareConfigClass = null },
            { imageViewerShareIsDialogField = null },
            { imageViewerShareItemField = null },
            { imageViewerShareAddOutsideMethod = null },
            { imageViewerShareGetRequestDataMethod = null },
            { imageViewerShareSetRequestDataMethod = null },
            { imageViewerShareGetContextMethod = null },
            { imageViewerShareItemClass = null },
            { imageViewerShareItemImageUriField = null },
            { imageViewerShareItemViewClass = null },
            { imageViewerShareItemNameByResMethod = null },
            { imageViewerShareItemNameByTextMethod = null },
            { imageViewerShareIconResId = 0 },
        )

        requiredFields.forEach { removeRequiredField ->
            assertFalse(
                HookInstallContext(
                    Constants.TARGET_PACKAGE,
                    nativeShareSymbols(removeRequiredField),
                ).canInstallImageViewerNativeShare(),
            )
        }
        assertTrue(
            HookInstallContext(
                Constants.TARGET_PACKAGE,
                nativeShareSymbols(),
            ).canInstallImageViewerNativeShare(),
        )
    }

    @Test
    fun imageViewerAiPathIsIndependentFromMainAiTargets() {
        val symbols = buildHookSymbols {
            aiImageViewerJumpButtonOwnerClass = "com.tieba.ImageViewer"
            aiImageViewerJumpButtonInitMethod = "initAiButton"
        }
        val settings = SettingsSnapshot(isAiComponentsDisabled = true)

        assertTrue(
            HookInstallContext(
                Constants.TARGET_PACKAGE + ":remote",
                symbols,
            ).canInstallImageViewerAiJumpButton(settings),
        )
        assertFalse(
            HookInstallContext(
                Constants.TARGET_PACKAGE,
                symbols,
            ).canInstallMainAiComponents(settings),
        )
    }

    @Test
    fun postAdAggregateInstallsOnlyEachReadyScannedSubpath() {
        val settings = SettingsSnapshot(isPostPageAdBlockEnabled = true)
        val dataPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                typeAdapterSetDataMethod = "setData"
                typeAdapterDataItemClass = "com.tieba.PostItem"
                typeAdapterDataGetTypeMethod = "getType"
            },
        )
        val earlyPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                pbEarlyAdInsertClass = "com.tieba.EarlyAd"
                pbEarlyAdInsertMethodSpecs = listOf("first", "second")
            },
        )
        val fallingPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                pbFallingViewClass = "com.tieba.FallingAd"
                pbFallingInitMethod = "init"
            },
        )

        assertTrue(dataPath.canInstallPostAdBlock(settings))
        assertFalse(dataPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(dataPath.canInstallPbFallingAdBlock(settings))

        assertFalse(earlyPath.canInstallPostAdBlock(settings))
        assertTrue(earlyPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(earlyPath.canInstallPbFallingAdBlock(settings))

        assertFalse(fallingPath.canInstallPostAdBlock(settings))
        assertFalse(fallingPath.canInstallPbEarlyAdBlock(settings))
        assertTrue(fallingPath.canInstallPbFallingAdBlock(settings))
    }

    private fun nativeShareSymbols(
        mutate: HookSymbolsBuilder.() -> Unit = {},
    ) = buildHookSymbols {
        imageViewerShareConfigClass = "com.tieba.ShareConfig"
        imageViewerShareIsDialogField = "isDialog"
        imageViewerShareItemField = "shareItem"
        imageViewerShareAddOutsideMethod = "addOutside"
        imageViewerShareGetRequestDataMethod = "getRequestData"
        imageViewerShareSetRequestDataMethod = "setRequestData"
        imageViewerShareGetContextMethod = "getContext"
        imageViewerShareItemClass = "com.tieba.ShareItem"
        imageViewerShareItemImageUriField = "imageUri"
        imageViewerShareItemViewClass = "com.tieba.ShareItemView"
        imageViewerShareItemNameByResMethod = "setName"
        imageViewerShareItemNameByTextMethod = "setNameText"
        imageViewerShareIconResId = 1
        mutate()
    }
}
