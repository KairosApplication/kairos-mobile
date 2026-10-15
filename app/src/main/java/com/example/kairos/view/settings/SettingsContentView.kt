package com.example.kairos.view.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import com.example.kairos.R
import com.example.kairos.model.home.AppTheme
import com.example.kairos.model.home.StockerPreferences

/** Figma v2 notification, appearance and help screens, sharing the existing account style. */
class SettingsContentView(
    context: Context, scale: Float, destination: SettingsDestination,
    private val preferences: StockerPreferences, busy: Boolean,
    onThemeChanged: () -> Unit, onSupport: () -> Unit, onAnswer: (String, String) -> Unit
) : LinearLayout(context) {
    private val s = AccountUiStyle(context, scale)
    init {
        orientation = VERTICAL
        id = when (destination) {
            SettingsDestination.NOTIFICATIONS -> R.id.settings_notifications_content
            SettingsDestination.APPEARANCE -> R.id.settings_appearance_content
            else -> R.id.settings_help_content
        }
        setPadding(s.px(17f), 0, s.px(18f), s.px(24f))
        fun heading(title: Int, top: Float = 21f) {
            addView(s.label(s.text(title), 22f, s.ink).apply {
                ViewCompat.setAccessibilityHeading(this, true)
            }, LayoutParams(-1, -2).apply { setMargins(s.px(14f), s.px(top), 0, s.px(18f)) })
        }
        fun toggle(card: LinearLayout, key: String, title: Int, subtitle: Int, default: Boolean = true) {
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = s.px(78f)
                setPadding(s.px(26f), s.px(16f), s.px(21f), s.px(16f))
            }
            val text = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(s.label(s.text(title), 18f))
                addView(s.label(s.text(subtitle), 16f, s.secondary, 500))
            }
            row.addView(text, LayoutParams(0, -2, 1f).apply { marginEnd = s.px(10f) })
            row.addView(SwitchCompat(context).apply {
                id = when (key) {
                    "general" -> R.id.preference_general
                    "restocking" -> R.id.preference_restocking
                    "updates" -> R.id.preference_updates
                    "sound" -> R.id.preference_sound
                    else -> R.id.preference_vibration
                }
                contentDescription = s.text(title)
                isChecked = preferences.enabled(key, default)
                isEnabled = !busy
                switchMinWidth = s.px(42f)
                minimumHeight = (48 * resources.displayMetrics.density).toInt()
                thumbTintList = ColorStateList.valueOf(0xFFEEEEEE.toInt())
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(context.getColor(R.color.home_green), 0xFF949494.toInt()))
                setOnCheckedChangeListener { _, checked -> preferences.setEnabled(key, checked) }
            }, LayoutParams(-2, -2))
            card.addView(row, LayoutParams(-1, -2))
        }
        when (destination) {
            SettingsDestination.NOTIFICATIONS -> {
                heading(R.string.settings_preferences)
                addView(s.card().apply {
                    toggle(this, "general", R.string.preference_general, R.string.preference_general_description, false)
                    toggle(this, "restocking", R.string.preference_restocking, R.string.preference_restocking_description)
                    toggle(this, "updates", R.string.preference_updates, R.string.preference_updates_description)
                }, LayoutParams(-1, -2))
                heading(R.string.preference_channels, 20f)
                addView(s.card().apply {
                    toggle(this, "sound", R.string.preference_sound, R.string.preference_sound_description)
                    toggle(this, "vibration", R.string.preference_vibration, R.string.preference_vibration_description)
                }, LayoutParams(-1, -2))
            }
            SettingsDestination.APPEARANCE -> {
                heading(R.string.preference_theme)
                val group = RadioGroup(context).apply {
                    setPadding(s.px(20f), s.px(7f), s.px(20f), s.px(7f))
                    contentDescription = s.text(R.string.preference_theme)
                }
                val titles = listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_system)
                val descriptions = listOf(R.string.theme_light_description, R.string.theme_dark_description, R.string.theme_system_description)
                AppTheme.entries.forEachIndexed { index, theme ->
                    group.addView(RadioButton(context).apply {
                        id = View.generateViewId()
                        tag = theme
                        val title = s.text(titles[index])
                        text = SpannableString("$title\n${s.text(descriptions[index])}").apply {
                            setSpan(AbsoluteSizeSpan((18f * scale).toInt(), true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(AbsoluteSizeSpan((14f * scale).toInt(), true), title.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(s.secondary), title.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        typeface = s.font
                        textSize = 16f * scale
                        setTextColor(s.ink)
                        fun ring() = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setSize(s.px(32f), s.px(32f))
                            setColor(Color.TRANSPARENT)
                            setStroke(s.px(3f).coerceAtLeast(1), s.ink)
                        }
                        val selected = LayerDrawable(arrayOf(ring(), GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(s.ink)
                        })).apply { setLayerInset(1, s.px(5f), s.px(5f), s.px(5f), s.px(5f)) }
                        buttonDrawable = StateListDrawable().apply {
                            addState(intArrayOf(android.R.attr.state_checked), selected)
                            addState(intArrayOf(), ring())
                        }
                        compoundDrawablePadding = s.px(24f)
                        minimumHeight = s.px(76f)
                        setPadding(s.px(10f), s.px(12f), 0, s.px(12f))
                        isEnabled = !busy
                        isChecked = preferences.theme == theme
                    }, RadioGroup.LayoutParams(-1, -2))
                }
                group.setOnCheckedChangeListener { radioGroup, checkedId ->
                    val theme = radioGroup.findViewById<RadioButton>(checkedId)?.tag as? AppTheme
                    if (theme != null && theme != preferences.theme) {
                        preferences.theme = theme
                        onThemeChanged()
                    }
                }
                addView(s.card().apply { addView(group) }, LayoutParams(-1, -2))
            }
            SettingsDestination.HELP -> {
                val search = EditText(context).apply {
                    id = R.id.help_search
                    hint = s.text(R.string.help_search)
                    contentDescription = hint
                    textSize = 15.5f * scale
                    typeface = s.font
                    setTextColor(s.ink)
                    setHintTextColor(s.secondary)
                    isSingleLine = true
                    background = s.rounded(s.surface, 100f, context.getColor(R.color.home_border))
                    setPadding(s.px(24f), s.px(14f), s.px(24f), s.px(14f))
                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.menu_search, 0, 0, 0)
                    compoundDrawablePadding = s.px(20f)
                    compoundDrawableTintList = ColorStateList.valueOf(s.ink)
                }
                addView(search, LayoutParams(-1, -2).apply { topMargin = s.px(23f); height = s.px(62f) })
                heading(R.string.help_faq, 15f)
                val list = s.card().apply { setPadding(0, s.px(10f), 0, s.px(10f)) }
                addView(list, LayoutParams(-1, -2))
                val questions = listOf(
                    R.string.help_restock to R.string.help_restock_answer,
                    R.string.help_scan to R.string.help_scan_answer,
                    R.string.help_password to R.string.help_password_answer,
                    R.string.help_problem to R.string.help_problem_answer,
                    R.string.help_profile to R.string.help_profile_answer
                )
                fun results(query: String) {
                    list.removeAllViews()
                    val matching = questions.filter { s.text(it.first).contains(query.trim(), ignoreCase = true) }
                    if (matching.isEmpty()) list.addView(s.label(s.text(R.string.help_no_results), 16f, s.secondary).apply {
                        setPadding(s.px(26f), s.px(16f), s.px(26f), s.px(16f))
                    })
                    matching.forEach { (question, answer) ->
                        list.addView(LinearLayout(context).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            minimumHeight = s.px(50f)
                            setPadding(s.px(29f), s.px(12f), s.px(16f), s.px(12f))
                            isFocusable = true
                            isEnabled = !busy
                            setOnClickListener { onAnswer(s.text(question), s.text(answer)) }
                            addView(s.label(s.text(question), 18f, s.ink, 500), LayoutParams(0, -2, 1f))
                            addView(s.icon(R.drawable.settings_chevron), LayoutParams(s.px(30f), s.px(30f)))
                        }, LayoutParams(-1, -2))
                    }
                }
                results("")
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) { results(value?.toString().orEmpty()) }
                    override fun afterTextChanged(value: Editable?) = Unit
                })
                addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(s.px(24f), s.px(20f), s.px(24f), s.px(20f))
                    background = s.rounded(context.getColor(R.color.home_subtle_tile), 20f)
                    addView(s.label(s.text(R.string.help_contact_heading), 18f, s.ink, 500).apply { gravity = Gravity.CENTER })
                    addView(s.label(s.text(R.string.help_contact_agent), 18f).apply { gravity = Gravity.CENTER })
                    addView(androidx.appcompat.widget.AppCompatButton(context).apply {
                        id = R.id.settings_contact_support
                        text = s.text(R.string.settings_contact_support)
                        isAllCaps = false
                        typeface = s.font
                        textSize = 18f * scale
                        setTextColor(context.getColor(R.color.home_button_text))
                        backgroundTintList = null
                        background = s.rounded(context.getColor(R.color.home_button), 50f)
                        isEnabled = !busy
                        setOnClickListener { onSupport() }
                    }, LayoutParams(-1, s.px(48f)).apply { topMargin = s.px(16f); marginStart = s.px(16f); marginEnd = s.px(16f) })
                }, LayoutParams(-1, -2).apply { topMargin = s.px(20f) })
            }
            else -> Unit
        }
    }
}
