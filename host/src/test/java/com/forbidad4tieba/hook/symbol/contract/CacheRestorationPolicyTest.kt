package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.textSymbols
import org.junit.Assert.*
import org.junit.Test

class CacheRestorationPolicyTest {
    private val loader = javaClass.classLoader!!

    @Test fun negativeDiscoveryDoesNotPretendThatSettingsTargetsExist() {
        val empty = buildHookSymbols {}
        val negativeCacheOwners = listOf(InputMemeBarContract, AutoRefreshContract, AutoLoadMoreContract,
            FeedContract, BannerAdContract, VideoDisableContract, CommentFilterContract, CommentShortcutContract)
        for (contract in negativeCacheOwners) assertTrue(contract.id, contract.isCacheValid(empty, loader))
        // Settings requires its menu targets; its validator does not admit an empty result.
        assertFalse(SettingsContract.isCacheValid(empty, loader))
    }

    @Test fun presentMalformedDescriptorsAreRejectedByTheirOwners() {
        val cases = listOf(
            CrashReportContract to listOf("crashReportExceptionMethod"),
            PbTopEnterBarContract to listOf("pbTopEnterBarControllerClass", "pbTopEnterBarInitializeMethod",
                "pbTopEnterBarContainerField", "pbTopEnterBarNavigationBarField"),
            CommentShortcutContract to listOf("commentShortcutTargets"),
            CommentFilterContract to listOf("commentFilterPageNative", "commentFilterPageJson", "commentFilterFloorNative",
                "commentFilterFloorJson", "commentFilterProtocol"),
            LzlSortContract to listOf("lzlDefaultSortSetter"),
        )
        for ((contract, keys) in cases) assertFalse(contract.id, contract.isCacheValid(textSymbols(keys), loader))
    }

    @Test fun exactCrashNameRestoresAndMissingOptionalReportDoesNotDisableAnyCapability() {
        val valid = buildHookSymbols { this[CrashReportContract.crashReportExceptionMethod] = "renamed" }
        assertEquals("renamed", CrashReportContract.resolveExceptionReportMethod(loader, valid)?.name)
        val absent = buildHookSymbols {}
        assertNull(CrashReportContract.resolveExceptionReportMethod(loader, absent))
        assertFalse(CrashReportContract.points(absent).single().isUnavailable())
        assertTrue(CrashReportContract.capabilities(absent).isEmpty())
    }
}
