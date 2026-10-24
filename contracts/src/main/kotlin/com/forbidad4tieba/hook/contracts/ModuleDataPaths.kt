package com.forbidad4tieba.hook.contracts

/** Names shared by storage writers and the explicit module-data reset. Never host-wide directories. */
object ModuleDataPaths {
    const val USER_SETTINGS = "tbhook_user_settings"
    const val MODULE_STATE = "tbhook_module_state"
    const val SYMBOL_CACHE = "tbhook_symbol_cache"
    const val LEGACY_SETTINGS = "tiebahook_settings"
    const val ABOUT_DIRECTORY = "tbhook"
    const val LEGACY_ABOUT_FILE = "tbhook_about_cache.json"
    const val COLLECTION_DIRECTORY = "tbhook_cache"
    const val BACKGROUND_DIRECTORY = "home_native_glass"
    const val MODEL_STATS_FILE = "tbhook_model_score_stats.tsv"
    const val SHARE_DIRECTORY = "com_qq_e_download/tbhook_share"
    const val NATIVE_DIRECTORY = "tbhook_native_libs"
    const val RESET_REQUEST_FILE = "tbhook_reset_request"

    enum class Area { FILES, CACHE, CODE_CACHE }
    data class Entry(val area: Area, val relativePath: String, val fullResetOnly: Boolean = false)

    fun preferenceNames(fullReset: Boolean): List<String> =
        listOfNotNull(USER_SETTINGS.takeIf { fullReset }, MODULE_STATE, SYMBOL_CACHE, LEGACY_SETTINGS)

    val files = listOf(
        Entry(Area.FILES, LEGACY_ABOUT_FILE),
        Entry(Area.FILES, "$LEGACY_ABOUT_FILE.tmp"),
        Entry(Area.FILES, "$LEGACY_ABOUT_FILE.bak"),
        Entry(Area.FILES, ABOUT_DIRECTORY),
        Entry(Area.FILES, COLLECTION_DIRECTORY),
        Entry(Area.FILES, MODEL_STATS_FILE),
        Entry(Area.FILES, "$MODEL_STATS_FILE.tmp"),
        Entry(Area.FILES, "$MODEL_STATS_FILE.bak"),
        Entry(Area.CACHE, SHARE_DIRECTORY),
        Entry(Area.FILES, BACKGROUND_DIRECTORY, fullResetOnly = true),
        Entry(Area.CODE_CACHE, NATIVE_DIRECTORY, fullResetOnly = true),
        Entry(Area.CACHE, NATIVE_DIRECTORY, fullResetOnly = true),
    )
}
