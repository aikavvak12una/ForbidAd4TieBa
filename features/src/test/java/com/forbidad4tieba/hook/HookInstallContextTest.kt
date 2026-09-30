package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SimpleToggle
import com.forbidad4tieba.hook.feature.ad.*
import com.forbidad4tieba.hook.feature.ui.*
import com.forbidad4tieba.hook.feature.perf.*
import com.forbidad4tieba.hook.feature.share.*
import com.forbidad4tieba.hook.feature.shared.*
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallContextTest {
    @Test
    fun homeTabCatalogObservesWithCustomizationOffButFollowedWebStillRequiresIt() {
        val symbols = cachedSymbols {
            this["homeTabClass"] = "com.tieba.HomeTabs"
            this["homeTabRebuildMethod"] = "rebuild"
            this["homeTabListField"] = "tabs"
            this["homeTabItemTypeField"] = "type"
            this["homeTabItemCodeField"] = "code"
            this["homeTabItemNameField"] = "name"
            this["homeTabItemUrlField"] = "url"
        }
        val disabled = SettingsSnapshot.bootstrap()
        val enabled = SettingsSnapshot.bootstrap().copy(isHomeTopTabsCustomEnabled = true)
        for (settings in listOf(disabled, enabled)) {
            assertEquals(
                1,
                FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, symbols, settings)
                    .entries.count { it.id == "HomeTabHook" },
            )
            assertFalse(
                FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE + ":remote", symbols, settings)
                    .entries.any { it.id == "HomeTabHook" },
            )
        }
        assertFalse(
            FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, cachedSymbols {}, disabled)
                .entries.any { it.id == "HomeTabHook" },
        )
        assertFalse(HookInstallContext(Constants.TARGET_PACKAGE, symbols).canInstallHomeTopTabs(disabled))
        assertTrue(HookInstallContext(Constants.TARGET_PACKAGE, symbols).canInstallHomeTopTabs(enabled))
        assertFalse(
            FeatureCatalog.postAttachPlan(Constants.TARGET_PACKAGE, symbols, disabled)
                .entries.any { it.id == "FollowedTabWebHook" },
        )
        assertTrue(
            FeatureCatalog.postAttachPlan(Constants.TARGET_PACKAGE, symbols, enabled)
                .entries.any { it.id == "FollowedTabWebHook" },
        )
    }

    @Test
    fun feedListHookIsSharedByStrategyAndFeedSettingsOnlyInMainProcess() {
        val symbols = cachedSymbols {
            this["feedTemplateKeyMethod"] = "templateKey"
            this["feedTemplateLoadMoreMethod"] = "loadMore"
        }
        val enabledSettings = listOf(
            SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true),
            SettingsSnapshot.bootstrap().copy(isFeedAdBlockEnabled = true),
            SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true, isFeedAdBlockEnabled = true),
        )
        enabledSettings.forEach { settings ->
            assertEquals(
                1,
                FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, symbols, settings)
                    .entries.count { it.id == "FeedAdHook" },
            )
            assertFalse(
                FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE + ":remote", symbols, settings)
                    .entries.any { it.id == "FeedAdHook" },
            )
        }
        assertFalse(
            FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, symbols, SettingsSnapshot.bootstrap())
                .entries.any { it.id == "FeedAdHook" },
        )
    }

    @Test
    fun missingFeedSymbolsLeaveOtherStrategyPathsAvailableAndReportPartialStatus() {
        val settings = SettingsSnapshot.bootstrap().copy(isStrategyAdBlockEnabled = true)
        val symbols = cachedSymbols {
            this["splashAdHelperClass"] = "com.tieba.SplashAdHelper"
            this["splashAdHelperMethod"] = "showSplash"
        }
        val entries = FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, symbols, settings).entries
        val status = HookSymbolResolver.featureStatusMap(symbols).getValue(HookFeatureKey.BLOCK_AD_STRATEGY)

        assertFalse(entries.any { it.id == "FeedAdHook" })
        assertTrue(entries.any { it.id == "StrategyAdHook.symbols" })
        assertEquals(HookFeatureState.PARTIAL, status.state)
        assertTrue(status.missingOptional.contains("feedTemplateKeyMethod"))
        assertTrue(status.missingOptional.contains("feedTemplateLoadMoreMethod"))

        val initialListOnly = cachedSymbols { this["feedTemplateKeyMethod"] = "templateKey" }
        assertTrue(
            FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, initialListOnly, settings)
                .entries.any { it.id == "FeedAdHook" },
        )
        assertTrue(
            HookSymbolResolver.featureStatusMap(initialListOnly).getValue(HookFeatureKey.BLOCK_AD_STRATEGY)
                .missingOptional.contains("feedTemplateLoadMoreMethod"),
        )
    }

    @Test
    fun tiebaHostLoggingInstallsOnlyForEnabledMainProcess() {
        val symbols = cachedSymbols {}
        val enabled = SettingsSnapshot.bootstrap().copy(isDetailedLoggingEnabled = true)
        val disabled = SettingsSnapshot.bootstrap().copy(isDetailedLoggingEnabled = false)

        assertTrue(
            FeatureCatalog.postAttachPlan(Constants.TARGET_PACKAGE, symbols, enabled)
                .entries
                .any { it.id == "TiebaHostLogHook" },
        )
        assertFalse(
            FeatureCatalog.postAttachPlan(Constants.TARGET_PACKAGE, symbols, disabled)
                .entries
                .any { it.id == "TiebaHostLogHook" },
        )
        assertFalse(
            FeatureCatalog.postAttachPlan(
                Constants.TARGET_PACKAGE + ":remote",
                symbols,
                enabled,
            ).entries.any { it.id == "TiebaHostLogHook" },
        )
    }

    @Test
    fun requiredEvidenceAllowsInstallWhenOnlyOptionalSymbolsAreMissing() {
        val symbols = cachedSymbols {
            this["homeTabClass"] = "com.tieba.HomeTabs"
            this["homeTabRebuildMethod"] = "rebuild"
            this["homeTabListField"] = "tabs"
            this["homeTabItemTypeField"] = "type"
            this["homeTabItemCodeField"] = "code"
            this["homeTabItemNameField"] = "name"
            this["homeTabItemUrlField"] = "url"

            this["mainTabDataClass"] = "com.tieba.MainTabs"
            this["mainTabAddMethod"] = "add"
            this["mainTabGetListMethod"] = "getTabs"
            this["mainTabDelegateGetStructureMethod"] = "getStructure"
            this["mainTabStructureTypeField"] = "type"

            this["origImageUrlDragImageViewClass"] = "com.tieba.UrlDragImageView"
            this["origImageDataClass"] = "com.tieba.ImageData"
            this["origImageAssistDataMethod"] = "getAssistData"
            this["origImageShowButtonField"] = "showButton"
            this["origImageBlockedField"] = "blocked"
            this["origImageOriginalProcessField"] = "originalProcess"
            this["origImageOriginalUrlField"] = "originalUrl"
            this["origImageTriggerMethod"] = "loadOriginal"

            this["aiSpriteMemePanControllerClass"] = "com.tieba.SpriteMemeController"
            this["aiSpriteMemeEnableMethod"] = "setEnabled"
            this["aiPbNewInputContainerClass"] = "com.tieba.PbInput"
            this["aiPbNewInputContainerInitSpriteMemeMethod"] = "initSpriteMeme"
            this["aiPbNewInputContainerInitAiWriteMethod"] = "initAiWrite"
        }
        val context = HookInstallContext(Constants.TARGET_PACKAGE, symbols)

        assertTrue(
            context.canInstallHomeTopTabs(
                SettingsSnapshot.bootstrap().copy(isHomeTopTabsCustomEnabled = true),
            ),
        )
        assertTrue(
            context.canInstallBottomTabs(
                SettingsSnapshot.bootstrap().copy(isBottomTabsCustomEnabled = true),
            ),
        )
        assertTrue(
            DefaultOriginalImageFeature.entries(
                context, SettingsSnapshot.bootstrap().copy(simpleToggles = setOf(SimpleToggle.DEFAULT_ORIGINAL_IMAGE)),
            ).isNotEmpty(),
        )
        assertTrue(
            context.canInstallMainAiComponents(
                SettingsSnapshot.bootstrap().copy(isAiComponentsDisabled = true),
            ),
        )
    }

    @Test
    fun freeCopyCommentInjectionRequiresEnabledSettingsAndEveryRuntimeTarget() {
        val incomplete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["freeCopyPopupMenuClass"] = "com.tieba.Popup"
                this["freeCopyPopupContentViewMethod"] = "getContentView"
            },
        )
        val complete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["freeCopyPopupMenuClass"] = "com.tieba.Popup"
                this["freeCopyPopupContentViewMethod"] = "getContentView"
                this["freeCopyPopupTextField"] = "text"
            },
        )
        val enabled = SettingsSnapshot.bootstrap().copy(
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
            cachedSymbols {},
        )
        val complete = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["freeCopyPostDataClass"] = "com.tieba.PostData"
                this["freeCopyPostCopyMethodSpec"] = "copy|void|"
                this["freeCopyPostParseMethodSpec"] = "parse|void|tbclient.Post"
            },
        )
        val enabled = SettingsSnapshot.bootstrap().copy(
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
        val requiredFields = listOf<JSONObject.() -> Unit>(
            { this["imageViewerShareConfigClass"] = null },
            { this["imageViewerShareIsDialogField"] = null },
            { this["imageViewerShareItemField"] = null },
            { this["imageViewerShareAddOutsideMethod"] = null },
            { this["imageViewerShareGetRequestDataMethod"] = null },
            { this["imageViewerShareSetRequestDataMethod"] = null },
            { this["imageViewerShareGetContextMethod"] = null },
            { this["imageViewerShareItemClass"] = null },
            { this["imageViewerShareItemImageUriField"] = null },
            { this["imageViewerShareItemViewClass"] = null },
            { this["imageViewerShareItemNameByResMethod"] = null },
            { this["imageViewerShareItemNameByTextMethod"] = null },
            { this["imageViewerShareIconResId"] = 0 },
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
        val symbols = cachedSymbols {
            this["aiImageViewerJumpButtonOwnerClass"] = "com.tieba.ImageViewer"
            this["aiImageViewerJumpButtonInitMethod"] = "initAiButton"
        }
        val settings = SettingsSnapshot.bootstrap().copy(isAiComponentsDisabled = true)

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
        val settings = SettingsSnapshot.bootstrap().copy(isInputMemeBarHidden = true)
        val readySymbols = cachedSymbols {
            this["inputMemeBarControllerClass"] = "com.tieba.SpriteMemePanController"
            this["inputMemeBarEnableMethod"] = "enabled"
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
            HookInstallContext(Constants.TARGET_PACKAGE, cachedSymbols {})
                .canInstallInputMemeBarBlock(settings),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, readySymbols)
                .canInstallInputMemeBarBlock(SettingsSnapshot.bootstrap()),
        )
    }

    @Test
    fun postAdAggregateInstallsOnlyEachReadyScannedSubpath() {
        val settings = SettingsSnapshot.bootstrap().copy(isPostPageAdBlockEnabled = true)
        val dataPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["typeAdapterSetDataMethod"] = "setData"
                this["typeAdapterDataItemClass"] = "com.tieba.PostItem"
                this["typeAdapterDataGetTypeMethod"] = "getType"
            },
        )
        val recyclerDataPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["recyclerViewTypeAdapterSetDataMethod"] = "setData"
                this["typeAdapterDataItemClass"] = "com.tieba.PostItem"
                this["typeAdapterDataGetTypeMethod"] = "getType"
            },
        )
        val earlyPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["pbEarlyAdInsertClass"] = "com.tieba.EarlyAd"
                this["pbEarlyAdInsertMethodSpecs"] = listOf("first", "second")
            },
        )
        val firstFloorRecommendPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["pbFirstFloorRecommendInsertClass"] = "com.tieba.LegacyHeader"
                this["pbFirstFloorRecommendInsertMethod"] = "insertRecommend"
            },
        )
        val fallingPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["pbFallingViewClass"] = "com.tieba.FallingAd"
                this["pbFallingInitMethod"] = "init"
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
        val symbols = cachedSymbols {
            this["homeBottomEasterEggParserClass"] = "com.tieba.EasterEggParser"
            this["homeBottomEasterEggParserMethod"] = "parseJson"
        }
        val enabled = SettingsSnapshot.bootstrap().copy(isHomeBottomEasterEggAdBlockEnabled = true)

        assertTrue(
            HookInstallContext(Constants.TARGET_PACKAGE, symbols)
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
        assertTrue(
            FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE, symbols, enabled)
                .entries.any { it.id == "HomeBottomEasterEggAdHook" },
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, symbols)
                .canInstallHomeBottomEasterEggAdBlock(SettingsSnapshot.bootstrap()),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE + ":remote", symbols)
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
        assertFalse(
            HookInstallContext(Constants.TARGET_PACKAGE, cachedSymbols {})
                .canInstallHomeBottomEasterEggAdBlock(enabled),
        )
    }

    private fun nativeShareSymbols(
        mutate: JSONObject.() -> Unit = {},
    ) = cachedSymbols {
        this["imageViewerShareConfigClass"] = "com.tieba.ShareConfig"
        this["imageViewerShareIsDialogField"] = "isDialog"
        this["imageViewerShareItemField"] = "shareItem"
        this["imageViewerShareAddOutsideMethod"] = "addOutside"
        this["imageViewerShareGetRequestDataMethod"] = "getRequestData"
        this["imageViewerShareSetRequestDataMethod"] = "setRequestData"
        this["imageViewerShareGetContextMethod"] = "getContext"
        this["imageViewerShareItemClass"] = "com.tieba.ShareItem"
        this["imageViewerShareItemImageUriField"] = "imageUri"
        this["imageViewerShareItemViewClass"] = "com.tieba.ShareItemView"
        this["imageViewerShareItemNameByResMethod"] = "setName"
        this["imageViewerShareItemNameByTextMethod"] = "setNameText"
        this["imageViewerShareIconResId"] = 1
        mutate()
    }
}

/** Feed the public cache boundary, without exposing host's internal scan builder. */
private fun cachedSymbols(block: JSONObject.() -> Unit): HookSymbols =
    requireNotNull(HookSymbols.fromJson(JSONObject().apply(block).toString()))

private operator fun JSONObject.set(key: String, value: Any?) {
    put(key, if (value is Collection<*>) JSONArray(value) else value)
}
