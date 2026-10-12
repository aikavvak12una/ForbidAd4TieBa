package com.baidu.tieba.pb.pb.main

class PbFragment {
    @JvmField var bottom: BottomListener? = null
}

interface BottomListener {
    fun onScrollToBottom()
}
