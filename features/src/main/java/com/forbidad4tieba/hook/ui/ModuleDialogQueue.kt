package com.forbidad4tieba.hook.ui

import android.os.Handler
import android.os.Looper
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.ArrayDeque

internal object ModuleDialogQueue {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<() -> Unit>()
    private var active = false

    fun enqueue(show: () -> Unit) {
        val startNow = synchronized(this) {
            if (active) {
                pending.addLast(show)
                false
            } else {
                active = true
                true
            }
        }
        if (startNow) {
            runOnMain(show)
        }
    }

    fun finishCurrent() {
        val next = synchronized(this) {
            if (pending.isEmpty()) {
                active = false
                null
            } else {
                pending.removeFirst()
            }
        }
        if (next != null) {
            runOnMain(next)
        }
    }

    private fun runOnMain(show: () -> Unit) {
        mainHandler.post {
            try {
                show()
            } catch (t: Throwable) {
                XposedCompat.logW("[ModuleDialogQueue] show failed: ${t.message}")
                finishCurrent()
            }
        }
    }
}
