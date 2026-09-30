package com.forbidad4tieba.hook.config

import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.ui.SettingsMenuGroupActions
import com.forbidad4tieba.hook.ui.SettingsMenuGroupBuilder
import com.forbidad4tieba.hook.ui.SwitchItem
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Discover declarations independently of the catalog, so forgetting registration fails verify. */
class SettingsCompletenessTest {
    private val root = File(checkNotNull(System.getProperty("project.root")))
    private val config = File(root, "features/src/main/java/com/forbidad4tieba/hook/config")
    private val declarations by lazy {
        config.listFiles()!!.filter { it.extension == "kt" }.flatMap { file ->
            val source = file.readText()
            val names = Regex("(?:internal )?val (\\w+) = (?:Boolean|Int|String)Preference\\(")
                .findAll(source).map { it.groupValues[1] }.toList()
            if (names.isEmpty() || file.name == "SimpleToggle.kt") emptyList() else {
                val owner = Regex("object (\\w+)").find(source)!!.groupValues[1]
                val type = Class.forName("com.forbidad4tieba.hook.config.$owner")
                names.map { name ->
                    val field = type.getDeclaredField(name).apply { isAccessible = true }
                    Triple(owner, name, field.get(null) as Preference<*>)
                }
            }
        }
    }

    @Test fun everyDeclarationAndPersistedKeyIsRegisteredExactlyOnce() {
        val discovered = declarations.map { it.third } + SimpleToggle.entries.map { it.preference }
        assertEquals(discovered.toSet(), SettingsCatalog.preferences.toSet())
        assertEquals(discovered.size, SettingsCatalog.preferences.size)
        assertEquals(discovered.size, discovered.map { it.key }.distinct().size)
        // Legacy aliases share their original stored key and must resolve to the same declaration.
        val storedKeys = config.listFiles()!!.filter { it.extension == "kt" }.flatMap { file ->
            Regex("const val KEY_\\w+ =\\s*\"([^\"]+)\"").findAll(file.readText())
                .map { it.groupValues[1] }.toList()
        }.toSet() - "user_settings_version_code" // storage schema, not a feature preference
        assertEquals(emptySet<String>(), storedKeys - discovered.map { it.key }.toSet())
        discovered.forEach { setting ->
            setting.capabilityKey?.let { assertTrue("${setting.key}: $it", it in HookFeatureKey.orderedKeys) }
            assertEquals(setting.key, setting.key.trim())
        }
    }

    @Test fun allSwitchDeclarationsReachExactlyOneMenuIncludingNestedForms() {
        val rows = allMenuRows(unlocked = true)
        val declared = SettingsCatalog.preferences.filter { it.use == PreferenceUse.SWITCH }
        assertEquals(declared.toSet(), rows.map { it.preference }.toSet())
        assertEquals("Duplicated menu ownership", declared.size, rows.size)
        rows.forEach { row ->
            assertTrue(row.label.isNotBlank())
            assertTrue(row.description.isNotBlank())
            assertEquals(row.preference.defaultValue, row.defaultValue)
        }
        assertTrue(allMenuRows(unlocked = false).all { it.preference in declared })
    }

    @Test fun declarationsAreConsumedOutsideTheirOwnDefinitionAndRegistration() {
        val sources = config.walkTopDown().filter { it.extension == "kt" }
            .associate { it.name to it.readText() }
        for ((owner, name, setting) in declarations) {
            if (setting === HomeGlassPreferences.ENABLE_HOME_TAB_DYNAMIC_TINT) continue
            // This obsolete key is deliberately ignored: dynamic tint is always enabled.
            val uses = sources.filterKeys { it != "SettingsCatalog.kt" }.map { (file, source) ->
                if (file == "$owner.kt" || file == "BottomTabLiquidGlassConfig.kt") {
                    source.replace(Regex("(?:internal )?val \\w+ = (?:Boolean|Int|String)Preference\\([\\s\\S]*?\\n    \\)"), "")
                        .replace(Regex("(?:internal )?val preferences: List<Preference<\\*>> = listOf\\([\\s\\S]*?\\)"), "")
                } else source
            }.joinToString("\n")
            val read = Regex("\\b${Regex.escape(name)}\\b")
            assertTrue("Unconsumed setting: $owner.$name (${setting.key})", read.containsMatchIn(uses))
        }
    }

    @Test fun runtimeConstructionCannotSilentlyDefaultAnOmittedField() {
        val source = File(config, "SettingsSnapshot.kt").readText()
        val constructor = source.substringAfter("data class SettingsSnapshot(").substringBefore(") {")
        assertFalse("Every runtime field must be explicitly derived", constructor.contains('='))
        val policy = File(config, "EffectiveSettingsPolicy.kt").readText()
        assertFalse(policy.contains("bootstrap()"))
        assertFalse(policy.contains(".getBoolean("))
        assertFalse(policy.contains(".getInt("))
        assertFalse(policy.contains(".getString("))
        val illegalBootstrap = File(root, "features/src/main").walkTopDown().filter { it.extension == "kt" }
            .filter { it.name !in setOf("SettingsSnapshot.kt", "ConfigManager.kt") }
            .filter { file ->
                // Static installation precedes preference initialization and intentionally uses bootstrap state.
                file.readText().replace(
                    "plan(FeaturePhase.STATIC, processName, null, SettingsSnapshot.bootstrap())", "",
                ).contains("SettingsSnapshot.bootstrap()")
            }.map { it.name }.toList()
        assertEquals(emptyList<String>(), illegalBootstrap)
    }

    @Test fun everySnapshotFieldHasAConsumerOrFeedsChildPolicy() {
        val snapshot = File(config, "SettingsSnapshot.kt").readText()
        val constructor = snapshot.substringAfter("data class SettingsSnapshot(").substringBefore(") {")
        val fields = Regex("val (\\w+):").findAll(constructor).map { it.groupValues[1] }
        val methods = snapshot.substringAfter("operator fun get")
        val policy = File(config, "EffectiveSettingsPolicy.kt").readText()
        val consumers = File(root, "features/src/main").walkTopDown().filter { it.extension == "kt" }
            .filter { it.name !in setOf("SettingsSnapshot.kt", "EffectiveSettingsPolicy.kt") }
            .joinToString("\n") { it.readText() }
        val missing = fields.filter { name ->
            val directRead = Regex("(?:\\.|::)${Regex.escape(name)}\\b").containsMatchIn(consumers)
            val snapshotMethodRead = Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(methods)
            // A master can feed child policy before construction instead of owning a standalone hook.
            val master = Regex("\\b${Regex.escape(name)} = (\\w+),").find(policy)?.groupValues?.get(1)
            val feedsChildren = master != null && Regex("\\b${Regex.escape(master)} &&").containsMatchIn(policy)
            !directRead && !snapshotMethodRead && !feedsChildren
        }.toList()
        assertEquals("Snapshot fields without consumers", emptyList<String>(), missing)
    }

    @Test fun publisherOwnsStorageAndPublicationWithoutFeatureRules() {
        val publisher = File(config, "ConfigManager.kt").readText()
        val forbidden = Regex("(?:fun (?:parse|serialize|normalize|format|homeTopTab|readHomeNativeGlass)|const val KEY_(?!USER_SETTINGS_VERSION_CODE)|HookFeatureKey\\.|ModelScoreThreshold)")
        assertFalse("Domain rules must stay with their owners", forbidden.containsMatchIn(publisher))
        val hooks = File(root, "features/src/main/java/com/forbidad4tieba/hook/feature")
            .walkTopDown().filter { it.extension == "kt" }
        assertEquals(emptyList<String>(), hooks.filter { it.readText().contains("SettingsCatalog") }.map { it.name }.toList())
    }

    private fun allMenuRows(unlocked: Boolean): List<SwitchItem> {
        val rows = mutableListOf<SwitchItem>()
        fun collect(items: List<SwitchItem>) {
            rows += items
            items.forEach { it.onActionClick?.invoke() }
        }
        val actions = SettingsMenuGroupActions(
            onAdBlock = ::collect, onCustomPostFilter = ::collect,
            onCustomPostModelScore = {}, onCustomPostFilterKeyword = {}, onPbLikeAutoReply = {},
            onFreeCopy = ::collect, onPerformanceOptimization = { groups -> groups.forEach { collect(it.items) } },
            onAutoSignIn = {}, onReplyVisibilityProbe = {}, onDetailedLogSave = {},
            onTabCustomization = ::collect, onHomeTopTab = {}, onHomeNativeGlass = {}, onBottomTab = {},
            onBottomTabLiquidGlass = {},
        )
        SettingsMenuGroupBuilder.build(unlocked, actions).forEach { collect(it.items) }
        return rows
    }
}
