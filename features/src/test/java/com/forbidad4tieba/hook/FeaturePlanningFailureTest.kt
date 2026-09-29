package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class FeaturePlanningFailureTest {
    @Test fun eligibilityFailureDoesNotAbortAnyPhase() {
        FeaturePhase.entries.forEach { phase ->
            verifyIsolation(phase, FeatureDefinition.single("plan-B", phase, FeatureProcess.MAIN,
                enabled = { error("eligibility failure") }) { _, _ -> error("must not install") })
        }
    }

    @Test fun factoryFailureDoesNotAbortAnyPhase() {
        FeaturePhase.entries.forEach { phase ->
            verifyIsolation(phase, FeatureDefinition("plan-B", phase, FeatureProcess.MAIN) { _, _ ->
                throw NoClassDefFoundError("factory dependency unavailable")
            })
        }
    }

    private fun verifyIsolation(phase: FeaturePhase, broken: FeatureDefinition) {
        val api = NativeApi()
        val handles = linkedSetOf<XposedInterface.HookHandle>()
        val calls = mutableListOf<String>()
        fun healthy(id: String) = FeatureDefinition.observed(id, phase, FeatureProcess.MAIN) { _, _ ->
            calls += id
            handles += RuntimeHooks.builder(api.value, targetMethod, id, "planning-test").intercept { "module" }
        }
        try {
            withCatalog(listOf(healthy("plan-A"), broken, healthy("plan-C"))) {
                repeat(2) { attempt ->
                    val plan = plan(phase)
                    assertEquals(phase.name, listOf("plan-A", "plan-B", "plan-C"), plan.entries.map { it.id })
                    val outcomes = plan.entries.associate { entry ->
                        entry.id to RuntimeHooks.measure(entry.id) { entry.install(javaClass.classLoader!!) }
                    }
                    val expected = if (attempt == 0) InstallState.INSTALLED else InstallState.ALREADY_INSTALLED
                    listOf("plan-A", "plan-C").forEach { id ->
                        assertEquals(expected, outcomes.getValue(id).state)
                        assertEquals(1, outcomes.getValue(id).installedCount)
                    }
                    val failure = outcomes.getValue("plan-B")
                    assertEquals(InstallState.FAILED, failure.state)
                    assertEquals(0, failure.installedCount)
                    assertTrue(failure.reason.orEmpty().contains("plan", ignoreCase = true))
                    val record = HookInstallRecord(plan.processName, plan.phase, "plan-B", failure, 0)
                    assertTrue(record.formatLine().contains("phase=${phase.diagnosticName}"))
                    assertEquals(2, api.callbacks.size)
                }
            }
            assertEquals(listOf("plan-A", "plan-C", "plan-A", "plan-C"), calls)
        } finally {
            handles.forEach { it.unhook() }
        }
        api.callbacks.forEach { assertEquals("original", it.intercept(originalChain())) }
    }

    @Test fun installationFailureRemainsDeferredAndRetainsPartialHandles() {
        val api = NativeApi()
        lateinit var handle: XposedInterface.HookHandle
        var installed = false
        val definition = FeatureDefinition.observed("partial-plan", FeaturePhase.SYMBOL, FeatureProcess.MAIN) { _, _ ->
            installed = true
            handle = RuntimeHooks.builder(api.value, targetMethod, "partial-plan", "first").intercept { "module" }
            error("second installation failed")
        }
        try {
            withCatalog(listOf(definition)) {
                val entry = plan(FeaturePhase.SYMBOL).entries.single()
                assertFalse(installed)
                val outcome = RuntimeHooks.measure(entry.id) { entry.install(javaClass.classLoader!!) }
                assertEquals(InstallState.PARTIAL, outcome.state)
                assertEquals(1, outcome.installedCount)
            }
        } finally {
            if (installed) handle.unhook()
        }
    }

    @Test fun assertionFailuresAreNotConvertedToUnavailableFeatures() {
        val failure = AssertionError("broken invariant")
        withCatalog(listOf(FeatureDefinition("fatal-plan", FeaturePhase.STATIC, FeatureProcess.MAIN) { _, _ ->
            throw failure
        })) {
            assertSame(failure, assertThrows(AssertionError::class.java) { plan(FeaturePhase.STATIC) })
        }
    }

    private fun plan(phase: FeaturePhase): HookInstallPlan = when (phase) {
        FeaturePhase.STATIC -> FeatureCatalog.staticPlan("com.baidu.tieba")
        FeaturePhase.POST_ATTACH -> FeatureCatalog.postAttachPlan("com.baidu.tieba", HookSymbols.unsupported(), SettingsSnapshot())
        FeaturePhase.SYMBOL -> FeatureCatalog.symbolPlan("com.baidu.tieba", HookSymbols.unsupported(), SettingsSnapshot())
    }

    private fun withCatalog(definitions: List<FeatureDefinition>, action: () -> Unit) {
        // Replace entries only in this test scope; all calls still use the production catalog path.
        // The catalog uses a fixed-size list and Gradle does not run these tests concurrently.
        @Suppress("UNCHECKED_CAST")
        val catalog = FeatureCatalog.definitions as MutableList<FeatureDefinition>
        val original = catalog.toList()
        val excluded = FeatureDefinition("excluded-plan", FeaturePhase.STATIC, FeatureProcess.IMAGE_VIEWER_REMOTE) { _, _ ->
            error("wrong-process factory executed")
        }
        try {
            catalog.indices.forEach { catalog[it] = definitions.getOrNull(it) ?: excluded }
            action()
        } finally {
            catalog.indices.forEach { catalog[it] = original[it] }
        }
    }

    private fun callbackTarget() = Unit
    private val targetMethod = javaClass.getDeclaredMethod("callbackTarget")

    private fun originalChain() = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
        check(method.name == "proceed")
        "original"
    } as XposedInterface.Chain

    private class NativeApi {
        val callbacks = mutableListOf<XposedInterface.Hooker>()
        val value = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(XposedInterface::class.java)) { _, method, args ->
            check(method.name == "hook")
            builder(args!![0] as Executable)
        } as XposedInterface

        private fun builder(member: Executable) = object : XposedInterface.HookBuilder {
            override fun setId(id: String?) = this
            override fun setPriority(priority: Int) = this
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = this
            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                callbacks += hooker
                return object : XposedInterface.HookHandle {
                    override fun getExecutable() = member
                    override fun getId(): String? = null
                    override fun unhook() = Unit
                    override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
                        error("replacement is outside this test")
                }
            }
        }
    }
}
