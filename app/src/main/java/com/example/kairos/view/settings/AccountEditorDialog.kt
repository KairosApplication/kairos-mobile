package com.example.kairos.view.settings

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import com.example.kairos.R
import com.example.kairos.model.auth.*
import com.example.kairos.view.InfoCardDialog

object AccountEditorDialog {
    class Editor internal constructor(
        val dialog: AlertDialog,
        private val owner: String,
        private val inputs: Map<Int, EditText>,
        private val error: TextView,
        private val progress: ProgressBar,
        private val save: TextView,
        private val cancel: View
    ) {
        private val idleLabel = save.text
        private var pending: AccountOperation? = null
        private var observedLoading = false

        internal fun start(operation: AccountOperation) {
            pending = operation
            observedLoading = false
            error.visibility = View.GONE
            setBusy(true)
        }

        private fun setBusy(busy: Boolean) {
            inputs.values.forEach { it.isEnabled = !busy }
            save.isEnabled = !busy
            save.alpha = if (busy) .6f else 1f
            cancel.isEnabled = !busy
            save.text = if (busy) save.context.getString(R.string.account_saving) else idleLabel
            progress.visibility = if (busy) View.VISIBLE else View.GONE
            dialog.setCancelable(!busy)
            dialog.setCanceledOnTouchOutside(!busy)
        }

        internal fun fail(message: String, clearCurrentPassword: Boolean = true) {
            pending = null
            observedLoading = false
            setBusy(false)
            if (clearCurrentPassword) inputs[R.id.account_form_current_password]?.text?.clear()
            error.text = message
            error.visibility = View.VISIBLE
            error.post { error.requestRectangleOnScreen(android.graphics.Rect(0, 0, error.width, error.height)) }
        }

        fun bind(user: SignedInUser?, busy: Boolean, message: String,
                 operation: AccountOperation?, succeeded: Boolean?) {
            if (!dialog.isShowing) return
            if (user?.uid != owner) { dialog.dismiss(); return }
            if (pending == null) return
            if (busy && operation == pending) observedLoading = true
            if (!busy && observedLoading && succeeded != null) {
                if (succeeded) dialog.dismiss()
                else fail(message.ifBlank { save.context.getString(R.string.account_update_failed) })
            }
        }
    }

    fun show(activity: AppCompatActivity, scale: Float, user: SignedInUser, field: AccountEditField,
             onSubmit: (AccountEditRequest) -> Unit): Editor {
        val s = AccountUiStyle(activity, scale)
        val panel = InfoCardDialog.panel(activity, scale)
        val title = when (field) {
            AccountEditField.NAME -> R.string.account_edit_name
            AccountEditField.EMAIL -> R.string.account_edit_email
            AccountEditField.PASSWORD -> R.string.account_edit_password
        }
        panel.addView(s.label(s.text(title), 22f).apply { ViewCompat.setAccessibilityHeading(this, true) })
        val description = when (field) {
            AccountEditField.NAME -> R.string.account_name_instructions
            AccountEditField.EMAIL -> R.string.account_email_instructions
            AccountEditField.PASSWORD -> R.string.account_password_instructions
        }
        panel.addView(s.label(s.text(description), 14f, s.secondary, 500),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(8f) })
        val inputs = mutableMapOf<Int, EditText>()
        fun input(id: Int, label: Int, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT) {
            panel.addView(s.label(s.text(label), 14f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(18f) })
            val edit = AppCompatEditText(activity).apply {
                this.id = id
                inputType = type
                isSingleLine = true
                isSaveEnabled = false
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                typeface = Typeface.create(s.font, 500, false)
                fontVariationSettings = "'wght' 500"
                textSize = 16f * scale
                setTextColor(s.ink)
                setHintTextColor(s.secondary)
                setText(value)
                hint = s.text(label)
                contentDescription = s.text(label)
                includeFontPadding = false
                ViewCompat.setBackgroundTintList(this, null)
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),
                        s.rounded(activity.getColor(R.color.home_subtle_tile), 12f, activity.getColor(R.color.home_icon_accent)))
                    addState(intArrayOf(), s.rounded(activity.getColor(R.color.home_subtle_tile), 12f, activity.getColor(R.color.home_border)))
                }
                setPadding(s.px(14f), s.px(14f), s.px(14f), s.px(14f))
                minimumHeight = (52 * activity.resources.displayMetrics.density).toInt()
                filters = arrayOf(InputFilter.LengthFilter(if (field == AccountEditField.NAME) 100 else 255))
            }
            inputs[id] = edit
            panel.addView(edit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(7f) })
        }
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        when (field) {
            AccountEditField.NAME -> {
                input(R.id.account_form_name, R.string.account_first_name, user.profile?.name.orEmpty())
                input(R.id.account_form_last_name, R.string.account_last_name, user.profile?.lastName.orEmpty())
            }
            AccountEditField.EMAIL -> {
                input(R.id.account_form_email, R.string.account_new_email, user.email,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                input(R.id.account_form_current_password, R.string.account_current_password, type = password)
            }
            AccountEditField.PASSWORD -> {
                input(R.id.account_form_current_password, R.string.account_current_password, type = password)
                input(R.id.account_form_new_password, R.string.account_new_password, type = password)
                input(R.id.account_form_confirmation, R.string.account_confirm_password, type = password)
            }
        }
        val error = s.label("", 14f, activity.getColor(R.color.account_error), 600).apply {
            id = R.id.account_form_error
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        panel.addView(error, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(16f) })
        val progress = ProgressBar(activity).apply {
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(activity.getColor(R.color.home_icon_accent))
            contentDescription = s.text(R.string.account_saving)
        }
        panel.addView(progress, LinearLayout.LayoutParams(s.px(28f), s.px(28f)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = s.px(12f)
        })
        val save = InfoCardDialog.button(activity, scale,
            s.text(if (field == AccountEditField.EMAIL) R.string.account_verify_email else R.string.account_save), true).apply {
            id = R.id.account_form_save
        }
        val cancel = InfoCardDialog.button(activity, scale, s.text(R.string.account_cancel), false)
        panel.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(24f) })
        panel.addView(cancel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(6f) })
        val dialog = InfoCardDialog.present(activity, scale, panel)
        val editor = Editor(dialog, user.uid, inputs, error, progress, save, cancel)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { inputs.values.forEach { it.text.clear() } }
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            fun value(id: Int) = inputs.getValue(id).text.toString()
            try {
                val request = when (field) {
                    AccountEditField.NAME -> {
                        val first = value(R.id.account_form_name).trim()
                        val last = value(R.id.account_form_last_name).trim()
                        AccountValidation.name(first, last)
                        AccountEditRequest.Name(user.uid, first, last)
                    }
                    AccountEditField.EMAIL -> {
                        val email = value(R.id.account_form_email).trim()
                        AuthValidation.email(email)
                        require(!email.equals(user.email, true)) { "Informe um e-mail diferente do atual." }
                        val current = value(R.id.account_form_current_password)
                        AuthValidation.password(current)
                        AccountEditRequest.Email(user.uid, email, current)
                    }
                    AccountEditField.PASSWORD -> {
                        val current = value(R.id.account_form_current_password)
                        val updated = value(R.id.account_form_new_password)
                        AccountValidation.password(current, updated, value(R.id.account_form_confirmation))
                        AccountEditRequest.Password(user.uid, current, updated)
                    }
                }
                editor.start(request.operation)
                onSubmit(request)
            } catch (e: IllegalArgumentException) {
                editor.fail(e.message.orEmpty(), clearCurrentPassword = false)
            }
        }
        return editor
    }
}
