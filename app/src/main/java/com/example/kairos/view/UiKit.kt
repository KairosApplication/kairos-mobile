package com.example.kairos.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.example.kairos.R

internal class UiKit(val context: Context) {
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun color(id: Int) = context.getColor(id)
    fun shape(fill: Int = R.color.home_surface, radius: Int = 24, border: Boolean = false) = GradientDrawable().apply {
        setColor(color(fill)); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), color(R.color.home_border))
    }
    fun text(value: String, size: Float = 15f, strong: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size
        typeface = ResourcesCompat.getFont(context, if (strong) R.font.montserrat_semibold else R.font.montserrat_regular)
        setTextColor(color(if (muted) R.color.home_secondary else R.color.home_ink))
        includeFontPadding = false
        setLineSpacing(dp(4).toFloat(), 1f)
    }
    fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 14f
        typeface = ResourcesCompat.getFont(context, R.font.montserrat_semibold)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(0x228CD9B8),
            shape(if (primary) R.color.home_button else R.color.home_surface, 16, !primary), null)
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(color(R.color.home_secondary), color(if (primary) R.color.home_button_text else R.color.home_ink))))
        stateListAnimator = null; elevation = 0f; translationZ = 0f
        includeFontPadding = false
        minimumWidth = 0; minWidth = 0
        setPadding(dp(16), dp(12), dp(16), dp(12)); minHeight = dp(52)
        setOnClickListener { action() }
    }
    fun notice(icon: Int, title: String, description: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
        background = shape(border = true); setPadding(dp(16),dp(18),dp(16),dp(18))
        addView(ImageView(context).apply {
            setImageResource(icon); imageTintList=ColorStateList.valueOf(color(R.color.home_icon_accent))
            importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(22),dp(22)).apply { marginEnd=dp(12) })
        addView(LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            addView(text(title,14f,true))
            addView(text(description,13f,muted=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        },LinearLayout.LayoutParams(0,-2,1f))
    }
    fun spinnerAdapter(values:List<String>) = object:ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,values) {
        override fun getView(position:Int,convertView:android.view.View?,parent:android.view.ViewGroup):android.view.View =
            LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(14),dp(12),dp(12),dp(12))
                minimumHeight=dp(52)
                addView(text(values[position],15f),LinearLayout.LayoutParams(0,-2,1f))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.settings_chevron); rotation=90f
                    imageTintList=ColorStateList.valueOf(color(R.color.home_secondary))
                    importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },LinearLayout.LayoutParams(dp(20),dp(20)))
            }
        override fun getDropDownView(position:Int,convertView:android.view.View?,parent:android.view.ViewGroup):android.view.View =
            text(values[position],15f).apply {
                setPadding(dp(16),dp(14),dp(16),dp(14)); minimumHeight=dp(48)
                setBackgroundColor(color(R.color.home_surface))
            }
    }
    fun spinner(values:List<String>) = Spinner(context,Spinner.MODE_DROPDOWN).apply {
        adapter=spinnerAdapter(values)
        background=shape(R.color.home_surface,14,true)
        setPopupBackgroundDrawable(shape(R.color.home_surface,14,true))
        minimumHeight=dp(52)
    }
    fun empty(icon: Int, title: String, description: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(24), dp(28), dp(24), dp(28)); background = shape(border = true)
        addView(FrameLayout(context).apply {
            background = shape(R.color.home_subtle_tile, 24)
            addView(ImageView(context).apply {
                setImageResource(icon); imageTintList = ColorStateList.valueOf(color(R.color.home_icon_accent))
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        addView(text(title, 18f, strong = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        addView(text(description, 14f, muted = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}
