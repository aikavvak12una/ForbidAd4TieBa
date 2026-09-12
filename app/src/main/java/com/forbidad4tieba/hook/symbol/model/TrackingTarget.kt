package com.forbidad4tieba.hook.symbol.model

/** Current stable SDK entry points; no retained manual StatService event APIs. */
enum class TrackingTarget(val className: String, val methodName: String) {
    CLOSE_TRACE("com.baidu.mobstat.CooperService", "isCloseTrace"),
    LOKI_SERVICE("com.baidu.searchbox.logsystem.basic.LokiService", "onStartCommand"),
    PAGE_TRACE("com.baidu.searchbox.track.Track", "startTrack"),
}
