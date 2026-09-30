package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.contracts.BuildIdentity
import com.forbidad4tieba.hook.contracts.DiagnosticSink
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.ModuleBuildInfo
import com.forbidad4tieba.hook.contracts.ModuleLocation
import com.forbidad4tieba.hook.contracts.ModuleLocationProvider
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedModule

internal object ModuleComposition {
    fun initialize(module: XposedModule, processName: String) {
        BuildIdentity.initialize(ModuleBuildInfo(
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.MIN_SUPPORTED_USER_SETTINGS_VERSION_CODE,
            BuildConfig.DEBUG,
        ))
        XposedCompat.attachModule(module)
        ModuleLocationProvider.initialize {
            runCatching {
                val info = module.moduleApplicationInfo
                ModuleLocation(
                    (listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty()).filter { it.isNotBlank() }.distinct(),
                    info.nativeLibraryDir,
                )
            }.getOrNull()
        }
        XposedCompat.configureLogging {
            !HookProcess.isSystemUi(processName) && ConfigManager.snapshot().isDetailedLoggingEnabled
        }
        Diagnostics.initialize(object : DiagnosticSink {
            override fun info(message: String) = XposedCompat.log(message)
            override fun debug(message: String) = XposedCompat.logD(message)
            override fun warning(message: String) = XposedCompat.logW(message)
            override fun error(failure: Throwable) = XposedCompat.log(failure)
            override fun detailed(): Boolean = XposedCompat.shouldOutputDetailedLogs()
        })
    }
}
