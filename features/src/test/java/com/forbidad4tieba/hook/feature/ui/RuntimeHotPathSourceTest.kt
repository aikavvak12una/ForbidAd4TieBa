package com.forbidad4tieba.hook.feature.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeHotPathSourceTest {
    @Test
    fun restoredFeatureConsumersDoNotRediscoverHostMembers() {
        val featureConsumers = listOf("ad/FeedAdHook.kt", "ad/CustomPostCardBlockHook.kt", "ui/HomeTabHook.kt",
            "ui/HistorySearchHook.kt", "ui/CollectionSearchHook.kt", "ui/DefaultOriginalImageHook.kt",
            "signin/AutoSignInNetwork.kt")
        val consumers = featureConsumers.map { "feature/$it" } + "ui/SettingsMenuHook.kt"
        for (file in consumers) {
            val source = readSource("features/src/main/java/com/forbidad4tieba/hook/$file")
            for (lookup in listOf("getDeclaredMethod(", "getDeclaredField(", ".declaredMethods", ".declaredFields",
                "resolveMethodByCachedSpec(", "Class.forName(", "findMethodInHierarchy(")) {
                assertFalse("$file reintroduced runtime target discovery: $lookup", source.contains(lookup))
            }
        }
    }

    private fun readSource(relativePath: String): String =
        File(checkNotNull(System.getProperty("project.root")), relativePath).readText()
}
