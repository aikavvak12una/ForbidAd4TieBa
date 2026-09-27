package com.forbidad4tieba.hook.contracts

fun interface LogPolicy {
    fun detailed(): Boolean
}

interface DiagnosticSink {
    fun info(message: String)
    fun debug(message: String)
    fun warning(message: String)
    fun error(failure: Throwable)
    fun detailed(): Boolean
}

/** The composition root injects the sink before any host or configuration work. */
object Diagnostics {
    @Volatile private var sink: DiagnosticSink? = null

    fun initialize(sink: DiagnosticSink) { this.sink = sink }
    fun log(message: String) { sink?.info(message) }
    fun log(failure: Throwable) { sink?.error(failure) }
    fun logD(message: String) { sink?.debug(message) }
    inline fun logD(message: () -> String) { if (detailed()) logD(message()) }
    fun logW(message: String) { sink?.warning(message) }
    fun detailed(): Boolean = sink?.detailed() == true
}
