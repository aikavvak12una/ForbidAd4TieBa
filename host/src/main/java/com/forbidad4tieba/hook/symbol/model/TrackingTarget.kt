package com.forbidad4tieba.hook.symbol.model

/** Active tracking entry points in the current host. */
enum class TrackingTarget(val className: String, val methodName: String) {
    LOKI_SERVICE("com.baidu.searchbox.logsystem.basic.LokiService", "onStartCommand"),
    PAGE_TRACE("com.baidu.searchbox.track.Track", "startTrack"),
}
