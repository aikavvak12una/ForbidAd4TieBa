package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot

internal enum class FeaturePhase(val diagnosticName: String) {
    STATIC("static"), POST_ATTACH("postAttach"), SYMBOL("symbol"),
}

internal enum class FeatureProcess {
    ANY, MAIN, IMAGE_VIEWER, IMAGE_VIEWER_REMOTE;

    fun accepts(context: HookInstallContext): Boolean = when (this) {
        ANY -> true
        MAIN -> context.isMain
        IMAGE_VIEWER -> context.isImageViewerProcess
        IMAGE_VIEWER_REMOTE -> context.isImageViewerRemote
    }
}

/** The owner supplies eligibility and lazy installers; the catalog only orders them. */
internal class FeatureDefinition(
    val id: String,
    val phase: FeaturePhase,
    val process: FeatureProcess,
    private val factory: (HookInstallContext, SettingsSnapshot) -> List<HookInstallEntry>,
) {
    fun entries(context: HookInstallContext, settings: SettingsSnapshot): List<HookInstallEntry> =
        if (process.accepts(context)) factory(context, settings) else emptyList()

    companion object {
        fun single(
            id: String,
            phase: FeaturePhase,
            process: FeatureProcess,
            enabled: HookInstallContext.(SettingsSnapshot) -> Boolean = { true },
            install: HookInstallContext.(ClassLoader, SettingsSnapshot) -> InstallOutcome?,
        ) = FeatureDefinition(id, phase, process) { context, settings ->
            if (context.enabled(settings)) {
                listOf(HookInstallEntry(id) { cl -> context.install(cl, settings) })
            } else emptyList()
        }

        fun observed(
            id: String,
            phase: FeaturePhase,
            process: FeatureProcess,
            enabled: HookInstallContext.(SettingsSnapshot) -> Boolean = { true },
            install: HookInstallContext.(ClassLoader, SettingsSnapshot) -> Unit,
        ) = single(id, phase, process, enabled) { cl, settings ->
            install(cl, settings)
            null
        }
    }
}
