package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.config.SimpleToggle

internal enum class FeaturePhase(val diagnosticName: String) {
    STATIC("static"), POST_ATTACH("postAttach"), SYMBOL("symbol"),
}

internal enum class FeatureProcess {
    ANY, MAIN, IMAGE_VIEWER, IMAGE_VIEWER_REMOTE, SYSTEM_UI;

    fun accepts(context: HookInstallContext): Boolean = when (this) {
        ANY -> !context.isSystemUi
        MAIN -> context.isMain
        IMAGE_VIEWER -> context.isImageViewerProcess
        IMAGE_VIEWER_REMOTE -> context.isImageViewerRemote
        SYSTEM_UI -> context.isSystemUi
    }
}

/** The owner supplies eligibility and lazy installers; the catalog only orders them. */
internal class FeatureDefinition(
    val id: String,
    val phase: FeaturePhase,
    val process: FeatureProcess,
    val toggle: SimpleToggle? = null,
    private val factory: (HookInstallContext, SettingsSnapshot) -> List<HookInstallEntry>,
) {
    fun entries(context: HookInstallContext, settings: SettingsSnapshot): List<HookInstallEntry> {
        if (!process.accepts(context)) return emptyList()
        return try {
            if (toggle != null && (!settings[toggle] || !context.available(toggle.capabilityKey))) {
                return emptyList()
            }
            factory(context, settings)
        } catch (failure: Exception) {
            planningFailure(failure)
        } catch (failure: LinkageError) {
            planningFailure(failure)
        }
    }

    private fun planningFailure(failure: Throwable): List<HookInstallEntry> {
        // Keep the failed owner in the normal installation ledger without aborting its siblings.
        val outcome = InstallOutcome(InstallState.FAILED,
            reason = "plan: ${failure.javaClass.name}: ${failure.message.orEmpty()}")
        return listOf(HookInstallEntry(id) { outcome })
    }

    companion object {
        fun single(
            id: String,
            phase: FeaturePhase,
            process: FeatureProcess,
            enabled: HookInstallContext.(SettingsSnapshot) -> Boolean = { true },
            toggle: SimpleToggle? = null,
            install: HookInstallContext.(ClassLoader, SettingsSnapshot) -> InstallOutcome?,
        ) = FeatureDefinition(id, phase, process, toggle) { context, settings ->
            if (context.enabled(settings)) {
                listOf(HookInstallEntry(id) { cl -> context.install(cl, settings) })
            } else emptyList()
        }

        fun observed(
            id: String,
            phase: FeaturePhase,
            process: FeatureProcess,
            enabled: HookInstallContext.(SettingsSnapshot) -> Boolean = { true },
            toggle: SimpleToggle? = null,
            install: HookInstallContext.(ClassLoader, SettingsSnapshot) -> Unit,
        ) = single(id, phase, process, enabled, toggle) { cl, settings ->
            install(cl, settings)
            null
        }
    }
}
