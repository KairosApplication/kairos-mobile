package com.example.kairos.view.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.example.kairos.R
import com.example.kairos.model.auth.AccountValidation
import com.example.kairos.model.auth.SignedInUser
import java.time.format.DateTimeFormatter

enum class AccountEditField { NAME, EMAIL, PASSWORD }

class SecurityView(context: Context, scale: Float, user: SignedInUser?, busy: Boolean, message: String,
                   onEdit: (AccountEditField) -> Unit) : LinearLayout(context) {
    init {
        val s = AccountUiStyle(context, scale)
        orientation = VERTICAL
        id = R.id.account_security_data
        fun heading(title: Int, top: Float) {
            addView(s.label(s.text(title), 22f, 0xCC0F2B21.toInt()).apply {
                ViewCompat.setAccessibilityHeading(this, true)
            }, LayoutParams(-1, -2).apply { setMargins(s.px(31f), s.px(top), s.px(21f), 0) })
        }
        fun row(title: Int, value: String, icon: Int, iconSize: Float, top: Float,
                field: AccountEditField? = null, actionId: Int = View.NO_ID) {
            val action = field?.let { { if (!busy) onEdit(it) } }
            val row = s.card(action).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = s.px(81f)
                setPadding(s.px(21f), s.px(16f), s.px(18f), s.px(16f))
                contentDescription = "${s.text(title)}. $value"
                isEnabled = !busy || field == null
            }
            row.addView(FrameLayout(context).apply {
                background = s.rounded(0x66B3D5C3, 50f)
                addView(s.icon(icon), FrameLayout.LayoutParams(s.px(iconSize), s.px(iconSize), Gravity.CENTER))
            }, LayoutParams(s.px(48f), s.px(48f)))
            row.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(s.label(s.text(title), 17f, 0xCC0F2B21.toInt(), 500))
                addView(s.label(value, 16f), LayoutParams(-1, -2).apply { topMargin = s.px(2f) })
            }, LayoutParams(0, -2, 1f).apply { marginStart = s.px(17f) })
            if (field != null) row.addView(androidx.appcompat.widget.AppCompatImageButton(context).apply {
                id = actionId
                contentDescription = s.text(when (field) {
                    AccountEditField.NAME -> R.string.account_edit_name
                    AccountEditField.EMAIL -> R.string.account_edit_email
                    AccountEditField.PASSWORD -> R.string.account_edit_password
                })
                setImageResource(R.drawable.account_pencil)
                background = null
                setPadding(s.px(10f), s.px(10f), s.px(10f), s.px(10f))
                isEnabled = !busy
                setOnClickListener { if (!busy) onEdit(field) }
            }, LayoutParams(s.px(48f), s.px(48f)))
            addView(row, LayoutParams(-1, -2).apply { setMargins(s.px(21f), s.px(top), s.px(19f), 0) })
        }
        heading(R.string.account_data, 21f)
        row(R.string.account_full_name, AccountValidation.fullName(user).ifBlank { s.text(R.string.account_not_informed) },
            R.drawable.settings_profile, 40f, 13f, AccountEditField.NAME, R.id.account_edit_name)
        row(R.string.account_cpf, AccountValidation.maskCpf(user?.profile?.cpf) ?: s.text(R.string.account_not_informed),
            R.drawable.account_id, 30f, 11f)
        row(R.string.account_birth_date, user?.profile?.birthDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            ?: s.text(R.string.account_not_informed), R.drawable.account_calendar, 26f, 12f)
        heading(R.string.account_access, 18f)
        row(R.string.account_email, user?.email.orEmpty(), R.drawable.account_mail, 26f, 13f,
            AccountEditField.EMAIL, R.id.account_edit_email)
        row(R.string.account_password, s.text(R.string.account_password_mask), R.drawable.account_lock, 26f, 11f,
            AccountEditField.PASSWORD, R.id.account_edit_password)
        if (message.isNotBlank() || busy) addView(s.label(if (busy) s.text(R.string.account_saving) else message,
            15f, s.secondary, 500).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE },
            LayoutParams(-1, -2).apply { setMargins(s.px(21f), s.px(16f), s.px(21f), 0) })
    }
}
