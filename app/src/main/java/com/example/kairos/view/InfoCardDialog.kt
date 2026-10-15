package com.example.kairos.view

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import com.example.kairos.R
import com.example.kairos.view.settings.AccountUiStyle

/** Shared, flat dialog surface and actions for guidance, alerts and filters. */
internal object InfoCardDialog {
    fun panel(activity: AppCompatActivity, scale: Float) = LinearLayout(activity).apply {
        val s = AccountUiStyle(activity, scale)
        orientation = LinearLayout.VERTICAL
        setPadding(s.px(24f), s.px(24f), s.px(24f), s.px(20f))
        background = s.rounded(s.surface, 24f, activity.getColor(R.color.home_border))
        elevation = 0f
    }

    fun button(activity: AppCompatActivity, scale: Float, text: String, filled: Boolean) =
        AppCompatButton(activity).apply {
            val s = AccountUiStyle(activity, scale)
            this.text = text
            isAllCaps = false
            typeface = s.font
            textSize = 15f * scale
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = (48 * activity.resources.displayMetrics.density).toInt()
            minWidth = 0
            minimumWidth = 0
            setPadding(s.px(12f), s.px(14f), s.px(12f), s.px(14f))
            // Native button animators cast a raised shadow even with a custom background.
            stateListAnimator = null
            elevation = 0f
            translationZ = 0f
            outlineProvider = null
            backgroundTintList = null
            ViewCompat.setBackgroundTintList(this, null)
            val shape = s.rounded(if (filled) activity.getColor(R.color.home_button) else Color.TRANSPARENT, 14f)
            background = RippleDrawable(ColorStateList.valueOf(0x228CD9B8), shape, s.rounded(Color.WHITE, 14f))
            setTextColor(activity.getColor(if (filled) R.color.home_button_text else R.color.home_icon_accent))
        }

    fun present(activity: AppCompatActivity, scale: Float, content: LinearLayout): AlertDialog {
        val s = AccountUiStyle(activity, scale)
        val dialog = AlertDialog.Builder(activity).setView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }).create()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.elevation = 0f
            decorView.translationZ = 0f
            setDimAmount(.5f)
            val availableWidth = activity.resources.displayMetrics.widthPixels - s.px(32f)
            setLayout(minOf(availableWidth, s.px(392f)), -2)
        }
        return dialog
    }

    fun show(activity: AppCompatActivity, scale: Float, title: String, message: String,
             subtitle: String? = null,
             primary: String = activity.getString(R.string.dialog_close),
             action: (() -> Unit)? = null, actionEnabled: Boolean = true,
             demo: Boolean = false): AlertDialog {
        val s = AccountUiStyle(activity, scale)
        val content = panel(activity, scale)
        content.addView(s.label(title, 22f).apply { ViewCompat.setAccessibilityHeading(this, true) },
            LinearLayout.LayoutParams(-1, -2))
        if (!subtitle.isNullOrBlank()) content.addView(s.label(subtitle, 14f, s.secondary, 500),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(8f) })
        content.addView(s.label(message, 15f, s.secondary, 400).apply { setLineSpacing(s.px(4f).toFloat(), 1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(16f) })
        if (demo) content.addView(s.label(s.text(R.string.home_demo), 13f, s.secondary, 500),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(16f) })
        lateinit var dialog: AlertDialog
        content.addView(button(activity, scale, primary, true).apply {
            id = R.id.info_primary
            isEnabled = actionEnabled
            if (!actionEnabled) {
                background = s.rounded(activity.getColor(R.color.home_button_disabled), 14f)
                setTextColor(s.secondary)
            }
            setOnClickListener { dialog.dismiss(); action?.invoke() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(24f) })
        if (action != null) content.addView(button(activity, scale, s.text(R.string.dialog_back), false).apply {
            id = R.id.info_secondary
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.px(8f) })
        dialog = present(activity, scale, content)
        return dialog
    }
}
