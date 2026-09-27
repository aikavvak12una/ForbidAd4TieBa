package com.forbidad4tieba.hook.contracts

data class ModuleLocation(val apkPaths: List<String>, val nativeLibraryDir: String?)

/** libxposed owns the location; host code only needs these filesystem values. */
object ModuleLocationProvider {
    @Volatile private var provider: () -> ModuleLocation? = { null }
    fun initialize(provider: () -> ModuleLocation?) { this.provider = provider }
    fun current(): ModuleLocation? = provider()
}
