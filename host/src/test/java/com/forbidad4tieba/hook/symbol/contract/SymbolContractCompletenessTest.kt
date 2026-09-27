package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.status.HookPointState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SymbolContractCompletenessTest {
    @Test fun everyDeclaredContractIsRegisteredOnce() {
        val source = File(System.getProperty("project.root"), "host/src/main/java")
        val declared = source.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            Regex("(?:object|class) (\\w+)\\s*:\\s*SymbolContract\\(").findAll(file.readText()).map { it.groupValues[1] }
        }.toSet()
        val registered = SymbolContracts.all.map { it.javaClass.simpleName }
        assertEquals(declared, registered.toSet())
        assertEquals(registered.size, registered.distinct().size)
        assertEquals(SymbolContracts.all.size, SymbolContracts.all.map { it.id }.distinct().size)
    }

    @Test fun allCapabilitiesHaveExactlyOneOwnerIncludingAggregates() {
        val empty = buildHookSymbols { }
        val keys = SymbolContracts.all.flatMap { it.capabilities(empty).keys + it.aggregates.keys }
        assertEquals(HookFeatureKey.orderedKeys.toSet(), keys.toSet())
        assertEquals("A capability cannot be defined by two contracts", keys.size, keys.distinct().size)
        val fields = SymbolContracts.all.flatMap { it.fields }
        assertEquals(fields.size, fields.map { it.cacheKey }.distinct().size)
    }

    @Test fun absentValuesAndEmptyCollectionsHaveStableRoundTripSemantics() {
        val empty = buildHookSymbols { }
        assertEquals(empty, HookSymbols.fromJson(empty.toJson()))
        val lists = buildHookSymbols {
            this[HomeTabsContract.homeTabClass] = null
            this[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds] = emptyList()
        }
        assertEquals(lists, HookSymbols.fromJson(lists.toJson()))
    }

    @Test fun everyMissingRequiredMemeDependencyDisablesOnlyItsOwnCapability() {
        InputMemeBarContract.required.fields.forEach { missing ->
            val symbols = buildHookSymbols {
                InputMemeBarContract.required.fields.filterNot { it == missing }.forEach {
                    @Suppress("UNCHECKED_CAST")
                    this[it as SymbolField<String?>] = "fixture"
                }
            }
            val capability = InputMemeBarContract.capabilities(symbols).getValue(HookFeatureKey.HIDE_INPUT_MEME_BAR)
            val point = InputMemeBarContract.points(symbols).single()
            assertEquals(listOf(missing.cacheKey), capability.missingCritical)
            assertEquals(capability.missingCritical, point.missing)
            assertEquals(HookPointState.MISSING, point.state)
            assertFalse(capability.isSupported())
        }
    }

    @Test fun failedRestorationDoesNotEscapeTheOwningContract() {
        val symbols = buildHookSymbols {
            this[InputMemeBarContract.inputMemeBarControllerClass] = "fixture.BrokenController"
            this[InputMemeBarContract.inputMemeBarEnableMethod] = "enabled"
        }
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == "fixture.BrokenController") throw LinkageError("fixture linkage failure")
                return super.loadClass(name, resolve)
            }
        }
        assertNull(InputMemeBarContract.resolveInputMemeBarSymbols(loader, symbols))
        assertFalse(InputMemeBarContract.isCacheValid(symbols, loader))
        assertTrue(InputMemeBarContract.isCacheValid(buildHookSymbols {}, loader))
        assertEquals(listOf(HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE),
            SymbolContracts.featuresForPoint("CommentAvatarDirectProfileHook"))
    }
}
