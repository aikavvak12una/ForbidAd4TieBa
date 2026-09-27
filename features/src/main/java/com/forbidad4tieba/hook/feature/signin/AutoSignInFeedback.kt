package com.forbidad4tieba.hook.feature.signin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object AutoSignInFeedback {
    private const val CHANNEL = "tbhook_auto_sign_result"
    private const val NOTIFICATION_ID = 0x5349474E

    // Posts as the host, whose permission is checked below. A module manifest permission would
    // not grant the host notification permission.
    @SuppressLint("MissingPermission")
    fun publish(context: Context, accountKey: String, state: SignInDayState,
                store: AutoSignInStateStore, report: SignInReport) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val tag = "tbhook_signin_$accountKey"
            if (!AutoSignInNoticePolicy.shouldNotify(report, state.notifiedFingerprint)) return
            if (!notificationsAllowed(context)) return
            val channel = NotificationChannel(CHANNEL, UiText.AutoSignIn.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW).apply {
                description = UiText.AutoSignIn.CHANNEL_DESCRIPTION
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
            if (!notificationsAllowed(context)) return
            postNotification(manager, tag, buildNotification(context, report))
            state.notifiedFingerprint = AutoSignInNoticePolicy.fingerprint(report)
            check(store.save(state)) { "Unable to persist sign-in notification fingerprint" }
        } catch (t: Exception) {
            // Permission/channel denial stays quiet. The report was saved before publishing.
            XposedCompat.logW("[AutoSignIn] notification unavailable: ${t.javaClass.simpleName}")
        }
    }

    // This binary-retained contract also applies when app Lint reads the features
    // library. publish checks the host permission before crossing this boundary.
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postNotification(manager: NotificationManager, tag: String, notification: Notification) {
        manager.notify(tag, NOTIFICATION_ID, notification)
    }

    fun notificationsAllowed(context: Context): Boolean = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager != null && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    internal fun buildNotification(context: Context, report: SignInReport): Notification {
        val title = when {
            report.taskFailure != null -> UiText.AutoSignIn.TASK_FAILED_TITLE
            report.hasFailures -> UiText.AutoSignIn.failureTitle(report.failures.size)
            else -> UiText.AutoSignIn.SUCCESS_TITLE
        }
        val icon = if (report.hasFailures) android.R.drawable.stat_notify_error
            else android.R.drawable.stat_notify_sync_noanim
        val summary = AutoSignInReportText.summary(report)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(AutoSignInReportText.detail(report, 8)))
            .setSubText(UiText.AutoSignIn.VIEW_RESULT_HINT)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(longArrayOf())
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPublicVersion(Notification.Builder(context, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title).setContentText(UiText.AutoSignIn.VIEW_RESULT_HINT).build())
        return builder.build()
    }
}

internal object AutoSignInReportText {
    fun summary(report: SignInReport): String =
        if (report.taskFailure != null && report.total == 0) UiText.AutoSignIn.TASK_FAILED_TITLE
        else UiText.AutoSignIn.summary(report.signed, report.alreadySigned, report.failures.size,
            (report.total - report.signed - report.alreadySigned - report.failures.size).coerceAtLeast(0))

    fun detail(report: SignInReport, limit: Int = Int.MAX_VALUE): String = buildString {
        append(UiText.AutoSignIn.finishedAt(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(report.finishedAt))))
        append('\n')
        append(summary(report))
        report.taskFailure?.let {
            append("\n\n")
            append(UiText.AutoSignIn.taskFailure(code(it), message(it)))
        }
        report.failures.take(limit).forEach { entry ->
            append("\n\n")
            append(UiText.AutoSignIn.forumFailure(
                AutoSignInResponses.safeMessage(entry.forum.name).take(60), entry.forum.id,
                code(entry.failure), message(entry.failure)))
        }
        if (report.failures.size > limit) {
            append("\n\n")
            append(UiText.AutoSignIn.moreFailures(report.failures.size - limit))
        }
    }

    private fun code(failure: SignInFailure): String = failure.code?.take(80) ?: UiText.AutoSignIn.NO_ERROR_CODE

    private fun message(failure: SignInFailure): String {
        if (failure.message.isNotBlank()) return AutoSignInResponses.safeMessage(failure.message)
        return when (failure.kind) {
            SignInFailureKind.API -> UiText.AutoSignIn.FAILURE_API
            SignInFailureKind.NO_RESPONSE -> UiText.AutoSignIn.FAILURE_NO_RESPONSE
            SignInFailureKind.INVALID_RESPONSE -> UiText.AutoSignIn.FAILURE_INVALID_RESPONSE
            SignInFailureKind.REQUEST_FAILED -> UiText.AutoSignIn.FAILURE_REQUEST
            SignInFailureKind.TIMEOUT -> UiText.AutoSignIn.FAILURE_TIMEOUT
            SignInFailureKind.UNCONFIRMED -> UiText.AutoSignIn.FAILURE_UNCONFIRMED
            SignInFailureKind.INVALID_FORUM -> UiText.AutoSignIn.FAILURE_INVALID_FORUM
            SignInFailureKind.INTERRUPTED -> UiText.AutoSignIn.FAILURE_INTERRUPTED
            SignInFailureKind.SERVER_NOTICE -> UiText.AutoSignIn.FAILURE_SERVER_NOTICE
        }
    }
}
