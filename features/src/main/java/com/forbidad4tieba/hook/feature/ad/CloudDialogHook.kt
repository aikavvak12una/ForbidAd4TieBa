package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** One admission hook owns both modes; the host still owns every admitted attempt. */
internal object CloudDialogHook {
    private val installedMethods by lazy {
        OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    }

    @Synchronized
    fun hook(targets: CloudDialogSymbols): InstallOutcome {
        if (!ConfigManager.snapshot().isCloudDialogAdBlockEnabled) return InstallOutcome.skipped("config disabled")
        val module = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        val method = targets.admissionMethod
        val alreadyInstalled = installedMethods.contains(method)
        return try {
            installedMethods.install(method) {
                val errorLogged = AtomicBoolean(false)
                RuntimeHooks.builder(module, method, "CloudDialogHook", "admission").intercept { chain ->
                    val settings = ConfigManager.snapshot()
                    if (!settings.isCloudDialogAdBlockEnabled) return@intercept chain.proceed()
                    val strict = settings.isCloudDialogWhitelistEnabled
                    val decision = try {
                        evaluate(chain.args, strict, targets)
                    } catch (failure: Exception) {
                        if (errorLogged.compareAndSet(false, true)) {
                            XposedCompat.logW("[CloudDialogHook] admission inspection failed: ${failure.javaClass.simpleName}")
                        }
                        CloudDialogDecision(strict, "inspection_failed", null)
                    }
                    if (!decision.block) return@intercept chain.proceed()
                    // The result consumer fills in the dialog name. Never share a mutable result.
                    targets.resultConstructor.newInstance(false, "module_policy", decision.reason)
                }
            }
            InstallOutcome(if (alreadyInstalled) InstallState.ALREADY_INSTALLED else InstallState.INSTALLED, 1)
        } catch (failure: Exception) {
            InstallOutcome(InstallState.FAILED, 0, "cloud admission: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }

    internal fun evaluate(args: List<Any?>, strict: Boolean, targets: CloudDialogSymbols): CloudDialogDecision {
        val data = args.getOrNull(1)
        val types = ArrayList<String>()
        if (data != null && targets.dialogNameField.declaringClass.isInstance(data)) {
            val strategies = targets.dialogStrategiesField.get(data) as? List<*>
            if (strategies != null) {
                if (strategies.size > MAX_STRATEGIES) return invalid(strict, "strategy_limit")
                for (strategy in strategies) {
                    if (strategy == null || !targets.strategyTypeMethod.declaringClass.isInstance(strategy)) {
                        return invalid(strict, "strategy_shape")
                    }
                    val type = targets.strategyTypeMethod.invoke(strategy) as? String
                        ?: return invalid(strict, "strategy_type")
                    types += type
                }
            }
        }
        val fields = LinkedHashMap<String, String>()
        val json = args.getOrNull(2)
        if (json != null && json !is JSONObject) return invalid(strict, "parameter_shape")
        if (json is JSONObject) {
            if (json.length() > MAX_PARAMETERS) return invalid(strict, "parameter_limit")
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                // parseUrl appends optString even for JSON payloads; preserve that actual meaning.
                val text = json.optString(key)
                if (key.length > MAX_KEY_LENGTH || text.length > MAX_PARAMETER_LENGTH) {
                    return invalid(strict, "parameter_size")
                }
                fields[key] = text
            }
        }
        val context = args.getOrNull(0)
        val time = if (context != null && targets.contextTimeMethod.declaringClass.isInstance(context)) {
            targets.contextTimeMethod.invoke(context)?.let { targets.timeNameMethod.invoke(it) as? String }
        } else null
        val validData = data != null && targets.dialogNameField.declaringClass.isInstance(data)
        return CloudDialogPolicy.evaluate(
            CloudDialogPolicyInput(
                mode = if (strict) CloudDialogMode.WHITELIST else CloudDialogMode.BLACKLIST,
                dialogName = if (validData) targets.dialogNameField.get(data) as? String else null,
                cachedUrl = if (validData) targets.dialogUrlField.get(data) as? String else null,
                strategyTypes = types,
                fields = fields,
                contextTime = time,
            ),
        )
    }

    private fun invalid(strict: Boolean, reason: String) = CloudDialogDecision(strict, reason, null)

    private const val MAX_STRATEGIES = 32
    private const val MAX_PARAMETERS = 64
    private const val MAX_KEY_LENGTH = 128
    private const val MAX_PARAMETER_LENGTH = 64 * 1024
}
