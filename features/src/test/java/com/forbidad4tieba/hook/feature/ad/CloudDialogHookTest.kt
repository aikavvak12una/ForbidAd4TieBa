package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDialogHookTest {
    private val admission = Host::class.java.getDeclaredMethod("admit", Context::class.java, Data::class.java, JSONObject::class.java)
    private val targets = CloudDialogSymbols(
        admissionMethod = admission,
        dialogNameField = Data::class.java.getField("dialogName"),
        dialogUrlField = Data::class.java.getField("dialogUrl"),
        dialogStrategiesField = Data::class.java.getField("dialogStrategy"),
        strategyTypeMethod = Strategy::class.java.getDeclaredMethod("type"),
        resultConstructor = Result::class.java.getDeclaredConstructor(Boolean::class.javaPrimitiveType, String::class.java, String::class.java),
        contextTimeMethod = Context::class.java.getDeclaredMethod("time"),
        timeNameMethod = Time::class.java.getDeclaredMethod("name"),
    )

    @Test
    fun oneInstalledCallbackSwitchesModesAndNeverStartsRejectedAttempts() = withRuntime { api ->
        setMode(enabled = true, strict = false)
        CloudDialogHook.hook(targets)
        val callback = api.callbacks.getValue(admission)
        var starts = 0
        val hostResult = Result(true, "", "")
        val unknown = args("futureActivity", "https://tieba.baidu.com/mo/q/new/promotion")
        assertSame(hostResult, callback.call(unknown) { starts++; hostResult })
        val gta = args("gta6", "https://tieba.baidu.com/mo/q/hybrid-usergrow-activity/reinvitationCard?dataUrl=simpleDialog&dialog_name=gta6")
        val blackResult = callback.call(gta) { starts++; hostResult } as Result
        assertFalse(blackResult.allowed)

        setMode(enabled = true, strict = true)
        CloudDialogHook.hook(targets)
        assertEquals("Changing policy must retain one admission hook", 1, api.created)
        val first = callback.call(unknown) { starts++; hostResult } as Result
        val second = callback.call(unknown) { starts++; hostResult } as Result
        assertNotSame(first, second)
        first.name = "first"
        assertEquals("", second.name)
        assertSame(hostResult, callback.call(args("frsCopyLinkGuide", "dialoginternal://frsCopyLinkGuide", "NA_FRS_COPY_LINK_GUIDE_STRATEGY")) { starts++; hostResult })
        assertEquals("Only the two admitted attempts enter host code", 2, starts)
    }

    @Test
    fun masterOffAndReleasedCallbacksReadNoHostArguments() = withRuntime { api ->
        setMode(enabled = true, strict = true)
        CloudDialogHook.hook(targets)
        val callback = api.callbacks.getValue(admission)
        val result = Any()
        setMode(enabled = false, strict = true)
        assertSame(result, callback.call(null) { result })
        release()
        setMode(enabled = true, strict = true)
        assertSame(result, callback.call(null) { result })
    }

    @Test
    fun malformedMetadataIsRejectedOnlyInStrictMode() {
        val values = listOf(Context(), Any(), JSONObject())
        assertFalse(CloudDialogHook.evaluate(values, strict = false, targets).block)
        assertTrue(CloudDialogHook.evaluate(values, strict = true, targets).block)
    }

    @Test
    fun explicitJsonNullIsStillAnAppendedParameterForWhitelistValidation() {
        val data = Data("levelUpPopuplevel10",
            "https://tieba.baidu.com/mo/q/hybrid-main-user/levelUpPopup?level=10",
            listOf(Strategy("NEW_FREQUENCE_STRATEGY")))
        for (key in listOf("level", "unknown_action")) {
            val values = listOf(Context(), data, JSONObject().put(key, JSONObject.NULL))
            assertTrue(CloudDialogHook.evaluate(values, strict = true, targets).block)
            assertFalse(CloudDialogHook.evaluate(values, strict = false, targets).block)
        }
    }

    private fun args(name: String, url: String, type: String? = null): List<Any?> =
        listOf(Context(), Data(name, url, listOfNotNull(type?.let(::Strategy))), JSONObject())

    private fun setMode(enabled: Boolean, strict: Boolean) {
        ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
            .set(ConfigManager, SettingsSnapshot.bootstrap().copy(
                isCloudDialogAdBlockEnabled = enabled,
                isCloudDialogWhitelistEnabled = strict,
            ))
    }

    private fun withRuntime(test: (FakeHookApi) -> Unit) {
        val settingsField = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val moduleField = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val oldSettings = settingsField.get(ConfigManager)
        val oldModule = moduleField.get(XposedCompat)
        val api = FakeHookApi(allowLog = true)
        try {
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            test(api)
        } finally {
            release()
            settingsField.set(ConfigManager, oldSettings)
            moduleField.set(XposedCompat, oldModule)
        }
    }

    private fun release() {
        val owned = CloudDialogHook::class.java.getDeclaredField("installedMethods\$delegate").apply { isAccessible = true }.get(null) as Lazy<*>
        assertTrue((owned.value as OwnedHookSet<*, *>).rollback().isEmpty())
        val runtime = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }.get(null) as OwnedHookSet<*, *>
        assertTrue(runtime.rollback().isEmpty())
    }

    private fun XposedInterface.Hooker.call(args: List<Any?>?, host: () -> Any?): Any? = intercept(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getArgs" -> checkNotNull(args) { "Disabled callback read host arguments" }
                "proceed" -> host()
                else -> error("Unexpected callback work: ${method.name}")
            }
        } as XposedInterface.Chain,
    )

    class Data(@JvmField val dialogName: String, @JvmField val dialogUrl: String, @JvmField val dialogStrategy: List<Strategy>)
    class Strategy(private val value: String) { fun type() = value }
    class Context { fun time() = Time() }
    class Time { fun name() = "frs_page" }
    class Result(val allowed: Boolean, val code: String, val detail: String) { var name = "" }
    class Host { fun admit(context: Context, data: Data, parameters: JSONObject) = Result(true, "", "") }
}
