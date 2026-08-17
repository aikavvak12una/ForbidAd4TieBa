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
    fun tiebaHostLoggingInstallsOnlyForEnabledMainProcess() {
        val symbols = buildHookSymbols {}
        val enabled = SettingsSnapshot(isDetailedLoggingEnabled = true)
        val disabled = SettingsSnapshot(isDetailedLoggingEnabled = false)

        assertTrue(
            HookInstallPlanner.postAttachPlan(Constants.TARGET_PACKAGE, symbols, enabled)
                .entries
                .any { it.id == "TiebaHostLogHook" },
        )
        assertFalse(
            HookInstallPlanner.postAttachPlan(Constants.TARGET_PACKAGE, symbols, disabled)
                .entries
                .any { it.id == "TiebaHostLogHook" },
        )
        assertFalse(
            HookInstallPlanner.postAttachPlan(
                Constants.TARGET_PACKAGE + ":remote",
                symbols,
                enabled,
            ).entries.any { it.id == "TiebaHostLogHook" },
        )
    }

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
    fun freeCopyCommentInjectionRequiresEnabledSettingsAndEveryRuntimeTarget() {
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
        val enabled = SettingsSnapshot(
            isFreeCopyEnabled = true,
            isFreeCopyCommentInjectionEnabled = true,
        )

        assertFalse(incomplete.canInstallFreeCopyCommentInjection(enabled))
        assertTrue(complete.canInstallFreeCopyCommentInjection(enabled))
        assertFalse(
            complete.canInstallFreeCopyCommentInjection(
                enabled.copy(isFreeCopyEnabled = false),
            ),
        )
        assertFalse(
            complete.canInstallFreeCopyCommentInjection(
                enabled.copy(isFreeCopyCommentInjectionEnabled = false),
            ),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE + ":remote", complete.symbols)
                .canInstallFreeCopyCommentInjection(enabled),
        )
    }

    @Test
    fun freeCopyNativeRequiresEnabledSettingsAndAtLeastOneSupportedChild() {
        val incomplete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {},
        )
        val complete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                freeCopyPostDataClass = "com.tieba.PostData"
                freeCopyPostCopyMethodSpec = "copy|void|"
                freeCopyPostParseMethodSpec = "parse|void|tbclient.Post"
            },
        )
        val enabled = SettingsSnapshot(
            isFreeCopyEnabled = true,
            isFreeCopyPostBodyEnabled = true,
            isFreeCopyPostLongPressEnabled = false,
            isFreeCopyCommentDialogEnabled = false,
        )

        assertFalse(incomplete.canInstallFreeCopyNative(enabled))
        assertTrue(complete.canInstallFreeCopyNative(enabled))
        assertFalse(complete.canInstallFreeCopyNative(enabled.copy(isFreeCopyEnabled = false)))
        assertFalse(
            complete.canInstallFreeCopyNative(
                enabled.copy(isFreeCopyPostBodyEnabled = false),
            ),
        )
        assertTrue(
            complete.canInstallFreeCopyNative(
                enabled.copy(
                    isFreeCopyPostBodyEnabled = false,
                    isFreeCopyCommentDialogEnabled = true,
                ),
            ),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE + ":remote", complete.symbols)
                .canInstallFreeCopyNative(enabled),
        )
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
    fun inputMemeBarBlockRequiresEnabledMainProcessAndReadySymbols() {
        val settings = SettingsSnapshot(isInputMemeBarHidden = true)
        val readySymbols = buildHookSymbols {
            inputMemeBarControllerClass = "com.tieba.SpriteMemePanController"
            inputMemeBarEnableMethod = "enabled"
        }

        assertTrue(
            HookInstallContext(Constants.TARGET_PACKAGE, readySymbols)
                .canInstallInputMemeBarBlock(settings),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE + ":remote", readySymbols)
                .canInstallInputMemeBarBlock(settings),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, buildHookSymbols {})
                .canInstallInputMemeBarBlock(settings),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, readySymbols)
                .canInstallInputMemeBarBlock(SettingsSnapshot()),
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
        val recyclerDataPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                recyclerViewTypeAdapterSetDataMethod = "setData"
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
        val firstFloorRecommendPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            buildHookSymbols {
                pbFirstFloorRecommendInsertClass = "com.tieba.LegacyHeader"
                pbFirstFloorRecommendInsertMethod = "insertRecommend"
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
        assertFalse(dataPath.canInstallPbFirstFloorRecommendBlock(settings))
        assertFalse(dataPath.canInstallPbFallingAdBlock(settings))

        assertTrue(recyclerDataPath.canInstallPostAdBlock(settings))
        assertFalse(recyclerDataPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(recyclerDataPath.canInstallPbFirstFloorRecommendBlock(settings))
        assertFalse(recyclerDataPath.canInstallPbFallingAdBlock(settings))

        assertFalse(earlyPath.canInstallPostAdBlock(settings))
        assertTrue(earlyPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(earlyPath.canInstallPbFirstFloorRecommendBlock(settings))
        assertFalse(earlyPath.canInstallPbFallingAdBlock(settings))

        assertFalse(firstFloorRecommendPath.canInstallPostAdBlock(settings))
        assertFalse(firstFloorRecommendPath.canInstallPbEarlyAdBlock(settings))
        assertTrue(
            firstFloorRecommendPath.canInstallPbFirstFloorRecommendBlock(settings),
        )
        assertFalse(firstFloorRecommendPath.canInstallPbFallingAdBlock(settings))

        assertFalse(fallingPath.canInstallPostAdBlock(settings))
        assertFalse(fallingPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(fallingPath.canInstallPbFirstFloorRecommendBlock(settings))
        assertTrue(fallingPath.canInstallPbFallingAdBlock(settings))
    }

    @Test
    fun homeBottomEasterEggAdRequiresMainProcessEnabledSettingAndParser() {
        val symbols = buildHookSymbols {
            homeBottomEasterEggParserClass = "com.tieba.EasterEggParser"
            homeBottomEasterEggParserMethod = "parseJson"
        }
        val enabled = SettingsSnapshot(isHomeBottomEasterEggAdBlockEnabled = true)

        assertTrue(
            HookInstallContext(Constants.TARGET_PACKAGE, symbols)
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
        assertTrue(
            HookInstallPlanner.symbolPlan(Constants.TARGET_PACKAGE, symbols, enabled)
                .entries.any { it.id == "HomeBottomEasterEggAdHook" },
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, symbols)
                .canInstallHomeBottomEasterEggAdBlock(SettingsSnapshot()),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE + ":remote", symbols)
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, buildHookSymbols {})
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
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
