package com.forbidad4tieba.hook.config

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SnapshotDerivedStateTest {
    @Test fun everyBottomSelectionKeepsTheExistingFallbackAndReusesItsPublishedValue() {
        val bootstrap = SettingsSnapshot.bootstrap()
        assertNull(bootstrap.bottomTabSelection)
        repeat(32) { mask ->
            val snapshot = bootstrap.copy(isBottomTabsCustomEnabled = true,
                isBottomTabHomeEnabled = mask and 1 != 0, isBottomTabEnterForumEnabled = mask and 2 != 0,
                isBottomTabRetailStoreEnabled = mask and 4 != 0, isBottomTabMessageEnabled = mask and 8 != 0,
                isBottomTabMineEnabled = mask and 16 != 0)
            val selection = snapshot.bottomTabSelection!!
            assertEquals(TabPreferences.BottomTabSelection(mask == 0 || mask and 1 != 0,
                mask and 2 != 0, mask and 4 != 0, mask and 8 != 0, mask and 16 != 0), selection)
            assertSame(selection, snapshot.bottomTabSelection)
            assertNull(snapshot.copy(isBottomTabsCustomEnabled = false).bottomTabSelection)
            assertSame(selection, snapshot.bottomTabSelection)
        }
    }

    @Test fun glassActivationUsesTheSameLightAndDarkPathsAcrossSnapshotCopies() {
        val empty = SettingsSnapshot.bootstrap().copy(isHomeNativeGlassEnabled = true)
        assertFalse(empty.isHomeNativeGlassRuntimeActive())
        for (dark in listOf(false, true)) {
            val style = HomeGlassPreferences.HomeNativeGlassStyleConfig(backgroundImagePath = "/local/background")
            val selected = if (dark) empty.copy(homeNativeGlassDarkStyle = style) else empty.copy(homeNativeGlassLightStyle = style)
            assertTrue(selected.hasAnyHomeNativeGlassBackgroundImage())
            assertTrue(selected.isHomeNativeGlassRuntimeActive())
            assertFalse(selected.copy(isHomeNativeGlassEnabled = false).isHomeNativeGlassRuntimeActive())
            assertFalse(empty.hasAnyHomeNativeGlassBackgroundImage())
        }
    }

    @Test fun urlTabIdentityRemainsByteForByteCompatibleWithStoredSelections() {
        for (url in listOf("https://example.org/path", "https://example.org/中文?q=1", " HTTPS://EXAMPLE.ORG/PATH ")) {
            val normalized = url.trim().lowercase(java.util.Locale.ROOT)
            val expected = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }.take(16)
            assertEquals("url:$expected", TabPreferences.homeTopTabKeyFor(null, null, url))
        }
    }
}
