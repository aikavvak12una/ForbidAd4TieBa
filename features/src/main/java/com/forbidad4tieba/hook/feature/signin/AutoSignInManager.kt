package com.forbidad4tieba.hook.feature.signin

import com.forbidad4tieba.hook.config.AccountPreferences
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.AutoSignInResultDialog
import com.forbidad4tieba.hook.ui.UiText
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object AutoSignInManager {
    private const val TAG = "TBHook-AutoSignIn"
    private const val COOKIE_URL = "https://tieba.baidu.com"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val running = AtomicBoolean(false)
    private val persistenceBlocked = mutableSetOf<String>()

    fun tryAutoSignIn(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!force && !AccountPreferences.isAutoSignInEnabled(app)) return
        if (!running.compareAndSet(false, true)) {
            if (force) toast(context, UiText.AutoSignIn.TOAST_TASK_RUNNING)
            return
        }
        val screen = WeakReference(context)
        if (force) toast(context, UiText.AutoSignIn.TOAST_START_MANUAL)
        thread(isDaemon = true, name = "tbhook-auto-signin") {
            val started = System.currentTimeMillis()
            var accountKey: String? = null
            try {
                if (!hasLoginCookie(force) { force || AccountPreferences.isAutoSignInEnabled(app) }) {
                    if (force) toast(app, UiText.AutoSignIn.TOAST_BDUSS_MISSING)
                    return@thread
                }
                val network = AutoSignInNetwork.resolve(app)
                if (network == null) {
                    if (force) toast(app, UiText.AutoSignIn.TOAST_UNAVAILABLE)
                    return@thread
                }
                val accountId = network.currentAccountId()
                if (accountId.isEmpty()) {
                    if (force) toast(app, UiText.AutoSignIn.TOAST_BDUSS_MISSING)
                    return@thread
                }
                accountKey = AutoSignInNoticePolicy.accountKey(accountId)
                if (accountKey in persistenceBlocked) {
                    if (force) toast(app, UiText.AutoSignIn.TOAST_STORAGE_ERROR)
                    return@thread
                }
                val day = currentDay()
                val store = AutoSignInStateStore(ConfigManager.getModuleStatePrefs(app), accountKey)
                val state = store.load(day)
                val active = {
                    (force || AccountPreferences.isAutoSignInEnabled(app)) &&
                        currentDay() == day && network.currentAccountId() == accountId
                }
                val report = AutoSignInTask(network, store::save, active, ::pause)
                    .run(state, force) ?: return@thread
                if (!active()) return@thread
                AutoSignInFeedback.publish(app, accountKey, state, store, report)
                XposedCompat.log("$TAG: ended account=$accountKey day=$day signed=${report.signed} " +
                    "already=${report.alreadySigned} failed=${report.failures.size} " +
                    "taskFailure=${report.taskFailure?.kind} automaticDone=${state.automaticDone} force=$force")
                if (force) mainHandler.post {
                    screen.get()?.takeIf { isUsable(it) && active() }?.let { target ->
                        AutoSignInResultDialog.show(target, report) { tryAutoSignIn(target, true) }
                    }
                }
            } catch (t: Throwable) {
                // Fail closed after a persistence/parser fault; never keep issuing requests
                // when the saved retry budget cannot be trusted.
                accountKey?.let { persistenceBlocked.add(it) }
                XposedCompat.logW("$TAG: stopped: ${t.javaClass.simpleName}: ${t.message}")
                if (force) toast(app, UiText.AutoSignIn.TOAST_STORAGE_ERROR)
            } finally {
                running.set(false)
                XposedCompat.log("$TAG: worker finished elapsedMs=${System.currentTimeMillis() - started}")
            }
        }
    }

    /** The existing row action is also the quiet, permission-independent result viewer. */
    fun showResult(context: Context) {
        val screen = WeakReference(context)
        val app = context.applicationContext
        thread(isDaemon = true, name = "tbhook-signin-result") {
            try {
                val network = AutoSignInNetwork.resolve(app)
                val accountId = network?.currentAccountId().orEmpty()
                if (accountId.isEmpty()) {
                    toast(app, UiText.AutoSignIn.TOAST_BDUSS_MISSING)
                    return@thread
                }
                val store = AutoSignInStateStore(ConfigManager.getModuleStatePrefs(app),
                    AutoSignInNoticePolicy.accountKey(accountId))
                val report = store.latestReport()
                mainHandler.post {
                    screen.get()?.takeIf { isUsable(it) && network?.currentAccountId() == accountId }?.let { target ->
                        AutoSignInResultDialog.show(target, report) { tryAutoSignIn(target, true) }
                    }
                }
            } catch (t: Throwable) {
                XposedCompat.logW("$TAG: result read failed: ${t.javaClass.simpleName}")
                toast(app, UiText.AutoSignIn.TOAST_STORAGE_ERROR)
            }
        }
    }

    private fun hasLoginCookie(force: Boolean, active: () -> Boolean): Boolean {
        val attempts = if (force) 1 else 3
        repeat(attempts) { index ->
            if (!active()) return false
            val cookie = CookieManager.getInstance().getCookie(COOKIE_URL).orEmpty()
            if (cookie.split(';').any { it.trim().startsWith("BDUSS=") &&
                    it.trim().removePrefix("BDUSS=").isNotEmpty() }) return true
            if (index < attempts - 1 && !pause(5000L)) return false
        }
        return false
    }

    private fun currentDay() = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())

    private fun pause(duration: Long): Boolean = try {
        Thread.sleep(duration)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun isUsable(context: Context): Boolean =
        context !is Activity || (!context.isFinishing && !context.isDestroyed)

    private fun toast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
