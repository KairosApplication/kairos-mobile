package com.example.kairos.view.settings

import com.example.kairos.R

/** Each destination can be replaced with its complete screen independently. */
enum class SettingsDestination(val title: Int, val description: Int, val icon: Int, val viewId: Int) {
    ROOT(R.string.settings_title, R.string.settings_title, R.drawable.menu_settings_active, R.id.home_nav_config),
    PROFILE(R.string.settings_profile, R.string.settings_profile_description, R.drawable.settings_profile, R.id.settings_profile),
    SECURITY(R.string.settings_security, R.string.settings_security_description, R.drawable.settings_security, R.id.settings_security),
    NOTIFICATIONS(R.string.settings_notifications, R.string.settings_notifications_description,
        R.drawable.settings_notifications, R.id.settings_notifications),
    APPEARANCE(R.string.settings_appearance, R.string.settings_appearance_description,
        R.drawable.settings_appearance, R.id.settings_appearance),
    HELP(R.string.settings_help, R.string.settings_help_description, R.drawable.settings_support, R.id.settings_help),
    SUPPORT(R.string.settings_support_section, R.string.settings_support_description,
        R.drawable.settings_support, R.id.settings_contact_support),
    ABOUT(R.string.settings_about, R.string.settings_about_description, R.drawable.settings_info, R.id.settings_about);

    val parent: SettingsDestination
        get() = if (this == SUPPORT) HELP else ROOT
}
