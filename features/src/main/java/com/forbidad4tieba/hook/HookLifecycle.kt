package com.forbidad4tieba.hook

interface HookLifecycle {
    fun onModuleLoaded(processName: String)
    fun onPackageLoaded(packageName: String, defaultClassLoader: ClassLoader)
    fun onPackageReady(packageName: String, classLoader: ClassLoader)
}

fun createHookLifecycle(detach: () -> Unit): HookLifecycle = FeatureLifecycle(detach)
