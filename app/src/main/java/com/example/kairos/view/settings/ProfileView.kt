package com.example.kairos.view.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.text.TextUtils
import com.example.kairos.R
import com.example.kairos.model.auth.AccountValidation
import com.example.kairos.model.auth.SignedInUser
import com.example.kairos.model.home.ProfileWorkSummary

class ProfileView(
    context: Context, scale: Float, user: SignedInUser?, summary: ProfileWorkSummary?,
    isDemo: Boolean, busy: Boolean, busyLabel: Int, message: String, onEdit: () -> Unit, onLogout: () -> Unit
) : LinearLayout(context) {
    init {
        val s = AccountUiStyle(context, scale)
        orientation = VERTICAL
        id = R.id.account_profile_summary
        val name = AccountValidation.fullName(user).ifBlank { s.text(R.string.account_not_informed) }
        val hero = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0A6A4F.toInt(), 0xFF1B4D3E.toInt())).apply {
                val radius = s.px(24f).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            }
        }
        val avatar = FrameLayout(context).apply {
            background = s.rounded(0xFFD9D9D9.toInt(), 100f)
            addView(s.icon(R.drawable.settings_profile), FrameLayout.LayoutParams(s.px(100f), s.px(100f), Gravity.CENTER))
            val edit = FrameLayout(context).apply {
                id = R.id.account_profile_edit
                isFocusable = true
                isEnabled = !busy
                contentDescription = s.text(R.string.account_edit_profile)
                addView(View(context).apply { background = s.rounded(0xFF939C96.toInt(), 50f) },
                    FrameLayout.LayoutParams(s.px(35f), s.px(35f), Gravity.CENTER))
                addView(s.icon(R.drawable.account_pencil).apply { imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE) },
                    FrameLayout.LayoutParams(s.px(18f), s.px(18f), Gravity.CENTER))
                setOnClickListener { if (!busy) onEdit() }
            }
            addView(edit, FrameLayout.LayoutParams(s.px(48f), s.px(48f), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = -s.px(6f); marginEnd = -s.px(6f)
            })
            clipChildren = false
        }
        hero.addView(avatar, FrameLayout.LayoutParams(s.px(136f), s.px(136f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = s.px(3f)
        })
        hero.addView(s.label(name, 18f, Color.WHITE).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, FrameLayout.LayoutParams(-1, -2).apply { setMargins(s.px(25f), s.px(154f), s.px(25f), 0) })
        hero.addView(s.label(user?.email.orEmpty(), 16f, Color.WHITE, 400).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, FrameLayout.LayoutParams(-1, -2).apply { setMargins(s.px(25f), s.px(177f), s.px(25f), 0) })
        addView(hero, LayoutParams(-1, s.px(230f)))

        fun countCard(title: Int, value: Int?) = s.card().apply {
            minimumHeight = s.px(104f)
            setPadding(s.px(26f), s.px(16f), s.px(16f), s.px(14f))
            addView(s.label(s.text(title), 17f, s.secondary, 500))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(s.label(value?.toString() ?: s.text(R.string.account_unavailable_count), 40f, context.getColor(R.color.home_green)))
                addView(s.label(s.text(R.string.home_week), 15f, s.secondary, 500),
                    LayoutParams(-2, -2).apply { marginStart = s.px(14f) })
            })
        }
        addView(LinearLayout(context).apply {
            addView(countCard(R.string.account_restocks, summary?.restocksThisWeek), LayoutParams(0, -2, 1f).apply { marginEnd = s.px(16f) })
            addView(countCard(R.string.account_damages, summary?.damagesThisWeek), LayoutParams(0, -2, 1f))
        }, LayoutParams(-1, -2).apply { setMargins(s.px(17f), s.px(16f), s.px(17f), 0) })
        val details = s.card().apply {
            minimumHeight = s.px(264f)
            setPadding(s.px(37f), s.px(22f), s.px(24f), s.px(30f))
        }
        fun field(label: Int, value: String, top: Float) {
            details.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(s.label(s.text(label), 17f))
                addView(s.label(value, 16f, 0xFF777777.toInt(), 400), LayoutParams(-1, -2).apply { topMargin = s.px(4f) })
            }, LayoutParams(-1, -2).apply { topMargin = s.px(top) })
        }
        field(R.string.account_name_label, name, 0f)
        field(R.string.account_role, summary?.role ?: s.text(R.string.account_not_informed), 38f)
        field(R.string.account_market, summary?.market ?: s.text(R.string.account_not_informed), 38f)
        addView(details, LayoutParams(-1, -2).apply { setMargins(s.px(17f), s.px(18f), s.px(18f), 0) })
        val logout = LinearLayout(context).apply {
            id = R.id.home_logout
            gravity = Gravity.CENTER
            minimumHeight = s.px(60f)
            isEnabled = !busy
            isFocusable = true
            val red = 0xFFFF2424.toInt()
            background = s.rounded(0x11FF2424, 20f, red)
            addView(s.icon(R.drawable.account_logout),
                LayoutParams(s.px(30f), s.px(30f)))
            addView(s.label(s.text(if (busy) busyLabel else R.string.home_logout), 20f, red, 500),
                LayoutParams(-2, -2).apply { marginStart = s.px(10f) })
            setOnClickListener { if (!busy) onLogout() }
        }
        addView(logout, LayoutParams(-1, -2).apply { setMargins(s.px(21f), s.px(27f), s.px(14f), 0) })
        if (isDemo) addView(s.label(s.text(R.string.home_demo), 12f, s.secondary, 500).apply { gravity = Gravity.CENTER },
            LayoutParams(-1, -2).apply { topMargin = s.px(12f) })
        if (message.isNotBlank()) addView(s.label(message, 15f, s.secondary, 500).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }, LayoutParams(-1, -2).apply { setMargins(s.px(21f), s.px(16f), s.px(21f), 0) })
    }
}
