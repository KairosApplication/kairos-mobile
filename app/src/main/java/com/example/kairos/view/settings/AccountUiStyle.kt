package com.example.kairos.view.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R

internal class AccountUiStyle(val context: Context, val scale: Float) {
    private val density = context.resources.displayMetrics.density
    val font = requireNotNull(ResourcesCompat.getFont(context, R.font.montserrat_semibold))
    val ink = context.getColor(R.color.home_ink)
    val secondary = context.getColor(R.color.home_secondary)
    fun px(value: Float) = (value * density * scale).toInt()
    fun text(id: Int) = context.getString(id)
    fun label(value: String, size: Float, color: Int = ink, weight: Int = 600) = TextView(context).apply {
        text = value
        textSize = size * scale
        typeface = Typeface.create(font, weight, false)
        fontVariationSettings = "'wght' $weight"
        includeFontPadding = false
        setTextColor(color)
    }
    fun rounded(color: Int, radius: Float, border: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = px(radius).toFloat()
        if (border != null) setStroke(px(1f).coerceAtLeast(1), border)
    }
    fun card(action: (() -> Unit)? = null) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val shape = rounded(Color.WHITE, 20f, context.getColor(R.color.home_border))
        background = if (action == null) shape else RippleDrawable(ColorStateList.valueOf(0x14266D57), shape, null)
        elevation = px(5f).toFloat()
        outlineAmbientShadowColor = 0x1A05291A
        outlineSpotShadowColor = 0x1A05291A
        if (action != null) {
            isFocusable = true
            setOnClickListener { action() }
        }
    }
    fun icon(resource: Int) = ImageView(context).apply {
        setImageResource(resource)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
