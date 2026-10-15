package com.example.kairos.model.home

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

enum class AppTheme { LIGHT, DARK, SYSTEM }

/** Device preferences scoped to the authenticated UID; no credentials are stored here. */
class StockerPreferences(context: Context, uid: String) {
    private val preferences = context.applicationContext.getSharedPreferences("stocker_preferences", Context.MODE_PRIVATE)
    private val prefix = "${uid.length}:$uid:"
    fun enabled(key: String, default: Boolean = true) = preferences.getBoolean(prefix + key, default)
    fun setEnabled(key: String, value: Boolean) { preferences.edit().putBoolean(prefix + key, value).apply() }
    var theme: AppTheme
        get() = AppTheme.entries.firstOrNull { it.name == preferences.getString(prefix + "theme", null) } ?: AppTheme.LIGHT
        set(value) { preferences.edit().putString(prefix + "theme", value.name).apply() }

    fun applyTheme(activity: AppCompatActivity) {
        preferences.edit().putString("activeTheme", theme.name).apply()
        activity.delegate.localNightMode = mode(theme)
    }

    companion object {
        private fun mode(theme: AppTheme) = when (theme) {
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        fun restoreTheme(activity: AppCompatActivity) {
            val saved = activity.getSharedPreferences("stocker_preferences", Context.MODE_PRIVATE).getString("activeTheme", null)
            activity.delegate.localNightMode = mode(AppTheme.entries.firstOrNull { it.name == saved } ?: AppTheme.LIGHT)
        }
    }
}
