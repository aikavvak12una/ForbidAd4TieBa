package com.forbidad4tieba.hook.config

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {
    private const val PREFS_NAME = "tiebahook_settings"
    
    @Volatile private var prefs: SharedPreferences? = null
    
    @Volatile var isAdBlockEnabled: Boolean = true
    @Volatile var isLiveBlockEnabled: Boolean = false
    @Volatile var isHomeTabSimplifyEnabled: Boolean = false
    @Volatile var isBottomTabSimplifyEnabled: Boolean = false
    @Volatile var isEnterForumWebFilterEnabled: Boolean = false
    @Volatile var isHidePbBottomEnterBarEnabled: Boolean = false
    @Volatile var isFeedVideoBlockEnabled: Boolean = false
    @Volatile var isMyForumBlockEnabled: Boolean = false
    @Volatile var isAutoRefreshDisabled: Boolean = false
    @Volatile var isAutoLoadMoreEnabled: Boolean = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        updateMemory(sharedPreferences, key)
    }

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            
            isAdBlockEnabled = p.getBoolean("block_ad", true)
            isLiveBlockEnabled = p.getBoolean("block_live", false)
            isHomeTabSimplifyEnabled = p.getBoolean("simplify_home_tabs", false)
            isBottomTabSimplifyEnabled = p.getBoolean("simplify_bottom_tabs", false)
            isEnterForumWebFilterEnabled = p.getBoolean("filter_enter_forum_web", false)
            isHidePbBottomEnterBarEnabled = p.getBoolean("hide_pb_bottom_enter_bar", false)
            isFeedVideoBlockEnabled = p.getBoolean("block_feed_video", false)
            isMyForumBlockEnabled = p.getBoolean("block_my_forum", false)
            isAutoRefreshDisabled = p.getBoolean("disable_auto_refresh", false)
            isAutoLoadMoreEnabled = p.getBoolean("enable_auto_load_more", false)
            
            p.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun updateMemory(p: SharedPreferences, key: String?) {
        when (key) {
            "block_ad" -> isAdBlockEnabled = p.getBoolean(key, true)
            "block_live" -> isLiveBlockEnabled = p.getBoolean(key, false)
            "simplify_home_tabs" -> isHomeTabSimplifyEnabled = p.getBoolean(key, false)
            "simplify_bottom_tabs" -> isBottomTabSimplifyEnabled = p.getBoolean(key, false)
            "filter_enter_forum_web" -> isEnterForumWebFilterEnabled = p.getBoolean(key, false)
            "hide_pb_bottom_enter_bar" -> isHidePbBottomEnterBarEnabled = p.getBoolean(key, false)
            "block_feed_video" -> isFeedVideoBlockEnabled = p.getBoolean(key, false)
            "block_my_forum" -> isMyForumBlockEnabled = p.getBoolean(key, false)
            "disable_auto_refresh" -> isAutoRefreshDisabled = p.getBoolean(key, false)
            "enable_auto_load_more" -> isAutoLoadMoreEnabled = p.getBoolean(key, false)
        }
    }
    
    fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
            init(context)
        }
    }
}
