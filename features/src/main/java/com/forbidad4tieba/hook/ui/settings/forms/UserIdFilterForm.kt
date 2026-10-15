package com.forbidad4tieba.hook.ui.settings.forms

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.forbidad4tieba.hook.config.PostUserFilterSettings
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.feature.ad.PostUserFilterController
import com.forbidad4tieba.hook.feature.ad.PostUserFilterController.Status
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_DESC_SP
import com.forbidad4tieba.hook.ui.SETTINGS_ROW_TITLE_SP
import com.forbidad4tieba.hook.ui.SETTINGS_SECTION_TITLE_SP
import com.forbidad4tieba.hook.ui.UiStyle
import com.forbidad4tieba.hook.ui.UiText
import com.forbidad4tieba.hook.ui.createDialogScrollContainer
import com.forbidad4tieba.hook.ui.dialogThemeFor
import com.forbidad4tieba.hook.ui.setSettingsTitle
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.doAfterTextChanged
import com.forbidad4tieba.hook.ui.settings.SettingsInputViews.styleMultilineInput
import com.forbidad4tieba.hook.ui.settingsDialogContentTopPadding
import com.forbidad4tieba.hook.ui.settingsDialogPadding
import com.forbidad4tieba.hook.ui.showSettingsDialog

internal object UserIdFilterForm {
    fun showCustomPostFilterUserIdDialog(context: Context, prefs: SharedPreferences) {
        var controller: PostUserFilterController? = null
        try {
            val owner = PostUserFilterController(context, prefs).also { controller = it }
            val tokens = UiStyle.tokens(context)
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            fun label(textValue: String, size: Float, color: Int) = TextView(context).apply {
                text = textValue
                textSize = size
                setTextColor(color)
                includeFontPadding = false
            }
            val input = EditText(context).apply {
                styleMultilineInput(tokens, density)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_DONE
                hint = UiText.Settings.CUSTOM_POST_FILTER_USER_ID_INPUT_LABEL
                contentDescription = UiText.Settings.CUSTOM_POST_FILTER_USER_ID_INPUT_LABEL
            }
            val addButton = TextView(context).apply {
                text = UiText.Settings.CUSTOM_POST_FILTER_USER_ADD
                textSize = SETTINGS_ROW_TITLE_SP
                setTextColor(tokens.accent)
                includeFontPadding = false
                gravity = Gravity.CENTER
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                setPadding(dp(8), 0, 0, 0)
                isFocusable = true
                setOnClickListener { owner.add(input.text.toString()) }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    owner.add(input.text.toString())
                    true
                } else false
            }
            input.doAfterTextChanged { raw ->
                input.error = null
                owner.inputChanged()
                val filtered = PostUserFilterSettings.filterProfileIdInput(raw)
                if (filtered != raw) {
                    input.setText(filtered)
                    input.setSelection(filtered.length)
                }
            }
            val inputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                    leftMargin = dp(8)
                })
            }
            val statusView = label("", SETTINGS_ROW_DESC_SP, tokens.textSecondary).apply {
                setPadding(0, dp(8), 0, 0)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            val listTitle = label("", SETTINGS_SECTION_TITLE_SP, tokens.textPrimary).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(20), 0, dp(10))
            }
            val userList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = settingsDialogPadding(density)
                setPadding(padding, settingsDialogContentTopPadding(padding), padding, 0)
                addView(label(UiText.Settings.CUSTOM_POST_FILTER_USER_ID_GUIDE,
                    SETTINGS_ROW_TITLE_SP, tokens.textPrimary).apply {
                    setLineSpacing(3f * density, 1f)
                    setPadding(0, 0, 0, dp(16))
                })
                addView(inputRow)
                addView(statusView)
                addView(listTitle)
                addView(userList)
            }
            val dialog = AlertDialog.Builder(context, dialogThemeFor(context))
                .setSettingsTitle(context, UiText.Settings.CUSTOM_POST_FILTER_USER_ID_LABEL)
                .setView(createDialogScrollContainer(context, root))
                .setPositiveButton(UiText.Settings.DONE, null)
                .create()
            dialog.setOnDismissListener { owner.close() }
            owner.observe { state ->
                addButton.isEnabled = state.canAdd
                addButton.alpha = if (state.canAdd) 1f else 0.4f
                addButton.text = if (state.status == Status.QUERYING)
                    UiText.Settings.CUSTOM_POST_FILTER_USER_QUERYING else UiText.Settings.CUSTOM_POST_FILTER_USER_ADD
                statusView.text = statusText(state.status)
                statusView.visibility = if (state.status == Status.IDLE) View.GONE else View.VISIBLE
                statusView.setTextColor(when (state.status) {
                    Status.ADDED, Status.REMOVED -> tokens.success
                    Status.IDLE, Status.QUERYING, Status.CANCELLED -> tokens.textSecondary
                    else -> tokens.danger
                })
                if (state.status == Status.INVALID_ID) input.error = UiText.Settings.CUSTOM_POST_FILTER_USER_INVALID_ID
                if (state.status == Status.ADDED) input.setText("")
                listTitle.text = UiText.Settings.customPostUserCount(state.users.size)
                userList.removeAllViews()
                if (state.users.isEmpty()) {
                    userList.addView(label(UiText.Settings.CUSTOM_POST_FILTER_USER_EMPTY,
                        SETTINGS_ROW_DESC_SP, tokens.textMuted).apply { setPadding(0, dp(10), 0, dp(10)) })
                }
                for (user in state.users) {
                    val name = user.nickname
                    val details = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(56)
                        setPadding(dp(12), dp(10), dp(8), dp(10))
                        isFocusable = true
                        contentDescription = UiText.Settings.customPostUserOpen(name)
                        setOnClickListener { owner.openProfile(context, user) }
                        addView(label(name, SETTINGS_ROW_TITLE_SP, tokens.accent).apply {
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        })
                        addView(label(UiText.Settings.customPostUserId(user.profileId),
                            SETTINGS_ROW_DESC_SP, tokens.textSecondary).apply { setPadding(0, dp(6), 0, 0) })
                    }
                    val remove = Button(context).apply {
                        UiStyle.paintScanActionButton(this, density, tokens.danger)
                        text = UiText.Settings.CUSTOM_POST_FILTER_USER_REMOVE
                        contentDescription = UiText.Settings.customPostUserRemove(name)
                        minimumHeight = dp(48)
                        setOnClickListener { owner.remove(user) }
                    }
                    userList.addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(tokens.surfaceAlt)
                        }
                        addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(remove, LinearLayout.LayoutParams(dp(66), ViewGroup.LayoutParams.WRAP_CONTENT))
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
                }
            }
            dialog.showSettingsDialog(density)
        } catch (t: Throwable) {
            controller?.close()
            XposedCompat.logW("[SettingsMenuHook] showCustomPostFilterUserIdDialog failed: " + t.message)
        }
    }

    private fun statusText(status: Status): String = when (status) {
        Status.IDLE -> ""
        Status.QUERYING -> UiText.Settings.CUSTOM_POST_FILTER_USER_QUERYING
        Status.ADDED -> UiText.Settings.CUSTOM_POST_FILTER_USER_ADDED
        Status.REMOVED -> UiText.Settings.CUSTOM_POST_FILTER_USER_REMOVED
        Status.INVALID_ID -> UiText.Settings.CUSTOM_POST_FILTER_USER_INVALID_ID
        Status.DUPLICATE -> UiText.Settings.CUSTOM_POST_FILTER_USER_DUPLICATE
        Status.FAILED -> UiText.Settings.CUSTOM_POST_FILTER_USER_FAILED
        Status.INVALID_RESPONSE -> UiText.Settings.CUSTOM_POST_FILTER_USER_INVALID_RESPONSE
        Status.TIMEOUT -> UiText.Settings.CUSTOM_POST_FILTER_USER_TIMEOUT
        Status.CANCELLED -> UiText.Settings.CUSTOM_POST_FILTER_USER_CANCELLED
        Status.UNAVAILABLE -> UiText.Settings.CUSTOM_POST_FILTER_USER_UNAVAILABLE
        Status.SAVE_FAILED -> UiText.Settings.CUSTOM_POST_FILTER_USER_SAVE_FAILED
        Status.PROFILE_FAILED -> UiText.Settings.CUSTOM_POST_FILTER_USER_PROFILE_FAILED
    }
}
