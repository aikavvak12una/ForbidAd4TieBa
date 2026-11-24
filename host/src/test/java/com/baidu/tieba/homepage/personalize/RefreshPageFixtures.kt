package com.baidu.tieba.homepage.personalize

import android.content.Context

@Suppress("UNUSED_PARAMETER")
internal class PersonalizePageView {
    fun trigger() {}
    fun initialize(context: Context) {}
    fun initializeCache() {}
    fun resume() {}
    fun setViewForeground() {}
    fun setViewForeground(value: Boolean) {}
    fun wrongReturn(): Boolean = false
    companion object { @JvmStatic fun staticInitializeCache() {} }
}
