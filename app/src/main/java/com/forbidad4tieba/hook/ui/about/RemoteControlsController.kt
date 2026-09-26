package com.forbidad4tieba.hook.ui.about

import android.content.Context
import com.forbidad4tieba.hook.BuildConfig
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.TiebaAccountIdentity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Keeps asynchronous policy application separate from parsing and hook installation. */
internal object RemoteControlsController {
    private class Active(val controls: RemoteControls, val environment: RuntimeEnvironment)
    private val lock = Any()
    private val retryRunning = AtomicBoolean(false)
    private var active: Active? = null
    @Volatile private var pending: Active? = null

    fun apply(context: Context, controls: RemoteControls, environment: RuntimeEnvironment) {
        val accountId = TiebaAccountIdentity.currentAccountId(context)
        val revision = Active(controls, environment)
        synchronized(lock) {
            active = revision
            pending = revision.takeIf { accountId == null && RemoteControlPolicy.dependsOnAccountId(controls) }
            applyLocked(context, revision, accountId)
        }
        if (pending != null) scheduleAccountRetry(context)
    }

    private fun applyLocked(context: Context, revision: Active, accountId: String?) {
        val level = revision.controls.forLevel(revision.environment.environmentRatingLevel)
        val result = RemoteControlPolicy.evaluate(
            rules = revision.controls.rules,
            conditionContext = RemoteControlPolicy.RemoteConditionContext(
                revision.environment, accountId, BuildConfig.VERSION_CODE,
            ),
        )
        ConfigManager.applyRemoteEnvironmentControls(
            context,
            showWarningDialog = level.showWarningDialog || result.showWarningDialog,
            lockHiddenFeatures = level.lockHiddenFeatures || result.lockHiddenFeatures,
        )
        RemoteCustomDialogs.enqueue(context, result.customDialogs)
        XposedCompat.logD {
            "[AboutInfo] remote controls applied: environmentRatingLevel=${revision.environment.environmentRatingLevel} " +
                "showWarningDialog=${level.showWarningDialog || result.showWarningDialog} " +
                "lockHiddenFeatures=${level.lockHiddenFeatures || result.lockHiddenFeatures} " +
                "matchedRules=${result.matchedRuleCount} customDialogs=${result.customDialogs.size}"
        }
    }

    private fun scheduleAccountRetry(context: Context) {
        if (!retryRunning.compareAndSet(false, true)) return
        val app = context.applicationContext ?: context
        thread(name = "tbhook-remote-controls-account-retry", isDaemon = true) {
            var lastAttempt: Active? = null
            try {
                Thread.sleep(1000L)
                for (attempt in 0..5) {
                    val candidate = pending ?: return@thread
                    lastAttempt = candidate
                    val accountId = TiebaAccountIdentity.currentAccountId(app)
                    if (accountId != null) {
                        val applied = synchronized(lock) {
                            // A response arriving during account lookup owns the newer decision.
                            if (active !== candidate) false else {
                                applyLocked(app, candidate, accountId)
                                if (pending === candidate) pending = null
                                true
                            }
                        }
                        if (applied) return@thread
                    }
                    if (attempt < 5) Thread.sleep(2000L)
                }
                XposedCompat.logD("[AboutInfo] remote controls skipped: account id unavailable after retry")
            } catch (failure: Throwable) {
                XposedCompat.logD { "[AboutInfo] remote controls account retry stopped: ${failure.message}" }
            } finally {
                synchronized(lock) {
                    if (pending === lastAttempt) pending = null
                    retryRunning.set(false)
                    // Only a newly published policy can start another bounded retry sequence.
                    if (pending != null) scheduleAccountRetry(app)
                }
            }
        }
    }
}
