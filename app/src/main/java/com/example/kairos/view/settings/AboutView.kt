package com.example.kairos.view.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.example.kairos.BuildConfig
import com.example.kairos.R
import com.example.kairos.view.UiKit

/** Product information; version comes from the installed build, without service requests. */
class AboutView(context: Context, scale: Float, onHelp: () -> Unit) : LinearLayout(context) {
    private val s=AccountUiStyle(context,scale)
    private val ui=UiKit(context)

    init {
        id=R.id.settings_about_content
        orientation=VERTICAL
        setPadding(s.px(20f),s.px(24f),s.px(20f),s.px(32f))
        fun space(top: Float=16f)=LayoutParams(-1,-2).apply { topMargin=s.px(top) }
        fun paragraph(text: String)=s.label(text,14f,s.secondary,400).apply {
            setLineSpacing(s.px(5f).toFloat(),1f)
        }
        fun heading(text: String)=s.label(text,19f).apply { ViewCompat.setAccessibilityHeading(this,true) }

        addView(LinearLayout(context).apply {
            orientation=VERTICAL
            setPadding(s.px(24f),s.px(24f),s.px(24f),s.px(28f))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF123E31.toInt(),0xFF22664F.toInt())).apply { cornerRadius=s.px(24f).toFloat() }
            val brand=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
            brand.addView(ImageView(context).apply {
                setImageResource(R.drawable.auth_logo)
                scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription=s.text(R.string.about_brand)
            },LayoutParams(s.px(100f),s.px(78f)))
            brand.addView(FrameLayout(context).apply {
                addView(s.label(context.getString(R.string.about_version,BuildConfig.VERSION_NAME),12f,Color.WHITE,500).apply {
                    background=GradientDrawable().apply { setColor(0x24FFFFFF); cornerRadius=s.px(50f).toFloat() }
                    setPadding(s.px(12f),s.px(8f),s.px(12f),s.px(8f))
                },FrameLayout.LayoutParams(-2,-2,Gravity.END or Gravity.CENTER_VERTICAL))
            },LayoutParams(0,-1,1f))
            addView(brand,LayoutParams(-1,s.px(78f)))
            addView(s.label(s.text(R.string.about_tagline),26f,Color.WHITE).apply {
                setLineSpacing(s.px(3f).toFloat(),1f)
                ViewCompat.setAccessibilityHeading(this,true)
            },space(22f))
            addView(s.label(s.text(R.string.about_intro),14f,0xE6FFFFFF.toInt(),400).apply {
                setLineSpacing(s.px(5f).toFloat(),1f)
            },space(12f))
        },LayoutParams(-1,-2))

        addView(heading(s.text(R.string.about_routine)),space(28f))
        val features=LinearLayout(context).apply {
            orientation=VERTICAL
            background=s.rounded(s.surface,22f,context.getColor(R.color.home_border))
            setPadding(s.px(18f),s.px(6f),s.px(18f),s.px(6f))
        }
        fun feature(icon: Int,title: Int,description: Int) {
            features.addView(LinearLayout(context).apply {
                gravity=Gravity.TOP
                setPadding(0,s.px(16f),0,s.px(16f))
                addView(FrameLayout(context).apply {
                    background=s.rounded(context.getColor(R.color.home_subtle_tile),14f)
                    addView(ImageView(context).apply {
                        setImageResource(icon)
                        imageTintList=ColorStateList.valueOf(context.getColor(R.color.home_icon_accent))
                        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
                    },FrameLayout.LayoutParams(s.px(23f),s.px(23f),Gravity.CENTER))
                },LayoutParams(s.px(44f),s.px(44f)).apply { marginEnd=s.px(14f) })
                addView(LinearLayout(context).apply {
                    orientation=VERTICAL
                    addView(s.label(s.text(title),15f))
                    addView(paragraph(s.text(description)),space(6f))
                },LayoutParams(0,-2,1f))
            },LayoutParams(-1,-2))
        }
        feature(R.drawable.home_camera,R.string.about_scan,R.string.about_scan_description)
        feature(R.drawable.home_check,R.string.about_restock,R.string.about_restock_description)
        feature(R.drawable.chat_bubble,R.string.about_assistant,R.string.about_assistant_description)
        addView(features,space(14f))

        addView(LinearLayout(context).apply {
            orientation=VERTICAL
            background=s.rounded(context.getColor(R.color.home_subtle_tile),22f)
            setPadding(s.px(22f),s.px(22f),s.px(22f),s.px(22f))
            addView(heading(s.text(R.string.about_help)))
            addView(paragraph(s.text(R.string.about_help_description)),space(8f))
            addView(ui.button(s.text(R.string.about_help_action),true,onHelp),space(18f))
        },space(20f))
        addView(s.label(s.text(R.string.about_team),12f,s.secondary,500).apply {
            gravity=Gravity.CENTER
        },space(26f))
        addView(paragraph(s.text(R.string.about_footer)).apply { gravity=Gravity.CENTER },space(8f))
    }
}
