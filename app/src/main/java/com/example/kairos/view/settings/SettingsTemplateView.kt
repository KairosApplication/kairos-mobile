package com.example.kairos.view.settings

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R

/** Shared temporary content; destination-specific screens will replace this view. */
class SettingsTemplateView(context: Context, scale: Float) : LinearLayout(context) {
    init {
        id = R.id.settings_template
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val density = resources.displayMetrics.density
        fun px(value: Float) = (value * density * scale).toInt()
        setPadding(px(24f), px(32f), px(24f), px(32f))
        val font = requireNotNull(ResourcesCompat.getFont(context, R.font.montserrat_semibold))
        addView(TextView(context).apply {
            text = context.getString(R.string.settings_coming_soon)
            textSize = 22f * scale
            typeface = Typeface.create(font, 600, false)
            setTextColor(context.getColor(R.color.home_ink))
            includeFontPadding = false
            gravity = Gravity.CENTER
        }, LayoutParams(-1, -2))
        addView(TextView(context).apply {
            text = context.getString(R.string.settings_template_description)
            textSize = 15f * scale
            typeface = Typeface.create(font, 500, false)
            fontVariationSettings = "'wght' 500"
            setTextColor(context.getColor(R.color.home_secondary))
            includeFontPadding = false
            gravity = Gravity.CENTER
        }, LayoutParams(-1, -2).apply { topMargin = px(12f) })
    }
}
