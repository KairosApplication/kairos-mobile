package com.example.kairos.view

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.example.kairos.R

/** Apply after inflation so AppCompat's theme font cannot reset the variable weight. */
internal object OnboardingStyle {
    fun apply(root: View) {
        val font = requireNotNull(ResourcesCompat.getFont(root.context, R.font.montserrat_semibold))
        listOf(R.id.onboarding_title to 700, R.id.onboarding_description to 600,
            R.id.onboarding_counter to 600, R.id.onboarding_next to 700).forEach { (id, weight) ->
            root.findViewById<TextView>(id).apply {
                typeface = Typeface.create(font, weight, false)
                fontVariationSettings = "'wght' $weight"
                includeFontPadding = false
            }
        }
        ViewCompat.setAccessibilityHeading(root.findViewById(R.id.onboarding_title), true)
    }
}
