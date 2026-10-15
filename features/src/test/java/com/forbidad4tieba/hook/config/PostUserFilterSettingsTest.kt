package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import org.junit.Assert.*
import org.junit.Test

class PostUserFilterSettingsTest {
    private val user = BlockedPostUser("9007199254740993", "123456", "昵称😀")

    @Test fun savedUsersRoundTripAndRuntimeUsesInternalUidOnly() {
        val raw = PostUserFilterSettings.encode(listOf(user))
        assertEquals(listOf(user), PostUserFilterSettings.decode(raw))
        assertEquals(setOf(user.userId), PostFilterPreferences.parseUserIdList(raw))
        val snapshot = EffectiveSettingsPolicy.derive(UserSettings(mapOf(
            PostFilterPreferences.KEY_ENABLE_CUSTOM_POST_FILTER to true,
            PostFilterPreferences.KEY_FILTER_POST_USER_ID to true,
            PostFilterPreferences.KEY_FILTER_POST_USER_ID_LIST to raw,
        )), HostCapabilities(mapOf(HookFeatureKey.ENABLE_CUSTOM_POST_FILTER to
            ConfigManager.ScanFeatureAvailabilityState.AVAILABLE)), RemoteSettingsPolicy(true)).snapshot
        assertEquals(setOf(user.userId), snapshot.customPostRules!!.userIds)
        assertFalse(user.profileId in snapshot.postUserIdList)
    }

    @Test fun deletingANamedUserRemovesOnlyItsUidAfterReload() {
        val updated = listOf(BlockedPostUser("123", "123000", "first"),
            BlockedPostUser("456", "456000", "second"), user)
        assertEquals(updated, PostUserFilterSettings.decode(PostUserFilterSettings.encode(updated)))
        assertEquals(setOf("456", user.userId), PostFilterPreferences.parseUserIdList(
            PostUserFilterSettings.encode(updated.filterNot { it.userId == "123" })))
    }

    @Test fun malformedOrFutureJsonDoesNotBecomeAnInternalUidList() {
        for (raw in listOf("{bad,123,456", "[]", "{\"version\":2,\"users\":[{\"userId\":\"123\"}]}",
            "{\"version\":1,\"users\":[{\"userId\":\"123\",\"profileId\":\"456\"}]}")) {
            assertTrue(raw, PostFilterPreferences.parseUserIdList(raw).isEmpty())
        }
    }

    @Test fun conflictingIdentityMappingsDisableOnlyTheirEntries() {
        val conflict = BlockedPostUser("789", user.profileId, "another")
        val unrelated = BlockedPostUser("456", "456789", "safe")
        val raw = PostUserFilterSettings.encode(listOf(user, conflict, unrelated))
        assertEquals(listOf(unrelated), PostUserFilterSettings.decode(raw))
        val sharedUid = user.copy(profileId = "999")
        assertEquals(listOf(unrelated), PostUserFilterSettings.decode(
            PostUserFilterSettings.encode(listOf(user, sharedUid, unrelated))))
    }

    @Test fun duplicateVerifiedRecordsAreReadOnce() {
        val raw = PostUserFilterSettings.encode(listOf(user, user))
        assertEquals(listOf(user), PostUserFilterSettings.decode(raw))
    }
}
