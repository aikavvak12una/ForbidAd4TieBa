package com.forbidad4tieba.hook

import android.annotation.TargetApi
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** APK entry point. Build identity and platform lifecycle are composed here. */
class MainHook : XposedModule() {
    private val lifecycle by lazy { createHookLifecycle { detach() } }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        ModuleComposition.initialize(this)
        lifecycle.onModuleLoaded(param.processName)
    }

    @TargetApi(android.os.Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        lifecycle.onPackageLoaded(param.packageName, param.defaultClassLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        lifecycle.onPackageReady(param.packageName, param.classLoader)
    }
}
