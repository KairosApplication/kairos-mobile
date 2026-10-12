package com.example.kairos.view.settings

import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.kairos.R
import com.example.kairos.model.auth.AccountEditRequest
import com.example.kairos.model.auth.AccountValidation
import com.example.kairos.model.auth.AuthValidation
import com.example.kairos.model.auth.SignedInUser

object AccountEditorDialog {
    fun show(activity: AppCompatActivity, scale: Float, user: SignedInUser, field: AccountEditField,
             onSubmit: (AccountEditRequest) -> Unit): AlertDialog {
        val s = AccountUiStyle(activity, scale)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.px(24f), s.px(12f), s.px(24f), s.px(12f))
        }
        val inputs = mutableMapOf<Int, EditText>()
        fun input(id: Int, label: Int, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT) {
            panel.addView(s.label(s.text(label), 15f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(12f) })
            val edit = EditText(activity).apply {
                this.id = id
                inputType = type
                isSingleLine = true
                isSaveEnabled = false
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                typeface = s.font
                setText(value)
                contentDescription = s.text(label)
            }
            inputs[id] = edit
            panel.addView(edit, LinearLayout.LayoutParams(-1, -2))
        }
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        when (field) {
            AccountEditField.NAME -> {
                input(R.id.account_form_name, R.string.account_first_name, user.profile?.name.orEmpty())
                input(R.id.account_form_last_name, R.string.account_last_name, user.profile?.lastName.orEmpty())
            }
            AccountEditField.EMAIL -> {
                panel.addView(s.label(s.text(R.string.account_email_instructions), 14f, s.secondary, 500))
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
        val error = s.label("", 14f, 0xFFB3261E.toInt(), 500).apply {
            id = R.id.account_form_error
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        panel.addView(error, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(8f) })
        val title = when (field) {
            AccountEditField.NAME -> R.string.account_edit_name
            AccountEditField.EMAIL -> R.string.account_edit_email
            AccountEditField.PASSWORD -> R.string.account_edit_password
        }
        val dialog = AlertDialog.Builder(activity).setTitle(title).setView(panel)
            .setPositiveButton(if (field == AccountEditField.EMAIL) R.string.account_verify_email else R.string.account_save, null)
            .setNegativeButton(R.string.account_cancel, null).create()
        dialog.setOnDismissListener { inputs.values.forEach { it.text.clear() } }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                    onSubmit(request)
                    dialog.dismiss()
                } catch (e: IllegalArgumentException) {
                    error.text = e.message.orEmpty()
                }
            }
        }
        dialog.show()
        return dialog
    }
}
