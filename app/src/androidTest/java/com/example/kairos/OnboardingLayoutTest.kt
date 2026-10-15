package com.example.kairos

import android.content.res.Configuration
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kairos.view.AccountTestActivity
import com.example.kairos.view.OnboardingStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingLayoutTest {
    @Test fun allPagesKeepTheActionAboveSystemBarsOnSmallAndLargePhones() {
        ActivityScenario.launch(AccountTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (fontScale in listOf(1f, 1.5f)) {
                    val config = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
                    val context = androidx.appcompat.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_Kairos)
                    val density = context.resources.displayMetrics.density
                    for ((width, height) in listOf(320 to 568, 360 to 640, 412 to 915)) {
                        val root = activity.layoutInflater.cloneInContext(context).inflate(R.layout.view_onboarding, null)
                        OnboardingStyle.apply(root)
                        root.setPadding(0, (24 * density).toInt(), 0, (32 * density).toInt())
                        for (title in listOf(R.string.onboarding_title_time, R.string.onboarding_title_restocking,
                            R.string.onboarding_title_connected)) {
                            root.findViewById<TextView>(R.id.onboarding_title).setText(title)
                            root.findViewById<TextView>(R.id.onboarding_description).setText(R.string.onboarding_description)
                            root.findViewById<TextView>(R.id.onboarding_next).setText(R.string.onboarding_next)
                            root.findViewById<ImageView>(R.id.onboarding_illustration).setImageResource(R.drawable.ic_onboarding_2)
                            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                            val button = root.findViewById<TextView>(R.id.onboarding_next)
                            assertTrue(button.bottom <= root.height - root.paddingBottom)
                            assertTrue(button.top >= root.paddingTop)
                            assertTrue(root.findViewById<View>(R.id.onboarding_scroll).bottom <= button.top)
                            assertTrue(button.width > 0)
                            assertTrue(button.height >= (48 * density).toInt())
                            assertTrue(root.findViewById<TextView>(R.id.onboarding_title).typeface.weight >= 700)
                            assertTrue(root.findViewById<TextView>(R.id.onboarding_description).typeface.weight >= 600)
                        }
                    }
                }
            }
        }
    }
}
