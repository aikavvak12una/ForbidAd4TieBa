package com.forbidad4tieba.hook.feature.perf

import android.view.View
import com.forbidad4tieba.hook.FeatureDefinition
import com.forbidad4tieba.hook.FeaturePhase
import com.forbidad4tieba.hook.FeatureProcess
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.AiImageCreationContract
import com.forbidad4tieba.hook.symbol.model.AiImageCreationSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageField
import com.forbidad4tieba.hook.symbol.model.AiImageMethod
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import io.github.libxposed.api.XposedInterface

internal val AiImageCreationFeature = FeatureDefinition.single(
    id = "AiImageCreationHook", phase = FeaturePhase.POST_ATTACH, process = FeatureProcess.IMAGE_VIEWER,
    enabled = { it.isAiImageCreationDisabled && available(HookFeatureKey.DISABLE_AI_IMAGE_CREATION) },
) { cl, _ -> AiImageCreationHook.hook(AiImageCreationContract.resolveAiImageCreationSymbols(cl, symbols)) }

internal object AiImageCreationHook {
    private var runtime: AiImageCreationRuntime? = null

    @Synchronized fun hook(targets: AiImageCreationSymbols?): InstallOutcome {
        if (!ConfigManager.snapshot().isAiImageCreationDisabled) return InstallOutcome.skipped("config disabled")
        val api = XposedCompat.module ?: return InstallOutcome.skipped("module unavailable")
        if (runtime?.active == true) return InstallOutcome(InstallState.ALREADY_INSTALLED, runtime!!.handleCount)
        val remaining = runtime?.release().orEmpty()
        if (remaining.isNotEmpty()) return InstallOutcome(InstallState.ROLLBACK_FAILED, runtime!!.handleCount, remaining.joinToString())
        runtime = null
        if (targets == null) return InstallOutcome.skipped("complete AI image group unavailable")
        val next = AiImageCreationRuntime(api, targets) { ConfigManager.snapshot().isAiImageCreationDisabled }
        val result = next.install()
        // Retain ownership only while handles remain, including failed unhooks awaiting a retry.
        if (next.handleCount > 0) runtime = next
        return result
    }
}

/** Entry suppression only: no upload callbacks, task tracking or WebView-wide hooks. */
internal class AiImageCreationRuntime(
    private val api: XposedInterface,
    private val targets: AiImageCreationSymbols,
    private val enabled: () -> Boolean,
) {
    private val jumpScheme = targets[AiImageMethod.IMAGE_JUMP_SCHEME]
    private val inputSchema = targets[AiImageMethod.INPUT_AI_SCHEMA]
    private val capsule = targets[AiImageMethod.PB_GUIDE_CAPSULE]
    private val webUrl = targets[AiImageField.WEB_URL]
    private val handles = ArrayList<XposedInterface.HookHandle>()
    @Volatile var active = false
        private set
    val handleCount: Int get() = handles.size

    fun install(): InstallOutcome {
        if (!enabled()) return InstallOutcome.skipped("config disabled")
        if (active) return InstallOutcome(InstallState.ALREADY_INSTALLED, handleCount)
        try {
            intercept(AiImageMethod.IMAGE_JUMP_DATA) { chain ->
                val data = chain.proceed()
                if (data != null && AiImageRoute.matches(jumpScheme.invoke(data) as? String)) null else data
            }
            intercept(AiImageMethod.ROUTE_DISPATCH) { chain ->
                val destination = (chain.args.getOrNull(2) as? Array<*>)?.firstOrNull() as? String
                if (AiImageRoute.matches(destination)) false else chain.proceed()
            }
            intercept(AiImageMethod.WEB_CONFIG_BUILD) { chain ->
                if (AiImageRoute.matches(webUrl.get(chain.thisObject) as? String)) null else chain.proceed()
            }
            intercept(AiImageMethod.INPUT_AI_VISIBLE) { chain ->
                if (AiImageRoute.matches(inputSchema.invoke(chain.thisObject) as? String)) 0 else chain.proceed()
            }
            intercept(AiImageMethod.PB_GUIDE_BIND) { chain ->
                // The caller attaches the capsule, rather than this binding holder's outer View.
                (capsule.invoke(chain.thisObject) as? View)?.visibility = View.GONE
                null
            }
            intercept(AiImageMethod.IMAGE_BOTTOM_TIP_BIND) { chain ->
                if (AiImageRoute.matches(chain.args.getOrNull(1) as? String)) {
                    (chain.thisObject as? View)?.visibility = View.GONE
                    null
                } else chain.proceed()
            }
            check(handles.all(RuntimeHooks::ownsActiveHandle)) { "An AI image handle is inactive" }
            active = true
            return InstallOutcome(InstallState.INSTALLED, handleCount)
        } catch (failure: Throwable) {
            val errors = release()
            return InstallOutcome(if (errors.isEmpty()) InstallState.ROLLED_BACK else InstallState.ROLLBACK_FAILED,
                handleCount, (listOf(failure.message.orEmpty()) + errors).joinToString("; "))
        }
    }

    fun release(): List<String> {
        active = false
        val errors = ArrayList<String>()
        for (index in handles.lastIndex downTo 0) {
            val handle = handles[index]
            try { handle.unhook(); handles.removeAt(index) }
            catch (failure: Throwable) { errors += failure.message.orEmpty() }
        }
        return errors
    }

    private fun intercept(role: AiImageMethod, callback: (XposedInterface.Chain) -> Any?) {
        handles += RuntimeHooks.builder(api, targets[role], "AiImageCreationHook", role.name).intercept { chain ->
            if (!active || !enabled()) chain.proceed() else callback(chain)
        }
    }
}
