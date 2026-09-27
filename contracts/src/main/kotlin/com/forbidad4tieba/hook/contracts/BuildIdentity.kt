package com.forbidad4tieba.hook.contracts

/** Values supplied by the APK entry point; libraries never reflect on app BuildConfig. */
data class ModuleBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val minSupportedSettingsVersionCode: Int,
    val debug: Boolean,
) {
    val cacheIdentity: String get() = "$versionName:$versionCode"
}

object BuildIdentity {
    @Volatile private var installed: ModuleBuildInfo? = null
    val current: ModuleBuildInfo get() = checkNotNull(installed) { "Build identity has not been supplied by app" }

    fun initialize(info: ModuleBuildInfo) {
        installed = info
    }
}
