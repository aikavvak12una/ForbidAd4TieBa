package com.baidu.tieba.parser

@Suppress("UNUSED_PARAMETER")
internal class LowScoreScheduler {
    fun restore(key: String): Boolean = false
    fun wrongParameter(key: Int): Boolean = false
    fun wrongReturn(key: String): Int = 0
    companion object { @JvmStatic fun staticRestore(key: String): Boolean = false }
}
