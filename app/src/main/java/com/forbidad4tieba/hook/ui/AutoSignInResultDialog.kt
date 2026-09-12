package com.forbidad4tieba.hook.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.signin.AutoSignInFeedback
import com.forbidad4tieba.hook.feature.signin.AutoSignInReportText
import com.forbidad4tieba.hook.feature.signin.SignInReport

internal object AutoSignInResultDialog {
    fun show(context: Context, report: SignInReport?, onSignIn: () -> Unit) {
        try {
            val text = buildString {
                append(report?.let { AutoSignInReportText.detail(it) } ?: UiText.AutoSignIn.NO_RESULTS)
                if (!AutoSignInFeedback.notificationsAllowed(context)) {
                    append("\n\n")
                    append(UiText.AutoSignIn.NOTIFICATIONS_OFF_HINT)
                }
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.AutoSignIn.RESULT_TITLE)
                .setMessage(text)
                .setNegativeButton(UiText.AutoSignIn.BUTTON_CLOSE, null)
                .setPositiveButton(if (report == null) UiText.AutoSignIn.BUTTON_START else UiText.AutoSignIn.BUTTON_RETRY) { _, _ -> onSignIn() }
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { applyUnifiedDialogCardStyle(it, context.resources.displayMetrics.density) }
                dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
            }
            dialog.show()
        } catch (t: Throwable) {
            XposedCompat.logW("[AutoSignIn] result dialog unavailable: ${t.javaClass.simpleName}")
        }
    }
}
