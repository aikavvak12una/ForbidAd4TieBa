package com.forbidad4tieba.hook.feature.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.AbstractMap

class CloudDialogPolicyTest {
    @Test
    fun blacklistBlocksOnlyTheSixExactPromotionStructures() {
        val expected = linkedMapOf(
            "black:4" to input(
                mode = CloudDialogMode.BLACKLIST,
                name = "commercial_push_sign_pop",
                url = "https://tieba.baidu.com/mo/q/hybrid-main-user/signTaskDialog",
                strategies = listOf(
                    "NA_COMMERCIAL_PUSH_SIGN_POP_STRATEGY",
                    "NEW_FREQUENCE_STRATEGY",
                ),
                contextTime = "sync_finish",
            ),
            "black:6" to native(
                CloudDialogMode.BLACKLIST,
                "frsLightHouseGuide",
                "NA_FRS_LIGHTHOUSE_GUIDE_STRATEGY",
                "PAGE_STRATEGY",
            ),
            "black:8" to native(
                CloudDialogMode.BLACKLIST,
                "easterEgg",
                "NA_EASTER_EGG_STRATEGY",
                "FREQUENCE_STRATEGY",
            ),
            "black:48" to input(
                mode = CloudDialogMode.BLACKLIST,
                name = "frs_shop_master_popup",
                url = "https://tieba.baidu.com/mo/q/hybrid-main-dialog/shopOwnerInvite" +
                    "?needNotify=1&forum_id=fixture-forum",
                strategies = listOf("LIMIT_STRATEGY", "FREQUENCE_STRATEGY"),
                contextTime = "frs_page",
            ),
            "black:49" to input(
                mode = CloudDialogMode.BLACKLIST,
                name = "frs_shop_open_popup",
                url = "https://tieba.baidu.com/mo/q/hybrid-main-frs/shopOpenPopup" +
                    "?fid=fixture-forum&fname=fixture",
                strategies = listOf("FREQUENCE_STRATEGY", "LIMIT_STRATEGY"),
                contextTime = "frs_page",
            ),
            "black:54" to gta(
                mode = CloudDialogMode.BLACKLIST,
                dataUrl = "simpleDialog",
                dialogName = "gta6",
            ),
        )

        for ((rule, candidate) in expected) {
            val decision = CloudDialogPolicy.evaluate(candidate)
            assertTrue(rule, decision.block)
            assertEquals(rule, decision.matchedRule)
        }
    }

    @Test
    fun blacklistLeavesUnknownNamesAndReusedNamesWithChangedAuthorityAlone() {
        val candidates = listOf(
            input(
                CloudDialogMode.BLACKLIST,
                "operateNew",
                "dialoginternal://operateNew",
                listOf("NA_OPERATION_STRATEGY"),
                contextTime = "sync_finish",
            ),
            input(
                CloudDialogMode.BLACKLIST,
                "future_dialog",
                "https://tieba.baidu.com/mo/q/future",
                listOf("FUTURE_STRATEGY"),
            ),
            input(
                CloudDialogMode.BLACKLIST,
                "frsLightHouseGuide",
                "dialoginternal://differentComponent",
                listOf("NA_FRS_LIGHTHOUSE_GUIDE_STRATEGY"),
            ),
            input(
                CloudDialogMode.BLACKLIST,
                "commercial_push_sign_pop",
                "https://tieba.baidu.com/mo/q/hybrid-main-user/signTaskDialogV2",
                listOf("NA_COMMERCIAL_PUSH_SIGN_POP_STRATEGY"),
            ),
            input(
                CloudDialogMode.BLACKLIST,
                "gta6",
                "https://tieba.baidu.com/mo/q/hybrid-usergrow-activity/otherCard" +
                    "?dataUrl=simpleDialog&dialog_name=gta6",
                emptyList(),
            ),
        )

        for (candidate in candidates) {
            assertFalse(candidate.dialogName, CloudDialogPolicy.evaluate(candidate).block)
        }
    }

    @Test
    fun whitelistAllowsAllTwentyEightKnownBusinessContractsWithoutMatchingOriginalTimes() {
        val matchedRules = linkedSetOf<String>()
        val candidates = mutableListOf<CloudDialogPolicyInput>()

        val nativeRules = listOf(
            "frsWishingLights" to "NA_FRS_WISHING_LIGHTS_STRATEGY",
            "adDownloadConfirmDialog" to "NEW_FREQUENCE_STRATEGY",
            "frsCopyLinkGuide" to "NA_FRS_COPY_LINK_GUIDE_STRATEGY",
            "frsNewUserGuide" to "NA_FRS_NEWUSER_STRATEGY",
            "frsTopicInspirationGuide" to "NA_FRS_TOPIC_INSPIRATION_GUIDE_STRATEGY",
            "userIcon" to "NA_USER_ICON_STRATEGY",
            "userGrowth" to "NA_USER_GROWTH_STRATEGY",
            "newGod" to "NA_NEW_GOD_STRATEGY",
            "frsExam" to "NA_FRS_EXAM_STRATEGY",
            "frsGuide" to "NA_FRS_GUIDE_STRATEGY",
            "frsGroupChatGuide" to "NA_FRS_GROUP_CHAT_GUIDE_STRATEGY",
            "frsUpgrade" to "NA_FRS_UPGRADE_STRATEGY",
            "updateDialog" to "NA_UPDATE_STRATEGY",
            "lcUpdateDialog" to "FREQUENCE_STRATEGY",
            "frsFollowGuide" to "NA_FRS_FOLLOW_GUIDE_STRATEGY",
        )
        for ((index, rule) in nativeRules.withIndex()) {
            val opaqueFields = if (rule.first == "adDownloadConfirmDialog") {
                mapOf("dialog_data" to """{"url":"https://download.invalid/app.apk"}""")
            } else {
                emptyMap()
            }
            val strategies = when (index % 3) {
                0 -> listOf(
                    rule.second,
                    "UNIQUE_STRATEGY",
                    "PRECHECK_RENDER_STRATEGY",
                    "UNIQUE_STRATEGY",
                )
                1 -> listOf("PRECHECK_RENDER_STRATEGY", rule.second, "UNIQUE_STRATEGY")
                else -> listOf("UNIQUE_STRATEGY", rule.second, "PRECHECK_RENDER_STRATEGY")
            }
            candidates.add(native(
                CloudDialogMode.WHITELIST,
                rule.first,
                strategies = strategies,
                fields = opaqueFields,
                contextTime = "default",
            ))
        }

        candidates.add(input(
            CloudDialogMode.WHITELIST,
            "PublishGuideDialog",
            "https://tieba.baidu.com/mo/q/hybrid-main-dialog/postGuideMask" +
                "?customfullscreen=1&nonavigationbar=1",
            listOf("PAGE_STRATEGY", "PRECHECK_RENDER_STRATEGY", "PUBLISH_GUIDE_STRATEGY"),
            emptyMap(),
            "default",
        ))
        candidates.add(input(
            CloudDialogMode.WHITELIST,
            "common_sprite_dialog",
            "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot-claim" +
                "?customfullscreen=1&nonavigationbar=1&loadingSignal=1" +
                "&time=default&skin=fixture",
            listOf("NEW_FREQUENCE_STRATEGY", "UNIQUE_STRATEGY"),
            emptyMap(),
            "default",
        ))
        candidates.add(input(
            CloudDialogMode.WHITELIST,
            "manager_invitation_frs",
            "https://tieba.baidu.com/mo/q/wise-bawu-core/bazhu-invitation-newapply" +
                "?dialog_name=manager_invitation_frs",
            listOf("NA_FRS_MANAGER_INVITATION_STRATEGY", "PAGE_STRATEGY"),
            emptyMap(),
            "default",
        ))
        for ((level, name) in levels()) {
            candidates.add(input(
                CloudDialogMode.WHITELIST,
                name,
                "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup" +
                    "?customfullscreen=1&nonavigationbar=1&level=$level",
                listOf("NEW_FREQUENCE_STRATEGY", "UNIQUE_STRATEGY"),
                emptyMap(),
                "default",
            ))
        }

        for (candidate in candidates) {
            val decision = CloudDialogPolicy.evaluate(candidate)
            assertFalse(candidate.dialogName, decision.block)
            assertNotNull(candidate.dialogName, decision.matchedRule)
            matchedRules.add(requireNotNull(decision.matchedRule))
        }
        assertEquals(28, candidates.size)
        assertEquals(28, matchedRules.size)
    }

    @Test
    fun whitelistFailsClosedForUnknownNamesAuthoritiesStrategiesContextAndActions() {
        val cases = listOf(
            input(
                CloudDialogMode.WHITELIST,
                "future_dialog",
                "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=1",
                listOf("NEW_FREQUENCE_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "operateNew",
                "dialoginternal://operateNew",
                listOf("NA_OPERATION_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "common_sprite_dialog",
                "https://evil.invalid/mo/q/hybrid-main-chatgroup/ai-bot-claim",
                listOf("NEW_FREQUENCE_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "frsWishingLights",
                "dialoginternal://replacementComponent",
                listOf("NA_FRS_WISHING_LIGHTS_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "common_sprite_dialog",
                "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot-claim",
                listOf("NEW_FREQUENCE_STRATEGY", "FUTURE_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "common_sprite_dialog",
                "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot-claim",
                listOf("NEW_FREQUENCE_STRATEGY"),
                contextTime = "future_page",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "common_sprite_dialog",
                "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot-claim",
                listOf("NEW_FREQUENCE_STRATEGY"),
                mapOf("action" to "openPromotion"),
                "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "common_sprite_dialog",
                "https://tieba.baidu.com/mo/q/hybrid-main-chatgroup/ai-bot-claim?ad_mode=1",
                listOf("NEW_FREQUENCE_STRATEGY"),
                emptyMap(),
                "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "manager_invitation_frs",
                "https://tieba.baidu.com/mo/q/wise-bawu-core/bazhu-invitation-newapply",
                listOf("NA_FRS_MANAGER_INVITATION_STRATEGY", "PAGE_STRATEGY"),
                contextTime = "default",
            ),
            input(
                CloudDialogMode.WHITELIST,
                "levelUpPopuplevel10",
                "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup",
                listOf("NEW_FREQUENCE_STRATEGY"),
                contextTime = "default",
            ),
        )

        for (candidate in cases) {
            assertTrue(candidate.toString(), CloudDialogPolicy.evaluate(candidate).block)
        }
    }

    @Test
    fun whitelistMergesQueryAndThirdArgumentAndRejectsConflicts() {
        val sameValue = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup" +
                "?level=10&level=10&customfullscreen=1",
            listOf("NEW_FREQUENCE_STRATEGY"),
            mapOf("level" to "10"),
            "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(sameValue).block)

        val queryConflict = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup" +
                "?level=10&level=9",
            listOf("NEW_FREQUENCE_STRATEGY"),
            emptyMap(),
            "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(queryConflict).block)

        val sourceConflict = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=10",
            listOf("NEW_FREQUENCE_STRATEGY"),
            mapOf("level" to "9"),
            "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(sourceConflict).block)

        val gtaConflict = gta(
            mode = CloudDialogMode.BLACKLIST,
            dataUrl = "simpleDialog",
            dialogName = "gta6",
            fields = mapOf("dataUrl" to "differentDialog"),
        )
        assertFalse(CloudDialogPolicy.evaluate(gtaConflict).block)
    }

    @Test
    fun whitelistIncludesTheHostAppendedCallerTimeInConflictDetection() {
        val base = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=10",
            listOf("NEW_FREQUENCE_STRATEGY"),
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(base.copy(fields = mapOf("time" to "default"))).block)
        assertTrue(CloudDialogPolicy.evaluate(base.copy(fields = mapOf("time" to "frs_page"))).block)
        assertTrue(CloudDialogPolicy.evaluate(base.copy(cachedUrl = base.cachedUrl + "&time=frs_page")).block)
    }

    @Test
    fun levelRulesRequireAnExactNameAndAgreeingLevel() {
        for ((level, name) in levels()) {
            val valid = input(
                CloudDialogMode.WHITELIST,
                name,
                "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=$level",
                listOf("NEW_FREQUENCE_STRATEGY"),
                contextTime = "default",
            )
            assertFalse(name, CloudDialogPolicy.evaluate(valid).block)
        }

        val swapped = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=1",
            listOf("NEW_FREQUENCE_STRATEGY"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(swapped).block)

        val unknown = input(
            CloudDialogMode.WHITELIST,
            "levelUpPopuplevel11",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=11",
            listOf("NEW_FREQUENCE_STRATEGY"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(unknown).block)
    }

    @Test
    fun dedicatedDownloadAndUpdatePayloadsKeepOpaqueDataButRejectReplacementActions() {
        val confirm = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf(
                "dialog_data" to """{"apk_url":"https://download.invalid/app.apk"}""",
                "dialogUrl" to "dialoginternal://adDownloadConfirmDialog",
            ),
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(confirm).block)

        val lcUpdate = input(
            mode = CloudDialogMode.WHITELIST,
            name = "lcUpdateDialog",
            url = "dialoginternal://lcUpdateDialog" +
                "?dialogUrl=dialoginternal%3A%2F%2FlcUpdateDialog" +
                "&version_code=123&version_name=fixture" +
                "&download_url=https%3A%2F%2Fdownload.invalid%2Fapp.apk" +
                "&change_log=fixture&apk_md5_rsa=fixture&client_preload=1",
            strategies = listOf("FREQUENCE_STRATEGY"),
            fields = mapOf(
                "sign_md5" to "fixture",
                "package_name" to "com.baidu.tieba",
            ),
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(lcUpdate).block)

        val lcUpdateUnknown = native(
            CloudDialogMode.WHITELIST,
            "lcUpdateDialog",
            "FREQUENCE_STRATEGY",
            fields = mapOf("dialog_data" to """{"apk_md5_rsa":"fixture"}"""),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(lcUpdateUnknown).block)

        val replacement = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf("action" to "replaceRenderer"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(replacement).block)

        val wrongSelfUrl = input(
            mode = CloudDialogMode.WHITELIST,
            name = "lcUpdateDialog",
            url = "dialoginternal://lcUpdateDialog" +
                "?dialogUrl=dialoginternal%3A%2F%2FdifferentComponent&version_code=123",
            strategies = listOf("FREQUENCE_STRATEGY"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(wrongSelfUrl).block)

        val confirmDialogUrl = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf(
                "dialogUrl" to "dialoginternal://differentComponent",
                "dialog_data" to """{"apk_url":"https://download.invalid/app.apk"}""",
            ),
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(confirmDialogUrl).block)

        val invalidJson = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf("dialog_data" to "{"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(invalidJson).block)

        val flattenedPayload = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf("dialog_data.apk_url" to "https://download.invalid/app.apk"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(flattenedPayload).block)

        val oversizedJson = native(
            CloudDialogMode.WHITELIST,
            "adDownloadConfirmDialog",
            "NEW_FREQUENCE_STRATEGY",
            fields = mapOf("dialog_data" to """{"value":"${"a".repeat(16_384)}"}"""),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(oversizedJson).block)
    }

    @Test
    fun nativeQueryTransportIsAllowedButUnknownBusinessParametersAreDenied() {
        val normalNativeQuery = input(
            CloudDialogMode.WHITELIST,
            "userIcon",
            "dialoginternal://userIcon?client_preload=1&time=default",
            listOf("NA_USER_ICON_STRATEGY"),
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(normalNativeQuery).block)

        val unknownNativeBusiness = input(
            CloudDialogMode.WHITELIST,
            "userIcon",
            "dialoginternal://userIcon?ad_mode=1",
            listOf("NA_USER_ICON_STRATEGY"),
            contextTime = "default",
        )
        assertTrue(CloudDialogPolicy.evaluate(unknownNativeBusiness).block)
    }

    @Test
    fun policyDoesNotMutateInputsAndMapReadFailuresFollowTheMode() {
        val strategies = mutableListOf("PRECHECK_RENDER_STRATEGY", "NEW_FREQUENCE_STRATEGY")
        val fields = linkedMapOf("level" to "10")
        val input = CloudDialogPolicyInput(
            mode = CloudDialogMode.WHITELIST,
            dialogName = "levelUpPopuplevel10",
            cachedUrl = "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=10",
            strategyTypes = strategies,
            fields = fields,
            contextTime = "default",
        )
        val strategiesBefore = strategies.toList()
        val fieldsBefore = LinkedHashMap(fields)

        assertFalse(CloudDialogPolicy.evaluate(input).block)
        assertEquals(strategiesBefore, strategies)
        assertEquals(fieldsBefore, fields)

        val throwingFields = object : AbstractMap<String, String>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
                get() = throw IllegalStateException("read failure")
        }
        val blackFailure = native(
            CloudDialogMode.BLACKLIST,
            "frsWishingLights",
            "NA_FRS_WISHING_LIGHTS_STRATEGY",
            fields = throwingFields,
        )
        val whiteFailure = native(
            CloudDialogMode.WHITELIST,
            "frsWishingLights",
            "NA_FRS_WISHING_LIGHTS_STRATEGY",
            fields = throwingFields,
            contextTime = "default",
        )
        assertFalse(CloudDialogPolicy.evaluate(blackFailure).block)
        assertTrue(CloudDialogPolicy.evaluate(whiteFailure).block)
    }

    private fun gta(
        mode: CloudDialogMode,
        dataUrl: String,
        dialogName: String,
        fields: Map<String, String> = emptyMap(),
    ): CloudDialogPolicyInput {
        return input(
            mode = mode,
            name = "gta6",
            url = "https://tieba.baidu.com/mo/q/hybrid-usergrow-activity/reinvitationCard" +
                "?customfullscreen=1&nonavigationbar=1&dataUrl=$dataUrl" +
                "&dialog_name=$dialogName&trackFrom=not-an-ad-signal",
            strategies = listOf("LIMIT_STRATEGY", "FREQUENCE_STRATEGY"),
            fields = fields,
            contextTime = "frs_page",
        )
    }

    private fun native(
        mode: CloudDialogMode,
        name: String,
        vararg strategies: String,
        fields: Map<String, String> = emptyMap(),
        contextTime: String? = null,
    ): CloudDialogPolicyInput {
        return native(mode, name, strategies.toList(), fields, contextTime)
    }

    private fun native(
        mode: CloudDialogMode,
        name: String,
        strategy: String,
        fields: Map<String, String>,
        contextTime: String?,
    ): CloudDialogPolicyInput {
        return native(mode, name, listOf(strategy), fields, contextTime)
    }

    private fun native(
        mode: CloudDialogMode,
        name: String,
        strategies: List<String>,
        fields: Map<String, String> = emptyMap(),
        contextTime: String? = null,
    ): CloudDialogPolicyInput {
        return input(
            mode = mode,
            name = name,
            url = "dialoginternal://$name",
            strategies = strategies,
            fields = fields,
            contextTime = contextTime,
        )
    }

    private fun input(
        mode: CloudDialogMode,
        name: String,
        url: String,
        strategies: List<String>,
        fields: Map<String, String> = emptyMap(),
        contextTime: String? = null,
    ): CloudDialogPolicyInput {
        return CloudDialogPolicyInput(
            mode = mode,
            dialogName = name,
            cachedUrl = url,
            strategyTypes = strategies,
            fields = fields,
            contextTime = contextTime,
        )
    }

    private fun levels(): List<Pair<Int, String>> {
        return listOf(
            1 to "levelUpPopuplevel1",
            2 to "levelUpPopuplevel2",
            3 to "levelUpPopuplevel3",
            4 to "levelUpPopuplevel4",
            5 to "levelUpPopuplevel5",
            6 to "levelUpPopuplevel6",
            7 to "levelUpPopuplevel7",
            8 to "levelUpPopuplevel8",
            9 to "levelUpPopuplevel9",
            10 to "levelUpPopuplevel10",
        )
    }
}
