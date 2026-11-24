package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageMethod
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.textSymbols
import org.junit.Assert.*
import org.junit.Test

class CacheRestorationPolicyTest {
    private val loader = javaClass.classLoader!!

    @Test fun negativeDiscoveryDoesNotPretendThatSettingsTargetsExist() {
        val empty = buildHookSymbols {}
        val negativeCacheOwners = listOf(InputMemeBarContract, AutoRefreshContract, AutoLoadMoreContract,
            FeedContract, BannerAdContract, VideoDisableContract, CommentFilterContract, CommentShortcutContract,
            AiImageCreationContract)
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
            AiImageCreationContract to listOf("aiImageCreation.INPUT_AI_VISIBLE"),
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

    @Test fun aiImageDescriptorsRoundTripAndPartialDiscoveryKeepsTheConsumerDisabled() {
        val visible = AiImageCreationContract.methodDescriptors.getValue(AiImageMethod.INPUT_AI_VISIBLE)
        val schema = AiImageCreationContract.methodDescriptors.getValue(AiImageMethod.INPUT_AI_SCHEMA)
        val partial = buildHookSymbols {
            this[visible] = AiImageCreationCodec.encode(AiInput::class.java.getDeclaredMethod("visible"))
            this[schema] = AiImageCreationCodec.encode(AiInput::class.java.getDeclaredMethod("schema"))
        }
        assertEquals(partial, HookSymbols.fromJson(partial.toJson()))
        assertTrue(AiImageCreationContract.isCacheValid(partial, loader))
        assertFalse(AiImageCreationContract.capabilities(partial).getValue(HookFeatureKey.DISABLE_AI_IMAGE_CREATION).isSupported())
        assertNull(AiImageCreationContract.resolveAiImageCreationSymbols(loader, partial))

        // A String accessor with the right signature still cannot belong to a different DTO.
        val wrongOwner = buildHookSymbols {
            this[visible] = partial[visible]
            this[schema] = AiImageCreationCodec.encode(OtherInput::class.java.getDeclaredMethod("schema"))
        }
        assertFalse(AiImageCreationContract.isCacheValid(wrongOwner, loader))
        val wrongReturn = buildHookSymbols {
            this[schema] = partial[visible]
        }
        assertFalse(AiImageCreationContract.isCacheValid(wrongReturn, loader))
    }

    class AiInput {
        fun visible() = 1
        fun schema() = ""
    }
    class OtherInput { fun schema() = "" }
}
