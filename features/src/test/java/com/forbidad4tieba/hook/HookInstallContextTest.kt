package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.feature.ad.canInstallPbEarlyAdBlock
import com.forbidad4tieba.hook.feature.ad.canInstallPostAdBlock
import com.forbidad4tieba.hook.feature.shared.canInstallHomeTopTabs
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallContextTest {
    @Test
    fun optionalObserverAndItsFollowedTabPrerequisiteStaySeparate() {
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
    fun feedAndBannerPathsAreIndependentUnderTheSharedFeedChoice() {
        val adapterOnly = cachedSymbols {
            this["feedTemplateKeyMethod"] = "templateKey"
            this["feedTemplateLoadMoreMethod"] = "loadMore"
        }
        val bannerOnly = cachedSymbols {
            this["bannerAdConversionMethodSpec"] = "example.HomeFeed#convert"
        }
        val both = cachedSymbols {
            this["feedTemplateKeyMethod"] = "templateKey"
            this["feedTemplateLoadMoreMethod"] = "loadMore"
            this["bannerAdConversionMethodSpec"] = "example.HomeFeed#convert"
        }
        assertEquals(setOf("FeedAdHook", "BannerAdHook"), feedEntries(both, feed = true))
        assertEquals(setOf("FeedAdHook"), feedEntries(both, strategy = true))
        assertEquals(setOf("BannerAdHook"), feedEntries(bannerOnly, feed = true))
        assertEquals(setOf("FeedAdHook"), feedEntries(adapterOnly, feed = true))
        assertTrue(feedEntries(cachedSymbols {}, feed = true).isEmpty())
        assertTrue(feedEntries(both, feed = true, process = Constants.TARGET_PACKAGE + ":remote").isEmpty())
        assertTrue(feedEntries(both).isEmpty())
    }

    @Test
    fun commentShortcutRequiresItsFilterPrerequisiteWhileTheFilterRemainsUsableAlone() {
        val filterFields = listOf(
            "commentFilterPageNative",
            "commentFilterPageJson",
            "commentFilterFloorNative",
            "commentFilterFloorJson",
            "commentFilterProtocol",
        )
        val allFields = filterFields + "commentShortcutTargets"
        assertTrue(planned("CommentShortcut", allFields))
        assertTrue(planned("CommentLevelFilter", allFields))
        assertFalse(planned("CommentShortcut", filterFields))
        assertFalse(planned("CommentLevelFilter", filterFields))
        assertTrue(planned("CommentLevelFilter", filterFields, filterEnabled = true))
        assertFalse(planned("CommentShortcut", allFields, process = Constants.TARGET_PACKAGE + ":extra"))
        assertFalse(planned("CommentShortcut", allFields, shortcut = false))
        assertFalse(planned("CommentLevelFilter", allFields, shortcut = false))
    }

    @Test
    fun postAdAggregateKeepsReadyScannedPathsIndependent() {
        val settings = SettingsSnapshot.bootstrap().copy(isPostPageAdBlockEnabled = true)
        val dataPath = HookInstallContext(
            Constants.TARGET_PACKAGE,
            cachedSymbols {
                this["typeAdapterSetDataMethod"] = "setData"
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
        assertTrue(dataPath.canInstallPostAdBlock(settings))
        assertFalse(dataPath.canInstallPbEarlyAdBlock(settings))
        assertFalse(earlyPath.canInstallPostAdBlock(settings))
        assertTrue(earlyPath.canInstallPbEarlyAdBlock(settings))
        assertTrue(
            FeatureCatalog.symbolPlan(Constants.TARGET_PACKAGE + ":remote", dataPath.symbols, settings)
                .entries.none { it.id == "PostAdHook" },
        )
    }

    private fun feedEntries(
        symbols: HookSymbols,
        feed: Boolean = false,
        strategy: Boolean = false,
        process: String = Constants.TARGET_PACKAGE,
    ): Set<String> = FeatureCatalog.symbolPlan(
        process,
        symbols,
        SettingsSnapshot.bootstrap().copy(
            isFeedAdBlockEnabled = feed,
            isStrategyAdBlockEnabled = strategy,
        ),
    ).entries.map { it.id }.filter { it == "FeedAdHook" || it == "BannerAdHook" }.toSet()

    private fun planned(
        name: String,
        fields: List<String>,
        filterEnabled: Boolean = false,
        shortcut: Boolean = true,
        process: String = Constants.TARGET_PACKAGE,
    ): Boolean {
        val baseline = SettingsSnapshot.bootstrap()
        val settings = baseline.copy(
            commentLevelFilter = baseline.commentLevelFilter.copy(enabled = filterEnabled),
            isCommentShortcutEnabled = shortcut,
        )
        return FeatureCatalog.definitions.single { it.id == name }
            .entries(HookInstallContext(process, symbols(fields)), settings)
            .isNotEmpty()
    }

    private fun symbols(fields: List<String>): HookSymbols = requireNotNull(
        HookSymbols.fromJson(fields.joinToString(prefix = "{", postfix = "}") { "\"$it\":\"fixture\"" }),
    )
}

private fun cachedSymbols(block: JSONObject.() -> Unit): HookSymbols =
    requireNotNull(HookSymbols.fromJson(JSONObject().apply(block).toString()))

private operator fun JSONObject.set(key: String, value: Any?) {
    put(key, if (value is Collection<*>) JSONArray(value) else value)
}
