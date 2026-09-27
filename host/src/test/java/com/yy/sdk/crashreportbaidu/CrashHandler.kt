package com.yy.sdk.crashreportbaidu

/** Type-only SDK fixture. No crash handling or reporting is performed. */
@Suppress("UNUSED_PARAMETER")
class CrashHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {}
    fun renamed(error: Throwable) {}
    fun wrongSignature(error: String) {}
}
