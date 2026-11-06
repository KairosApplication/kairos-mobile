package com.example.kairos.view.settings

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.example.kairos.R

/** Account menu shared by customer and stocker. Chat is not part of this menu. */
class SettingsMenuView(context:Context,scale:Float,busy:Boolean,onOpen:(SettingsDestination)->Unit,
    accountReadOnly:Boolean=false):LinearLayout(context) {
    init {
        val s=AccountUiStyle(context,scale)
        orientation=VERTICAL
        setPadding(s.px(20f),s.px(6f),s.px(20f),s.px(24f))
        fun heading(title:Int) {
            addView(s.label(s.text(title),20f).apply { ViewCompat.setAccessibilityHeading(this,true) },
                LayoutParams(-1,-2).apply { topMargin=s.px(20f);bottomMargin=s.px(12f) })
        }
        fun row(destination:SettingsDestination) {
            val description=s.text(if(accountReadOnly && destination==SettingsDestination.PROFILE) R.string.customer_profile_description else destination.description)
            val card=s.card { if(!busy) onOpen(destination) }.apply {
                id=destination.viewId
                orientation=HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                minimumHeight=s.px(80f)
                elevation=0f
                setPadding(s.px(16f),s.px(16f),s.px(14f),s.px(16f))
                isEnabled=!busy
                contentDescription="${s.text(destination.title)}. $description"
            }
            card.addView(FrameLayout(context).apply {
                background=s.rounded(context.getColor(R.color.home_subtle_tile),16f)
                addView(s.icon(destination.icon),FrameLayout.LayoutParams(s.px(26f),s.px(26f),Gravity.CENTER))
            },LayoutParams(s.px(46f),s.px(46f)))
            card.addView(LinearLayout(context).apply {
                orientation=VERTICAL
                addView(s.label(s.text(destination.title),16f))
                addView(s.label(description,13f,s.secondary,500),LayoutParams(-1,-2).apply { topMargin=s.px(4f) })
            },LayoutParams(0,-2,1f).apply { marginStart=s.px(14f);marginEnd=s.px(8f) })
            card.addView(s.icon(R.drawable.settings_chevron),LayoutParams(s.px(22f),s.px(22f)))
            addView(card,LayoutParams(-1,-2).apply { bottomMargin=s.px(10f) })
        }
        heading(R.string.settings_account)
        row(SettingsDestination.PROFILE);row(SettingsDestination.SECURITY)
        heading(R.string.settings_preferences)
        row(SettingsDestination.NOTIFICATIONS);row(SettingsDestination.APPEARANCE)
        heading(R.string.settings_support_section)
        row(SettingsDestination.HELP);row(SettingsDestination.ABOUT)
    }
}
