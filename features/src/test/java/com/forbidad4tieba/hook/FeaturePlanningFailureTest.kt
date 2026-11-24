package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturePlanningFailureTest {
    @Test
    fun planningFailuresStayInTheLedgerAcrossPhasesWithoutBlockingSiblings() {
        for (phase in FeaturePhase.entries) {
            val api = FakeHookApi()
            val handles = linkedSetOf<XposedInterface.HookHandle>()
            val calls = mutableListOf<String>()
            fun healthy(id: String) = FeatureDefinition.observed(id, phase, FeatureProcess.MAIN) { _, _ ->
                calls += id
                handles += RuntimeHooks.builder(api.value, targetMethod, id, "planning-test")
                    .intercept { "module" }
            }
            val eligibilityFailure = FeatureDefinition.single(
                "plan-B",
                phase,
                FeatureProcess.MAIN,
                enabled = { error("eligibility failure") },
            ) { _, _ -> error("must not install") }
            val factoryFailure = FeatureDefinition("plan-C", phase, FeatureProcess.MAIN) { _, _ ->
                throw NoClassDefFoundError("factory dependency unavailable")
            }
            try {
                withCatalog(listOf(healthy("plan-A"), eligibilityFailure, factoryFailure, healthy("plan-D"))) {
                    repeat(2) { attempt ->
                        val plan = plan(phase)
                        assertEquals(listOf("plan-A", "plan-B", "plan-C", "plan-D"), plan.entries.map { it.id })
                        val outcomes = plan.entries.associate { entry ->
                            entry.id to RuntimeHooks.measure(entry.id) {
                                entry.install(javaClass.classLoader!!)
                            }
                        }
                        val expected = if (attempt == 0) InstallState.INSTALLED else InstallState.ALREADY_INSTALLED
                        listOf("plan-A", "plan-D").forEach { id ->
                            assertEquals(expected, outcomes.getValue(id).state)
                            assertEquals(1, outcomes.getValue(id).installedCount)
                        }
                        for (id in listOf("plan-B", "plan-C")) {
                            assertEquals(InstallState.FAILED, outcomes.getValue(id).state)
                            assertEquals(0, outcomes.getValue(id).installedCount)
                            assertTrue(outcomes.getValue(id).reason.orEmpty().contains("plan", ignoreCase = true))
                        }
                        assertEquals(2, api.snapshots.size)
                    }
                }
                assertEquals(listOf("plan-A", "plan-D", "plan-A", "plan-D"), calls)
            } finally {
                handles.forEach { it.unhook() }
            }
            api.snapshots.forEach { assertEquals("original", it.intercept(originalChain())) }
        }

        val invariant = AssertionError("broken invariant")
        withCatalog(listOf(FeatureDefinition("fatal-plan", FeaturePhase.STATIC, FeatureProcess.MAIN) { _, _ ->
            throw invariant
        })) {
            assertSame(invariant, assertThrows(AssertionError::class.java) { plan(FeaturePhase.STATIC) })
        }
    }

    @Test
    fun installationFailureKeepsActualPartialHandleCountsForRetry() {
        val api = FakeHookApi()
        lateinit var handle: XposedInterface.HookHandle
        var installed = false
        val definition = FeatureDefinition.observed(
            "partial-plan",
            FeaturePhase.SYMBOL,
            FeatureProcess.MAIN,
        ) { _, _ ->
            installed = true
            handle = RuntimeHooks.builder(api.value, targetMethod, "partial-plan", "first")
                .intercept { "module" }
            error("second installation failed")
        }
        try {
            withCatalog(listOf(definition)) {
                val entry = plan(FeaturePhase.SYMBOL).entries.single()
                assertEquals(false, installed)
                val outcome = RuntimeHooks.measure(entry.id) {
                    entry.install(javaClass.classLoader!!)
                }
                assertEquals(InstallState.PARTIAL, outcome.state)
                assertEquals(1, outcome.installedCount)
            }
        } finally {
            if (installed) handle.unhook()
        }
    }

    private fun plan(phase: FeaturePhase): HookInstallPlan = when (phase) {
        FeaturePhase.STATIC -> FeatureCatalog.staticPlan("com.baidu.tieba")
        FeaturePhase.POST_ATTACH -> FeatureCatalog.postAttachPlan(
            "com.baidu.tieba",
            HookSymbols.unsupported(),
            SettingsSnapshot.bootstrap(),
        )
        FeaturePhase.SYMBOL -> FeatureCatalog.symbolPlan(
            "com.baidu.tieba",
            HookSymbols.unsupported(),
            SettingsSnapshot.bootstrap(),
        )
    }

    private fun withCatalog(definitions: List<FeatureDefinition>, action: () -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val catalog = FeatureCatalog.definitions as MutableList<FeatureDefinition>
        val original = catalog.toList()
        val excluded = FeatureDefinition(
            "excluded-plan",
            FeaturePhase.STATIC,
            FeatureProcess.IMAGE_VIEWER_REMOTE,
        ) { _, _ -> error("wrong-process factory executed") }
        try {
            catalog.indices.forEach { catalog[it] = definitions.getOrNull(it) ?: excluded }
            action()
        } finally {
            catalog.indices.forEach { catalog[it] = original[it] }
        }
    }

    private fun callbackTarget() = Unit
    private val targetMethod = javaClass.getDeclaredMethod("callbackTarget")

    private fun originalChain(): XposedInterface.Chain = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(XposedInterface.Chain::class.java),
    ) { _, method, _ ->
        check(method.name == "proceed")
        "original"
    } as XposedInterface.Chain
}
