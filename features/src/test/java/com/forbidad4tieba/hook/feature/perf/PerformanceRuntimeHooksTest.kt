package com.forbidad4tieba.hook.feature.perf

import com.baidu.tbadk.abtest.UbsABTestHelper
import com.forbidad4tieba.hook.HookInstallContext
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.PostPageAdAbFeature
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PerformanceAbTarget
import com.forbidad4tieba.hook.symbol.model.PerformanceRuntimeTarget
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PerformanceRuntimeHooksTest {
    private val methods = mapOf(
        PerformanceRuntimeTarget.AD_CSJ to method("csj"),
        PerformanceRuntimeTarget.AD_GDT to method("gdt"),
        PerformanceRuntimeTarget.AD_BAIDU to method("baidu"),
    )

    @After fun release() {
        val owned = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
            .get(null) as OwnedHookSet<*, *>
        assertTrue(owned.rollback().isEmpty())
        val installed = AdSdkInitBlockHook::class.java.getDeclaredField("installed").apply { isAccessible = true }
            .get(null) as MutableMap<*, *>
        installed.clear()
        AdSdkInitBlockHook::class.java.getDeclaredField("prepared").apply { isAccessible = true }.set(null, null)
    }

    @Test fun everyMissingAdChannelCombinationKeepsIndependentPointsAndAccurateHandleCounts() {
        val keys = methods.keys.toList()
        for (mask in 0 until (1 shl keys.size)) {
            val available = methods.filterKeys { key -> mask and (1 shl keys.indexOf(key)) != 0 }
            val api = FakeHookApi(allowLog = true)
            try {
                val ad = RuntimeHooks.measure("AdSdkInitBlockHook") {
                    AdSdkInitBlockHook.install(available, api.value) { true }
                }
                val adCount = available.size
                assertEquals("ad mask=$mask", state(adCount, 3), ad.state)
                assertEquals(adCount, ad.installedCount)
                assertEquals(available.values.toSet(), api.callbacks.keys)
            } finally {
                release()
            }
        }
    }

    @Test fun eachInstallationFailureCanRetryWithoutDuplicatingSuccessfulPoints() {
        for (failedMethod in methods.values) {
            val api = FakeHookApi(allowLog = true).apply { failInstall += failedMethod }
            val expected = 3
            val owner = "AdSdkInitBlockHook"
            fun install() = RuntimeHooks.measure(owner) {
                AdSdkInitBlockHook.install(methods, api.value) { true }
            }
            try {
                val partial = install()
                assertEquals(InstallState.PARTIAL, partial.state)
                assertEquals(expected - 1, partial.installedCount)
                assertFalse(api.callbacks.containsKey(failedMethod))
                api.failInstall.clear()
                val recovered = install()
                assertEquals(InstallState.INSTALLED, recovered.state)
                assertEquals(expected, recovered.installedCount)
                assertEquals(expected, api.created)
                val repeated = install()
                assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
                assertEquals(expected, repeated.installedCount)
                assertEquals(expected, api.created)
            } finally {
                release()
            }
        }
    }

    @Test fun disabledAdInstallDoesNoWorkAndItsCallbacksFollowTheSetting() {
        val api = FakeHookApi(allowLog = true)
        var ads = false
        assertEquals(InstallState.SKIPPED, AdSdkInitBlockHook.install(methods, api.value, requested = ads) { ads }?.state)
        assertEquals(0, api.created)
        ads = true
        RuntimeHooks.measure("AdSdkInitBlockHook") { AdSdkInitBlockHook.install(methods, api.value) { ads } }
        for (adEnabled in listOf(false, true)) {
            ads = adEnabled
            for ((target, member) in methods) {
                var originalCalls = 0
                val result = api.invoke(member) { originalCalls++; true }
                assertEquals("$target ads=$ads", !ads, result)
                assertEquals(if (ads) 0 else 1, originalCalls)
            }
        }
        for (member in listOf(method("sdkStart"), method("frameworkInit"))) {
            assertFalse(api.callbacks.containsKey(member))
            assertEquals("host-result", api.invoke(member) { "host-result" })
        }
    }

    @Test fun adPreparationTracksCurrentOwnedHandlesAndRecoversFromAnInactiveReleaseFailure() {
        val api = FakeHookApi(allowLog = true)
        fun prepare() = RuntimeHooks.measure("AdSdkInitBlockHook") {
            AdSdkInitBlockHook.install(methods, api.value, requested = true) { false }
        }
        val first = prepare()
        assertEquals(InstallState.INSTALLED, first.state)
        assertEquals(3, first.installedCount)
        assertTrue(AdSdkInitBlockHook.isPreparedFor(methods))
        methods.values.forEach { member -> assertEquals(true, api.invoke(member) { true }) }
        val selected = methods.getValue(PerformanceRuntimeTarget.AD_CSJ)
        val retained = RuntimeHooks.builder(api.value, selected, "AdSdkInitBlockHook", PerformanceRuntimeTarget.AD_CSJ.name)
            .intercept { error("existing registration should be reused") }
        assertTrue(RuntimeHooks.ownsActiveHandle(retained))
        val foreign = api.value.hook(method("sdkStart")).intercept { null }
        try {
            assertFalse(RuntimeHooks.ownsActiveHandle(foreign))
            assertFalse(AdSdkInitBlockHook.isPreparedFor(methods + (PerformanceRuntimeTarget.AD_CSJ to method("sdkStart"))))
            api.failUnhook += selected
            assertThrows(IllegalStateException::class.java) { retained.unhook() }
            assertFalse(RuntimeHooks.ownsActiveHandle(retained))
            assertFalse(AdSdkInitBlockHook.isPreparedFor(methods))
            assertEquals(true, api.invoke(selected) { true })
            val beforeRetry = api.created
            val failed = prepare()
            assertEquals(InstallState.ROLLBACK_FAILED, failed.state)
            assertFalse(AdSdkInitBlockHook.isPreparedFor(methods))
            assertEquals(beforeRetry, api.created)
            api.failUnhook.clear()
            val recovered = prepare()
            assertEquals(InstallState.INSTALLED, recovered.state)
            assertEquals(3, recovered.installedCount)
            assertEquals(beforeRetry + 1, api.created)
            assertTrue(AdSdkInitBlockHook.isPreparedFor(methods))
            val fresh = RuntimeHooks.builder(api.value, selected, "AdSdkInitBlockHook", PerformanceRuntimeTarget.AD_CSJ.name)
                .intercept { error("existing registration should be reused") }
            fresh.unhook()
            assertFalse(RuntimeHooks.ownsActiveHandle(fresh))
            assertFalse(AdSdkInitBlockHook.isPreparedFor(methods))
            assertEquals(InstallState.INSTALLED, prepare().state)
            assertTrue(AdSdkInitBlockHook.isPreparedFor(methods))
        } finally {
            api.failUnhook.clear()
            foreign.unhook()
        }
    }

    @Test fun cookieInstallsOnlyItsVerifiedAbAndDisablingRestoresTheOriginalCallback() {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshotField.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        val module = object : XposedModule() {}.apply { attachFramework(api.value) {} }
        val cookie = method("cookie")
        val abMethods = mapOf(
            PerformanceAbTarget.COOKIE_REPEATED.methodName to cookie,
            "isPbCommentFunAdABTest" to method("unrelatedAdAb"),
        )
        fun setEnabled(value: Boolean) {
            snapshotField.set(ConfigManager, SettingsSnapshot.bootstrap().copy(isCookieSyncDedupEnabled = value))
        }
        try {
            XposedCompat.attachModule(module)
            setEnabled(false)
            val disabled = RuntimeHooks.measure("CookieSyncDedupHook") { CookieSyncDedupHook.hook(abMethods); null }
            assertEquals(InstallState.SKIPPED, disabled.state)
            assertEquals(0, api.created)
            setEnabled(true)
            val missing = RuntimeHooks.measure("CookieSyncDedupHook") { CookieSyncDedupHook.hook(emptyMap()); null }
            assertEquals(InstallState.SKIPPED, missing.state)
            api.failInstall += cookie
            val failed = RuntimeHooks.measure("CookieSyncDedupHook") { CookieSyncDedupHook.hook(abMethods); null }
            assertEquals(InstallState.FAILED, failed.state)
            assertEquals(0, failed.installedCount)
            assertEquals(0, api.created)
            api.failInstall.clear()
            val installed = RuntimeHooks.measure("CookieSyncDedupHook") { CookieSyncDedupHook.hook(abMethods); null }
            assertEquals(InstallState.INSTALLED, installed.state)
            assertEquals(1, installed.installedCount)
            assertEquals(setOf(cookie), api.callbacks.keys)
            val repeated = RuntimeHooks.measure("CookieSyncDedupHook") { CookieSyncDedupHook.hook(abMethods); null }
            assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
            assertEquals(1, api.created)
            var originalCalls = 0
            assertEquals(true, api.invoke(cookie) { originalCalls++; false })
            assertEquals(0, originalCalls)
            setEnabled(false)
            assertEquals(false, api.invoke(cookie) { originalCalls++; false })
            assertEquals(1, originalCalls)
        } finally {
            release()
            snapshotField.set(ConfigManager, originalSettings)
            moduleField.set(XposedCompat, originalModule)
        }
    }

    @Test fun postAdAbInstallationAndCallbacksAreIndependentOfEveryPerformanceCombination() {
        val snapshotField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val originalSettings = snapshotField.get(ConfigManager)
        val originalModule = moduleField.get(XposedCompat)
        val symbols = requireNotNull(HookSymbols.fromJson(JSONObject().put(
            "performanceAbMethods", JSONArray(PerformanceAbTarget.entries.map { it.methodName }),
        ).toString()))
        val targets = setOf(
            UbsABTestHelper::class.java.getDeclaredMethod(PerformanceAbTarget.PB_COMMENT_AD.methodName),
            UbsABTestHelper::class.java.getDeclaredMethod(PerformanceAbTarget.PB_BANNER_AD.methodName),
        )
        try {
            for (master in listOf(false, true)) for (cookie in listOf(false, true))
                for (ads in listOf(false, true)) for (video in listOf(false, true))
                    for (postAd in listOf(false, true)) {
                        val api = FakeHookApi(allowLog = true)
                        val module = object : XposedModule() {}.apply { attachFramework(api.value) {} }
                        XposedCompat.attachModule(module)
                        val settings = SettingsSnapshot.bootstrap().copy(
                            isPerformanceOptimizationEnabled = master,
                            isCookieSyncDedupEnabled = cookie,
                            isAdSdkComponentsDisabled = ads,
                            isVideoPreloadDisabled = video,
                            isPostPageAdBlockEnabled = postAd,
                        )
                        snapshotField.set(ConfigManager, settings)
                        val label = "master=$master cookie=$cookie ads=$ads video=$video postAd=$postAd"
                        try {
                            val entries = PostPageAdAbFeature.entries(HookInstallContext("com.baidu.tieba", symbols), settings)
                            assertEquals(label, if (postAd) listOf("PostPageAdAbHook") else emptyList<String>(), entries.map { it.id })
                            assertTrue(PostPageAdAbFeature.entries(HookInstallContext("com.baidu.tieba:remote", symbols), settings).isEmpty())
                            val outcome = RuntimeHooks.measure("PostPageAdAbHook") {
                                entries.singleOrNull()?.install(javaClass.classLoader!!)
                            }
                            assertEquals(label, if (postAd) InstallState.INSTALLED else InstallState.SKIPPED, outcome.state)
                            assertEquals(label, if (postAd) 2 else 0, outcome.installedCount)
                            assertEquals(label, if (postAd) targets else emptySet<Method>(), api.callbacks.keys)
                            for (member in targets) {
                                var calls = 0
                                assertEquals(!postAd, api.invoke(member) { calls++; true })
                                assertEquals(if (postAd) 0 else 1, calls)
                            }
                            if (postAd) {
                                val repeated = RuntimeHooks.measure("PostPageAdAbHook") { entries.single().install(javaClass.classLoader!!) }
                                assertEquals(InstallState.ALREADY_INSTALLED, repeated.state)
                                assertEquals(2, repeated.installedCount)
                                assertEquals(2, api.created)
                                snapshotField.set(ConfigManager, settings.copy(isPostPageAdBlockEnabled = false))
                                for (member in targets) for (hostValue in listOf(false, true)) {
                                    var calls = 0
                                    assertEquals(hostValue, api.invoke(member) { calls++; hostValue })
                                    assertEquals(1, calls)
                                }
                            }
                        } finally {
                            release()
                        }
                    }
        } finally {
            snapshotField.set(ConfigManager, originalSettings)
            moduleField.set(XposedCompat, originalModule)
        }
    }

    private fun state(count: Int, total: Int) = when (count) {
        0 -> InstallState.SKIPPED
        total -> InstallState.INSTALLED
        else -> InstallState.PARTIAL
    }

    private fun method(name: String): Method = Fixture::class.java.getDeclaredMethod(name)

    class Fixture {
        fun csj() = true
        fun gdt() = true
        fun baidu() = true
        fun sdkStart() = Unit
        fun frameworkInit() = Unit
        fun cookie() = false
        fun unrelatedAdAb() = true
    }

    private fun FakeHookApi.invoke(member: Executable, original: () -> Any?): Any? {
        val callback = callbacks[member] ?: return original()
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "proceed" -> original()
                else -> error("Unexpected callback work: " + method.name)
            }
        } as XposedInterface.Chain
        return callback.intercept(chain)
    }
}
